package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * SUB04: horizontal FACING = the direction the docked submarine's nose points (dock frame -z); the block model is
 * invisible and {@code SubmarineDockRenderer} draws the animated arms / gangway (DockMesh).
 * SUB02 submarine dock: a clamp hanging from the ceiling (lit, level 12, so it doubles as the pool light). Built with
 * the habitat constructor over the middle of a moon pool ({@link SubmarineDockEntry}); no item, no drops (the tool's
 * dismantle refunds). {@link SubmarineDockBlockEntity} docks and charges.
 */
public class SubmarineDockBlock extends BaseEntityBlock
{
    public static final MapCodec<SubmarineDockBlock> CODEC = simpleCodec(SubmarineDockBlock::new);
    /** small ceiling mount (the arms are visual only) */
    private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 16, 12);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public SubmarineDockBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston)
    {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof SubmarineDockBlockEntity dock) dock.removeGangway();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;   // drawn by SubmarineDockRenderer; the block model only serves the build ghost
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof SubmarineDockBlockEntity dock)
        {
            NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
            String buffer = nf.format(dock.energy().getEnergyStored()), cap = nf.format(SubmarineDockBlockEntity.CAPACITY);
            Submarine sub = dock.docked();
            String key = "message." + Abyssia.MODID + ".submarine_dock." + (sub == null ? "empty" : "status");
            player.displayClientMessage(sub == null ? Component.translatable(key, buffer, cap)
                    : Component.translatable(key, buffer, cap, Math.round(100.0f * sub.getEnergy() / Math.max(1, Submarine.capacity()))), true);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new SubmarineDockBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide) return (lvl, pos, st, be) ->
        {
            if (be instanceof SubmarineDockBlockEntity dock) dock.clientTick();
        };
        return (lvl, pos, st, be) ->
        {
            if (be instanceof SubmarineDockBlockEntity dock) dock.serverTick();
        };
    }
}
