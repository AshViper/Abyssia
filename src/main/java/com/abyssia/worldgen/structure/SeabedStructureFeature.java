package com.abyssia.worldgen.structure;

import com.abyssia.Config;
import com.abyssia.worldgen.OceanChunkGenerator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * One pass per chunk over the seabed structures reaching it. Two instances run at different steps, which gives the
 * generation order terrain -> structure bodies (and the terrain reshaped around them) -> ore veins and vent fields
 * -> structure dressing (plants, crystal clusters, mineral crusts) -> the biome's ordinary vegetation.
 * <p>
 * Listed in every deep biome with no biome filter: a structure crossing into another biome is still painted whole;
 * only the biome at its centre decides whether it exists.
 */
public class SeabedStructureFeature extends Feature<NoneFeatureConfiguration>
{
    private final boolean dressing;

    public SeabedStructureFeature(boolean dressing)
    {
        super(NoneFeatureConfiguration.CODEC);
        this.dressing = dressing;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
    {
        if (!Config.STRUCTURES_ENABLED.get() || !(context.chunkGenerator() instanceof OceanChunkGenerator generator)) return false;
        WorldGenLevel level = context.level();
        SeabedStructures structures = generator.seabedStructures(level.getLevel().getChunkSource().randomState(), level.registryAccess(), level.getSeed());
        if (structures.isEmpty()) return false;
        return structures.paint(level, new ChunkPos(context.origin()), dressing);
    }
}
