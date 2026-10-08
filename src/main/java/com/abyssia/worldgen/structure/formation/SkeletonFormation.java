package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The skeleton of a huge creature lying on its back: a gently bent spine of vertebrae with spikes, a cage of rib arches
 * over the chest (some missing or snapped short), and a skull with eye sockets, teeth and a dropped jaw at the head.
 * The spine is half sunk into the seabed. Ribs and skull are bone; a little accent (fossil rock) is mixed in.
 */
public record SkeletonFormation(Span length, Span rib, float ribGap, float bend, Mix bone, Mix accent) implements Formation
{
    public static final String TYPE = "skeleton";
    public static final MapCodec<SkeletonFormation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Span.CODEC.lenientOptionalFieldOf("length", Span.of(24, 40)).forGetter(SkeletonFormation::length),
            Span.CODEC.lenientOptionalFieldOf("rib", Span.of(4, 7)).forGetter(SkeletonFormation::rib),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("rib_gap", 0.15f).forGetter(SkeletonFormation::ribGap),
            Codec.floatRange(0, 8).lenientOptionalFieldOf("bend", 4f).forGetter(SkeletonFormation::bend),
            Mix.CODEC.fieldOf("bone").forGetter(SkeletonFormation::bone),
            Mix.CODEC.lenientOptionalFieldOf("accent", Mix.EMPTY).forGetter(SkeletonFormation::accent)
    ).apply(i, SkeletonFormation::new));

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        if (p.isEmpty()) return;
        // The whole layout is drawn up front, in a fixed order, so every chunk agrees on it.
        RandomSource random = site.random();
        double len = length.sample(random);
        double ribMax = rib.sample(random);
        double angle = random.nextDouble() * Math.PI * 2;
        double amp = bend * random.nextDouble();
        double phase = random.nextDouble() * Math.PI * 2;
        int slots = Math.max(1, (int) (len * 0.55 / 3));
        boolean[] keepL = new boolean[slots], keepR = new boolean[slots];
        double[] tip = new double[slots];
        for (int k = 0; k < slots; k++)
        {
            keepL[k] = random.nextFloat() >= ribGap;
            keepR[k] = random.nextFloat() >= ribGap;
            tip[k] = 0.7 + 0.3 * random.nextDouble();
        }
        int jaw = 1 + random.nextInt(3);
        Spine spine = new Spine(site.x, site.z, len, Math.cos(angle), Math.sin(angle), amp, phase);

        // Vertebrae: a fat one every other block, a narrow link between, and a spike up from the fat ones.
        int count = (int) len;
        for (int i = 0; i <= count; i++)
        {
            double s = -len / 2 + i, t = (s + len / 2) / len;
            double vr = i % 2 == 0 ? 0.8 + 1.1 * t : 0.55 + 0.4 * t;
            double cx = spine.x(s), cz = spine.z(s);
            if (!p.touches(cx, cz, vr + 1)) continue;
            int bx = Mth.floor(cx), bz = Mth.floor(cz);
            int cy = Mth.floor(p.smoothBase(bx, bz));
            Direction.Axis axis = Math.abs(spine.dx) > Math.abs(spine.dz) ? Direction.Axis.X : Direction.Axis.Z;
            int r = Mth.ceil(vr);
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int dy = -r; dy <= r; dy++)
                    {
                        if (dx * dx + dy * dy + dz * dz > vr * vr + 0.3) continue;
                        put(p, bx + dx, cy + dy, bz + dz, axis);
                    }
            if (i % 2 == 0)
            {
                int spike = 1 + (int) (p.hash(bx, cy, bz, 61) * 2.5);
                for (int k = 0; k < spike; k++) put(p, bx, cy + r + k, bz, Direction.Axis.Y);
            }
        }

        // Rib arches over the chest, tallest in the middle of the cage.
        for (int k = 0; k < slots; k++)
        {
            double s = -len / 2 + len * 0.36 + k * 3.0, t = (s + len / 2) / len;
            double rk = ribMax * Math.pow(Math.max(0, Math.sin(Math.PI * (t - 0.3) / 0.6)), 0.6);
            if (rk < 2) continue;
            double cx = spine.x(s), cz = spine.z(s);
            if (!p.touches(cx, cz, rk + 3)) continue;
            double tx = spine.x(s + 0.5) - spine.x(s - 0.5), tz = spine.z(s + 0.5) - spine.z(s - 0.5);
            double tl = Math.max(1e-6, Math.sqrt(tx * tx + tz * tz));
            tx /= tl;
            tz /= tl;
            double nx = -tz, nz = tx;
            int cy = Mth.floor(p.smoothBase(Mth.floor(cx), Mth.floor(cz)));
            Direction.Axis across = Math.abs(nx) > Math.abs(nz) ? Direction.Axis.X : Direction.Axis.Z;
            int ox = Math.abs(tx) > Math.abs(tz) ? (int) Math.signum(tx) : 0, oz = ox == 0 ? (int) Math.signum(tz) : 0;
            for (int side = -1; side <= 1; side += 2)
            {
                if (!(side < 0 ? keepL[k] : keepR[k])) continue;
                double phiMax = Math.PI * 0.88 * tip[k], step = Math.max(0.05, 0.45 / rk);
                for (double phi = 0; phi <= phiMax; phi += step)
                {
                    double lat = side * rk * Math.sin(phi), up = rk * 0.95 * (1 - Math.cos(phi));
                    double dLat = Math.abs(rk * Math.cos(phi)), dUp = rk * 0.95 * Math.sin(phi);
                    int x = Mth.floor(cx + nx * lat), z = Mth.floor(cz + nz * lat), y = cy + (int) Math.round(up);
                    Direction.Axis axis = dUp > dLat ? Direction.Axis.Y : across;
                    put(p, x, y, z, axis);
                    if (rk >= 5.5 && phi < phiMax * 0.7) put(p, x + ox, y, z + oz, axis);
                }
            }
        }

        // Skull at the head end: a dome with eye sockets over a dropped lower jaw, teeth along the gap.
        double h = ribMax * 0.55 + 2, semiA = 1.35 * h, semiB = h, semiC = 0.8 * h;
        double hx = spine.x(len / 2), hz = spine.z(len / 2);
        double tx = spine.x(len / 2) - spine.x(len / 2 - 1), tz = spine.z(len / 2) - spine.z(len / 2 - 1);
        double tl = Math.max(1e-6, Math.sqrt(tx * tx + tz * tz));
        tx /= tl;
        tz /= tl;
        double nx = -tz, nz = tx;
        double scx = hx + tx * semiA * 0.55, scz = hz + tz * semiA * 0.55;
        if (!p.touches(scx, scz, semiA + 2)) return;
        int yFloor = Mth.floor(p.smoothBase(Mth.floor(scx), Mth.floor(scz)));
        double jawY = yFloor + 0.3 * semiC, skullY = yFloor + 0.45 * semiC + jaw;
        Direction.Axis skullAxis = Math.abs(tx) > Math.abs(tz) ? Direction.Axis.X : Direction.Axis.Z;
        for (int x = p.fromX(scx, semiA + 1); x <= p.toX(scx, semiA + 1); x++)
        {
            for (int z = p.fromZ(scz, semiA + 1); z <= p.toZ(scz, semiA + 1); z++)
            {
                double dx = x + 0.5 - scx, dz = z + 0.5 - scz;
                double u = dx * tx + dz * tz, v = dx * nx + dz * nz;
                // upper dome
                double uq = Mth.square(u / semiA) + Mth.square(v / semiB);
                for (int y = (int) skullY; y <= skullY + semiC + 1; y++)
                {
                    double w = y + 0.5 - skullY;
                    double q = uq + Mth.square(w / semiC);
                    if (q < 0.62 || q > 1.0) continue;
                    double eu = u - 0.55 * semiA, ev = Math.abs(v) - 0.55 * semiB, ew = w - 0.45 * semiC;
                    if (eu * eu + ev * ev + ew * ew < Mth.square(0.28 * h + 0.4)) continue;
                    put(p, x, y, z, skullAxis);
                }
                // lower jaw
                double jq = Mth.square(u / (0.95 * semiA)) + Mth.square(v / (0.85 * semiB));
                for (int y = (int) (jawY - 0.45 * semiC) - 1; y <= jawY; y++)
                {
                    double q = jq + Mth.square((y + 0.5 - jawY) / (0.45 * semiC));
                    if (q >= 0.6 && q <= 1.0) put(p, x, y, z, skullAxis);
                }
                // teeth hanging from the rim of the upper skull
                if (Math.abs(u) < 0.9 * semiA && Math.floorMod(Math.round(u * 2), 3) == 0)
                {
                    double edge = semiB * Math.sqrt(Math.max(0, 1 - Mth.square(u / semiA)));
                    if (Math.abs(Math.abs(v) - edge * 0.92) < 0.6)
                    {
                        int ty = (int) skullY - 1;
                        put(p, x, ty, z, Direction.Axis.Y);
                        if (p.hash(x, ty, z, 62) < 0.5) put(p, x, ty - 1, z, Direction.Axis.Y);
                    }
                }
            }
        }
    }

    /** One bone block; a fraction come from the accent mix (fossilised rock). */
    private void put(Painter p, int x, int y, int z, Direction.Axis axis)
    {
        BlockState state = !accent.isEmpty() && p.hash(x, y, z, 63) < 0.12 ? p.pick(accent, x, y, z, 6) : p.pick(bone, x, y, z, 1);
        p.place(x, y, z, Painter.axis(state, axis));
    }

    /** The spine's centre line, running tail to head along (dx, dz) with a slow sideways wave. */
    private record Spine(int cx, int cz, double len, double dx, double dz, double amp, double phase)
    {
        private double lateral(double s)
        {
            return amp * Math.sin(s / len * Math.PI * 1.6 + phase);
        }

        double x(double s)
        {
            return cx + 0.5 + dx * s - dz * lateral(s);
        }

        double z(double s)
        {
            return cz + 0.5 + dz * s + dx * lateral(s);
        }
    }
}
