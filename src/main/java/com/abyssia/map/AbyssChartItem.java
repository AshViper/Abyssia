package com.abyssia.map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** MP01: single-use chart; searches the nearest deep sea entrance (see {@link EntranceLocator}). */
public class AbyssChartItem extends Item
{
    public AbyssChartItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack held = player.getItemInHand(hand);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp)
            EntranceLocator.start(server, sp, hand);
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.abyssia.abyss_chart.tooltip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.abyssia.abyss_chart.tooltip2").withStyle(ChatFormatting.GRAY));
    }
}
