package com.abyssia.entity.ai;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A loose swarm: an animal that drifted away from others of its kind swims back toward their middle. */
public class SchoolGoal extends Goal
{
    private final PathfinderMob mob;
    private final double radius;
    private final double speed;
    private final BooleanSupplier allowed;

    public SchoolGoal(PathfinderMob mob, double radius, double speed, BooleanSupplier allowed)
    {
        this.mob = mob;
        this.radius = radius;
        this.speed = speed;
        this.allowed = allowed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (this.mob.getRandom().nextInt(20) != 0 || !this.mob.getNavigation().isDone() || !this.allowed.getAsBoolean()) return false;
        List<PathfinderMob> mates = this.mob.level().getEntitiesOfClass(PathfinderMob.class, this.mob.getBoundingBox().inflate(this.radius),
                e -> e != this.mob && e.getType() == this.mob.getType() && e.isAlive());
        if (mates.isEmpty()) return false;
        Vec3 centre = Vec3.ZERO;
        for (PathfinderMob m : mates) centre = centre.add(m.position());
        centre = centre.scale(1.0 / mates.size());
        if (centre.distanceToSqr(this.mob.position()) < 3.0 * 3.0) return false;
        Vec3 to = centre.add(this.mob.getRandom().nextGaussian(), this.mob.getRandom().nextGaussian() * 0.5, this.mob.getRandom().nextGaussian());
        return this.mob.getNavigation().moveTo(to.x, to.y, to.z, this.speed);
    }

    @Override
    public boolean canContinueToUse()
    {
        return !this.mob.getNavigation().isDone() && this.allowed.getAsBoolean();
    }
}
