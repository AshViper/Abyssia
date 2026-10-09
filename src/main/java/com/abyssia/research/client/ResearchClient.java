package com.abyssia.research.client;

import com.abyssia.Abyssia;
import com.abyssia.research.ClientResearch;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** AB05 client key binding: K opens the research {@link DatabaseScreen}. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ResearchClient
{
    public static final KeyMapping DATABASE = new KeyMapping("key." + Abyssia.MODID + ".database", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories." + Abyssia.MODID);

    static
    {
        // the unlock notice payload (R) only tells ClientResearch; the HUD line is drawn here
        ClientResearch.addUnlockListener(ScanHud::showUnlocked);
    }

    private ResearchClient() {}

    /** wires {@link ResearchView} to the synced {@link ClientResearch} state (client setup) */
    static
    {
        ResearchView.setSource(new ClientResearchSource());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (DATABASE.consumeClick())
            if (mc.player != null && mc.screen == null) mc.setScreen(new DatabaseScreen());
    }

    @Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration
    {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event)
        {
            event.register(DATABASE);
        }
    }
}
