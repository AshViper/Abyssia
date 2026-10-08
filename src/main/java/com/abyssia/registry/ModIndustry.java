package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.block.AbyssalExcavatorBlock;
import com.abyssia.industry.block.BeamBlock;
import com.abyssia.industry.block.EnergyCableBlock;
import com.abyssia.industry.block.EnergyDeviceBlock;
import com.abyssia.industry.block.GratingBlock;
import com.abyssia.industry.block.IndustrialLightBlock;
import com.abyssia.industry.block.IndustrialPipeBlock;
import com.abyssia.industry.block.MachineBlock;
import com.abyssia.industry.block.ValveBlock;
import com.abyssia.industry.blockentity.AbyssalExcavatorBlockEntity;
import com.abyssia.industry.blockentity.EnergyDeviceBlockEntity;
import com.abyssia.industry.blockentity.GeneratorBlockEntity;
import com.abyssia.industry.blockentity.ProcessingMachineBlockEntity;
import com.abyssia.industry.menu.IndustryMenu;
import com.abyssia.waypoint.WaypointBeaconBlock;
import com.abyssia.waypoint.WaypointBeaconBlockEntity;
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
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Abyssia.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Abyssia.MODID);
    /** blocks that get a block item in the Abyssia tab, in tab order */
    private static final List<RegistryObject<? extends Block>> ITEM_BLOCKS = new ArrayList<>();

    // ---------- building materials ----------
    public static final RegistryObject<Block> INDUSTRIAL_PANEL = block("industrial_panel", () -> new Block(metal()));
    public static final RegistryObject<Block> INDUSTRIAL_PANEL_STAIRS = block("industrial_panel_stairs",
            () -> new StairBlock(() -> INDUSTRIAL_PANEL.get().defaultBlockState(), metal()));
    public static final RegistryObject<Block> INDUSTRIAL_PANEL_SLAB = block("industrial_panel_slab", () -> new SlabBlock(metal()));
    public static final RegistryObject<Block> INDUSTRIAL_PANEL_WALL = block("industrial_panel_wall", () -> new WallBlock(metal().forceSolidOn()));
    public static final RegistryObject<Block> METAL_GRATING = block("metal_grating", () -> new GratingBlock(seeThrough()));
    public static final RegistryObject<Block> METAL_GRATING_SLAB = block("metal_grating_slab", () -> new SlabBlock(seeThrough()));
    public static final RegistryObject<Block> INDUSTRIAL_BEAM = block("industrial_beam", () -> new BeamBlock(metal()));
    public static final RegistryObject<Block> INDUSTRIAL_PIPE = block("industrial_pipe", () -> new IndustrialPipeBlock(small()));
    public static final RegistryObject<Block> INDUSTRIAL_VALVE = block("industrial_valve", () -> new ValveBlock(small()));
    public static final RegistryObject<Block> WORK_LIGHT = block("work_light", () -> new IndustrialLightBlock(small().lightLevel(s -> 15)));
    public static final RegistryObject<Block> WARNING_LIGHT = block("warning_light", () -> new IndustrialLightBlock(small().lightLevel(s -> 10)));
    /** W01 (inbox/specs/W01-waypoint-beacon.md): HUD waypoint, work light model with a tinted lamp */
    public static final RegistryObject<Block> WAYPOINT_BEACON = block("waypoint_beacon", () -> new WaypointBeaconBlock(small().lightLevel(s -> 15)));

    // ---------- energy ----------
    public static final RegistryObject<Block> ENERGY_CABLE = block("energy_cable", () -> new EnergyCableBlock(small().strength(1.0f, 6.0f), 128, 6, 10));
    public static final RegistryObject<Block> REINFORCED_ENERGY_CABLE = block("reinforced_energy_cable",
            () -> new EnergyCableBlock(small().strength(1.5f, 6.0f), 512, 5, 11));
    public static final RegistryObject<Block> HYDROTHERMAL_GENERATOR = machine(MachineKind.HYDROTHERMAL_GENERATOR);
    public static final RegistryObject<Block> AUXILIARY_GENERATOR = machine(MachineKind.AUXILIARY_GENERATOR);
    public static final RegistryObject<Block> ENERGY_DEVICE = block("energy_device",
            () -> new EnergyDeviceBlock(machineProps().lightLevel(s -> 2 * s.getValue(EnergyDeviceBlock.CHARGE))));

    // ---------- machines ----------
    public static final RegistryObject<Block> CRUSHER = machine(MachineKind.CRUSHER);
    public static final RegistryObject<Block> REFINERY_FURNACE = machine(MachineKind.REFINERY_FURNACE);
    public static final RegistryObject<Block> ALLOY_FURNACE = machine(MachineKind.ALLOY_FURNACE);
    public static final RegistryObject<Block> HIGH_TEMP_FURNACE = machine(MachineKind.HIGH_TEMP_FURNACE);
    /** I02 (inbox/specs/I02-selective-leaching-separator.md) */
    public static final RegistryObject<Block> SELECTIVE_LEACHING_SEPARATOR = machine(MachineKind.SELECTIVE_LEACHING_SEPARATOR);
    /** Excavator Mk1 (deep-sea mining platform); built with the habitat constructor (ExcavatorEntry): no block item, no drops */
    public static final RegistryObject<Block> ABYSSAL_EXCAVATOR = BLOCKS.register("abyssal_excavator", () ->
            new AbyssalExcavatorBlock(excavatorProps().lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 7 : 0)));

    /** Excavator Mk2 (ORE01): wider reach, faster, 2 ore a cycle, can mine the rare minerals */
    public static final RegistryObject<Block> ABYSSAL_EXCAVATOR_MK2 = BLOCKS.register("abyssal_excavator_mk2", () ->
            new AbyssalExcavatorBlock(excavatorProps().lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 7 : 0),
                    com.abyssia.industry.ExcavatorTier.MK2));

    /** invisible collision cells of the excavator multiblock (see ExcavatorStructure); no item, no drops */
    public static final RegistryObject<Block> EXCAVATOR_PART = BLOCKS.register("excavator_part", () ->
            new com.abyssia.industry.block.ExcavatorPartBlock(excavatorProps()));

    // ---------- items ----------
    /** Used once per selective leaching operation; registered in {@link #registerItems}. */
    public static final RegistryObject<Item> ACIDIC_LEACHING_REAGENT = RegistryObject.create(
            ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "acidic_leaching_reagent"), ForgeRegistries.ITEMS);

    // ---------- block entities and menu ----------
    public static final RegistryObject<BlockEntityType<ProcessingMachineBlockEntity>> MACHINE_ENTITY = BLOCK_ENTITIES.register("industrial_machine",
            () -> BlockEntityType.Builder.of(ProcessingMachineBlockEntity::new,
                    CRUSHER.get(), REFINERY_FURNACE.get(), ALLOY_FURNACE.get(), HIGH_TEMP_FURNACE.get(),
                    SELECTIVE_LEACHING_SEPARATOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<AbyssalExcavatorBlockEntity>> EXCAVATOR_ENTITY = BLOCK_ENTITIES.register("abyssal_excavator",
            () -> BlockEntityType.Builder.of(AbyssalExcavatorBlockEntity::new, ABYSSAL_EXCAVATOR.get(), ABYSSAL_EXCAVATOR_MK2.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.abyssia.industry.blockentity.ExcavatorPartBlockEntity>> EXCAVATOR_PART_ENTITY = BLOCK_ENTITIES.register("excavator_part",
            () -> BlockEntityType.Builder.of(com.abyssia.industry.blockentity.ExcavatorPartBlockEntity::new, EXCAVATOR_PART.get()).build(null));
    public static final RegistryObject<BlockEntityType<GeneratorBlockEntity>> GENERATOR_ENTITY = BLOCK_ENTITIES.register("industrial_generator",
            () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new, HYDROTHERMAL_GENERATOR.get(), AUXILIARY_GENERATOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<EnergyDeviceBlockEntity>> ENERGY_DEVICE_ENTITY = BLOCK_ENTITIES.register("energy_device",
            () -> BlockEntityType.Builder.of(EnergyDeviceBlockEntity::new, ENERGY_DEVICE.get()).build(null));
    public static final RegistryObject<BlockEntityType<WaypointBeaconBlockEntity>> WAYPOINT_BEACON_ENTITY = BLOCK_ENTITIES.register("waypoint_beacon",
            () -> BlockEntityType.Builder.of(WaypointBeaconBlockEntity::new, WAYPOINT_BEACON.get()).build(null));
    public static final RegistryObject<MenuType<IndustryMenu>> MACHINE_MENU = MENUS.register("industrial_machine",
            () -> IForgeMenuType.create(IndustryMenu::new));

    private ModIndustry() {}

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }

    /** Block items and the industrial items in the Abyssia tab (called from ModItems.register like ModTools). */
    public static void registerItems(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab)
    {
        for (RegistryObject<? extends Block> block : ITEM_BLOCKS)
            tab.add(items.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties())));
        tab.add(items.register(ACIDIC_LEACHING_REAGENT.getId().getPath(), () -> new Item(new Item.Properties())));
    }

    private static <T extends Block> RegistryObject<T> block(String name, Supplier<T> factory)
    {
        RegistryObject<T> block = BLOCKS.register(name, factory);
        ITEM_BLOCKS.add(block);
        return block;
    }

    private static RegistryObject<Block> machine(MachineKind kind)
    {
        return block(kind.id, () -> new MachineBlock(machineProps().lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 7 : 0), kind));
    }

    /** the excavator: machine strength, but see-through (light and water pass the multiblock; the renderer draws it) */
    private static BlockBehaviour.Properties excavatorProps()
    {
        return machineProps().noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                .isValidSpawn((s, l, p, e) -> false);
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
