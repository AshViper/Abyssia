package com.abyssia.entity;

import com.abyssia.entity.ai.SeabedStrollGoal;
import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * ウロコフネタマガイ / scaly-foot snail (Chrysomallon squamiferum), known only from three vent fields of the Indian
 * Ocean (Kairei, Solitaire, Longqi; 2400-2900 m). The only living animal known to build iron sulfide (pyrite,
 * greigite) into its skeleton: a black metallic shell and hundreds of overlapping mineral scales on its foot. It
 * lives on bacteria housed in an enormous throat gland, so it only creeps slowly over the warm vent rock; touched or
 * attacked, it pulls into its armour and takes very little damage. Passive.
 */
public class ScalyFootSnail extends BenthicWalker
{
    private static final EntityDataAccessor<Boolean> RETRACTED = SynchedEntityData.defineId(ScalyFootSnail.class, EntityDataSerializers.BOOLEAN);
    private static final float ARMOURED_DAMAGE = 0.25F;
    private int retractTicks;
    private boolean shownRetracted;

    public ScalyFootSnail(EntityType<? extends ScalyFootSnail> type, Level level)
    {
        super(type, level, "crawl");
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.ARMOR, 10.0).add(Attributes.MOVEMENT_SPEED, 0.06)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(RETRACTED, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(4, new SeabedStrollGoal(this, 200, 1.0, 3, 2, () -> !this.isRetracted(),
                pos -> this.level().getBlockState(pos.below()).is(ModTags.FAUNA_VENT) ? 2.0 : 0.0));
    }

    @Override
    protected int homeRadius()
    {
        return 6;
    }

    public boolean isRetracted()
    {
        return this.entityData.get(RETRACTED);
    }

    @Override
    protected boolean showsWalking()
    {
        return !this.isRetracted() && super.showsWalking();
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            boolean retracted = this.isRetracted();
            if (retracted && !this.shownRetracted) this.animations().play("retract", 0);
            if (!retracted && this.shownRetracted) this.animations().stop("retract");
            this.shownRetracted = retracted;
            return;
        }
        if (this.retractTicks > 0 && --this.retractTicks == 0) this.entityData.set(RETRACTED, false);
        if ((this.tickCount + this.getId()) % 10 == 0)
        {
            Player near = this.level().getNearestPlayer(this, 1.3);
            if (near != null && !near.isSpectator()) this.retract(60 + this.random.nextInt(60));
        }
    }

    private void retract(int ticks)
    {
        if (!this.isRetracted())
        {
            this.entityData.set(RETRACTED, true);
            this.getNavigation().stop();
            this.playSound(ModSounds.SCALY_FOOT_SNAIL.get("retract"), 0.5F, 0.9F + this.random.nextFloat() * 0.2F);
        }
        this.retractTicks = Math.max(this.retractTicks, ticks);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.isRetracted()) amount *= ARMOURED_DAMAGE;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive()) this.retract(160 + this.random.nextInt(80));
        return hurt;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.SCALY_FOOT_SNAIL.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.SCALY_FOOT_SNAIL.get("death");
    }

    @Override
    protected SoundEvent getSwimSound()
    {
        return ModSounds.SCALY_FOOT_SNAIL.get("step");
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.4F;
    }
}
