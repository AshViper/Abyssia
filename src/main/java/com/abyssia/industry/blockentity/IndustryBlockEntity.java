package com.abyssia.industry.blockentity;

import com.abyssia.industry.MachineKind;
import com.abyssia.industry.block.IndustryEntityBlock;
import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.industry.energy.IndustryEnergyStorage;
import com.abyssia.industry.menu.IndustryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * Common base of the industrial block entities: energy buffer (Capabilities.EnergyStorage.BLOCK on every side), slots
 * (Capabilities.ItemHandler.BLOCK: inputs from the top and sides, output from any side, bottom output only; a machine with a reagent
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

    private final IItemHandler topItems;
    private final IItemHandler sideItems;
    private final IItemHandler bottomItems;

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
        boolean split = kind.reagent;
        this.topItems = new SidedItemHandler(items, true, !split, true);
        this.sideItems = new SidedItemHandler(items, !split, true, true);
        this.bottomItems = new SidedItemHandler(items, false, true);
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

    /** Capabilities.EnergyStorage.BLOCK provider (registered in ModIndustry#registerCapabilities): every side. */
    public IEnergyStorage energyStorage(@Nullable Direction side)
    {
        return energy;
    }

    /** Capabilities.ItemHandler.BLOCK provider: whole inventory without a side, else the automation view of the face. */
    @Nullable
    public IItemHandler itemHandler(@Nullable Direction side)
    {
        if (items.getSlots() == 0) return null;
        if (side == null) return items;
        if (side == Direction.DOWN) return bottomItems;
        return side == Direction.UP ? topItems : sideItems;
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", progress);
        tag.putInt("Burn", burn);
        tag.putInt("BurnMax", burnMax);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) items.deserializeNBT(registries, tag.getCompound("Items"));
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
