package com.abyssia.block;

import com.abyssia.registry.ModBlocks;
import com.abyssia.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * OL01 cultivated oil kelp: a column of up to {@link #MAX_LENGTH} segments growing upward in water. Each segment has
 * {@code ripe} (an oil sac hangs on it; right-click gives an oil_sac and the segment turns unripe) and {@code base}
 * (the lowest segment, only for the model). Breaking a ripe segment drops an oil_sac, any segment 5% an oil_kelp_seed.
 */
public class OilKelpBlock extends UnderwaterPlantBlock implements BonemealableBlock
{
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
    public static final BooleanProperty BASE = BooleanProperty.create("base");
    public static final int MAX_LENGTH = 8;
    private static final float GROW_CHANCE = 0.14f;
    private static final float RIPEN_CHANCE = 0.05f;
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    public OilKelpBlock(Properties properties)
    {
        super(properties.randomTicks());
        registerDefaultState(stateDefinition.any().setValue(RIPE, false).setValue(BASE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(RIPE, BASE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        return ground.is(this) || mayPlaceOn(ground, level, below);
    }

    /** Unsupported segments break with drops (like vanilla kelp) instead of vanishing: the break is deferred to a tick. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        if (!state.canSurvive(level, pos))
        {
            level.scheduleTick(pos, this, 1);
            return state;
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean onKelp = level.getBlockState(pos.below()).is(this);
        if (onKelp && length(level, pos.below()) >= MAX_LENGTH) return null;
        return state.setValue(RIPE, false).setValue(BASE, !onKelp);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state)
    {
        return new ItemStack(ModItems.OIL_KELP_SEED.get());
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (!state.getValue(RIPE) && random.nextFloat() < RIPEN_CHANCE) level.setBlock(pos, state.setValue(RIPE, true), 2);
        if (random.nextFloat() < GROW_CHANCE) tryGrow(level, pos);
    }

    /** Adds a segment above {@code pos} when it is the top of its column, below the length cap, with open water above. */
    private boolean tryGrow(ServerLevel level, BlockPos pos)
    {
        BlockPos above = pos.above();
        if (!level.getBlockState(above).is(Blocks.WATER) || level.getFluidState(above).getAmount() != 8) return false;
        if (length(level, pos) >= MAX_LENGTH) return false;
        level.setBlock(above, defaultBlockState().setValue(RIPE, false).setValue(BASE, false), 3);
        return true;
    }

    /** Number of oil kelp segments in the column from the lowest one up to and including {@code pos} (counted downward). */
    private int length(BlockGetter level, BlockPos pos)
    {
        int n = 0;
        for (BlockPos p = pos; level.getBlockState(p).is(this); p = p.below()) n++;
        return n;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!state.getValue(RIPE)) return InteractionResult.PASS;
        if (!level.isClientSide)
        {
            popResource(level, pos, new ItemStack(ModItems.OIL_SAC.get()));
            level.setBlock(pos, state.setValue(RIPE, false), 2);
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 0.8f + level.random.nextFloat() * 0.4f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------- bone meal: one growth roll (a new segment or one unripe segment ripens)

    private List<BlockPos> unripe(BlockGetter level, BlockPos any)
    {
        List<BlockPos> list = new ArrayList<>();
        BlockPos p = any;
        while (level.getBlockState(p.below()).is(this)) p = p.below();
        for (; level.getBlockState(p).is(this); p = p.above())
            if (!level.getBlockState(p).getValue(RIPE)) list.add(p);
        return list;
    }

    private BlockPos top(BlockGetter level, BlockPos any)
    {
        BlockPos p = any;
        while (level.getBlockState(p.above()).is(this)) p = p.above();
        return p;
    }

    private boolean canGrow(BlockGetter level, BlockPos any)
    {
        BlockPos top = top(level, any);
        BlockPos above = top.above();
        return level.getBlockState(above).is(Blocks.WATER) && level.getFluidState(above).getAmount() == 8 && length(level, top) < MAX_LENGTH;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state)
    {
        return canGrow(level, pos) || !unripe(level, pos).isEmpty();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state)
    {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state)
    {
        List<BlockPos> unripe = unripe(level, pos);
        boolean grow = canGrow(level, pos);
        if (grow && (unripe.isEmpty() || random.nextBoolean())) tryGrow(level, top(level, pos));
        else if (!unripe.isEmpty())
        {
            BlockPos target = unripe.get(random.nextInt(unripe.size()));
            level.setBlock(target, level.getBlockState(target).setValue(RIPE, true), 2);
        }
    }
}
