package com.abyssia.waypoint.client;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModIndustry;
import com.abyssia.waypoint.WaypointBeaconBlock;
import com.abyssia.waypoint.WaypointColors;
import com.abyssia.waypoint.WaypointEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * W01 client side: lamp tint (block + item), the HUD marker overlay, the settings screen, and the beacon list the
 * server sent for the current dimension.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WaypointClient
{
    /** beacons of the current dimension, as last sent by the server */
    private static List<WaypointEntry> beacons = List.of();
    private static int maxNameLength = 32;

    private WaypointClient() {}

    public static List<WaypointEntry> beacons()
    {
        return beacons;
    }

    public static int maxNameLength()
    {
        return maxNameLength;
    }

    public static void setBeacons(List<WaypointEntry> list, int maxName)
    {
        beacons = List.copyOf(list);
        maxNameLength = Math.max(1, maxName);
    }

    public static void openScreen(BlockPos pos, String name, int color)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(new WaypointBeaconScreen(pos.immutable(), name, color));
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event)
    {
        event.register((state, level, pos, tint) -> tint == 0 ? WaypointColors.rgb(state.getValue(WaypointBeaconBlock.COLOR)) : -1,
                ModIndustry.WAYPOINT_BEACON.get());
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event)
    {
        event.register((stack, tint) -> tint == 0 ? WaypointColors.rgb(WaypointColors.DEFAULT) : -1, ModIndustry.WAYPOINT_BEACON.get());
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event)
    {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "waypoint_markers", WaypointHud::render);
    }

    @Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeEvents
    {
        private ForgeEvents() {}

        /** The HUD projects with this frame's camera matrices. */
        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event)
        {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) WaypointHud.captureCamera(event);
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
        {
            beacons = List.of();
        }
    }
}
