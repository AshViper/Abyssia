package com.abyssia.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * D01 entry diving gear: vanilla-material starter set (spec inbox/specs/D01-entry-diving-gear.md). No potion effects:
 * helmet / tank extend the air time (see DivingBreathing); leggings / flippers add swim speed; the full set adds
 * a small swim speed bonus. Weaker than the deep diver gear by design.
 */
public final class EntryDivingGear
{
    // Per-piece values indexed by EquipmentSlot.getIndex() (boots, legs, chest, head), as in MaterialTools.
    private static final ArmorMaterial MATERIAL = new Material("abyssia:entry_diving", new int[]{100, 150, 160, 120},
            new int[]{1, 2, 0, 2}, 0.0F, 0.0F, 9, () -> Items.IRON_INGOT);

    private static final UUID LEGGINGS_SPEED = UUID.fromString("b3a6f0d2-5c71-4e8a-9f24-6d1e8c7a3b05");
    private static final UUID FLIPPERS_SPEED = UUID.fromString("e9d41c7b-2a68-4f35-8b90-1c5f7a2d6e48");
    private static final UUID SET_SPEED = UUID.fromString("41c8a5e7-9b3d-4026-a7f1-3e6b0d9c5a82");

    public static RegistryObject<Item> HELMET, TANK, LEGGINGS, FLIPPERS;

    private EntryDivingGear() {}

    public static void register(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab)
    {
        HELMET = add(items, tab, "entry_diver_helmet", () -> new ArmorItem(MATERIAL, ArmorItem.Type.HELMET, new Item.Properties()));
        TANK = add(items, tab, "entry_dive_tank", () -> new ArmorItem(MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
        LEGGINGS = add(items, tab, "entry_diving_suit_leggings", () -> new Leggings(MATERIAL, ArmorItem.Type.LEGGINGS, new Item.Properties()));
        FLIPPERS = add(items, tab, "entry_diving_flippers", () -> new Flippers(MATERIAL, ArmorItem.Type.BOOTS, new Item.Properties()));
        MinecraftForge.EVENT_BUS.register(EntryDivingGear.class);
    }

    private static RegistryObject<Item> add(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab,
                                            String id, Supplier<Item> factory)
    {
        RegistryObject<Item> result = items.register(id, factory);
        tab.add(result);
        return result;
    }

    private static boolean fullSet(Player player)
    {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(TANK.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(FLIPPERS.get());
    }

    /** Full-set swim bonus, only while in water. */
    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        AttributeInstance swim = player.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (swim == null) return;
        boolean active = player.isInWater() && fullSet(player);
        boolean has = swim.getModifier(SET_SPEED) != null;
        if (active && !has)
            swim.addTransientModifier(new AttributeModifier(SET_SPEED, "Entry diving set swim speed", 0.05, AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!active && has)
            swim.removeModifier(SET_SPEED);
    }

    private static final class Leggings extends ArmorItem
    {
        Leggings(ArmorMaterial material, Type type, Properties props) { super(material, type, props); }

        @Override
        @SuppressWarnings("deprecation")
        public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot)
        {
            Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(slot);
            if (slot != EquipmentSlot.LEGS) return base;
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.putAll(base);
            b.put(ForgeMod.SWIM_SPEED.get(), new AttributeModifier(LEGGINGS_SPEED, "Entry suit swim speed", 0.03, AttributeModifier.Operation.MULTIPLY_TOTAL));
            return b.build();
        }
    }

    private static final class Flippers extends ArmorItem
    {
        Flippers(ArmorMaterial material, Type type, Properties props) { super(material, type, props); }

        @Override
        @SuppressWarnings("deprecation")
        public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot)
        {
            Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(slot);
            if (slot != EquipmentSlot.FEET) return base;
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.putAll(base);
            b.put(ForgeMod.SWIM_SPEED.get(), new AttributeModifier(FLIPPERS_SPEED, "Entry flipper swim speed", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL));
            return b.build();
        }
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
