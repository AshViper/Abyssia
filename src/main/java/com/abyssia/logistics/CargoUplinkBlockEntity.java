package com.abyssia.logistics;

import com.abyssia.Config;
import com.abyssia.industry.energy.IndustryEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Cargo uplink: nine slots. Send mode takes items from automation, moves up to 64 every 20 ticks into the owner's
 * cargo buffer (8 FE each by default); receive mode moves up to 64 every 20 ticks from the buffer into its slots,
 * where automation takes them out. The owner is whoever placed it.
 */
public class CargoUplinkBlockEntity extends BaseContainerBlockEntity
{
    public static final int SLOTS = 9, CAPACITY = 10_000, RECEIVE_LIMIT = 256, PER_OP = 64, PERIOD = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final IndustryEnergyStorage energy = new IndustryEnergyStorage(CAPACITY, RECEIVE_LIMIT, 0, this::setChanged);
    @Nullable
    private UUID owner;

    /** Automation view: insert only in send mode, extract only in receive mode. */
    private final IItemHandler automation = new IItemHandler()
    {
        @Override
        public int getSlots()
        {
            return SLOTS;
        }

        @Override
        @Nonnull
        public ItemStack getStackInSlot(int slot)
        {
            return items.get(slot);
        }

        @Override
        @Nonnull
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate)
        {
            if (stack.isEmpty() || receiving()) return stack;
            ItemStack held = items.get(slot);
            int limit = stack.getMaxStackSize();
            if (!held.isEmpty() && !ItemStack.isSameItemSameTags(held, stack)) return stack;
            int room = limit - held.getCount();
            if (room <= 0) return stack;
            int move = Math.min(room, stack.getCount());
            if (!simulate)
            {
                if (held.isEmpty()) items.set(slot, withCount(stack, move));
                else held.grow(move);
                setChanged();
            }
            return move >= stack.getCount() ? ItemStack.EMPTY : withCount(stack, stack.getCount() - move);
        }

        @Override
        @Nonnull
        public ItemStack extractItem(int slot, int amount, boolean simulate)
        {
            if (amount <= 0 || !receiving()) return ItemStack.EMPTY;
            ItemStack held = items.get(slot);
            if (held.isEmpty()) return ItemStack.EMPTY;
            int move = Math.min(amount, held.getCount());
            ItemStack out = withCount(held, move);
            if (!simulate)
            {
                held.shrink(move);
                if (held.isEmpty()) items.set(slot, ItemStack.EMPTY);
                setChanged();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot)
        {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack)
        {
            return !receiving();
        }
    };

    public CargoUplinkBlockEntity(BlockPos pos, BlockState state)
    {
        super(LogisticsContent.UPLINK_ENTITY.get(), pos, state);
    }

    public boolean receiving()
    {
        BlockState state = getBlockState();
        return state.hasProperty(CargoUplinkBlock.RECEIVER) && state.getValue(CargoUplinkBlock.RECEIVER);
    }

    /** the vanilla Container path (hoppers fall back to it when the capability refuses): same rule as the capability */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack)
    {
        return !receiving();
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack)
    {
        return receiving();
    }

    @Nullable
    public UUID owner()
    {
        return owner;
    }

    public void setOwner(@Nullable UUID owner)
    {
        this.owner = owner;
        setChanged();
    }

    public IndustryEnergyStorage energy()
    {
        return energy;
    }

    public IEnergyStorage energyStorage(@Nullable Direction side)
    {
        return energy;
    }

    public IItemHandler itemHandler(@Nullable Direction side)
    {
        return automation;
    }

    private static ItemStack withCount(ItemStack stack, int count)
    {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    // ---------------------------------------------------------------- capabilities

    /** The handler checks the mode on every call, so one LazyOptional serves both modes (no rebuild on a mode switch). */
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> automation);
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        itemCap.invalidate();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps()
    {
        super.reviveCaps();
        itemCap = LazyOptional.of(() -> automation);
        energyCap = LazyOptional.of(() -> energy);
    }

    // ---------------------------------------------------------------- tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, CargoUplinkBlockEntity be)
    {
        if (!Config.LOGISTICS_ENABLED.get() || be.owner == null || level.getServer() == null) return;
        if ((level.getGameTime() + pos.asLong()) % PERIOD != 0) return;
        be.operate(level);
    }

    /** One operation; returns the number of items moved. */
    public int operate(Level level)
    {
        if (owner == null || level.getServer() == null) return 0;
        CargoNetwork network = CargoNetwork.get(level.getServer());
        return receiving() ? receive(network) : send(network);
    }

    private int send(CargoNetwork network)
    {
        int perItem = Config.CARGO_FE_PER_ITEM.get();
        int budget = perItem <= 0 ? PER_OP : Math.min(PER_OP, energy.getEnergyStored() / perItem);
        int sent = 0;
        for (int slot = 0; slot < SLOTS && sent < budget; slot++)
        {
            ItemStack held = items.get(slot);
            if (held.isEmpty()) continue;
            int offer = Math.min(held.getCount(), budget - sent);
            ItemStack rest = network.insert(owner, withCount(held, offer));
            int moved = offer - rest.getCount();
            if (moved <= 0) continue;
            held.shrink(moved);
            if (held.isEmpty()) items.set(slot, ItemStack.EMPTY);
            sent += moved;
        }
        if (sent > 0)
        {
            energy.consume(sent * perItem);
            setChanged();
        }
        return sent;
    }

    private int receive(CargoNetwork network)
    {
        int moved = network.drain(owner, PER_OP, this::putIntoSlots);
        if (moved > 0) setChanged();
        return moved;
    }

    /** Merges onto the same item first, then an empty slot; returns what did not fit. */
    private ItemStack putIntoSlots(ItemStack stack)
    {
        ItemStack left = stack.copy();
        for (int pass = 0; pass < 2 && !left.isEmpty(); pass++)
        {
            for (int slot = 0; slot < SLOTS && !left.isEmpty(); slot++)
            {
                ItemStack held = items.get(slot);
                if (pass == 0 ? held.isEmpty() || !ItemStack.isSameItemSameTags(held, left) : !held.isEmpty()) continue;
                int room = left.getMaxStackSize() - held.getCount();
                int move = Math.min(room, left.getCount());
                if (move <= 0) continue;
                if (held.isEmpty()) items.set(slot, withCount(left, move));
                else held.grow(move);
                left.shrink(move);
            }
        }
        return left;
    }

    // ---------------------------------------------------------------- container

    @Override
    protected Component getDefaultName()
    {
        return Component.translatable("block.abyssia.cargo_uplink");
    }

    @Override
    public int getContainerSize()
    {
        return SLOTS;
    }

    @Override
    public boolean isEmpty()
    {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot)
    {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) setChanged();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player)
    {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent()
    {
        items.clear();
        setChanged();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory)
    {
        return new ChestMenu(MenuType.GENERIC_9x1, id, inventory, this, 1);
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Energy", energy.getEnergyStored());
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        energy.setEnergy(tag.getInt("Energy"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}
