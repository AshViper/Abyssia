package com.abyssia.worldgen.structure.formation;

import com.abyssia.worldgen.structure.Formation;
import com.abyssia.worldgen.structure.Mix;
import com.abyssia.worldgen.structure.Painter;
import com.abyssia.worldgen.structure.Site;
import com.abyssia.worldgen.structure.Span;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WRK01: a small stranded ship hull. An open shell (floor plate, walls with a jagged broken top, closed narrow bow and
 * stern, a partial deck over the stern) of panels with holes, gratings and vertical beams, rolled and pitched, sunk part
 * way into the seabed and heaped with silt, and exactly one wreck core lying exposed on the hull floor (the lidar
 * scanner analyses it, see WreckProgress). The whole layout is drawn up front; each chunk paints its own columns.
 */
public record WreckFormation(Span length, Span width, Span height, float pitch, float roll, Span sink, float holes, float grating,
                             Mix hull, Mix grates, Mix beams, Mix silt, Mix core) implements Formation
{
    public static final String TYPE = "wreck";
    public static final Codec<WreckFormation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.optionalFieldOf("length", Span.of(8, 14)).forGetter(WreckFormation::length),
            Span.CODEC.optionalFieldOf("width", Span.of(3, 5)).forGetter(WreckFormation::width),
            Span.CODEC.optionalFieldOf("height", Span.of(2, 4)).forGetter(WreckFormation::height),
            Codec.floatRange(0, 0.6f).optionalFieldOf("pitch", 0.2f).forGetter(WreckFormation::pitch),
            Codec.floatRange(0, 0.6f).optionalFieldOf("roll", 0.15f).forGetter(WreckFormation::roll),
            Span.CODEC.optionalFieldOf("sink", Span.of(0, 2)).forGetter(WreckFormation::sink),
            Codec.floatRange(0, 1).optionalFieldOf("holes", 0.22f).forGetter(WreckFormation::holes),
            Codec.floatRange(0, 1).optionalFieldOf("grating", 0.15f).forGetter(WreckFormation::grating),
            Mix.CODEC.fieldOf("hull").forGetter(WreckFormation::hull),
            Mix.CODEC.optionalFieldOf("gratings", Mix.EMPTY).forGetter(WreckFormation::grates),
            Mix.CODEC.optionalFieldOf("beams", Mix.EMPTY).forGetter(WreckFormation::beams),
            Mix.CODEC.optionalFieldOf("silt", Mix.EMPTY).forGetter(WreckFormation::silt),
            Mix.CODEC.fieldOf("core").forGetter(WreckFormation::core)
    ).apply(i, WreckFormation::new));

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public void paint(Site site, Painter p)
    {
        if (p.isEmpty() || core.isEmpty() || hull.isEmpty()) return;
        // The whole layout is drawn up front, in a fixed order, so every chunk agrees on it.
        RandomSource random = site.random();
        float len = Math.max(6, length.sample(random)), wid = Math.max(3, width.sample(random));
        int hgt = Math.max(2, height.sampleInt(random));
        double yaw = random.nextDouble() * Math.PI * 2;
        double slopeU = (random.nextBoolean() ? 1 : -1) * pitch * (0.5 + 0.5 * random.nextDouble());
        double slopeV = (random.nextBoolean() ? 1 : -1) * roll * (0.5 + 0.5 * random.nextDouble());
        int sunk = Math.max(0, sink.sampleInt(random));
        double coreU = (random.nextDouble() - 0.5) * len * 0.5;

        double ex = site.x + 0.5, ez = site.z + 0.5, cos = Math.cos(yaw), sin = Math.sin(yaw);
        double halfL = len / 2.0, halfW = wid / 2.0;
        int base = site.baseAt(ex, ez) - sunk;
        int rise = (int) Math.ceil(Math.abs(slopeU) * halfL + Math.abs(slopeV) * halfW) + 1;
        if (site.clampHeight(base + rise, hgt) < hgt) return;
        double reach = halfL + halfW + 4;
        int coreX = Mth.floor(ex + coreU * cos), coreZ = Mth.floor(ez + coreU * sin);

        for (int x = p.fromX(ex, reach); x <= p.toX(ex, reach); x++)
        {
            for (int z = p.fromZ(ez, reach); z <= p.toZ(ez, reach); z++)
            {
                if (!p.inReach(x, z)) continue;
                double dx = x + 0.5 - ex, dz = z + 0.5 - ez;
                double u = dx * cos + dz * sin, v = -dx * sin + dz * cos;
                double along = u / halfL;
                // The bow (+u) narrows to a point, the stern is blunt.
                double hw = halfW * (1 - 0.55 * Math.pow(Math.max(0, along), 2) - 0.15 * Math.pow(Math.max(0, -along), 2));
                double outside = Math.max(Math.abs(v) - hw, Math.abs(u) - halfL);
                int surface = p.floor(x, z);
                if (outside < 0.5 && hw >= 0.6)
                {
                    int yb = base + (int) Math.round(u * slopeU + v * slopeV);
                    boolean coreColumn = x == coreX && z == coreZ;
                    double edge = hw - Math.abs(v);
                    boolean wall = edge < 1.0 || Math.abs(u) > halfL - 1.0;
                    int wallTop = (int) Math.round(hgt * (0.45 + 0.55 * (0.5 + 0.5 * p.noise(x * 0.3, z * 0.3))));
                    boolean rib = wall && Math.floorMod(Mth.floor(u + halfL), 4) == 0;
                    boolean deck = u < -halfL / 3 && Math.abs(v) < hw - 0.5;
                    for (int t = 0; t <= hgt; t++)
                    {
                        int y = yb + t;
                        if (y < surface) continue; // buried in the seabed
                        if (coreColumn && t >= 1) continue; // keep the core's column open above it
                        boolean solid = t == 0 || (wall && t <= wallTop) || (deck && t == hgt);
                        if (!solid) continue;
                        double r = p.hash(x, y, z, 21);
                        if (t > 0 && r < holes) continue;
                        if (t == 0 && !coreColumn && r < holes * 0.4) continue;
                        BlockState state;
                        if (rib && t > 0 && !beams.isEmpty()) state = p.pick(beams, x, y, z, 3);
                        else if (!grates.isEmpty() && r < holes + grating) state = p.pick(grates, x, y, z, 2);
                        else state = p.pick(hull, x, y, z, 1);
                        p.place(x, y, z, state);
                    }
                    if (coreColumn)
                    {
                        // On the hull floor, or on the silt/seabed when the floor is buried; open water above.
                        int coreY = Math.max(yb + 1, surface);
                        p.set(x, coreY, z, core.pick(0));
                    }
                }
                else if (!silt.isEmpty() && outside < 3.0)
                {
                    // Silt drifted against the hull, thicker on the side the noise favours.
                    double n = 0.5 + 0.5 * p.noise(x * 0.25, z * 0.25);
                    int k = (int) Math.round(2.2 * (1 - outside / 3.0) * n);
                    for (int i = 0; i < k; i++) p.fill(x, surface + i, z, p.pick(silt, x, surface + i, z, 5));
                }
            }
        }
    }
}
