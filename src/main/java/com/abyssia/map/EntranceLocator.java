package com.abyssia.map;

import com.abyssia.Abyssia;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MP01: finds the nearest open deep entrance (biome tag #abyssia:deep_entrances) for the Abyss Chart. The biome grid
 * scan and the height refinement run on {@link Util#backgroundExecutor()} (noise sampling only, no chunk is generated
 * or loaded); the result is handed back to the server thread.
 */
public final class EntranceLocator
{
    public static final TagKey<Biome> DEEP_ENTRANCES = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "deep_entrances"));
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int RADIUS = 3000;
    private static final int STEP = 64;
    /** Biome sampling height: above the deep layer, inside the ocean world's seabed zone. */
    private static final int SAMPLE_Y = -40;
    private static final int REFINE_SAMPLES = 32;
    private static final int REFINE_SPACING = 4;
    private static final int MAX_CANDIDATES = 80;
    private static final int COOLDOWN_TICKS = 600;
    private static final int MAP_COLOR = 0x524444;

    private static final Set<UUID> RUNNING = new HashSet<>();
    private static final Map<UUID, Integer> COOLDOWN_UNTIL = new HashMap<>();

    private EntranceLocator() {}

    private record Cand(int x, int z, long d2) {}

    /** Called on the server thread when a chart is used. */
    public static void start(ServerLevel level, ServerPlayer player, InteractionHand hand)
    {
        MinecraftServer server = level.getServer();
        UUID id = player.getUUID();
        if (RUNNING.contains(id) || COOLDOWN_UNTIL.getOrDefault(id, 0) > server.getTickCount())
        {
            player.displayClientMessage(Component.translatable("message.abyssia.abyss_chart.cooldown"), true);
            return;
        }
        if (!DeepLayer.hasDeepLayer(level.dimensionType().effectsLocation()))
        {
            player.displayClientMessage(Component.translatable("message.abyssia.abyss_chart.none"), true);
            return;
        }
        RUNNING.add(id);
        player.displayClientMessage(Component.translatable("message.abyssia.abyss_chart.searching"), true);

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource source = generator.getBiomeSource();
        RandomState random = level.getChunkSource().randomState();
        Climate.Sampler sampler = random.sampler();
        int ox = player.getBlockX(), oz = player.getBlockZ();
        Util.backgroundExecutor().execute(() ->
        {
            BlockPos found = null;
            try
            {
                found = search(level, generator, source, random, sampler, ox, oz);
            }
            catch (Throwable t)
            {
                LOGGER.error("Abyss chart search failed", t);
            }
            BlockPos result = found;
            server.execute(() -> finish(server, id, result, hand, level));
        });
    }

    /** Background thread: biome grid candidates by distance, each refined to an open slit column. */
    private static BlockPos search(ServerLevel level, ChunkGenerator generator, BiomeSource source, RandomState random,
                                   Climate.Sampler sampler, int ox, int oz)
    {
        int n = RADIUS / STEP;
        long limit = (long) RADIUS * RADIUS;
        List<Cand> cands = new ArrayList<>();
        int qy = QuartPos.fromBlock(SAMPLE_Y);
        for (int i = -n; i <= n; i++)
        {
            for (int j = -n; j <= n; j++)
            {
                long dx = (long) i * STEP, dz = (long) j * STEP;
                long d2 = dx * dx + dz * dz;
                if (d2 > limit) continue;
                int x = ox + (int) dx, z = oz + (int) dz;
                Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), qy, QuartPos.fromBlock(z), sampler);
                if (biome.is(DEEP_ENTRANCES)) cands.add(new Cand(x, z, d2));
            }
        }
        cands.sort(Comparator.comparingLong(Cand::d2).thenComparingInt(Cand::x).thenComparingInt(Cand::z));

        // The REFINE_SAMPLES nearest offsets of a spacing-4 grid (the open slit is about 7 blocks wide).
        List<int[]> offsets = new ArrayList<>();
        for (int a = -4; a <= 4; a++)
            for (int b = -4; b <= 4; b++) offsets.add(new int[] {a * REFINE_SPACING, b * REFINE_SPACING});
        offsets.sort(Comparator.comparingInt((int[] o) -> o[0] * o[0] + o[1] * o[1]).thenComparingInt(o -> o[0]).thenComparingInt(o -> o[1]));
        if (offsets.size() > REFINE_SAMPLES) offsets = offsets.subList(0, REFINE_SAMPLES);

        int tried = 0;
        for (Cand c : cands)
        {
            if (tried++ >= MAX_CANDIDATES) break;
            for (int[] o : offsets)
            {
                int x = c.x() + o[0], z = c.z() + o[1];
                int height = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                if (height < DeepLayer.TOP_Y) return new BlockPos(x, height, z);
            }
        }
        return null;
    }

    /** Server thread: cooldown, consume, hand over the map. */
    private static void finish(MinecraftServer server, UUID id, BlockPos entrance, InteractionHand hand, ServerLevel level)
    {
        RUNNING.remove(id);
        COOLDOWN_UNTIL.put(id, server.getTickCount() + COOLDOWN_TICKS);
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) return;
        if (entrance == null)
        {
            player.displayClientMessage(Component.translatable("message.abyssia.abyss_chart.none"), true);
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(com.abyssia.registry.ModItems.ABYSS_CHART.get())) return;

        ItemStack map = MapItem.create(level, entrance.getX(), entrance.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, entrance, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("item.abyssia.abyss_chart.filled"));
        map.set(DataComponents.MAP_COLOR, new MapItemColor(MAP_COLOR));

        held.consume(1, player);
        if (!player.getInventory().add(map)) player.drop(map, false);
    }

    /** Server stop: forget in-flight searches and cooldowns (they belong to the world that just closed). */
    static void reset()
    {
        RUNNING.clear();
        COOLDOWN_UNTIL.clear();
    }
}
