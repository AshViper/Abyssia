package com.abyssia.habitat;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Opaque hull block with connected textures (CT01): one boolean per direction (PipeBlock properties) says the same
 * block is there; the 64 models (tools/habitat_assets.py) drop the frame band on connected sides.  Same logic as
 * {@link HabitatWindowBlock}.
 */
public class HabitatConnectedBlock extends Block
{
    public static final MapCodec<HabitatConnectedBlock> CODEC = simpleCodec(HabitatConnectedBlock::new);

    @Override
    protected MapCodec<? extends Block> codec()
    {
        return CODEC;
    }

    public HabitatConnectedBlock(Properties properties)
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

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving)
    {
        super.onPlace(state, level, pos, old, moving);
        BlockState linked = HabitatWindowBlock.connected(state, dir -> level.getBlockState(pos.relative(dir)).is(this));
        if (linked != state) level.setBlock(pos, linked, Block.UPDATE_CLIENTS);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos)
    {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), neighbour.is(this));
    }
}
