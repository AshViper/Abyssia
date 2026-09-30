package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.SeabedStructure;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Volcanic cones, from a small vent cone to a submerged volcano: concave flanks scored by radial gullies and dark
 * lava channels, a summit crater (deep enough, it digs below the seabed), a molten or glassy crater floor with an
 * optional vent core, parasitic cones on the flanks, and an ash apron re-coating the seabed around the base.
 */
public record VolcanoFormation(Span radius, Span height, Span crater, Span craterDepth, float gullies, Span cones, Mix rock, Mix rim,
                               Mix craterFloor, Mix core, Mix ash, float ashRadius, float lava, Mix lavaMix) implements Formation
{
    public static final String TYPE = "volcano";
    public static final Codec<VolcanoFormation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.fieldOf("radius").forGetter(VolcanoFormation::radius),
            Span.CODEC.fieldOf("height").forGetter(VolcanoFormation::height),
            Span.CODEC.optionalFieldOf("crater", Span.of(0.2f, 0.3f)).forGetter(VolcanoFormation::crater),
            Span.CODEC.optionalFieldOf("crater_depth", Span.of(0.2f, 0.35f)).forGetter(VolcanoFormation::craterDepth),
            Codec.floatRange(0, 1).optionalFieldOf("gullies", 0.4f).forGetter(VolcanoFormation::gullies),
            Span.CODEC.optionalFieldOf("cones", Span.of(0, 0)).forGetter(VolcanoFormation::cones),
            Mix.CODEC.fieldOf("rock").forGetter(VolcanoFormation::rock),
            Mix.CODEC.fieldOf("rim").forGetter(VolcanoFormation::rim),
            Mix.CODEC.fieldOf("crater_floor").forGetter(VolcanoFormation::craterFloor),
            Mix.CODEC.optionalFieldOf("core", Mix.EMPTY).forGetter(VolcanoFormation::core),
            Mix.CODEC.optionalFieldOf("ash", Mix.EMPTY).forGetter(VolcanoFormation::ash),
            Codec.floatRange(1, 4).optionalFieldOf("ash_radius", 1.5f).forGetter(VolcanoFormation::ashRadius),
            Codec.floatRange(0, 1).optionalFieldOf("lava", 0.3f).forGetter(VolcanoFormation::lava),
            Mix.CODEC.optionalFieldOf("lava_mix", Mix.EMPTY).forGetter(VolcanoFormation::lavaMix)
    ).apply(i, VolcanoFormation::new));

    private record Cone(double x, double z, double r, double h, double crater) {}

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public int carveDepth(SeabedStructure definition)
    {
        // The crater floor sits (rim height - crater depth) above the base: it only digs when the crater is deeper
        // than the lowest possible rim is high. Worst case over the parameter ranges.
        double lowestRim = height.min() * Math.pow(1 - crater.max(), 1.5);
        return Math.max(0, Mth.ceil(height.max() * craterDepth.max() - lowestRim));
    }

    /** Only the summit crater digs. */
    @Override
    public int carveReach(SeabedStructure definition)
    {
        return Mth.ceil(radius.max() * crater.max()) + 2;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        RandomSource random = site.random();
        double R = radius.sample(random);
        int base = site.baseY;
        double H = site.clampHeight(base, Math.round(height.sample(random)));
        double rc = R * crater.sample(random);
        double depth = H * craterDepth.sample(random);
        double gullyPhase = random.nextDouble() * 100;
        int n = cones.sampleInt(random);
        Cone[] parasites = new Cone[Math.max(0, n)];
        for (int i = 0; i < parasites.length; i++)
        {
            double a = random.nextDouble() * Math.PI * 2, d = R * (0.45 + 0.45 * random.nextDouble());
            double r = R * (0.12 + 0.1 * random.nextDouble());
            parasites[i] = new Cone(site.x + 0.5 + Math.cos(a) * d, site.z + 0.5 + Math.sin(a) * d, r, r * (0.6 + 0.4 * random.nextDouble()), r * 0.3);
        }
        if (H < 2) return;
        double rimH = H * Math.pow(1 - rc / R, 1.5);
        double ashR = ash.isEmpty() ? R : R * ashRadius;
        double reach = Math.max(R, ashR) + 2;
        for (int x = p.fromX(site.x, reach); x <= p.toX(site.x, reach); x++)
        {
            for (int z = p.fromZ(site.z, reach); z <= p.toZ(site.z, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                double dx = x + 0.5 - site.x - 0.5, dz = z + 0.5 - site.z - 0.5;
                double d = Math.sqrt(dx * dx + dz * dz);
                double ang = Math.atan2(dz, dx);
                double surface = Double.NEGATIVE_INFINITY;
                boolean inCrater = false;
                if (d < R)
                {
                    double q = d / R;
                    if (d < rc)
                    {
                        double floorH = rimH - depth;
                        surface = floorH + (rimH - floorH) * Math.pow(d / rc, 3);
                        inCrater = true;
                    }
                    else
                    {
                        surface = H * Math.pow(1 - q, 1.5);
                        // Radial gullies: ridges and valleys running down the flanks, fading toward the rim.
                        surface -= gullies * H * 0.12 * q * Math.abs(p.noise(Math.cos(ang) * 2.2 + gullyPhase, Math.sin(ang) * 2.2));
                        surface += p.noise(x * 0.08, z * 0.08) * 1.2;
                    }
                }
                for (Cone c : parasites)
                {
                    double cd = Math.hypot(x + 0.5 - c.x, z + 0.5 - c.z);
                    if (cd >= c.r) continue;
                    double mainAt = H * Math.pow(1 - Math.min(1, Math.hypot(c.x - site.x - 0.5, c.z - site.z - 0.5) / R), 1.5);
                    double s = mainAt + c.h * Math.pow(1 - cd / c.r, 1.3) - (cd < c.crater ? c.h * 0.4 * (1 - cd / c.crater) : 0);
                    if (s > surface)
                    {
                        surface = s;
                        inCrater = cd < c.crater;
                    }
                }
                int floor = p.floor(x, z);
                if (surface == Double.NEGATIVE_INFINITY)
                {
                    apron(p, x, z, floor, d, R, ashR);
                    continue;
                }
                // The cone rises from the centre's seabed but settles onto the local seabed toward its foot, so a
                // volcano on a slope neither digs into the upslope nor stands on a cliff downslope.
                double q = Math.min(1, d / R), blend = q * q * (3 - 2 * q);
                double ref = base + (p.smoothBase(x, z) - base) * blend;
                int top = (int) Math.round(ref + surface * p.edge(x, z, 10));
                boolean channel = !inCrater && lava > 0 && !lavaMix.isEmpty() && d > rc * 1.2
                        && Math.abs(p.noise(Math.cos(ang) * 3.5 + gullyPhase * 2, Math.sin(ang) * 3.5 + d * 0.02)) < lava * 0.12;
                if (top >= floor)
                {
                    for (int y = floor - 1; y < top; y++)
                    {
                        boolean skin = y >= top - 2;
                        BlockState state = !skin ? p.pick(rock, x, y, z, 31)
                                : inCrater && y == top - 1 ? p.pick(craterFloor, x, y, z, 32)
                                : d < rc * 1.25 ? p.pick(rim, x, y, z, 33)
                                : channel && y == top - 1 ? p.pick(lavaMix, x, y, z, 34)
                                : p.pick(rock, x, y, z, 31);
                        if (y == floor - 1) p.recoat(x, y, z, state);
                        else p.place(x, y, z, state);
                    }
                }
                else if (inCrater)
                {
                    // A crater dug below the seabed.
                    for (int y = floor - 1; y >= top; y--) if (!p.carve(x, y, z)) break;
                    p.recoat(x, top - 1, z, p.pick(craterFloor, x, top - 1, z, 32));
                }
                if (d < R && top >= floor) p.root(x, floor - 2, z, p.pick(rock, x, floor, z, 31), 8);
            }
        }
        if (!core.isEmpty())
        {
            int top = base + (int) Math.round(rimH - depth);
            if (p.inReach(site.x, site.z)) p.set(site.x, top, site.z, p.pick(core, site.x, top, site.z, 35));
        }
    }

    private void apron(Painter p, int x, int z, int floor, double d, double R, double ashR)
    {
        if (ash.isEmpty() || d >= ashR) return;
        double t = (d - R) / Math.max(1, ashR - R);
        if (p.hash(x, 0, z, 36) > 1 - t * t) p.recoat(x, floor - 1, z, p.pick(ash, x, floor - 1, z, 37));
        if (p.hash(x, 1, z, 38) < 0.35 * (1 - t)) p.fill(x, floor, z, p.pick(ash, x, floor, z, 37));
    }
}
