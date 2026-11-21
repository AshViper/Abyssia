package com.abyssia.logistics;

import com.abyssia.Config;
import com.abyssia.industry.block.FacingDecorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

/**
 * Logistics pump: every 10 ticks it takes up to pump_items_per_op items from the block behind it and pushes them into
 * the item handlers next to the pipe network in front of it (nearest pipe first). The network is walked again every
 * 40 ticks; handlers are looked up on every operation. Nothing is taken that the simulation did not accept.
 */
public class LogisticsPumpBlockEntity extends BlockEntity
{
    private static final int PERIOD = 10, REBUILD = 40;

    private PipeNetwork.Result network = PipeNetwork.Result.EMPTY;
    private long builtAt = Long.MIN_VALUE;

    public LogisticsPumpBlockEntity(BlockPos pos, BlockState state)
    {
        super(LogisticsContent.PUMP_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LogisticsPumpBlockEntity pump)
    {
        if (!Config.LOGISTICS_ENABLED.get())
        {
            pump.network = PipeNetwork.Result.EMPTY;
            pump.builtAt = Long.MIN_VALUE;
            return;
        }
        if ((level.getGameTime() + pos.asLong()) % PERIOD != 0) return;
        pump.operate(level);
    }

    /** One operation (the periodic tick calls it); returns the number of items moved. */
    public int operate(Level level)
    {
        BlockState state = getBlockState();
        if (!Config.LOGISTICS_ENABLED.get() || !state.hasProperty(FacingDecorBlock.FACING)) return 0;
        Direction facing = state.getValue(FacingDecorBlock.FACING);
        BlockPos source = worldPosition.relative(facing.getOpposite());
        long now = level.getGameTime();
        if (builtAt == Long.MIN_VALUE || now - builtAt >= REBUILD || now < builtAt) rescan(level);
        if (network.dests().isEmpty()) return 0;
        IItemHandler from = ItemLookup.get(level, source, facing);
        if (from == null) return 0;

        int budget = Config.PUMP_ITEMS_PER_OP.get();
        int moved = 0;
        for (int slot = 0; slot < from.getSlots() && budget > 0; slot++)
        {
            ItemStack offer = from.extractItem(slot, budget, true);
            if (offer.isEmpty()) continue;
            for (PipeNetwork.Dest dest : network.dests())
            {
                if (offer.isEmpty() || budget <= 0) break;
                IItemHandler to = ItemLookup.get(level, dest.pos(), dest.face());
                if (to == null) continue;
                ItemStack rest = ItemLookup.insert(to, offer, true);
                int fits = offer.getCount() - rest.getCount();
                if (fits <= 0) continue;
                // take exactly what the simulation said fits, then put it in for real
                ItemStack taken = from.extractItem(slot, Math.min(fits, budget), false);
                if (taken.isEmpty()) break;
                ItemStack left = ItemLookup.insert(to, taken, false);
                int placed = taken.getCount() - left.getCount();
                moved += placed;
                budget -= placed;
                if (!left.isEmpty()) giveBack(level, from, slot, left);
                offer = from.extractItem(slot, budget, true);
            }
        }
        return moved;
    }

    /** Walks the pipe network again now. */
    public void rescan(Level level)
    {
        BlockState state = getBlockState();
        if (!state.hasProperty(FacingDecorBlock.FACING)) return;
        Direction facing = state.getValue(FacingDecorBlock.FACING);
        builtAt = level.getGameTime();
        network = PipeNetwork.scan(level, worldPosition.relative(facing), worldPosition,
                worldPosition.relative(facing.getOpposite()), Config.PUMP_MAX_PIPES.get());
    }

    /** A destination took less than its simulation promised: the rest goes back, or onto the ground (never lost). */
    private void giveBack(Level level, IItemHandler from, int slot, ItemStack left)
    {
        ItemStack back = from.insertItem(slot, left, false);
        if (!back.isEmpty()) back = ItemLookup.insert(from, back, false);
        if (!back.isEmpty())
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, back);
    }

    /** Pipes of the current network (right-click message, tests). */
    public int pipeCount()
    {
        return Config.LOGISTICS_ENABLED.get() ? network.pipes() : 0;
    }

    /** Destinations of the current network (0 when logistics is off). */
    public int destCount()
    {
        return Config.LOGISTICS_ENABLED.get() ? network.dests().size() : 0;
    }

    /** Forces the network to be walked again on the next operation. */
    public void invalidate()
    {
        builtAt = Long.MIN_VALUE;
    }
}
