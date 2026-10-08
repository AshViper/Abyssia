package com.abyssia.worldgen;

import com.abyssia.Config;
import com.abyssia.registry.ModTags;
import com.abyssia.worldgen.deposit.OreDepositManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An ore vein: a noise-warped body of ore inside host rock, following a direction (horizontal, vertical,
 * diagonal, thin vein, compact cluster, or flat along the strata). Ore is richest along the core and thins
 * into host rock at the rim. Exposed veins break through the seabed and are ringed by nodules / clusters,
 * so they can be spotted from afar. There is no crust (ORE01).
 */
public class OreVeinFeature extends Feature<OreVeinFeature.VeinConfig>
{
    public enum Size implements StringRepresentable
    {
        SMALL, MEDIUM, LARGE, HUGE;

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase();
        }

        int blocks(RandomSource random)
        {
            int[] range = switch (this)
            {
                case SMALL -> new int[] {Config.SMALL_VEIN_MIN.get(), Config.SMALL_VEIN_MAX.get()};
                case MEDIUM -> new int[] {Config.MEDIUM_VEIN_MIN.get(), Config.MEDIUM_VEIN_MAX.get()};
                case LARGE -> new int[] {Config.LARGE_VEIN_MIN.get(), Config.LARGE_VEIN_MAX.get()};
                case HUGE -> new int[] {Config.HUGE_VEIN_MIN.get(), Config.HUGE_VEIN_MAX.get()};
            };
            int count = Mth.randomBetweenInclusive(random, range[0], Math.max(range[0], range[1]));
            if (this == LARGE || this == HUGE) count = (int) Math.round(count * Config.LARGE_VEIN_MULTIPLIER.get());
            return Math.max(1, count);
        }

        /** Mineable amount recorded for the deposit; independent of how many blocks the vein body has. */
        int depositAmount(RandomSource random)
        {
            int[] range = switch (this)
            {
                case SMALL -> new int[] {Config.SMALL_DEPOSIT_MIN.get(), Config.SMALL_DEPOSIT_MAX.get()};
                case MEDIUM -> new int[] {Config.MEDIUM_DEPOSIT_MIN.get(), Config.MEDIUM_DEPOSIT_MAX.get()};
                case LARGE -> new int[] {Config.LARGE_DEPOSIT_MIN.get(), Config.LARGE_DEPOSIT_MAX.get()};
                case HUGE -> new int[] {Config.HUGE_DEPOSIT_MIN.get(), Config.HUGE_DEPOSIT_MAX.get()};
            };
            return Mth.randomBetweenInclusive(random, range[0], Math.max(range[0], range[1]));
        }

        public static final Codec<Size> CODEC = StringRepresentable.fromEnum(Size::values);
    }

    public enum Shape implements StringRepresentable
    {
        HORIZONTAL, VERTICAL, DIAGONAL, VEIN, CLUSTER, STRATUM;

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase();
        }

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);
    }

    /** Keeps every block within the region a feature may write to. */
    private static final double MAX_REACH = 13.0;
    private static final Codec<Size> SIZE_CODEC = StringRepresentable.fromEnum(Size::values);

    public OreVeinFeature()
    {
        super(VeinConfig.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<VeinConfig> context)
    {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        VeinConfig config = context.config();
        BlockPos origin = context.origin();

        int target = config.size().blocks(random);
        boolean exposed = Config.SURFACE_VEINS_ENABLED.get()
                && random.nextDouble() < Math.min(1.0, Config.EXPOSED_VEIN_CHANCE.get() * config.exposure()
                * (config.size() == Size.HUGE ? 3 : config.size() == Size.LARGE ? 2 : 1));
        Shape shape = Shape.values()[random.nextInt(Shape.values().length)];

        // Body dimensions from the target volume: thickness r, path length L (volume ~ pi r^2 L).
        double r = switch (shape)
        {
            case VEIN -> 0.9 + Math.cbrt(target) * 0.25;
            case CLUSTER -> Math.cbrt(target * 0.3);
            case STRATUM -> 0.8 + Math.cbrt(target) * 0.2;
            default -> 0.8 + Math.cbrt(target) * 0.35;
        };
        double length = Math.min(MAX_REACH * 2 - 2 * r - 2, Math.max(r, target / (Math.PI * r * r)));
        if (shape == Shape.CLUSTER) length = r * 1.2;
        double[] dir = direction(shape, random);

        // Exposed veins straddle the seabed; buried ones sit a few blocks under it.
        int floor = DeepFloorPlacement.surface(level, origin.getX(), origin.getZ(), origin.getY());
        double cy = exposed ? floor - 0.5 : floor - r - 2 - random.nextInt(6);
        if (cy - r < level.getMinBuildHeight() + 3) return false;
        double cx = origin.getX() + 0.5, cz = origin.getZ() + 0.5;

        SimplexNoise noise = new SimplexNoise(new XoroshiroRandomSource(random.nextLong()));
        // Purity per position: 0 at the core, 1 at the rim. Keep the best (lowest) from any path point.
        Map<Long, Double> purity = new HashMap<>();
        int steps = Math.max(1, (int) Math.ceil(length / 0.7));
        for (int i = 0; i <= steps; i++)
        {
            double t = steps == 0 ? 0 : i / (double) steps - 0.5;
            double warp = length * 0.12;
            double px = cx + dir[0] * t * length + noise.getValue(t * 3, 0.5) * warp;
            double py = cy + dir[1] * t * length + noise.getValue(t * 3, 10.5) * warp * 0.6;
            double pz = cz + dir[2] * t * length + noise.getValue(t * 3, 20.5) * warp;
            double radius = r * (0.75 + 0.5 * (noise.getValue(t * 5, 30.5) * 0.5 + 0.5));
            int reach = Mth.ceil(radius * 1.3 + 1);
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dy = -reach; dy <= reach; dy++)
                {
                    for (int dz = -reach; dz <= reach; dz++)
                    {
                        int bx = Mth.floor(px) + dx, by = Mth.floor(py) + dy, bz = Mth.floor(pz) + dz;
                        if (Math.abs(bx + 0.5 - cx) > MAX_REACH || Math.abs(bz + 0.5 - cz) > MAX_REACH) continue;
                        double ddx = bx + 0.5 - px, ddy = (by + 0.5 - py) * (shape == Shape.STRATUM ? 2.2 : 1.0), ddz = bz + 0.5 - pz;
                        double d = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) / radius
                                + noise.getValue(bx * 0.35, by * 0.35 + bz * 0.21) * 0.18;
                        if (d > 1.3) continue;
                        purity.merge(BlockPos.asLong(bx, by, bz), d, Math::min);
                    }
                }
            }
        }

        // Paint bottom-up so exposed blocks above the seabed rest on what was painted below them.
        List<Map.Entry<Long, Double>> cells = new ArrayList<>(purity.entrySet());
        cells.sort((a, b) -> Integer.compare(BlockPos.getY(a.getKey()), BlockPos.getY(b.getKey())));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int ores = 0;
        for (Map.Entry<Long, Double> cell : cells)
        {
            pos.set(cell.getKey());
            if (level.isOutsideBuildHeight(pos)) continue;
            BlockState current = level.getBlockState(pos);
            double d = cell.getValue();
            boolean water = current.is(Blocks.WATER);
            if (water)
            {
                // Only the core may stand proud of the seabed, and only on support.
                if (d > 0.9 || !exposed) continue;
                BlockState below = level.getBlockState(pos.below());
                if (below.is(Blocks.WATER) || !below.isFaceSturdy(level, pos.below(), Direction.UP)) continue;
            }
            else if (!current.is(ModTags.VEIN_REPLACEABLE))
            {
                continue;
            }
            BlockState state;
            if (d < 0.55) state = random.nextFloat() < 0.9f ? config.ore() : config.host();
            else if (d < 0.9) state = random.nextFloat() < 0.4f ? config.ore() : config.host();
            else if (water) continue;
            else state = config.host();
            if (state == config.ore()) ores++;
            level.setBlock(pos, state, 2);
        }

        decorateSurface(level, random, config, cx, cz, r, exposed, floor);

        // Register the ore deposit for tracking
        net.minecraft.server.level.ServerLevel serverLevel = level.getLevel(); // Java 17: no same-type instanceof pattern
        if (ores > 0 && serverLevel != null
                && com.abyssia.industry.ExcavatorMinerals.isMinable(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(config.ore().getBlock())))
        {
            // Compute bounds: the vein extends up to MAX_REACH horizontally from center,
            // and vertically from cy-r-2 (bottom) to floor+1 (top, including exposed ore)
            double minY = cy - r - 2;
            double maxY = floor + 1; // include exposed ore above seabed
            AABB bounds = new AABB(
                    cx - MAX_REACH, minY, cz - MAX_REACH,
                    cx + MAX_REACH, maxY, cz + MAX_REACH
            );
            BlockPos centerPos = BlockPos.containing(cx, cy, cz);
            ResourceLocation mineralId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(config.ore().getBlock());
            // worldgen runs on worker threads: queue, the main thread registers (OreDepositFlusher)
            // The body keeps its generated size; the deposit's mineable amount is configured separately.
            OreDepositManager.enqueue(serverLevel, mineralId, centerPos, bounds, config.size(), shape,
                    config.size().depositAmount(random));
        }

        return ores > 0;
    }

    /** Nodules / clusters on the seabed around (exposed) or above (buried) the vein. */
    private void decorateSurface(WorldGenLevel level, RandomSource random, VeinConfig config, double cx, double cz, double r, boolean exposed, int floorY)
    {
        if (config.cluster().isEmpty()) return;
        double radius = exposed ? r + 4 : r + 1;
        double coverage = exposed ? 0.8 : 0.25;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int ir = Mth.ceil(Math.min(radius, MAX_REACH));
        for (int dx = -ir; dx <= ir; dx++)
        {
            for (int dz = -ir; dz <= ir; dz++)
            {
                double d = Math.sqrt(dx * dx + dz * dz) / radius;
                if (d > 1 || random.nextDouble() > coverage * (1 - d * d)) continue;
                int x = Mth.floor(cx) + dx, z = Mth.floor(cz) + dz;
                int top = DeepFloorPlacement.surface(level, x, z, floorY);
                pos.set(x, top - 1, z);
                if (config.cluster().isPresent() && random.nextFloat() < (exposed ? 0.3f : 0.1f) * (1 - d))
                {
                    pos.set(x, top, z);
                    if (level.getBlockState(pos).is(Blocks.WATER) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
                    {
                        BlockState cluster = config.cluster().get();
                        if (cluster.hasProperty(AmethystClusterBlock.FACING)) cluster = cluster.setValue(AmethystClusterBlock.FACING, Direction.UP);
                        if (cluster.hasProperty(AmethystClusterBlock.WATERLOGGED)) cluster = cluster.setValue(AmethystClusterBlock.WATERLOGGED, true);
                        level.setBlock(pos, cluster, 2);
                    }
                }
            }
        }
    }

    private static double[] direction(Shape shape, RandomSource random)
    {
        double yaw = random.nextDouble() * Math.PI * 2;
        double pitch = switch (shape)
        {
            case VERTICAL -> Math.toRadians(75 + random.nextDouble() * 15);
            case DIAGONAL -> Math.toRadians(35 + random.nextDouble() * 20);
            case HORIZONTAL, STRATUM -> Math.toRadians((random.nextDouble() - 0.5) * 16);
            default -> Math.toRadians((random.nextDouble() - 0.5) * 120);
        };
        return new double[] {Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch)};
    }

    /**
     * @param exposure multiplier on the configured chance of breaking through the seabed
     */
    public record VeinConfig(BlockState ore, BlockState host, Optional<BlockState> cluster,
                         Size size, float exposure) implements FeatureConfiguration
    {
        public static final Codec<VeinConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("ore").forGetter(VeinConfig::ore),
                BlockState.CODEC.fieldOf("host").forGetter(VeinConfig::host),
                BlockState.CODEC.optionalFieldOf("cluster").forGetter(VeinConfig::cluster),
                SIZE_CODEC.fieldOf("size").forGetter(VeinConfig::size),
                Codec.floatRange(0f, 10f).optionalFieldOf("exposure", 1f).forGetter(VeinConfig::exposure)
        ).apply(i, VeinConfig::new));
    }
}
