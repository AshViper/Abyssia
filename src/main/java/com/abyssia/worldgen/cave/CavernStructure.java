package com.abyssia.worldgen.cave;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Structures a cavern template can call for, each with a base count at the reference cavern size (radius 24) and a
 * cap per cavern tier, so decoration grows with the cavern but stays bounded (small, large, massive caverns).
 */
public enum CavernStructure implements StringRepresentable
{
    //                                    base   small  large  massive  mega
    PILLARS("pillars", 1.5, 2, 5, 10, 40),
    FLOATING_ROCKS("floating_rocks", 0.8, 1, 3, 6, 18),
    BRIDGES("bridges", 0.5, 1, 2, 4, 10),
    SHELVES("shelves", 3.0, 4, 9, 18, 50),
    WALL_CAVES("wall_caves", 7.0, 8, 20, 40, 120),
    MOUNDS("mounds", 10.0, 12, 30, 60, 150),
    HOLLOWS("hollows", 3.0, 3, 8, 14, 36),
    VALLEYS("valleys", 1.0, 1, 3, 5, 12),
    RUBBLE("rubble", 0.6, 1, 2, 4, 10),
    STALACTITES("stalactites", 3.0, 3, 8, 16, 48),
    ROCK_SPIKES("rock_spikes", 2.0, 2, 5, 10, 28),
    MEGA_ORES("mega_ores", 0.6, 1, 2, 5, 14),
    MINERAL_PILLARS("mineral_pillars", 0.4, 1, 2, 3, 8),
    CRYSTAL_FORESTS("crystal_forests", 0.5, 1, 2, 3, 8),
    LAKES("lakes", 0.4, 1, 1, 2, 4),
    GARDENS("gardens", 0.4, 1, 1, 2, 4),
    GROVES("groves", 0.6, 1, 2, 4, 10);

    public static final Codec<CavernStructure> CODEC = StringRepresentable.fromEnum(CavernStructure::values);

    private final String name;
    public final double base;
    private final int[] caps;

    CavernStructure(String name, double base, int small, int large, int massive, int mega)
    {
        this.name = name;
        this.base = base;
        this.caps = new int[] {small, large, massive, mega};
    }

    public int cap(Cavern.Tier tier)
    {
        return caps[tier.ordinal()];
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
