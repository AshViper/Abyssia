package com.abyssia.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * A thin moss film that can cover any face of a cave: floors, walls and ceilings at once (like glow lichen, but
 * always submerged). One block, no ticking, so it can carpet whole cave walls cheaply.
 */
public class CaveMossBlock extends MultifaceBlock implements LiquidBlockContainer
{
    public static final MapCodec<CaveMossBlock> CODEC = simpleCodec(CaveMossBlock::new);
    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    public CaveMossBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected MapCodec<CaveMossBlock> codec()
    {
        return CODEC;
    }

    @Override
    public MultifaceSpreader getSpreader()
    {
        return spreader;
    }

    /** Moss covering one face; world generation adds further faces with {@code setValue(getFaceProperty(dir), true)}. */
    public BlockState onFace(Direction face)
    {
        return defaultBlockState().setValue(getFaceProperty(face), true);
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
