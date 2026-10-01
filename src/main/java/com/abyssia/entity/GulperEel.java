package com.abyssia.entity;

import com.abyssia.entity.ai.ScoredSwimGoal;
import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * フクロウナギ: the gulper (pelican) eel Eurypharynx pelecanoides.
 * <p>
 * A slow, undulating swimmer that here keeps mostly to dark cave water (a game choice: the real fish lives in open
 * midwater). Every so often it throws its enormous jaws open to engulf water and whatever small crustaceans are in
 * it, its main food. A player coming close sets off the display filmed by Nautilus in 2018: the mouth balloons into
 * a black sphere, then deflates and the eel swims off. Its whip tail ends in a light organ that glows pink with
 * occasional red flashes (drawn by the renderer).
 */
public class GulperEel extends DeepSeaSwimmer
{
    public static final byte CRUISE = 0, GULP = 1, BALLOON = 2, FLEE = 3;
    private static final EntityDataAccessor<Byte> MOOD = SynchedEntityData.defineId(GulperEel.class, EntityDataSerializers.BYTE);
    private static final int INFLATED_TICKS = 34;

    private int gulpCooldown = 200;
    private int displayCooldown;
    private boolean startled;
    @Nullable
    private Vec3 threatFrom;

    // client side: the balloon's swelling (eased), the mood last tick
    private float balloon, balloonO;
    private byte shownMood = CRUISE;

    public GulperEel(EntityType<? extends GulperEel> type, Level level)
    {
        // cruising thrust 0.0016 -> about 0.3 blocks/s
        super(type, level, 25, 4, 0.0016F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 1.0).add(Attributes.FOLLOW_RANGE, 10.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(MOOD, CRUISE);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new BalloonGoal());
        this.goalSelector.addGoal(1, new GulpGoal());
        // slow wandering that favours enclosed, dark water: the cave passages it keeps to
        this.goalSelector.addGoal(2, new ScoredSwimGoal(this, 50, 1.0, 10, 5, 300, () -> this.mood() == CRUISE,
                pos -> ScoredSwimGoal.enclosure(this.level(), pos) * 1.5 + ScoredSwimGoal.darkness(this.level(), pos) * 0.4));
    }

    public byte mood()
    {
        return this.entityData.get(MOOD);
    }

    private void setMood(byte mood)
    {
        this.entityData.set(MOOD, mood);
    }

    public Vec3 mouthPosition()
    {
        return this.bodyPoint(0, -0.05, 0.4);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.balloonO = this.balloon;
            byte mood = this.mood();
            FaunaAnimations clips = this.animations();
            clips.tick("swim", this.isSwimmingNow());
            // the gulp gapes the jaws; the balloon keeps them shut and swells the pouch (see GulperEelModel)
            boolean open = mood == GULP;
            boolean wasOpen = this.shownMood == GULP;
            if (open && !wasOpen) clips.play("mouth_open", 0);
            if (!open && wasOpen)
            {
                clips.stop("mouth_open");
                clips.play("mouth_close", 8);
            }
            this.shownMood = mood;
            clips.when("glow", this.isInWater());
            this.balloon += ((mood == BALLOON ? 1.0F : 0.0F) - this.balloon) * (mood == BALLOON ? 0.12F : 0.2F);
        }
        else if (this.displayCooldown > 0)
        {
            --this.displayCooldown;
        }
    }

    public float balloon(float partial)
    {
        return Mth.lerp(partial, this.balloonO, this.balloon);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide)
        {
            Entity attacker = source.getEntity();
            this.startled = true;
            this.threatFrom = attacker != null ? attacker.position() : this.position().add(this.getLookAngle());
        }
        return hurt;
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.GULPER_EEL_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 360;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.GULPER_EEL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.GULPER_EEL_DEATH.get();
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return ModSounds.GULPER_EEL_FLOP.get();
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.6F;
    }

    // ---------------------------------------------------------------- behaviour

    /** Threatened: the mouth balloons into a black sphere, deflates, and the eel swims away. */
    private class BalloonGoal extends Goal
    {
        private int ticks;

        BalloonGoal()
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
            GulperEel self = GulperEel.this;
            if (!self.isInWater()) return false;
            if (self.startled) return true;
            if (self.displayCooldown > 0 || self.random.nextInt(3) != 0) return false;
            Player player = self.level().getNearestPlayer(self.getX(), self.getY(), self.getZ(), 3.0,
                    p -> !p.isSpectator() && p.isAlive() && self.hasLineOfSight(p));
            if (player == null) return false;
            self.threatFrom = player.position();
            return true;
        }

        @Override
        public void start()
        {
            GulperEel self = GulperEel.this;
            this.ticks = 0;
            self.startled = false;
            self.getNavigation().stop();
            self.setMood(BALLOON);
            self.playSound(ModSounds.GULPER_EEL_INFLATE.get(), 0.8F, 0.9F + self.random.nextFloat() * 0.2F);
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.ticks < INFLATED_TICKS + 80 && GulperEel.this.isInWater();
        }

        @Override
        public void tick()
        {
            GulperEel self = GulperEel.this;
            ++this.ticks;
            if (this.ticks < INFLATED_TICKS)
            {
                if (self.threatFrom != null) self.getLookControl().setLookAt(self.threatFrom.x, self.threatFrom.y + 1, self.threatFrom.z, 10.0F, 10.0F);
                self.setDeltaMovement(self.getDeltaMovement().scale(0.8));
                return;
            }
            if (this.ticks == INFLATED_TICKS)
            {
                self.setMood(FLEE);
                self.playSound(ModSounds.GULPER_EEL_DEFLATE.get(), 0.8F, 1.0F);
                if (self.level() instanceof ServerLevel server)
                {
                    Vec3 m = self.mouthPosition();
                    server.sendParticles(ParticleTypes.BUBBLE, m.x, m.y, m.z, 14, 0.25, 0.2, 0.25, 0.05);
                }
            }
            if (self.getNavigation().isDone())
            {
                Vec3 to = DefaultRandomPos.getPosAway(self, 12, 5, self.threatFrom != null ? self.threatFrom : self.position());
                if (to != null && self.level().getFluidState(BlockPos.containing(to)).is(FluidTags.WATER))
                {
                    self.getNavigation().moveTo(to.x, to.y, to.z, 3.5);
                }
            }
        }

        @Override
        public void stop()
        {
            GulperEel self = GulperEel.this;
            self.setMood(CRUISE);
            self.getNavigation().stop();
            self.displayCooldown = 200 + self.random.nextInt(200);
            self.threatFrom = null;
        }
    }

    /** Every so often the jaws are thrown open to engulf a mouthful of water and the small animals in it. */
    private class GulpGoal extends Goal
    {
        private int ticks;

        GulpGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public boolean canUse()
        {
            GulperEel self = GulperEel.this;
            return self.mood() == CRUISE && self.isInWater() && --self.gulpCooldown <= 0;
        }

        @Override
        public void start()
        {
            GulperEel self = GulperEel.this;
            this.ticks = 0;
            self.setMood(GULP);
            self.playSound(ModSounds.GULPER_EEL_GULP.get(), 0.7F, 0.85F + self.random.nextFloat() * 0.3F);
            self.setDeltaMovement(self.getDeltaMovement().add(self.getLookAngle().scale(0.08)));
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.ticks < 26 && GulperEel.this.mood() == GULP;
        }

        @Override
        public void tick()
        {
            GulperEel self = GulperEel.this;
            if (++this.ticks < 4 || this.ticks > 16) return;
            Vec3 mouth = self.mouthPosition();
            for (Mob prey : self.level().getEntitiesOfClass(Mob.class, self.getBoundingBox().inflate(1.2),
                    e -> e != self && e.getType().is(ModTags.GULPER_EEL_PREY) && isFairPrey(e)))
            {
                if (prey.position().distanceToSqr(mouth) < 1.0)
                {
                    prey.discard();
                    self.heal(2.0F);
                    self.gameEvent(GameEvent.EAT);
                }
            }
        }

        @Override
        public void stop()
        {
            GulperEel self = GulperEel.this;
            if (self.mood() == GULP) self.setMood(CRUISE);
            self.gulpCooldown = 300 + self.random.nextInt(500);
        }
    }
}
