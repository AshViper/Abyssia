package com.abyssia.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Large locker (feature H04, inbox/specs/H04-base-equipment.md): 2 wide x 1 deep x 2 high, four cells of this block
 * (PART bl / br / tl / tr). The bottom-left cell holds the 81 slots ({@link LargeLockerBlockEntity}); the other cells
 * carry a {@link LockerPartBlockEntity} that hands out the base's item capability. Right-click on any cell opens the
 * base. Removing any cell removes all four: the base's loot table drops the locker (only part=bl drops anything) and
 * its onRemove spills the contents. OPEN swaps the front textures while someone has it open.
 */
public class LargeLockerBlock extends BaseEntityBlock implements SimpleWaterloggedBlock
{
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<LockerPart> PART = EnumProperty.create("part", LockerPart.class);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final MapCodec<LargeLockerBlock> CODEC = simpleCodec(LargeLockerBlock::new);

    @Override
    protected MapCodec<LargeLockerBlock> codec()
    {
        return CODEC;
    }

    public LargeLockerBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, LockerPart.BL)
                .setValue(OPEN, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, PART, OPEN, WATERLOGGED);
    }

    // ---------------------------------------------------------------- geometry

    /** Base (bottom-left) position of the locker the cell at pos belongs to. */
    public static BlockPos basePos(BlockPos pos, BlockState state)
    {
        return state.getValue(PART).base(pos, state.getValue(FACING));
    }

    /** Whether the cell at pos is the given part of the locker with that facing. */
    private boolean isPart(LevelAccessor level, BlockPos pos, Direction facing, LockerPart part)
    {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part;
    }

    private static boolean water(LevelAccessor level, BlockPos pos)
    {
        return level.getFluidState(pos).getType() == Fluids.WATER;
    }

    /** Drops cached item capabilities of all four cells (pipes keep BlockCapabilityCache handlers across changes). */
    public static void invalidateCaps(Level level, BlockPos base, Direction facing)
    {
        for (LockerPart part : LockerPart.values()) level.invalidateCapabilities(part.from(base, facing));
    }

    // ---------------------------------------------------------------- placement

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        Level level = context.getLevel();
        BlockPos base = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (base.getY() >= level.getMaxBuildHeight() - 1) return null;
        for (LockerPart part : LockerPart.values())
        {
            if (part == LockerPart.BL) continue;
            BlockPos p = part.from(base, facing);
            if (!level.getWorldBorder().isWithinBounds(p) || !level.getBlockState(p).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(WATERLOGGED, water(level, base));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING);
        for (LockerPart part : LockerPart.values())
        {
            if (part == LockerPart.BL) continue;
            BlockPos p = part.from(pos, facing);
            level.setBlock(p, state.setValue(PART, part).setValue(OPEN, false).setValue(WATERLOGGED, water(level, p)), 3);
        }
        invalidateCaps(level, pos, facing);
        if (stack.has(DataComponents.CUSTOM_NAME) && level.getBlockEntity(pos) instanceof LargeLockerBlockEntity be)
            be.applyComponentsFromItemStack(stack);
    }

    /**
     * Places a whole locker with its base (bottom-left) at base, front towards facing (for commands / tests).
     * Returns false and changes nothing unless all four cells are replaceable.
     */
    public boolean placeAt(Level level, BlockPos base, Direction facing)
    {
        if (facing.getAxis().isVertical()) return false;
        for (LockerPart part : LockerPart.values())
        {
            BlockPos p = part.from(base, facing);
            if (!level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced()) return false;
        }
        BlockState state = defaultBlockState().setValue(FACING, facing);
        for (LockerPart part : LockerPart.values())
        {
            BlockPos p = part.from(base, facing);
            level.setBlock(p, state.setValue(PART, part).setValue(WATERLOGGED, water(level, p)), 3);
        }
        invalidateCaps(level, base, facing);
        return true;
    }

    // ---------------------------------------------------------------- removal

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        // creative: take the base away first without drops (its contents still spill, like a vanilla chest)
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) != LockerPart.BL)
        {
            BlockPos base = basePos(pos, state);
            if (isPart(level, base, state.getValue(FACING), LockerPart.BL))
                level.setBlock(base, level.getFluidState(base).createLegacyBlock(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!level.isClientSide && !state.is(newState.getBlock()))
        {
            Direction facing = state.getValue(FACING);
            LockerPart part = state.getValue(PART);
            if (part == LockerPart.BL)
            {
                // the base is going: spill the contents, then clear the other cells without drops
                if (level.getBlockEntity(pos) instanceof LargeLockerBlockEntity be)
                {
                    Containers.dropContents(level, pos, be);
                    be.clearContent();
                }
                for (LockerPart other : LockerPart.values())
                {
                    if (other == LockerPart.BL) continue;
                    BlockPos p = other.from(pos, facing);
                    if (isPart(level, p, facing, other))
                        level.setBlock(p, level.getFluidState(p).createLegacyBlock(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
                level.updateNeighbourForOutputSignal(pos, this);
                invalidateCaps(level, pos, facing);
            }
            else
            {
                // another cell is going: break the base with its drops (one locker + contents)
                BlockPos base = part.base(pos, facing);
                if (isPart(level, base, facing, LockerPart.BL)) level.destroyBlock(base, true);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    // ---------------------------------------------------------------- use

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(basePos(pos, state)) instanceof LargeLockerBlockEntity be) player.openMenu(be);
        return InteractionResult.CONSUME;
    }

    /** Called by the openers counter's scheduled re-check (on the base). */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (level.getBlockEntity(pos) instanceof LargeLockerBlockEntity be) be.recheckOpen();
    }

    /** Sets OPEN on all four cells of the locker based at base. */
    void setOpen(Level level, BlockPos base, BlockState baseState, boolean open)
    {
        Direction facing = baseState.getValue(FACING);
        for (LockerPart part : LockerPart.values())
        {
            BlockPos p = part.from(base, facing);
            BlockState s = level.getBlockState(p);
            if (s.is(this) && s.getValue(FACING) == facing && s.getValue(PART) == part && s.getValue(OPEN) != open)
                level.setBlock(p, s.setValue(OPEN, open), 3);
        }
    }

    // ---------------------------------------------------------------- misc

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return state.getValue(PART) == LockerPart.BL ? new LargeLockerBlockEntity(pos, state) : new LockerPartBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    protected FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state)
    {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos)
    {
        return level.getBlockEntity(basePos(pos, state)) instanceof LargeLockerBlockEntity be
                ? AbstractContainerMenu.getRedstoneSignalFromContainer(be) : 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** A mirror always swaps the locker's left and right. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror)
    {
        if (mirror == Mirror.NONE) return state;
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(PART, state.getValue(PART).mirrored());
    }
}
