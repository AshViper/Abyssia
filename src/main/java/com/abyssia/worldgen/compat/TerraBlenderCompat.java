package com.abyssia.worldgen.compat;

import com.abyssia.worldgen.LayeredBiomeSource;
import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.worldgen.IExtendedBiomeSource;
import terrablender.worldgen.IExtendedNoiseGeneratorSettings;
import terrablender.worldgen.IExtendedParameterList;

import java.util.List;

/**
 * TerraBlender compatibility (Biomes O' Plenty, Oh The Biomes We've Gone and other region-based biome mods) for the
 * normal world type. Only loaded when TerraBlender is present.
 * <p>
 * TerraBlender sets up only overworlds whose biome source is a vanilla {@code MultiNoiseBiomeSource}; Abyssia's normal
 * world uses {@link LayeredBiomeSource} (vanilla preset above Y -64, deep biomes below), so it would be skipped. This
 * does the same set-up for the upper layer: regions on the upper parameter list, the mods' biomes in the possible-biome
 * list (feature order), and TerraBlender's namespaced surface rules on Abyssia's overworld noise settings (biomes of
 * other namespaces, Abyssia's deep ones included, keep the original rule). The deep layer and the ocean world type are
 * untouched.
 */
public final class TerraBlenderCompat
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private TerraBlenderCompat()
    {
    }

    public static void init()
    {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, TerraBlenderCompat::onServerAboutToStart);
    }

    @SuppressWarnings("unchecked")
    private static void onServerAboutToStart(ServerAboutToStartEvent event)
    {
        MinecraftServer server = event.getServer();
        RegistryAccess access = server.registryAccess();
        LevelStem stem = access.registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator generator)
                || !(generator.getBiomeSource() instanceof LayeredBiomeSource layered)) return;

        ((IExtendedNoiseGeneratorSettings) (Object) generator.generatorSettings().value()).setRegionType(RegionType.OVERWORLD);

        // MultiNoiseBiomeSource.parameters() (SRG m_274409_) is private in 1.20.1; TerraBlender's own access transformer
        // only applies at run time, so it is called once here by reflection.
        Climate.ParameterList<Holder<Biome>> list;
        try
        {
            list = (Climate.ParameterList<Holder<Biome>>) ObfuscationReflectionHelper
                    .findMethod(MultiNoiseBiomeSource.class, "m_274409_").invoke(layered.upper());
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            LOGGER.error("Abyssia: TerraBlender compat could not read the upper biome parameters; biome mods stay out of the normal world", e);
            return;
        }
        IExtendedParameterList<Holder<Biome>> parameters = (IExtendedParameterList<Holder<Biome>>) (Object) list;
        if (!parameters.isInitialized()) parameters.initializeForTerraBlender(access, RegionType.OVERWORLD, server.getWorldData().worldGenOptions().seed());

        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        ImmutableList.Builder<Holder<Biome>> added = ImmutableList.builder();
        Regions.get(RegionType.OVERWORLD).forEach(region -> region.addBiomes(biomes,
                point -> biomes.getHolder(point.getSecond()).ifPresent(added::add)));
        List<Holder<Biome>> regionBiomes = added.build();
        ((IExtendedBiomeSource) layered.upper()).appendDeferredBiomesList(regionBiomes);
        ((IExtendedBiomeSource) layered).appendDeferredBiomesList(regionBiomes);

        // TerraBlender 3 on 1.20.1 shares one (locked) uniqueness area between threads, as it does for vanilla's overworld.
        layered.setUpperLookup(parameters::findValuePositional);
        LOGGER.info("Abyssia: TerraBlender regions ({}) applied to the upper layer of the normal world", Regions.getCount(RegionType.OVERWORLD));
    }
}
