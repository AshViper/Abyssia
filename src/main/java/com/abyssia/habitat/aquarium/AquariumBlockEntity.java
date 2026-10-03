package com.abyssia.habitat.aquarium;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BT01g aquarium contents: up to {@link #CAPACITY} creatures {species, bornGameTime, adult}. Growth and breeding are
 * computed lazily from level gameTime ({@link #catchUp}), so unloaded time is caught up exactly: a juvenile becomes
 * adult {@link #GROW_TICKS} after birth; a species with 2+ adults and free space breeds one juvenile every
 * {@link #BREED_TICKS} counted from when it became eligible (or its last birth). Synced to the client for the renderer.
 */
public class AquariumBlockEntity extends BlockEntity
{
    public static final int CAPACITY = 8;
    public static final long GROW_TICKS = 12000L;
    public static final long BREED_TICKS = 24000L;
    private static final int CHECK_INTERVAL = 100;

    public record Creature(String species, long born, boolean adult) {}

    private final List<Creature> creatures = new ArrayList<>();
    /** species currently eligible to breed -> start of the running breeding interval */
    private final Map<String, Long> breedFrom = new LinkedHashMap<>();

    public AquariumBlockEntity(BlockPos pos, BlockState state)
    {
        super(AquariumContent.AQUARIUM_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AquariumBlockEntity be)
    {
        if ((level.getGameTime() + pos.asLong()) % CHECK_INTERVAL == 0) be.update();
    }

    public List<Creature> creatures()
    {
        return Collections.unmodifiableList(creatures);
    }

    /** Server: catch up to now; syncs when something changed. */
    public boolean update()
    {
        if (level == null || level.isClientSide) return false;
        boolean changed = catchUp(level.getGameTime());
        if (changed) sync();
        return changed;
    }

    /** Applies every growth / birth event up to {@code now} in time order. */
    boolean catchUp(long now)
    {
        boolean changed = false;
        for (int guard = 0; guard < 256; guard++)
        {
            long tGrow = Long.MAX_VALUE;
            int grow = -1;
            for (int i = 0; i < creatures.size(); i++)
            {
                Creature c = creatures.get(i);
                if (!c.adult() && c.born() + GROW_TICKS < tGrow)
                {
                    tGrow = c.born() + GROW_TICKS;
                    grow = i;
                }
            }
            long tBreed = Long.MAX_VALUE;
            String breeder = null;
            for (Map.Entry<String, Long> e : breedFrom.entrySet())
            {
                long t = e.getValue() + BREED_TICKS;
                if (t < tBreed)
                {
                    tBreed = t;
                    breeder = e.getKey();
                }
            }
            if (Math.min(tGrow, tBreed) > now) break;
            if (tGrow <= tBreed)
            {
                Creature c = creatures.get(grow);
                creatures.set(grow, new Creature(c.species(), c.born(), true));
                refreshBreeding(tGrow);
            }
            else
            {
                creatures.add(new Creature(breeder, tBreed, false));
                breedFrom.put(breeder, tBreed);
                refreshBreeding(tBreed);
            }
            changed = true;
        }
        return changed;
    }

    /** Eligible = 2+ adults of the species and a free slot; newly eligible species start their interval at {@code time}. */
    private void refreshBreeding(long time)
    {
        boolean space = creatures.size() < CAPACITY;
        Map<String, Integer> adults = new HashMap<>();
        for (Creature c : creatures) if (c.adult()) adults.merge(c.species(), 1, Integer::sum);
        breedFrom.keySet().removeIf(s -> !space || adults.getOrDefault(s, 0) < 2);
        if (space)
            for (Map.Entry<String, Integer> e : adults.entrySet())
                if (e.getValue() >= 2) breedFrom.putIfAbsent(e.getKey(), time);
    }

    /** Server: adds a creature (wild catches are adults); false when full. */
    public boolean release(String species, boolean adult)
    {
        if (level == null) return false;
        long now = level.getGameTime();
        catchUp(now);
        if (creatures.size() >= CAPACITY)
        {
            sync();
            return false;
        }
        creatures.add(new Creature(species, now, adult));
        refreshBreeding(now);
        sync();
        return true;
    }

    /** Server: removes one adult (the species with the most adults first, so pairs are split last); null when none. */
    @Nullable
    public Creature takeAdult()
    {
        if (level == null) return null;
        long now = level.getGameTime();
        catchUp(now);
        Map<String, Integer> adults = new HashMap<>();
        for (Creature c : creatures) if (c.adult()) adults.merge(c.species(), 1, Integer::sum);
        int pick = -1;
        for (int i = 0; i < creatures.size(); i++)
        {
            Creature c = creatures.get(i);
            if (!c.adult()) continue;
            if (pick < 0 || adults.get(c.species()) > adults.get(creatures.get(pick).species())) pick = i;
        }
        if (pick < 0)
        {
            sync();
            return null;
        }
        Creature taken = creatures.remove(pick);
        refreshBreeding(now);
        sync();
        return taken;
    }

    /** Server: empties the tank into filled canisters (dismantle / broken controller). */
    public List<ItemStack> takeAllAsCanisters()
    {
        if (level != null) catchUp(level.getGameTime());
        List<ItemStack> out = new ArrayList<>();
        for (Creature c : creatures) out.add(CreatureCaptureCanisterItem.filled(c.species(), c.adult()));
        creatures.clear();
        breedFrom.clear();
        setChanged();
        return out;
    }

    private void sync()
    {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Creature c : creatures)
        {
            CompoundTag t = new CompoundTag();
            t.putString("Species", c.species());
            t.putLong("Born", c.born());
            t.putBoolean("Adult", c.adult());
            list.add(t);
        }
        tag.put("Creatures", list);
        ListTag breed = new ListTag();
        for (Map.Entry<String, Long> e : breedFrom.entrySet())
        {
            CompoundTag t = new CompoundTag();
            t.putString("Species", e.getKey());
            t.putLong("From", e.getValue());
            breed.add(t);
        }
        tag.put("Breeding", breed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        creatures.clear();
        breedFrom.clear();
        ListTag list = tag.getList("Creatures", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && creatures.size() < CAPACITY; i++)
        {
            CompoundTag t = list.getCompound(i);
            if (!t.getString("Species").isEmpty())
                creatures.add(new Creature(t.getString("Species"), t.getLong("Born"), t.getBoolean("Adult")));
        }
        ListTag breed = tag.getList("Breeding", Tag.TAG_COMPOUND);
        for (int i = 0; i < breed.size(); i++)
        {
            CompoundTag t = breed.getCompound(i);
            breedFrom.put(t.getString("Species"), t.getLong("From"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
