package com.abyssia.furniture;

import com.abyssia.registry.ModFurniture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import javax.annotation.Nullable;

/**
 * The large locker's storage, on its bottom-left cell: 81 slots (9 x 9) shown with {@link LargeLockerMenu}; saves
 * from the 54-slot version load into the first 54 slots. Item capability: top = insert only, bottom = extract only, sides (and null) = both; the other cells hand this out
 * through {@link LockerPartBlockEntity}. The openers counter drives OPEN and the sounds (like a barrel).
 */
public class LargeLockerBlockEntity extends RandomizableContainerBlockEntity
{
    public static final int SLOTS = LargeLockerMenu.SLOTS;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    private final ContainerOpenersCounter openers = new ContainerOpenersCounter()
    {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state)
        {
            playSounds(state, SoundEvents.BARREL_OPEN, SoundEvents.IRON_DOOR_OPEN);
            setOpen(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state)
        {
            playSounds(state, SoundEvents.BARREL_CLOSE, SoundEvents.IRON_DOOR_CLOSE);
            setOpen(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int count)
        {
        }

        @Override
        protected boolean isOwnContainer(Player player)
        {
            return player.containerMenu instanceof LargeLockerMenu menu && menu.container() == LargeLockerBlockEntity.this;
        }
    };

    /** Item capability views, registered in ModFurniture: top = insert only, bottom = extract only, sides / null = both. */
    private final IItemHandler allItems = new InvWrapper(this);
    private final IItemHandler topItems = new DirectionalItemHandler(allItems, true, false);
    private final IItemHandler bottomItems = new DirectionalItemHandler(allItems, false, true);

    public IItemHandler itemHandler(@Nullable Direction side)
    {
        if (side == Direction.UP) return topItems;
        return side == Direction.DOWN ? bottomItems : allItems;
    }

    public LargeLockerBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModFurniture.LARGE_LOCKER_ENTITY.get(), pos, state);
    }

    /** The other cells forward to this handler through a cached capability: refresh it once this base exists. */
    @Override
    public void onLoad()
    {
        super.onLoad();
        if (level != null && !level.isClientSide && getBlockState().hasProperty(LargeLockerBlock.FACING))
            LargeLockerBlock.invalidateCaps(level, worldPosition, getBlockState().getValue(LargeLockerBlock.FACING));
    }

    // ---------------------------------------------------------------- container

    @Override
    public int getContainerSize()
    {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems()
    {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items)
    {
        this.items = items;
    }

    @Override
    protected Component getDefaultName()
    {
        return Component.translatable("container.abyssia.large_locker");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory)
    {
        return new LargeLockerMenu(id, inventory, this);
    }

    // ---------------------------------------------------------------- open state

    @Override
    public void startOpen(Player player)
    {
        if (!remove && !player.isSpectator() && level != null)
            openers.incrementOpeners(player, level, worldPosition, getBlockState());
    }

    @Override
    public void stopOpen(Player player)
    {
        if (!remove && !player.isSpectator() && level != null)
            openers.decrementOpeners(player, level, worldPosition, getBlockState());
    }

    /** Scheduled block tick: drops players who walked away without closing. */
    public void recheckOpen()
    {
        if (!remove && level != null) openers.recheckOpeners(level, worldPosition, getBlockState());
    }

    private void setOpen(BlockState state, boolean open)
    {
        if (level != null && state.getBlock() instanceof LargeLockerBlock block) block.setOpen(level, worldPosition, state, open);
    }

    /** At the middle of the 2x2 front: the barrel sound plus a quieter iron door. */
    private void playSounds(BlockState state, SoundEvent barrel, SoundEvent door)
    {
        if (level == null || !state.hasProperty(LargeLockerBlock.FACING)) return;
        Direction facing = state.getValue(LargeLockerBlock.FACING);
        Direction right = facing.getCounterClockWise();
        double x = worldPosition.getX() + 0.5 + right.getStepX() * 0.5 + facing.getStepX() * 0.5;
        double y = worldPosition.getY() + 1.0;
        double z = worldPosition.getZ() + 0.5 + right.getStepZ() * 0.5 + facing.getStepZ() * 0.5;
        float pitch = level.random.nextFloat() * 0.1f + 0.9f;
        level.playSound(null, x, y, z, barrel, SoundSource.BLOCKS, 0.5f, pitch);
        level.playSound(null, x, y, z, door, SoundSource.BLOCKS, 0.3f, pitch);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items, registries);
    }
}
