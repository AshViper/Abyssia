package com.abyssia.worldgen.cave;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Very rare, unique cave formations: the goals worth a long expedition. A landmark turns a system into a grand
 * route (entrance, side caves, a main chamber, an underground lake and a water tunnel) ending in one huge,
 * specially decorated chamber. Which ones a biome can host, and how often, is data ({@link CaveProfile}).
 */
public enum CaveLandmark implements StringRepresentable
{
    //                         name                         environment        radius   vertical
    MASSIVE_CRYSTAL_CHAMBER("massive_crystal_chamber", "crystal", 32, 48, 0.6),
    GIANT_KELP_CAVERN("giant_kelp_cavern", "forest", 36, 56, 0.75),
    ANCIENT_MINERAL_CHAMBER("ancient_mineral_chamber", "mineral", 30, 44, 0.55),
    THERMAL_CATHEDRAL("thermal_cathedral", "thermal", 40, 60, 0.85),
    ABYSSAL_UNDERGROUND_LAKE("abyssal_underground_lake", "underground_sea", 44, 68, 0.35),
    GIANT_STALACTITE_CHAMBER("giant_stalactite_chamber", "abyssal", 34, 52, 0.7),
    DEEP_CAVE_FOREST("deep_cave_forest", "forest", 30, 46, 0.65);

    public static final Codec<CaveLandmark> CODEC = StringRepresentable.fromEnum(CaveLandmark::values);

    private final String name;
    public final String environment;
    public final int minRadius;
    public final int maxRadius;
    public final double vertical;

    CaveLandmark(String name, String environment, int minRadius, int maxRadius, double vertical)
    {
        this.name = name;
        this.environment = environment;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.vertical = vertical;
    }

    public boolean hasLake()
    {
        return this == ABYSSAL_UNDERGROUND_LAKE;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
