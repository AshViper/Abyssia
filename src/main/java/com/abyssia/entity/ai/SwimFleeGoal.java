package com.abyssia.entity.ai;

import com.abyssia.entity.DeepSeaSwimmer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** While the swimmer is fleeing ({@link DeepSeaSwimmer#startFleeing}), it swims hard away from the threat. */
public class SwimFleeGoal extends Goal
{
    private final DeepSeaSwimmer fish;
    private final double speed;
    private final int distance;

    public SwimFleeGoal(DeepSeaSwimmer fish, double speed, int distance)
    {
        this.fish = fish;
        this.speed = speed;
        this.distance = distance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public boolean canUse()
    {
        return this.fish.isFleeing() && this.fish.isInWater();
    }

    @Override
    public void start()
    {
        this.flee();
    }

    @Override
    public void tick()
    {
        if (this.fish.isFleeing() && this.fish.getNavigation().isDone()) this.flee();
    }

    private void flee()
    {
        Vec3 from = this.fish.fleeFrom();
        Vec3 to = DefaultRandomPos.getPosAway(this.fish, this.distance, Math.max(2, this.distance / 2), from);
        if (to != null && this.fish.level().getFluidState(BlockPos.containing(to)).is(FluidTags.WATER))
        {
            this.fish.getNavigation().moveTo(to.x, to.y, to.z, this.speed);
        }
    }

    @Override
    public void stop()
    {
        this.fish.getNavigation().stop();
    }
}
