package com.abyssia.registry;

import com.abyssia.Abyssia;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags
{
    /** Ores, crusts and hot minerals that plants cannot root in, keeping exposed veins visible. */
    public static final TagKey<Block> INHIBITS_PLANTS = block("inhibits_plants");
    /** Deep ocean terrain that ore veins and rock spires may replace. */
    public static final TagKey<Block> VEIN_REPLACEABLE = block("vein_replaceable");

    // Habitats the fauna spawn rules and AI look for (fauna spawn rules may name any block tag).
    public static final TagKey<Block> FAUNA_ROCK = block("fauna/rock");
    public static final TagKey<Block> FAUNA_SOFT_SEDIMENT = block("fauna/soft_sediment");

    /** Small fish an anglerfish engulfs when they come within reach of its jaws. */
    public static final TagKey<EntityType<?>> ANGLERFISH_PREY = entity("anglerfish_prey");
    /** Small fish drawn toward bioluminescent lures in the dark. */
    public static final TagKey<EntityType<?>> LURED_BY_LIGHT = entity("lured_by_light");
    /** Small animals a gulper eel engulfs with its pouch (small crustaceans, when the mod has them). */
    public static final TagKey<EntityType<?>> GULPER_EEL_PREY = entity("gulper_eel_prey");
    public static final TagKey<EntityType<?>> VIPERFISH_PREY = entity("viperfish_prey");
    public static final TagKey<EntityType<?>> GOBLIN_SHARK_PREY = entity("goblin_shark_prey");
    public static final TagKey<EntityType<?>> FRILLED_SHARK_PREY = entity("frilled_shark_prey");
    public static final TagKey<EntityType<?>> GIANT_SQUID_PREY = entity("giant_squid_prey");
    /** Predators the small animals of the midwater and the vents shy away from. */
    public static final TagKey<EntityType<?>> SMALL_FAUNA_THREATS = entity("small_fauna_threats");
    /** Larger predators that turn toward a jelly's "burglar alarm" light display. */
    public static final TagKey<EntityType<?>> ALARM_RESPONDERS = entity("alarm_responders");
    public static final TagKey<Block> FAUNA_VENT = block("fauna/vent");
    /** Entities the ocean current never carries (drifting medusae already ride their own regional current). */
    public static final TagKey<EntityType<?>> IGNORES_OCEAN_CURRENT = entity("ignores_ocean_current");

    /** Carrion a giant isopod scavenges from the seabed. */
    public static final TagKey<Item> ISOPOD_FOOD = item("isopod_food");
    /** Edible fruits / vegetables a hydro planter grows besides edible plant-block items (PlanterCrop.GENERIC). */
    public static final TagKey<Item> PLANTER_CROPS = item("planter_crops");

    /** Crystal blocks the crystal pickaxe breaks twice as fast (material system crystal_harvest). */
    public static final TagKey<Block> CRYSTAL_BLOCKS = block("crystal_blocks");
    /** Gear exempt from the future hadal pressure damage (pressure_diver_helmet). */
    public static final TagKey<Item> PRESSURE_PROOF = item("pressure_proof");

    /** Abyssia's fish (not eels, sharks or jellies): thermal_catch cooks their meat drops. */
    public static final TagKey<EntityType<?>> FISH = entity("fish");

    private ModTags() {}

    private static TagKey<Block> block(String name)
    {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name));
    }

    static TagKey<EntityType<?>> entity(String name)
    {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name));
    }

    private static TagKey<Item> item(String name)
    {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name));
    }
}
