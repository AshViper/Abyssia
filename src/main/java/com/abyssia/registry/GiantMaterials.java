package com.abyssia.registry;

import com.abyssia.Abyssia;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * GNT01: materials dropped by the giant wild animals, and the Lumen Lantern built from the two sheared ones.
 * Assets, loot, recipes and research are written by tools/gnt01_assets.py (run by gen_deep_assets.py).
 */
public final class GiantMaterials
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Abyssia.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);

    public static final DeferredItem<Item> LANCE_JAW = ITEMS.registerSimpleItem("lance_jaw", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> LUMEN_FILAMENT = ITEMS.registerSimpleItem("lumen_filament", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> LANTERN_BALEEN = ITEMS.registerSimpleItem("lantern_baleen", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ECHO_ORGAN = ITEMS.registerSimpleItem("echo_organ", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> SLATEBACK_OIL = ITEMS.registerSimpleItem("slateback_oil", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> RASP_RING = ITEMS.registerSimpleItem("rasp_ring", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> SHROUD_MEMBRANE = ITEMS.registerSimpleItem("shroud_membrane", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> GLASS_SPINE = ITEMS.registerSimpleItem("glass_spine", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> CREST_SPINE = ITEMS.registerSimpleItem("crest_spine", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> UMBRAL_GLAND = ITEMS.registerSimpleItem("umbral_gland", new Item.Properties().rarity(Rarity.RARE));

    /** Full light, glass-like; a full cube, so it simply replaces the water when placed under the sea. */
    public static final DeferredBlock<Block> LUMEN_LANTERN = BLOCKS.registerSimpleBlock("lumen_lantern", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW).strength(0.5f).sound(SoundType.GLASS).lightLevel(s -> 15));
    public static final DeferredItem<BlockItem> LUMEN_LANTERN_ITEM = ITEMS.registerSimpleBlockItem("lumen_lantern", LUMEN_LANTERN);

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
