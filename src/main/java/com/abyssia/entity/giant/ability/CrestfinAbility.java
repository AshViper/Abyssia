package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/** Crestfin (neutral): when hit it raises its crest for 60 ticks (hold crest_up), then rams for 40 ticks; same species within 32 blocks join in. */
final class CrestfinAbility extends GiantAbility
{
    CrestfinAbility(GiantFauna g) { super(g); }

    @Override
    public int warnTicks() { return 60; }

    @Override
    public int chaseMax() { return 100; }

    @Override
    public boolean holding(GiantBrain.State s) { return s == GiantBrain.State.WARN || s == GiantBrain.State.CHASE; }

    @Override
    public void onState(GiantBrain.State from, GiantBrain.State to, @Nullable Entity target)
    {
        if (to != GiantBrain.State.WARN || target == null) return;
        long now = g.level().getGameTime();
        AABB box = g.getBoundingBox().inflate(32);
        // an animal already warned or chasing ignores the call, so the alert does not bounce back
        for (GiantFauna other : g.level().getEntitiesOfClass(GiantFauna.class, box, e -> e != g && e.spec() == g.spec() && e.isAlive()))
            if (other.headPos().distanceToSqr(g.headPos()) <= 32 * 32) other.brain().onHurt(target, now);
    }

    @Override
    public double speedMul(GiantBrain.State s, int stateTicks, @Nullable Entity target)
    {
        return s == GiantBrain.State.CHASE && stateTicks < 40 ? 2.0 : 1.0;
    }

    @Override
    public double knockback() { return 1.2; }
}
