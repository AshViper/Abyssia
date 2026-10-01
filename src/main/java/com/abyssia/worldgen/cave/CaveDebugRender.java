package com.abyssia.worldgen.cave;

import com.abyssia.Abyssia;
import com.abyssia.worldgen.DeepLayer;
import com.abyssia.worldgen.OceanChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Debug output for testing generation on a headless server: top-down slices and vertical sections of the world
 * rendered to PNG (map colours; water blue, gas white, light sources bright, chunk borders ticked), and block
 * counts in a box. Reading blocks generates the chunks involved, so keep the areas modest. The biome map instead
 * samples the generator directly and can cover thousands of blocks.
 */
final class CaveDebugRender
{
    private static final int WATER = 0x10284f, AIR = 0xd8dde4, BORDER_TICK = 0xff3030;

    private CaveDebugRender() {}

    private static File output(ServerLevel level, String name) throws IOException
    {
        File dir = new File(level.getServer().getServerDirectory(), Abyssia.MODID + "_debug");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        return new File(dir, name);
    }

    private static int colour(ServerLevel level, BlockPos pos, BlockState state)
    {
        if (state.isAir()) return AIR;
        if (state.is(Blocks.WATER)) return WATER;
        int rgb = state.getMapColor(level, pos) == MapColor.NONE ? 0x808080 : state.getMapColor(level, pos).col;
        // Light sources stand out, like they do down there.
        if (state.getLightEmission(level, pos) > 0) rgb = brighten(rgb, 0.5f);
        // Non-full blocks (plants, speleothems, crystals) are lighter, so they read against the rock.
        if (!state.isCollisionShapeFullBlock(level, pos)) rgb = brighten(rgb, 0.25f);
        return rgb;
    }

    private static int brighten(int rgb, float t)
    {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int shade(int rgb, double f)
    {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f), b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Top-down slice at height y; chunk borders are ticked along the image edges. */
    static String slice(ServerLevel level, BlockPos centre, int y, int radius) throws IOException
    {
        int size = radius * 2 + 1;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < size; dz++)
        {
            for (int dx = 0; dx < size; dx++)
            {
                pos.set(centre.getX() - radius + dx, y, centre.getZ() - radius + dz);
                BlockState state = level.getBlockState(pos);
                int rgb = colour(level, pos, state);
                if ((dz < 2 || dz >= size - 2) && Math.floorMod(pos.getX(), 16) == 0) rgb = BORDER_TICK;
                if ((dx < 2 || dx >= size - 2) && Math.floorMod(pos.getZ(), 16) == 0) rgb = BORDER_TICK;
                image.setRGB(dx, dz, rgb);
            }
        }
        File file = output(level, "slice_" + centre.getX() + "_" + centre.getZ() + "_y" + y + ".png");
        ImageIO.write(image, "png", file);
        return file.getAbsolutePath();
    }

    /** Vertical section through the centre along x (or z), from y0 up to y1 (image top). */
    static String section(ServerLevel level, BlockPos centre, boolean alongX, int radius, int y0, int y1) throws IOException
    {
        int width = radius * 2 + 1, height = y1 - y0 + 1;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < width; i++)
        {
            for (int y = y0; y <= y1; y++)
            {
                if (alongX) pos.set(centre.getX() - radius + i, y, centre.getZ());
                else pos.set(centre.getX(), y, centre.getZ() - radius + i);
                BlockState state = level.getBlockState(pos);
                int rgb = colour(level, pos, state);
                int along = alongX ? pos.getX() : pos.getZ();
                if ((y - y0 < 2 || y1 - y < 2) && Math.floorMod(along, 16) == 0) rgb = BORDER_TICK;
                image.setRGB(i, y1 - y, rgb);
            }
        }
        File file = output(level, "section_" + (alongX ? "x_" : "z_") + centre.getX() + "_" + centre.getZ() + ".png");
        ImageIO.write(image, "png", file);
        return file.getAbsolutePath();
    }

    /**
     * Top-down map of the biomes and the seabed around the centre, sampled every {@code step} blocks straight from the
     * generator (no chunk is generated): biome colours hill-shaded by the seabed, plus a CSV of every sample. Returns
     * the image path and each biome's share of the map. Below the bedrock band it maps the deep layer: its biomes and
     * the deep seabed from the cave network's density search (the heightmap would find the ocean world's seabed).
     */
    static String map(ServerLevel level, BlockPos centre, int radius, int step) throws IOException
    {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        BiomeSource biomes = generator.getBiomeSource();
        CaveNetwork deep = DeepLayer.isDeep(level, centre.getY()) && generator instanceof OceanChunkGenerator ocean
                ? ocean.caveNetwork(random, level.registryAccess(), level.getSeed()) : null;
        int n = radius * 2 / step + 1;
        int[] seabed = new int[n * n];
        String[] biome = new String[n * n];
        StringBuilder csv = new StringBuilder("x,z,biome,seabed\n");
        for (int j = 0; j < n; j++)
        {
            for (int i = 0; i < n; i++)
            {
                int x = centre.getX() - radius + i * step, z = centre.getZ() - radius + j * step, k = j * n + i;
                biome[k] = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(centre.getY()), QuartPos.fromBlock(z), random.sampler())
                        .unwrapKey().map(key -> key.location().toString()).orElse("?");
                seabed[k] = deep != null ? deep.seabed(x, z) : generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                csv.append(x).append(',').append(z).append(',').append(biome[k]).append(',').append(seabed[k]).append('\n');
            }
        }
        BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        Map<String, Integer> counts = new TreeMap<>();
        for (int j = 0; j < n; j++)
        {
            for (int i = 0; i < n; i++)
            {
                int k = j * n + i;
                counts.merge(biome[k], 1, Integer::sum);
                int rgb = java.awt.Color.HSBtoRGB((biome[k].hashCode() & 1023) / 1024f, 0.6f, 0.85f);
                // Light from the north-west: slopes facing it brighter.
                double slope = (seabed[k] - seabed[j * n + Math.max(0, i - 1)]) - (seabed[k] - seabed[Math.max(0, j - 1) * n + i]);
                image.setRGB(i, j, shade(rgb, 1 + Math.max(-0.45, Math.min(0.45, slope * 0.6 / step))));
            }
        }
        String name = "map_" + level.dimension().location().getPath() + (deep != null ? "_deep" : "") + "_" + centre.getX() + "_" + centre.getZ() + "_r" + radius + "_s" + step;
        ImageIO.write(image, "png", output(level, name + ".png"));
        java.nio.file.Files.writeString(output(level, name + ".csv").toPath(), csv);
        StringBuilder shares = new StringBuilder(output(level, name + ".png").getAbsolutePath());
        counts.forEach((b, c) -> shares.append(String.format("\n  %s %.1f%%", b, c * 100.0 / (n * n))));
        return shares.toString();
    }

    private static boolean touchesAir(ServerLevel level, BlockPos.MutableBlockPos pos)
    {
        for (net.minecraft.core.Direction d : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.DOWN})
        {
            if (level.getBlockState(pos.relative(d)).isAir()) return true;
        }
        return false;
    }

    /** Counts of every Abyssia block (plus water and air) in a box around the centre. */
    static Map<String, Integer> census(ServerLevel level, BlockPos centre, int radius, int y0, int y1)
    {
        Map<String, Integer> counts = new TreeMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = centre.getX() - radius; x <= centre.getX() + radius; x++)
        {
            for (int z = centre.getZ() - radius; z <= centre.getZ() + radius; z++)
            {
                for (int y = y0; y <= y1; y++)
                {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    String key;
                    if (state.isAir()) key = "air";
                    else if (state.is(Blocks.WATER)) key = "water";
                    else
                    {
                        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                        if (id == null || !id.getNamespace().equals(Abyssia.MODID)) key = id == null ? "?" : id.toString();
                        else key = id.getPath();
                    }
                    counts.merge(key, 1, Integer::sum);
                    // Lake seal check: water must never sit beside or on top of a gas pocket.
                    if (key.equals("water") && touchesAir(level, pos)) counts.merge("!water_touching_air", 1, Integer::sum);
                }
            }
        }
        return counts;
    }
}
