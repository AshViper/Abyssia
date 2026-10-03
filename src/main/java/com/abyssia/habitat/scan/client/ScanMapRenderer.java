package com.abyssia.habitat.scan.client;

import com.abyssia.habitat.scan.ScanConsoleBlockEntity;
import com.abyssia.habitat.scan.ScanData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws a scan result in "map space" (1 unit = 1 block, origin = console block centre, north = -z): bounding box,
 * translucent cyan terrain voxels (outer faces only, cached per result version), ore cubes in their map colour,
 * the console pin, nearby players and a north marker. Shared by the hologram above the console and the terminal.
 */
public final class ScanMapRenderer
{
    /** extent of the terrain grid (cells of 4 around the block centres), per upgrade level */
    public static float minXZ(int tier) { return -ScanData.radius(tier) - 2.5f; }

    public static float maxXZ(int tier) { return minXZ(tier) + ScanData.gridXZ(tier) * ScanData.CELL; }

    public static float minY(int tier) { return -ScanData.halfHeight(tier) - 2.5f; }

    public static float maxY(int tier) { return minY(tier) + ScanData.gridY(tier) * ScanData.CELL; }

    /** hologram scale above the console: the level-0 map keeps 1/40, bigger maps shrink to the same size */
    public static float hologramScale(int tier) { return 32.0f / (ScanData.radius(tier) * 40.0f); }
    private static final float ORE = 0.9f;

    private record Cache(int version, float[] quads, int[] colors) {}

    private static final Map<ScanConsoleBlockEntity, Cache> CACHE = new WeakHashMap<>();

    private ScanMapRenderer() {}

    public static void draw(PoseStack pose, MultiBufferSource buffers, ScanConsoleBlockEntity be, float alpha)
    {
        Level level = be.getLevel();
        ScanData data = be.result();
        Matrix4f m = pose.last().pose();
        int tier = be.upgrade();
        int radius = ScanData.radius(tier), halfHeight = ScanData.halfHeight(tier);
        float minXZ = minXZ(tier), maxXZ = maxXZ(tier), minY = minY(tier), maxY = maxY(tier);
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());

        // ores first (the terrain is translucent on top of them)
        if (data.done)
        {
            Cache cache = cache(be, data);
            ResourceLocation target = be.target();
            for (int i = 0; i < data.hits.length; i++)
            {
                int kind = data.kinds[i];
                if (target != null && kind < data.palette.size() && !target.equals(data.palette.get(kind))) continue;
                int rgb = cache.colors[Math.min(kind, cache.colors.length - 1)];
                int p = data.hits[i];
                cube(quads, m, ScanData.dx(p), ScanData.dy(p), ScanData.dz(p), ORE, rgb, alpha);
            }
            float a = 0.22f * alpha;
            float[] q = cache.quads;
            for (int i = 0; i < q.length; i += 3)
                quads.vertex(m, q[i], q[i + 1], q[i + 2]).color(0.15f, 0.65f, 0.75f, a).endVertex();
        }
        // console pin and nearby players
        cube(quads, m, 0, 0, 0, 1.6f, 0x40F0FF, alpha);
        if (level != null)
        {
            BlockPos origin = be.getBlockPos();
            for (Player player : level.players())
            {
                double dx = player.getX() - origin.getX() - 0.5, dy = player.getY() - origin.getY(), dz = player.getZ() - origin.getZ() - 0.5;
                if (Math.abs(dx) > radius || Math.abs(dz) > radius || Math.abs(dy) > halfHeight) continue;
                cube(quads, m, (float) dx, (float) dy + 0.9f, (float) dz, 1.4f, 0xFFFFFF, alpha);
            }
        }
        // north marker at the top of the north edge
        cube(quads, m, 0, maxY, minXZ, 2.0f, 0xFF5050, alpha);

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, lines, minXZ, minY, minXZ, maxXZ, maxY, maxXZ, 0.35f, 0.9f, 1.0f, 0.8f * alpha);
        LevelRenderer.renderLineBox(pose, lines, -0.3, minY, -0.3, 0.3, 0.0, 0.3, 0.35f, 0.9f, 1.0f, 0.9f * alpha);
    }

    private static Cache cache(ScanConsoleBlockEntity be, ScanData data)
    {
        Cache cache = CACHE.get(be);
        if (cache != null && cache.version == be.version()) return cache;
        int[] colors = new int[Math.max(1, data.palette.size())];
        for (int i = 0; i < data.palette.size(); i++)
        {
            Block block = BuiltInRegistries.BLOCK.get(data.palette.get(i));
            MapColor color = block.defaultMapColor();
            colors[i] = color == null || color.col == 0 ? 0xE0E0E0 : color.col;
        }
        cache = new Cache(be.version(), terrainQuads(data), colors);
        CACHE.put(be, cache);
        return cache;
    }

    /** Outer faces of the solid terrain cells, as a flat x,y,z vertex list (4 per face). */
    private static float[] terrainQuads(ScanData data)
    {
        FloatList out = new FloatList();
        int tier = data.tier, gx = ScanData.gridXZ(tier), gy = ScanData.gridY(tier);
        for (int cy = 0; cy < gy; cy++)
            for (int cz = 0; cz < gx; cz++)
                for (int cx = 0; cx < gx; cx++)
                {
                    if (!data.solid(cx, cy, cz)) continue;
                    float x0 = cx * ScanData.CELL + minXZ(tier), y0 = cy * ScanData.CELL + minY(tier);
                    float z0 = cz * ScanData.CELL + minXZ(tier);
                    float s = ScanData.CELL;
                    for (Direction d : Direction.values())
                    {
                        if (data.solid(cx + d.getStepX(), cy + d.getStepY(), cz + d.getStepZ())) continue;
                        face(out, x0, y0, z0, s, d);
                    }
                }
        return out.toArray();
    }

    private static void face(FloatList out, float x0, float y0, float z0, float s, Direction d)
    {
        float x1 = x0 + s, y1 = y0 + s, z1 = z0 + s;
        switch (d)
        {
            case DOWN -> out.add(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            case UP -> out.add(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            case NORTH -> out.add(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
            case SOUTH -> out.add(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            case WEST -> out.add(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            case EAST -> out.add(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        }
    }

    /** Axis-aligned cube of the given size centred on (x, y, z). */
    private static void cube(VertexConsumer vc, Matrix4f m, float x, float y, float z, float size, int rgb, float alpha)
    {
        float h = size / 2;
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float x0 = x - h, y0 = y - h, z0 = z - h, x1 = x + h, y1 = y + h, z1 = z + h;
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] f : faces)
            for (int i = 0; i < 12; i += 3)
                vc.vertex(m, f[i], f[i + 1], f[i + 2]).color(r, g, b, alpha).endVertex();
    }

    /** Minimal growable float list. */
    private static final class FloatList
    {
        private float[] data = new float[4096];
        private int size;

        void add(float... values)
        {
            if (size + values.length > data.length) data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + values.length));
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }

        float[] toArray()
        {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}
