package com.abyssia.habitat.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

/** BT01d: a generator part cell; knows its controller (set by GeneratorEntry.complete) and forwards the item handler. */
public class GeneratorPartBlockEntity extends BlockEntity
{
    @Nullable
    private BlockPos controller;

    public GeneratorPartBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModGenerators.PART_ENTITY.get(), pos, state);
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
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ITEM_HANDLER && controller != null && level != null && level.isLoaded(controller)
                && level.getBlockEntity(controller) instanceof GeneratorBlockEntity be)
            return be.getCapability(cap, side);
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

    @Override
    public CompoundTag getUpdateTag()
    {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
