package com.abyssia.habitat.aquarium;

import com.abyssia.Abyssia;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
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

/**
 * BT01g aquarium controller: the floor centre cell of the tank (sand + coral model). Its block entity keeps the
 * creatures; right-click with a filled canister releases, with an empty one takes an adult, otherwise shows the count.
 * No item, no loot; if it is ever broken other than by dismantling, the creatures drop as filled canisters.
 */
public class AquariumBlock extends BaseEntityBlock
{
    public static final MapCodec<AquariumBlock> CODEC = simpleCodec(AquariumBlock::new);
    private static final String MSG = "message." + Abyssia.MODID + ".aquarium.";

    public AquariumBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.block();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos)
    {
        return 1.0f;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos)
    {
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit)
    {
        return itemResult(stack, level, pos, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        return interact(level, pos, player, InteractionHand.MAIN_HAND);
    }

    /** Canisters are handled in useItemOn; anything else falls through to useWithoutItem (status). */
    static ItemInteractionResult itemResult(ItemStack stack, Level level, BlockPos controller, Player player, InteractionHand hand)
    {
        if (!stack.is(AquariumContent.CANISTER.get()) && !stack.is(AquariumContent.CANISTER_FILLED.get()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        InteractionResult r = interact(level, controller, player, hand);
        if (r == InteractionResult.SUCCESS) return ItemInteractionResult.SUCCESS;
        if (r.consumesAction()) return ItemInteractionResult.CONSUME;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Shared by the controller and the parts. */
    static InteractionResult interact(Level level, BlockPos controller, Player player, InteractionHand hand)
    {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(controller) instanceof AquariumBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.is(AquariumContent.CANISTER_FILLED.get()))
        {
            String species = CreatureCaptureCanisterItem.species(held);
            if (species == null || CreatureCaptureCanisterItem.type(species) == null)
            {
                player.displayClientMessage(Component.translatable(MSG + "invalid"), true);
                return InteractionResult.CONSUME;
            }
            if (!be.release(species, CreatureCaptureCanisterItem.adult(held)))
            {
                player.displayClientMessage(Component.translatable(MSG + "full", AquariumBlockEntity.CAPACITY), true);
                return InteractionResult.CONSUME;
            }
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(AquariumContent.CANISTER.get()), false));
            level.playSound(null, controller, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable(MSG + "released", CreatureCaptureCanisterItem.speciesName(species),
                    be.creatures().size(), AquariumBlockEntity.CAPACITY), true);
            return InteractionResult.CONSUME;
        }
        if (held.is(AquariumContent.CANISTER.get()))
        {
            AquariumBlockEntity.Creature taken = be.takeAdult();
            if (taken == null)
            {
                player.displayClientMessage(Component.translatable(MSG + "no_adult"), true);
                return InteractionResult.CONSUME;
            }
            ItemStack filled = CreatureCaptureCanisterItem.filled(taken.species(), true);
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, filled, false));
            level.playSound(null, controller, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable(MSG + "taken", CreatureCaptureCanisterItem.speciesName(taken.species())), true);
            return InteractionResult.CONSUME;
        }
        be.update();
        int adults = 0;
        for (AquariumBlockEntity.Creature c : be.creatures()) if (c.adult()) adults++;
        player.displayClientMessage(Component.translatable(MSG + "status", be.creatures().size(), AquariumBlockEntity.CAPACITY, adults,
                be.creatures().size() - adults), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof AquariumBlockEntity be)
        {
            // safety net: never lose creatures (dismantling empties the tank first)
            for (ItemStack stack : be.takeAllAsCanisters())
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new AquariumBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? null : createTickerHelper(type, AquariumContent.AQUARIUM_ENTITY.get(), AquariumBlockEntity::serverTick);
    }
}
