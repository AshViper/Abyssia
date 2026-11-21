package com.abyssia.entity.giant;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/** A hitbox riding on one joint group of a giant's chain. Hits are forwarded to the animal. */
public class GiantPart extends PartEntity<GiantFauna>
{
    private final int index;

    public GiantPart(GiantFauna parent, int index)
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
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand)
    {
        return this.getParent().interact(player, hand);
    }

    @Override
    public boolean is(Entity entity) { return this == entity || this.getParent() == entity; }

    @Override
    public ItemStack getPickResult() { return this.getParent().getPickResult(); }

    @Override
    public boolean shouldBeSaved() { return false; }
}
