package com.abyssia.habitat.scan;

import com.abyssia.Abyssia;
import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.industry.energy.IndustryEnergyStorage;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * H07 scan console: 20,000 FE buffer (receive only, every face = a cable network consumer), 5,000 FE per scan.
 * A scan walks the cylinder (radius 32..96, +-24..48 Y by upgrade level) around the console in loaded chunks only, at most
 * {@link #BLOCKS_PER_TICK} blocks per tick, and keeps the nearest {@link #MAX_HITS} targets plus the coarse terrain.
 * After the first manual scan it rescans every 5 s while a player is within 16 blocks and the energy suffices.
 * Results reach clients through the block entity update tag.
 */
public class ScanConsoleBlockEntity extends BlockEntity implements MenuProvider
{
    public static final int CAPACITY = 20_000;
    public static final int MAX_RECEIVE = 2_000;
    public static final int SCAN_COST = 5_000;
    public static final int BLOCKS_PER_TICK = 4_096;
    public static final int MAX_HITS = 2_048;
    public static final int RESCAN_TICKS = 100;
    public static final double PLAYER_RANGE = 16.0;
    public static final TagKey<Block> SCANNABLE = BlockTags.create(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "scannable"));

    /** ContainerData: energy lo / hi, progress (0-1000), scanning (0 / 1) */
    public static final int DATA_ENERGY = 0, DATA_PROGRESS = 2, DATA_SCANNING = 3, DATA_COUNT = 4;

    private final IndustryEnergyStorage energy = new IndustryEnergyStorage(CAPACITY, MAX_RECEIVE, 0, this::setChanged);
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    private ScanData result = ScanData.EMPTY;
    /** BT01f radar upgrade level 0..3 (ScanData.radius / halfHeight) */
    private int upgrade;
    /** null = every target */
    @Nullable private ResourceLocation target;
    /** set by the first manual scan: enables the automatic rescans */
    private boolean active;
    private long lastScanEnd = Long.MIN_VALUE / 2;
    @Nullable private Scan scan;
    /** client side: bumped on every update so renderers can rebuild caches */
    private int version;

    private final ContainerData data = new ContainerData()
    {
        @Override
        public int get(int index)
        {
            return switch (index)
            {
                case DATA_ENERGY -> energy.getEnergyStored() & 0xFFFF;
                case DATA_ENERGY + 1 -> energy.getEnergyStored() >>> 16;
                case DATA_PROGRESS -> scan == null ? 0 : (int) (1000L * scan.cursor / scan.total);
                case DATA_SCANNING -> scan == null ? 0 : 1;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount()
        {
            return DATA_COUNT;
        }
    };

    public ScanConsoleBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModHabitat.SCAN_CONSOLE_ENTITY.get(), pos, state);
    }

    // ---------------------------------------------------------------- API (menu, tests)

    public IEnergyStorage energy()
    {
        return energy;
    }

    public ContainerData data()
    {
        return data;
    }

    public ScanData result()
    {
        return result;
    }

    @Nullable
    public ResourceLocation target()
    {
        return target;
    }

    public int upgrade()
    {
        return upgrade;
    }

    public int radius()
    {
        return ScanData.radius(upgrade);
    }

    public int halfHeight()
    {
        return ScanData.halfHeight(upgrade);
    }

    /** BT01f (server): sets the upgrade level; a running scan finishes at its old size. */
    public void setUpgrade(int tier)
    {
        upgrade = ScanData.clampTier(tier);
        setChanged();
        sync();
    }

    public int version()
    {
        return version;
    }

    public boolean isScanning()
    {
        return scan != null;
    }

    /** Number of kept target positions of the last finished scan. */
    public int resultCount()
    {
        return result.hits.length;
    }

    /** Starts a scan (server side) with the current target; false when one is running or the energy is short. */
    public boolean startScan()
    {
        if (!(level instanceof ServerLevel) || scan != null || energy.getEnergyStored() < SCAN_COST) return false;
        energy.consume(SCAN_COST);
        active = true;
        scan = new Scan(target, upgrade);
        return true;
    }

    /** Sets the target (null = all) and starts a scan with it. */
    public boolean startScan(@Nullable ResourceLocation newTarget)
    {
        setTarget(newTarget);
        return startScan();
    }

    /** Which kind the map shows (null = all); the next scan keeps the nearest of that kind only. */
    public void setTarget(@Nullable ResourceLocation newTarget)
    {
        target = newTarget;
        setChanged();
        sync();
    }

    // ---------------------------------------------------------------- tick

    public void serverTick()
    {
        if (!(level instanceof ServerLevel server)) return;
        if ((level.getGameTime() + worldPosition.asLong()) % 10 == 0) CableNetworkManager.touchAround(level, worldPosition);
        if (scan != null)
        {
            if (scan.step(server)) finish();
            return;
        }
        if (active && level.getGameTime() - lastScanEnd >= RESCAN_TICKS && energy.getEnergyStored() >= SCAN_COST
                && level.hasNearbyAlivePlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, PLAYER_RANGE))
            startScan();
    }

    private void finish()
    {
        result = scan.build();
        scan = null;
        lastScanEnd = level.getGameTime();
        setChanged();
        sync();
    }

    private void sync()
    {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private record Hit(int distSq, int packed, ResourceLocation id) {}

    /** One scan in progress: cursor over the box around the console, column by column. */
    private final class Scan
    {
        @Nullable final ResourceLocation filter;
        int cursor;
        final int tier, radius, halfHeight, sizeX, sizeY, total;
        final short[] solid;
        final short[] seen;
        final Map<ResourceLocation, Integer> counts = new TreeMap<>();
        final List<Hit> hits = new ArrayList<>();
        @Nullable LevelChunk chunk;
        int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;

        Scan(@Nullable ResourceLocation filter, int tier)
        {
            this.filter = filter;
            this.tier = tier;
            this.radius = ScanData.radius(tier);
            this.halfHeight = ScanData.halfHeight(tier);
            this.sizeX = radius * 2 + 1;
            this.sizeY = halfHeight * 2 + 1;
            this.total = sizeX * sizeX * sizeY;
            this.solid = new short[ScanData.gridXZ(tier) * ScanData.gridXZ(tier) * ScanData.gridY(tier)];
            this.seen = new short[solid.length];
        }

        /** true when done */
        boolean step(ServerLevel server)
        {
            int budget = BLOCKS_PER_TICK;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (cursor < total && budget > 0)
            {
                int dx = cursor / (sizeX * sizeY) - radius;
                int rem = cursor % (sizeX * sizeY);
                int dz = rem / sizeY - radius;
                int dy = rem % sizeY - halfHeight;
                if (dx * dx + dz * dz > radius * radius)
                {
                    cursor += sizeY - (dy + halfHeight);   // skip the rest of this column
                    continue;
                }
                cursor++;
                budget--;
                pos.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                if (server.isOutsideBuildHeight(pos)) continue;
                int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
                if (cx != chunkX || cz != chunkZ)
                {
                    chunkX = cx;
                    chunkZ = cz;
                    chunk = server.getChunkSource().getChunkNow(cx, cz);
                }
                if (chunk == null) continue;
                BlockState state = chunk.getBlockState(pos);
                int cell = ScanData.cellIndex(tier, dx, dy, dz);
                seen[cell]++;
                if (state.isSolid()) solid[cell]++;
                if (state.is(Tags.Blocks.ORES) || state.is(SCANNABLE))
                {
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    counts.merge(id, 1, Integer::sum);
                    if (filter == null || filter.equals(id))
                        hits.add(new Hit(dx * dx + dy * dy + dz * dz, ScanData.pack(dx, dy, dz), id));
                }
            }
            return cursor >= total;
        }

        ScanData build()
        {
            List<ResourceLocation> palette = new ArrayList<>(counts.keySet());
            int[] countArr = new int[palette.size()];
            for (int i = 0; i < palette.size(); i++) countArr[i] = counts.get(palette.get(i));
            hits.sort((a, b) -> Integer.compare(a.distSq(), b.distSq()));
            int n = Math.min(MAX_HITS, hits.size());
            int[] packed = new int[n], kinds = new int[n];
            for (int i = 0; i < n; i++)
            {
                packed[i] = hits.get(i).packed();
                kinds[i] = Math.max(0, palette.indexOf(hits.get(i).id()));
            }
            BitSet terrain = new BitSet(solid.length);
            for (int i = 0; i < solid.length; i++)
                if (seen[i] > 0 && solid[i] * 2 >= seen[i]) terrain.set(i);
            return new ScanData(palette, countArr, packed, kinds, terrain, true, tier);
        }
    }

    // ---------------------------------------------------------------- capabilities

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps()
    {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energy);
    }

    // ---------------------------------------------------------------- save / sync

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.put("Scan", result.save());
        if (target != null) tag.putString("Target", target.toString());
        tag.putBoolean("Active", active);
        tag.putInt("Upgrade", upgrade);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        energy.setEnergy(tag.getInt("Energy"));
        result = tag.contains("Scan") ? ScanData.load(tag.getCompound("Scan")) : ScanData.EMPTY;
        target = tag.contains("Target") ? ResourceLocation.tryParse(tag.getString("Target")) : null;
        active = tag.getBoolean("Active");
        upgrade = ScanData.clampTier(tag.getInt("Upgrade"));
        version++;
    }

    @Override
    public CompoundTag getUpdateTag()
    {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox()
    {
        return new AABB(worldPosition).inflate(1.5, 3.0, 1.5);
    }

    // ---------------------------------------------------------------- menu

    @Override
    public Component getDisplayName()
    {
        return Component.translatable("container." + Abyssia.MODID + ".scan_console");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player)
    {
        return new ScanConsoleMenu(id, worldPosition, data);
    }
}
