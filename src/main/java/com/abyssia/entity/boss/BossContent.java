package com.abyssia.entity.boss;

import com.abyssia.Abyssia;
import com.abyssia.registry.AbyssiaTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * BOSS01 1b: the abyssal altar (block + block entity), the serpent lure that summons at it, and the serpent scale drop.
 * Assets and loot are plain JSON (no generator).
 */
public final class BossContent
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Abyssia.MODID);

    public static final RegistryObject<Block> ALTAR = BLOCKS.register("abyssal_altar", () -> new AbyssalAltarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(25.0f, 1200.0f).sound(SoundType.DEEPSLATE)
                    .lightLevel(s -> 6).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<BlockEntityType<AbyssalAltarBlockEntity>> ALTAR_ENTITY = BLOCK_ENTITIES.register("abyssal_altar",
            () -> BlockEntityType.Builder.of(AbyssalAltarBlockEntity::new, ALTAR.get()).build(null));

    public static final RegistryObject<Item> ALTAR_ITEM = ITEMS.register("abyssal_altar", () -> new BlockItem(ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SERPENT_LURE = ITEMS.register("abyss_serpent_lure", () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SERPENT_SCALE = ITEMS.register("abyss_serpent_scale", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<Item> WYRM_LURE = ITEMS.register("trench_wyrm_lure", () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> WYRM_FANG = ITEMS.register("trench_wyrm_fang", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<Item> KRAKEN_LURE = ITEMS.register("abyss_kraken_lure", () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> KRAKEN_BEAK = ITEMS.register("kraken_beak", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> KRAKEN_INK_SAC = ITEMS.register("kraken_ink_sac", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> MOTHER_LURE = ITEMS.register("abyss_mother_lure", () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> MOTHER_BELL_GEL = ITEMS.register("mother_bell_gel", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ABYSS_HEART = ITEMS.register("abyss_heart", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    /** Boss kinds an altar can summon, chosen by the lure used. */
    public static final int KIND_SERPENT = 0, KIND_WYRM = 1, KIND_KRAKEN = 2, KIND_MOTHER = 3, KINDS = 4;

    /** The kind a lure summons, or -1 if the stack is not a lure. */
    public static int lureKind(net.minecraft.world.item.ItemStack stack)
    {
        if (stack.is(SERPENT_LURE.get())) return KIND_SERPENT;
        if (stack.is(WYRM_LURE.get())) return KIND_WYRM;
        if (stack.is(KRAKEN_LURE.get())) return KIND_KRAKEN;
        if (stack.is(MOTHER_LURE.get())) return KIND_MOTHER;
        return -1;
    }

    public static Item lureItem(int kind)
    {
        return kind == KIND_MOTHER ? MOTHER_LURE.get() : kind == KIND_KRAKEN ? KRAKEN_LURE.get() : kind == KIND_WYRM ? WYRM_LURE.get() : SERPENT_LURE.get();
    }

    private BossContent() {}

    public static void register(IEventBus bus)
    {
        BossNetwork.register();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        AbyssiaTab.MACHINES.items().add(ALTAR_ITEM);
        AbyssiaTab.MATERIALS.items().add(SERPENT_LURE);
        AbyssiaTab.MATERIALS.items().add(SERPENT_SCALE);
        AbyssiaTab.MATERIALS.items().add(WYRM_LURE);
        AbyssiaTab.MATERIALS.items().add(WYRM_FANG);
        AbyssiaTab.MATERIALS.items().add(KRAKEN_LURE);
        AbyssiaTab.MATERIALS.items().add(KRAKEN_BEAK);
        AbyssiaTab.MATERIALS.items().add(KRAKEN_INK_SAC);
        AbyssiaTab.MATERIALS.items().add(MOTHER_LURE);
        AbyssiaTab.MATERIALS.items().add(MOTHER_BELL_GEL);
        AbyssiaTab.MATERIALS.items().add(ABYSS_HEART);
    }
}
