package com.abyssia;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only options (abyssia-client.toml): how the deep ocean's large caverns look from inside, and shader pack support.
 * Kept apart from the common config, so a dedicated server never loads them and tweaking them never rewrites the shared
 * common file.
 */
public final class ClientConfig
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
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
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}
}
