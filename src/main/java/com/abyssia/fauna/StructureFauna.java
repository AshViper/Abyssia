package com.abyssia.fauna;

import com.abyssia.worldgen.structure.SeabedStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * QOL01: the species the seabed structures around a position favour ({@code preferred_mobs} of the structure
 * definitions). FaunaSpawner multiplies those species' weights near the structure. Worlds without seabed structures
 * (not the ocean generator) always give the empty set. The answer is per chunk (every placed structure whose footprint
 * reaches it) and cached per level, 256 chunks, least recently used out.
 */
public final class StructureFauna
{
    private static final int CACHE_SIZE = 256;

    private static final Map<ServerLevel, Map<Long, Set<ResourceLocation>>> CACHES = new WeakHashMap<>();

    private StructureFauna() {}

    public static Set<ResourceLocation> preferredAt(ServerLevel level, BlockPos pos)
    {
        Map<Long, Set<ResourceLocation>> cache;
        synchronized (CACHES)
        {
            cache = CACHES.computeIfAbsent(level, l -> new LinkedHashMap<>(CACHE_SIZE + 1, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Set<ResourceLocation>> eldest)
                {
                    return size() > CACHE_SIZE;
                }
            });
        }
        long key = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        synchronized (cache)
        {
            Set<ResourceLocation> hit = cache.get(key);
            if (hit != null) return hit;
        }
        Set<ResourceLocation> found = compute(level, pos.getX() >> 4, pos.getZ() >> 4);
        synchronized (cache)
        {
            cache.put(key, found);
        }
        return found;
    }

    private static Set<ResourceLocation> compute(ServerLevel level, int chunkX, int chunkZ)
    {
        SeabedStructures structures = SeabedStructures.of(level);
        if (structures == null) return Set.of();
        Set<ResourceLocation> out = new HashSet<>();
        for (SeabedStructures.Candidate c : structures.placedNear(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15))
            out.addAll(c.slot().definition().preferredMobs());
        return Set.copyOf(out);
    }

    /** the weight of a species: times {@code boost} if a nearby structure prefers it (boost 1 = unchanged) */
    public static double boosted(double weight, ResourceLocation entityId, Set<ResourceLocation> preferred, double boost)
    {
        return boost != 1.0 && preferred.contains(entityId) ? weight * boost : weight;
    }
}
