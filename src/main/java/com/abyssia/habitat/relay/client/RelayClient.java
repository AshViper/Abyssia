package com.abyssia.habitat.relay.client;

import com.abyssia.habitat.relay.RelaySyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.List;

/**
 * WR01 client side: the link list of the current level as last sent by the server ({@link RelaySyncPacket}); dropped
 * when the client level changes or on logout. Drawn by {@link RelayBeamRenderer}.
 */
public final class RelayClient
{
    private static List<RelaySyncPacket.Entry> links = List.of();
    private static ClientLevel level;

    private RelayClient() {}

    /** from ClientBuildContent (mod bus); the listeners go on the Forge bus */
    public static void register(IEventBus modBus)
    {
        MinecraftForge.EVENT_BUS.addListener(RelayClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(RelayBeamRenderer::onRenderLevelStage);
    }

    public static void setLinks(List<RelaySyncPacket.Entry> list)
    {
        links = List.copyOf(list);
        level = Minecraft.getInstance().level;
    }

    public static List<RelaySyncPacket.Entry> links()
    {
        ClientLevel now = Minecraft.getInstance().level;
        if (level == null) level = now;
        else if (now != level)
        {
            links = List.of();
            level = now;
        }
        return links;
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        links = List.of();
        level = null;
    }
}
