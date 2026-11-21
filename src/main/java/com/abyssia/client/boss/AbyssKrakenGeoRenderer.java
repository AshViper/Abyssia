package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssKraken;
import com.abyssia.entity.boss.BossChain;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** BOSS01 near view of the abyss kraken: the GeckoLib model at every distance (small bones dropped far off, see {@link BossGeo#DETAIL_RANGE}). */
public class AbyssKrakenGeoRenderer extends GeoEntityRenderer<AbyssKraken>
{
    private static final ResourceLocation GLOW = new ResourceLocation(Abyssia.MODID, "textures/entity/boss/abyss_kraken_geo_glow.png");

    public AbyssKrakenGeoRenderer(EntityRendererProvider.Context context)
    {
        super(context, new AbyssKrakenGeoModel());
        withScale((float) AbyssKrakenGeoModel.MODEL_SCALE);
        addRenderLayer(new BossGeo.GlowLayer<>(this, GLOW));
    }

    @Override
    public void render(AbyssKraken entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain chain = entity.chain();
        if (!chain.isInitialised()) return;
        // the old renderer's cut-off, kept: beyond the fog end the model is not drawn (BossFarRenderer draws the ribbon instead)
        if (ClientConfig.LOD_ENABLED.get() && !BossGeo.forceFar)
        {
            Vec3 cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double near = BossFarRenderer.nearestPoint(entity.farPoints(pt), cp);
            if (near >= BossGeo.GEO_RANGE && near > BossFarRenderer.fogEnd()) return;
        }
        super.render(entity, yaw, pt, pose, buffer, packedLight);
    }

    /** The bones are placed from world-space joints, so the body yaw must not rotate the model again. */
    @Override
    protected void applyRotations(AbyssKraken animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick)
    {
    }
}
