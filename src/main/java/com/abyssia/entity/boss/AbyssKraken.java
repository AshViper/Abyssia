package com.abyssia.entity.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;

/**
 * Great Kraken: a mantle about 20 blocks tall (the head position is its centre) and 8 tentacles, each a {@link BossChain} of
 * 16 joints x 5.5 pinned at a ring of anchors under the mantle (tip to tip about 196). The tips are reached toward goal
 * points (FABRIK, server only; the client blends toward the 5-tick snapshots). No terrain contact: it floats near the arena
 * centre at 0.15 b/t.
 * Moves: IDLE_SWAY -> SLAM (1-2 tentacles raise, then strike a point locked 1 s before: 12 within 6 of the tip) |
 * GRAB (phase 2+: a tip that touches the target carries it to the mouth in 3 s, 40 damage on that tentacle frees it, else
 * bite 20) | INK (blindness 4 s in radius 16, phase 1+, every 20 s) | FRENZY (phase 3: all tentacles slam in turn, 1.5x speed).
 * Hitboxes: 3 body parts + 4 per tentacle (35). Each tentacle has durability (6% of design health); damage on it also hits the
 * body at 0.5x; at 0 it is severed (only the 4 root joints stay, the body takes 5%), 4+ severed = slow.
 */
public class AbyssKraken extends AbyssBoss implements software.bernie.geckolib.animatable.GeoEntity
{
    /** GeckoLib (near view only): the idle animation drives the mantle parts; the renderer places the arm bones. */
    private final software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache geoCache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<AbyssKraken>(this, "main", 0,
                state -> state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("idle"))));
    }

    @Override
    public software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return geoCache;
    }

    public static final int TENTACLES = 8;
    public static final int JOINTS = 16;
    public static final double SPACING = 5.5;
    private static final int BODY_PARTS = 3;
    private static final int[] PART_JOINTS = {14, 11, 8, 5};
    /** body part centres relative to the head position (the mantle centre of the 6 x 6 hitbox) */
    private static final double[] BODY_PART_Y = {-3.0, 3.0, 9.0};
    private static final double ANCHOR_Y = -6.0, ANCHOR_R = 3.0, MOUTH_Y = -7.5, MANTLE_TOP = 14.0;
    private static final double TARGET_RANGE = 110;
    /** targets farther from the body than this are not attacked (tentacle length 82 plus margin) */
    private static final double REACH = 84;
    private static final double DESIGN_HEALTH = 1600.0;
    private static final float TENTACLE_DURABILITY = (float) (DESIGN_HEALTH * 0.06);
    private static final float SLAM_DAMAGE = 12.0F, BITE_DAMAGE = 20.0F;
    private static final double SLAM_RADIUS = 6.0;
    private static final int GRAB_TICKS = 60;
    private static final float GRAB_FREE_DAMAGE = 40.0F;
    private static final int INK_COOLDOWN = 400;
    private static final double INK_RADIUS = 16.0;
    private static final int[] FAR_JOINTS = {0, 4, 8, 12, 15};
    /** points in {@link #farPoints}: head, mantle top, then per tentacle a break marker and {@link #FAR_JOINTS} */
    public static final int FAR_POINTS = 2 + TENTACLES * (1 + FAR_JOINTS.length);

    private static final EntityDataAccessor<Integer> DATA_SEVERED = SynchedEntityData.defineId(AbyssKraken.class, EntityDataSerializers.INT);

    private enum State { IDLE_SWAY, SLAM, GRAB, INK, FRENZY }

    private final BossChain[] tentacles = new BossChain[TENTACLES];
    private final float[] durability = new float[TENTACLES];
    private final KrakenPart[] kParts;
    // server-only AI state
    private final Vec3[] tip = new Vec3[TENTACLES];
    private final int[] slamT = new int[TENTACLES];
    private final Vec3[] slamPoint = new Vec3[TENTACLES];
    private State state = State.IDLE_SWAY;
    private int stateTicks;
    private int stateLength = 80;
    @Nullable
    private LivingEntity target;
    private int inkCooldown = 200;
    private Vec3 inkCenter = Vec3.ZERO;
    private boolean pendingFrenzy;
    private int grabT = -1;
    @Nullable
    private LivingEntity grabbed;
    private int grabTicks;
    private float grabDamage;
    private Vec3 grabGoal = Vec3.ZERO;
    private double grabSpeed;

    public AbyssKraken(EntityType<? extends AbyssKraken> type, Level level)
    {
        // the spine chain is just the mantle centre (2 joints); the tentacles are the extra chains
        super(type, level, 2, 8.0);
        kParts = new KrakenPart[BODY_PARTS + TENTACLES * PART_JOINTS.length];
        for (int i = 0; i < BODY_PARTS; i++) kParts[i] = new KrakenPart(this, i, -1, 6.0F);
        for (int t = 0; t < TENTACLES; t++)
        {
            tentacles[t] = new BossChain(JOINTS, SPACING).smooth();
            durability[t] = TENTACLE_DURABILITY;
            slamT[t] = -1;
            for (int k = 0; k < PART_JOINTS.length; k++) kParts[BODY_PARTS + t * PART_JOINTS.length + k] = new KrakenPart(this, BODY_PARTS + t * PART_JOINTS.length + k, t, 4.0F);
        }
        // part ids follow the boss id (the client needs the same ids to attack a part)
        this.setId(ENTITY_COUNTER.getAndAdd(kParts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, DESIGN_HEALTH).add(Attributes.ARMOR, 12.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.FOLLOW_RANGE, 110.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected double designHealth() { return DESIGN_HEALTH; }

    @Override
    protected int farKind() { return 2; }

    @Override
    protected String stateName() { return state.name(); }

    @Override
    protected boolean contactActive() { return false; }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_SEVERED, 0);
    }

    // ---- geometry (both sides)

    public BossChain tentacle(int t) { return tentacles[t]; }

    @Override
    @Nullable
    public BossChain chainAt(int index)
    {
        if (index == 0) return chain;
        return index <= TENTACLES ? tentacles[index - 1] : null;
    }

    public boolean severed(int t) { return (entityData.get(DATA_SEVERED) >> t & 1) != 0; }

    public int severedCount() { return Integer.bitCount(entityData.get(DATA_SEVERED)); }

    /** Number of tentacle joints that are drawn / alive: all, or the 4 root joints of a severed one. */
    public int liveJoints(int t) { return severed(t) ? 4 : JOINTS; }

    public Vec3 anchor(int t)
    {
        Vec3 h = headPos();
        double a = t * (Math.PI * 2 / TENTACLES);
        return new Vec3(h.x + Math.cos(a) * ANCHOR_R, h.y + ANCHOR_Y, h.z + Math.sin(a) * ANCHOR_R);
    }

    public Vec3 mouthPos()
    {
        Vec3 h = headPos();
        return new Vec3(h.x, h.y + MOUTH_Y, h.z);
    }

    private Vec3 tentacleTip(int t)
    {
        BossChain c = tentacles[t];
        return new Vec3(c.x(JOINTS - 1), c.y(JOINTS - 1), c.z(JOINTS - 1));
    }

    @Override
    protected void afterChainTick()
    {
        boolean server = !level().isClientSide;
        for (int t = 0; t < TENTACLES; t++)
        {
            BossChain c = tentacles[t];
            Vec3 a = anchor(t);
            double ang = t * (Math.PI * 2 / TENTACLES);
            c.tick(a.x, a.y, a.z, Math.cos(ang) * 0.4, -1.0, Math.sin(ang) * 0.4);
            if (server && tip[t] != null && !severed(t) && c.isInitialised()) c.reach(tip[t].x, tip[t].y, tip[t].z, 2);
        }
    }

    @Override
    protected void updateParts()
    {
        if (kParts == null) return;
        Vec3 h = headPos();
        for (int i = 0; i < BODY_PARTS; i++) kParts[i].setPos(h.x, h.y + BODY_PART_Y[i] - 3.0, h.z);
        for (int t = 0; t < TENTACLES; t++)
        {
            BossChain c = tentacles[t];
            boolean dead = severed(t);
            for (int k = 0; k < PART_JOINTS.length; k++)
            {
                KrakenPart p = kParts[BODY_PARTS + t * PART_JOINTS.length + k];
                p.active = !dead;
                if (dead || !c.isInitialised())
                {
                    Vec3 a = anchor(t);
                    p.setPos(a.x, a.y - 2.0, a.z);
                }
                else
                {
                    int j = PART_JOINTS[k];
                    p.setPos(c.x(j), c.y(j) - 2.0, c.z(j));
                }
            }
        }
    }

    @Override
    public PartEntity<?>[] getParts() { return kParts != null ? kParts : super.getParts(); }

    @Override
    public void setId(int id)
    {
        super.setId(id);
        if (kParts != null) for (int i = 0; i < kParts.length; i++) kParts[i].setId(id + i + 1);
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        Vec3 h = headPos();
        AABB box = new AABB(h, h).inflate(14);
        for (BossChain c : tentacles) if (c != null && c.isInitialised()) box = box.minmax(c.bounds(3.0));
        return box;
    }

    /**
     * Far silhouette polylines (x y z per point, interpolated at {@code pt}): head, mantle top, then for each tentacle a break
     * marker (NaN point) and 5 points along it. A severed tentacle repeats its 4th joint. Same layout on both sides.
     */
    public double[] farPoints(float pt)
    {
        double[] p = new double[3 * FAR_POINTS];
        double hx = chain.lerpX(0, pt), hy = chain.lerpY(0, pt), hz = chain.lerpZ(0, pt);
        p[0] = hx;
        p[1] = hy;
        p[2] = hz;
        p[3] = hx;
        p[4] = hy + MANTLE_TOP;
        p[5] = hz;
        int o = 6;
        for (int t = 0; t < TENTACLES; t++)
        {
            p[o++] = Double.NaN;
            p[o++] = Double.NaN;
            p[o++] = Double.NaN;
            BossChain c = tentacles[t];
            int last = liveJoints(t) - 1;
            Vec3 a = anchor(t);
            for (int f : FAR_JOINTS)
            {
                int j = Math.min(f, last);
                if (c.isInitialised())
                {
                    p[o++] = c.lerpX(j, pt);
                    p[o++] = c.lerpY(j, pt);
                    p[o++] = c.lerpZ(j, pt);
                }
                else
                {
                    p[o++] = a.x;
                    p[o++] = a.y - j * SPACING;
                    p[o++] = a.z;
                }
            }
        }
        return p;
    }

    @Override
    protected short[] farRel()
    {
        double[] p = farPoints(1.0F);
        short[] rel = new short[p.length - 3];
        for (int i = 3; i < p.length; i += 3)
        {
            if (Double.isNaN(p[i]))
            {
                rel[i - 3] = rel[i - 2] = rel[i - 1] = Short.MIN_VALUE;
                continue;
            }
            rel[i - 3] = (short) Math.round((p[i] - p[0]) * BossChain.UNIT);
            rel[i - 2] = (short) Math.round((p[i + 1] - p[1]) * BossChain.UNIT);
            rel[i - 1] = (short) Math.round((p[i + 2] - p[2]) * BossChain.UNIT);
        }
        return rel;
    }

    // ---- tentacle snapshots

    private void sendTentacles(@Nullable ServerPlayer only)
    {
        for (int t = 0; t < TENTACLES; t++)
        {
            if (!tentacles[t].isInitialised()) continue;
            BossChainPayload msg = new BossChainPayload(getId(), t + 1, tentacles[t].snapshot());
            if (only != null) BossNetwork.sendToPlayer(only, msg);
            else BossNetwork.sendToPlayersTrackingEntity(this, msg);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player)
    {
        super.startSeenByPlayer(player);
        sendTentacles(player);
    }

    // ---- AI (server)

    private boolean validTarget(@Nullable LivingEntity p)
    {
        return p != null && isValidTarget(p) && p.level() == level() && p.distanceToSqr(this) <= TARGET_RANGE * TARGET_RANGE;
    }

    private double speedMul()
    {
        double m = severedCount() >= 4 ? 0.5 : 1.0;
        return state == State.FRENZY ? m * 1.5 : m;
    }

    private void setState(State s, int length)
    {
        state = s;
        stateTicks = 0;
        stateLength = length;
        switch (s)
        {
            case SLAM ->
            {
                int count = phase >= 2 ? 2 : 1;
                for (int n = 0; n < count; n++)
                {
                    int t = nearestFreeTentacle();
                    if (t >= 0) slamT[t] = 0;
                }
            }
            case GRAB ->
            {
                grabT = nearestFreeTentacle();
                grabbed = null;
                grabTicks = 0;
                grabDamage = 0;
                if (grabT < 0) { state = State.IDLE_SWAY; stateLength = 40; }
            }
            case INK ->
            {
                inkCooldown = INK_COOLDOWN;
                inkCenter = target != null ? target.position() : headPos();
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.SQUID_SQUIRT, SoundSource.HOSTILE, 8.0F, 0.5F);
            }
            case FRENZY -> level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 8.0F, 0.5F);
            default -> {}
        }
    }

    /** Intact tentacle not slamming or grabbing whose anchor is nearest the target (or the first free one without a target). */
    private int nearestFreeTentacle()
    {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int t = 0; t < TENTACLES; t++)
        {
            if (severed(t) || slamT[t] >= 0 || t == grabT) continue;
            double d = target != null ? anchor(t).distanceToSqr(target.position()) : t;
            if (d < bd) { bd = d; best = t; }
        }
        return best;
    }

    private boolean anySlam()
    {
        for (int s : slamT) if (s >= 0) return true;
        return false;
    }

    @Override
    protected void onPhaseChange(int newPhase)
    {
        if (newPhase >= 3) pendingFrenzy = true;
    }

    @Override
    protected void aiTick()
    {
        if (tickCount % 10 == 0 && !validTarget(target)) target = findTarget(TARGET_RANGE);
        if (!validTarget(target))
        {
            target = null;
            if (state != State.IDLE_SWAY && state != State.INK) abortAttacks();
        }
        double mul = speedMul();
        // float around the arena centre, leaning a little toward the target
        Vec3 base = center;
        if (target != null) base = center.add(target.position().subtract(center).scale(0.3));
        Vec3 drift = base.add(Math.cos(tickCount * 0.01) * 15, Math.sin(tickCount * 0.007) * 5, Math.sin(tickCount * 0.01) * 15);
        double dist = headPos().distanceTo(drift);
        steer(drift, 0.15 * mul * Math.min(1.0, dist / 6.0), 0.05);

        if (inkCooldown > 0) inkCooldown--;
        stateTicks++;
        switch (state)
        {
            case IDLE_SWAY -> { if (stateTicks >= stateLength) chooseNext(); }
            case SLAM -> { if (stateTicks > 10 && !anySlam()) setState(State.IDLE_SWAY, 50 + random.nextInt(50)); }
            case GRAB -> grabTick();
            case INK -> inkTick();
            case FRENZY -> frenzyTick();
        }
        // goals and tip movement (every tick: 8 x 16 joints, cheap)
        for (int t = 0; t < TENTACLES; t++) if (!severed(t)) moveTip(t, mul);
        if (tickCount % 5 == 0) sendTentacles(null);
    }

    private void abortAttacks()
    {
        for (int t = 0; t < TENTACLES; t++) slamT[t] = -1;
        endGrab();
        state = State.IDLE_SWAY;
        stateTicks = 0;
        stateLength = 40;
    }

    private void chooseNext()
    {
        if (target == null) { setState(State.IDLE_SWAY, 60); return; }
        double d = headPos().distanceTo(target.position());
        if (pendingFrenzy && d <= REACH) { pendingFrenzy = false; setState(State.FRENZY, 240); return; }
        if (inkCooldown <= 0 && d <= 90) { setState(State.INK, 50); return; }
        if (d > REACH) { setState(State.IDLE_SWAY, 40); return; }
        double r = random.nextDouble();
        if (phase >= 3 && r < 0.3) setState(State.FRENZY, 240);
        else if (phase >= 2 && r < 0.55) setState(State.GRAB, 220);
        else setState(State.SLAM, 100);
    }

    // ---- tentacle goals

    private void moveTip(int t, double mul)
    {
        Vec3 a = anchor(t);
        Vec3 goal;
        double speed;
        if (t == grabT && state == State.GRAB)
        {
            goal = grabGoal;
            speed = grabSpeed;
        }
        else if (slamT[t] >= 0)
        {
            Vec3[] gs = slamGoal(t, a);
            goal = gs[0];
            speed = gs[1].x;
        }
        else
        {
            goal = idleGoal(t, a);
            speed = 1.0;
        }
        if (tip[t] == null)
            tip[t] = tentacles[t].isInitialised() ? tentacleTip(t) : goal;
        Vec3 d = goal.subtract(tip[t]);
        double len = d.length();
        double step = speed * mul;
        tip[t] = len <= step ? goal : tip[t].add(d.scale(step / len));
    }

    private Vec3 idleGoal(int t, Vec3 a)
    {
        double ang = t * (Math.PI * 2 / TENTACLES) + 0.15 * Math.sin(tickCount * 0.03 + t);
        double r = 45 + 12 * Math.sin(tickCount * 0.04 + t * 1.3);
        double y = a.y - 18 + 14 * Math.sin(tickCount * 0.035 + t * 0.9);
        return new Vec3(a.x + Math.cos(ang) * r, y, a.z + Math.sin(ang) * r);
    }

    /** Slam timeline of tentacle t: raise above the target, lock the point, strike, impact, rest. Returns {goal, (speed, 0, 0)}. */
    private Vec3[] slamGoal(int t, Vec3 a)
    {
        int raise = state == State.FRENZY ? 12 : 24, strike = 8, rest = 20;
        int k = slamT[t]++;
        if (k < raise)
        {
            if (target == null) { slamT[t] = -1; return new Vec3[]{idleGoal(t, a), new Vec3(1, 0, 0)}; }
            return new Vec3[]{target.position().add(0, 26, 0), new Vec3(2.0, 0, 0)};
        }
        if (k == raise) slamPoint[t] = target != null ? target.position().add(0, 0.5, 0) : tentacleTip(t);
        if (k < raise + strike) return new Vec3[]{slamPoint[t], new Vec3(5.0, 0, 0)};
        if (k == raise + strike) impact(t);
        if (k < raise + strike + rest) return new Vec3[]{slamPoint[t], new Vec3(1.0, 0, 0)};
        slamT[t] = -1;
        return new Vec3[]{idleGoal(t, a), new Vec3(1.0, 0, 0)};
    }

    private void impact(int t)
    {
        Vec3 tp = tentacleTip(t);
        if (!(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.POOF, tp.x, tp.y, tp.z, 40, 3.0, 1.5, 3.0, 0.1);
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, tp.x, tp.y, tp.z, 30, 3.0, 1.0, 3.0, 0.2);
        sl.playSound(null, tp.x, tp.y, tp.z, SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 8.0F, 0.4F);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(tp, tp).inflate(SLAM_RADIUS + 1), x -> x != this && isValidTarget(x)))
        {
            Vec3 c = e.getBoundingBox().getCenter();
            if (c.distanceToSqr(tp) > SLAM_RADIUS * SLAM_RADIUS) continue;
            e.hurt(damageSources().mobAttack(this), SLAM_DAMAGE);
            Vec3 away = c.subtract(tp);
            double l = Math.max(away.length(), 1.0E-3);
            e.setDeltaMovement(e.getDeltaMovement().add(away.x / l * 1.2, 0.5, away.z / l * 1.2));
            e.hurtMarked = true;
        }
    }

    // ---- states

    private void frenzyTick()
    {
        if (stateTicks < stateLength)
        {
            if (stateTicks % 10 == 0 && target != null && headPos().distanceTo(target.position()) <= REACH)
            {
                int t = nearestFreeTentacle();
                if (t >= 0 && random.nextDouble() < 0.8) slamT[t] = 0;
            }
        }
        else if (!anySlam()) setState(State.IDLE_SWAY, 80);
    }

    private void inkTick()
    {
        if (!(level() instanceof ServerLevel sl)) return;
        if (stateTicks < 20)
        {
            Vec3 m = mouthPos();
            sl.sendParticles(ParticleTypes.SQUID_INK, m.x, m.y, m.z, 10, 1.5, 1.0, 1.5, 0.1);
            if (target != null) inkCenter = target.position();
            return;
        }
        if (stateTicks % 2 == 0)
        {
            sl.sendParticles(ParticleTypes.SQUID_INK, inkCenter.x, inkCenter.y + 2, inkCenter.z, 60, INK_RADIUS * 0.5, 5.0, INK_RADIUS * 0.5, 0.02);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, inkCenter.x, inkCenter.y + 2, inkCenter.z, 14, INK_RADIUS * 0.5, 5.0, INK_RADIUS * 0.5, 0.01);
        }
        if (stateTicks == 20 || stateTicks == 35 || stateTicks == 50)
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(inkCenter, inkCenter).inflate(INK_RADIUS), x -> x != this && isValidTarget(x)))
                if (e.getBoundingBox().getCenter().distanceToSqr(inkCenter) <= INK_RADIUS * INK_RADIUS)
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
        if (stateTicks >= stateLength) setState(State.IDLE_SWAY, 60 + random.nextInt(40));
    }

    private void endGrab()
    {
        if (grabbed != null)
        {
            // the carry velocity must not fling the target (it was thrown ~50 blocks up after the bite)
            Vec3 out = grabbed.getBoundingBox().getCenter().subtract(mouthPos());
            double l = Math.max(out.length(), 1.0E-3);
            grabbed.setDeltaMovement(out.x / l * 0.4, Math.min(out.y / l * 0.4, 0.2), out.z / l * 0.4);
            grabbed.hurtMarked = true;
        }
        grabbed = null;
        grabT = -1;
        grabTicks = 0;
    }

    private void grabTick()
    {
        if (grabT < 0 || severed(grabT) || !validTarget(target))
        {
            endGrab();
            setState(State.IDLE_SWAY, 60);
            return;
        }
        Vec3 tp = tentacleTip(grabT);
        if (grabbed == null)
        {
            grabGoal = target.position().add(0, target.getBbHeight() * 0.5, 0);
            grabSpeed = 2.4;
            if (tp.distanceTo(grabGoal) < 4.0)
            {
                grabbed = target;
                grabTicks = 0;
                grabDamage = 0;
                level().playSound(null, tp.x, tp.y, tp.z, SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 6.0F, 0.5F);
            }
            else if (stateTicks > 120)
            {
                endGrab();
                setState(State.IDLE_SWAY, 80);
            }
            return;
        }
        if (!isValidTarget(grabbed))
        {
            endGrab();
            setState(State.IDLE_SWAY, 60);
            return;
        }
        grabTicks++;
        Vec3 mouth = mouthPos();
        grabGoal = mouth;
        Vec3 cur = tip[grabT] != null ? tip[grabT] : tp;
        grabSpeed = Math.max(0.3, cur.distanceTo(mouth) / Math.max(1, GRAB_TICKS - grabTicks));
        // the held body rides the tip
        Vec3 gc = grabbed.position().add(0, grabbed.getBbHeight() * 0.5, 0);
        grabbed.setDeltaMovement(tp.subtract(gc).scale(0.6));
        grabbed.hurtMarked = true;
        grabbed.fallDistance = 0;
        if (grabDamage >= GRAB_FREE_DAMAGE)
        {
            level().playSound(null, tp.x, tp.y, tp.z, SoundEvents.RAVAGER_HURT, SoundSource.HOSTILE, 8.0F, 0.6F);
            endGrab();
            setState(State.IDLE_SWAY, 100);
            return;
        }
        if (grabTicks >= GRAB_TICKS)
        {
            grabbed.hurt(damageSources().mobAttack(this), BITE_DAMAGE);
            grabbed.setDeltaMovement(Vec3.ZERO);
            level().playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 8.0F, 0.4F);
            endGrab();
            setState(State.IDLE_SWAY, 80);
        }
    }

    // ---- damage

    @Override
    public boolean hurtFromPart(BossPart part, DamageSource source, float amount)
    {
        if (!(part instanceof KrakenPart kp) || kp.tentacle() < 0) return super.hurtFromPart(part, source, amount);
        int t = kp.tentacle();
        if (severed(t)) return false;
        // body takes 0.5x; the tentacle's own durability takes the full hit
        boolean hit = super.hurtFromPart(part, source, amount * 0.5F);
        if (hit)
        {
            durability[t] -= amount;
            if (t == grabT && grabbed != null) grabDamage += amount;
            if (durability[t] <= 0) sever(t);
        }
        return hit;
    }

    private void sever(int t)
    {
        entityData.set(DATA_SEVERED, entityData.get(DATA_SEVERED) | 1 << t);
        durability[t] = 0;
        slamT[t] = -1;
        if (t == grabT)
        {
            endGrab();
            if (state == State.GRAB) setState(State.IDLE_SWAY, 100);
        }
        // the cut costs the body 5% of its health (never a kill: that is left to the attacks)
        setHealth(Math.max(1.0F, getHealth() - getMaxHealth() * 0.05F));
        if (level() instanceof ServerLevel sl)
        {
            Vec3 a = anchor(t);
            sl.sendParticles(ParticleTypes.SQUID_INK, a.x, a.y - 8, a.z, 80, 2.0, 3.0, 2.0, 0.05);
            sl.playSound(null, a.x, a.y, a.z, SoundEvents.RAVAGER_HURT, SoundSource.HOSTILE, 10.0F, 0.4F);
        }
    }

    // ---- save

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("Severed", severedCount());
        tag.putInt("SeveredMask", entityData.get(DATA_SEVERED));
        ListTag hp = new ListTag();
        for (float f : durability) hp.add(FloatTag.valueOf(f));
        tag.put("TentacleHP", hp);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SeveredMask")) entityData.set(DATA_SEVERED, tag.getInt("SeveredMask"));
        if (tag.contains("TentacleHP", Tag.TAG_LIST))
        {
            ListTag hp = tag.getList("TentacleHP", Tag.TAG_FLOAT);
            for (int t = 0; t < TENTACLES && t < hp.size(); t++) durability[t] = hp.getFloat(t);
        }
    }
}
