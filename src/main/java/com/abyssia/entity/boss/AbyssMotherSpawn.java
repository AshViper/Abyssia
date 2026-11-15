package com.abyssia.entity.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/** Brood jelly of the abyss mother: 6 health, drifts at the nearest valid target, poison I on contact. Vanishes with its mother (never saved). */
public class AbyssMotherSpawn extends Monster
{
    private static final double SPEED = 0.14, SEEK_RANGE = 64.0;
    @Nullable
    private UUID mother;
    private int touchCooldown;

    public AbyssMotherSpawn(EntityType<? extends AbyssMotherSpawn> type, Level level)
    {
        super(type, level);
        this.setNoGravity(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, SEEK_RANGE).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    public void setMother(UUID id) { this.mother = id; }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) return;
        if (tickCount % 20 == 0)
        {
            Entity m = mother != null ? sl.getEntity(mother) : null;
            if (m == null || !m.isAlive())
            {
                discard();
                return;
            }
        }
        if (touchCooldown > 0) touchCooldown--;
        LivingEntity target = null;
        double best = SEEK_RANGE * SEEK_RANGE;
        for (var p : sl.players())
        {
            double d = p.distanceToSqr(this);
            if (AbyssBoss.isValidTarget(p) && d < best) { best = d; target = p; }
        }
        Vec3 want = Vec3.ZERO;
        if (target != null)
        {
            want = target.getBoundingBox().getCenter().subtract(position()).normalize().scale(SPEED);
            if (touchCooldown == 0 && getBoundingBox().inflate(0.3).intersects(target.getBoundingBox()))
            {
                touchCooldown = 20;
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                target.hurt(damageSources().mobAttack(this), 1.0F);
            }
        }
        setDeltaMovement(getDeltaMovement().scale(0.9).add(want.scale(0.1)));
        if (want.lengthSqr() > 1.0E-6) setYRot((float) (Math.atan2(-want.x, want.z) * (180.0 / Math.PI)));
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean removeWhenFarAway(double distance) { return true; }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public boolean canDrownInFluidType(net.minecraftforge.fluids.FluidType type) { return false; }

    @Override
    protected void registerGoals() {}

    @Override
    public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); }
}
