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
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Rock that fills a large cavern's volume so it never reads as an empty box: {@code column} (floor-to-roof columns,
 * waisted like merged stalactite and stalagmite, flaring into floor and roof), {@code hanging} (clusters of rock
 * pendants and drapery from the roof) and {@code stalagmites} (a cluster of cones rising from the floor). Heights
 * follow the cavern's own floor and roof at each part, so parts always meet the rock they grow from.
 */
public record CaveFormation(Shape shape, Span count, float spread, Span radius, Span length, float waist, float erosion, Mix rock, Mix accent,
                            float accentChance) implements Formation
{
    public static final String TYPE = "cave";
    public static final MapCodec<CaveFormation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Shape.CODEC.fieldOf("shape").forGetter(CaveFormation::shape),
            Span.CODEC.lenientOptionalFieldOf("count", Span.of(1, 1)).forGetter(CaveFormation::count),
            Codec.floatRange(0, 60).lenientOptionalFieldOf("spread", 0f).forGetter(CaveFormation::spread),
            Span.CODEC.fieldOf("radius").forGetter(CaveFormation::radius),
            Span.CODEC.lenientOptionalFieldOf("length", Span.of(0.3f, 0.6f)).forGetter(CaveFormation::length),
            Codec.floatRange(0, 0.8f).lenientOptionalFieldOf("waist", 0.4f).forGetter(CaveFormation::waist),
            Codec.floatRange(0, 0.8f).lenientOptionalFieldOf("erosion", 0.25f).forGetter(CaveFormation::erosion),
            Mix.CODEC.fieldOf("rock").forGetter(CaveFormation::rock),
            Mix.CODEC.lenientOptionalFieldOf("accent", Mix.EMPTY).forGetter(CaveFormation::accent),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("accent_chance", 0f).forGetter(CaveFormation::accentChance)
    ).apply(i, CaveFormation::new));

    public enum Shape implements StringRepresentable
    {
        COLUMN, HANGING, STALAGMITES;

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

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
            double ex = site.x + 0.5, ez = site.z + 0.5;
            float scale = 1f;
            if (i > 0)
            {
                double a = random.nextDouble() * Math.PI * 2, d = spread * Math.sqrt(0.15 + 0.85 * random.nextDouble());
                ex += Math.cos(a) * d;
                ez += Math.sin(a) * d;
                scale = 0.45f + 0.55f * random.nextFloat();
            }
            float r0 = Math.max(1f, radius.sample(random) * scale);
            float len = length.sample(random) * (0.6f + 0.4f * scale);
            double lx = (random.nextDouble() - 0.5) * 0.2, lz = (random.nextDouble() - 0.5) * 0.2;
            part(site, p, i, ex, ez, r0, len, lx, lz);
        }
    }

    private void part(Site site, Painter p, int index, double ex, double ez, float r0, float len, double lx, double lz)
    {
        int floor = site.baseAt(ex, ez) - 2;
        int roof = site.ceilingAt(ex, ez) + 2;
        int H = roof - floor;
        if (H < 6) return;
        int y0, y1;
        switch (shape)
        {
            case COLUMN -> { y0 = floor; y1 = roof; }
            case HANGING -> { y0 = roof - Math.max(3, Math.round(H * len)); y1 = roof; }
            default -> { y0 = floor; y1 = floor + Math.max(3, Math.round(H * len)); }
        }
        double rMax = r0 * 1.9 * (1 + erosion) + 0.5;
        double reach = rMax + 2 + Math.max(Math.abs(lx), Math.abs(lz)) * H;
        for (int x = p.fromX(ex, reach); x <= p.toX(ex, reach); x++)
        {
            for (int z = p.fromZ(ez, reach); z <= p.toZ(ez, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                for (int y = y0; y <= y1; y++)
                {
                    double t = (y - y0) / (double) Math.max(1, y1 - y0);
                    double rad = switch (shape)
                    {
                        // Hourglass, flaring where it meets floor and roof.
                        case COLUMN -> r0 * (1 - waist * Math.sin(Math.PI * t)) * (1 + 0.9 * Mth.square(Math.max(0, 0.15 - t) / 0.15)
                                + 0.9 * Mth.square(Math.max(0, t - 0.85) / 0.15));
                        // Pendant: thick at the roof, a point at the tip.
                        case HANGING -> r0 * Math.pow(t, 0.9);
                        case STALAGMITES -> r0 * Math.pow(1 - t, 0.9);
                    };
                    double cx = ex + lx * (y - y0), cz = ez + lz * (y - y0);
                    double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                    if (dx * dx + dz * dz > rMax * rMax) continue;
                    double ang = Math.atan2(dz, dx);
                    rad *= 1 + erosion * p.noise(Math.cos(ang) * 1.2 + index * 9, y * 0.1, Math.sin(ang) * 1.2);
                    if (dx * dx + dz * dz > rad * rad) continue;
                    p.place(x, y, z, material(p, x, y, z));
                }
            }
        }
    }

    private BlockState material(Painter p, int x, int y, int z)
    {
        if (accentChance > 0 && !accent.isEmpty() && p.noise(x * 0.3, y * 0.25, z * 0.3) > 1 - accentChance * 2) return p.pick(accent, x, y, z, 82);
        return p.pick(rock, x, y, z, 81);
    }
}
