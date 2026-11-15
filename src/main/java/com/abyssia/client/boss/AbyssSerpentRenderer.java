package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssSerpent;
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
 * Draws the serpent joint by joint (head, tapering body segments, tail with a fin), each turned toward the joint ahead.
 * Parts are textured boxes with the ModelPart cube unwrap on one 128x128 texture; glow organs and eyes go in a second,
 * full-bright pass. Local space per joint: +Z = toward the head, +Y = up, units = blocks.
 * Rendering goes by the chain, not by the entity's own position, so the culling box is the whole chain.
 */
public class AbyssSerpentRenderer extends EntityRenderer<AbyssSerpent>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/abyss_serpent.png");
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float TEX = 128.0F;
    private static final int TAIL_JOINTS = 4;
    private static final float BODY = 4.5F;
    /** segments reach this far past half the spacing on each end, so bends do not open gaps */
    private static final float OVERLAP = 0.4F;

    /** Texture unwrap regions {u, v, tw, th, tl} (texels): see the generator comment in the texture notes. */
    private static final int[] UV_BODY = {0, 0, 12, 12, 16};
    private static final int[] UV_CRANIUM = {0, 28, 18, 9, 15};
    private static final int[] UV_SNOUT = {0, 52, 12, 6, 12};
    private static final int[] UV_JAW = {0, 70, 11, 5, 24};
    private static final int[] UV_FIN = {0, 100, 1, 10, 15};
    private static final int[] UV_SPINE = {60, 0, 2, 6, 4};
    private static final int[] UV_FRILL = {76, 0, 2, 9, 4};
    private static final int[] UV_TOOTH = {92, 0, 1, 3, 1};
    private static final int[] UV_GLOW = {100, 0, 3, 1, 3};
    private static final int[] UV_EYE = {100, 8, 1, 2, 3};

    /** A box: pivot, rotation (ModelPart order Z*Y*X), box min corner relative to the pivot, size, texture region. */
    private record Part(float px, float py, float pz, float rx, float ry, float rz,
                        float x0, float y0, float z0, float w, float h, float l, int[] uv)
    {
        static Part box(float x0, float y0, float z0, float w, float h, float l, int[] uv)
        {
            return new Part(0, 0, 0, 0, 0, 0, x0, y0, z0, w, h, l, uv);
        }
    }

    // ---- head (joint 0): hinge of both jaws at y = -0.4, z = -2.0 ----
    private static final float HINGE_Y = -0.4F, HINGE_Z = -2.0F;
    private static final List<Part> UPPER = new ArrayList<>();
    private static final List<Part> UPPER_GLOW = new ArrayList<>();
    private static final List<Part> LOWER = new ArrayList<>();
    private static final List<Part> FRILL = new ArrayList<>();
    // ---- body segment (scaled per joint) ----
    private static final List<Part> SEG_DECOR = new ArrayList<>();
    private static final List<Part> SEG_GLOW = new ArrayList<>();
    // ---- tail fin (last joint, unscaled) ----
    private static final List<Part> FIN = new ArrayList<>();
    private static final List<Part> FIN_GLOW = new ArrayList<>();

    static
    {
        UPPER.add(Part.box(-3.0F, -0.4F, -2.5F, 6.0F, 3.0F, 5.0F, UV_CRANIUM));
        UPPER.add(Part.box(-2.0F, -0.4F, 2.5F, 4.0F, 2.0F, 4.0F, UV_SNOUT));
        for (float z : new float[]{3.0F, 4.1F, 5.2F})
            for (float x : new float[]{-1.98F, 1.68F}) UPPER.add(Part.box(x, -1.3F, z, 0.3F, 0.9F, 0.3F, UV_TOOTH));
        for (float x : new float[]{-1.4F, 1.0F}) UPPER.add(Part.box(x, -1.9F, 6.0F, 0.4F, 1.5F, 0.4F, UV_TOOTH));
        UPPER_GLOW.add(Part.box(2.95F, 1.0F, 0.6F, 0.2F, 0.6F, 1.0F, UV_EYE));
        UPPER_GLOW.add(Part.box(-3.15F, 1.0F, 0.6F, 0.2F, 0.6F, 1.0F, UV_EYE));
        UPPER_GLOW.add(Part.box(-1.45F, 2.55F, -1.6F, 0.9F, 0.25F, 0.9F, UV_GLOW));
        UPPER_GLOW.add(Part.box(0.55F, 2.55F, -1.6F, 0.9F, 0.25F, 0.9F, UV_GLOW));

        LOWER.add(Part.box(-1.7F, -2.1F, -2.3F, 3.4F, 1.7F, 8.2F, UV_JAW));
        for (float z : new float[]{3.0F, 4.2F, 5.3F})
            for (float x : new float[]{-1.6F, 1.3F}) LOWER.add(Part.box(x, -0.5F, z, 0.3F, 0.8F, 0.3F, UV_TOOTH));
        for (float x : new float[]{-1.1F, 0.75F}) LOWER.add(Part.box(x, -0.5F, 5.4F, 0.35F, 1.2F, 0.35F, UV_TOOTH));

        // neck frill: spikes fanning out behind the skull, leaning back
        FRILL.add(new Part(0.0F, 2.4F, -2.0F, -1.0F, 0, 0, -0.3F, 0, -0.6F, 0.6F, 3.4F, 1.2F, UV_FRILL));
        float[][] fan = {{1.5F, 2.3F, 0.45F}, {2.5F, 1.6F, 0.95F}, {2.9F, 0.5F, 1.4F}};
        for (float[] s : fan)
        {
            FRILL.add(new Part(s[0], s[1], -2.0F, -0.9F, 0, -s[2], -0.3F, 0, -0.6F, 0.6F, 3.0F, 1.2F, UV_FRILL));
            FRILL.add(new Part(-s[0], s[1], -2.0F, -0.9F, 0, s[2], -0.3F, 0, -0.6F, 0.6F, 3.0F, 1.2F, UV_FRILL));
        }

        // body segment decoration (main box is built per joint, its length depends on the scale)
        SEG_DECOR.add(new Part(0, 2.1F, -0.6F, -0.6F, 0, 0, -0.3F, 0, -0.65F, 0.6F, 2.0F, 1.3F, UV_SPINE));
        SEG_GLOW.add(Part.box(-1.7F, 2.15F, 0.9F, 0.9F, 0.25F, 0.9F, UV_GLOW));
        SEG_GLOW.add(Part.box(0.8F, 2.15F, 0.9F, 0.9F, 0.25F, 0.9F, UV_GLOW));

        // tail fin: two vertical lobes swept back (pivot set per draw at the tail tip)
        FIN.add(new Part(0, 0, 0, -0.4F, 0, 0, -0.15F, -0.3F, -5.0F, 0.3F, 3.3F, 5.0F, UV_FIN));
        FIN.add(new Part(0, 0, 0, 0.4F, 0, 0, -0.15F, -3.0F, -5.0F, 0.3F, 3.3F, 5.0F, UV_FIN));
        FIN_GLOW.add(new Part(0, 0, 0, -0.4F, 0, 0, -0.25F, 2.4F, -4.9F, 0.5F, 0.5F, 0.7F, UV_GLOW));
        FIN_GLOW.add(new Part(0, 0, 0, 0.4F, 0, 0, -0.25F, -2.4F, -4.9F, 0.5F, 0.5F, 0.7F, UV_GLOW));
    }

    /** smoothed jaw opening per serpent (client only) */
    private static final Map<AbyssSerpent, float[]> JAW = new WeakHashMap<>();

    public AbyssSerpentRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssSerpent entity)
    {
        return TEXTURE;
    }

    /** Cross-section scale of joint k: 1.0 at the neck to 0.55 at the tail tip, the last tail joints thinner still. */
    private static float jointScale(int k, int n)
    {
        if (k <= 0) return 1.0F;
        float s = 1.0F - 0.45F * (k - 1) / Math.max(1, n - 2);
        int t = k - (n - 1 - TAIL_JOINTS); // 1..TAIL_JOINTS on the tail
        if (t > 0) s *= 1.0F - 0.16F * t;
        return s;
    }

    @Override
    public void render(AbyssSerpent entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight)
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

        // jaw: no synced AI state, so the head speed stands in (CHARGE runs at 1.2 blocks/tick, circling at 0.9..1.0);
        // the charge opens it, the stop on a bite snaps it shut
        double hs = Math.sqrt(sq(chain.lerpX(0, 1) - chain.lerpX(0, 0)) + sq(chain.lerpY(0, 1) - chain.lerpY(0, 0))
                + sq(chain.lerpZ(0, 1) - chain.lerpZ(0, 0)));
        float target = Mth.clamp((float) ((hs - 1.05) / 0.12), 0.0F, 1.0F);
        float[] jawState = JAW.computeIfAbsent(entity, e -> new float[1]);
        jawState[0] += (target - jawState[0]) * 0.2F;
        float jaw = Math.max(jawState[0], 0.06F + 0.05F * Mth.sin((entity.tickCount + partialTick) * 0.08F));

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
            if (p[0] == p[1]) joint(vc, pose, entity, p[0], n, jx, jy, jz, ex, ey, ez, jaw, false, chain.spacing());
            else tube(vc, pose, entity, p[0], p[1], n, jx, jy, jz, ex, ey, ez, cp, chain.spacing(), glows);
        }
        // full-bright pass for eyes and glow organs (a BufferSource ends the previous batch when the type changes,
        // so every solid quad above is already written)
        VertexConsumer ec = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        for (int[] p : plan)
            if (p[0] == p[1]) joint(ec, pose, entity, p[0], n, jx, jy, jz, ex, ey, ez, jaw, true, chain.spacing());
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
                        .setColor(0.35F, 0.9F, 1.0F, 0.85F).setUv(quv[v][0], quv[v][1]).setOverlay(OverlayTexture.NO_OVERLAY)
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
    private void joint(VertexConsumer vc, PoseStack pose, AbyssSerpent entity, int k, int n, double[] jx, double[] jy, double[] jz,
                       double ex, double ey, double ez, float jaw, boolean glow, double spacing)
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
            // upper jaw lifts a little, lower jaw drops
            pose.pushPose();
            hinge(pose, -0.18F * jaw);
            draw(vc, pose, glow ? UPPER_GLOW : UPPER, light);
            pose.popPose();
            if (!glow)
            {
                draw(vc, pose, FRILL, light);
                pose.pushPose();
                hinge(pose, 0.65F * jaw);
                draw(vc, pose, LOWER, light);
                pose.popPose();
            }
        }
        else
        {
            float s = jointScale(k, n);
            float len = (float) spacing + 2 * OVERLAP;
            pose.pushPose();
            pose.scale(s, s, s);
            if (glow) draw(vc, pose, SEG_GLOW, light);
            else
            {
                part(vc, pose, Part.box(-BODY / 2, -BODY / 2, -len / (2 * s), BODY, BODY, len / s, UV_BODY), light, true);
                draw(vc, pose, SEG_DECOR, light);
            }
            pose.popPose();
            if (k == n - 1)
            {
                // tail fin hangs off the back end of the last segment, unscaled
                pose.translate(0, 0, -len / 2 + 0.3F);
                draw(vc, pose, glow ? FIN_GLOW : FIN, light);
            }
        }
        pose.popPose();
    }

    private static void hinge(PoseStack pose, float angle)
    {
        pose.translate(0, HINGE_Y, HINGE_Z);
        pose.mulPose(Axis.XP.rotation(angle));
        pose.translate(0, -HINGE_Y, -HINGE_Z);
    }

    /** Middle distance: one plain body tube over joints i..j (body texture, scale of the middle joint) plus a glow point. */
    private void tube(VertexConsumer vc, PoseStack pose, AbyssSerpent entity, int i, int j, int n, double[] jx, double[] jy, double[] jz,
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
        pose.scale(s, s, s);
        part(vc, pose, Part.box(-BODY / 2, -BODY / 2, -l / (2 * s), BODY, BODY, l / s, UV_BODY), light, false);
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
