package com.abyssia.habitat.client.build;

import net.neoforged.bus.api.IEventBus;

/**
 * BT01: client-only registration for build entries (block entity renderers, screens, hologram extras), called by
 * BuildContent on a physical client with the mod event bus. Each sub-spec adds ONE line at its anchor.
 */
public final class ClientBuildContent
{
    private ClientBuildContent() {}

    public static void register(IEventBus bus)
    {
        com.abyssia.habitat.dismantle.client.DismantleClient.register(bus); // BT01b
        // BT01c
        com.abyssia.habitat.generator.client.GeneratorClient.register(bus); // BT01d
        // BT01e
        // BT01f
        com.abyssia.habitat.aquarium.client.AquariumClient.register(bus); // BT01g
        // BT01h
        com.abyssia.habitat.relay.client.RelayClient.register(bus); // WR01
        com.abyssia.industry.client.ExcavatorClient.register(bus); // ORE01 excavator multiblock
    }
}
