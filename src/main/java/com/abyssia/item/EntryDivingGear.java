package com.abyssia.item;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * D01 entry diving gear: vanilla-material starter set (spec inbox/specs/D01-entry-diving-gear.md). No potion effects:
 * helmet / tank extend the air time (see DivingBreathing); leggings / flippers add swim speed; the full set adds
 * a small swim speed bonus. Weaker than the deep diver gear by design.
 */
public final class EntryDivingGear
{
    private static final Holder<ArmorMaterial> MATERIAL = ModItems.ARMOR_MATERIALS.register("entry_diving", () -> new ArmorMaterial(
            Map.of(ArmorItem.Type.BOOTS, 1, ArmorItem.Type.LEGGINGS, 2, ArmorItem.Type.CHESTPLATE, 0, ArmorItem.Type.HELMET, 2),
            9, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(Items.IRON_INGOT),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "entry_diving"))), 0.0F, 0.0F));

    private static final ResourceLocation LEGGINGS_SPEED = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "entry_suit_swim_speed");
    private static final ResourceLocation FLIPPERS_SPEED = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "entry_flipper_swim_speed");
    private static final ResourceLocation SET_SPEED = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "entry_set_swim_speed");

    public static DeferredItem<Item> HELMET, TANK, LEGGINGS, FLIPPERS;

    private EntryDivingGear() {}

    public static void register(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
    {
        // Per-piece durability as in the Forge material (boots, legs, chest, head = 100, 150, 160, 120).
        HELMET = add(items, tab, "entry_diver_helmet", () -> new ArmorItem(MATERIAL, ArmorItem.Type.HELMET, new Item.Properties().durability(120)));
        TANK = add(items, tab, "entry_dive_tank", () -> new ArmorItem(MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties().durability(160)));
        LEGGINGS = add(items, tab, "entry_diving_suit_leggings", () -> new Leggings(MATERIAL, ArmorItem.Type.LEGGINGS, new Item.Properties().durability(150)));
        FLIPPERS = add(items, tab, "entry_diving_flippers", () -> new Flippers(MATERIAL, ArmorItem.Type.BOOTS, new Item.Properties().durability(100)));
        NeoForge.EVENT_BUS.register(EntryDivingGear.class);
    }

    private static DeferredItem<Item> add(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab,
                                          String id, Supplier<Item> factory)
    {
        DeferredItem<Item> result = items.register(id, factory);
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
    public static void playerTick(PlayerTickEvent.Post event)
    {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        AttributeInstance swim = player.getAttribute(NeoForgeMod.SWIM_SPEED);
        if (swim == null) return;
        boolean active = player.isInWater() && fullSet(player);
        boolean has = swim.getModifier(SET_SPEED) != null;
        if (active && !has)
            swim.addTransientModifier(new AttributeModifier(SET_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!active && has)
            swim.removeModifier(SET_SPEED);
    }

    private static final class Leggings extends ArmorItem
    {
        Leggings(Holder<ArmorMaterial> material, Type type, Properties props) { super(material, type, props); }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack)
        {
            return super.getDefaultAttributeModifiers(stack).withModifierAdded(NeoForgeMod.SWIM_SPEED,
                    new AttributeModifier(LEGGINGS_SPEED, 0.03, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.LEGS);
        }
    }

    private static final class Flippers extends ArmorItem
    {
        Flippers(Holder<ArmorMaterial> material, Type type, Properties props) { super(material, type, props); }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack)
        {
            return super.getDefaultAttributeModifiers(stack).withModifierAdded(NeoForgeMod.SWIM_SPEED,
                    new AttributeModifier(FLIPPERS_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.FEET);
        }
    }
}
