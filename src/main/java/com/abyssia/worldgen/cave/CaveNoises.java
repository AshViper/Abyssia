package com.abyssia.worldgen.cave;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Every noise the cave network uses, seeded from the world seed. Shape-level noises (tunnel wander, cave size,
 * branching) are sampled once per layout; wall noises per block near a cave surface; decoration noises per candidate.
 * <ul>
 *     <li>shape: large wall undulation, lobes and bulges (cave shape noise)</li>
 *     <li>detail + erosion: jagged fine detail, smoothed away where erosion is high (erosion noise)</li>
 *     <li>shelf: phase of the horizontal rock shelves along chamber walls (vertical noise)</li>
 *     <li>path: tunnel wander, radius changes and branch spacing (tunnel / size / branch noise)</li>
 *     <li>strata: warps the boundaries between rock layers</li>
 *     <li>zone, cluster, species: vegetation zones (sparse to very dense), patches, and one species per patch</li>
 * </ul>
 */
public final class CaveNoises
{
    private final SimplexNoise shape, detail, erosion, shelf, path, strata, zone, cluster, species;
    private final long seed;

    public CaveNoises(long seed)
    {
        this.seed = seed;
        this.shape = noise(seed, 1);
        this.detail = noise(seed, 2);
        this.erosion = noise(seed, 3);
        this.shelf = noise(seed, 4);
        this.path = noise(seed, 5);
        this.strata = noise(seed, 6);
        this.zone = noise(seed, 7);
        this.cluster = noise(seed, 8);
        this.species = noise(seed, 9);
    }

    private static SimplexNoise noise(long seed, int salt)
    {
        return new SimplexNoise(new XoroshiroRandomSource(RandomSupport.mixStafford13(seed ^ (0x5CA7EL * salt + 0x9E3779B97F4A7C15L * salt))));
    }

    /** Large wall undulation, -1..1. Flatter vertically, so walls bulge and recede in horizontal bands. */
    public double shape(double x, double y, double z)
    {
        return shape.getValue(x * 0.045, y * 0.07, z * 0.045);
    }

    /** Fine wall detail already scaled by local roughness (0 where erosion has polished the rock). */
    public double detail(double x, double y, double z)
    {
        double rough = Mth.clamp(0.55 + 0.75 * erosion.getValue(x * 0.013, y * 0.018, z * 0.013), 0.08, 1.0);
        return (detail.getValue(x * 0.15, y * 0.19, z * 0.15) + 0.45 * detail.getValue(x * 0.33 + 31.7, y * 0.38, z * 0.33)) / 1.45 * rough;
    }

    /** Rolling cavern floor, -1..1: broad swells with smaller hummocks on them. */
    public double floorRelief(double x, double z)
    {
        return zone.getValue(x * 0.035 + 311.0, z * 0.035) * 0.65 + zone.getValue(x * 0.11 - 71.0, z * 0.11) * 0.35;
    }

    /** A smooth 2D field, -1..1, independent per salt. */
    public double field2(double x, double z, double salt)
    {
        return strata.getValue(x + salt * 97.31, z - salt * 41.97);
    }

    /**
     * Wall displacement of a space at a block centre, in blocks: large undulation, fine detail (smoothed where eroded),
     * rock shelves, and a cavern's grooves. {@link CaveChunk} computes the same from cached noise values.
     */
    public double displacement(CaveSpace space, double x, double y, double z)
    {
        double d = space.shapeAmp * shape(x, y, z);
        double detail = space.detailAmp * (1 - space.smoothness);
        if (detail > 0.01) d += detail * detail(x, y, z);
        if (space.shelfAmp > 0) d += space.shelfAmp * shelf(y, shelfPhase(x, z), space.shelfPeriod);
        if (space.cavern != null) d += space.cavern.wallRelief(x, y, z);
        return d;
    }

    /** Vertical phase offset for rock shelves in this column. */
    public double shelfPhase(double x, double z)
    {
        return shelf.getValue(x * 0.03, z * 0.03) * 5.0;
    }

    /** Protrusion (0..1) of a rock shelf at this height for the given shelf spacing. */
    public static double shelf(double y, double phase, double period)
    {
        double t = Math.sin((y + phase) * (Math.PI * 2 / period));
        if (t <= 0.4) return 0;
        double k = (t - 0.4) / 0.6;
        return k * k;
    }

    /** Smooth 1D noise along a path parameter, -1..1; {@code salt} separates independent paths. */
    public double path(double t, double salt)
    {
        return path.getValue(t, salt * 7.31 + 0.5);
    }

    /** Offset (in blocks, about ±4) warping stratum boundaries so layers undulate. */
    public double strata(double x, double y, double z)
    {
        return strata.getValue(x * 0.02, y * 0.05, z * 0.02) * 4.0 + strata.getValue(x * 0.09, y * 0.2, z * 0.09);
    }

    /**
     * Vegetation zone multiplier: sparse (0.25), normal (1), dense (2) and very dense (3.2) regions of a few tens
     * of blocks each, with smooth transitions.
     */
    public double vegetationZone(double x, double y, double z)
    {
        double v = zone.getValue(x * 0.025, y * 0.035, z * 0.025);
        if (v < -0.35) return 0.25;
        if (v < 0.05) return Mth.lerp((v + 0.35) / 0.4, 0.25, 1.0);
        if (v < 0.4) return Mth.lerp((v - 0.05) / 0.35, 1.0, 2.0);
        return Mth.lerp(Math.min(1.0, (v - 0.4) / 0.3), 2.0, 3.2);
    }

    /** Patch strength 0..1: plants and crystals grow in clumps around patch centres rather than evenly. */
    public double patch(double x, double y, double z, double salt)
    {
        double v = cluster.getValue(x * 0.17 + salt, y * 0.2, z * 0.17 - salt);
        return v <= -0.15 ? 0 : Math.min(1.0, (v + 0.15) / 0.75);
    }

    /**
     * 0..1 mottling for choosing among a palette's rocks: blotches a few blocks across with a little per-block
     * jitter, so walls read as patches of rock rather than salt-and-pepper noise.
     */
    public double mottle(int x, int y, int z, int salt)
    {
        double v = cluster.getValue(x * 0.11 + salt * 13.1, y * 0.14, z * 0.11 - salt * 7.7) * 0.55 + 0.5 + (hash(x, y, z, salt) - 0.5) * 0.3;
        return Mth.clamp(v, 0.0, 0.999);
    }

    /** 0..1, slowly varying: which species a patch is made of. */
    public double species(double x, double y, double z)
    {
        return Mth.clamp(species.getValue(x * 0.07, y * 0.09, z * 0.07) * 0.6 + 0.5, 0.0, 0.999);
    }

    /** Deterministic 0..1 value per block and purpose. */
    public double hash(int x, int y, int z, int salt)
    {
        long h = RandomSupport.mixStafford13(seed ^ x * 0x2F0F3D5L ^ z * 0x6C8E9CF5L ^ (long) y * 0x5DEECE66DL ^ salt * 0x9E3779B97F4A7C15L);
        return (h >>> 11) * 0x1.0p-53;
    }
}
