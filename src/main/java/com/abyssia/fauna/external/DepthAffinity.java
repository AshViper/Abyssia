package com.abyssia.fauna.external;

import com.abyssia.fauna.DepthZone;
import com.abyssia.fauna.FaunaSpawnRule;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Depth bands for other mods' animals, in real metres like the native spawn rules ({@link DepthZone} maps them onto
 * the world). Ocean world water reads at most about 500 m (below Y 0); in the deep layer under the bedrock band the top
 * (Y -64) is about 830 m, Y -77 1000 m, Y -177 4000 m, Y -277 6000 m and the floor near Y -368 about 11700 m, so
 * UPPER is the ocean world's deep water and the top of the deep layer, and HADAL its trenches.
 */
public enum DepthAffinity implements StringRepresentable
{
    /** Air breathers: the sunlit top of the ocean world; the deep layer never reaches it. */
    SURFACE(0, 0, 150, 250),
    /** Ordinary ocean animals: deep ocean world water and the top of the deep layer. */
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
