package com.abyssia.habitat.custom;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import com.abyssia.habitat.charging.ChargingContent;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * BT01e registration (one call from BuildContent): the air membrane block, the charging station
 * ({@link ChargingContent}) and the build entries open entrance / glass wall / wall revert / charging station.
 * Blockstates and models: tools/bt01/custom_assets.py. No block items, no loot tables.
 */
public final class CustomContent
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);

    public static final RegistryObject<Block> MEMBRANE = BLOCKS.register("habitat_membrane",
            () -> new HabitatMembraneBlock(HabitatMembraneBlock.properties()));

    private CustomContent() {}

    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
        BuildRegistry.register(new OpenEntranceEntry());
        BuildRegistry.register(new GlassWallEntry());
        BuildRegistry.register(new WallRevertEntry());
        ChargingContent.register(bus);
    }
}
