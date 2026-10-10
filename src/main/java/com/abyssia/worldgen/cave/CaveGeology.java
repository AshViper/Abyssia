package com.abyssia.worldgen.cave;

import com.abyssia.registry.ModBlocks;
import com.abyssia.thermal.ThermalTemperature;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Geological layers on every cave surface. The first blocks into a wall take the cave environment's coating (wet
 * rock, thermal rock, crystal rock...), broken by accent patches (mineral crust, crystal walls) and thin sediment
 * bands; floors are covered in settled sediment and mud; ceilings get their own rock. Behind the coating, walls
 * show the biome's strata by depth below the seabed (e.g. sediment, wet rock, layered rock, abyssal rock), with
 * noise-warped boundaries, so a shaft cuts visibly through the layers on its way down. Lakes leave a mineral crust
 * ring at their water line, vents a zoned mineral floor, and small ore exposures dot walls and ceilings.
 */
final class CaveGeology
{
    /** Field depth into the wall covered by the environment's coating; deeper blocks show strata. */
    private static final double COATING = 1.8;

    private CaveGeology() {}

    /** Wall rock of a space at a block: environment coating, chosen in blotches. */
    static BlockState wallRock(CaveChunk ctx, CaveSpace space, int x, int y, int z)
    {
        if (space.hall != null)
        {
            // A hall's rock (also its columns and formations): the look's wall rock with its mosaic patches.
            CaveEnvironment env = space.environment;
            if (!env.hallMosaic.isEmpty() && ctx.noises.mosaic(x, y, z) > CaveNoises.mosaicThreshold(env.hall.look().mosaicCoverage()))
            {
                return env.hallMosaic.pick(ctx.noises.mottle(x, y, z, 9));
            }
            BlockState rock = env.hallWall.pick(ctx.noises.mottle(x, y, z, 1));
            if (rock != null) return rock;
        }
        BlockState state = space.environment.wall.pick(ctx.noises.mottle(x, y, z, 1));
        return state != null ? state : ModBlocks.ABYSSAL_CAVE_ROCK.get().defaultBlockState();
    }

    static void paint(CaveChunk ctx)
    {
        CaveNoises noises = ctx.noises;
        for (int y = ctx.yMin; y <= ctx.yMax; y++)
        {
            for (int lz = 0; lz < 16; lz++)
            {
                for (int lx = 0; lx < 16; lx++)
                {
                    float s = ctx.field(lx, y, lz);
                    if (s < 0 || s >= CaveChunk.WALL_DEPTH || ctx.fillFlag(lx, y, lz) != 0) continue;
                    CaveSpace space = ctx.space(lx, y, lz);
                    if (space == null) continue;
                    BlockState current = ctx.get(lx, y, lz);
                    if (current.isAir() || !current.getFluidState().isEmpty() || current.is(Blocks.BEDROCK) || current.canBeReplaced()) continue;
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    boolean floor = ctx.carvedHere(lx, y + 1, lz);
                    boolean ceiling = !floor && ctx.carvedHere(lx, y - 1, lz);
                    CaveEnvironment env = space.environment;
                    BlockState state;
                    if (space.hall != null)
                    {
                        state = hallSurface(ctx, space, lx, y, lz, s, floor, ceiling);
                    }
                    else if (space.hasLake() && !floor && !ceiling && Math.abs(y - space.waterLevel) <= 1 && s < 1.5)
                    {
                        // Bathtub ring: minerals precipitated at the lake's surface.
                        state = ModBlocks.CAVE_MINERAL_CRUST.get().defaultBlockState();
                    }
                    else if (floor && s < 1.6 && !env.floor.isEmpty())
                    {
                        state = heatedFloor(ctx, x, y, z);
                        if (state == null && space.cavern != null) state = patchFloor(ctx.patchAt(space.cavern, x - ctx.x0, z - ctx.z0), noises.mottle(x, y, z, 8));
                        if (state == null) state = env.floor.pick(noises.mottle(x, y, z, 2));
                    }
                    else if (ceiling && s < 1.2 && !env.ceiling.isEmpty())
                    {
                        state = env.ceiling.pick(noises.mottle(x, y, z, 3));
                    }
                    else if (s < COATING)
                    {
                        float accent = env.geology.accentChance();
                        if (accent > 0 && !env.accents.isEmpty() && noises.patch(x, y, z, 5.3) > 1 - accent * 2.2)
                        {
                            state = env.accents.pick(noises.mottle(x, y, z, 4));
                        }
                        else if (!floor && !ceiling && space.cavern != null
                                && Math.sin((y + noises.strata(x, y, z) * 2.0) * 0.35) > 1.02 - 0.06 * space.cavern.mineralFactor(x + 0.5, z + 0.5))
                        {
                            // Mineral layers banding cavern walls, thicker and more frequent over mineral ground.
                            state = (noises.hash(x, y, z, 15) < 0.3 ? ModBlocks.CAVE_MINERAL_CRUST : ModBlocks.MINERAL_CAVE_ROCK).get().defaultBlockState();
                        }
                        else if (!floor && !ceiling && Math.sin((y + noises.strata(x, y, z) * 1.5) * 0.9) > 0.94)
                        {
                            // Thin sediment layers showing the strata through the coating.
                            state = stratum(ctx, space, lx, y, lz);
                        }
                        else
                        {
                            state = wallRock(ctx, space, x, y, z);
                        }
                    }
                    else
                    {
                        state = stratum(ctx, space, lx, y, lz);
                    }
                    double ores = space.cavern != null ? space.cavern.mineralFactor(x + 0.5, z + 0.5) : 1.0;
                    if (!floor && s < 1.2 && !space.profile.ores.isEmpty() && noises.patch(x, y, z, 91.1) > 0.82 && noises.hash(x, y, z, 13) < 0.45 * ores)
                    {
                        // Ore exposures: small outcrops to spot while exploring.
                        state = space.profile.ores.pick(noises.hash(x, y, z, 14));
                    }
                    if (state != null && state != current) ctx.set(lx, y, lz, state);
                }
            }
        }
    }

    /**
     * AB03: a hall's surfaces: terrace tops in the look's floor rock, the basin below the terraces in its basin blocks (magma
     * fields, ice), walls and roof in its rock with mosaic patches and embedded light blocks; deeper rock as any cave.
     */
    private static BlockState hallSurface(CaveChunk ctx, CaveSpace space, int lx, int y, int lz, float s, boolean floor, boolean ceiling)
    {
        CaveEnvironment env = space.environment;
        CaveNoises noises = ctx.noises;
        int x = ctx.x0 + lx, z = ctx.z0 + lz;
        if (floor && s < 1.6)
        {
            BlockState heated = heatedFloor(ctx, x, y, z);
            if (heated != null) return heated;
            BlockState state = (y < space.hall.level ? env.hallBasin : env.hallFloor).pick(noises.mottle(x, y, z, 2));
            return state != null ? state : wallRock(ctx, space, x, y, z);
        }
        if (s < COATING || (ceiling && s < 1.2))
        {
            CaveEnvironment.HallLook look = env.hall.look();
            if (!env.hallGlow.isEmpty() && noises.hash(x, y, z, 77) < look.glowChance() * (0.3 + 1.7 * noises.patch(x, y, z, 23.9)))
            {
                return env.hallGlow.pick(noises.hash(x, y, z, 78));
            }
            if (!env.hallMosaic.isEmpty() && noises.mosaic(x, y, z) > CaveNoises.mosaicThreshold(look.mosaicCoverage()))
            {
                return env.hallMosaic.pick(noises.mottle(x, y, z, 9));
            }
            BlockState state = (ceiling ? env.hallCeiling : env.hallWall).pick(noises.mottle(x, y, z, ceiling ? 3 : 1));
            return state != null ? state : wallRock(ctx, space, x, y, z);
        }
        return stratum(ctx, space, lx, y, lz);
    }

    /** Floor cover of a cavern ecology patch; null keeps the environment's own sediment. */
    private static BlockState patchFloor(CavernPatch patch, double r)
    {
        return switch (patch)
        {
            case MINERAL -> (r < 0.35 ? ModBlocks.CAVE_MINERAL_CRUST : ModBlocks.MINERAL_SEDIMENT).get().defaultBlockState();
            case CRYSTAL -> (r < 0.2 ? ModBlocks.CRYSTAL_CAVE_ROCK : ModBlocks.CRYSTAL_SEDIMENT).get().defaultBlockState();
            case ROCK -> (r < 0.5 ? ModBlocks.DARK_CAVE_ROCK : ModBlocks.ABYSSAL_CAVE_ROCK).get().defaultBlockState();
            case THERMAL -> (r < 0.5 ? ModBlocks.VOLCANIC_ASH : ModBlocks.MINERAL_SEDIMENT).get().defaultBlockState();
            case WATER -> ModBlocks.CAVE_MUD.get().defaultBlockState();
            default -> null;
        };
    }

    /** The biome's stratum rock at this block's depth below the seabed. */
    private static BlockState stratum(CaveChunk ctx, CaveSpace space, int lx, int y, int lz)
    {
        int x = ctx.x0 + lx, z = ctx.z0 + lz;
        // AB06: inside a window the strata count from its top; only the descent route above it counts from the real seabed.
        int top = ctx.network.isWindow() && y <= ctx.network.maxY() ? ctx.network.maxY() : ctx.seabed[lz * 16 + lx];
        int depth = top - y + (int) Math.round(ctx.noises.strata(x, y, z));
        BlockState state = space.profile.stratum(Math.max(0, depth));
        return state != null ? state : wallRock(ctx, space, x, y, z);
    }

    /** Mineral zoning of floors around cave vents: deposits outlive the heat that made them. */
    private static BlockState heatedFloor(CaveChunk ctx, int x, int y, int z)
    {
        if (ctx.vents.isEmpty()) return null;
        float heat = 0;
        for (CaveSystem.Vent vent : ctx.vents)
        {
            if (Math.abs(y - vent.yHint()) > 10) continue;
            double d = Math.sqrt((double) (x - vent.x()) * (x - vent.x()) + (double) (z - vent.z()) * (z - vent.z()));
            heat = Math.max(heat, vent.type().maxTemperature * ThermalTemperature.falloff(d, vent.type().radius * 1.3));
        }
        if (heat <= 0.3f) return null;
        double r = ctx.noises.hash(x, y, z, 17);
        if (heat > 0.7f) return (r < 0.5 ? ModBlocks.BLACK_MINERAL_DEPOSIT : ModBlocks.SULFUR_DEPOSIT).get().defaultBlockState();
        return (r < 0.4 ? ModBlocks.SULFUR_DEPOSIT : r < 0.8 ? ModBlocks.MINERAL_SEDIMENT : ModBlocks.CAVE_MINERAL_CRUST).get().defaultBlockState();
    }
}
