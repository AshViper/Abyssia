package com.abyssia.entity;

import com.abyssia.entity.ai.ClawWarningGoal;
import com.abyssia.entity.ai.SeabedStrollGoal;
import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
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
 * ゴエモンコシオリエビ / Shinkaia crosnieri, the squat lobster that carpets the vent chimneys of the Okinawa Trough
 * (around 700-1600 m) in dense crowds. It farms its food: chemosynthetic bacteria grow on the dense setae of its
 * underside, fed by the vent water it sits in, and it combs them off with its mouthparts. It raises its long claws at
 * intruders and escapes backwards with a flip of its tucked-under abdomen. Passive.
 */
public class SquatLobster extends BenthicWalker
{
    private static final int ACTION_SNAP = 0;

    public SquatLobster(EntityType<? extends SquatLobster> type, Level level)
    {
        super(type, level, "walk");
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.ARMOR, 2.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 8.0);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(1, new ClawWarningGoal(this, 2.0, this::snap, player -> this.tailFlip(player.position())));
        // stays in the crowd on the warm vent rock
        this.goalSelector.addGoal(4, new SeabedStrollGoal(this, 120, 1.0, 4, 2, () -> true, this::spotScore));
    }

    private double spotScore(BlockPos pos)
    {
        double s = this.level().getBlockState(pos.below()).is(ModTags.FAUNA_VENT) ? 2.0 : 0.0;
        int crowd = this.level().getEntities(this.getType(), new AABB(pos).inflate(2.0), Entity::isAlive).size();
        return s + Math.min(crowd, 4) * 0.5;
    }

    @Override
    protected int homeRadius()
    {
        return 6;
    }

    private void snap()
    {
        this.broadcastAction(ACTION_SNAP);
        this.playSound(ModSounds.GOEMON_SQUAT_LOBSTER.get("snap"), 0.5F, 1.0F + this.random.nextFloat() * 0.2F);
    }

    /** The escape: the abdomen flips and the animal shoots backwards and up off the rock. */
    private void tailFlip(Vec3 from)
    {
        Vec3 away = this.position().subtract(from).multiply(1, 0, 1).normalize();
        this.setDeltaMovement(away.x * 0.6, 0.35, away.z * 0.6);
        this.hasImpulse = true;
        this.playSound(ModSounds.GOEMON_SQUAT_LOBSTER.get("flick"), 0.5F, 1.0F + this.random.nextFloat() * 0.2F);
    }

    @Override
    protected void onAction(int action)
    {
        if (action == ACTION_SNAP) this.animations().play("claw_snap", 12);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive())
        {
            Entity attacker = source.getEntity();
            this.tailFlip(attacker != null ? attacker.position() : this.position().add(this.getLookAngle()));
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.GOEMON_SQUAT_LOBSTER.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 500;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.GOEMON_SQUAT_LOBSTER.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.GOEMON_SQUAT_LOBSTER.get("death");
    }

    @Override
    protected SoundEvent getSwimSound()
    {
        return ModSounds.GOEMON_SQUAT_LOBSTER.get("step");
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.4F;
    }
}
