package com.abyssia.map;

import com.abyssia.Abyssia;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * MP01: paints the deep seabed into held deep sea maps. Server player tick END, once per map id per tick, ~256 seabed
 * columns per tick, loaded chunks only (getChunkNow); a pixel is skipped once it has a colour, so the map fills as you
 * move. The deep seabed is a patchwork of crystals, crusts, moss and ores, so a pixel reads as a map rather than a
 * speckle: every column of its 4x4 block is sampled, colours are folded into 8 map categories, the most common one
 * wins, and a pixel whose 8 neighbours mostly (5+) agree on another category takes theirs (inbox/specs/MP01-review.md).
 */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DeepMapFiller
{
    /** Seabed columns scanned per tick per map, pixel visits per tick, map size. */
    private static final int BUDGET = 256, MAX_VISITS = 1024, SIZE = 128;
    /** A pixel takes its surrounding pixels' colour when at least this many of the 8 agree (and it differs). */
    private static final int SMOOTH_VOTES = 5;
    private static final Map<Integer, State> STATES = new HashMap<>();

    private DeepMapFiller() {}

    private static final class State
    {
        long lastTick = -1;
        int cursor;
        final short[] floors = new short[SIZE * SIZE];
        /** Majority colour category of each sampled pixel (index into PALETTE, valid where floors is set). */
        final byte[] colors = new byte[SIZE * SIZE];
        /** Columns scanned so far this tick (the budget). */
        int scanned;

        State()
        {
            Arrays.fill(floors, Short.MIN_VALUE);
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event)
    {
        STATES.clear();
        EntranceLocator.reset();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (!DeepLayer.isDeep(level, player.getY())) return;
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(Items.FILLED_MAP)) continue;
            MapId mapId = stack.get(DataComponents.MAP_ID);
            if (mapId == null) continue;
            int id = mapId.id();
            if (!DeepMapIndex.get(level).contains(id)) continue;
            MapItemSavedData data = level.getMapData(mapId);
            if (data == null || data.dimension != level.dimension()) continue;
            State state = STATES.computeIfAbsent(id, k -> new State());
            long now = level.getGameTime();
            if (state.lastTick == now) continue;
            state.lastTick = now;
            fill(level, player, data, state);
        }
    }

    private static void fill(ServerLevel level, ServerPlayer player, MapItemSavedData data, State state)
    {
        int blocksPerPixel = 1 << data.scale;
        int cx = Mth.floor(player.getX() - data.centerX) / blocksPerPixel + SIZE / 2;
        int cz = Mth.floor(player.getZ() - data.centerZ) / blocksPerPixel + SIZE / 2;
        int radius = 128 / blocksPerPixel;
        int side = radius * 2 + 1, total = side * side;
        state.scanned = 0;
        for (int visits = 0; visits < MAX_VISITS && state.scanned < BUDGET; visits++)
        {
            int c = state.cursor;
            state.cursor = (state.cursor + 1) % total;
            int px = cx - radius + c % side, pz = cz - radius + c / side;
            if (px < 0 || pz < 0 || px >= SIZE || pz >= SIZE) continue;
            if (data.colors[px + pz * SIZE] != 0) continue;
            int floor = floorAt(level, data, state, px, pz);
            if (floor == Short.MIN_VALUE) continue;
            int north = pz > 0 ? floorAt(level, data, state, px, pz - 1) : Short.MIN_VALUE;
            MapColor color = PALETTE[smoothed(level, data, state, px, pz)];
            data.setColor(px, pz, color.getPackedId(brightness(floor, north)));
        }
    }

    /** The pixel's category, or its neighbours' when at least SMOOTH_VOTES of the 8 agree on another one. */
    private static int smoothed(ServerLevel level, MapItemSavedData data, State state, int px, int pz)
    {
        int own = state.colors[px + pz * SIZE];
        int[] votes = new int[PALETTE.length];
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
            {
                int x = px + dx, z = pz + dz;
                if ((dx == 0 && dz == 0) || x < 0 || z < 0 || x >= SIZE || z >= SIZE) continue;
                if (floorAt(level, data, state, x, z) != Short.MIN_VALUE) votes[state.colors[x + z * SIZE]]++;
            }
        for (int k = 0; k < votes.length; k++) if (k != own && votes[k] >= SMOOTH_VOTES) return k;
        return own;
    }

    /**
     * Seabed Y of a pixel (mean of its 4x4 columns) and, stored alongside, its majority colour category; cached.
     * Short.MIN_VALUE if the pixel's chunk is not loaded (a 4x4 block never spans two chunks).
     */
    private static int floorAt(ServerLevel level, MapItemSavedData data, State state, int px, int pz)
    {
        int i = px + pz * SIZE;
        if (state.floors[i] != Short.MIN_VALUE) return state.floors[i];
        int b = 1 << data.scale;
        int x0 = (data.centerX / b + px - SIZE / 2) * b, z0 = (data.centerZ / b + pz - SIZE / 2) * b;
        LevelChunk chunk = level.getChunkSource().getChunkNow(x0 >> 4, z0 >> 4);
        if (chunk == null) return Short.MIN_VALUE;
        int[] counts = new int[PALETTE.length];
        int sum = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < b; dx++)
            for (int dz = 0; dz < b; dz++)
            {
                int x = x0 + dx, z = z0 + dz;
                int y = DeepLayer.floorY(chunk, x, z);
                sum += y;
                pos.set(x, y, z);
                counts[category(chunk.getBlockState(pos).getMapColor(level, pos))]++;
            }
        state.scanned += b * b;
        int best = 0;
        for (int k = 1; k < counts.length; k++) if (counts[k] > counts[best]) best = k;
        int floor = Math.round(sum / (float) (b * b));
        state.floors[i] = (short) floor;
        state.colors[i] = (byte) best;
        return floor;
    }

    /** Map categories of the deep map, each drawn with one vanilla map colour (index = category). */
    private static final MapColor[] PALETTE = {
            MapColor.STONE,        // 0 rock
            MapColor.COLOR_GRAY,   // 1 dark rock
            MapColor.COLOR_BLACK,  // 2 black rock
            MapColor.SNOW,         // 3 pale (ice, salt, quartz, bone)
            MapColor.DIRT,         // 4 sediment (mud, sand, wood, yellow)
            MapColor.COLOR_GREEN,  // 5 plants, moss
            MapColor.COLOR_CYAN,   // 6 blue and cyan (crystals, glow, lapis)
            MapColor.COLOR_RED     // 7 accent (red, orange, pink, purple: vents, crusts)
    };

    /** Folds any map colour into a category by its hue, saturation and brightness. */
    static int category(MapColor color)
    {
        if (color == MapColor.NONE || color == MapColor.WATER) return 0;
        int rgb = color.col;
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        float hue = hsb[0] * 360, sat = hsb[1], val = hsb[2];
        if (sat < 0.25F) return val > 0.75F ? 3 : val > 0.38F ? 0 : val > 0.2F ? 1 : 2;
        if (hue < 15 || hue >= 280) return 7;
        if (hue < 65) return sat > 0.75F && val > 0.7F && hue < 40 ? 7 : 4;
        if (hue < 165) return 5;
        return 6;
    }

    /** 5 depth bands (deeper = darker) plus a 3-level slope against the north neighbour, folded into 4 map shades. */
    static MapColor.Brightness brightness(int floor, int north)
    {
        int depth = DeepLayer.CEILING_BOTTOM_Y - floor;
        int band = depth < 50 ? 0 : depth < 100 ? 1 : depth < 150 ? 2 : depth < 210 ? 3 : 4;
        int slope = north == Short.MIN_VALUE ? 0 : floor - north >= 2 ? 1 : floor - north <= -2 ? -1 : 0;
        int score = (2 - band) + slope;
        if (score >= 2) return MapColor.Brightness.HIGH;
        if (score >= -1) return MapColor.Brightness.NORMAL;
        return MapColor.Brightness.LOW;
    }
}
