package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssAngler;
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
    /** Far kind of the abyss angler ({@code AbyssAngler.farKind()} must return it). */
    static final int ANGLER = 4;

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
        boolean fogPushed = false, drewModel = false;
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
                        if (b instanceof AbyssAngler) kind = ANGLER;
                        nearest = nearestJoint(b.chain(), cp, pt) - b.bodyRadius();
                        p = jointPoints(b.chain(), pt);
                    }
                    if (nearest <= fogEndNow) continue;
                    if (nearest > maxDist) continue;
                    // tracked and beyond the fog end: the same GeckoLib model, drawn with the fog pushed out (no box silhouette)
                    if (!fogPushed)
                    {
                        RenderSystem.setShaderFogStart(1.0E8F);
                        RenderSystem.setShaderFogEnd(2.0E8F);
                        fogPushed = true;
                    }
                    drewModel |= drawModel(b, mc, cam, buffers, event.getPoseStack(), event.getPartialTick());
                    continue;
                }
                else
                {
                    p = f.points(time);
                    nearest = nearestPoint(p, cp);
                }
                if (nearest > maxDist) continue;
                if (!fogPushed)
                {
                    RenderSystem.setShaderFogStart(1.0E8F);
                    RenderSystem.setShaderFogEnd(2.0E8F);
                    fogPushed = true;
                }
                // not synced: the same GeckoLib model on a client-only stand-in entity filled from the snapshot points
                AbyssBoss dummy = standIn(f, level, p);
                if (dummy != null)
                {
                    drewModel |= drawModel(dummy, mc, cam, buffers, event.getPoseStack(), event.getPartialTick());
                    continue;
                }
                if (vc == null) vc = buffers.getBuffer(type);
                draw(vc, pose, cam, cp, p, time, kind);
            }
        }
        finally
        {
            if (vc != null) buffers.endBatch(type);
            if (drewModel) buffers.endBatch();
            if (fogPushed)
            {
                RenderSystem.setShaderFogStart(fogStart);
                RenderSystem.setShaderFogEnd(fogEndSaved);
            }
        }
    }

    private static final int[] KRAKEN_JOINTS = {0, 4, 8, 12, 15};   // = AbyssKraken.FAR_JOINTS
    private static final int[] MOTHER_JOINTS = {0, 5, 10, 16, 22, 27};   // = AbyssMother.FAR_JOINTS

    /**
     * The far entry's stand-in entity (created once, never added to the level or ticked) with its chains rebuilt from the
     * snapshot points {@code p} (sparse joints bridged by a Catmull-Rom curve), or null when the layout does not fit.
     */
    private static AbyssBoss standIn(BossFarClient.Far f, ClientLevel level, double[] p)
    {
        if (f.dummy == null)
        {
            net.minecraft.world.entity.EntityType<?> type = switch (f.kind)
            {
                case 0 -> com.abyssia.registry.ModEntities.ABYSS_SERPENT.get();
                case 1 -> com.abyssia.registry.ModEntities.TRENCH_WYRM.get();
                case 2 -> com.abyssia.registry.ModEntities.ABYSS_KRAKEN.get();
                case 3 -> com.abyssia.registry.ModEntities.ABYSS_MOTHER.get();
                case ANGLER -> com.abyssia.registry.ModEntities.ABYSS_ANGLER.get();
                default -> null;
            };
            if (type == null || !(type.create(level) instanceof AbyssBoss b)) return null;
            f.dummy = b;
        }
        AbyssBoss b = f.dummy;
        if (p.length < 3 || Double.isNaN(p[0])) return null;
        BossChain spine = b.chain();
        int n = spine.size();
        if (b instanceof AbyssKraken || b instanceof com.abyssia.entity.boss.AbyssMother)
        {
            boolean kraken = b instanceof AbyssKraken;
            int[] fj = kraken ? KRAKEN_JOINTS : MOTHER_JOINTS;
            int tents = kraken ? AbyssKraken.TENTACLES : com.abyssia.entity.boss.AbyssMother.TENTACLES;
            if (p.length != 3 * (2 + tents * (1 + fj.length)) || n < 2) return null;
            spine.fillSampled(new int[]{0, n - 1}, new double[]{p[0], p[1], p[2], p[0], p[1], p[2]}, 0);
            for (int t = 0; t < tents; t++)
            {
                BossChain c = kraken ? ((AbyssKraken) b).tentacle(t) : ((com.abyssia.entity.boss.AbyssMother) b).tentacle(t);
                int off = 3 * (2 + t * (1 + fj.length) + 1);   // skip the break marker
                if (Double.isNaN(p[off])) return null;
                c.fillSampled(fj, p, off);
            }
        }
        else
        {
            java.util.List<Integer> idx = new java.util.ArrayList<>();
            idx.add(0);
            for (int i = BossFarPayload.STEP; i < n; i += BossFarPayload.STEP) idx.add(i);
            if (idx.get(idx.size() - 1) != n - 1) idx.add(n - 1);
            if (p.length != 3 * idx.size()) return null;
            spine.fillSampled(idx.stream().mapToInt(Integer::intValue).toArray(), p, 0);
        }
        b.setPos(p[0], p[1], p[2]);
        b.setOldPosAndRot();
        return b;
    }

    /** Draws the boss's own GeckoLib renderer (cut-off lifted, dim light) at its interpolated position; false if it could not. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean drawModel(AbyssBoss b, Minecraft mc, Camera cam, MultiBufferSource buffers, PoseStack base, float pt)
    {
        net.minecraft.client.renderer.entity.EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(b);
        if (renderer == null) return false;
        Vec3 cp = cam.getPosition();
        // 1.20.1: the camera rotation lives in the level pose stack (the model view matrix stays identity)
        PoseStack ps = base;
        ps.pushPose();
        ps.translate(net.minecraft.util.Mth.lerp(pt, b.xo, b.getX()) - cp.x, net.minecraft.util.Mth.lerp(pt, b.yo, b.getY()) - cp.y,
                net.minecraft.util.Mth.lerp(pt, b.zo, b.getZ()) - cp.z);
        BossGeo.forceFar = true;
        try
        {
            renderer.render(b, net.minecraft.util.Mth.lerp(pt, b.yRotO, b.getYRot()), pt, ps, buffers, LightTexture.pack(0, 0));
        }
        finally
        {
            BossGeo.forceFar = false;
            ps.popPose();
        }
        return true;
    }

    /** Body scale of the chain bosses (0 = serpent, 1 = wyrm, 4 = angler) for the silhouette width. */
    private static double chainSize(int kind)
    {
        return kind == 0 ? com.abyssia.entity.boss.AbyssSerpent.SIZE : kind == 1 ? com.abyssia.entity.boss.TrenchWyrm.SIZE
                : kind == ANGLER ? AbyssAngler.SIZE : 1.0;
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
                        : kind == ANGLER ? (k == 0 ? 6.5 : k == 1 ? 3.4 : 1.2) * chainSize(kind)   // big head, tapering body
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
        if (kind == ANGLER && m >= 2 && !Double.isNaN(p[3])) anglerLures(vc, pose, left, up, cp, p, time);
    }

    /**
     * The angler's lure lights: where its bulbs hang at rest ({@link AnglerMesh#FAR_LURES}, forward / up from the head),
     * forward along head minus the next sample, up = world up square to it. Pulsing out of step, sized against the distance.
     */
    private static void anglerLures(VertexConsumer vc, PoseStack.Pose pose, Vector3f left, Vector3f up, Vec3 cp, double[] p, double time)
    {
        double fx = p[0] - p[3], fy = p[1] - p[4], fz = p[2] - p[5], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1.0E-6) return;
        fx /= fl; fy /= fl; fz /= fl;
        double ux = -fx * fy, uy = 1 - fy * fy, uz = -fz * fy, ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0E-3) { ux = 1; uy = 0; uz = 0; ul = 1; }
        ux /= ul; uy /= ul; uz /= ul;
        double sc = AbyssAngler.SIZE / AnglerMesh.DESIGN;
        for (int i = 0; i < AnglerMesh.FAR_LURES.length; i++)
        {
            float[] o = AnglerMesh.FAR_LURES[i];
            double x = p[0] + (fx * o[0] + ux * o[1]) * sc - cp.x, y = p[1] + (fy * o[0] + uy * o[1]) * sc - cp.y, z = p[2] + (fz * o[0] + uz * o[1]) * sc - cp.z;
            double dist = Math.sqrt(x * x + y * y + z * z);
            float size = (float) ((i == 0 ? 2.2 : 1.5) * Math.sqrt(sc) + dist * (i == 0 ? 0.011 : 0.008));
            float a = (float) (0.75 + 0.25 * Math.sin(time * 0.13 + i * 1.9));
            float lx = left.x * size, ly = left.y * size, lz = left.z * size, vx = up.x * size, vy = up.y * size, vz = up.z * size;
            quad(vc, pose, x + lx - vx, y + ly - vy, z + lz - vz, x + lx + vx, y + ly + vy, z + lz + vz,
                    x - lx + vx, y - ly + vy, z - lz + vz, x - lx - vx, y - ly - vy, z - lz - vz, 0.30F, 0.78F, 1.0F, a);
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
