package com.abyssia.industry.client;

import com.abyssia.Abyssia;
import com.abyssia.industry.recipe.MachineRecipes;
import com.abyssia.registry.ModIndustry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client setup of the industrial blocks: the GUI screen. Render types come from the models (render_type). */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class IndustryClient
{
    private IndustryClient() {}

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event)
    {
        event.register(ModIndustry.MACHINE_MENU.get(), IndustryScreen::new);
    }

    /** The client keeps one RecipeManager and refills it, so the derived machine recipes are dropped on every sync. */
    @EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
    public static final class GameEvents
    {
        private GameEvents() {}

        @SubscribeEvent
        public static void onRecipesUpdated(RecipesUpdatedEvent event)
        {
            MachineRecipes.clearCache();
        }
    }
}
