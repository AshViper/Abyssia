package com.abyssia.waypoint.client;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModIndustry;
import com.abyssia.waypoint.WaypointBeaconBlock;
import com.abyssia.waypoint.WaypointColors;
import com.abyssia.waypoint.WaypointEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

/**
 * W01 client side: lamp tint (block + item), the HUD marker overlay, the settings screen, and the beacon list the
 * server sent for the current dimension.
 */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
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
        event.register((state, level, pos, tint) -> tint == 0 ? 0xFF000000 | WaypointColors.rgb(state.getValue(WaypointBeaconBlock.COLOR)) : -1,
                ModIndustry.WAYPOINT_BEACON.get());
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event)
    {
        event.register((stack, tint) -> tint == 0 ? 0xFF000000 | WaypointColors.rgb(WaypointColors.DEFAULT) : -1, ModIndustry.WAYPOINT_BEACON.get());
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event)
    {
        event.registerBelow(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "waypoint_markers"), WaypointHud::render);
    }

    @EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
    public static final class GameEvents
    {
        private GameEvents() {}

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
