package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Slateback (neutral): calm until hit, then a 40 tick ram at double speed with a strong knockback. */
final class SlatebackAbility extends GiantAbility
{
    SlatebackAbility(GiantFauna g) { super(g); }

    @Override
    public int warnTicks() { return 20; }

    @Override
    public int chaseMax() { return 60; }

    @Override
    public double speedMul(GiantBrain.State s, int stateTicks, @Nullable Entity target)
    {
        return s == GiantBrain.State.CHASE && stateTicks < 40 ? 2.0 : 1.0;
    }

    @Override
    public double knockback() { return 1.5; }
}
