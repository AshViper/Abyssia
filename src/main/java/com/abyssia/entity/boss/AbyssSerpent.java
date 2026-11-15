package com.abyssia.entity.boss;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Abyssal Serpent: head + 40 joints of 5 blocks (about 200 blocks). Dev load test: start the game with
 * {@code -Dabyssia.boss.joints=360} to give every serpent a 360 joint chain.
 */
public class AbyssSerpent extends AbyssBoss
{
    private static final EntityDataAccessor<Long> DATA_SWIPE_START = SynchedEntityData.defineId(AbyssSerpent.class, EntityDataSerializers.LONG);
    private static final int SWIPE_TICKS = 40;
    private static final double TARGET_RANGE = 96;

    private enum State { CIRCLE, CHARGE, BITE, COIL, TAIL_SWIPE }

    private State state = State.CIRCLE;
    private int stateTicks;
    private int stateLength = 120;
    private LivingEntity target;
    private int idleTicks;
    private int biteCooldown;
    private double angle;
    private int dir = 1;
    private double circleRadius = 50;
    private final Set<UUID> swipeHit = new HashSet<>();

    public AbyssSerpent(EntityType<? extends AbyssSerpent> type, Level level)
    {
        super(type, level, 1 + Integer.getInteger("abyssia.boss.joints", 40), 5.0, BossEvent.BossBarColor.BLUE);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1200.0).add(Attributes.ARMOR, 10.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.FOLLOW_RANGE, 96.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_SWIPE_START, -1L);
    }

    private boolean swiping()
    {
        long start = entityData.get(DATA_SWIPE_START);
        long t = level().getGameTime() - start;
        return start >= 0 && t >= 0 && t < SWIPE_TICKS;
    }

    @Override
    protected void afterChainTick()
    {
        if (swiping())
        {
            double sign = (entityData.get(DATA_SWIPE_START) & 1L) == 0 ? 1 : -1;
            chain.rotateTail(chain.size() - 11, sign * Math.toRadians(140.0 / SWIPE_TICKS));
        }
    }

    // ---- AI

    private void setState(State s, int length)
    {
        state = s;
        stateTicks = 0;
        stateLength = length;
        if (s == State.CIRCLE || s == State.COIL || s == State.TAIL_SWIPE)
        {
            Vec3 rel = headPos().subtract(target != null ? target.position() : center);
            angle = Math.atan2(rel.z, rel.x);
            dir = random.nextBoolean() ? 1 : -1;
        }
        if (s == State.CIRCLE) circleRadius = 40 + random.nextDouble() * 20;
        if (s == State.TAIL_SWIPE)
        {
            swipeHit.clear();
            entityData.set(DATA_SWIPE_START, level().getGameTime());
        }
    }

    private void pickNext()
    {
        double r = random.nextDouble();
        if (phase >= 3 && r < 0.4 && tailNear()) setState(State.TAIL_SWIPE, SWIPE_TICKS + 20);
        else if (phase >= 2 && r < 0.7) setState(State.COIL, 200);
        else setState(State.CIRCLE, 100 + random.nextInt(60));
    }

    private boolean tailNear()
    {
        if (target == null) return false;
        int pivot = chain.size() - 11;
        Vec3 t = target.position();
        return Math.sqrt(chain.distanceSqr(t.x, t.y, t.z)) < 40 && t.distanceTo(new Vec3(chain.x(pivot), chain.y(pivot), chain.z(pivot))) < 60;
    }

    @Override
    protected double designHealth() { return 1200.0; }

    @Override
    protected String stateName() { return state.name(); }

    private boolean validTarget(LivingEntity p)
    {
        return isValidTarget(p) && p.level() == level() && p.distanceToSqr(this) <= TARGET_RANGE * TARGET_RANGE;
    }

    @Override
    protected void aiTick()
    {
        if (biteCooldown > 0) biteCooldown--;
        if (tickCount % 10 == 0 && !validTarget(target))
        {
            target = findTarget(TARGET_RANGE);
        }
        if (!validTarget(target))
        {
            target = null;
            if (altarPos == null && ++idleTicks > 1200)
            {
                discard(); // /summon only; an altar boss is retreated by its altar
                return;
            }
            steerAround(center, 40, 0.6);
            return;
        }
        idleTicks = 0;
        stateTicks++;
        Vec3 tp = target.position().add(0, target.getBbHeight() * 0.5, 0);
        switch (state)
        {
            case CIRCLE ->
            {
                steerAround(tp, circleRadius, 0.9);
                if (stateTicks >= stateLength) setState(State.CHARGE, 60);
            }
            case CHARGE ->
            {
                steer(tp, 1.2, 0.2);
                if (biteCooldown <= 0 && headPos().distanceTo(tp) < 4.0) bite(tp);
                else if (stateTicks >= stateLength) pickNext();
            }
            case BITE ->
            {
                if (stateTicks >= stateLength) pickNext();
            }
            case COIL ->
            {
                double t = Math.min(1.0, stateTicks / (double) stateLength);
                steerAround(tp.add(0, t * 10 - 5, 0), 30 - 20 * t, 1.0);
                if (stateTicks >= stateLength) setState(State.CIRCLE, 100 + random.nextInt(60));
            }
            case TAIL_SWIPE ->
            {
                steerAround(tp, 25, 1.0);
                if (stateTicks >= stateLength) setState(State.CIRCLE, 100 + random.nextInt(60));
            }
        }
        if (tickCount % 2 == 0 && swiping()) swipeDamage();
    }

    /** Orbits {@code c} at {@code radius}: the aim point runs ahead on the circle. */
    private void steerAround(Vec3 c, double radius, double speed)
    {
        angle += dir * speed / Math.max(radius, 8.0);
        Vec3 point = c.add(Math.cos(angle) * radius, Math.sin(angle * 0.5) * 6, Math.sin(angle) * radius);
        steer(point, speed, 0.08);
    }

    private void bite(Vec3 tp)
    {
        biteCooldown = 40;
        target.hurt(damageSources().mobAttack(this), 14.0F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 4.0F, 0.5F);
        setState(State.BITE, 10);
    }

    private void swipeDamage()
    {
        java.util.List<LivingEntity> victims = new java.util.ArrayList<>(level().players());
        victims.addAll(level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(CONTACT_RANGE),
                x -> x != this && !(x instanceof net.minecraft.world.entity.player.Player)));
        for (LivingEntity p : victims)
        {
            if (!isValidTarget(p) || swipeHit.contains(p.getUUID())) continue;
            Vec3 pc = p.getBoundingBox().getCenter();
            for (int i = Math.max(0, chain.size() - 10); i < chain.size(); i++)
            {
                double dx = pc.x - chain.x(i), dy = pc.y - chain.y(i), dz = pc.z - chain.z(i);
                if (dx * dx + dy * dy + dz * dz > 144.0) continue;
                swipeHit.add(p.getUUID());
                p.hurt(damageSources().mobAttack(this), 10.0F);
                double len = Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-3);
                p.setDeltaMovement(p.getDeltaMovement().add(dx / len * 1.4, 0.35, dz / len * 1.4));
                p.hurtMarked = true;
                break;
            }
        }
    }
}
