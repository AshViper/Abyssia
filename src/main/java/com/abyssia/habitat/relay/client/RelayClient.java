package com.abyssia.habitat.relay.client;

import com.abyssia.habitat.relay.RelaySyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/** WR01 client: the link list of the current level (from {@link RelaySyncPacket}), dropped on a level change / logout. */
public final class RelayClient
{
    private static List<RelaySyncPacket.Entry> links = List.of();
    private static ClientLevel owner;

    private RelayClient() {}

    /** ClientBuildContent (mod bus); the listeners go on the game bus */
    public static void register(IEventBus modBus)
    {
        NeoForge.EVENT_BUS.addListener(RelayBeamRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(RelayClient::onLoggingOut);
    }

    public static void setLinks(List<RelaySyncPacket.Entry> list)
    {
        links = List.copyOf(list);
        owner = Minecraft.getInstance().level;
    }

    /** links of {@code level}; empty (and cleared) when the list belongs to another level */
    public static List<RelaySyncPacket.Entry> links(ClientLevel level)
    {
        if (level != owner)
        {
            links = List.of();
            owner = null;
        }
        return links;
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        links = List.of();
        owner = null;
    }
}
