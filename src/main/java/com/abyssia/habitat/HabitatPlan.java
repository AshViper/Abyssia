package com.abyssia.habitat;

import com.abyssia.habitat.HabitatMode.Face;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a module goes: {@code origin} is local (0, 0, 0) (near face centre, floor level), {@code forward} the
 * horizontal direction of local +z, {@code snapped} when it was aligned to a hatch panel. Shared by the server
 * (build) and the client (hologram).
 */
public record HabitatPlan(HabitatMode mode, BlockPos origin, Direction forward, boolean snapped)
{
    public static final double REACH = 6.0;
    /** hatch panels within this distance of the aim ray are snap candidates */
    public static final double SNAP_RANGE = 2.0;
    private static final int SNAP_TRIES = 3;

    public enum Problem { NONE, NOT_WATER, ENTITY, PERMISSION }

    /** Rotation index (Direction 2D data value: 0 south, 1 west, 2 north, 3 east; +1 = clockwise) to forward. */
    public static Direction facing(int rot)
    {
        return Direction.from2DDataValue(Math.floorMod(rot, 4));
    }

    /**
     * H02 placement: snap to the aimed hatch panel (or a buildable one within {@link #SNAP_RANGE} of the aim ray),
     * else centred on the block above an aimed top face, else 2 blocks ahead of the player (floor = feet y).
     * {@code rot} sets the forward direction except when snapped (the hatch decides).
     */
    public static HabitatPlan plan(Player player, HabitatMode mode, int rot, float partialTick)
    {
        Level level = player.level();
        Direction forward = facing(rot);
        Vec3 eye = player.getEyePosition(partialTick);
        HitResult hit = player.pick(REACH, partialTick, false);
        Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(player.getViewVector(partialTick).scale(REACH)) : hit.getLocation();
        if (hit instanceof BlockHitResult bhit && hit.getType() == HitResult.Type.BLOCK)
        {
            BlockPos pos = bhit.getBlockPos();
            if (level.getBlockState(pos).is(ModHabitat.HATCH.get())) return snap(level, mode, pos);
        }
        HabitatPlan near = nearbySnap(level, player, mode, eye, end);
        if (near != null) return near;
        if (hit instanceof BlockHitResult bhit && hit.getType() == HitResult.Type.BLOCK && bhit.getDirection() == Direction.UP)
        {
            BlockPos centre = bhit.getBlockPos().above();
            return new HabitatPlan(mode, centre.relative(forward, -(mode.depth / 2)), forward, false);
        }
        Direction look = player.getDirection();
        int along = look.getAxis() == forward.getAxis() ? mode.depth : mode.width;
        BlockPos centre = player.blockPosition().relative(look, 2 + along / 2);
        return new HabitatPlan(mode, centre.relative(forward, -(mode.depth / 2)), forward, false);
    }

    /** Plan whose near connector faces the panel of this hatch block. */
    private static HabitatPlan snap(Level level, HabitatMode mode, BlockPos hatch)
    {
        Direction out = level.getBlockState(hatch).getValue(HabitatHatchBlock.FACING);
        return new HabitatPlan(mode, panelBase(level, hatch).relative(out).below(), out, true);
    }

    /** Bottom centre of the hatch panel containing {@code pos}: down while hatch, then to the middle of the 3 wide row. */
    public static BlockPos panelBase(Level level, BlockPos pos)
    {
        Direction out = level.getBlockState(pos).getValue(HabitatHatchBlock.FACING);
        BlockPos low = pos;
        while (level.getBlockState(low.below()).is(ModHabitat.HATCH.get())) low = low.below();
        Direction side = out.getClockWise();
        int a = 0, b = 0;
        while (a < 2 && level.getBlockState(low.relative(side, a + 1)).is(ModHabitat.HATCH.get())) a++;
        while (b < 2 && level.getBlockState(low.relative(side, -(b + 1))).is(ModHabitat.HATCH.get())) b++;
        return low.relative(side, (a - b) / 2);
    }

    /** Closest hatch panels to the aim ray (within SNAP_RANGE); the first whose plan is buildable wins. */
    private static HabitatPlan nearbySnap(Level level, Player player, HabitatMode mode, Vec3 eye, Vec3 end)
    {
        int r = (int) Math.ceil(SNAP_RANGE);
        BlockPos min = BlockPos.containing(Math.min(eye.x, end.x) - r, Math.min(eye.y, end.y) - r, Math.min(eye.z, end.z) - r);
        BlockPos max = BlockPos.containing(Math.max(eye.x, end.x) + r, Math.max(eye.y, end.y) + r, Math.max(eye.z, end.z) + r);
        Map<BlockPos, Double> panels = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max))
        {
            if (!level.getBlockState(pos).is(ModHabitat.HATCH.get())) continue;
            double d = distanceToSegment(Vec3.atCenterOf(pos), eye, end);
            if (d > SNAP_RANGE) continue;
            panels.merge(panelBase(level, pos.immutable()), d, Math::min);
        }
        List<Map.Entry<BlockPos, Double>> sorted = new ArrayList<>(panels.entrySet());
        sorted.sort(Map.Entry.comparingByValue());
        ItemStack stack = player.getMainHandItem();
        for (int i = 0; i < Math.min(SNAP_TRIES, sorted.size()); i++)
        {
            HabitatPlan plan = snap(level, mode, sorted.get(i).getKey());
            if (plan.check(level, player, stack) == Problem.NONE) return plan;
        }
        return null;
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b)
    {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1.0e-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    public Direction right()
    {
        return forward.getClockWise();
    }

    public BlockPos at(int x, int y, int z)
    {
        return origin.relative(forward, z).relative(right(), x).above(y);
    }

    public Direction outward(Face face)
    {
        return switch (face)
        {
            case NEAR -> forward.getOpposite();
            case FAR -> forward;
            case LEFT -> right().getOpposite();
            case RIGHT -> right();
        };
    }

    public AABB box()
    {
        int hw = HabitatLayout.halfWidth(mode);
        BlockPos a = at(-hw, 0, 0);
        BlockPos b = at(hw, mode.height - 1, mode.depth - 1);
        return new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, b.getY() + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    public List<BlockPos> cells()
    {
        int hw = HabitatLayout.halfWidth(mode);
        List<BlockPos> out = new ArrayList<>(mode.width * mode.depth * mode.height);
        for (int y = 0; y < mode.height; y++)
            for (int z = 0; z < mode.depth; z++)
                for (int x = -hw; x <= hw; x++) out.add(at(x, y, z));
        return out;
    }

    /** Local x / z of the cells of a connector panel (3 wide along the face, y1..3 not included). */
    public static int[][] panelColumns(HabitatMode mode, Face face)
    {
        int fx = HabitatLayout.faceX(mode, face), fz = HabitatLayout.faceZ(mode, face);
        boolean alongX = face == Face.NEAR || face == Face.FAR;
        int[][] out = new int[HabitatLayout.PANEL][];
        for (int i = 0; i < HabitatLayout.PANEL; i++)
        {
            int d = i - HabitatLayout.PANEL / 2;
            out[i] = alongX ? new int[]{fx + d, fz} : new int[]{fx, fz + d};
        }
        return out;
    }

    /** Wall cell of a face at {@code lateral} from the face centre (along the face) and height y. */
    public BlockPos faceCell(Face face, int lateral, int y)
    {
        int fx = HabitatLayout.faceX(mode, face), fz = HabitatLayout.faceZ(mode, face);
        boolean alongX = face == Face.NEAR || face == Face.FAR;
        return alongX ? at(fx + lateral, y, fz) : at(fx, y, fz + lateral);
    }

    /** World box of each connector panel (3 x 3, y1..3). */
    public List<AABB> connectorPanels()
    {
        List<AABB> out = new ArrayList<>();
        for (Face face : mode.connectors)
        {
            int[][] cols = panelColumns(mode, face);
            BlockPos a = at(cols[0][0], 1, cols[0][1]);
            BlockPos b = at(cols[cols.length - 1][0], 3, cols[cols.length - 1][1]);
            out.add(new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()) + 1, b.getY() + 1, Math.max(a.getZ(), b.getZ()) + 1));
        }
        return out;
    }

    public Problem check(Level level, Player player, ItemStack stack)
    {
        for (BlockPos pos : cells())
        {
            if (!replaceable(level.getBlockState(pos))) return Problem.NOT_WATER;
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, stack)) return Problem.PERMISSION;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, box(), LivingEntity::isAlive).isEmpty()) return Problem.ENTITY;
        return Problem.NONE;
    }

    /** Water (source or flowing), or a replaceable / kelp plant standing in water. */
    public static boolean replaceable(BlockState state)
    {
        if (state.is(Blocks.WATER)) return true;
        if (!state.getFluidState().is(FluidTags.WATER)) return false;
        return state.canBeReplaced() || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
    }
}
