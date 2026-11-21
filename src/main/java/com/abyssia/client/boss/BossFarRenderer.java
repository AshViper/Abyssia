package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssBoss;
import com.abyssia.entity.boss.AbyssKraken;
import com.abyssia.entity.boss.BossChain;
import com.abyssia.entity.boss.BossFarPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * BOSS01 LOD far view: a thick near-black ribbon with a teal rim and glow points along it, drawn from the sparse snapshots
 * of {@link BossFarClient}, ignoring fog (the shader fog range is pushed out while drawing). Skipped for a boss whose
 * entity is on the client (it draws itself), beyond {@code lod_distance}, or with {@code lod_enabled} off.
 * Drawn at AFTER_TRANSLUCENT_BLOCKS with {@link RenderType#entityTranslucentEmissive} (kept visible by Iris / Oculus).
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BossFarRenderer
{
    private static final ResourceLocation WHITE = new ResourceLocation("textures/misc/white.png");
    private static final float[][] UV = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};

    private BossFarRenderer() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        BossFarClient.clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !ClientConfig.LOD_ENABLED.get()) return;
        var list = BossFarClient.active(level);
        if (list.isEmpty()) return;
        Camera cam = event.getCamera();
        Vec3 cp = cam.getPosition();
        double maxDist = ClientConfig.LOD_DISTANCE.get();
        double time = level.getGameTime() + event.getPartialTick();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(WHITE);
        double fogEndNow = fogEnd();
        float fogStart = RenderSystem.getShaderFogStart(), fogEndSaved = RenderSystem.getShaderFogEnd();
        VertexConsumer vc = null;
        PoseStack.Pose pose = event.getPoseStack().last();
        try
        {
            for (BossFarClient.Far f : list)
            {
                double[] p;
                double nearest;
                int kind = f.kind;
                if (level.getEntity(f.id) instanceof AbyssBoss b && b.chain().isInitialised())
                {
                    // tracked: the entity draws itself inside the fog; beyond the fog end it is drawn here (from its own joints)
                    float pt = event.getPartialTick();
                    if (b instanceof AbyssKraken k)
                    {
                        kind = 2;
                        p = k.farPoints(pt);
                        nearest = nearestPoint(p, cp);
                    }
                    else if (b instanceof com.abyssia.entity.boss.AbyssMother m)
                    {
                        kind = 3;
                        p = m.farPoints(pt);
                        nearest = nearestPoint(p, cp);
                    }
                    else
                    {
                        nearest = nearestJoint(b.chain(), cp, pt) - b.bodyRadius();
                        p = jointPoints(b.chain(), pt);
                    }
                    if (nearest <= fogEndNow) continue;
                }
                else
                {
                    p = f.points(time);
                    nearest = nearestPoint(p, cp);
                }
                if (nearest > maxDist) continue;
                if (vc == null)
                {
                    RenderSystem.setShaderFogStart(1.0E8F);
                    RenderSystem.setShaderFogEnd(2.0E8F);
                    vc = buffers.getBuffer(type);
                }
                draw(vc, pose, cam, cp, p, time, kind);
            }
        }
        finally
        {
            if (vc != null)
            {
                buffers.endBatch(type);
                RenderSystem.setShaderFogStart(fogStart);
                RenderSystem.setShaderFogEnd(fogEndSaved);
            }
        }
    }

    /** Body scale of the chain bosses (0 = serpent, 1 = wyrm) for the silhouette width. */
    private static double chainSize(int kind)
    {
        return kind == 0 ? com.abyssia.entity.boss.AbyssSerpent.SIZE : kind == 1 ? com.abyssia.entity.boss.TrenchWyrm.SIZE : 1.0;
    }

    /** Current fog end (64 when unknown): a tracked boss beyond it is drawn as the fog-ignoring ribbon instead of the model. */
    public static double fogEnd()
    {
        float e = RenderSystem.getShaderFogEnd();
        return e > 0 && e < 1.0E7F ? e : 64.0;
    }

    /** Distance from the camera to the nearest point of an x y z list (NaN break markers skipped). */
    public static double nearestPoint(double[] p, Vec3 cp)
    {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < p.length; i += 3)
        {
            if (Double.isNaN(p[i])) continue;
            best = Math.min(best, (p[i] - cp.x) * (p[i] - cp.x) + (p[i + 1] - cp.y) * (p[i + 1] - cp.y) + (p[i + 2] - cp.z) * (p[i + 2] - cp.z));
        }
        return Math.sqrt(best);
    }

    /** Distance from the camera to the nearest joint (lerped). */
    public static double nearestJoint(BossChain chain, Vec3 cp, float pt)
    {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < chain.size(); i++)
        {
            double x = chain.lerpX(i, pt) - cp.x, y = chain.lerpY(i, pt) - cp.y, z = chain.lerpZ(i, pt) - cp.z;
            best = Math.min(best, x * x + y * y + z * z);
        }
        return Math.sqrt(best);
    }

    /** Head and every STEP-th joint (plus the tail end), like the far snapshot. */
    private static double[] jointPoints(BossChain chain, float pt)
    {
        int n = chain.size();
        java.util.List<Integer> idx = new java.util.ArrayList<>();
        idx.add(0);
        for (int i = BossFarPayload.STEP; i < n; i += BossFarPayload.STEP) idx.add(i);
        if (idx.get(idx.size() - 1) != n - 1) idx.add(n - 1);
        double[] p = new double[idx.size() * 3];
        for (int k = 0; k < idx.size(); k++)
        {
            int i = idx.get(k);
            p[3 * k] = chain.lerpX(i, pt);
            p[3 * k + 1] = chain.lerpY(i, pt);
            p[3 * k + 2] = chain.lerpZ(i, pt);
        }
        return p;
    }

    private static void draw(VertexConsumer vc, PoseStack.Pose pose, Camera cam, Vec3 cp, double[] p, double time, int kind)
    {
        int m = p.length / 3;
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        for (int pass = 0; pass < 2; pass++)
        {
            for (int k = 0; k + 1 < m; k++)
            {
                if (Double.isNaN(p[3 * k]) || Double.isNaN(p[3 * k + 3])) continue; // break between polylines
                double ax = p[3 * k] - cp.x, ay = p[3 * k + 1] - cp.y, az = p[3 * k + 2] - cp.z;
                double bx = p[3 * k + 3] - cp.x, by = p[3 * k + 4] - cp.y, bz = p[3 * k + 5] - cp.z;
                double dx = bx - ax, dy = by - ay, dz = bz - az;
                double mx = (ax + bx) * 0.5, my = (ay + by) * 0.5, mz = (az + bz) * 0.5;
                double dist = Math.sqrt(mx * mx + my * my + mz * mz);
                // width vector: perpendicular to the segment and to the view ray, so the ribbon always faces the camera
                double wx = dy * mz - dz * my, wy = dz * mx - dx * mz, wz = dx * my - dy * mx;
                double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
                if (wl < 1.0E-6) continue;
                double half = (kind == 3 ? (k == 0 ? 15.0 : 1.2) : kind == 2 ? (k == 0 ? 6.5 : 2.2)
                        : (k == 0 ? 3.5 : 2.8 * (1.0 - 0.5 * k / (double) m)) * chainSize(kind)) + dist * 0.004;
                if (pass == 0) half *= 1.35;
                double s = half / wl;
                float r = pass == 0 ? 0.05F : 0.01F, g = pass == 0 ? 0.28F : 0.02F, bl = pass == 0 ? 0.38F : 0.04F, a = pass == 0 ? 0.35F : 0.92F;
                quad(vc, pose, ax - wx * s, ay - wy * s, az - wz * s, ax + wx * s, ay + wy * s, az + wz * s,
                        bx + wx * s, by + wy * s, bz + wz * s, bx - wx * s, by - wy * s, bz - wz * s, r, g, bl, a);
            }
        }
        // glow points: bright head, a dimmer pulsing dot on every other sample
        for (int k = 0; k < m; k += (k == 0 ? 1 : 2))
        {
            if (Double.isNaN(p[3 * k])) continue;
            double x = p[3 * k] - cp.x, y = p[3 * k + 1] - cp.y, z = p[3 * k + 2] - cp.z;
            double dist = Math.sqrt(x * x + y * y + z * z);
            float size = (float) ((k == 0 ? 2.5 : 1.2) * Math.sqrt(chainSize(kind)) + dist * (k == 0 ? 0.012 : 0.008));
            float a = k == 0 ? 1.0F : (float) (0.7 + 0.3 * Math.sin(time * 0.2 + k));
            float lx = left.x * size, ly = left.y * size, lz = left.z * size, ux = up.x * size, uy = up.y * size, uz = up.z * size;
            quad(vc, pose, x + lx - ux, y + ly - uy, z + lz - uz, x + lx + ux, y + ly + uy, z + lz + uz,
                    x - lx + ux, y - ly + uy, z - lz + uz, x - lx - ux, y - ly - uy, z - lz - uz, 0.4F, 0.95F, 1.0F, a);
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
