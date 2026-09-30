package com.abyssia.worldgen.cave;

import com.abyssia.Config;
import com.abyssia.block.CaveMossBlock;
import com.abyssia.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Cave flora. Plants are sorted by where they root (floor, wall, ceiling), each with its own species. How much grows
 * follows a vegetation-zone noise (sparse, normal, dense, very dense stretches) times the environment's density;
 * within a zone, plants grow in clumps around patch centres, and a slow species noise makes each clump mostly one
 * species, so caves show natural stands rather than random single blocks. A small share are luminous: faintly
 * (8-25%) or strongly (1-5%), set per environment.
 * <p>
 * In large caverns the forest becomes three-dimensional: giant kelp and ancient plants rise from the floor toward the
 * roof (in a Giant Kelp Cavern almost touching it), mid-height kelp and tube plants fill between, and vines, roots
 * and hanging kelp come down from above.
 */
final class CaveVegetationGenerator
{
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private CaveVegetationGenerator() {}

    /** Giant plants of cavern forests: the canopy claims its ground before the undergrowth. */
    static void giants(CaveChunk ctx)
    {
        CaveNoises noises = ctx.noises;
        double vegetation = Config.CAVE_VEGETATION.get();
        if (vegetation <= 0) return;
        for (int lz = 0; lz < 16; lz++)
        {
            for (int lx = 0; lx < 16; lx++)
            {
                for (int y = ctx.yMin + 1; y < ctx.yMax; y++)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz)) continue;
                    CaveSpace space = ctx.space(lx, y, lz);
                    if (space == null || !space.isCavern()) continue;
                    // Caverns grow their giants as clumped forests in the cavern pass instead.
                    if (space.cavern != null && space.cavern.template.forest.density() > 0) continue;
                    CaveEnvironment env = space.environment;
                    float giant = env.flora.giantDensity();
                    if (giant <= 0 || env.giantPlants.isEmpty() || !ctx.sturdy(lx, y - 1, lz, Direction.UP)) continue;
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    boolean forest = space.landmark == CaveLandmark.GIANT_KELP_CAVERN || space.landmark == CaveLandmark.DEEP_CAVE_FOREST;
                    double chance = giant * noises.vegetationZone(x, y, z) * vegetation * (forest ? 2.5 : 1.0);
                    if (noises.hash(x, y, z, 31) >= chance) continue;
                    int free = CaveDecorationGenerator.free(ctx, lx, y, lz, Direction.UP, 255);
                    if (free < 4) continue;
                    CaveEnvironment.PlantEntry entry = env.giantPlants.pick(noises.species(x + 97, y, z));
                    int height;
                    if (space.landmark == CaveLandmark.GIANT_KELP_CAVERN && entry.state().is(ModBlocks.GIANT_CAVE_KELP.get()))
                    {
                        height = free - 1 - Mth.floor(noises.hash(x, y, z, 32) * 3);
                    }
                    else
                    {
                        height = Math.min(free - 1, entry.minHeight() + Mth.floor(noises.hash(x, y, z, 32) * (entry.maxHeight() - entry.minHeight() + 1)));
                    }
                    if (height < 2) continue;
                    CavePlacer.plant(ctx, lx, y, lz, new CaveEnvironment.PlantEntry(entry.state(), height, height), Direction.DOWN, 0, height);
                }
            }
        }
    }

    /** Floor, wall and ceiling plants, luminous species and moss films. */
    static void grow(CaveChunk ctx)
    {
        CaveNoises noises = ctx.noises;
        double vegetation = Config.CAVE_VEGETATION.get(), glowScale = Config.CAVE_GLOW.get();
        if (vegetation <= 0) return;
        CaveMossBlock moss = (CaveMossBlock) ModBlocks.CAVE_MOSS.get();
        for (int y = ctx.yMin + 1; y < ctx.yMax; y++)
        {
            for (int lz = 0; lz < 16; lz++)
            {
                for (int lx = 0; lx < 16; lx++)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz)) continue;
                    CaveSpace space = ctx.space(lx, y, lz);
                    if (space == null) continue;
                    CaveEnvironment env = space.environment;
                    CaveEnvironment.Flora flora = env.flora;
                    if (flora.density() <= 0 && flora.moss() <= 0) continue;
                    Direction support = support(ctx, lx, y, lz);
                    if (support == null) continue;
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    double zone = noises.vegetationZone(x, y, z);
                    double patch = noises.patch(x, y, z, 0);
                    double orientation = support == Direction.DOWN ? 0.6 : support == Direction.UP ? 0.4 : 0.25;
                    double chance = flora.density() * zone * vegetation * (0.08 + 0.92 * patch) * orientation * space.vegetationBoost;
                    // In a cavern: by ecology patch, entrance-to-deep gradient and height.
                    if (space.cavern != null) chance *= space.cavern.vegetationFactor(x + 0.5, y + 0.5, z + 0.5);
                    if (noises.hash(x, y, z, 21) < chance)
                    {
                        Palette<CaveEnvironment.PlantEntry> normal = support == Direction.DOWN ? env.floorPlants : support == Direction.UP ? env.ceilingPlants : env.wallPlants;
                        double tier = noises.hash(x, y, z, 22);
                        double bright = flora.brightRatio() * glowScale * space.glowScale, glow = flora.glowRatio() * glowScale * space.glowScale;
                        Palette<CaveEnvironment.PlantEntry> palette = tier < bright && !env.brightPlants.isEmpty() ? env.brightPlants
                                : tier < bright + glow && !env.glowPlants.isEmpty() ? env.glowPlants : normal;
                        double species = Mth.clamp(noises.species(x, y, z) + (noises.hash(x, y, z, 24) - 0.5) * 0.15, 0.0, 0.999);
                        double height = noises.hash(x, y, z, 25);
                        int cap = space.isCavern() ? 40 : 10;
                        boolean placed = !palette.isEmpty() && CavePlacer.plant(ctx, lx, y, lz, palette.pick(species), support, height, cap);
                        if (!placed && palette != normal && !normal.isEmpty()) placed = CavePlacer.plant(ctx, lx, y, lz, normal.pick(species), support, height, cap);
                        if (placed) continue;
                    }
                    // Moss films on bare rock, in their own patches.
                    if (flora.moss() > 0 && noises.hash(x, y, z, 23) < flora.moss() * vegetation * noises.patch(x, y, z, 44.4))
                    {
                        CavePlacer.moss(ctx, lx, y, lz, moss);
                    }
                }
            }
        }
    }

    /** What an open block could root on: floor first, then ceiling, then a wall inside this chunk. */
    private static Direction support(CaveChunk ctx, int lx, int y, int lz)
    {
        if (ctx.sturdy(lx, y - 1, lz, Direction.UP)) return Direction.DOWN;
        if (ctx.sturdy(lx, y + 1, lz, Direction.DOWN)) return Direction.UP;
        for (Direction d : HORIZONTAL)
        {
            if (ctx.sturdy(lx + d.getStepX(), y, lz + d.getStepZ(), d.getOpposite())) return d;
        }
        return null;
    }
}
