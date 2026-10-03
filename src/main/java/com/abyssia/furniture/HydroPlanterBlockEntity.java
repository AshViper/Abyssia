package com.abyssia.furniture;

import com.abyssia.registry.ModFurniture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Hydro planter cells (feature PL02, inbox/specs/PL02-planter-remake.md): four cells NW / NE / SW / SE, each with a
 * crop and the game time its growth started. Growth is computed lazily from the game time (no ticker; it also advances
 * while unloaded). A cell is ripe once the crop's time has passed; harvesting gives the produce and restarts the cell
 * (the seedling stays). Clients get the cells through the update tag and render them with HydroPlanterRenderer.
 * <p>
 * Automation: an extract-only IItemHandler (4 slots, every side except the top) shows the produce of ripe cells.
 * Hoppers take 1 item at a time, so the amount taken from a cell is tracked; when it is empty the cell restarts.
 * <p>
 * Legacy PL01 data (one "Seed" slot + "Growth" ticks) is moved to the NW cell; a stack that is not a seedling is kept
 * as a leftover and dropped when the planter breaks.
 */
public class HydroPlanterBlockEntity extends BlockEntity
{
    public static final int CELLS = 4;

    private final PlanterCrop[] crops = {PlanterCrop.NONE, PlanterCrop.NONE, PlanterCrop.NONE, PlanterCrop.NONE};
    /** game time the current growth started */
    private final long[] planted = new long[CELLS];
    /** produce already taken out of a ripe cell by automation */
    private final int[] taken = new int[CELLS];
    /** legacy growth ticks waiting for a level to turn into a start time (-1 = none) */
    private final int[] pendingProgress = {-1, -1, -1, -1};
    /** legacy items that are not seedlings: dropped on break */
    private final List<ItemStack> leftovers = new ArrayList<>();

    private final IItemHandler output = new IItemHandler()
    {
        @Override
        public int getSlots()
        {
            return CELLS;
        }

        @Override
        public @Nonnull ItemStack getStackInSlot(int slot)
        {
            return slot < 0 || slot >= CELLS ? ItemStack.EMPTY : remainingProduce(slot);
        }

        @Override
        public @Nonnull ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate)
        {
            return stack;
        }

        @Override
        public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate)
        {
            if (slot < 0 || slot >= CELLS || amount <= 0 || level == null || level.isClientSide) return ItemStack.EMPTY;
            ItemStack left = remainingProduce(slot);
            if (left.isEmpty()) return ItemStack.EMPTY;
            int n = Math.min(amount, left.getCount());
            ItemStack out = left.copyWithCount(n);
            if (!simulate)
            {
                taken[slot] += n;
                if (n >= left.getCount()) restart(slot);
                changed();
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
            return false;
        }
    };
    private LazyOptional<IItemHandler> outputCap = LazyOptional.of(() -> output);

    public HydroPlanterBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModFurniture.HYDRO_PLANTER_ENTITY.get(), pos, state);
    }

    // ---------------------------------------------------------------- cells

    /** Cell index from a position inside the block (0..1 local): bit 0 = east half, bit 1 = south half. */
    public static int cellAt(double localX, double localZ)
    {
        return (localX >= 0.5 ? 1 : 0) | (localZ >= 0.5 ? 2 : 0);
    }

    public PlanterCrop crop(int cell)
    {
        return crops[cell];
    }

    private long now()
    {
        return level == null ? 0 : level.getGameTime();
    }

    private void resolvePending()
    {
        if (level == null) return;
        for (int i = 0; i < CELLS; i++)
            if (pendingProgress[i] >= 0)
            {
                planted[i] = level.getGameTime() - pendingProgress[i];
                pendingProgress[i] = -1;
            }
    }

    /** Growth ticks of a cell, 0..crop time. */
    public long progress(int cell)
    {
        PlanterCrop crop = crops[cell];
        if (crop == PlanterCrop.NONE) return 0;
        if (pendingProgress[cell] >= 0) return Math.min(crop.ticks, pendingProgress[cell]);
        return Math.max(0, Math.min(crop.ticks, now() - planted[cell]));
    }

    public boolean isRipe(int cell)
    {
        return crops[cell] != PlanterCrop.NONE && progress(cell) >= crops[cell].ticks;
    }

    /** Visual stage 0..2 of a cell. */
    public int stage(int cell)
    {
        return crops[cell].stage(progress(cell));
    }

    /** Produce amount of the current harvest: fixed per growth cycle so simulate / extract agree. */
    private int produceCount(int cell)
    {
        PlanterCrop crop = crops[cell];
        int range = crop.max - crop.min + 1;
        long h = planted[cell] * 0x9E3779B97F4A7C15L + cell * 31L + worldPosition.asLong();
        return crop.min + (int) Math.floorMod(h ^ (h >>> 29), (long) range);
    }

    /** What is left of a ripe cell's produce; empty when not ripe. */
    private ItemStack remainingProduce(int cell)
    {
        if (!isRipe(cell)) return ItemStack.EMPTY;
        resolvePending();
        var item = crops[cell].resultItem();
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        int left = produceCount(cell) - taken[cell];
        return left <= 0 ? ItemStack.EMPTY : new ItemStack(item, left);
    }

    private void restart(int cell)
    {
        resolvePending();
        planted[cell] = now();
        taken[cell] = 0;
    }

    /** Plants a crop into an empty cell; false when the cell is taken. */
    public boolean plant(int cell, PlanterCrop crop)
    {
        if (crop == PlanterCrop.NONE || crops[cell] != PlanterCrop.NONE) return false;
        crops[cell] = crop;
        pendingProgress[cell] = -1;
        restart(cell);
        changed();
        return true;
    }

    /** Harvests a ripe cell: returns the remaining produce and restarts the cell (seedling stays). */
    public ItemStack harvest(int cell)
    {
        ItemStack out = remainingProduce(cell);
        if (!isRipe(cell)) return ItemStack.EMPTY;
        restart(cell);
        changed();
        return out;
    }

    /** Takes the seedling out of a cell (the cell becomes empty); empty stack when there was none. */
    public ItemStack removeSeedling(int cell)
    {
        PlanterCrop crop = crops[cell];
        if (crop == PlanterCrop.NONE) return ItemStack.EMPTY;
        crops[cell] = PlanterCrop.NONE;
        planted[cell] = 0;
        taken[cell] = 0;
        pendingProgress[cell] = -1;
        changed();
        return crop.seedStack();
    }

    /** Bone meal: moves the growth start back by a tenth of the crop time; false when nothing changed. */
    public boolean advance(int cell)
    {
        PlanterCrop crop = crops[cell];
        if (crop == PlanterCrop.NONE || isRipe(cell)) return false;
        resolvePending();
        planted[cell] -= Math.max(1, crop.ticks / 10);
        changed();
        return true;
    }

    /** Drops every seedling (and legacy leftovers) at the block; called when the block is removed. */
    public void dropContents()
    {
        if (level == null) return;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        for (int i = 0; i < CELLS; i++)
        {
            ItemStack seed = crops[i].seedStack();
            if (!seed.isEmpty()) Containers.dropItemStack(level, x, y, z, seed);
            crops[i] = PlanterCrop.NONE;
        }
        for (ItemStack stack : leftovers) Containers.dropItemStack(level, x, y, z, stack);
        leftovers.clear();
    }

    private void changed()
    {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void onLoad()
    {
        super.onLoad();
        if (level != null && !level.isClientSide)
        {
            boolean pending = false;
            for (int p : pendingProgress) pending |= p >= 0;
            resolvePending();
            if (pending) setChanged();
        }
    }

    // ---------------------------------------------------------------- capability

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !remove && side != Direction.UP) return outputCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        outputCap.invalidate();
    }

    @Override
    public void reviveCaps()
    {
        super.reviveCaps();
        outputCap = LazyOptional.of(() -> output);
    }

    // ---------------------------------------------------------------- save / sync

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        resolvePending();
        ListTag cells = new ListTag();
        for (int i = 0; i < CELLS; i++)
        {
            CompoundTag c = new CompoundTag();
            c.putString("Crop", crops[i].getSerializedName());
            if (pendingProgress[i] >= 0) c.putInt("Progress", pendingProgress[i]);
            else c.putLong("Planted", planted[i]);
            if (taken[i] > 0) c.putInt("Taken", taken[i]);
            cells.add(c);
        }
        tag.put("Cells", cells);
        if (!leftovers.isEmpty())
        {
            ListTag list = new ListTag();
            for (ItemStack stack : leftovers) list.add(stack.save(new CompoundTag()));
            tag.put("Leftovers", list);
        }
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        for (int i = 0; i < CELLS; i++)
        {
            crops[i] = PlanterCrop.NONE;
            planted[i] = 0;
            taken[i] = 0;
            pendingProgress[i] = -1;
        }
        leftovers.clear();
        if (tag.contains("Leftovers", Tag.TAG_LIST))
        {
            ListTag list = tag.getList("Leftovers", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++)
            {
                ItemStack stack = ItemStack.of(list.getCompound(i));
                if (!stack.isEmpty()) leftovers.add(stack);
            }
        }
        if (tag.contains("Cells", Tag.TAG_LIST))
        {
            ListTag cells = tag.getList("Cells", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(CELLS, cells.size()); i++)
            {
                CompoundTag c = cells.getCompound(i);
                crops[i] = PlanterCrop.byName(c.getString("Crop"));
                if (crops[i] == PlanterCrop.NONE) continue;
                if (c.contains("Progress")) pendingProgress[i] = Math.max(0, c.getInt("Progress"));
                else planted[i] = c.getLong("Planted");
                taken[i] = Math.max(0, c.getInt("Taken"));
            }
        }
        else if (tag.contains("Seed", Tag.TAG_COMPOUND)) loadLegacy(tag);
    }

    /** PL01 format: one seedling slot + growth ticks -> NW cell; anything else is kept to drop on break. */
    private void loadLegacy(CompoundTag tag)
    {
        ItemStackHandler seed = new ItemStackHandler(1);
        try
        {
            seed.deserializeNBT(tag.getCompound("Seed"));
        }
        catch (RuntimeException e)
        {
            return;
        }
        ItemStack stack = seed.getStackInSlot(0);
        if (stack.isEmpty()) return;
        PlanterCrop crop = PlanterCrop.of(stack);
        if (crop == PlanterCrop.NONE)
        {
            leftovers.add(stack.copy());
            return;
        }
        crops[0] = crop;
        pendingProgress[0] = Math.max(0, Math.min(crop.ticks, tag.getInt("Growth")));
        if (stack.getCount() > 1) leftovers.add(stack.copyWithCount(stack.getCount() - 1));
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
