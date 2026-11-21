package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** QOL01: lists the guaranteed (100 %) effects of an Abyssia dish with the vanilla effect name and duration. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FoodBuffTooltip
{
    private FoodBuffTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event)
    {
        if (!Config.QOL01_FOOD_TOOLTIP.get()) return;
        if (!Abyssia.MODID.equals(ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem()).getNamespace())) return;
        FoodProperties food = event.getItemStack().getFoodProperties(event.getEntity());
        if (food == null) return;
        for (Pair<MobEffectInstance, Float> pe : food.getEffects())
        {
            if (pe.getSecond() < 1.0f) continue;
            MobEffectInstance inst = pe.getFirst();
            Component name = Component.translatable(inst.getDescriptionId());
            if (inst.getAmplifier() > 0) name = Component.translatable("potion.withAmplifier", name, Component.translatable("potion.potency." + inst.getAmplifier()));
            name = Component.translatable("potion.withDuration", name, MobEffectUtil.formatDuration(inst, 1.0f));
            event.getToolTip().add(name.copy().withStyle(inst.getEffect().getCategory().getTooltipFormatting()));
        }
    }
}
