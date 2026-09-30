package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Boulders, rock masses, outcrops, ridges and rubble fields: a group of noise-deformed, elongated and tilted
 * ellipsoids, partly sunk into the seabed and rooted under their overhangs. {@code layers} stripes them into strata
 * (ancient outcrops, cliff faces); many small blobs over a wide spread make a collapse debris field.
 */
public record RockFormation(Span count, float spread, Span size, float heightRatio, Span elongation, float sink, float tilt, float erosion,
                            int layers, Mix rock, Mix accent, float accentChance) implements Formation
{
    public static final String TYPE = "rock";
    public static final Codec<RockFormation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.optionalFieldOf("count", Span.of(1, 1)).forGetter(RockFormation::count),
            Codec.floatRange(0, 120).optionalFieldOf("spread", 0f).forGetter(RockFormation::spread),
            Span.CODEC.fieldOf("size").forGetter(RockFormation::size),
            Codec.floatRange(0.1f, 4f).optionalFieldOf("height_ratio", 0.8f).forGetter(RockFormation::heightRatio),
            Span.CODEC.optionalFieldOf("elongation", Span.of(1, 1.4f)).forGetter(RockFormation::elongation),
            Codec.floatRange(0, 1).optionalFieldOf("sink", 0.35f).forGetter(RockFormation::sink),
            Codec.floatRange(0, 1.2f).optionalFieldOf("tilt", 0.2f).forGetter(RockFormation::tilt),
            Codec.floatRange(0, 0.8f).optionalFieldOf("erosion", 0.3f).forGetter(RockFormation::erosion),
            Codec.intRange(0, 16).optionalFieldOf("layers", 0).forGetter(RockFormation::layers),
            Mix.CODEC.fieldOf("rock").forGetter(RockFormation::rock),
            Mix.CODEC.optionalFieldOf("accent", Mix.EMPTY).forGetter(RockFormation::accent),
            Codec.floatRange(0, 1).optionalFieldOf("accent_chance", 0f).forGetter(RockFormation::accentChance)
    ).apply(i, RockFormation::new));

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        RandomSource random = site.random();
        int n = Math.max(1, count.sampleInt(random));
        for (int i = 0; i < n; i++)
        {
            double bx = site.x + 0.5, bz = site.z + 0.5;
            float scale = 1f;
            if (i > 0)
            {
                double a = random.nextDouble() * Math.PI * 2, d = spread * Math.sqrt(0.1 + 0.9 * random.nextDouble());
                bx += Math.cos(a) * d;
                bz += Math.sin(a) * d;
                scale = 0.4f + 0.6f * random.nextFloat();
            }
            double a = size.sample(random) * scale;
            double el = elongation.sample(random);
            double b = a * heightRatio * (0.8 + 0.4 * random.nextDouble());
            double theta = random.nextDouble() * Math.PI;
            double tx = (random.nextDouble() - 0.5) * 2 * tilt, tz = (random.nextDouble() - 0.5) * 2 * tilt;
            blob(site, p, i, bx, bz, a, el, b, theta, tx, tz);
        }
    }

    private void blob(Site site, Painter p, int index, double bx, double bz, double a, double el, double b, double theta, double tx, double tz)
    {
        int base = site.baseAt(bx, bz);
        double cy = base - 1 + b * (1 - 2 * sink);
        double cos = Math.cos(theta), sin = Math.sin(theta);
        double reach = a * el * (1 + erosion) + 2;
        double shear = (Math.abs(tx) + Math.abs(tz)) * reach;
        int y0 = Mth.floor(cy - b * (1 + erosion) - 2 - shear), y1 = Mth.ceil(cy + b * (1 + erosion) + 2 + shear);
        for (int x = p.fromX(bx, reach); x <= p.toX(bx, reach); x++)
        {
            for (int z = p.fromZ(bz, reach); z <= p.toZ(bz, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                double dx = x + 0.5 - bx, dz = z + 0.5 - bz;
                double u = (dx * cos + dz * sin) / (a * el), v = (-dx * sin + dz * cos) / a;
                double horizontal = u * u + v * v;
                if (horizontal > Mth.square(1 + erosion)) continue;
                int lowest = Integer.MAX_VALUE;
                for (int y = y0; y <= y1; y++)
                {
                    // Tilt shears the blob so its top leans one way.
                    double w = (y + 0.5 - cy - tx * dx - tz * dz) / b;
                    double q = horizontal + w * w;
                    if (q > 1 + erosion * p.noise(x * 0.15 + index * 5, y * 0.15, z * 0.15)) continue;
                    p.place(x, y, z, material(p, x, y, z));
                    lowest = Math.min(lowest, y);
                }
                if (lowest != Integer.MAX_VALUE) p.root(x, lowest - 1, z, p.pick(rock, x, lowest, z, 1), 12);
            }
        }
    }

    private BlockState material(Painter p, int x, int y, int z)
    {
        if (layers > 0 && !accent.isEmpty())
        {
            // Strata: bands of the two rocks, wavering a little so the lines are not ruler-straight.
            int band = Math.floorDiv(y + (int) Math.round(p.noise(x * 0.05, z * 0.05) * 2), layers);
            return (band & 1) == 0 ? p.pick(rock, x, y, z, 1) : p.pick(accent, x, y, z, 2);
        }
        if (accentChance > 0 && !accent.isEmpty() && p.noise(x * 0.3, y * 0.3, z * 0.3) > 1 - accentChance * 2) return p.pick(accent, x, y, z, 2);
        return p.pick(rock, x, y, z, 1);
    }
}
