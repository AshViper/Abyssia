package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.client.light.SpotlightProjector;
import com.abyssia.habitat.client.HabitatClient;
import com.abyssia.network.AbyssiaNetwork;
import com.abyssia.vehicle.Submarine;
import com.abyssia.vehicle.SubmarineLightPacket;
import com.abyssia.vehicle.SubmarineUpgrades;
import com.abyssia.vehicle.VehicleContent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * SUB02 pilot controls: W/S forward / back along the nose, A/D sideways, Space rises, Ctrl (the sprint key) dives and releases the dock,
 * G headlights, Shift = vanilla dismount, mouse = target yaw + pitch; the hull swings there with a short lag (SUB07, SubmarineSteering). With toggle-sprint on, Ctrl is read as the physical key so the
 * toggle does not latch it. NeoForge's key lookup gives a key to every mapping bound to it, so G (shared with the
 * habitat build menu by default) works through consumeClick (Forge reads the raw key event instead).
 */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class SubmarineClient
{
    public static final KeyMapping LIGHT = new KeyMapping("key." + Abyssia.MODID + ".submarine_light", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, HabitatClient.CATEGORY);

    private SubmarineClient() {}

    static Submarine riding()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() instanceof Submarine sub ? sub : null;
    }

    /** {forward, strafe (left +), vertical (+1 Space / -1 Ctrl; -1 also undocks)} of the local player; zero with a screen open */
    static int[] input()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return new int[]{0, 0, 0};
        Options o = mc.options;
        int forward = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        int strafe = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        int vertical = (o.keyJump.isDown() ? 1 : 0) - (down(mc) ? 1 : 0);
        return new int[]{forward, strafe, vertical};
    }

    /** the sprint key held right now (hold mode: the mapping; toggle mode: the physical key, keyboard bindings only) */
    private static boolean down(Minecraft mc)
    {
        KeyMapping sprint = mc.options.keySprint;
        if (!mc.options.toggleSprint().get()) return sprint.isDown();
        InputConstants.Key key = sprint.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event)
    {
        while (LIGHT.consumeClick())
            if (Minecraft.getInstance().screen == null && riding() != null)
                AbyssiaNetwork.sendToServer(new SubmarineLightPacket(SubmarineLightPacket.Action.TOGGLE_LIGHTS));
    }

    @EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
    public static final class Registration
    {
        private Registration() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event)
        {
            event.register(LIGHT);
        }

        @SubscribeEvent
        public static void screens(RegisterMenuScreensEvent event)
        {
            event.register(SubmarineUpgrades.MENU.get(), SubmarineUpgradeScreen::new);
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event)
        {
            event.registerEntityRenderer(VehicleContent.SUBMARINE.get(), SubmarineRenderer::new);
            event.registerBlockEntityRenderer(VehicleContent.SUBMARINE_DOCK_ENTITY.get(), DockRenderer::new);
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event)
        {
            SpotlightProjector.addSource(SubmarineLamps::collect);
            Submarine.pilot = new Submarine.Pilot()
            {
                @Override
                public int[] input()
                {
                    return SubmarineClient.input();
                }

                @Override
                public void requestUndock(Submarine sub)
                {
                    AbyssiaNetwork.sendToServer(new SubmarineLightPacket(SubmarineLightPacket.Action.UNDOCK));
                }
            };
        }
    }
}