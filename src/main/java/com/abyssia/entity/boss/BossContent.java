package com.abyssia.entity.boss;

import com.abyssia.Abyssia;
import com.abyssia.registry.AbyssiaTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * BOSS01 1b: the abyssal altar (block + block entity), the serpent lure that summons at it, and the serpent scale drop.
 * Assets and loot are plain JSON (no generator).
 */
public final class BossContent
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Abyssia.MODID);

    public static final DeferredBlock<Block> ALTAR = BLOCKS.register("abyssal_altar", () -> new AbyssalAltarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(25.0f, 1200.0f).sound(SoundType.DEEPSLATE)
                    .lightLevel(s -> 6).pushReaction(PushReaction.BLOCK)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AbyssalAltarBlockEntity>> ALTAR_ENTITY = BLOCK_ENTITIES.register("abyssal_altar",
            () -> BlockEntityType.Builder.of(AbyssalAltarBlockEntity::new, ALTAR.get()).build(null));

    public static final DeferredItem<BlockItem> ALTAR_ITEM = ITEMS.registerSimpleBlockItem("abyssal_altar", ALTAR, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<Item> SERPENT_LURE = ITEMS.registerSimpleItem("abyss_serpent_lure", new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> SERPENT_SCALE = ITEMS.registerSimpleItem("abyss_serpent_scale", new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> WYRM_LURE = ITEMS.registerSimpleItem("trench_wyrm_lure", new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> WYRM_FANG = ITEMS.registerSimpleItem("trench_wyrm_fang", new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> KRAKEN_LURE = ITEMS.registerSimpleItem("abyss_kraken_lure", new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> KRAKEN_BEAK = ITEMS.registerSimpleItem("kraken_beak", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> KRAKEN_INK_SAC = ITEMS.registerSimpleItem("kraken_ink_sac", new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> MOTHER_LURE = ITEMS.registerSimpleItem("abyss_mother_lure", new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> MOTHER_BELL_GEL = ITEMS.registerSimpleItem("mother_bell_gel", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_HEART = ITEMS.registerSimpleItem("abyss_heart", new Item.Properties().rarity(Rarity.RARE));

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
