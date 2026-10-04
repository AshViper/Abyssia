package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.vehicle.Submarine;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * SUB02 submarine: emits the baked quads of {@link SubmarineMesh} (tools/vehicle_model.py). Hull and lamps cutout
 * (no culling: the quads carry their own normals), lamps full-bright while the headlights are on, glass translucent
 * last. The mesh front is -Z, so 180 - yaw turns it to the entity's forward.
 */
public class SubmarineRenderer extends EntityRenderer<Submarine>
{
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/submarine.png");

    public SubmarineRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        shadowRadius = 1.4f;
    }

    @Override
    public void render(Submarine sub, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light)
    {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
        // SUB05: pitch about the hull mid-height (xRot + = nose down, the nose is -Z), glass / lamps share the pose
        float pitch = Mth.lerp(partialTick, sub.xRotO, sub.getXRot());
        pose.translate(0.0, Submarine.PIVOT_Y, 0.0);
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        pose.translate(0.0, -Submarine.PIVOT_Y, 0.0);
        PoseStack.Pose last = pose.last();
        VertexConsumer cutout = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        quads(last, cutout, SubmarineMesh.HULL, light);
        quads(last, cutout, SubmarineMesh.LAMPS, sub.lights() ? LightTexture.FULL_BRIGHT : light);
        quads(last, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), SubmarineMesh.GLASS, light);
        pose.popPose();
        super.render(sub, yaw, partialTick, pose, buffers, light);
    }

    private static void quads(PoseStack.Pose pose, VertexConsumer out, float[] mesh, int light)
    {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int q = 0; q + SubmarineMesh.STRIDE <= mesh.length; q += SubmarineMesh.STRIDE)
        {
            float nx = mesh[q + 20], ny = mesh[q + 21], nz = mesh[q + 22];
            for (int v = 0; v < 4; v++)
            {
                int i = q + v * 5;
                out.vertex(m, mesh[i], mesh[i + 1], mesh[i + 2]).color(255, 255, 255, 255).uv(mesh[i + 3], mesh[i + 4])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(Submarine sub)
    {
        return TEXTURE;
    }
}
