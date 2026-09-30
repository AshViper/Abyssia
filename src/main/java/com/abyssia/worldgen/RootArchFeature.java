package com.abyssia.worldgen;

import com.abyssia.block.HangingPlantBlock;
import com.abyssia.block.StackingPlantBlock;
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

import java.util.List;

/**
 * Abyssal Root Colony: a knot of ancient root on the seabed with roots arching out of it and down into the mud, a
 * landmark you can see from a distance. Resin roots hang from the arches, and the colony's plants (knotstalk columns,
 * amber fans) gather in their shelter. Everything stays within 12 blocks of the origin, inside the writable region.
 */
public class RootArchFeature extends Feature<RootArchFeature.Config>
{
    private static final int REACH = 12;

    public RootArchFeature()
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
        if (!level.getBlockState(origin).is(Blocks.WATER)) return false;

        int knotTop = origin.getY() + 2 + random.nextInt(3);
        for (int dy = -1; dy <= knotTop - origin.getY(); dy++)
        {
            int r = dy >= knotTop - origin.getY() - 1 ? 1 : 2;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    if (dx * dx + dz * dz <= r * r + 1) setRoot(level, origin.offset(dx, dy, dz), config);
        }

        int arches = Mth.nextInt(random, config.minArches(), config.maxArches());
        double start = random.nextDouble() * Math.PI * 2;
        for (int a = 0; a < arches; a++)
        {
            double angle = start + a * Math.PI * 2 / arches + (random.nextDouble() - 0.5) * 0.6;
            int length = Mth.nextInt(random, 7, REACH);
            arch(level, random, config, origin, knotTop, angle, length, 3 + random.nextInt(4));
        }
        return true;
    }

    private void arch(WorldGenLevel level, RandomSource random, Config config, BlockPos origin, int knotTop, double angle, int length, int rise)
    {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        int endX = origin.getX() + (int) Math.round(cos * length), endZ = origin.getZ() + (int) Math.round(sin * length);
        int endY = floorY(level, config, endX, knotTop, endZ) - 1;
        int steps = length * 3;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= steps; i++)
        {
            double t = i / (double) steps;
            int x = origin.getX() + (int) Math.round(cos * length * t);
            int z = origin.getZ() + (int) Math.round(sin * length * t);
            int y = (int) Math.round(Mth.lerp(t, knotTop, endY) + rise * Math.sin(Math.PI * t));
            setRoot(level, pos.set(x, y, z), config);
            if (t < 0.15 || t > 0.85) setRoot(level, pos.set(x, y - 1, z), config);
            // hanging resin roots under the high part of the arch
            if (t > 0.25 && t < 0.75 && random.nextInt(4) == 0) hang(level, random, config, pos.set(x, y - 1, z));
        }
        // plants sheltering under and beside the arch
        for (int k = 0; k < 6; k++)
        {
            double t = 0.2 + random.nextDouble() * 0.7;
            int x = origin.getX() + (int) Math.round(cos * length * t) + random.nextInt(5) - 2;
            int z = origin.getZ() + (int) Math.round(sin * length * t) + random.nextInt(5) - 2;
            int y = floorY(level, config, x, knotTop + rise, z);
            if (random.nextInt(3) == 0) column(level, random, config, new BlockPos(x, y, z));
            else floorPlant(level, config.floor().get(random.nextInt(config.floor().size())), new BlockPos(x, y, z));
        }
    }

    /**
     * The first water block above the seabed below {@code fromY}. Scans instead of reading the OCEAN_FLOOR_WG heightmap,
     * which only exists while a chunk generates (so /place feature works too); passes through the colony's own roots.
     */
    private static int floorY(WorldGenLevel level, Config config, int x, int fromY, int z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, fromY, z);
        for (int i = 0; i < 48 && pos.getY() > level.getMinBuildHeight(); i++)
        {
            BlockState below = level.getBlockState(pos.move(0, -1, 0));
            if (!below.is(config.root().getBlock()) && below.isFaceSturdy(level, pos, Direction.UP)) return pos.getY() + 1;
        }
        return fromY - 48;
    }

    private static void setRoot(WorldGenLevel level, BlockPos pos, Config config)
    {
        BlockState here = level.getBlockState(pos);
        if (here.is(Blocks.WATER) || here.canBeReplaced()) level.setBlock(pos, config.root(), 2);
    }

    private static void hang(WorldGenLevel level, RandomSource random, Config config, BlockPos top)
    {
        int length = 1 + random.nextInt(3);
        int placed = 0;
        while (placed < length && level.getBlockState(top.below(placed)).is(Blocks.WATER)) placed++;
        for (int i = 0; i < placed; i++)
            level.setBlock(top.below(i), config.hanging().setValue(HangingPlantBlock.TIP, i == placed - 1), 2);
    }

    private static void column(WorldGenLevel level, RandomSource random, Config config, BlockPos base)
    {
        if (!config.column().canSurvive(level, base) || !level.getBlockState(base).is(Blocks.WATER)) return;
        int height = 2 + random.nextInt(5);
        int placed = 0;
        while (placed < height && level.getBlockState(base.above(placed)).is(Blocks.WATER)) placed++;
        for (int i = 0; i < placed; i++)
            level.setBlock(base.above(i), config.column().setValue(StackingPlantBlock.TOP, i == placed - 1), 2);
    }

    private static void floorPlant(WorldGenLevel level, BlockState plant, BlockPos pos)
    {
        if (level.getBlockState(pos).is(Blocks.WATER) && plant.canSurvive(level, pos)) level.setBlock(pos, plant, 2);
    }

    public record Config(BlockState root, BlockState hanging, BlockState column, List<BlockState> floor, int minArches, int maxArches)
            implements FeatureConfiguration
    {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("root").forGetter(Config::root),
                BlockState.CODEC.fieldOf("hanging").forGetter(Config::hanging),
                BlockState.CODEC.fieldOf("column").forGetter(Config::column),
                BlockState.CODEC.listOf().fieldOf("floor").forGetter(Config::floor),
                Codec.intRange(1, 8).fieldOf("min_arches").forGetter(Config::minArches),
                Codec.intRange(1, 8).fieldOf("max_arches").forGetter(Config::maxArches)
        ).apply(i, Config::new));
    }
}
