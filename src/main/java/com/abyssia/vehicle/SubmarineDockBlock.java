package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * SUB02 submarine dock: a clamp hanging from the ceiling (lit, level 12, so it doubles as the pool light). Built with
 * the habitat constructor over the middle of a moon pool ({@link SubmarineDockEntry}); no item, no drops (the tool's
 * dismantle refunds). {@link SubmarineDockBlockEntity} docks and charges.
 */
public class SubmarineDockBlock extends BaseEntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(Block.box(6, 6, 6, 10, 16, 10), Block.box(2, 3, 2, 14, 6, 14),
            Block.box(2, 0, 2, 4, 3, 14), Block.box(12, 0, 2, 14, 3, 14));

    public SubmarineDockBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof SubmarineDockBlockEntity dock)
        {
            NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
            String buffer = nf.format(dock.energy().getEnergyStored()), cap = nf.format(SubmarineDockBlockEntity.CAPACITY);
            Submarine sub = dock.docked();
            String key = "message." + Abyssia.MODID + ".submarine_dock." + (sub == null ? "empty" : "status");
            player.displayClientMessage(sub == null ? Component.translatable(key, buffer, cap)
                    : Component.translatable(key, buffer, cap, Math.round(100.0f * sub.getEnergy() / Math.max(1, sub.maxEnergy()))), true);
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
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) ->
        {
            if (be instanceof SubmarineDockBlockEntity dock) dock.serverTick();
        };
    }
}
