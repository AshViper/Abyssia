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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Arrays;

/**
 * A toppled log lying along x or z (1x1 or 2x2), its axis matching its direction, partly sunk into the seabed, kinked
 * one block sideways part-way and with a few side branches at its top. Ends and breaks are sprinkled with planks.
 */
public record FallenLogFormation(Span length, float thick, Span branches, Span branchLength, float jogChance, float buryChance,
                                 float strippedChance, Mix wood, Mix stripped, Mix root, Mix planks) implements Formation
{
    public static final String TYPE = "fallen_log";
    public static final MapCodec<FallenLogFormation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Span.CODEC.lenientOptionalFieldOf("length", Span.of(5, 14)).forGetter(FallenLogFormation::length),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("thick", 0.3f).forGetter(FallenLogFormation::thick),
            Span.CODEC.lenientOptionalFieldOf("branches", Span.of(0, 4)).forGetter(FallenLogFormation::branches),
            Span.CODEC.lenientOptionalFieldOf("branch_length", Span.of(1, 3)).forGetter(FallenLogFormation::branchLength),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("jog_chance", 0.7f).forGetter(FallenLogFormation::jogChance),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("bury_chance", 0.4f).forGetter(FallenLogFormation::buryChance),
            Codec.floatRange(0, 1).lenientOptionalFieldOf("stripped_chance", 0.2f).forGetter(FallenLogFormation::strippedChance),
            Mix.CODEC.fieldOf("wood").forGetter(FallenLogFormation::wood),
            Mix.CODEC.lenientOptionalFieldOf("stripped", Mix.EMPTY).forGetter(FallenLogFormation::stripped),
            Mix.CODEC.fieldOf("root").forGetter(FallenLogFormation::root),
            Mix.CODEC.lenientOptionalFieldOf("planks", Mix.EMPTY).forGetter(FallenLogFormation::planks)
    ).apply(i, FallenLogFormation::new));

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
        int len = Math.max(5, length.sampleInt(random));
        int size = random.nextFloat() < thick ? 2 : 1;
        boolean alongX = random.nextBoolean();
        Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
        Direction.Axis across = alongX ? Direction.Axis.Z : Direction.Axis.X;
        int jogAt = 2 + random.nextInt(Math.max(1, len - 4));
        int jog = random.nextFloat() < jogChance ? (random.nextBoolean() ? 1 : -1) : 0;
        int buryFrom = random.nextInt(len), buryLen = 1 + random.nextInt(Math.max(1, len / 2));
        boolean buryAny = random.nextFloat() < buryChance;
        int nBranch = Math.max(0, branches.sampleInt(random));
        int[] bAt = new int[nBranch], bSide = new int[nBranch], bLen = new int[nBranch];
        boolean[] bRise = new boolean[nBranch];
        for (int b = 0; b < nBranch; b++)
        {
            bAt[b] = random.nextInt(len);
            bSide[b] = random.nextBoolean() ? 1 : -1;
            bLen[b] = Math.max(1, branchLength.sampleInt(random));
            bRise[b] = random.nextBoolean();
        }

        int start = -len / 2;
        int[] y0 = new int[len];
        Arrays.fill(y0, Integer.MIN_VALUE);

        for (int i = 0; i < len; i++)
        {
            int lat = i >= jogAt ? jog : 0;
            int lo = Math.min(lat, i == jogAt ? 0 : lat), hi = Math.max(lat + size - 1, i == jogAt ? size - 1 : lat + size - 1);
            if (!anyInReach(site, p, alongX, start + i, lo, hi)) continue;
            int y = floorY(site, alongX, y0, i, start, lo, hi, buryAny && i >= buryFrom && i < buryFrom + buryLen);
            for (int l = lo; l <= hi; l++)
            {
                int x = site.x + (alongX ? start + i : l), z = site.z + (alongX ? l : start + i);
                if (!p.inReach(x, z)) continue;
                for (int v = 0; v < size; v++)
                {
                    int yy = y + v;
                    BlockState state;
                    if ((i == 0 || i == len - 1) && !planks.isEmpty() && p.hash(x, yy, z, 21) < 0.3) state = p.pick(planks, x, yy, z, 3);
                    else
                    {
                        boolean strip = !stripped.isEmpty() && p.hash(x, yy, z, 22) < strippedChance;
                        state = axis(strip ? p.pick(stripped, x, yy, z, 2) : p.pick(wood, x, yy, z, 1), axis);
                    }
                    p.place(x, yy, z, state);
                }
                p.root(x, y - 1, z, p.pick(root, x, y - 1, z, 4), 6);
            }
        }

        for (int b = 0; b < nBranch; b++)
        {
            int i = bAt[b];
            int lat = i >= jogAt ? jog : 0;
            int edge = bSide[b] > 0 ? lat + size : lat - 1;
            boolean reach = false;
            for (int k = 0; k < bLen[b] && !reach; k++) reach = anyInReach(site, p, alongX, start + i, edge + bSide[b] * k, edge + bSide[b] * k);
            if (!reach) continue;
            int y = floorY(site, alongX, y0, i, start, Math.min(lat, i == jogAt ? 0 : lat), Math.max(lat + size - 1, i == jogAt ? size - 1 : lat + size - 1), buryAny && i >= buryFrom && i < buryFrom + buryLen) + size - 1;
            for (int k = 0; k < bLen[b]; k++)
            {
                int l = edge + bSide[b] * k;
                int x = site.x + (alongX ? start + i : l), z = site.z + (alongX ? l : start + i);
                if (!p.inReach(x, z)) continue;
                int yy = y + (bRise[b] && k >= 2 ? 1 : 0);
                p.place(x, yy, z, axis(p.pick(wood, x, yy, z, 1), across));
                p.root(x, yy - 1, z, p.pick(root, x, yy - 1, z, 4), 4);
            }
        }
    }

    /** Bottom block of the log at this index: the lowest floor under its cells, one lower where it is sunk. */
    private static int floorY(Site site, boolean alongX, int[] cache, int i, int start, int lo, int hi, boolean buried)
    {
        if (cache[i] == Integer.MIN_VALUE)
        {
            int base = Integer.MAX_VALUE;
            for (int l = lo; l <= hi; l++)
                base = Math.min(base, site.baseAt(site.x + (alongX ? start + i : l) + 0.5, site.z + (alongX ? l : start + i) + 0.5));
            cache[i] = base;
        }
        return cache[i] - (buried ? 1 : 0);
    }

    private static boolean anyInReach(Site site, Painter p, boolean alongX, int along, int lo, int hi)
    {
        for (int l = lo; l <= hi; l++)
            if (p.inReach(site.x + (alongX ? along : l), site.z + (alongX ? l : along))) return true;
        return false;
    }

    private static BlockState axis(BlockState state, Direction.Axis axis)
    {
        return state.hasProperty(BlockStateProperties.AXIS) ? state.setValue(BlockStateProperties.AXIS, axis) : state;
    }
}
