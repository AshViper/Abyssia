package com.abyssia.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.List;

/**
 * Server-side view of vent fields: which fields and vents exist near a position and how warm the water is there.
 * Fields are recomputed from the world seed on demand (nothing is stored), so this stays in sync with world
 * generation. Intended for future gameplay (heat on players, plant growth); clients track vents from blocks instead.
 */
public final class ThermalVentManager
{
    private ThermalVentManager() {}

    public static List<ThermalVentField> fieldsNear(ServerLevel level, BlockPos pos, int range)
    {
        BiomeSource biomes = level.getChunkSource().getGenerator().getBiomeSource();
        RandomState randomState = level.getChunkSource().randomState();
        return ThermalVentField.near(level.getSeed(), pos.getX() - range, pos.getZ() - range, pos.getX() + range, pos.getZ() + range,
                (x, z) -> biomes.getNoiseBiome(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z), randomState.sampler()).unwrapKey().orElse(null));
    }

    /** Water temperature from nearby vents by horizontal distance (0..1). */
    public static float temperatureAt(ServerLevel level, BlockPos pos)
    {
        float max = 0f;
        for (ThermalVentField field : fieldsNear(level, pos, 32))
        {
            for (ThermalVentField.Vent v : field.vents())
            {
                double d = Math.sqrt((double) (pos.getX() - v.x()) * (pos.getX() - v.x()) + (double) (pos.getZ() - v.z()) * (pos.getZ() - v.z()));
                max = Math.max(max, ThermalTemperature.of(v.type(), v.age().activity, d));
            }
        }
        return max;
    }
}
