package com.abyssia.item;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import java.util.List;

/** Abyssal alloy tools and equipment. */
public final class ModTools {
    private static final TagKey<Item> UNDERWATER_TOOLS = TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "underwater_tools"));
    private static final Tier ALLOY = new Tier() {
        public int getUses() { return 1800; }
        public float getSpeed() { return 9.0F; }
        public float getAttackDamageBonus() { return 3.5F; }
        public int getLevel() { return 4; }
        public int getEnchantmentValue() { return 18; }
        public TagKey<Block> getTag() { return BlockTags.NEEDS_DIAMOND_TOOL; }
        public Ingredient getRepairIngredient() { return Ingredient.of(com.abyssia.registry.ModItems.VANADIUM_INGOT.get()); }
    };
    private static final ArmorMaterial ARMOR = new AlloyArmor();
    public static RegistryObject<Item> PICKAXE, AXE, SHOVEL, HOE, SWORD, DIVER_HELMET, FLIPPERS;
    private ModTools() {}

    public static void register(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab) {
        PICKAXE = add(items, tab, "abyssal_alloy_pickaxe", () -> new PickaxeItem(ALLOY, 1, -2.8F, new Item.Properties()));
        AXE = add(items, tab, "abyssal_alloy_axe", () -> new AxeItem(ALLOY, 5.5F, -3.0F, new Item.Properties()));
        SHOVEL = add(items, tab, "abyssal_alloy_shovel", () -> new ShovelItem(ALLOY, 1.5F, -3.0F, new Item.Properties()));
        HOE = add(items, tab, "abyssal_alloy_hoe", () -> new HoeItem(ALLOY, -3, 0.0F, new Item.Properties()));
        SWORD = add(items, tab, "abyssal_alloy_sword", () -> new SwordItem(ALLOY, 3, -2.4F, new Item.Properties()));
        DIVER_HELMET = add(items, tab, "deep_diver_helmet", () -> new DiverHelmet(ARMOR, ArmorItem.Type.HELMET, new Item.Properties()));
        FLIPPERS = add(items, tab, "abyssal_flippers", () -> new Flippers(ARMOR, ArmorItem.Type.BOOTS, new Item.Properties()));
        MinecraftForge.EVENT_BUS.register(ModTools.class);
    }

    private static RegistryObject<Item> add(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab,
                                             String id, java.util.function.Supplier<Item> factory) {
        RegistryObject<Item> result = items.register(id, factory); tab.add(result); return result;
    }

    @SubscribeEvent public static void breakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!player.getMainHandItem().is(UNDERWATER_TOOLS)) return;
        // Vanilla divides speed by 5 for each penalty, so each is restored separately:
        // swimming while off the ground (the usual case) needs x25 to fully cancel both.
        // No water bonus with Aqua Affinity: there is no penalty to restore there.
        float mult = 1.0F;
        if (player.isEyeInFluidType(net.minecraftforge.common.ForgeMod.WATER_TYPE.get())
                && !net.minecraft.world.item.enchantment.EnchantmentHelper.hasAquaAffinity(player))
            mult *= 5.0F;
        if (!player.onGround()) mult *= 5.0F;
        if (mult != 1.0F) event.setNewSpeed(event.getNewSpeed() * mult);
    }

    // Package-private: MaterialTools' pressure_diver_helmet keeps the same night vision (air time: DivingBreathing).
    static final class DiverHelmet extends ArmorItem {
        DiverHelmet(ArmorMaterial material, Type type, Properties props) { super(material, type, props); }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer)
        { com.abyssia.client.armor.DivingSuitClient.init(consumer); }
        // IForgeItem#onArmorTick is deprecated for removal in this Forge: hook the
        // non-deprecated onInventoryTick instead and only act while actually worn.
        @Override public void onInventoryTick(ItemStack stack, net.minecraft.world.level.Level level, Player player, int slot, int selected) {
            super.onInventoryTick(stack, level, player, slot, selected); // keeps vanilla inventoryTick (pickup pop animation)
            if (stack != player.getItemBySlot(EquipmentSlot.HEAD)) return;
            if (player.isEyeInFluidType(ForgeMod.WATER_TYPE.get())
                    && com.abyssia.Config.DIVER_HELMET_NIGHT_VISION.get())
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, true, false));
        }
    }

    static final class Flippers extends ArmorItem {
        Flippers(ArmorMaterial material, Type type, Properties props) {
            super(material, type, props);
        }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer)
        { com.abyssia.client.armor.DivingSuitClient.init(consumer); }
    }

    private static final class AlloyArmor implements ArmorMaterial {
        // Diamond-tier protection (verified: EquipmentSlot.getIndex() maps armor to 0..3 here,
        // so the arrays below line up as boots/legs/chest/helmet).
        public int getDurabilityForType(ArmorItem.Type type) { return new int[]{13, 15, 16, 11}[type.getSlot().getIndex()] * 30; }
        public int getDefenseForType(ArmorItem.Type type) { return new int[]{3, 6, 8, 3}[type.getSlot().getIndex()]; }
        public int getEnchantmentValue() { return 18; }
        public net.minecraft.sounds.SoundEvent getEquipSound() { return net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON; }
        public Ingredient getRepairIngredient() { return Ingredient.of(com.abyssia.registry.ModItems.VANADIUM_INGOT.get()); }
        public String getName() { return "abyssia:abyssal_alloy"; }
        public float getToughness() { return 2.0F; }
        public float getKnockbackResistance() { return 0.05F; }
    }
}
