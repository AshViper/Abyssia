package com.abyssia.habitat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import java.util.function.Predicate;

/**
 * Full-block pressure window (translucent model): keeps water out and lights / culls like glass.  Connected glass:
 * one boolean per direction (PipeBlock properties) says another window is there; the 64 models
 * (tools/habitat_assets.py) drop the frame on connected edges, so a block of windows reads as one pane.
 */
public class HabitatWindowBlock extends TransparentBlock
{
    public static final MapCodec<HabitatWindowBlock> CODEC = simpleCodec(HabitatWindowBlock::new);

    @Override
    protected MapCodec<? extends TransparentBlock> codec()
    {
        return CODEC;
    }

    public HabitatWindowBlock(Properties properties)
    {
        super(properties);
        BlockState state = stateDefinition.any();
        for (Direction dir : Direction.values()) state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    /** {@code base} with every direction set from {@code isWindow}. */
    public static BlockState connected(BlockState base, Predicate<Direction> isWindow)
    {
        for (Direction dir : Direction.values()) base = base.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), isWindow.test(dir));
        return base;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving)
    {
        super.onPlace(state, level, pos, old, moving);
        BlockState linked = connected(state, dir -> level.getBlockState(pos.relative(dir)).is(this));
        if (linked != state) level.setBlock(pos, linked, Block.UPDATE_CLIENTS);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos)
    {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), neighbour.is(this));
    }
}
