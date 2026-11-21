package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.item.SupplyContractItem;
import com.abyssia.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** QOL01: the supply contract's tooltip shows today's request (the same day / uuid hash as the server). */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ContractTooltip
{
    private ContractTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event)
    {
        if (!event.getItemStack().is(ModItems.SUPPLY_CONTRACT.get()) || event.getEntity() == null) return;
        event.getToolTip().add(SupplyContractItem.requestText(event.getEntity(), event.getEntity().level().getGameTime()).copy().withStyle(ChatFormatting.GRAY));
    }
}
