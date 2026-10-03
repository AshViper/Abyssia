package com.abyssia.habitat.charging;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** BT01e charging station: block + block entity (no item, no drops) and its build entry. */
public final class ChargingContent
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Abyssia.MODID);

    public static final DeferredBlock<Block> STATION = BLOCKS.register("charging_station",
            () -> new ChargingStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f)
                    .noLootTable().noOcclusion().sound(SoundType.METAL).lightLevel(s -> 7)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargingStationBlockEntity>> STATION_ENTITY = BLOCK_ENTITIES.register(
            "charging_station", () -> BlockEntityType.Builder.of(ChargingStationBlockEntity::new, STATION.get()).build(null));

    private ChargingContent() {}

    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(ChargingContent::registerCapabilities);
        BuildRegistry.register(new ChargingStationEntry());
    }

    /** FE (receive only) on every face: HabitatPower and the cable network treat the station as a consumer */
    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, STATION_ENTITY.get(), (be, side) -> be.energy());
    }
}
