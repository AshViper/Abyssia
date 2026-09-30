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
    public static final RegistryObject<Item> THERMAL_CRYSTAL_SHARD = item("thermal_crystal_shard");
    public static final RegistryObject<Item> ABYSSAL_CRYSTAL_SHARD = item("abyssal_crystal_shard");

    // Rare metals (tools/gen_deep_assets.py RARE_METALS): tooltip names the source (item.abyssia.<id>.source)
    public static final RegistryObject<Item> RAW_PLATINUM = sourcedItem("raw_platinum");
    public static final RegistryObject<Item> RAW_TELLURIUM = sourcedItem("raw_tellurium");
    public static final RegistryObject<Item> RAW_MOLYBDENUM = sourcedItem("raw_molybdenum");
    public static final RegistryObject<Item> RAW_VANADIUM = sourcedItem("raw_vanadium");
    public static final RegistryObject<Item> RAW_TUNGSTEN = sourcedItem("raw_tungsten");
    public static final RegistryObject<Item> RAW_YTTRIUM = sourcedItem("raw_yttrium");
    public static final RegistryObject<Item> PLATINUM_INGOT = sourcedItem("platinum_ingot");
    public static final RegistryObject<Item> TELLURIUM_INGOT = sourcedItem("tellurium_ingot");
    public static final RegistryObject<Item> MOLYBDENUM_INGOT = sourcedItem("molybdenum_ingot");
    public static final RegistryObject<Item> VANADIUM_INGOT = sourcedItem("vanadium_ingot");
    public static final RegistryObject<Item> TUNGSTEN_INGOT = sourcedItem("tungsten_ingot");
    public static final RegistryObject<Item> YTTRIUM_INGOT = sourcedItem("yttrium_ingot");
    public static final RegistryObject<Item> ABYSSAL_ALLOY_INGOT = sourcedItem("abyssal_alloy_ingot");

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

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("abyssia", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.abyssia"))
            .icon(() -> ModBlocks.ABYSSAL_BLOOM.get().asItem().getDefaultInstance())
            .displayItems((params, output) -> TAB_ITEMS.forEach(item -> output.accept(item.get())))
            .build());

    private ModItems() {}

    public static void register(IEventBus modBus)
    {
        ITEMS.register(modBus);
        ModTools.register(ITEMS, TAB_ITEMS);
        TABS.register(modBus);
    }

    private static RegistryObject<Item> item(String name)
    {
        RegistryObject<Item> item = ITEMS.register(name, () -> new Item(new Item.Properties()));
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
