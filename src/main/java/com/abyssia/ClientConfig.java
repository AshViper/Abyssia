package com.abyssia;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only options (abyssia-client.toml): underwater fog distance, how the deep ocean's large caverns look from inside,
 * and shader pack support.
 * Kept apart from the common config, so a dedicated server never loads them and tweaking them never rewrites the shared
 * common file.
 */
public final class ClientConfig
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Bumped when a default changes in a way old files should follow (see {@link #migrate}); 0 = file older than this key. */
    public static final ModConfigSpec.IntValue CONFIG_VERSION = BUILDER
            .comment("Internal: version of this file's defaults (do not edit)")
            .defineInRange("config_version", 0, 0, Integer.MAX_VALUE);

    static {
        BUILDER.comment("Underwater fog of the ocean world and the deep layer").push("fog");
    }

    public static final ModConfigSpec.IntValue DEEP_SEA_FOG_DISTANCE = BUILDER
            .comment("Underwater fog end distance (blocks) at every depth. Depth shows as denser, darker fog rather than a shorter view; marine snow, vent haze and caverns shorten it to 0.6x at most. Capped by the render distance")
            .defineInRange("deep_sea_fog_distance", 144, 64, 192);

    static {
        BUILDER.pop();
        BUILDER.comment("How large deep ocean caverns look from inside").push("caverns");
    }

    public static final ModConfigSpec.BooleanValue CAVERN_FOG = BUILDER
            .comment("Cavern haze: inside a large cavern the water near you stays clear while the far walls fade into fog, so the cavern reads as deep rather than as a box")
            .define("cavern_fog", true);
    public static final ModConfigSpec.DoubleValue CAVERN_FOG_DISTANCE = BUILDER
            .comment("Fog distance multiplier deep inside a massive cavern (below 1: a little hazier than open deep water)")
            .defineInRange("cavern_fog_distance_multiplier", 0.85, 0.3, 2.0);
    public static final ModConfigSpec.DoubleValue CAVERN_FOG_CLEAR = BUILDER
            .comment("Share of the fog distance around the camera that stays clear inside a massive cavern (near crisp, middle hazy, far hidden)")
            .defineInRange("cavern_fog_clear_fraction", 0.3, 0.0, 0.8);
    public static final ModConfigSpec.DoubleValue CAVERN_FOG_DARKENING = BUILDER
            .comment("How much darker the fog colour gets deep inside a massive cavern (0 = unchanged)")
            .defineInRange("cavern_fog_darkening", 0.15, 0.0, 0.8);
    public static final ModConfigSpec.BooleanValue CAVERN_SNOW_DEPTH = BUILDER
            .comment("Marine snow depth cue in large caverns: flakes near you larger and brighter, distant ones smaller and fainter. The particle budget is unchanged")
            .define("cavern_snow_depth_cue", true);
    // AB04: halls (the large, massive and mega caverns) are judged by the same long-range cavern probe; each tweak can be switched off.
    public static final ModConfigSpec.BooleanValue HALL_FOG = BUILDER
            .comment("Halls: see further inside large caverns (replaces cavern_fog_distance_multiplier there)")
            .define("hall_fog", true);
    public static final ModConfigSpec.DoubleValue HALL_FOG_DISTANCE = BUILDER
            .comment("Fog distance multiplier deep inside a hall (1.35 x 144 = about 194 blocks; the render distance still caps it)")
            .defineInRange("hall_fog_distance_multiplier", 1.35, 0.5, 2.5);
    public static final ModConfigSpec.BooleanValue HALL_FOG_TINT = BUILDER
            .comment("Halls: tint the fog toward the biome's water colour, so silhouettes stand out against coloured haze instead of black")
            .define("hall_fog_tint", true);
    public static final ModConfigSpec.DoubleValue HALL_FOG_TINT_STRENGTH = BUILDER
            .comment("How far the hall fog colour moves toward the biome water colour (at 30% of its brightness)")
            .defineInRange("hall_fog_tint_strength", 0.65, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue HALL_NEAR_CLEAR = BUILDER
            .comment("Halls: keep the water around you clear of fog")
            .define("hall_near_clear", true);
    public static final ModConfigSpec.IntValue HALL_NEAR_CLEAR_DISTANCE = BUILDER
            .comment("Blocks around the camera kept clear of fog inside a hall")
            .defineInRange("hall_near_clear_distance", 12, 0, 48);
    public static final ModConfigSpec.BooleanValue HALL_BRIGHTNESS_FLOOR = BUILDER
            .comment("Halls: keep the fog colour from falling below a minimum brightness (render correction only: block light and mob spawning are unchanged)")
            .define("hall_brightness_floor", true);
    public static final ModConfigSpec.DoubleValue HALL_BRIGHTNESS_FLOOR_VALUE = BUILDER
            .comment("Minimum brightness (luminance 0..1) of the fog colour deep inside a hall")
            .defineInRange("hall_brightness_floor_value", 0.08, 0.0, 0.5);

    static {
        BUILDER.pop();
        BUILDER.comment("Iris / Oculus shader packs").push("shaders");
    }

    public static final ModConfigSpec.BooleanValue SHADER_FOG = BUILDER
            .comment("With a shader pack active, draw the mod's underwater fog (distance and colour by depth, marine snow, vents, caverns) over the pack's image. Packs replace vanilla fog with their own, which ignores these")
            .define("shader_fog", true);
    public static final ModConfigSpec.DoubleValue SHADER_FOG_OPACITY = BUILDER
            .comment("Strength of that fog over the shader pack's image (1 = as dense as without shaders)")
            .defineInRange("shader_fog_opacity", 1.0, 0.0, 1.0);

    static {
        BUILDER.pop();
        BUILDER.comment("How natural currents look").push("natural_currents");
    }

    public static final ModConfigSpec.IntValue CURRENT_PARTICLE_DISTANCE = BUILDER
            .comment("Draw current particles up to this many blocks from you")
            .defineInRange("particle_distance", 40, 8, 96);
    public static final ModConfigSpec.DoubleValue CURRENT_PARTICLE_DENSITY = BUILDER
            .comment("Multiplier on how many current particles are drawn (0 = none)")
            .defineInRange("particle_density", 1.0, 0.0, 4.0);

    static {
        BUILDER.pop();
        BUILDER.comment("How CU01 current streams look (bundles of white streaks)").push("current_streams");
    }

    public static final ModConfigSpec.IntValue STREAM_RENDER_DISTANCE = BUILDER
            .comment("Draw stream streaks up to this many blocks from you (near 0-32, mid 32-64, far 64-96)")
            .defineInRange("max_render_distance", 96, 0, 160);
    public static final ModConfigSpec.IntValue STREAM_PARTICLE_BUDGET = BUILDER
            .comment("Most stream streaks alive at once (nearest streams and those in front of you first; 0 = none)")
            .defineInRange("particle_budget", 600, 0, 4000);
    public static final ModConfigSpec.BooleanValue STREAM_RIBBONS = BUILDER
            .comment("Draw streams as translucent flowing ribbons, so they can be seen from outside")
            .define("ribbons", true);
    public static final ModConfigSpec.IntValue STREAM_RIBBON_DISTANCE = BUILDER
            .comment("Draw stream ribbons up to this many blocks from you (they fade out from 48 blocks)")
            .defineInRange("ribbon_distance", 128, 16, 256);

    static {
        BUILDER.pop();
        BUILDER.comment("Title screen").push("title");
    }

    public static final ModConfigSpec.BooleanValue TITLE_PANORAMA = BUILDER
            .comment("Use the Abyssia deep-sea title screen panorama (other resource packs' panoramas take priority)")
            .define("title_panorama", true);
    public static final ModConfigSpec.BooleanValue TITLE_PANORAMA_EFFECTS = BUILDER
            .comment("Draw faint bubbles, dust and a deep-blue tint over the title panorama")
            .define("title_panorama_effects", true);

    static {
        BUILDER.pop();
        BUILDER.comment("Waypoint beacon markers on the HUD").push("waypoint_beacon");
    }

    public static final ModConfigSpec.IntValue WAYPOINT_MAX_DISTANCE = BUILDER
            .comment("Beacons farther than this many blocks get no marker (0 = no limit: every beacon of the dimension is shown)")
            .defineInRange("max_display_distance", 0, 0, 1000000);
    public static final ModConfigSpec.IntValue WAYPOINT_HIDE_WITHIN = BUILDER
            .comment("Beacons this close (blocks) get no marker: you can already see them (0 = always show)")
            .defineInRange("hide_within_distance", 16, 0, 512);
    public static final ModConfigSpec.BooleanValue WAYPOINT_SHOW_DISTANCE = BUILDER
            .comment("Show the distance under each marker")
            .define("show_distance", true);
    public static final ModConfigSpec.BooleanValue WAYPOINT_SHOW_NAME = BUILDER
            .comment("Show the beacon name under each marker")
            .define("show_name", true);
    public static final ModConfigSpec.BooleanValue WAYPOINT_SHOW_OFFSCREEN = BUILDER
            .comment("Pin beacons that are off screen or behind you to the screen edge with an arrow")
            .define("show_offscreen_marker", true);
    public static final ModConfigSpec.IntValue WAYPOINT_MARKER_SIZE = BUILDER
            .comment("Marker diamond size in GUI pixels")
            .defineInRange("marker_size", 12, 8, 32);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * 1: W02 changed the waypoint marker defaults (hide within 64 -> 16 blocks, max distance 512 -> 0 = no limit). Files that
     * still hold the untouched old defaults move to the new ones; values the player changed are kept.
     */
    private static final int CURRENT_CONFIG_VERSION = 1;

    static void migrate(ModConfigEvent event)
    {
        if (event.getConfig().getSpec() != SPEC || CONFIG_VERSION.get() >= CURRENT_CONFIG_VERSION) return;
        if (WAYPOINT_HIDE_WITHIN.get() == 64) WAYPOINT_HIDE_WITHIN.set(16);
        if (WAYPOINT_MAX_DISTANCE.get() == 512) WAYPOINT_MAX_DISTANCE.set(0);
        CONFIG_VERSION.set(CURRENT_CONFIG_VERSION);
        SPEC.save();
    }

    private ClientConfig() {}
}
