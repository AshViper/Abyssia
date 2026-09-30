package com.abyssia.entity;

import com.abyssia.entity.ai.SeabedStrollGoal;
import com.abyssia.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A seabed walker configured by data (walk speed, how often it wanders, how far from home): crabs, sea spiders, sea
 * pigs, brittle stars and other bottom crawlers without a behaviour of their own. It keeps loosely together with its
 * kind and backs off from whatever hurt it. Voice kinds: ambient, hurt, death, step (step doubles as the walk sound).
 */
public class GenericWalker extends BenthicWalker
{
    private final ModSounds.Voice voice;
    private final int wander, home, ambient;

    public GenericWalker(EntityType<? extends GenericWalker> type, Level level, ModSounds.Voice voice, String moveClip, double speed, int wander,
                         int home, int ambient)
    {
        super(type, level, moveClip);
        this.voice = voice;
        this.wander = wander;
        this.home = home;
        this.ambient = ambient;
        // Mob's constructor calls registerGoals() before these fields are set: add the goals now
        if (!level.isClientSide) this.goalSelector.addGoal(4, new SeabedStrollGoal(this, wander, speed, 4, 2, () -> true, this::spotScore));
    }

    public static AttributeSupplier.Builder attributes(double health, double attack, double armor)
    {
        AttributeSupplier.Builder b = Mob.createMobAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 8.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
        if (attack > 0) b.add(Attributes.ATTACK_DAMAGE, attack);
        if (armor > 0) b.add(Attributes.ARMOR, armor);
        return b;
    }

    @Override
    protected void registerGoals()
    {
        // see the constructor
    }

    /** Loose aggregations: a few of its kind nearby make a spot better. */
    private double spotScore(net.minecraft.core.BlockPos pos)
    {
        int crowd = this.level().getEntities(this.getType(), new AABB(pos).inflate(3.0), Entity::isAlive).size();
        return Math.min(crowd, 3) * 0.4 + this.random.nextDouble() * 0.5;
    }

    @Override
    protected int homeRadius()
    {
        return this.home;
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive())
        {
            // backs away from the attacker along the seabed
            Entity attacker = source.getEntity();
            Vec3 from = attacker != null ? attacker.position() : this.position().add(this.getLookAngle());
            Vec3 away = this.position().subtract(from).multiply(1, 0, 1).normalize();
            this.getNavigation().moveTo(this.getX() + away.x * 4, this.getY(), this.getZ() + away.z * 4, 1.4);
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.voice.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return this.ambient;
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
    protected SoundEvent getSwimSound()
    {
        return this.voice.get(this.voice.has("step") ? "step" : "ambient");
    }
}
