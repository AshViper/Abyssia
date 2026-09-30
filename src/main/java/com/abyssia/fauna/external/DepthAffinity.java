package com.abyssia.fauna.external;

import com.abyssia.fauna.DepthZone;
import com.abyssia.fauna.FaunaSpawnRule;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Depth bands for other mods' animals, in real metres like the native spawn rules ({@link DepthZone} maps them onto
 * the world). In the deep ocean (coordinate offset 200) Y 240 is about 115 m, Y 163 1000 m, Y 63 4000 m, Y -37
 * 6000 m and the floor near Y -128 about 11700 m, so UPPER is the top of the dimension and HADAL its trenches.
 */
public enum DepthAffinity implements StringRepresentable
{
    /** Air breathers: the sunlit top, which the deep ocean never reaches (players leave it at Y 240, ~115 m). */
    SURFACE(0, 0, 150, 250),
    /** Ordinary ocean animals: the top of the deep ocean. */
    UPPER(0, 150, 1000, 2500),
    /** Animals that cope with the twilight and midnight zones. */
    MIDDLE(200, 1000, 4000, 6000),
    /** Deep-sea animals: the abyssal plains and below. */
    DEEP(1000, 3000, 11000, 12000),
    /** Trenches only. */
    HADAL(4000, 6000, 11000, 12000);

    public static final Codec<DepthAffinity> CODEC = StringRepresentable.fromEnum(DepthAffinity::values);

    private final FaunaSpawnRule.Depth depth;

    DepthAffinity(double min, double coreMin, double coreMax, double max)
    {
        this.depth = new FaunaSpawnRule.Depth(min, coreMin, coreMax, max);
    }

    public FaunaSpawnRule.Depth depth()
    {
        return this.depth;
    }

    @Override
    public String getSerializedName()
    {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
