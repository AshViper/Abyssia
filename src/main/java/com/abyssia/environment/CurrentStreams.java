package com.abyssia.environment;

import com.abyssia.Config;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CU01 current streams: wide, strong, curving bands of water placed from the world seed (via the natural-current salt),
 * independent of biome. Server and clients rebuild the same bands from the salt and the server's settings
 * ({@code network.NaturalCurrentSaltPacket}); nothing is stored or synced per tick. Overworld only.
 *
 * <pre>
 * Generation (plain math, identical on Forge 1.20.1 and NeoForge 1.21.1):
 *   streamSalt = salt ^ 0x6375303100000000L                      // "cu01" in the top four bytes
 *   layers: UPPER(idx 0, Y 4..52, chance = generation_chance * 0.5), DEEP(idx 1, Y -330..-110, chance = generation_chance)
 *   per cell (layer, cx, cz), cell = cell_size:
 *     seed = streamSalt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL) ^ ((layer + 1) * 0x165667B19E3779F9L)
 *     r = new java.util.Random(seed)
 *     1. if r.nextDouble() >= chance: no stream
 *     2. y      = minY + r.nextDouble() * (maxY - minY)
 *     3. x      = cx * cell + r.nextDouble() * cell;  z = cz * cell + r.nextDouble() * cell   (anchor = band midpoint)
 *     4. yaw    = r.nextDouble() * 2 PI
 *     5. pitch  = (r.nextDouble() * 2 - 1) * toRadians(10)
 *     6. length = min_length + r.nextDouble() * (max_length - min_length)
 *     7. width  = min_width + r.nextDouble() * (max_width - min_width);  radius = width / 2
 *     8. tier   = r.nextDouble(): < 0.25 WEAK [0.65, 0.80) | < 0.80 NORMAL [0.80, 1.00) | else STRONG [1.00, 1.20]
 *        strength = clamp(lo + r.nextDouble() * (hi - lo), min_strength, max_strength)
 *     9. n = 4 + r.nextInt(3)   (control points)
 *        dir  = (cos(pitch) cos(yaw), sin(pitch), cos(pitch) sin(yaw));  side = (-sin(yaw), 0, cos(yaw))
 *        for i in 0..n-1:  f = i / (n - 1) - 0.5
 *            lat = (r.nextDouble() * 2 - 1) * length * 0.08;  up = (r.nextDouble() * 2 - 1) * radius * 0.5
 *            p[i] = anchor + dir * (f * length) + side * lat + (0, up, 0)
 *   spacing: a cell's stream is dropped when one of its 8 neighbours (same layer) that comes earlier in (cx, then cz)
 *   order has a generated stream whose anchor is closer than 64 blocks (3D). Neighbours are compared before their own
 *   spacing check, so the result depends only on the seed and cell coordinates.
 *   centreline: uniform Catmull-Rom through p[0..n-1].
 * Query: r = distance to centreline / radius (r >= 1: outside);  flowMultiplier = 1 - smoothstep(0.65, 1, r).
 * </pre>
 * Terrain is ignored by the shape; pushing and particles skip anything that is not water.
 */
public final class CurrentStreams
{
    public static final long CU01_SALT = 0x6375303100000000L;
    public static final double MIN_SPACING = 64.0;
    public static final double UPPER_CHANCE_SCALE = 0.5;
    private static final int CACHE_LIMIT = 16384;

    public enum Layer
    {
        UPPER(4, 52),
        DEEP(-330, -110);

        public final int minY, maxY;

        Layer(int minY, int maxY)
        {
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    /** Everything generation and pushing depend on: the server's values on both sides. */
    public record Settings(long salt, double chance, int cell, double minLength, double maxLength, double minWidth, double maxWidth,
                           double minStrength, double maxStrength, double baseSpeed, double maxSpeed)
    {
        /** How far a stream can reach from its anchor (half length + lateral wander + radius), plus a block. */
        double reach()
        {
            return maxLength * 0.5 + maxLength * 0.08 + maxWidth * 0.5 + 1.0;
        }

        /** How far above / below its layer a stream can reach. */
        double verticalReach()
        {
            return maxLength * 0.5 * Math.sin(Math.toRadians(10)) + maxWidth * 0.75 + 1.0;
        }

        double chance(Layer layer)
        {
            return layer == Layer.UPPER ? chance * UPPER_CHANCE_SCALE : chance;
        }
    }

    /** What a stream does at one position. */
    public record Sample(CurrentStream stream, Vec3 direction, double r, double flowMultiplier, double strength)
    {
        /** Target drift speed (blocks/tick) here: clamp(base x strength, 0, max) x flowMultiplier. */
        public double speed(Settings settings)
        {
            return Math.max(0.0, Math.min(settings.maxSpeed(), settings.baseSpeed() * strength)) * flowMultiplier;
        }

        public Vec3 velocity(Settings settings)
        {
            return direction.scale(speed(settings));
        }
    }

    private record CellKey(Settings settings, int layer, int cx, int cz) {}

    private static final Map<CellKey, CurrentStream> RAW = new ConcurrentHashMap<>();
    private static final Map<CellKey, CurrentStream> FINAL = new ConcurrentHashMap<>();
    private static final CurrentStream EMPTY = CurrentStream.of(0L, List.of(Vec3.ZERO, Vec3.ZERO), 0, 0, 0, Vec3.ZERO);

    private static volatile Settings clientSettings;

    private CurrentStreams() {}

    // ---- settings ----------------------------------------------------------------------------------------------

    public static void setClientSettings(Settings settings)
    {
        clientSettings = settings;
        RAW.clear();
        FINAL.clear();
    }

    /** The server's settings from its config (salt of the given seed). */
    public static Settings serverSettings(long seed)
    {
        double minL = Config.STREAM_MIN_LENGTH.get(), maxL = Config.STREAM_MAX_LENGTH.get();
        double minW = Config.STREAM_MIN_WIDTH.get(), maxW = Config.STREAM_MAX_WIDTH.get();
        double minS = Config.STREAM_MIN_STRENGTH.get(), maxS = Config.STREAM_MAX_STRENGTH.get();
        return new Settings(NaturalCurrents.saltOf(seed), Config.STREAM_CHANCE.get(), Config.STREAM_CELL_SIZE.get(),
                Math.min(minL, maxL), Math.max(minL, maxL), Math.min(minW, maxW), Math.max(minW, maxW),
                Math.min(minS, maxS), Math.max(minS, maxS), Config.STREAM_BASE_SPEED.get(), Config.STREAM_MAX_SPEED.get());
    }

    private static volatile long cachedSeed;
    private static volatile Settings cachedServer;

    /** The level's settings, or null where there are no streams (disabled, not the overworld, client before login). */
    public static Settings settings(Level level)
    {
        if (level.dimension() != Level.OVERWORLD) return null;
        if (level instanceof ServerLevel server)
        {
            if (!Config.STREAMS_ENABLED.get()) return null;
            long seed = server.getSeed();
            Settings s = serverSettings(seed);
            Settings cached = cachedServer;
            // Keep one instance while nothing changed, so the cell cache keys stay equal cheaply.
            if (cached != null && cachedSeed == seed && cached.equals(s)) return cached;
            cachedServer = s;
            cachedSeed = seed;
            return s;
        }
        return level.isClientSide ? clientSettings : null;
    }

    // ---- generation --------------------------------------------------------------------------------------------

    public static long cellSeed(long salt, int layer, int cx, int cz)
    {
        return (salt ^ CU01_SALT) ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL) ^ ((layer + 1) * 0x165667B19E3779F9L);
    }

    /** The stream rolled for a cell before the spacing rule, or null. Pure function of the settings and cell. */
    public static CurrentStream generate(Settings s, Layer layer, int cx, int cz)
    {
        long seed = cellSeed(s.salt(), layer.ordinal(), cx, cz);
        Random r = new Random(seed);
        if (r.nextDouble() >= s.chance(layer)) return null;
        double y = layer.minY + r.nextDouble() * (layer.maxY - layer.minY);
        double x = cx * (double) s.cell() + r.nextDouble() * s.cell();
        double z = cz * (double) s.cell() + r.nextDouble() * s.cell();
        double yaw = r.nextDouble() * Math.PI * 2.0;
        double pitch = (r.nextDouble() * 2.0 - 1.0) * Math.toRadians(10.0);
        double length = s.minLength() + r.nextDouble() * (s.maxLength() - s.minLength());
        double width = s.minWidth() + r.nextDouble() * (s.maxWidth() - s.minWidth());
        double radius = width * 0.5;
        double tier = r.nextDouble();
        double lo, hi;
        if (tier < 0.25)
        {
            lo = 0.65;
            hi = 0.80;
        }
        else if (tier < 0.80)
        {
            lo = 0.80;
            hi = 1.00;
        }
        else
        {
            lo = 1.00;
            hi = 1.20;
        }
        double strength = Math.max(s.minStrength(), Math.min(s.maxStrength(), lo + r.nextDouble() * (hi - lo)));
        int n = 4 + r.nextInt(3);
        Vec3 anchor = new Vec3(x, y, z);
        Vec3 dir = new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw));
        Vec3 side = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        List<Vec3> control = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
        {
            double f = i / (double) (n - 1) - 0.5;
            double lat = (r.nextDouble() * 2.0 - 1.0) * length * 0.08;
            double up = (r.nextDouble() * 2.0 - 1.0) * radius * 0.5;
            control.add(anchor.add(dir.scale(f * length)).add(side.scale(lat)).add(0.0, up, 0.0));
        }
        return CurrentStream.of(seed, control, length, radius, strength, anchor);
    }

    private static CurrentStream raw(Settings s, Layer layer, int cx, int cz)
    {
        if (RAW.size() > CACHE_LIMIT) RAW.clear();
        return RAW.computeIfAbsent(new CellKey(s, layer.ordinal(), cx, cz), k ->
        {
            CurrentStream c = generate(s, layer, cx, cz);
            return c == null ? EMPTY : c;
        });
    }

    /** The cell's stream after the spacing rule, or null. */
    public static CurrentStream cell(Settings s, Layer layer, int cx, int cz)
    {
        if (FINAL.size() > CACHE_LIMIT) FINAL.clear();
        CurrentStream c = FINAL.computeIfAbsent(new CellKey(s, layer.ordinal(), cx, cz), k ->
        {
            CurrentStream own = raw(s, layer, cx, cz);
            if (own == EMPTY) return EMPTY;
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    // Only neighbours earlier in (cx, cz) order win.
                    if (dx > 0 || (dx == 0 && dz >= 0)) continue;
                    CurrentStream other = raw(s, layer, cx + dx, cz + dz);
                    if (other != EMPTY && other.center().distanceTo(own.center()) < MIN_SPACING) return EMPTY;
                }
            }
            return own;
        });
        return c == EMPTY ? null : c;
    }

    private static boolean layerExists(Level level, Layer layer)
    {
        return layer != Layer.DEEP || DeepLayer.hasDeepLayer(level.dimensionType().effectsLocation());
    }

    // ---- queries -----------------------------------------------------------------------------------------------

    public static double smoothstep(double edge0, double edge1, double x)
    {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    /** Streams whose band comes within {@code range} of the point. */
    public static List<CurrentStream> near(Level level, double x, double y, double z, double range)
    {
        List<CurrentStream> found = new ArrayList<>();
        Settings s = settings(level);
        if (s == null) return found;
        double vr = s.verticalReach() + range;
        int span = (int) Math.ceil((s.reach() + range) / s.cell());
        int cx = Math.floorDiv((int) Math.floor(x), s.cell()), cz = Math.floorDiv((int) Math.floor(z), s.cell());
        for (Layer layer : Layer.values())
        {
            if (y < layer.minY - vr || y > layer.maxY + vr || !layerExists(level, layer)) continue;
            for (int dx = -span; dx <= span; dx++)
            {
                for (int dz = -span; dz <= span; dz++)
                {
                    CurrentStream c = cell(s, layer, cx + dx, cz + dz);
                    if (c == null) continue;
                    if (c.center().distanceTo(new Vec3(x, y, z)) > c.reach() + range) continue;
                    if (c.nearest(x, y, z).distance() <= range + c.radius()) found.add(c);
                }
            }
        }
        return found;
    }

    /** The stream felt most at this position (largest strength x flowMultiplier), or null outside every stream. */
    public static Sample sample(Level level, double x, double y, double z)
    {
        Settings s = settings(level);
        if (s == null) return null;
        Sample best = null;
        double bestFelt = 0.0;
        for (CurrentStream c : near(level, x, y, z, 0.0))
        {
            CurrentStream.Nearest n = c.nearest(x, y, z);
            double r = n.distance() / c.radius();
            if (r >= 1.0) continue;
            double flow = 1.0 - smoothstep(0.65, 1.0, r);
            if (flow * c.strength() > bestFelt)
            {
                bestFelt = flow * c.strength();
                best = new Sample(c, n.tangent(), r, flow, c.strength());
            }
        }
        return best;
    }

    public static Sample sample(Level level, BlockPos pos)
    {
        return sample(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** Test hook: the stream whose centreline is nearest to {@code pos} within {@code radius} blocks, or null. */
    public static CurrentStream nearest(Level level, BlockPos pos, double radius)
    {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        CurrentStream best = null;
        double bestD = Double.MAX_VALUE;
        for (CurrentStream c : near(level, x, y, z, radius))
        {
            double d = c.nearest(x, y, z).distance();
            if (d < bestD)
            {
                bestD = d;
                best = c;
            }
        }
        return best;
    }
}
