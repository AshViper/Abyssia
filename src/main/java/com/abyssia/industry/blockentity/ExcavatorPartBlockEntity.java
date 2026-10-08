package com.abyssia.industry.blockentity;

import com.abyssia.registry.ModIndustry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

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
        // NeoForge caches block capabilities: the forwarded handlers appear only now
        if (level != null) level.invalidateCapabilities(worldPosition);
    }

    @Nullable
    public AbyssalExcavatorBlockEntity master()
    {
        if (controller != null && level != null && level.isLoaded(controller)
                && level.getBlockEntity(controller) instanceof AbyssalExcavatorBlockEntity be)
            return be;
        return null;
    }

    @Nullable
    public IEnergyStorage energyStorage(@Nullable Direction side)
    {
        AbyssalExcavatorBlockEntity be = master();
        return be == null ? null : be.energyStorage(side);
    }

    @Nullable
    public IItemHandler itemHandler(@Nullable Direction side)
    {
        AbyssalExcavatorBlockEntity be = master();
        return be == null ? null : be.itemHandler(side);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        if (controller != null) tag.put("Controller", NbtUtils.writeBlockPos(controller));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        controller = NbtUtils.readBlockPos(tag, "Controller").orElse(null);
    }
}
