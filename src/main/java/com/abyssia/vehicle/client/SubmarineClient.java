package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.habitat.client.HabitatClient;
import com.abyssia.network.AbyssiaNetwork;
import com.abyssia.vehicle.Submarine;
import com.abyssia.vehicle.SubmarineLightPacket;
import com.abyssia.vehicle.VehicleContent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * SUB02 pilot controls: W/S forward / back, A/D sideways, Space up, Ctrl (the sprint key, read as the physical key
 * so toggle-sprint does not latch it) down / release the dock, G headlights, Shift = vanilla dismount, mouse = yaw.
 * G shares its default key with the habitat build menu, so it is read from the raw key event while riding instead of
 * the key-mapping lookup (which hands a key to one mapping only).
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
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

    /** {forward, strafe (left +), vertical (up +)} of the local player; zero with a screen open */
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

    /** the sprint key held right now (physical state; keyboard bindings only) */
    private static boolean down(Minecraft mc)
    {
        InputConstants.Key key = mc.options.keySprint.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }

    @SubscribeEvent
    public static void key(InputEvent.Key event)
    {
        if (event.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null || riding() == null) return;
        if (LIGHT.isActiveAndMatches(InputConstants.getKey(event.getKey(), event.getScanCode())))
            AbyssiaNetwork.sendToServer(new SubmarineLightPacket(SubmarineLightPacket.Action.TOGGLE_LIGHTS));
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        // presses are handled in key(); drop the queued clicks so none piles up
        while (LIGHT.consumeClick()) { }
    }

    @Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration
    {
        private Registration() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event)
        {
            event.register(LIGHT);
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event)
        {
            event.registerEntityRenderer(VehicleContent.SUBMARINE.get(), SubmarineRenderer::new);
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> MenuScreens.register(VehicleContent.SUBMARINE_UPGRADE_MENU.get(), SubmarineUpgradeScreen::new));
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
