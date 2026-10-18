package com.abyssia.worldgen;

import com.abyssia.Abyssia;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

/**
 * Picks the initial world spawn of the Abyssia ocean world over the shallows.
 *
 * <p>Vanilla's spawn finder weighs the distance from the origin more heavily than the climate, so the
 * spawn_target in noise_settings/planet alone cannot guarantee that the spawn lies over
 * {@code abyssia:shallows}; players often start above a deep seabed. This handler searches rings around
 * the origin (every 64 blocks, up to 4096) for a shallows column whose seabed is at Y30 or higher with
 * water above it, and cancels vanilla's spawn logic when one is found.
 *
 * <p>Cost: the biome check samples the biome source only (no chunk loading, cheap); the base height is
 * computed only for candidates that are already in the shallows.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class OceanWorldSpawn
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceKey<Biome> SHALLOWS = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "shallows"));
    private static final int STEP = 64;
    private static final int MAX_RADIUS = 4096;
    private static final int MIN_FLOOR_Y = 31;
    private static final int WATER_MARGIN = 3;

    private OceanWorldSpawn() {}

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
            || !DeepLayer.OCEAN_WORLD_EFFECTS.equals(level.dimensionType().effectsLocation()))
        {
            return;
        }
        long start = System.nanoTime();
        try
        {
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            RandomState randomState = level.getChunkSource().randomState();
            int maxFloor = generator.getSeaLevel() - WATER_MARGIN;
            for (int r = 0; r <= MAX_RADIUS; r += STEP)
            {
                int samples = r == 0 ? 1 : Math.max(1, (int) Math.ceil(2.0 * Math.PI * r / STEP));
                for (int i = 0; i < samples; i++)
                {
                    double angle = 2.0 * Math.PI * i / samples;
                    int x = (int) Math.round(Math.cos(angle) * r);
                    int z = (int) Math.round(Math.sin(angle) * r);
                    boolean shallows = generator.getBiomeSource()
                        .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(40), QuartPos.fromBlock(z), randomState.sampler())
                        .is(SHALLOWS);
                    if (!shallows)
                    {
                        continue;
                    }
                    int h = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
                    if (h >= MIN_FLOOR_Y && h <= maxFloor)
                    {
                        ServerLevelData settings = event.getSettings();
                        settings.setSpawn(new BlockPos(x, generator.getSeaLevel() + 1, z), 0.0F);
                        event.setCanceled(true);
                        LOGGER.info("Ocean world spawn set to ({}, {}, {}) over shallows, seabed Y{} ({} ms)",
                            x, generator.getSeaLevel() + 1, z, h - 1, (System.nanoTime() - start) / 1_000_000L);
                        return;
                    }
                }
            }
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("Ocean world spawn search failed, falling back to vanilla", e);
        }
    }
}
