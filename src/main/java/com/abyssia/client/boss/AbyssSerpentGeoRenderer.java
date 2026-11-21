package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssSerpent;
import com.abyssia.entity.boss.BossChain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * BOSS01 view of the abyss serpent: the GeckoLib model at every distance (small bones dropped from {@link BossGeo#DETAIL_RANGE}); past the fog
 * {@link BossFarRenderer} takes over. The bones come from the chain (see {@link AbyssSerpentGeoModel}), so the entity's own
 * body rotation is not applied. Culling uses the entity's chain-wide culling box ({@code AbyssBoss.getBoundingBoxForCulling}).
 */
public class AbyssSerpentGeoRenderer extends GeoEntityRenderer<AbyssSerpent>
{
    /** Distance (blocks, to the nearest joint) from which the old "beyond the fog end = not drawn" cut-off applies. */
    public static final double GEO_RANGE = 64.0;
    private static final ResourceLocation GLOW = new ResourceLocation(Abyssia.MODID, "textures/entity/boss/abyss_serpent_geo_glow.png");

    public AbyssSerpentGeoRenderer(EntityRendererProvider.Context context)
    {
        super(context, new AbyssSerpentGeoModel());
        withScale((float) AbyssSerpent.SIZE);
        addRenderLayer(new GlowLayer(this));
    }

    @Override
    public void render(AbyssSerpent entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain chain = entity.chain();
        if (!chain.isInitialised()) return;
        // the old renderer's cut-off, kept: beyond the fog end the model is not drawn (BossFarRenderer draws the ribbon instead)
        if (ClientConfig.LOD_ENABLED.get() && !BossGeo.forceFar)
        {
            Vec3 cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double near = BossFarRenderer.nearestJoint(chain, cp, pt);
            if (near >= BossGeo.GEO_RANGE && near - entity.bodyRadius() > BossFarRenderer.fogEnd()) return;
        }
        super.render(entity, yaw, pt, pose, buffer, packedLight);
    }

    /** The bones are placed from world-space joints, so the body yaw must not rotate the model again. */
    @Override
    protected void applyRotations(AbyssSerpent animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick)
    {
    }

    /** Glow texture drawn additively over the model (RenderType.eyes), full bright. */
    private static class GlowLayer extends GeoRenderLayer<AbyssSerpent>
    {
        GlowLayer(AbyssSerpentGeoRenderer renderer)
        {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, AbyssSerpent animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType, MultiBufferSource bufferSource,
                           @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
        {
            RenderType type = RenderType.eyes(GLOW);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick,
                    LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
