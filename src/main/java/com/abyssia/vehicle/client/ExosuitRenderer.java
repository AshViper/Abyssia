package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.vehicle.Exosuit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Walking exosuit: draws {@link ExosuitModel} (cutout hull, full-bright headlamp, translucent dome last). Mesh front is
 * -Z, so 180 - yaw turns it to the entity's forward, same as {@link SubmarineRenderer}.
 */
public class ExosuitRenderer extends EntityRenderer<Exosuit>
{
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/exosuit.png");
    private final ExosuitModel model = new ExosuitModel();

    public ExosuitRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        shadowRadius = 0.8f;
    }

    @Override
    public void render(Exosuit suit, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light)
    {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - Mth.rotLerp(partialTick, suit.yRotO, suit.getYRot())));
        model.render(suit, partialTick, pose, buffers, TEXTURE, light);
        pose.popPose();
        super.render(suit, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Exosuit suit)
    {
        return TEXTURE;
    }
}
