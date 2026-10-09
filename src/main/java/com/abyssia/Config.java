package com.abyssia;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;

/** Common (server-synced) options for the deep ocean, terrain, ores and thermal vents. */
public class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("deep_ocean");
    }

    public static final ModConfigSpec.IntValue CONFIG_VERSION = BUILDER
            .comment("Internal: config file version, used to upgrade old files. Do not edit.")
            .defineInRange("config_version", 1, 1, 100);

    public static final ModConfigSpec.BooleanValue DEEP_OCEAN_ENABLE_FOG = BUILDER
            .comment("Client: thicken and darken underwater fog with depth, down through the deep layer below the bedrock band")
            .define("enable_fog", true);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Deep layer terrain (below the bedrock band). Generation options take effect for newly generated chunks.").push("terrain");
    }

    public static final ModConfigSpec.BooleanValue CUSTOM_BLOCKS_ONLY = BUILDER
            .comment("Build deep ocean terrain only from Abyssia blocks. If false, terrain blocks are swapped for rough vanilla equivalents")
            .define("custom_blocks_only", true);

    static {
        BUILDER.pop();
        BUILDER.comment("Deep ocean ore veins").push("ore");
    }

    public static final ModConfigSpec.BooleanValue SURFACE_VEINS_ENABLED = BUILDER
            .comment("Allow veins to break through the seabed so they can be spotted from a distance")
            .define("surface_veins_enabled", true);
    public static final ModConfigSpec.DoubleValue LARGE_VEIN_MULTIPLIER = BUILDER
            .comment("Size multiplier for large and huge veins")
            .defineInRange("large_vein_multiplier", 1.0, 0.1, 3.0);
    public static final ModConfigSpec.DoubleValue EXPOSED_VEIN_CHANCE = BUILDER
            .comment("Chance a vein is exposed on the seabed (large veins are more likely to be)")
            .defineInRange("exposed_vein_chance", 0.15, 0.0, 1.0);
    public static final ModConfigSpec.IntValue SMALL_VEIN_MIN = veinSize("small_vein_min", 5);
    public static final ModConfigSpec.IntValue SMALL_VEIN_MAX = veinSize("small_vein_max", 15);
    public static final ModConfigSpec.IntValue MEDIUM_VEIN_MIN = veinSize("medium_vein_min", 15);
    public static final ModConfigSpec.IntValue MEDIUM_VEIN_MAX = veinSize("medium_vein_max", 40);
    public static final ModConfigSpec.IntValue LARGE_VEIN_MIN = veinSize("large_vein_min", 40);
    public static final ModConfigSpec.IntValue LARGE_VEIN_MAX = veinSize("large_vein_max", 100);
    public static final ModConfigSpec.IntValue HUGE_VEIN_MIN = veinSize("huge_vein_min", 100);
    public static final ModConfigSpec.IntValue HUGE_VEIN_MAX = veinSize("huge_vein_max", 300);

    static {
        BUILDER.pop();
        BUILDER.comment("AB02 ore veins inside the solid abyss crust (Y -376 and below), by excavator tier. Multipliers scale how many veins "
                + "form (0 = none, fractions are resolved randomly). Take effect for newly generated chunks.").push("crust_ore");
    }

    public static final ModConfigSpec.DoubleValue CRUST_ORE_DENSITY = BUILDER
            .comment("Global multiplier on all crust ore veins")
            .defineInRange("density", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue CRUST_MK0_ORE = BUILDER
            .comment("MK0 crust ores (iron, copper, gold, redstone, lapis, diamond, emerald)")
            .defineInRange("mk0_ore", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue CRUST_MK1_ORE = BUILDER
            .comment("MK1 crust ores (cobalt, nickel, manganese, titanium, lead, molybdenum, vanadium, zinc)")
            .defineInRange("mk1_ore", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue CRUST_MK2_ORE = BUILDER
            .comment("MK2 crust ores (tungsten, platinum, tellurium, iridium, uranium, neodymium, yttrium, thorium)")
            .defineInRange("mk2_ore", 1.0, 0.0, 10.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Deep ocean vegetation").push("vegetation");
    }

    public static final ModConfigSpec.BooleanValue VEGETATION_ENABLED = BUILDER.define("enabled", true);
    public static final ModConfigSpec.DoubleValue VEGETATION_DENSITY = BUILDER
            .comment("Multiplier for how many plant patches generate")
            .defineInRange("density_multiplier", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue GIANT_PLANT_CHANCE = BUILDER
            .comment("How often giant kelp, giant tubes and other canopy plants appear (0.1 = default amount)")
            .defineInRange("giant_plant_chance", 0.1, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue GLOWING_PLANT_CHANCE = BUILDER
            .comment("How often glowing plant patches appear (0.03 = default amount)")
            .defineInRange("glowing_plant_chance", 0.03, 0.0, 0.3);
    public static final ModConfigSpec.BooleanValue ABYSSAL_FORESTS = BUILDER
            .comment("Giant kelp and giant tube forests")
            .define("abyssal_forests", true);

    static {
        BUILDER.pop();
        BUILDER.push("crystal");
    }

    public static final ModConfigSpec.BooleanValue CRYSTAL_FIELDS = BUILDER
            .comment("Crystal spires, gardens, clusters and crystal caves")
            .define("enabled", true);

    static {
        BUILDER.pop();
        BUILDER.push("thermal");
    }

    public static final ModConfigSpec.BooleanValue THERMAL_VEGETATION = BUILDER
            .comment("Heat-adapted plants in rings around thermal vents")
            .define("thermal_vegetation_enabled", true);

    static {
        BUILDER.pop();
        BUILDER.comment("Deep ocean cave network (cave types, landmarks and their palettes are datapack data: abyssia/cave_profile and",
                "abyssia/cave_environment). Generation options take effect for newly generated chunks; the spacing changes the whole layout.").push("caves");
    }

    public static final ModConfigSpec.BooleanValue CAVES_ENABLED = BUILDER.define("enabled", true);
    public static final ModConfigSpec.IntValue CAVE_SYSTEM_SPACING = BUILDER
            .comment("Grid spacing of cave systems in blocks; each cell holds at most one system")
            .defineInRange("system_spacing", 176, 96, 512);
    public static final ModConfigSpec.DoubleValue CAVE_SYSTEM_CHANCE = BUILDER
            .comment("Multiplier on each biome's chance that a cell holds a cave system")
            .defineInRange("system_chance_multiplier", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue MINOR_CAVE_CHANCE = BUILDER
            .comment("Multiplier on the chance of small sea caves and sea arches between the systems")
            .defineInRange("minor_cave_chance_multiplier", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue LARGE_CAVE_WEIGHT = BUILDER
            .comment("Weight multiplier for large caves (uncommon)")
            .defineInRange("large_cave_multiplier", 1.0, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue MASSIVE_CAVE_WEIGHT = BUILDER
            .comment("Weight multiplier for massive caverns and underground seas (rare)")
            .defineInRange("massive_cavern_multiplier", 1.0, 0.0, 50.0);
    public static final ModConfigSpec.DoubleValue LANDMARK_CHANCE = BUILDER
            .comment("Multiplier on the chance of a landmark (Thermal Cathedral, Giant Kelp Cavern, ...: very rare). Raise it to test them")
            .defineInRange("landmark_multiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue CAVE_CONNECTION_CHANCE = BUILDER
            .comment("Multiplier on the chance that neighbouring cave systems are joined by a tunnel")
            .defineInRange("connection_multiplier", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.BooleanValue UNDERGROUND_LAKES = BUILDER
            .comment("Gas pockets above underground lakes (air-filled cavern tops with a water surface)")
            .define("underground_lakes", true);
    public static final ModConfigSpec.DoubleValue CAVE_VEGETATION = BUILDER
            .comment("Multiplier for cave plant density (floors, walls, ceilings and cave forests)")
            .defineInRange("vegetation_multiplier", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue CAVE_GLOW = BUILDER
            .comment("Multiplier for the share of luminous cave plants")
            .defineInRange("glowing_plant_multiplier", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.BooleanValue CAVE_DEBUG_TIMING = BUILDER
            .comment("Log cave generation time per chunk every 256 chunks (for performance testing)")
            .define("log_generation_time", false);

    static {
        BUILDER.pop();
        BUILDER.comment("AB02 crust cave windows: free-floating cave networks inside the abyss crust, one per depth window (B' Y -540..-380,",
                "C -700..-540, D -860..-700, E -998..-860; see DeepLayer). The values below are per window in this order: B', C, D, E.",
                "The shallow, seabed-relative network above is configured by [caves] and is not affected. Changing spacing, size or the",
                "vertical link chance reshapes the layout of chunks that are not generated yet: set them before exploring new areas.").push("caves_bands");
    }

    public static final ModConfigSpec.BooleanValue BANDS_ENABLED = BUILDER
            .comment("Generate the crust cave windows (needs the biomes' cave profiles; off = the crust stays solid)")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue[] BAND_SPACING = bandInts("system_spacing", "Grid spacing of cave systems of this window in blocks (each cell holds at most one system; "
            + "the lower the denser the network, the cost grows with 1/spacing squared)", new int[] {208, 224, 224, 208}, 112, 768);
    public static final ModConfigSpec.DoubleValue[] BAND_SYSTEM_CHANCE = bandDoubles("system_chance_multiplier", "Multiplier on each biome's chance that a cell of this window "
            + "holds a cave system (stacks with [caves] system_chance_multiplier): rarer high up, denser deeper", new double[] {0.8, 0.9, 1.0, 1.1}, 4.0);
    public static final ModConfigSpec.DoubleValue[] BAND_SIZE = bandDoubles("size_multiplier", "Multiplier on the radius of the systems of this window "
            + "(mega caverns stay within radius 128)", new double[] {1.0, 1.0, 1.0, 1.0}, 2.0);
    public static final ModConfigSpec.DoubleValue[] BAND_MEGA_WEIGHT = bandDoubles("mega_cavern_multiplier", "Weight multiplier for mega caverns (radius 64-128, rare) "
            + "in the cave profiles of this window; 0 = none", new double[] {0.5, 1.0, 1.2, 1.5}, 50.0);
    public static final ModConfigSpec.DoubleValue[] BAND_CONNECTION = bandDoubles("connection_multiplier", "Multiplier on the chance that neighbouring cave systems "
            + "of this window are joined by a tunnel (stacks with [caves] connection_multiplier)", new double[] {1.0, 1.0, 1.0, 1.0}, 4.0);
    public static final ModConfigSpec.DoubleValue BAND_VERTICAL_LINK_CHANCE = BUILDER
            .comment("Chance that a system gets a vertical shaft up into the nearest system of the window above (windows overlap by 40 blocks), "
                    + "so the player can climb or descend through the bands; the topmost window has none. 0 = none")
            .defineInRange("vertical_link_chance", 0.5, 0.0, 1.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Biome-specific seabed structures: rock spires, crystal clusters, chimney groups, volcanoes, craters, fissures,",
                "mud mounds, kelp forests and cavern pillars (definitions and per-biome profiles are datapack data:",
                "abyssia/seabed_structure and abyssia/seabed_structure_profile). Changing these on an existing world leaves seams",
                "where old and new chunks meet: set them before exploring new areas.").push("seabed_structures");
    }

    public static final ModConfigSpec.BooleanValue STRUCTURES_ENABLED = BUILDER.define("enabled", true);
    public static final ModConfigSpec.DoubleValue STRUCTURE_DENSITY = BUILDER
            .comment("Multiplier on every structure's chance per grid cell (1.0 = the profiles' own density)")
            .defineInRange("density_multiplier", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue LANDMARK_STRUCTURE_CHANCE = BUILDER
            .comment("Extra multiplier for rare landmarks (giant rock tower, great rift, submerged volcano...). Raise it to test them")
            .defineInRange("landmark_multiplier", 1.0, 0.0, 100.0);

    static {
        BUILDER.pop();
    }

    private static ModConfigSpec.IntValue veinSize(String name, int value)
    {
        return BUILDER.defineInRange(name, value, 1, 600);
    }

    /** One option per cave window, named {@code <name>_<window>} (b = B', c, d, e). Static-init order: no static fields used here. */
    private static ModConfigSpec.IntValue[] bandInts(String name, String comment, int[] defaults, int min, int max)
    {
        ModConfigSpec.IntValue[] values = new ModConfigSpec.IntValue[defaults.length];
        for (int i = 0; i < values.length; i++) values[i] = BUILDER.comment(comment).defineInRange(name + "_" + "bcde".charAt(i), defaults[i], min, max);
        return values;
    }

    private static ModConfigSpec.DoubleValue[] bandDoubles(String name, String comment, double[] defaults, double max)
    {
        ModConfigSpec.DoubleValue[] values = new ModConfigSpec.DoubleValue[defaults.length];
        for (int i = 0; i < values.length; i++) values[i] = BUILDER.comment(comment).defineInRange(name + "_" + "bcde".charAt(i), defaults[i], 0.0, max);
        return values;
    }

    static {
        BUILDER.comment("Client-side marine snow and underwater environment particles").push("marine_snow");
    }

    public static final ModConfigSpec.BooleanValue MARINE_SNOW_ENABLED = BUILDER.define("enabled", true);

    public static final ModConfigSpec.DoubleValue SURFACE_DENSITY = density("surface_density", 0.05);
    public static final ModConfigSpec.DoubleValue TWILIGHT_DENSITY = density("twilight_density", 0.15);
    public static final ModConfigSpec.DoubleValue DEEP_DENSITY = density("deep_density", 0.35);
    public static final ModConfigSpec.DoubleValue ABYSSAL_DENSITY = density("abyssal_density", 0.60);
    public static final ModConfigSpec.DoubleValue TRENCH_DENSITY = density("trench_density", 0.85);
    public static final ModConfigSpec.DoubleValue HADAL_DENSITY = density("hadal_density", 1.0);

    public static final ModConfigSpec.DoubleValue MIN_FALL_SPEED = BUILDER
            .comment("Marine snow fall speed range, blocks per tick")
            .defineInRange("min_fall_speed", 0.005, 0.0, 0.2);
    public static final ModConfigSpec.DoubleValue MAX_FALL_SPEED = BUILDER
            .defineInRange("max_fall_speed", 0.03, 0.0, 0.2);

    public static final ModConfigSpec.DoubleValue CURRENT_STRENGTH = BUILDER
            .comment("Overall ocean current strength; 0.25 keeps the per-biome defaults")
            .defineInRange("current_strength", 0.25, 0.0, 2.0);

    public static final ModConfigSpec.IntValue PARTICLE_RENDER_DISTANCE = BUILDER
            .comment("Horizontal radius around the player in which environment particles spawn (vertical is half)")
            .defineInRange("particle_render_distance", 32, 8, 64);

    public static final ModConfigSpec.DoubleValue BIOLUMINESCENT_CHANCE = BUILDER
            .comment("Chance that a hadal marine snow particle glows")
            .defineInRange("bioluminescent_chance", 0.01, 0.0, 0.2);

    public static final ModConfigSpec.BooleanValue SEDIMENT_ENABLED = BUILDER
            .comment("Sediment particles near the seabed (sediment blocks are part of world generation and always generate)")
            .define("sediment_enabled", true);
    public static final ModConfigSpec.BooleanValue RISING_SEDIMENT_ENABLED = BUILDER.define("rising_sediment_enabled", true);
    public static final ModConfigSpec.BooleanValue THERMAL_PARTICLES_ENABLED = BUILDER.define("thermal_particles_enabled", true);
    public static final ModConfigSpec.BooleanValue VOLCANIC_ASH_ENABLED = BUILDER.define("volcanic_ash_enabled", true);

    public static final ModConfigSpec.DoubleValue CAVE_SNOW_MULTIPLIER = BUILDER
            .comment("Marine snow density multiplier deep inside a massive cavern (smaller caves get part of it); 1 disables the cave boost")
            .defineInRange("cave_density_multiplier", 2.6, 1.0, 5.0);
    public static final ModConfigSpec.BooleanValue CAVE_CURRENT_EFFECTS = BUILDER
            .comment("Particles stream faster through narrow cave passages and caves shelter them from the open-ocean current")
            .define("cave_current_effects", true);

    public static final ModConfigSpec.IntValue SURFACE_PARTICLE_LIMIT = limit("surface_particle_limit", 50);
    public static final ModConfigSpec.IntValue DEEP_PARTICLE_LIMIT = limit("deep_particle_limit", 100);
    public static final ModConfigSpec.IntValue ABYSSAL_PARTICLE_LIMIT = limit("abyssal_particle_limit", 150);
    public static final ModConfigSpec.IntValue HADAL_PARTICLE_LIMIT = limit("hadal_particle_limit", 200);

    static {
        BUILDER.pop();
    }

    private static ModConfigSpec.DoubleValue density(String name, double value)
    {
        return BUILDER.defineInRange(name, value, 0.0, 2.0);
    }

    private static ModConfigSpec.IntValue limit(String name, int value)
    {
        return BUILDER.comment("Maximum environment particles alive at once in this depth band").defineInRange(name, value, 0, 2000);
    }

    static {
        BUILDER.comment("Hydrothermal vent fields. Generation options take effect for newly generated chunks.").push("thermal_vents");
    }

    public static final ModConfigSpec.BooleanValue THERMAL_VENTS = BUILDER.define("enabled", true);
    public static final ModConfigSpec.IntValue VENT_FIELD_MIN_SIZE = BUILDER.comment("Vent field diameter range, blocks").defineInRange("vent_field_min_size", 20, 8, 200);
    public static final ModConfigSpec.IntValue VENT_FIELD_MAX_SIZE = BUILDER.defineInRange("vent_field_max_size", 80, 8, 200);
    public static final ModConfigSpec.DoubleValue LARGE_FIELD_CHANCE = BUILDER
            .comment("Chance a field is large (100-160 blocks); multiplied by 6 in Abyssal Trench")
            .defineInRange("large_field_chance", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ANCIENT_FIELD_CHANCE = BUILDER
            .comment("Chance a field is an ancient field (140-200 blocks); only in Hadal Zone, multiplied by 20 there")
            .defineInRange("ancient_field_chance", 0.01, 0.0, 1.0);
    public static final ModConfigSpec.IntValue MIN_CHIMNEY_HEIGHT = BUILDER.defineInRange("min_chimney_height", 3, 1, 32);
    public static final ModConfigSpec.IntValue MAX_CHIMNEY_HEIGHT = BUILDER.defineInRange("max_chimney_height", 15, 1, 32);
    public static final ModConfigSpec.IntValue MIN_CHIMNEY_COUNT = BUILDER.defineInRange("min_chimney_count", 3, 1, 40);
    public static final ModConfigSpec.IntValue MAX_CHIMNEY_COUNT = BUILDER.defineInRange("max_chimney_count", 15, 1, 40);
    public static final ModConfigSpec.DoubleValue BLACK_SMOKER_CHANCE = BUILDER.comment("Relative weights of vent types").defineInRange("black_smoker_chance", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue WHITE_SMOKER_CHANCE = BUILDER.defineInRange("white_smoker_chance", 0.30, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue MINERAL_VENT_CHANCE = BUILDER.defineInRange("mineral_vent_chance", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SUPERHEATED_VENT_CHANCE = BUILDER
            .comment("Superheated vents only form in Volcanic Deep (x3), Abyssal Trench and Hadal Zone")
            .defineInRange("superheated_vent_chance", 0.10, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue VENT_PARTICLES_ENABLED = BUILDER.comment("Client: vent plumes").define("particle_enabled", true);
    public static final ModConfigSpec.DoubleValue THERMAL_PARTICLE_DENSITY = BUILDER.defineInRange("thermal_particle_density", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue BUBBLE_DENSITY = BUILDER.defineInRange("bubble_density", 0.5, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue VENT_SEDIMENT_DENSITY = BUILDER.defineInRange("sediment_density", 0.5, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue THERMAL_UPDRAFT_STRENGTH = BUILDER.defineInRange("thermal_updraft_strength", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.BooleanValue MINERAL_GENERATION = BUILDER.comment("Sulfur and sulfide ores in vent fields").define("mineral_generation", true);
    public static final ModConfigSpec.BooleanValue CRYSTAL_GENERATION = BUILDER.define("crystal_generation", true);

    static {
        BUILDER.pop();
        BUILDER.comment("Ocean currents carry what is in the water: players, mobs, dropped items, XP and boats. The flow itself is marine_snow.current_strength").push("ocean_current");
    }

    public static final ModConfigSpec.BooleanValue CURRENT_PUSH_ENTITIES = BUILDER
            .comment("Currents carry mobs, dropped items, XP orbs and boats (entity type tag abyssia:ignores_ocean_current opts out)")
            .define("push_entities", true);
    public static final ModConfigSpec.BooleanValue CURRENT_PUSH_PLAYERS = BUILDER
            .comment("Currents carry swimming players (and the boat a player steers); creative flight and spectators are never carried")
            .define("push_players", true);
    public static final ModConfigSpec.DoubleValue CURRENT_PUSH_SCALE = BUILDER
            .comment("Drift speed relative to the current field: 1 = things drift with the marine snow, higher = stronger pull")
            .defineInRange("push_scale", 2.0, 0.0, 10.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Natural currents: seed-placed streams of fast water (weak / normal / strong) in the overworld's ocean and deep layer. Players see them as particles flowing along the stream").push("natural_currents");
    }

    public static final ModConfigSpec.BooleanValue NATURAL_CURRENTS = BUILDER
            .comment("Place natural currents (clients learn the placement from the server on login)")
            .define("enabled", true);
    public static final ModConfigSpec.DoubleValue NATURAL_CURRENT_CHANCE = BUILDER
            .comment("Chance that a 192x192-block cell holds a stream, per depth band (upper ocean, deep layer)")
            .defineInRange("cell_chance", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue NATURAL_CURRENT_MAX_SPEED = BUILDER
            .comment("Drift speed in blocks per tick at strength 1 on a stream's axis (0.16 = 3.2 blocks/s: sprint-swimming still beats it)")
            .defineInRange("max_speed", 0.16, 0.0, 1.0);

    static {
        BUILDER.pop();
        BUILDER.comment("CU01 current streams: wide, strong, curving bands of water (white streaks) placed from the world seed in the upper ocean (Y 4..52) and the deep layer (Y -330..-110), in any biome. Clients learn the settings from the server on login").push("current_streams");
    }

    public static final ModConfigSpec.BooleanValue STREAMS_ENABLED = BUILDER
            .comment("Place current streams")
            .define("enabled", true);
    public static final ModConfigSpec.DoubleValue STREAM_CHANCE = BUILDER
            .comment("Chance that a cell holds a stream in the deep layer (the upper ocean uses half of it)")
            .defineInRange("generation_chance", 0.20, 0.0, 1.0);
    public static final ModConfigSpec.IntValue STREAM_CELL_SIZE = BUILDER
            .comment("Cell size in blocks (one roll per cell and layer); changing it moves every stream")
            .defineInRange("cell_size", 192, 64, 1024);
    public static final ModConfigSpec.IntValue STREAM_MIN_LENGTH = BUILDER
            .comment("Shortest stream in blocks")
            .defineInRange("min_length", 64, 16, 512);
    public static final ModConfigSpec.IntValue STREAM_MAX_LENGTH = BUILDER
            .comment("Longest stream in blocks")
            .defineInRange("max_length", 256, 16, 512);
    public static final ModConfigSpec.IntValue STREAM_MIN_WIDTH = BUILDER
            .comment("Narrowest stream diameter in blocks")
            .defineInRange("min_width", 8, 2, 48);
    public static final ModConfigSpec.IntValue STREAM_MAX_WIDTH = BUILDER
            .comment("Widest stream diameter in blocks")
            .defineInRange("max_width", 20, 2, 48);
    public static final ModConfigSpec.DoubleValue STREAM_MIN_STRENGTH = BUILDER
            .comment("Weakest stream strength (WEAK 0.65-0.80, NORMAL 0.80-1.00, STRONG 1.00-1.20; rolled strengths are clamped to this range)")
            .defineInRange("min_strength", 0.65, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue STREAM_MAX_STRENGTH = BUILDER
            .comment("Strongest stream strength")
            .defineInRange("max_strength", 1.2, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue STREAM_BASE_SPEED = BUILDER
            .comment("Drift speed in blocks per tick at strength 1 on a stream's centreline (0.34: a propulsion screw cannot fully hold against it)")
            .defineInRange("base_flow_speed", 0.34, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue STREAM_MAX_SPEED = BUILDER
            .comment("Cap on a stream's drift speed in blocks per tick")
            .defineInRange("max_flow_speed", 0.40, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue STREAM_AFFECTS_PLAYERS = BUILDER
            .comment("Streams carry swimming players (and the boat a player steers)")
            .define("affects_players", true);
    public static final ModConfigSpec.BooleanValue STREAM_AFFECTS_MOBS = BUILDER
            .comment("Streams carry mobs")
            .define("affects_mobs", true);
    public static final ModConfigSpec.BooleanValue STREAM_AFFECTS_ITEMS = BUILDER
            .comment("Streams carry dropped items and XP orbs")
            .define("affects_items", true);
    public static final ModConfigSpec.BooleanValue STREAM_AFFECTS_BOATS = BUILDER
            .comment("Streams carry boats")
            .define("affects_boats", true);

    static {
        BUILDER.pop();
        BUILDER.comment("Deep-sea fauna. Which animals live where (depth in metres, habitat, caps) is datapack data: data/<namespace>/fauna_spawns").push("fauna");
    }

    public static final ModConfigSpec.BooleanValue FAUNA_SPAWNING = BUILDER
            .comment("Spawn deep-sea animals around players by depth and habitat (also needs the doMobSpawning game rule)")
            .define("spawning_enabled", true);
    public static final ModConfigSpec.IntValue FAUNA_SPAWN_INTERVAL = BUILDER
            .comment("Ticks between spawn rounds")
            .defineInRange("spawn_interval", 20, 5, 1200);
    public static final ModConfigSpec.IntValue FAUNA_SPAWN_ATTEMPTS = BUILDER
            .comment("Positions tried around each player per spawn round")
            .defineInRange("spawn_attempts", 6, 1, 32);
    public static final ModConfigSpec.IntValue FAUNA_MAX_PER_PLAYER = BUILDER
            .comment("At most this many deep-sea animals within 128 blocks of a player before no more spawn near them (all fauna, tubeworm colonies and vent animals included: 1.5x this)")
            .defineInRange("max_per_player", 180, 0, 1000);
    public static final ModConfigSpec.DoubleValue FAUNA_DENSITY = BUILDER
            .comment("Multiplier on every species' spawn weight (1 = default density)")
            .defineInRange("density_multiplier", 6.0, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue FAUNA_FOOD_FISH_SHARE = BUILDER
            .comment("Share of spawns that go to edible fish (tag abyssia:food_fish) where both they and other animals fit; the rest goes to the other animals. 0 = off")
            .defineInRange("food_fish_share", 0.6, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue LURE_ATTRACTION = BUILDER
            .comment("Small fish are drawn to anglerfish lures in the dark (and may be eaten)")
            .define("lure_attraction", true);

    public static final ModConfigSpec.BooleanValue DIVER_HELMET_NIGHT_VISION = BUILDER
            .comment("Allow the Deep Diver's Helmet to grant weak underwater night vision")
            .define("diver_helmet_night_vision", true);

    static {
        BUILDER.comment("Underwater air time by diving gear stage (stage 1 = entry gear, stage 2 = deep gear; the best of helmet / tank counts)").push("breathing");
    }
    public static final ModConfigSpec.BooleanValue BREATHING_ENABLED = BUILDER
            .comment("Scale the underwater air time by diving gear stage")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue BREATHING_STAGE0_SECONDS = BUILDER
            .comment("Seconds of air without diving gear")
            .defineInRange("stage0_seconds", 120, 15, 3600);
    public static final ModConfigSpec.IntValue BREATHING_STAGE1_SECONDS = BUILDER
            .comment("Seconds of air with entry diving gear")
            .defineInRange("stage1_seconds", 300, 15, 3600);
    public static final ModConfigSpec.IntValue BREATHING_STAGE2_SECONDS = BUILDER
            .comment("Seconds of air with deep diving gear")
            .defineInRange("stage2_seconds", 480, 15, 3600);
    public static final ModConfigSpec.IntValue BREATHING_STAGE1_DURABILITY_SECONDS = BUILDER
            .comment("Underwater seconds per 1 durability lost on entry gear")
            .defineInRange("stage1_durability_seconds", 5, 1, 600);
    public static final ModConfigSpec.IntValue BREATHING_STAGE2_DURABILITY_SECONDS = BUILDER
            .comment("Underwater seconds per 1 durability lost on deep gear")
            .defineInRange("stage2_durability_seconds", 10, 1, 600);
    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Water pressure: diving gear tier decides the safe depth (see item PressureGear)").push("pressure");
    }
    public static final ModConfigSpec.BooleanValue PRESSURE_ENABLED = BUILDER
            .comment("Hurt and slow players who dive deeper than their gear tier allows (survival / adventure, not in a submarine)")
            .define("enabled", true);
    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("AB02 environment hazards of the toxic / hot / frozen deep-crust biomes (see com.abyssia.hazard.HazardZone)").push("hazard");
    }
    public static final ModConfigSpec.BooleanValue HAZARD_ENABLED = BUILDER
            .comment("Toxic gas, heat and cold hurt players in the special abyss biomes unless protected (survival / adventure, not in a submarine or habitat)")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue HAZARD_PULSE_TICKS = BUILDER
            .comment("Ticks between hazard checks; damage is scaled so that it stays per second")
            .defineInRange("pulse_ticks", 20, 5, 200);
    public static final ModConfigSpec.DoubleValue HAZARD_TOXIC_DAMAGE_PER_LEVEL = BUILDER
            .comment("Extra magic damage per second and intensity level in toxic gas (half hearts; Poison is applied on top, 0 = Poison / Wither only)")
            .defineInRange("toxic_damage_per_level", 0.5, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue HAZARD_HEAT_DAMAGE_PER_LEVEL = BUILDER
            .comment("Fire damage per second and intensity level in a hot biome (half hearts), before heat-proof armor")
            .defineInRange("heat_damage_per_level", 1.0, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue HAZARD_COLD_DAMAGE_PER_LEVEL = BUILDER
            .comment("Freeze damage per second and intensity level in a frozen biome (half hearts), before insulating armor")
            .defineInRange("cold_damage_per_level", 0.75, 0.0, 20.0);
    public static final ModConfigSpec.IntValue HAZARD_INTENSITY_OVERRIDE = BUILDER
            .comment("0 = intensity follows the depth band (B' 1, C 2, D 3, E 4); 1..4 forces that intensity everywhere")
            .defineInRange("intensity_override", 0, 0, 4);
    static {
        BUILDER.pop();
    }

    static {
        BUILDER.pop();
        BUILDER.comment("Waypoint beacons (HUD markers of named, coloured beacons in the same dimension)").push("waypoint_beacon");
    }

    public static final ModConfigSpec.BooleanValue WAYPOINT_SHARE = BUILDER
            .comment("Every player sees every beacon (false: only the player who placed it sees and edits it)")
            .define("share_beacons", true);
    public static final ModConfigSpec.IntValue WAYPOINT_MAX_NAME_LENGTH = BUILDER
            .comment("Longest beacon name in characters")
            .defineInRange("max_name_length", 32, 1, 64);

    static {
        BUILDER.pop();
        BUILDER.comment("SUB02 submarine (piloted vehicle) and the moon pool submarine dock").push("submarine");
    }

    public static final ModConfigSpec.IntValue SUBMARINE_ENERGY_CAPACITY = BUILDER
            .comment("Battery of a submarine in FE")
            .defineInRange("energy_capacity", 60_000, 1_000, 10_000_000);
    public static final ModConfigSpec.IntValue SUBMARINE_THRUST_FE = BUILDER
            .comment("FE per tick while the submarine is moving under its own power")
            .defineInRange("thrust_fe_per_tick", 8, 0, 10_000);
    public static final ModConfigSpec.IntValue SUBMARINE_LIGHT_FE = BUILDER
            .comment("FE per tick while the headlights are on")
            .defineInRange("light_fe_per_tick", 1, 0, 10_000);
    public static final ModConfigSpec.DoubleValue SUBMARINE_MAX_SPEED = BUILDER
            .comment("Top forward speed in blocks per tick (reverse / sideways 0.22, up / down 0.18). The pilot's client applies it")
            .defineInRange("max_speed", 0.42, 0.05, 2.0);
    public static final ModConfigSpec.IntValue SUBMARINE_DOCK_CHARGE_RATE = BUILDER
            .comment("FE per tick a dock gives its docked submarine (from the dock's own 20,000 FE buffer)")
            .defineInRange("dock_charge_rate", 500, 0, 100_000);
    public static final ModConfigSpec.DoubleValue SUBMARINE_DOCK_CAPTURE_RANGE = BUILDER
            .comment("Horizontal reach of a dock in blocks (from its centre; it reaches 6 blocks down)")
            .defineInRange("dock_capture_range", 3.5, 0.5, 16.0);

    static {
        BUILDER.comment("SUB03 submarine upgrades (the base thrust FE is thrust_fe_per_tick above)").push("upgrades");
    }

    public static final ModConfigSpec.DoubleValue SUB_HULL_MAX_DAMAGE = BUILDER
            .comment("Pressure hull: breaking threshold of the hull (without it 40)")
            .defineInRange("hull_max_damage", 70.0, 1.0, 10_000.0);
    public static final ModConfigSpec.DoubleValue SUB_HULL_DEEP_REDUCTION = BUILDER
            .comment("Pressure hull: damage multiplier at or below Y -64 (rounded up, so 1 stays 1)")
            .defineInRange("hull_deep_reduction", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SUB_HULL_SPEED_MULT = BUILDER
            .comment("Pressure hull: multiplier of every top speed")
            .defineInRange("hull_speed_mult", 0.92, 0.1, 2.0);
    public static final ModConfigSpec.IntValue SUB_BATTERY_CAPACITY = BUILDER
            .comment("High-capacity battery: battery size in FE")
            .defineInRange("battery_capacity", 150_000, 1_000, 10_000_000);
    public static final ModConfigSpec.DoubleValue SUB_BATTERY_VERTICAL_MULT = BUILDER
            .comment("High-capacity battery: multiplier of the up / down top speed")
            .defineInRange("battery_vertical_mult", 0.95, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue SUB_THRUSTER_FORWARD = BUILDER
            .comment("Maneuver thruster: top forward speed (blocks per tick)")
            .defineInRange("thruster_forward", 0.56, 0.05, 2.0);
    public static final ModConfigSpec.DoubleValue SUB_THRUSTER_SIDE = BUILDER
            .comment("Maneuver thruster: top reverse / sideways speed")
            .defineInRange("thruster_side", 0.30, 0.05, 2.0);
    public static final ModConfigSpec.DoubleValue SUB_THRUSTER_VERTICAL = BUILDER
            .comment("Maneuver thruster: top up / down speed")
            .defineInRange("thruster_vertical", 0.23, 0.05, 2.0);
    public static final ModConfigSpec.IntValue SUB_THRUSTER_FE = BUILDER
            .comment("Maneuver thruster: FE per tick while moving under power (replaces thrust_fe_per_tick)")
            .defineInRange("thruster_fe_per_tick", 12, 0, 10_000);
    public static final ModConfigSpec.DoubleValue SUB_THRUSTER_ACCEL = BUILDER
            .comment("Maneuver thruster: acceleration in blocks per tick^2 (without it 0.04)")
            .defineInRange("thruster_accel", 0.05, 0.005, 1.0);
    public static final ModConfigSpec.IntValue SUBMARINE_DEPTH_BASE = BUILDER
            .comment("Rated depth in metres of a submarine without a Depth Hull upgrade")
            .defineInRange("depth_base", 300, 10, 20_000);
    public static final ModConfigSpec.IntValue SUBMARINE_DEPTH_MK1 = BUILDER
            .comment("Depth Hull Mk1: rated depth in metres")
            .defineInRange("depth_mk1", 600, 10, 20_000);
    public static final ModConfigSpec.IntValue SUBMARINE_DEPTH_MK2 = BUILDER
            .comment("Depth Hull Mk2: rated depth in metres")
            .defineInRange("depth_mk2", 1000, 10, 20_000);
    public static final ModConfigSpec.IntValue SUBMARINE_DEPTH_MK3 = BUILDER
            .comment("Depth Hull Mk3: rated depth in metres")
            .defineInRange("depth_mk3", 2000, 10, 20_000);
    public static final ModConfigSpec.DoubleValue SUBMARINE_CRUSH_DAMAGE = BUILDER
            .comment("Hull damage per second below the rated depth (break threshold is 40, or 70 with the Pressure Hull)")
            .defineInRange("crush_damage", 4.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue SUBMARINE_CRUSH_STEP = BUILDER
            .comment("Extra crush damage per second for every this many metres below the rated depth")
            .defineInRange("crush_step_metres", 200, 10, 20_000);
    public static final ModConfigSpec.IntValue SUB_SONAR_FE = BUILDER
            .comment("Deep-sea sonar: FE per tick while someone is aboard")
            .defineInRange("sonar_fe_per_tick", 4, 0, 10_000);
    public static final ModConfigSpec.IntValue SUB_SONAR_RANGE = BUILDER
            .comment("Deep-sea sonar: scan radius for sea life and currents (blocks)")
            .defineInRange("sonar_range", 16, 1, 64);
    public static final ModConfigSpec.IntValue SUB_SONAR_INTERVAL = BUILDER
            .comment("Deep-sea sonar: ticks between scans")
            .defineInRange("sonar_interval", 10, 1, 200);
    public static final ModConfigSpec.IntValue SUB_SONAR_DOCK_RANGE = BUILDER
            .comment("Deep-sea sonar: reach of the submarine dock search (blocks)")
            .defineInRange("sonar_dock_range", 32, 1, 128);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.pop();
    }

    static
    {
        BUILDER.comment("Progression gates (WRK01)").push("progression");
    }

    public static final ModConfigSpec.IntValue WRECKS_TO_UNLOCK = BUILDER
            .comment("Wreck cores that must be analysed with the lidar scanner before the habitat constructor can build")
            .defineInRange("wrecks_to_unlock", 3, 1, 10);

    static
    {
        BUILDER.pop();
        BUILDER.comment("Scan research (AB05). wrecks_to_unlock above is no longer used: the count lives in data/abyssia/technologies").push("research");
    }

    public static final ModConfigSpec.BooleanValue RESEARCH_OP_BYPASS = BUILDER
            .comment("Operators (permission level 2) skip the research locks like creative players do")
            .define("op_bypass", true);

    public static final ModConfigSpec.BooleanValue RESEARCH_GATE_MACHINES = BUILDER
            .comment("Machines named by a technology's unlocks (abyssia:machine/<block>) cannot be placed or used until that technology is unlocked")
            .define("gate_machines", true);

    public static final ModConfigSpec.BooleanValue RESEARCH_GATE_BUILDINGS = BUILDER
            .comment("Habitat build menu entries named by a technology's unlocks (abyssia:building/<entry>) are locked until it is unlocked")
            .define("gate_buildings", true);

    public static final ModConfigSpec.BooleanValue RESEARCH_JEI_HIDE_LOCKED = BUILDER
            .comment("JEI hides recipes whose result is locked by a technology (abyssia:recipe/<item>) and the recipes of locked machines (display only)")
            .define("jei_hide_locked", true);

    static
    {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * 4: the deep ocean dimension became the overworld's deep layer; its transition options (enabled, transition_y,
     * return_y, coordinate_offset_y, transition_cooldown, deep_ocean_respawn_in_ocean_world, preload_distance,
     * enable_transition_effect) were removed. Forge drops the stale keys from old files when it corrects them.
     */
    private static final int CURRENT_CONFIG_VERSION = 4;

    static void migrate(ModConfigEvent event)
    {
        if (event.getConfig().getSpec() != SPEC || CONFIG_VERSION.get() >= CURRENT_CONFIG_VERSION) return;
        CONFIG_VERSION.set(CURRENT_CONFIG_VERSION);
        SPEC.save();
    }

    static void onLoading(ModConfigEvent.Loading event)
    {
        migrate(event);
    }

    static void onReloading(ModConfigEvent.Reloading event)
    {
        migrate(event);
    }
}
