package com.abyssia.furniture;

import com.abyssia.registry.ModFurniture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * Hydro planter storage and growth (feature PL01): one seedling slot, a growth counter in ticks that advances while a
 * seedling is inside (and the chunk is loaded). Harvesting resets it to 0 and keeps the seedling. The blockstate
 * (crop / ripe) is kept in sync for the model.
 */
public class HydroPlanterBlockEntity extends BlockEntity implements MenuProvider
{
    public static final int DATA_GROWTH = 0, DATA_TOTAL = 1, DATA_COUNT = 2;

    private final ItemStackHandler seed = new ItemStackHandler(1)
    {
        @Override
        public int getSlotLimit(int slot)
        {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack)
        {
            return PlanterCrop.of(stack) != PlanterCrop.NONE;
        }

        @Override
        protected void onContentsChanged(int slot)
        {
            // a different (or removed) seedling starts from 0
            if (PlanterCrop.of(getStackInSlot(0)) != crop) growth = 0;
            crop = PlanterCrop.of(getStackInSlot(0));
            setChanged();
            syncState();
        }
    };

    private PlanterCrop crop = PlanterCrop.NONE;
    private int growth;

    private final ContainerData data = new ContainerData()
    {
        @Override
        public int get(int index)
        {
            return index == DATA_GROWTH ? growth : index == DATA_TOTAL ? crop.ticks : 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount()
        {
            return DATA_COUNT;
        }
    };

    public HydroPlanterBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModFurniture.HYDRO_PLANTER_ENTITY.get(), pos, state);
    }

    public PlanterCrop crop()
    {
        return crop;
    }

    public boolean isRipe()
    {
        return crop != PlanterCrop.NONE && growth >= crop.ticks;
    }

    public ItemStackHandler seed()
    {
        return seed;
    }

    public ContainerData data()
    {
        return data;
    }

    /** Advances growth by ticks (bone meal); returns whether anything changed. */
    public boolean advance(int ticks)
    {
        if (crop == PlanterCrop.NONE || isRipe()) return false;
        growth = Math.min(crop.ticks, growth + ticks);
        setChanged();
        syncState();
        return true;
    }

    /** Harvest: resets growth to 0 (the seedling stays). */
    public void harvested()
    {
        growth = 0;
        setChanged();
        syncState();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HydroPlanterBlockEntity be)
    {
        be.syncState(); // cheap compare; fixes a state left stale by NBT load or /data
        if (be.crop == PlanterCrop.NONE || be.growth >= be.crop.ticks) return;
        be.growth++;
        if (be.growth >= be.crop.ticks)
        {
            be.setChanged();
            be.syncState();
        }
        else if (be.growth % 200 == 0) be.setChanged();
    }

    /** Writes crop / ripe into the blockstate when they differ. */
    private void syncState()
    {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(HydroPlanterBlock.CROP)) return;
        boolean ripe = isRipe();
        if (state.getValue(HydroPlanterBlock.CROP) != crop || state.getValue(HydroPlanterBlock.RIPE) != ripe)
            level.setBlock(worldPosition, state.setValue(HydroPlanterBlock.CROP, crop).setValue(HydroPlanterBlock.RIPE, ripe), 3);
    }

    public void dropContents()
    {
        if (level == null) return;
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, seed.getStackInSlot(0));
        seed.setStackInSlot(0, ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- menu

    @Override
    public Component getDisplayName()
    {
        return Component.translatable("container.abyssia.hydro_planter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player)
    {
        return new HydroPlanterMenu(id, inventory, seed, data, ContainerLevelAccess.create(level, worldPosition));
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        tag.put("Seed", seed.serializeNBT(registries));
        tag.putInt("Growth", growth);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        seed.deserializeNBT(registries, tag.getCompound("Seed"));
        crop = PlanterCrop.of(seed.getStackInSlot(0));
        growth = crop == PlanterCrop.NONE ? 0 : Math.min(tag.getInt("Growth"), crop.ticks);
    }
}
