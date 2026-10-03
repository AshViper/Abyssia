package com.abyssia.environment;

import com.abyssia.Config;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Natural currents: discrete streams of fast water (direction, strength 0..1, radius) placed from the world seed, on
 * top of the gentle {@link OceanCurrentManager} field that flows everywhere. Overworld only.
 * <p>
 * The world is cut into {@value #CELL}-block cells; each cell and depth band (the upper ocean, and the deep layer where
 * there is one) holds at most one stream, rolled from a salt and the cell coordinates, so the same seed always gives the
 * same streams and nothing is stored. The salt is a one-way hash of the world seed: the server computes it, clients
 * receive it on login ({@code network.NaturalCurrentSaltPacket}) and see no streams until then.
 * Streams ignore terrain; the parts that run through rock simply carry nothing.
 */
public final class NaturalCurrents
{
    public static final int CELL = 192;
    /** Furthest a stream reaches from its cell's interior: half the longest stream plus the widest radius. */
    private static final int MAX_REACH = 80 + 24;
    private static final int EDGE_MARGIN = 24;
    private static final int CACHE_LIMIT = 16384;

    private enum Band
    {
        UPPER(4, 52),
        DEEP(-330, -110);

        final int minY, maxY;

        Band(int minY, int maxY)
        {
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    /** The chance is part of the key: clients use the server's, which reaches them with the salt. */
    private record CellKey(long salt, double chance, int band, int cx, int cz) {}

    private static final Map<CellKey, NaturalCurrent> CELLS = new ConcurrentHashMap<>();
    /** Placeholder for cells without a stream (ConcurrentHashMap holds no nulls). */
    private static final NaturalCurrent EMPTY = new NaturalCurrent(NaturalCurrent.Type.WEAK, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0f, 1f, 1f);

    /** Salt and stream chance the client received from the server; salt null before login, after logout or when disabled. */
    private static volatile Long clientSalt;
    private static volatile double clientChance;
    private static volatile double clientMaxSpeed;
    private static volatile long serverSeed;
    private static volatile long serverSalt;

    private NaturalCurrents() {}

    /** One-way hash of a world seed, so clients can place streams without learning the seed. */
    public static long saltOf(long seed)
    {
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(ByteBuffer.allocate(24).putLong(seed).put("abyssia:currents".getBytes(), 0, 16).array());
            return ByteBuffer.wrap(hash).getLong();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public static void setClientSettings(Long salt, double chance, double maxSpeed)
    {
        clientSalt = salt;
        clientChance = chance;
        clientMaxSpeed = maxSpeed;
        CELLS.clear();
    }

    private record Settings(long salt, double chance) {}

    /** The level's salt and chance, or null where there are no natural currents (disabled, not the overworld, client without salt). */
    private static Settings settings(Level level)
    {
        if (level.dimension() != Level.OVERWORLD) return null;
        if (level instanceof ServerLevel server)
        {
            if (!Config.NATURAL_CURRENTS.get()) return null;
            long seed = server.getSeed();
            if (serverSalt == 0L || serverSeed != seed)
            {
                serverSalt = saltOf(seed);
                serverSeed = seed;
            }
            return new Settings(serverSalt, Config.NATURAL_CURRENT_CHANCE.get());
        }
        Long salt = clientSalt;
        return level.isClientSide && salt != null ? new Settings(salt, clientChance) : null;
    }

    /** Drift speed at strength 1 (blocks per tick): the server's setting on both sides, so prediction matches. */
    public static double maxSpeed(Level level)
    {
        return level.isClientSide ? clientMaxSpeed : Config.NATURAL_CURRENT_MAX_SPEED.get();
    }

    /** The salt to send to clients joining this server, or null when natural currents are off. */
    public static Long saltFor(ServerLevel level)
    {
        return Config.NATURAL_CURRENTS.get() ? saltOf(level.getSeed()) : null;
    }

    /**
     * The strongest current felt at this position: a natural current or a CU01 {@link CurrentStreams current stream},
     * whichever has the higher local strength; {@link CurrentData#NONE} outside both.
     */
    public static CurrentData getCurrentAt(Level level, BlockPos pos)
    {
        return getCurrentAt(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static CurrentData getCurrentAt(Level level, double x, double y, double z)
    {
        CurrentData natural = getNaturalCurrentAt(level, x, y, z);
        CurrentStreams.Sample sample = CurrentStreams.sample(level, x, y, z);
        if (sample == null || sample.flowMultiplier() * sample.strength() <= natural.getLocalStrength()) return natural;
        // A CU01 stream as a synthetic natural current: a point at the nearest centre-line point, flowing along its tangent.
        CurrentStream stream = sample.stream();
        Vec3 at = stream.point(sample.t());
        NaturalCurrent.Type type = switch (stream.tier())
        {
            case WEAK -> NaturalCurrent.Type.WEAK;
            case NORMAL -> NaturalCurrent.Type.NORMAL;
            case STRONG -> NaturalCurrent.Type.STRONG;
        };
        float radius = (float) stream.radius();
        return new CurrentData(new NaturalCurrent(type, at, at, sample.direction(), (float) stream.strength(), radius, radius), (float) sample.flowMultiplier());
    }

    /** The strongest natural current (not CU01 streams) felt at this position, or {@link CurrentData#NONE}. */
    public static CurrentData getNaturalCurrentAt(Level level, double x, double y, double z)
    {
        Settings settings = settings(level);
        Band band = band(level, y);
        if (settings == null || band == null) return CurrentData.NONE;
        int cx = Math.floorDiv((int) Math.floor(x), CELL), cz = Math.floorDiv((int) Math.floor(z), CELL);
        NaturalCurrent best = null;
        float bestFelt = 0f, bestFalloff = 0f;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                NaturalCurrent c = cell(settings, band, cx + dx, cz + dz);
                if (c == EMPTY) continue;
                float falloff = c.falloff(x, y, z);
                if (falloff * c.strength() > bestFelt)
                {
                    best = c;
                    bestFelt = falloff * c.strength();
                    bestFalloff = falloff;
                }
            }
        }
        return best == null ? CurrentData.NONE : new CurrentData(best, bestFalloff);
    }

    /** Streams whose axis passes within {@code range} (+ their radius) of the point, for particles and debugging. */
    public static List<NaturalCurrent> near(Level level, double x, double y, double z, double range)
    {
        List<NaturalCurrent> found = new ArrayList<>();
        Settings settings = settings(level);
        if (settings == null) return found;
        int reach = (int) Math.ceil(range) + MAX_REACH;
        int cx0 = Math.floorDiv((int) Math.floor(x) - reach, CELL), cx1 = Math.floorDiv((int) Math.floor(x) + reach, CELL);
        int cz0 = Math.floorDiv((int) Math.floor(z) - reach, CELL), cz1 = Math.floorDiv((int) Math.floor(z) + reach, CELL);
        for (Band band : Band.values())
        {
            if (!bandExists(level, band) || y < band.minY - range - 24 || y > band.maxY + range + 24) continue;
            for (int cx = cx0; cx <= cx1; cx++)
            {
                for (int cz = cz0; cz <= cz1; cz++)
                {
                    NaturalCurrent c = cell(settings, band, cx, cz);
                    if (c != EMPTY && c.axisDistance(x, y, z) <= range + c.radius()) found.add(c);
                }
            }
        }
        return found;
    }

    private static Band band(Level level, double y)
    {
        if (y >= Band.UPPER.minY - 24 && y <= Band.UPPER.maxY + 24) return Band.UPPER;
        if (y >= Band.DEEP.minY - 24 && y <= Band.DEEP.maxY + 24 && bandExists(level, Band.DEEP)) return Band.DEEP;
        return null;
    }

    private static boolean bandExists(Level level, Band band)
    {
        return band != Band.DEEP || DeepLayer.hasDeepLayer(level.dimensionType().effectsLocation());
    }

    private static NaturalCurrent cell(Settings settings, Band band, int cx, int cz)
    {
        if (CELLS.size() > CACHE_LIMIT) CELLS.clear();
        return CELLS.computeIfAbsent(new CellKey(settings.salt(), settings.chance(), band.ordinal(), cx, cz), NaturalCurrents::generate);
    }

    private static NaturalCurrent generate(CellKey key)
    {
        long seed = key.salt() ^ (key.cx() * 0x9E3779B97F4A7C15L) ^ (key.cz() * 0xC2B2AE3D27D4EB4FL) ^ ((key.band() + 1) * 0x165667B19E3779F9L);
        RandomSource random = new XoroshiroRandomSource(seed);
        if (random.nextDouble() >= key.chance()) return EMPTY;

        Band band = Band.values()[key.band()];
        double cx = key.cx() * (double) CELL + EDGE_MARGIN + random.nextInt(CELL - 2 * EDGE_MARGIN);
        double cz = key.cz() * (double) CELL + EDGE_MARGIN + random.nextInt(CELL - 2 * EDGE_MARGIN);
        double cy = band.minY + random.nextInt(band.maxY - band.minY + 1);

        float roll = random.nextFloat();
        NaturalCurrent.Type type = roll < 0.45f ? NaturalCurrent.Type.WEAK : roll < 0.8f ? NaturalCurrent.Type.NORMAL : NaturalCurrent.Type.STRONG;
        float strength = type.minStrength + random.nextFloat() * (type.maxStrength - type.minStrength);
        // Strong currents are narrow jets, weak ones broad drifts.
        float radius = switch (type)
        {
            case WEAK -> 12 + random.nextInt(13);
            case NORMAL -> 9 + random.nextInt(10);
            case STRONG -> 6 + random.nextInt(8);
        };
        float height = radius * 0.6f;

        double angle = random.nextDouble() * Math.PI * 2.0;
        Vec3 direction = new Vec3(Math.cos(angle), (random.nextDouble() - 0.5) * 0.2, Math.sin(angle)).normalize();
        // Mostly long channels; a few round eddies that push one way within a ball of water.
        double length = random.nextFloat() < 0.15f ? 0.0 : 48 + random.nextInt(113);
        Vec3 center = new Vec3(cx, cy, cz);
        Vec3 half = direction.scale(length / 2.0);
        return new NaturalCurrent(type, center.subtract(half), center.add(half), direction, strength, radius, height);
    }
}
