package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.BossChain;
import com.abyssia.entity.boss.TrenchWyrm;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** BOSS01 near view of the trench wyrm: the GeckoLib model at every distance (small bones dropped far off, see {@link BossGeo#DETAIL_RANGE}). */
public class TrenchWyrmGeoRenderer extends GeoEntityRenderer<TrenchWyrm>
{
    private static final ResourceLocation GLOW = new ResourceLocation(Abyssia.MODID, "textures/entity/boss/trench_wyrm_geo_glow.png");

    public TrenchWyrmGeoRenderer(EntityRendererProvider.Context context)
    {
        super(context, new TrenchWyrmGeoModel());
        withScale((float) TrenchWyrm.SIZE);
        addRenderLayer(new BossGeo.GlowLayer<>(this, GLOW));
    }

    @Override
    public void render(TrenchWyrm entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
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
    protected void applyRotations(TrenchWyrm animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick)
    {
    }
}
