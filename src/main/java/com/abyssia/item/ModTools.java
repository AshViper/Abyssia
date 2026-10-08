package com.abyssia.item;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.neoforged.neoforge.common.NeoForge;
import com.abyssia.registry.ModItems;
import net.neoforged.neoforge.registries.DeferredItem;
import java.util.List;
import java.util.Map;

/** Abyssal alloy tools and equipment. */
public final class ModTools {
    private static final TagKey<Item> UNDERWATER_TOOLS = TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "underwater_tools"));
    private static final Tier ALLOY = new Tier() {
        public int getUses() { return 1800; }
        public float getSpeed() { return 9.0F; }
        public float getAttackDamageBonus() { return 3.5F; }
        public int getEnchantmentValue() { return 18; }
        public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_DIAMOND_TOOL; }
        public Ingredient getRepairIngredient() { return Ingredient.of(com.abyssia.registry.ModItems.VANADIUM_INGOT.get()); }
    };
    // Diamond-tier protection. Durability per piece = Type.getDurability(30) (set on each item's Properties).
    private static final Holder<ArmorMaterial> ARMOR = ModItems.ARMOR_MATERIALS.register("abyssal_alloy", () -> new ArmorMaterial(
            Map.of(ArmorItem.Type.BOOTS, 3, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.CHESTPLATE, 8, ArmorItem.Type.HELMET, 3),
            18, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(ModItems.VANADIUM_INGOT.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "abyssal_alloy"))),
            2.0F, 0.05F));
    public static DeferredItem<Item> PICKAXE, AXE, SHOVEL, HOE, SWORD, DIVER_HELMET, FLIPPERS;
    private static final ResourceLocation FLIPPER_SPEED = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "flipper_swim_speed");
    private ModTools() {}

    public static void register(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab) {
        PICKAXE = add(items, tab, "abyssal_alloy_pickaxe", () -> new PickaxeItem(ALLOY, new Item.Properties().attributes(PickaxeItem.createAttributes(ALLOY, 1, -2.8F))));
        AXE = add(items, tab, "abyssal_alloy_axe", () -> new AxeItem(ALLOY, new Item.Properties().attributes(AxeItem.createAttributes(ALLOY, 5.5F, -3.0F))));
        SHOVEL = add(items, tab, "abyssal_alloy_shovel", () -> new ShovelItem(ALLOY, new Item.Properties().attributes(ShovelItem.createAttributes(ALLOY, 1.5F, -3.0F))));
        HOE = add(items, tab, "abyssal_alloy_hoe", () -> new HoeItem(ALLOY, new Item.Properties().attributes(HoeItem.createAttributes(ALLOY, -3.0F, 0.0F))));
        SWORD = add(items, tab, "abyssal_alloy_sword", () -> new SwordItem(ALLOY, new Item.Properties().attributes(SwordItem.createAttributes(ALLOY, 3, -2.4F))));
        DIVER_HELMET = add(items, tab, "deep_diver_helmet", () -> new DiverHelmet(ARMOR, ArmorItem.Type.HELMET, new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(30))));
        FLIPPERS = add(items, tab, "abyssal_flippers", () -> new Flippers(ARMOR, ArmorItem.Type.BOOTS, new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(30))));
        NeoForge.EVENT_BUS.register(ModTools.class);
    }

    private static DeferredItem<Item> add(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab,
                                             String id, java.util.function.Supplier<Item> factory) {
        DeferredItem<Item> result = items.register(id, factory); tab.add(result); return result;
    }

    @SubscribeEvent public static void breakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!player.getMainHandItem().is(UNDERWATER_TOOLS)) return;
        // Vanilla divides speed by 5 for each penalty, so each is restored separately:
        // swimming while off the ground (the usual case) needs x25 to fully cancel both.
        // No water bonus with Aqua Affinity: there is no penalty to restore there.
        float mult = 1.0F;
        if (player.isEyeInFluidType(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value())
                && player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED) < 1.0D)   // Aqua Affinity raises it to 1.0
            mult *= 5.0F;
        if (!player.onGround()) mult *= 5.0F;
        if (mult != 1.0F) event.setNewSpeed(event.getNewSpeed() * mult);
    }

    // Package-private: MaterialTools' pressure_diver_helmet keeps the same night vision (air time: DivingBreathing).
    static final class DiverHelmet extends ArmorItem {
        DiverHelmet(Holder<ArmorMaterial> material, Type type, Properties props) { super(material, type, props); }
        // Hook inventoryTick and only act while actually worn.
        @Override public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level, Entity entity, int slot, boolean selected) {
            if (!(entity instanceof Player player) || stack != player.getItemBySlot(EquipmentSlot.HEAD)) return;
            if (player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value())
                    && com.abyssia.Config.DIVER_HELMET_NIGHT_VISION.get())
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, true, false));
        }
    }

    static final class Flippers extends ArmorItem {
        Flippers(Holder<ArmorMaterial> material, Type type, Properties props) {
            super(material, type, props);
        }
        @Override public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            // Keep the armor's own modifiers (defense, toughness): dropping them
            // left the flippers with no protection at all.
            return super.getDefaultAttributeModifiers(stack).withModifierAdded(NeoForgeMod.SWIM_SPEED,
                    new AttributeModifier(FLIPPER_SPEED, 0.35, Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.FEET);
        }
    }
}
