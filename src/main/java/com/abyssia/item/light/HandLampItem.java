package com.abyssia.item.light;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Hand lamp: right-click switches it on or off (custom data "On"). While it is on and held in either hand it shines as a
 * client-side {@link com.abyssia.client.light.Spotlight} (see HandLamps); there is no server-side light.
 */
public class HandLampItem extends Item
{
    private static final String TAG_ON = "On";

    public HandLampItem(Properties props) { super(props); }

    public static void register(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
    {
        tab.add(items.register("hand_lamp", () -> new HandLampItem(new Item.Properties().stacksTo(1))));
    }

    public static boolean isOn(ItemStack stack)
    {
        if (!(stack.getItem() instanceof HandLampItem)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(TAG_ON);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        boolean on = !isOn(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(TAG_ON, on));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS,
                0.5f, on ? 1.2f : 0.9f);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return isOn(stack); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.abyssia.hand_lamp.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(isOn(stack) ? "tooltip.abyssia.hand_lamp.on" : "tooltip.abyssia.hand_lamp.off")
                .withStyle(isOn(stack) ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
    }
}
