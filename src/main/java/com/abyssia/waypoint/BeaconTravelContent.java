package com.abyssia.waypoint;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModIndustry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** QOL01: registers the beacon's energy capability and the travel payload itself (AbyssiaNetwork stays untouched). */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class BeaconTravelContent
{
    private BeaconTravelContent() {}

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModIndustry.WAYPOINT_BEACON_ENTITY.get(), (be, side) -> be.energy());
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event)
    {
        event.registrar("1").playToServer(BeaconTravelPacket.TYPE, BeaconTravelPacket.STREAM_CODEC, BeaconTravelPacket::handle);
    }
}
