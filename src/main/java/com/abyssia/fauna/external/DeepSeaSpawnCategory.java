package com.abyssia.fauna.external;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What role another mod's animal plays in the deep ocean, and its spawn defaults: weight (on the native rules' scale,
 * where a typical species has 10), group size, population cap, preference for caves and depth band. Biome rules
 * (data/&lt;namespace&gt;/external_fauna/biomes) weigh categories per biome; entity rules can override any of this.
 */
public enum DeepSeaSpawnCategory implements StringRepresentable
{
    /** Small schooling fish. */
    AMBIENT(8, 2, 5, 10, 48, 0.5F, DepthAffinity.UPPER),
    /** Other small, harmless animals (squid, rays...). */
    SMALL_CREATURE(6, 1, 3, 6, 48, 0.7F, DepthAffinity.UPPER),
    /** Hostile animals and hunters. */
    PREDATOR(3, 1, 2, 3, 64, 0.8F, DepthAffinity.MIDDLE),
    /** Big or strong animals: rare, alone, open water only, away from players. */
    LARGE_CREATURE(1, 1, 1, 1, 128, 0.0F, DepthAffinity.DEEP),
    /** Animals that already live in the dark (underground water, deep-ocean-only spawns). */
    DEEP_SEA(5, 1, 2, 4, 64, 1.2F, DepthAffinity.DEEP),
    /** Whitelisted animals that could not be examined. */
    UNKNOWN(2, 1, 1, 2, 64, 0.5F, DepthAffinity.MIDDLE);

    public static final Codec<DeepSeaSpawnCategory> CODEC = StringRepresentable.fromEnum(DeepSeaSpawnCategory::values);

    public final int weight;
    public final int groupMin;
    public final int groupMax;
    public final int capCount;
    public final int capRadius;
    /** Weight multiplier under rock (caves, overhangs). */
    public final float caveFactor;
    public final DepthAffinity depth;

    DeepSeaSpawnCategory(int weight, int groupMin, int groupMax, int capCount, int capRadius, float caveFactor, DepthAffinity depth)
    {
        this.weight = weight;
        this.groupMin = groupMin;
        this.groupMax = groupMax;
        this.capCount = capCount;
        this.capRadius = capRadius;
        this.caveFactor = caveFactor;
        this.depth = depth;
    }

    @Override
    public String getSerializedName()
    {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
