package com.abyssia.logistics;

import com.abyssia.Abyssia;
import com.abyssia.registry.AbyssiaTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** LOG01 logistics: the logistics pump and the cargo uplink, with their own registers (like VehicleContent). */
public final class LogisticsContent
{
    private LogisticsContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Abyssia.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Abyssia.MODID);

    public static final DeferredBlock<Block> LOGISTICS_PUMP = BLOCKS.register("logistics_pump", () -> new LogisticsPumpBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> CARGO_UPLINK = BLOCKS.register("cargo_uplink", () -> new CargoUplinkBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogisticsPumpBlockEntity>> PUMP_ENTITY = BLOCK_ENTITIES.register("logistics_pump",
            () -> BlockEntityType.Builder.of(LogisticsPumpBlockEntity::new, LOGISTICS_PUMP.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoUplinkBlockEntity>> UPLINK_ENTITY = BLOCK_ENTITIES.register("cargo_uplink",
            () -> BlockEntityType.Builder.of(CargoUplinkBlockEntity::new, CARGO_UPLINK.get()).build(null));

    public static final DeferredItem<BlockItem> LOGISTICS_PUMP_ITEM = ITEMS.register("logistics_pump",
            () -> new BlockItem(LOGISTICS_PUMP.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> CARGO_UPLINK_ITEM = ITEMS.register("cargo_uplink",
            () -> new BlockItem(CARGO_UPLINK.get(), new Item.Properties()));

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(LogisticsContent::registerCapabilities);
        // the creative tab reads these lists when it is built
        AbyssiaTab.MACHINES.items().add(LOGISTICS_PUMP_ITEM);
        AbyssiaTab.MACHINES.items().add(CARGO_UPLINK_ITEM);
    }

    /** The uplink's slots (insert-only in send mode, extract-only in receive mode) and its FE buffer. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, UPLINK_ENTITY.get(), CargoUplinkBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, UPLINK_ENTITY.get(), CargoUplinkBlockEntity::energyStorage);
    }
}
