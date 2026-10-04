package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.client.anim.AnimMeshRenderer;
import com.abyssia.vehicle.SubmarineDockBlockEntity;
import com.abyssia.vehicle.SubmarineDockBlockEntity.State;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * SUB04 dock: draws {@link DockMesh} (tools/anim_model.py) posed by {@link DockAnim} from the block entity's synced
 * state. Model origin = bottom centre of the dock block, nose = -z, turned to the block's FACING. The teal light tile is
 * drawn full-bright while docked (blinking while capturing) by swapping the lightmap of quads sampling that tile.
 */
public class DockRenderer implements BlockEntityRenderer<SubmarineDockBlockEntity>
{
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/submarine_dock.png");
    /** atlas (tools/anim_model.py: sorted tile names, 3 columns): dock_light is tile 4 of dock_dark, dock_gangway_grating, dock_gangway_rail, dock_hazard, dock_light, dock_metal */
    private static final float LIGHT_U0 = 16.0f / 48.0f, LIGHT_U1 = 32.0f / 48.0f, LIGHT_V0 = 16.0f / 32.0f, LIGHT_V1 = 1.0f;

    public DockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SubmarineDockBlockEntity dock, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
    {
        State state = dock.animState();
        float t = dock.animTicks() + partialTick;
        boolean lit = state == State.DOCKED || (state == State.CAPTURE && ((int) (t / 4.0f)) % 2 == 0);
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - dock.facing().toYRot()));
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        if (lit) out = new LightTile(out);
        AnimMeshRenderer.render(pose, out, DockMesh.PARTS, DockAnim.poses(state, t), light, OverlayTexture.NO_OVERLAY, 255, 255, 255, 255);
        pose.popPose();
    }

    /** Full-bright lightmap for quads whose texture coordinates lie in the light tile. */
    private static final class LightTile implements VertexConsumer
    {
        private final VertexConsumer out;
        private boolean bright;

        LightTile(VertexConsumer out)
        {
            this.out = out;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z)
        {
            out.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a)
        {
            out.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v)
        {
            bright = u >= LIGHT_U0 - 1e-4f && u <= LIGHT_U1 + 1e-4f && v >= LIGHT_V0 - 1e-4f && v <= LIGHT_V1 + 1e-4f;
            out.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v)
        {
            out.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v)
        {
            if (bright) out.uv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16 & 0xFFFF);
            else out.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z)
        {
            out.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex()
        {
            out.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a)
        {
            out.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor()
        {
            out.unsetDefaultColor();
        }
    }
}
