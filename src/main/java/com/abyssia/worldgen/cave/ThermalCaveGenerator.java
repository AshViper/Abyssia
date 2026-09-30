package com.abyssia.worldgen.cave;

import com.abyssia.Config;
import com.abyssia.block.StackingPlantBlock;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.registry.ModBlocks;
import com.abyssia.thermal.ThermalTemperature;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hydrothermal features inside thermal caves and the Thermal Cathedral. Vents are the same {@link ThermalVentBlock}
 * cores as on the open seabed, so plumes, smoke, updraft and hot-water fog come for free from the existing client
 * vent tracker; no block entity, no ticking. Around them the heat decides what lives: bare hot minerals at the core,
 * heat moss, sulfur and thermal crystal buds close in, then heat-adapted tubes and vent grass. Colours stay dark
 * rock with orange, red, yellow and dark green accents.
 */
final class ThermalCaveGenerator
{
    private ThermalCaveGenerator() {}

    /** Chimneys topped by vent cores, each rising from the exact cave floor in its own column. */
    static void vents(CaveChunk ctx)
    {
        if (!Config.THERMAL_VENTS.get()) return;
        for (CaveSystem.Vent vent : ctx.vents)
        {
            int lx = vent.x() - ctx.x0, lz = vent.z() - ctx.z0;
            if (!ctx.inChunk(lx, lz)) continue;
            int floor = findFloor(ctx, lx, lz, vent.yHint());
            if (floor == Integer.MIN_VALUE) continue;
            int built = 0;
            while (built < vent.height() && ctx.open(lx, floor + built, lz) && ctx.open(lx, floor + built + 1, lz))
            {
                ctx.set(lx, floor + built, lz, chimney(ctx, vent, lx, floor + built, lz, built >= vent.height() - 2));
                built++;
            }
            if (!ctx.open(lx, floor + built, lz)) continue;
            ctx.set(lx, floor + built, lz, ModBlocks.THERMAL_VENT.get().defaultBlockState()
                    .setValue(ThermalVentBlock.TYPE, vent.type()).setValue(ThermalVentBlock.ACTIVITY, vent.activity()));
            // A low skirt of vent rock around the chimney foot.
            for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST})
            {
                int nx = lx + d.getStepX(), nz = lz + d.getStepZ();
                if (ctx.inChunk(nx, nz) && ctx.open(nx, floor, nz) && ctx.sturdy(nx, floor - 1, nz, Direction.UP)
                        && ctx.noises.hash(vent.x() + d.getStepX(), floor, vent.z() + d.getStepZ(), 101) < 0.6)
                {
                    ctx.set(nx, floor, nz, chimney(ctx, vent, nx, floor, nz, false));
                }
            }
        }
    }

    private static int findFloor(CaveChunk ctx, int lx, int lz, int hint)
    {
        for (int y = Math.min(ctx.yMax - 2, hint + 12); y >= Math.max(ctx.yMin + 1, hint - 16); y--)
        {
            if (ctx.carvedHere(lx, y, lz) && ctx.water(lx, y, lz) && ctx.sturdy(lx, y - 1, lz, Direction.UP)) return y;
        }
        return Integer.MIN_VALUE;
    }

    private static BlockState chimney(CaveChunk ctx, CaveSystem.Vent vent, int lx, int y, int lz, boolean top)
    {
        double r = ctx.noises.hash(ctx.x0 + lx, y, ctx.z0 + lz, 102);
        Block block = switch (vent.type())
        {
            case BLACK_SMOKER -> top && r < 0.6 ? ModBlocks.BLACK_MINERAL_DEPOSIT.get() : ModBlocks.BLACK_VENT_ROCK.get();
            case WHITE_SMOKER -> r < 0.4 ? ModBlocks.SULFUR_VENT_ROCK.get() : ModBlocks.VENT_ROCK.get();
            case MINERAL -> r < 0.3 ? ModBlocks.MINERAL_SEDIMENT.get() : ModBlocks.MINERAL_VENT_ROCK.get();
            case SUPERHEATED -> r < 0.2 ? ModBlocks.MOLTEN_VOLCANIC_ROCK.get() : ModBlocks.THERMAL_CAVE_ROCK.get();
        };
        return block.defaultBlockState();
    }

    /** Heat at a floor block from the vents near it (0..1), by horizontal distance and vent strength. */
    private static float heat(CaveChunk ctx, int x, int y, int z)
    {
        float heat = 0;
        for (CaveSystem.Vent vent : ctx.vents)
        {
            if (Math.abs(y - vent.yHint()) > 10) continue;
            double d = Math.sqrt((double) (x - vent.x()) * (x - vent.x()) + (double) (z - vent.z()) * (z - vent.z()));
            heat = Math.max(heat, vent.type().maxTemperature * vent.activity().temperature * ThermalTemperature.falloff(d, vent.type().radius * 1.3));
        }
        return heat;
    }

    /** Heat zones around cave vents: heat moss, sulfur, thermal crystal buds, then tubes and vent grass. */
    static void zones(CaveChunk ctx)
    {
        if (ctx.vents.isEmpty() || !Config.THERMAL_VEGETATION.get()) return;
        for (int y = ctx.yMin + 1; y < ctx.yMax; y++)
        {
            for (int lz = 0; lz < 16; lz++)
            {
                for (int lx = 0; lx < 16; lx++)
                {
                    if (!ctx.carvedHere(lx, y, lz) || !ctx.water(lx, y, lz)) continue;
                    int x = ctx.x0 + lx, z = ctx.z0 + lz;
                    float heat = heat(ctx, x, y, z);
                    if (heat <= 0.12f) continue;
                    double r = ctx.noises.hash(x, y, z, 103);
                    if (ctx.sturdy(lx, y + 1, lz, Direction.DOWN) && heat > 0.3f && r < 0.3)
                    {
                        // Minerals precipitate on the roof above hot water.
                        if (ctx.get(lx, y + 1, lz).is(ModBlocks.THERMAL_CAVE_ROCK.get()) || r < 0.1)
                        {
                            ctx.set(lx, y + 1, lz, (r < 0.15 ? ModBlocks.SULFUR_DEPOSIT : ModBlocks.CAVE_MINERAL_CRUST).get().defaultBlockState());
                        }
                        continue;
                    }
                    if (!ctx.sturdy(lx, y - 1, lz, Direction.UP) || heat > 0.75f) continue;
                    if (heat > 0.4f)
                    {
                        if (r < 0.4) ctx.set(lx, y, lz, ModBlocks.HEAT_MOSS.get().defaultBlockState());
                        else if (r < 0.46) CavePlacer.cluster(ctx, lx, y, lz, ModBlocks.SULFUR_CLUSTER.get().defaultBlockState(), Direction.DOWN);
                        else if (r < 0.56)
                        {
                            Block bud = heat > 0.6f ? ModBlocks.THERMAL_CRYSTAL_CLUSTER.get() : heat > 0.5f ? ModBlocks.MEDIUM_THERMAL_CRYSTAL_BUD.get()
                                    : ModBlocks.SMALL_THERMAL_CRYSTAL_BUD.get();
                            CavePlacer.cluster(ctx, lx, y, lz, bud.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP), Direction.DOWN);
                        }
                    }
                    else
                    {
                        BlockState state = r < 0.15 ? ModBlocks.THERMAL_TUBE.get().defaultBlockState().setValue(StackingPlantBlock.TOP, true)
                                : r < 0.27 ? ModBlocks.THERMAL_PLANT.get().defaultBlockState() : r < 0.47 ? ModBlocks.VENT_GRASS.get().defaultBlockState() : null;
                        if (state != null)
                        {
                            CavePlacer.plant(ctx, lx, y, lz, new CaveEnvironment.PlantEntry(state, 1, 4), Direction.DOWN, ctx.noises.hash(x, y, z, 104), 4);
                        }
                    }
                }
            }
        }
    }
}
