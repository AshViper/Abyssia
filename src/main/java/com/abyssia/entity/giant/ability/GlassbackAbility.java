package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Glassback: dark while it hunts from afar; within 16 blocks of the prey it lights up and bites in a 40 tick rush at 2.2x speed. */
final class GlassbackAbility extends GiantAbility
{
    static final double FLARE = 16;
    static final int RUSH = 40;
    private boolean flared;
    private long rushUntil;

    GlassbackAbility(GiantFauna g) { super(g); }

    @Override
    public void tick(long now, GiantBrain.State state, @Nullable Entity target)
    {
        if (state != GiantBrain.State.CHASE || target == null)
        {
            flared = false;
            g.setGlow(true);
            return;
        }
        if (g.headPos().distanceToSqr(center(target)) > FLARE * FLARE)
        {
            flared = false;
            g.setGlow(false);
        }
        else if (!flared)
        {
            flared = true;
            rushUntil = now + RUSH;
            g.setGlow(true);
        }
    }

    @Override
    public double speedMul(GiantBrain.State s, int stateTicks, @Nullable Entity target)
    {
        return s == GiantBrain.State.CHASE && g.level().getGameTime() < rushUntil ? 2.2 : 1.0;
    }
}
