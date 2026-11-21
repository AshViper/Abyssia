package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Optional;

/**
 * A bone graveyard: several giant skeletons ({@link SkeletonFormation}, each from its own sub-seed) lying around the
 * site centre on a patch of bone sediment, with single bones scattered between them and, in big sites, one loot barrel
 * tucked among the ribs. The layout is drawn up front in a fixed order, so every chunk paints its own share of it.
 */
public record FossilGraveyardFormation(Span count, float spread, SkeletonFormation skeleton, Mix ground, Span groundRadius, Span scatter,
                                       Mix crate, Optional<ResourceLocation> loot, float lootChance) implements Formation
{
    public static final String TYPE = "fossil_graveyard";
    public static final Codec<FossilGraveyardFormation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.optionalFieldOf("count", Span.of(1, 4)).forGetter(FossilGraveyardFormation::count),
            Codec.floatRange(0, 200).optionalFieldOf("spread", 20f).forGetter(FossilGraveyardFormation::spread),
            SkeletonFormation.CODEC.fieldOf("skeleton").forGetter(FossilGraveyardFormation::skeleton),
            Mix.CODEC.optionalFieldOf("ground", Mix.EMPTY).forGetter(FossilGraveyardFormation::ground),
            Span.CODEC.optionalFieldOf("ground_radius", Span.of(14, 22)).forGetter(FossilGraveyardFormation::groundRadius),
            Span.CODEC.optionalFieldOf("scatter", Span.of(4, 10)).forGetter(FossilGraveyardFormation::scatter),
            Mix.CODEC.optionalFieldOf("crate", Mix.EMPTY).forGetter(FossilGraveyardFormation::crate),
            ResourceLocation.CODEC.optionalFieldOf("loot").forGetter(FossilGraveyardFormation::loot),
            Codec.floatRange(0, 1).optionalFieldOf("loot_chance", 0f).forGetter(FossilGraveyardFormation::lootChance)
    ).apply(i, FossilGraveyardFormation::new));

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        if (p.isEmpty()) return;
        RandomSource random = site.random();
        int n = Math.max(1, count.sampleInt(random));
        int[] sx = new int[n], sz = new int[n];
        double[] gr = new double[n];
        long[] seeds = new long[n];
        for (int k = 0; k < n; k++)
        {
            double a = random.nextDouble() * Math.PI * 2, d = k == 0 && n > 1 ? spread * 0.25 * random.nextDouble() : spread * (0.55 + 0.45 * random.nextDouble());
            if (n == 1) d = 0;
            sx[k] = site.x + (int) Math.round(Math.cos(a) * d);
            sz[k] = site.z + (int) Math.round(Math.sin(a) * d);
            gr[k] = groundRadius.sample(random);
            seeds[k] = random.nextLong();
        }
        int bones = Math.max(0, scatter.sampleInt(random));
        int[] bx = new int[bones], bz = new int[bones], bl = new int[bones];
        boolean[] bAlongX = new boolean[bones];
        for (int k = 0; k < bones; k++)
        {
            double a = random.nextDouble() * Math.PI * 2, d = (spread + 12) * Math.sqrt(random.nextDouble());
            bx[k] = site.x + (int) Math.round(Math.cos(a) * d);
            bz[k] = site.z + (int) Math.round(Math.sin(a) * d);
            bl[k] = 2 + random.nextInt(4);
            bAlongX[k] = random.nextBoolean();
        }
        boolean chest = !crate.isEmpty() && loot.isPresent() && random.nextFloat() < lootChance;
        double ca = random.nextDouble() * Math.PI * 2;
        int cx = chest ? sx[0] + (int) Math.round(Math.cos(ca) * 3) : 0, cz = chest ? sz[0] + (int) Math.round(Math.sin(ca) * 3) : 0;

        // Bone-sediment patch under and around each skeleton, ragged at the edge.
        if (!ground.isEmpty())
        {
            for (int k = 0; k < n; k++)
            {
                double r = gr[k];
                if (!p.touches(sx[k], sz[k], r + 1)) continue;
                for (int x = p.fromX(sx[k], r); x <= p.toX(sx[k], r); x++)
                {
                    for (int z = p.fromZ(sz[k], r); z <= p.toZ(sz[k], r); z++)
                    {
                        if (!p.inReach(x, z)) continue;
                        double dx = x - sx[k], dz = z - sz[k];
                        double lim = r * (0.7 + 0.3 * p.noise(x * 0.12, z * 0.12));
                        if (dx * dx + dz * dz > lim * lim || p.hash(x, 0, z, 71) > 0.9) continue;
                        int y = p.floor(x, z) - 1;
                        p.recoat(x, y, z, p.pick(ground, x, y, z, 72));
                    }
                }
            }
        }

        for (int k = 0; k < n; k++) skeleton.paintAt(sx[k], sz[k], RandomSource.create(seeds[k]), p);

        // Single bones lying on the sediment.
        for (int k = 0; k < bones; k++)
        {
            if (!p.touches(bx[k], bz[k], bl[k] + 1)) continue;
            Direction.Axis axis = bAlongX[k] ? Direction.Axis.X : Direction.Axis.Z;
            for (int j = 0; j < bl[k]; j++)
            {
                int x = bx[k] + (bAlongX[k] ? j : 0), z = bz[k] + (bAlongX[k] ? 0 : j);
                if (!p.inReach(x, z)) continue;
                int y = p.floor(x, z);
                p.place(x, y, z, Painter.axis(p.pick(skeleton.bone(), x, y, z, 73), axis));
            }
        }

        if (chest && p.touches(cx, cz, 1) && p.inReach(cx, cz))
        {
            int y = p.floor(cx, cz);
            p.container(cx, y, cz, crate.pick(p.hash(cx, y, cz, 74)), loot.get(), site.seed ^ Mth.getSeed(cx, y, cz));
        }
    }
}
