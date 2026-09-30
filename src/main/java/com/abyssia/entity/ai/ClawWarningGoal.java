package com.abyssia.entity.ai;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * A crustacean's warning: a player comes close, it stops, turns to face them and raises and snaps its claws; one who
 * keeps crowding it gets the animal's answer (a pinch, or a tail-flip escape).
 */
public class ClawWarningGoal extends Goal
{
    private final PathfinderMob mob;
    private final double range;
    private final Runnable display;
    private final Consumer<Player> crowded;
    @Nullable
    private Player player;
    private int ticks, closeTicks, cooldown;

    public ClawWarningGoal(PathfinderMob mob, double range, Runnable display, Consumer<Player> crowded)
    {
        this.mob = mob;
        this.range = range;
        this.display = display;
        this.crowded = crowded;
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
        if (this.cooldown > 0)
        {
            --this.cooldown;
            return false;
        }
        if (this.mob.getRandom().nextInt(4) != 0) return false;
        this.player = this.mob.level().getNearestPlayer(this.mob.getX(), this.mob.getY(), this.mob.getZ(), this.range,
                p -> !p.isSpectator() && p.isAlive());
        return this.player != null;
    }

    @Override
    public void start()
    {
        this.ticks = 0;
        this.closeTicks = 0;
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.player != null && this.player.isAlive() && this.ticks < 100 && this.mob.distanceToSqr(this.player) < (this.range + 1.5) * (this.range + 1.5);
    }

    @Override
    public void tick()
    {
        if (this.player == null) return;
        this.mob.getLookControl().setLookAt(this.player, 30.0F, 30.0F);
        if (this.ticks++ % 20 == 0) this.display.run();
        if (this.mob.distanceToSqr(this.player) < 1.4 * 1.4)
        {
            if (++this.closeTicks > 30)
            {
                this.crowded.accept(this.player);
                this.ticks = 100;
            }
        }
        else
        {
            this.closeTicks = Math.max(0, this.closeTicks - 1);
        }
    }

    @Override
    public void stop()
    {
        this.player = null;
        this.cooldown = 60 + this.mob.getRandom().nextInt(60);
    }
}
