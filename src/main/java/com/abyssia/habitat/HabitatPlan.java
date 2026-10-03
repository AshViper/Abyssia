package com.abyssia.habitat;

import com.abyssia.habitat.HabitatMode.Face;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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
    /** BT01a placement distance (blocks from the eye along the look vector): default, min, max */
    public static final int DEFAULT_DISTANCE = 6, MIN_DISTANCE = 3, MAX_DISTANCE = 12;
    /** hatch panels within this distance of the aim ray are snap candidates */
    public static final double SNAP_RANGE = 2.0;
    private static final int SNAP_TRIES = 3;

    public enum Problem { NONE, NOT_WATER, ENTITY, PERMISSION }

    /** Rotation index (Direction 2D data value: 0 south, 1 west, 2 north, 3 east; +1 = clockwise) to forward. */
    public static Direction facing(int rot)
    {
        return Direction.from2DDataValue(Math.floorMod(rot, 4));
    }

    public static int clampDistance(int distance)
    {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
    }

    /** {@link #plan(Player, HabitatMode, int, int, float)} at the default distance (test harness / old callers). */
    public static HabitatPlan plan(Player player, HabitatMode mode, int rot, float partialTick)
    {
        return plan(player, mode, rot, DEFAULT_DISTANCE, partialTick);
    }

    /**
     * BT01a placement: the aim point is eye + look x distance (3..12, block-snapped). A hatch hit on the way, or a
     * buildable hatch panel within {@link #SNAP_RANGE} of that segment, still wins (H02 snap). Otherwise the module's
     * near edge (towards the player) is at the aim point and its floor one block below it, so a level look keeps the
     * old feet-level floor. {@code rot} sets the forward direction except when snapped (the hatch decides).
     */
    public static HabitatPlan plan(Player player, HabitatMode mode, int rot, int distance, float partialTick)
    {
        Level level = player.level();
        Direction forward = facing(rot);
        double reach = clampDistance(distance);
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 end = eye.add(player.getViewVector(partialTick).scale(reach));
        HitResult hit = player.pick(reach, partialTick, false);
        if (hit instanceof BlockHitResult bhit && hit.getType() == HitResult.Type.BLOCK)
        {
            BlockPos pos = bhit.getBlockPos();
            if (level.getBlockState(pos).is(ModHabitat.HATCH.get())) return snap(level, mode, pos);
            // BT01c: a room aimed at the roof of a room stacks on it (same centre column, floor right on the roof)
            if (mode == HabitatMode.ROOM && bhit.getDirection() == Direction.UP)
            {
                HabitatPlan stacked = stackOnRoof(level, mode, pos, forward);
                if (stacked != null) return stacked;
            }
        }
        // snap candidates along the visible part of the ray (not through a wall that is hit first)
        Vec3 snapEnd = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
        HabitatPlan near = nearbySnap(level, player, mode, eye, snapEnd);
        if (near != null) return near;
        Direction look = player.getDirection();
        int along = look.getAxis() == forward.getAxis() ? mode.depth : mode.width;
        BlockPos centre = BlockPos.containing(end).below().relative(look, along / 2);
        return new HabitatPlan(mode, centre.relative(forward, -(mode.depth / 2)), forward, false);
    }

    /** Plan whose near connector faces the panel of this hatch block. */
    private static HabitatPlan snap(Level level, HabitatMode mode, BlockPos hatch)
    {
        Direction out = level.getBlockState(hatch).getValue(HabitatHatchBlock.FACING);
        return new HabitatPlan(mode, panelBase(level, hatch).relative(out).below(), out, true);
    }

    /** BT01c: height of a stacked room's floor above the lower room's floor (lower y0..y4, upper floor on the roof) */
    public static final int STACK_STEP = 5;
    /** BT01c: spacing of the 3 x 3 ceiling lights of a 13 x 13 module (HabitatLayout.light) */
    private static final int LIGHT_STEP = 4;

    /**
     * BT01c vertical stacking: {@code roof} is a ceiling / light block of a 13 x 13 ROOM (not a moon pool: its floor
     * centre is water) seen from above. The new room keeps {@code forward}, its floor centre sits right on the roof
     * centre (origin = lower origin + {@link #STACK_STEP}). The server also requires a registered 13 x 5 x 13 module
     * there (HabitatBases). Legs: the column below the new floor starts at the lower roof (not water), so legs() adds
     * none and nothing runs through the lower room.
     */
    @Nullable
    private static HabitatPlan stackOnRoof(Level level, HabitatMode mode, BlockPos roof, Direction forward)
    {
        BlockState state = level.getBlockState(roof);
        if (!state.is(ModHabitat.CEILING.get()) && !state.is(ModHabitat.LIGHT.get())) return null;
        BlockPos centre = roomCeilingCentre(level, roof);
        // the floor centre is floor (a moon pool has water there; a hatched middle room has a ladder: not water)
        if (centre == null || level.getBlockState(centre.below(STACK_STEP - 1)).getFluidState().is(FluidTags.WATER)) return null;
        if (level instanceof ServerLevel server && !registeredRoom(server, centre)) return null;
        return new HabitatPlan(mode, centre.above().relative(forward, -(mode.depth / 2)), forward, true);
    }

    /**
     * BT01c: the centre light of a 13 x 13 module ceiling in the layer of {@code near} (within 6 blocks): the light
     * whose 8 neighbours 4 apart are lights too (unique: merged neighbours' lights are 5 apart). Null when none.
     */
    @Nullable
    public static BlockPos roomCeilingCentre(BlockGetter level, BlockPos near)
    {
        int r = HabitatLayout.halfWidth(HabitatMode.ROOM);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
            {
                BlockPos c = near.offset(dx, 0, dz);
                if (isCentreLight(level, c)) return c;
            }
        return null;
    }

    /** BT01c: c is a light with lights 4 apart all around (the middle of a room's 3 x 3 light grid) */
    public static boolean isCentreLight(BlockGetter level, BlockPos c)
    {
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++)
                if (!level.getBlockState(c.offset(i * LIGHT_STEP, 0, j * LIGHT_STEP)).is(ModHabitat.LIGHT.get())) return false;
        return true;
    }

    /** BT01c server: a registered 13 x 5 x 13 module box contains this ceiling cell, centred on its column */
    public static boolean registeredRoom(ServerLevel level, BlockPos ceilingCentre)
    {
        HabitatBases data = HabitatBases.get(level);
        int id = data.moduleAt(ceilingCentre);
        if (id < 0) return false;
        HabitatBases.Module m = data.module(id);
        if (m == null || m.removed()) return false;
        BoundingBox b = m.box();
        int w = HabitatMode.ROOM.width, h = HabitatMode.ROOM.height;
        return b.getXSpan() == w && b.getZSpan() == w && b.getYSpan() == h && b.maxY() == ceilingCentre.getY()
                && b.minX() + w / 2 == ceilingCentre.getX() && b.minZ() + w / 2 == ceilingCentre.getZ();
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
