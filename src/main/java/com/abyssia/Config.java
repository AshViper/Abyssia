package com.abyssia;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Common (server-synced) options for the deep ocean, terrain, ores and thermal vents. */
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.push("deep_ocean");
    }

    public static final ForgeConfigSpec.IntValue CONFIG_VERSION = BUILDER
            .comment("Internal: config file version, used to upgrade old files. Do not edit.")
            .defineInRange("config_version", 1, 1, 100);

    public static final ForgeConfigSpec.BooleanValue DEEP_OCEAN_ENABLE_FOG = BUILDER
            .comment("Client: thicken and darken underwater fog with depth, down through the deep layer below the bedrock band")
            .define("enable_fog", true);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Deep layer terrain (below the bedrock band). Generation options take effect for newly generated chunks.").push("terrain");
    }

    public static final ForgeConfigSpec.BooleanValue CUSTOM_BLOCKS_ONLY = BUILDER
            .comment("Build deep ocean terrain only from Abyssia blocks. If false, terrain blocks are swapped for rough vanilla equivalents")
            .define("custom_blocks_only", true);

    static {
        BUILDER.pop();
        BUILDER.comment("Deep ocean ore veins").push("ore");
    }

    public static final ForgeConfigSpec.BooleanValue SURFACE_VEINS_ENABLED = BUILDER
            .comment("Allow veins to break through the seabed so they can be spotted from a distance")
            .define("surface_veins_enabled", true);
    public static final ForgeConfigSpec.DoubleValue LARGE_VEIN_MULTIPLIER = BUILDER
            .comment("Size multiplier for large and huge veins")
            .defineInRange("large_vein_multiplier", 1.0, 0.1, 3.0);
    public static final ForgeConfigSpec.DoubleValue EXPOSED_VEIN_CHANCE = BUILDER
            .comment("Chance a vein is exposed on the seabed (large veins are more likely to be)")
            .defineInRange("exposed_vein_chance", 0.15, 0.0, 1.0);
    public static final ForgeConfigSpec.IntValue SMALL_VEIN_MIN = veinSize("small_vein_min", 5);
    public static final ForgeConfigSpec.IntValue SMALL_VEIN_MAX = veinSize("small_vein_max", 15);
    public static final ForgeConfigSpec.IntValue MEDIUM_VEIN_MIN = veinSize("medium_vein_min", 15);
    public static final ForgeConfigSpec.IntValue MEDIUM_VEIN_MAX = veinSize("medium_vein_max", 40);
    public static final ForgeConfigSpec.IntValue LARGE_VEIN_MIN = veinSize("large_vein_min", 40);
    public static final ForgeConfigSpec.IntValue LARGE_VEIN_MAX = veinSize("large_vein_max", 100);
    public static final ForgeConfigSpec.IntValue HUGE_VEIN_MIN = veinSize("huge_vein_min", 100);
    public static final ForgeConfigSpec.IntValue HUGE_VEIN_MAX = veinSize("huge_vein_max", 300);

    static {
        BUILDER.pop();
        BUILDER.comment("Deep ocean vegetation").push("vegetation");
    }

    public static final ForgeConfigSpec.BooleanValue VEGETATION_ENABLED = BUILDER.define("enabled", true);
    public static final ForgeConfigSpec.DoubleValue VEGETATION_DENSITY = BUILDER
            .comment("Multiplier for how many plant patches generate")
            .defineInRange("density_multiplier", 1.0, 0.0, 4.0);
    public static final ForgeConfigSpec.DoubleValue GIANT_PLANT_CHANCE = BUILDER
            .comment("How often giant kelp, giant tubes and other canopy plants appear (0.1 = default amount)")
            .defineInRange("giant_plant_chance", 0.1, 0.0, 1.0);
    public static final ForgeConfigSpec.DoubleValue GLOWING_PLANT_CHANCE = BUILDER
            .comment("How often glowing plant patches appear (0.03 = default amount)")
            .defineInRange("glowing_plant_chance", 0.03, 0.0, 0.3);
    public static final ForgeConfigSpec.BooleanValue ABYSSAL_FORESTS = BUILDER
            .comment("Giant kelp and giant tube forests")
            .define("abyssal_forests", true);

    static {
        BUILDER.pop();
        BUILDER.push("crystal");
    }

    public static final ForgeConfigSpec.BooleanValue CRYSTAL_FIELDS = BUILDER
            .comment("Crystal spires, gardens, clusters and crystal caves")
            .define("enabled", true);

    static {
        BUILDER.pop();
        BUILDER.push("thermal");
    }

    public static final ForgeConfigSpec.BooleanValue THERMAL_VEGETATION = BUILDER
            .comment("Heat-adapted plants in rings around thermal vents")
            .define("thermal_vegetation_enabled", true);

    static {
        BUILDER.pop();
        BUILDER.comment("Deep ocean cave network (cave types, landmarks and their palettes are datapack data: abyssia/cave_profile and",
                "abyssia/cave_environment). Generation options take effect for newly generated chunks; the spacing changes the whole layout.").push("caves");
    }

    public static final ForgeConfigSpec.BooleanValue CAVES_ENABLED = BUILDER.define("enabled", true);
    public static final ForgeConfigSpec.IntValue CAVE_SYSTEM_SPACING = BUILDER
            .comment("Grid spacing of cave systems in blocks; each cell holds at most one system")
            .defineInRange("system_spacing", 176, 96, 512);
    public static final ForgeConfigSpec.DoubleValue CAVE_SYSTEM_CHANCE = BUILDER
            .comment("Multiplier on each biome's chance that a cell holds a cave system")
            .defineInRange("system_chance_multiplier", 1.0, 0.0, 4.0);
    public static final ForgeConfigSpec.DoubleValue MINOR_CAVE_CHANCE = BUILDER
            .comment("Multiplier on the chance of small sea caves and sea arches between the systems")
            .defineInRange("minor_cave_chance_multiplier", 1.0, 0.0, 4.0);
    public static final ForgeConfigSpec.DoubleValue LARGE_CAVE_WEIGHT = BUILDER
            .comment("Weight multiplier for large caves (uncommon)")
            .defineInRange("large_cave_multiplier", 1.0, 0.0, 20.0);
    public static final ForgeConfigSpec.DoubleValue MASSIVE_CAVE_WEIGHT = BUILDER
            .comment("Weight multiplier for massive caverns and underground seas (rare)")
            .defineInRange("massive_cavern_multiplier", 1.0, 0.0, 50.0);
    public static final ForgeConfigSpec.DoubleValue LANDMARK_CHANCE = BUILDER
            .comment("Multiplier on the chance of a landmark (Thermal Cathedral, Giant Kelp Cavern, ...: very rare). Raise it to test them")
            .defineInRange("landmark_multiplier", 1.0, 0.0, 100.0);
    public static final ForgeConfigSpec.DoubleValue CAVE_CONNECTION_CHANCE = BUILDER
            .comment("Multiplier on the chance that neighbouring cave systems are joined by a tunnel")
            .defineInRange("connection_multiplier", 1.0, 0.0, 4.0);
    public static final ForgeConfigSpec.BooleanValue UNDERGROUND_LAKES = BUILDER
            .comment("Gas pockets above underground lakes (air-filled cavern tops with a water surface)")
            .define("underground_lakes", true);
    public static final ForgeConfigSpec.DoubleValue CAVE_VEGETATION = BUILDER
            .comment("Multiplier for cave plant density (floors, walls, ceilings and cave forests)")
            .defineInRange("vegetation_multiplier", 1.0, 0.0, 4.0);
    public static final ForgeConfigSpec.DoubleValue CAVE_GLOW = BUILDER
            .comment("Multiplier for the share of luminous cave plants")
            .defineInRange("glowing_plant_multiplier", 1.0, 0.0, 4.0);
    public static final ForgeConfigSpec.BooleanValue CAVE_DEBUG_TIMING = BUILDER
            .comment("Log cave generation time per chunk every 256 chunks (for performance testing)")
            .define("log_generation_time", false);

    static {
        BUILDER.pop();
        BUILDER.comment("Biome-specific seabed structures: rock spires, crystal clusters, chimney groups, volcanoes, craters, fissures,",
                "mud mounds, kelp forests and cavern pillars (definitions and per-biome profiles are datapack data:",
                "abyssia/seabed_structure and abyssia/seabed_structure_profile). Changing these on an existing world leaves seams",
                "where old and new chunks meet: set them before exploring new areas.").push("seabed_structures");
    }

    public static final ForgeConfigSpec.BooleanValue STRUCTURES_ENABLED = BUILDER.define("enabled", true);
    public static final ForgeConfigSpec.DoubleValue STRUCTURE_DENSITY = BUILDER
            .comment("Multiplier on every structure's chance per grid cell (1.0 = the profiles' own density)")
            .defineInRange("density_multiplier", 1.0, 0.0, 4.0);
    public static final ForgeConfigSpec.DoubleValue LANDMARK_STRUCTURE_CHANCE = BUILDER
            .comment("Extra multiplier for rare landmarks (giant rock tower, great rift, submerged volcano...). Raise it to test them")
            .defineInRange("landmark_multiplier", 1.0, 0.0, 100.0);

    static {
        BUILDER.pop();
    }

    private static ForgeConfigSpec.IntValue veinSize(String name, int value)
    {
        return BUILDER.defineInRange(name, value, 1, 600);
    }

    static {
        BUILDER.comment("Client-side marine snow and underwater environment particles").push("marine_snow");
    }

    public static final ForgeConfigSpec.BooleanValue MARINE_SNOW_ENABLED = BUILDER.define("enabled", true);

    public static final ForgeConfigSpec.DoubleValue SURFACE_DENSITY = density("surface_density", 0.05);
    public static final ForgeConfigSpec.DoubleValue TWILIGHT_DENSITY = density("twilight_density", 0.15);
    public static final ForgeConfigSpec.DoubleValue DEEP_DENSITY = density("deep_density", 0.35);
    public static final ForgeConfigSpec.DoubleValue ABYSSAL_DENSITY = density("abyssal_density", 0.60);
    public static final ForgeConfigSpec.DoubleValue TRENCH_DENSITY = density("trench_density", 0.85);
    public static final ForgeConfigSpec.DoubleValue HADAL_DENSITY = density("hadal_density", 1.0);

    public static final ForgeConfigSpec.DoubleValue MIN_FALL_SPEED = BUILDER
            .comment("Marine snow fall speed range, blocks per tick")
            .defineInRange("min_fall_speed", 0.005, 0.0, 0.2);
    public static final ForgeConfigSpec.DoubleValue MAX_FALL_SPEED = BUILDER
            .defineInRange("max_fall_speed", 0.03, 0.0, 0.2);

    public static final ForgeConfigSpec.DoubleValue CURRENT_STRENGTH = BUILDER
            .comment("Overall ocean current strength; 0.25 keeps the per-biome defaults")
            .defineInRange("current_strength", 0.25, 0.0, 2.0);

    public static final ForgeConfigSpec.IntValue PARTICLE_RENDER_DISTANCE = BUILDER
            .comment("Horizontal radius around the player in which environment particles spawn (vertical is half)")
            .defineInRange("particle_render_distance", 32, 8, 64);

    public static final ForgeConfigSpec.DoubleValue BIOLUMINESCENT_CHANCE = BUILDER
            .comment("Chance that a hadal marine snow particle glows")
            .defineInRange("bioluminescent_chance", 0.01, 0.0, 0.2);

    public static final ForgeConfigSpec.BooleanValue SEDIMENT_ENABLED = BUILDER
            .comment("Sediment particles near the seabed (sediment blocks are part of world generation and always generate)")
            .define("sediment_enabled", true);
    public static final ForgeConfigSpec.BooleanValue RISING_SEDIMENT_ENABLED = BUILDER.define("rising_sediment_enabled", true);
    public static final ForgeConfigSpec.BooleanValue THERMAL_PARTICLES_ENABLED = BUILDER.define("thermal_particles_enabled", true);
    public static final ForgeConfigSpec.BooleanValue VOLCANIC_ASH_ENABLED = BUILDER.define("volcanic_ash_enabled", true);

    public static final ForgeConfigSpec.DoubleValue CAVE_SNOW_MULTIPLIER = BUILDER
            .comment("Marine snow density multiplier deep inside a massive cavern (smaller caves get part of it); 1 disables the cave boost")
            .defineInRange("cave_density_multiplier", 2.6, 1.0, 5.0);
    public static final ForgeConfigSpec.BooleanValue CAVE_CURRENT_EFFECTS = BUILDER
            .comment("Particles stream faster through narrow cave passages and caves shelter them from the open-ocean current")
            .define("cave_current_effects", true);

    public static final ForgeConfigSpec.IntValue SURFACE_PARTICLE_LIMIT = limit("surface_particle_limit", 50);
    public static final ForgeConfigSpec.IntValue DEEP_PARTICLE_LIMIT = limit("deep_particle_limit", 100);
    public static final ForgeConfigSpec.IntValue ABYSSAL_PARTICLE_LIMIT = limit("abyssal_particle_limit", 150);
    public static final ForgeConfigSpec.IntValue HADAL_PARTICLE_LIMIT = limit("hadal_particle_limit", 200);

    static {
        BUILDER.pop();
    }

    private static ForgeConfigSpec.DoubleValue density(String name, double value)
    {
        return BUILDER.defineInRange(name, value, 0.0, 2.0);
    }

    private static ForgeConfigSpec.IntValue limit(String name, int value)
    {
        return BUILDER.comment("Maximum environment particles alive at once in this depth band").defineInRange(name, value, 0, 2000);
    }

    static {
        BUILDER.comment("Hydrothermal vent fields. Generation options take effect for newly generated chunks.").push("thermal_vents");
    }

    public static final ForgeConfigSpec.BooleanValue THERMAL_VENTS = BUILDER.define("enabled", true);
    public static final ForgeConfigSpec.IntValue VENT_FIELD_MIN_SIZE = BUILDER.comment("Vent field diameter range, blocks").defineInRange("vent_field_min_size", 20, 8, 200);
    public static final ForgeConfigSpec.IntValue VENT_FIELD_MAX_SIZE = BUILDER.defineInRange("vent_field_max_size", 80, 8, 200);
    public static final ForgeConfigSpec.DoubleValue LARGE_FIELD_CHANCE = BUILDER
            .comment("Chance a field is large (100-160 blocks); multiplied by 6 in Abyssal Trench")
            .defineInRange("large_field_chance", 0.05, 0.0, 1.0);
    public static final ForgeConfigSpec.DoubleValue ANCIENT_FIELD_CHANCE = BUILDER
            .comment("Chance a field is an ancient field (140-200 blocks); only in Hadal Zone, multiplied by 20 there")
            .defineInRange("ancient_field_chance", 0.01, 0.0, 1.0);
    public static final ForgeConfigSpec.IntValue MIN_CHIMNEY_HEIGHT = BUILDER.defineInRange("min_chimney_height", 3, 1, 32);
    public static final ForgeConfigSpec.IntValue MAX_CHIMNEY_HEIGHT = BUILDER.defineInRange("max_chimney_height", 15, 1, 32);
    public static final ForgeConfigSpec.IntValue MIN_CHIMNEY_COUNT = BUILDER.defineInRange("min_chimney_count", 3, 1, 40);
    public static final ForgeConfigSpec.IntValue MAX_CHIMNEY_COUNT = BUILDER.defineInRange("max_chimney_count", 15, 1, 40);
    public static final ForgeConfigSpec.DoubleValue BLACK_SMOKER_CHANCE = BUILDER.comment("Relative weights of vent types").defineInRange("black_smoker_chance", 0.35, 0.0, 1.0);
    public static final ForgeConfigSpec.DoubleValue WHITE_SMOKER_CHANCE = BUILDER.defineInRange("white_smoker_chance", 0.30, 0.0, 1.0);
    public static final ForgeConfigSpec.DoubleValue MINERAL_VENT_CHANCE = BUILDER.defineInRange("mineral_vent_chance", 0.25, 0.0, 1.0);
    public static final ForgeConfigSpec.DoubleValue SUPERHEATED_VENT_CHANCE = BUILDER
            .comment("Superheated vents only form in Volcanic Deep (x3), Abyssal Trench and Hadal Zone")
            .defineInRange("superheated_vent_chance", 0.10, 0.0, 1.0);
    public static final ForgeConfigSpec.BooleanValue VENT_PARTICLES_ENABLED = BUILDER.comment("Client: vent plumes").define("particle_enabled", true);
    public static final ForgeConfigSpec.DoubleValue THERMAL_PARTICLE_DENSITY = BUILDER.defineInRange("thermal_particle_density", 1.0, 0.0, 4.0);
    public static final ForgeConfigSpec.DoubleValue BUBBLE_DENSITY = BUILDER.defineInRange("bubble_density", 0.5, 0.0, 4.0);
    public static final ForgeConfigSpec.DoubleValue VENT_SEDIMENT_DENSITY = BUILDER.defineInRange("sediment_density", 0.5, 0.0, 4.0);
    public static final ForgeConfigSpec.DoubleValue THERMAL_UPDRAFT_STRENGTH = BUILDER.defineInRange("thermal_updraft_strength", 1.0, 0.0, 4.0);
    public static final ForgeConfigSpec.BooleanValue MINERAL_GENERATION = BUILDER.comment("Sulfur and sulfide ores in vent fields").define("mineral_generation", true);
    public static final ForgeConfigSpec.BooleanValue CRYSTAL_GENERATION = BUILDER.define("crystal_generation", true);

    static {
        BUILDER.pop();
        BUILDER.comment("Ocean currents carry what is in the water: players, mobs, dropped items, XP and boats. The flow itself is marine_snow.current_strength").push("ocean_current");
    }

    public static final ForgeConfigSpec.BooleanValue CURRENT_PUSH_ENTITIES = BUILDER
            .comment("Currents carry mobs, dropped items, XP orbs and boats (entity type tag abyssia:ignores_ocean_current opts out)")
            .define("push_entities", true);
    public static final ForgeConfigSpec.BooleanValue CURRENT_PUSH_PLAYERS = BUILDER
            .comment("Currents carry swimming players (and the boat a player steers); creative flight and spectators are never carried")
            .define("push_players", true);
    public static final ForgeConfigSpec.DoubleValue CURRENT_PUSH_SCALE = BUILDER
            .comment("Drift speed relative to the current field: 1 = things drift with the marine snow, higher = stronger pull")
            .defineInRange("push_scale", 2.0, 0.0, 10.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Natural currents: seed-placed streams of fast water (weak / normal / strong) in the overworld's ocean and deep layer. Players see them as particles flowing along the stream").push("natural_currents");
    }

    public static final ForgeConfigSpec.BooleanValue NATURAL_CURRENTS = BUILDER
            .comment("Place natural currents (clients learn the placement from the server on login)")
            .define("enabled", true);
    public static final ForgeConfigSpec.DoubleValue NATURAL_CURRENT_CHANCE = BUILDER
            .comment("Chance that a 192x192-block cell holds a stream, per depth band (upper ocean, deep layer)")
            .defineInRange("cell_chance", 0.4, 0.0, 1.0);
    public static final ForgeConfigSpec.DoubleValue NATURAL_CURRENT_MAX_SPEED = BUILDER
            .comment("Drift speed in blocks per tick at strength 1 on a stream's axis (0.16 = 3.2 blocks/s: sprint-swimming still beats it)")
            .defineInRange("max_speed", 0.16, 0.0, 1.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Deep-sea fauna. Which animals live where (depth in metres, habitat, caps) is datapack data: data/<namespace>/fauna_spawns").push("fauna");
    }

    public static final ForgeConfigSpec.BooleanValue FAUNA_SPAWNING = BUILDER
            .comment("Spawn deep-sea animals around players by depth and habitat (also needs the doMobSpawning game rule)")
            .define("spawning_enabled", true);
    public static final ForgeConfigSpec.IntValue FAUNA_SPAWN_INTERVAL = BUILDER
            .comment("Ticks between spawn rounds")
            .defineInRange("spawn_interval", 20, 5, 1200);
    public static final ForgeConfigSpec.IntValue FAUNA_SPAWN_ATTEMPTS = BUILDER
            .comment("Positions tried around each player per spawn round")
            .defineInRange("spawn_attempts", 6, 1, 32);
    public static final ForgeConfigSpec.IntValue FAUNA_MAX_PER_PLAYER = BUILDER
            .comment("At most this many deep-sea animals within 128 blocks of a player before no more spawn near them (all fauna, tubeworm colonies and vent animals included: 1.5x this)")
            .defineInRange("max_per_player", 180, 0, 1000);
    public static final ForgeConfigSpec.DoubleValue FAUNA_DENSITY = BUILDER
            .comment("Multiplier on every species' spawn weight (1 = default density)")
            .defineInRange("density_multiplier", 6.0, 0.0, 20.0);
    public static final ForgeConfigSpec.BooleanValue LURE_ATTRACTION = BUILDER
            .comment("Small fish are drawn to anglerfish lures in the dark (and may be eaten)")
            .define("lure_attraction", true);

    public static final ForgeConfigSpec.BooleanValue DIVER_HELMET_NIGHT_VISION = BUILDER
            .comment("Allow the Deep Diver's Helmet to grant weak underwater night vision")
            .define("diver_helmet_night_vision", true);

    static {
        BUILDER.comment("Underwater air time by diving gear stage (stage 1 = entry gear, stage 2 = deep gear; the best of helmet / tank counts)").push("breathing");
    }
    public static final ForgeConfigSpec.BooleanValue BREATHING_ENABLED = BUILDER
            .comment("Scale the underwater air time by diving gear stage")
            .define("enabled", true);
    public static final ForgeConfigSpec.IntValue BREATHING_STAGE0_SECONDS = BUILDER
            .comment("Seconds of air without diving gear")
            .defineInRange("stage0_seconds", 120, 15, 3600);
    public static final ForgeConfigSpec.IntValue BREATHING_STAGE1_SECONDS = BUILDER
            .comment("Seconds of air with entry diving gear")
            .defineInRange("stage1_seconds", 300, 15, 3600);
    public static final ForgeConfigSpec.IntValue BREATHING_STAGE2_SECONDS = BUILDER
            .comment("Seconds of air with deep diving gear")
            .defineInRange("stage2_seconds", 480, 15, 3600);
    public static final ForgeConfigSpec.IntValue BREATHING_STAGE1_DURABILITY_SECONDS = BUILDER
            .comment("Underwater seconds per 1 durability lost on entry gear")
            .defineInRange("stage1_durability_seconds", 5, 1, 600);
    public static final ForgeConfigSpec.IntValue BREATHING_STAGE2_DURABILITY_SECONDS = BUILDER
            .comment("Underwater seconds per 1 durability lost on deep gear")
            .defineInRange("stage2_durability_seconds", 10, 1, 600);
    static {
        BUILDER.pop();
    }

    static {
        BUILDER.pop();
        BUILDER.comment("Waypoint beacons (HUD markers of named, coloured beacons in the same dimension)").push("waypoint_beacon");
    }

    public static final ForgeConfigSpec.BooleanValue WAYPOINT_SHARE = BUILDER
            .comment("Every player sees every beacon (false: only the player who placed it sees and edits it)")
            .define("share_beacons", true);
    public static final ForgeConfigSpec.IntValue WAYPOINT_MAX_NAME_LENGTH = BUILDER
            .comment("Longest beacon name in characters")
            .defineInRange("max_name_length", 32, 1, 64);

    static {
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

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
