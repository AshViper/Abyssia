package com.abyssia.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** A tapering, slightly leaning crystal spire rooted in the seabed, ringed by crystal clusters. */
public class CrystalSpikeFeature extends Feature<CrystalSpikeFeature.Config>
{
    private static final double MAX_TILT = 0.3;
    private static final int CLUSTER_TRIES = 12;
    private static final int CLUSTER_SPREAD = 5;

    public CrystalSpikeFeature()
    {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context)
    {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        Config config = context.config();
        if (!level.getBlockState(origin).is(Blocks.WATER) || !level.getBlockState(origin.below()).isFaceSturdy(level, origin.below(), Direction.UP)) return false;

        int height = Mth.randomBetweenInclusive(random, config.minHeight(), config.maxHeight());
        double baseRadius = 1.0 + height * 0.12;
        double tiltX = (random.nextDouble() * 2 - 1) * MAX_TILT;
        double tiltZ = (random.nextDouble() * 2 - 1) * MAX_TILT;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = -2; y <= height; y++)
        {
            double radius = baseRadius * (1.0 - Math.max(0, y) / (double) height) + 0.3;
            double cx = tiltX * Math.max(0, y);
            double cz = tiltZ * Math.max(0, y);
            int r = Mth.ceil(radius);
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    int bx = Mth.floor(cx) + dx;
                    int bz = Mth.floor(cz) + dz;
                    if ((bx - cx) * (bx - cx) + (bz - cz) * (bz - cz) > radius * radius) continue;
                    pos.set(origin.getX() + bx, origin.getY() + y, origin.getZ() + bz);
                    // Above the seabed only replace water; below it, root into the ground.
                    if (y > 0 && !level.getBlockState(pos).is(Blocks.WATER)) continue;
                    level.setBlock(pos, config.crystal(), 2);
                }
            }
        }

        for (int i = 0; i < CLUSTER_TRIES; i++)
        {
            int x = origin.getX() + random.nextInt(CLUSTER_SPREAD * 2 + 1) - CLUSTER_SPREAD;
            int z = origin.getZ() + random.nextInt(CLUSTER_SPREAD * 2 + 1) - CLUSTER_SPREAD;
            for (int y = origin.getY() + 3; y >= origin.getY() - 4; y--)
            {
                pos.set(x, y, z);
                if (!level.getBlockState(pos).is(Blocks.WATER)) continue;
                BlockPos below = pos.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
                level.setBlock(pos, config.cluster(), 2);
                break;
            }
        }
        return true;
    }

    public record Config(BlockState crystal, BlockState cluster, int minHeight, int maxHeight) implements FeatureConfiguration
    {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("crystal").forGetter(Config::crystal),
                BlockState.CODEC.fieldOf("cluster").forGetter(Config::cluster),
                Codec.intRange(2, 14).fieldOf("min_height").forGetter(Config::minHeight),
                Codec.intRange(2, 14).fieldOf("max_height").forGetter(Config::maxHeight)
        ).apply(i, Config::new));
    }
}
