package com.abyssia.worldgen;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.lang.ref.WeakReference;
import java.util.stream.Stream;

/**
 * {@code {"type": "abyssia:deep_floor"}}: moves a placement to the first open block above the deep layer's seabed
 * (the deep-layer stand-in for {@code minecraft:heightmap OCEAN_FLOOR_WG}, which finds the ocean world's seabed above
 * the bedrock band). Drops the position where the column has no deep floor.
 * <p>
 * {@link #surface} is the same lookup for features: like the heightmap it follows blocks placed earlier in the chunk
 * (spires, structures), and a per-thread cache of the floors keeps it cheap: a cached floor is re-checked against the
 * blocks and walked up or rescanned only when something changed it. The cache lives for one generation region (one
 * chunk's decoration, over every chunk it reads), so loops that cross chunk edges keep it. Something built over a cached floor
 * without touching it (an overhang) is not seen: code that builds overhangs and reads the floor afterwards (structure
 * painting) scans with {@link DeepLayer#floorY} instead. Structures paint before anything reads this cache.
 */
public class DeepFloorPlacement extends PlacementModifier
{
    public static final DeepFloorPlacement INSTANCE = new DeepFloorPlacement();
    public static final Codec<DeepFloorPlacement> CODEC = Codec.unit(() -> INSTANCE);

    private static final int UNKNOWN = Integer.MIN_VALUE;
    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

    private DeepFloorPlacement() {}

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos)
    {
        int y = surface(context.getLevel(), pos.getX(), pos.getZ());
        return y > DeepLayer.MIN_Y + 1 ? Stream.of(new BlockPos(pos.getX(), y, pos.getZ())) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type()
    {
        return ModWorldgen.DEEP_FLOOR.get();
    }

    /** First open block above the deep seabed of this column, as the level stands now ({@link DeepLayer#floorY} + 1). */
    public static int surface(WorldGenLevel level, int x, int z)
    {
        Cache cache = CACHE.get();
        // One region (a chunk's decoration step) owns the cache; outside generation, the column's chunk does.
        Object owner = level instanceof WorldGenRegion ? level : level.getLevel();
        long chunk = level instanceof WorldGenRegion ? 0L : BlockPos.asLong(x >> 4, 0, z >> 4);
        if (cache.owner.get() != owner || cache.chunk != chunk)
        {
            cache.owner = new WeakReference<>(owner);
            cache.chunk = chunk;
            cache.floors.clear();
        }
        long column = (long) x << 32 | (z & 0xFFFFFFFFL);
        int y = cache.floors.get(column);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        if (y != UNKNOWN)
        {
            if (solid(level, pos.setY(y)))
            {
                // Something was built on the floor: its top is the floor now.
                while (y < DeepLayer.CEILING_BOTTOM_Y && solid(level, pos.setY(y))) y++;
                if (y >= DeepLayer.CEILING_BOTTOM_Y) y = UNKNOWN;
            }
            else if (!solid(level, pos.setY(y - 1))) y = UNKNOWN;  // the floor was dug away
        }
        if (y == UNKNOWN) y = DeepLayer.floorY(level, x, z) + 1;
        cache.floors.put(column, y);
        return y;
    }

    /** {@link #surface} in the deep layer (near {@code y}); the ocean world's floor heightmap above it. */
    public static int surface(WorldGenLevel level, int x, int z, int y)
    {
        return DeepLayer.isDeep(y) ? surface(level, x, z) : level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
    }

    /** A floor block, as {@link DeepLayer#floorY} counts it. */
    private static boolean solid(BlockGetter level, BlockPos pos)
    {
        return level.getBlockState(pos).blocksMotion();
    }

    private static final class Cache
    {
        WeakReference<Object> owner = new WeakReference<>(null);
        long chunk = Long.MIN_VALUE;
        final Long2IntOpenHashMap floors = new Long2IntOpenHashMap(1024);

        Cache()
        {
            floors.defaultReturnValue(UNKNOWN);
        }
    }
}
