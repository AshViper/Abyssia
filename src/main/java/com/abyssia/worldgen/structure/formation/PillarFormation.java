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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rock spires and towers: one main spire and optional satellites, each tapering, leaning and slightly bent, with
 * terraced ledges, an eroded non-circular outline, vertical cracks, mineral streaks and a crumbled tip on some, rooted
 * into the seabed and skirted by a talus apron so the foot blends into the floor.
 */
public record PillarFormation(Span count, float spread, Span height, Span radius, float taper, float lean, float ledges, float erosion,
                              float cracks, float brokenTop, Mix rock, Mix accent, float accentChance, Mix apron, float apronHeight) implements Formation
{
    public static final String TYPE = "pillar";
    public static final MapCodec<PillarFormation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Span.CODEC.lenientOptionalFieldOf("count", Span.of(1, 1)).forGetter(PillarFormation::count),
            Codec.floatRange(0, 120).lenientOptionalFieldOf("spread", 0f).forGetter(PillarFormation::spread),
            Span.CODEC.fieldOf("height").forGetter(PillarFormation::height),
            Span.CODEC.fieldOf("radius").forGetter(PillarFormation::radius),
            Codec.floatRange(0.1f, 3f).lenientOptionalFieldOf("taper", 0.8f).forGetter(PillarFormation::taper),
            Codec.floatRange(0, 0.6f).lenientOptionalFieldOf("lean", 0.12f).forGetter(PillarFormation::lean),
            Codec.floatRange(0, 0.6f).lenientOptionalFieldOf("ledges", 0.15f).forGetter(PillarFormation::ledges),
            Codec.floatRange(0, 0.8f).lenientOptionalFieldOf("erosion", 0.25f).forGetter(PillarFormation::erosion),
            Codec.floatRange(0, 0.5f).lenientOptionalFieldOf("cracks", 0.08f).forGetter(PillarFormation::cracks),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("broken_top", 0.3f).forGetter(PillarFormation::brokenTop),
            Mix.CODEC.fieldOf("rock").forGetter(PillarFormation::rock),
            Mix.CODEC.lenientOptionalFieldOf("accent", Mix.EMPTY).forGetter(PillarFormation::accent),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("accent_chance", 0f).forGetter(PillarFormation::accentChance),
            Mix.CODEC.lenientOptionalFieldOf("apron", Mix.EMPTY).forGetter(PillarFormation::apron),
            Codec.floatRange(0, 12).lenientOptionalFieldOf("apron_height", 2f).forGetter(PillarFormation::apronHeight)
    ).apply(i, PillarFormation::new));

    private static final int ROOT = 3;

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
            // Every draw happens for every spire, painted here or not, so all chunks see the same layout.
            double ex = site.x + 0.5, ez = site.z + 0.5;
            float scale = 1f;
            if (i > 0)
            {
                double a = random.nextDouble() * Math.PI * 2, d = spread * (0.35 + 0.65 * random.nextDouble());
                ex += Math.cos(a) * d;
                ez += Math.sin(a) * d;
                scale = 0.3f + 0.55f * random.nextFloat();
            }
            float h = height.sample(random) * scale;
            float r0 = radius.sample(random) * (i == 0 ? 1f : 0.55f + 0.45f * scale);
            double lx = (random.nextDouble() - 0.5) * 2 * lean, lz = (random.nextDouble() - 0.5) * 2 * lean;
            boolean broken = random.nextFloat() < brokenTop;
            int step = 3 + random.nextInt(4);
            spire(site, p, i, ex, ez, Math.round(h), r0, lx, lz, broken, step);
        }
    }

    private void spire(Site site, Painter p, int index, double ex, double ez, int h0, float r0, double lx, double lz, boolean broken, int step)
    {
        int base = site.baseAt(ex, ez) - 1;
        int h = site.clampHeight(base, h0);
        if (h < 3) return;
        double apronR = apron.isEmpty() ? 0 : r0 * 2.4 + 1;
        double rMax = r0 * (1 + ledges) * (1 + erosion) + 0.5;
        double reach = Math.max(rMax + 1 + Math.max(Math.abs(lx), Math.abs(lz)) * h, apronR);
        for (int x = p.fromX(ex, reach); x <= p.toX(ex, reach); x++)
        {
            for (int z = p.fromZ(ez, reach); z <= p.toZ(ez, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                double d0 = Math.hypot(x + 0.5 - ex, z + 0.5 - ez);
                if (d0 < apronR)
                {
                    // Talus: rubble and sediment heaped around the foot, highest against the rock.
                    int floor = p.floor(x, z);
                    double a = apronHeight * Mth.square(1 - d0 / apronR) * (0.7 + 0.3 * p.noise(x * 0.3, z * 0.3));
                    for (int k = 0; k < Math.round(a); k++) p.fill(x, floor + k, z, p.pick(apron, x, floor + k, z, 5));
                }
                boolean rooted = false;
                for (int y = base - ROOT; y <= base + h; y++)
                {
                    int hy = Math.max(0, y - base);
                    double t = hy / (double) h;
                    double bend = h * Math.pow(t, 1.4);
                    double dx = x + 0.5 - (ex + lx * bend), dz = z + 0.5 - (ez + lz * bend);
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > rMax) continue;
                    double rad = r0 * Math.pow(1 - t * 0.9, taper);
                    // Terraces: every few blocks the section steps in or out.
                    rad *= 1 + ledges * (p.hash(index, Math.floorDiv(y, step), 0, 11) - 0.5) * 2;
                    double ang = Math.atan2(dz, dx);
                    rad *= 1 + erosion * p.noise(Math.cos(ang) * 1.3 + index * 7, y * 0.09, Math.sin(ang) * 1.3);
                    if (dist > rad + 0.5) continue;
                    if (y == base - ROOT) rooted = true;
                    if (cracks > 0 && dist > rad * 0.55 && Math.abs(p.noise(x * 0.21, y * 0.05, z * 0.21)) < cracks * 0.25) continue;
                    // Crumbled tip: each column stops at its own height, a jagged break with nothing left floating.
                    if (broken && t > 0.78 + 0.22 * p.hash(x, index, z, 12)) continue;
                    p.place(x, y, z, material(p, x, y, z));
                }
                if (rooted) p.root(x, base - ROOT - 1, z, p.pick(rock, x, base, z, 1), 24);
            }
        }
    }

    private BlockState material(Painter p, int x, int y, int z)
    {
        if (accentChance > 0 && !accent.isEmpty() && p.noise(x * 0.35, y * 0.2, z * 0.35) > 1 - accentChance * 2) return p.pick(accent, x, y, z, 2);
        return p.pick(rock, x, y, z, 1);
    }
}
