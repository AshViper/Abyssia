package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.cave.CaveEnvironment;
import com.abyssia.worldgen.cave.Palette;
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
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.SimpleWeightedRandomList;

import java.util.Locale;

/**
 * Plant communities built as one structure rather than scattered singles: a {@code grove} (giant kelp forest with
 * glades), a {@code wall} (a dense band), a {@code hill} (a sediment mound overgrown to the top) or a {@code tunnel}
 * (two tall walls along a winding corridor). Canopy heights follow smooth noise so stands grow in even tiers, and
 * the soil under them turns organic. The plants are painted in the dressing pass, after ores and vents.
 */
public record VegetationFormation(Shape shape, Span radius, Span width, float density, float clearings,
                                  SimpleWeightedRandomList<CaveEnvironment.PlantEntry> plants,
                                  SimpleWeightedRandomList<CaveEnvironment.PlantEntry> understory, float understoryDensity,
                                  Mix mound, Span moundHeight, Mix soil, float soilChance) implements Formation
{
    private static final Codec<SimpleWeightedRandomList<CaveEnvironment.PlantEntry>> PLANTS =
            SimpleWeightedRandomList.wrappedCodecAllowingEmpty(CaveEnvironment.PlantEntry.CODEC);
    public static final String TYPE = "vegetation";
    public static final MapCodec<VegetationFormation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Shape.CODEC.lenientOptionalFieldOf("shape", Shape.GROVE).forGetter(VegetationFormation::shape),
            Span.CODEC.fieldOf("radius").forGetter(VegetationFormation::radius),
            Span.CODEC.lenientOptionalFieldOf("width", Span.of(6, 10)).forGetter(VegetationFormation::width),
            Codec.floatRange(0, 1).fieldOf("density").forGetter(VegetationFormation::density),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("clearings", 0.2f).forGetter(VegetationFormation::clearings),
            PLANTS.fieldOf("plants").forGetter(VegetationFormation::plants),
            PLANTS.lenientOptionalFieldOf("understory", SimpleWeightedRandomList.empty()).forGetter(VegetationFormation::understory),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("understory_density", 0f).forGetter(VegetationFormation::understoryDensity),
            Mix.CODEC.lenientOptionalFieldOf("mound", Mix.EMPTY).forGetter(VegetationFormation::mound),
            Span.CODEC.lenientOptionalFieldOf("mound_height", Span.of(0, 0)).forGetter(VegetationFormation::moundHeight),
            Mix.CODEC.lenientOptionalFieldOf("soil", Mix.EMPTY).forGetter(VegetationFormation::soil),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("soil_chance", 0f).forGetter(VegetationFormation::soilChance)
    ).apply(i, VegetationFormation::new));

    public enum Shape implements StringRepresentable
    {
        GROVE, WALL, HILL, TUNNEL;

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private record Layout(double r, double w, double cos, double sin, double moundH, double curve) {}

    @Override
    public String type()
    {
        return TYPE;
    }

    private Layout layout(Site site)
    {
        RandomSource random = site.random();
        double theta = random.nextDouble() * Math.PI;
        return new Layout(radius.sample(random), width.sample(random), Math.cos(theta), Math.sin(theta), moundHeight.sample(random),
                0.5 + random.nextDouble());
    }

    /** Planting density at a column (0 = none); for a tunnel the corridor is kept open. */
    private double mask(Site site, Painter p, Layout l, int x, int z)
    {
        double dx = x + 0.5 - site.x, dz = z + 0.5 - site.z;
        double edge = 0.2 * p.noise(x * 0.05, z * 0.05);
        return switch (shape)
        {
            case GROVE, HILL -> {
                double q = Math.hypot(dx, dz) / l.r;
                yield q >= 1 + edge ? 0 : 1 - Math.pow(Math.min(1, q), 4);
            }
            case WALL -> {
                double u = dx * l.cos + dz * l.sin, v = -dx * l.sin + dz * l.cos;
                double e = Math.abs(u) / l.r;
                yield e >= 1 || Math.abs(v) > l.w / 2 * (1 + edge) * Math.sqrt(Math.max(0, 1 - e * e)) ? 0 : 1;
            }
            case TUNNEL -> {
                double u = dx * l.cos + dz * l.sin, v = -dx * l.sin + dz * l.cos;
                double e = Math.abs(u) / l.r;
                if (e >= 1) yield 0;
                v -= l.curve * l.w * p.noise(u * 0.03, 55);
                double corridor = l.w / 2, wall = corridor + 3 + 2 * (1 + edge);
                yield Math.abs(v) < corridor || Math.abs(v) > wall ? 0 : 1;
            }
        };
    }

    @Override
    public void paint(Site site, Painter p)
    {
        Layout l = layout(site);
        double reach = l.r + l.w + 6;
        for (int x = p.fromX(site.x, reach); x <= p.toX(site.x, reach); x++)
        {
            for (int z = p.fromZ(site.z, reach); z <= p.toZ(site.z, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                int floor = p.floor(x, z);
                if (shape == Shape.HILL && !mound.isEmpty())
                {
                    double q = Math.hypot(x + 0.5 - site.x, z + 0.5 - site.z) / l.r;
                    int h = (int) Math.round(l.moundH * Math.exp(-2.2 * q * q) * (0.9 + 0.1 * p.noise(x * 0.1, z * 0.1)) * p.edge(x, z, 8));
                    for (int k = 0; k < h; k++) p.fill(x, floor + k, z, p.pick(mound, x, floor + k, z, 71));
                    floor += Math.max(0, h);
                }
                if (!soil.isEmpty() && mask(site, p, l, x, z) > 0 && p.hash(x, 0, z, 72) < soilChance) p.recoat(x, floor - 1, z, p.pick(soil, x, floor - 1, z, 73));
            }
        }
    }

    @Override
    public void dress(Site site, Painter p)
    {
        Layout l = layout(site);
        Palette<CaveEnvironment.PlantEntry> canopy = Palette.of(plants), under = Palette.of(understory);
        double reach = l.r + l.w + 6;
        for (int x = p.fromX(site.x, reach); x <= p.toX(site.x, reach); x++)
        {
            for (int z = p.fromZ(site.z, reach); z <= p.toZ(site.z, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                double m = mask(site, p, l, x, z);
                if (m <= 0) continue;
                // Glades: open patches inside the stand.
                if (clearings > 0 && p.noise(x * 0.06 + 11, z * 0.06) < clearings * 2 - 1) m *= 0.15;
                int y = p.floor(x, z);
                double roll = p.hash(x, 0, z, 74);
                // Even tiers: heights follow smooth noise with a little jitter.
                double tier = Mth.clamp(0.5 + 0.5 * p.noise(x * 0.07, z * 0.07 + 70) + (p.hash(x, 1, z, 75) - 0.5) * 0.25, 0, 1);
                if (roll < density * m && !canopy.isEmpty()) p.plant(x, y, z, canopy.pick(p.hash(x, 2, z, 76)), tier);
                else if (roll < density * m + understoryDensity * m && !under.isEmpty()) p.plant(x, y, z, under.pick(p.hash(x, 3, z, 77)), p.hash(x, 4, z, 78));
            }
        }
    }
}
