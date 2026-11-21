package com.abyssia.vehicle.client;

import com.abyssia.vehicle.Exosuit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Exosuit geometry, hand-written boxes (not tool output). Units = blocks, y up, front = -Z, bottom = 0, right = +X.
 * Texture exosuit.png is 64x64 with one 16px material cell per box face (index = row * 4 + col). The rider's feet sit
 * at {@link #SEAT_Y} inside the torso so the head is inside the glass dome (y 2.16..2.62).
 */
public final class ExosuitModel
{
    static final int HULL = 0, METAL = 1, JOINT = 2, PISTON = 3, GLASS = 4, LAMP = 5, VENT = 6, DRILL = 7, HAZARD = 8, TREAD = 9, GRILLE = 10;
    /** rider (player) feet height above the exosuit origin assumed by this model */
    public static final float SEAT_Y = 0.7f;
    static final float HIP_Y = 1.05f, HIP_X = 0.34f, ANKLE_Y = 0.17f, SHOULDER_Y = 1.8f, SHOULDER_X = 0.74f, SHOULDER_Z = -0.2f;
    static final float STRIDE = 0.45f;

    private PoseStack pose;
    private VertexConsumer out;
    private int light;

    /** leg swing (rad, right leg; left = -swing) */
    static float swing(Exosuit e, float pt)
    {
        float pos = Mth.lerp(pt, e.limbPosO, e.limbPos);
        float speed = Math.min(1.0f, Mth.lerp(pt, e.limbSpeedO, e.limbSpeed));
        return Mth.cos(pos * 0.6662f) * STRIDE * speed;
    }

    /** whole-body drop that keeps both swung feet on the ground */
    static float bob(float swing)
    {
        return -HIP_Y * (1.0f - Mth.cos(swing));
    }

    static float attack(Exosuit e, float pt)
    {
        Entity rider = e.getFirstPassenger();
        return rider instanceof LivingEntity living ? living.getAttackAnim(pt) : 0.0f;
    }

    void render(Exosuit e, float pt, PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, int light)
    {
        this.pose = pose;
        this.light = light;
        float swing = swing(e, pt);
        float atk = attack(e, pt);
        float rightArm = -swing * 0.4f + Mth.sin(Mth.sqrt(atk) * Mth.PI) * 1.3f;
        float leftArm = swing * 0.4f;
        float spin = atk > 0.0f ? (e.tickCount + pt) * 0.9f : 0.0f;

        out = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        pose.pushPose();
        pose.translate(0.0f, bob(swing), 0.0f);
        leg(1, swing);
        leg(-1, -swing);
        torso();
        arm(1, rightArm, spin);
        arm(-1, leftArm, 0.0f);
        // headlamp lens, full-bright
        int keep = this.light;
        this.light = LightTexture.FULL_BRIGHT;
        box(LAMP, 0.47f, 2.03f, -0.875f, 0.59f, 2.13f, -0.855f);
        this.light = keep;
        out = buffers.getBuffer(RenderType.entityTranslucent(tex));
        box(GLASS, -0.42f, 2.16f, -0.5f, 0.42f, 2.62f, 0.27f);
        pose.popPose();
    }

    private void leg(int s, float a)
    {
        float c = HIP_X;
        pose.pushPose();
        rotX(0.0f, HIP_Y, 0.0f, a);
        side(s, JOINT, c - 0.13f, c + 0.13f, 0.92f, 1.12f, -0.13f, 0.13f);
        side(s, HULL, c - 0.17f, c + 0.17f, 0.56f, 0.98f, -0.17f, 0.17f);
        side(s, METAL, c - 0.18f, c + 0.18f, 0.62f, 0.68f, -0.18f, 0.18f);
        side(s, HULL, c - 0.12f, c + 0.12f, 0.46f, 0.70f, -0.23f, -0.15f);
        side(s, PISTON, c - 0.075f, c + 0.075f, 0.16f, 0.60f, -0.075f, 0.075f);
        side(s, METAL, c - 0.05f, c + 0.05f, 0.55f, 0.90f, 0.16f, 0.25f);
        side(s, PISTON, c - 0.03f, c + 0.03f, 0.20f, 0.58f, 0.18f, 0.23f);
        // ankle + foot counter-rotated so the sole stays flat
        rotX(0.0f, ANKLE_Y, 0.0f, -a);
        side(s, JOINT, c - 0.11f, c + 0.11f, 0.10f, 0.22f, -0.11f, 0.11f);
        side(s, METAL, c - 0.23f, c + 0.23f, 0.04f, 0.13f, -0.40f, 0.26f);
        side(s, TREAD, c - 0.235f, c + 0.235f, 0.0f, 0.045f, -0.43f, 0.28f);
        side(s, HULL, c - 0.20f, c + 0.20f, 0.03f, 0.16f, -0.47f, -0.30f);
        side(s, METAL, c - 0.08f, c + 0.08f, 0.02f, 0.12f, 0.26f, 0.34f);
        pose.popPose();
    }

    private void torso()
    {
        // rounded pressure hull: three overlapping boxes, no shared planes
        box(HULL, -0.64f, 1.14f, -0.76f, 0.64f, 2.00f, 0.38f);
        box(HULL, -0.56f, 1.04f, -0.84f, 0.56f, 2.10f, 0.44f);
        box(METAL, -0.46f, 0.98f, -0.72f, 0.46f, 2.14f, 0.40f);
        box(METAL, -0.50f, 0.94f, -0.30f, 0.50f, 1.06f, 0.30f);
        box(HAZARD, -0.42f, 1.10f, -0.855f, 0.42f, 1.22f, -0.83f);
        box(METAL, -0.30f, 1.35f, -0.86f, 0.30f, 1.85f, -0.82f);
        box(GRILLE, -0.22f, 1.42f, -0.875f, 0.22f, 1.62f, -0.85f);
        for (int s = -1; s <= 1; s += 2)
            side(s, GRILLE, 0.635f, 0.655f, 1.30f, 1.70f, -0.50f, 0.10f);
        // dome collar, side ribs, rear hood
        box(METAL, -0.47f, 2.10f, -0.54f, 0.47f, 2.17f, 0.30f);
        box(METAL, -0.05f, 2.60f, -0.52f, 0.05f, 2.66f, 0.30f);
        for (int s = -1; s <= 1; s += 2)
            side(s, METAL, 0.40f, 0.45f, 2.16f, 2.60f, -0.12f, -0.06f);
        box(HULL, -0.48f, 2.12f, 0.24f, 0.48f, 2.52f, 0.50f);
        // headlamp housing (lens drawn full-bright in render)
        box(METAL, 0.44f, 2.00f, -0.86f, 0.62f, 2.16f, -0.70f);
        // back pack, grille, twin thrusters
        box(METAL, -0.44f, 1.12f, 0.40f, 0.44f, 2.02f, 0.64f);
        box(GRILLE, -0.30f, 1.50f, 0.63f, 0.30f, 1.92f, 0.655f);
        for (int s = -1; s <= 1; s += 2)
        {
            side(s, METAL, 0.14f, 0.38f, 1.14f, 1.38f, 0.60f, 0.84f);
            side(s, VENT, 0.17f, 0.35f, 1.17f, 1.35f, 0.835f, 0.86f);
        }
        // shoulder pauldrons stay on the hull
        for (int s = -1; s <= 1; s += 2)
            side(s, HULL, 0.58f, 0.90f, 1.74f, 2.06f, -0.42f, 0.02f);
    }

    private void arm(int s, float a, float spin)
    {
        pose.pushPose();
        rotX(0.0f, SHOULDER_Y, SHOULDER_Z, a);
        side(s, JOINT, 0.65f, 0.83f, 1.62f, 1.76f, -0.30f, -0.10f);
        side(s, METAL, 0.66f, 0.82f, 1.28f, 1.66f, -0.29f, -0.11f);
        side(s, PISTON, 0.71f, 0.77f, 1.30f, 1.60f, -0.335f, -0.28f);
        side(s, JOINT, 0.645f, 0.835f, 1.16f, 1.30f, -0.31f, -0.09f);
        side(s, HULL, 0.62f, 0.88f, 0.84f, 1.20f, -0.34f, -0.06f);
        side(s, HAZARD, 0.615f, 0.885f, 1.06f, 1.12f, -0.345f, -0.055f);
        if (s > 0)
        {
            // drill + side claws
            side(s, METAL, 0.64f, 0.86f, 0.72f, 0.85f, -0.31f, -0.09f);
            side(s, METAL, 0.60f, 0.64f, 0.50f, 0.84f, -0.24f, -0.16f);
            side(s, METAL, 0.86f, 0.90f, 0.50f, 0.84f, -0.24f, -0.16f);
            pose.pushPose();
            pose.translate(0.75f, 0.0f, -0.2f);
            pose.mulPose(Axis.YP.rotation(spin));
            box(DRILL, -0.10f, 0.54f, -0.10f, 0.10f, 0.72f, 0.10f);
            box(DRILL, -0.072f, 0.40f, -0.072f, 0.072f, 0.54f, 0.072f);
            box(DRILL, -0.045f, 0.28f, -0.045f, 0.045f, 0.40f, 0.045f);
            box(DRILL, -0.018f, 0.18f, -0.018f, 0.018f, 0.28f, 0.018f);
            pose.popPose();
        }
        else
        {
            // heavy grabber: wrist, palm, two fingers + thumb
            side(s, METAL, 0.66f, 0.84f, 0.74f, 0.86f, -0.29f, -0.11f);
            side(s, METAL, 0.60f, 0.90f, 0.62f, 0.76f, -0.36f, -0.04f);
            side(s, JOINT, 0.62f, 0.69f, 0.40f, 0.64f, -0.35f, -0.27f);
            side(s, JOINT, 0.81f, 0.88f, 0.40f, 0.64f, -0.35f, -0.27f);
            side(s, HULL, 0.615f, 0.695f, 0.36f, 0.44f, -0.37f, -0.25f);
            side(s, HULL, 0.805f, 0.885f, 0.36f, 0.44f, -0.37f, -0.25f);
            side(s, JOINT, 0.71f, 0.79f, 0.42f, 0.64f, -0.13f, -0.05f);
            side(s, HULL, 0.705f, 0.795f, 0.38f, 0.46f, -0.15f, -0.03f);
        }
        pose.popPose();
    }

    private void rotX(float px, float py, float pz, float rad)
    {
        pose.translate(px, py, pz);
        pose.mulPose(Axis.XP.rotation(rad));
        pose.translate(-px, -py, -pz);
    }

    /** box given in +X side coordinates, mirrored to -X for s = -1 */
    private void side(int s, int mat, float xa, float xb, float y0, float y1, float z0, float z1)
    {
        if (s > 0) box(mat, xa, y0, z0, xb, y1, z1);
        else box(mat, -xb, y0, z0, -xa, y1, z1);
    }

    private void box(int mat, float x0, float y0, float z0, float x1, float y1, float z1)
    {
        PoseStack.Pose p = pose.last();
        float u0 = ((mat & 3) * 16 + 0.25f) / 64.0f, u1 = ((mat & 3) * 16 + 15.75f) / 64.0f;
        float v0 = ((mat >> 2) * 16 + 0.25f) / 64.0f, v1 = ((mat >> 2) * 16 + 15.75f) / 64.0f;
        face(p, u0, v0, u1, v1, x0, y1, z0, x0, y0, z0, x1, y0, z0, x1, y1, z0, 0, 0, -1);
        face(p, u0, v0, u1, v1, x1, y1, z1, x1, y0, z1, x0, y0, z1, x0, y1, z1, 0, 0, 1);
        face(p, u0, v0, u1, v1, x0, y1, z1, x0, y0, z1, x0, y0, z0, x0, y1, z0, -1, 0, 0);
        face(p, u0, v0, u1, v1, x1, y1, z0, x1, y0, z0, x1, y0, z1, x1, y1, z1, 1, 0, 0);
        face(p, u0, v0, u1, v1, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, 0, 1, 0);
        face(p, u0, v0, u1, v1, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0, -1, 0);
    }

    private void face(PoseStack.Pose p, float u0, float v0, float u1, float v1,
                      float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz)
    {
        vertex(p, ax, ay, az, u0, v0, nx, ny, nz);
        vertex(p, bx, by, bz, u0, v1, nx, ny, nz);
        vertex(p, cx, cy, cz, u1, v1, nx, ny, nz);
        vertex(p, dx, dy, dz, u1, v0, nx, ny, nz);
    }

    private void vertex(PoseStack.Pose p, float x, float y, float z, float u, float v, float nx, float ny, float nz)
    {
        out.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, nx, ny, nz);
    }
}
