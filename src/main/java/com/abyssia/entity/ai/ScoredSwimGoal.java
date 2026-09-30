package com.abyssia.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import java.util.function.ToDoubleFunction;

/**
 * Wandering with a preference: now and then a few random water spots nearby are scored (darkness for an ambush
 * predator, enclosure for a cave dweller, depth for a migrator...) and the animal swims to the best one.
 */
public class ScoredSwimGoal extends Goal
{
    private final PathfinderMob mob;
    private final int chance;
    private final double speed;
    private final int horizontal, vertical;
    private final int maxTicks;
    private final BooleanSupplier allowed;
    private final ToDoubleFunction<BlockPos> score;
    private int ticks;

    /**
     * @param chance one in {@code chance} checks (every other tick) picks a new spot
     * @param score  higher is better; a little noise is added so the choice is not always the same
     */
    public ScoredSwimGoal(PathfinderMob mob, int chance, double speed, int horizontal, int vertical, int maxTicks, BooleanSupplier allowed,
                          ToDoubleFunction<BlockPos> score)
    {
        this.mob = mob;
        this.chance = chance;
        this.speed = speed;
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.maxTicks = maxTicks;
        this.allowed = allowed;
        this.score = score;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (!this.mob.isInWater() || !this.mob.getNavigation().isDone() || this.mob.getRandom().nextInt(this.chance) != 0 || !this.allowed.getAsBoolean())
        {
            return false;
        }
        Vec3 best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++)
        {
            Vec3 p = BehaviorUtils.getRandomSwimmablePos(this.mob, this.horizontal, this.vertical);
            if (p == null) continue;
            BlockPos pos = BlockPos.containing(p);
            if (!this.mob.level().getFluidState(pos).is(FluidTags.WATER)) continue;
            double s = this.score.applyAsDouble(pos) + this.mob.getRandom().nextDouble() * 2.0;
            if (s > bestScore)
            {
                best = p;
                bestScore = s;
            }
        }
        if (best == null) return false;
        this.ticks = 0;
        return this.mob.getNavigation().moveTo(best.x, best.y, best.z, this.speed);
    }

    @Override
    public boolean canContinueToUse()
    {
        return !this.mob.getNavigation().isDone() && ++this.ticks < this.maxTicks && this.allowed.getAsBoolean();
    }

    @Override
    public void stop()
    {
        this.mob.getNavigation().stop();
    }

    /** How many of the six directions hit rock within 6 blocks: a cave passage scores high, open water 0. */
    public static int enclosure(Level level, BlockPos pos)
    {
        int walls = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values())
        {
            p.set(pos);
            for (int i = 1; i <= 6; i++)
            {
                p.move(d);
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty())
                {
                    walls++;
                    break;
                }
            }
        }
        return walls;
    }

    /** Blocks of water below {@code pos} down to the first solid block ({@code max} when there is none within reach). */
    public static int floorDistance(Level level, BlockPos pos, int max)
    {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 1; i <= max; i++)
        {
            p.move(Direction.DOWN);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return i - 1;
        }
        return max;
    }

        /** Brighter spots score lower (light level 0..15). */
    public static double darkness(Level level, BlockPos pos)
    {
        return -level.getMaxLocalRawBrightness(pos);
    }
}
