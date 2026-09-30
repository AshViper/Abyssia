package com.abyssia.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * One column of a stacking plant (grass, tubes, kelp, giant kelp...). Its height follows a smooth world-space
 * noise, so neighbouring columns form groves of similar height instead of random spikes.
 */
public class ColumnPlantFeature extends Feature<ColumnPlantFeature.Config>
{
    private static final long NOISE_SALT = 0x6B656C70L;
    private static volatile long cachedSeed;
    private static volatile SimplexNoise cachedNoise;

    public ColumnPlantFeature()
    {
        super(Config.CODEC);
    }

    private static SimplexNoise noise(long seed)
    {
        SimplexNoise noise = cachedNoise;
        if (noise == null || cachedSeed != seed)
        {
            noise = new SimplexNoise(new XoroshiroRandomSource(seed ^ NOISE_SALT));
            cachedSeed = seed;
            cachedNoise = noise;
        }
        return noise;
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context)
    {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        Config config = context.config();

        BlockPos below = origin.below();
        if (!level.getBlockState(origin).is(Blocks.WATER) || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;

        double n = noise(level.getSeed()).getValue(origin.getX() * config.clusterScale(), origin.getZ() * config.clusterScale());
        double t = Mth.clamp((n + 1.0) * 0.5 + (random.nextDouble() - 0.5) * 0.2, 0.0, 1.0);
        int target = Mth.floor(Mth.lerp(t, config.minHeight(), config.maxHeight()));

        // Stop below the first non-water block so the stalk never cuts into overhangs or the surface.
        BlockPos.MutableBlockPos pos = origin.mutable();
        int height = 0;
        while (height < target && level.getBlockState(pos.setY(origin.getY() + height)).is(Blocks.WATER)) height++;
        if (height < 1) return false;

        BlockState head = config.head().hasProperty(GrowingPlantHeadBlock.AGE)
                ? config.head().setValue(GrowingPlantHeadBlock.AGE, GrowingPlantHeadBlock.MAX_AGE) : config.head();
        for (int i = 0; i < height - 1; i++) level.setBlock(pos.setY(origin.getY() + i), config.body(), 2);
        level.setBlock(pos.setY(origin.getY() + height - 1), head, 2);
        return true;
    }

    public record Config(BlockState head, BlockState body, int minHeight, int maxHeight, float clusterScale) implements FeatureConfiguration
    {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("head").forGetter(Config::head),
                BlockState.CODEC.fieldOf("body").forGetter(Config::body),
                Codec.intRange(1, 200).fieldOf("min_height").forGetter(Config::minHeight),
                Codec.intRange(1, 200).fieldOf("max_height").forGetter(Config::maxHeight),
                Codec.floatRange(0.0001f, 1.0f).fieldOf("cluster_scale").forGetter(Config::clusterScale)
        ).apply(i, Config::new));
    }
}
