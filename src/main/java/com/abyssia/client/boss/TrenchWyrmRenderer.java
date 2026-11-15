package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.BossChain;
import com.abyssia.entity.boss.TrenchWyrm;
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
 * Draws the wyrm joint by joint (head with a round maw and three jaw plates, ringed body segments with dorsal plates and
 * belly legs, tail needle), each turned toward the joint ahead. Parts are textured boxes with the ModelPart cube unwrap on
 * one 128x128 texture (tools/boss_wyrm_texture.py); glow points go in a second, full-bright pass.
 * Local space per joint: +Z = toward the head, +Y = up, units = blocks.
 * Segments scale only across (x, y), so the rings keep their length and the chain never opens gaps.
 * Rendering goes by the chain, not by the entity's own position, so the culling box is the whole chain.
 */
public class TrenchWyrmRenderer extends EntityRenderer<TrenchWyrm>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/trench_wyrm.png");
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float TEX = 128.0F;
    private static final float RING = 5.0F;
    /** rings reach this far past half the spacing on each end (front and back overlap a little) */
    private static final float OVERLAP = 0.25F;

    /** Texture unwrap regions {u, v, tw, th, tl} (texels): see tools/boss_wyrm_texture.py. */
    private static final int[] UV_RING = {0, 0, 10, 10, 12};
    private static final int[] UV_PLATE_TOP = {0, 24, 12, 2, 12};
    private static final int[] UV_PLATE_SIDE = {50, 24, 1, 6, 12};
    private static final int[] UV_HEAD = {0, 44, 16, 16, 13};
    private static final int[] UV_JAW = {0, 74, 10, 2, 10};
    private static final int[] UV_LEG = {80, 0, 1, 3, 1};
    private static final int[] UV_TOOTH = {88, 0, 1, 1, 2};
    private static final int[] UV_GLOW = {96, 0, 2, 1, 2};
    private static final int[] UV_NEEDLE = {80, 8, 4, 4, 6};

    /** A box: pivot, rotation (ModelPart order Z*Y*X), box min corner relative to the pivot, size, texture region. */
    private record Part(float px, float py, float pz, float rx, float ry, float rz,
                        float x0, float y0, float z0, float w, float h, float l, int[] uv)
    {
        static Part box(float x0, float y0, float z0, float w, float h, float l, int[] uv)
        {
            return new Part(0, 0, 0, 0, 0, 0, x0, y0, z0, w, h, l, uv);
        }
    }

    // ---- head (joint 0): maw on the front face (z = HEAD_FRONT), three jaw plates hinged on its rim ----
    private static final float HEAD_FRONT = 2.6F, JAW_R = 2.4F;
    private static final float JAW_CLOSED = 0.5F, JAW_OPEN = -0.75F;
    private static final List<Part> HEAD = new ArrayList<>();
    private static final List<Part> HEAD_GLOW = new ArrayList<>();
    private static final List<Part> JAW_PLATE = new ArrayList<>();
    // ---- body segment (scaled across per joint, ring built per draw) ----
    private static final List<Part> SEG = new ArrayList<>();
    private static final List<Part> SEG_GLOW = new ArrayList<>();
    // ---- tail needle (behind the last segment, unscaled) ----
    private static final List<Part> NEEDLE = new ArrayList<>();

    static
    {
        HEAD.add(Part.box(-3.2F, -3.2F, -2.6F, 6.4F, 6.4F, 5.2F, UV_HEAD));
        // head shield: a larger dorsal plate with flanks, lifted at the back over the first segment
        HEAD.add(new Part(0, 2.9F, 2.0F, 0.08F, 0, 0, -3.7F, 0, -5.6F, 7.4F, 1.0F, 5.6F, UV_PLATE_TOP));
        HEAD.add(new Part(0, 2.9F, 2.0F, 0.08F, 0, 0, 3.0F, -3.4F, -5.6F, 0.7F, 3.4F, 5.6F, UV_PLATE_SIDE));
        HEAD.add(new Part(0, 2.9F, 2.0F, 0.08F, 0, 0, -3.7F, -3.4F, -5.6F, 0.7F, 3.4F, 5.6F, UV_PLATE_SIDE));
        // inner tooth ring: 10 teeth on the maw rim, tilted toward the throat
        for (int t = 0; t < 10; t++)
        {
            float a = t * Mth.TWO_PI / 10 + 0.31F;
            HEAD.add(new Part(-1.7F * Mth.sin(a), 1.7F * Mth.cos(a), HEAD_FRONT, 0.7F, 0, a, -0.2F, -0.2F, 0, 0.4F, 0.4F, 1.2F, UV_TOOTH));
        }
        // six glow points around the maw: on the jaw axes (outside the hinges) and between the jaws
        for (int g = 0; g < 6; g++)
        {
            float a = g * Mth.TWO_PI / 6;
            HEAD_GLOW.add(Part.box(-3.05F * Mth.sin(a) - 0.25F, 3.05F * Mth.cos(a) - 0.25F, HEAD_FRONT - 0.02F, 0.5F, 0.5F, 0.2F, UV_GLOW));
        }

        // one jaw plate in its own frame: hinge at the origin, +Y = outward, +Z = forward; teeth on the inner face
        JAW_PLATE.add(Part.box(-2.1F, -0.35F, 0, 4.2F, 0.7F, 4.4F, UV_JAW));
        for (float z : new float[]{1.6F, 3.3F})
            for (float x : new float[]{-1.4F, 0.0F, 1.4F})
                JAW_PLATE.add(new Part(x, -0.35F, z, 0, 0, 0, -0.18F, -0.7F, -0.18F, 0.36F, 0.7F, 0.36F, UV_TOOTH));

        // dorsal plate (tergite): arched top + flank flaps; pivot at its front edge, tilted so the back edge lifts
        // over the front of the next plate toward the tail
        SEG.add(new Part(0, 2.3F, 2.2F, 0.06F, 0, 0, -3.0F, 0, -5.8F, 6.0F, 0.8F, 5.8F, UV_PLATE_TOP));
        SEG.add(new Part(0, 2.3F, 2.2F, 0.06F, 0, 0, 2.4F, -2.7F, -5.8F, 0.6F, 2.7F, 5.8F, UV_PLATE_SIDE));
        SEG.add(new Part(0, 2.3F, 2.2F, 0.06F, 0, 0, -3.0F, -2.7F, -5.8F, 0.6F, 2.7F, 5.8F, UV_PLATE_SIDE));
        // faint red-orange seam glow at the front of the plate (every second segment)
        SEG_GLOW.add(new Part(0, 2.3F, 2.2F, 0.06F, 0, 0, -2.0F, 0.8F, -0.6F, 4.0F, 0.12F, 0.5F, UV_GLOW));

        NEEDLE.add(Part.box(-1.1F, -1.1F, -2.6F, 2.2F, 2.2F, 2.8F, UV_NEEDLE));
        NEEDLE.add(Part.box(-0.65F, -0.65F, -5.2F, 1.3F, 1.3F, 2.8F, UV_NEEDLE));
        NEEDLE.add(Part.box(-0.25F, -0.25F, -7.8F, 0.5F, 0.5F, 2.8F, UV_NEEDLE));
    }

    /** smoothed maw opening per wyrm (client only) */
    private static final Map<TrenchWyrm, float[]> JAW = new WeakHashMap<>();

    public TrenchWyrmRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TrenchWyrm entity)
    {
        return TEXTURE;
    }

    /** Cross-section scale of joint k: 1.0 behind the head to 0.6 at the tail. */
    private static float jointScale(int k, int n)
    {
        if (k <= 0) return 1.0F;
        return 1.0F - 0.4F * (k - 1) / Math.max(1, n - 2);
    }

    @Override
    public void render(TrenchWyrm entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain chain = entity.chain();
        if (!chain.isInitialised()) return;
        int n = chain.size();
        if (n < 2) return;
        double ex = Mth.lerp(partialTick, entity.xo, entity.getX());
        double ey = Mth.lerp(partialTick, entity.yo, entity.getY());
        double ez = Mth.lerp(partialTick, entity.zo, entity.getZ());
        double[] jx = new double[n], jy = new double[n], jz = new double[n];
        for (int i = 0; i < n; i++)
        {
            jx[i] = chain.lerpX(i, partialTick);
            jy[i] = chain.lerpY(i, partialTick);
            jz[i] = chain.lerpZ(i, partialTick);
        }
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition();
        boolean lod = ClientConfig.LOD_ENABLED.get();
        // beyond the fog end the model vanishes in the fog: BossFarRenderer draws the fog-ignoring ribbon instead
        if (lod && BossFarRenderer.nearestJoint(chain, cp, partialTick) > BossFarRenderer.fogEnd()) return;

        // maw: synced from the AI (open while erupting / sweeping, snaps shut on the bite), smoothed here
        float time = entity.tickCount + partialTick;
        float[] jawState = JAW.computeIfAbsent(entity, e -> new float[1]);
        jawState[0] += ((entity.mawOpen() ? 1.0F : 0.0F) - jawState[0]) * 0.25F;
        float jaw = Math.max(jawState[0], 0.08F + 0.06F * Mth.sin(time * 0.07F));

        // plan: {first joint, last joint} per drawn piece; a single joint = full model
        List<int[]> plan = new ArrayList<>();
        int i = 0;
        while (i < n)
        {
            double d2 = sq(jx[i] - cp.x) + sq(jy[i] - cp.y) + sq(jz[i] - cp.z);
            // LOD: near = every joint; middle (64..160) = one simple tube per 2 joints + glow point; far (160..) = per 4 joints
            int step = (!lod || i == 0 || d2 < 64.0 * 64.0) ? 1 : d2 < 160.0 * 160.0 ? 2 : 4;
            int j = Math.min(i + step - 1, n - 1);
            plan.add(new int[]{i, j});
            i = j + 1;
        }

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        List<float[]> glows = new ArrayList<>();
        for (int[] p : plan)
        {
            if (p[0] == p[1]) joint(vc, pose, entity, p[0], n, jx, jy, jz, ex, ey, ez, jaw, time, false, chain.spacing());
            else tube(vc, pose, entity, p[0], p[1], n, jx, jy, jz, ex, ey, ez, cp, chain.spacing(), glows);
        }
        // full-bright pass for the maw lights and seam glow (a BufferSource ends the previous batch when the type changes,
        // so every solid quad above is already written)
        VertexConsumer ec = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        for (int[] p : plan)
            if (p[0] == p[1]) joint(ec, pose, entity, p[0], n, jx, jy, jz, ex, ey, ez, jaw, time, true, chain.spacing());
        if (glows.isEmpty()) return;
        // mid-range glow points: camera facing quads
        VertexConsumer gc = buffer.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PoseStack.Pose last = pose.last();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        float[][] quv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (float[] g : glows)
        {
            float s = g[3];
            for (int v = 0; v < 4; v++)
            {
                float sl = (v == 0 || v == 1) ? 1 : -1, su = (v == 1 || v == 2) ? 1 : -1;
                gc.addVertex(last, g[0] + (left.x * sl + up.x * su) * s, g[1] + (left.y * sl + up.y * su) * s, g[2] + (left.z * sl + up.z * su) * s)
                        .setColor(1.0F, 0.5F, 0.2F, 0.85F).setUv(quv[v][0], quv[v][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0, 0, 1);
            }
        }
    }

    private static double sq(double d)
    {
        return d * d;
    }

    /** Turns the pose so +Z points along (dx, dy, dz); false if the direction is degenerate. */
    private static boolean face(PoseStack pose, double dx, double dy, double dz)
    {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) return false;
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        pose.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(dy / len, -1.0, 1.0))));
        return true;
    }

    /** One joint in full detail: solid parts, or (glow = true) only the full-bright parts. */
    private void joint(VertexConsumer vc, PoseStack pose, TrenchWyrm entity, int k, int n, double[] jx, double[] jy, double[] jz,
                       double ex, double ey, double ez, float jaw, float time, boolean glow, double spacing)
    {
        int a = k == 0 ? 0 : k - 1, b = k == 0 ? 1 : k;
        int light = glow ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(entity.level(), BlockPos.containing(jx[k], jy[k], jz[k]));
        pose.pushPose();
        pose.translate(jx[k] - ex, jy[k] - ey, jz[k] - ez);
        if (!face(pose, jx[a] - jx[b], jy[a] - jy[b], jz[a] - jz[b]))
        {
            pose.popPose();
            return;
        }
        if (k == 0)
        {
            if (glow) draw(vc, pose, HEAD_GLOW, light);
            else
            {
                draw(vc, pose, HEAD, light);
                // three jaw plates at 120 degrees: closed they meet in a blunt cone, open they splay back
                float tilt = Mth.lerp(jaw, JAW_CLOSED, JAW_OPEN);
                for (int p = 0; p < 3; p++)
                {
                    float rot = p * Mth.TWO_PI / 3;
                    pose.pushPose();
                    pose.translate(-JAW_R * Mth.sin(rot), JAW_R * Mth.cos(rot), HEAD_FRONT);
                    pose.mulPose(Axis.ZP.rotation(rot));
                    pose.mulPose(Axis.XP.rotation(tilt));
                    draw(vc, pose, JAW_PLATE, light);
                    pose.popPose();
                }
            }
        }
        else
        {
            float s = jointScale(k, n);
            float len = (float) spacing + 2 * OVERLAP;
            pose.pushPose();
            pose.scale(s, s, 1.0F);
            if (glow)
            {
                if (k % 2 == 0) draw(vc, pose, SEG_GLOW, light);
            }
            else
            {
                part(vc, pose, Part.box(-RING / 2, -RING / 2, -len / 2, RING, RING, len, UV_RING), light, true);
                draw(vc, pose, SEG, light);
                // belly legs: two pairs, splayed outward, paddling in a wave down the body
                for (int pair = 0; pair < 2; pair++)
                {
                    float z = pair == 0 ? 1.3F : -1.3F;
                    float swing = 0.35F * Mth.sin(time * 0.25F - k * 0.6F + pair * 1.6F);
                    for (int side = -1; side <= 1; side += 2)
                        part(vc, pose, new Part(side * 1.7F, -2.3F, z, 0.25F + swing, 0, side * 0.45F,
                                -0.25F, -1.5F, -0.25F, 0.5F, 1.5F, 0.5F, UV_LEG), light, true);
                }
            }
            pose.popPose();
            if (k == n - 1 && !glow)
            {
                // tail needle off the back end of the last segment, unscaled
                pose.translate(0, 0, -len / 2 + 0.2F);
                draw(vc, pose, NEEDLE, light);
            }
        }
        pose.popPose();
    }

    /** Middle distance: one plain ring tube over joints i..j (ring texture, scale of the middle joint) plus a glow point. */
    private void tube(VertexConsumer vc, PoseStack pose, TrenchWyrm entity, int i, int j, int n, double[] jx, double[] jy, double[] jz,
                      double ex, double ey, double ez, Vec3 cp, double spacing, List<float[]> glows)
    {
        double mx = (jx[i] + jx[j]) * 0.5, my = (jy[i] + jy[j]) * 0.5, mz = (jz[i] + jz[j]) * 0.5;
        double dx = jx[i] - jx[j], dy = jy[i] - jy[j], dz = jz[i] - jz[j];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) return;
        int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(mx, my, mz));
        float s = jointScale((i + j) / 2, n);
        float l = (float) (len + spacing + 0.4);
        pose.pushPose();
        pose.translate(mx - ex, my - ey, mz - ez);
        face(pose, dx, dy, dz);
        pose.scale(s, s, 1.0F);
        part(vc, pose, Part.box(-RING / 2, -RING / 2, -l / 2, RING, RING, l, UV_RING), light, false);
        pose.popPose();
        double dist = Math.sqrt(sq(mx - cp.x) + sq(my - cp.y) + sq(mz - cp.z));
        glows.add(new float[]{(float) (mx - ex), (float) (my - ey), (float) (mz - ez), (float) (1.0 + dist * 0.006)});
    }

    private static void draw(VertexConsumer vc, PoseStack pose, List<Part> parts, int light)
    {
        for (Part p : parts) part(vc, pose, p, light, true);
    }

    /** A box with the ModelPart cube unwrap: TOP/BOTTOM in the first row, RIGHT/FRONT/LEFT/BACK below. */
    private static void part(VertexConsumer vc, PoseStack pose, Part p, int light, boolean caps)
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
            quad(vc, last, light, x0, y1, z1, p.w, 0, 0, 0, -p.h, 0, u + tl, v + tl, tw, th, 0, 0, 1);              // front +Z
            quad(vc, last, light, x1, y1, z0, -p.w, 0, 0, 0, -p.h, 0, u + 2 * tl + tw, v + tl, tw, th, 0, 0, -1);   // back -Z
        }
        quad(vc, last, light, x1, y1, z1, 0, 0, -p.l, 0, -p.h, 0, u, v + tl, tl, th, 1, 0, 0);                  // right +X
        quad(vc, last, light, x0, y1, z0, 0, 0, p.l, 0, -p.h, 0, u + tl + tw, v + tl, tl, th, -1, 0, 0);        // left -X
        quad(vc, last, light, x0, y1, z0, p.w, 0, 0, 0, 0, p.l, u + tl, v, tw, tl, 0, 1, 0);                     // top +Y
        quad(vc, last, light, x0, y0, z1, p.w, 0, 0, 0, 0, -p.l, u + tl + tw, v, tw, tl, 0, -1, 0);             // bottom -Y
        if (turned) pose.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose last, int light, float ox, float oy, float oz,
                             float ux, float uy, float uz, float vx, float vy, float vz,
                             int tu, int tv, int tw, int th, float nx, float ny, float nz)
    {
        float u0 = tu / TEX, v0 = tv / TEX, u1 = (tu + tw) / TEX, v1 = (tv + th) / TEX;
        vertex(vc, last, light, ox, oy, oz, u0, v0, nx, ny, nz);
        vertex(vc, last, light, ox + vx, oy + vy, oz + vz, u0, v1, nx, ny, nz);
        vertex(vc, last, light, ox + ux + vx, oy + uy + vy, oz + uz + vz, u1, v1, nx, ny, nz);
        vertex(vc, last, light, ox + ux, oy + uy, oz + uz, u1, v0, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, int light, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz)
    {
        vc.addVertex(last, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(last, nx, ny, nz);
    }
}
