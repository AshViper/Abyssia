package com.abyssia.worldgen;

import com.abyssia.block.FrondBlock;
import com.abyssia.registry.ModBlocks;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The small underwater ancient tree: an ancient_stem trunk 5-9 high with 2-3 layers of ancient_frond on top (up to 5
 * wide). Shared by the ancient sapling's growth and the {@code abyssia:ancient_tree} feature. Nothing is placed
 * unless every cell it needs holds water or a replaceable plant.
 */
public final class AncientTree
{
    private AncientTree() {}

    /** Whether a tree can root at {@code base} (the block the trunk starts in): water or a plant above sturdy ground. */
    public static boolean canRoot(LevelAccessor level, BlockPos base)
    {
        BlockPos below = base.below();
        BlockState ground = level.getBlockState(below);
        return ground.isFaceSturdy(level, below, Direction.UP) && !ground.is(ModTags.INHIBITS_PLANTS);
    }

    /** Grows the tree with its trunk starting at {@code base}; returns false (changing nothing) if it does not fit. */
    public static boolean place(LevelAccessor level, BlockPos base, RandomSource random, int flags)
    {
        if (!canRoot(level, base)) return false;
        int height = Mth.randomBetweenInclusive(random, 5, 9);
        int layers = Mth.randomBetweenInclusive(random, 2, 3);
        int ceiling = DeepLayer.isDeep(base.getY()) ? DeepLayer.CEILING_BOTTOM_Y : level.getMaxBuildHeight();
        if (base.getY() + height + 1 >= ceiling) return false;

        BlockState stem = ModBlocks.ANCIENT_STEM.get().defaultBlockState();
        BlockState frond = ModBlocks.ANCIENT_FROND.get().defaultBlockState();
        Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        for (int h = 0; h < height; h++) cells.put(base.above(h), stem);
        // Frond layers sit on the top of the trunk: the lowest and widest first, a cap one block above the trunk.
        for (int i = 0; i < layers; i++)
        {
            int y = height - layers + 1 + i;
            int r = i == layers - 1 ? 1 : 2;
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    if (Math.abs(dx) == r && Math.abs(dz) == r) continue;
                    BlockPos pos = base.offset(dx, y, dz);
                    cells.putIfAbsent(pos, frond);
                }
            }
        }

        for (BlockPos pos : cells.keySet())
        {
            if (level.isOutsideBuildHeight(pos)) return false;
            BlockState current = level.getBlockState(pos);
            boolean ok = current.is(Blocks.WATER) || current.canBeReplaced() || current.is(BlockTags.REPLACEABLE_BY_TREES)
                    || current.getBlock() instanceof BushBlock;
            if (!ok) return false;
        }
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet())
        {
            BlockState state = e.getValue();
            if (state.hasProperty(FrondBlock.WATERLOGGED))
                state = state.setValue(FrondBlock.WATERLOGGED, level.getFluidState(e.getKey()).is(FluidTags.WATER));
            level.setBlock(e.getKey(), state, flags);
        }
        return true;
    }
}
