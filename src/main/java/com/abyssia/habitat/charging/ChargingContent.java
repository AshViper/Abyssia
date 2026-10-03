package com.abyssia.habitat.charging;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** BT01e charging station: block + block entity (no item, no drops) and its build entry. */
public final class ChargingContent
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Abyssia.MODID);

    public static final RegistryObject<Block> STATION = BLOCKS.register("charging_station",
            () -> new ChargingStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f)
                    .noLootTable().noOcclusion().sound(SoundType.METAL).lightLevel(s -> 7)));
    public static final RegistryObject<BlockEntityType<ChargingStationBlockEntity>> STATION_ENTITY = BLOCK_ENTITIES.register("charging_station",
            () -> BlockEntityType.Builder.of(ChargingStationBlockEntity::new, STATION.get()).build(null));

    private ChargingContent() {}

    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        BuildRegistry.register(new ChargingStationEntry());
    }
}
