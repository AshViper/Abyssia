package com.abyssia.entity.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Trench Wyrm, leviathan scale (BOSS02): the original 5.5-block-thick design drawn {@link #SIZE} times thicker
 * (about 16 blocks across), head + 26 joints of 12 blocks (about 312). It passes through terrain (noPhysics, terrain is never changed):
 * BURROW (circles under the target, harmless and untouchable) -> OMEN (2 s of dust and rumbling under the target's floor)
 * -> ERUPT (shoots up through the target, bite 18; phase 3 also rains rock shards) -> ARC (exposed in the water, 3-5 s)
 * -> [SWEEP, phase 2+: a lateral sweep around the target] -> DIVE (back into the floor) -> BURROW.
 * Floor and wall checks are single block samples, no searches.
 */
public class TrenchWyrm extends AbyssBoss implements software.bernie.geckolib.animatable.GeoEntity
{
    /** GeckoLib (near view only): the idle animation drives the bristles; the renderer places the spine bones and opens the mandibles. */
    private final software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache geoCache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<TrenchWyrm>(this, "main", 0,
                state -> state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("idle"))));
    }

    @Override
    public software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return geoCache;
    }

    /** Cross-section scale against the original model (shared with the renderer and the far silhouette). */
    public static final double SIZE = 3.0;
    /** Swim speeds against the original. */
    private static final double SPEED = 1.5;
    /** AI distances (orbits, eruption height, arc, sweep) against the original. */
    private static final double K = 2.5;
    private static final double TARGET_RANGE = 160;
    private static final int OMEN_TICKS = 40;
    private static final float BITE_DAMAGE = 18.0F;
    private static final float SWEEP_DAMAGE = 12.0F;
    private static final double SWEEP_RANGE = 12.0 * K;
    private static final double BURY_DEPTH = 8 * SIZE;

    private enum State { BURROW, OMEN, ERUPT, ARC, SWEEP, DIVE }
    /** maw open (ERUPT / SWEEP until the bite lands): drives the jaw plates in TrenchWyrmRenderer */
    private static final EntityDataAccessor<Boolean> DATA_MAW = SynchedEntityData.defineId(TrenchWyrm.class, EntityDataSerializers.BOOLEAN);

    private State state = State.BURROW;
    private int stateTicks;
    private int stateLength = 120;
    @Nullable
    private LivingEntity target;
    private double angle;
    private int dir = 1;
    private double floorY = Double.NaN;
    private boolean bitten;
    private Vec3 arcStart = Vec3.ZERO;
    private Vec3 arcDir = new Vec3(1, 0, 0);
    private final Set<UUID> sweepHit = new HashSet<>();

    public TrenchWyrm(EntityType<? extends TrenchWyrm> type, Level level)
    {
        super(type, level, 1 + 26, 12.0);
    }

    @Override
    public double size() { return SIZE; }

    @Override
    public double bodyRadius() { return 2.75 * SIZE; }

    @Override
    protected int jointsPerPart() { return 1; }

    @Override
    protected double arenaScale() { return 1.4; }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_MAW, false);
    }

    public boolean mawOpen() { return entityData.get(DATA_MAW); }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 2000.0).add(Attributes.ARMOR, 14.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.FOLLOW_RANGE, 160.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected double designHealth() { return 2000.0; }

    @Override
    protected int farKind() { return 1; }

    @Override
    protected String stateName() { return state.name(); }

    // ---- underground rules

    private boolean underground() { return state == State.BURROW || state == State.OMEN; }

    @Override
    protected boolean contactActive() { return !underground(); }

    @Override
    protected boolean attackable(@Nullable BossPart part)
    {
        if (underground()) return false;
        Vec3 p = part == null ? headPos() : new Vec3(part.getX(), part.getY() + part.getBbHeight() * 0.5, part.getZ());
        return !solidAt(p.x, p.y, p.z);
    }

    private boolean solidAt(double x, double y, double z)
    {
        BlockPos pos = BlockPos.containing(x, y, z);
        return level().hasChunkAt(pos) && !level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty();
    }

    /** Top surface of the first solid block going down from {@code (x, startY, z)} (at most {@code maxDown} blocks), else {@code startY - fallback}. One sample per block. */
    private double floorBelow(double x, double startY, double z, int maxDown, double fallback)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(startY), Mth.floor(z));
        for (int i = 0; i <= maxDown; i++, pos.move(0, -1, 0))
        {
            if (!level().hasChunkAt(pos)) break;
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) return pos.getY() + 1.0;
        }
        return startY - fallback;
    }

    // ---- AI

    private void setState(State s, int length)
    {
        state = s;
        stateTicks = 0;
        entityData.set(DATA_MAW, s == State.ERUPT || s == State.SWEEP);
        stateLength = length;
        Vec3 head = headPos();
        switch (s)
        {
            case BURROW ->
            {
                Vec3 rel = head.subtract(target != null ? target.position() : center);
                angle = Math.atan2(rel.z, rel.x);
                dir = random.nextBoolean() ? 1 : -1;
            }
            case ERUPT ->
            {
                bitten = false;
                level().playSound(null, head.x, head.y, head.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 8.0F, 0.5F);
                if (phase >= 3 && target != null) rockRain();
            }
            case ARC ->
            {
                arcStart = head;
                double a = target != null ? Math.atan2(head.z - target.getZ(), head.x - target.getX()) : random.nextDouble() * Math.PI * 2;
                // toward the arena centre when far from it, otherwise a random sideways heading
                Vec3 toCentre = center.subtract(head);
                if (toCentre.horizontalDistanceSqr() > 40.0 * K * 40.0 * K) a = Math.atan2(toCentre.z, toCentre.x) + (random.nextDouble() - 0.5);
                else a += (random.nextBoolean() ? 1 : -1) * (0.8 + random.nextDouble());
                arcDir = new Vec3(Math.cos(a), 0, Math.sin(a));
            }
            case SWEEP ->
            {
                sweepHit.clear();
                Vec3 rel = head.subtract(target.position());
                angle = Math.atan2(rel.z, rel.x);
                dir = random.nextBoolean() ? 1 : -1;
            }
            case DIVE -> floorY = floorBelow(head.x, head.y + 2, head.z, 64, 20);
            default -> {}
        }
    }

    private boolean validTarget(@Nullable LivingEntity p)
    {
        return p != null && isValidTarget(p) && p.level() == level() && p.distanceToSqr(this) <= TARGET_RANGE * TARGET_RANGE;
    }

    @Override
    protected void aiTick()
    {
        if (tickCount % 10 == 0 && !validTarget(target)) target = findTarget(TARGET_RANGE);
        if (!validTarget(target))
        {
            target = null;
            if (state == State.OMEN || state == State.ERUPT || state == State.SWEEP) setState(State.DIVE, 80);
            else if (state == State.BURROW)
            {
                orbit(center, 40 * K, center.y - BURY_DEPTH, 0.6 * SPEED);
                return;
            }
        }
        stateTicks++;
        Vec3 head = headPos();
        if (target != null && state != State.ARC && state != State.DIVE && (Double.isNaN(floorY) || tickCount % 5 == 0))
            floorY = floorBelow(target.getX(), target.getY() + 1, target.getZ(), 64, 12);
        switch (state)
        {
            case BURROW ->
            {
                orbit(target.position(), 30 * K, floorY - BURY_DEPTH, 1.3 * SPEED);
                if (stateTicks >= stateLength) setState(State.OMEN, OMEN_TICKS);
            }
            case OMEN ->
            {
                Vec3 under = new Vec3(target.getX(), floorY - BURY_DEPTH, target.getZ());
                steer(under, Mth.clamp(head.distanceTo(under) * 0.15, 0.1, 1.5 * SPEED), 0.25);
                omenEffects();
                if (stateTicks >= stateLength) setState(State.ERUPT, 60);
            }
            case ERUPT ->
            {
                Vec3 tp = target.position().add(0, target.getBbHeight() * 0.5, 0);
                // keep the aim line above the target so the head passes through it and keeps rising
                steer(new Vec3(head.x + (tp.x - head.x) * 0.5, Math.max(tp.y, head.y) + 24 * K, head.z + (tp.z - head.z) * 0.5), 2.0 * SPEED, 0.3);
                if (!bitten && head.distanceTo(tp) < 5.0 * SIZE) bite();
                if (stateTicks >= 12 && (head.y > tp.y + 14 * K || stateTicks >= stateLength)) setState(State.ARC, 60 + random.nextInt(41));
            }
            case ARC ->
            {
                double t = Math.min(1.0, stateTicks / (double) stateLength);
                Vec3 aim = arcStart.add(arcDir.scale(55 * K * t)).add(0, (14 * Math.sin(Math.PI * t) - 26 * t) * K, 0);
                steer(aim, Mth.clamp(head.distanceTo(aim) * 0.3, 0.4, 1.6 * SPEED), 0.25);
                if (stateTicks >= stateLength)
                {
                    if (phase >= 2 && target != null && random.nextDouble() < 0.6 && head.distanceTo(target.position()) < 70 * K) setState(State.SWEEP, 50);
                    else setState(State.DIVE, 80);
                }
            }
            case SWEEP ->
            {
                orbit(target.position(), 10 * K, target.getY() + 2, 1.5 * SPEED);
                if (tickCount % 2 == 0) sweepDamage();
                if (stateTicks >= stateLength) setState(State.DIVE, 80);
            }
            case DIVE ->
            {
                if (tickCount % 5 == 0 && stateTicks > 5) floorY = floorBelow(head.x, head.y + 2, head.z, 64, 20);
                Vec3 h = vel.multiply(1, 0, 1);
                Vec3 ahead = h.lengthSqr() > 1.0E-4 ? h.normalize().scale(10 * K) : Vec3.ZERO;
                steer(new Vec3(head.x + ahead.x, floorY - BURY_DEPTH - 4 * K, head.z + ahead.z), 1.6 * SPEED, 0.25);
                if (head.y < floorY - 3 * SIZE || stateTicks >= stateLength) setState(State.BURROW, 100 + random.nextInt(60));
            }
        }
    }

    /** Orbits {@code c} at {@code radius} on the plane {@code y}: the aim point runs ahead on the circle. */
    private void orbit(Vec3 c, double radius, double y, double speed)
    {
        angle += dir * speed / Math.max(radius, 8.0);
        steer(new Vec3(c.x + Math.cos(angle) * radius, y, c.z + Math.sin(angle) * radius), speed, 0.1);
    }

    private void omenEffects()
    {
        if (!(level() instanceof ServerLevel sl) || target == null) return;
        double x = target.getX(), z = target.getZ();
        if (stateTicks % 2 == 0)
        {
            BlockState bs = sl.getBlockState(BlockPos.containing(x, floorY - 0.5, z));
            if (!bs.isAir())
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), x, floorY + 0.4, z, 14, 2.5, 0.3, 2.5, 0.08);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, floorY + 0.6, z, 3, 3.0, 0.2, 3.0, 0.01);
        }
        if (stateTicks % 8 == 1) sl.playSound(null, x, floorY, z, SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 6.0F, 0.5F + stateTicks * 0.01F);
    }

    private void bite()
    {
        bitten = true;
        entityData.set(DATA_MAW, false);
        if (target == null) return;
        target.hurt(damageSources().mobAttack(this), BITE_DAMAGE);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 8.0F, 0.3F);
    }

    private void sweepDamage()
    {
        java.util.List<LivingEntity> victims = new java.util.ArrayList<>(level().players());
        victims.addAll(level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SWEEP_RANGE + 4),
                x -> x != this && !(x instanceof net.minecraft.world.entity.player.Player)));
        Vec3 head = headPos();
        for (LivingEntity p : victims)
        {
            if (!isValidTarget(p) || sweepHit.contains(p.getUUID())) continue;
            Vec3 pc = p.getBoundingBox().getCenter();
            if (pc.distanceToSqr(head) > SWEEP_RANGE * SWEEP_RANGE) continue;
            double dx = pc.x - head.x, dz = pc.z - head.z;
            sweepHit.add(p.getUUID());
            p.hurt(damageSources().mobAttack(this), SWEEP_DAMAGE);
            double len = Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-3);
            p.setDeltaMovement(p.getDeltaMovement().add(dx / len * 1.6, 0.4, dz / len * 1.6));
            p.hurtMarked = true;
        }
    }

    /** Phase 3: 6-10 rock shards fall around the target when the wyrm erupts. */
    private void rockRain()
    {
        int n = 6 + random.nextInt(5);
        Vec3 t = target.position();
        for (int i = 0; i < n; i++)
        {
            double a = random.nextDouble() * Math.PI * 2, r = 3 + random.nextDouble() * 25;
            RockShard rock = new RockShard(level(), this);
            rock.setPos(t.x + Math.cos(a) * r, t.y + 14 + random.nextDouble() * 10, t.z + Math.sin(a) * r);
            rock.setDeltaMovement(0, -0.3, 0);
            level().addFreshEntity(rock);
        }
    }
}
