package com.abyssia.logistics;

import com.abyssia.Abyssia;
import com.abyssia.registry.AbyssiaTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** LOG01 logistics: the logistics pump and the cargo uplink, with their own registers (like VehicleContent). */
public final class LogisticsContent
{
    private LogisticsContent() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Abyssia.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Abyssia.MODID);

    public static final RegistryObject<Block> LOGISTICS_PUMP = BLOCKS.register("logistics_pump", () -> new LogisticsPumpBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> CARGO_UPLINK = BLOCKS.register("cargo_uplink", () -> new CargoUplinkBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL)));

    public static final RegistryObject<BlockEntityType<LogisticsPumpBlockEntity>> PUMP_ENTITY = BLOCK_ENTITIES.register("logistics_pump",
            () -> BlockEntityType.Builder.of(LogisticsPumpBlockEntity::new, LOGISTICS_PUMP.get()).build(null));
    public static final RegistryObject<BlockEntityType<CargoUplinkBlockEntity>> UPLINK_ENTITY = BLOCK_ENTITIES.register("cargo_uplink",
            () -> BlockEntityType.Builder.of(CargoUplinkBlockEntity::new, CARGO_UPLINK.get()).build(null));

    public static final RegistryObject<Item> LOGISTICS_PUMP_ITEM = ITEMS.register("logistics_pump",
            () -> new BlockItem(LOGISTICS_PUMP.get(), new Item.Properties()));
    public static final RegistryObject<Item> CARGO_UPLINK_ITEM = ITEMS.register("cargo_uplink",
            () -> new BlockItem(CARGO_UPLINK.get(), new Item.Properties()));

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        // the creative tab reads these lists when it is built
        AbyssiaTab.MACHINES.items().add(LOGISTICS_PUMP_ITEM);
        AbyssiaTab.MACHINES.items().add(CARGO_UPLINK_ITEM);
    }
}
