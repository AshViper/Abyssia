package com.abyssia.industry.client;

import com.abyssia.Abyssia;
import com.abyssia.industry.recipe.MachineRecipes;
import com.abyssia.registry.ModIndustry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client setup of the industrial blocks: the GUI screen. Render types come from the models (render_type). */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class IndustryClient
{
    private IndustryClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> MenuScreens.register(ModIndustry.MACHINE_MENU.get(), IndustryScreen::new));
    }

    /** The client keeps one RecipeManager and refills it, so the derived machine recipes are dropped on every sync. */
    @Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeEvents
    {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void onRecipesUpdated(RecipesUpdatedEvent event)
        {
            MachineRecipes.clearCache();
        }
    }
}
