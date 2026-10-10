package com.abyssia.item;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * D01 entry diving gear: vanilla-material starter set (spec inbox/specs/D01-entry-diving-gear.md). No potion effects:
 * helmet + tank = tank oxygen (see DivingBreathing / DiveTank); leggings + flippers = swim pair (DivingSwimGear).
 * Weaker than the deep diver gear by design.
 */
public final class EntryDivingGear
{
    private static final Holder<ArmorMaterial> MATERIAL = ModItems.ARMOR_MATERIALS.register("entry_diving", () -> new ArmorMaterial(
            Map.of(ArmorItem.Type.BOOTS, 1, ArmorItem.Type.LEGGINGS, 2, ArmorItem.Type.CHESTPLATE, 0, ArmorItem.Type.HELMET, 2),
            9, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(Items.IRON_INGOT),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "entry_diving"))), 0.0F, 0.0F));

    public static DeferredItem<Item> HELMET, TANK, LEGGINGS, FLIPPERS;

    private EntryDivingGear() {}

    public static void register(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
    {
        // Per-piece durability as in the Forge material (boots, legs, chest, head = 100, 150, 160, 120).
        HELMET = add(items, tab, "entry_diver_helmet", () -> new ArmorItem(MATERIAL, ArmorItem.Type.HELMET, new Item.Properties().durability(120)));
        TANK = add(items, tab, "entry_dive_tank", () -> new ArmorItem(MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties().durability(160)));
        LEGGINGS = add(items, tab, "entry_diving_suit_leggings", () -> new ArmorItem(MATERIAL, ArmorItem.Type.LEGGINGS, new Item.Properties().durability(150)));
        FLIPPERS = add(items, tab, "entry_diving_flippers", () -> new ArmorItem(MATERIAL, ArmorItem.Type.BOOTS, new Item.Properties().durability(100)));
    }

    private static DeferredItem<Item> add(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab,
                                          String id, Supplier<Item> factory)
    {
        DeferredItem<Item> result = items.register(id, factory);
        tab.add(result);
        return result;
    }
}
