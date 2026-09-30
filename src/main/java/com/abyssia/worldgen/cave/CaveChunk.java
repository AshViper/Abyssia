package com.abyssia.worldgen.cave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.CarvingMask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generation context for one chunk: the cave field over the chunk plus a one-block rim (so wall orientation and
 * neighbours can be judged at the chunk edge), the space owning each carved block, and the lazily cached wall
 * noise. Buffers are thread-local and reused; every value is a pure function of world position, so neighbouring
 * chunks computing their own fields agree exactly along the border.
 */
public final class CaveChunk
{
    /** Blocks of wall behind a cave surface painted with geological layers. */
    public static final int WALL_DEPTH = 3;
    private static final int PAD = 1;
    private static final int SIZE = 16 + 2 * PAD;
    private static final float OUTSIDE = Float.MAX_VALUE;
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static final ThreadLocal<Buffers> BUFFERS = ThreadLocal.withInitial(Buffers::new);

    /** Reused per thread; grown on demand. */
    private static final class Buffers
    {
        float[] field = new float[0], dome = new float[0], shapeNoise = new float[0], detailNoise = new float[0];
        short[] owner = new short[0], domeOwner = new short[0];
        byte[] fill = new byte[0];

        void ensure(int n)
        {
            if (field.length >= n) return;
            field = new float[n];
            dome = new float[n];
            shapeNoise = new float[n];
            detailNoise = new float[n];
            owner = new short[n];
            domeOwner = new short[n];
            fill = new byte[n];
        }
    }

    /** Fill flags: formation rock placed here, and whether it is the pointed end of a giant speleothem. */
    static final byte FILLED = 1, TIP_DOWN = 2, TIP_UP = 3, SHELF = 4;

    final CaveNetwork network;
    final CaveNoises noises;
    final ChunkAccess chunk;
    final int x0, z0;
    final int yMin, yMax;
    final List<CaveSystem> systems;
    final List<CaveSpace> spaces = new ArrayList<>();
    private final Map<CaveSpace, Integer> spaceIndex = new IdentityHashMap<>();
    private final List<CaveShape> carves = new ArrayList<>(), fills = new ArrayList<>(), ores = new ArrayList<>(), cuts = new ArrayList<>();
    final List<CaveSystem.Site> sites = new ArrayList<>();
    final List<CaveSystem.Vent> vents = new ArrayList<>();
    private final float[] field, dome, shapeNoise, detailNoise;
    private final short[] owner, domeOwner;
    final byte[] fill;
    private final double[] shelfPhase = new double[SIZE * SIZE];
    /** Seabed (first open block above the terrain) per column before carving; strata depths are measured from it. */
    final int[] seabed = new int[256];
    private final LevelChunkSection[] sections;
    private final int minBuildY;
    private boolean hasLakes;
    private CarvingMask mask;
    final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    int carved;

    private CaveChunk(CaveNetwork network, ChunkAccess chunk, List<CaveSystem> systems, int yMin, int yMax)
    {
        this.network = network;
        this.noises = network.noises();
        this.chunk = chunk;
        ChunkPos cp = chunk.getPos();
        this.x0 = cp.getMinBlockX();
        this.z0 = cp.getMinBlockZ();
        this.yMin = yMin;
        this.yMax = yMax;
        this.systems = systems;
        this.sections = chunk.getSections();
        this.minBuildY = chunk.getMinBuildHeight();
        Buffers buffers = BUFFERS.get();
        int n = SIZE * SIZE * (yMax - yMin + 1);
        buffers.ensure(n);
        this.field = buffers.field;
        this.dome = buffers.dome;
        this.shapeNoise = buffers.shapeNoise;
        this.detailNoise = buffers.detailNoise;
        this.owner = buffers.owner;
        this.domeOwner = buffers.domeOwner;
        this.fill = buffers.fill;
        Arrays.fill(field, 0, n, OUTSIDE);
        Arrays.fill(dome, 0, n, OUTSIDE);
        Arrays.fill(shapeNoise, 0, n, Float.NaN);
        Arrays.fill(detailNoise, 0, n, Float.NaN);
        Arrays.fill(owner, 0, n, (short) -1);
        Arrays.fill(domeOwner, 0, n, (short) -1);
        Arrays.fill(fill, 0, n, (byte) 0);
    }

    /** Context for this chunk, or null when no cave shape reaches it. */
    static CaveChunk create(CaveNetwork network, ChunkAccess chunk, List<CaveSystem> systems)
    {
        ChunkPos cp = chunk.getPos();
        int bx0 = cp.getMinBlockX() - PAD, bz0 = cp.getMinBlockZ() - PAD, bx1 = cp.getMaxBlockX() + PAD, bz1 = cp.getMaxBlockZ() + PAD;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        List<CaveShape> shapes = new ArrayList<>();
        for (CaveSystem s : systems)
        {
            for (CaveShape shape : s.shapes)
            {
                if (!shape.intersects(bx0, network.minY(), bz0, bx1, network.maxY(), bz1)) continue;
                shapes.add(shape);
                lo = Math.min(lo, shape.minY);
                hi = Math.max(hi, shape.maxY);
            }
            for (CaveSystem.Site site : s.sites)
            {
                if (site.x() + site.radius() < bx0 || site.x() - site.radius() > bx1 || site.z() + site.radius() < bz0 || site.z() - site.radius() > bz1) continue;
                lo = Math.min(lo, (int) site.y() - 16);
                hi = Math.max(hi, (int) site.y() + 24);
            }
        }
        if (lo > hi) return null;
        lo = Math.max(lo, network.minY() + 1);
        hi = Math.min(hi, network.maxY() - 2);
        if (lo > hi) return null;
        CaveChunk ctx = new CaveChunk(network, chunk, systems, lo, hi);
        for (CaveShape shape : shapes)
        {
            switch (shape.kind)
            {
                case CARVE -> ctx.carves.add(shape);
                case FILL -> ctx.fills.add(shape);
                case ORE -> ctx.ores.add(shape);
                case CUT -> ctx.cuts.add(shape);
            }
            ctx.indexOf(shape.space);
            if (shape.kind == CaveShape.Kind.CARVE && shape.space.hasLake()) ctx.hasLakes = true;
        }
        for (CaveSystem s : systems)
        {
            for (CaveSystem.Site site : s.sites)
            {
                if (site.x() + site.radius() >= bx0 && site.x() - site.radius() <= bx1 && site.z() + site.radius() >= bz0 && site.z() - site.radius() <= bz1)
                {
                    ctx.sites.add(site);
                    ctx.indexOf(site.space());
                }
            }
            for (CaveSystem.Vent vent : s.vents)
            {
                if (vent.x() >= bx0 - 12 && vent.x() <= bx1 + 12 && vent.z() >= bz0 - 12 && vent.z() <= bz1 + 12) ctx.vents.add(vent);
            }
        }
        for (int lz = 0; lz < 16; lz++)
        {
            for (int lx = 0; lx < 16; lx++) ctx.seabed[lz * 16 + lx] = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lx, lz);
        }
        for (int lz = -PAD; lz < 16 + PAD; lz++)
        {
            for (int lx = -PAD; lx < 16 + PAD; lx++) ctx.shelfPhase[(lz + PAD) * SIZE + lx + PAD] = ctx.noises.shelfPhase(ctx.x0 + lx, ctx.z0 + lz);
        }
        return ctx;
    }

    /**
     * Context for the terrain's own caves in this chunk: the cheese caverns and tunnels the deep ocean's density
     * function leaves below the seabed, outside the cave network. Every enclosed water block (below the seabed, not
     * carved by the network) counts as carved, owned by {@code large} where its column's open run is at least
     * {@code largeRun} blocks tall and by {@code small} elsewhere. Only the chunk itself is classified, never the rim.
     * Null when the chunk holds no such cave. Call only after the network's own context is finished: both share the
     * thread's buffers.
     */
    static CaveChunk terrain(CaveNetwork network, ChunkAccess chunk, CaveSpace small, CaveSpace large, int largeRun)
    {
        CarvingMask mask = chunk instanceof ProtoChunk proto ? proto.getOrCreateCarvingMask(GenerationStep.Carving.AIR) : null;
        LevelChunkSection[] sections = chunk.getSections();
        int minBuildY = chunk.getMinBuildHeight();
        int[] seabed = new int[256];
        int top = Integer.MIN_VALUE;
        for (int i = 0; i < 256; i++)
        {
            seabed[i] = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, i & 15, i >> 4);
            top = Math.max(top, seabed[i] - 2);
        }
        int bottom = network.minY() + 6;
        top = Math.min(top, network.maxY() - 3);
        if (top < bottom) return null;
        // Enclosed water: at least one solid block overhead (below the seabed), skipping sections without any water.
        boolean[] cave = new boolean[256 * (top - bottom + 1)];
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int s = Math.max(0, (bottom - minBuildY) >> 4); s <= Math.min(sections.length - 1, (top - minBuildY) >> 4); s++)
        {
            LevelChunkSection section = sections[s];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state == WATER)) continue;
            int y0 = Math.max(bottom, minBuildY + s * 16), y1 = Math.min(top, minBuildY + s * 16 + 15);
            for (int y = y0; y <= y1; y++)
            {
                for (int i = 0; i < 256; i++)
                {
                    int lx = i & 15, lz = i >> 4;
                    if (y > seabed[i] - 2 || section.getBlockState(lx, y & 15, lz) != WATER || (mask != null && mask.get(lx, y, lz))) continue;
                    cave[(y - bottom) * 256 + i] = true;
                    lo = Math.min(lo, y);
                    hi = Math.max(hi, y);
                }
            }
        }
        if (lo > hi) return null;
        CaveChunk ctx = new CaveChunk(network, chunk, List.of(), lo - 1, hi + 1);
        short smallIndex = (short) ctx.indexOf(small), largeIndex = (short) ctx.indexOf(large);
        System.arraycopy(seabed, 0, ctx.seabed, 0, 256);
        for (int i = 0; i < 256; i++)
        {
            int lx = i & 15, lz = i >> 4, run = 0;
            for (int y = lo; y <= hi + 1; y++)
            {
                if (y <= hi && cave[(y - bottom) * 256 + i])
                {
                    run++;
                    continue;
                }
                // Close a run of open blocks: one owner for the whole vertical stretch.
                short owner = run >= largeRun ? largeIndex : smallIndex;
                for (int k = y - run; k < y; k++)
                {
                    int idx = ctx.index(lx, k, lz);
                    ctx.field[idx] = -1f;
                    ctx.owner[idx] = owner;
                }
                run = 0;
            }
        }
        return ctx;
    }

    private int indexOf(CaveSpace space)
    {
        Integer i = spaceIndex.get(space);
        if (i != null) return i;
        spaces.add(space);
        spaceIndex.put(space, spaces.size() - 1);
        return spaces.size() - 1;
    }

    // ---------------------------------------------------------------- field

    private int index(int lx, int y, int lz)
    {
        return ((y - yMin) * SIZE + lz + PAD) * SIZE + lx + PAD;
    }

    boolean inField(int lx, int y, int lz)
    {
        return y >= yMin && y <= yMax && lx >= -PAD && lx < 16 + PAD && lz >= -PAD && lz < 16 + PAD;
    }

    /** Signed cave field at a block: negative where our caves are carved, 0..WALL_DEPTH in their walls. */
    float field(int lx, int y, int lz)
    {
        return inField(lx, y, lz) ? field[index(lx, y, lz)] : OUTSIDE;
    }

    /** Carved by the cave network (as opposed to open sea or rock). */
    boolean carvedHere(int lx, int y, int lz)
    {
        return inField(lx, y, lz) && field[index(lx, y, lz)] < 0;
    }

    /** The space owning a carved or wall block, or null. */
    CaveSpace space(int lx, int y, int lz)
    {
        if (!inField(lx, y, lz)) return null;
        short o = owner[index(lx, y, lz)];
        return o < 0 ? null : spaces.get(o);
    }

    byte fillFlag(int lx, int y, int lz)
    {
        return inField(lx, y, lz) ? fill[index(lx, y, lz)] : 0;
    }

    /** Wall displacement of this space at this block, from cached raw noise. */
    private double displacement(CaveSpace space, int idx, int x, int y, int z, int col)
    {
        float sn = shapeNoise[idx];
        if (Float.isNaN(sn))
        {
            sn = (float) noises.shape(x + 0.5, y + 0.5, z + 0.5);
            shapeNoise[idx] = sn;
        }
        double d = space.shapeAmp * sn;
        double detail = space.detailAmp * (1 - space.smoothness);
        if (detail > 0.01)
        {
            float dn = detailNoise[idx];
            if (Float.isNaN(dn))
            {
                dn = (float) noises.detail(x + 0.5, y + 0.5, z + 0.5);
                detailNoise[idx] = dn;
            }
            d += detail * dn;
        }
        if (space.shelfAmp > 0) d += space.shelfAmp * CaveNoises.shelf(y + 0.5, shelfPhase[col], space.shelfPeriod);
        if (space.cavern != null) d += space.cavern.wallRelief(x + 0.5, y + 0.5, z + 0.5);
        return d;
    }

    /** Evaluates every carve shape over its part of this chunk (plus rim): the union keeps the smallest value. */
    void computeField()
    {
        for (CaveShape shape : carves)
        {
            int si = indexOf(shape.space);
            CaveSpace space = shape.space;
            double reach = shape.noisy ? space.maxDisplacement() : 0;
            boolean lake = space.hasLake();
            int ax = Math.max(shape.minX, x0 - PAD), bx = Math.min(shape.maxX, x0 + 15 + PAD);
            int az = Math.max(shape.minZ, z0 - PAD), bz = Math.min(shape.maxZ, z0 + 15 + PAD);
            int ay = Math.max(shape.minY, yMin), by = Math.min(shape.maxY, yMax);
            for (int y = ay; y <= by; y++)
            {
                for (int z = az; z <= bz; z++)
                {
                    int row = (z - z0 + PAD) * SIZE;
                    for (int x = ax; x <= bx; x++)
                    {
                        double d = shape.distance(x + 0.5, y + 0.5, z + 0.5);
                        if (d - reach > WALL_DEPTH + 0.5) continue;
                        int idx = index(x - x0, y, z - z0);
                        // Deep inside, noise cannot change the answer: skip sampling it.
                        double s = shape.noisy && d + reach > -0.5 ? d + displacement(space, idx, x, y, z, row + x - x0 + PAD) : d;
                        if (s < field[idx])
                        {
                            field[idx] = (float) s;
                            owner[idx] = (short) si;
                        }
                        if (lake && s < dome[idx])
                        {
                            dome[idx] = (float) s;
                            domeOwner[idx] = (short) si;
                        }
                    }
                }
            }
        }
    }

    private int waterLevelAt(int idx)
    {
        return spaces.get(domeOwner[idx]).waterLevel;
    }

    /** Inside a lake's gas pocket: above its water line. */
    private boolean gasAt(int idx, int y)
    {
        return hasLakes && dome[idx] < 0 && y > waterLevelAt(idx);
    }

    /**
     * Carves: water, or air in the gas pocket above an underground lake's water line. The gas pocket is sealed by a
     * solid shell that overrides any other cave or open water, then any water still touching the air from the
     * side or from above is turned to rock, so no lake ever drains or floods at runtime.
     */
    void carve()
    {
        mask = chunk instanceof ProtoChunk proto ? proto.getOrCreateCarvingMask(GenerationStep.Carving.AIR) : null;
        int bottom = network.minY() + 5;
        for (int y = Math.max(yMin, bottom); y <= yMax; y++)
        {
            for (int lz = 0; lz < 16; lz++)
            {
                for (int lx = 0; lx < 16; lx++)
                {
                    int idx = index(lx, y, lz);
                    boolean gas = gasAt(idx, y);
                    if (hasLakes && !gas && dome[idx] >= 0 && dome[idx] < CaveChamberGenerator.LAKE_SHELL && y > waterLevelAt(idx))
                    {
                        seal(lx, y, lz, spaces.get(domeOwner[idx]));
                        field[idx] = Math.max(field[idx], 0.5f);
                        continue;
                    }
                    if (field[idx] >= 0 && !gas) continue;
                    BlockState current = get(lx, y, lz);
                    if (current.is(Blocks.BEDROCK)) continue;
                    if (gas)
                    {
                        owner[idx] = domeOwner[idx];
                        field[idx] = Math.min(field[idx], -0.01f);
                    }
                    BlockState target = gas ? AIR : WATER;
                    if (current != target) set(lx, y, lz, target);
                    if (mask != null) mask.set(lx, y, lz);
                    carved++;
                }
            }
        }
        if (hasLakes) sealLakes();
    }

    private void seal(int lx, int y, int lz, CaveSpace lake)
    {
        BlockState current = get(lx, y, lz);
        if (current.isAir() || !current.getFluidState().isEmpty() || current.canBeReplaced())
        {
            set(lx, y, lz, CaveGeology.wallRock(this, lake, x0 + lx, y, z0 + lz));
        }
    }

    /** Guarantee: no water beside or above gas. Neighbours outside the chunk are judged from the (identical) field. */
    private void sealLakes()
    {
        for (int y = yMin; y <= yMax; y++)
        {
            for (int lz = 0; lz < 16; lz++)
            {
                for (int lx = 0; lx < 16; lx++)
                {
                    int idx = index(lx, y, lz);
                    if (dome[idx] > CaveChamberGenerator.LAKE_SHELL + 3 || gasAt(idx, y)) continue;
                    if (!get(lx, y, lz).getFluidState().isEmpty() && touchesGas(lx, y, lz))
                    {
                        CaveSpace lake = spaces.get(domeOwner[idx] >= 0 ? domeOwner[idx] : owner[idx] >= 0 ? owner[idx] : 0);
                        set(lx, y, lz, CaveGeology.wallRock(this, lake, x0 + lx, y, z0 + lz));
                        field[idx] = Math.max(field[idx], 0.5f);
                    }
                }
            }
        }
    }

    private boolean touchesGas(int lx, int y, int lz)
    {
        for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN})
        {
            int nx = lx + d.getStepX(), ny = y + d.getStepY(), nz = lz + d.getStepZ();
            if (!inField(nx, ny, nz)) continue;
            if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16 ? get(nx, ny, nz).isAir() && gasAt(index(nx, ny, nz), ny) : gasAt(index(nx, ny, nz), ny)) return true;
        }
        return false;
    }

    /**
     * Formation rock (pillars, bridges, shelves, boulders, giant speleothems, large crystals, arches), which wins
     * over carving; pointed formations mark their blocks so tips can be added later.
     */
    void placeFills()
    {
        for (CaveShape shape : fills)
        {
            CaveSpace space = shape.space;
            double reach = shape.noisy ? space.maxDisplacement() : 0;
            int ax = Math.max(shape.minX, x0), bx = Math.min(shape.maxX, x0 + 15);
            int az = Math.max(shape.minZ, z0), bz = Math.min(shape.maxZ, z0 + 15);
            int ay = Math.max(shape.minY, Math.max(yMin, network.minY() + 5)), by = Math.min(shape.maxY, yMax);
            byte flag = shape.tip == Direction.DOWN ? TIP_DOWN : shape.tip == Direction.UP ? TIP_UP : shape.shelf ? SHELF : FILLED;
            for (int y = ay; y <= by; y++)
            {
                for (int z = az; z <= bz; z++)
                {
                    for (int x = ax; x <= bx; x++)
                    {
                        double d = shape.distance(x + 0.5, y + 0.5, z + 0.5);
                        if (d - reach >= 0) continue;
                        int idx = index(x - x0, y, z - z0);
                        if (shape.noisy && d + reach > -0.5) d += displacement(space, idx, x, y, z, (z - z0 + PAD) * SIZE + x - x0 + PAD);
                        if (d >= 0) continue;
                        int lx = x - x0, lz = z - z0;
                        if (get(lx, y, lz).is(Blocks.BEDROCK)) continue;
                        // Solid rock is always safe next to a gas pocket: islands rising out of a lake, stalactites
                        // hanging into its air.
                        BlockState state = shape.block != null ? shape.block : CaveGeology.wallRock(this, space, x, y, z);
                        // Waterloggable fill (frond crowns) keeps the water it replaces.
                        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) state = state.setValue(BlockStateProperties.WATERLOGGED, water(lx, y, lz));
                        set(lx, y, lz, state);
                        if (fill[idx] == 0 || flag != FILLED) fill[idx] = flag;
                    }
                }
            }
        }
    }

    /**
     * Cuts: cracks and worn-through holes in formation rock, carved after the formations exist (water, or gas above a
     * lake's water line, like any carving).
     */
    void placeCuts()
    {
        for (CaveShape shape : cuts)
        {
            int si = indexOf(shape.space);
            int ax = Math.max(shape.minX, x0), bx = Math.min(shape.maxX, x0 + 15);
            int az = Math.max(shape.minZ, z0), bz = Math.min(shape.maxZ, z0 + 15);
            int ay = Math.max(shape.minY, yMin), by = Math.min(shape.maxY, yMax);
            for (int y = ay; y <= by; y++)
            {
                for (int z = az; z <= bz; z++)
                {
                    for (int x = ax; x <= bx; x++)
                    {
                        int lx = x - x0, lz = z - z0;
                        int idx = index(lx, y, lz);
                        if (fill[idx] == 0 || shape.distance(x + 0.5, y + 0.5, z + 0.5) >= 0) continue;
                        boolean gas = gasAt(idx, y);
                        // Never open water into a lake's sealing shell or beside its gas.
                        if (!gas && hasLakes && (dome[idx] < CaveChamberGenerator.LAKE_SHELL + 1 && y > waterLevelAt(idx) || touchesGas(lx, y, lz))) continue;
                        set(lx, y, lz, gas ? AIR : WATER);
                        fill[idx] = 0;
                        field[idx] = -0.01f;
                        owner[idx] = (short) si;
                    }
                }
            }
        }
    }

    /** Ore bodies: replace solid rock only (never carved space), ore at the core and host rock at the rim. */
    void placeOres()
    {
        for (CaveShape shape : ores)
        {
            CaveSpace space = shape.space;
            double reach = shape.noisy ? space.maxDisplacement() : 0;
            int ax = Math.max(shape.minX, x0), bx = Math.min(shape.maxX, x0 + 15);
            int az = Math.max(shape.minZ, z0), bz = Math.min(shape.maxZ, z0 + 15);
            int ay = Math.max(shape.minY, Math.max(yMin, network.minY() + 5)), by = Math.min(shape.maxY, yMax);
            for (int y = ay; y <= by; y++)
            {
                for (int z = az; z <= bz; z++)
                {
                    for (int x = ax; x <= bx; x++)
                    {
                        double d = shape.distance(x + 0.5, y + 0.5, z + 0.5);
                        if (d - reach >= 0) continue;
                        int lx = x - x0, lz = z - z0;
                        int idx = index(lx, y, lz);
                        if (shape.noisy && d + reach > -0.5) d += displacement(space, idx, x, y, z, (z - z0 + PAD) * SIZE + lx + PAD);
                        if (d >= 0) continue;
                        BlockState current = get(lx, y, lz);
                        if (current.isAir() || !current.getFluidState().isEmpty() || current.is(Blocks.BEDROCK) || current.canBeReplaced()) continue;
                        boolean core = shape.host == null || d < -1.0 || noises.hash(x, y, z, 41) < 0.25;
                        set(lx, y, lz, core ? shape.block : shape.host);
                    }
                }
            }
        }
    }

    /** Refresh the ocean floor heightmap after entrances were cut through the seabed and formations raised on it. */
    void finish()
    {
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
    }

    // ---------------------------------------------------------------- block access (chunk-local x/z)

    BlockState get(int lx, int y, int lz)
    {
        if (y < minBuildY || y >= minBuildY + sections.length * 16) return AIR;
        return sections[(y - minBuildY) >> 4].getBlockState(lx, y & 15, lz);
    }

    void set(int lx, int y, int lz, BlockState state)
    {
        if (y < minBuildY || y >= minBuildY + sections.length * 16) return;
        sections[(y - minBuildY) >> 4].setBlockState(lx, y & 15, lz, state, false);
    }

    boolean inChunk(int lx, int lz)
    {
        return lx >= 0 && lx < 16 && lz >= 0 && lz < 16;
    }

    /** Absolute position for block methods that need one (support checks). */
    BlockPos at(int lx, int y, int lz)
    {
        return pos.set(x0 + lx, y, z0 + lz);
    }

    /** Whether the block at (lx, y, lz) offers a sturdy face toward {@code face} (e.g. UP for something standing on it). */
    boolean sturdy(int lx, int y, int lz, Direction face)
    {
        if (!inChunk(lx, lz)) return false;
        BlockState state = get(lx, y, lz);
        return !state.isAir() && state.getFluidState().isEmpty() && state.isFaceSturdy(chunk, at(lx, y, lz), face);
    }

    /** Open cave water (or gas) with nothing placed in it yet. */
    boolean open(int lx, int y, int lz)
    {
        BlockState state = get(lx, y, lz);
        return state.isAir() || state.is(Blocks.WATER);
    }

    boolean water(int lx, int y, int lz)
    {
        return get(lx, y, lz).is(Blocks.WATER);
    }
}
