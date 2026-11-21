package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.vehicle.Submarine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Duskmaw: comes for a submarine whose headlights are on from 64 blocks (48 when they are off). Within 4 blocks of the hull it
 * wraps it (hold grab): it swims on, dragging the hull behind its head, the hull's speed is halved and 400 FE are drained every
 * 20 ticks (the lights go out at 0). At most 160 ticks; it lets go when the riders deal 25 damage to it or the headlights go off.
 */
final class DuskmawAbility extends LatchAbility
{
    static final int DRAIN_FE = 400;
    private Vec3 pull = Vec3.ZERO;

    DuskmawAbility(GiantFauna g) { super(g); }

    @Override
    public double detect(Entity t)
    {
        return t instanceof Submarine s && s.lights() ? 64 : GiantBrain.DETECT;
    }

    @Override
    public double maxDetect() { return 64; }

    @Override
    protected double range() { return 4.0; }

    @Override
    protected int maxTicks() { return 160; }

    @Override
    protected float damageLimit() { return 25.0F; }

    @Override
    protected boolean wanted(Submarine s) { return s.lights(); }

    @Override
    protected void onLatch(Submarine s, long now)
    {
        pull = g.heading();
    }

    @Override
    protected void pulse(Submarine s, long now)
    {
        s.setEnergy(s.getEnergy() - DRAIN_FE);
        if (s.getEnergy() <= 0) s.setLights(false);
    }

    @Override
    public void tick(long now, GiantBrain.State state, @Nullable Entity target)
    {
        super.tick(now, state, target);
        Submarine s = held != null ? heldSub() : null;
        if (s != null)
        {
            // tether the hull to the head: closes 25% of the distance beyond 4 blocks per tick (max 0.6)
            Vec3 to = g.headPos().subtract(center(s));
            double dist = to.length();
            if (dist > 4)
            {
                s.giantPull(to.scale(Math.min(0.6, (dist - 4) * 0.25) / dist));
            }
        }
    }

    @Override
    public Vec3 steer(GiantBrain.State state, Vec3 head, @Nullable Entity target, Vec3 dir)
    {
        return held != null && pull.lengthSqr() > 0.01 ? pull : dir;
    }

    @Override
    public double speedMul(GiantBrain.State state, int stateTicks, @Nullable Entity target)
    {
        return held != null ? 0.5 : 1.0;
    }
}
