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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Cracks in the seabed. A {@code fissure} is a long, meandering, V-narrowing rift with exposed strata on its walls,
 * a broken lip, fallen blocks wedged between the walls and, optionally, a shaft at the bottom leading deeper down.
 * A {@code shaft} is a near-round vertical pit with ledged, narrowing walls. Depth fades toward the ends, so a rift
 * closes naturally instead of stopping at a wall.
 */
public record TrenchFormation(Shape shape, Span length, Span width, Span depth, float meander, float roughness, float rimHeight,
                              Mix wall, Mix floor, Mix debris, Span debrisCount, Span shaft) implements Formation
{
    public static final String TYPE = "trench";
    public static final MapCodec<TrenchFormation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Shape.CODEC.lenientOptionalFieldOf("shape", Shape.FISSURE).forGetter(TrenchFormation::shape),
            Span.CODEC.fieldOf("length").forGetter(TrenchFormation::length),
            Span.CODEC.fieldOf("width").forGetter(TrenchFormation::width),
            Span.CODEC.fieldOf("depth").forGetter(TrenchFormation::depth),
            Codec.floatRange(0, 2).lenientOptionalFieldOf("meander", 0.5f).forGetter(TrenchFormation::meander),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("roughness", 0.4f).forGetter(TrenchFormation::roughness),
            Codec.floatRange(0, 10).lenientOptionalFieldOf("rim_height", 1.5f).forGetter(TrenchFormation::rimHeight),
            Mix.CODEC.fieldOf("wall").forGetter(TrenchFormation::wall),
            Mix.CODEC.fieldOf("floor").forGetter(TrenchFormation::floor),
            Mix.CODEC.lenientOptionalFieldOf("debris", Mix.EMPTY).forGetter(TrenchFormation::debris),
            Span.CODEC.lenientOptionalFieldOf("debris_count", Span.of(0, 0)).forGetter(TrenchFormation::debrisCount),
            Span.CODEC.lenientOptionalFieldOf("shaft", Span.of(0, 0)).forGetter(TrenchFormation::shaft)
    ).apply(i, TrenchFormation::new));

    public enum Shape implements StringRepresentable
    {
        FISSURE, SHAFT;

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private record Rock(double u, double k, double r) {}

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public int carveDepth(SeabedStructure definition)
    {
        return Mth.ceil(depth.max() + shaft.max()) + 2;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        RandomSource random = site.random();
        double L = shape == Shape.SHAFT ? 0 : length.sample(random);
        double W = width.sample(random), D = depth.sample(random);
        double theta = random.nextDouble() * Math.PI;
        double shaftDepth = shaft.sample(random), shaftU = (random.nextDouble() - 0.5) * 0.6 * L / 2, shaftR = W * (0.3 + 0.2 * random.nextDouble());
        Rock[] rocks = new Rock[Math.max(0, debrisCount.sampleInt(random))];
        for (int i = 0; i < rocks.length; i++)
        {
            rocks[i] = new Rock((random.nextDouble() - 0.5) * 0.8 * L, 0.25 + 0.6 * random.nextDouble(), 1.5 + random.nextDouble() * W * 0.25);
        }
        if (shape == Shape.SHAFT) shaftPit(site, p, W / 2, D);
        else fissure(site, p, L, W, D, theta, shaftDepth, shaftU, shaftR, rocks);
    }

    private void fissure(Site site, Painter p, double L, double W, double D, double theta, double shaftDepth, double shaftU, double shaftR, Rock[] rocks)
    {
        double half = L / 2, cos = Math.cos(theta), sin = Math.sin(theta);
        double reach = half + W + 4;
        int bottomLimit = p.level.getMinBuildHeight() + 8;
        for (int x = p.fromX(site.x, reach); x <= p.toX(site.x, reach); x++)
        {
            for (int z = p.fromZ(site.z, reach); z <= p.toZ(site.z, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                double dx = x + 0.5 - site.x, dz = z + 0.5 - site.z;
                double u = dx * cos + dz * sin;
                if (Math.abs(u) > half) continue;
                double v = -dx * sin + dz * cos - meander * W * 1.5 * p.noise(u * 0.02, 17.3);
                double e = u / half;
                double wHalf = W / 2 * Math.sqrt(Math.max(0, 1 - e * e * e * e)) * (1 + roughness * 0.4 * p.noise(u * 0.1, v * 0.1 + 40));
                double dHere = D * Math.sqrt(Math.max(0, 1 - e * e)) * (0.85 + 0.15 * p.noise(u * 0.05, 90));
                double av = Math.abs(v);
                int floorY = p.floor(x, z);
                if (av >= wHalf)
                {
                    // Broken lip along the rim, with wall rock showing just past the edge.
                    double lip = (av - wHalf) / Math.max(1, W * 0.6);
                    if (lip < 1 && rimHeight > 0)
                    {
                        int h = (int) Math.round(rimHeight * (1 - lip) * (0.6 + 0.4 * p.noise(x * 0.3, z * 0.3)));
                        for (int k = 0; k < h; k++) p.fill(x, floorY + k, z, p.pick(wall, x, floorY + k, z, 51));
                    }
                    if (av < wHalf + 1.6) for (int k = 1; k <= Math.min(dHere, 40); k++) p.recoat(x, floorY - k, z, p.pick(wall, x, floorY - k, z, 51));
                    continue;
                }
                int deepest = floorY;
                for (int k = 0; k < dHere; k++)
                {
                    int y = floorY - 1 - k;
                    if (y <= bottomLimit) break;
                    // V-profile: the crack narrows with depth; beyond the edge the wall is re-coated (exposed strata).
                    double allowed = wHalf * (1 - 0.45 * k / Math.max(1, dHere)) + roughness * 1.2 * p.noise(x * 0.2, y * 0.15, z * 0.2);
                    if (av > allowed)
                    {
                        if (av < allowed + 1.6) p.recoat(x, y, z, p.pick(wall, x, y, z, 51));
                        continue;
                    }
                    if (!p.carve(x, y, z)) break;
                    deepest = y;
                }
                // Deeper shaft at the bottom: "the way further down".
                if (shaftDepth > 0 && Math.hypot(u - shaftU, v) < shaftR)
                {
                    double sd = Math.hypot(u - shaftU, v) / shaftR;
                    for (int k = 0; k < shaftDepth * (1 - sd * sd * 0.3); k++)
                    {
                        int y = deepest - 1 - k;
                        if (y <= bottomLimit || !p.carve(x, y, z)) break;
                    }
                }
                else if (deepest < floorY)
                {
                    p.recoat(x, deepest - 1, z, p.pick(floor, x, deepest - 1, z, 52));
                }
            }
        }
        // Blocks fallen into the crack and wedged between its walls.
        if (!debris.isEmpty())
        {
            for (int i = 0; i < rocks.length; i++)
            {
                Rock r = rocks[i];
                double bx = site.x + 0.5 + r.u * cos, bz = site.z + 0.5 + r.u * sin;
                double by = site.baseAt(bx, bz) - 1 - D * r.k * Math.sqrt(Math.max(0, 1 - Mth.square(r.u / half)));
                for (int x = p.fromX(bx, r.r + 1); x <= p.toX(bx, r.r + 1); x++)
                {
                    for (int z = p.fromZ(bz, r.r + 1); z <= p.toZ(bz, r.r + 1); z++)
                    {
                        if (!p.inReach(x, z)) continue;
                        for (int y = Mth.floor(by - r.r); y <= Mth.ceil(by + r.r); y++)
                        {
                            double q = Mth.square(x + 0.5 - bx) + Mth.square((y + 0.5 - by) * 1.3) + Mth.square(z + 0.5 - bz);
                            if (q <= r.r * r.r * (1 + 0.3 * p.noise(x * 0.4 + i, y * 0.4, z * 0.4))) p.place(x, y, z, p.pick(debris, x, y, z, 53));
                        }
                    }
                }
            }
        }
    }

    private void shaftPit(Site site, Painter p, double r0, double D)
    {
        double reach = r0 * 1.6 + 3;
        int bottomLimit = p.level.getMinBuildHeight() + 8;
        for (int x = p.fromX(site.x, reach); x <= p.toX(site.x, reach); x++)
        {
            for (int z = p.fromZ(site.z, reach); z <= p.toZ(site.z, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                double dx = x + 0.5 - site.x - 0.5, dz = z + 0.5 - site.z - 0.5, d = Math.sqrt(dx * dx + dz * dz);
                double ang = Math.atan2(dz, dx);
                int floorY = p.floor(x, z);
                int deepest = floorY;
                for (int k = 0; k < D; k++)
                {
                    int y = floorY - 1 - k;
                    if (y <= bottomLimit) break;
                    double t = k / D;
                    // Narrowing, with ledges every few blocks and a lobed outline.
                    double r = r0 * (1 - 0.35 * t) * (1 + roughness * 0.3 * p.noise(Math.cos(ang) * 1.4, y * 0.08, Math.sin(ang) * 1.4))
                            - (Math.floorMod(y, 7) == 0 ? roughness * 1.5 : 0);
                    if (d > r)
                    {
                        if (d < r + 1.6) p.recoat(x, y, z, p.pick(wall, x, y, z, 51));
                        continue;
                    }
                    if (!p.carve(x, y, z)) break;
                    deepest = y;
                }
                if (deepest < floorY) p.recoat(x, deepest - 1, z, p.pick(floor, x, deepest - 1, z, 52));
                else if (d < r0 * 1.5 && rimHeight > 0 && p.hash(x, 0, z, 54) < 0.5)
                {
                    p.fill(x, floorY, z, p.pick(wall, x, floorY, z, 51));
                }
            }
        }
    }
}
