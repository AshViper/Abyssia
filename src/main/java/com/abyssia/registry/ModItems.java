package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.item.MaterialItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import com.abyssia.item.MaterialTools;
import com.abyssia.item.ModTools;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Items (block items and deep-sea materials) and the Abyssia creative tab that lists them all. */
public final class ModItems
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Abyssia.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Abyssia.MODID);
    private static final List<DeferredItem<? extends Item>> TAB_ITEMS = new ArrayList<>();

    // Deep-sea metals and minerals
    public static final DeferredItem<Item> RAW_MANGANESE = item("raw_manganese");
    public static final DeferredItem<Item> RAW_COBALT = item("raw_cobalt");
    public static final DeferredItem<Item> RAW_NICKEL = item("raw_nickel");
    public static final DeferredItem<Item> MANGANESE_INGOT = item("manganese_ingot");
    public static final DeferredItem<Item> COBALT_INGOT = item("cobalt_ingot");
    public static final DeferredItem<Item> NICKEL_INGOT = item("nickel_ingot");
    public static final DeferredItem<Item> SULFUR = item("sulfur");
    public static final DeferredItem<Item> CRUST_POWDER = item("crust_powder");
    public static final DeferredItem<Item> THERMAL_CRYSTAL_SHARD = item("thermal_crystal_shard");
    public static final DeferredItem<Item> ABYSSAL_CRYSTAL_SHARD = item("abyssal_crystal_shard");

    // Rare metals (tools/gen_deep_assets.py RARE_METALS): tooltip names the source (item.abyssia.<id>.source)
    public static final DeferredItem<Item> RAW_PLATINUM = sourcedItem("raw_platinum");
    public static final DeferredItem<Item> RAW_TELLURIUM = sourcedItem("raw_tellurium");
    public static final DeferredItem<Item> RAW_MOLYBDENUM = sourcedItem("raw_molybdenum");
    public static final DeferredItem<Item> RAW_VANADIUM = sourcedItem("raw_vanadium");
    public static final DeferredItem<Item> RAW_TUNGSTEN = sourcedItem("raw_tungsten");
    public static final DeferredItem<Item> RAW_YTTRIUM = sourcedItem("raw_yttrium");
    public static final DeferredItem<Item> PLATINUM_INGOT = sourcedItem("platinum_ingot");
    public static final DeferredItem<Item> TELLURIUM_INGOT = sourcedItem("tellurium_ingot");
    public static final DeferredItem<Item> MOLYBDENUM_INGOT = sourcedItem("molybdenum_ingot");
    public static final DeferredItem<Item> VANADIUM_INGOT = sourcedItem("vanadium_ingot");
    public static final DeferredItem<Item> TUNGSTEN_INGOT = sourcedItem("tungsten_ingot");
    public static final DeferredItem<Item> YTTRIUM_INGOT = sourcedItem("yttrium_ingot");
    public static final DeferredItem<Item> ABYSSAL_ALLOY_INGOT = sourcedItem("abyssal_alloy_ingot");

    // Material processing system (docs/material-system.md, tools/material_spec.json): plain items, rarity from the spec.
    // Tools and armor of the system are in com.abyssia.item.MaterialTools.
    // TODO(phase2 machines): crusher / refinery_furnace / alloy_furnace / crystal_processor / high_temp_furnace /
    // energy_device / mining_machine (spec machines_phase2) would register their blocks + menus here and automate
    // the same spec recipes; machine_frame is already obtainable.
    // powders and concentrates (crushed with the crushing hammer)
    public static final DeferredItem<Item> IRON_POWDER = item("iron_powder", Rarity.COMMON);
    public static final DeferredItem<Item> COBALT_POWDER = item("cobalt_powder", Rarity.COMMON);
    public static final DeferredItem<Item> NICKEL_POWDER = item("nickel_powder", Rarity.COMMON);
    public static final DeferredItem<Item> MANGANESE_POWDER = item("manganese_powder", Rarity.COMMON);
    public static final DeferredItem<Item> VANADIUM_POWDER = item("vanadium_powder", Rarity.UNCOMMON);
    public static final DeferredItem<Item> TUNGSTEN_POWDER = item("tungsten_powder", Rarity.UNCOMMON);
    public static final DeferredItem<Item> TELLURIUM_POWDER = item("tellurium_powder", Rarity.UNCOMMON);
    public static final DeferredItem<Item> YTTRIUM_POWDER = item("yttrium_powder", Rarity.UNCOMMON);
    public static final DeferredItem<Item> COBALT_CONCENTRATE = item("cobalt_concentrate", Rarity.COMMON);
    public static final DeferredItem<Item> MANGANESE_CONCENTRATE = item("manganese_concentrate", Rarity.COMMON);
    public static final DeferredItem<Item> NICKEL_CONCENTRATE = item("nickel_concentrate", Rarity.COMMON);
    // basic iron/copper parts
    public static final DeferredItem<Item> IRON_PLATE = item("iron_plate", Rarity.COMMON);
    public static final DeferredItem<Item> IRON_ROD = item("iron_rod", Rarity.COMMON);
    public static final DeferredItem<Item> IRON_GEAR = item("iron_gear", Rarity.COMMON);
    public static final DeferredItem<Item> COPPER_WIRE = item("copper_wire", Rarity.COMMON);
    // alloys
    public static final DeferredItem<Item> CORROSION_ALLOY_INGOT = item("corrosion_alloy_ingot", Rarity.UNCOMMON);
    public static final DeferredItem<Item> HIGH_STRENGTH_ALLOY_INGOT = item("high_strength_alloy_ingot", Rarity.UNCOMMON);
    public static final DeferredItem<Item> HEAT_RESISTANT_ALLOY_INGOT = item("heat_resistant_alloy_ingot", Rarity.UNCOMMON);
    public static final DeferredItem<Item> TUNGSTEN_ALLOY_INGOT = item("tungsten_alloy_ingot", Rarity.RARE);
    public static final DeferredItem<Item> CONDUCTIVE_ALLOY_INGOT = item("conductive_alloy_ingot", Rarity.RARE);
    public static final DeferredItem<Item> THERMAL_ALLOY_INGOT = item("thermal_alloy_ingot", Rarity.RARE);
    // plant-based materials
    public static final DeferredItem<Item> REINFORCED_FIBER = item("reinforced_fiber", Rarity.COMMON);
    public static final DeferredItem<Item> REINFORCED_CABLE = item("reinforced_cable", Rarity.UNCOMMON);
    public static final DeferredItem<Item> MARINE_RESIN = item("marine_resin", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSSAL_COMPOSITE = item("abyssal_composite", Rarity.RARE);
    // thermal materials
    public static final DeferredItem<Item> THERMAL_CORE = item("thermal_core", Rarity.RARE);
    public static final DeferredItem<Item> THERMAL_REAGENT = item("thermal_reagent", Rarity.RARE);
    // crystal and energy parts
    public static final DeferredItem<Item> LUMINOUS_CRYSTAL = item("luminous_crystal", Rarity.RARE);
    public static final DeferredItem<Item> CRYSTAL_CORE = item("crystal_core", Rarity.RARE);
    public static final DeferredItem<Item> ABYSSAL_ENERGY_CELL = item("abyssal_energy_cell", Rarity.RARE);
    public static final DeferredItem<Item> ADVANCED_LUMEN_CELL = item("advanced_lumen_cell", Rarity.RARE);
    public static final DeferredItem<Item> ABYSSAL_LIGHT_CORE = item("abyssal_light_core", Rarity.EPIC);
    public static final DeferredItem<Item> ADVANCED_ENERGY_CELL = item("advanced_energy_cell", Rarity.EPIC);
    public static final DeferredItem<Item> ABYSSAL_POWER_CORE = item("abyssal_power_core", Rarity.EPIC);
    // shared components
    public static final DeferredItem<Item> HARDENED_TIP = item("hardened_tip", Rarity.UNCOMMON);
    public static final DeferredItem<Item> TUNGSTEN_TIP = item("tungsten_tip", Rarity.RARE);
    public static final DeferredItem<Item> DRILL_HEAD = item("drill_head", Rarity.RARE);
    public static final DeferredItem<Item> PRESSURE_VALVE = item("pressure_valve", Rarity.UNCOMMON);
    public static final DeferredItem<Item> PRESSURE_SHELL = item("pressure_shell", Rarity.EPIC);
    public static final DeferredItem<Item> THERMAL_COMPONENT = item("thermal_component", Rarity.RARE);
    public static final DeferredItem<Item> CONDUCTIVE_COMPONENT = item("conductive_component", Rarity.RARE);
    public static final DeferredItem<Item> MACHINE_FRAME = item("machine_frame", Rarity.UNCOMMON);

    // Spawn eggs (colours match tools/fauna/<species>.py INFO["egg"])
    public static final DeferredItem<Item> ANGLERFISH_SPAWN_EGG = spawnEgg("anglerfish_spawn_egg", ModEntities.ANGLERFISH, 0x1B2029, 0x8FF0FF);
    public static final DeferredItem<Item> GIANT_ISOPOD_SPAWN_EGG = spawnEgg("giant_isopod_spawn_egg", ModEntities.GIANT_ISOPOD, 0xA9A3B5, 0x5D566B);
    public static final DeferredItem<Item> GULPER_EEL_SPAWN_EGG = spawnEgg("gulper_eel_spawn_egg", ModEntities.GULPER_EEL, 0x15171D, 0xF0A0D0);
    public static final DeferredItem<Item> VIPERFISH_SPAWN_EGG = spawnEgg("viperfish_spawn_egg", ModEntities.VIPERFISH, 0x1C2733, 0x6FA8FF);
    public static final DeferredItem<Item> GOBLIN_SHARK_SPAWN_EGG = spawnEgg("goblin_shark_spawn_egg", ModEntities.GOBLIN_SHARK, 0xC9A0A0, 0x7C8FA8);
    public static final DeferredItem<Item> BARRELEYE_SPAWN_EGG = spawnEgg("barreleye_spawn_egg", ModEntities.BARRELEYE, 0x2B2F36, 0x6EDC8C);
    public static final DeferredItem<Item> YUMENAMAKO_SPAWN_EGG = spawnEgg("yumenamako_spawn_egg", ModEntities.YUMENAMAKO, 0xB0485A, 0x7DDDC6);
    public static final DeferredItem<Item> FRILLED_SHARK_SPAWN_EGG = spawnEgg("frilled_shark_spawn_egg", ModEntities.FRILLED_SHARK, 0x3E3A36, 0x8A7A6A);
    public static final DeferredItem<Item> GIANT_SQUID_SPAWN_EGG = spawnEgg("giant_squid_spawn_egg", ModEntities.GIANT_SQUID, 0x8A3B2E, 0xD9B8A0);
    public static final DeferredItem<Item> DEEP_SEA_SHRIMP_SPAWN_EGG = spawnEgg("deep_sea_shrimp_spawn_egg", ModEntities.DEEP_SEA_SHRIMP, 0x8E1B1B, 0xE06040);
    public static final DeferredItem<Item> OHARA_SHRIMP_SPAWN_EGG = spawnEgg("ohara_shrimp_spawn_egg", ModEntities.OHARA_SHRIMP, 0xE8C8C0, 0xC07870);
    public static final DeferredItem<Item> VENT_EELPOUT_SPAWN_EGG = spawnEgg("vent_eelpout_spawn_egg", ModEntities.VENT_EELPOUT, 0xE6B8B0, 0x9A6A66);
    public static final DeferredItem<Item> YUNOHANA_CRAB_SPAWN_EGG = spawnEgg("yunohana_crab_spawn_egg", ModEntities.YUNOHANA_CRAB, 0xECE8E0, 0x9A8F84);
    public static final DeferredItem<Item> GOEMON_SQUAT_LOBSTER_SPAWN_EGG = spawnEgg("goemon_squat_lobster_spawn_egg", ModEntities.GOEMON_SQUAT_LOBSTER, 0xE8E0D0, 0xB0A080);
    public static final DeferredItem<Item> SCALY_FOOT_SNAIL_SPAWN_EGG = spawnEgg("scaly_foot_snail_spawn_egg", ModEntities.SCALY_FOOT_SNAIL, 0x202020, 0xB08040);
    public static final DeferredItem<Item> TUBEWORM_SPAWN_EGG = spawnEgg("tubeworm_spawn_egg", ModEntities.TUBEWORM, 0xEEEAE0, 0xC0282C);
    public static final DeferredItem<Item> SATSUMA_TUBEWORM_SPAWN_EGG = spawnEgg("satsuma_tubeworm_spawn_egg", ModEntities.SATSUMA_TUBEWORM, 0xD8C8A8, 0xE06030);
    public static final DeferredItem<Item> SILKY_MEDUSA_SPAWN_EGG = spawnEgg("silky_medusa_spawn_egg", ModEntities.SILKY_MEDUSA, 0xC9D3DE, 0x6FB2FF);
    public static final DeferredItem<Item> ATOLLA_JELLY_SPAWN_EGG = spawnEgg("atolla_jelly_spawn_egg", ModEntities.ATOLLA_JELLY, 0x8C2C34, 0x3D7DFF);
    public static final DeferredItem<Item> HELMET_JELLY_SPAWN_EGG = spawnEgg("helmet_jelly_spawn_egg", ModEntities.HELMET_JELLY, 0x5A1E30, 0x4FE0BF);
    public static final DeferredItem<Item> GIANT_PHANTOM_JELLY_SPAWN_EGG = spawnEgg("giant_phantom_jelly_spawn_egg", ModEntities.GIANT_PHANTOM_JELLY, 0x4A121C, 0x9A3040);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("abyssia", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.abyssia"))
            .icon(() -> ModBlocks.ABYSSAL_BLOOM.get().asItem().getDefaultInstance())
            .displayItems((params, output) -> TAB_ITEMS.forEach(item -> output.accept(item.get())))
            .build());

    private ModItems() {}

    public static void register(IEventBus modBus)
    {
        ITEMS.register(modBus);
        ModTools.register(ITEMS, TAB_ITEMS);
        MaterialTools.register(ITEMS, TAB_ITEMS);
        TABS.register(modBus);
    }

    private static DeferredItem<Item> item(String name)
    {
        DeferredItem<Item> item = ITEMS.register(name, () -> new Item(new Item.Properties()));
        TAB_ITEMS.add(item);
        return item;
    }

    private static DeferredItem<Item> item(String name, Rarity rarity)
    {
        DeferredItem<Item> item = ITEMS.register(name, () -> new Item(new Item.Properties().rarity(rarity)));
        TAB_ITEMS.add(item);
        return item;
    }

    private static DeferredItem<Item> sourcedItem(String name)
    {
        DeferredItem<Item> item = ITEMS.register(name, () -> new MaterialItem(new Item.Properties(), 0, true));
        TAB_ITEMS.add(item);
        return item;
    }

    static DeferredItem<Item> spawnEgg(String name, DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type, int background, int highlight)
    {
        DeferredItem<Item> item = ITEMS.register(name, () -> new DeferredSpawnEggItem(type, background, highlight, new Item.Properties()));
        TAB_ITEMS.add(item);
        return item;
    }

    /** Items registered elsewhere (ModPlants) that belong in the Abyssia tab. */
    static <T extends Item> DeferredItem<T> tabItem(String name, Supplier<T> factory)
    {
        DeferredItem<T> item = ITEMS.register(name, factory);
        TAB_ITEMS.add(item);
        return item;
    }

    static void blockItem(String name, DeferredBlock<? extends Block> block)
    {
        TAB_ITEMS.add(ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
    }

    /** Doors: the item places both halves. */
    static void doubleHighBlockItem(String name, DeferredBlock<? extends Block> block)
    {
        TAB_ITEMS.add(ITEMS.register(name, () -> new DoubleHighBlockItem(block.get(), new Item.Properties())));
    }
}
