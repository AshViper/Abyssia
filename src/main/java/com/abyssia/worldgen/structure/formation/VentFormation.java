package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A colony of hydrothermal chimneys: small, medium and large chimneys mixed (the largest in the middle), each a
 * lumpy tapering stack with sulfide crusts and beehive flanges, crowned by a vent core, standing on overlapping
 * mineral mounds whose deposits re-coat the seabed around them. Smaller than a full vent field
 * ({@link com.abyssia.thermal.ThermalVentField}), and kept out of those.
 */
public record VentFormation(Span count, float spread, Span height, Span width, float lean, Mix chimney, Mix accent, float accentChance,
                            Mix core, Mix mound, float moundRadius, float moundHeight, float flanges) implements Formation
{
    public static final String TYPE = "vent";
    public static final MapCodec<VentFormation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Span.CODEC.fieldOf("count").forGetter(VentFormation::count),
            Codec.floatRange(0, 100).lenientOptionalFieldOf("spread", 8f).forGetter(VentFormation::spread),
            Span.CODEC.fieldOf("height").forGetter(VentFormation::height),
            Span.CODEC.fieldOf("width").forGetter(VentFormation::width),
            Codec.floatRange(0, 0.5f).lenientOptionalFieldOf("lean", 0.1f).forGetter(VentFormation::lean),
            Mix.CODEC.fieldOf("chimney").forGetter(VentFormation::chimney),
            Mix.CODEC.lenientOptionalFieldOf("accent", Mix.EMPTY).forGetter(VentFormation::accent),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("accent_chance", 0f).forGetter(VentFormation::accentChance),
            Mix.CODEC.lenientOptionalFieldOf("core", Mix.EMPTY).forGetter(VentFormation::core),
            Mix.CODEC.fieldOf("mound").forGetter(VentFormation::mound),
            Codec.floatRange(0, 60).lenientOptionalFieldOf("mound_radius", 5f).forGetter(VentFormation::moundRadius),
            Codec.floatRange(0, 20).lenientOptionalFieldOf("mound_height", 2f).forGetter(VentFormation::moundHeight),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("flanges", 0.4f).forGetter(VentFormation::flanges)
    ).apply(i, VentFormation::new));

    private record Chimney(double x, double z, int height, float width, double lx, double lz, float scale, int[] flangeAt, boolean cored) {}

    @Override
    public String type()
    {
        return TYPE;
    }

    private Chimney[] layout(Site site)
    {
        RandomSource random = site.random();
        int n = Math.max(1, count.sampleInt(random));
        Chimney[] out = new Chimney[n];
        for (int i = 0; i < n; i++)
        {
            double x = site.x + 0.5, z = site.z + 0.5;
            // Small / medium / large vents: the main one is always large.
            float roll = random.nextFloat();
            float scale = i == 0 ? 1f : roll < 0.5f ? 0.35f : roll < 0.85f ? 0.65f : 1f;
            if (i > 0)
            {
                double a = random.nextDouble() * Math.PI * 2, d = spread * Math.sqrt(0.1 + 0.9 * random.nextDouble());
                x += Math.cos(a) * d;
                z += Math.sin(a) * d;
            }
            int h = Math.max(2, Math.round(height.sample(random) * scale));
            float w = Math.max(0.8f, width.sample(random) * (0.5f + 0.5f * scale));
            double lx = (random.nextDouble() - 0.5) * 2 * lean, lz = (random.nextDouble() - 0.5) * 2 * lean;
            int[] flangeAt = new int[random.nextFloat() < flanges ? 1 + random.nextInt(3) : 0];
            for (int k = 0; k < flangeAt.length; k++) flangeAt[k] = Math.max(1, Math.round(h * (0.25f + 0.6f * random.nextFloat())));
            boolean cored = random.nextFloat() < 0.4f + 0.6f * scale;
            out[i] = new Chimney(x, z, h, w, lx, lz, scale, flangeAt, cored);
        }
        return out;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        Chimney[] chimneys = layout(site);
        mounds(site, p, chimneys);
        for (int i = 0; i < chimneys.length; i++) chimney(site, p, i, chimneys[i]);
    }

    private double moundR(Chimney c)
    {
        return moundRadius * (0.5 + 0.5 * c.scale) + c.width * 2;
    }

    private void mounds(Site site, Painter p, Chimney[] chimneys)
    {
        double reach = spread + moundRadius + 6;
        for (int x = p.fromX(site.x, reach); x <= p.toX(site.x, reach); x++)
        {
            for (int z = p.fromZ(site.z, reach); z <= p.toZ(site.z, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                double m = 0, zone = 0;
                for (Chimney c : chimneys)
                {
                    double r = moundR(c), d = Math.hypot(x + 0.5 - c.x, z + 0.5 - c.z);
                    m = Math.max(m, moundHeight * (0.5 + 0.5 * c.scale) * Math.exp(-2 * Mth.square(d / r)));
                    zone = Math.max(zone, 1 - d / (r * 1.4));
                }
                if (zone <= 0) continue;
                int floor = p.floor(x, z);
                // Deposits re-coat the seabed, patchily toward the edge of the zone.
                if (p.hash(x, 0, z, 21) < zone * 1.3) p.recoat(x, floor - 1, z, p.pick(mound, x, floor - 1, z, 22));
                int h = (int) Math.round(m + p.noise(x * 0.25, z * 0.25) * 0.6);
                for (int k = 0; k < h; k++) p.fill(x, floor + k, z, p.pick(mound, x, floor + k, z, 22));
            }
        }
    }

    private void chimney(Site site, Painter p, int index, Chimney c)
    {
        int base = site.baseAt(c.x, c.z) - 1;
        int h = site.clampHeight(base, c.height + Math.round(moundHeight * c.scale));
        if (h < 2) return;
        double reach = c.width * 1.6 + 4 + Math.max(Math.abs(c.lx), Math.abs(c.lz)) * h;
        for (int x = p.fromX(c.x, reach); x <= p.toX(c.x, reach); x++)
        {
            for (int z = p.fromZ(c.z, reach); z <= p.toZ(c.z, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                boolean rooted = false;
                for (int y = base - 2; y <= base + h; y++)
                {
                    int hy = Math.max(0, y - base);
                    double t = hy / (double) h;
                    double cx = c.x + c.lx * hy, cz = c.z + c.lz * hy;
                    double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                    // Lumpy stack: wide at the foot, bulging where minerals piled up, narrowing to the orifice.
                    double r = c.width * (1.25 - 0.6 * t) * (1 + 0.3 * p.noise(index * 11 + y * 0.35, x * 0.3, z * 0.3));
                    for (int f : c.flangeAt) if (hy == f) r += 1.5 + c.width * 0.6;
                    // The flue column is always solid, so thin tops never break into floating bits.
                    if (d > Math.max(r, 0.75)) continue;
                    if (y == base - 2) rooted = true;
                    boolean crust = accentChance > 0 && !accent.isEmpty() && p.noise(x * 0.4, y * 0.3, z * 0.4) > 1 - accentChance * 2;
                    p.place(x, y, z, crust ? p.pick(accent, x, y, z, 23) : p.pick(chimney, x, y, z, 24));
                }
                if (rooted) p.root(x, base - 3, z, p.pick(chimney, x, base, z, 24), 16);
            }
        }
        if (c.cored && !core.isEmpty())
        {
            int top = base + h;
            int x = Mth.floor(c.x + c.lx * h), z = Mth.floor(c.z + c.lz * h);
            if (p.inReach(x, z))
            {
                p.place(x, top - 1, z, p.pick(chimney, x, top - 1, z, 24));
                p.set(x, top, z, p.pick(core, x, top, z, 25));
            }
        }
    }
}
