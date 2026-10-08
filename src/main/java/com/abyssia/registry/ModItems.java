package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.item.MaterialItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import com.abyssia.item.MaterialTools;
import com.abyssia.item.ModTools;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Items (block items and deep-sea materials) and the Abyssia creative tab that lists them all. */
public final class ModItems
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Abyssia.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Abyssia.MODID);
    private static final List<RegistryObject<? extends Item>> TAB_ITEMS = new ArrayList<>();

    // Deep-sea metals and minerals
    public static final RegistryObject<Item> RAW_MANGANESE = item("raw_manganese");
    public static final RegistryObject<Item> RAW_COBALT = item("raw_cobalt");
    public static final RegistryObject<Item> RAW_NICKEL = item("raw_nickel");
    public static final RegistryObject<Item> MANGANESE_INGOT = item("manganese_ingot");
    public static final RegistryObject<Item> COBALT_INGOT = item("cobalt_ingot");
    public static final RegistryObject<Item> NICKEL_INGOT = item("nickel_ingot");
    public static final RegistryObject<Item> SULFUR = item("sulfur");
    public static final RegistryObject<Item> CRUST_POWDER = item("crust_powder");
    public static final RegistryObject<Item> THERMAL_CRYSTAL_SHARD = item("thermal_crystal_shard");
    public static final RegistryObject<Item> ABYSSAL_CRYSTAL_SHARD = item("abyssal_crystal_shard");

    // Rare metals (tools/gen_deep_assets.py RARE_METALS): tooltip names the source (item.abyssia.<id>.source)
    public static final RegistryObject<Item> RAW_PLATINUM = sourcedItem("raw_platinum");
    public static final RegistryObject<Item> RAW_TELLURIUM = sourcedItem("raw_tellurium");
    public static final RegistryObject<Item> RAW_MOLYBDENUM = sourcedItem("raw_molybdenum");
    public static final RegistryObject<Item> RAW_VANADIUM = sourcedItem("raw_vanadium");
    public static final RegistryObject<Item> RAW_TUNGSTEN = sourcedItem("raw_tungsten");
    public static final RegistryObject<Item> RAW_YTTRIUM = sourcedItem("raw_yttrium");
    public static final RegistryObject<Item> RAW_TITANIUM = sourcedItem("raw_titanium");
    public static final RegistryObject<Item> RAW_LEAD = sourcedItem("raw_lead");
    public static final RegistryObject<Item> RAW_ZINC = sourcedItem("raw_zinc");
    public static final RegistryObject<Item> RAW_IRIDIUM = sourcedItem("raw_iridium");
    public static final RegistryObject<Item> RAW_URANIUM = sourcedItem("raw_uranium");
    public static final RegistryObject<Item> RAW_NEODYMIUM = sourcedItem("raw_neodymium");
    public static final RegistryObject<Item> RAW_THORIUM = sourcedItem("raw_thorium");
    public static final RegistryObject<Item> PLATINUM_INGOT = sourcedItem("platinum_ingot");
    public static final RegistryObject<Item> TELLURIUM_INGOT = sourcedItem("tellurium_ingot");
    public static final RegistryObject<Item> MOLYBDENUM_INGOT = sourcedItem("molybdenum_ingot");
    public static final RegistryObject<Item> VANADIUM_INGOT = sourcedItem("vanadium_ingot");
    public static final RegistryObject<Item> TUNGSTEN_INGOT = sourcedItem("tungsten_ingot");
    public static final RegistryObject<Item> YTTRIUM_INGOT = sourcedItem("yttrium_ingot");
    public static final RegistryObject<Item> TITANIUM_INGOT = sourcedItem("titanium_ingot");
    public static final RegistryObject<Item> LEAD_INGOT = sourcedItem("lead_ingot");
    public static final RegistryObject<Item> ZINC_INGOT = sourcedItem("zinc_ingot");
    public static final RegistryObject<Item> IRIDIUM_INGOT = sourcedItem("iridium_ingot");
    public static final RegistryObject<Item> URANIUM_INGOT = sourcedItem("uranium_ingot");
    public static final RegistryObject<Item> NEODYMIUM_INGOT = sourcedItem("neodymium_ingot");
    public static final RegistryObject<Item> THORIUM_INGOT = sourcedItem("thorium_ingot");
    public static final RegistryObject<Item> ABYSSAL_ALLOY_INGOT = sourcedItem("abyssal_alloy_ingot");

    // Material processing system (docs/material-system.md, tools/material_spec.json): plain items, rarity from the spec.
    // Tools and armor of the system are in com.abyssia.item.MaterialTools.
    // TODO(phase2 machines): crusher / refinery_furnace / alloy_furnace / crystal_processor / high_temp_furnace /
    // energy_device / mining_machine (spec machines_phase2) would register their blocks + menus here and automate
    // the same spec recipes; machine_frame is already obtainable.
    // powders and concentrates (crushed with the crushing hammer)
    public static final RegistryObject<Item> IRON_POWDER = item("iron_powder", Rarity.COMMON);
    public static final RegistryObject<Item> COBALT_POWDER = item("cobalt_powder", Rarity.COMMON);
    public static final RegistryObject<Item> NICKEL_POWDER = item("nickel_powder", Rarity.COMMON);
    public static final RegistryObject<Item> MANGANESE_POWDER = item("manganese_powder", Rarity.COMMON);
    public static final RegistryObject<Item> VANADIUM_POWDER = item("vanadium_powder", Rarity.UNCOMMON);
    public static final RegistryObject<Item> TUNGSTEN_POWDER = item("tungsten_powder", Rarity.UNCOMMON);
    public static final RegistryObject<Item> TELLURIUM_POWDER = item("tellurium_powder", Rarity.UNCOMMON);
    public static final RegistryObject<Item> YTTRIUM_POWDER = item("yttrium_powder", Rarity.UNCOMMON);
    public static final RegistryObject<Item> COBALT_CONCENTRATE = item("cobalt_concentrate", Rarity.COMMON);
    public static final RegistryObject<Item> MANGANESE_CONCENTRATE = item("manganese_concentrate", Rarity.COMMON);
    public static final RegistryObject<Item> NICKEL_CONCENTRATE = item("nickel_concentrate", Rarity.COMMON);
    // basic iron/copper parts
    public static final RegistryObject<Item> IRON_PLATE = item("iron_plate", Rarity.COMMON);
    public static final RegistryObject<Item> IRON_ROD = item("iron_rod", Rarity.COMMON);
    public static final RegistryObject<Item> IRON_GEAR = item("iron_gear", Rarity.COMMON);
    public static final RegistryObject<Item> COPPER_WIRE = item("copper_wire", Rarity.COMMON);
    // alloys
    public static final RegistryObject<Item> CORROSION_ALLOY_INGOT = item("corrosion_alloy_ingot", Rarity.UNCOMMON);
    public static final RegistryObject<Item> HIGH_STRENGTH_ALLOY_INGOT = item("high_strength_alloy_ingot", Rarity.UNCOMMON);
    public static final RegistryObject<Item> HEAT_RESISTANT_ALLOY_INGOT = item("heat_resistant_alloy_ingot", Rarity.UNCOMMON);
    public static final RegistryObject<Item> TUNGSTEN_ALLOY_INGOT = item("tungsten_alloy_ingot", Rarity.RARE);
    public static final RegistryObject<Item> CONDUCTIVE_ALLOY_INGOT = item("conductive_alloy_ingot", Rarity.RARE);
    public static final RegistryObject<Item> THERMAL_ALLOY_INGOT = item("thermal_alloy_ingot", Rarity.RARE);
    // plant-based materials
    public static final RegistryObject<Item> REINFORCED_FIBER = item("reinforced_fiber", Rarity.COMMON);
    public static final RegistryObject<Item> REINFORCED_CABLE = item("reinforced_cable", Rarity.UNCOMMON);
    public static final RegistryObject<Item> MARINE_RESIN = item("marine_resin", Rarity.UNCOMMON);
    public static final RegistryObject<Item> ABYSSAL_COMPOSITE = item("abyssal_composite", Rarity.RARE);
    // thermal materials
    public static final RegistryObject<Item> THERMAL_CORE = item("thermal_core", Rarity.RARE);
    public static final RegistryObject<Item> THERMAL_REAGENT = item("thermal_reagent", Rarity.RARE);
    // crystal and energy parts
    public static final RegistryObject<Item> LUMINOUS_CRYSTAL = item("luminous_crystal", Rarity.RARE);
    public static final RegistryObject<Item> CRYSTAL_CORE = item("crystal_core", Rarity.RARE);
    public static final RegistryObject<Item> ABYSSAL_ENERGY_CELL = item("abyssal_energy_cell", Rarity.RARE);
    public static final RegistryObject<Item> ADVANCED_LUMEN_CELL = item("advanced_lumen_cell", Rarity.RARE);
    public static final RegistryObject<Item> ABYSSAL_LIGHT_CORE = item("abyssal_light_core", Rarity.EPIC);
    public static final RegistryObject<Item> ADVANCED_ENERGY_CELL = item("advanced_energy_cell", Rarity.EPIC);
    public static final RegistryObject<Item> ABYSSAL_POWER_CORE = item("abyssal_power_core", Rarity.EPIC);
    // shared components
    public static final RegistryObject<Item> HARDENED_TIP = item("hardened_tip", Rarity.UNCOMMON);
    public static final RegistryObject<Item> TUNGSTEN_TIP = item("tungsten_tip", Rarity.RARE);
    public static final RegistryObject<Item> DRILL_HEAD = item("drill_head", Rarity.RARE);
    public static final RegistryObject<Item> PRESSURE_VALVE = item("pressure_valve", Rarity.UNCOMMON);
    public static final RegistryObject<Item> PRESSURE_SHELL = item("pressure_shell", Rarity.EPIC);
    public static final RegistryObject<Item> THERMAL_COMPONENT = item("thermal_component", Rarity.RARE);
    public static final RegistryObject<Item> CONDUCTIVE_COMPONENT = item("conductive_component", Rarity.RARE);
    public static final RegistryObject<Item> MACHINE_FRAME = item("machine_frame", Rarity.UNCOMMON);

    // Fauna drops (F01): fish/eel/shark flesh is food (raw gives a chance effect, cooked via smelting/smoking/campfire),
    // jelly tentacles are plain crafting materials. Loot tables come from tools/fauna/<id>.py INFO["loot"].
    public static final RegistryObject<Item> ABYSSAL_FISH_FILLET = food("abyssal_fish_fillet", 2, 0.3f, Rarity.COMMON);
    public static final RegistryObject<Item> COOKED_ABYSSAL_FISH = food("cooked_abyssal_fish", 6, 9.6f, Rarity.COMMON,
            buff(ModMobEffects.ABYSSAL_CURRENT, 120, 0));
    public static final RegistryObject<Item> VIPER_FLESH = food("viper_flesh", 2, 0.2f, Rarity.UNCOMMON,
            new FoodEffect(MobEffects.DARKNESS, 10 * 20, 0.3f));
    public static final RegistryObject<Item> COOKED_VIPER_FLESH = food("cooked_viper_flesh", 6, 9.6f, Rarity.UNCOMMON);
    public static final RegistryObject<Item> SHARK_FLESH = food("shark_flesh", 3, 0.3f, Rarity.UNCOMMON,
            new FoodEffect(MobEffects.POISON, 10 * 20, 0.3f));
    public static final RegistryObject<Item> COOKED_SHARK_FLESH = food("cooked_shark_flesh", 8, 12.8f, Rarity.UNCOMMON);
    public static final RegistryObject<Item> EELPOUT_FLESH = food("eelpout_flesh", 2, 0.3f, Rarity.COMMON,
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.3f));
    public static final RegistryObject<Item> COOKED_EELPOUT_FLESH = food("cooked_eelpout_flesh", 6, 9.6f, Rarity.COMMON);
    public static final RegistryObject<Item> BLOBFISH_FLESH = food("blobfish_flesh", 2, 0.4f, Rarity.COMMON);
    public static final RegistryObject<Item> COOKED_BLOBFISH = food("cooked_blobfish", 5, 8.0f, Rarity.COMMON,
            new FoodEffect(MobEffects.REGENERATION, 10 * 20, 0.2f));
    public static final RegistryObject<Item> ANGLER_FLESH = food("angler_flesh", 2, 0.3f, Rarity.UNCOMMON,
            new FoodEffect(MobEffects.NIGHT_VISION, 15 * 20, 0.5f));
    public static final RegistryObject<Item> COOKED_ANGLER_FLESH = food("cooked_angler_flesh", 6, 9.6f, Rarity.UNCOMMON);
    // FS01 edible fish: each species drops its own raw fillet; no effects. Saturation = 2 * nutrition * modifier.
    public static final RegistryObject<Item> RAW_ORANGE_ROUGHY = food("raw_orange_roughy", 3, 2.4f, Rarity.COMMON);
    public static final RegistryObject<Item> COOKED_ORANGE_ROUGHY = food("cooked_orange_roughy", 7, 12.6f, Rarity.COMMON);
    public static final RegistryObject<Item> RAW_SABLEFISH = food("raw_sablefish", 3, 3.0f, Rarity.COMMON);
    public static final RegistryObject<Item> COOKED_SABLEFISH = food("cooked_sablefish", 8, 16.0f, Rarity.COMMON);
    public static final RegistryObject<Item> RAW_PATAGONIAN_TOOTHFISH = food("raw_patagonian_toothfish", 4, 4.0f, Rarity.COMMON);
    public static final RegistryObject<Item> COOKED_PATAGONIAN_TOOTHFISH = food("cooked_patagonian_toothfish", 9, 19.8f, Rarity.COMMON);
    public static final RegistryObject<Item> RAW_BLACK_SCABBARDFISH = food("raw_black_scabbardfish", 3, 2.4f, Rarity.COMMON);
    public static final RegistryObject<Item> COOKED_BLACK_SCABBARDFISH = food("cooked_black_scabbardfish", 8, 14.4f, Rarity.COMMON);
    public static final RegistryObject<Item> RAW_GREENLAND_HALIBUT = food("raw_greenland_halibut", 4, 4.0f, Rarity.COMMON);
    public static final RegistryObject<Item> COOKED_GREENLAND_HALIBUT = food("cooked_greenland_halibut", 9, 19.8f, Rarity.COMMON);
    public static final RegistryObject<Item> RAW_ALFONSINO = food("raw_alfonsino", 3, 2.4f, Rarity.COMMON);
    public static final RegistryObject<Item> COOKED_ALFONSINO = food("cooked_alfonsino", 7, 12.6f, Rarity.COMMON);
    public static final RegistryObject<Item> RAW_BLUE_LING = food("raw_blue_ling", 3, 2.4f, Rarity.COMMON);
    public static final RegistryObject<Item> COOKED_BLUE_LING = food("cooked_blue_ling", 8, 16.0f, Rarity.COMMON);
    public static final RegistryObject<Item> RAW_DEEPWATER_REDFISH = food("raw_deepwater_redfish", 3, 2.4f, Rarity.COMMON);
    public static final RegistryObject<Item> COOKED_DEEPWATER_REDFISH = food("cooked_deepwater_redfish", 7, 12.6f, Rarity.COMMON);
    public static final RegistryObject<Item> JELLY_TENTACLE = item("jelly_tentacle", Rarity.COMMON);
    public static final RegistryObject<Item> ATOLLA_TENTACLE = item("atolla_tentacle", Rarity.UNCOMMON);
    public static final RegistryObject<Item> PHANTOM_TENTACLE = item("phantom_tentacle", Rarity.UNCOMMON);
    public static final RegistryObject<Item> DEEPSTARIA_TENTACLE = item("deepstaria_tentacle", Rarity.RARE);

    // FD01 deep-sea cooking: three plant ingredients (eaten raw) + 30 dishes. Table mirrors FOODS in tools/gen_deep_assets.py.
    public static final RegistryObject<Item> MUSHROOM_CAP = foodMod("mushroom_cap", 1, 0.3f, Rarity.COMMON, false);
    public static final RegistryObject<Item> GOURD_FLESH = foodMod("gourd_flesh", 2, 0.4f, Rarity.COMMON, false);
    public static final RegistryObject<Item> KELP_LEAF = foodMod("kelp_leaf", 1, 0.2f, Rarity.COMMON, false);
    public static final RegistryObject<Item> FISH_MUSHROOM_SKEWER = foodMod("fish_mushroom_skewer", 6, 0.8f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.NIGHT_VISION, 10 * 20, 0.1f));
    public static final RegistryObject<Item> GOURD_FISH_SKEWER = foodMod("gourd_fish_skewer", 7, 0.9f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.1f));
    public static final RegistryObject<Item> KELP_FISH_SKEWER = foodMod("kelp_fish_skewer", 5, 0.7f, Rarity.COMMON, false);
    public static final RegistryObject<Item> MUSHROOM_STEW = foodMod("mushroom_stew", 7, 0.8f, Rarity.COMMON, true,
            new FoodEffect(MobEffects.NIGHT_VISION, 10 * 20, 0.15f));
    public static final RegistryObject<Item> GOURD_SOUP = foodMod("gourd_soup", 6, 0.8f, Rarity.COMMON, true,
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.15f));
    public static final RegistryObject<Item> KELP_SOUP = foodMod("kelp_soup", 5, 0.7f, Rarity.COMMON, true);
    public static final RegistryObject<Item> FISH_SOUP = foodMod("fish_soup", 8, 1.0f, Rarity.COMMON, true,
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.15f),
            buff(ModMobEffects.ABYSSAL_CURRENT, 180, 0));
    public static final RegistryObject<Item> MUSHROOM_FISH_STEW = foodMod("mushroom_fish_stew", 9, 1.0f, Rarity.COMMON, true,
            new FoodEffect(MobEffects.REGENERATION, 10 * 20, 0.05f));
    public static final RegistryObject<Item> GOURD_FISH_STEW = foodMod("gourd_fish_stew", 9, 1.0f, Rarity.COMMON, true,
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.2f));
    public static final RegistryObject<Item> KELP_FISH_STEW = foodMod("kelp_fish_stew", 8, 0.9f, Rarity.COMMON, true,
            buff(ModMobEffects.DEEP_SIGHT, 150, 0));
    public static final RegistryObject<Item> MUSHROOM_PIE = foodMod("mushroom_pie", 8, 0.8f, Rarity.COMMON, false);
    public static final RegistryObject<Item> GOURD_PIE = foodMod("gourd_pie", 8, 0.9f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.NIGHT_VISION, 10 * 20, 0.1f));
    public static final RegistryObject<Item> FISH_PIE = foodMod("fish_pie", 10, 1.0f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.REGENERATION, 10 * 20, 0.05f));
    public static final RegistryObject<Item> MUSHROOM_FISH_PIE = foodMod("mushroom_fish_pie", 10, 1.1f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.NIGHT_VISION, 10 * 20, 0.15f));
    public static final RegistryObject<Item> GOURD_FISH_PIE = foodMod("gourd_fish_pie", 10, 1.1f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.15f));
    public static final RegistryObject<Item> KELP_FISH_PIE = foodMod("kelp_fish_pie", 9, 1.0f, Rarity.COMMON, false);
    public static final RegistryObject<Item> PRESERVED_FISH = foodMod("preserved_fish", 7, 1.0f, Rarity.COMMON, false);
    public static final RegistryObject<Item> SMOKED_MUSHROOM = foodMod("smoked_mushroom", 4, 0.7f, Rarity.COMMON, false);
    public static final RegistryObject<Item> SMOKED_GOURD = foodMod("smoked_gourd", 5, 0.8f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.05f));
    public static final RegistryObject<Item> GRILLED_KELP = foodMod("grilled_kelp", 4, 0.6f, Rarity.COMMON, false);
    public static final RegistryObject<Item> MUSHROOM_FISH_GRILL = foodMod("mushroom_fish_grill", 8, 0.9f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.NIGHT_VISION, 10 * 20, 0.1f),
            buff(ModMobEffects.ABYSSAL_CURRENT, 60, 1));
    public static final RegistryObject<Item> GOURD_FISH_GRILL = foodMod("gourd_fish_grill", 9, 1.0f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.1f));
    public static final RegistryObject<Item> KELP_FISH_GRILL = foodMod("kelp_fish_grill", 7, 0.8f, Rarity.COMMON, false);
    public static final RegistryObject<Item> JELLYFISH_SKEWER = foodMod("jellyfish_skewer", 6, 0.8f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.1f),
            buff(ModMobEffects.DEEP_SIGHT, 120, 0));
    public static final RegistryObject<Item> JELLYFISH_STEW = foodMod("jellyfish_stew", 7, 0.9f, Rarity.COMMON, true,
            new FoodEffect(MobEffects.REGENERATION, 10 * 20, 0.05f),
            buff(ModMobEffects.DEEP_SIGHT, 120, 0));
    public static final RegistryObject<Item> ABYSSAL_SURVIVAL_RATION = foodMod("abyssal_survival_ration", 10, 1.2f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.NIGHT_VISION, 10 * 20, 0.1f),
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.1f),
            buff(ModMobEffects.DEEP_SIGHT, 90, 0));
    public static final RegistryObject<Item> THERMAL_RATION = foodMod("thermal_ration", 9, 1.1f, Rarity.COMMON, false,
            new FoodEffect(MobEffects.REGENERATION, 10 * 20, 0.05f));
    public static final RegistryObject<Item> MUSHROOM_SALAD = foodMod("mushroom_salad", 5, 0.7f, Rarity.COMMON, true);
    public static final RegistryObject<Item> GOURD_KELP_SALAD = foodMod("gourd_kelp_salad", 5, 0.7f, Rarity.COMMON, true,
            new FoodEffect(MobEffects.WATER_BREATHING, 15 * 20, 0.05f));
    public static final RegistryObject<Item> ABYSSAL_VEGETABLE_STEW = foodMod("abyssal_vegetable_stew", 7, 0.9f, Rarity.COMMON, true,
            new FoodEffect(MobEffects.NIGHT_VISION, 10 * 20, 0.1f));

    // Spawn eggs (colours match tools/fauna/<species>.py INFO["egg"])
    public static final RegistryObject<Item> ANGLERFISH_SPAWN_EGG = spawnEgg("anglerfish_spawn_egg", ModEntities.ANGLERFISH, 0x1B2029, 0x8FF0FF);
    public static final RegistryObject<Item> GIANT_ISOPOD_SPAWN_EGG = spawnEgg("giant_isopod_spawn_egg", ModEntities.GIANT_ISOPOD, 0xA9A3B5, 0x5D566B);
    public static final RegistryObject<Item> GULPER_EEL_SPAWN_EGG = spawnEgg("gulper_eel_spawn_egg", ModEntities.GULPER_EEL, 0x15171D, 0xF0A0D0);
    public static final RegistryObject<Item> VIPERFISH_SPAWN_EGG = spawnEgg("viperfish_spawn_egg", ModEntities.VIPERFISH, 0x1C2733, 0x6FA8FF);
    public static final RegistryObject<Item> GOBLIN_SHARK_SPAWN_EGG = spawnEgg("goblin_shark_spawn_egg", ModEntities.GOBLIN_SHARK, 0xC9A0A0, 0x7C8FA8);
    public static final RegistryObject<Item> BARRELEYE_SPAWN_EGG = spawnEgg("barreleye_spawn_egg", ModEntities.BARRELEYE, 0x2B2F36, 0x6EDC8C);
    public static final RegistryObject<Item> YUMENAMAKO_SPAWN_EGG = spawnEgg("yumenamako_spawn_egg", ModEntities.YUMENAMAKO, 0xB0485A, 0x7DDDC6);
    public static final RegistryObject<Item> FRILLED_SHARK_SPAWN_EGG = spawnEgg("frilled_shark_spawn_egg", ModEntities.FRILLED_SHARK, 0x3E3A36, 0x8A7A6A);
    public static final RegistryObject<Item> GIANT_SQUID_SPAWN_EGG = spawnEgg("giant_squid_spawn_egg", ModEntities.GIANT_SQUID, 0x8A3B2E, 0xD9B8A0);
    public static final RegistryObject<Item> DEEP_SEA_SHRIMP_SPAWN_EGG = spawnEgg("deep_sea_shrimp_spawn_egg", ModEntities.DEEP_SEA_SHRIMP, 0x8E1B1B, 0xE06040);
    public static final RegistryObject<Item> OHARA_SHRIMP_SPAWN_EGG = spawnEgg("ohara_shrimp_spawn_egg", ModEntities.OHARA_SHRIMP, 0xE8C8C0, 0xC07870);
    public static final RegistryObject<Item> VENT_EELPOUT_SPAWN_EGG = spawnEgg("vent_eelpout_spawn_egg", ModEntities.VENT_EELPOUT, 0xE6B8B0, 0x9A6A66);
    public static final RegistryObject<Item> YUNOHANA_CRAB_SPAWN_EGG = spawnEgg("yunohana_crab_spawn_egg", ModEntities.YUNOHANA_CRAB, 0xECE8E0, 0x9A8F84);
    public static final RegistryObject<Item> GOEMON_SQUAT_LOBSTER_SPAWN_EGG = spawnEgg("goemon_squat_lobster_spawn_egg", ModEntities.GOEMON_SQUAT_LOBSTER, 0xE8E0D0, 0xB0A080);
    public static final RegistryObject<Item> SCALY_FOOT_SNAIL_SPAWN_EGG = spawnEgg("scaly_foot_snail_spawn_egg", ModEntities.SCALY_FOOT_SNAIL, 0x202020, 0xB08040);
    public static final RegistryObject<Item> TUBEWORM_SPAWN_EGG = spawnEgg("tubeworm_spawn_egg", ModEntities.TUBEWORM, 0xEEEAE0, 0xC0282C);
    public static final RegistryObject<Item> SATSUMA_TUBEWORM_SPAWN_EGG = spawnEgg("satsuma_tubeworm_spawn_egg", ModEntities.SATSUMA_TUBEWORM, 0xD8C8A8, 0xE06030);
    public static final RegistryObject<Item> SILKY_MEDUSA_SPAWN_EGG = spawnEgg("silky_medusa_spawn_egg", ModEntities.SILKY_MEDUSA, 0xC9D3DE, 0x6FB2FF);
    public static final RegistryObject<Item> ATOLLA_JELLY_SPAWN_EGG = spawnEgg("atolla_jelly_spawn_egg", ModEntities.ATOLLA_JELLY, 0x8C2C34, 0x3D7DFF);
    public static final RegistryObject<Item> HELMET_JELLY_SPAWN_EGG = spawnEgg("helmet_jelly_spawn_egg", ModEntities.HELMET_JELLY, 0x5A1E30, 0x4FE0BF);
    public static final RegistryObject<Item> GIANT_PHANTOM_JELLY_SPAWN_EGG = spawnEgg("giant_phantom_jelly_spawn_egg", ModEntities.GIANT_PHANTOM_JELLY, 0x4A121C, 0x9A3040);

    // OL01 cultivated oil kelp: the sac is smelted into bio_oil; the seed plants the (item-less) oil_kelp block
    public static final RegistryObject<Item> OIL_SAC = item("oil_sac");
    public static final RegistryObject<Item> OIL_KELP_SEED = tabItem("oil_kelp_seed", () -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.OIL_KELP.get(), new Item.Properties()));

    // MP01 deep sea map / abyss chart (filled maps are vanilla filled_map stacks)
    public static final RegistryObject<Item> DEEP_SEA_MAP = tabItem("deep_sea_map", () -> new com.abyssia.map.DeepSeaMapItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> ABYSS_CHART = tabItem("abyss_chart", () -> new com.abyssia.map.AbyssChartItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("abyssia", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.abyssia"))
            .icon(() -> ModBlocks.ABYSSAL_BLOOM.get().asItem().getDefaultInstance())
            .displayItems((params, output) ->
            {
                TAB_ITEMS.forEach(item -> output.accept(item.get()));
                com.abyssia.item.electric.ElectricTools.addFullVariants(output);
            })
            .build());

    private ModItems() {}

    public static void register(IEventBus modBus)
    {
        ITEMS.register(modBus);
        ModTools.register(ITEMS, TAB_ITEMS);
        MaterialTools.register(ITEMS, TAB_ITEMS);
        com.abyssia.item.DivingBreathing.register();
        com.abyssia.item.electric.ElectricTools.register(ITEMS, TAB_ITEMS);
        com.abyssia.item.scanner.LidarScannerItem.register(ITEMS, TAB_ITEMS);
        com.abyssia.item.EntryDivingGear.register(ITEMS, TAB_ITEMS);
        ModIndustry.registerItems(ITEMS, TAB_ITEMS);
        ModHabitat.registerItems(ITEMS, TAB_ITEMS);
        ModFurniture.registerItems(ITEMS, TAB_ITEMS);
        com.abyssia.vehicle.VehicleContent.registerItems(ITEMS, TAB_ITEMS);
        TABS.register(modBus);
    }

    private static RegistryObject<Item> item(String name)
    {
        RegistryObject<Item> item = ITEMS.register(name, () -> new Item(new Item.Properties()));
        TAB_ITEMS.add(item);
        return item;
    }

    private static RegistryObject<Item> item(String name, Rarity rarity)
    {
        RegistryObject<Item> item = ITEMS.register(name, () -> new Item(new Item.Properties().rarity(rarity)));
        TAB_ITEMS.add(item);
        return item;
    }

    /** Extra effect of a food: duration in ticks, probability 0..1. */
    private record FoodEffect(Supplier<? extends MobEffect> effect, int ticks, float chance, int amplifier)
    {
        FoodEffect(MobEffect effect, int ticks, float chance) { this(() -> effect, ticks, chance, 0); }
    }

    /** EN01 buff: always applied, amplifier 0 = level I. */
    private static FoodEffect buff(Supplier<? extends MobEffect> effect, int seconds, int amplifier)
    {
        return new FoodEffect(effect, seconds * 20, 1.0f, amplifier);
    }

    /** Food item: saturation is the absolute value (vanilla stores nutrition * mod * 2), standard eat speed, not meat. */
    private static RegistryObject<Item> food(String name, int nutrition, float saturation, Rarity rarity, FoodEffect... effects)
    {
        RegistryObject<Item> item = ITEMS.register(name, () ->
        {
            FoodProperties.Builder food = new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturation / (2.0f * nutrition));
            for (FoodEffect e : effects)
                food.effect(() -> new MobEffectInstance(e.effect().get(), e.ticks(), e.amplifier()), e.chance());
            return new Item(new Item.Properties().rarity(rarity).food(food.build()));
        });
        TAB_ITEMS.add(item);
        return item;
    }

    /** FD01 dish: saturation is the vanilla saturation modifier; bowl dishes return a bowl and stack to 1. */
    private static RegistryObject<Item> foodMod(String name, int nutrition, float saturationMod, Rarity rarity, boolean bowl, FoodEffect... effects)
    {
        RegistryObject<Item> item = ITEMS.register(name, () ->
        {
            FoodProperties.Builder food = new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturationMod);
            for (FoodEffect e : effects)
                food.effect(() -> new MobEffectInstance(e.effect().get(), e.ticks(), e.amplifier()), e.chance());
            Item.Properties props = new Item.Properties().rarity(rarity).food(food.build());
            return bowl ? new BowlFoodItem(props.stacksTo(1)) : new Item(props);
        });
        TAB_ITEMS.add(item);
        return item;
    }

    private static RegistryObject<Item> sourcedItem(String name)
    {
        RegistryObject<Item> item = ITEMS.register(name, () -> new MaterialItem(new Item.Properties(), 0, true));
        TAB_ITEMS.add(item);
        return item;
    }

    static RegistryObject<Item> spawnEgg(String name, RegistryObject<? extends EntityType<? extends Mob>> type, int background, int highlight)
    {
        RegistryObject<Item> item = ITEMS.register(name, () -> new ForgeSpawnEggItem(type, background, highlight, new Item.Properties()));
        TAB_ITEMS.add(item);
        return item;
    }

    /** Items registered elsewhere (ModPlants) that belong in the Abyssia tab. */
    static <T extends Item> RegistryObject<T> tabItem(String name, Supplier<T> factory)
    {
        RegistryObject<T> item = ITEMS.register(name, factory);
        TAB_ITEMS.add(item);
        return item;
    }

    static void blockItem(String name, RegistryObject<? extends Block> block)
    {
        TAB_ITEMS.add(ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
    }

    /** Doors: the item places both halves. */
    static void doubleHighBlockItem(String name, RegistryObject<? extends Block> block)
    {
        TAB_ITEMS.add(ITEMS.register(name, () -> new DoubleHighBlockItem(block.get(), new Item.Properties())));
    }
}
