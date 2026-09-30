package com.abyssia.worldgen;

import com.abyssia.registry.ModTags;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * A deep rock spire: a tall, tapering, slightly leaning pillar with lumpy sides, rooted into the seabed.
 * Built from the local rock with accent streaks (minerals, magma veins or crystal), it is a landmark
 * visible through the fog from a distance.
 */
public class RockSpireFeature extends Feature<RockSpireFeature.Config>
{
    private static final double MAX_LEAN = 0.12;
    private static final int ROOT_DEPTH = 4;

    public RockSpireFeature()
    {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context)
    {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        int base = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());
        int height = Mth.randomBetweenInclusive(random, config.minHeight(), config.maxHeight());
        if (base + height >= level.getMaxBuildHeight() - 12) return false;

        double baseRadius = config.minRadius() + random.nextDouble() * (config.maxRadius() - config.minRadius());
        double leanX = (random.nextDouble() - 0.5) * 2 * MAX_LEAN, leanZ = (random.nextDouble() - 0.5) * 2 * MAX_LEAN;
        SimplexNoise noise = new SimplexNoise(new XoroshiroRandomSource(random.nextLong()));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int h = -ROOT_DEPTH; h <= height; h++)
        {
            double t = Math.max(0, h) / (double) height;
            // Tapers toward a narrow tip, with bulges and pinches along its length.
            double radius = baseRadius * Math.pow(1 - t, 0.8) * (0.85 + 0.3 * noise.getValue(h * 0.15, 0.5)) + 0.5;
            double cx = origin.getX() + 0.5 + leanX * Math.max(0, h);
            double cz = origin.getZ() + 0.5 + leanZ * Math.max(0, h);
            int r = Mth.ceil(radius + 1);
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    int x = Mth.floor(cx) + dx, z = Mth.floor(cz) + dz;
                    double ddx = x + 0.5 - cx, ddz = z + 0.5 - cz;
                    double rr = radius * (1 + 0.2 * noise.getValue(x * 0.4, z * 0.4 + h * 0.2));
                    if (ddx * ddx + ddz * ddz > rr * rr) continue;
                    pos.set(x, base + h, z);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    BlockState current = level.getBlockState(pos);
                    if (!current.is(Blocks.WATER) && !current.is(ModTags.VEIN_REPLACEABLE) && !current.canBeReplaced()) continue;
                    boolean accent = noise.getValue(x * 0.3, (base + h) * 0.45 + z * 0.3) > 1 - config.accentChance() * 2;
                    level.setBlock(pos, accent ? config.accent() : config.rock(), 2);
                }
            }
        }
        return true;
    }

    public record Config(BlockState rock, BlockState accent, float accentChance, int minHeight, int maxHeight,
                         float minRadius, float maxRadius) implements FeatureConfiguration
    {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("rock").forGetter(Config::rock),
                BlockState.CODEC.fieldOf("accent").forGetter(Config::accent),
                Codec.floatRange(0f, 1f).fieldOf("accent_chance").forGetter(Config::accentChance),
                Codec.intRange(4, 64).fieldOf("min_height").forGetter(Config::minHeight),
                Codec.intRange(4, 64).fieldOf("max_height").forGetter(Config::maxHeight),
                Codec.floatRange(1f, 6f).fieldOf("min_radius").forGetter(Config::minRadius),
                Codec.floatRange(1f, 6f).fieldOf("max_radius").forGetter(Config::maxRadius)
        ).apply(i, Config::new));
    }
}
