package com.abyssia.industry;

import com.abyssia.industry.block.AbyssalExcavatorBlock;
import com.abyssia.industry.block.ExcavatorPartBlock;
import com.abyssia.industry.blockentity.ExcavatorPartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * ORE01: the excavator multiblock, 3 x 3 x 3 blocks. The master block (AbyssalExcavatorBlock) sits at the centre-bottom
 * cell; the collision cells (the four corner columns and the centre column above the master) hold
 * {@link ExcavatorPartBlock}s, every other cell stays water. Offsets are (dx, dz) in -1..1 from the master, dy 0..2
 * above it; the layout is symmetric under rotation, so it does not depend on FACING. The boxes are written by
 * tools/bt01/excavator_assets.py (keep in sync).
 */
public final class ExcavatorStructure
{
    private ExcavatorStructure() {}

    /** whether the cell at the given offset from the master holds a block (the master itself included) */
    public static boolean solid(int dx, int dy, int dz)
    {
        return (dx == 0 && dz == 0) || (dx != 0 && dz != 0);
    }

    /** every footprint cell, bottom-up */
    public static List<BlockPos> cells(BlockPos master)
    {
        List<BlockPos> out = new ArrayList<>(27);
        for (int dy = 0; dy < 3; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) out.add(master.offset(dx, dy, dz));
        return out;
    }

    /** the part cells (the master cell is not included), bottom-up */
    public static List<BlockPos> partCells(BlockPos master)
    {
        List<BlockPos> out = new ArrayList<>(14);
        for (int dy = 0; dy < 3; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (solid(dx, dy, dz) && (dx != 0 || dy != 0 || dz != 0)) out.add(master.offset(dx, dy, dz));
        return out;
    }

    /** turns this machine's parts and master back into water (the master drops its inventory when it goes) */
    public static void remove(Level level, BlockPos master)
    {
        for (BlockPos pos : partCells(master))
        {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ExcavatorPartBlock && ownedBy(level, pos, master))
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (level.getBlockState(master).getBlock() instanceof AbyssalExcavatorBlock)
            level.setBlock(master, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static boolean ownedBy(Level level, BlockPos part, BlockPos master)
    {
        return !(level.getBlockEntity(part) instanceof ExcavatorPartBlockEntity be) || be.controller() == null || be.controller().equals(master);
    }
}
