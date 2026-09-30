package com.abyssia.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * LandRandomPos for the seabed. Vanilla's movePosUpOutOfSolid turns down every spot whose block holds water, so under
 * water LandRandomPos always returns null and a walker never strolls or backs off. These keep its other rules (a
 * standable spot inside the mob's limits and restriction, lifted out of rock, no path malus) and drop that one.
 */
public final class SeabedRandomPos
{
    private SeabedRandomPos() {}

    /** A random walkable spot within the given ranges, the mob's walk target value picking among the tries. */
    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int horizontal, int vertical)
    {
        boolean restricted = GoalUtils.mobRestricted(mob, horizontal);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos dir = RandomPos.generateRandomDirection(mob.getRandom(), horizontal, vertical);
            return liftOutOfSolid(mob, LandRandomPos.generateRandomPosTowardDirection(mob, horizontal, restricted, dir));
        });
    }

    /** A random walkable spot within 90 degrees either side of the direction away from {@code from}. */
    @Nullable
    public static Vec3 getPosAway(PathfinderMob mob, int horizontal, int vertical, Vec3 from)
    {
        Vec3 away = mob.position().subtract(from);
        boolean restricted = GoalUtils.mobRestricted(mob, horizontal);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos dir = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), horizontal, vertical, 0, away.x, away.z, Math.PI / 2);
            return dir == null ? null : liftOutOfSolid(mob, LandRandomPos.generateRandomPosTowardDirection(mob, horizontal, restricted, dir));
        });
    }

    @Nullable
    private static BlockPos liftOutOfSolid(PathfinderMob mob, @Nullable BlockPos pos)
    {
        if (pos == null) return null;
        pos = RandomPos.moveUpOutOfSolid(pos, mob.level().getMaxBuildHeight(), p -> GoalUtils.isSolid(mob, p));
        return GoalUtils.hasMalus(mob, pos) ? null : pos;
    }
}
