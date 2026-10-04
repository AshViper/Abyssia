package com.abyssia.map;

import com.abyssia.worldgen.DeepLayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;

/**
 * MP01 empty Deep Sea Map. Using it makes a vanilla {@code filled_map} (so it renders in hand and item frames) whose
 * data is locked and registered in {@link DeepMapIndex}; {@link DeepMapFiller} paints the seafloor into it.
 */
public class DeepSeaMapItem extends Item
{
    public static final String MARKER = "abyssia_deep_map";
    private static final int MAP_COLOR = 0x1F6F78;

    public DeepSeaMapItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack held = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.sidedSuccess(held, true);

        MapItemSavedData data = MapItemSavedData.createFresh(player.getX(), player.getZ(), (byte) 2, true, false, level.dimension()).locked();
        MapId id = server.getFreeMapId();
        server.setMapData(id, data);
        DeepMapIndex.get(server).add(id.id());

        ItemStack filled = new ItemStack(Items.FILLED_MAP);
        filled.set(DataComponents.MAP_ID, id);
        filled.set(DataComponents.ITEM_NAME, Component.translatable("item.abyssia.deep_sea_map.filled"));
        filled.set(DataComponents.MAP_COLOR, new MapItemColor(MAP_COLOR));
        CustomData.update(DataComponents.CUSTOM_DATA, filled, tag -> tag.putBoolean(MARKER, true));

        if (!DeepLayer.isDeep(level, player.getY()))
            player.displayClientMessage(Component.translatable("message.abyssia.deep_map.outside"), true);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(ItemUtils.createFilledResult(held, player, filled), false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.abyssia.deep_sea_map.tooltip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.abyssia.deep_sea_map.tooltip2").withStyle(ChatFormatting.GRAY));
    }
}
