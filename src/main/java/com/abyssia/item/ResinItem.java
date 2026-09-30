package com.abyssia.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Sea resin coats copper like honeycomb: using it on any waxable copper block waxes it (the vanilla wax table). */
public class ResinItem extends MaterialItem
{
    public ResinItem(Properties properties, int burnTime, boolean hasSource)
    {
        super(properties, burnTime, hasSource);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return HoneycombItem.getWaxed(level.getBlockState(pos)).map(waxed -> wax(context, level, pos, waxed)).orElse(InteractionResult.PASS);
    }

    private static InteractionResult wax(UseOnContext context, Level level, BlockPos pos, BlockState waxed)
    {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, stack);
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        level.setBlock(pos, waxed, 11);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, waxed));
        level.levelEvent(player, 3003, pos, 0);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
