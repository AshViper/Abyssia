package com.abyssia.entity.boss;

import com.abyssia.Abyssia;
import com.abyssia.registry.AbyssiaTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * BOSS01: the boss drop materials. Bosses spawn naturally ({@link BossSpawner}); there is no altar or lure any more.
 * Assets and loot are plain JSON (no generator).
 */
public final class BossContent
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Abyssia.MODID);

    public static final DeferredItem<Item> SERPENT_SCALE = ITEMS.registerSimpleItem("abyss_serpent_scale", new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> WYRM_FANG = ITEMS.registerSimpleItem("trench_wyrm_fang", new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> KRAKEN_BEAK = ITEMS.registerSimpleItem("kraken_beak", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> KRAKEN_INK_SAC = ITEMS.registerSimpleItem("kraken_ink_sac", new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> MOTHER_BELL_GEL = ITEMS.registerSimpleItem("mother_bell_gel", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_HEART = ITEMS.registerSimpleItem("abyss_heart", new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> ANGLER_LURE = ITEMS.registerSimpleItem("angler_lure", new Item.Properties().rarity(Rarity.RARE));

    /** Boss kinds, in the order of the {@code boss_spawn/<name>} biome tags. */
    public static final int KIND_SERPENT = 0, KIND_WYRM = 1, KIND_KRAKEN = 2, KIND_MOTHER = 3, KIND_ANGLER = 4, KINDS = 5;

    private BossContent() {}

    public static void register(IEventBus bus)
    {
        ITEMS.register(bus);
        AbyssiaTab.MATERIALS.items().add(SERPENT_SCALE);
        AbyssiaTab.MATERIALS.items().add(WYRM_FANG);
        AbyssiaTab.MATERIALS.items().add(KRAKEN_BEAK);
        AbyssiaTab.MATERIALS.items().add(KRAKEN_INK_SAC);
        AbyssiaTab.MATERIALS.items().add(MOTHER_BELL_GEL);
        AbyssiaTab.MATERIALS.items().add(ABYSS_HEART);
        AbyssiaTab.MATERIALS.items().add(ANGLER_LURE);
    }
}
