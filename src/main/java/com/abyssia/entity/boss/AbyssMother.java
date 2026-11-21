package com.abyssia.entity.boss;

import com.abyssia.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Abyss Mother: a jellyfish bell about 36 across (the head position is its centre) with 12 tentacles, each a {@link BossChain}
 * of 28 joints x 6 pinned on the rim (tip to tip about 204). The tentacles are dragged by the "current": every 2 ticks each joint
 * is pushed backwards (against the travel direction) in proportion to its distance from the root, and the follow-the-leader
 * chain keeps the length, so they stream out horizontally behind a moving bell. They never sink below 8 above the floor.
 * Moves: DRIFT (0.1-0.2 b/t, speeding up with the bell beat) | STING (a target touching a tentacle segment: poison II 5 s,
 * slowness 4 s, 4 damage; the contact test runs every 2 ticks, always) | PULSE (glow pulse: 2 s warning with darkness, then a
 * flash, 8 damage within 24 unless a block is in the line of sight; every 15 s) | BROOD (phase 2+: brood jellies, at most 6)
 * | BLOOM (phase 3: the bell opens, exposing the core x2, movement stops, pulse every 8 s).
 * Hitboxes: 4 bell parts (the last is the core under the bell) + 3 per tentacle (40).
 */
public class AbyssMother extends AbyssBoss
{
    public static final int TENTACLES = 12;
    public static final int JOINTS = 28;
    public static final double SPACING = 6.0;
    private static final int BELL_PARTS = 4;
    private static final int[] PART_JOINTS = {26, 22, 18};
    private static final double ANCHOR_Y = -3.0, ANCHOR_R = 15.0, BELL_TOP = 8.0, CORE_Y = -4.0;
    private static final double TARGET_RANGE = 140;
    private static final double DESIGN_HEALTH = 2600.0;
    private static final double FLOOR_GAP = 8.0;
    private static final int[] FLOOR_JOINTS = {7, 14, 21, 27};
    private static final int[] FAR_JOINTS = {0, 5, 10, 16, 22, 27};
    public static final int FAR_POINTS = 2 + TENTACLES * (1 + FAR_JOINTS.length);
    private static final float STING_DAMAGE = 4.0F, FLASH_DAMAGE = 8.0F;
    private static final double FLASH_RADIUS = 24.0;
    private static final int PULSE_WARN = 40, PULSE_END = 50, PULSE_COOLDOWN = 300, BLOOM_PULSE_COOLDOWN = 160;
    private static final int MAX_SPAWN = 6, BROOD_COOLDOWN = 400;

    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(AbyssMother.class, EntityDataSerializers.INT);

    private enum State { DRIFT, STING, BROOD, BLOOM }

    private final BossChain[] tentacles = new BossChain[TENTACLES];
    private final MotherPart[] mParts;
    private final double[][] floorY = new double[TENTACLES][FLOOR_JOINTS.length];
    // server-only AI state
    private final HashMap<UUID, Integer> stingCd = new HashMap<>();
    private final List<AbyssMotherSpawn> spawns = new ArrayList<>();
    private State state = State.DRIFT;
    private int stateTicks;
    @Nullable
    private LivingEntity target;
    private int pulseT = -1;
    private int pulseCd = 200;
    private int broodCd = 300;

    public AbyssMother(EntityType<? extends AbyssMother> type, Level level)
    {
        super(type, level, 2, 8.0, BossEvent.BossBarColor.PURPLE);
        mParts = new MotherPart[BELL_PARTS + TENTACLES * PART_JOINTS.length];
        for (int i = 0; i < BELL_PARTS; i++) mParts[i] = new MotherPart(this, i, -1, i == BELL_PARTS - 1, 8.0F);
        for (int t = 0; t < TENTACLES; t++)
        {
            tentacles[t] = new BossChain(JOINTS, SPACING).smooth();
            for (int f = 0; f < FLOOR_JOINTS.length; f++) floorY[t][f] = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < PART_JOINTS.length; k++)
            {
                int idx = BELL_PARTS + t * PART_JOINTS.length + k;
                mParts[idx] = new MotherPart(this, idx, t, false, 3.0F);
            }
        }
        this.setId(ENTITY_COUNTER.getAndAdd(mParts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, DESIGN_HEALTH).add(Attributes.ARMOR, 8.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.FOLLOW_RANGE, 140.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected double designHealth() { return DESIGN_HEALTH; }

    @Override
    protected int farKind() { return 3; }

    @Override
    protected String stateName() { return pulseT >= 0 ? "PULSE" : state.name(); }

    @Override
    protected boolean contactActive() { return false; }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_FLAGS, 0);
    }

    private void setFlag(int bit, boolean on)
    {
        int f = entityData.get(DATA_FLAGS);
        int n = on ? f | 1 << bit : f & ~(1 << bit);
        if (n != f) entityData.set(DATA_FLAGS, n);
    }

    /** Phase 3: the bell is open and the core exposed. */
    public boolean bloom() { return (entityData.get(DATA_FLAGS) & 1) != 0; }

    /** The glow pulse is charging (the 2 second warning). */
    public boolean warning() { return (entityData.get(DATA_FLAGS) & 2) != 0; }

    /** Bell beat in -1..1, a period of 50 ticks (both sides). */
    public float beat(float pt) { return Mth.sin((tickCount + pt) * (Mth.TWO_PI / 50.0F)); }

    // ---- geometry (both sides)

    public BossChain tentacle(int t) { return tentacles[t]; }

    @Override
    @Nullable
    public BossChain chainAt(int index)
    {
        if (index == 0) return chain;
        return index <= TENTACLES ? tentacles[index - 1] : null;
    }

    public Vec3 anchor(int t)
    {
        Vec3 h = headPos();
        double a = t * (Math.PI * 2 / TENTACLES);
        return new Vec3(h.x + Math.cos(a) * ANCHOR_R, h.y + ANCHOR_Y, h.z + Math.sin(a) * ANCHOR_R);
    }

    /** Horizontal "behind" direction (against the heading; the yaw is synced). */
    private Vec3 back()
    {
        double yaw = Math.toRadians(getYRot());
        return new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
    }

    @Override
    protected void afterChainTick()
    {
        boolean server = !level().isClientSide;
        Vec3 back = back();
        for (int t = 0; t < TENTACLES; t++)
        {
            BossChain c = tentacles[t];
            Vec3 a = anchor(t);
            c.tick(a.x, a.y, a.z, back.x, -0.25, back.z);
            if (!server || !c.isInitialised()) continue;
            if ((tickCount + t) % 10 == 0) sampleFloor(t, c);
            if ((tickCount + t) % 2 == 0) drift(t, c, back);
            for (int i = 1; i < JOINTS; i++) c.lift(i, floorY[t][floorBucket(i)] + FLOOR_GAP);
        }
    }

    private static int floorBucket(int joint)
    {
        return joint <= 10 ? 0 : joint <= 17 ? 1 : joint <= 24 ? 2 : 3;
    }

    /** The current: each joint is pushed back in proportion to its index (every 2 ticks), a slow sway, and a sag when the bell is nearly still. */
    private void drift(int t, BossChain c, Vec3 back)
    {
        boolean moving = vel.horizontalDistanceSqr() > 0.0016;
        double k = moving ? 0.12 : 0.0;
        double sag = moving ? -0.02 : -0.08;
        double sway = 0.05 * Math.sin(tickCount * 0.07 + t * 1.1);
        double px = -back.z, pz = back.x;
        double dx = back.x * k + px * sway, dz = back.z * k + pz * sway;
        for (int i = 1; i < JOINTS; i++) c.shift(i, dx * i, sag * i, dz * i);
    }

    private void sampleFloor(int t, BossChain c)
    {
        for (int f = 0; f < FLOOR_JOINTS.length; f++)
        {
            int j = FLOOR_JOINTS[f];
            floorY[t][f] = floorBelow(c.x(j), c.y(j), c.z(j));
        }
    }

    /** Top of the first motion-blocking block below the point (up to 64 down), or -infinity if none / not loaded. */
    private double floorBelow(double x, double y, double z)
    {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        if (!level().hasChunkAt(m)) return Double.NEGATIVE_INFINITY;
        int min = level().getMinBuildHeight();
        for (int n = 0; n < 64 && m.getY() > min; n++)
        {
            if (level().getBlockState(m).blocksMotion()) return m.getY() + 1;
            m.move(Direction.DOWN);
        }
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    protected void updateParts()
    {
        if (mParts == null) return;
        Vec3 h = headPos();
        double[][] bell = {{0, 3, 0}, {12, 0, 0}, {-12, 0, 0}, {0, CORE_Y, 0}};
        for (int i = 0; i < BELL_PARTS; i++) mParts[i].setPos(h.x + bell[i][0], h.y + bell[i][1] - 4.0, h.z + bell[i][2]);
        for (int t = 0; t < TENTACLES; t++)
        {
            BossChain c = tentacles[t];
            for (int k = 0; k < PART_JOINTS.length; k++)
            {
                MotherPart p = mParts[BELL_PARTS + t * PART_JOINTS.length + k];
                if (!c.isInitialised())
                {
                    Vec3 a = anchor(t);
                    p.setPos(a.x, a.y - 1.5, a.z);
                }
                else
                {
                    int j = PART_JOINTS[k];
                    p.setPos(c.x(j), c.y(j) - 1.5, c.z(j));
                }
            }
        }
    }

    @Override
    public PartEntity<?>[] getParts() { return mParts != null ? mParts : super.getParts(); }

    @Override
    public void setId(int id)
    {
        super.setId(id);
        if (mParts != null) for (int i = 0; i < mParts.length; i++) mParts[i].setId(id + i + 1);
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        Vec3 h = headPos();
        AABB box = new AABB(h, h).inflate(20);
        for (BossChain c : tentacles) if (c != null && c.isInitialised()) box = box.minmax(c.bounds(3.0));
        return box;
    }

    /** Far silhouette polylines (x y z per point, interpolated at {@code pt}): head, bell top, then per tentacle a break marker and 6 points. */
    public double[] farPoints(float pt)
    {
        double[] p = new double[3 * FAR_POINTS];
        double hx = chain.lerpX(0, pt), hy = chain.lerpY(0, pt), hz = chain.lerpZ(0, pt);
        p[0] = hx;
        p[1] = hy;
        p[2] = hz;
        p[3] = hx;
        p[4] = hy + BELL_TOP;
        p[5] = hz;
        int o = 6;
        for (int t = 0; t < TENTACLES; t++)
        {
            p[o++] = Double.NaN;
            p[o++] = Double.NaN;
            p[o++] = Double.NaN;
            BossChain c = tentacles[t];
            Vec3 a = anchor(t);
            for (int j : FAR_JOINTS)
            {
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

    /** Valid players plus the current target (a dummy); no area search. */
    private List<LivingEntity> candidates()
    {
        List<LivingEntity> list = new ArrayList<>();
        for (ServerPlayer p : ((ServerLevel) level()).players()) if (isValidTarget(p)) list.add(p);
        if (target != null && isValidTarget(target) && !list.contains(target)) list.add(target);
        return list;
    }

    private void setState(State s)
    {
        state = s;
        stateTicks = 0;
    }

    @Override
    protected void onPhaseChange(int newPhase)
    {
        if (newPhase >= 3)
        {
            setState(State.BLOOM);
            setFlag(0, true);
            pulseCd = Math.min(pulseCd, 40);
        }
    }

    @Override
    protected void aiTick()
    {
        if (tickCount % 10 == 0 && !validTarget(target)) target = findTarget(TARGET_RANGE);
        if (!validTarget(target))
        {
            target = null;
        }

        // drift around the arena centre, leaning toward the target; speeds up with the bell beat; stops in BLOOM
        Vec3 base = center;
        if (target != null) base = center.add(target.position().subtract(center).scale(0.4));
        Vec3 point = base.add(Math.cos(tickCount * 0.004) * 20, Math.sin(tickCount * 0.003) * 6, Math.sin(tickCount * 0.004) * 20);
        double beat = Math.max(0.0, beat(0.0F));
        double speed = state == State.BLOOM ? 0.0 : 0.1 + 0.1 * beat;
        steer(point, speed * Math.min(1.0, headPos().distanceTo(point) / 8.0), 0.03);

        stateTicks++;
        if (broodCd > 0) broodCd--;
        switch (state)
        {
            case DRIFT -> { if (phase >= 2 && broodCd <= 0 && target != null && liveSpawns() < MAX_SPAWN) setState(State.BROOD); }
            case STING -> { if (stateTicks >= 20) setState(State.DRIFT); }
            case BROOD ->
            {
                if (stateTicks == 20) brood();
                if (stateTicks >= 40) setState(State.DRIFT);
            }
            case BLOOM -> { if (broodCd <= 0 && target != null && liveSpawns() < MAX_SPAWN) brood(); }
        }

        if (pulseT >= 0) pulseTick();
        else if (--pulseCd <= 0 && target != null) startPulse();

        if (tickCount % 2 == 0) stingCheck();
        if (tickCount % 5 == 0) sendTentacles(null);
    }

    // ---- sting: nearby targets x tentacle segments (no AABB search)

    private void stingCheck()
    {
        Vec3 h = headPos();
        int now = tickCount;
        if (now % 100 == 0) stingCd.values().removeIf(v -> v < now);
        for (LivingEntity e : candidates())
        {
            Vec3 pc = e.getBoundingBox().getCenter();
            if (pc.distanceToSqr(h) > 230.0 * 230.0) continue;
            Integer next = stingCd.get(e.getUUID());
            if (next != null && next > now) continue;
            double r = 2.0 + e.getBbWidth() * 0.5;
            for (BossChain c : tentacles)
            {
                if (!c.isInitialised() || c.distanceSqr(pc.x, pc.y, pc.z) >= r * r) continue;
                sting(e);
                stingCd.put(e.getUUID(), now + 30);
                break;
            }
        }
    }

    private void sting(LivingEntity e)
    {
        e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
        e.hurt(damageSources().mobAttack(this), STING_DAMAGE);
        if (state == State.DRIFT) setState(State.STING);
    }

    // ---- glow pulse: 2 s dark warning, then a flash that needs a line of sight

    private void startPulse()
    {
        pulseT = 0;
        setFlag(1, true);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 8.0F, 0.5F);
    }

    private void pulseTick()
    {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 h = headPos();
        if (pulseT == 0 || pulseT == 20)
            for (LivingEntity e : candidates())
                if (e.getBoundingBox().getCenter().distanceToSqr(h) <= (FLASH_RADIUS + 8) * (FLASH_RADIUS + 8))
                    e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        if (pulseT < PULSE_WARN)
            sl.sendParticles(ParticleTypes.END_ROD, h.x, h.y - 2, h.z, 3, 8.0, 3.0, 8.0, 0.02);
        else if (pulseT == PULSE_WARN)
        {
            setFlag(1, false);
            sl.sendParticles(ParticleTypes.FLASH, h.x, h.y - 2, h.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.END_ROD, h.x, h.y - 2, h.z, 120, 10.0, 4.0, 10.0, 0.3);
            sl.playSound(null, h.x, h.y, h.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 6.0F, 1.6F);
            for (LivingEntity e : candidates())
            {
                Vec3 pc = e.getBoundingBox().getCenter();
                if (pc.distanceToSqr(h) > FLASH_RADIUS * FLASH_RADIUS) continue;
                // blocks in the line of sight stop the flash
                if (level().clip(new ClipContext(h, pc, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
                e.hurt(damageSources().mobAttack(this), FLASH_DAMAGE);
            }
        }
        if (++pulseT >= PULSE_END)
        {
            pulseT = -1;
            pulseCd = state == State.BLOOM ? BLOOM_PULSE_COOLDOWN : PULSE_COOLDOWN;
        }
    }

    // ---- brood

    private int liveSpawns()
    {
        spawns.removeIf(s -> !s.isAlive());
        return spawns.size();
    }

    private void brood()
    {
        broodCd = BROOD_COOLDOWN;
        if (!(level() instanceof ServerLevel sl)) return;
        int n = Math.min(2, MAX_SPAWN - liveSpawns());
        Vec3 h = headPos();
        for (int i = 0; i < n; i++)
        {
            AbyssMotherSpawn s = ModEntities.ABYSS_MOTHER_SPAWN.get().create(sl);
            if (s == null) continue;
            s.setPos(h.x + (random.nextDouble() - 0.5) * 10, h.y - 6, h.z + (random.nextDouble() - 0.5) * 10);
            s.setMother(getUUID());
            if (sl.addFreshEntity(s)) spawns.add(s);
        }
        sl.playSound(null, h.x, h.y, h.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 8.0F, 0.4F);
    }

    @Override
    public void remove(RemovalReason reason)
    {
        for (AbyssMotherSpawn s : spawns) s.discard();
        spawns.clear();
        super.remove(reason);
    }

    // ---- damage

    @Override
    public boolean hurtFromPart(BossPart part, DamageSource source, float amount)
    {
        if (part instanceof MotherPart mp)
        {
            // soft tentacles x0.5; the core is exposed (x2) only while the bell is open, otherwise shielded (x0.5)
            if (mp.tentacle() >= 0) amount *= 0.5F;
            else if (mp.core()) amount *= bloom() ? 2.0F : 0.5F;
        }
        return super.hurtFromPart(part, source, amount);
    }

    // ---- save

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (phase >= 3)
        {
            state = State.BLOOM;
            setFlag(0, true);
        }
    }
}
