package com.abyssia.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A small ancient tree on the seabed (same shape as a grown ancient sapling). */
public class AncientTreeFeature extends Feature<NoneFeatureConfiguration>
{
    public AncientTreeFeature()
    {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
    {
        return AncientTree.place(context.level(), context.origin(), context.random(), 2);
    }
}
