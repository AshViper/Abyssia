package com.abyssia.habitat.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * BT01d controller of a multiblock generator (one per unit, at {@link GeneratorKind#cx}..). Invisible as a block: the
 * {@link GeneratorBlockEntity} renderer draws the whole design model. FACING = the unit's forward (local +z).
 */
public class GeneratorBlock extends BaseEntityBlock implements SimpleWaterloggedBlock
{
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public final GeneratorKind kind;

    public GeneratorBlock(GeneratorKind kind, Properties properties)
    {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(WATERLOGGED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new GeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (!level.isClientSide) return createTickerHelper(type, ModGenerators.GENERATOR_ENTITY.get(), GeneratorBlockEntity::serverTick);
        return createTickerHelper(type, ModGenerators.GENERATOR_ENTITY.get(), GeneratorBlockEntity::clientTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        return interact(level, pos, player, hand);
    }

    /** Right-click on the controller or any part: bio-oil goes into the fuel slot, an empty hand prints the status. */
    public static InteractionResult interact(Level level, BlockPos controller, Player player, InteractionHand hand)
    {
        if (!(level.getBlockEntity(controller) instanceof GeneratorBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty())
        {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!level.isClientSide) be.sendStatus(player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (be.kind() == GeneratorKind.BIOFUEL && GeneratorBlockEntity.fuelValue(held) > 0)
        {
            if (!level.isClientSide)
            {
                ItemStack rest = be.fuelSlot().insertItem(0, held.copy(), false);
                if (!player.getAbilities().instabuild) player.setItemInHand(hand, rest);
                be.sendStatus(player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GeneratorBlockEntity be)
        {
            ItemStack fuel = be.fuelSlot().getStackInSlot(0);
            if (!fuel.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, fuel.copy());
            be.fuelSlot().setStackInSlot(0, ItemStack.EMPTY);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos)
    {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, dir, neighbour, level, pos, neighbourPos);
    }
}
