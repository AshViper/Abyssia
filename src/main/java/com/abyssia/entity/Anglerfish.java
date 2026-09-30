package com.abyssia.entity;

import com.abyssia.entity.ai.PreyStrikeGoal;
import com.abyssia.entity.ai.ScoredSwimGoal;
import com.abyssia.registry.ModParticles;
import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * チョウチンアンコウ: the female Atlantic footballfish (Himantolophus groenlandicus), a bathypelagic ambush predator.
 * <p>
 * It hangs almost motionless in dark water with its lure lit (in situ, an Oneirodes drifted passively for 74 % of a
 * 24 minute dive), now and then drifting a short way to darker water. Small fish drawn to the light are engulfed in
 * a sudden strike, after which it digests for a while. It never hunts players: one that comes close gets a gaping
 * threat display, one that stays at its jaws is bitten once, and when hurt it darkens its lure and flees.
 * (Whether anglerfish actively dim the esca is not settled; here it is a game simplification.)
 */
public class Anglerfish extends DeepSeaSwimmer implements LureBearer
{
    public static final byte IDLE = 0, THREAT = 1, FLEE = 2;
    private static final EntityDataAccessor<Byte> MOOD = SynchedEntityData.defineId(Anglerfish.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DIGESTING = SynchedEntityData.defineId(Anglerfish.class, EntityDataSerializers.BOOLEAN);
    private static final byte EVENT_STRIKE = 61;
    private static final double THREAT_RANGE = 4.5;

    private int digestTicks;
    private int fleeTicks;
    @Nullable
    private Vec3 fleeFrom;
    private int threatCooldown;

    // client side: the lure's brightness (eased), the strike in progress, the mood last tick
    private float lure = 1.0F, lureO = 1.0F;
    private int strikeTicks = -1;
    private byte shownMood = IDLE;

    public Anglerfish(EntityType<? extends Anglerfish> type, Level level)
    {
        // turns slowly; cruising thrust 0.002 -> about 0.4 blocks/s at speed modifier 1
        super(type, level, 20, 5, 0.002F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 12.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 12.0);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(MOOD, IDLE);
        this.entityData.define(DIGESTING, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FleeGoal());
        this.goalSelector.addGoal(1, new PreyStrikeGoal(this, ModTags.ANGLERFISH_PREY, 2.2, 0.3, () -> !this.isDigesting() && this.mood() != FLEE,
                this::mouthPosition, this::strike, prey -> {
                    this.heal(4.0F);
                    this.digest();
                }));
        this.goalSelector.addGoal(2, new ThreatDisplayGoal());
        // now and then it repositions a few blocks, preferring the darkest water it can find
        this.goalSelector.addGoal(4, new ScoredSwimGoal(this, 240, 1.0, 8, 4, 240, () -> this.mood() == IDLE,
                pos -> ScoredSwimGoal.darkness(this.level(), pos) * 4.0));
        this.goalSelector.addGoal(5, new SlowTurnGoal());
    }

    // ---------------------------------------------------------------- state

    public byte mood()
    {
        return this.entityData.get(MOOD);
    }

    private void setMood(byte mood)
    {
        this.entityData.set(MOOD, mood);
    }

    @Override
    public boolean isDigesting()
    {
        return this.entityData.get(DIGESTING);
    }

    /** The lure is lit (and draws small fish) unless the anglerfish is fleeing or out of water. */
    @Override
    public boolean isLureLit()
    {
        return this.isAlive() && this.isInWater() && this.mood() != FLEE;
    }

    /** Where the esca hangs: in front of and above the mouth. */
    @Override
    public Vec3 lurePosition()
    {
        return this.bodyPoint(0, 0.27, 0.48);
    }

    public Vec3 mouthPosition()
    {
        return this.bodyPoint(0, -0.02, 0.36);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.lureO = this.lure;
            byte mood = this.mood();
            FaunaAnimations clips = this.animations();
            clips.tick("swim", this.isSwimmingNow());
            // threat display: the jaw gapes and holds; a strike opens and snaps it shut
            if (mood == THREAT && this.shownMood != THREAT) clips.play("mouth_open", 0);
            if (mood != THREAT && this.shownMood == THREAT) this.closeJaw();
            this.shownMood = mood;
            if (this.strikeTicks >= 0 && ++this.strikeTicks == 3)
            {
                this.closeJaw();
                this.strikeTicks = -1;
            }
            float lureTarget = mood == FLEE || !this.isInWater() ? 0.04F : mood == THREAT ? 1.0F : this.isDigesting() ? 0.7F : 0.9F;
            this.lure += (lureTarget - this.lure) * (lureTarget < this.lure ? 0.25F : 0.05F);
            clips.when("glow", this.lure > 0.5F);
            // now and then a mote of light drifts off the esca
            if (this.lure > 0.5F && this.random.nextInt(50) == 0)
            {
                Vec3 p = this.lurePosition();
                this.level().addParticle(ModParticles.GLOW_DUST.get(), p.x, p.y, p.z, 0.0, 0.005, 0.0);
            }
        }
        else
        {
            if (this.digestTicks > 0 && --this.digestTicks == 0) this.entityData.set(DIGESTING, false);
            if (this.threatCooldown > 0) --this.threatCooldown;
        }
    }

    private void closeJaw()
    {
        this.animations().stop("mouth_open");
        this.animations().play("mouth_close", 8);
    }

    @Override
    public void handleEntityEvent(byte id)
    {
        if (id == EVENT_STRIKE)
        {
            this.strikeTicks = 0;
            this.animations().play("mouth_open", 0);
        }
        else
        {
            super.handleEntityEvent(id);
        }
    }

    public float lure(float partial)
    {
        return Mth.lerp(partial, this.lureO, this.lure);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide)
        {
            Entity attacker = source.getEntity();
            this.startFleeing(attacker != null ? attacker.position() : this.position().add(this.getLookAngle()));
        }
        return hurt;
    }

    private void startFleeing(Vec3 from)
    {
        this.fleeFrom = from;
        this.fleeTicks = 100 + this.random.nextInt(60);
        this.setMood(FLEE);
    }

    private void digest()
    {
        this.digestTicks = 2400 + this.random.nextInt(1200);
        this.entityData.set(DIGESTING, true);
    }

    /** The jaws snap open and shut (the model plays it on the entity event). */
    private void strike()
    {
        this.level().broadcastEntityEvent(this, EVENT_STRIKE);
        this.playSound(ModSounds.ANGLERFISH_SNAP.get(), 0.8F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("DigestTicks", this.digestTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.digestTicks = tag.getInt("DigestTicks");
        this.entityData.set(DIGESTING, this.digestTicks > 0);
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.ANGLERFISH_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 400;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.ANGLERFISH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.ANGLERFISH_DEATH.get();
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return ModSounds.ANGLERFISH_FLOP.get();
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.6F;
    }

    // ---------------------------------------------------------------- behaviour

    /** Hurt: lure dark, swim hard away from the attacker for a few seconds. */
    private class FleeGoal extends Goal
    {
        FleeGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public boolean canUse()
        {
            return Anglerfish.this.fleeTicks > 0 && Anglerfish.this.isInWater();
        }

        @Override
        public void start()
        {
            this.flee();
        }

        @Override
        public void tick()
        {
            if (--Anglerfish.this.fleeTicks > 0 && Anglerfish.this.getNavigation().isDone()) this.flee();
        }

        private void flee()
        {
            Vec3 from = Anglerfish.this.fleeFrom != null ? Anglerfish.this.fleeFrom : Anglerfish.this.position();
            Vec3 to = DefaultRandomPos.getPosAway(Anglerfish.this, 10, 5, from);
            if (to != null && Anglerfish.this.level().getFluidState(BlockPos.containing(to)).is(FluidTags.WATER))
            {
                Anglerfish.this.getNavigation().moveTo(to.x, to.y, to.z, 4.0);
            }
        }

        @Override
        public void stop()
        {
            Anglerfish.this.fleeTicks = 0;
            Anglerfish.this.getNavigation().stop();
            if (Anglerfish.this.mood() == FLEE) Anglerfish.this.setMood(IDLE);
        }
    }

    /** A player drifting too close: turn to face them and gape; one who stays at the jaws is bitten, then it flees. */
    private class ThreatDisplayGoal extends Goal
    {
        @Nullable
        private Player player;
        private int ticks, duration, closeTicks;

        ThreatDisplayGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public boolean canUse()
        {
            Anglerfish self = Anglerfish.this;
            if (self.threatCooldown > 0 || self.mood() == FLEE || !self.isInWater() || self.random.nextInt(3) != 0) return false;
            this.player = self.level().getNearestPlayer(self.getX(), self.getY(), self.getZ(), THREAT_RANGE,
                    p -> !p.isSpectator() && p.isAlive() && self.hasLineOfSight(p));
            return this.player != null;
        }

        @Override
        public void start()
        {
            this.ticks = 0;
            this.closeTicks = 0;
            this.duration = 50 + Anglerfish.this.random.nextInt(40);
            Anglerfish.this.getNavigation().stop();
            Anglerfish.this.setMood(THREAT);
            Anglerfish.this.playSound(ModSounds.ANGLERFISH_THREAT.get(), 0.8F, 0.85F + Anglerfish.this.random.nextFloat() * 0.3F);
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.player != null && this.player.isAlive() && this.ticks < this.duration && Anglerfish.this.mood() == THREAT
                    && Anglerfish.this.distanceToSqr(this.player) < 7.0 * 7.0;
        }

        @Override
        public void tick()
        {
            Anglerfish self = Anglerfish.this;
            ++this.ticks;
            self.getLookControl().setLookAt(this.player, 30.0F, 30.0F);
            if (self.distanceToSqr(this.player) < 1.7 * 1.7 && !this.player.getAbilities().invulnerable)
            {
                if (++this.closeTicks > 16)
                {
                    self.strike();
                    this.player.hurt(self.damageSources().mobAttack(self), (float) self.getAttributeValue(Attributes.ATTACK_DAMAGE));
                    self.startFleeing(this.player.position());
                }
            }
            else
            {
                this.closeTicks = Math.max(0, this.closeTicks - 1);
            }
        }

        @Override
        public void stop()
        {
            if (Anglerfish.this.mood() == THREAT) Anglerfish.this.setMood(IDLE);
            Anglerfish.this.threatCooldown = 80 + Anglerfish.this.random.nextInt(60);
            this.player = null;
        }
    }

    /** Hovering: now and then a slow turn to a new heading, as a drifting fish does. */
    private class SlowTurnGoal extends Goal
    {
        private float targetYaw;
        private int ticks;

        SlowTurnGoal()
        {
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public boolean canUse()
        {
            if (Anglerfish.this.mood() != IDLE || !Anglerfish.this.getNavigation().isDone() || Anglerfish.this.random.nextInt(160) != 0) return false;
            this.targetYaw = Anglerfish.this.getYRot() + (Anglerfish.this.random.nextFloat() - 0.5F) * 120.0F;
            this.ticks = 0;
            return true;
        }

        @Override
        public boolean canContinueToUse()
        {
            return ++this.ticks < 80 && Anglerfish.this.getNavigation().isDone();
        }

        @Override
        public void tick()
        {
            Anglerfish self = Anglerfish.this;
            float rot = Mth.approachDegrees(self.getYRot(), this.targetYaw, 1.2F);
            self.setYRot(rot);
            self.yBodyRot = rot;
            self.yHeadRot = rot;
        }
    }
}
