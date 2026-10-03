package com.abyssia.item;

import com.abyssia.registry.ModItems;
import com.abyssia.registry.ModTags;
import com.abyssia.Abyssia;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Tools and diving armor of the material processing system (docs/material-system.md, tools/material_spec.json
 * "tools" / "armor"). Stats are the spec values; the simple "special" effects are the event handlers below.
 * Not implemented yet (see the spec): drill (2-block mining, x1.5 wear), plant_cut (shears behaviour, fiber bonus),
 * crystal_harvest silk-touch loot. TODO(phase2 machines): energy use of the drill/cutter hooks in here.
 */
public final class MaterialTools
{
    private static final Tier COBALT = tier(420, 11.0F, 2.0F, 16, () -> ModItems.COBALT_INGOT.get());
    private static final Tier MANGANESE = tier(2600, 6.0F, 3.0F, 8, () -> ModItems.MANGANESE_INGOT.get());
    private static final Tier MOLYBDENUM = tier(1300, 7.5F, 2.5F, 12, () -> ModItems.HEAT_RESISTANT_ALLOY_INGOT.get());
    private static final Tier TUNGSTEN = tier(3200, 7.0F, 3.5F, 10, () -> ModItems.TUNGSTEN_ALLOY_INGOT.get());
    private static final Tier CRYSTAL = tier(900, 8.0F, 2.0F, 22, () -> ModItems.CRYSTAL_CORE.get());
    private static final Tier DRILL = tier(2200, 12.0F, 2.0F, 14, () -> ModItems.DRILL_HEAD.get());
    private static final Tier CUTTER = tier(1500, 7.0F, 1.5F, 20, () -> ModItems.TUNGSTEN_TIP.get());

    // Durabilities are the spec's per-piece totals.
    // (the per-piece durability is set on the item's Properties, see register)
    private static final Holder<ArmorMaterial> DIVING = material("diving_alloy",
            Map.of(ArmorItem.Type.LEGGINGS, 5, ArmorItem.Type.CHESTPLATE, 6), 1.5F, 0.0F, 12, () -> ModItems.CORROSION_ALLOY_INGOT.get());
    private static final Holder<ArmorMaterial> PRESSURE = material("pressure_alloy",
            Map.of(ArmorItem.Type.HELMET, 4), 3.0F, 0.1F, 15, () -> ModItems.PRESSURE_SHELL.get());

    private static final ResourceLocation SUIT_SPEED = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "diving_suit_swim_speed");
    private static final ResourceLocation SET_SPEED = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "diving_set_swim_speed");

    public static DeferredItem<Item> CRUSHING_HAMMER, COBALT_PICKAXE, COBALT_SHOVEL, MANGANESE_AXE, MANGANESE_SWORD,
            MOLYBDENUM_PICKAXE, TUNGSTEN_PICKAXE, TUNGSTEN_AXE, CRYSTAL_PICKAXE, ABYSSAL_DRILL, ABYSSAL_CUTTER,
            DIVE_TANK, DIVING_SUIT_LEGGINGS, PRESSURE_DIVER_HELMET;

    private MaterialTools() {}

    public static void register(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
    {
        CRUSHING_HAMMER = add(items, tab, "crushing_hammer", () -> new CrushingHammerItem(props(Rarity.COMMON).durability(250), 2, -3.0));
        COBALT_PICKAXE = add(items, tab, "cobalt_pickaxe", () -> new PickaxeItem(COBALT, props(Rarity.UNCOMMON).attributes(PickaxeItem.createAttributes(COBALT, 1, -2.6F))));
        COBALT_SHOVEL = add(items, tab, "cobalt_shovel", () -> new ShovelItem(COBALT, props(Rarity.UNCOMMON).attributes(ShovelItem.createAttributes(COBALT, 1.5F, -3.0F))));
        MANGANESE_AXE = add(items, tab, "manganese_axe", () -> new AxeItem(MANGANESE, props(Rarity.UNCOMMON).attributes(AxeItem.createAttributes(MANGANESE, 5.5F, -3.0F))));
        MANGANESE_SWORD = add(items, tab, "manganese_sword", () -> new SwordItem(MANGANESE, props(Rarity.UNCOMMON).attributes(SwordItem.createAttributes(MANGANESE, 3, -2.4F))));
        // heat_guard: the item itself survives lava/fire; the damage reduction is in hurt() below.
        MOLYBDENUM_PICKAXE = add(items, tab, "molybdenum_pickaxe", () -> new PickaxeItem(MOLYBDENUM, props(Rarity.RARE).fireResistant().attributes(PickaxeItem.createAttributes(MOLYBDENUM, 1, -2.8F))));
        TUNGSTEN_PICKAXE = add(items, tab, "tungsten_pickaxe", () -> new PickaxeItem(TUNGSTEN, props(Rarity.RARE).attributes(PickaxeItem.createAttributes(TUNGSTEN, 1, -3.1F))));
        TUNGSTEN_AXE = add(items, tab, "tungsten_axe", () -> new AxeItem(TUNGSTEN, props(Rarity.RARE).attributes(AxeItem.createAttributes(TUNGSTEN, 6.5F, -3.3F))));
        CRYSTAL_PICKAXE = add(items, tab, "crystal_pickaxe", () -> new PickaxeItem(CRYSTAL, props(Rarity.RARE).attributes(PickaxeItem.createAttributes(CRYSTAL, 1, -2.8F))));
        ABYSSAL_DRILL = add(items, tab, "abyssal_drill", () -> new PickaxeItem(DRILL, props(Rarity.EPIC).attributes(PickaxeItem.createAttributes(DRILL, 1, -3.0F))));
        ABYSSAL_CUTTER = add(items, tab, "abyssal_cutter", () -> new SwordItem(CUTTER, props(Rarity.EPIC).attributes(SwordItem.createAttributes(CUTTER, 2, -1.8F))));
        DIVE_TANK = add(items, tab, "dive_tank", () -> new ArmorItem(DIVING, ArmorItem.Type.CHESTPLATE, props(Rarity.UNCOMMON).durability(448)));
        DIVING_SUIT_LEGGINGS = add(items, tab, "diving_suit_leggings", () -> new SuitLeggings(DIVING, ArmorItem.Type.LEGGINGS, props(Rarity.UNCOMMON).durability(420)));
        // Smithing upgrade of deep_diver_helmet: same breathing / night vision; tag abyssia:pressure_proof marks it for
        // the future hadal pressure damage.
        PRESSURE_DIVER_HELMET = add(items, tab, "pressure_diver_helmet", () -> new ModTools.DiverHelmet(PRESSURE, ArmorItem.Type.HELMET, props(Rarity.EPIC).durability(462)));
        NeoForge.EVENT_BUS.register(MaterialTools.class);
    }

    private static Item.Properties props(Rarity rarity)
    {
        return new Item.Properties().rarity(rarity);
    }

    private static DeferredItem<Item> add(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab,
                                            String id, Supplier<Item> factory)
    {
        DeferredItem<Item> result = items.register(id, factory);
        tab.add(result);
        return result;
    }

    private static Holder<ArmorMaterial> material(String name, Map<ArmorItem.Type, Integer> defense, float toughness,
                                                   float knockback, int enchant, Supplier<Item> repair)
    {
        return ModItems.ARMOR_MATERIALS.register(name, () -> new ArmorMaterial(defense, enchant, SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(repair.get()),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name))), toughness, knockback));
    }

    /** Mining level and harvest tag match the abyssal alloy tier (spec: minecraft:needs_diamond_tool). */
    private static Tier tier(int uses, float speed, float bonus, int enchant, Supplier<Item> repair)
    {
        return new Tier()
        {
            public int getUses() { return uses; }
            public float getSpeed() { return speed; }
            public float getAttackDamageBonus() { return bonus; }
            public int getEnchantmentValue() { return enchant; }
            public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_DIAMOND_TOOL; }
            public Ingredient getRepairIngredient() { return Ingredient.of(repair.get()); }
        };
    }

    @SubscribeEvent
    public static void breakSpeed(PlayerEvent.BreakSpeed event)
    {
        ItemStack held = event.getEntity().getMainHandItem();
        if (held.is(TUNGSTEN_PICKAXE.get()))
        {
            // hard_block_bonus: obsidian, deepslate-like rock, ores (destroy time >= 3)
            float hardness = event.getPosition()
                    .map(pos -> event.getState().getDestroySpeed(event.getEntity().level(), pos))
                    .orElse(0.0F);
            if (hardness >= 3.0F) event.setNewSpeed(event.getNewSpeed() * 1.5F);
        }
        else if (held.is(CRYSTAL_PICKAXE.get()) && event.getState().is(ModTags.CRYSTAL_BLOCKS))
        {
            event.setNewSpeed(event.getNewSpeed() * 2.0F);   // crystal_harvest (speed part)
        }
    }

    /** heat_guard: fire, lava, magma / hot floor (and vents) hurt half as much while a molybdenum pickaxe is held. */
    @SubscribeEvent
    public static void hurt(LivingIncomingDamageEvent event)
    {
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) return;
        LivingEntity entity = event.getEntity();
        if (entity.getMainHandItem().is(MOLYBDENUM_PICKAXE.get()) || entity.getOffhandItem().is(MOLYBDENUM_PICKAXE.get()))
            event.setAmount(event.getAmount() * 0.5F);
    }

    /** Diving set (any diver helmet + tank + suit leggings + abyssal flippers): +10% swim speed while worn. */
    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event)
    {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        AttributeInstance swim = player.getAttribute(NeoForgeMod.SWIM_SPEED);
        if (swim == null) return;
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean full = (head.is(ModTools.DIVER_HELMET.get()) || head.is(PRESSURE_DIVER_HELMET.get()))
                && player.getItemBySlot(EquipmentSlot.CHEST).is(DIVE_TANK.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(DIVING_SUIT_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModTools.FLIPPERS.get());
        boolean has = swim.getModifier(SET_SPEED) != null;
        if (full && !has)
            swim.addTransientModifier(new AttributeModifier(SET_SPEED, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!full && has)
            swim.removeModifier(SET_SPEED);
    }

    /** suit_swim: +5% swim speed (the armor's own defense/toughness modifiers are kept). */
    private static final class SuitLeggings extends ArmorItem
    {
        SuitLeggings(Holder<ArmorMaterial> material, Type type, Properties props) { super(material, type, props); }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack)
        {
            return super.getDefaultAttributeModifiers(stack).withModifierAdded(NeoForgeMod.SWIM_SPEED,
                    new AttributeModifier(SUIT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.LEGS);
        }
    }
}
