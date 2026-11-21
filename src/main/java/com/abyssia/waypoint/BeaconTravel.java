package com.abyssia.waypoint;

import com.abyssia.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** QOL01: travel between two of your own waypoint beacons in the same dimension; the departure beacon pays FE. */
public final class BeaconTravel
{
    public enum Result { OK, TOO_FAR, NOT_OWNER, NO_ENERGY, DEST_UNPOWERED, COOLDOWN, RIDING, BLOCKED, DISABLED }

    public static final String TAG = "abyssia_qol01";
    public static final String KEY_UNTIL = "travel_until";
    private static final double REACH_SQR = 4.0 * 4.0;

    private BeaconTravel() {}

    /** FE the departure beacon pays for a trip between the two positions */
    public static int cost(BlockPos from, BlockPos to)
    {
        double dx = from.getX() - to.getX(), dz = from.getZ() - to.getZ();
        long horizontal = Math.round(Math.sqrt(dx * dx + dz * dz));
        return (int) Math.min(Integer.MAX_VALUE, Config.QOL01_TRAVEL_BASE_COST.get() + horizontal * Config.QOL01_TRAVEL_COST_PER_BLOCK.get());
    }

    public static Result travel(ServerPlayer player, BlockPos from, BlockPos to)
    {
        if (!Config.QOL01_TRAVEL_ENABLED.get()) return Result.DISABLED;
        if (player.isPassenger() || player.isVehicle() || player.isSleeping() || player.isSpectator() || !player.isAlive()) return Result.RIDING;
        ServerLevel level = player.serverLevel();
        if (from.equals(to)) return Result.BLOCKED;
        if (!level.isLoaded(from) || player.position().distanceToSqr(Vec3.atCenterOf(from)) > REACH_SQR) return Result.TOO_FAR;
        WaypointRegistry reg = WaypointRegistry.get(level);
        UUID id = player.getUUID();
        WaypointEntry a = reg.entry(from), b = reg.entry(to);
        if (a == null || b == null || !id.equals(a.owner()) || !id.equals(b.owner())) return Result.NOT_OWNER;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        long now = level.getGameTime();
        if (now < persisted.getCompound(TAG).getLong(KEY_UNTIL)) return Result.COOLDOWN;
        if (!(level.getBlockEntity(from) instanceof WaypointBeaconBlockEntity src)) return Result.NOT_OWNER;
        int cost = cost(from, to);
        if (src.energy().getEnergyStored() < cost) return Result.NO_ENERGY;
        level.getChunk(to.getX() >> 4, to.getZ() >> 4);        // load the destination before looking at it
        if (!(level.getBlockEntity(to) instanceof WaypointBeaconBlockEntity dst))
            return Result.NOT_OWNER;
        if (dst.energy().getEnergyStored() < Config.QOL01_TRAVEL_DEST_MIN.get()) return Result.DEST_UNPOWERED;
        Vec3 spot = landing(level, to);
        if (spot == null) return Result.BLOCKED;

        src.energy().consume(cost);
        CompoundTag mine = persisted.getCompound(TAG);
        mine.putLong(KEY_UNTIL, now + Config.QOL01_TRAVEL_COOLDOWN.get());
        persisted.put(TAG, mine);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        player.fallDistance = 0;
        player.teleportTo(level, spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        return Result.OK;
    }

    /** where a player arrives at the beacon `to` (feet position, block centre), or null when every spot is blocked */
    public static Vec3 landing(ServerLevel level, BlockPos to)
    {
        BlockPos spot = findSpot(level, to);
        return spot == null ? null : new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
    }

    /** feet position one block above the beacon with two free blocks over it (water allowed); else a horizontal neighbour */
    private static BlockPos findSpot(ServerLevel level, BlockPos beacon)
    {
        if (free(level, beacon.above())) return beacon.above();
        for (Direction d : Direction.Plane.HORIZONTAL)
        {
            BlockPos p = beacon.relative(d).above();
            if (free(level, p)) return p;
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos feet)
    {
        return passable(level, feet) && passable(level, feet.above());
    }

    private static boolean passable(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && !state.getFluidState().is(FluidTags.LAVA);
    }
}
