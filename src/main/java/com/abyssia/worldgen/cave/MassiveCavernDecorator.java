package com.abyssia.worldgen.cave;

import com.abyssia.block.CaveMossBlock;
import com.abyssia.block.LeaningPlantBlock;
import com.abyssia.block.StackingPlantBlock;
import com.abyssia.registry.ModBlocks;
import com.abyssia.registry.ModTags;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The massive cavern decoration pass: runs only in chunks that touch a planned cavern, after the shared cave
 * passes have shaped and coated it, and dresses the cavern's volume by zone and ecology patch:
 * <ul>
 *     <li>underground water: the shimmering surface of brine lakes</li>
 *     <li>giant vegetation: clumped giant kelp forests broken by glades, some stalks leaning; giant plants in lakes
 *     reaching for the surface</li>
 *     <li>hanging vegetation: curtains of roots and vines over the kelp clumps (floor to roof in one vertical sweep),
 *     some reaching the floor, and over open ground</li>
 *     <li>medium vegetation: cave gardens, deep-sea groves, lake shores (dense) and lake beds (sparse), floating
 *     plants under the lake surface</li>
 *     <li>shelf tops (sediment, plants, rubble, ore, crystal) and a scatter of glowing moss on the roof</li>
 * </ul>
 * Only open water is ever used, everything is decided per block from world-space noise and hashes, and leaning
 * stalks never leave their chunk, so the pass is seamless and bounded by the chunk's volume.
 */
final class MassiveCavernDecorator
{
    private MassiveCavernDecorator() {}

    /** The caverns touching this chunk (the cavern detection step); empty for all other chunks. */
    static List<Cavern> caverns(CaveChunk ctx)
    {
        List<Cavern> list = new ArrayList<>(2);
        for (CaveSpace space : ctx.spaces)
        {
            if (space.cavern != null && !list.contains(space.cavern)) list.add(space.cavern);
        }
        return list;
    }

    /** The cavern a block belongs to: its owner's, or (lake basins, wall pockets) the cavern around it. */
    @Nullable
    private static Cavern cavernAt(CaveChunk ctx, List<Cavern> caverns, int lx, int y, int lz)
    {
        CaveSpace space = ctx.space(lx, y, lz);
        if (space != null && space.cavern != null) return space.cavern;
        double x = ctx.x0 + lx + 0.5, z = ctx.z0 + lz + 0.5;
        for (Cavern c : caverns)
        {
            if (c.footprint(x, z) < 1.1 && y > c.floor0 - 20 && y < c.ceiling0 + 5) return c;
        }
        return null;
    }

    // ---------------------------------------------------------------- 10. underground water

    static void water(CaveChunk ctx, List<Cavern> caverns)
    {
        BlockState surface = ModBlocks.BRINE_SURFACE.get().defaultBlockState();
        for (Cavern cavern : caverns)
        {
            for (Cavern.Lake lake : cavern.lakes())
            {
                int y = lake.surfaceY();
                if (y <= ctx.yMin + 1 || y >= ctx.yMax) continue;
                for (int lz = 0; lz < 16; lz++)
                {
                    for (int lx = 0; lx < 16; lx++)
                    {
                        if (lake.normalized(ctx.x0 + lx + 0.5, ctx.z0 + lz + 0.5) >= 1) continue;
                        if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz) || !ctx.water(lx, y - 1, lz)) continue;
                        // Only over the basin itself (inside its shoreline, with a bed somewhere below, even where a
                        // deeper channel crosses it).
                        for (int d = 2; d < 64 && y - d > ctx.yMin; d++)
                        {
                            if (!ctx.open(lx, y - d, lz))
                            {
                                ctx.set(lx, y, lz, surface);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 11. giant vegetation

    static void giants(CaveChunk ctx, List<Cavern> caverns)
    {
        CaveNoises noises = ctx.noises;
        for (int lz = 0; lz < 16; lz++)
        {
            for (int lx = 0; lx < 16; lx++)
            {
                for (int y = ctx.yMin + 1; y < ctx.yMax; y++)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz) || !ctx.sturdy(lx, y - 1, lz, Direction.UP)) continue;
                    Cavern cavern = cavernAt(ctx, caverns, lx, y, lz);
                    if (cavern == null) continue;
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    double cx = x + 0.5, cz = z + 0.5;
                    Cavern.Lake lake = cavern.lakeAt(cx, cz, 1.0);
                    if (lake != null && y < lake.surfaceY())
                    {
                        // Giants of the lake: crystal kelp, deep tubes and kelp reaching toward (never through) the surface.
                        double n = lake.normalized(cx, cz);
                        if (noises.hash(x, y, z, 501) >= 0.035 + 0.09 * n) continue;
                        double r = noises.hash(x, y, z, 502);
                        Block plant = r < 0.4 ? ModBlocks.CRYSTAL_KELP.get() : r < 0.65 ? ModBlocks.GIANT_TUBE.get() : ModBlocks.GIANT_CAVE_KELP.get();
                        int height = lake.surfaceY() - y - 1 - Mth.floor(noises.hash(x, y, z, 503) * 3);
                        if (height >= 2) column(ctx, lx, y, lz, plant.defaultBlockState(), height, null);
                        continue;
                    }
                    CavernTemplate.Forest forest = cavern.template.forest;
                    if (forest.density() <= 0 || cavern.template.forestPlants.isEmpty()) continue;
                    Cavern.Cluster clump = cavern.kelpCluster(cx, cz);
                    if (clump == null) continue;
                    CavernPatch patch = ctx.patchAt(cavern, lx, lz);
                    double patchFactor = switch (patch)
                    {
                        case PLANT -> 1.0;
                        case WATER -> 0.5;
                        case THERMAL, MINERAL -> 0.25;
                        case CRYSTAL -> 0.15;
                        case ROCK -> 0.1;
                        case OPEN -> 0.08;
                    };
                    double chance = forest.density() * patchFactor * 0.85 * (1 - clump.q() * clump.q()) * (Math.abs(cavern.depth(cx, cz)) < 0.35 ? 1.15 : 1.0);
                    if (noises.hash(x, y, z, 511) >= chance) continue;
                    int free = CaveDecorationGenerator.free(ctx, lx, y, lz, Direction.UP, 255);
                    // Tallest at the heart of each clump.
                    double h = Mth.lerp(clump.height(), forest.minHeight(), forest.maxHeight()) * cavern.forestScale * (0.55 + 0.45 * (1 - clump.q()))
                            * (0.85 + 0.3 * noises.hash(x, y, z, 512));
                    int height = Math.min(Mth.floor(h), free - 2);
                    if (height < 3) continue;
                    CaveEnvironment.PlantEntry entry = cavern.template.forestPlants.pick(Mth.clamp(noises.species(x, y, z) + (noises.hash(x, y, z, 513) - 0.5) * 0.2, 0, 0.999));
                    column(ctx, lx, y, lz, entry.state(), height, clump);
                }
            }
        }
    }

    /**
     * A column of a giant stacking plant; leaning species step sideways every few blocks in their clump's lean
     * direction, but only within this chunk.
     */
    private static boolean column(CaveChunk ctx, int lx, int y, int lz, BlockState plant, int height, @Nullable Cavern.Cluster clump)
    {
        // Callers cap the height by the free water above, which may be almost none.
        if (height < 2) return false;
        Block block = plant.getBlock();
        if (!(block instanceof StackingPlantBlock stacking))
        {
            return CavePlacer.plant(ctx, lx, y, lz, new CaveEnvironment.PlantEntry(plant, height, height), Direction.DOWN, 0, height);
        }
        if (!ctx.sturdy(lx, y - 1, lz, Direction.UP) || (!stacking.growsOnAnything() && ctx.get(lx, y - 1, lz).is(ModTags.INHIBITS_PLANTS))) return false;
        boolean leans = block instanceof LeaningPlantBlock && clump != null && (clump.leanX() != 0 || clump.leanZ() != 0);
        List<int[]> cells = new ArrayList<>(height);
        int cx = lx, cz = lz;
        for (int i = 0; i < height; i++)
        {
            int py = y + i;
            if (py >= ctx.yMax) break;
            if (leans && i > 0 && i % clump.leanEvery() == 0)
            {
                int nx = cx + clump.leanX(), nz = cz + clump.leanZ();
                if (ctx.inChunk(nx, nz) && ctx.water(nx, py, nz))
                {
                    cx = nx;
                    cz = nz;
                }
            }
            if (!ctx.water(cx, py, cz)) break;
            cells.add(new int[] {cx, py, cz});
        }
        if (cells.size() < 2) return false;
        for (int i = 0; i < cells.size(); i++)
        {
            int[] c = cells.get(i);
            ctx.set(c[0], c[1], c[2], plant.setValue(StackingPlantBlock.TOP, i == cells.size() - 1));
        }
        return true;
    }

    // ---------------------------------------------------------------- 14. hanging vegetation

    static void hanging(CaveChunk ctx, List<Cavern> caverns)
    {
        CaveNoises noises = ctx.noises;
        for (int lz = 0; lz < 16; lz++)
        {
            for (int lx = 0; lx < 16; lx++)
            {
                for (int y = ctx.yMax - 1; y > ctx.yMin; y--)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz) || !ctx.sturdy(lx, y + 1, lz, Direction.DOWN)) continue;
                    Cavern cavern = cavernAt(ctx, caverns, lx, y, lz);
                    if (cavern == null) continue;
                    CavernTemplate.Hanging hanging = cavern.template.hanging;
                    if (hanging.density() <= 0 || cavern.template.hangingPlants.isEmpty()) continue;
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    double cx = x + 0.5, cz = z + 0.5;
                    // From the roof, and from the undersides of shelves, bridges and hanging rocks.
                    if (cavern.relativeHeight(cx, y, cz) < 0.55 && ctx.fillFlag(lx, y + 1, lz) == 0) continue;
                    CavernPatch patch = ctx.patchAt(cavern, lx, lz);
                    double patchFactor = patch == CavernPatch.PLANT ? 1.0 : patch == CavernPatch.OPEN ? 0.35 : patch == CavernPatch.WATER ? 0.7 : 0.4;
                    Cavern.Cluster clump = cavern.kelpCluster(cx, cz), open = cavern.rootCluster(cx, cz);
                    double chance = hanging.density() * (clump != null ? 0.55 * (1 - clump.q() * clump.q()) * patchFactor : 0)
                            + hanging.density() * (open != null ? 0.3 * (1 - open.q() * open.q()) * (patch == CavernPatch.OPEN ? 1.2 : 0.6) : 0)
                            + (cavern.gardenAt(cx, cz) != null ? 0.2 : 0);
                    if (noises.hash(x, y, z, 521) >= chance) continue;
                    int free = CaveDecorationGenerator.free(ctx, lx, y, lz, Direction.DOWN, 255);
                    int length = noises.hash(x, y, z, 522) < hanging.reachFloor() ? free : Math.max(2, Mth.floor(free * Mth.lerp(noises.hash(x, y, z, 523), 0.12, 0.6)));
                    CaveEnvironment.PlantEntry entry = cavern.template.hangingPlants.pick(Mth.clamp(noises.species(x + 53, y, z) + (noises.hash(x, y, z, 524) - 0.5) * 0.2, 0, 0.999));
                    CavePlacer.plant(ctx, lx, y, lz, new CaveEnvironment.PlantEntry(entry.state(), length, length), Direction.UP, 0, length);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 12. gardens, groves and lake shores

    static void gardensAndShores(CaveChunk ctx, List<Cavern> caverns)
    {
        CaveNoises noises = ctx.noises;
        BlockState floating = ModBlocks.FLOATING_BLOOM.get().defaultBlockState();
        for (int y = ctx.yMin + 1; y < ctx.yMax; y++)
        {
            for (int lz = 0; lz < 16; lz++)
            {
                for (int lx = 0; lx < 16; lx++)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz)) continue;
                    Cavern cavern = cavernAt(ctx, caverns, lx, y, lz);
                    if (cavern == null) continue;
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    double cx = x + 0.5, cz = z + 0.5;
                    Cavern.Lake lake = cavern.lakeAt(cx, cz, 1.7);
                    if (lake != null && y == lake.surfaceY() - 1 && lake.normalized(cx, cz) < 0.95)
                    {
                        // Floating plants just under the surface.
                        if (noises.hash(x, y, z, 531) < 0.02) ctx.set(lx, y, lz, floating);
                        continue;
                    }
                    if (!ctx.sturdy(lx, y - 1, lz, Direction.UP)) continue;
                    double roll = noises.hash(x, y, z, 532), pick = noises.hash(x, y, z, 533), height = noises.hash(x, y, z, 534);
                    if (lake != null)
                    {
                        double n = lake.normalized(cx, cz);
                        if (n < 1 && y < lake.surfaceY())
                        {
                            if (roll < 0.1 + 0.35 * n) plant(ctx, lx, y, lz, pick < 0.45 ? ModBlocks.CAVE_GRASS.get() : pick < 0.75 ? ModBlocks.CAVE_FERN.get() : ModBlocks.CAVE_MOSS.get(), height, 1, 3);
                        }
                        else if (n >= 1 && y >= lake.surfaceY() - 1 && y <= lake.surfaceY() + 5 && roll < 0.6 * (1 - (n - 1) / 0.7))
                        {
                            // The shore: dense.
                            Block species = pick < 0.2 ? ModBlocks.CAVE_KELP.get() : pick < 0.5 ? ModBlocks.CAVE_GRASS.get() : pick < 0.7 ? ModBlocks.CAVE_FERN.get() : pick < 0.85 ? ModBlocks.CAVE_TUBE_PLANT.get() : ModBlocks.CAVE_MOSS.get();
                            plant(ctx, lx, y, lz, species, height, species == ModBlocks.CAVE_KELP.get() ? 4 : 1, species == ModBlocks.CAVE_KELP.get() ? 10 : 4);
                        }
                        continue;
                    }
                    Cavern.Garden garden = cavern.gardenAt(cx, cz);
                    if (garden == null) continue;
                    double d = Math.sqrt(Mth.square(cx - garden.x()) + Mth.square(cz - garden.z())) / garden.radius();
                    double tall = garden.tall() ? 1.5 : 1.0;
                    if (!garden.grove())
                    {
                        if (d < 0.2) continue;
                        if (d < 0.65)
                        {
                            if (roll >= 0.7) continue;
                            Block species = pick < 0.04 ? ModBlocks.CAVE_BLOOM.get() : pick < 0.18 ? ModBlocks.CAVE_CRYSTAL_PLANT.get() : pick < 0.45 ? ModBlocks.CAVE_GRASS.get() : pick < 0.7 ? ModBlocks.CAVE_FERN.get()
                                    : pick < 0.9 ? ModBlocks.CAVE_TUBE_PLANT.get() : ModBlocks.GLOWTIP_GRASS.get();
                            plant(ctx, lx, y, lz, species, height, 1, 4);
                        }
                        else if (roll < 0.22)
                        {
                            if (pick < 0.6) column(ctx, lx, y, lz, ModBlocks.GIANT_CAVE_KELP.get().defaultBlockState(),
                                    Math.min(Mth.floor((10 + height * 14) * tall), CaveDecorationGenerator.free(ctx, lx, y, lz, Direction.UP, 255) - 2), null);
                            else plant(ctx, lx, y, lz, ModBlocks.CAVE_KELP.get(), height, 6, 14);
                        }
                    }
                    else if (roll < 0.45)
                    {
                        if (pick < 0.25)
                        {
                            int h = Math.min(Mth.floor((12 + height * 18) * tall), CaveDecorationGenerator.free(ctx, lx, y, lz, Direction.UP, 255) - 2);
                            if (h >= 3) column(ctx, lx, y, lz, ModBlocks.GIANT_CAVE_KELP.get().defaultBlockState(), h, cavern.kelpCluster(cx, cz));
                        }
                        else if (pick < 0.45) plant(ctx, lx, y, lz, ModBlocks.CAVE_FERN.get(), height, 1, 1);
                        else if (pick < 0.7) plant(ctx, lx, y, lz, ModBlocks.CAVE_GRASS.get(), height, 1, 3);
                        else if (pick < 0.82) plant(ctx, lx, y, lz, height < 0.5 ? ModBlocks.CAVE_CRYSTAL_PLANT.get() : ModBlocks.GLOWTIP_GRASS.get(), height, 1, 1);
                        else if (pick < 0.9)
                        {
                            BlockState cluster = cavern.crystalCluster(height);
                            if (cluster != null) CavePlacer.cluster(ctx, lx, y, lz, cluster, Direction.DOWN);
                        }
                        else plant(ctx, lx, y, lz, ModBlocks.CAVE_TUBE_PLANT.get(), height, 2, 5);
                    }
                }
            }
        }
    }

    private static void plant(CaveChunk ctx, int lx, int y, int lz, Block block, double roll, int min, int max)
    {
        if (block instanceof CaveMossBlock moss)
        {
            CavePlacer.moss(ctx, lx, y, lz, moss);
            return;
        }
        CavePlacer.plant(ctx, lx, y, lz, new CaveEnvironment.PlantEntry(block.defaultBlockState(), min, max), Direction.DOWN, roll, max);
    }

    // ---------------------------------------------------------------- shelf tops and roof light

    static void surfaces(CaveChunk ctx, List<Cavern> caverns)
    {
        CaveNoises noises = ctx.noises;
        CaveMossBlock luminous = (CaveMossBlock) ModBlocks.LUMINOUS_MOSS.get();
        for (int y = ctx.yMin + 1; y < ctx.yMax; y++)
        {
            for (int lz = 0; lz < 16; lz++)
            {
                for (int lx = 0; lx < 16; lx++)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz)) continue;
                    Cavern cavern = cavernAt(ctx, caverns, lx, y, lz);
                    if (cavern == null) continue;
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    double cx = x + 0.5, cz = z + 0.5;
                    double roll = noises.hash(x, y, z, 541);
                    if (ctx.fillFlag(lx, y - 1, lz) == CaveChunk.SHELF && ctx.sturdy(lx, y - 1, lz, Direction.UP))
                    {
                        // Shelf tops: sediment settles, plants take hold, loose rock, an ore seam, a crystal.
                        if (roll >= 0.5) continue;
                        double pick = noises.hash(x, y, z, 542);
                        CaveEnvironment env = cavern.space.environment;
                        if (pick < 0.3)
                        {
                            BlockState sediment = env.floor.pick(noises.hash(x, y, z, 543));
                            if (sediment != null) ctx.set(lx, y - 1, lz, sediment);
                        }
                        else if (pick < 0.6 && !env.floorPlants.isEmpty())
                        {
                            CavePlacer.plant(ctx, lx, y, lz, env.floorPlants.pick(noises.species(x, y, z)), Direction.DOWN, noises.hash(x, y, z, 544), 6);
                        }
                        else if (pick < 0.7) plant(ctx, lx, y, lz, pick < 0.65 ? ModBlocks.CAVE_RUBBLE.get() : ModBlocks.SEAFLOOR_PEBBLES.get(), 0, 1, 1);
                        else if (pick < 0.8 && !cavern.space.profile.ores.isEmpty()) ctx.set(lx, y - 1, lz, cavern.space.profile.ores.pick(noises.hash(x, y, z, 545)));
                        else if (pick < 0.9)
                        {
                            BlockState cluster = cavern.crystalCluster(noises.hash(x, y, z, 546));
                            if (cluster != null) CavePlacer.cluster(ctx, lx, y, lz, cluster, Direction.DOWN);
                        }
                        continue;
                    }
                    if (!ctx.sturdy(lx, y + 1, lz, Direction.DOWN) || cavern.relativeHeight(cx, y, cz) < 0.8) continue;
                    // Points of light across the roof, thicker over crystal and plant ground.
                    CavernPatch patch = ctx.patchAt(cavern, lx, lz);
                    double chance = cavern.template.ceilingGlow * (patch == CavernPatch.CRYSTAL ? 2.0 : patch == CavernPatch.PLANT ? 1.3 : 1.0)
                            * (0.3 + 1.4 * noises.patch(x, y, z, 61.7));
                    if (roll < chance) CavePlacer.moss(ctx, lx, y, lz, luminous);
                }
            }
        }
    }
}
