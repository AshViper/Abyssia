package com.abyssia.entity.giant;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The one entity class of every GNT01 giant species: what differs is only the {@link GiantSpec} (see {@link #SPECS}). */
public class GiantSpecies extends GiantFauna
{
    private static final GiantBrain.Temper F = GiantBrain.Temper.FRIENDLY, N = GiantBrain.Temper.NEUTRAL, H = GiantBrain.Temper.HOSTILE;

    // id, length, joints, radius, joints per part, hp, armor, cruise speed (b/tick; hostile chase = x1.8 stays under the submarine's 0.42), temper,
    // player dmg, submarine hurt, hold animation, cull side pad, cull down pad
    public static final GiantSpec LANCEJAW = new GiantSpec("lancejaw", 32, 17, 2.5, 2, 240, 6, 0.20, H, 8F, 1.4F, "jaw_out", 6.25, 0);
    public static final GiantSpec LUMENWING = new GiantSpec("lumenwing", 60, 31, 5, 3, 200, 2, 0.08, F, 6F, 1.0F, null, 30, 0);
    public static final GiantSpec LANTERN_GRAZER = new GiantSpec("lantern_grazer", 52, 27, 4, 3, 260, 4, 0.10, F, 6F, 1.0F, null, 10, 0);
    public static final GiantSpec HUSH_WHALE = new GiantSpec("hush_whale", 48, 25, 4, 3, 360, 8, 0.18, H, 10F, 1.6F, "charge", 10, 0);
    public static final GiantSpec SLATEBACK = new GiantSpec("slateback", 34, 18, 3, 2, 300, 10, 0.15, N, 8F, 1.2F, "jaw_out", 7.5, 0);
    public static final GiantSpec RIFT_LAMPREY = new GiantSpec("rift_lamprey", 44, 23, 2.5, 3, 220, 4, 0.20, H, 7F, 1.2F, "latch", 6.25, 0);
    public static final GiantSpec SHROUD_RAY = new GiantSpec("shroud_ray", 32, 17, 6, 2, 200, 4, 0.20, H, 6F, 1.0F, "shroud", 22, 0);
    public static final GiantSpec GLASSBACK = new GiantSpec("glassback", 50, 26, 2.5, 3, 260, 4, 0.18, H, 8F, 1.4F, "jaw_out", 6.25, 0);
    public static final GiantSpec CRESTFIN = new GiantSpec("crestfin", 42, 22, 3, 2, 320, 8, 0.15, N, 9F, 1.4F, "crest_up", 8, 0);
    public static final GiantSpec DUSKMAW = new GiantSpec("duskmaw", 56, 29, 4, 3, 340, 6, 0.17, H, 10F, 1.6F, "grab", 10, 14);

    /** Every species, in the order of {@code /abyssia giants list}. */
    public static final GiantSpec[] SPECS = {LANCEJAW, LUMENWING, LANTERN_GRAZER, HUSH_WHALE, SLATEBACK, RIFT_LAMPREY, SHROUD_RAY, GLASSBACK, CRESTFIN, DUSKMAW};

    public GiantSpecies(EntityType<? extends GiantSpecies> type, Level level, GiantSpec spec)
    {
        super(type, level, spec);
    }
}
