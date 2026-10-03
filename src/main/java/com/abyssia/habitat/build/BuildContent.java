package com.abyssia.habitat.build;

import com.abyssia.habitat.HabitatMode;
import com.abyssia.habitat.client.build.ClientBuildContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * BT01: the one place build menu entries are registered (called from the mod constructor). Each sub-spec adds ONE
 * line at its anchor below (its own DeferredRegisters may be registered on {@code bus} from that call); BT01h fixes
 * the final order. Client-only parts (renderers, screens) go in {@link ClientBuildContent}.
 */
public final class BuildContent
{
    private BuildContent() {}

    public static void register(IEventBus bus)
    {
        // BT01a: the H01 modules (ids = HabitatMode ids, old constructor NBT keeps working)
        for (HabitatMode mode : HabitatMode.values()) BuildRegistry.register(new ModuleEntry(mode));
        BuildRegistry.register(new com.abyssia.habitat.dismantle.DismantleEntry()); // BT01b
        com.abyssia.habitat.ladder.LadderContent.register(bus); // BT01c
        com.abyssia.habitat.generator.ModGenerators.register(bus); // BT01d
        com.abyssia.habitat.custom.CustomContent.register(bus); // BT01e
        // BT01f
        BuildRegistry.register(new com.abyssia.habitat.scan.ScanUpgradeEntry());
        com.abyssia.habitat.aquarium.AquariumContent.register(bus); // BT01g
        // BT01h: locker / workbench, then the final menu order (the module tab also holds open_entrance, customize holds vertical_hatch)
        BuildRegistry.register(new com.abyssia.habitat.build.furniture.LockerEntry());
        BuildRegistry.register(new com.abyssia.habitat.build.furniture.WorkbenchEntry());
        BuildRegistry.applyOrder(java.util.List.of(
                "foundation", "room", "corridor", "entrance", "open_entrance", "moon_pool", "scan_room",
                "large_locker", "wall_workbench", "charging_station", "ladder",
                "current_turbine", "geothermal_generator", "biofuel_generator",
                "glass_wall", "wall_revert", "vertical_hatch", "dismantle",
                "scan_upgrade", "aquarium"));

        // only resolved on a physical client (ClientBuildContent may touch client classes)
        if (FMLEnvironment.dist.isClient()) ClientBuildContent.register(bus);
    }
}
