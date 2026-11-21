package com.abyssia.client.giant;

import com.abyssia.client.boss.BossFarRenderer;
import com.abyssia.entity.boss.BossChain;
import com.abyssia.entity.giant.GiantFauna;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * GNT01 renderer shared by every giant. A species model registered with {@link #registerModel} draws the animal at
 * every distance the client tracks it ({@link #NEAR} is no longer used for that); only without one is it the boss LOD look: a
 * dark camera-facing ribbon with a teal rim and glow points along it. Nothing is drawn beyond {@link #FAR}. Rendering goes by
 * the chain, not by the entity's own position, so the culling box is the whole chain ({@code GiantFauna.getBoundingBoxForCulling}).
 */
public class GiantRenderer extends EntityRenderer<GiantFauna>
{
    public static final double NEAR = 64, FAR = 128;
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float[][] UV = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};

    /** A species' full model (for example GeckoLib): draws the animal at the pose origin, which is the entity's interpolated position. */
    @FunctionalInterface
    public interface GiantModel
    {
        void render(GiantFauna entity, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight);
    }

    private static final Map<String, GiantModel> MODELS = new HashMap<>();

    public static void registerModel(String speciesId, GiantModel model)
    {
        MODELS.put(speciesId, model);
    }

    /** Species models that need the renderer context (GeckoLib); built on first use, one per renderer. */
    private static final Map<String, java.util.function.Function<EntityRendererProvider.Context, GiantModel>> FACTORIES = new HashMap<>();
    private final Map<String, GiantModel> built = new HashMap<>();
    private final EntityRendererProvider.Context context;

    public static void registerModelFactory(String speciesId, java.util.function.Function<EntityRendererProvider.Context, GiantModel> factory)
    {
        FACTORIES.put(speciesId, factory);
    }

    /** GeckoLib model for a species: only the asset names (geo/animation stem, texture path), model scale and segment count. */
    public static void registerGeo(String speciesId, String assetName, String texturePath, float modelScale, int segments)
    {
        registerGeo(speciesId, assetName, texturePath, modelScale, segments, null, false);
    }

    /** As above with a glow texture path (null = no glow layer) and whether bones named *_skin are drawn in a second, translucent pass. */
    public static void registerGeo(String speciesId, String assetName, String texturePath, float modelScale, int segments, String glowPath, boolean translucentSkin)
    {
        registerModelFactory(speciesId, ctx -> new GiantGeoRenderer(ctx, new GiantGeoModel(assetName, texturePath, modelScale, segments), modelScale, glowPath, translucentSkin));
    }

    private GiantModel modelFor(String id)
    {
        GiantModel m = MODELS.get(id);
        if (m == null)
        {
            var f = FACTORIES.get(id);
            if (f != null) m = built.computeIfAbsent(id, k -> f.apply(context));
        }
        return m;
    }

    public GiantRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.context = context;
    }

    @Override
    public ResourceLocation getTextureLocation(GiantFauna entity)
    {
        return WHITE;
    }

    @Override
    public void render(GiantFauna entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain chain = entity.chain();
        if (!chain.isInitialised()) return;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition();
        double radius = entity.spec().radius();
        GiantModel model = modelFor(entity.spec().id());
        // The species model at every distance the client has the entity (user 2026-11-21: the mid-range box looked wrong).
        // The simplified ribbon is only the fallback for a species without a model.
        if (model != null)
        {
            model.render(entity, pt, pose, buffer, packedLight);
            return;
        }
        if (BossFarRenderer.nearestJoint(chain, cp, pt) - radius > FAR) return;
        double ex =Mth.lerp(pt, entity.xo, entity.getX()), ey = Mth.lerp(pt, entity.yo, entity.getY()), ez = Mth.lerp(pt, entity.zo, entity.getZ());
        drawBox(chain, radius, buffer, pose.last(), cam, cp.x - ex, cp.y - ey, cp.z - ez, ex, ey, ez, entity.tickCount + pt, pt, entity.glowOn());
    }

    /** Entity-relative joint positions, camera at (cx, cy, cz) in the same frame. */
    private static void drawBox(BossChain chain, double radius, MultiBufferSource buffer, PoseStack.Pose pose, Camera cam, double cx, double cy, double cz,
                                double ex, double ey, double ez, double time, float pt, boolean glow)
    {
        int n = chain.size();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        double[] x = new double[n], y = new double[n], z = new double[n];
        for (int i = 0; i < n; i++)
        {
            x[i] = chain.lerpX(i, pt) - ex;
            y[i] = chain.lerpY(i, pt) - ey;
            z[i] = chain.lerpZ(i, pt) - ez;
        }
        for (int pass = 0; pass < 2; pass++)
        {
            for (int k = 0; k + 1 < n; k++)
            {
                double dx = x[k + 1] - x[k], dy = y[k + 1] - y[k], dz = z[k + 1] - z[k];
                double mx = (x[k] + x[k + 1]) * 0.5 - cx, my = (y[k] + y[k + 1]) * 0.5 - cy, mz = (z[k] + z[k + 1]) * 0.5 - cz;
                double dist = Math.sqrt(mx * mx + my * my + mz * mz);
                // width vector: perpendicular to the segment and the view ray, so the ribbon always faces the camera
                double wx = dy * mz - dz * my, wy = dz * mx - dx * mz, wz = dx * my - dy * mx;
                double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
                if (wl < 1.0E-6) continue;
                double half = radius * (k == 0 ? 1.0 : Math.max(0.25, 1.0 - 0.7 * k / (double) n)) + dist * 0.004;
                if (pass == 0) half *= 1.35;
                double s = half / wl;
                float r = pass == 0 ? 0.05F : 0.01F, g = pass == 0 ? 0.28F : 0.02F, b = pass == 0 ? 0.38F : 0.04F, a = pass == 0 ? 0.35F : 0.92F;
                quad(vc, pose, x[k] - wx * s, y[k] - wy * s, z[k] - wz * s, x[k] + wx * s, y[k] + wy * s, z[k] + wz * s,
                        x[k + 1] + wx * s, y[k + 1] + wy * s, z[k + 1] + wz * s, x[k + 1] - wx * s, y[k + 1] - wy * s, z[k + 1] - wz * s, r, g, b, a);
            }
        }
        // glow points: bright head, a dimmer pulsing dot on every other joint
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        for (int k = 0; glow && k < n; k += (k == 0 ? 1 : 2))
        {
            double px = x[k] - cx, py = y[k] - cy, pz = z[k] - cz;
            double dist = Math.sqrt(px * px + py * py + pz * pz);
            float size = (float) ((k == 0 ? 0.5 : 0.3) * radius + dist * (k == 0 ? 0.012 : 0.008));
            float a = k == 0 ? 1.0F : (float) (0.7 + 0.3 * Math.sin(time * 0.2 + k));
            float lx = left.x * size, ly = left.y * size, lz = left.z * size, ux = up.x * size, uy = up.y * size, uz = up.z * size;
            quad(vc, pose, x[k] + lx - ux, y[k] + ly - uy, z[k] + lz - uz, x[k] + lx + ux, y[k] + ly + uy, z[k] + lz + uz,
                    x[k] - lx + ux, y[k] - ly + uy, z[k] - lz + uz, x[k] - lx - ux, y[k] - ly - uy, z[k] - lz - uz, 0.4F, 0.95F, 1.0F, a);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3, float r, float g, float b, float a)
    {
        double[][] v = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        for (int i = 0; i < 4; i++)
            vc.vertex(pose.pose(), (float) v[i][0], (float) v[i][1], (float) v[i][2]).color(r, g, b, a).uv(UV[i][0], UV[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), 0, 0, 1).endVertex();
    }
}
