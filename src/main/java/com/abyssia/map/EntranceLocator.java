package com.abyssia.map;

import com.abyssia.registry.ModItems;
import com.abyssia.worldgen.DeepLayer;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
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
 * MP01 abyss chart search: the nearest open deep entrance (biome tag #abyssia:deep_entrances). The biome grid scan and
 * the slit refinement run on the background executor (noise sampling only, no chunk is generated or loaded); the
 * result is handed back to the server thread, which owns all state here.
 */
public final class EntranceLocator
{
    public static final TagKey<Biome> ENTRANCES = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("abyssia", "deep_entrances"));
    private static final int RADIUS = 3000, STEP = 64, COOLDOWN_TICKS = 600, REFINE_SAMPLES = 32, REFINE_SPACING = 4, MAX_CANDIDATES = 80;
    /** Fixed search Y: in the world above the deep layer, where the fissure / rift biomes are placed. */
    private static final int SEARCH_Y = -40;

    private static final Set<UUID> IN_FLIGHT = new HashSet<>();
    private static final Map<UUID, Long> COOLDOWN_END = new HashMap<>();

    private EntranceLocator() {}

    /** Server stopped: forget searches whose callback will never run and cooldowns in that world's game time. */
    static void reset()
    {
        IN_FLIGHT.clear();
        COOLDOWN_END.clear();
    }

    public static void start(ServerLevel level, ServerPlayer player, InteractionHand hand)
    {
        MinecraftServer server = level.getServer();
        UUID id = player.getUUID();
        long now = server.overworld().getGameTime();
        if (IN_FLIGHT.contains(id) || COOLDOWN_END.getOrDefault(id, 0L) > now)
        {
            player.displayClientMessage(Component.translatable("message.abyssia.abyss_chart.cooldown"), true);
            return;
        }
        if (!DeepLayer.hasDeepLayer(level.dimensionType().effectsLocation()))
        {
            player.displayClientMessage(Component.translatable("message.abyssia.abyss_chart.none"), true);
            return;
        }
        player.displayClientMessage(Component.translatable("message.abyssia.abyss_chart.searching"), true);
        IN_FLIGHT.add(id);

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        BiomeSource source = generator.getBiomeSource();
        Climate.Sampler sampler = random.sampler();
        BlockPos origin = new BlockPos(player.getBlockX(), SEARCH_Y, player.getBlockZ());
        int ox = player.getBlockX(), oz = player.getBlockZ();
        Util.backgroundExecutor().execute(() ->
        {
            BlockPos found = null;
            try
            {
                found = search(level, generator, source, random, sampler, ox, oz);
            }
            catch (RuntimeException e)
            {
                LogUtils.getLogger().warn("Abyss chart search failed", e);
            }
            BlockPos result = found;
            server.execute(() -> finish(server, level, id, hand, result));
        });
    }

    private record Cand(int x, int z, long d2) {}

    /**
     * Background thread (noise sampling only, no chunk is generated or loaded; the generator's fluid-zone binding is
     * synchronized with thread-local caches): every entrance-biome point of a 64-block grid, nearest first, each
     * refined to the first open-slit column among its 32 nearest offsets on a 4-block grid. A biome patch can exist
     * with its slit sealed, so the search moves on to the next patch instead of failing.
     */
    private static BlockPos search(ServerLevel level, ChunkGenerator generator, BiomeSource source, RandomState random,
                                   Climate.Sampler sampler, int ox, int oz)
    {
        int n = RADIUS / STEP;
        long limit = (long) RADIUS * RADIUS;
        List<Cand> cands = new ArrayList<>();
        int qy = QuartPos.fromBlock(SEARCH_Y);
        for (int i = -n; i <= n; i++)
            for (int j = -n; j <= n; j++)
            {
                long dx = (long) i * STEP, dz = (long) j * STEP, d2 = dx * dx + dz * dz;
                if (d2 > limit) continue;
                int x = ox + (int) dx, z = oz + (int) dz;
                if (source.getNoiseBiome(QuartPos.fromBlock(x), qy, QuartPos.fromBlock(z), sampler).is(ENTRANCES))
                    cands.add(new Cand(x, z, d2));
            }
        cands.sort(Comparator.comparingLong(Cand::d2).thenComparingInt(Cand::x).thenComparingInt(Cand::z));

        List<int[]> offsets = new ArrayList<>();
        for (int a = -4; a <= 4; a++)
            for (int b = -4; b <= 4; b++) offsets.add(new int[]{a * REFINE_SPACING, b * REFINE_SPACING});
        offsets.sort(Comparator.comparingInt((int[] o) -> o[0] * o[0] + o[1] * o[1]).thenComparingInt(o -> o[0]).thenComparingInt(o -> o[1]));
        offsets = offsets.subList(0, Math.min(REFINE_SAMPLES, offsets.size()));

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

    private static void finish(MinecraftServer server, ServerLevel level, UUID id, InteractionHand hand, BlockPos entrance)
    {
        IN_FLIGHT.remove(id);
        COOLDOWN_END.put(id, server.overworld().getGameTime() + COOLDOWN_TICKS);
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) return;

        if (entrance == null)
        {
            player.displayClientMessage(Component.translatable("message.abyssia.abyss_chart.none"), true);
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ModItems.ABYSS_CHART.get())) return;

        ItemStack map = MapItem.create(level, entrance.getX(), entrance.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, new BlockPos(entrance.getX(), 64, entrance.getZ()), "+", MapDecoration.Type.RED_X);
        map.setHoverName(Component.translatable("item.abyssia.abyss_chart.filled").withStyle(s -> s.withItalic(false)));
        map.getOrCreateTagElement("display").putInt("MapColor", 0x524444);
        held.shrink(1);
        if (!player.getInventory().add(map)) player.drop(map, false);
    }
}
