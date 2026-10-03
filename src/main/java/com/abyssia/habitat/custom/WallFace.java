package com.abyssia.habitat.custom;

import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.build.BuildChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * BT01e: one 13-wide wall face of a room / moon pool. {@code centre} = the face's middle cell at floor level (y0),
 * {@code along} = lateral +, {@code inward} = into the module. Cells: lateral -6..6 (±6 = corner pillars), y0..4.
 * Server: found from the HabitatBases box behind the aimed wall cell (exact). Client: from the blocks (floor / ceiling
 * shell 4 apart on the inner side, corner pillars where the inner neighbour is shell; merged rows of 13-wide modules
 * are split every 13 cells).
 */
public record WallFace(BlockPos centre, Direction along, Direction inward)
{
    public static final int HALF = 6;
    private static final int WIDTH = 13, HEIGHT = 5, MAX_WALK = WIDTH * 4;

    public BlockPos cell(int lateral, int y)
    {
        return centre.relative(along, lateral).above(y);
    }

    /** the whole face, corners and floor / ceiling rows included */
    public AABB box()
    {
        BlockPos a = cell(-HALF, 0), b = cell(HALF, HEIGHT - 1);
        return new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, b.getY() + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    /** the module box behind this face (server), or null */
    @Nullable
    public BoundingBox module(Level level)
    {
        return BuildChecks.moduleInterior(level, centre.relative(inward).above(2));
    }

    /** The face of the wall cell the player looks at (rows y1..3, not a corner), or null. */
    @Nullable
    public static WallFace aimed(Player player, double reach, float partialTick)
    {
        HitResult hit = player.pick(reach, partialTick, false);
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK) return null;
        Direction face = b.getDirection();
        if (face.getAxis().isVertical()) return null;
        BlockPos pos = b.getBlockPos();
        Level level = player.level();
        if (!HabitatBuilder.shell(level.getBlockState(pos))) return null;
        return level instanceof ServerLevel server ? fromBases(server, pos, face) : fromBlocks(level, pos, face);
    }

    @Nullable
    private static WallFace fromBases(ServerLevel level, BlockPos pos, Direction face)
    {
        for (Direction in : new Direction[]{face, face.getOpposite()})
        {
            BoundingBox b = BuildChecks.moduleInterior(level, pos.relative(in));
            if (b == null || !b.isInside(pos)) continue;
            Direction.Axis axis = in.getAxis();
            int coord = axis.choose(pos.getX(), pos.getY(), pos.getZ());
            int boundary = in.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? axis.choose(b.minX(), b.minY(), b.minZ()) : axis.choose(b.maxX(), b.maxY(), b.maxZ());
            if (coord != boundary) continue;
            Direction along = in.getClockWise();
            boolean alongX = along.getAxis() == Direction.Axis.X;
            int span = alongX ? b.getXSpan() : b.getZSpan();
            if (span != WIDTH || b.getYSpan() != HEIGHT) return null;
            if (pos.getY() < b.minY() + 1 || pos.getY() > b.minY() + 3) return null;
            int mid = alongX ? (b.minX() + b.maxX()) / 2 : (b.minZ() + b.maxZ()) / 2;
            BlockPos centre = alongX ? new BlockPos(mid, b.minY(), pos.getZ()) : new BlockPos(pos.getX(), b.minY(), mid);
            return new WallFace(centre, along, in);
        }
        return null;
    }

    @Nullable
    private static WallFace fromBlocks(Level level, BlockPos pos, Direction face)
    {
        for (Direction in : new Direction[]{face, face.getOpposite()})
        {
            BlockPos inner = pos.relative(in);
            BlockState s = level.getBlockState(inner);
            if (HabitatBuilder.shell(s) || !s.getFluidState().isEmpty()) continue;
            int floor = Integer.MIN_VALUE, ceil = Integer.MIN_VALUE;
            for (int k = 1; k <= 3 && floor == Integer.MIN_VALUE; k++)
                if (HabitatBuilder.shell(level.getBlockState(inner.below(k)))) floor = inner.getY() - k;
            for (int k = 1; k <= 3 && ceil == Integer.MIN_VALUE; k++)
                if (HabitatBuilder.shell(level.getBlockState(inner.above(k)))) ceil = inner.getY() + k;
            if (floor == Integer.MIN_VALUE || ceil == Integer.MIN_VALUE || ceil - floor != HEIGHT - 1) continue;
            Direction along = in.getClockWise();
            BlockPos start = new BlockPos(pos.getX(), floor + 1, pos.getZ());
            int plus = corner(level, start, along, in), minus = corner(level, start, along.getOpposite(), in);
            if (plus < 0 || minus < 0) return null;
            int width = plus + minus + 1;
            if (width < WIDTH || width % WIDTH != 0) return null;
            int seg = Math.min(minus / WIDTH, width / WIDTH - 1);
            BlockPos c = start.relative(along, -minus + seg * WIDTH + HALF);
            return new WallFace(new BlockPos(c.getX(), floor, c.getZ()), along, in);
        }
        return null;
    }

    /** cells from start to the corner pillar (inner neighbour is shell) in dir; -1 = water / no corner */
    private static int corner(Level level, BlockPos start, Direction dir, Direction in)
    {
        for (int k = 1; k <= MAX_WALK; k++)
        {
            BlockPos c = start.relative(dir, k);
            BlockState wall = level.getBlockState(c);
            if (HabitatBuilder.shell(level.getBlockState(c.relative(in)))) return HabitatBuilder.shell(wall) ? k : -1;
            if (!wall.getFluidState().isEmpty()) return -1;
        }
        return -1;
    }
}
