package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.vehicle.Submarine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Shroud ray: swims round to 6 blocks above the prey and covers it (hold shroud) for up to 100 ticks: players within 10 blocks
 * get darkness, submarines within 10 blocks have their headlights dimmed to 30%. Then it stings with the tail (poison 5 s for a player).
 */
final class ShroudRayAbility extends GiantAbility
{
    static final double ABOVE = 6, RANGE = 10;
    static final int COVER = 100;
    /** 0 = going over the prey, 1 = covering, 2 = covered, now stinging */
    private int phase;
    private long coverStart;

    ShroudRayAbility(GiantFauna g) { super(g); }

    private Vec3 above(Entity t) { return center(t).add(0, ABOVE, 0); }

    @Override
    public void onState(GiantBrain.State from, GiantBrain.State to, @Nullable Entity target)
    {
        phase = 0;
    }

    @Override
    public boolean holding(GiantBrain.State s) { return s == GiantBrain.State.CHASE && phase == 1; }

    @Override
    public boolean canAttack(int stateTicks) { return phase == 2; }

    @Override
    public void tick(long now, GiantBrain.State state, @Nullable Entity target)
    {
        if (state != GiantBrain.State.CHASE || target == null) return;
        Vec3 head = g.headPos();
        if (phase == 0 && head.distanceToSqr(above(target)) < 25) { phase = 1; coverStart = now; }
        if (phase == 1)
        {
            if (now - coverStart >= COVER) phase = 2;
            else if (g.tickCount % 10 == 0)
            {
                for (ServerPlayer p : ((net.minecraft.server.level.ServerLevel) g.level()).players())
                    if (!p.isSpectator() && !p.isCreative() && p.position().distanceToSqr(head) <= RANGE * RANGE)
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                for (Submarine s : g.level().getEntitiesOfClass(Submarine.class, g.getBoundingBox().inflate(RANGE + 2), Entity::isAlive))
                    if (s.position().distanceToSqr(head) <= RANGE * RANGE) s.giantDim(now + 20);
            }
        }
    }

    @Override
    public Vec3 steer(GiantBrain.State state, Vec3 head, @Nullable Entity target, Vec3 dir)
    {
        if (state != GiantBrain.State.CHASE || target == null || phase == 2) return dir;
        Vec3 to = above(target).subtract(head);
        return to.lengthSqr() > 1.0E-4 ? to.normalize() : dir;
    }

    @Override
    public double speedMul(GiantBrain.State state, int stateTicks, @Nullable Entity target)
    {
        if (state != GiantBrain.State.CHASE || target == null || phase == 2) return 1.0;
        double d = g.headPos().distanceTo(above(target));
        return phase == 1 ? Math.max(0.15, Math.min(0.6, d / 6)) : 1.0;
    }

    @Override
    public void onHit(Entity target)
    {
        if (target instanceof LivingEntity living) living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
    }
}
