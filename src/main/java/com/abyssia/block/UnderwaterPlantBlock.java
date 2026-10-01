package com.abyssia.block;

import com.abyssia.registry.ModTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * A small plant that only exists fully submerged, like seagrass. It roots on any sturdy seabed except
 * plant-inhibiting minerals, and may shed spores or glow dust.
 */
public class UnderwaterPlantBlock extends BushBlock implements LiquidBlockContainer
{
    public static final MapCodec<UnderwaterPlantBlock> CODEC = simpleCodec(UnderwaterPlantBlock::new);
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);
    private final SporeEmitter spores;

    public UnderwaterPlantBlock(Properties properties)
    {
        this(properties, SporeEmitter.NONE);
    }

    public UnderwaterPlantBlock(Properties properties, SporeEmitter spores)
    {
        super(properties);
        this.spores = spores;
    }

    @Override
    protected MapCodec<? extends UnderwaterPlantBlock> codec()
    {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos)
    {
        return state.isFaceSturdy(level, pos, Direction.UP) && !state.is(ModTags.INHIBITS_PLANTS);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        BlockPos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return fluid.is(FluidTags.WATER) && fluid.getAmount() == 8 ? super.getStateForPlacement(context) : null;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        if (!updated.isAir()) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return updated;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        spores.emit(level, pos, random);
    }

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return Fluids.WATER.getSource(false);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid)
    {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid)
    {
        return false;
    }
}
