package com.abyssia.block;

import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/** A thin submerged layer over the seabed: ground-cover moss, heat moss, pebbles. */
public class SeafloorCarpetBlock extends Block implements LiquidBlockContainer
{
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    /** Heat moss grows on hot mineral rock that other plants avoid. */
    private final boolean anySubstrate;

    public SeafloorCarpetBlock(Properties properties, boolean anySubstrate)
    {
        super(properties);
        this.anySubstrate = anySubstrate;
    }

    /** Lies on any sturdy ground, including ore and hot minerals. */
    public boolean growsOnAnything()
    {
        return anySubstrate;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        return ground.isFaceSturdy(level, below, Direction.UP) && (anySubstrate || !ground.is(ModTags.INHIBITS_PLANTS));
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
        if (!state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state;
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
