package com.abyssia.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * Neutral animals: whoever hurt it is chased and bitten for a while, then forgotten. Nothing is attacked unprovoked.
 */
public class RetaliateGoal extends Goal
{
    private final PathfinderMob mob;
    private final double speed;
    private final double reach;
    private final int memory;
    private final Consumer<LivingEntity> bite;
    @Nullable
    private LivingEntity target;
    private int cooldown, repath;

    /**
     * @param reach  bite range beyond the two hitboxes' half widths (blocks)
     * @param memory ticks after the last blow it keeps after the attacker
     * @param bite   the bite itself (damage, sound, animation)
     */
    public RetaliateGoal(PathfinderMob mob, double speed, double reach, int memory, Consumer<LivingEntity> bite)
    {
        this.mob = mob;
        this.speed = speed;
        this.reach = reach;
        this.memory = memory;
        this.bite = bite;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    private boolean valid(@Nullable LivingEntity e)
    {
        if (e == null || !e.isAlive() || e.distanceToSqr(this.mob) > 20 * 20) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return this.mob.tickCount - this.mob.getLastHurtByMobTimestamp() < this.memory;
    }

    @Override
    public boolean canUse()
    {
        LivingEntity attacker = this.mob.getLastHurtByMob();
        if (!this.valid(attacker)) return false;
        this.target = attacker;
        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.valid(this.target);
    }

    @Override
    public void start()
    {
        this.cooldown = 10;
        this.repath = 0;
    }

    @Override
    public void tick()
    {
        if (this.target == null) return;
        this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
        if (--this.repath <= 0)
        {
            this.repath = 10;
            this.mob.getNavigation().moveTo(this.target, this.speed);
        }
        double range = this.reach + (this.mob.getBbWidth() + this.target.getBbWidth()) * 0.5;
        if (--this.cooldown <= 0 && this.mob.distanceToSqr(this.target) < range * range && this.mob.hasLineOfSight(this.target))
        {
            this.cooldown = 25 + this.mob.getRandom().nextInt(15);
            this.bite.accept(this.target);
        }
    }

    @Override
    public void stop()
    {
        this.target = null;
        this.mob.getNavigation().stop();
    }
}
