package com.abyssia.worldgen;

import com.abyssia.block.OilKelpBlock;
import com.abyssia.registry.ModBlocks;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** OL01: one oil kelp column (4-6 segments, some already ripe) rooted on the seabed. */
public class OilKelpFeature extends Feature<NoneFeatureConfiguration>
{
    public OilKelpFeature()
    {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
    {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        BlockPos below = origin.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.isFaceSturdy(level, below, Direction.UP) || ground.is(ModTags.INHIBITS_PLANTS)) return false;
        int length = Mth.randomBetweenInclusive(random, 4, 6);
        BlockState kelp = ModBlocks.OIL_KELP.get().defaultBlockState();
        int placed = 0;
        for (int i = 0; i < length; i++)
        {
            BlockPos pos = origin.above(i);
            if (!level.getBlockState(pos).is(Blocks.WATER) || level.getFluidState(pos).getAmount() != 8) break;
            level.setBlock(pos, kelp.setValue(OilKelpBlock.BASE, i == 0).setValue(OilKelpBlock.RIPE, random.nextFloat() < 0.3f), 2);
            placed++;
        }
        return placed > 0;
    }
}
