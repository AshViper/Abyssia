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
 * Crystal groups: hexagonal prisms with pointed tips, a large central crystal and smaller ones fanning outward from a
 * crystal-rock mound. Sizes mix within one group (small, medium and large crystals together); with a high
 * {@code tilt} crystals lie almost flat and cross like bridges. In caverns a share can hang from the roof.
 */
public record CrystalFormation(Span count, float spread, Span length, Span radius, float tilt, float centerScale, Mix crystal, Mix core,
                               Mix base, float baseRadius, float baseHeight, float hanging) implements Formation
{
    public static final String TYPE = "crystal";
    public static final MapCodec<CrystalFormation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Span.CODEC.fieldOf("count").forGetter(CrystalFormation::count),
            Codec.floatRange(0, 100).lenientOptionalFieldOf("spread", 4f).forGetter(CrystalFormation::spread),
            Span.CODEC.fieldOf("length").forGetter(CrystalFormation::length),
            Span.CODEC.fieldOf("radius").forGetter(CrystalFormation::radius),
            Codec.floatRange(0, 1.5f).lenientOptionalFieldOf("tilt", 0.5f).forGetter(CrystalFormation::tilt),
            Codec.floatRange(0.2f, 4f).lenientOptionalFieldOf("center_scale", 1.5f).forGetter(CrystalFormation::centerScale),
            Mix.CODEC.fieldOf("crystal").forGetter(CrystalFormation::crystal),
            Mix.CODEC.lenientOptionalFieldOf("core", Mix.EMPTY).forGetter(CrystalFormation::core),
            Mix.CODEC.lenientOptionalFieldOf("base", Mix.EMPTY).forGetter(CrystalFormation::base),
            Codec.floatRange(0, 100).lenientOptionalFieldOf("base_radius", 0f).forGetter(CrystalFormation::baseRadius),
            Codec.floatRange(0, 30).lenientOptionalFieldOf("base_height", 0f).forGetter(CrystalFormation::baseHeight),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("hanging", 0f).forGetter(CrystalFormation::hanging)
    ).apply(i, CrystalFormation::new));

    private static final double SIN60 = Math.sqrt(3) / 2;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        RandomSource random = site.random();
        if (!base.isEmpty() && baseRadius > 0) mound(site, p);
        int n = Math.max(1, count.sampleInt(random));
        for (int i = 0; i < n; i++)
        {
            double ox = site.x + 0.5, oz = site.z + 0.5, az;
            float scale;
            double tiltHere;
            if (i == 0)
            {
                az = random.nextDouble() * Math.PI * 2;
                scale = centerScale;
                tiltHere = tilt * 0.3 * random.nextDouble();
            }
            else
            {
                az = random.nextDouble() * Math.PI * 2;
                double d = spread * Math.sqrt(0.15 + 0.85 * random.nextDouble());
                ox += Math.cos(az) * d;
                oz += Math.sin(az) * d;
                // Fan outward: the farther out, the more a crystal leans away from the group's heart.
                scale = 0.35f + 0.75f * random.nextFloat();
                tiltHere = tilt * (0.35 + 0.65 * random.nextDouble());
                az += (random.nextDouble() - 0.5) * 0.8;
            }
            float len = length.sample(random) * scale;
            float r0 = Math.max(0.8f, radius.sample(random) * (float) Math.sqrt(scale));
            boolean down = site.cavern != null && random.nextFloat() < hanging;
            double twist = random.nextDouble() * Math.PI;
            prism(site, p, ox, oz, az, tiltHere, len, r0, down, twist);
        }
    }

    private void mound(Site site, Painter p)
    {
        for (int x = p.fromX(site.x, baseRadius); x <= p.toX(site.x, baseRadius); x++)
        {
            for (int z = p.fromZ(site.z, baseRadius); z <= p.toZ(site.z, baseRadius); z++)
            {
                double q = Math.hypot(x - site.x, z - site.z) / baseRadius;
                if (q >= 1 || !p.inReach(x, z)) continue;
                double h = baseHeight * (1 - q * q) * (0.8 + 0.3 * p.noise(x * 0.2, z * 0.2));
                int floor = p.floor(x, z);
                p.recoat(x, floor - 1, z, p.pick(base, x, floor - 1, z, 3));
                for (int k = 0; k < Math.round(h); k++) p.fill(x, floor + k, z, p.pick(base, x, floor + k, z, 3));
            }
        }
    }

    private void prism(Site site, Painter p, double ox, double oz, double az, double tiltAngle, float len, float r0, boolean down, double twist)
    {
        double dy = Math.cos(tiltAngle) * (down ? -1 : 1);
        double dx = Math.sin(tiltAngle) * Math.cos(az), dz = Math.sin(tiltAngle) * Math.sin(az);
        double oy;
        if (down) oy = site.ceilingAt(ox, oz) + 1 + 0.1 * len;
        else oy = site.baseAt(ox, oz) - 1 - 0.15 * len;
        // Keep the tip under the height limit.
        double tipY = oy + dy * len;
        if (!down && tipY >= site.topY - 1) len = (float) Math.max(2, (site.topY - 2 - oy) / Math.max(0.2, dy));
        // Orthonormal cross-section axes, rotated by twist so hexagons do not all face the same way.
        double[] e1 = perpendicular(dx, dy, dz);
        double[] e2 = {dy * e1[2] - dz * e1[1], dz * e1[0] - dx * e1[2], dx * e1[1] - dy * e1[0]};
        double c = Math.cos(twist), s = Math.sin(twist);
        double[] u1 = {e1[0] * c + e2[0] * s, e1[1] * c + e2[1] * s, e1[2] * c + e2[2] * s};
        double[] u2 = {e2[0] * c - e1[0] * s, e2[1] * c - e1[1] * s, e2[2] * c - e1[2] * s};
        double ex = ox + dx * len, ey = oy + dy * len, ez = oz + dz * len;
        double pad = r0 + 1.5;
        int xa = Math.max(p.x0, Mth.floor(Math.min(ox, ex) - pad)), xb = Math.min(p.x1, Mth.ceil(Math.max(ox, ex) + pad));
        int za = Math.max(p.z0, Mth.floor(Math.min(oz, ez) - pad)), zb = Math.min(p.z1, Mth.ceil(Math.max(oz, ez) + pad));
        int ya = Mth.floor(Math.min(oy, ey) - pad), yb = Mth.ceil(Math.max(oy, ey) + pad);
        for (int x = xa; x <= xb; x++)
        {
            for (int z = za; z <= zb; z++)
            {
                if (!p.inReach(x, z)) continue;
                for (int y = ya; y <= yb; y++)
                {
                    double wx = x + 0.5 - ox, wy = y + 0.5 - oy, wz = z + 0.5 - oz;
                    double along = wx * dx + wy * dy + wz * dz;
                    if (along < 0 || along > len) continue;
                    double a = wx * u1[0] + wy * u1[1] + wz * u1[2], b = wx * u2[0] + wy * u2[1] + wz * u2[2];
                    double hex = Math.max(Math.abs(a), Math.max(Math.abs(a * 0.5 + b * SIN60), Math.abs(a * 0.5 - b * SIN60)));
                    double r = along < 0.7 * len ? r0 : r0 * (1 - (along - 0.7 * len) / (0.3 * len));
                    if (hex > r + 0.35) continue;
                    boolean inner = !core.isEmpty() && hex < r - 1.2;
                    p.place(x, y, z, inner ? p.pick(core, x, y, z, 4) : p.pick(crystal, x, y, z, 5));
                }
            }
        }
    }

    private static double[] perpendicular(double x, double y, double z)
    {
        // Cross with whichever axis is least parallel, then normalise.
        double ax = Math.abs(x) < 0.6 ? 1 : 0, ay = ax == 0 ? 1 : 0;
        double px = y * 0 - z * ay, py = z * ax - x * 0, pz = x * ay - y * ax;
        double l = Math.sqrt(px * px + py * py + pz * pz);
        return new double[]{px / l, py / l, pz / l};
    }
}
