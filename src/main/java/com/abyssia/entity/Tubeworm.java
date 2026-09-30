package com.abyssia.entity;

import com.abyssia.registry.ModSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * チューブワーム / giant tubeworm (Riftia pachyptila) and サツマハオリムシ (Lamellibrachia satsuma): a colony of tubes
 * rooted in vent rock. No mouth and no gut - symbiotic sulfur bacteria inside feed them - so they never move and
 * never hunt; the red plumes (haemoglobin binding oxygen and sulfide) sway in the vent water and snap back into the
 * tubes when something touches or brushes past them, reappearing a little later. An environmental animal: it does
 * not despawn, and it takes little damage while withdrawn into its chitin tubes.
 */
public class Tubeworm extends WaterAnimal implements FaunaAnimated
{
    private static final EntityDataAccessor<Boolean> RETRACTED = SynchedEntityData.defineId(Tubeworm.class, EntityDataSerializers.BOOLEAN);
    private static final int DEATH_TICKS = 44;
    private static final float TUBE_DAMAGE = 0.4F;

    private final ModSounds.Voice voice;
    private final FaunaAnimations animations = new FaunaAnimations(this);
    private int retractTicks;
    private boolean shownRetracted;

    public Tubeworm(EntityType<? extends Tubeworm> type, Level level, ModSounds.Voice voice)
    {
        super(type, level);
        this.voice = voice;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.ARMOR, 4.0).add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(RETRACTED, false);
    }

    public boolean isRetracted()
    {
        return this.entityData.get(RETRACTED);
    }

    @Override
    public FaunaAnimations animations()
    {
        return this.animations;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            boolean retracted = this.isRetracted();
            this.animations.tick("sway", !retracted);
            if (retracted && !this.shownRetracted) this.animations.play("retract", 0);
            if (!retracted && this.shownRetracted)
            {
                this.animations.stop("retract");
                this.animations.play("extend", 30);
            }
            this.shownRetracted = retracted;
            return;
        }
        if (this.retractTicks > 0 && --this.retractTicks == 0) this.entityData.set(RETRACTED, false);
        // a touch or something brushing past the plumes
        if ((this.tickCount + this.getId()) % 5 == 0 && this.touched()) this.retract(60 + this.random.nextInt(80));
    }

    private boolean touched()
    {
        List<LivingEntity> near = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(1.2, 0.6, 1.2),
                e -> e != this && !(e instanceof Tubeworm) && e.isAlive() && !(e instanceof Player p && p.isSpectator()));
        for (LivingEntity e : near)
        {
            if (e instanceof Player || e.getDeltaMovement().lengthSqr() > 1.0E-4) return true;
        }
        return false;
    }

    private void retract(int ticks)
    {
        if (!this.isRetracted())
        {
            this.entityData.set(RETRACTED, true);
            this.playSound(this.voice.get("retract"), 0.5F, 0.9F + this.random.nextFloat() * 0.2F);
        }
        this.retractTicks = Math.max(this.retractTicks, ticks);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.isRetracted()) amount *= TUBE_DAMAGE;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive()) this.retract(160 + this.random.nextInt(80));
        return hurt;
    }

    // ---------------------------------------------------------------- rooted in the rock

    @Override
    public void travel(Vec3 input)
    {
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    protected void doPush(Entity entity)
    {
    }

    @Override
    public boolean isPushedByFluid()
    {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player)
    {
        return false;
    }

    @Override
    protected void tickDeath()
    {
        ++this.deathTime;
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved())
        {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void handleDamageEvent(DamageSource source)
    {
        super.handleDamageEvent(source);
        this.animations().hurt();
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
    protected float getSoundVolume()
    {
        return 0.5F;
    }
}
