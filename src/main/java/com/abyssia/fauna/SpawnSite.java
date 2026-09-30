package com.abyssia.fauna;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * A sampled water position and what surrounds it: the seabed below, rock above, cover (under the seabed surface:
 * a cave or an overhang) and the blocks around. Short scans only - it is built for every spawn attempt.
 */
public final class SpawnSite
{
    private static final int FLOOR_SCAN = 32;
    private static final int CEILING_SCAN = 16;

    private final ServerLevel level;
    private final BlockPos pos;
    private final int floorY;
    private final int ceilingY;

    private SpawnSite(ServerLevel level, BlockPos pos, int floorY, int ceilingY)
    {
        this.level = level;
        this.pos = pos;
        this.floorY = floorY;
        this.ceilingY = ceilingY;
    }

    /** The site at a water block, or null when it is not open water. */
    @Nullable
    public static SpawnSite at(ServerLevel level, BlockPos pos)
    {
        if (!isWater(level, pos)) return null;
        BlockPos.MutableBlockPos p = pos.mutable();
        int floor = Integer.MIN_VALUE;
        for (int i = 1; i <= FLOOR_SCAN && p.getY() > level.getMinBuildHeight(); i++)
        {
            p.move(Direction.DOWN);
            if (!isWater(level, p))
            {
                floor = p.getY();
                break;
            }
        }
        p.set(pos);
        int ceiling = Integer.MAX_VALUE;
        for (int i = 1; i <= CEILING_SCAN && p.getY() < level.getMaxBuildHeight() - 1; i++)
        {
            p.move(Direction.UP);
            if (!isWater(level, p))
            {
                ceiling = p.getY();
                break;
            }
        }
        return new SpawnSite(level, pos.immutable(), floor, ceiling);
    }

    /** Water an animal can occupy: a water fluid and nothing solid (plants and kelp count as water). */
    public static boolean isWater(ServerLevel level, BlockPos pos)
    {
        return level.getFluidState(pos).is(FluidTags.WATER) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    public ServerLevel level()
    {
        return this.level;
    }

    public BlockPos pos()
    {
        return this.pos;
    }

    public boolean hasFloor()
    {
        return this.floorY != Integer.MIN_VALUE;
    }

    /** Water blocks between the sampled position and the seabed (0: resting on it); large when none was found. */
    public int floorDistance()
    {
        return this.hasFloor() ? this.pos.getY() - this.floorY - 1 : FLOOR_SCAN;
    }

    public int ceilingDistance()
    {
        return this.ceilingY == Integer.MAX_VALUE ? CEILING_SCAN : this.ceilingY - this.pos.getY() - 1;
    }

    /** The water block resting on the seabed below the sampled position. */
    public BlockPos floorPos()
    {
        return new BlockPos(this.pos.getX(), this.floorY + 1, this.pos.getZ());
    }

    /** The seabed below can be stood on (not a plant, slab edge or other thin block). */
    public boolean solidFloor()
    {
        BlockPos floor = new BlockPos(this.pos.getX(), this.floorY, this.pos.getZ());
        return this.level.getBlockState(floor).isFaceSturdy(this.level, floor, Direction.UP);
    }

    /**
     * Under rock: below the seabed surface of its column, i.e. in a cave or under an overhang. Sea ice (a frozen
     * ocean's sheet, icebergs) is not a roof: the ice and the water under it are skipped.
     */
    /** Whether any block within {@code radius} (a full cube scan, stopping at the first match) passes {@code test}. */
    public boolean any(BlockPos around, int radius, Predicate<BlockState> test)
    {
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-radius, -radius, -radius), around.offset(radius, radius, radius)))
        {
            if (test.test(this.level.getBlockState(p))) return true;
        }
        return false;
    }

        /** Open water at least {@code distance} blocks in all six directions (room for a large animal). */
    public boolean clear(BlockPos p, int distance)
    {
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values())
        {
            q.set(p);
            for (int i = 1; i <= distance; i++)
            {
                if (!isWater(this.level, q.move(d))) return false;
            }
        }
        return true;
    }

        public boolean covered(BlockPos p)
    {
        BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos(p.getX(), this.level.getHeight(Heightmap.Types.OCEAN_FLOOR, p.getX(), p.getZ()) - 1, p.getZ());
        while (top.getY() > p.getY() && this.level.getBlockState(top).is(BlockTags.ICE))
        {
            top.move(Direction.DOWN);
            while (top.getY() > p.getY() && isWater(this.level, top)) top.move(Direction.DOWN);
        }
        return p.getY() < top.getY();
    }

    @Nullable
    public BlockPos waterAt(BlockPos p)
    {
        return isWater(this.level, p) ? p : null;
    }

    /** How many of the 27 points of a cube of half-size {@code radius} around {@code around} pass the test. */
    public int count(BlockPos around, int radius, Predicate<BlockState> test)
    {
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx += radius)
        {
            for (int dy = -radius; dy <= radius; dy += radius)
            {
                for (int dz = -radius; dz <= radius; dz += radius)
                {
                    if (test.test(this.level.getBlockState(p.setWithOffset(around, dx, dy, dz)))) n++;
                }
            }
        }
        return n;
    }
}
