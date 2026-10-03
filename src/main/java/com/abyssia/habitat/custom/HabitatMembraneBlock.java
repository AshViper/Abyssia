package com.abyssia.habitat.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * BT01e air membrane of the open entrance: players, mobs and items pass (no collision), water does not.
 * <p>
 * Water (1.20.1 FlowingFluid): spreading needs {@code canHoldFluid}, which for a LiquidBlockContainer is only
 * {@link #canPlaceLiquid} (false here), so neither flowing water nor sources ever enter the cell; buckets also go
 * through canBeReplaced(Fluid) / canPlaceLiquid (both false). The block holds no fluid itself. A full outline shape
 * keeps it targetable, so a bucket aimed through the opening hits the membrane instead of the dry floor behind it.
 * Unbreakable, no drops, no item: only the constructor places / removes it.
 */
public class HabitatMembraneBlock extends Block implements LiquidBlockContainer
{
    public HabitatMembraneBlock(Properties properties)
    {
        super(properties);
    }

    public static Properties properties()
    {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0f, 3600000.0f).noLootTable()
                .noCollission().noOcclusion().sound(SoundType.GLASS).lightLevel(s -> 4).pushReaction(PushReaction.BLOCK)
                .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                .isValidSpawn((s, l, p, e) -> false);
    }

    // ---------------------------------------------------------------- water stays out

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid)
    {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid)
    {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canBeReplaced(BlockState state, Fluid fluid)
    {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context)
    {
        return false;
    }

    // ---------------------------------------------------------------- shapes / rendering

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side)
    {
        return adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos)
    {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos)
    {
        return 1.0f;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type)
    {
        return type != PathComputationType.WATER;
    }
}
