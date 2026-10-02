package com.abyssia.habitat;

import com.abyssia.Abyssia;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Habitat constructor (H01 / H02): module in NBT Mode, rotation in NBT Rot; controls live in client/HabitatClient. */
public class HabitatConstructorItem extends Item
{
    public static final String MODE = "Mode";
    public static final String ROT = "Rot";
    private static final String KEY = "tooltip." + Abyssia.MODID + ".habitat.";

    public HabitatConstructorItem(Properties properties)
    {
        super(properties);
    }

    public static HabitatMode mode(ItemStack stack)
    {
        return stack.hasTag() ? HabitatMode.byId(stack.getTag().getString(MODE)) : HabitatMode.FOUNDATION;
    }

    /** NBT Rot (0-3, see HabitatPlan.facing); unset = the player's horizontal facing. */
    public static int rotation(ItemStack stack, Player player)
    {
        if (stack.hasTag() && stack.getTag().contains(ROT)) return Math.floorMod(stack.getTag().getInt(ROT), 4);
        return player.getDirection().get2DDataValue();
    }

    /** Right-click is taken by the client (build menu, HabitatClient); nothing happens here. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        HabitatMode mode = mode(stack);
        tooltip.add(Component.translatable(KEY + "mode", mode.displayName().withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(KEY + "size", mode.width, mode.depth, mode.height).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(KEY + "cost").withStyle(ChatFormatting.GRAY));
        for (HabitatMode.Cost cost : mode.cost)
            tooltip.add(Component.literal("  " + cost.count() + "x ").append(cost.item().get().getDescription()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "creative").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "menu").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable(KEY + "build").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable(KEY + "rotate").withStyle(ChatFormatting.BLUE));
    }
}
