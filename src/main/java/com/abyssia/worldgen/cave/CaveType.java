package com.abyssia.worldgen.cave;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Shapes of cave system. Each type fixes the geometry (radius range, vertical proportions, wall roughness, how
 * branched it is, how deep it sits) and the environment it defaults to; how often each appears is data, set per
 * biome group by {@link CaveProfile#caveTypes()}.
 */
public enum CaveType implements StringRepresentable
{
    //                  name                 size             r min  r max  vertical  rough  smooth  branches  environment
    SMALL_SEA_CAVE("small_sea_cave", Size.SMALL, 3, 8, 0.75, 1.2, 0.0, 0, 1, null),
    MEDIUM_SEA_CAVE("medium_sea_cave", Size.MEDIUM, 8, 16, 0.7, 1.6, 0.0, 1, 2, null),
    LARGE_ABYSSAL_CAVE("large_abyssal_cave", Size.LARGE, 16, 32, 0.65, 2.5, 0.0, 2, 3, null),
    MASSIVE_CAVERN("massive_cavern", Size.MASSIVE, 32, 64, 0.62, 3.5, 0.0, 2, 4, null),
    SEA_TUNNEL("sea_tunnel", Size.MEDIUM, 4, 9, 0.8, 1.4, 0.3, 0, 1, null),
    SEA_ARCH("sea_arch", Size.SMALL, 3, 7, 0.8, 1.2, 0.5, 0, 0, "eroded"),
    VERTICAL_SHAFT("vertical_shaft", Size.MEDIUM, 4, 10, 1.0, 1.8, 0.0, 1, 2, null),
    TRENCH_CAVE("trench_cave", Size.LARGE, 10, 24, 1.3, 2.2, 0.0, 1, 2, "trench"),
    MINERAL_CAVE("mineral_cave", Size.MEDIUM, 8, 18, 0.7, 2.0, 0.0, 1, 2, "mineral"),
    CRYSTAL_CAVE("crystal_cave", Size.MEDIUM, 8, 20, 0.75, 1.6, 0.0, 1, 2, "crystal"),
    THERMAL_CAVE("thermal_cave", Size.MEDIUM, 8, 20, 0.7, 2.0, 0.0, 1, 2, "thermal"),
    ERODED_CAVE("eroded_cave", Size.MEDIUM, 6, 14, 0.8, 0.6, 0.85, 1, 2, "eroded"),
    UNDERGROUND_SEA("underground_sea", Size.MASSIVE, 40, 72, 0.3, 2.0, 0.3, 1, 2, "underground_sea"),
    /** AB02: halls 128-256 wide, only in the crust windows (never in the shallow network); built from wide flat halls, chimneys and rooms. */
    MEGA_CAVERN("mega_cavern", Size.MEGA, 64, 128, 0.62, 3.5, 0.0, 2, 4, null);

    public static final Codec<CaveType> CODEC = StringRepresentable.fromEnum(CaveType::values);

    public enum Size
    {
        SMALL(Rarity.COMMON), MEDIUM(Rarity.COMMON), LARGE(Rarity.UNCOMMON), MASSIVE(Rarity.RARE), MEGA(Rarity.RARE);

        public final Rarity rarity;

        Size(Rarity rarity)
        {
            this.rarity = rarity;
        }
    }

    public enum Rarity { COMMON, UNCOMMON, RARE, VERY_RARE }

    private final String name;
    public final Size size;
    public final int minRadius;
    public final int maxRadius;
    /** Vertical radius as a fraction of the horizontal one: below 1 for flat halls, above 1 for tall trench caves. */
    public final double vertical;
    /** Amplitude of fine wall detail, in blocks. */
    public final double roughness;
    /** 0 for broken, jagged rock; toward 1 for walls polished smooth by water (eroded caves). */
    public final double smoothness;
    public final int minBranches;
    public final int maxBranches;
    /** Environment this type always uses, or null to use the biome's own. */
    public final String environment;

    CaveType(String name, Size size, int minRadius, int maxRadius, double vertical, double roughness, double smoothness,
             int minBranches, int maxBranches, String environment)
    {
        this.name = name;
        this.size = size;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.vertical = vertical;
        this.roughness = roughness;
        this.smoothness = smoothness;
        this.minBranches = minBranches;
        this.maxBranches = maxBranches;
        this.environment = environment;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
