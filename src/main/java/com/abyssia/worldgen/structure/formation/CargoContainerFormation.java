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
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Sunken shipping containers: one to three side by side on the seabed, some with another laid on top (sometimes crossways),
 * each a 4x4 metal box (corner posts and rails of beam, panel walls with the odd hole, one or both doors torn open) with
 * barrels and crates left inside and spilled around. Bottoms may sit a block into the seabed.
 */
public record CargoContainerFormation(Span stack, Span length, float topChance, float openChance, float holeChance, float cargoChance,
                                      Span spill, Mix wall, Mix frame, Mix cargo, Mix root) implements Formation
{
    public static final String TYPE = "cargo_container";
    private static final int WIDTH = 4, HEIGHT = 4;
    public static final Codec<CargoContainerFormation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.optionalFieldOf("stack", Span.of(1, 3)).forGetter(CargoContainerFormation::stack),
            Span.CODEC.optionalFieldOf("length", Span.of(7, 9)).forGetter(CargoContainerFormation::length),
            Codec.floatRange(0, 1).optionalFieldOf("top_chance", 0.45f).forGetter(CargoContainerFormation::topChance),
            Codec.floatRange(0, 1).optionalFieldOf("open_chance", 0.5f).forGetter(CargoContainerFormation::openChance),
            Codec.floatRange(0, 1).optionalFieldOf("hole_chance", 0.1f).forGetter(CargoContainerFormation::holeChance),
            Codec.floatRange(0, 1).optionalFieldOf("cargo_chance", 0.45f).forGetter(CargoContainerFormation::cargoChance),
            Span.CODEC.optionalFieldOf("spill", Span.of(2, 6)).forGetter(CargoContainerFormation::spill),
            Mix.CODEC.fieldOf("wall").forGetter(CargoContainerFormation::wall),
            Mix.CODEC.fieldOf("frame").forGetter(CargoContainerFormation::frame),
            Mix.CODEC.fieldOf("cargo").forGetter(CargoContainerFormation::cargo),
            Mix.CODEC.fieldOf("root").forGetter(CargoContainerFormation::root)
    ).apply(i, CargoContainerFormation::new));

    /** One container: centre, length (along x when alongX), which ends are torn open, how far it sits in the seabed, and the box under it (-1 on the ground). */
    private record Box(int cx, int cz, int len, boolean alongX, boolean openA, boolean openB, int sink, int under) {}

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
        boolean alongX = random.nextBoolean();
        int ground = Mth.clamp(stack.sampleInt(random), 1, 3);
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < ground; i++)
        {
            int len = Mth.clamp(length.sampleInt(random), 6, 10);
            int along = random.nextInt(5) - 2;
            int lat = Math.round((i - (ground - 1) / 2f) * (WIDTH + 1)) + random.nextInt(3) - 1;
            boolean openA = random.nextFloat() < openChance, openB = random.nextFloat() < openChance;
            int sink = random.nextFloat() < 0.4f ? 1 : 0;
            boxes.add(new Box(site.x + (alongX ? along : lat), site.z + (alongX ? lat : along), len, alongX, openA, openB, sink, -1));
        }
        for (int i = 0; i < ground; i++)
        {
            if (random.nextFloat() >= topChance) continue;
            boolean cross = random.nextFloat() < 0.35f;
            int len = Mth.clamp(length.sampleInt(random), 6, 10);
            Box under = boxes.get(i);
            boxes.add(new Box(under.cx() + random.nextInt(3) - 1, under.cz() + random.nextInt(3) - 1, len, cross != alongX,
                    random.nextFloat() < openChance, random.nextFloat() < openChance, 0, i));
        }
        int nSpill = Math.max(0, spill.sampleInt(random));
        double[] spillAngle = new double[nSpill], spillDist = new double[nSpill];
        for (int k = 0; k < nSpill; k++)
        {
            spillAngle[k] = random.nextDouble() * Math.PI * 2;
            spillDist[k] = 5 + random.nextDouble() * Math.max(1, site.radius() - 6);
        }

        int[] y0 = new int[boxes.size()];
        for (int i = 0; i < boxes.size(); i++)
        {
            Box b = boxes.get(i);
            if (b.under() >= 0)
            {
                y0[i] = y0[b.under()] + HEIGHT;
                continue;
            }
            int floor = Integer.MAX_VALUE;
            for (int u : new int[]{0, b.len() / 2, b.len() - 1})
                for (int v : new int[]{0, WIDTH - 1})
                    floor = Math.min(floor, Mth.floor(p.smoothBase(worldX(b, u, v), worldZ(b, u, v))));
            y0[i] = floor - b.sink();
        }

        for (int i = 0; i < boxes.size(); i++) paintBox(p, boxes.get(i), y0[i]);

        for (int k = 0; k < nSpill; k++)
        {
            int x = site.x + (int) Math.round(Math.cos(spillAngle[k]) * spillDist[k]);
            int z = site.z + (int) Math.round(Math.sin(spillAngle[k]) * spillDist[k]);
            if (!p.inReach(x, z)) continue;
            int y = Mth.floor(p.smoothBase(x, z));
            p.place(x, y, z, Painter.wet(p.pick(cargo, x, y, z, 5)));
            if (p.hash(x, y, z, 24) < 0.4) p.place(x, y + 1, z, Painter.wet(p.pick(cargo, x, y + 1, z, 5)));
        }
    }

    private static int worldX(Box b, int u, int v)
    {
        return b.alongX() ? b.cx() + u - b.len() / 2 : b.cx() + v - WIDTH / 2;
    }

    private static int worldZ(Box b, int u, int v)
    {
        return b.alongX() ? b.cz() + v - WIDTH / 2 : b.cz() + u - b.len() / 2;
    }

    private void paintBox(Painter p, Box b, int y0)
    {
        if (!p.touches(b.cx(), b.cz(), b.len() * 0.75 + 2)) return;
        Direction.Axis uAxis = b.alongX() ? Direction.Axis.X : Direction.Axis.Z;
        Direction.Axis vAxis = b.alongX() ? Direction.Axis.Z : Direction.Axis.X;
        for (int u = 0; u < b.len(); u++)
        {
            for (int v = 0; v < WIDTH; v++)
            {
                int x = worldX(b, u, v), z = worldZ(b, u, v);
                if (!p.inReach(x, z)) continue;
                boolean eu = u == 0 || u == b.len() - 1, ev = v == 0 || v == WIDTH - 1;
                for (int w = 0; w < HEIGHT; w++)
                {
                    int y = y0 + w;
                    boolean ew = w == 0 || w == HEIGHT - 1;
                    int edges = (eu ? 1 : 0) + (ev ? 1 : 0) + (ew ? 1 : 0);
                    BlockState state;
                    if (edges >= 2)
                    {
                        Direction.Axis axis = !eu ? uAxis : !ev ? vAxis : Direction.Axis.Y;
                        state = Painter.axis(p.pick(frame, x, y, z, 2), axis);
                    }
                    else if (edges == 1)
                    {
                        boolean door = (u == 0 && b.openA() || u == b.len() - 1 && b.openB()) && eu;
                        if (door || p.hash(x, y, z, 23) < holeChance) continue;
                        state = p.pick(wall, x, y, z, 1);
                    }
                    else
                    {
                        // Interior: cargo stacked from the floor up, thinning with height.
                        if (p.hash(x, y, z, 31) >= cargoChance * (w == 1 ? 1.0 : 0.4)) continue;
                        state = p.pick(cargo, x, y, z, 3);
                    }
                    p.place(x, y, z, Painter.wet(state));
                }
                p.root(x, y0 - 1, z, p.pick(root, x, y0 - 1, z, 4), 5);
            }
        }
    }
}
