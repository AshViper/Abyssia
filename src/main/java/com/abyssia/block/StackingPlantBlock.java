package com.abyssia.block;

import com.abyssia.registry.ModTags;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Submerged plant that stacks into columns (tall grass, tube plants, deep and giant kelp, giant tubes).
 * One block per plant: {@code top} selects the tip model and is kept in sync as the column changes.
 * No ticking and no block entity, so dense forests stay cheap.
 */
public class StackingPlantBlock extends Block implements LiquidBlockContainer
{
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    private final VoxelShape shape;
    /** Mineral vines root in the minerals that stop other plants. */
    private final boolean anySubstrate;
    private final SporeEmitter spores;

    public StackingPlantBlock(Properties properties, double width, boolean anySubstrate, SporeEmitter spores)
    {
        super(properties);
        double inset = (16 - width) / 2;
        this.shape = Block.box(inset, 0, inset, 16 - inset, 16, 16 - inset);
        this.anySubstrate = anySubstrate;
        this.spores = spores;
        registerDefaultState(stateDefinition.any().setValue(TOP, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(TOP);
    }

    /** Roots even in the ore and hot minerals that stop other plants. */
    public boolean growsOnAnything()
    {
        return anySubstrate;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return shape;
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
        if (ground.is(this)) return true;
        return ground.isFaceSturdy(level, below, Direction.UP) && (anySubstrate || !ground.is(ModTags.INHIBITS_PLANTS));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        if (!fluid.is(FluidTags.WATER) || fluid.getAmount() != 8) return null;
        BlockState state = defaultBlockState().setValue(TOP, !context.getLevel().getBlockState(context.getClickedPos().above()).is(this));
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        // Returning air lets the level replace the plant with the water it was holding.
        if (!state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (direction == Direction.UP) return state.setValue(TOP, !neighborState.is(this));
        return state;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (state.getValue(TOP)) spores.emit(level, pos, random);
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
