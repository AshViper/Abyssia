package com.abyssia.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Abyssal rift field ({@code {"type": "abyssia:rift", ...}}, 2D, ignores Y): the ocean world's only way down to the
 * deep ocean. At most one rift per {@code cell_size} square cell, present with probability {@code chance} if the
 * {@code seabed} function at its axis is at or below {@code max_seabed}; radius between {@code min_radius} and
 * {@code max_radius}. Returns 1 on the axis, 0 at the radius, falling linearly outside and clamped to [-1, 1], so
 * -1 from two radii out and everywhere away from rifts.
 * <p>
 * The terrain opens the shaft where this is above 0 and the biome source places {@code abyssia:abyssal_rift} from
 * the same value (tools/gen_worldgen.py), so shaft and biome always coincide. Placement is seeded by {@code noise},
 * so it differs per world but is the same in both dimensions (the deep ocean keeps its arrival water open under it).
 */
public final class RiftDensityFunction implements DensityFunction
{
    public static final MapCodec<RiftDensityFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NoiseHolder.CODEC.fieldOf("noise").forGetter(f -> f.noise),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("seabed").forGetter(f -> f.seabed),
            Codec.intRange(64, 65536).fieldOf("cell_size").forGetter(f -> f.cellSize),
            Codec.floatRange(0f, 1f).fieldOf("chance").forGetter(f -> f.chance),
            Codec.intRange(2, 256).fieldOf("min_radius").forGetter(f -> f.minRadius),
            Codec.intRange(2, 256).fieldOf("max_radius").forGetter(f -> f.maxRadius),
            Codec.DOUBLE.fieldOf("max_seabed").forGetter(f -> f.maxSeabed)
    ).apply(i, RiftDensityFunction::new));
    public static final KeyDispatchDataCodec<RiftDensityFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final NoiseHolder noise;
    private final DensityFunction seabed;
    private final int cellSize;
    private final float chance;
    private final int minRadius;
    private final int maxRadius;
    private final double maxSeabed;
    /** Per-world salt read from the seeded noise; 0 until the noise is wired (never used for generation then). */
    private final long salt;

    public RiftDensityFunction(NoiseHolder noise, DensityFunction seabed, int cellSize, float chance, int minRadius, int maxRadius, double maxSeabed)
    {
        this.noise = noise;
        this.seabed = seabed;
        this.cellSize = cellSize;
        this.chance = chance;
        this.minRadius = Math.min(minRadius, maxRadius);
        this.maxRadius = Math.max(minRadius, maxRadius);
        this.maxSeabed = maxSeabed;
        this.salt = noise.noise() == null ? 0L
                : mix(Double.doubleToLongBits(noise.getValue(0.5, 17.25, -3.75)) * 31 + Double.doubleToLongBits(noise.getValue(-41.5, 3.125, 9.5)));
    }

    @Override
    public double compute(FunctionContext context)
    {
        int x = context.blockX();
        int z = context.blockZ();
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        long h = mix(salt ^ cellX * 0x9E3779B97F4A7C15L ^ cellZ * 0xC2B2AE3D27D4EB4FL);
        if (unit(h) >= chance) return -1.0;
        h = mix(h);
        double radius = minRadius + unit(h) * (maxRadius - minRadius);
        // Axes stay two radii inside their cell, so a point only ever needs its own cell.
        int margin = 2 * maxRadius;
        int span = Math.max(1, cellSize - 2 * margin);
        h = mix(h);
        int axisX = cellX * cellSize + Math.min(margin, cellSize / 2) + (int) (unit(h) * span);
        h = mix(h);
        int axisZ = cellZ * cellSize + Math.min(margin, cellSize / 2) + (int) (unit(h) * span);
        double dx = x - axisX;
        double dz = z - axisZ;
        double d2 = dx * dx + dz * dz;
        if (d2 >= 4.0 * radius * radius) return -1.0;
        if (seabed.compute(new SinglePointContext(axisX, 0, axisZ)) > maxSeabed) return -1.0;
        return Mth.clamp(1.0 - Math.sqrt(d2) / radius, -1.0, 1.0);
    }

    private static long mix(long z)
    {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double unit(long h)
    {
        return (h >>> 11) * 0x1.0p-53;
    }

    @Override
    public void fillArray(double[] values, ContextProvider provider)
    {
        provider.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor)
    {
        return visitor.apply(new RiftDensityFunction(visitor.visitNoise(noise), seabed.mapAll(visitor), cellSize, chance, minRadius, maxRadius, maxSeabed));
    }

    @Override
    public double minValue()
    {
        return -1.0;
    }

    @Override
    public double maxValue()
    {
        return 1.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec()
    {
        return CODEC;
    }
}
