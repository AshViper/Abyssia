package com.abyssia.research.client;

import com.abyssia.Abyssia;
import com.abyssia.research.ClientResearch;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** AB05 client key binding: K opens the research {@link DatabaseScreen}. */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
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
    public static void onRegisterKeys(RegisterKeyMappingsEvent event)
    {
        event.register(DATABASE);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        while (DATABASE.consumeClick())
            if (mc.player != null && mc.screen == null) mc.setScreen(new DatabaseScreen());
    }
}
