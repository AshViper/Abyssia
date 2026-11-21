package com.abyssia.entity.giant;

import com.abyssia.Config;
import com.abyssia.entity.giant.ability.GiantAbility;
import com.abyssia.vehicle.Submarine;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Temper and behaviour of a giant (GNT01). HOSTILE: notice, warn (40 ticks), chase, attack, retreat. NEUTRAL runs the same
 * course but only after being hit. FRIENDLY never turns on anyone. The chase ends after {@link #CHASE_MAX} ticks, at
 * {@link #LEASH} blocks from the spawn point, or when the target is lost; then the animal stays calm for {@link #CALM_TICKS}.
 * Targets are players and {@link Submarine}s (a player riding a submarine is represented by the submarine).
 */
public final class GiantBrain
{
    public enum Temper { FRIENDLY, NEUTRAL, HOSTILE }

    public enum State { WANDER, WARN, CHASE, RETREAT }

    public static final int WARN_TICKS = 40, CHASE_MAX = 400, CALM_TICKS = 1200, ATTACK_INTERVAL = 40, RETREAT_TICKS = 100;
    public static final double LEASH = 64, DETECT = 48, LOSE = 96;

    private final GiantFauna owner;
    private final Temper temper;
    private State state = State.WANDER;
    private int stateTicks;
    @Nullable
    private Entity target;
    private long calmUntil;
    private long nextAttack;
    private Vec3 wander = Vec3.ZERO;
    private int wanderLeft;

    public GiantBrain(GiantFauna owner, Temper temper)
    {
        this.owner = owner;
        this.temper = temper;
    }

    public State state() { return state; }

    @Nullable
    public Entity target() { return target; }

    private GiantAbility ability() { return owner.ability(); }

    /** Ends the chase now (a lamprey that let go): calm for {@link #CALM_TICKS}, then back to wandering after the retreat. */
    public void retreat(long now)
    {
        calmUntil = now + CALM_TICKS;
        setState(State.RETREAT);
    }

    public long calmUntil() { return calmUntil; }

    public void setCalmUntil(long t) { calmUntil = t; }

    private void setState(State s)
    {
        State from = state;
        state = s;
        stateTicks = 0;
        owner.setChasing(s == State.CHASE);
        ability().onState(from, s, target);
    }

    // ---- targets

    private boolean valid(@Nullable Entity t)
    {
        if (t == null || !t.isAlive() || t.isRemoved() || t.level() != owner.level()) return false;
        if (t instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return t instanceof Submarine;
    }

    /** A player in a submarine is represented by the submarine. */
    @Nullable
    private Entity resolve(@Nullable Entity e)
    {
        if (e instanceof Player p && p.getVehicle() instanceof Submarine s) return valid(s) ? s : null;
        return valid(e) ? e : null;
    }

    private static Vec3 center(Entity t)
    {
        return t.getBoundingBox().getCenter();
    }

    @Nullable
    private Entity findTarget()
    {
        Vec3 h = owner.headPos();
        Entity best = null;
        double bd = ability().maxDetect() * ability().maxDetect();
        for (Player p : owner.level().players())
        {
            if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) || !p.isAlive() || p.getVehicle() instanceof Submarine) continue;
            double d = p.distanceToSqr(h), r = ability().detect(p);
            if (d < bd && d < r * r) { bd = d; best = p; }
        }
        for (Submarine s : owner.level().getEntitiesOfClass(Submarine.class, new AABB(h, h).inflate(ability().maxDetect()), Entity::isAlive))
        {
            double d = center(s).distanceToSqr(h), r = ability().detect(s);
            if (d < bd && d < r * r) { bd = d; best = s; }
        }
        return best;
    }

    /** NEUTRAL: a hit from a player starts the warning, unless the animal is calming down. */
    public void onHurt(@Nullable Entity attacker, long now)
    {
        if (temper != Temper.NEUTRAL || state != State.WANDER || now < calmUntil) return;
        if (owner.level().getDifficulty() == Difficulty.PEACEFUL) return;
        Entity t = resolve(attacker);
        if (t != null) begin(t);
    }

    private void begin(Entity t)
    {
        target = t;
        setState(State.WARN);
        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.5F);
    }

    // ---- tick (server, once per tick)

    public void tick(long now)
    {
        stateTicks++;
        switch (state)
        {
            case WANDER ->
            {
                if (--wanderLeft <= 0) pickWander();
                if (temper == Temper.HOSTILE && now >= calmUntil && owner.tickCount % 10 == 0
                        && owner.level().getDifficulty() != Difficulty.PEACEFUL)
                {
                    Entity found = findTarget();
                    if (found != null) begin(found);
                }
            }
            case WARN ->
            {
                if (!valid(target)) setState(State.WANDER);
                else if (stateTicks >= ability().warnTicks()) setState(State.CHASE);
            }
            case CHASE ->
            {
                Vec3 head = owner.headPos();
                if (!valid(target) || stateTicks > ability().chaseMax() || head.distanceToSqr(owner.home()) > LEASH * LEASH
                        || head.distanceToSqr(center(target)) > LOSE * LOSE)
                {
                    calmUntil = now + CALM_TICKS;
                    setState(State.RETREAT);
                }
                else if (now >= nextAttack && owner.tickCount % 2 == 0 && ability().canAttack(stateTicks) && inContact(target))
                {
                    attack(target);
                    nextAttack = now + ATTACK_INTERVAL;
                }
            }
            case RETREAT ->
            {
                if (stateTicks >= RETREAT_TICKS) setState(State.WANDER);
            }
        }
    }

    private boolean inContact(Entity t)
    {
        Boolean custom = ability().contact(t);
        if (custom != null) return custom;
        Vec3 c = center(t);
        double reach = owner.spec().radius() + Math.max(1.0, t.getBbWidth() * 0.5);
        return owner.chain().distanceSqr(c.x, c.y, c.z) < reach * reach;
    }

    private void attack(Entity t)
    {
        owner.triggerAnim("action", "attack");
        GiantSpec spec = owner.spec();
        if (t instanceof Submarine s) hurtSub(s, spec.subDamage());
        else if (t instanceof LivingEntity living) living.hurt(owner.damageSources().mobAttack(owner), spec.playerDamage());
        double k = ability().knockback();
        if (k > 0) push(t, k);
        ability().onHit(t);
    }

    /** Hurts a submarine by {@code amount} (it takes 10x as hull damage), never grinding the hull below the configured floor. */
    public void hurtSub(Submarine s, float amount)
    {
        int floor = Config.GIANT_SUB_HULL_FLOOR.get();
        if (floor > 0)
        {
            float room = s.maxDamage() - s.getDamage() - floor;
            if (room <= 0) return;
            amount = Math.min(amount, room / 10.0F);
        }
        s.hurt(owner.damageSources().mobAttack(owner), amount);
    }

    /** Knocks the target away from the head. */
    private void push(Entity t, double strength)
    {
        Vec3 away = center(t).subtract(owner.headPos());
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1.0E-4) away = owner.heading();
        away = away.normalize();
        if (t instanceof Submarine s) s.setDeltaMovement(s.getDeltaMovement().add(away.scale(strength * 0.6)));
        else if (t instanceof LivingEntity living) living.knockback(strength, -away.x, -away.z);
        t.hurtMarked = true;
    }

    // ---- steering input

    private void pickWander()
    {
        RandomSource r = owner.getRandom();
        Vec3 base = owner.heading();
        double ang = Math.atan2(base.z, base.x) + (r.nextDouble() * 2 - 1) * 1.2;
        wander = new Vec3(Math.cos(ang), (r.nextDouble() - 0.5) * 0.3, Math.sin(ang)).normalize();
        wanderLeft = 100 + r.nextInt(140);
    }

    /** Wanted swimming direction (unit vector). */
    public Vec3 direction(Vec3 head)
    {
        if (wander == Vec3.ZERO) pickWander();
        return ability().steer(state, head, valid(target) ? target : null, baseDirection(head));
    }

    private Vec3 baseDirection(Vec3 head)
    {
        switch (state)
        {
            case WARN, CHASE:
                if (valid(target))
                {
                    Vec3 to = center(target).subtract(head);
                    if (to.lengthSqr() > 1.0E-4) return to.normalize();
                }
                return wander;
            case RETREAT:
                Vec3 home = owner.home().subtract(head);
                return home.lengthSqr() > 64 ? home.normalize() : wander;
            default:
                return wander;
        }
    }

    /** Share of the cruise speed. */
    public double speedFactor()
    {
        double base = switch (state)
        {
            case WANDER -> 1.0;
            case WARN -> 0.3;
            case CHASE -> 1.8;
            case RETREAT -> 1.4;
        };
        return base * ability().speedMul(state, stateTicks, valid(target) ? target : null);
    }

    /** Share of the velocity difference closed per tick. */
    public double turnRate()
    {
        return ability().turnRate(state, switch (state)
        {
            case WANDER -> 0.04;
            case WARN -> 0.08;
            case CHASE -> 0.07;
            case RETREAT -> 0.06;
        });
    }
}
