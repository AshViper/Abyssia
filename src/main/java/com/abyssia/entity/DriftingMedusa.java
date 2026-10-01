package com.abyssia.entity;

import com.abyssia.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A medusa (jellyfish or hydromedusa): no pathfinding and no goals. It hangs in the water, sinking very slowly, and
 * every so often makes one stroke of its bell - a short push upward (and a little along its heading) that carries it
 * back toward the depth it keeps, then a long glide. On top of that the water carries it: a slow current that is the
 * same for all jellies in a region and turns over hours, so neighbours drift together instead of wandering at random.
 * <p>
 * Senses are checked every 10 ticks (staggered by entity id): the nearest player, the light around it, a player among
 * its tentacles. Bioluminescence is visual only (a glow layer on the model, no light blocks): a faint resting glow, a
 * stronger display while alerted, a flash when struck, fading out as it dies.
 */
public abstract class DriftingMedusa extends DeepSeaSwimmer
{
    protected static final int ACTION_PULSE = 0;
    private static final byte ALERT = 1, ESCAPING = 2, SHED = 4;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(DriftingMedusa.class, EntityDataSerializers.BYTE);
    /** Counts flashes: a change means "flash now" on every client (the action event stays free for the pulse). */
    private static final EntityDataAccessor<Byte> FLASHES = SynchedEntityData.defineId(DriftingMedusa.class, EntityDataSerializers.BYTE);
    /** Size of a current cell (blocks) and how slowly the current turns (ticks for a full change). */
    private static final int CURRENT_CELL = 48, CURRENT_PERIOD = 24000;

    private final ModSounds.Voice voice;
    private int pulseTicks;
    private int alertTicks;
    private int stingCooldown;
    /** The depth it keeps (spawn height, shifted by what it avoids). */
    private double anchorY = Double.NaN;
    private Vec3 current = Vec3.ZERO;
    // client side
    private float glow, glowO, flash, flashO;
    private int alertAge;

    protected DriftingMedusa(EntityType<? extends DriftingMedusa> type, Level level, ModSounds.Voice voice)
    {
        super(type, level, 0, 2, 0.0F);
        this.voice = voice;
        this.pulseTicks = this.random.nextInt(60);
        this.setYRot(this.random.nextFloat() * 360.0F);
    }

    protected static AttributeSupplier.Builder medusaAttributes(double health)
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.MOVEMENT_SPEED, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(STATE, (byte) 0);
        builder.define(FLASHES, (byte) 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);
        if (FLASHES.equals(key) && this.level().isClientSide && this.tickCount > 0) this.flash = 1.0F;
    }

    @Override
    protected void registerGoals()
    {
        // no goals: it drifts and pulses (see tick)
    }

    // ---------------------------------------------------------------- species parameters

    /** Ticks between calm bell strokes: base + random extra. */
    protected abstract int pulseInterval();

    /** Speed a bell stroke adds (blocks per tick). */
    protected abstract double pulseStrength();

    /** Brightness of the resting glow (0 = dark until disturbed). */
    protected float restingGlow()
    {
        return 0.0F;
    }

    /** How far below the bell its tentacles or arms hang (blocks): culling, sting reach. */
    protected double hangBelow()
    {
        return 0.5;
    }

    /** Sting damage of a player caught in its tentacles (0: it does not sting). */
    protected float stingDamage()
    {
        return 0.0F;
    }

    /** A player was stung (after the damage landed): add effects. */
    protected void onSting(Player player)
    {
    }

    /** Every 10 ticks, in water, alive: react to the nearest player within {@link #senseRange()} (or none). */
    protected void sense(@Nullable Player near)
    {
    }

    protected double senseRange()
    {
        return 6.0;
    }

    /** Struck (server side, still alive). */
    protected void onStruck(DamageSource source)
    {
        this.flash();
    }

    /** Extra height it keeps on top of its anchor (diel migrators rise by night). */
    protected double anchorOffset()
    {
        return 0.0;
    }

    // ---------------------------------------------------------------- state

    private boolean has(byte flag)
    {
        return (this.entityData.get(STATE) & flag) != 0;
    }

    private void set(byte flag, boolean on)
    {
        byte s = this.entityData.get(STATE);
        this.entityData.set(STATE, (byte) (on ? s | flag : s & ~flag));
    }

    /** Its light display (and tentacle spread) is on. */
    public boolean isAlerted()
    {
        return this.has(ALERT);
    }

    public boolean isEscaping()
    {
        return this.has(ESCAPING);
    }

    /** Has cast off its tentacles (they grow back). */
    public boolean hasShedTentacles()
    {
        return this.has(SHED);
    }

    protected void setShedTentacles(boolean shed)
    {
        this.set(SHED, shed);
    }

    /** Starts (or prolongs) the alert display for {@code ticks}; true when it was not on yet. */
    protected boolean alert(int ticks)
    {
        boolean started = !this.isAlerted();
        this.alertTicks = Math.max(this.alertTicks, ticks);
        this.set(ALERT, true);
        return started;
    }

    /** A bright flash on every client. */
    protected void flash()
    {
        this.entityData.set(FLASHES, (byte) (this.entityData.get(FLASHES) + 1));
    }

    /** Escape: rapid strokes away from {@code from}. */
    protected void escape(Vec3 from, int ticks)
    {
        this.startFleeing(from, ticks);
        this.pulseTicks = Math.min(this.pulseTicks, 2);
    }

    /** Moves the depth it keeps by {@code dy} blocks. */
    protected void shiftAnchor(double dy)
    {
        if (!Double.isNaN(this.anchorY)) this.anchorY += dy;
    }

    // ---------------------------------------------------------------- behaviour

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.clientTick();
            return;
        }
        if (this.alertTicks > 0 && --this.alertTicks == 0) this.set(ALERT, false);
        this.set(ESCAPING, this.isFleeing());
        if (!this.isInWater() || this.isDeadOrDying() || this.isNoAi()) return;
        if (Double.isNaN(this.anchorY)) this.anchorY = this.getY();
        int phase = this.tickCount + this.getId();
        if (phase % 20 == 0) this.current = this.currentAt();
        // between strokes: sink slowly and go with the water
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.x + this.current.x, v.y - 8.0E-4, v.z + this.current.z);
        if (this.horizontalCollision) this.current = this.current.scale(-1.0);
        if (--this.pulseTicks <= 0) this.pulse();
        if (this.stingCooldown > 0) --this.stingCooldown;
        if (phase % 10 == 0)
        {
            Player near = this.level().getNearestPlayer(this, this.senseRange());
            this.sense(near != null && !near.isSpectator() ? near : null);
            if (this.stingDamage() > 0 && this.stingCooldown == 0) this.stingTouching();
        }
    }

    /**
     * The regional current: a direction per 48-block cell, blended across cells and turning slowly with the time of
     * day, so jellies in one area drift the same way.
     */
    private Vec3 currentAt()
    {
        double t = this.level().getGameTime() / (double) CURRENT_PERIOD;
        double cx = this.getX() / CURRENT_CELL, cz = this.getZ() / CURRENT_CELL;
        double angle = Math.sin(cx * 0.9 + t) * 2.1 + Math.cos(cz * 0.7 - t * 0.8) * 2.3 + t * 1.3;
        double strength = 6.0E-4 * (0.6 + 0.4 * Math.sin(cx * 0.5 + cz * 0.6 + t * 2.0));
        return new Vec3(Math.cos(angle) * strength, 0.0, Math.sin(angle) * strength);
    }

    /** One bell stroke: back toward its depth, or away from a threat when escaping. */
    private void pulse()
    {
        boolean escaping = this.isFleeing();
        Vec3 dir;
        if (escaping)
        {
            dir = this.position().subtract(this.fleeFrom());
            if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 1, 0);
            dir = dir.normalize();
            this.pulseTicks = 6 + this.random.nextInt(4);
        }
        else
        {
            // wander the heading a little each stroke; rise as far as it is below its depth
            this.setYRot(this.getYRot() + (this.random.nextFloat() - 0.5F) * 50.0F);
            this.yBodyRot = this.getYRot();
            Vec3 heading = Vec3.directionFromRotation(0.0F, this.getYRot());
            double below = this.anchorY + this.anchorOffset() - this.getY();
            double up = Mth.clamp(0.45 + below * 0.2, 0.0, 1.0);
            if (!this.level().getFluidState(this.blockPosition().above(2)).isSource()) up = 0.0;
            dir = new Vec3(heading.x * 0.35, up, heading.z * 0.35);
            this.pulseTicks = this.pulseInterval() + this.random.nextInt(this.pulseInterval() / 2 + 1);
        }
        this.addDeltaMovement(dir.scale(this.pulseStrength() * (escaping ? 1.6 : 1.0)));
        if (!escaping) this.broadcastAction(ACTION_PULSE);
        else if (this.random.nextInt(3) == 0) this.playSound(this.voice.get("pulse"), this.getSoundVolume(), this.getVoicePitch());
    }

    /** A player among the tentacles is stung (at most once a second). */
    private void stingTouching()
    {
        double spread = this.getBbWidth() * 0.35;
        AABB reach = this.getBoundingBox().expandTowards(0.0, -this.hangBelow(), 0.0).inflate(spread, 0.0, spread);
        for (Player player : this.level().getEntitiesOfClass(Player.class, reach, p -> p.isAlive() && !p.isSpectator()))
        {
            if (player.hurt(this.damageSources().mobAttack(this), this.stingDamage())) this.onSting(player);
            this.stingCooldown = 20;
            return;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive()) this.onStruck(source);
        return hurt;
    }

    // ---------------------------------------------------------------- client: animation and light

    private void clientTick()
    {
        this.glowO = this.glow;
        this.flashO = this.flash;
        boolean alerted = this.isAlerted();
        this.alertAge = alerted ? this.alertAge + 1 : 0;
        float target = this.restingGlow() + (alerted ? 1.0F - this.restingGlow() : 0.0F);
        this.glow += (target - this.glow) * (alerted ? 0.2F : 0.05F);
        this.flash = Math.max(0.0F, this.flash - 0.07F);
        this.animations().tick("swim", this.isEscaping() && this.isInWater());
        this.animations().when("spread", alerted && !this.isDeadOrDying());
    }

    @Override
    protected void onAction(int action)
    {
        if (action == ACTION_PULSE) this.animations().play("pulse", 28);
    }

    /** 0..1: fades out over the death clip. */
    private float alive(float partial)
    {
        return this.deathTime <= 0 ? 1.0F : 1.0F - Mth.clamp((this.deathTime + partial) / DEATH_TICKS, 0.0F, 1.0F);
    }

    /** 0..1: the light of its luminous organs this frame (resting glow / alert display / flash, fading at death). */
    public float glow(float partial)
    {
        return Math.min(1.0F, Mth.lerp(partial, this.glowO, this.glow) + Mth.lerp(partial, this.flashO, this.flash)) * this.alive(partial);
    }

    /** Ticks the current alert display has been running (client side). */
    public float alertAge(float partial)
    {
        return this.alertAge == 0 ? 0.0F : this.alertAge + partial;
    }

    /** The alert part of the glow alone (0..1), without the resting glow and flash. */
    public float alertLevel(float partial)
    {
        float g = Mth.lerp(partial, this.glowO, this.glow);
        return Mth.clamp((g - this.restingGlow()) / Math.max(0.01F, 1.0F - this.restingGlow()), 0.0F, 1.0F) * this.alive(partial);
    }

    public float flashLevel(float partial)
    {
        return Mth.lerp(partial, this.flashO, this.flash) * this.alive(partial);
    }

    // ---------------------------------------------------------------- misc

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().expandTowards(0.0, -this.hangBelow(), 0.0).inflate(0.8);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (!Double.isNaN(this.anchorY)) tag.putDouble("AnchorY", this.anchorY);
        tag.putBoolean("ShedTentacles", this.hasShedTentacles());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains("AnchorY")) this.anchorY = tag.getDouble("AnchorY");
        this.setShedTentacles(tag.getBoolean("ShedTentacles"));
    }

    /** Keeps gliding between strokes. */
    @Override
    protected double waterDrag()
    {
        return 0.93;
    }

    @Override
    protected boolean flopsOnLand()
    {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.voice.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 240;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return this.voice.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return this.voice.get("death");
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return this.voice.get("hurt");
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.35F;
    }
}
