package com.abyssia.client.giant;

import com.abyssia.Abyssia;
import com.abyssia.entity.giant.GiantFauna;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import javax.annotation.Nullable;

/**
 * GNT01 near view of a giant (0-64 blocks): a {@link GiantGeoModel} drawn at the pose origin, bones placed from the chain.
 * Layers: an emissive glow layer ({@code <id>_geo_glow.png}, RenderType.eyes; switched by {@link GiantFauna#glowOn()}) and, for
 * species with translucent skin (lumenwing, glassback), a second pass: the main pass draws every bone except those whose name ends
 * in {@code _skin} (cutout, no cull), then the skin bones alone are drawn translucent.
 */
public class GiantGeoRenderer extends GeoEntityRenderer<GiantFauna> implements GiantRenderer.GiantModel
{
    private enum Pass { ALL, OPAQUE, SKIN }

    private final boolean skin;
    private Pass pass;

    public GiantGeoRenderer(EntityRendererProvider.Context context, GiantGeoModel model, float scale, @Nullable String glowTexture, boolean skin)
    {
        super(context, model);
        withScale(scale);
        this.shadowRadius = 0.0F;
        this.skin = skin;
        this.pass = skin ? Pass.OPAQUE : Pass.ALL;
        if (glowTexture != null) addRenderLayer(new PassLayer(this, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, glowTexture), true));
        if (skin) addRenderLayer(new PassLayer(this, null, false));
    }

    @Override
    public void render(GiantFauna entity, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        super.render(entity, 0.0F, partialTick, pose, buffer, packedLight);
    }

    /** The bones are placed from the chain's joints, so the body yaw must not rotate the model again. */
    @Override
    protected void applyRotations(GiantFauna animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick)
    {
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        if (pass != Pass.ALL)
        {
            boolean isSkin = bone.getName().endsWith("_skin");
            if (isSkin != (pass == Pass.SKIN)) return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** Glow layer (eyes render type, all bones) or the translucent skin layer (null texture: skin bones only, entityTranslucent). */
    private static final class PassLayer extends GeoRenderLayer<GiantFauna>
    {
        private final GiantGeoRenderer owner;
        private final ResourceLocation glow;

        PassLayer(GiantGeoRenderer owner, @Nullable ResourceLocation glow, boolean isGlow)
        {
            super(owner);
            this.owner = owner;
            this.glow = isGlow ? glow : null;
        }

        @Override
        public void render(PoseStack poseStack, GiantFauna animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType, MultiBufferSource bufferSource,
                           @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
        {
            if (animatable.isInvisible()) return;
            Pass saved = owner.pass;
            RenderType rt;
            try
            {
                if (glow != null)
                {
                    if (!animatable.glowOn()) return;
                    rt = RenderType.eyes(glow);
                    owner.pass = Pass.ALL;
                    packedLight = LightTexture.FULL_BRIGHT;
                }
                else
                {
                    rt = RenderType.entityTranslucent(getTextureResource(animatable));
                    owner.pass = Pass.SKIN;
                }
                ((GeoRenderer<GiantFauna>) owner).reRender(bakedModel, poseStack, bufferSource, animatable, rt, bufferSource.getBuffer(rt), partialTick, packedLight, packedOverlay,
                        1.0F, 1.0F, 1.0F, 1.0F);
            }
            finally
            {
                owner.pass = saved;
            }
        }
    }
}
