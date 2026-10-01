package com.abyssia.environment;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cheap, deterministic ocean current field: horizontal direction and strength come from smooth noise that
 * drifts slowly over game time, biomes scale the strength, and separate noise creates up/downwelling zones.
 * <p>
 * Seeds depend only on the dimension and layer (clients do not know the world seed), so every player sees the same
 * flow; the deep layer below the bedrock band flows on its own, keeping the former deep ocean dimension's seed.
 * It is side-agnostic so it can later push players, boats or items; today only particles use it.
 */
public final class OceanCurrentManager
{
    public enum Zone { WEAK, NORMAL, STRONG, UPWELLING, DOWNWELLING }

    /** Blocks per tick of horizontal flow per unit of biome strength. */
    private static final double SPEED_PER_STRENGTH = 0.05;
    private static final double DIRECTION_SCALE = 1.0 / 384.0;
    private static final double STRENGTH_SCALE = 1.0 / 192.0;
    private static final double VERTICAL_SCALE = 1.0 / 160.0;
    /** Game ticks per unit of noise time: the flow pattern turns over roughly every two in-game days. */
    private static final double TICKS_PER_TIME_UNIT = 48000.0;
    private static final double WELLING_THRESHOLD = 0.55;
    private static final double MAX_WELLING_SPEED = 0.012;
    private static final double DEFAULT_CONFIG_STRENGTH = 0.25;
    private static final double DEFAULT_BIOME_STRENGTH = 0.15;

    private static final Map<ResourceKey<Biome>, Double> BIOME_STRENGTH = Map.of(
            biome("twilight_reef"), 0.10,
            biome("deep_sea"), 0.20,
            biome("abyssal_ocean"), 0.30,
            biome("abyssal_forest"), 0.20,
            biome("deep_crystal_fields"), 0.20,
            biome("thermal_vents"), 0.30,
            biome("volcanic_deep"), 0.30,
            biome("abyssal_trench"), 0.45,
            biome("hadal_zone"), 0.15);
    private static final ResourceKey<Biome> TRENCH = biome("abyssal_trench");
    /** Extra downward pull inside trenches: water pours down the trench walls. */
    private static final double TRENCH_SINK = 0.004;

    /** Seed name of the deep layer's flow: the former deep ocean dimension's id, so its currents are unchanged. */
    private static final String DEEP_LAYER_SEED = Abyssia.MODID + ":deep_ocean";

    /** Noise cache key: a level's dimension, and whether it is the deep layer (no string built per call). */
    private record NoiseKey(ResourceKey<Level> dimension, boolean deep) {}

    private static final Map<NoiseKey, Noises> NOISES = new ConcurrentHashMap<>();

    private OceanCurrentManager() {}

    private static ResourceKey<Biome> biome(String name)
    {
        return ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name));
    }

    private record Noises(SimplexNoise direction, SimplexNoise strength, SimplexNoise vertical)
    {
        static Noises forName(String name)
        {
            long seed = name.hashCode() * 0x9E3779B97F4A7C15L;
            return new Noises(new SimplexNoise(new XoroshiroRandomSource(seed)),
                    new SimplexNoise(new XoroshiroRandomSource(seed + 1)),
                    new SimplexNoise(new XoroshiroRandomSource(seed + 2)));
        }
    }

    private static Noises noises(Level level, BlockPos pos)
    {
        return NOISES.computeIfAbsent(new NoiseKey(level.dimension(), DeepLayer.isDeep(level, pos.getY())),
                k -> Noises.forName(k.deep() ? DEEP_LAYER_SEED : k.dimension().location().toString()));
    }

    private static double time(Level level)
    {
        return level.getGameTime() / TICKS_PER_TIME_UNIT;
    }

    /** Biome strength multiplier for this position, including the config scale. */
    public static double getCurrentStrength(Level level, BlockPos pos)
    {
        double biome = level.getBiome(pos).unwrapKey().map(k -> BIOME_STRENGTH.getOrDefault(k, DEFAULT_BIOME_STRENGTH)).orElse(DEFAULT_BIOME_STRENGTH);
        double local = 0.6 + 0.4 * noises(level, pos).strength().getValue(pos.getX() * STRENGTH_SCALE, pos.getZ() * STRENGTH_SCALE, time(level));
        return biome * local * (Config.CURRENT_STRENGTH.get() / DEFAULT_CONFIG_STRENGTH);
    }

    /** Horizontal flow direction in radians (0 = +X). */
    public static double getCurrentDirection(Level level, BlockPos pos)
    {
        return noises(level, pos).direction().getValue(pos.getX() * DIRECTION_SCALE, pos.getZ() * DIRECTION_SCALE, time(level) * 0.5) * Math.PI * 2.0;
    }

    private static double welling(Level level, BlockPos pos)
    {
        return noises(level, pos).vertical().getValue(pos.getX() * VERTICAL_SCALE, pos.getZ() * VERTICAL_SCALE, time(level));
    }

    public static Zone getZone(Level level, BlockPos pos)
    {
        double w = welling(level, pos);
        if (w > WELLING_THRESHOLD) return Zone.UPWELLING;
        if (w < -WELLING_THRESHOLD) return Zone.DOWNWELLING;
        double strength = getCurrentStrength(level, pos);
        return strength > 0.3 ? Zone.STRONG : strength < 0.12 ? Zone.WEAK : Zone.NORMAL;
    }

    /** Current velocity in blocks per tick at this position. */
    public static Vec3 getCurrent(Level level, BlockPos pos)
    {
        double speed = getCurrentStrength(level, pos) * SPEED_PER_STRENGTH;
        double angle = getCurrentDirection(level, pos);
        double w = welling(level, pos);
        double vy = 0.0;
        if (Math.abs(w) > WELLING_THRESHOLD)
        {
            vy = Math.signum(w) * Mth.inverseLerp(Math.abs(w), WELLING_THRESHOLD, 1.0) * MAX_WELLING_SPEED;
        }
        if (level.getBiome(pos).is(TRENCH)) vy -= TRENCH_SINK;
        return new Vec3(Math.cos(angle) * speed, vy, Math.sin(angle) * speed);
    }

    public static Vec3 getCurrent(Level level, double x, double y, double z)
    {
        return getCurrent(level, BlockPos.containing(x, y, z));
    }
}
