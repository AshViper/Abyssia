package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.vehicle.Submarine;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Hush whale: the click. When the chase begins every submarine within 48 blocks loses sonar and HUD for 200 ticks (and the
 * riders hear the sonic charge); the whale holds 40 ticks (charge), then rams at double speed for 60 ticks.
 */
final class HushWhaleAbility extends GiantAbility
{
    static final int CLICK_RADIUS = 48, JAM_TICKS = 200, WINDUP = 40, RAM = 60;

    HushWhaleAbility(GiantFauna g) { super(g); }

    @Override
    public void onState(GiantBrain.State from, GiantBrain.State to, @Nullable Entity target)
    {
        if (to != GiantBrain.State.CHASE) return;
        long now = g.level().getGameTime();
        Vec3 h = g.headPos();
        for (Submarine s : g.level().getEntitiesOfClass(Submarine.class, g.getBoundingBox().inflate(CLICK_RADIUS), Entity::isAlive))
        {
            if (s.position().distanceToSqr(h) > CLICK_RADIUS * CLICK_RADIUS) continue;
            s.giantJam(now + JAM_TICKS);
            s.level().playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3.0F, 0.7F);
        }
    }

    @Override
    public boolean canAttack(int stateTicks) { return stateTicks >= WINDUP; }

    @Override
    public double speedMul(GiantBrain.State s, int stateTicks, @Nullable Entity target)
    {
        if (s != GiantBrain.State.CHASE) return 1.0;
        if (stateTicks < WINDUP) return 0.15;
        return stateTicks < WINDUP + RAM ? 2.0 : 1.0;
    }

    @Override
    public double knockback() { return 1.5; }
}
