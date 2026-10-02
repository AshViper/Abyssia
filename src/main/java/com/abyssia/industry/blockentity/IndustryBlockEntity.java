package com.abyssia.industry.blockentity;

import com.abyssia.industry.MachineKind;
import com.abyssia.industry.block.IndustryEntityBlock;
import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.industry.energy.IndustryEnergyStorage;
import com.abyssia.industry.menu.IndustryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Common base of the industrial block entities: energy buffer (ForgeCapabilities.ENERGY on every side), slots
 * (ITEM_HANDLER: inputs from the top and sides, output from any side, bottom output only; a machine with a reagent
 * slot takes its input from the top and the reagent from the sides), the GUI data and the
 * server tick. Subclasses do the work in {@link #work()}.
 */
public abstract class IndustryBlockEntity extends BlockEntity implements MenuProvider, Clearable
{
    /** ContainerData slots; 32-bit values are split into 16-bit lo/hi pairs (data slots sync as shorts). */
    public static final int DATA_PROGRESS = 0, DATA_MAX_PROGRESS = 1, DATA_ENERGY = 2, DATA_CAPACITY = 4,
            DATA_BURN = 6, DATA_BURN_MAX = 8, DATA_RATE = 10, DATA_STATUS = 11, DATA_COUNT = 12;

    private static final BooleanProperty LIT = BlockStateProperties.LIT;

    protected final MachineKind kind;
    protected final IndustryEnergyStorage energy;
    protected final MachineInventory items;

    protected int progress;
    protected int maxProgress;
    protected int burn;
    protected int burnMax;
    /** FE/t shown in the GUI (generation, or use while working) */
    protected int rate;
    /** kind-specific flags / values shown in the GUI */
    protected int status;
    private int litLinger;

    private LazyOptional<IEnergyStorage> energyCap;
    private LazyOptional<IItemHandler> allItems;
    private LazyOptional<IItemHandler> topItems;
    private LazyOptional<IItemHandler> sideItems;
    private LazyOptional<IItemHandler> bottomItems;

    protected final ContainerData data = new ContainerData()
    {
        @Override
        public int get(int index)
        {
            return switch (index)
            {
                case DATA_PROGRESS -> progress;
                case DATA_MAX_PROGRESS -> maxProgress;
                case DATA_ENERGY -> energy.getEnergyStored() & 0xFFFF;
                case DATA_ENERGY + 1 -> energy.getEnergyStored() >>> 16;
                case DATA_CAPACITY -> energy.getMaxEnergyStored() & 0xFFFF;
                case DATA_CAPACITY + 1 -> energy.getMaxEnergyStored() >>> 16;
                case DATA_BURN -> burn & 0xFFFF;
                case DATA_BURN + 1 -> burn >>> 16;
                case DATA_BURN_MAX -> burnMax & 0xFFFF;
                case DATA_BURN_MAX + 1 -> burnMax >>> 16;
                case DATA_RATE -> rate;
                case DATA_STATUS -> status;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value)
        {
        }

        @Override
        public int getCount()
        {
            return DATA_COUNT;
        }
    };

    protected IndustryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity, int maxReceive, int maxExtract)
    {
        super(type, pos, state);
        this.kind = ((IndustryEntityBlock) state.getBlock()).kind();
        this.energy = new IndustryEnergyStorage(capacity, maxReceive, maxExtract, this::setChanged);
        this.items = new MachineInventory(kind, this::getLevel, this::onItemsChanged);
        createCaps();
    }

    public MachineKind kind()
    {
        return kind;
    }

    /** /fill and structure placement empty the block first instead of spilling its items (like vanilla chests). */
    @Override
    public void clearContent()
    {
        for (int i = 0; i < items.getSlots(); i++) items.setStackInSlot(i, ItemStack.EMPTY);
    }

    public MachineInventory items()
    {
        return items;
    }

    public ContainerData data()
    {
        return data;
    }

    protected void onItemsChanged()
    {
        setChanged();
    }

    // ---------------------------------------------------------------- tick

    /** Server tick (registered by IndustryEntityBlock#getTicker). */
    public final void serverTick()
    {
        if (level == null) return;
        work();
        if ((level.getGameTime() + worldPosition.asLong()) % 10 == 0) CableNetworkManager.touchAround(level, worldPosition);
    }

    protected abstract void work();

    /** Sets LIT (if the block has it); stays on a few ticks after the last work so an energy-starved machine doesn't flicker. */
    protected void setWorking(boolean working)
    {
        if (working) litLinger = 10;
        else if (litLinger > 0) litLinger--;
        boolean lit = litLinger > 0;
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(LIT) && state.getValue(LIT) != lit)
            level.setBlock(worldPosition, state.setValue(LIT, lit), 3);
    }

    // ---------------------------------------------------------------- inventory

    public void dropContents(Level level, BlockPos pos)
    {
        for (int i = 0; i < items.getSlots(); i++)
        {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ---------------------------------------------------------------- capabilities

    private void createCaps()
    {
        energyCap = LazyOptional.of(() -> energy);
        allItems = LazyOptional.of(() -> items);
        boolean split = kind.reagent;
        topItems = LazyOptional.of(() -> new SidedItemHandler(items, true, !split, true));
        sideItems = LazyOptional.of(() -> new SidedItemHandler(items, !split, true, true));
        bottomItems = LazyOptional.of(() -> new SidedItemHandler(items, false, true));
    }

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER && items.getSlots() > 0)
        {
            if (side == null) return allItems.cast();
            if (side == Direction.DOWN) return bottomItems.cast();
            return side == Direction.UP ? topItems.cast() : sideItems.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        energyCap.invalidate();
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

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", progress);
        tag.putInt("Burn", burn);
        tag.putInt("BurnMax", burnMax);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        energy.setEnergy(tag.getInt("Energy"));
        progress = tag.getInt("Progress");
        burn = tag.getInt("Burn");
        burnMax = tag.getInt("BurnMax");
    }

    // ---------------------------------------------------------------- menu

    @Override
    public Component getDisplayName()
    {
        return Component.translatable("container.abyssia." + kind.id);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player)
    {
        return new IndustryMenu(id, inventory, this);
    }
}
