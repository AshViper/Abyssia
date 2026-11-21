package com.abyssia.logistics;

import com.abyssia.industry.block.IndustrialPipeBlock;
import com.abyssia.industry.block.ValveBlock;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Breadth-first walk over connected industrial pipes (only IndustrialPipeBlock; valves, pumps and unloaded chunks
 * end the walk) that collects the non-pipe blocks next to them which offer an item handler.
 */
public final class PipeNetwork
{
    private PipeNetwork() {}

    /** A destination: the block at pos, reached through its face. */
    public record Dest(BlockPos pos, Direction face) {}

    /** pipes = pipes visited, dests = destinations in BFS order (nearest pipe first). */
    public record Result(int pipes, List<Dest> dests)
    {
        public static final Result EMPTY = new Result(0, List.of());
    }

    public static Result scan(Level level, BlockPos start, BlockPos pump, BlockPos source, int maxPipes)
    {
        if (!level.isLoaded(start) || !(level.getBlockState(start).getBlock() instanceof IndustrialPipeBlock)) return Result.EMPTY;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LongOpenHashSet pipes = new LongOpenHashSet();
        LongOpenHashSet seen = new LongOpenHashSet();
        List<Dest> dests = new ArrayList<>();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        Direction[] dirs = Direction.values();
        BlockPos startImm = start.immutable();
        queue.add(startImm);
        pipes.add(startImm.asLong());
        while (!queue.isEmpty())
        {
            BlockPos at = queue.poll();
            for (Direction dir : dirs)
            {
                next.setWithOffset(at, dir);
                long key = next.asLong();
                if (pipes.contains(key) || seen.contains(key) || !level.isLoaded(next)) continue;
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof IndustrialPipeBlock)
                {
                    if (pipes.size() >= maxPipes) continue;
                    BlockPos immutable = next.immutable();
                    pipes.add(key);
                    queue.add(immutable);
                    continue;
                }
                if (state.getBlock() instanceof ValveBlock || next.equals(pump) || next.equals(source)) continue;
                seen.add(key);
                // every item handler of this mod and of vanilla is a block-entity capability; skip blocks without one
                if (state.isAir() || !state.hasBlockEntity()) continue;
                if (ItemLookup.get(level, next, dir.getOpposite()) != null) dests.add(new Dest(next.immutable(), dir.getOpposite()));
            }
        }
        return new Result(pipes.size(), dests);
    }
}
