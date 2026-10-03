package com.abyssia.habitat;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildRegistry;
import com.abyssia.registry.ModHabitat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Habitat constructor (H01 / H02 / BT01a): selected build entry id in data component abyssia:habitat_mode (old
 * HabitatMode ids still valid), rotation in abyssia:habitat_rot, placement distance in abyssia:habitat_dist (Forge 1.20
 * used NBT Mode / Rot / Dist, not migrated); controls live in client/HabitatClient.
 */
public class HabitatConstructorItem extends Item
{
    private static final String KEY = "tooltip." + Abyssia.MODID + ".habitat.";

    /** component-only changes (mode / rotation / DismantleSync's box) must not replay the equip animation */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged)
    {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    public HabitatConstructorItem(Properties properties)
    {
        super(properties);
    }

    /** habitat_mode as stored (may name an entry that no longer exists) */
    public static String entryId(ItemStack stack)
    {
        String id = stack.get(ModHabitat.HABITAT_MODE.get());
        return id != null ? id : BuildRegistry.first().id();
    }

    /** the selected build entry (the first one when unset / unknown) */
    public static BuildEntry entry(ItemStack stack)
    {
        return BuildRegistry.getOrDefault(entryId(stack));
    }

    /** H01 view: the selected module, FOUNDATION when the selection is not a module */
    public static HabitatMode mode(ItemStack stack)
    {
        String id = stack.get(ModHabitat.HABITAT_MODE.get());
        return id != null ? HabitatMode.byId(id) : HabitatMode.FOUNDATION;
    }

    /** Rotation component (0-3, see HabitatPlan.facing); unset = the player's horizontal facing. */
    public static int rotation(ItemStack stack, Player player)
    {
        Integer rot = stack.get(ModHabitat.HABITAT_ROT.get());
        if (rot != null) return Math.floorMod(rot, 4);
        return player.getDirection().get2DDataValue();
    }

    /** habitat_dist clamped to 3..12; unset = 6. */
    public static int distance(ItemStack stack)
    {
        Integer dist = stack.get(ModHabitat.HABITAT_DIST.get());
        return dist != null ? HabitatPlan.clampDistance(dist) : HabitatPlan.DEFAULT_DISTANCE;
    }

    public static void setMode(ItemStack stack, HabitatMode mode)
    {
        setEntryId(stack, mode.id);
    }

    public static void setEntryId(ItemStack stack, String entryId)
    {
        stack.set(ModHabitat.HABITAT_MODE.get(), entryId);
    }

    public static void setDistance(ItemStack stack, int distance)
    {
        stack.set(ModHabitat.HABITAT_DIST.get(), HabitatPlan.clampDistance(distance));
    }

    public static void setRotation(ItemStack stack, int rot)
    {
        stack.set(ModHabitat.HABITAT_ROT.get(), Math.floorMod(rot, 4));
    }

    /** Right-click is taken by the client (build menu, HabitatClient); nothing happens here. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        BuildEntry entry = entry(stack);
        tooltip.add(Component.translatable(KEY + "mode", entry.displayName().copy().withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        Component detail = entry.detail();
        if (entry instanceof com.abyssia.habitat.build.ModuleEntry module)
            tooltip.add(Component.translatable(KEY + "size", module.mode.width, module.mode.depth, module.mode.height).withStyle(ChatFormatting.GRAY));
        else if (detail != null) tooltip.add(detail.copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(KEY + "cost").withStyle(ChatFormatting.GRAY));
        for (ItemStack cost : entry.cost())
            tooltip.add(Component.literal("  " + cost.getCount() + "x ").append(cost.getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "creative").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "menu").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable(KEY + "build").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable(KEY + "rotate").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable(KEY + "distance", distance(stack)).withStyle(ChatFormatting.BLUE));
    }
}
