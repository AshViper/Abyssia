package com.abyssia.entity.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/** A hitbox riding on one joint group of a boss chain. Hits are forwarded to the boss. */
public class BossPart extends PartEntity<AbyssBoss>
{
    private final int index;

    public BossPart(AbyssBoss parent, int index)
    {
        super(parent);
        this.index = index;
    }

    public int index() { return index; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        return !this.isInvulnerableTo(source) && this.getParent().hurtFromPart(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) { return this == entity || this.getParent() == entity; }

    @Override
    public ItemStack getPickResult() { return this.getParent().getPickResult(); }

    @Override
    public boolean shouldBeSaved() { return false; }
}
