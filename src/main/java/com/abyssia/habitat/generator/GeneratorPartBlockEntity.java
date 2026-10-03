package com.abyssia.habitat.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

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
        if (level != null)
        {
            // NeoForge caches block capabilities: the forwarded fuel slot appears only now
            level.invalidateCapabilities(worldPosition);
            if (!level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** the controller's fuel slot (biofuel only), registered as Capabilities.ItemHandler.BLOCK in ModGenerators */
    @Nullable
    public IItemHandler itemHandler()
    {
        if (controller != null && level != null && level.isLoaded(controller)
                && level.getBlockEntity(controller) instanceof GeneratorBlockEntity be)
            return be.itemHandler();
        return null;
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

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
