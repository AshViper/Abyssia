package com.abyssia.worldgen.cave;

import com.abyssia.registry.ModBlocks;
import com.abyssia.registry.ModTags;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Mineral decoration of cave surfaces, in the generation order crystals, then stalactites and stalagmites:
 * <ul>
 *     <li>tips on giant formations, so they end in a point rather than a stump</li>
 *     <li>crystal clusters on floors, walls and ceilings in patches</li>
 *     <li>stalactites, stalagmites and, where the two meet, joined columns</li>
 *     <li>debris fields under collapses: rubble, broken crystal, fallen kelp and sediment piles</li>
 *     <li>landmark dressing around entrances and sea arches: kelp, vines under overhangs, crystals, crust, sediment</li>
 * </ul>
 * Everything is decided per block from hashes and noise, so each chunk decorates only itself and still agrees with
 * its neighbours; columns stay within their own block column.
 */
final class CaveDecorationGenerator
{
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final List<RegistryObject<Block>> MINERAL_CLUSTER_LIST = List.of(ModBlocks.COBALT_CLUSTER, ModBlocks.NICKEL_CLUSTER,
            ModBlocks.MANGANESE_NODULES, ModBlocks.SULFUR_CLUSTER);
    @SuppressWarnings("unchecked")
    private static final RegistryObject<Block>[] MINERAL_CLUSTERS = MINERAL_CLUSTER_LIST.toArray(new RegistryObject[0]);

    private CaveDecorationGenerator() {}

    /** Speleothem tips below (and above) the pointed ends of giant stalactites and stalagmites. */
    static void formationTips(CaveChunk ctx)
    {
        for (int lz = 0; lz < 16; lz++)
        {
            for (int lx = 0; lx < 16; lx++)
            {
                for (int y = ctx.yMin + 1; y < ctx.yMax; y++)
                {
                    byte flag = ctx.fillFlag(lx, y, lz);
                    if (flag != CaveChunk.TIP_DOWN && flag != CaveChunk.TIP_UP) continue;
                    Direction tip = flag == CaveChunk.TIP_DOWN ? Direction.DOWN : Direction.UP;
                    int ty = y + tip.getStepY();
                    if (!ctx.carvedHere(lx, ty, lz) || !ctx.open(lx, ty, lz) || ctx.fillFlag(lx, ty, lz) != 0) continue;
                    CaveSpace space = ctx.space(lx, ty, lz);
                    if (space == null || space.environment.speleothem == null) continue;
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    int length = 1 + Mth.floor(ctx.noises.hash(x, y, z, 51) * 3);
                    length = Math.min(length, free(ctx, lx, ty, lz, tip, 5) - 1);
                    if (length > 0) CavePlacer.speleothem(ctx, lx, ty, lz, space.environment.speleothem, tip, length, false);
                }
            }
        }
    }

    /** Open blocks in a row from (lx, y, lz) toward {@code dir}, up to {@code max}. */
    static int free(CaveChunk ctx, int lx, int y, int lz, Direction dir, int max)
    {
        int n = 0;
        while (n < max && y + dir.getStepY() * n >= ctx.yMin && y + dir.getStepY() * n <= ctx.yMax && ctx.open(lx, y + dir.getStepY() * n, lz)) n++;
        return n;
    }

    /** Crystal clusters and needles in patches on every kind of face; crystal caves are thick with them. */
    static void crystals(CaveChunk ctx)
    {
        CaveNoises noises = ctx.noises;
        for (int y = ctx.yMin + 1; y < ctx.yMax; y++)
        {
            for (int lz = 0; lz < 16; lz++)
            {
                for (int lx = 0; lx < 16; lx++)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.open(lx, y, lz)) continue;
                    CaveSpace space = ctx.space(lx, y, lz);
                    if (space == null || (space.environment.crystals.isEmpty() && space.cavern == null)) continue;
                    double density = space.environment.formations.crystalDensity();
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    CavernPatch cavernPatch = space.cavern != null ? ctx.patchAt(space.cavern, lx, lz) : null;
                    // Crystal ground in a cavern crystallises even where its environment rarely does.
                    if (cavernPatch != null) density = Math.max(density, cavernPatch == CavernPatch.CRYSTAL ? 0.05 : 0) * cavernPatch.crystals;
                    if (density <= 0) continue;
                    // The roll is checked against the best case first, so most blocks never sample the patch noise.
                    double roll = noises.hash(x, y, z, 61);
                    if (roll >= density * 2.4 || roll >= density * (0.2 + noises.patch(x, y, z, 7.7) * 2.2)) continue;
                    Direction support = support(ctx, lx, y, lz, noises.hash(x, y, z, 62));
                    if (support == null) continue;
                    BlockState crystal = space.environment.crystals.pick(noises.species(x + 311, y, z));
                    // Cavern crystal ground shows the cavern colours; mineral ground grows mineral clusters.
                    if (cavernPatch == CavernPatch.CRYSTAL) crystal = space.cavern.crystalCluster(noises.species(x + 97, y, z));
                    else if (cavernPatch == CavernPatch.MINERAL) crystal = MINERAL_CLUSTERS[(int) (noises.hash(x, y, z, 63) * MINERAL_CLUSTERS.length)].get().defaultBlockState();
                    if (crystal == null) continue;
                    CavePlacer.cluster(ctx, lx, y, lz, crystal, support);
                }
            }
        }
    }

    /** A random sturdy neighbour (ceiling, floor or wall) of an open block, preferring ceilings and floors. */
    private static Direction support(CaveChunk ctx, int lx, int y, int lz, double roll)
    {
        if (ctx.sturdy(lx, y + 1, lz, Direction.DOWN) && roll < 0.5) return Direction.UP;
        if (ctx.sturdy(lx, y - 1, lz, Direction.UP)) return Direction.DOWN;
        if (ctx.sturdy(lx, y + 1, lz, Direction.DOWN)) return Direction.UP;
        int start = (int) (roll * 4) & 3;
        for (int i = 0; i < 4; i++)
        {
            Direction d = HORIZONTAL[(start + i) & 3];
            if (ctx.sturdy(lx + d.getStepX(), y, lz + d.getStepZ(), d.getOpposite())) return d;
        }
        return null;
    }

    /**
     * Stalactites hanging from ceilings and stalagmites rising from floors, in clusters, longer in bigger caves;
     * where a stalactite would nearly reach the floor it joins a stalagmite into a column.
     */
    static void speleothems(CaveChunk ctx)
    {
        CaveNoises noises = ctx.noises;
        for (int lz = 0; lz < 16; lz++)
        {
            for (int lx = 0; lx < 16; lx++)
            {
                for (int y = ctx.yMax - 1; y > ctx.yMin; y--)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.open(lx, y, lz)) continue;
                    CaveSpace space = ctx.space(lx, y, lz);
                    if (space == null || space.environment.speleothem == null) continue;
                    // Only blocks under a roof or on a floor can grow one: test that before any noise.
                    boolean roof = ctx.sturdy(lx, y + 1, lz, Direction.DOWN);
                    if (!roof && !ctx.sturdy(lx, y - 1, lz, Direction.UP)) continue;
                    float density = space.environment.formations.speleothemDensity();
                    if (space.landmark == CaveLandmark.GIANT_STALACTITE_CHAMBER) density *= 2.5f;
                    if (space.hasLake() && y > space.waterLevel) density *= 1.8f;
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    if (space.cavern != null) density *= (float) space.cavern.speleothemFactor(x + 0.5, z + 0.5);
                    double chance = density * (0.2 + 1.0 * noises.patch(x, y, z, 3.3));
                    BlockState material = space.environment.speleothem;
                    int maxLength = space.maxSpeleothemLength();
                    if (roof)
                    {
                        if (noises.hash(x, y, z, 71) >= chance) continue;
                        int free = free(ctx, lx, y, lz, Direction.DOWN, maxLength * 2 + 2);
                        boolean floorBelow = ctx.sturdy(lx, y - free, lz, Direction.UP);
                        double roll = noises.hash(x, y, z, 72);
                        if (floorBelow && free >= 2 && free <= maxLength * 2 && noises.hash(x, y, z, 73) < 0.35)
                        {
                            int top = free / 2 + (free % 2 == 1 && roll < 0.5 ? 1 : 0);
                            CavePlacer.speleothem(ctx, lx, y, lz, material, Direction.DOWN, top, true);
                            CavePlacer.speleothem(ctx, lx, y - free + 1, lz, material, Direction.UP, free - top, true);
                        }
                        else
                        {
                            int length = Math.min(1 + Mth.floor(roll * roll * maxLength), free - 1);
                            CavePlacer.speleothem(ctx, lx, y, lz, material, Direction.DOWN, length, false);
                        }
                        y -= free;
                    }
                    else
                    {
                        if (noises.hash(x, y, z, 74) >= chance * 0.7) continue;
                        int free = free(ctx, lx, y, lz, Direction.UP, maxLength + 1);
                        double roll = noises.hash(x, y, z, 75);
                        int length = Math.min(1 + Mth.floor(roll * roll * maxLength * 0.8), free - 1);
                        CavePlacer.speleothem(ctx, lx, y, lz, material, Direction.UP, length, false);
                    }
                }
            }
        }
    }

    /** Debris under collapses: rubble, broken crystal, fallen kelp, and small sediment piles at the centre. */
    static void collapses(CaveChunk ctx)
    {
        for (CaveSystem.Site site : ctx.sites)
        {
            if (site.kind() != CaveSystem.SiteKind.COLLAPSE) continue;
            CaveEnvironment env = site.space().environment;
            forEachFloor(ctx, site, 6, (lx, y, lz, d) -> {
                int x = ctx.x0 + lx, z = ctx.z0 + lz;
                double roll = ctx.noises.hash(x, y, z, 81);
                if (d < 0.3 && roll < 0.35 && ctx.water(lx, y, lz))
                {
                    ctx.set(lx, y, lz, ModBlocks.CAVE_SEDIMENT.get().defaultBlockState());
                    if (roll < 0.12 && ctx.water(lx, y + 1, lz)) ctx.set(lx, y + 1, lz, ModBlocks.CAVE_SEDIMENT.get().defaultBlockState());
                    return;
                }
                if (env.debris.isEmpty() || roll >= 0.6 * (1 - d)) return;
                BlockState debris = env.debris.pick(ctx.noises.hash(x, y, z, 82));
                CavePlacer.plant(ctx, lx, y, lz, new CaveEnvironment.PlantEntry(debris, 1, 1), Direction.DOWN, 0, 1);
            });
        }
    }

    /** Entrance and arch dressing: the look that makes a cave mouth a landmark. */
    static void sites(CaveChunk ctx)
    {
        for (CaveSystem.Site site : ctx.sites)
        {
            if (site.kind() == CaveSystem.SiteKind.COLLAPSE) continue;
            CaveEnvironment env = site.space().environment;
            boolean arch = site.kind() == CaveSystem.SiteKind.ARCH;
            int range = arch ? 22 : 12;
            BlockState kelp = ModBlocks.GIANT_CAVE_KELP.get().defaultBlockState();
            forEachOpen(ctx, site, range, (lx, y, lz, d) -> {
                int x = ctx.x0 + lx, z = ctx.z0 + lz;
                double roll = ctx.noises.hash(x, y, z, 91);
                double near = 1 - d;
                if (ctx.sturdy(lx, y + 1, lz, Direction.DOWN) && !ctx.sturdy(lx, y - 1, lz, Direction.UP))
                {
                    // Vines and roots trailing from overhangs and the underside of arches.
                    if (roll < 0.55 && !env.ceilingPlants.isEmpty())
                    {
                        CavePlacer.plant(ctx, lx, y, lz, env.ceilingPlants.pick(ctx.noises.species(x, y, z)), Direction.UP, ctx.noises.hash(x, y, z, 92), 8);
                    }
                    return;
                }
                if (!ctx.sturdy(lx, y - 1, lz, Direction.UP)) return;
                // Only plain terrain is repainted; ores, crusts and formations keep their look.
                boolean soft = ctx.get(lx, y - 1, lz).is(ModTags.VEIN_REPLACEABLE);
                // Mineral crust and fresh sediment ringing the mouth.
                double paint = ctx.noises.patch(x, y, z, 17.3);
                if (soft && paint > 0.55 && roll < near * 0.8) ctx.set(lx, y - 1, lz, ModBlocks.CAVE_MINERAL_CRUST.get().defaultBlockState());
                else if (soft && paint < 0.15 && roll < near * 0.6) ctx.set(lx, y - 1, lz, ModBlocks.CAVE_SEDIMENT.get().defaultBlockState());
                if (roll < 0.07 * near + (arch ? 0.03 : 0))
                {
                    // A stand of giant kelp marking the spot from afar.
                    CavePlacer.plant(ctx, lx, y, lz, new CaveEnvironment.PlantEntry(kelp, 8, arch ? 22 : 30), Direction.DOWN, ctx.noises.hash(x, y, z, 93), 40);
                }
                else if (roll < 0.1 * near + 0.07 && !env.crystals.isEmpty())
                {
                    CavePlacer.cluster(ctx, lx, y, lz, env.crystals.pick(ctx.noises.hash(x, y, z, 94)), Direction.DOWN);
                }
                else if (roll < 0.35 * near + 0.1 && !env.floorPlants.isEmpty())
                {
                    CavePlacer.plant(ctx, lx, y, lz, env.floorPlants.pick(ctx.noises.species(x, y, z)), Direction.DOWN, ctx.noises.hash(x, y, z, 95), 12);
                }
            });
        }
    }

    @FunctionalInterface
    interface SiteVisitor
    {
        void visit(int lx, int y, int lz, double distance);
    }

    /** Open blocks resting on a floor within a site (distance 0 at its centre, 1 at its edge). */
    private static void forEachFloor(CaveChunk ctx, CaveSystem.Site site, int yRange, SiteVisitor visitor)
    {
        forEachOpen(ctx, site, yRange, (lx, y, lz, d) -> {
            if (ctx.sturdy(lx, y - 1, lz, Direction.UP)) visitor.visit(lx, y, lz, d);
        });
    }

    private static void forEachOpen(CaveChunk ctx, CaveSystem.Site site, int yRange, SiteVisitor visitor)
    {
        int y0 = Math.max(ctx.yMin + 1, Mth.floor(site.y()) - yRange), y1 = Math.min(ctx.yMax - 1, Mth.floor(site.y()) + yRange);
        for (int lz = 0; lz < 16; lz++)
        {
            for (int lx = 0; lx < 16; lx++)
            {
                double dx = ctx.x0 + lx + 0.5 - site.x(), dz = ctx.z0 + lz + 0.5 - site.z();
                double d = Math.sqrt(dx * dx + dz * dz) / site.radius();
                if (d >= 1) continue;
                for (int y = y1; y >= y0; y--)
                {
                    if (ctx.water(lx, y, lz)) visitor.visit(lx, y, lz, d);
                }
            }
        }
    }
}
