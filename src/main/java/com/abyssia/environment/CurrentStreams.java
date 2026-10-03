package com.abyssia.environment;

import com.abyssia.Config;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CU01 current streams: long curving bands of strong current, placed from the world seed and nothing else (no biome,
 * no chunk load order), so server and clients rebuild the same streams. Overworld only; separate from (and stacking
 * with) {@link NaturalCurrents}.
 * <p>
 * Generation (plain math, identical on the Forge and NeoForge branches):
 * <pre>
 * streamSalt = salt ^ 0x6375303100000000L           // salt = NaturalCurrents.saltOf(seed), 0x63753031 = "cu01"
 * layers: 0 = UPPER Y 4..52 (chance = generation_chance * 0.5), 1 = DEEP Y -330..-110 (chance = generation_chance)
 * per layer L and cell (cx, cz) of cell_size:
 *   seed = streamSalt ^ cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ (L + 1) * 0x165667B19E3779F9L
 *   r = new java.util.Random(seed)
 *   1  r.nextDouble() &gt;= chance: no stream
 *   2  y      = minY + r.nextDouble() * (maxY - minY)
 *   3  x      = cx * cell + r.nextDouble() * cell, then z likewise      (anchor = the band's midpoint)
 *   4  yaw    = r.nextDouble() * 2 pi
 *   5  pitch  = (r.nextDouble() * 2 - 1) * toRadians(10)
 *   6  length = min_length + r.nextDouble() * (max_length - min_length)
 *   7  width  = min_width + r.nextDouble() * (max_width - min_width);  radius = width / 2
 *   8  tier = r.nextDouble(): &lt; 0.25 WEAK [0.65, 0.80), &lt; 0.80 NORMAL [0.80, 1.00), else STRONG [1.00, 1.20];
 *      strength = clamp(lo + r.nextDouble() * (hi - lo), min_strength, max_strength)
 *   9  n = 4 + r.nextInt(3); dir = (cos p cos y, sin p, cos p sin y), side = (-sin y, 0, cos y)
 *      for i in 0 .. n-1: f = i / (n - 1) - 0.5, lat = (r.nextDouble() * 2 - 1) * length * 0.08,
 *                         up = (r.nextDouble() * 2 - 1) * radius * 0.5,
 *                         p_i = anchor + dir * f * length + side * lat + (0, up, 0)
 * spacing: a cell's stream is dropped when a raw stream in one of its 8 neighbours on the same layer that comes
 *   earlier in (cx, then cz) order has its anchor within 64 blocks (3D). Raw candidates are compared (not recursive).
 * centre line: uniform Catmull-Rom with the end points repeated, 12 samples per segment.
 * </pre>
 * The salt is the natural currents' one-way world-seed hash; clients get it and the server's stream settings in
 * {@code network.NaturalCurrentSaltPacket} and see no streams until then.
 */
public final class CurrentStreams
{
    /** "cu01" in ASCII: derives the streams' salt from the natural currents' one. */
    public static final long CU01_TAG = 0x6375303100000000L;
    public static final double MIN_CENTER_DISTANCE = 64.0;
    public static final double MAX_PITCH_DEGREES = 10.0;
    /** Control point offsets: sideways up to length x this, vertically up to radius x VERTICAL_BEND. */
    public static final double LATERAL_BEND = 0.08;
    public static final double VERTICAL_BEND = 0.5;
    /** Fraction of the radius from which the flow fades out (smoothstep 0.65..1). */
    public static final double EDGE_START = 0.65;
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

    /** The server's stream settings; clients receive them with the salt. */
    public record Params(double chance, int cellSize, double minLength, double maxLength, double minWidth, double maxWidth,
                         double minStrength, double maxStrength, double baseFlowSpeed, double maxFlowSpeed)
    {
        public static Params fromConfig()
        {
            double minL = Config.CURRENT_STREAM_MIN_LENGTH.get(), maxL = Config.CURRENT_STREAM_MAX_LENGTH.get();
            double minW = Config.CURRENT_STREAM_MIN_WIDTH.get(), maxW = Config.CURRENT_STREAM_MAX_WIDTH.get();
            double minS = Config.CURRENT_STREAM_MIN_STRENGTH.get(), maxS = Config.CURRENT_STREAM_MAX_STRENGTH.get();
            // Swapped min/max config values are sorted, as on NeoForge.
            return new Params(Config.CURRENT_STREAM_CHANCE.get(), Config.CURRENT_STREAM_CELL.get(),
                    Math.min(minL, maxL), Math.max(minL, maxL), Math.min(minW, maxW), Math.max(minW, maxW),
                    Math.min(minS, maxS), Math.max(minS, maxS),
                    Config.CURRENT_STREAM_BASE_SPEED.get(), Config.CURRENT_STREAM_MAX_SPEED.get());
        }

    }

    /**
     * What a stream does at one position.
     *
     * @param direction      unit flow direction (the centre line's tangent at the nearest point)
     * @param r              distance from the centre line / radius (0 on the axis, 1 at the edge)
     * @param flowMultiplier 1 - smoothstep(0.65, 1, r)
     * @param strength       the stream's strength
     * @param t              centre-line parameter of the nearest point
     */
    public record Sample(CurrentStream stream, Vec3 direction, double r, double flowMultiplier, double strength, double t) {}

    private record Settings(long streamSalt, Params params) {}

    private record CellKey(Settings settings, int layer, int cx, int cz) {}

    private static final Map<CellKey, Candidate> CANDIDATES = new ConcurrentHashMap<>();
    private static final Map<CellKey, CurrentStream> STREAMS = new ConcurrentHashMap<>();
    /** Placeholder for cells without a stream (ConcurrentHashMap holds no nulls). */
    private static final CurrentStream EMPTY = new CurrentStream(0L, List.of(Vec3.ZERO, Vec3.ZERO), 0, 0, 0);
    private static final Candidate NO_CANDIDATE = new Candidate(EMPTY, Vec3.ZERO);

    private static volatile Long clientSalt;
    private static volatile Params clientParams;
    private static volatile long serverSeed;
    private static volatile long serverSalt;

    private CurrentStreams() {}

    // ---- pure generation math -------------------------------------------------------------------------------------

    public static long streamSalt(long salt)
    {
        return salt ^ CU01_TAG;
    }

    public static long cellSeed(long streamSalt, int layer, int cx, int cz)
    {
        return streamSalt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL) ^ ((layer + 1) * 0x165667B19E3779F9L);
    }

    public static double layerChance(Params p, int layer)
    {
        return layer == Layer.UPPER.ordinal() ? p.chance() * 0.5 : p.chance();
    }

    public static double smoothstep(double edge0, double edge1, double x)
    {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    /** A raw candidate: the stream and its anchor (midpoint draw), before spacing. */
    public record Candidate(CurrentStream stream, Vec3 anchor) {}

    /** The raw candidate of one cell and layer (before spacing), or null. Draw order: see the class comment. */
    public static Candidate generateCandidate(long streamSalt, Params p, int layer, int cx, int cz)
    {
        Layer l = Layer.values()[layer];
        long seed = cellSeed(streamSalt, layer, cx, cz);
        Random r = new Random(seed);
        if (r.nextDouble() >= layerChance(p, layer)) return null;
        double y = l.minY + r.nextDouble() * (l.maxY - l.minY);
        double cell = p.cellSize();
        double x = cx * cell + r.nextDouble() * cell;
        double z = cz * cell + r.nextDouble() * cell;
        double yaw = r.nextDouble() * Math.PI * 2.0;
        double pitch = (r.nextDouble() * 2.0 - 1.0) * Math.toRadians(MAX_PITCH_DEGREES);
        double length = p.minLength() + r.nextDouble() * (p.maxLength() - p.minLength());
        double width = p.minWidth() + r.nextDouble() * (p.maxWidth() - p.minWidth());
        double radius = width / 2.0;
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
        double strength = Math.max(p.minStrength(), Math.min(p.maxStrength(), lo + r.nextDouble() * (hi - lo)));
        int n = 4 + r.nextInt(3);

        double dx = Math.cos(pitch) * Math.cos(yaw), dy = Math.sin(pitch), dz = Math.cos(pitch) * Math.sin(yaw);
        double sx = -Math.sin(yaw), sz = Math.cos(yaw);
        List<Vec3> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
        {
            double f = i / (double) (n - 1) - 0.5;
            double lat = (r.nextDouble() * 2.0 - 1.0) * length * LATERAL_BEND;
            double up = (r.nextDouble() * 2.0 - 1.0) * radius * VERTICAL_BEND;
            points.add(new Vec3(x + dx * f * length + sx * lat, y + dy * f * length + up, z + dz * f * length + sz * lat));
        }
        return new Candidate(new CurrentStream(seed, points, length, radius, strength), new Vec3(x, y, z));
    }

    /** Cells to search around a point: ceil((max_length * 0.58 + max_width / 2 + 1 + range) / cell). */
    static int cellReach(Params p, double range)
    {
        return (int) Math.ceil((p.maxLength() * 0.58 + p.maxWidth() / 2.0 + 1.0 + range) / p.cellSize());
    }

    // ---- settings & caches ----------------------------------------------------------------------------------------

    public static void setClientSettings(Long salt, Params params)
    {
        clientSalt = salt;
        clientParams = params;
        CANDIDATES.clear();
        STREAMS.clear();
    }

    private static Settings settings(Level level)
    {
        if (level.dimension() != Level.OVERWORLD) return null;
        if (level instanceof ServerLevel server)
        {
            if (!Config.CURRENT_STREAMS.get()) return null;
            long seed = server.getSeed();
            if (serverSalt == 0L || serverSeed != seed)
            {
                serverSalt = NaturalCurrents.saltOf(seed);
                serverSeed = seed;
            }
            return new Settings(streamSalt(serverSalt), Params.fromConfig());
        }
        Long salt = clientSalt;
        Params params = clientParams;
        return level.isClientSide && salt != null && params != null ? new Settings(streamSalt(salt), params) : null;
    }

    /** The settings in force for this level (server config, or what the client received), or null when there are no streams. */
    public static Params params(Level level)
    {
        Settings s = settings(level);
        return s == null ? null : s.params();
    }

    private static Candidate candidate(Settings s, int layer, int cx, int cz)
    {
        if (CANDIDATES.size() > CACHE_LIMIT) CANDIDATES.clear();
        return CANDIDATES.computeIfAbsent(new CellKey(s, layer, cx, cz), k ->
        {
            Candidate c = generateCandidate(s.streamSalt(), s.params(), layer, cx, cz);
            return c == null ? NO_CANDIDATE : c;
        });
    }

    private static CurrentStream stream(Settings s, int layer, int cx, int cz)
    {
        if (STREAMS.size() > CACHE_LIMIT) STREAMS.clear();
        CellKey key = new CellKey(s, layer, cx, cz);
        CurrentStream cached = STREAMS.get(key);
        if (cached != null) return cached;
        CurrentStream result = spaced(s, layer, cx, cz);
        STREAMS.put(key, result);
        return result;
    }

    /** The cell's raw stream, unless an earlier (cx, then cz) neighbour of the 8 has its anchor within 64 blocks. */
    private static CurrentStream spaced(Settings s, int layer, int cx, int cz)
    {
        Candidate c = candidate(s, layer, cx, cz);
        if (c == NO_CANDIDATE) return EMPTY;
        for (int ox = cx - 1; ox <= cx; ox++)
        {
            for (int oz = cz - 1; oz <= cz + 1; oz++)
            {
                if (ox == cx && oz >= cz) break; // only neighbours before this cell
                Candidate other = candidate(s, layer, ox, oz);
                if (other != NO_CANDIDATE && other.anchor().distanceTo(c.anchor()) < MIN_CENTER_DISTANCE) return EMPTY;
            }
        }
        return c.stream();
    }

    /** How far above / below its layer a stream's band can reach (pitch, vertical offsets, radius). */
    private static double verticalPad(Params p)
    {
        return p.maxLength() * 0.5 * Math.sin(Math.toRadians(MAX_PITCH_DEGREES)) + p.maxWidth() / 2.0 * (1.0 + VERTICAL_BEND) + 1.0;
    }

    private static boolean layerExists(Level level, Layer layer)
    {
        return layer != Layer.DEEP || DeepLayer.hasDeepLayer(level.dimensionType().effectsLocation());
    }

    // ---- queries --------------------------------------------------------------------------------------------------

    /** Streams whose band comes within {@code range} of the point. */
    public static List<CurrentStream> near(Level level, double x, double y, double z, double range)
    {
        List<CurrentStream> found = new ArrayList<>();
        Settings s = settings(level);
        if (s == null) return found;
        Params p = s.params();
        int reach = cellReach(p, range);
        int cx0 = Math.floorDiv((int) Math.floor(x), p.cellSize()), cz0 = Math.floorDiv((int) Math.floor(z), p.cellSize());
        double pad = range + verticalPad(p);
        for (Layer layer : Layer.values())
        {
            if (!layerExists(level, layer) || y < layer.minY - pad || y > layer.maxY + pad) continue;
            for (int cx = cx0 - reach; cx <= cx0 + reach; cx++)
            {
                for (int cz = cz0 - reach; cz <= cz0 + reach; cz++)
                {
                    CurrentStream c = stream(s, layer.ordinal(), cx, cz);
                    if (c == EMPTY || !c.roughlyNear(x, y, z, range)) continue;
                    if (c.nearest(x, y, z)[1] <= range + c.radius()) found.add(c);
                }
            }
        }
        return found;
    }

    public static List<CurrentStream> near(Level level, Vec3 pos, double range)
    {
        return near(level, pos.x, pos.y, pos.z, range);
    }

    /** Test hook: the stream whose centre line passes nearest to {@code pos}, within {@code radius} of its band, or null. */
    public static CurrentStream nearest(Level level, Vec3 pos, double radius)
    {
        return near(level, pos, radius).stream()
                .min(Comparator.comparingDouble(c -> c.nearest(pos.x, pos.y, pos.z)[1] - c.radius()))
                .orElse(null);
    }

    public static CurrentStream nearest(Level level, BlockPos pos, double radius)
    {
        return nearest(level, Vec3.atCenterOf(pos), radius);
    }

    /** The stream felt most strongly at this position (flowMultiplier x strength), or null outside every stream. */
    public static Sample sample(Level level, Vec3 pos)
    {
        return sample(level, pos.x, pos.y, pos.z);
    }

    public static Sample sample(Level level, BlockPos pos)
    {
        return sample(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static Sample sample(Level level, double x, double y, double z)
    {
        Settings s = settings(level);
        if (s == null) return null;
        Params p = s.params();
        Sample best = null;
        double bestFelt = 0.0;
        double pad = verticalPad(p);
        int reach = cellReach(p, 0.0);
        int cx0 = Math.floorDiv((int) Math.floor(x), p.cellSize()), cz0 = Math.floorDiv((int) Math.floor(z), p.cellSize());
        for (Layer layer : Layer.values())
        {
            if (y < layer.minY - pad || y > layer.maxY + pad || !layerExists(level, layer)) continue;
            for (int cx = cx0 - reach; cx <= cx0 + reach; cx++)
            {
                for (int cz = cz0 - reach; cz <= cz0 + reach; cz++)
                {
                    CurrentStream c = stream(s, layer.ordinal(), cx, cz);
                    if (c == EMPTY || !c.roughlyNear(x, y, z, 0.0)) continue;
                    Sample sample = sampleStream(c, x, y, z);
                    if (sample != null && sample.flowMultiplier() * sample.strength() > bestFelt)
                    {
                        best = sample;
                        bestFelt = sample.flowMultiplier() * sample.strength();
                    }
                }
            }
        }
        return best;
    }

    /** What one stream does at a position, or null outside its band. */
    public static Sample sampleStream(CurrentStream c, double x, double y, double z)
    {
        double[] near = c.nearest(x, y, z);
        double r = near[1] / c.radius();
        if (r >= 1.0) return null;
        double flow = 1.0 - smoothstep(EDGE_START, 1.0, r);
        if (flow <= 0.0) return null;
        return new Sample(c, c.tangent(near[0]), r, flow, c.strength(), near[0]);
    }

    /** Axis flow speed of a stream of this strength: clamp(base_flow_speed x strength, 0, max_flow_speed). */
    public static double flowSpeed(Params p, double strength)
    {
        return Math.max(0.0, Math.min(p.maxFlowSpeed(), p.baseFlowSpeed() * strength));
    }

    /** Flow velocity at the sample (direction x speed x flowMultiplier). */
    public static Vec3 velocity(Level level, Sample sample)
    {
        Params p = params(level);
        if (p == null || sample == null) return Vec3.ZERO;
        return sample.direction().scale(flowSpeed(p, sample.strength()) * sample.flowMultiplier());
    }

    /** Whether streams carry this entity (config per kind). */
    public static boolean affects(Level level, Entity entity)
    {
        if (entity instanceof Player) return Config.CURRENT_STREAM_PLAYERS.get();
        if (entity instanceof Boat) return Config.CURRENT_STREAM_BOATS.get();
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) return Config.CURRENT_STREAM_ITEMS.get();
        if (entity instanceof LivingEntity) return Config.CURRENT_STREAM_MOBS.get();
        return false;
    }

    public static boolean enabled(Level level)
    {
        return settings(level) != null;
    }
}
