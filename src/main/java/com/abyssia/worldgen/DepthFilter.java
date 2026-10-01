package com.abyssia.worldgen;

import com.abyssia.fauna.DepthZone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Keeps a deep-ocean placement inside a depth band in metres ({@code {"type": "abyssia:depth", "min_depth": 1000,
 * "max_depth": 6200}}), so plant species change with depth inside one biome. Must come after the floor step
 * ({@code abyssia:deep_floor}): it reads the Y of the position it is given. Metres follow {@link DepthZone}, whose
 * tables count in the old deep-ocean Y.
 */
public class DepthFilter extends PlacementFilter
{
    public static final Codec<DepthFilter> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("min_depth").forGetter(f -> f.minDepth),
            Codec.INT.fieldOf("max_depth").forGetter(f -> f.maxDepth)
    ).apply(i, DepthFilter::new));

    private final int minDepth;
    private final int maxDepth;

    private DepthFilter(int minDepth, int maxDepth)
    {
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos)
    {
        double metres = DepthZone.deepOceanMetres(DeepLayer.toDeepY(pos.getY()));
        return metres >= minDepth && metres <= maxDepth;
    }

    @Override
    public PlacementModifierType<?> type()
    {
        return ModWorldgen.DEPTH_FILTER.get();
    }
}
