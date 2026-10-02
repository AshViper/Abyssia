package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.block.BeamBlock;
import com.abyssia.industry.block.EnergyCableBlock;
import com.abyssia.industry.block.EnergyDeviceBlock;
import com.abyssia.industry.block.GratingBlock;
import com.abyssia.industry.block.IndustrialLightBlock;
import com.abyssia.industry.block.IndustrialPipeBlock;
import com.abyssia.industry.block.MachineBlock;
import com.abyssia.industry.block.ValveBlock;
import com.abyssia.industry.blockentity.EnergyDeviceBlockEntity;
import com.abyssia.industry.blockentity.GeneratorBlockEntity;
import com.abyssia.industry.blockentity.IndustryBlockEntity;
import com.abyssia.industry.blockentity.ProcessingMachineBlockEntity;
import com.abyssia.industry.menu.IndustryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Industrial blocks (feature I01, inbox/specs/I01-industrial-blocks.md): building materials, pipe / valve / lights,
 * energy cables, the machines (I02 adds the selective leaching separator and its reagent item), generators and the
 * energy device, with their block entities and the shared menu.
 * Blockstates, models, loot, recipes, tags and names come from tools/industrial_assets.py.
 */
public final class ModIndustry
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Abyssia.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Abyssia.MODID);
    /** blocks that get a block item in the Abyssia tab, in tab order */
    private static final List<DeferredBlock<? extends Block>> ITEM_BLOCKS = new ArrayList<>();

    // ---------- building materials ----------
    public static final DeferredBlock<Block> INDUSTRIAL_PANEL = block("industrial_panel", () -> new Block(metal()));
    public static final DeferredBlock<Block> INDUSTRIAL_PANEL_STAIRS = block("industrial_panel_stairs",
            () -> new StairBlock(INDUSTRIAL_PANEL.get().defaultBlockState(), metal()));
    public static final DeferredBlock<Block> INDUSTRIAL_PANEL_SLAB = block("industrial_panel_slab", () -> new SlabBlock(metal()));
    public static final DeferredBlock<Block> INDUSTRIAL_PANEL_WALL = block("industrial_panel_wall", () -> new WallBlock(metal().forceSolidOn()));
    public static final DeferredBlock<Block> METAL_GRATING = block("metal_grating", () -> new GratingBlock(seeThrough()));
    public static final DeferredBlock<Block> METAL_GRATING_SLAB = block("metal_grating_slab", () -> new SlabBlock(seeThrough()));
    public static final DeferredBlock<Block> INDUSTRIAL_BEAM = block("industrial_beam", () -> new BeamBlock(metal()));
    public static final DeferredBlock<Block> INDUSTRIAL_PIPE = block("industrial_pipe", () -> new IndustrialPipeBlock(small()));
    public static final DeferredBlock<Block> INDUSTRIAL_VALVE = block("industrial_valve", () -> new ValveBlock(small()));
    public static final DeferredBlock<Block> WORK_LIGHT = block("work_light", () -> new IndustrialLightBlock(small().lightLevel(s -> 15)));
    public static final DeferredBlock<Block> WARNING_LIGHT = block("warning_light", () -> new IndustrialLightBlock(small().lightLevel(s -> 10)));

    // ---------- energy ----------
    public static final DeferredBlock<Block> ENERGY_CABLE = block("energy_cable", () -> new EnergyCableBlock(small().strength(1.0f, 6.0f), 128, 6, 10));
    public static final DeferredBlock<Block> REINFORCED_ENERGY_CABLE = block("reinforced_energy_cable",
            () -> new EnergyCableBlock(small().strength(1.5f, 6.0f), 512, 5, 11));
    public static final DeferredBlock<Block> HYDROTHERMAL_GENERATOR = machine(MachineKind.HYDROTHERMAL_GENERATOR);
    public static final DeferredBlock<Block> AUXILIARY_GENERATOR = machine(MachineKind.AUXILIARY_GENERATOR);
    public static final DeferredBlock<Block> ENERGY_DEVICE = block("energy_device",
            () -> new EnergyDeviceBlock(machineProps().lightLevel(s -> 2 * s.getValue(EnergyDeviceBlock.CHARGE))));

    // ---------- machines ----------
    public static final DeferredBlock<Block> CRUSHER = machine(MachineKind.CRUSHER);
    public static final DeferredBlock<Block> REFINERY_FURNACE = machine(MachineKind.REFINERY_FURNACE);
    public static final DeferredBlock<Block> ALLOY_FURNACE = machine(MachineKind.ALLOY_FURNACE);
    public static final DeferredBlock<Block> HIGH_TEMP_FURNACE = machine(MachineKind.HIGH_TEMP_FURNACE);
    /** I02 (inbox/specs/I02-selective-leaching-separator.md) */
    public static final DeferredBlock<Block> SELECTIVE_LEACHING_SEPARATOR = machine(MachineKind.SELECTIVE_LEACHING_SEPARATOR);

    // ---------- items ----------
    /** Used once per selective leaching operation; registered in {@link #registerItems}. */
    public static final DeferredItem<Item> ACIDIC_LEACHING_REAGENT = DeferredItem.createItem(
            ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "acidic_leaching_reagent"));

    // ---------- block entities and menu ----------
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingMachineBlockEntity>> MACHINE_ENTITY = BLOCK_ENTITIES.register("industrial_machine",
            () -> BlockEntityType.Builder.of(ProcessingMachineBlockEntity::new,
                    CRUSHER.get(), REFINERY_FURNACE.get(), ALLOY_FURNACE.get(), HIGH_TEMP_FURNACE.get(),
                    SELECTIVE_LEACHING_SEPARATOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlockEntity>> GENERATOR_ENTITY = BLOCK_ENTITIES.register("industrial_generator",
            () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new, HYDROTHERMAL_GENERATOR.get(), AUXILIARY_GENERATOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyDeviceBlockEntity>> ENERGY_DEVICE_ENTITY = BLOCK_ENTITIES.register("energy_device",
            () -> BlockEntityType.Builder.of(EnergyDeviceBlockEntity::new, ENERGY_DEVICE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<IndustryMenu>> MACHINE_MENU = MENUS.register("industrial_machine",
            () -> IMenuTypeExtension.create(IndustryMenu::new));

    private ModIndustry() {}

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(ModIndustry::registerCapabilities);
    }

    /** NeoForge block capabilities (Forge: BlockEntity#getCapability): FE on every side, sided item handlers. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        for (BlockEntityType<? extends IndustryBlockEntity> type : List.of(MACHINE_ENTITY.get(), GENERATOR_ENTITY.get(), ENERGY_DEVICE_ENTITY.get()))
        {
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, IndustryBlockEntity::energyStorage);
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, IndustryBlockEntity::itemHandler);
        }
    }

    /** Block items and the industrial items in the Abyssia tab (called from ModItems.register like ModTools). */
    public static void registerItems(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
    {
        for (DeferredBlock<? extends Block> block : ITEM_BLOCKS)
            tab.add(items.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties())));
        tab.add(items.register(ACIDIC_LEACHING_REAGENT.getId().getPath(), () -> new Item(new Item.Properties())));
    }

    private static <T extends Block> DeferredBlock<T> block(String name, Supplier<T> factory)
    {
        DeferredBlock<T> block = BLOCKS.register(name, factory);
        ITEM_BLOCKS.add(block);
        return block;
    }

    private static DeferredBlock<Block> machine(MachineKind kind)
    {
        return block(kind.id, () -> new MachineBlock(machineProps().lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 7 : 0), kind));
    }

    /** Pressure-proof metal: like an iron block, pickaxe needed. */
    private static BlockBehaviour.Properties metal()
    {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(4.0f, 6.0f)
                .requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties seeThrough()
    {
        return metal().noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                .isValidSpawn((s, l, p, e) -> false);
    }

    /** Pipes, valves, lights and cables: lighter, no occlusion. */
    private static BlockBehaviour.Properties small()
    {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f)
                .requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion();
    }

    /** Machines, generators, energy device: plain full cubes, sturdier. */
    private static BlockBehaviour.Properties machineProps()
    {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f)
                .requiresCorrectToolForDrops().sound(SoundType.METAL);
    }
}
