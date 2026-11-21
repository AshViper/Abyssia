package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** QOL01: lists the guaranteed (100 %) effects of an Abyssia dish with the vanilla effect name and duration. */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class FoodBuffTooltip
{
    private FoodBuffTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event)
    {
        if (!Config.QOL01_FOOD_TOOLTIP.get()) return;
        if (!Abyssia.MODID.equals(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getNamespace())) return;
        FoodProperties food = event.getItemStack().get(DataComponents.FOOD);
        if (food == null) return;
        for (FoodProperties.PossibleEffect pe : food.effects())
        {
            if (pe.probability() < 1.0f) continue;
            MobEffectInstance inst = pe.effect();
            Component name = Component.translatable(inst.getDescriptionId());
            if (inst.getAmplifier() > 0) name = Component.translatable("potion.withAmplifier", name, Component.translatable("potion.potency." + inst.getAmplifier()));
            name = Component.translatable("potion.withDuration", name, MobEffectUtil.formatDuration(inst, 1.0f, 20.0f));
            event.getToolTip().add(name.copy().withStyle(inst.getEffect().value().getCategory().getTooltipFormatting()));
        }
    }
}
