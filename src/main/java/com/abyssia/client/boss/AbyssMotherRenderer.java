package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssMother;
import com.abyssia.entity.boss.AbyssMotherSpawn;
import com.abyssia.entity.boss.BossChain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the abyss mother, a giant jelly: a stepped dome bell (16 sectors x 7 layers, diameter ~36, height ~14) with 16
 * scallop flaps on the rim waving in a travelling wave, an inner bell of 8 valves closed around the glowing core (the abyss
 * heart) that open like a flower in BLOOM, 4 short frilled oral arms, and 12 thin tentacles drawn as one tapering box per
 * joint pair (coarser with distance). The bell beats (scale 0.92..1.0 with {@link AbyssMother#beat}); in BLOOM it widens and
 * flattens. Parts are textured boxes with the ModelPart cube unwrap on one 128x128 texture (tools/boss_mother_texture.py);
 * the radial canals / gonads inside the bell, the 32 rim lights and the tentacle bands go in a second, full-bright
 * translucent pass. Rendering goes by the chains, not by the entity position; beyond the fog end the model is skipped and
 * {@link BossFarRenderer} draws the ribbons. Bell local space: world axes, origin = bell centre (head joint), units = blocks.
 * Bell sector space: +X = radially out. Tentacle local space per box: +Z = toward the tip.
 */
public class AbyssMotherRenderer extends EntityRenderer<AbyssMother>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(Abyssia.MODID, "textures/entity/abyss_mother.png");
    private static final ResourceLocation WHITE = new ResourceLocation("textures/misc/white.png");
    private static final float TEX = 128.0F;
    private static final float OVERLAP = 0.6F;

    /** Texture unwrap regions {u, v, tw, th, tl} (texels): see tools/boss_mother_texture.py. */
    private static final int[] UV_BELL = {0, 0, 4, 6, 12};
    private static final int[] UV_RIM = {32, 0, 4, 6, 12};
    private static final int[] UV_CANAL = {64, 0, 1, 6, 12};
    private static final int[] UV_GONAD = {90, 0, 1, 6, 12};
    private static final int[] UV_DOT = {116, 0, 2, 2, 2};
    private static final int[] UV_SCALLOP = {0, 20, 2, 6, 12};
    private static final int[] UV_CAP = {28, 20, 8, 2, 8};
    private static final int[] UV_CORE = {60, 20, 8, 8, 8};
    private static final int[] UV_PETAL = {92, 20, 1, 8, 6};
    private static final int[] UV_PETAL_TIP = {106, 20, 1, 8, 6};
    private static final int[] UV_ARM = {0, 40, 4, 8, 4};
    private static final int[] UV_FRILL = {16, 40, 1, 8, 3};
    private static final int[] UV_TENT = {24, 40, 4, 4, 16};
    private static final int[] UV_TGLOW = {64, 40, 4, 4, 2};
    private static final int[] UV_RING = {76, 40, 1, 6, 12};

    // ---- bell: 16 sectors x 7 layers of 2, radius = half ellipse (R 18, H 14) at the layer middle ----
    private static final int SECTORS = 16, LAYERS = 7, PETALS = 8, ORAL_ARMS = 4;
    private static final float BELL_R = 18.0F, BELL_H = 14.0F, RIM_Y = -6.0F, LAYER_H = 2.0F, CORE_Y = -4.0F;
    private static final float TAN = (float) Math.tan(Math.PI / SECTORS), DOT_TAN = (float) Math.tan(Math.PI / (2 * SECTORS));
    private static final float[] LAYER_R = new float[LAYERS], LAYER_TH = new float[LAYERS];
    /** brood jelly size relative to the mother */
    private static final float SPAWN_SCALE = 1.0F / 30.0F;

    /** A box: pivot, rotation (ModelPart order Z*Y*X), box min corner relative to the pivot, size, texture region. */
    private record Part(float px, float py, float pz, float rx, float ry, float rz,
                        float x0, float y0, float z0, float w, float h, float l, int[] uv)
    {
        static Part box(float x0, float y0, float z0, float w, float h, float l, int[] uv)
        {
            return new Part(0, 0, 0, 0, 0, 0, x0, y0, z0, w, h, l, uv);
        }
    }

    /** crown cap over the top layer: two squares, the second turned 45 degrees (an octagon) */
    private static final List<Part> CAP = new ArrayList<>();

    static
    {
        for (int k = 0; k < LAYERS; k++)
        {
            float t = (2 * k + 1) * LAYER_H * 0.5F / BELL_H;
            LAYER_R[k] = BELL_R * (float) Math.sqrt(1.0F - t * t);
        }
        // each layer is thick enough that its top face covers the step up to the next (no gaps seen from above)
        for (int k = 0; k < LAYERS - 1; k++) LAYER_TH[k] = Math.max(1.6F, LAYER_R[k] - LAYER_R[k + 1] + 0.3F);
        LAYER_TH[LAYERS - 1] = LAYER_R[LAYERS - 1] - 3.5F;
        CAP.add(Part.box(-3.7F, RIM_Y + BELL_H - 0.2F, -3.7F, 7.4F, 0.6F, 7.4F, UV_CAP));
        CAP.add(new Part(0, 0, 0, 0, Mth.PI / 4, 0, -3.7F, RIM_Y + BELL_H - 0.17F, -3.7F, 7.4F, 0.6F, 7.4F, UV_CAP));
    }

    /** bell openness per mother (0 closed .. 1 BLOOM), eased on the client so the valves open over ~1 s */
    private final Map<AbyssMother, float[]> opens = new WeakHashMap<>();

    public AbyssMotherRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssMother entity)
    {
        return TEXTURE;
    }

    private float openness(AbyssMother entity, float time)
    {
        float target = entity.bloom() ? 1.0F : 0.0F;
        float[] s = opens.computeIfAbsent(entity, e -> new float[]{target, time});
        float dt = Mth.clamp(time - s[1], 0.0F, 20.0F);
        s[1] = time;
        s[0] += (target - s[0]) * Math.min(1.0F, dt * 0.06F);
        return s[0] * s[0] * (3.0F - 2.0F * s[0]);
    }

    /** Tentacle thickness at joint position k: 1.2 at the root to 0.4 at the tip. */
    private static float tentWidth(float k)
    {
        return Mth.lerp(k / (AbyssMother.JOINTS - 1), 1.2F, 0.4F);
    }

    @Override
    public void render(AbyssMother entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain spine = entity.chain();
        if (!spine.isInitialised()) return;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition();
        boolean lod = ClientConfig.LOD_ENABLED.get();
        if (lod && BossFarRenderer.nearestPoint(entity.farPoints(pt), cp) > BossFarRenderer.fogEnd()) return;
        double ex = Mth.lerp(pt, entity.xo, entity.getX()), ey = Mth.lerp(pt, entity.yo, entity.getY()), ez = Mth.lerp(pt, entity.zo, entity.getZ());
        double hx = spine.lerpX(0, pt), hy = spine.lerpY(0, pt), hz = spine.lerpZ(0, pt);
        double ox = hx - ex, oy = hy - ey, oz = hz - ez;
        float time = entity.tickCount + pt;
        float beat = entity.beat(pt);
        float open = openness(entity, time);

        // plan: {tentacle, first joint, last joint} per drawn box
        List<int[]> plan = new ArrayList<>();
        for (int t = 0; t < AbyssMother.TENTACLES; t++)
        {
            BossChain c = entity.tentacle(t);
            if (!c.isInitialised()) continue;
            int last = AbyssMother.JOINTS - 1;
            int i = 0;
            while (i < last)
            {
                double d2 = sq(c.lerpX(i, pt) - cp.x) + sq(c.lerpY(i, pt) - cp.y) + sq(c.lerpZ(i, pt) - cp.z);
                // LOD: near = every joint pair; 64..160 = 2 joints per box; beyond = 4
                int step = (!lod || d2 < 64.0 * 64.0) ? 1 : d2 < 160.0 * 160.0 ? 2 : 4;
                int j = Math.min(i + step, last);
                plan.add(new int[]{t, i, j});
                i = j;
            }
        }

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(hx, hy, hz));
        float cr = Mth.lerp(open, 2.2F, 3.4F) * (1.0F + 0.04F * beat);
        pose.pushPose();
        pose.translate(ox, oy, oz);
        bell(vc, pose, time, beat, open, light, false);
        inner(vc, pose, time, open, light);
        oralArms(vc, pose, time, open, light);
        pose.translate(0, CORE_Y, 0);
        core(vc, pose, cr);
        pose.popPose();
        List<float[]> glows = new ArrayList<>();
        for (int[] p : plan) segment(vc, pose, entity, p[0], p[1], p[2], pt, ex, ey, ez, cp, false, glows);

        // full-bright pass: canals and gonads inside the bell, the rim lights, the tentacle bands (a BufferSource ends the
        // previous batch when the type changes, so every solid quad above is already written)
        VertexConsumer ec = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        pose.pushPose();
        pose.translate(ox, oy, oz);
        bell(ec, pose, time, beat, open, LightTexture.FULL_BRIGHT, true);
        pose.popPose();
        for (int[] p : plan) segment(ec, pose, entity, p[0], p[1], p[2], pt, ex, ey, ez, cp, true, glows);

        VertexConsumer gc = buffer.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        // halo round the heart (mostly hidden by the closed valves), the glow ring while the pulse charges
        float ha = 0.12F + 0.22F * open + 0.06F * beat;
        glowBox(gc, pose, ox, oy + CORE_Y, oz, cr * 2.6F, cr * 2.6F, cr * 2.6F, 1.0F, 0.35F, 0.85F, ha);
        if (entity.warning())
        {
            float a = 0.35F + 0.25F * Mth.sin((entity.tickCount + pt) * 0.6F);
            // 16 glowing beads round the rim (a box round the whole bell read as a big pale rectangle in game)
            for (int i = 0; i < 16; i++)
            {
                double ang = i * (Math.PI * 2 / 16);
                glowBox(gc, pose, ox + Math.cos(ang) * 18.5, oy - 5.5, oz + Math.sin(ang) * 18.5, 2.4F, 1.0F, 2.4F, 0.8F, 1.0F, 1.0F, a);
            }
        }
        if (glows.isEmpty()) return;
        // mid-range tentacle glow points: camera facing quads
        PoseStack.Pose last = pose.last();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        float[][] quv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (float[] g : glows)
        {
            float s = g[3];
            for (int v = 0; v < 4; v++)
            {
                float sl = (v == 0 || v == 1) ? 1 : -1, su = (v == 1 || v == 2) ? 1 : -1;
                gc.vertex(last.pose(), g[0] + (left.x * sl + up.x * su) * s, g[1] + (left.y * sl + up.y * su) * s, g[2] + (left.z * sl + up.z * su) * s)
                        .color(0.78F, 0.80F, 1.0F, 0.5F).uv(quv[v][0], quv[v][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT).normal(last.normal(), 0, 0, 1).endVertex();
            }
        }
    }

    private static double sq(double d)
    {
        return d * d;
    }

    /**
     * Bell in bell space, scaled by the beat (0.92..1.0 across, a little taller when contracted) and the bloom (wider,
     * flatter): sector layers, rim scallops and the crown cap; glow = only the canals/gonads inside and the 32 rim lights.
     */
    private static void bell(VertexConsumer vc, PoseStack pose, float time, float beat, float open, int light, boolean glow)
    {
        float pulse = 0.96F - 0.04F * beat;
        float sx = pulse * (1.0F + 0.22F * open), sy = (2.0F - pulse) * (1.0F - 0.4F * open);
        float dots = 0.35F + 0.65F * (0.5F + 0.5F * beat);
        float canal = 0.45F + 0.3F * (0.5F + 0.5F * beat);
        pose.pushPose();
        pose.scale(sx, sy, sx);
        for (int k = 0; k < SECTORS; k++)
        {
            float e = (k & 1) * 0.02F;   // odd sectors stand a hair proud so overlapping neighbours do not z-fight
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(k * Mth.TWO_PI / SECTORS));
            for (int n = 0; n < LAYERS; n++)
            {
                float r = LAYER_R[n] + e, th = LAYER_TH[n], y0 = RIM_Y + n * LAYER_H, hw = r * TAN + 0.05F;
                if (!glow) part(vc, pose, Part.box(r - th, y0, -hw, th, LAYER_H + e, 2 * hw, n == 0 ? UV_RIM : UV_BELL), light, false, 1.0F);
                else if (n < LAYERS - 1)
                {
                    // a thin sheet just inside the inner face: ring canal on the margin, gonads in the middle layers
                    int[] uv = n == 0 ? UV_RING : (n == 3 || n == 4) ? UV_GONAD : UV_CANAL;
                    part(vc, pose, Part.box(r - th - 0.08F, y0 + 0.05F, -hw * 0.92F, 0.05F, LAYER_H - 0.1F, hw * 1.84F, uv), light, false, canal);
                }
            }
            if (glow)
            {
                // two rim lights per sector, on the outer face of the margin layer
                float r = LAYER_R[0] + e, z = r * DOT_TAN;
                for (float dz : new float[]{-z, z}) part(vc, pose, Part.box(r + 0.01F, RIM_Y + 0.35F, dz - 0.28F, 0.22F, 0.56F, 0.56F, UV_DOT), light, true, dots);
            }
            else
            {
                // scallop: hinged under the margin, a wave running round the bell, flaring out when the bell opens
                float tilt = 0.18F + 0.22F * Mth.sin(time * 0.18F - k * (Mth.PI / 4)) + 0.75F * open;
                pose.translate(LAYER_R[0] - 0.35F + e, RIM_Y + 0.2F, 0);
                pose.mulPose(Axis.ZP.rotation(tilt));
                part(vc, pose, Part.box(-0.3F, -3.4F, -3.7F, 0.6F, 3.4F, 7.4F, UV_SCALLOP), light, false, 1.0F);
            }
            pose.popPose();
        }
        if (!glow) draw(vc, pose, CAP, light);
        pose.popPose();
    }

    /** Inner bell: 8 valves hinged round the heart, closed into a bud (tips meeting under it) or opened flat like a flower. */
    private static void inner(VertexConsumer vc, PoseStack pose, float time, float open, int light)
    {
        float tilt = Mth.lerp(open, 0.26F, 1.45F), curl = Mth.lerp(open, 0.95F, -0.3F);
        for (int p = 0; p < PETALS; p++)
        {
            float sway = 0.05F * Mth.sin(time * 0.1F + p * 1.7F);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(p * Mth.TWO_PI / PETALS + Mth.PI / PETALS));
            pose.translate(2.8F, -0.5F, 0);
            pose.mulPose(Axis.ZP.rotation(tilt + sway));
            part(vc, pose, Part.box(-0.25F, -3.6F, -1.5F, 0.5F, 3.6F, 3.0F, UV_PETAL), light, true, 1.0F);
            pose.translate(0, -3.5F, 0);
            pose.mulPose(Axis.ZP.rotation(-curl + sway));
            part(vc, pose, Part.box(-0.25F, -4.0F, -1.2F, 0.5F, 4.0F, 2.4F, UV_PETAL_TIP), light, true, 1.0F);
            pose.popPose();
        }
    }

    /** Four short, thick oral arms with ruffled side plates (the plates turn 90 degrees from segment to segment). */
    private static void oralArms(VertexConsumer vc, PoseStack pose, float time, float open, int light)
    {
        for (int q = 0; q < ORAL_ARMS; q++)
        {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(q * (Mth.PI / 2) + Mth.PI / 4));
            pose.translate(4.6F, -3.0F, 0);
            pose.mulPose(Axis.ZP.rotation(0.16F + 0.55F * open));
            pose.mulPose(Axis.XP.rotation(0.1F * Mth.sin(time * 0.07F + q)));
            for (int s = 0; s < 4; s++)
            {
                float w = Mth.lerp(s / 3.0F, 2.6F, 1.4F), len = 3.4F, fw = 1.0F + 0.35F * w;
                part(vc, pose, Part.box(-w / 2, -len - 0.3F, -w / 2, w, len + 0.6F, w, UV_ARM), light, true, 1.0F);
                pose.pushPose();
                if ((s & 1) == 1) pose.mulPose(Axis.YP.rotation((Mth.PI / 2)));
                part(vc, pose, Part.box(-0.15F, -len, w / 2 - 0.1F, 0.3F, len, fw, UV_FRILL), light, false, 1.0F);
                part(vc, pose, Part.box(-0.15F, -len, -w / 2 + 0.1F - fw, 0.3F, len, fw, UV_FRILL), light, false, 1.0F);
                pose.popPose();
                pose.translate(0, -len, 0);
                pose.mulPose(Axis.ZP.rotation(0.06F + 0.12F * Mth.sin(time * 0.09F + q * 1.3F + s * 0.8F)));
                pose.mulPose(Axis.XP.rotation(0.08F * Mth.sin(time * 0.07F + s + q)));
            }
            pose.popPose();
        }
    }

    /** The abyss heart: a stepped sphere of radius cr round the origin, full bright (drawn solid so the valves hide it). */
    private static void core(VertexConsumer vc, PoseStack pose, float cr)
    {
        int fb = LightTexture.FULL_BRIGHT;
        part(vc, pose, Part.box(-cr, -0.55F * cr, -cr, 2 * cr, 1.1F * cr, 2 * cr, UV_CORE), fb, true, 1.0F);
        part(vc, pose, Part.box(-0.75F * cr, -0.85F * cr, -0.75F * cr, 1.5F * cr, 1.7F * cr, 1.5F * cr, UV_CORE), fb, true, 1.0F);
        part(vc, pose, Part.box(-0.45F * cr, -cr, -0.45F * cr, 0.9F * cr, 2 * cr, 0.9F * cr, UV_CORE), fb, true, 1.0F);
    }

    /** One tentacle box over joints i..j: solid, or (glow = true) a faint band on every second near segment. */
    private void segment(VertexConsumer vc, PoseStack pose, AbyssMother entity, int t, int i, int j, float pt, double ex, double ey, double ez,
                         Vec3 cp, boolean glow, List<float[]> glows)
    {
        BossChain c = entity.tentacle(t);
        double ax = c.lerpX(i, pt), ay = c.lerpY(i, pt), az = c.lerpZ(i, pt);
        double bx = c.lerpX(j, pt), by = c.lerpY(j, pt), bz = c.lerpZ(j, pt);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) return;
        boolean detail = j - i == 1;
        boolean tip = j == AbyssMother.JOINTS - 1;
        if (glow && !(detail && (i & 1) == 0)) return;
        double mx = (ax + bx) * 0.5, my = (ay + by) * 0.5, mz = (az + bz) * 0.5;
        float w = tentWidth((i + j) * 0.5F);
        float l = (float) len + 2 * OVERLAP;
        int light = glow ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(entity.level(), BlockPos.containing(mx, my, mz));
        pose.pushPose();
        pose.translate(mx - ex, my - ey, mz - ez);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        pose.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(dy / len, -1.0, 1.0))));
        float hw = w / 2;
        if (glow) part(vc, pose, Part.box(-hw - 0.06F, -hw - 0.06F, -0.35F, w + 0.12F, w + 0.12F, 0.7F, UV_TGLOW), light, false, 0.55F);
        else part(vc, pose, Part.box(-hw, -hw, -l / 2, w, w, l, UV_TENT), light, tip, 1.0F);
        pose.popPose();
        if (!glow && !detail && (i % 4 == 0 || tip))
        {
            double dist = Math.sqrt(sq(mx - cp.x) + sq(my - cp.y) + sq(mz - cp.z));
            glows.add(new float[]{(float) (mx - ex), (float) (my - ey), (float) (mz - ez), (float) (0.35 + dist * 0.004)});
        }
    }

    private static void glowBox(VertexConsumer vc, PoseStack pose, double cx, double cy, double cz, float w, float h, float l, float r, float g, float b, float a)
    {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        PoseStack.Pose last = pose.last();
        float x0 = -w / 2, y0 = -h / 2, z0 = -l / 2, x1 = x0 + w, y1 = y0 + h, z1 = z0 + l;
        float[][] v = {
                {x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1}, {x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0},
                {x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0}};
        for (float[] q : v)
            for (int k = 0; k < 4; k++)
                vc.vertex(last.pose(), q[3 * k], q[3 * k + 1], q[3 * k + 2]).color(r, g, b, a).uv(k == 0 || k == 3 ? 0 : 1, k < 2 ? 0 : 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(last.normal(), 0, 1, 0).endVertex();
        pose.popPose();
    }

    private static void draw(VertexConsumer vc, PoseStack pose, List<Part> parts, int light)
    {
        for (Part p : parts) part(vc, pose, p, light, true, 1.0F);
    }

    /** A box with the ModelPart cube unwrap: TOP/BOTTOM in the first row, RIGHT/FRONT/LEFT/BACK below; a = vertex alpha. */
    private static void part(VertexConsumer vc, PoseStack pose, Part p, int light, boolean caps, float a)
    {
        boolean turned = p.px != 0 || p.py != 0 || p.pz != 0 || p.rx != 0 || p.ry != 0 || p.rz != 0;
        if (turned)
        {
            pose.pushPose();
            pose.translate(p.px, p.py, p.pz);
            if (p.rz != 0) pose.mulPose(Axis.ZP.rotation(p.rz));
            if (p.ry != 0) pose.mulPose(Axis.YP.rotation(p.ry));
            if (p.rx != 0) pose.mulPose(Axis.XP.rotation(p.rx));
        }
        PoseStack.Pose last = pose.last();
        float x0 = p.x0, y0 = p.y0, z0 = p.z0, x1 = x0 + p.w, y1 = y0 + p.h, z1 = z0 + p.l;
        int u = p.uv[0], v = p.uv[1], tw = p.uv[2], th = p.uv[3], tl = p.uv[4];
        // sides: origin = texture top-left corner, eu = texture +u, ev = texture +v (down)
        if (caps)
        {
            quad(vc, last, light, a, x0, y1, z1, p.w, 0, 0, 0, -p.h, 0, u + tl, v + tl, tw, th, 0, 0, 1);              // front +Z
            quad(vc, last, light, a, x1, y1, z0, -p.w, 0, 0, 0, -p.h, 0, u + 2 * tl + tw, v + tl, tw, th, 0, 0, -1);   // back -Z
        }
        quad(vc, last, light, a, x1, y1, z1, 0, 0, -p.l, 0, -p.h, 0, u, v + tl, tl, th, 1, 0, 0);                  // right +X
        quad(vc, last, light, a, x0, y1, z0, 0, 0, p.l, 0, -p.h, 0, u + tl + tw, v + tl, tl, th, -1, 0, 0);        // left -X
        quad(vc, last, light, a, x0, y1, z0, p.w, 0, 0, 0, 0, p.l, u + tl, v, tw, tl, 0, 1, 0);                     // top +Y
        quad(vc, last, light, a, x0, y0, z1, p.w, 0, 0, 0, 0, -p.l, u + tl + tw, v, tw, tl, 0, -1, 0);             // bottom -Y
        if (turned) pose.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose last, int light, float a, float ox, float oy, float oz,
                             float ux, float uy, float uz, float vx, float vy, float vz,
                             int tu, int tv, int tw, int th, float nx, float ny, float nz)
    {
        float u0 = tu / TEX, v0 = tv / TEX, u1 = (tu + tw) / TEX, v1 = (tv + th) / TEX;
        vertex(vc, last, light, a, ox, oy, oz, u0, v0, nx, ny, nz);
        vertex(vc, last, light, a, ox + vx, oy + vy, oz + vz, u0, v1, nx, ny, nz);
        vertex(vc, last, light, a, ox + ux + vx, oy + uy + vy, oz + uz + vz, u1, v1, nx, ny, nz);
        vertex(vc, last, light, a, ox + ux, oy + uy, oz + uz, u1, v0, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, int light, float a, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz)
    {
        vc.vertex(last.pose(), x, y, z).color(1.0F, 1.0F, 1.0F, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(last.normal(), nx, ny, nz).endVertex();
    }

    /**
     * Brood jelly: the mother's bell, inner bell, heart and oral arms at 1/30 scale (about 1.2 across, the hitbox), always
     * closed, beating faster, with 8 short swaying tentacles; the same texture regions and glow pass.
     */
    public static class Spawn extends EntityRenderer<AbyssMotherSpawn>
    {
        public Spawn(EntityRendererProvider.Context context)
        {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(AbyssMotherSpawn entity)
        {
            return TEXTURE;
        }

        @Override
        public void render(AbyssMotherSpawn entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
        {
            float time = entity.tickCount + pt + entity.getId() * 7;
            float beat = Mth.sin(time * (Mth.TWO_PI / 24.0F));
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            pose.pushPose();
            pose.translate(0, 0.85, 0);
            pose.scale(SPAWN_SCALE, SPAWN_SCALE, SPAWN_SCALE);
            bell(vc, pose, time, beat, 0.0F, packedLight, false);
            inner(vc, pose, time, 0.0F, packedLight);
            oralArms(vc, pose, time, 0.0F, packedLight);
            pose.pushPose();
            pose.translate(0, CORE_Y, 0);
            core(vc, pose, 2.2F);
            pose.popPose();
            for (int t = 0; t < 8; t++)
            {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotation(t * Mth.PI / 4 + Mth.PI / 8));
                pose.translate(13.0F, -5.0F, 0);
                pose.mulPose(Axis.XP.rotation((Mth.PI / 2)));   // local +Z = down the strand
                for (int s = 0; s < 4; s++)
                {
                    float w = Mth.lerp(s / 3.0F, 2.4F, 1.0F);
                    pose.mulPose(Axis.YP.rotation(0.25F * Mth.sin(time * 0.15F + t + s * 0.9F) - (s == 0 ? 0.2F : 0.0F)));
                    part(vc, pose, Part.box(-w / 2, -w / 2, -0.3F, w, w, 6.6F, UV_TENT), packedLight, s == 3, 1.0F);
                    pose.translate(0, 0, 6.0F);
                }
                pose.popPose();
            }
            VertexConsumer ec = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
            bell(ec, pose, time, beat, 0.0F, LightTexture.FULL_BRIGHT, true);
            pose.popPose();
        }
    }
}
