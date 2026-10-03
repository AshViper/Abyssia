package com.abyssia.block;

import com.abyssia.worldgen.AncientTree;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Underwater sapling of the small ancient tree (ancient_stem trunk, ancient_frond crown). Needs no light, grows only in
 * the deep layer, and takes two random-tick steps (stage 0, 1, tree) averaging about half an hour; bone meal advances
 * a stage 45% of the time. If the tree does not fit, the sapling simply stays.
 */
public class AncientSaplingBlock extends UnderwaterPlantBlock implements BonemealableBlock
{
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 1);
    /** About 26 random ticks fit in 30 minutes at the default tick speed; two steps, so one step per ~13 ticks. */
    private static final float STEP_CHANCE = 1f / 13f;

    public AncientSaplingBlock(Properties properties)
    {
        super(properties.randomTicks());
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(STAGE);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (random.nextFloat() < STEP_CHANCE) advance(level, pos, state, random);
    }

    private static void advance(ServerLevel level, BlockPos pos, BlockState state, RandomSource random)
    {
        if (!DeepLayer.isDeep(level, pos.getY())) return;
        if (state.getValue(STAGE) == 0) level.setBlock(pos, state.setValue(STAGE, 1), 4);
        else AncientTree.place(level, pos, random, 3);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state)
    {
        return pos.getY() < DeepLayer.TOP_Y;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state)
    {
        return random.nextFloat() < 0.45f;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state)
    {
        advance(level, pos, state, random);
    }
}
