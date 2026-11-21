package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.vehicle.Submarine;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Rift lamprey: when the head is within 3 blocks of a submarine it fastens on the hull's side (the head follows the hull, the
 * chain trails behind), hurts it for 0.3 every 20 ticks and halves its speed. At most 200 ticks; it lets go when the riders deal
 * 20 damage to it. (There is no boost in the submarine, so there is no letting go on a sudden acceleration.)
 */
final class RiftLampreyAbility extends LatchAbility
{
    private double side = 1;

    RiftLampreyAbility(GiantFauna g) { super(g); }

    @Override
    protected double range() { return 3.0; }

    @Override
    protected int maxTicks() { return 200; }

    @Override
    protected float damageLimit() { return 20.0F; }

    @Override
    protected void onLatch(Submarine s, long now)
    {
        side = g.headPos().subtract(center(s)).dot(left(s)) >= 0 ? 1 : -1;
        s.level().playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    @Override
    protected void pulse(Submarine s, long now)
    {
        g.brain().hurtSub(s, 0.3F);
    }

    @Nullable
    @Override
    public Vec3 anchor()
    {
        Submarine s = held != null ? heldSub() : null;
        if (s == null) return null;
        return center(s).add(left(s).scale(side * (s.getBbWidth() * 0.5 + 1.2)));
    }
}
