package com.abyssia.item.electric;

import com.abyssia.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * I03 electric tools: registration plus the FE helpers shared by the drill and the cutter. The stored energy lives in
 * the abyssia:energy data component (1.20: the "Energy" tag); the tools have no durability (see {@link StackEnergy} for the charger capability).
 * Test hooks: {@link #getEnergy}, {@link #setEnergy}, {@link #fullStack}.
 */
public final class ElectricTools
{
    public static final int DRILL_CAPACITY = 100_000, DRILL_COST = 500;
    public static final int CUTTER_CAPACITY = 75_000, CUTTER_COST = 250;
    public static final int BAR_COLOR = 0x00E5FF;

    public static DeferredItem<Item> DRILL, CUTTER, PROPULSION_SCREW;

    private static final Map<Player, Long> LAST_EMPTY_MESSAGE = new WeakHashMap<>();

    private ElectricTools() {}

    public static void register(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
    {
        DRILL = add(items, tab, "electric_abyssal_drill", () -> new ElectricDrillItem(tier(12.0F, 2.0F, 14), new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));
        CUTTER = add(items, tab, "electric_abyssal_cutter", () -> new ElectricCutterItem(tier(7.0F, 1.5F, 20), new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));
        PROPULSION_SCREW = add(items, tab, "propulsion_screw", () -> new PropulsionScrewItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));
    }

    private static DeferredItem<Item> add(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab,
                                            String id, Supplier<Item> factory)
    {
        DeferredItem<Item> result = items.register(id, factory);
        tab.add(result);
        return result;
    }

    /** Creative tab: the empty items come from the normal list, these are the fully charged ones. */
    public static void addFullVariants(CreativeModeTab.Output output)
    {
        output.accept(fullStack(DRILL.get()));
        output.accept(fullStack(CUTTER.get()));
        output.accept(fullStack(PROPULSION_SCREW.get()));
    }

    /** Same mining level (4, diamond tag) and speed / attack bonus as abyssal_drill / abyssal_cutter; no durability. */
    private static Tier tier(float speed, float bonus, int enchant)
    {
        return new Tier()
        {
            public int getUses() { return 0; }
            public float getSpeed() { return speed; }
            public float getAttackDamageBonus() { return bonus; }
            public int getEnchantmentValue() { return enchant; }
            public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_DIAMOND_TOOL; }
            public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        };
    }

    // ---- energy on the stack ----

    public static int capacity(ItemStack stack)
    {
        return stack.getItem() instanceof Electric e ? e.capacity() : 0;
    }

    public static boolean isElectric(ItemStack stack) { return stack.getItem() instanceof Electric; }

    public static int getEnergy(ItemStack stack)
    {
        return Math.max(0, Math.min(capacity(stack), stack.getOrDefault(ModDataComponents.ENERGY.get(), 0)));
    }

    public static void setEnergy(ItemStack stack, int energy)
    {
        stack.set(ModDataComponents.ENERGY.get(), Math.max(0, Math.min(capacity(stack), energy)));
    }

    public static ItemStack fullStack(Item item)
    {
        ItemStack stack = new ItemStack(item);
        setEnergy(stack, capacity(stack));
        return stack;
    }

    /** Takes up to {@code amount} FE (creative players pay nothing); true if the full amount was available. */
    public static boolean consume(ItemStack stack, int amount, LivingEntity user)
    {
        if (user instanceof Player p && p.getAbilities().instabuild) return true;
        int have = getEnergy(stack);
        setEnergy(stack, have - amount);
        return have >= amount;
    }

    // ---- shared item behaviour ----

    public static int barWidth(ItemStack stack)
    {
        int cap = capacity(stack);
        return cap <= 0 ? 0 : Math.round(13.0F * getEnergy(stack) / cap);
    }

    public static void tooltip(ItemStack stack, List<Component> lines)
    {
        int cap = capacity(stack), energy = getEnergy(stack);
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        int percent = cap <= 0 ? 0 : Math.round(100.0F * energy / cap);
        lines.add(Component.translatable("tooltip.abyssia.electric_tool.energy", percent, nf.format(energy), nf.format(cap))
                .withStyle(energy > 0 ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.RED));
        lines.add(Component.translatable("tooltip.abyssia.electric_tool.charge").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** Out of energy: the weapon bonus is removed, an attack does the bare-hand 1 damage. */
    public static ItemAttributeModifiers attackModifiers(ItemStack stack, ItemAttributeModifiers base)
    {
        if (getEnergy(stack) > 0) return base;
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : base.modifiers())
        {
            AttributeModifier m = e.modifier();
            b.add(e.attribute(), e.attribute().is(Attributes.ATTACK_DAMAGE)
                    ? new AttributeModifier(m.id(), 0.0, m.operation()) : m, e.slot());
        }
        return b.build();
    }

    /** Item ENERGY capability for the chargers (registered on the mod bus from Abyssia). */
    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new StackEnergy(stack),
                DRILL.get(), CUTTER.get(), PROPULSION_SCREW.get());
    }

    /** Action-bar "energy depleted" message, at most once per 2 seconds per player (server side). */
    public static void emptyMessage(LivingEntity entity)
    {
        if (!(entity instanceof Player player) || player.level().isClientSide) return;
        long now = player.level().getGameTime();
        Long last = LAST_EMPTY_MESSAGE.get(player);
        if (last != null && now - last < 40) return;
        LAST_EMPTY_MESSAGE.put(player, now);
        player.displayClientMessage(Component.translatable("message.abyssia.electric_tool.empty"), true);
    }

    /** Implemented by the electric items (capacity in FE). */
    public interface Electric
    {
        int capacity();
    }
}
