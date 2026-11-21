package com.abyssia.registry;

import com.abyssia.Abyssia;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * GNT01: materials dropped by the giant wild animals, and the Lumen Lantern built from the two sheared ones.
 * Assets, loot, recipes and research are written by tools/gnt01_assets.py (run by gen_deep_assets.py).
 */
public final class GiantMaterials
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Abyssia.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);

    public static final RegistryObject<Item> LANCE_JAW = ITEMS.register("lance_jaw", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> LUMEN_FILAMENT = ITEMS.register("lumen_filament", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> LANTERN_BALEEN = ITEMS.register("lantern_baleen", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ECHO_ORGAN = ITEMS.register("echo_organ", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SLATEBACK_OIL = ITEMS.register("slateback_oil", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> RASP_RING = ITEMS.register("rasp_ring", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SHROUD_MEMBRANE = ITEMS.register("shroud_membrane", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> GLASS_SPINE = ITEMS.register("glass_spine", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CREST_SPINE = ITEMS.register("crest_spine", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> UMBRAL_GLAND = ITEMS.register("umbral_gland", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    /** Full light, glass-like; a full cube, so it simply replaces the water when placed under the sea. */
    public static final RegistryObject<Block> LUMEN_LANTERN = BLOCKS.register("lumen_lantern", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW).strength(0.5f).sound(SoundType.GLASS).lightLevel(s -> 15)));
    public static final RegistryObject<BlockItem> LUMEN_LANTERN_ITEM = ITEMS.register("lumen_lantern", () -> new BlockItem(LUMEN_LANTERN.get(), new Item.Properties()));

    private GiantMaterials() {}

    public static void register(IEventBus bus)
    {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        AbyssiaTab.MATERIALS.items().add(LANCE_JAW);
        AbyssiaTab.MATERIALS.items().add(LUMEN_FILAMENT);
        AbyssiaTab.MATERIALS.items().add(LANTERN_BALEEN);
        AbyssiaTab.MATERIALS.items().add(ECHO_ORGAN);
        AbyssiaTab.MATERIALS.items().add(SLATEBACK_OIL);
        AbyssiaTab.MATERIALS.items().add(RASP_RING);
        AbyssiaTab.MATERIALS.items().add(SHROUD_MEMBRANE);
        AbyssiaTab.MATERIALS.items().add(GLASS_SPINE);
        AbyssiaTab.MATERIALS.items().add(CREST_SPINE);
        AbyssiaTab.MATERIALS.items().add(UMBRAL_GLAND);
        AbyssiaTab.BUILDING.items().add(LUMEN_LANTERN_ITEM);
    }
}
