package com.abyssia.habitat.scan.client;

import com.abyssia.habitat.scan.ScanConsoleBlockEntity;
import com.abyssia.habitat.scan.ScanData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Matrix4f;

/** H07 hologram: the scan map at 1/40 scale (level 0; bigger maps shrink to the same size) above the console (slowly turning) and a light beam up into it. */
public class ScanConsoleRenderer implements BlockEntityRenderer<ScanConsoleBlockEntity>
{
    /** map centre above the console block origin */
    private static final float CENTRE_Y = 1.85f;
    private static final float TOP = 14.0f / 16.0f;

    public ScanConsoleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(ScanConsoleBlockEntity be)
    {
        return be.renderBox();
    }

    @Override
    public void render(ScanConsoleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
    {
        if (be.getLevel() == null) return;
        float time = be.getLevel().getGameTime() + partialTick;
        float scale = ScanMapRenderer.hologramScale(be.upgrade());
        float bottom = CENTRE_Y + ScanMapRenderer.minY(be.upgrade()) * scale;

        // beam from the console top to the map floor
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = pose.last().pose();
        float a = 0.25f + 0.08f * (float) Math.sin(time * 0.15f);
        float w = 0.06f;
        beam(quads, m, 0.5f - w, 0.5f, 0.5f + w, 0.5f, TOP, CENTRE_Y, a);
        beam(quads, m, 0.5f, 0.5f - w, 0.5f, 0.5f + w, TOP, CENTRE_Y, a);

        pose.pushPose();
        pose.translate(0.5f, CENTRE_Y, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.5f));
        pose.scale(scale, scale, scale);
        ScanMapRenderer.draw(pose, buffers, be, 1.0f);
        pose.popPose();
    }

    private static void beam(VertexConsumer vc, Matrix4f m, float x0, float z0, float x1, float z1, float y0, float y1, float alpha)
    {
        vc.addVertex(m, x0, y0, z0).setColor(0.4f, 0.95f, 1.0f, alpha);
        vc.addVertex(m, x1, y0, z1).setColor(0.4f, 0.95f, 1.0f, alpha);
        vc.addVertex(m, x1, y1, z1).setColor(0.4f, 0.95f, 1.0f, 0.0f);
        vc.addVertex(m, x0, y1, z0).setColor(0.4f, 0.95f, 1.0f, 0.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(ScanConsoleBlockEntity be)
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 48;
    }
}
