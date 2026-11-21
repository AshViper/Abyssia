package com.abyssia.industry.blockentity;

import com.abyssia.Config;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.PrecipitatorYields;
import com.abyssia.industry.VentHeat;
import com.abyssia.registry.ModIndustry;
import com.abyssia.thermal.ThermalVentType;
import com.abyssia.thermal.VentActivity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * PRC01 hydrothermal precipitator: next to a live thermal vent it uses 20 FE/t and makes one raw metal per cycle
 * (400 ticks / (VentHeat.multiplier x precipitator_speed)). Two precipitators on one vent: only the one with the lowest
 * BlockPos.asLong() works. Output only (9 slots). Status: 1 no vent, 2 dormant, 3 vent used by another, 4 output full,
 * 5 no power, 6 disabled in the config, 7 working.
 */
public class PrecipitatorBlockEntity extends IndustryBlockEntity
{
    public static final int FE_PER_TICK = 20;
    public static final int BASE_CYCLE = 400;
    public static final int STATUS_NO_VENT = 1, STATUS_DORMANT = 2, STATUS_SHARED = 3, STATUS_OUTPUT_FULL = 4,
            STATUS_NO_POWER = 5, STATUS_DISABLED = 6, STATUS_WORKING = 7;

    private static final int CHECK_INTERVAL = 20;

    private int ventCheck;
    @Nullable
    private ThermalVentType ventType;
    @Nullable
    private VentActivity ventActivity;
    private boolean shared;
    private BlockPos ventPos = BlockPos.ZERO;
    /** a finished metal that did not fit into the outputs yet */
    private ItemStack pending = ItemStack.EMPTY;

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        if (!pending.isEmpty()) tag.put("Pending", pending.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        pending = tag.contains("Pending") ? ItemStack.parseOptional(registries, tag.getCompound("Pending")) : ItemStack.EMPTY;
    }

    /** the held metal is paid for: it drops with the slots when the block is broken */
    @Override
    public void dropContents(net.minecraft.world.level.Level level, BlockPos pos)
    {
        super.dropContents(level, pos);
        if (!pending.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pending);
        pending = ItemStack.EMPTY;
    }

    @Override
    public void clearContent()
    {
        super.clearContent();
        pending = ItemStack.EMPTY;
    }

    public PrecipitatorBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModIndustry.PRECIPITATOR_ENTITY.get(), pos, state, MachineKind.MACHINE_CAPACITY, MachineKind.MACHINE_RECEIVE, 0);
    }

    /** Looks at the 6 neighbours: the most active vent wins; shared = a precipitator with a lower position touches the same vent. */
    private void scanVent()
    {
        ventType = null;
        ventActivity = null;
        shared = false;
        BlockPos best = null;
        for (Direction dir : Direction.values())
        {
            BlockPos n = worldPosition.relative(dir);
            if (!level.isLoaded(n)) continue;
            BlockState s = level.getBlockState(n);
            if (!(s.getBlock() instanceof ThermalVentBlock)) continue;
            VentActivity a = s.getValue(ThermalVentBlock.ACTIVITY);
            if (ventActivity == null || a.ordinal() > ventActivity.ordinal())
            {
                ventActivity = a;
                ventType = s.getValue(ThermalVentBlock.TYPE);
                best = n;
            }
        }
        if (best == null) return;
        ventPos = best;
        for (Direction dir : Direction.values())
        {
            BlockPos n = best.relative(dir);
            if (n.equals(worldPosition) || !level.isLoaded(n)) continue;
            if (level.getBlockEntity(n) instanceof PrecipitatorBlockEntity other && other.worldPosition.asLong() < worldPosition.asLong())
                shared = true;
        }
    }

    /** ticks per item for the current vent; 0 if it does not run */
    private int cycle()
    {
        float mult = VentHeat.multiplier(ventActivity);
        if (mult <= 0) return 0;
        return (int) Math.ceil(BASE_CYCLE / (mult * Config.PRECIPITATOR_SPEED.get()));
    }

    @Override
    protected void work()
    {
        if (--ventCheck <= 0)
        {
            ventCheck = CHECK_INTERVAL;
            scanVent();
        }
        rate = 0;
        boolean working = step();
        maxProgress = cycle();
        setWorking(working);
    }

    private boolean step()
    {
        if (!Config.PRECIPITATOR_ENABLED.get()) return stop(STATUS_DISABLED);
        if (ventActivity == null) return stop(STATUS_NO_VENT);
        int cycle = cycle();
        if (cycle <= 0) return stop(STATUS_DORMANT);
        if (shared) return stop(STATUS_SHARED);
        if (!pending.isEmpty())
        {
            // a metal waits for room: no FE is spent until it fits
            if (!store(pending)) return stop(STATUS_OUTPUT_FULL);
            pending = ItemStack.EMPTY;
            progress = 0;
            status = STATUS_WORKING;
            setChanged();
            return true;
        }
        if (outputFull()) return stop(STATUS_OUTPUT_FULL);
        if (progress < cycle)
        {
            if (energy.getEnergyStored() < FE_PER_TICK) return stop(STATUS_NO_POWER);
            energy.consume(FE_PER_TICK);
            rate = FE_PER_TICK;
            progress++;
        }
        if (progress >= cycle)
        {
            // the metal is rolled once when the cycle ends; if it does not fit it is kept in `pending`
            ItemStack rolled = PrecipitatorYields.roll(ventType,
                    PrecipitatorYields.ventKey(level instanceof net.minecraft.server.level.ServerLevel sl ? sl.getSeed() : 0L, ventPos), level.getRandom());
            if (!store(rolled))
            {
                pending = rolled;
                setChanged();
                return stop(STATUS_OUTPUT_FULL);
            }
            progress = 0;
        }
        status = STATUS_WORKING;
        setChanged();
        return true;
    }

    private boolean stop(int newStatus)
    {
        status = newStatus;
        return false;
    }

    /** no empty slot and every stack at its limit */
    private boolean outputFull()
    {
        for (int i = 0; i < items.getSlots(); i++)
        {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return false;
        }
        return true;
    }

    /** adds one item: onto a matching stack first, else into an empty slot */
    private boolean store(ItemStack stack)
    {
        for (int i = 0; i < items.getSlots(); i++)
        {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize())
            {
                ItemStack grown = s.copy();
                grown.grow(1);
                items.setStackInSlot(i, grown);
                return true;
            }
        }
        for (int i = 0; i < items.getSlots(); i++)
        {
            if (items.getStackInSlot(i).isEmpty())
            {
                items.setStackInSlot(i, stack.copy());
                return true;
            }
        }
        return false;
    }
}
