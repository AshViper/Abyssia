package com.abyssia.entity.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Abyss Angler: a 150 block anglerfish, head + 9 joints of 15 blocks (the snout reaches about 15 ahead of joint 0), body
 * about 30 wide and 42 tall. Patrols with the jaw slightly ajar, flashes its lure to blind and pull players in, then
 * charges with the maw open and rests with the jaw shut. The renderer reads {@link #jawOpen} and {@link #lureFlash}.
 */
public class AbyssAngler extends AbyssBoss implements software.bernie.geckolib.animatable.GeoEntity
{
    /** GeckoLib (near view only): the idle animation drives the jaw, lures and fins; the renderer places the spine bones. */
    private final software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache geoCache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<AbyssAngler>(this, "main", 0,
                state -> state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("idle"))));
    }

    @Override
    public software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return geoCache;
    }

    /** Cross-section scale against the base model (shared with the renderer). */
    public static final float SIZE = 3.0f;
    private static final EntityDataAccessor<Float> DATA_JAW = SynchedEntityData.defineId(AbyssAngler.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LURE = SynchedEntityData.defineId(AbyssAngler.class, EntityDataSerializers.FLOAT);
    private static final double TARGET_RANGE = 200;
    private static final double MOUTH_AHEAD = 18, MOUTH_RADIUS = 26;
    private static final int LURE_TICKS = 110, LURE_WARN = 60, WINDUP = 45, DASH_MAX = 90, REST = 110;
    private static final double CRUISE = 0.45, DASH_SPEED = 1.7;

    private enum State { PATROL, LURE, WINDUP, DASH, REST }

    private State state = State.PATROL;
    private int stateTicks;
    private int stateLength = 160;
    private LivingEntity target;
    private double angle;
    private int dir = 1;
    private double circleRadius = 90;
    private Vec3 dashDir = Vec3.ZERO;
    private final Set<UUID> biteHit = new HashSet<>();
    private float jawNow, jawOld, lureNow, lureOld;

    public AbyssAngler(EntityType<? extends AbyssAngler> type, Level level)
    {
        super(type, level, 10, 15.0);
    }

    @Override
    public double size() { return SIZE; }

    @Override
    public double bodyRadius() { return 16.0; }

    @Override
    protected int jointsPerPart() { return 1; }

    @Override
    protected double arenaScale() { return 1.6; }

    @Override
    protected int farKind() { return 4; }

    @Override
    protected double designHealth() { return 1000.0; }

    @Override
    protected String stateName() { return state.name(); }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.ARMOR, 8.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.FOLLOW_RANGE, 200.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_JAW, 0.2F);
        this.entityData.define(DATA_LURE, 0.0F);
    }

    /** Jaw opening 0 (shut) to 1 (wide open), interpolated between ticks. */
    public float jawOpen(float partialTick) { return Mth.lerp(partialTick, jawOld, jawNow); }

    /** Lure glow 0..1, interpolated between ticks. */
    public float lureFlash(float partialTick) { return Mth.lerp(partialTick, lureOld, lureNow); }

    @Override
    public void tick()
    {
        super.tick();
        jawOld = jawNow;
        lureOld = lureNow;
        jawNow = entityData.get(DATA_JAW);
        lureNow = entityData.get(DATA_LURE);
    }

    // ---- AI

    private void setState(State s, int length)
    {
        state = s;
        stateTicks = 0;
        stateLength = length;
        if (s == State.PATROL)
        {
            Vec3 rel = headPos().subtract(target != null ? target.position() : center);
            angle = Math.atan2(rel.z, rel.x);
            dir = random.nextBoolean() ? 1 : -1;
            circleRadius = 80 + random.nextDouble() * 40;
        }
        if (s == State.DASH) biteHit.clear();
    }

    private boolean validTarget(LivingEntity p)
    {
        return isValidTarget(p) && p.level() == level() && p.distanceToSqr(this) <= TARGET_RANGE * TARGET_RANGE;
    }

    private Vec3 forward()
    {
        return vel.lengthSqr() > 1.0E-6 ? vel.normalize() : Vec3.directionFromRotation(getXRot(), getYRot());
    }

    private void setJaw(float goal, float step)
    {
        float j = entityData.get(DATA_JAW);
        entityData.set(DATA_JAW, j + Mth.clamp(goal - j, -step, step));
    }

    private void setLure(float goal, float step)
    {
        float l = entityData.get(DATA_LURE);
        entityData.set(DATA_LURE, l + Mth.clamp(goal - l, -step, step));
    }

    @Override
    protected void aiTick()
    {
        if (tickCount % 10 == 0 && !validTarget(target)) target = findTarget(TARGET_RANGE);
        if (!validTarget(target))
        {
            target = null;
            if (state != State.PATROL) setState(State.PATROL, 160);
            patrol(center, 60, CRUISE);
            return;
        }
        stateTicks++;
        Vec3 tp = target.position().add(0, target.getBbHeight() * 0.5, 0);
        switch (state)
        {
            case PATROL ->
            {
                patrol(tp, circleRadius, CRUISE);
                if (stateTicks >= stateLength)
                {
                    if (random.nextDouble() < 0.5) setState(State.LURE, LURE_TICKS);
                    else setState(State.WINDUP, WINDUP);
                }
            }
            case LURE ->
            {
                // face the target and drift: the lure brightens, then blinds and drags
                steer(tp, 0.25, 0.04);
                setJaw(0.3F, 0.02F);
                setLure(stateTicks < LURE_WARN ? 1.0F : 0.85F, 0.03F);
                if (stateTicks == LURE_WARN)
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 8.0F, 0.5F);
                if (stateTicks >= LURE_WARN) lureEffect();
                if (stateTicks >= stateLength) setState(State.WINDUP, WINDUP);
            }
            case WINDUP ->
            {
                steer(tp, 0.3, 0.1);
                setJaw(1.0F, 0.03F);
                setLure(0.0F, 0.04F);
                if (stateTicks == 1)
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 8.0F, 0.4F);
                if (stateTicks >= stateLength)
                {
                    dashDir = tp.subtract(headPos()).normalize();
                    setState(State.DASH, DASH_MAX);
                }
            }
            case DASH ->
            {
                steer(headPos().add(dashDir.scale(100)), DASH_SPEED, 0.3);
                setJaw(1.0F, 0.1F);
                if (stateTicks % 2 == 0 && biteDamage()) setState(State.REST, REST);
                else if (stateTicks >= stateLength) setState(State.REST, REST);
            }
            case REST ->
            {
                vel = vel.scale(0.94);
                setJaw(0.0F, 0.08F);
                setLure(0.0F, 0.04F);
                if (stateTicks >= stateLength) setState(State.PATROL, 140 + random.nextInt(80));
            }
        }
    }

    /** Slow orbit round {@code c} with the jaw working between 0.15 and 0.3. */
    private void patrol(Vec3 c, double radius, double speed)
    {
        angle += dir * speed / Math.max(radius, 8.0);
        Vec3 point = c.add(Math.cos(angle) * radius, Math.sin(angle * 0.5) * 12, Math.sin(angle) * radius);
        steer(point, speed, 0.05);
        setJaw(0.225F + 0.075F * (float) Math.sin(tickCount * 0.06), 0.02F);
        setLure(0.0F, 0.04F);
    }

    /** Darkness for players in line of sight, and a weak pull toward the mouth. */
    private void lureEffect()
    {
        Vec3 mouth = headPos().add(forward().scale(MOUTH_AHEAD));
        for (Player p : level().players())
        {
            if (!isValidTarget(p) || p.distanceToSqr(this) > TARGET_RANGE * TARGET_RANGE || !hasLineOfSight(p)) continue;
            if (stateTicks == LURE_WARN) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, false, false));
            Vec3 to = mouth.subtract(p.getBoundingBox().getCenter());
            double len = to.length();
            if (len < 3.0) continue;
            p.setDeltaMovement(p.getDeltaMovement().add(to.scale(0.035 / len)));
            p.hurtMarked = true;
        }
    }

    /** Large damage and knockback to everything in front of the open mouth; true when something was bitten. */
    private boolean biteDamage()
    {
        Vec3 mouth = headPos().add(dashDir.scale(MOUTH_AHEAD));
        java.util.List<LivingEntity> victims = new java.util.ArrayList<>(level().players());
        victims.addAll(level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(MOUTH_RADIUS * 2),
                x -> x != this && !(x instanceof Player)));
        boolean hit = false;
        for (LivingEntity p : victims)
        {
            if (!isValidTarget(p) || biteHit.contains(p.getUUID())) continue;
            if (p.getBoundingBox().getCenter().distanceToSqr(mouth) > MOUTH_RADIUS * MOUTH_RADIUS) continue;
            biteHit.add(p.getUUID());
            p.hurt(damageSources().mobAttack(this), 22.0F);
            p.setDeltaMovement(p.getDeltaMovement().add(dashDir.x * 2.2, 0.5 + dashDir.y * 1.0, dashDir.z * 2.2));
            p.hurtMarked = true;
            hit = true;
        }
        if (hit) level().playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 10.0F, 0.3F);
        return hit;
    }
}
