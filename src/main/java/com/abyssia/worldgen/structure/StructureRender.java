package com.abyssia.worldgen.structure;

import com.abyssia.Abyssia;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Debug pictures of generated seabed (generates the chunks it looks at): a hill-shaded top view of the floor
 * coloured by block, and a vertical section coloured by block. Structure footprints are outlined on the top view.
 */
final class StructureRender
{
    private static final int WATER = 0x0B1A33, AIR = 0x000000;

    private StructureRender() {}

    /** Top view, {@code scale} pixels per block: floor block colour, lit from the north-west by the height slope. */
    static String top(ServerLevel level, BlockPos centre, int radius, SeabedStructures structures, boolean plants) throws IOException
    {
        int size = radius * 2 + 1, scale = size <= 256 ? 3 : size <= 512 ? 2 : 1;
        int[][] height = new int[size][size];
        int[][] colour = new int[size][size];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                int x = centre.getX() - radius + i, z = centre.getZ() - radius + j;
                // Topmost solid block: plants are skipped (they would hide the landforms) unless asked for.
                int y = level.getMaxBuildHeight() - 1;
                var chunk = level.getChunk(x >> 4, z >> 4);
                while (y > level.getMinBuildHeight())
                {
                    BlockState s = chunk.getBlockState(pos.set(x, y, z));
                    if (plants ? !s.isAir() && !s.is(net.minecraft.world.level.block.Blocks.WATER) : s.isSolid()) break;
                    y--;
                }
                height[i][j] = y;
                colour[i][j] = colour(level, pos.set(x, y, z));
            }
        }
        BufferedImage img = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                int dh = height[i][j] - height[Math.max(0, i - 1)][Math.max(0, j - 1)];
                double light = Mth.clamp(1 + dh * 0.12, 0.45, 1.5);
                int rgb = shade(colour[i][j], light);
                for (int a = 0; a < scale; a++) for (int b = 0; b < scale; b++) img.setRGB(i * scale + a, j * scale + b, rgb);
            }
        }
        // Footprint rings of the structures placed here.
        for (SeabedStructures.Candidate c : structures.placedNear(centre.getX() - radius, centre.getZ() - radius, centre.getX() + radius, centre.getZ() + radius))
        {
            int r = c.slot().definition().footprintRadius();
            for (int k = 0; k < 360; k++)
            {
                int px = (int) Math.round((c.x() + Math.cos(Math.toRadians(k)) * r - centre.getX() + radius) * scale);
                int pz = (int) Math.round((c.z() + Math.sin(Math.toRadians(k)) * r - centre.getZ() + radius) * scale);
                if (px >= 0 && pz >= 0 && px < img.getWidth() && pz < img.getHeight()) img.setRGB(px, pz, 0xFFFF00);
            }
        }
        return write(level, img, "structures_top_" + centre.getX() + "_" + centre.getZ());
    }

    /** Vertical section along x (or z) through the centre, {@code scale} pixels per block. */
    static String section(ServerLevel level, BlockPos centre, boolean alongX, int radius, int y0, int y1) throws IOException
    {
        int width = radius * 2 + 1, height = y1 - y0 + 1, scale = Math.max(1, Math.min(4, 1200 / Math.max(width, height)));
        BufferedImage img = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_RGB);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < width; i++)
        {
            int x = alongX ? centre.getX() - radius + i : centre.getX(), z = alongX ? centre.getZ() : centre.getZ() - radius + i;
            for (int y = y0; y <= y1; y++)
            {
                int rgb = colour(level, pos.set(x, y, z));
                for (int a = 0; a < scale; a++) for (int b = 0; b < scale; b++) img.setRGB(i * scale + a, (y1 - y) * scale + b, rgb);
            }
        }
        return write(level, img, "structures_section_" + (alongX ? "x" : "z") + "_" + centre.getX() + "_" + centre.getZ());
    }

    private static int colour(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return AIR;
        MapColor map = state.getMapColor(level, pos);
        if (map == MapColor.WATER && state.getFluidState().isSource() && !state.isSolid()) return WATER;
        return map == MapColor.NONE ? 0xFF00FF : map.col;
    }

    private static int shade(int rgb, double light)
    {
        int r = Mth.clamp((int) (((rgb >> 16) & 255) * light), 0, 255), g = Mth.clamp((int) (((rgb >> 8) & 255) * light), 0, 255),
                b = Mth.clamp((int) ((rgb & 255) * light), 0, 255);
        return r << 16 | g << 8 | b;
    }

    private static String write(ServerLevel level, BufferedImage img, String name) throws IOException
    {
        File dir = new File(level.getServer().getServerDirectory(), Abyssia.MODID + "_debug");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File file = new File(dir, name + ".png");
        ImageIO.write(img, "png", file);
        return file.getPath();
    }
}
