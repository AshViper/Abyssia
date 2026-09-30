package com.abyssia.thermal;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Layout of one hydrothermal vent field: centre, size and every chimney, derived purely from the world seed and a
 * coarse grid cell. Any chunk asking about the same cell gets the identical field, which is what lets each chunk
 * paint only its own part of a field that spans many chunks, with no seams and no duplicates.
 */
public record ThermalVentField(int centerX, int centerZ, int radius, Kind kind, List<Vent> vents, long seed)
{
    public enum Kind { NORMAL, LARGE, ANCIENT }

    /** One chimney. {@code height} is the intact height; the top {@code collapse} fraction has crumbled away. */
    public record Vent(int x, int z, ThermalVentType type, VentAge age, int height, float width, float leanX, float leanZ,
                       float moundHeight, boolean main) {}

    @FunctionalInterface
    public interface BiomeLookup
    {
        ResourceKey<Biome> at(int x, int z);
    }

    /** Fields are at most one per cell; cell size exceeds two maximum radii plus jitter, so fields never overlap. */
    public static final int CELL_SIZE = 320;
    public static final int MAX_RADIUS = 100;
    private static final long SALT = 0x7E47F1E1D5L;

    private static final Map<ResourceKey<Biome>, Float> FIELD_CHANCE = Map.of(
            biome("thermal_vents"), 1.0f,
            biome("volcanic_deep"), 0.8f,
            biome("abyssal_trench"), 0.6f,
            biome("hadal_zone"), 0.5f,
            biome("abyssal_ocean"), 0.3f);
    private static final ResourceKey<Biome> VOLCANIC = biome("volcanic_deep");
    private static final ResourceKey<Biome> TRENCH = biome("abyssal_trench");
    private static final ResourceKey<Biome> HADAL = biome("hadal_zone");

    private static ResourceKey<Biome> biome(String name)
    {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name));
    }

    /** All fields whose area may touch the given block rectangle. */
    public static List<ThermalVentField> near(long worldSeed, int minX, int minZ, int maxX, int maxZ, BiomeLookup biomes)
    {
        List<ThermalVentField> fields = new ArrayList<>();
        int cx0 = Math.floorDiv(minX - MAX_RADIUS, CELL_SIZE), cx1 = Math.floorDiv(maxX + MAX_RADIUS, CELL_SIZE);
        int cz0 = Math.floorDiv(minZ - MAX_RADIUS, CELL_SIZE), cz1 = Math.floorDiv(maxZ + MAX_RADIUS, CELL_SIZE);
        for (int cx = cx0; cx <= cx1; cx++)
        {
            for (int cz = cz0; cz <= cz1; cz++)
            {
                ThermalVentField field = inCell(worldSeed, cx, cz, biomes);
                if (field != null && field.touches(minX, minZ, maxX, maxZ)) fields.add(field);
            }
        }
        return fields;
    }

    public boolean touches(int minX, int minZ, int maxX, int maxZ)
    {
        int dx = Math.max(0, Math.max(minX - centerX, centerX - maxX));
        int dz = Math.max(0, Math.max(minZ - centerZ, centerZ - maxZ));
        return dx * dx + dz * dz <= (radius + 8) * (radius + 8);
    }

    private static ThermalVentField inCell(long worldSeed, int cellX, int cellZ, BiomeLookup biomes)
    {
        long seed = worldSeed ^ SALT ^ (cellX * 341873128712L + cellZ * 132897987541L);
        RandomSource random = new XoroshiroRandomSource(seed);
        int margin = MAX_RADIUS + 8;
        int x = cellX * CELL_SIZE + margin + random.nextInt(CELL_SIZE - 2 * margin);
        int z = cellZ * CELL_SIZE + margin + random.nextInt(CELL_SIZE - 2 * margin);
        ResourceKey<Biome> biome = biomes.at(x, z);
        if (random.nextFloat() >= FIELD_CHANCE.getOrDefault(biome, 0f)) return null;

        Kind kind = Kind.NORMAL;
        if (biome == HADAL && random.nextDouble() < Config.ANCIENT_FIELD_CHANCE.get() * 20) kind = Kind.ANCIENT;
        else if (random.nextDouble() < Config.LARGE_FIELD_CHANCE.get() * (biome == TRENCH ? 6 : 1)) kind = Kind.LARGE;

        int minSize = Config.VENT_FIELD_MIN_SIZE.get();
        int maxSize = Math.max(minSize, Config.VENT_FIELD_MAX_SIZE.get());
        int diameter = switch (kind)
        {
            case NORMAL -> Mth.randomBetweenInclusive(random, minSize, maxSize);
            case LARGE -> Mth.randomBetweenInclusive(random, 100, 160);
            case ANCIENT -> Mth.randomBetweenInclusive(random, 140, 200);
        };
        int radius = Math.min(MAX_RADIUS, diameter / 2);

        // More chimneys in bigger fields.
        int minCount = Config.MIN_CHIMNEY_COUNT.get();
        int maxCount = Math.max(minCount, Config.MAX_CHIMNEY_COUNT.get());
        float sizeT = Mth.clamp((diameter - minSize) / (float) Math.max(1, 200 - minSize), 0f, 1f);
        int count = Mth.clamp(Math.round(Mth.lerp(sizeT, minCount, maxCount)) + random.nextInt(3) - 1, minCount, maxCount);

        List<Vent> vents = new ArrayList<>();
        vents.add(vent(random, x, z, pickType(random, biome), kind == Kind.ANCIENT ? VentAge.OLD : VentAge.ACTIVE, kind, true));
        int spacing = Math.max(4, radius / 4);
        for (int attempt = 0; attempt < count * 6 && vents.size() < count; attempt++)
        {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(random.nextDouble()) * radius * 0.8;
            int vx = x + Mth.floor(Math.cos(angle) * dist);
            int vz = z + Mth.floor(Math.sin(angle) * dist);
            boolean crowded = false;
            for (Vent other : vents)
            {
                if (Mth.square(other.x() - vx) + Mth.square(other.z() - vz) < spacing * spacing) crowded = true;
            }
            if (!crowded) vents.add(vent(random, vx, vz, pickType(random, biome), pickAge(random, kind), kind, false));
        }
        return new ThermalVentField(x, z, radius, kind, List.copyOf(vents), seed);
    }

    private static ThermalVentType pickType(RandomSource random, ResourceKey<Biome> biome)
    {
        double superheated = Config.SUPERHEATED_VENT_CHANCE.get() * (biome == VOLCANIC ? 3 : biome == TRENCH || biome == HADAL ? 1 : 0);
        double black = Config.BLACK_SMOKER_CHANCE.get(), white = Config.WHITE_SMOKER_CHANCE.get(), mineral = Config.MINERAL_VENT_CHANCE.get();
        double total = black + white + mineral + superheated;
        if (total <= 0) return ThermalVentType.WHITE_SMOKER;
        double r = random.nextDouble() * total;
        if ((r -= black) < 0) return ThermalVentType.BLACK_SMOKER;
        if ((r -= white) < 0) return ThermalVentType.WHITE_SMOKER;
        if ((r -= mineral) < 0) return ThermalVentType.MINERAL;
        return ThermalVentType.SUPERHEATED;
    }

    private static VentAge pickAge(RandomSource random, Kind kind)
    {
        float r = random.nextFloat();
        if (kind == Kind.ANCIENT) return r < 0.1f ? VentAge.ACTIVE : r < 0.55f ? VentAge.OLD : VentAge.DEAD;
        return r < 0.25f ? VentAge.YOUNG : r < 0.6f ? VentAge.ACTIVE : r < 0.85f ? VentAge.OLD : VentAge.DEAD;
    }

    private static Vent vent(RandomSource random, int x, int z, ThermalVentType type, VentAge age, Kind kind, boolean main)
    {
        int minH = Config.MIN_CHIMNEY_HEIGHT.get();
        int maxH = Math.max(minH, Config.MAX_CHIMNEY_HEIGHT.get());
        float scale = age.size * (kind == Kind.ANCIENT ? 1.6f : 1f) * (main ? 1.2f : 1f) * (type == ThermalVentType.MINERAL ? 0.4f : 1f);
        int height = Math.max(1, Math.round(Mth.randomBetweenInclusive(random, minH, maxH) * scale));
        float width = (1.2f + random.nextFloat() * 1.3f) * (kind == Kind.ANCIENT ? 1.5f : 1f) * (type == ThermalVentType.MINERAL ? 1.3f : 1f);
        float leanX = (random.nextFloat() - 0.5f) * 0.25f;
        float leanZ = (random.nextFloat() - 0.5f) * 0.25f;
        float mound = (type == ThermalVentType.MINERAL ? 4f : 2f) + random.nextFloat() * 2f + (kind == Kind.ANCIENT ? 3f : 0f);
        return new Vent(x, z, type, age, height, width, leanX, leanZ, mound, main);
    }
}
