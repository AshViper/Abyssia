package com.abyssia.entity.boss;

import com.abyssia.registry.ModEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A rock thrown up by the trench wyrm: sinks through the water, hurts what it hits (6) and vanishes on any block. Never changes terrain. */
public class RockShard extends ThrowableItemProjectile
{
    public static final float DAMAGE = 6.0F;
    private static final int LIFETIME = 240;

    public RockShard(EntityType<? extends RockShard> type, Level level)
    {
        super(type, level);
    }

    public RockShard(Level level, LivingEntity owner)
    {
        super(ModEntities.ROCK_SHARD.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem()
    {
        return Items.COBBLED_DEEPSLATE;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide || isRemoved()) return;
        // sinks faster than a thrown item would in water
        Vec3 dm = getDeltaMovement();
        setDeltaMovement(dm.x, dm.y - 0.08, dm.z);
        if (tickCount > LIFETIME) discard();
    }

    @Override
    protected boolean canHitEntity(Entity entity)
    {
        return super.canHitEntity(entity) && !(entity instanceof AbyssBoss) && !(entity instanceof BossPart)
                && !(entity instanceof RockShard) && (!(entity instanceof LivingEntity le) || AbyssBoss.isValidTarget(le));
    }

    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        super.onHitEntity(result);
        if (level().isClientSide) return;
        result.getEntity().hurt(damageSources().thrown(this, getOwner()), DAMAGE);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result)
    {
        super.onHitBlock(result);
        if (!level().isClientSide) discard();
    }
}
