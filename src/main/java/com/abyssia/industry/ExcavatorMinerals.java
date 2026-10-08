package com.abyssia.industry;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ORE01: which excavator tier a deposit's mineral needs, and the Mk2 by-products, in one table. Keys are the deposit
 * mineral ids (ore block paths in the abyssia namespace, OreDeposit#mineralId). The vanilla-drop deposits
 * (abyssal_iron_ore, deep_copper_ore, abyssal_<vanilla>_ore) are Mk1 by default; only the exceptions are listed.
 */
public final class ExcavatorMinerals
{
    private ExcavatorMinerals() {}

    /**
     * ECO02 - Mk1 mines: cobalt, nickel, manganese, titanium, lead, molybdenum, vanadium, zinc (and the vanilla-drop
     * deposits: iron, copper, diamond, ...). Only a Mk2 mines these eight; every metal is its own deposit.
     */
    private static final Map<String, Integer> MIN_TIER = Map.ofEntries(
            Map.entry("tungsten_ore", 2), Map.entry("platinum_ore", 2), Map.entry("tellurium_ore", 2), Map.entry("iridium_ore", 2),
            Map.entry("uranium_ore", 2), Map.entry("neodymium_ore", 2), Map.entry("yttrium_ore", 2), Map.entry("thorium_ore", 2));

    /** A raw metal rolled besides the main output. ECO02: no metal is a by-product any more, the table is empty. */
    public record Byproduct(String rawItem, float chance) {}

    /**
     * What a finished cycle yields: the raw material (the same item the ore dropped before the deposit ores became
     * unbreakable), not the ore block. Deposit ids not listed fall back to their block item.
     */
    private static final Map<String, String> RAW_ITEM = Map.ofEntries(
            Map.entry("manganese_ore", "abyssia:raw_manganese"), Map.entry("cobalt_ore", "abyssia:raw_cobalt"),
            Map.entry("deep_nickel_ore", "abyssia:raw_nickel"),
            Map.entry("titanium_ore", "abyssia:raw_titanium"), Map.entry("lead_ore", "abyssia:raw_lead"),
            Map.entry("zinc_ore", "abyssia:raw_zinc"), Map.entry("iridium_ore", "abyssia:raw_iridium"),
            Map.entry("uranium_ore", "abyssia:raw_uranium"), Map.entry("neodymium_ore", "abyssia:raw_neodymium"),
            Map.entry("thorium_ore", "abyssia:raw_thorium"),
            Map.entry("platinum_ore", "abyssia:raw_platinum"), Map.entry("tellurium_ore", "abyssia:raw_tellurium"),
            Map.entry("molybdenum_ore", "abyssia:raw_molybdenum"), Map.entry("vanadium_ore", "abyssia:raw_vanadium"),
            Map.entry("tungsten_ore", "abyssia:raw_tungsten"), Map.entry("yttrium_ore", "abyssia:raw_yttrium"),
            Map.entry("abyssal_iron_ore", "minecraft:raw_iron"), Map.entry("deep_copper_ore", "minecraft:raw_copper"),
            Map.entry("abyssal_diamond_ore", "minecraft:diamond"), Map.entry("abyssal_gold_ore", "minecraft:raw_gold"),
            Map.entry("abyssal_redstone_ore", "minecraft:redstone"), Map.entry("abyssal_lapis_ore", "minecraft:lapis_lazuli"),
            Map.entry("abyssal_emerald_ore", "minecraft:emerald"), Map.entry("abyssal_quartz_ore", "minecraft:quartz"));

    /** The raw item a cycle on this mineral yields, if it has one. */
    public static Optional<ResourceLocation> rawItem(ResourceLocation mineralId)
    {
        return Optional.ofNullable(RAW_ITEM.get(mineralId.getPath())).map(ResourceLocation::parse);
    }

    /** Whether an excavator can mine this mineral at all (sulfur and the crystals are hand-mined ores, not deposits). */
    public static boolean isMinable(ResourceLocation mineralId)
    {
        return RAW_ITEM.containsKey(mineralId.getPath());
    }

    /** Whether this ore block is a vein ore that only exists as part of an excavator deposit (adoption scan). */
    public static boolean isDepositOnly(net.minecraft.world.level.block.Block block)
    {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return id.getNamespace().equals("abyssia") && isMinable(id);
    }

    /** Minimum {@link ExcavatorTier#tier} needed to mine this mineral. */
    public static int requiredTier(ResourceLocation mineralId)
    {
        return MIN_TIER.getOrDefault(mineralId.getPath(), 1);
    }

    public static boolean canMine(ExcavatorTier tier, ResourceLocation mineralId)
    {
        return tier.tier >= requiredTier(mineralId);
    }

    /** By-products rolled once per finished cycle: none (ECO02). */
    public static List<Byproduct> byproducts(ExcavatorTier tier, ResourceLocation mineralId)
    {
        return List.of();
    }
}
