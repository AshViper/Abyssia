package com.abyssia.entity;

import com.abyssia.entity.ai.ScoredSwimGoal;
import com.abyssia.entity.ai.SwimFleeGoal;
import com.abyssia.registry.ModParticles;
import com.abyssia.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * ゲンゲ / vent eelpout (Thermarces cerberus), the pink vent fish of the East Pacific Rise (2300-2630 m). A sluggish,
 * pale, eel-shaped fish that lies on the bottom among the tubeworms and noses for food: mostly the little limpets
 * and amphipods that crowd the tubes. It never leaves its vent field and slides away slowly when disturbed.
 */
public class VentEelpout extends DeepSeaSwimmer
{
    private static final int ACTION_FORAGE = 0;
    private int forageTicks = -1;

    public VentEelpout(EntityType<? extends VentEelpout> type, Level level)
    {
        super(type, level, 20, 5, 0.0018F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 1.0).add(Attributes.FOLLOW_RANGE, 8.0);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new SwimFleeGoal(this, 2.0, 6));
        this.goalSelector.addGoal(3, new ScoredSwimGoal(this, 300, 0.8, 6, 2, 200, () -> !this.isFleeing(),
                pos -> -ScoredSwimGoal.floorDistance(this.level(), pos, 6)));
    }

    @Override
    protected int homeRadius()
    {
        return 10;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.animations().tick("swim", this.isSwimmingNow());
            if (this.forageTicks >= 0 && ++this.forageTicks == 8)
            {
                this.animations().stop("mouth_open");
                this.animations().play("mouth_close", 10);
                this.forageTicks = -1;
            }
            return;
        }
        if (!this.isInWater() || this.isFleeing() || !this.getNavigation().isDone()) return;
        // a bottom dweller, heavier than water: at rest it lies on the seabed
        if (!this.onGround()) this.setDeltaMovement(this.getDeltaMovement().add(0, -0.003, 0));
        else if (this.random.nextInt(300) == 0) this.forage();
    }

    /** Snaps up something small from the rock, stirring a little sediment. */
    private void forage()
    {
        this.broadcastAction(ACTION_FORAGE);
        if (this.level() instanceof ServerLevel server)
        {
            Vec3 p = this.bodyPoint(0, -0.1, 0.35);
            server.sendParticles(ModParticles.SEDIMENT.get(), p.x, p.y, p.z, 6, 0.15, 0.05, 0.15, 0.01);
        }
    }

    @Override
    protected void onAction(int action)
    {
        if (action != ACTION_FORAGE) return;
        this.animations().play("mouth_open", 0);
        this.forageTicks = 0;
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide)
        {
            Entity attacker = source.getEntity();
            this.startFleeing(attacker != null ? attacker.position() : this.position().add(this.getLookAngle()), 80);
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.VENT_EELPOUT.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 600;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.VENT_EELPOUT.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.VENT_EELPOUT.get("death");
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return ModSounds.VENT_EELPOUT.get("flop");
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.4F;
    }
}
