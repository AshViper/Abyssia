package com.abyssia.furniture;

import com.abyssia.registry.ModFurniture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The large locker's storage, on its bottom-left cell: 81 slots (9 x 9) shown with {@link LargeLockerMenu}; saves
 * from the 54-slot version load into the first 54 slots. Item capability: top = insert only, bottom = extract only, sides (and null) = both; the other cells hand this out
 * through {@link LockerPartBlockEntity}. The openers counter drives OPEN and the sounds (like a barrel).
 * The custom name (anvil, or the screen's name field via {@link LockerRenamePacket}) is synced to clients, where
 * LargeLockerRenderer shows it as a plate in the middle of the top row.
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

    private LazyOptional<IItemHandler> allItems;
    private LazyOptional<IItemHandler> topItems;
    private LazyOptional<IItemHandler> sideItems;
    private LazyOptional<IItemHandler> bottomItems;

    public LargeLockerBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModFurniture.LARGE_LOCKER_ENTITY.get(), pos, state);
        createCaps();
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
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.setCustomName(null); // super.load keeps the old name when the tag has none (a cleared name on clients)
        super.load(tag);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
    }

    // ---------------------------------------------------------------- name sync

    @Override
    public void setCustomName(@Nullable Component name)
    {
        super.setCustomName(name);
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Only the name goes to clients (the items are synced by the menu). */
    @Override
    public CompoundTag getUpdateTag()
    {
        CompoundTag tag = new CompoundTag();
        if (getCustomName() != null) tag.putString("CustomName", Component.Serializer.toJson(getCustomName()));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The name plate spans the whole 2 x 2 front, not just this cell. */
    @Override
    public AABB getRenderBoundingBox()
    {
        return new AABB(worldPosition).inflate(1.0);
    }

    // ---------------------------------------------------------------- capabilities

    private void createCaps()
    {
        IItemHandler wrapper = new InvWrapper(this);
        allItems = LazyOptional.of(() -> wrapper);
        topItems = LazyOptional.of(() -> new DirectionalItemHandler(wrapper, true, false));
        sideItems = LazyOptional.of(() -> wrapper);
        bottomItems = LazyOptional.of(() -> new DirectionalItemHandler(wrapper, false, true));
    }

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !remove)
        {
            if (side == null) return allItems.cast();
            if (side == Direction.UP) return topItems.cast();
            return side == Direction.DOWN ? bottomItems.cast() : sideItems.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        allItems.invalidate();
        topItems.invalidate();
        sideItems.invalidate();
        bottomItems.invalidate();
    }

    @Override
    public void reviveCaps()
    {
        super.reviveCaps();
        createCaps();
    }
}
