package com.abyssia.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Hydro planter (feature PL02, inbox/specs/PL02-planter-remake.md): a bottom half slab with four 2x2 cells, each
 * growing its own crop without water or FE. No GUI; right-click acts on the cell under the cursor (horizontal hit
 * position, any face):
 * <ul>
 *   <li>empty cell + seedling: plant (uses one)</li>
 *   <li>ripe cell: harvest to the player (dropped at the player when full); the seedling stays and regrows</li>
 *   <li>growing cell + bone meal: a tenth of the growth time</li>
 *   <li>sneak + empty hand: take the seedling back, the cell becomes empty</li>
 * </ul>
 * Hoppers / pipes below or at the sides extract ripe produce (HydroPlanterBlockEntity). Breaking drops all seedlings.
 */
public class HydroPlanterBlock extends BaseEntityBlock
{
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    private enum Action { NONE, PLANT, HARVEST, BONE_MEAL, REMOVE }

    public HydroPlanterBlock(Properties properties)
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
    @SuppressWarnings("deprecation")
    public boolean useShapeForLightOcclusion(BlockState state)
    {
        return true;
    }

    // ---------------------------------------------------------------- use

    private static Action action(HydroPlanterBlockEntity be, int cell, Player player, ItemStack held)
    {
        PlanterCrop crop = be.crop(cell);
        if (crop == PlanterCrop.NONE)
            return PlanterCrop.of(held) != PlanterCrop.NONE ? Action.PLANT : Action.NONE;
        if (player.isSecondaryUseActive() && held.isEmpty()) return Action.REMOVE;
        if (be.isRipe(cell)) return Action.HARVEST;
        if (held.is(Items.BONE_MEAL)) return Action.BONE_MEAL;
        return Action.NONE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof HydroPlanterBlockEntity be)) return InteractionResult.PASS;
        Vec3 at = hit.getLocation();
        int cell = HydroPlanterBlockEntity.cellAt(at.x - pos.getX(), at.z - pos.getZ());
        ItemStack held = player.getItemInHand(hand);
        Action action = action(be, cell, player, held);
        if (action == Action.NONE) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        switch (action)
        {
            case PLANT ->
            {
                if (be.plant(cell, held))
                {
                    if (!player.getAbilities().instabuild) held.shrink(1);
                    level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
                }
            }
            case HARVEST ->
            {
                give(level, player, be.harvest(cell));
                level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            case BONE_MEAL ->
            {
                if (be.advance(cell))
                {
                    if (!player.getAbilities().instabuild) held.shrink(1);
                    level.levelEvent(1505, pos, 0); // bone meal particles
                }
            }
            case REMOVE -> give(level, player, be.removeSeedling(cell));
            default -> {}
        }
        return InteractionResult.CONSUME;
    }

    private static void give(Level level, Player player, ItemStack stack)
    {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack) && !stack.isEmpty())
        {
            Vec3 p = player.position();
            Containers.dropItemStack(level, p.x, p.y, p.z, stack);
        }
    }

    // ---------------------------------------------------------------- misc

    @Override
    @SuppressWarnings("deprecation")
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

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }
}
