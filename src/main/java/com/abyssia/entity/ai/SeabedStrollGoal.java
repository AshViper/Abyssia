package com.abyssia.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import java.util.function.ToDoubleFunction;

/** A walker's wander over the seabed: of a few spots it could walk to, the best scored (plus a little chance) wins. */
public class SeabedStrollGoal extends Goal
{
    private final PathfinderMob mob;
    private final int chance;
    private final double speed;
    private final int horizontal, vertical;
    private final BooleanSupplier allowed;
    private final ToDoubleFunction<BlockPos> score;
    private int ticks;

    public SeabedStrollGoal(PathfinderMob mob, int chance, double speed, int horizontal, int vertical, BooleanSupplier allowed,
                            ToDoubleFunction<BlockPos> score)
    {
        this.mob = mob;
        this.chance = chance;
        this.speed = speed;
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.allowed = allowed;
        this.score = score;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (!this.mob.getNavigation().isDone() || this.mob.getRandom().nextInt(this.chance) != 0 || !this.allowed.getAsBoolean()) return false;
        Vec3 best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < 5; i++)
        {
            Vec3 p = SeabedRandomPos.getPos(this.mob, this.horizontal, this.vertical);
            if (p == null) continue;
            double s = this.score.applyAsDouble(BlockPos.containing(p)) + this.mob.getRandom().nextDouble();
            if (s > bestScore)
            {
                best = p;
                bestScore = s;
            }
        }
        this.ticks = 0;
        return best != null && this.mob.getNavigation().moveTo(best.x, best.y, best.z, this.speed);
    }

    @Override
    public boolean canContinueToUse()
    {
        return !this.mob.getNavigation().isDone() && ++this.ticks < 400 && this.allowed.getAsBoolean();
    }

    @Override
    public void stop()
    {
        this.mob.getNavigation().stop();
    }
}
