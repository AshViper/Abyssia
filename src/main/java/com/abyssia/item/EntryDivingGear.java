package com.abyssia.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

/**
 * D01 entry diving gear: vanilla-material starter set (spec inbox/specs/D01-entry-diving-gear.md). No potion effects:
 * helmet / tank extend the air time (see DivingBreathing); leggings + flippers form the swim pair (DivingSwimGear). Weaker than the deep diver gear by design.
 */
public final class EntryDivingGear
{
    // Per-piece values indexed by EquipmentSlot.getIndex() (boots, legs, chest, head), as in MaterialTools.
    private static final ArmorMaterial MATERIAL = new Material("abyssia:entry_diving", new int[]{100, 150, 160, 120},
            new int[]{1, 2, 0, 2}, 0.0F, 0.0F, 9, () -> Items.IRON_INGOT);

    public static RegistryObject<Item> HELMET, TANK, LEGGINGS, FLIPPERS;

    private EntryDivingGear() {}

    public static void register(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab)
    {
        HELMET = add(items, tab, "entry_diver_helmet", () -> new DivingArmorItem(MATERIAL, ArmorItem.Type.HELMET, new Item.Properties()));
        TANK = add(items, tab, "entry_dive_tank", () -> new DivingArmorItem(MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
        LEGGINGS = add(items, tab, "entry_diving_suit_leggings", () -> new Leggings(MATERIAL, ArmorItem.Type.LEGGINGS, new Item.Properties()));
        FLIPPERS = add(items, tab, "entry_diving_flippers", () -> new Flippers(MATERIAL, ArmorItem.Type.BOOTS, new Item.Properties()));
    }

    private static RegistryObject<Item> add(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab,
                                            String id, Supplier<Item> factory)
    {
        RegistryObject<Item> result = items.register(id, factory);
        tab.add(result);
        return result;
    }

    private static final class Leggings extends ArmorItem
    {
        Leggings(ArmorMaterial material, Type type, Properties props) { super(material, type, props); }
    }

    private static final class Flippers extends ArmorItem
    {
        Flippers(ArmorMaterial material, Type type, Properties props) { super(material, type, props); }

        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer)
        { com.abyssia.client.armor.DivingSuitClient.init(consumer); }
    }

    private record Material(String name, int[] durability, int[] defense, float toughness, float knockback,
                            int enchant, Supplier<Item> repair) implements ArmorMaterial
    {
        public int getDurabilityForType(ArmorItem.Type type) { return durability[type.getSlot().getIndex()]; }
        public int getDefenseForType(ArmorItem.Type type) { return defense[type.getSlot().getIndex()]; }
        public int getEnchantmentValue() { return enchant; }
        public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        public Ingredient getRepairIngredient() { return Ingredient.of(repair.get()); }
        public String getName() { return name; }
        public float getToughness() { return toughness; }
        public float getKnockbackResistance() { return knockback; }
    }
}
