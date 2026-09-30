package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

/**
 * Soft seabed built up over a long time: mud mounds, sediment heaps and broad seabed swells. Smooth, overlapping,
 * slightly elongated domes draped over the terrain (never rock-sharp), with a surface layer over a core, optional
 * stratified banding exposed on their flanks, and patches of organic deposit.
 */
public record SedimentFormation(Span count, float spread, Span radius, Span height, Mix surface, Mix core, int layers, Mix organic,
                                float organicChance) implements Formation
{
    public static final String TYPE = "sediment";
    public static final Codec<SedimentFormation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.optionalFieldOf("count", Span.of(1, 1)).forGetter(SedimentFormation::count),
            Codec.floatRange(0, 120).optionalFieldOf("spread", 0f).forGetter(SedimentFormation::spread),
            Span.CODEC.fieldOf("radius").forGetter(SedimentFormation::radius),
            Span.CODEC.fieldOf("height").forGetter(SedimentFormation::height),
            Mix.CODEC.fieldOf("surface").forGetter(SedimentFormation::surface),
            Mix.CODEC.fieldOf("core").forGetter(SedimentFormation::core),
            Codec.intRange(0, 16).optionalFieldOf("layers", 0).forGetter(SedimentFormation::layers),
            Mix.CODEC.optionalFieldOf("organic", Mix.EMPTY).forGetter(SedimentFormation::organic),
            Codec.floatRange(0, 1).optionalFieldOf("organic_chance", 0f).forGetter(SedimentFormation::organicChance)
    ).apply(i, SedimentFormation::new));

    private record Mound(double x, double z, double r, double h, double el, double cos, double sin) {}

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        RandomSource random = site.random();
        Mound[] mounds = new Mound[Math.max(1, count.sampleInt(random))];
        for (int i = 0; i < mounds.length; i++)
        {
            double x = site.x + 0.5, z = site.z + 0.5;
            double scale = 1;
            if (i > 0)
            {
                double a = random.nextDouble() * Math.PI * 2, d = spread * Math.sqrt(0.1 + 0.9 * random.nextDouble());
                x += Math.cos(a) * d;
                z += Math.sin(a) * d;
                scale = 0.45 + 0.55 * random.nextDouble();
            }
            double theta = random.nextDouble() * Math.PI;
            mounds[i] = new Mound(x, z, radius.sample(random) * scale, height.sample(random) * scale, 1 + random.nextDouble() * 0.6,
                    Math.cos(theta), Math.sin(theta));
        }
        int budget = site.topY - 1;
        double reach = spread + radius.max() * 1.8 + 2;
        for (int x = p.fromX(site.x, reach); x <= p.toX(site.x, reach); x++)
        {
            for (int z = p.fromZ(site.z, reach); z <= p.toZ(site.z, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                double m = 0;
                for (Mound mound : mounds)
                {
                    double dx = x + 0.5 - mound.x, dz = z + 0.5 - mound.z;
                    double u = (dx * mound.cos + dz * mound.sin) / (mound.r * mound.el), v = (-dx * mound.sin + dz * mound.cos) / mound.r;
                    // Soft-shouldered dome: gaussian, so neighbouring mounds merge into a smooth swell.
                    m = Math.max(m, mound.h * Math.exp(-2.2 * (u * u + v * v)));
                }
                m *= (0.88 + 0.12 * p.noise(x * 0.1, z * 0.1)) * p.edge(x, z, 10);
                int h = (int) Math.round(m);
                if (m < 0.15) continue;
                int floor = p.floor(x, z);
                boolean organicPatch = organicChance > 0 && !organic.isEmpty() && p.noise(x * 0.07 + 30, z * 0.07) > 1 - organicChance * 2;
                // Organic deposits also stain the flat seabed between the mounds.
                if (organicPatch && h == 0) p.recoat(x, floor - 1, z, p.pick(organic, x, floor - 1, z, 61));
                for (int k = 0; k < h && floor + k < budget; k++)
                {
                    int y = floor + k;
                    boolean top = k == h - 1;
                    var state = top ? (organicPatch ? p.pick(organic, x, y, z, 61) : p.pick(surface, x, y, z, 62))
                            : layers > 0 && Math.floorMod(y, layers * 2) < layers ? p.pick(surface, x, y, z, 62) : p.pick(core, x, y, z, 63);
                    p.fill(x, y, z, state);
                }
            }
        }
    }
}
