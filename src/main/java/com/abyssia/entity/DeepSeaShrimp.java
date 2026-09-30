package com.abyssia.entity;

import com.abyssia.entity.ai.SchoolGoal;
import com.abyssia.entity.ai.ScoredSwimGoal;
import com.abyssia.entity.ai.SwimFleeGoal;
import com.abyssia.registry.ModParticles;
import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * シンカイエビ / a deep-sea shrimp of the type of Acanthephyra purpurea: blood-red (red light does not reach these
 * depths, so red reads as black), antennae far longer than the body. Swarms loosely in the midwater (about
 * 300-3300 m), rising at night and sinking by day. It has no photophores; threatened, it spews a bright cloud of
 * bioluminescent secretion from near its mouth and escapes backwards with a flick of the tail. Food for most of the
 * midwater predators.
 */
public class DeepSeaShrimp extends DeepSeaSwimmer
{
    private static final int ACTION_FLICK = 0;
    private int flickCooldown;
    private int spewCooldown;

    public DeepSeaShrimp(EntityType<? extends DeepSeaShrimp> type, Level level)
    {
        super(type, level, 40, 12, 0.0025F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 3.0).add(Attributes.MOVEMENT_SPEED, 1.0).add(Attributes.FOLLOW_RANGE, 8.0);
    }

    protected ModSounds.Voice voice()
    {
        return ModSounds.DEEP_SEA_SHRIMP;
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new SwimFleeGoal(this, 3.0, 8));
        this.goalSelector.addGoal(2, new SchoolGoal(this, 8.0, 1.0, () -> !this.isFleeing()));
        this.goalSelector.addGoal(3, new ScoredSwimGoal(this, 80, 1.0, 6, 4, 200, () -> !this.isFleeing(), this::spotScore));
    }

    protected double spotScore(BlockPos pos)
    {
        return this.migration(pos, 0.5) + ScoredSwimGoal.darkness(this.level(), pos) * 0.5;
    }

    /** A bright defensive spew (the real animal's has limited stores, so not every time). */
    protected boolean spews()
    {
        return true;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            boolean walking = this.onGround() && this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
            this.animations().tick(walking ? "walk" : this.isSwimmingNow() ? "swim" : null, "swim", "walk");
            return;
        }
        if (this.flickCooldown > 0) --this.flickCooldown;
        if (this.spewCooldown > 0) --this.spewCooldown;
        if (this.flickCooldown <= 0 && this.isInWater() && (this.tickCount + this.getId()) % 4 == 0)
        {
            LivingEntity threat = this.threat();
            if (threat != null) this.escape(threat.position());
        }
    }

    @Nullable
    private LivingEntity threat()
    {
        List<LivingEntity> near = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(2.5),
                e -> e.isAlive() && (e instanceof Player p ? !p.isSpectator() : e.getType().is(ModTags.SMALL_FAUNA_THREATS)));
        return near.isEmpty() ? null : near.get(0);
    }

    /** The tail flick: a jump backwards, away from the threat (and, if it can, a glowing spew left behind). */
    private void escape(Vec3 from)
    {
        this.flickCooldown = 50 + this.random.nextInt(30);
        this.broadcastAction(ACTION_FLICK);
        this.playSound(this.voice().get("flick"), 0.5F, 1.0F + this.random.nextFloat() * 0.3F);
        Vec3 away = this.position().subtract(from).normalize();
        this.setDeltaMovement(this.getDeltaMovement().add(away.x * 0.5, 0.1 + away.y * 0.3, away.z * 0.5));
        if (this.spews() && this.spewCooldown <= 0 && this.level() instanceof ServerLevel server)
        {
            this.spewCooldown = 400 + this.random.nextInt(400);
            Vec3 p = this.bodyPoint(0, 0, 0.15);
            server.sendParticles(ModParticles.BIOLUMINESCENT_SNOW.get(), p.x, p.y, p.z, 24, 0.25, 0.2, 0.25, 0.01);
            this.playSound(this.voice().get("spew"), 0.5F, 1.0F);
        }
        this.startFleeing(from, 40);
    }

    @Override
    protected void onAction(int action)
    {
        if (action == ACTION_FLICK) this.animations().play("flick", 10);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive())
        {
            Entity attacker = source.getEntity();
            this.flickCooldown = 0;
            this.escape(attacker != null ? attacker.position() : this.position().add(this.getLookAngle()));
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.voice().get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 400;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return this.voice().get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return this.voice().get("death");
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return this.voice().get("flick");
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.35F;
    }
}
