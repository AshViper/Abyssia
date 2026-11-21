package com.abyssia.logistics;

import com.abyssia.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Cargo buffers of the cargo uplinks (LOG01): one buffer per owner, at most cargo_buffer_stacks stacks, saved with the
 * overworld ("abyssia_cargo"). Distance and dimension do not matter: sender and receiver only share the owner.
 */
public class CargoNetwork extends SavedData
{
    public static final String NAME = "abyssia_cargo";

    private final Map<UUID, List<ItemStack>> buffers = new HashMap<>();

    public static CargoNetwork get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(CargoNetwork::load, CargoNetwork::new, NAME);
    }

    /** Puts the stack into the owner's buffer; returns what did not fit. */
    public ItemStack insert(UUID owner, ItemStack stack)
    {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        List<ItemStack> buffer = buffers.computeIfAbsent(owner, k -> new ArrayList<>());
        ItemStack left = stack.copy();
        for (ItemStack held : buffer)
        {
            if (left.isEmpty()) break;
            if (!ItemStack.isSameItemSameTags(held, left)) continue;
            int room = held.getMaxStackSize() - held.getCount();
            int move = Math.min(room, left.getCount());
            if (move <= 0) continue;
            held.grow(move);
            left.shrink(move);
        }
        int limit = Config.CARGO_BUFFER_STACKS.get();
        while (!left.isEmpty() && buffer.size() < limit)
        {
            int move = Math.min(left.getMaxStackSize(), left.getCount());
            buffer.add(withCount(left, move));
            left.shrink(move);
        }
        if (left.getCount() != stack.getCount()) setDirty();
        return left;
    }

    private static ItemStack withCount(ItemStack stack, int count)
    {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    /**
     * Hands up to max items to sink one stack at a time; sink returns what it did not take (that stays in the buffer).
     * Returns the number of items moved.
     */
    public int drain(UUID owner, int max, UnaryOperator<ItemStack> sink)
    {
        List<ItemStack> buffer = buffers.get(owner);
        if (buffer == null) return 0;
        int moved = 0;
        for (int i = 0; i < buffer.size() && moved < max; )
        {
            ItemStack held = buffer.get(i);
            int offer = Math.min(held.getCount(), max - moved);
            ItemStack rest = sink.apply(withCount(held, offer));
            int taken = offer - rest.getCount();
            if (taken > 0)
            {
                moved += taken;
                held.shrink(taken);
                setDirty();
            }
            if (held.isEmpty()) buffer.remove(i);
            else i++;
        }
        if (buffer.isEmpty()) buffers.remove(owner);
        return moved;
    }

    public int total(UUID owner)
    {
        int n = 0;
        for (ItemStack s : buffers.getOrDefault(owner, List.of())) n += s.getCount();
        return n;
    }

    public int stacks(UUID owner)
    {
        return buffers.getOrDefault(owner, List.of()).size();
    }

    /** The stacks of an owner (copies). */
    public List<ItemStack> contents(UUID owner)
    {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : buffers.getOrDefault(owner, List.of())) out.add(s.copy());
        return out;
    }

    public void remove(UUID owner)
    {
        if (buffers.remove(owner) != null) setDirty();
    }

    // ---------------------------------------------------------------- save

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, List<ItemStack>> entry : buffers.entrySet())
        {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag one = new CompoundTag();
            one.putUUID("Owner", entry.getKey());
            ListTag stacks = new ListTag();
            for (ItemStack stack : entry.getValue()) stacks.add(stack.save(new CompoundTag()));
            one.put("Stacks", stacks);
            owners.add(one);
        }
        tag.put("Buffers", owners);
        return tag;
    }

    public static CargoNetwork load(CompoundTag tag)
    {
        CargoNetwork network = new CargoNetwork();
        ListTag owners = tag.getList("Buffers", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++)
        {
            CompoundTag one = owners.getCompound(i);
            if (!one.hasUUID("Owner")) continue;
            List<ItemStack> buffer = new ArrayList<>();
            ListTag stacks = one.getList("Stacks", Tag.TAG_COMPOUND);
            for (int k = 0; k < stacks.size(); k++)
            {
                ItemStack stack = ItemStack.of(stacks.getCompound(k));
                if (!stack.isEmpty()) buffer.add(stack);
            }
            if (!buffer.isEmpty()) network.buffers.put(one.getUUID("Owner"), buffer);
        }
        return network;
    }
}
