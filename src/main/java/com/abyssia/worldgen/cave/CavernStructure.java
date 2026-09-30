package com.abyssia.worldgen.cave;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Structures a cavern template can call for, each with a base count at the reference cavern size (radius 24) and a
 * cap per cavern tier, so decoration grows with the cavern but stays bounded (small, large, massive caverns).
 */
public enum CavernStructure implements StringRepresentable
{
    //                                    base   small  large  massive
    PILLARS("pillars", 1.5, 2, 5, 10),
    FLOATING_ROCKS("floating_rocks", 0.8, 1, 3, 6),
    BRIDGES("bridges", 0.5, 1, 2, 4),
    SHELVES("shelves", 3.0, 4, 9, 18),
    WALL_CAVES("wall_caves", 7.0, 8, 20, 40),
    MOUNDS("mounds", 10.0, 12, 30, 60),
    HOLLOWS("hollows", 3.0, 3, 8, 14),
    VALLEYS("valleys", 1.0, 1, 3, 5),
    RUBBLE("rubble", 0.6, 1, 2, 4),
    STALACTITES("stalactites", 3.0, 3, 8, 16),
    ROCK_SPIKES("rock_spikes", 2.0, 2, 5, 10),
    MEGA_ORES("mega_ores", 0.6, 1, 2, 5),
    MINERAL_PILLARS("mineral_pillars", 0.4, 1, 2, 3),
    CRYSTAL_FORESTS("crystal_forests", 0.5, 1, 2, 3),
    LAKES("lakes", 0.4, 1, 1, 2),
    GARDENS("gardens", 0.4, 1, 1, 2),
    GROVES("groves", 0.6, 1, 2, 4);

    public static final Codec<CavernStructure> CODEC = StringRepresentable.fromEnum(CavernStructure::values);

    private final String name;
    public final double base;
    private final int[] caps;

    CavernStructure(String name, double base, int small, int large, int massive)
    {
        this.name = name;
        this.base = base;
        this.caps = new int[] {small, large, massive};
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
