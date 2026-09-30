package com.abyssia.worldgen.cave;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * What may stand at the core of a cavern: a landmark that breaks up its empty middle without filling it. The
 * surrounding space stays open, so the landmark reads as something huge floating in something huger.
 */
public enum CavernCenter implements StringRepresentable
{
    ANCIENT_PLANT("ancient_plant"),
    GIANT_CRYSTAL("giant_crystal"),
    MINERAL_PILLAR("mineral_pillar"),
    THERMAL_VENTS("thermal_vents"),
    DEEP_SEA_GARDEN("deep_sea_garden"),
    GIANT_PILLAR("giant_pillar"),
    ROCK_ISLAND("rock_island"),
    KELP_GROVE("kelp_grove"),
    LAKE("lake");

    public static final Codec<CavernCenter> CODEC = StringRepresentable.fromEnum(CavernCenter::values);

    private final String name;

    CavernCenter(String name)
    {
        this.name = name;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
