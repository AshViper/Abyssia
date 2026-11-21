package com.abyssia.entity.boss;

import com.abyssia.Abyssia;
import com.abyssia.registry.AbyssiaTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * BOSS01: the boss drop materials. Bosses spawn naturally ({@link BossSpawner}); there is no altar or lure any more.
 * Assets and loot are plain JSON (no generator).
 */
public final class BossContent
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Abyssia.MODID);

    public static final RegistryObject<Item> SERPENT_SCALE = ITEMS.register("abyss_serpent_scale", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<Item> WYRM_FANG = ITEMS.register("trench_wyrm_fang", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<Item> KRAKEN_BEAK = ITEMS.register("kraken_beak", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> KRAKEN_INK_SAC = ITEMS.register("kraken_ink_sac", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> MOTHER_BELL_GEL = ITEMS.register("mother_bell_gel", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ABYSS_HEART = ITEMS.register("abyss_heart", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<Item> ANGLER_LURE = ITEMS.register("angler_lure", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    /** Boss kinds, in the order of the {@code boss_spawn/<name>} biome tags. */
    public static final int KIND_SERPENT = 0, KIND_WYRM = 1, KIND_KRAKEN = 2, KIND_MOTHER = 3, KIND_ANGLER = 4, KINDS = 5;

    private BossContent() {}

    public static void register(IEventBus bus)
    {
        BossNetwork.register();
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
