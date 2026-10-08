package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.block.BrineSurfaceBlock;
import com.abyssia.block.CaveMossBlock;
import com.abyssia.block.FloatingPlantBlock;
import com.abyssia.block.FrondBlock;
import com.abyssia.block.HangingPlantBlock;
import com.abyssia.block.LeaningPlantBlock;
import com.abyssia.block.SpeleothemBlock;
import com.abyssia.block.WallPlantBlock;
import com.abyssia.block.MoltenRockBlock;
import com.abyssia.block.SeafloorCarpetBlock;
import com.abyssia.block.SporeEmitter;
import com.abyssia.block.StackingPlantBlock;
import com.abyssia.block.StrippableLogBlock;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.block.UnderwaterPlantBlock;
import com.abyssia.block.VoidKelpBlock;
import com.abyssia.block.VoidKelpPlantBlock;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.function.Supplier;

/** Every Abyssia block. The deep ocean dimension is built from these alone; no vanilla terrain blocks. */
public final class ModBlocks
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);

    // ---------- Terrain: rocks ----------
    public static final DeferredBlock<Block> DEEP_SEA_ROCK = rock("deep_sea_rock", MapColor.COLOR_GRAY, 1.8f, SoundType.STONE, 0);
    public static final DeferredBlock<Block> ABYSSAL_ROCK = rock("abyssal_rock", MapColor.COLOR_BLACK, 2.5f, SoundType.DEEPSLATE, 0);
    public static final DeferredBlock<Block> TRENCH_ROCK = rock("trench_rock", MapColor.TERRACOTTA_BLUE, 3.0f, SoundType.DEEPSLATE, 0);
    public static final DeferredBlock<Block> THERMAL_ROCK = rock("thermal_rock", MapColor.CRIMSON_NYLIUM, 2.0f, SoundType.BASALT, 3);
    public static final DeferredBlock<Block> VOLCANIC_ROCK = rock("volcanic_rock", MapColor.TERRACOTTA_BLACK, 2.0f, SoundType.BASALT, 0);
    public static final DeferredBlock<Block> MOLTEN_VOLCANIC_ROCK = block("molten_volcanic_rock", () -> new MoltenRockBlock(props(MapColor.NETHER)
            .strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.BASALT).lightLevel(s -> 6)));
    public static final DeferredBlock<Block> VOLCANIC_GLASS = rock("volcanic_glass", MapColor.COLOR_BLACK, 8.0f, SoundType.GLASS, 0);
    public static final DeferredBlock<Block> CRYSTAL_ROCK = rock("crystal_rock", MapColor.COLOR_LIGHT_BLUE, 2.0f, SoundType.CALCITE, 2);
    public static final DeferredBlock<Block> MINERAL_HOST_ROCK = rock("mineral_host_rock", MapColor.TERRACOTTA_BROWN, 2.5f, SoundType.TUFF, 0);

    // ---------- Terrain: cobbled rocks (what the main rocks drop without Silk Touch; smelt back to the rock) ----------
    public static final DeferredBlock<Block> COBBLED_DEEP_SEA_ROCK = rock("cobbled_deep_sea_rock", MapColor.COLOR_GRAY, 1.8f, SoundType.STONE, 0);
    public static final DeferredBlock<Block> COBBLED_ABYSSAL_ROCK = rock("cobbled_abyssal_rock", MapColor.COLOR_BLACK, 2.5f, SoundType.DEEPSLATE, 0);
    public static final DeferredBlock<Block> COBBLED_TRENCH_ROCK = rock("cobbled_trench_rock", MapColor.TERRACOTTA_BLUE, 3.0f, SoundType.DEEPSLATE, 0);
    public static final DeferredBlock<Block> COBBLED_THERMAL_ROCK = rock("cobbled_thermal_rock", MapColor.CRIMSON_NYLIUM, 2.0f, SoundType.BASALT, 3);
    public static final DeferredBlock<Block> COBBLED_VOLCANIC_ROCK = rock("cobbled_volcanic_rock", MapColor.TERRACOTTA_BLACK, 2.0f, SoundType.BASALT, 0);
    public static final DeferredBlock<Block> COBBLED_CRYSTAL_ROCK = rock("cobbled_crystal_rock", MapColor.COLOR_LIGHT_BLUE, 2.0f, SoundType.CALCITE, 2);
    public static final DeferredBlock<Block> COBBLED_MINERAL_HOST_ROCK = rock("cobbled_mineral_host_rock", MapColor.TERRACOTTA_BROWN, 2.5f, SoundType.TUFF, 0);

    // ---------- Terrain: sediments (settled marine snow, thickest in valleys and trenches) ----------
    public static final DeferredBlock<Block> DEEP_SEDIMENT = soft("deep_sediment", MapColor.COLOR_GRAY, SoundType.SAND);
    public static final DeferredBlock<Block> ABYSSAL_MUD = soft("abyssal_mud", MapColor.TERRACOTTA_GRAY, SoundType.MUD);
    public static final DeferredBlock<Block> DEEP_MUD = soft("deep_mud", MapColor.TERRACOTTA_CYAN, SoundType.MUD);
    public static final DeferredBlock<Block> MINERAL_SEDIMENT = soft("mineral_sediment", MapColor.TERRACOTTA_ORANGE, SoundType.SAND);
    public static final DeferredBlock<Block> CRYSTAL_SEDIMENT = soft("crystal_sediment", MapColor.COLOR_CYAN, SoundType.SAND);
    public static final DeferredBlock<Block> ORGANIC_SEDIMENT = soft("organic_sediment", MapColor.TERRACOTTA_GREEN, SoundType.MUD);
    public static final DeferredBlock<Block> VOLCANIC_ASH = soft("volcanic_ash", MapColor.COLOR_GRAY, SoundType.SAND);

    // ---------- Terrain: new biomes (sunken ruins, bone graveyard, brine lakes, glow gardens, frost abyss) ----------
    public static final DeferredBlock<Block> RUIN_GRAVEL = soft("ruin_gravel", MapColor.TERRACOTTA_CYAN, SoundType.GRAVEL);
    public static final DeferredBlock<Block> RUIN_SEDIMENT = soft("ruin_sediment", MapColor.TERRACOTTA_CYAN, SoundType.SAND);
    public static final DeferredBlock<Block> ANCIENT_MASONRY = rock("ancient_masonry", MapColor.TERRACOTTA_CYAN, 2.5f, SoundType.DEEPSLATE_BRICKS, 0);
    public static final DeferredBlock<Block> BONE_SEDIMENT = soft("bone_sediment", MapColor.SAND, SoundType.SAND);
    public static final DeferredBlock<Block> FOSSIL_SILT = soft("fossil_silt", MapColor.TERRACOTTA_WHITE, SoundType.MUD);
    public static final DeferredBlock<Block> FOSSIL_ROCK = rock("fossil_rock", MapColor.TERRACOTTA_WHITE, 2.0f, SoundType.BONE_BLOCK, 0);
    public static final DeferredBlock<Block> SALT_CRUST = soft("salt_crust", MapColor.QUARTZ, SoundType.SAND);
    public static final DeferredBlock<Block> BRINE_SILT = soft("brine_silt", MapColor.TERRACOTTA_PINK, SoundType.MUD);
    public static final DeferredBlock<Block> SALT_ROCK = rock("salt_rock", MapColor.QUARTZ, 1.5f, SoundType.CALCITE, 0);
    public static final DeferredBlock<Block> LUMEN_SAND = soft("lumen_sand", MapColor.COLOR_CYAN, SoundType.SAND);
    public static final DeferredBlock<Block> GLOW_SILT = soft("glow_silt", MapColor.COLOR_GREEN, SoundType.MUD);
    public static final DeferredBlock<Block> LUMEN_ROCK = rock("lumen_rock", MapColor.COLOR_CYAN, 2.0f, SoundType.CALCITE, 4);
    public static final DeferredBlock<Block> FROST_SILT = soft("frost_silt", MapColor.ICE, SoundType.SAND);
    public static final DeferredBlock<Block> ICY_SEDIMENT = soft("icy_sediment", MapColor.ICE, SoundType.SNOW);
    public static final DeferredBlock<Block> FROZEN_ROCK = rock("frozen_rock", MapColor.ICE, 2.5f, SoundType.GLASS, 0);

    // ---------- Hydrothermal vents ----------
    public static final DeferredBlock<Block> THERMAL_VENT = block("thermal_vent", () -> new ThermalVentBlock(props(MapColor.NETHER)
            .strength(1.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.BASALT).lightLevel(ThermalVentBlock::lightLevel)));
    public static final DeferredBlock<Block> VENT_ROCK = rock("vent_rock", MapColor.COLOR_LIGHT_GRAY, 2.0f, SoundType.BASALT, 0);
    public static final DeferredBlock<Block> BLACK_VENT_ROCK = rock("black_vent_rock", MapColor.COLOR_BLACK, 2.0f, SoundType.BASALT, 0);
    public static final DeferredBlock<Block> SULFUR_VENT_ROCK = rock("sulfur_vent_rock", MapColor.COLOR_YELLOW, 2.0f, SoundType.BASALT, 0);
    public static final DeferredBlock<Block> MINERAL_VENT_ROCK = rock("mineral_vent_rock", MapColor.TERRACOTTA_ORANGE, 2.0f, SoundType.BASALT, 0);
    public static final DeferredBlock<Block> SULFUR_DEPOSIT = block("sulfur_deposit", () -> new Block(props(MapColor.COLOR_YELLOW)
            .strength(0.8f).requiresCorrectToolForDrops().sound(SoundType.TUFF)));
    public static final DeferredBlock<Block> BLACK_MINERAL_DEPOSIT = block("black_mineral_deposit", () -> new Block(props(MapColor.COLOR_BLACK)
            .strength(1.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK)));

    // ---------- Ores ----------
    public static final DeferredBlock<Block> ABYSSAL_IRON_ORE = ore("abyssal_iron_ore", 0, 0, 0);
    public static final DeferredBlock<Block> DEEP_COPPER_ORE = ore("deep_copper_ore", 0, 0, 0);
    public static final DeferredBlock<Block> SULFUR_ORE = ore("sulfur_ore", 1, 3, 0);
    public static final DeferredBlock<Block> THERMAL_CRYSTAL_ORE = ore("thermal_crystal_ore", 2, 5, 3);
    public static final DeferredBlock<Block> ABYSSAL_CRYSTAL_ORE = ore("abyssal_crystal_ore", 2, 5, 3);
    public static final DeferredBlock<Block> MANGANESE_ORE = deposit("manganese_ore", 0);
    public static final DeferredBlock<Block> COBALT_ORE = deposit("cobalt_ore", 0);
    public static final DeferredBlock<Block> DEEP_NICKEL_ORE = deposit("deep_nickel_ore", 0);
    // Rare metals: tiny, very rare veins in the deep biomes (tools/gen_worldgen.py RARE_VEINS)
    public static final DeferredBlock<Block> PLATINUM_ORE = deposit("platinum_ore", 0);
    public static final DeferredBlock<Block> TELLURIUM_ORE = deposit("tellurium_ore", 0);
    public static final DeferredBlock<Block> MOLYBDENUM_ORE = deposit("molybdenum_ore", 0);
    public static final DeferredBlock<Block> VANADIUM_ORE = deposit("vanadium_ore", 0);
    public static final DeferredBlock<Block> TUNGSTEN_ORE = deposit("tungsten_ore", 0);
    public static final DeferredBlock<Block> YTTRIUM_ORE = deposit("yttrium_ore", 0);
    public static final DeferredBlock<Block> TITANIUM_ORE = deposit("titanium_ore", 0);
    public static final DeferredBlock<Block> LEAD_ORE = deposit("lead_ore", 0);
    public static final DeferredBlock<Block> ZINC_ORE = deposit("zinc_ore", 0);
    public static final DeferredBlock<Block> IRIDIUM_ORE = deposit("iridium_ore", 0);
    public static final DeferredBlock<Block> URANIUM_ORE = deposit("uranium_ore", 0);
    public static final DeferredBlock<Block> NEODYMIUM_ORE = deposit("neodymium_ore", 0);
    public static final DeferredBlock<Block> THORIUM_ORE = deposit("thorium_ore", 0);
    // Vanilla minerals in deep-sea form: seabed veins (tools/gen_worldgen.py VANILLA_VEINS), vanilla drops and XP
    public static final DeferredBlock<Block> ABYSSAL_DIAMOND_ORE = ore("abyssal_diamond_ore", 3, 7, 0);
    public static final DeferredBlock<Block> ABYSSAL_GOLD_ORE = ore("abyssal_gold_ore", 0, 0, 0);
    public static final DeferredBlock<Block> ABYSSAL_REDSTONE_ORE = ore("abyssal_redstone_ore", 1, 5, 0);
    public static final DeferredBlock<Block> ABYSSAL_LAPIS_ORE = ore("abyssal_lapis_ore", 2, 5, 0);
    public static final DeferredBlock<Block> ABYSSAL_EMERALD_ORE = ore("abyssal_emerald_ore", 3, 7, 0);
    public static final DeferredBlock<Block> ABYSSAL_QUARTZ_ORE = ore("abyssal_quartz_ore", 2, 5, 0);

    // Vein crusts were removed (ORE01); salt_crust and cave_mineral_crust below are unrelated terrain blocks.

    // ---------- Mineral clusters and crystals ----------
    public static final DeferredBlock<Block> MANGANESE_NODULES = block("manganese_nodules", () -> depositCrystal(MapColor.COLOR_BLACK, 4, 4, 0));
    public static final DeferredBlock<Block> COBALT_CLUSTER = block("cobalt_cluster", () -> depositCrystal(MapColor.COLOR_BLUE, 6, 3, 0));
    public static final DeferredBlock<Block> NICKEL_CLUSTER = block("nickel_cluster", () -> depositCrystal(MapColor.COLOR_LIGHT_GREEN, 6, 3, 0));
    public static final DeferredBlock<Block> SULFUR_CLUSTER = block("sulfur_cluster", () -> crystal(MapColor.COLOR_YELLOW, 5, 3, 0));
    public static final DeferredBlock<Block> ABYSSAL_CRYSTAL_CLUSTER = block("abyssal_crystal_cluster", () -> crystal(MapColor.COLOR_PURPLE, 7, 3, 5));
    public static final DeferredBlock<Block> DEEP_CRYSTAL_BLOCK = block("deep_crystal_block", () -> new Block(props(MapColor.COLOR_CYAN)
            .strength(1.5f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST).lightLevel(s -> 8)));
    public static final DeferredBlock<Block> DEEP_CRYSTAL_CLUSTER = block("deep_crystal_cluster", () -> crystal(MapColor.COLOR_CYAN, 7, 3, 6));
    public static final DeferredBlock<Block> PRESSURE_CRYSTAL_CLUSTER = block("pressure_crystal_cluster", () -> crystal(MapColor.COLOR_PURPLE, 7, 3, 4));
    // Thermal crystal grows from bud to cluster the closer it is to a vent.
    public static final DeferredBlock<Block> SMALL_THERMAL_CRYSTAL_BUD = block("small_thermal_crystal_bud", () -> crystal(MapColor.COLOR_ORANGE, 3, 4, 1));
    public static final DeferredBlock<Block> MEDIUM_THERMAL_CRYSTAL_BUD = block("medium_thermal_crystal_bud", () -> crystal(MapColor.COLOR_ORANGE, 4, 3, 2));
    public static final DeferredBlock<Block> THERMAL_CRYSTAL_CLUSTER = block("thermal_crystal_cluster", () -> crystal(MapColor.COLOR_ORANGE, 7, 3, 5));

    // ---------- Plants: low layer ----------
    public static final DeferredBlock<Block> ABYSSAL_GRASS = stacking("abyssal_grass", MapColor.PLANT, 12, false, SporeEmitter.NONE, 0);
    public static final DeferredBlock<Block> TEAL_ABYSSAL_GRASS = stacking("teal_abyssal_grass", MapColor.COLOR_CYAN, 12, false, SporeEmitter.NONE, 0);
    public static final DeferredBlock<Block> VIOLET_ABYSSAL_GRASS = stacking("violet_abyssal_grass", MapColor.COLOR_PURPLE, 12, false, SporeEmitter.NONE, 0);
    public static final DeferredBlock<Block> ASHEN_ABYSSAL_GRASS = stacking("ashen_abyssal_grass", MapColor.COLOR_GRAY, 12, false, SporeEmitter.NONE, 0);
    public static final DeferredBlock<Block> GLOWTIP_GRASS = plant("glowtip_grass", MapColor.COLOR_LIGHT_BLUE, 2, SporeEmitter.GLOW_DUST);
    public static final DeferredBlock<Block> SEA_FERN = plant("sea_fern", MapColor.PLANT, 0, SporeEmitter.SPORES);
    public static final DeferredBlock<Block> ABYSSAL_MOSS = carpet("abyssal_moss", MapColor.PLANT, false, 0);
    public static final DeferredBlock<Block> SEAFLOOR_PEBBLES = carpet("seafloor_pebbles", MapColor.STONE, false, 0);

    // ---------- Plants: medium layer ----------
    public static final DeferredBlock<Block> TUBE_PLANT = stacking("tube_plant", MapColor.COLOR_PINK, 8, false, SporeEmitter.SPORES, 0);
    public static final DeferredBlock<Block> SPONGE_PLANT = plant("sponge_plant", MapColor.COLOR_ORANGE, 0, SporeEmitter.NONE);
    public static final DeferredBlock<Block> CRYSTAL_PLANT = plant("crystal_plant", MapColor.COLOR_CYAN, 4, SporeEmitter.GLOW_DUST);

    // ---------- Plants: high and canopy layers ----------
    public static final DeferredBlock<Block> DEEP_KELP = stacking("deep_kelp", MapColor.COLOR_GREEN, 12, false, SporeEmitter.NONE, 0);
    public static final DeferredBlock<Block> GIANT_KELP = stacking("giant_kelp", MapColor.COLOR_GREEN, 14, false, SporeEmitter.NONE, 0);
    public static final DeferredBlock<Block> GIANT_TUBE = stacking("giant_tube", MapColor.COLOR_MAGENTA, 14, false, SporeEmitter.SPORES, 0);
    public static final DeferredBlock<Block> VOID_KELP = block("void_kelp", () -> new VoidKelpBlock(kelpProps()));
    public static final DeferredBlock<Block> VOID_KELP_PLANT = BLOCKS.register("void_kelp_plant", () -> new VoidKelpPlantBlock(kelpProps()));
    public static final DeferredBlock<Block> FLOATING_BLOOM = block("floating_bloom", () -> new FloatingPlantBlock(plantProps(MapColor.COLOR_LIGHT_BLUE, 4), SporeEmitter.GLOW_DUST));

    // ---------- Plants: thermal (heat-adapted, around vents) ----------
    public static final DeferredBlock<Block> VENT_GRASS = plant("vent_grass", MapColor.COLOR_YELLOW, 0, SporeEmitter.NONE);
    public static final DeferredBlock<Block> THERMAL_TUBE = stacking("thermal_tube", MapColor.COLOR_RED, 8, false, SporeEmitter.NONE, 3);
    public static final DeferredBlock<Block> HEAT_MOSS = carpet("heat_moss", MapColor.COLOR_ORANGE, true, 1);
    public static final DeferredBlock<Block> MINERAL_VINE = stacking("mineral_vine", MapColor.TERRACOTTA_ORANGE, 10, true, SporeEmitter.NONE, 0);

    // ---------- Plants: glowing and deep-zone species ----------
    public static final DeferredBlock<Block> GLOW_ANEMONE = plant("glow_anemone", MapColor.COLOR_PINK, 3, SporeEmitter.GLOW_DUST);
    public static final DeferredBlock<Block> GLOW_CORAL = plant("glow_coral", MapColor.COLOR_YELLOW, 3, SporeEmitter.NONE);
    public static final DeferredBlock<Block> ABYSSAL_MUSHROOM = plant("abyssal_mushroom", MapColor.COLOR_CYAN, 5, SporeEmitter.SPORES);
    public static final DeferredBlock<Block> ABYSSAL_BLOOM = plant("abyssal_bloom", MapColor.COLOR_LIGHT_BLUE, 6, SporeEmitter.GLOW_DUST);
    public static final DeferredBlock<Block> SOUL_CORAL = plant("soul_coral", MapColor.COLOR_LIGHT_BLUE, 4, SporeEmitter.NONE);
    public static final DeferredBlock<Block> BLACK_CORAL = plant("black_coral", MapColor.COLOR_BLACK, 0, SporeEmitter.NONE);
    public static final DeferredBlock<Block> HADAL_BLOOM = plant("hadal_bloom", MapColor.COLOR_PURPLE, 7, SporeEmitter.GLOW_DUST);

    // ---------- Caves: rock and soil. Cave walls are painted with these in geological layers, never vanilla stone ----------
    public static final DeferredBlock<Block> ABYSSAL_CAVE_ROCK = rock("abyssal_cave_rock", MapColor.COLOR_BLACK, 2.5f, SoundType.DEEPSLATE, 0);
    public static final DeferredBlock<Block> DARK_CAVE_ROCK = rock("dark_cave_rock", MapColor.TERRACOTTA_BLACK, 3.0f, SoundType.DEEPSLATE, 0);
    public static final DeferredBlock<Block> WET_CAVE_ROCK = rock("wet_cave_rock", MapColor.COLOR_GRAY, 2.0f, SoundType.STONE, 0);
    public static final DeferredBlock<Block> LAYERED_CAVE_ROCK = rock("layered_cave_rock", MapColor.TERRACOTTA_LIGHT_GRAY, 2.2f, SoundType.TUFF, 0);
    public static final DeferredBlock<Block> MINERAL_CAVE_ROCK = rock("mineral_cave_rock", MapColor.TERRACOTTA_BROWN, 2.5f, SoundType.TUFF, 0);
    // Thermal and crystal cave rock glow through emissive texture overlays, not block light: caves stay dark and
    // walls full of them add no lighting work.
    public static final DeferredBlock<Block> THERMAL_CAVE_ROCK = rock("thermal_cave_rock", MapColor.TERRACOTTA_RED, 2.2f, SoundType.BASALT, 0);
    public static final DeferredBlock<Block> CRYSTAL_CAVE_ROCK = rock("crystal_cave_rock", MapColor.COLOR_CYAN, 2.0f, SoundType.CALCITE, 0);
    public static final DeferredBlock<Block> ORGANIC_CAVE_ROCK = rock("organic_cave_rock", MapColor.TERRACOTTA_GREEN, 1.8f, SoundType.MUD_BRICKS, 0);
    public static final DeferredBlock<Block> ERODED_CAVE_ROCK = rock("eroded_cave_rock", MapColor.TERRACOTTA_CYAN, 2.0f, SoundType.STONE, 0);
    public static final DeferredBlock<Block> CAVE_SEDIMENT = soft("cave_sediment", MapColor.COLOR_LIGHT_GRAY, SoundType.SAND);
    public static final DeferredBlock<Block> CAVE_MUD = soft("cave_mud", MapColor.TERRACOTTA_BROWN, SoundType.MUD);
    public static final DeferredBlock<Block> CAVE_MINERAL_CRUST = crust("cave_mineral_crust", MapColor.TERRACOTTA_ORANGE);

    // ---------- Caves: speleothems (tip_direction down = stalactite, up = stalagmite) ----------
    public static final DeferredBlock<Block> ABYSSAL_STALACTITE = speleothem("abyssal_stalactite", MapColor.COLOR_BLACK, 0);
    public static final DeferredBlock<Block> MINERAL_STALACTITE = speleothem("mineral_stalactite", MapColor.TERRACOTTA_ORANGE, 0);
    public static final DeferredBlock<Block> CRYSTAL_STALACTITE = speleothem("crystal_stalactite", MapColor.COLOR_CYAN, 4);
    public static final DeferredBlock<Block> THERMAL_STALACTITE = speleothem("thermal_stalactite", MapColor.COLOR_RED, 0);
    public static final DeferredBlock<Block> CRYSTAL_NEEDLE = block("crystal_needle", () -> crystal(MapColor.COLOR_LIGHT_BLUE, 15, 6, 3));

    // ---------- Caves: floor plants ----------
    public static final DeferredBlock<Block> CAVE_GRASS = stacking("cave_grass", MapColor.COLOR_GREEN, 12, false, SporeEmitter.NONE, 0);
    public static final DeferredBlock<Block> CAVE_FERN = plant("cave_fern", MapColor.PLANT, 0, SporeEmitter.SPORES);
    public static final DeferredBlock<Block> CAVE_TUBE_PLANT = stacking("cave_tube_plant", MapColor.COLOR_PINK, 8, false, SporeEmitter.SPORES, 0);
    public static final DeferredBlock<Block> CAVE_CORAL = plant("cave_coral", MapColor.COLOR_ORANGE, 0, SporeEmitter.NONE);
    public static final DeferredBlock<Block> CAVE_SPONGE = plant("cave_sponge", MapColor.COLOR_YELLOW, 0, SporeEmitter.NONE);
    public static final DeferredBlock<Block> CAVE_KELP = stacking("cave_kelp", MapColor.COLOR_GREEN, 12, false, SporeEmitter.NONE, 0);
    // Faintly luminous (block light 3-5) and strongly luminous (9-11) species: only a small share of cave flora.
    public static final DeferredBlock<Block> CAVE_CRYSTAL_PLANT = plant("cave_crystal_plant", MapColor.COLOR_CYAN, 5, SporeEmitter.GLOW_DUST);
    public static final DeferredBlock<Block> CAVE_BLOOM = plant("cave_bloom", MapColor.COLOR_LIGHT_BLUE, 10, SporeEmitter.GLOW_DUST);
    public static final DeferredBlock<Block> THERMAL_PLANT = plant("thermal_plant", MapColor.COLOR_RED, 3, SporeEmitter.SPORES);
    // Giants of large caverns.
    public static final DeferredBlock<Block> GIANT_CAVE_KELP = block("giant_cave_kelp", () -> new LeaningPlantBlock(plantProps(MapColor.COLOR_GREEN, 0), 14, false, SporeEmitter.NONE));
    public static final DeferredBlock<Block> ANCIENT_CAVE_PLANT = stacking("ancient_cave_plant", MapColor.TERRACOTTA_CYAN, 12, false, SporeEmitter.SPORES, 0);

    // ---------- Caves: ceiling plants (hang down in columns; the tip is the lowest block) ----------
    public static final DeferredBlock<Block> CAVE_VINE = hanging("cave_vine", MapColor.PLANT, 10, SporeEmitter.GLOW_DUST, 4);
    public static final DeferredBlock<Block> HANGING_KELP = hanging("hanging_kelp", MapColor.COLOR_GREEN, 12, SporeEmitter.NONE, 0);
    public static final DeferredBlock<Block> CAVE_ROOT = hanging("cave_root", MapColor.TERRACOTTA_BROWN, 10, SporeEmitter.NONE, 0);
    public static final DeferredBlock<Block> ABYSSAL_VINE = hanging("abyssal_vine", MapColor.COLOR_PURPLE, 10, SporeEmitter.GLOW_DUST, 9);
    public static final DeferredBlock<Block> DEEP_ROOT = hanging("deep_root", MapColor.TERRACOTTA_BROWN, 14, SporeEmitter.NONE, 0);

    // ---------- Caves: wall plants and films ----------
    public static final DeferredBlock<Block> WALL_FERN = wall("wall_fern", MapColor.PLANT, SporeEmitter.SPORES);
    public static final DeferredBlock<Block> WALL_MINERAL_VINE = wall("wall_mineral_vine", MapColor.TERRACOTTA_ORANGE, SporeEmitter.NONE);
    public static final DeferredBlock<Block> CAVE_MOSS = block("cave_moss", () -> new CaveMossBlock(props(MapColor.PLANT).replaceable()
            .noCollission().strength(0.2f).sound(SoundType.GLOW_LICHEN).pushReaction(PushReaction.DESTROY)));

    // ---------- Caves: loose debris from collapses ----------
    public static final DeferredBlock<Block> CAVE_RUBBLE = carpet("cave_rubble", MapColor.STONE, true, 0);
    public static final DeferredBlock<Block> CRYSTAL_SHARDS = carpet("crystal_shards", MapColor.COLOR_CYAN, true, 0);
    public static final DeferredBlock<Block> FALLEN_KELP = carpet("fallen_kelp", MapColor.COLOR_GREEN, true, 0);

    // ---------- Caverns: giant plants, lakes, ceiling light and coloured crystal ----------
    public static final DeferredBlock<Block> CRYSTAL_KELP = stacking("crystal_kelp", MapColor.COLOR_CYAN, 12, false, SporeEmitter.GLOW_DUST, 0);
    // A log: an axe strips it (see ModBuildingBlocks for the rest of its wood set).
    public static final DeferredBlock<Block> ANCIENT_STEM = block("ancient_stem", () -> new StrippableLogBlock(
            props(MapColor.TERRACOTTA_CYAN).strength(2.5f).sound(SoundType.STEM), () -> ModBuildingBlocks.STRIPPED_ANCIENT_STEM.get()));
    public static final DeferredBlock<Block> ANCIENT_ROOT = block("ancient_root", () -> new Block(props(MapColor.TERRACOTTA_BROWN).strength(2.0f).sound(SoundType.ROOTS)));
    public static final DeferredBlock<Block> ANCIENT_FROND = block("ancient_frond", () -> new FrondBlock(props(MapColor.COLOR_GREEN).strength(0.3f)
            .sound(SoundType.WET_GRASS).noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));
    // OL01: no block item; oil_kelp_seed places it
    public static final DeferredBlock<Block> OIL_KELP = BLOCKS.register("oil_kelp", () -> new com.abyssia.block.OilKelpBlock(plantProps(MapColor.COLOR_BROWN, 0)
            .lightLevel(s -> s.getValue(com.abyssia.block.OilKelpBlock.RIPE) ? 4 : 0)));   // OL02: ripe sac glows
    public static final DeferredBlock<Block> ANCIENT_SAPLING = block("ancient_sapling", () -> new com.abyssia.block.AncientSaplingBlock(
            plantProps(MapColor.TERRACOTTA_CYAN, 0)));
    // Glows only through its emissive texture (no block light), so it sprinkles cavern roofs with points of light without lighting them.
    public static final DeferredBlock<Block> LUMINOUS_MOSS = block("luminous_moss", () -> new CaveMossBlock(props(MapColor.COLOR_LIGHT_BLUE).replaceable()
            .noCollission().strength(0.2f).sound(SoundType.GLOW_LICHEN).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<Block> BRINE_SURFACE = block("brine_surface", () -> new BrineSurfaceBlock(props(MapColor.WATER).noCollission()
            .instabreak().noOcclusion().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<Block> CYAN_CRYSTAL_BLOCK = crystalBlock("cyan_crystal_block", MapColor.COLOR_CYAN);
    public static final DeferredBlock<Block> BLUE_CRYSTAL_BLOCK = crystalBlock("blue_crystal_block", MapColor.COLOR_BLUE);
    public static final DeferredBlock<Block> VIOLET_CRYSTAL_BLOCK = crystalBlock("violet_crystal_block", MapColor.COLOR_PURPLE);
    public static final DeferredBlock<Block> GREEN_CRYSTAL_BLOCK = crystalBlock("green_crystal_block", MapColor.COLOR_LIGHT_GREEN);
    public static final DeferredBlock<Block> WHITE_CRYSTAL_BLOCK = crystalBlock("white_crystal_block", MapColor.SNOW);
    public static final DeferredBlock<Block> AMBER_CRYSTAL_BLOCK = crystalBlock("amber_crystal_block", MapColor.COLOR_ORANGE);
    public static final DeferredBlock<Block> PALE_CRYSTAL_CLUSTER = block("pale_crystal_cluster", () -> crystal(MapColor.SNOW, 7, 3, 3));

    private ModBlocks() {}

    public static void register(IEventBus modBus)
    {
        ModBuildingBlocks.init();
        ModPlants.init();
        BLOCKS.register(modBus);
    }

    private static BlockBehaviour.Properties props(MapColor color)
    {
        return BlockBehaviour.Properties.of().mapColor(color);
    }

    static BlockBehaviour.Properties plantProps(MapColor color, int light)
    {
        return props(color).noCollission().instabreak().sound(SoundType.WET_GRASS).lightLevel(s -> light).pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties kelpProps()
    {
        return props(MapColor.WATER).noCollission().randomTicks().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY);
    }

    private static DeferredBlock<Block> rock(String name, MapColor color, float hardness, SoundType sound, int light)
    {
        return block(name, () -> new Block(props(color).strength(hardness, 6.0f).requiresCorrectToolForDrops().sound(sound).lightLevel(s -> light)));
    }

    private static DeferredBlock<Block> soft(String name, MapColor color, SoundType sound)
    {
        return block(name, () -> new Block(props(color).strength(0.5f).sound(sound)));
    }

    private static DeferredBlock<Block> ore(String name, int minXp, int maxXp, int light)
    {
        return block(name, () -> new DropExperienceBlock(UniformInt.of(minXp, maxXp), props(MapColor.COLOR_BLACK).strength(3.0f, 3.0f).requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE).lightLevel(s -> light)));
    }

    private static DeferredBlock<Block> crust(String name, MapColor color)
    {
        return block(name, () -> new Block(props(color).strength(1.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.TUFF)));
    }

    /** ORE01: a mineral only the excavator can take - a visible deposit that survival players cannot break. */
    private static DeferredBlock<Block> deposit(String name, int light)
    {
        return block(name, () -> new Block(props(MapColor.COLOR_BLACK).strength(-1.0f, 3_600_000.0f).sound(SoundType.DEEPSLATE).lightLevel(s -> light)));
    }

    private static Block depositCrystal(MapColor color, int height, int offset, int light)
    {
        return new AmethystClusterBlock(height, offset, props(color).forceSolidOn().noOcclusion().strength(-1.0f, 3_600_000.0f)
                .sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> light).pushReaction(PushReaction.BLOCK));
    }

    private static Block crystal(MapColor color, int height, int offset, int light)
    {
        return new AmethystClusterBlock(height, offset, props(color).forceSolidOn().noOcclusion().strength(1.5f)
                .sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> light).pushReaction(PushReaction.DESTROY));
    }

    private static DeferredBlock<Block> plant(String name, MapColor color, int light, SporeEmitter spores)
    {
        return block(name, () -> new UnderwaterPlantBlock(plantProps(color, light), spores));
    }

    private static DeferredBlock<Block> stacking(String name, MapColor color, double width, boolean anySubstrate, SporeEmitter spores, int light)
    {
        return block(name, () -> new StackingPlantBlock(plantProps(color, light), width, anySubstrate, spores));
    }

    private static DeferredBlock<Block> carpet(String name, MapColor color, boolean anySubstrate, int light)
    {
        return block(name, () -> new SeafloorCarpetBlock(plantProps(color, light), anySubstrate));
    }

    /** Crystal masses for cavern crystal forests: emissive texture, no block light, so forests stay dim. */
    private static DeferredBlock<Block> crystalBlock(String name, MapColor color)
    {
        return block(name, () -> new Block(props(color).strength(1.5f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST)));
    }

    private static DeferredBlock<Block> speleothem(String name, MapColor color, int light)
    {
        return block(name, () -> new SpeleothemBlock(props(color).forceSolidOn().noOcclusion().strength(1.5f, 3.0f)
                .sound(SoundType.POINTED_DRIPSTONE).dynamicShape().lightLevel(s -> light).pushReaction(PushReaction.DESTROY)));
    }

    /** Only the tip glows (bulbs, luminous ends); the rest of the column is dark. */
    private static DeferredBlock<Block> hanging(String name, MapColor color, double width, SporeEmitter spores, int tipLight)
    {
        return block(name, () -> new HangingPlantBlock(props(color).noCollission().instabreak().sound(SoundType.WET_GRASS)
                .lightLevel(s -> s.getValue(HangingPlantBlock.TIP) ? tipLight : 0).pushReaction(PushReaction.DESTROY), width, spores));
    }

    private static DeferredBlock<Block> wall(String name, MapColor color, SporeEmitter spores)
    {
        return block(name, () -> new WallPlantBlock(plantProps(color, 0), spores));
    }

    static <T extends Block> DeferredBlock<T> block(String name, Supplier<T> factory)
    {
        DeferredBlock<T> block = BLOCKS.register(name, factory);
        ModItems.blockItem(name, block);
        return block;
    }
}
