package com.abyssia.worldgen.cave;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * AB03: the hall pass, in chunks touching a hall (see {@link CaveShape.Hall}): the glowing niche in its far wall, the extra
 * (luminous) plants its look asks for on terrace ledges, walls and roof, and its light columns. Everything is decided per
 * block from world-space hashes and noise, and a light column stays in its own block column, so chunks agree.
 */
final class HallDecorator
{
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private HallDecorator() {}

    /** The halls among the caverns touching this chunk. */
    static List<Cavern> halls(List<Cavern> caverns)
    {
        List<Cavern> halls = new ArrayList<>(1);
        for (Cavern c : caverns)
        {
            if (c.hall != null) halls.add(c);
        }
        return halls;
    }

    /** The far-wall niche: rock around it, where it faces the water, turns to the look's bright window blocks. */
    static void windows(CaveChunk ctx, List<Cavern> halls)
    {
        for (Cavern cavern : halls)
        {
            Cavern.Window w = cavern.window();
            Palette<BlockState> palette = cavern.space.environment.hallWindow;
            if (w == null || palette.isEmpty()) continue;
            double reach = w.radius() * 1.3 + 2;
            int x0 = Math.max(ctx.x0, Mth.floor(w.x() - reach)), x1 = Math.min(ctx.x0 + 15, Mth.ceil(w.x() + reach));
            int z0 = Math.max(ctx.z0, Mth.floor(w.z() - reach)), z1 = Math.min(ctx.z0 + 15, Mth.ceil(w.z() + reach));
            int y0 = Math.max(ctx.yMin + 1, Mth.floor(w.y() - reach * 1.3)), y1 = Math.min(ctx.yMax - 1, Mth.ceil(w.y() + reach * 1.3));
            for (int y = y0; y <= y1; y++)
            {
                for (int z = z0; z <= z1; z++)
                {
                    for (int x = x0; x <= x1; x++)
                    {
                        double d = Math.sqrt(Mth.square(x + 0.5 - w.x()) + Mth.square((y + 0.5 - w.y()) / 1.3) + Mth.square(z + 0.5 - w.z()));
                        if (d > reach) continue;
                        int lx = x - ctx.x0, lz = z - ctx.z0;
                        BlockState state = ctx.get(lx, y, lz);
                        if (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()) continue;
                        if (!facesWater(ctx, lx, y, lz) || ctx.noises.hash(x, y, z, 611) >= 0.6) continue;
                        ctx.set(lx, y, lz, palette.pick(ctx.noises.hash(x, y, z, 612)));
                    }
                }
            }
        }
    }

    private static boolean facesWater(CaveChunk ctx, int lx, int y, int lz)
    {
        for (Direction d : Direction.values())
        {
            int nx = lx + d.getStepX(), ny = y + d.getStepY(), nz = lz + d.getStepZ();
            if (ctx.inChunk(nx, nz) ? ctx.water(nx, ny, nz) : ctx.carvedHere(nx, ny, nz)) return true;
        }
        return false;
    }

    /** Extra plants of the hall look on floors (terrace ledges), walls (terrace risers) and the roof, in patches. */
    static void life(CaveChunk ctx)
    {
        CaveNoises noises = ctx.noises;
        for (int y = ctx.yMin + 1; y < ctx.yMax; y++)
        {
            for (int lz = 0; lz < 16; lz++)
            {
                for (int lx = 0; lx < 16; lx++)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz)) continue;
                    CaveSpace space = ctx.space(lx, y, lz);
                    if (space == null || space.hall == null) continue;
                    CaveEnvironment env = space.environment;
                    CaveEnvironment.HallLife life = env.hall.life();
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    double patch = 0.15 + 1.7 * noises.patch(x, y, z, 31.7);
                    if (!env.hallFloorPlants.isEmpty() && ctx.sturdy(lx, y - 1, lz, Direction.UP))
                    {
                        if (noises.hash(x, y, z, 601) < life.floorDensity() * patch
                                && CavePlacer.plant(ctx, lx, y, lz, env.hallFloorPlants.pick(noises.species(x + 7, y, z)), Direction.DOWN, noises.hash(x, y, z, 602), 12)) continue;
                    }
                    else if (!env.hallCeilingPlants.isEmpty() && ctx.sturdy(lx, y + 1, lz, Direction.DOWN))
                    {
                        if (noises.hash(x, y, z, 603) < life.ceilingDensity() * patch
                                && CavePlacer.plant(ctx, lx, y, lz, env.hallCeilingPlants.pick(noises.species(x + 11, y, z)), Direction.UP, noises.hash(x, y, z, 604), 16)) continue;
                    }
                    if (env.hallWallPlants.isEmpty() || noises.hash(x, y, z, 605) >= life.wallDensity() * patch) continue;
                    int start = (int) (noises.hash(x, y, z, 606) * 4) & 3;
                    for (int i = 0; i < 4; i++)
                    {
                        Direction d = HORIZONTAL[(start + i) & 3];
                        if (!ctx.sturdy(lx + d.getStepX(), y, lz + d.getStepZ(), d.getOpposite())) continue;
                        if (CavePlacer.plant(ctx, lx, y, lz, env.hallWallPlants.pick(noises.species(x + 13, y, z)), d, noises.hash(x, y, z, 607), 4)) break;
                    }
                }
            }
        }
    }

    /** Light columns: a tall luminous plant rising from the floor toward the roof, in its own block column. */
    static void beacons(CaveChunk ctx, List<Cavern> halls)
    {
        for (Cavern cavern : halls)
        {
            var beacon = cavern.space.environment.hall.form().beacon();
            if (beacon.isEmpty()) continue;
            for (Cavern.Beacon b : cavern.beacons())
            {
                int lx = b.x() - ctx.x0, lz = b.z() - ctx.z0;
                if (!ctx.inChunk(lx, lz)) continue;
                int y = (int) cavern.hall.floor(b.x() + 0.5, b.z() + 0.5);
                // Formations may stand there: climb to the first open block over the floor.
                for (int k = 0; k < 24 && y < ctx.yMax && !ctx.water(lx, y, lz); k++) y++;
                if (!ctx.water(lx, y, lz) || !ctx.sturdy(lx, y - 1, lz, Direction.UP)) continue;
                int free = CaveDecorationGenerator.free(ctx, lx, y, lz, Direction.UP, 200);
                int height = Math.min(free - 3, Mth.floor(free * Mth.lerp(ctx.noises.hash(b.x(), y, b.z(), 621), 0.45, 0.85)));
                if (height < 6) continue;
                CavePlacer.plant(ctx, lx, y, lz, new CaveEnvironment.PlantEntry(beacon.get().state(), height, height), Direction.DOWN, 0, height);
            }
        }
    }
}
