package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssKraken;
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

/**
 * Draws the kraken: an upright bullet-shaped mantle (two flapping fins at the top, a golden eye on each side, a beak in the
 * mouth under the arm ring) and 8 tentacles drawn as one tapering box per joint pair (coarser with distance), with a sucker
 * row underneath and a hooked club at the tip. Parts are textured boxes with the ModelPart cube unwrap on one 128x128 texture
 * (tools/boss_kraken_texture.py); the club glow dots and the faint mantle rim go in a second, full-bright pass. Rendering goes
 * by the chains, not by the entity position; beyond the fog end the model is skipped and {@link BossFarRenderer} draws the
 * ribbons. Mantle local space: world axes, origin = mantle centre (head joint), units = blocks. Tentacle local space per box:
 * +Z = toward the tip, -Y = underside (suckers).
 */
public class AbyssKrakenRenderer extends EntityRenderer<AbyssKraken>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(Abyssia.MODID, "textures/entity/abyss_kraken.png");
    private static final ResourceLocation WHITE = new ResourceLocation("textures/misc/white.png");
    private static final float TEX = 128.0F;
    private static final float OVERLAP = 0.6F;
    /** first joint of the club (the last 4 joint pairs) */
    private static final int CLUB_START = AbyssKraken.JOINTS - 5;

    /** Texture unwrap regions {u, v, tw, th, tl} (texels): see tools/boss_kraken_texture.py. */
    private static final int[] UV_MANTLE = {0, 0, 16, 16, 16};
    private static final int[] UV_HEAD = {64, 0, 12, 8, 12};
    private static final int[] UV_GLOW = {112, 0, 3, 1, 3};
    private static final int[] UV_RIM = {112, 8, 4, 1, 1};
    private static final int[] UV_CUT = {96, 24, 6, 6, 2};
    private static final int[] UV_ARM = {0, 32, 8, 8, 16};
    private static final int[] UV_CLUB = {48, 32, 8, 8, 12};
    private static final int[] UV_FIN = {0, 56, 12, 12, 2};
    private static final int[] UV_EYE = {32, 56, 2, 8, 10};
    private static final int[] UV_MOUTH = {56, 56, 8, 3, 8};
    private static final int[] UV_BEAK = {88, 56, 3, 4, 2};
    private static final int[] UV_HOOK = {104, 56, 1, 2, 1};

    /** A box: pivot, rotation (ModelPart order Z*Y*X), box min corner relative to the pivot, size, texture region. */
    private record Part(float px, float py, float pz, float rx, float ry, float rz,
                        float x0, float y0, float z0, float w, float h, float l, int[] uv)
    {
        static Part box(float x0, float y0, float z0, float w, float h, float l, int[] uv)
        {
            return new Part(0, 0, 0, 0, 0, 0, x0, y0, z0, w, h, l, uv);
        }
    }

    // ---- mantle (around the head joint): head + arm base, bullet-shaped mantle, eyes, mouth ----
    private static final List<Part> BODY = new ArrayList<>();
    private static final List<Part> RIM_GLOW = new ArrayList<>();
    // ---- fins (pivot at the mantle side, flapped per draw) ----
    private static final List<Part> FIN_R = new ArrayList<>();
    private static final List<Part> FIN_L = new ArrayList<>();
    // ---- beak halves (hinge at the mouth) ----
    private static final float MOUTH_Y = -8.0F;
    private static final List<Part> BEAK_A = new ArrayList<>();
    private static final List<Part> BEAK_B = new ArrayList<>();

    static
    {
        BODY.add(Part.box(-4.5F, -7.0F, -4.5F, 9.0F, 5.0F, 9.0F, UV_HEAD));            // head, arms grow from its underside
        BODY.add(Part.box(-6.0F, -2.0F, -6.0F, 12.0F, 8.0F, 12.0F, UV_MANTLE));        // mantle, widest at the opening
        BODY.add(Part.box(-5.25F, 6.0F, -5.25F, 10.5F, 4.0F, 10.5F, UV_MANTLE));
        BODY.add(Part.box(-4.0F, 10.0F, -4.0F, 8.0F, 2.5F, 8.0F, UV_MANTLE));
        BODY.add(Part.box(-2.5F, 12.5F, -2.5F, 5.0F, 1.5F, 5.0F, UV_MANTLE));
        BODY.add(Part.box(-1.2F, 14.0F, -1.2F, 2.4F, 1.2F, 2.4F, UV_MANTLE));          // tip
        BODY.add(Part.box(4.35F, -5.2F, -1.8F, 0.6F, 2.8F, 3.6F, UV_EYE));             // eyes, one per side
        BODY.add(Part.box(-4.95F, -5.2F, -1.8F, 0.6F, 2.8F, 3.6F, UV_EYE));
        BODY.add(Part.box(-2.5F, MOUTH_Y, -2.5F, 5.0F, 1.0F, 5.0F, UV_MOUTH));         // lips around the beak

        // faint glow line along the mantle opening
        RIM_GLOW.add(Part.box(6.0F, -2.0F, -6.0F, 0.08F, 0.3F, 12.0F, UV_RIM));
        RIM_GLOW.add(Part.box(-6.08F, -2.0F, -6.0F, 0.08F, 0.3F, 12.0F, UV_RIM));
        RIM_GLOW.add(Part.box(-6.0F, -2.0F, 6.0F, 12.0F, 0.3F, 0.08F, UV_RIM));
        RIM_GLOW.add(Part.box(-6.0F, -2.0F, -6.08F, 12.0F, 0.3F, 0.08F, UV_RIM));

        // fin: a rhombic flap in the X-Y plane, inner plate + outer point
        FIN_R.add(Part.box(0.0F, -3.5F, -0.2F, 5.0F, 7.0F, 0.4F, UV_FIN));
        FIN_R.add(Part.box(5.0F, -2.0F, -0.15F, 2.0F, 4.0F, 0.3F, UV_FIN));
        FIN_L.add(Part.box(-5.0F, -3.5F, -0.2F, 5.0F, 7.0F, 0.4F, UV_FIN));
        FIN_L.add(Part.box(-7.0F, -2.0F, -0.15F, 2.0F, 4.0F, 0.3F, UV_FIN));

        // beak: two chitin mandibles, the upper one hooking over the lower
        BEAK_A.add(Part.box(-0.9F, -2.2F, 0.0F, 1.8F, 2.2F, 1.1F, UV_BEAK));
        BEAK_A.add(Part.box(-0.5F, -2.7F, -0.35F, 1.0F, 0.6F, 0.8F, UV_BEAK));
        BEAK_B.add(Part.box(-0.8F, -1.9F, -1.0F, 1.6F, 1.9F, 1.0F, UV_BEAK));
    }

    public AbyssKrakenRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssKraken entity)
    {
        return TEXTURE;
    }

    /** Tentacle thickness at joint position k: 3.2 at the root to 1.0 at the tip, the club swelling over the last 4 joints. */
    private static float armWidth(float k)
    {
        float w = Mth.lerp(k / (AbyssKraken.JOINTS - 1), 3.2F, 1.0F);
        float c = k - CLUB_START;
        if (c > 0) w += 0.65F * Mth.sin((float) Math.PI * Math.min(c / 4.6F, 1.0F));
        return w;
    }

    @Override
    public void render(AbyssKraken entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain spine = entity.chain();
        if (!spine.isInitialised()) return;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition();
        boolean lod = ClientConfig.LOD_ENABLED.get();
        if (lod && BossFarRenderer.nearestPoint(entity.farPoints(pt), cp) > BossFarRenderer.fogEnd()) return;
        double ex = Mth.lerp(pt, entity.xo, entity.getX()), ey = Mth.lerp(pt, entity.yo, entity.getY()), ez = Mth.lerp(pt, entity.zo, entity.getZ());
        double hx = spine.lerpX(0, pt), hy = spine.lerpY(0, pt), hz = spine.lerpZ(0, pt);
        float time = entity.tickCount + pt;

        // plan: {tentacle, first joint, last joint} per drawn box
        List<int[]> plan = new ArrayList<>();
        for (int t = 0; t < AbyssKraken.TENTACLES; t++)
        {
            BossChain c = entity.tentacle(t);
            if (!c.isInitialised()) continue;
            int last = entity.liveJoints(t) - 1;
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
        mantle(vc, pose, hx - ex, hy - ey, hz - ez, time, light, false);
        List<float[]> glows = new ArrayList<>();
        for (int[] p : plan) segment(vc, pose, entity, p[0], p[1], p[2], pt, ex, ey, ez, cp, false, glows);

        // full-bright pass: club glow dots and the mantle rim (a BufferSource ends the previous batch when the type changes,
        // so every solid quad above is already written)
        VertexConsumer ec = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        mantle(ec, pose, hx - ex, hy - ey, hz - ez, time, LightTexture.FULL_BRIGHT, true);
        for (int[] p : plan) segment(ec, pose, entity, p[0], p[1], p[2], pt, ex, ey, ez, cp, true, glows);
        if (glows.isEmpty()) return;
        // mid-range club glow points: camera facing quads
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
                gc.vertex(last.pose(), g[0] + (left.x * sl + up.x * su) * s, g[1] + (left.y * sl + up.y * su) * s, g[2] + (left.z * sl + up.z * su) * s)
                        .color(0.75F, 0.92F, 1.0F, 0.85F).uv(quv[v][0], quv[v][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT).normal(last.normal(), 0, 0, 1).endVertex();
            }
        }
    }

    private static double sq(double d)
    {
        return d * d;
    }

    /** Mantle, head, eyes, fins and beak (glow = only the rim line). */
    private void mantle(VertexConsumer vc, PoseStack pose, double cx, double cy, double cz, float time, int light, boolean glow)
    {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        if (glow)
        {
            draw(vc, pose, RIM_GLOW, light);
            pose.popPose();
            return;
        }
        draw(vc, pose, BODY, light);
        // fins tilt up and flap slowly
        float flap = 0.35F * Mth.sin(time * 0.12F);
        for (int s = -1; s <= 1; s += 2)
        {
            pose.pushPose();
            pose.translate(s * 3.8F, 11.2F, 0);
            pose.mulPose(Axis.YP.rotation(s * flap));
            pose.mulPose(Axis.ZP.rotation(s * 0.3F));
            draw(vc, pose, s > 0 ? FIN_R : FIN_L, light);
            pose.popPose();
        }
        // beak: slow chewing (no synced state)
        float open = 0.10F + 0.14F * (0.5F + 0.5F * Mth.sin(time * 0.09F));
        pose.translate(0, MOUTH_Y, 0);
        pose.pushPose();
        pose.mulPose(Axis.XP.rotation(-open));
        draw(vc, pose, BEAK_A, light);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.XP.rotation(open));
        draw(vc, pose, BEAK_B, light);
        pose.popPose();
        pose.popPose();
    }

    /** One tentacle box over joints i..j: solid parts, or (glow = true) only the club glow dots. */
    private void segment(VertexConsumer vc, PoseStack pose, AbyssKraken entity, int t, int i, int j, float pt, double ex, double ey, double ez,
                         Vec3 cp, boolean glow, List<float[]> glows)
    {
        BossChain c = entity.tentacle(t);
        double ax = c.lerpX(i, pt), ay = c.lerpY(i, pt), az = c.lerpZ(i, pt);
        double bx = c.lerpX(j, pt), by = c.lerpY(j, pt), bz = c.lerpZ(j, pt);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) return;
        boolean severed = entity.severed(t);
        boolean detail = j - i == 1;
        boolean club = i >= CLUB_START;
        boolean tip = !severed && j == AbyssKraken.JOINTS - 1;
        boolean stump = severed && j == entity.liveJoints(t) - 1;
        if (glow && !(club && detail)) return;
        double mx = (ax + bx) * 0.5, my = (ay + by) * 0.5, mz = (az + bz) * 0.5;
        float w = armWidth((i + j) * 0.5F);
        float l = (float) len + 2 * OVERLAP;
        int light = glow ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(entity.level(), BlockPos.containing(mx, my, mz));
        pose.pushPose();
        pose.translate(mx - ex, my - ey, mz - ez);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        pose.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(dy / len, -1.0, 1.0))));
        float hw = w / 2;
        if (glow)
        {
            // pale blue dots on the palm, between the hook pairs
            for (float x : new float[]{-w / 4, w / 4}) part(vc, pose, Part.box(x - 0.25F, -hw - 0.06F, -0.25F, 0.5F, 0.1F, 0.5F, UV_GLOW), light, true);
            if (tip) part(vc, pose, Part.box(-0.2F, -0.56F, l / 2 + 0.3F, 0.4F, 0.1F, 0.4F, UV_GLOW), light, true);
        }
        else
        {
            part(vc, pose, Part.box(-hw, -hw, -l / 2, w, w, l, club ? UV_CLUB : UV_ARM), light, false);
            if (club && detail)
                // hooked teeth on the club palm, two rows
                for (float z : new float[]{-l / 4, l / 4})
                    for (float x : new float[]{-w / 4, w / 4}) part(vc, pose, Part.box(x - 0.15F, -hw - 0.55F, z - 0.15F, 0.3F, 0.6F, 0.3F, UV_HOOK), light, true);
            if (tip) part(vc, pose, Part.box(-0.5F, -0.5F, l / 2 - 0.4F, 1.0F, 1.0F, 1.6F, UV_CLUB), light, true);
            // severed: the cut end of the stump closed with raw flesh
            if (stump) part(vc, pose, Part.box(-hw - 0.05F, -hw - 0.05F, l / 2 - 0.25F, w + 0.1F, w + 0.1F, 0.3F, UV_CUT), light, true);
        }
        pose.popPose();
        if (!glow && tip && !detail)
        {
            double dist = Math.sqrt(sq(bx - cp.x) + sq(by - cp.y) + sq(bz - cp.z));
            glows.add(new float[]{(float) (mx - ex), (float) (my - ey), (float) (mz - ez), (float) (0.8 + dist * 0.006)});
        }
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
        vc.vertex(last.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(last.normal(), nx, ny, nz).endVertex();
    }
}
