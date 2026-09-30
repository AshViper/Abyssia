package com.abyssia.entity.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/** Hanging in the water: now and then a slow turn to a new heading, as a drifting fish does. */
public class SlowTurnGoal extends Goal
{
    private final Mob mob;
    private final int chance;
    private final float degreesPerTick;
    private final BooleanSupplier allowed;
    private float targetYaw;
    private int ticks;

    public SlowTurnGoal(Mob mob, int chance, float degreesPerTick, BooleanSupplier allowed)
    {
        this.mob = mob;
        this.chance = chance;
        this.degreesPerTick = degreesPerTick;
        this.allowed = allowed;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public boolean canUse()
    {
        if (!this.mob.getNavigation().isDone() || this.mob.getRandom().nextInt(this.chance) != 0 || !this.allowed.getAsBoolean()) return false;
        this.targetYaw = this.mob.getYRot() + (this.mob.getRandom().nextFloat() - 0.5F) * 120.0F;
        this.ticks = 0;
        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        return ++this.ticks < 80 && this.mob.getNavigation().isDone() && this.allowed.getAsBoolean();
    }

    @Override
    public void tick()
    {
        float rot = Mth.approachDegrees(this.mob.getYRot(), this.targetYaw, this.degreesPerTick);
        this.mob.setYRot(rot);
        this.mob.yBodyRot = rot;
        this.mob.yHeadRot = rot;
    }
}
