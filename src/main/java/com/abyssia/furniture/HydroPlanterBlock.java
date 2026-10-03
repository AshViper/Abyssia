package com.abyssia.furniture;

import com.abyssia.registry.ModFurniture;
import net.minecraft.core.BlockPos;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Hydro planter (feature PL01, inbox/specs/PL01-hydro-planter.md): a one-slot seedling holder that grows food on its
 * own (no water, no FE). Right-click: harvest when ripe, bone meal advances 10%, otherwise open the GUI.
 * NORTH..DOWN are true when that neighbour is a planter (the multipart model hides frame edges towards it; purely
 * visual). CROP / RIPE show the plant on top.
 */
public class HydroPlanterBlock extends BaseEntityBlock
{
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final EnumProperty<PlanterCrop> CROP = EnumProperty.create("crop", PlanterCrop.class);
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");

    private static final Map<Direction, BooleanProperty> SIDES = Map.of(
            Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH,
            Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN);

    public static final MapCodec<HydroPlanterBlock> CODEC = simpleCodec(HydroPlanterBlock::new);

    @Override
    protected MapCodec<HydroPlanterBlock> codec()
    {
        return CODEC;
    }

    public HydroPlanterBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false).setValue(CROP, PlanterCrop.NONE).setValue(RIPE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, CROP, RIPE);
    }

    // ---------------------------------------------------------------- connection

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> e : SIDES.entrySet())
            state = state.setValue(e.getValue(), context.getLevel().getBlockState(context.getClickedPos().relative(e.getKey())).is(this));
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        return state.setValue(SIDES.get(direction), neighbor.is(this));
    }

    // ---------------------------------------------------------------- use

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof HydroPlanterBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        PlanterCrop crop = be.crop();
        if (be.isRipe())
        {
            harvest(level, pos, player, be, crop);
            return ItemInteractionResult.CONSUME;
        }
        if (crop != PlanterCrop.NONE && held.is(Items.BONE_MEAL))
        {
            if (be.advance(Math.max(1, crop.ticks / 10)))
            {
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.levelEvent(1505, pos.above(), 0); // bone meal particles
            }
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof HydroPlanterBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (be.isRipe())
        {
            harvest(level, pos, player, be, be.crop());
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.openMenu(be, buf -> buf.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    private static void harvest(Level level, BlockPos pos, Player player, HydroPlanterBlockEntity be, PlanterCrop crop)
    {
        var item = crop.resultItem();
        if (item != null && item != Items.AIR)
        {
            ItemStack stack = new ItemStack(item, crop.min + level.random.nextInt(crop.max - crop.min + 1));
            if (!player.getInventory().add(stack))
            {
                Vec3 p = player.position();
                Containers.dropItemStack(level, p.x, p.y, p.z, stack);
            }
        }
        be.harvested();
    }

    // ---------------------------------------------------------------- misc

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HydroPlanterBlockEntity be) be.dropContents();
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HydroPlanterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? null : createTickerHelper(type, ModFurniture.HYDRO_PLANTER_ENTITY.get(), HydroPlanterBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }
}
