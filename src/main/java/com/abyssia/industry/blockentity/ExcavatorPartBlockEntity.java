package com.abyssia.industry.blockentity;

import com.abyssia.registry.ModIndustry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** ORE01: a collision cell of the excavator; remembers the master (set by ExcavatorEntry.complete) and forwards energy / items to it. */
public class ExcavatorPartBlockEntity extends BlockEntity
{
    @Nullable
    private BlockPos controller;

    public ExcavatorPartBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModIndustry.EXCAVATOR_PART_ENTITY.get(), pos, state);
    }

    @Nullable
    public BlockPos controller()
    {
        return controller;
    }

    public void setController(BlockPos pos)
    {
        controller = pos.immutable();
        setChanged();
    }

    @Nullable
    public AbyssalExcavatorBlockEntity master()
    {
        if (controller != null && level != null && level.isLoaded(controller)
                && level.getBlockEntity(controller) instanceof AbyssalExcavatorBlockEntity be)
            return be;
        return null;
    }

    /** energy and items are the master capabilities (its LazyOptionals, so they invalidate with it) */
    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ENERGY || cap == ForgeCapabilities.ITEM_HANDLER)
        {
            AbyssalExcavatorBlockEntity be = master();
            if (be != null) return be.getCapability(cap, side);
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (controller != null) tag.put("Controller", NbtUtils.writeBlockPos(controller));
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        controller = tag.contains("Controller") ? NbtUtils.readBlockPos(tag.getCompound("Controller")) : null;
    }
}
