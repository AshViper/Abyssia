package com.abyssia.worldgen.structure;

import com.abyssia.block.SeafloorCarpetBlock;
import com.abyssia.block.StackingPlantBlock;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.registry.ModTags;
import com.abyssia.worldgen.cave.CaveEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * What a {@link Formation} draws through: one structure instance clipped to one chunk and to the structure's
 * footprint. Writes outside either are dropped, so formations can loop over generous bounds; everything random is
 * a hash of the position and the instance seed, so a block comes out the same from whichever chunk paints it.
 * <p>
 * Write modes: {@link #place} builds rock (replacing water, plants and loose seabed material), {@link #fill} only
 * fills open water (mounds, aprons), {@link #carve} opens rock into water.
 */
public final class Painter
{
    public final WorldGenLevel level;
    public final Site site;
    /** Chunk columns inside the footprint's bounding box (empty when x0 > x1). */
    public final int x0, x1, z0, z1;
    private final int radiusSq;
    private final SimplexNoise noise;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final int minY, maxY;

    Painter(WorldGenLevel level, ChunkPos chunk, Site site)
    {
        this.level = level;
        this.site = site;
        int r = site.radius();
        this.x0 = Math.max(chunk.getMinBlockX(), site.x - r);
        this.x1 = Math.min(chunk.getMaxBlockX(), site.x + r);
        this.z0 = Math.max(chunk.getMinBlockZ(), site.z - r);
        this.z1 = Math.min(chunk.getMaxBlockZ(), site.z + r);
        this.radiusSq = r * r;
        this.noise = new SimplexNoise(new XoroshiroRandomSource(site.seed ^ 0x5EABEDL));
        this.minY = level.getMinBuildHeight() + 1;
        this.maxY = level.getMaxBuildHeight() - 1;
    }

    // ---------------------------------------------------------------- bounds

    public boolean isEmpty()
    {
        return x0 > x1 || z0 > z1;
    }

    /** Inside this chunk and the structure's footprint. */
    public boolean inReach(int x, int z)
    {
        if (x < x0 || x > x1 || z < z0 || z > z1) return false;
        int dx = x - site.x, dz = z - site.z;
        return dx * dx + dz * dz <= radiusSq;
    }

    /**
     * 1 inside the footprint, easing to 0 over the last {@code width} blocks before its edge. Formations that heap
     * material multiply by it, so nothing ends in a vertical cut where the footprint clips it.
     */
    public double edge(int x, int z, double width)
    {
        double d = Math.sqrt(Mth.square(x + 0.5 - site.x - 0.5) + Mth.square(z + 0.5 - site.z - 0.5));
        return Mth.clamp((site.radius() - d) / width, 0.0, 1.0);
    }

    private static final int GRID = 8;
    private final java.util.Map<Long, Integer> gridBase = new java.util.HashMap<>();

    /**
     * Undisturbed floor height, interpolated from samples on a world-aligned 8-block grid: smooth, identical from
     * every chunk, and about 25 times cheaper than {@link Site#baseAt} per column.
     */
    public double smoothBase(int x, int z)
    {
        int gx = Math.floorDiv(x, GRID), gz = Math.floorDiv(z, GRID);
        double fx = (x - gx * GRID) / (double) GRID, fz = (z - gz * GRID) / (double) GRID;
        double a = gridBase(gx, gz), b = gridBase(gx + 1, gz), c = gridBase(gx, gz + 1), d = gridBase(gx + 1, gz + 1);
        return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private int gridBase(int gx, int gz)
    {
        return gridBase.computeIfAbsent(((long) gx << 32) ^ (gz & 0xFFFFFFFFL), k -> site.baseAt(gx * GRID, gz * GRID));
    }

    /** First chunk column at or after {@code c - r} (loop bounds for a part centred at c). */
    public int fromX(double c, double r)
    {
        return Math.max(x0, Mth.floor(c - r));
    }

    public int toX(double c, double r)
    {
        return Math.min(x1, Mth.ceil(c + r));
    }

    public int fromZ(double c, double r)
    {
        return Math.max(z0, Mth.floor(c - r));
    }

    public int toZ(double c, double r)
    {
        return Math.min(z1, Mth.ceil(c + r));
    }

    // ---------------------------------------------------------------- reading

    public BlockState get(int x, int y, int z)
    {
        return level.getBlockState(pos.set(x, y, z));
    }

    /** Water, air, or anything soft (plants, carpets): what a structure may grow into. */
    public static boolean open(BlockState state)
    {
        return state.is(Blocks.WATER) || state.isAir() || !state.isSolid() && !state.is(Blocks.BEDROCK) && !(state.getBlock() instanceof ThermalVentBlock);
    }

    public boolean open(int x, int y, int z)
    {
        return open(get(x, y, z));
    }

    public boolean water(int x, int y, int z)
    {
        return get(x, y, z).is(Blocks.WATER);
    }

    /** Seabed rock and sediments a structure may merge into (the ore-vein host set). */
    public static boolean loose(BlockState state)
    {
        return state.is(ModTags.VEIN_REPLACEABLE);
    }

    /**
     * First open block above the floor of this column as the chunk stands now (seabed, or the cavern floor for cavern
     * structures). Reads only this chunk, whose earlier writes are deterministic.
     */
    public int floor(int x, int z)
    {
        if (site.cavern == null) return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        int y = Math.min(site.ceilingAt(x, z) - 1, site.baseAt(x, z) + 12);
        int guard = 0;
        while (!open(x, y, z) && y < maxY && guard++ < 16) y++;
        while (y > minY && open(x, y - 1, z) && guard++ < 80) y--;
        return y;
    }

    // ---------------------------------------------------------------- writing

    private boolean writable(int x, int y, int z)
    {
        return y >= minY && y < maxY && y < site.topY && inReach(x, z);
    }

    public void set(int x, int y, int z, BlockState state)
    {
        if (!writable(x, y, z) || get(x, y, z).is(Blocks.BEDROCK)) return;
        level.setBlock(pos.set(x, y, z), state, 2);
    }

    /** Structure material: replaces water, soft blocks and loose seabed material, never other structures' cores. */
    public void place(int x, int y, int z, BlockState state)
    {
        if (!writable(x, y, z)) return;
        BlockState current = get(x, y, z);
        if (open(current) || loose(current)) level.setBlock(pos, state, 2);
    }

    /** Only into open water or soft blocks: mounds and aprons settle on whatever is there. */
    public boolean fill(int x, int y, int z, BlockState state)
    {
        if (!writable(x, y, z)) return false;
        if (!open(get(x, y, z))) return false;
        level.setBlock(pos, state, 2);
        return true;
    }

    /** Replaces any existing solid block (surface re-coating: crater floors, mineral zones). */
    public void recoat(int x, int y, int z, BlockState state)
    {
        if (!writable(x, y, z)) return;
        BlockState current = get(x, y, z);
        if (!open(current) && !current.is(Blocks.BEDROCK) && !(current.getBlock() instanceof ThermalVentBlock)) level.setBlock(pos, state, 2);
    }

    /**
     * Opens a block into water. Returns false where the column meets air (a cave gas pocket) or bedrock: the caller
     * stops digging there so no water ever touches an air pocket.
     */
    public boolean carve(int x, int y, int z)
    {
        if (!writable(x, y, z)) return false;
        BlockState current = get(x, y, z);
        if (current.isAir() || current.is(Blocks.BEDROCK)) return false;
        if (!current.is(Blocks.WATER)) level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
        return true;
    }

    /** Fills the open gap under a part down to solid ground (at most {@code depth} blocks), so nothing floats. */
    public void root(int x, int y, int z, BlockState state, int depth)
    {
        for (int d = 0; d < depth; d++)
        {
            int ry = y - d;
            if (ry < minY || !open(x, ry, z)) return;
            set(x, ry, z, state);
        }
    }

    // ---------------------------------------------------------------- randomness

    /** Deterministic 0..1 per position and purpose. */
    public double hash(int x, int y, int z, int salt)
    {
        long h = RandomSupport.mixStafford13(site.seed ^ x * 3129871L ^ z * 116129781L ^ y * 0x5DEECE66DL ^ salt * 0x9E3779B97F4A7C15L);
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Smooth noise in -1..1, unique to this instance. */
    public double noise(double x, double y, double z)
    {
        return noise.getValue(x, y, z);
    }

    public double noise(double x, double z)
    {
        return noise.getValue(x, z);
    }

    /**
     * A block from the mix, in patches: smooth noise decides the material over a few blocks with a little per-block
     * jitter along the patch edges, so mixed rock reads as veins and bands rather than salt-and-pepper.
     */
    public BlockState pick(Mix mix, int x, int y, int z, int salt)
    {
        double n = noise.getValue(x * 0.17 + salt * 31.7, y * 0.13, z * 0.17);
        return mix.pick(Mth.clamp(0.5 + 0.55 * n + (hash(x, y, z, salt) - 0.5) * 0.18, 0, 0.9999));
    }

    // ---------------------------------------------------------------- plants

    /**
     * Places a plant, crystal cluster or carpet standing on the floor block below (x, y, z): stacking plants grow a
     * column up to the next non-water block, clusters point up. Honours the plant-inhibiting minerals.
     */
    public boolean plant(int x, int y, int z, CaveEnvironment.PlantEntry entry, double roll)
    {
        if (!writable(x, y, z)) return false;
        BlockState state = entry.state();
        Block block = state.getBlock();
        BlockState here = get(x, y, z);
        boolean water = here.is(Blocks.WATER);
        if (!water && !here.isAir()) return false;
        BlockPos below = new BlockPos(x, y - 1, z);
        BlockState ground = level.getBlockState(below);
        if (!ground.isFaceSturdy(level, below, Direction.UP)) return false;
        if (block instanceof AmethystClusterBlock)
        {
            if (state.hasProperty(AmethystClusterBlock.FACING)) state = state.setValue(AmethystClusterBlock.FACING, Direction.UP);
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) state = state.setValue(BlockStateProperties.WATERLOGGED, water);
            set(x, y, z, state);
            return true;
        }
        if (!water) return false;
        if (block instanceof StackingPlantBlock stacking)
        {
            if (!stacking.growsOnAnything() && ground.is(ModTags.INHIBITS_PLANTS)) return false;
            int height = entry.minHeight() + Mth.floor(Math.min(roll, 0.9999) * (entry.maxHeight() - entry.minHeight() + 1));
            int n = 0;
            while (n < height && y + n < site.topY && water(x, y + n, z)) n++;
            for (int i = 0; i < n; i++) set(x, y + i, z, state.setValue(StackingPlantBlock.TOP, i == n - 1));
            return n > 0;
        }
        if (block instanceof SeafloorCarpetBlock carpet ? !carpet.growsOnAnything() && ground.is(ModTags.INHIBITS_PLANTS) : ground.is(ModTags.INHIBITS_PLANTS)) return false;
        if (!state.canSurvive(level, pos.set(x, y, z))) return false;
        set(x, y, z, state);
        return true;
    }
}
