package com.abyssia.fauna;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where the vent cores are in a chunk, for the vent fauna's spawn attempts. Only sections whose block palette can
 * hold a vent core are scanned, and the answer is cached for a few minutes, so asking is cheap.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class VentSites
{
    private static final long FRESH_TICKS = 6000;
    private static final int MAX_PER_CHUNK = 16;
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<Entry>> CACHE = new WeakHashMap<>();

    private record Entry(long time, List<BlockPos> vents) {}

    private VentSites() {}

    /** Vent cores in a loaded chunk (empty when the chunk is not loaded). */
    public static List<BlockPos> vents(ServerLevel level, ChunkPos pos)
    {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) return List.of();
        Long2ObjectOpenHashMap<Entry> cache = CACHE.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
        long now = level.getGameTime();
        Entry entry = cache.get(pos.toLong());
        if (entry != null && now - entry.time() < FRESH_TICKS) return entry.vents();
        List<BlockPos> vents = scan(chunk);
        if (cache.size() > 4096) cache.clear();
        cache.put(pos.toLong(), new Entry(now, vents));
        return vents;
    }

    private static List<BlockPos> scan(LevelChunk chunk)
    {
        Block core = ModBlocks.THERMAL_VENT.get();
        List<BlockPos> vents = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length && vents.size() < MAX_PER_CHUNK; i++)
        {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(core))) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16 && vents.size() < MAX_PER_CHUNK; y++)
            {
                for (int z = 0; z < 16; z++)
                {
                    for (int x = 0; x < 16; x++)
                    {
                        if (section.getBlockState(x, y, z).is(core)) vents.add(new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z));
                    }
                }
            }
        }
        return List.copyOf(vents);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof ServerLevel level) CACHE.remove(level);
    }
}
