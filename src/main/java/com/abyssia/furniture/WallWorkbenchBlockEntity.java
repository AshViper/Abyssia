package com.abyssia.furniture;

import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.industry.energy.IndustryEnergyStorage;
import com.abyssia.registry.ModFurniture;
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
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Wall workbench storage: a 20,000 FE buffer that only receives (every face, so the industry cable network treats it
 * as a consumer) and the charge slot, topped up at up to 2,000 FE/t. The crafting grid lives in the menu (it goes back
 * to the player on close); the charge item is saved here and drops when the block breaks.
 */
public class WallWorkbenchBlockEntity extends BlockEntity implements MenuProvider, Clearable
{
    public static final int CAPACITY = 20_000;
    public static final int MAX_RECEIVE = 2_000;
    public static final int CHARGE_RATE = 2_000;
    /** ContainerData: energy and capacity as 16-bit lo / hi pairs (data slots sync as shorts) */
    public static final int DATA_ENERGY = 0, DATA_CAPACITY = 2, DATA_COUNT = 4;

    private final IndustryEnergyStorage energy = new IndustryEnergyStorage(CAPACITY, MAX_RECEIVE, 0, this::setChanged);
    private final ChargeSlotHandler charge = new ChargeSlotHandler(this::setChanged);
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    private final ContainerData data = new ContainerData()
    {
        @Override
        public int get(int index)
        {
            return switch (index)
            {
                case DATA_ENERGY -> energy.getEnergyStored() & 0xFFFF;
                case DATA_ENERGY + 1 -> energy.getEnergyStored() >>> 16;
                case DATA_CAPACITY -> energy.getMaxEnergyStored() & 0xFFFF;
                case DATA_CAPACITY + 1 -> energy.getMaxEnergyStored() >>> 16;
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

    public WallWorkbenchBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModFurniture.WALL_WORKBENCH_ENTITY.get(), pos, state);
    }

    /** The FE buffer (for tests / tooling; the cable network uses the capability). */
    public IEnergyStorage energy()
    {
        return energy;
    }

    public ChargeSlotHandler charge()
    {
        return charge;
    }

    public ContainerData data()
    {
        return data;
    }

    // ---------------------------------------------------------------- tick

    public void serverTick()
    {
        if (level == null) return;
        chargeItem();
        boolean powered = energy.getEnergyStored() > 0;
        BlockState state = getBlockState();
        if (state.hasProperty(WallWorkbenchBlock.POWERED) && state.getValue(WallWorkbenchBlock.POWERED) != powered)
            level.setBlock(worldPosition, state.setValue(WallWorkbenchBlock.POWERED, powered), 3);
        if ((level.getGameTime() + worldPosition.asLong()) % 10 == 0) CableNetworkManager.touchAround(level, worldPosition);
    }

    private void chargeItem()
    {
        ItemStack stack = charge.getStackInSlot(0);
        if (stack.isEmpty() || energy.getEnergyStored() <= 0) return;
        IEnergyStorage target = stack.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
        if (target == null || !target.canReceive()) return;
        int offer = Math.min(CHARGE_RATE, energy.getEnergyStored());
        int accepted = target.receiveEnergy(offer, false);
        if (accepted > 0)
        {
            energy.consume(Math.min(accepted, offer));
            setChanged();
        }
    }

    // ---------------------------------------------------------------- contents

    public void dropContents(Level level, BlockPos pos)
    {
        ItemStack stack = charge.getStackInSlot(0);
        if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
        charge.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    public void clearContent()
    {
        charge.setStackInSlot(0, ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- capabilities

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps()
    {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energy);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.put("Charge", charge.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        energy.setEnergy(tag.getInt("Energy"));
        if (tag.contains("Charge")) charge.deserializeNBT(tag.getCompound("Charge"));
    }

    // ---------------------------------------------------------------- menu

    @Override
    public Component getDisplayName()
    {
        return Component.translatable("container.abyssia.wall_workbench");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player)
    {
        return new WallWorkbenchMenu(id, inventory, charge, data, ContainerLevelAccess.create(level, worldPosition));
    }
}
