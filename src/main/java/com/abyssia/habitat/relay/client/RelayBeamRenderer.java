package com.abyssia.habitat.relay.client;

import com.abyssia.habitat.relay.RelayNetwork;
import com.abyssia.habitat.relay.RelaySyncPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * WR01 link beams: antenna tip to antenna tip, a camera-facing strip 1.5 px wide in 16 slightly wavering segments
 * (sin, 0.04 blocks, moving with time) plus a faint wider halo. Idle: alpha 0.20 breathing over 3 s. Transferring:
 * alpha 0.8 (+ a little per band) and 2-3 bright pulses running in the transfer direction, 1.0-1.6x faster by band.
 * Only links up to 96 blocks long whose midpoint is within 96 blocks of the camera; paused links are not drawn.
 * <p>
 * Drawn at AFTER_TRANSLUCENT_BLOCKS with {@link RenderType#entityTranslucentEmissive} on the vanilla white texture
 * (full bright, depth test, no depth write) - the render type Iris / Oculus packs keep visible (see CurrentStreamRibbons);
 * a custom additive type would vanish under shader packs.
 */
public final class RelayBeamRenderer
{
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    public static final double MAX_DRAW_DISTANCE = 96.0;
    private static final int SEGMENTS = 16;
    private static final double HALF_WIDTH = 1.5 / 16.0 / 2.0;
    private static final double WOBBLE = 0.04;
    private static final float RED = 0x3F / 255.0f, GREEN = 0xE6 / 255.0f, BLUE = 0xD8 / 255.0f;
    private static final float PULSE_R = 0.80f, PULSE_G = 1.0f, PULSE_B = 0.97f;
    private static final double PULSE_SPEED = 0.30, PULSE_LENGTH = 0.6;

    private RelayBeamRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        // 1.21: the view rotation is in the model-view matrix; vertices go in camera-relative with an identity pose
        render(new PoseStack(), event.getCamera().getPosition(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    private static void render(PoseStack poseStack, Vec3 cam, float partialTick)
    {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        List<RelaySyncPacket.Entry> links = RelayClient.links(level);
        if (links.isEmpty()) return;
        double time = level.getGameTime() + partialTick;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(WHITE);
        VertexConsumer vc = null;
        PoseStack.Pose pose = poseStack.last();
        double maxSq = MAX_DRAW_DISTANCE * MAX_DRAW_DISTANCE;
        for (RelaySyncPacket.Entry e : links)
        {
            if (e.state() == 0) continue;
            Vec3 a = new Vec3(e.a().getX() + RelayNetwork.TIP_X - cam.x, e.a().getY() + RelayNetwork.TIP_Y - cam.y, e.a().getZ() + RelayNetwork.TIP_Z - cam.z);
            Vec3 b = new Vec3(e.b().getX() + RelayNetwork.TIP_X - cam.x, e.b().getY() + RelayNetwork.TIP_Y - cam.y, e.b().getZ() + RelayNetwork.TIP_Z - cam.z);
            if (a.distanceToSqr(b) > maxSq || a.add(b).scale(0.5).lengthSqr() > maxSq) continue;
            if (vc == null) vc = buffers.getBuffer(type);
            drawLink(vc, pose, a, b, e, time);
        }
        if (vc != null) buffers.endBatch(type);
    }

    private static void drawLink(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, RelaySyncPacket.Entry e, double time)
    {
        Vec3 dir = b.subtract(a);
        double len = dir.length();
        if (len < 1.0e-3) return;
        Vec3 t = dir.scale(1.0 / len);
        Vec3 u = t.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1.0e-6) u = t.cross(new Vec3(1, 0, 0));
        u = u.normalize();
        Vec3 v = t.cross(u).normalize();
        long seed = e.a().asLong() * 31L + e.b().asLong();
        double phase = (seed & 0xFFFF) / 65536.0 * Math.PI * 2.0;

        boolean sending = e.state() >= 2;
        float alpha = sending ? Math.min(0.95f, 0.8f + 0.05f * e.band())
                : (float) (0.20 + 0.06 * Math.sin(time / 60.0 * Math.PI * 2.0 + phase)); // idle clearly dimmer than sending (WR01 review)

        Vec3[] pts = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) pts[i] = point(a, dir, u, v, i / (double) SEGMENTS, len, time, phase);
        for (int i = 0; i < SEGMENTS; i++)
        {
            strip(vc, pose, pts[i], pts[i + 1], t, HALF_WIDTH * 3.0, RED, GREEN, BLUE, sending ? alpha * 0.22f : alpha * 0.10f); // halo (faint when idle)
            strip(vc, pose, pts[i], pts[i + 1], t, HALF_WIDTH, RED, GREEN, BLUE, alpha);
        }

        if (!sending) return;
        int count = len < 8.0 ? 2 : 3;
        double speed = PULSE_SPEED * (1.0 + 0.2 * e.band());
        double start = (phase / (Math.PI * 2.0)) + time * speed / len;
        double piece = Math.min(0.5, PULSE_LENGTH / len);
        for (int k = 0; k < count; k++)
        {
            double s = start + k / (double) count;
            s -= Math.floor(s);
            if (e.state() == 3) s = 1.0 - s;
            double s0 = Mth.clamp(s - piece * 0.5, 0.0, 1.0), s1 = Mth.clamp(s + piece * 0.5, 0.0, 1.0);
            if (s1 - s0 < 1.0e-4) continue;
            Vec3 p0 = point(a, dir, u, v, s0, len, time, phase), p1 = point(a, dir, u, v, s1, len, time, phase);
            strip(vc, pose, p0, p1, t, HALF_WIDTH * 4.0, RED, GREEN, BLUE, 0.35f);
            strip(vc, pose, p0, p1, t, HALF_WIDTH * 1.8, PULSE_R, PULSE_G, PULSE_B, 1.0f);
        }
    }

    /** point at fraction s of the link, wavering sideways (0 at both ends) */
    private static Vec3 point(Vec3 a, Vec3 dir, Vec3 u, Vec3 v, double s, double len, double time, double phase)
    {
        double env = Math.sin(Math.PI * s);
        double w = s * len * 0.9 - time * 0.12 + phase;
        double ou = WOBBLE * env * Math.sin(w), ov = WOBBLE * env * Math.cos(w * 0.7 + 1.3);
        return a.add(dir.scale(s)).add(u.scale(ou)).add(v.scale(ov));
    }

    /** camera-facing quad from p0 to p1 (camera at the origin) */
    private static void strip(VertexConsumer vc, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 t, double half,
                              float r, float g, float b, float alpha)
    {
        Vec3 s0 = side(p0, t, half), s1 = side(p1, t, half);
        vertex(vc, pose, p0.subtract(s0), r, g, b, alpha);
        vertex(vc, pose, p0.add(s0), r, g, b, alpha);
        vertex(vc, pose, p1.add(s1), r, g, b, alpha);
        vertex(vc, pose, p1.subtract(s1), r, g, b, alpha);
    }

    private static Vec3 side(Vec3 p, Vec3 t, double half)
    {
        Vec3 s = t.cross(p);
        if (s.lengthSqr() < 1.0e-9) s = t.cross(new Vec3(0, 1, 0));
        if (s.lengthSqr() < 1.0e-9) s = new Vec3(1, 0, 0);
        return s.normalize().scale(half);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float r, float g, float b, float a)
    {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0f, 1.0f, 0.0f);
    }
}
