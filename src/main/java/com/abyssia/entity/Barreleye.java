package com.abyssia.entity;

import com.abyssia.entity.ai.ScoredSwimGoal;
import com.abyssia.entity.ai.SlowTurnGoal;
import com.abyssia.entity.ai.SwimFleeGoal;
import com.abyssia.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * デメニギス / barreleye (Macropinna microstoma). A 15 cm fish of 600-800 m that hangs almost motionless, level, its
 * big fins spread, looking straight up through the transparent, fluid-filled shield over its head with tubular green
 * eyes - hunting for the silhouettes of drifting animals against the faint light from above. When it spots food it
 * tilts its body upright while the eyes rotate to keep the target in view, and plucks it (probably prey from the
 * tentacles of siphonophores). Harmless and passive; it darts off when hurt.
 */
public class Barreleye extends DeepSeaSwimmer
{
    private static final int ACTION_FEED = 0;
    private int feedTicks = -1;

    public Barreleye(EntityType<? extends Barreleye> type, Level level)
    {
        super(type, level, 30, 4, 0.0012F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 1.0).add(Attributes.FOLLOW_RANGE, 10.0);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new SwimFleeGoal(this, 3.0, 8));
        this.goalSelector.addGoal(2, new FeedUpwardGoal());
        this.goalSelector.addGoal(3, new ScoredSwimGoal(this, 400, 0.8, 6, 3, 200, () -> !this.isFleeing(),
                pos -> ScoredSwimGoal.darkness(this.level(), pos)));
        this.goalSelector.addGoal(4, new SlowTurnGoal(this, 200, 0.8F, () -> !this.isFleeing()));
    }

    @Override
    protected void onAction(int action)
    {
        if (action != ACTION_FEED) return;
        this.animations().play("mouth_open", 0);
        this.feedTicks = 0;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!this.level().isClientSide) return;
        this.animations().tick("swim", this.isSwimmingNow());
        if (this.feedTicks >= 0 && ++this.feedTicks == 6)
        {
            this.animations().stop("mouth_open");
            this.animations().play("mouth_close", 9);
            this.feedTicks = -1;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide)
        {
            Entity attacker = source.getEntity();
            this.startFleeing(attacker != null ? attacker.position() : this.position().add(this.getLookAngle()), 80 + this.random.nextInt(40));
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.BARRELEYE.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 600;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.BARRELEYE.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.BARRELEYE.get("death");
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return ModSounds.BARRELEYE.get("flop");
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.4F;
    }

    /** Something spotted above: tilt upright (eyes kept on it by the model), rise a little, pluck it, level out. */
    private class FeedUpwardGoal extends Goal
    {
        private int ticks, duration;

        FeedUpwardGoal()
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
            Barreleye self = Barreleye.this;
            return !self.isFleeing() && self.isInWater() && self.getNavigation().isDone() && self.random.nextInt(500) == 0
                    && self.level().getFluidState(self.blockPosition().above(2)).isSource();
        }

        @Override
        public void start()
        {
            this.ticks = 0;
            this.duration = 70 + Barreleye.this.random.nextInt(50);
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.ticks < this.duration && !Barreleye.this.isFleeing();
        }

        @Override
        public void tick()
        {
            Barreleye self = Barreleye.this;
            ++this.ticks;
            Vec3 above = self.position().add(Vec3.directionFromRotation(0, self.getYRot()).scale(0.3)).add(0, 3, 0);
            self.getLookControl().setLookAt(above.x, above.y, above.z, 10.0F, 10.0F);
            if (self.getXRot() < -50.0F) self.setDeltaMovement(self.getDeltaMovement().add(0, 0.004, 0));
            if (this.ticks == this.duration - 25) self.broadcastAction(ACTION_FEED);
        }
    }
}
