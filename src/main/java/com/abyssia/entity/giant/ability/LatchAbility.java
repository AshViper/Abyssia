package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.vehicle.Submarine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Shared course of an animal that takes hold of a submarine (rift lamprey, duskmaw): once the head is close enough during a
 * chase it holds the hull (the hold animation plays), applies its effect every 20 ticks, and lets go after a maximum time, when
 * the riders have dealt enough damage to it, or when the subclass says so. After letting go it does not take hold again for 600
 * ticks and retreats. The hold is only kept as a UUID (saved), so a lost submarine or a state change always frees it.
 */
abstract class LatchAbility extends GiantAbility
{
    static final int COOLDOWN = 600;
    @Nullable
    protected UUID held;
    protected long start, noUntil;
    protected float taken;

    LatchAbility(GiantFauna g) { super(g); }

    protected abstract double range();

    protected abstract int maxTicks();

    protected abstract float damageLimit();

    /** Every 20 ticks while held. */
    protected abstract void pulse(Submarine sub, long now);

    /** Extra reason to take hold / keep holding. */
    protected boolean wanted(Submarine sub) { return true; }

    protected void onLatch(Submarine sub, long now) {}

    @Nullable
    protected Submarine heldSub() { return sub(held); }

    @Override
    public boolean holding(GiantBrain.State state) { return held != null; }

    @Override
    public boolean canAttack(int stateTicks) { return held == null; }

    @Override
    public void tick(long now, GiantBrain.State state, @Nullable Entity target)
    {
        if (held != null)
        {
            Submarine s = heldSub();
            if (s == null || state != GiantBrain.State.CHASE || now - start >= maxTicks() || taken >= damageLimit() || !wanted(s)) release(now);
            else
            {
                s.giantSlow(now + 5);
                if ((now - start) % 20 == 0) pulse(s, now);
                return;
            }
        }
        if (state == GiantBrain.State.CHASE && target instanceof Submarine s && now >= noUntil && g.tickCount % 2 == 0 && wanted(s)
                && distToBox(g.headPos(), s.getBoundingBox()) <= range())
        {
            held = s.getUUID();
            start = now;
            taken = 0;
            onLatch(s, now);
        }
    }

    protected void release(long now)
    {
        held = null;
        noUntil = now + COOLDOWN;
        g.brain().retreat(now);
    }

    @Override
    public void onHurt(DamageSource source, float amount)
    {
        Submarine s = held != null ? heldSub() : null;
        Entity attacker = source.getEntity();
        if (s != null && attacker != null && (s.hasPassenger(attacker) || attacker.getVehicle() == s)) taken += amount;
    }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putLong("LatchNoUntil", noUntil);
    }

    @Override
    public void load(CompoundTag tag)
    {
        noUntil = tag.getLong("LatchNoUntil");
    }
}
