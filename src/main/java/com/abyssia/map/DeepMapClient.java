package com.abyssia.map;

import com.abyssia.Abyssia;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** MP01: hides the vanilla "Locked" tooltip line on deep sea maps. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class DeepMapClient
{
    private DeepMapClient() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event)
    {
        if (event.getItemStack().getTag() == null || !event.getItemStack().getTag().getBoolean(DeepSeaMapItem.MARKER)) return;
        event.getToolTip().removeIf(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("filled_map.locked"));
    }
}
