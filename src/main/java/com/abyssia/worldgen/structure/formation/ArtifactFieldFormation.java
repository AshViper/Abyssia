package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Abandoned human-made things strewn over a patch of seabed: pipe runs (lying or standing, sometimes broken), the
 * posts and crossbar of a collapsed gantry, grated platforms tipped on their legs, and little heaps of crates and barrels.
 */
public record ArtifactFieldFormation(Span pieces, Mix frame, Mix deck, Mix pipe, Mix crate, Mix root) implements Formation
{
    public static final String TYPE = "artifact_field";
    public static final Codec<ArtifactFieldFormation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.optionalFieldOf("pieces", Span.of(4, 8)).forGetter(ArtifactFieldFormation::pieces),
            Mix.CODEC.fieldOf("frame").forGetter(ArtifactFieldFormation::frame),
            Mix.CODEC.fieldOf("deck").forGetter(ArtifactFieldFormation::deck),
            Mix.CODEC.fieldOf("pipe").forGetter(ArtifactFieldFormation::pipe),
            Mix.CODEC.fieldOf("crate").forGetter(ArtifactFieldFormation::crate),
            Mix.CODEC.fieldOf("root").forGetter(ArtifactFieldFormation::root)
    ).apply(i, ArtifactFieldFormation::new));

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        if (p.isEmpty()) return;
        // Every piece draws the same numbers whatever its kind, so every chunk agrees on the whole layout.
        RandomSource random = site.random();
        int n = Math.max(1, pieces.sampleInt(random));
        for (int i = 0; i < n; i++)
        {
            int kind = random.nextInt(4);
            double angle = random.nextDouble() * Math.PI * 2, dist = Math.sqrt(random.nextDouble()) * Math.max(1, site.radius() - 6);
            boolean alongX = random.nextBoolean();
            int size = 3 + random.nextInt(5), extra = random.nextInt(4);
            int cx = site.x + (int) Math.round(Math.cos(angle) * dist), cz = site.z + (int) Math.round(Math.sin(angle) * dist);
            if (!p.touches(cx, cz, 9)) continue;
            switch (kind)
            {
                case 0 -> pipeRun(p, cx, cz, alongX, size + 1, extra);
                case 1 -> gantry(p, cx, cz, alongX, Math.min(size, 6), 3 + extra);
                case 2 -> platform(p, cx, cz, alongX, Math.min(size + 1, 6), 3 + (extra & 1), extra - 1);
                default -> crates(p, cx, cz, 3 + extra);
            }
        }
    }

    private int floor(Painter p, int x, int z)
    {
        return Mth.floor(p.smoothBase(x, z));
    }

    /** A pipe lying on the seabed (or standing, for extra 3), each block joined to its neighbours; extra 0 breaks it in the middle. */
    private void pipeRun(Painter p, int cx, int cz, boolean alongX, int len, int extra)
    {
        boolean standing = extra == 3;
        Direction pos = standing ? Direction.UP : alongX ? Direction.EAST : Direction.SOUTH, neg = pos.getOpposite();
        int gap = extra == 0 ? len / 2 : -1;
        for (int j = 0; j < len; j++)
        {
            if (j == gap) continue;
            int x = cx + (standing ? 0 : alongX ? j : 0), z = cz + (standing ? 0 : alongX ? 0 : j);
            int y = standing ? floor(p, cx, cz) + j : floor(p, x, z);
            if (standing && j >= len - 1 - (int) (p.hash(cx, cz, 0, 51) * 2)) continue;
            BlockState state = p.pick(pipe, x, y, z, 1);
            if (j > 0 && j - 1 != gap) state = join(state, neg);
            if (j < len - 1 && j + 1 != gap) state = join(state, pos);
            p.place(x, y, z, Painter.wet(state));
            if (!standing || j == 0) p.root(x, y - 1, z, p.pick(root, x, y - 1, z, 4), 3);
        }
    }

    private static BlockState join(BlockState state, Direction dir)
    {
        var property = PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
        return state.hasProperty(property) ? state.setValue(property, true) : state;
    }

    /** Two posts and a crossbar, the far post shorter and the bar broken off short for the taller extras. */
    private void gantry(Painter p, int cx, int cz, boolean alongX, int span, int height)
    {
        Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
        int ax = alongX ? span : 0, az = alongX ? 0 : span;
        int hb = Math.max(2, height - 2);
        int ya = floor(p, cx, cz), yb = floor(p, cx + ax, cz + az);
        for (int k = 0; k < height; k++) p.place(cx, ya + k, cz, Painter.wet(Painter.axis(p.pick(frame, cx, ya + k, cz, 2), Direction.Axis.Y)));
        for (int k = 0; k < hb; k++) p.place(cx + ax, yb + k, cz + az, Painter.wet(Painter.axis(p.pick(frame, cx + ax, yb + k, cz + az, 2), Direction.Axis.Y)));
        int reach = height >= 5 ? span * 2 / 3 : span;
        for (int j = 1; j <= reach; j++)
        {
            int x = cx + (alongX ? j : 0), z = cz + (alongX ? 0 : j), y = ya + height - 1;
            p.place(x, y, z, Painter.wet(Painter.axis(p.pick(frame, x, y, z, 2), axis)));
        }
        p.root(cx, ya - 1, cz, p.pick(root, cx, ya - 1, cz, 4), 3);
        p.root(cx + ax, yb - 1, cz + az, p.pick(root, cx + ax, yb - 1, cz + az, 4), 3);
    }

    /** A grated deck on four short legs, tipped by {@code tilt} blocks across its length so one end has sunk. */
    private void platform(Painter p, int cx, int cz, boolean alongX, int w, int d, int tilt)
    {
        int base = floor(p, cx, cz);
        for (int i = 0; i < w; i++)
        {
            int dy = Math.round(tilt * i / (float) Math.max(1, w - 1));
            for (int j = 0; j < d; j++)
            {
                int x = cx + (alongX ? i : j), z = cz + (alongX ? j : i), y = base + 2 + dy;
                p.place(x, y, z, Painter.wet(p.pick(deck, x, y, z, 1)));
                boolean leg = (i == 0 || i == w - 1) && (j == 0 || j == d - 1);
                if (leg)
                    for (int k = base; k < y; k++) p.place(x, k, z, Painter.wet(Painter.axis(p.pick(frame, x, k, z, 2), Direction.Axis.Y)));
            }
        }
    }

    /** A heap of crates, some stacked two high. */
    private void crates(Painter p, int cx, int cz, int count)
    {
        for (int c = 0; c < count; c++)
        {
            int x = cx + (int) (p.hash(cx, c, cz, 41) * 3) - 1, z = cz + (int) (p.hash(cx, c, cz, 42) * 3) - 1;
            int y = floor(p, x, z);
            p.place(x, y, z, Painter.wet(p.pick(crate, x, y, z, 5)));
            if (p.hash(x, c, z, 43) < 0.35) p.place(x, y + 1, z, Painter.wet(p.pick(crate, x, y + 1, z, 5)));
        }
    }
}
