package com.abyssia.item;

import com.abyssia.registry.ModItems;
import com.abyssia.registry.ModTags;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;
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
    private static final ArmorMaterial DIVING = new Material("abyssia:diving_alloy", new int[]{0, 420, 448, 0},
            new int[]{0, 5, 6, 0}, 1.5F, 0.0F, 12, () -> ModItems.CORROSION_ALLOY_INGOT.get());
    private static final ArmorMaterial PRESSURE = new Material("abyssia:pressure_alloy", new int[]{350, 420, 520, 462},
            new int[]{3, 6, 7, 4}, 3.0F, 0.1F, 15, () -> ModItems.PRESSURE_SHELL.get());

    private static final UUID SUIT_SPEED = UUID.fromString("0f4f1c8e-6a52-4c0b-9d0e-2b7a31c5e7a1");
    private static final UUID SET_SPEED = UUID.fromString("7c1d2e44-3b9a-4f6e-8a15-5d9c0b2f6e83");

    public static RegistryObject<Item> CRUSHING_HAMMER, COBALT_PICKAXE, COBALT_SHOVEL, MANGANESE_AXE, MANGANESE_SWORD,
            MOLYBDENUM_PICKAXE, TUNGSTEN_PICKAXE, TUNGSTEN_AXE, CRYSTAL_PICKAXE, ABYSSAL_DRILL, ABYSSAL_CUTTER,
            DIVE_TANK, DIVING_SUIT_LEGGINGS, PRESSURE_DIVER_HELMET, PRESSURE_DIVE_TANK, PRESSURE_SUIT_LEGGINGS, PRESSURE_FLIPPERS;

    private MaterialTools() {}

    public static void register(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab)
    {
        CRUSHING_HAMMER = add(items, tab, "crushing_hammer", () -> new CrushingHammerItem(props(Rarity.COMMON).durability(250), 2, -3.0));
        COBALT_PICKAXE = add(items, tab, "cobalt_pickaxe", () -> new PickaxeItem(COBALT, 1, -2.6F, props(Rarity.UNCOMMON)));
        COBALT_SHOVEL = add(items, tab, "cobalt_shovel", () -> new ShovelItem(COBALT, 1.5F, -3.0F, props(Rarity.UNCOMMON)));
        MANGANESE_AXE = add(items, tab, "manganese_axe", () -> new AxeItem(MANGANESE, 5.5F, -3.0F, props(Rarity.UNCOMMON)));
        MANGANESE_SWORD = add(items, tab, "manganese_sword", () -> new SwordItem(MANGANESE, 3, -2.4F, props(Rarity.UNCOMMON)));
        // heat_guard: the item itself survives lava/fire; the damage reduction is in hurt() below.
        MOLYBDENUM_PICKAXE = add(items, tab, "molybdenum_pickaxe", () -> new PickaxeItem(MOLYBDENUM, 1, -2.8F, props(Rarity.RARE).fireResistant()));
        TUNGSTEN_PICKAXE = add(items, tab, "tungsten_pickaxe", () -> new PickaxeItem(TUNGSTEN, 1, -3.1F, props(Rarity.RARE)));
        TUNGSTEN_AXE = add(items, tab, "tungsten_axe", () -> new AxeItem(TUNGSTEN, 6.5F, -3.3F, props(Rarity.RARE)));
        CRYSTAL_PICKAXE = add(items, tab, "crystal_pickaxe", () -> new PickaxeItem(CRYSTAL, 1, -2.8F, props(Rarity.RARE)));
        ABYSSAL_DRILL = add(items, tab, "abyssal_drill", () -> new PickaxeItem(DRILL, 1, -3.0F, props(Rarity.EPIC)));
        ABYSSAL_CUTTER = add(items, tab, "abyssal_cutter", () -> new SwordItem(CUTTER, 2, -1.8F, props(Rarity.EPIC)));
        DIVE_TANK = add(items, tab, "dive_tank", () -> new ArmorItem(DIVING, ArmorItem.Type.CHESTPLATE, props(Rarity.UNCOMMON)));
        DIVING_SUIT_LEGGINGS = add(items, tab, "diving_suit_leggings", () -> new SuitLeggings(DIVING, ArmorItem.Type.LEGGINGS, props(Rarity.UNCOMMON)));
        // Smithing upgrade of deep_diver_helmet: same breathing / night vision; tag abyssia:pressure_proof marks it for
        // the future hadal pressure damage.
        PRESSURE_DIVER_HELMET = add(items, tab, "pressure_diver_helmet", () -> new ModTools.DiverHelmet(PRESSURE, ArmorItem.Type.HELMET, props(Rarity.EPIC)));
        // Pressure gear (tier 3, PressureGear): smithing upgrades of the deep pieces, same behaviour as their bases.
        PRESSURE_DIVE_TANK = add(items, tab, "pressure_dive_tank", () -> new ArmorItem(PRESSURE, ArmorItem.Type.CHESTPLATE, props(Rarity.EPIC)));
        PRESSURE_SUIT_LEGGINGS = add(items, tab, "pressure_suit_leggings", () -> new SuitLeggings(PRESSURE, ArmorItem.Type.LEGGINGS, props(Rarity.EPIC)));
        PRESSURE_FLIPPERS = add(items, tab, "pressure_flippers", () -> new ModTools.Flippers(PRESSURE, ArmorItem.Type.BOOTS, props(Rarity.EPIC)));
        MinecraftForge.EVENT_BUS.register(MaterialTools.class);
        PressureGear.register();
    }

    private static Item.Properties props(Rarity rarity)
    {
        return new Item.Properties().rarity(rarity);
    }

    private static RegistryObject<Item> add(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab,
                                            String id, Supplier<Item> factory)
    {
        RegistryObject<Item> result = items.register(id, factory);
        tab.add(result);
        return result;
    }

    /** Mining level and harvest tag match the abyssal alloy tier (spec: minecraft:needs_diamond_tool). */
    private static Tier tier(int uses, float speed, float bonus, int enchant, Supplier<Item> repair)
    {
        return new Tier()
        {
            public int getUses() { return uses; }
            public float getSpeed() { return speed; }
            public float getAttackDamageBonus() { return bonus; }
            public int getLevel() { return 4; }
            public int getEnchantmentValue() { return enchant; }
            public TagKey<Block> getTag() { return BlockTags.NEEDS_DIAMOND_TOOL; }
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
    public static void hurt(LivingHurtEvent event)
    {
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) return;
        LivingEntity entity = event.getEntity();
        if (entity.getMainHandItem().is(MOLYBDENUM_PICKAXE.get()) || entity.getOffhandItem().is(MOLYBDENUM_PICKAXE.get()))
            event.setAmount(event.getAmount() * 0.5F);
    }

    /** Diving set (any diver helmet + tank + suit leggings + abyssal flippers): +10% swim speed while worn. */
    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        AttributeInstance swim = player.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (swim == null) return;
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean full = (head.is(ModTools.DIVER_HELMET.get()) || head.is(PRESSURE_DIVER_HELMET.get()))
                && (player.getItemBySlot(EquipmentSlot.CHEST).is(DIVE_TANK.get()) || player.getItemBySlot(EquipmentSlot.CHEST).is(PRESSURE_DIVE_TANK.get()))
                && (player.getItemBySlot(EquipmentSlot.LEGS).is(DIVING_SUIT_LEGGINGS.get()) || player.getItemBySlot(EquipmentSlot.LEGS).is(PRESSURE_SUIT_LEGGINGS.get()))
                && (player.getItemBySlot(EquipmentSlot.FEET).is(ModTools.FLIPPERS.get()) || player.getItemBySlot(EquipmentSlot.FEET).is(PRESSURE_FLIPPERS.get()));
        boolean has = swim.getModifier(SET_SPEED) != null;
        if (full && !has)
            swim.addTransientModifier(new AttributeModifier(SET_SPEED, "Abyssia diving set swim speed", 0.10, AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!full && has)
            swim.removeModifier(SET_SPEED);
    }

    /** suit_swim: +5% swim speed (the armor's own defense/toughness modifiers are kept). */
    private static final class SuitLeggings extends ArmorItem
    {
        SuitLeggings(ArmorMaterial material, Type type, Properties props) { super(material, type, props); }

        @Override
        @SuppressWarnings("deprecation")
        public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot)
        {
            Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(slot);
            if (slot != EquipmentSlot.LEGS) return base;
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.putAll(base);
            b.put(ForgeMod.SWIM_SPEED.get(), new AttributeModifier(SUIT_SPEED, "Diving suit swim speed", 0.05, AttributeModifier.Operation.MULTIPLY_TOTAL));
            return b.build();
        }
    }

    /** Per-piece armor values indexed by EquipmentSlot.getIndex() (boots, legs, chest, head), as in ModTools. */
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
