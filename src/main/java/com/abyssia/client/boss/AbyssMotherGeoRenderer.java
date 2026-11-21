package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssMother;
import com.abyssia.entity.boss.BossChain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * BOSS01 near view of the abyss mother: the GeckoLib model at every distance (small bones dropped far off, see {@link BossGeo#DETAIL_RANGE}).
 * The bell and fringes are translucent (entityTranslucentCull); the halo round the heart and the beads of light round the rim
 * while the pulse charges are not in the model and are drawn here as the old renderer did.
 */
public class AbyssMotherGeoRenderer extends GeoEntityRenderer<AbyssMother>
{
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/boss/abyss_mother_geo_glow.png");
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float CORE_Y = -4.0F;

    private final AbyssMotherGeoModel geo;

    public AbyssMotherGeoRenderer(EntityRendererProvider.Context context)
    {
        super(context, new AbyssMotherGeoModel());
        this.geo = (AbyssMotherGeoModel) getGeoModel();
        withScale((float) AbyssMotherGeoModel.MODEL_SCALE);
        addRenderLayer(new BossGeo.GlowLayer<>(this, GLOW));
    }

    @Override
    public void render(AbyssMother entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain spine = entity.chain();
        if (!spine.isInitialised()) return;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        // the old renderer's cut-off, kept: beyond the fog end the model is not drawn (BossFarRenderer draws the ribbon instead)
        if (ClientConfig.LOD_ENABLED.get() && !BossGeo.forceFar)
        {
            Vec3 cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double near = BossFarRenderer.nearestPoint(entity.farPoints(pt), cp);
            if (near >= BossGeo.GEO_RANGE && near > BossFarRenderer.fogEnd()) return;
        }
        super.render(entity, yaw, pt, pose, buffer, packedLight);

        // halo round the heart, and beads of light round the rim while the pulse charges
        double ex = Mth.lerp(pt, entity.xo, entity.getX()), ey = Mth.lerp(pt, entity.yo, entity.getY()), ez = Mth.lerp(pt, entity.zo, entity.getZ());
        double ox = spine.lerpX(0, pt) - ex, oy = spine.lerpY(0, pt) - ey, oz = spine.lerpZ(0, pt) - ez;
        float time = entity.tickCount + pt;
        float open = geo.openness(entity, time);
        float cr = Mth.lerp(open, 3.4F, 4.6F) * (1.0F + 0.05F * entity.beat(pt));
        VertexConsumer gc = buffer.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PoseStack.Pose last = pose.last();
        BossMesh.billboard(gc, last, cam, (float) ox, (float) oy + CORE_Y, (float) oz, cr * 2.2F, 1.0F, 0.35F, 0.85F, 0.14F + 0.22F * open + 0.06F * entity.beat(pt));
        if (entity.warning())
        {
            float a = 0.4F + 0.3F * Mth.sin(time * 0.6F);
            for (int i = 0; i < 16; i++)
            {
                double ang = i * (Math.PI * 2 / 16);
                BossMesh.billboard(gc, last, cam, (float) (ox + Math.cos(ang) * 18.5), (float) oy - 6.0F, (float) (oz + Math.sin(ang) * 18.5), 1.5F, 0.8F, 1.0F, 1.0F, a);
            }
        }
    }

    /** Translucent bell and fringes (the heart, tentacles and arms are opaque pixels of the same texture). */
    @Nullable
    @Override
    public RenderType getRenderType(AbyssMother animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick)
    {
        return RenderType.entityTranslucentCull(texture);
    }

    /** The bones are placed from world-space joints, so the body yaw must not rotate the model again. */
    @Override
    protected void applyRotations(AbyssMother animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale)
    {
    }
}
