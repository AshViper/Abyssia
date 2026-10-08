package com.abyssia.map;

import com.abyssia.Abyssia;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** MP01: hides the vanilla "Locked" tooltip line on deep sea maps. */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class DeepMapClient
{
    private DeepMapClient() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event)
    {
        if (!event.getItemStack().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(DeepSeaMapItem.MARKER)) return;
        event.getToolTip().removeIf(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("filled_map.locked"));
    }
}
