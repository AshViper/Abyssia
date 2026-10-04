package com.abyssia.map;

import com.abyssia.worldgen.DeepLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import javax.annotation.Nullable;
import java.util.List;

/**
 * MP01: the empty deep sea map. Using it makes a vanilla filled_map (a custom MapItem would not render in hand or
 * item frames) whose locked data is painted from the deep seabed by {@link DeepMapFiller}.
 */
public class DeepSeaMapItem extends Item
{
    /** Marker tag on the filled map stack; the client uses it to hide the "Locked" tooltip line. */
    public static final String MARKER = "abyssia_deep_map";
    private static final int DARK_CYAN = 0x1F6F78;

    public DeepSeaMapItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack held = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp))
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);

        ItemStack map = new ItemStack(Items.FILLED_MAP);
        MapItemSavedData data = MapItemSavedData.createFresh(player.getX(), player.getZ(), (byte) 2, true, false, level.dimension()).locked();
        int id = level.getFreeMapId();
        level.setMapData(MapItem.makeKey(id), data);
        map.getOrCreateTag().putInt("map", id); // MapItem.storeMapTag is private in 1.20.1
        map.setHoverName(Component.translatable("item.abyssia.deep_sea_map.filled").withStyle(s -> s.withItalic(false)));
        map.getOrCreateTagElement("display").putInt("MapColor", DARK_CYAN);
        map.getOrCreateTag().putBoolean(MARKER, true);
        DeepMapIndex.get(server.getServer()).add(id);
        if (!DeepLayer.isDeep(level, player.getY()))
            sp.displayClientMessage(Component.translatable("message.abyssia.deep_map.outside"), true);

        held.shrink(1);
        if (held.isEmpty()) return InteractionResultHolder.consume(map);
        if (!player.getInventory().add(map)) player.drop(map, false);
        return InteractionResultHolder.consume(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.abyssia.deep_sea_map.tooltip1").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.abyssia.deep_sea_map.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
