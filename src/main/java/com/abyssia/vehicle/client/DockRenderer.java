package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.client.anim.AnimMeshRenderer;
import com.abyssia.vehicle.SubmarineDockBlockEntity;
import com.abyssia.vehicle.SubmarineDockBlockEntity.State;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * SUB04 dock: draws {@link DockMesh} (tools/anim_model.py) posed by {@link DockAnim} from the block entity's synced
 * state. Model origin = bottom centre of the dock block, nose = -z, turned to the block's FACING. The teal light tile is
 * drawn full-bright while docked (blinking while capturing): quads sampling that tile get the full-bright lightmap.
 */
public class DockRenderer implements BlockEntityRenderer<SubmarineDockBlockEntity>
{
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/submarine_dock.png");
    /** atlas (tools/anim_model.py: sorted tile names, 3 columns): dock_light is tile 4 (column 1, row 1) */
    private static final float[] LIGHT_TILE = {16.0f / 48.0f, 16.0f / 32.0f, 32.0f / 48.0f, 1.0f};

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
        AnimMeshRenderer.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), DockMesh.PARTS, DockAnim.poses(state, t), light,
                OverlayTexture.NO_OVERLAY, 255, 255, 255, 255, lit ? LIGHT_TILE : null);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(SubmarineDockBlockEntity dock)
    {
        return dock.getRenderBoundingBox();
    }

    @Override
    public int getViewDistance()
    {
        return 96;
    }
}
