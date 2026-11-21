package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssAngler;
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
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * BOSS01 near view of the abyss angler: the GeckoLib model at every distance (small bones dropped far off, see {@link BossGeo#DETAIL_RANGE}).
 * The soft halos round every lure bulb and the big eyes are drawn after the model, at the bone-transformed centres from
 * contract.json (lure_bulbs / eye_halos), because the fog swallows a small emissive surface within a few blocks.
 */
public class AbyssAnglerGeoRenderer extends GeoEntityRenderer<AbyssAngler>
{
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/boss/abyss_angler_geo_glow.png");
    private static final ResourceLocation HALO = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/glow_halo.png");
    /** Lure light, #4cc8ff. */
    private static final float LR = 0.30F, LG = 0.78F, LB = 1.0F;

    /** Halo centres at rest (geo coordinates, the file's X) on their bones; index 0 is the main lure. */
    private static final String[] LURE_BONE = {"lure_bulb", "lure_nape_1", "lure_nape_2", "lure_chin_1", "lure_chin_2", "lure_chin_3", "lure_back_1",
            "lure_back_2", "lure_belly_1", "lure_belly_2", "lure_belly_3", "lure_belly_4"};
    private static final float[][] LURE_CENTER = {{0.0F, 84.89F, -193.46F}, {-48.96F, 206.04F, 65.74F}, {49.5F, 206.22F, 105.46F}, {0.0F, -184.13F, -22.18F},
            {-49.05F, -209.06F, 22.12F}, {47.42F, -227.06F, 58.96F}, {-26.53F, 194.29F, 223.87F}, {22.58F, 157.29F, 307.74F}, {0.0F, -215.02F, 198.0F},
            {-35.74F, -197.26F, 236.87F}, {33.08F, -178.56F, 284.87F}, {0.0F, -152.77F, 331.12F}};
    private static final float[][] EYE_CENTER = {{126.89F, 58.71F, -10.84F}, {-126.89F, 58.71F, -10.84F}};

    public AbyssAnglerGeoRenderer(EntityRendererProvider.Context context)
    {
        super(context, new AbyssAnglerGeoModel());
        withScale(AbyssAngler.SIZE);
        addRenderLayer(new BossGeo.GlowLayer<>(this, GLOW));
    }

    @Override
    public void render(AbyssAngler entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain chain = entity.chain();
        if (!chain.isInitialised()) return;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        // the old renderer's cut-off, kept: beyond the fog end the model is not drawn (BossFarRenderer draws the ribbon instead)
        if (ClientConfig.LOD_ENABLED.get() && !BossGeo.forceFar)
        {
            Vec3 cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double near = BossFarRenderer.nearestJoint(chain, cp, pt);
            if (near >= BossGeo.GEO_RANGE && near - entity.bodyRadius() > BossFarRenderer.fogEnd()) return;
        }
        super.render(entity, yaw, pt, pose, buffer, packedLight);
        halos(entity, pt, pose, buffer, cam);
    }

    /** The bones are placed from world-space joints, so the body yaw must not rotate the model again. */
    @Override
    protected void applyRotations(AbyssAngler animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale)
    {
    }

    /** Additive halos at the lure bulbs (swelling with the flash, shimmering out of step) and the big eyes. */
    private void halos(AbyssAngler entity, float pt, PoseStack pose, MultiBufferSource buffer, Camera cam)
    {
        float time = entity.tickCount + pt;
        float flash = Mth.clamp(entity.lureFlash(pt), 0.0F, 1.0F);
        VertexConsumer halo = buffer.getBuffer(RenderType.eyes(HALO));
        PoseStack.Pose last = pose.last();
        float s = AbyssAngler.SIZE / AnglerMesh.DESIGN;
        Vector4f p = new Vector4f();
        for (int l = 0; l < LURE_BONE.length; l++)
        {
            if (!centre(LURE_BONE[l], LURE_CENTER[l], p)) continue;
            float a = (0.35F + 0.65F * flash) * (0.85F + 0.15F * Mth.sin(time * 0.11F + l * 1.9F));
            float size = (l == 0 ? 9.0F : 5.5F) * s * (1.0F + 0.35F * flash);
            BossMesh.billboard(halo, last, cam, p.x, p.y, p.z, size, LR * a, LG * a, LB * a, 1);
        }
        for (float[] e : EYE_CENTER)
            if (centre("head", e, p))
                BossMesh.billboard(halo, last, cam, p.x, p.y, p.z, 6.0F * s, 0.16F, 0.36F, 0.45F, 1);
    }

    /**
     * The rest-pose point {@code c} (geo coordinates) carried along by the named bone, as a position in blocks from the entity
     * origin (the bone's local-space matrix, tracked while the model is drawn). False until the bone has been drawn once.
     */
    private boolean centre(String boneName, float[] c, Vector4f out)
    {
        GeoBone bone = getGeoModel().getAnimationProcessor().getBone(boneName);
        if (bone == null) return false;
        boolean tracked = bone.isTrackingMatrices();
        Matrix4f m = bone.getLocalSpaceMatrix();   // switches tracking on for the next frame
        if (!tracked) return false;
        // the matrix sits at the bone's pivot, in 1/16 block (render space: X mirrored)
        out.set((-c[0] - bone.getPivotX()) / 16.0F, (c[1] - bone.getPivotY()) / 16.0F, (c[2] - bone.getPivotZ()) / 16.0F, 1.0F);
        m.transform(out);
        return true;
    }
}
