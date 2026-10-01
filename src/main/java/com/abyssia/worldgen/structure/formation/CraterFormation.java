package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.SeabedStructure;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

/**
 * A bowl sunk into the seabed with a raised rim: a pockmark in the mud, a volcanic crater, or a giant hadal crater
 * with a central uplift. The bowl is dug into water and re-floored, the rim heaped up, and ejecta scattered around.
 * The rim wanders with noise so it is never a perfect circle.
 */
public record CraterFormation(Span radius, Span depth, Span rimHeight, float rimWidth, float peak, float roughness, Mix floor, Mix rim,
                              Mix ejecta, float ejectaDensity) implements Formation
{
    public static final String TYPE = "crater";
    public static final MapCodec<CraterFormation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Span.CODEC.fieldOf("radius").forGetter(CraterFormation::radius),
            Span.CODEC.fieldOf("depth").forGetter(CraterFormation::depth),
            Span.CODEC.lenientOptionalFieldOf("rim_height", Span.of(1, 3)).forGetter(CraterFormation::rimHeight),
            Codec.floatRange(0.05f, 2f).lenientOptionalFieldOf("rim_width", 0.35f).forGetter(CraterFormation::rimWidth),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("peak", 0f).forGetter(CraterFormation::peak),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("roughness", 0.3f).forGetter(CraterFormation::roughness),
            Mix.CODEC.fieldOf("floor").forGetter(CraterFormation::floor),
            Mix.CODEC.fieldOf("rim").forGetter(CraterFormation::rim),
            Mix.CODEC.lenientOptionalFieldOf("ejecta", Mix.EMPTY).forGetter(CraterFormation::ejecta),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("ejecta_density", 0.05f).forGetter(CraterFormation::ejectaDensity)
    ).apply(i, CraterFormation::new));

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public int carveDepth(SeabedStructure definition)
    {
        return (int) Math.ceil(depth.max()) + 2;
    }

    /** The bowl digs; the rim only heaps up. */
    @Override
    public int carveReach(SeabedStructure definition)
    {
        return (int) Math.ceil(radius.max() * (1 + 0.25 * roughness) / 0.85) + 2;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        RandomSource random = site.random();
        double R = radius.sample(random), D = depth.sample(random), rimH = rimHeight.sample(random);
        double squash = 0.85 + 0.3 * random.nextDouble(), theta = random.nextDouble() * Math.PI;
        int base = site.baseY;
        double outer = R * (1 + rimWidth);
        double reach = outer * 1.6 + 2;
        double cos = Math.cos(theta), sin = Math.sin(theta);
        for (int x = p.fromX(site.x, reach); x <= p.toX(site.x, reach); x++)
        {
            for (int z = p.fromZ(site.z, reach); z <= p.toZ(site.z, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                double dx = x - site.x, dz = z - site.z;
                double u = dx * cos + dz * sin, v = (-dx * sin + dz * cos) * squash;
                double ang = Math.atan2(v, u);
                // Wandering rim: the radius varies with direction.
                double rr = R * (1 + roughness * 0.25 * p.noise(Math.cos(ang) * 1.5, Math.sin(ang) * 1.5));
                double q = Math.sqrt(u * u + v * v) / rr;
                int floorY = p.floor(x, z);
                double s;
                if (q < 1)
                {
                    s = -D * (1 - q * q) + rimH * Math.pow(q, 8);
                    if (peak > 0 && q < 0.25) s += peak * D * (1 - q / 0.25) * (1 - q / 0.25);
                }
                else if (q < 1 + rimWidth)
                {
                    double t = (q - 1) / rimWidth;
                    s = rimH * (1 - t) * (1 - t);
                }
                else
                {
                    if (!ejecta.isEmpty() && q < (1 + rimWidth) * 1.6 && p.hash(x, 0, z, 41) < ejectaDensity * (1.6 * (1 + rimWidth) - q))
                    {
                        p.fill(x, floorY, z, p.pick(ejecta, x, floorY, z, 42));
                    }
                    continue;
                }
                s += roughness * 1.5 * p.noise(x * 0.12, z * 0.12);
                // Relative to the centre inside the bowl, easing onto the local seabed across the rim (slopes).
                double qe = Math.min(1, q / (1 + rimWidth)), blend = qe * qe * (3 - 2 * qe);
                double ref = base + (p.smoothBase(x, z) - base) * blend;
                int target = (int) Math.round(ref + s * (q < 1 ? 1 : p.edge(x, z, 8)));
                if (target < floorY)
                {
                    int y = floorY - 1;
                    for (; y >= target; y--) if (!p.carve(x, y, z)) break;
                    if (y < target)
                    {
                        p.recoat(x, target - 1, z, p.pick(q < 1 ? floor : rim, x, target - 1, z, 43));
                        if (q < 0.8) p.recoat(x, target - 2, z, p.pick(floor, x, target - 2, z, 43));
                    }
                }
                else
                {
                    p.recoat(x, floorY - 1, z, p.pick(q < 1 ? floor : rim, x, floorY - 1, z, 43));
                    for (int y = floorY; y < target; y++) p.fill(x, y, z, p.pick(q < 1 ? floor : rim, x, y, z, 44));
                }
            }
        }
    }
}
