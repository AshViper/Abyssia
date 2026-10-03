package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A dead, standing tree trunk (1x1 or 2x2) with a broken top, a few sideways branches ending in fronds and a few roots
 * flaring at the foot. The trunk sinks one block into the seabed.
 */
public record RottenTreeFormation(Span height, float thick, Span branches, Span branchLength, Span roots, float frondChance,
                                  Mix wood, Mix stripped, Mix root, Mix frond, Mix planks) implements Formation
{
    public static final String TYPE = "rotten_tree";
    public static final Codec<RottenTreeFormation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.optionalFieldOf("height", Span.of(3, 8)).forGetter(RottenTreeFormation::height),
            Codec.floatRange(0, 1).optionalFieldOf("thick", 0.35f).forGetter(RottenTreeFormation::thick),
            Span.CODEC.optionalFieldOf("branches", Span.of(0, 3)).forGetter(RottenTreeFormation::branches),
            Span.CODEC.optionalFieldOf("branch_length", Span.of(1, 3)).forGetter(RottenTreeFormation::branchLength),
            Span.CODEC.optionalFieldOf("roots", Span.of(1, 3)).forGetter(RottenTreeFormation::roots),
            Codec.floatRange(0, 1).optionalFieldOf("frond_chance", 0.5f).forGetter(RottenTreeFormation::frondChance),
            Mix.CODEC.fieldOf("wood").forGetter(RottenTreeFormation::wood),
            Mix.CODEC.optionalFieldOf("stripped", Mix.EMPTY).forGetter(RottenTreeFormation::stripped),
            Mix.CODEC.fieldOf("root").forGetter(RottenTreeFormation::root),
            Mix.CODEC.optionalFieldOf("frond", Mix.EMPTY).forGetter(RottenTreeFormation::frond),
            Mix.CODEC.optionalFieldOf("planks", Mix.EMPTY).forGetter(RottenTreeFormation::planks)
    ).apply(i, RottenTreeFormation::new));

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
        int h = Math.max(3, height.sampleInt(random));
        int size = random.nextFloat() < thick ? 2 : 1;
        int nBranch = Math.max(0, branches.sampleInt(random));
        int[] bLevel = new int[nBranch], bDir = new int[nBranch], bOff = new int[nBranch], bLen = new int[nBranch];
        boolean[] bRise = new boolean[nBranch], bFrond = new boolean[nBranch];
        int lo = Math.max(1, h / 2), hi = Math.max(lo, h - 2);
        for (int b = 0; b < nBranch; b++)
        {
            bLevel[b] = lo + random.nextInt(hi - lo + 1);
            bDir[b] = random.nextInt(4);
            bOff[b] = random.nextInt(size);
            bLen[b] = Math.max(1, branchLength.sampleInt(random));
            bRise[b] = random.nextBoolean();
            bFrond[b] = random.nextFloat() < frondChance;
        }
        int nRoot = Math.max(1, roots.sampleInt(random));
        int[] rDir = new int[nRoot], rOff = new int[nRoot], rDist = new int[nRoot];
        boolean[] rFlare = new boolean[nRoot];
        for (int k = 0; k < nRoot; k++)
        {
            rDir[k] = random.nextInt(4);
            rOff[k] = random.nextInt(size);
            rDist[k] = random.nextFloat() < 0.3f ? 2 : 1;
            rFlare[k] = random.nextFloat() < 0.5f;
        }

        int base = Integer.MAX_VALUE;
        for (int dx = 0; dx < size; dx++)
            for (int dz = 0; dz < size; dz++) base = Math.min(base, site.baseAt(site.x + dx + 0.5, site.z + dz + 0.5));

        // Trunk: sunk one block, stripped patches in the upper half, a ragged broken top.
        for (int dx = 0; dx < size; dx++)
        {
            for (int dz = 0; dz < size; dz++)
            {
                int x = site.x + dx, z = site.z + dz;
                if (!p.inReach(x, z)) continue;
                for (int y = base - 1; y <= base + h - 2; y++)
                {
                    boolean strip = !stripped.isEmpty() && y - base >= h / 2 && p.hash(x, y, z, 11) < 0.35;
                    p.place(x, y, z, strip ? p.pick(stripped, x, y, z, 2) : p.pick(wood, x, y, z, 1));
                }
                int top = base + h - 1;
                double v = p.hash(x, top, z, 12);
                if (v < 0.4) p.place(x, top, z, !stripped.isEmpty() ? p.pick(stripped, x, top, z, 2) : p.pick(wood, x, top, z, 1));
                else if (v < 0.6 && !planks.isEmpty()) p.place(x, top, z, p.pick(planks, x, top, z, 3));
                p.root(x, base - 2, z, p.pick(root, x, base - 2, z, 4), 10);
            }
        }

        for (int b = 0; b < nBranch; b++)
        {
            Direction d = Direction.from2DDataValue(bDir[b]);
            int sx = d.getStepX() > 0 ? site.x + size : d.getStepX() < 0 ? site.x - 1 : site.x + bOff[b];
            int sz = d.getStepZ() > 0 ? site.z + size : d.getStepZ() < 0 ? site.z - 1 : site.z + bOff[b];
            int by = base + bLevel[b];
            for (int k = 0; k < bLen[b]; k++)
            {
                int x = sx + d.getStepX() * k, z = sz + d.getStepZ() * k, y = by + (bRise[b] && k >= 2 ? 1 : 0);
                if (!p.inReach(x, z)) continue;
                p.place(x, y, z, axis(p.pick(wood, x, y, z, 1), d.getAxis()));
            }
            if (bFrond[b] && !frond.isEmpty())
            {
                int k = bLen[b] - 1;
                int x = sx + d.getStepX() * k, z = sz + d.getStepZ() * k, y = by + (bRise[b] && k >= 2 ? 1 : 0);
                if (p.inReach(x + d.getStepX(), z + d.getStepZ())) p.place(x + d.getStepX(), y, z + d.getStepZ(), p.pick(frond, x, y, z, 5));
                if (p.inReach(x, z)) p.place(x, y + 1, z, p.pick(frond, x, y + 1, z, 5));
            }
        }

        for (int k = 0; k < nRoot; k++)
        {
            Direction d = Direction.from2DDataValue(rDir[k]);
            int ex = d.getStepX() > 0 ? site.x + size - 1 : d.getStepX() < 0 ? site.x : site.x + rOff[k];
            int ez = d.getStepZ() > 0 ? site.z + size - 1 : d.getStepZ() < 0 ? site.z : site.z + rOff[k];
            int x = ex + d.getStepX() * rDist[k], z = ez + d.getStepZ() * rDist[k];
            if (!p.inReach(x, z)) continue;
            int y = site.baseAt(x + 0.5, z + 0.5);
            p.place(x, y, z, axis(p.pick(root, x, y, z, 4), d.getAxis()));
            if (rDist[k] == 1 && rFlare[k]) p.place(x, y + 1, z, axis(p.pick(root, x, y + 1, z, 4), d.getAxis()));
        }
    }

    private static BlockState axis(BlockState state, Direction.Axis axis)
    {
        return state.hasProperty(BlockStateProperties.AXIS) ? state.setValue(BlockStateProperties.AXIS, axis) : state;
    }
}
