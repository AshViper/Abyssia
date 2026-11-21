package com.abyssia.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * BOSS05: the abyss angler on {@link BossMesh}, a deep-sea angler / dragonfish: a head a third of its length with a
 * hinged gaping jaw full of long uneven fangs (longest at the front) and a blue-violet mouth with rows of small spines;
 * glowing eyes (a big one and three small on the head, three along the fore body); twelve lure stalks (forehead, nape,
 * back, chin and belly) ending in glowing teardrop bulbs; a bone horn; tall dorsal spines and ragged translucent fins
 * trailing back (dorsal, anal, forked caudal, pectoral and pelvic fans).
 * <p>
 * Model units are world blocks at {@link #DESIGN} size, scaled by {@code size / DESIGN}; joint coordinates in the tables
 * are those of the 9-joint design (head + 9 joints, 15 blocks apart), stretched to the actual chain. The snout reaches
 * about 16 blocks ahead of joint 0 and the tail fin ends at the last joint: about 150 blocks in all. Body 44 high,
 * 31 wide at the head. One instance per frame holds the head frame and the lure geometry, drawn in three passes
 * ({@link Pass}); the lure bulb centres ({@link #halo}) and the big eyes ({@link #eyeHalo}) are left for halos.
 */
final class AnglerMesh
{
    /** SOLID: cutout skin (body, head, teeth, spines, stalks, bulbs); FIN: the translucent membranes; GLOW: the additive glow texture. */
    enum Pass { SOLID, FIN, GLOW }

    /** The size the tables are modelled for (the entity's SIZE). */
    static final float DESIGN = 3.0F;
    /** Angler-only region: the big ragged fin membrane (dorsal, anal, caudal); u along the body, v root to edge. */
    static final float[] FIN = {128, 128, 128, 64};

    /** Body cross-section by design joint coordinate {k, half width, up, down}. */
    private static final float[][] BODY = {
            {0.0F, 7.0F, 9.0F, 5.0F}, {0.5F, 10.0F, 13.0F, 7.0F}, {1.0F, 12.5F, 17.0F, 10.0F}, {1.5F, 14.2F, 19.5F, 16.0F},
            {2.0F, 14.0F, 19.0F, 17.5F}, {2.5F, 13.0F, 17.5F, 16.0F}, {3.0F, 12.0F, 16.0F, 14.5F}, {4.0F, 10.0F, 13.5F, 12.0F},
            {5.0F, 8.3F, 11.0F, 9.5F}, {6.0F, 6.5F, 8.5F, 7.0F}, {7.0F, 4.6F, 6.0F, 4.8F}, {7.8F, 3.0F, 4.0F, 3.2F},
            {8.5F, 1.5F, 2.0F, 1.7F}, {9.0F, 0.25F, 0.3F, 0.3F}};
    private static final float BODY_E = 0.9F;

    /** Head frame: joint 0, +Z out of the snout. The mouth line is at y = MOUTH_Y; both jaws turn about the hinge. */
    private static final float MOUTH_Y = -3.0F, HINGE_Y = -3.5F, HINGE_Z = -19.0F, SKULL_OPEN = 0.16F, JAW_OPEN = 0.95F;
    /** Loft stations {z, half width, up, down, y offset, exponent}. */
    private static final float[][] SKULL = {
            {-28, 13.5F, 21.0F, 2.5F, -3, 0.85F}, {-22, 15.2F, 24.5F, 3.0F, -3, 0.85F}, {-15, 15.5F, 25.0F, 3.2F, -3, 0.85F},
            {-8, 15.0F, 23.0F, 3.2F, -3, 0.85F}, {-2, 13.8F, 19.5F, 3.0F, -3, 0.85F}, {3, 12.0F, 15.5F, 2.8F, -3, 0.85F},
            {7, 9.8F, 11.5F, 2.4F, -3, 0.85F}, {10.5F, 7.2F, 7.5F, 2.0F, -3, 0.9F}, {13, 4.6F, 4.4F, 1.5F, -3, 0.9F},
            {14.4F, 2.2F, 2.0F, 1.0F, -3, 0.95F}, {15, 0.3F, 0.3F, 0.3F, -3, 1}};
    private static final float[][] JAW = {
            {-27, 12.5F, 1.5F, 13.5F, -3, 0.85F}, {-20, 14.5F, 2.0F, 18.5F, -3, 0.85F}, {-13, 14.6F, 2.0F, 18.5F, -3, 0.85F},
            {-6, 13.6F, 2.0F, 16.0F, -3, 0.85F}, {0, 12.4F, 2.0F, 13.0F, -3, 0.85F}, {5, 10.8F, 2.0F, 10.0F, -3, 0.85F},
            {9.5F, 8.6F, 2.2F, 7.4F, -3, 0.85F}, {12.8F, 6.0F, 2.6F, 5.2F, -3, 0.9F}, {14.8F, 3.6F, 2.4F, 3.4F, -3, 0.9F},
            {15.6F, 1.6F, 1.4F, 1.6F, -3, 0.95F}, {16.0F, 0.3F, 0.3F, 0.3F, -3, 1}};
    /** The back of the mouth (does not move): what the open jaws show behind the teeth. */
    private static final float[][] THROAT = {
            {-27, 9.0F, 6.0F, 7.0F, -5}, {-20, 13.0F, 7.0F, 10.0F, -5}, {-12, 12.5F, 6.5F, 9.0F, -5}, {-5, 10.0F, 5.0F, 7.0F, -5},
            {0, 6.5F, 3.5F, 4.0F, -5}, {3, 0.3F, 0.3F, 0.3F, -5}};
    /** Teeth {z, length}: upper ones hang from the palate edge, lower ones stand on the jaw lip; longer toward the front. */
    private static final float[][] UPPER = {{-15, 2.4F}, {-12, 3.2F}, {-9, 2.6F}, {-6, 4.0F}, {-3, 3.0F}, {0, 5.2F}, {3, 4.0F},
            {6, 6.8F}, {8.6F, 5.6F}, {11, 8.6F}, {12.8F, 6.4F}};
    private static final float[][] LOWER = {{-14, 2.2F}, {-11, 3.4F}, {-8, 2.6F}, {-5, 4.4F}, {-2, 3.4F}, {1, 5.8F}, {4, 4.6F},
            {7, 7.6F}, {9.6F, 6.2F}, {12, 10.0F}, {14, 7.0F}};
    /** Head eyes {z, y, radius} (the first is the big one), body eyes {design k, radius}. */
    private static final float[][] EYES = {{-7.0F, 5.0F, 3.8F}, {-14.5F, 8.5F, 1.4F}, {-12.0F, 1.5F, 1.2F}, {-1.0F, 2.0F, 1.0F}};
    private static final float[][] BODY_EYES = {{2.15F, 1.5F}, {2.5F, 1.3F}, {2.85F, 1.1F}};

    /**
     * Lures {frame, x, y, z, start direction xyz, bend xyz, length, bulb radius, phase}. Frame 0 = skull, 1 = jaw (head
     * units); 2 = back, 3 = belly: z is then the design joint coordinate, the root sits on the skin and x y z of the
     * stalk are body units (right, up, forward). Stalk point at t in 0..1: root + length * (dir * t + bend * t^2).
     */
    private static final float[][] LURES = {
            {0, 0.0F, 14.0F, 0.5F, 0, 0.85F, 0.5F, 0, -0.95F, 0.32F, 32, 2.4F, 0.0F},
            {0, -3.0F, 20.6F, -12.0F, -0.25F, 0.95F, 0.15F, -0.05F, -0.45F, -0.55F, 25, 1.7F, 1.7F},
            {0, 3.2F, 21.2F, -20.0F, 0.25F, 0.95F, 0.0F, 0.05F, -0.5F, -0.6F, 22, 1.6F, 3.1F},
            {1, 0.0F, -11.0F, 8.0F, 0, -1, 0.2F, 0, 0.2F, -0.6F, 26, 1.6F, 0.9F},
            {1, -4.5F, -14.2F, 2.0F, -0.2F, -1, 0.05F, -0.05F, 0.25F, -0.6F, 22, 1.4F, 2.3F},
            {1, 4.5F, -17.2F, -4.0F, 0.2F, -1, 0.0F, 0.05F, 0.25F, -0.6F, 20, 1.4F, 4.0F},
            {2, -2.0F, 0, 2.6F, -0.15F, 1, 0.3F, 0, -0.4F, -0.6F, 24, 1.6F, 5.1F},
            {2, 2.0F, 0, 3.6F, 0.15F, 1, 0.15F, 0, -0.4F, -0.65F, 20, 1.5F, 0.4F},
            {3, 0.0F, 0, 2.05F, 0, -1, 0.1F, 0, 0.25F, -0.65F, 24, 1.6F, 2.9F},
            {3, -4.0F, 0, 2.55F, -0.15F, -1, 0, 0, 0.25F, -0.6F, 20, 1.4F, 3.7F},
            {3, 4.0F, 0, 3.1F, 0.15F, -1, 0, 0, 0.25F, -0.6F, 21, 1.4F, 1.2F},
            {3, 0.0F, 0, 3.8F, 0, -1, -0.1F, 0, 0.2F, -0.55F, 17, 1.3F, 4.6F}};
    static final int LURE_COUNT = LURES.length;
    /** Where the bulbs hang at rest, {forward, up} from joint 0 in design units: the far view's lure lights. */
    static final float[][] FAR_LURES = {
            {29.1F, 8.1F}, {-24.0F, 30.7F}, {-36.0F, 28.5F}, {-6.0F, -35.9F}, {-15.0F, -33.4F}, {-20.9F, -34.2F},
            {-45.4F, 28.0F}, {-62.7F, 23.3F}, {-45.3F, -36.3F}, {-51.1F, -31.6F}, {-58.0F, -30.9F}, {-68.1F, -27.0F}};
    private static final int STALK = 7;
    /** Teardrop bulb: stations along its axis and their radius (narrow neck at the stalk, round below). */
    private static final float[] BULB_F = {0, 0.15F, 0.35F, 0.55F, 0.75F, 0.9F, 1.0F};
    private static final float[] BULB_R = {0.25F, 0.45F, 0.75F, 0.97F, 0.95F, 0.65F, 0.08F};
    private static final float BULB_LENGTH = 2.6F;

    /** Fins {design k, height}: membrane height along the body (the ribbon's root sits a little inside the skin). */
    private static final float[][] DORSAL = {{1.7F, 0}, {1.85F, 14}, {2.25F, 26}, {2.75F, 25}, {3.25F, 21}, {3.75F, 18}, {4.25F, 15.5F},
            {4.75F, 13.5F}, {5.25F, 11.5F}, {5.75F, 10}, {6.25F, 9}, {6.75F, 7.5F}, {7.0F, 5}, {7.25F, 0}};
    private static final float[][] ANAL = {{4.6F, 0}, {5.0F, 8}, {5.5F, 11.5F}, {6.0F, 13.5F}, {6.5F, 14.5F}, {7.0F, 12.5F}, {7.3F, 0}};
    private static final float[][] CAUDAL_UP = {{7.2F, 0}, {7.5F, 4}, {7.75F, 7.5F}, {8.0F, 11}, {8.25F, 15}, {8.5F, 20}, {8.75F, 26}, {9.0F, 22.5F}};
    private static final float[][] CAUDAL_DOWN = {{7.2F, 0}, {7.5F, 3.5F}, {7.75F, 6.5F}, {8.0F, 9.5F}, {8.25F, 13}, {8.5F, 17.5F}, {8.75F, 22.5F}, {9.0F, 19}};
    /** Ray lengths of the pectoral and pelvic fans. */
    private static final float[] PECTORAL = {30, 37, 40, 34, 24};
    private static final float[] PELVIC = {20, 25, 27, 22};
    private static final float PECTORAL_K = 2.05F, PELVIC_K = 2.9F;

    private final BossMesh.Path path;
    /** Light per joint; {@link #lights} is it in the lit passes and null (the mesh's full bright) in the glow pass. */
    private final int[] worldLights;
    private int[] lights;
    private final int sub, sides;
    private final float s, kk, jaw, time, flash;
    private final boolean detail;
    private final float hx, hy, hz, yaw, pitch;
    /** Lure stalks and bulbs in render-origin space: [lure][point]. */
    private final float[][] sx = new float[LURE_COUNT][STALK], sy = new float[LURE_COUNT][STALK], sz = new float[LURE_COUNT][STALK];
    private final float[][] bx = new float[LURE_COUNT][BULB_F.length], by = new float[LURE_COUNT][BULB_F.length], bz = new float[LURE_COUNT][BULB_F.length];
    /** Lure bulb centres (x y z per lure) and the two big eyes (x y z each), in render-origin space. */
    final float[] halo = new float[LURE_COUNT * 3], eyeHalo = new float[6];
    private final float[] tmp = new float[4];

    /**
     * @param pose  at the render origin (the entity's interpolated position); left as it was
     * @param path  the body spline through the joints, relative to (ex, ey, ez), sub points per joint
     */
    AnglerMesh(PoseStack pose, BossMesh.Path path, int[] lights, double[] jx, double[] jy, double[] jz, double ex, double ey, double ez,
               float size, float jaw, float time, float flash, int sub, int sides, boolean detail)
    {
        this.path = path;
        this.worldLights = lights;
        this.lights = lights;
        this.sub = sub;
        this.sides = sides;
        this.s = size / DESIGN;
        this.kk = 9.0F / Math.max(1, jx.length - 1);
        this.jaw = jaw;
        this.time = time;
        this.flash = flash;
        this.detail = detail;
        hx = (float) (jx[0] - ex);
        hy = (float) (jy[0] - ey);
        hz = (float) (jz[0] - ez);
        double dx = jx[0] - jx[1], dy = jy[0] - jy[1], dz = jz[0] - jz[1], len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        yaw = len > 1.0E-6 ? (float) Math.atan2(dx, dz) : 0;
        pitch = len > 1.0E-6 ? (float) -Math.asin(Mth.clamp(dy / len, -1.0, 1.0)) : 0;

        // head frames relative to the render origin, for the lures and halos (the lofts go through the pose stack)
        Matrix4f rootInv = new Matrix4f(pose.last().pose()).invert();
        headPose(pose, -SKULL_OPEN * jaw);
        Matrix4f skull = rootInv.mul(pose.last().pose(), new Matrix4f());
        pose.popPose();
        headPose(pose, JAW_OPEN * jaw);
        Matrix4f lower = rootInv.mul(pose.last().pose(), new Matrix4f());
        pose.popPose();
        lures(skull, lower);
        Vector3f v = new Vector3f();
        for (int side = -1, o = 0; side <= 1; side += 2, o += 3)
        {
            float[] e = EYES[0];
            float w = loftAt(SKULL, e[0], 1), hu = loftAt(SKULL, e[0], 2), yl = e[1] - MOUTH_Y;
            skull.transformPosition(side * (surfX(w, hu, 0.85F, yl) + 0.6F), e[1], e[0], v);
            eyeHalo[o] = v.x;
            eyeHalo[o + 1] = v.y;
            eyeHalo[o + 2] = v.z;
        }
    }

    /** Pushes the head frame (joint 0 looking along the neck, scaled), turned about the hinge; the caller pops. */
    private void headPose(PoseStack pose, float hinge)
    {
        pose.pushPose();
        pose.translate(hx, hy, hz);
        pose.mulPose(Axis.YP.rotation(yaw));
        pose.mulPose(Axis.XP.rotation(pitch));
        pose.scale(s, s, s);
        if (hinge != 0)
        {
            pose.translate(0, HINGE_Y, HINGE_Z);
            pose.mulPose(Axis.XP.rotation(hinge));
            pose.translate(0, -HINGE_Y, -HINGE_Z);
        }
    }

    // ---------------------------------------------------------------- tables

    /** Linear lookup in {key, value...} rows (clamped at the ends) of column col. */
    private static float table(float[][] t, float k, int col)
    {
        if (k <= t[0][0]) return t[0][col];
        for (int i = 1; i < t.length; i++)
            if (k <= t[i][0]) return Mth.lerp((k - t[i - 1][0]) / (t[i][0] - t[i - 1][0]), t[i - 1][col], t[i][col]);
        return t[t.length - 1][col];
    }

    /** A fin's height at design k (0 outside it). */
    private static float fin(float[][] t, float k)
    {
        return k < t[0][0] || k > t[t.length - 1][0] ? 0 : table(t, k, 1);
    }

    private static float loftAt(float[][] st, float z, int col)
    {
        return table(st, z, col);
    }

    /** Body section at design k, scaled: out = {half width, up, down}. */
    private void body(float k9, float[] out)
    {
        out[0] = table(BODY, k9, 1) * s;
        out[1] = table(BODY, k9, 2) * s;
        out[2] = table(BODY, k9, 3) * s;
    }

    /** Half width of a super-ellipse section (half width w, half height h, exponent e) at height y. */
    private static float surfX(float w, float h, float e, float y)
    {
        float a = Math.min(Math.abs(y) / h, 0.999F);
        return w * (float) Math.pow(1 - Math.pow(a, 2 / e), e / 2);
    }

    /** Path index nearest to design joint coordinate k9. */
    private int index(float k9)
    {
        return Mth.clamp(Math.round(k9 / kk * sub), 0, path.n - 1);
    }

    private int lightAt(float k9, int fallback)
    {
        return lights == null ? fallback : lights[Mth.clamp(Math.round(k9 / kk), 0, lights.length - 1)];
    }

    // ---------------------------------------------------------------- lures

    private void lures(Matrix4f skull, Matrix4f lower)
    {
        Vector3f v = new Vector3f();
        for (int l = 0; l < LURE_COUNT; l++)
        {
            float[] d = LURES[l];
            int frame = (int) d[0];
            float len = d[10], ph = d[12];
            float rootY = d[2], rootZ = d[3];
            int pi = 0;
            if (frame >= 2)
            {
                pi = index(d[3]);
                rootY = frame == 2 ? 0.92F * table(BODY, d[3], 2) : -0.92F * table(BODY, d[3], 3);
                rootZ = 0;
            }
            Matrix4f m = frame == 0 ? skull : lower;
            float ex = 0, ey = 0, ez = 0;
            for (int j = 0; j < STALK; j++)
            {
                float t = j / (float) (STALK - 1), sw = len * 0.10F * t * t;
                ex = d[1] + len * (d[4] * t + d[7] * t * t) + sw * Mth.sin(time * 0.045F + ph + t * 1.3F);
                ey = rootY + len * (d[5] * t + d[8] * t * t);
                ez = rootZ + len * (d[6] * t + d[9] * t * t) + sw * 0.5F * Mth.cos(time * 0.04F + ph);
                place(frame, pi, m, ex, ey, ez, v);
                sx[l][j] = v.x;
                sy[l][j] = v.y;
                sz[l][j] = v.z;
            }
            // the bulb hangs from the stalk's end: its axis between the stalk's last direction and straight down
            float tx = d[4] + 2 * d[7], ty = d[5] + 2 * d[8], tz = d[6] + 2 * d[9], tl = Mth.sqrt(tx * tx + ty * ty + tz * tz);
            float ax = 0.5F * tx / tl, ay = 0.5F * ty / tl - 1, az = 0.5F * tz / tl, al = Mth.sqrt(ax * ax + ay * ay + az * az);
            float lb = BULB_LENGTH * d[11] / al;
            for (int j = 0; j < BULB_F.length; j++)
            {
                place(frame, pi, m, ex + ax * lb * BULB_F[j], ey + ay * lb * BULB_F[j], ez + az * lb * BULB_F[j], v);
                bx[l][j] = v.x;
                by[l][j] = v.y;
                bz[l][j] = v.z;
            }
            place(frame, pi, m, ex + ax * lb * 0.6F, ey + ay * lb * 0.6F, ez + az * lb * 0.6F, v);
            halo[3 * l] = v.x;
            halo[3 * l + 1] = v.y;
            halo[3 * l + 2] = v.z;
        }
    }

    /** A point of a lure in render-origin space: head units through the skull / jaw frame, or body units at path point pi. */
    private void place(int frame, int pi, Matrix4f m, float x, float y, float z, Vector3f out)
    {
        if (frame < 2)
        {
            m.transformPosition(x, y, z, out);
            return;
        }
        BossMesh.Path p = path;
        out.set(p.x[pi] + (p.rx[pi] * x + p.ux[pi] * y - p.tx[pi] * z) * s,
                p.y[pi] + (p.ry[pi] * x + p.uy[pi] * y - p.ty[pi] * z) * s,
                p.z[pi] + (p.rz[pi] * x + p.uz[pi] * y - p.tz[pi] * z) * s);
    }

    private void lureMeshes(BossMesh mesh, boolean glow)
    {
        int stalkSides = detail ? 5 : 3, bulbSides = detail ? 8 : 5;
        for (int l = 0; l < LURE_COUNT; l++)
        {
            float[] d = LURES[l];
            int fallback = lights == null ? 0 : lights[0];
            if (lights != null) mesh.light(d[0] < 2 ? lights[0] : lightAt(d[3], fallback));
            if (!glow)
            {
                // hint: the body's right, so the stalk's frame never starts along its own length
                int pi = d[0] < 2 ? 0 : index(d[3]);
                mesh.tube(BossMesh.through(sx[l], sy[l], sz[l], path.rx[pi], path.ry[pi], path.rz[pi]), stalkSides, (k, o) -> {
                    float r = (0.55F - 0.04F * k) * s;
                    o[0] = r; o[1] = r; o[2] = r; o[3] = 1;
                }, BossMesh.FLESH, 0, null, false, false);
            }
            final float br = d[11] * s;
            mesh.tube(BossMesh.through(bx[l], by[l], bz[l], path.rx[0], path.ry[0], path.rz[0]), bulbSides, (k, o) -> {
                float r = br * BULB_R[Mth.clamp(Math.round(k), 0, BULB_R.length - 1)];
                o[0] = r; o[1] = r; o[2] = r; o[3] = 1;
            }, BossMesh.GLOW, 0, null, true, true);
        }
    }

    // ---------------------------------------------------------------- passes

    void draw(BossMesh mesh, PoseStack pose, Pass pass)
    {
        boolean glow = pass == Pass.GLOW;
        lights = glow ? null : worldLights;
        if (pass == Pass.FIN)
        {
            fins(mesh);
            return;
        }
        // the glow type culls back faces: tubes and lofts must face out to show (eye discs already do)
        if (glow) mesh.outward(true);
        // body: the front lies inside the head; the tail thins to a point under the caudal fin
        mesh.tube(path, sides, (k, o) -> {
            body(k * kk, o);
            o[3] = BODY_E;
        }, BossMesh.SKIN, 1, lights, false, true);
        bodyEyes(mesh);
        if (!glow && detail) spines(mesh);

        // head: the throat stays, the skull tips up and the jaw drops about the hinge
        headPose(pose, 0);
        mesh.pose(pose);
        if (lights != null) mesh.light(lights[0]);
        mesh.loft(THROAT, Math.max(8, sides - 4), BossMesh.INNER);
        pose.popPose();

        headPose(pose, -SKULL_OPEN * jaw);
        mesh.pose(pose);
        mesh.loft(SKULL, sides, BossMesh.HEAD);
        for (int side = -1; side <= 1; side += 2)
        {
            for (float[] e : EYES) headEye(mesh, e[0], e[1], e[2], side);
            if (glow || !detail) continue;
            for (float[] t : UPPER)
            {
                float z = t[0], l = t[1], f = (z + 15) / 28.0F;
                mesh.spike(side * loftAt(SKULL, z, 1) * 0.84F, MOUTH_Y - 0.4F, z, side * 0.18F * l, -l, (0.12F + 0.25F * f) * l,
                        0.35F + 0.1F * l, 4, BossMesh.TOOTH);
            }
            mesh.spike(side * 1.4F, MOUTH_Y - 0.3F, 13.6F, side * 1.0F, -10.0F, 3.0F, 1.2F, 5, BossMesh.TOOTH);   // snout fangs
            // rows of small spines on the palate, raked back toward the throat
            for (float z = -16; z < 10; z += 3)
                for (float xo = 3.5F; xo < 8; xo += 3.5F)
                    mesh.spike(side * xo * loftAt(SKULL, z, 1) / 13.0F, MOUTH_Y - 0.85F * loftAt(SKULL, z, 3), z, 0, -2.0F, -1.2F,
                            0.4F, 3, BossMesh.TOOTH);
        }
        if (!glow && detail) mesh.spike(0, 19.5F, -9.0F, 0, 9.5F, -5.0F, 1.9F, 6, BossMesh.HORN);
        pose.popPose();

        headPose(pose, JAW_OPEN * jaw);
        mesh.pose(pose);
        mesh.loft(JAW, sides, BossMesh.SKIN2);
        if (!glow && detail)
        {
            for (int side = -1; side <= 1; side += 2)
            {
                for (float[] t : LOWER)
                {
                    float z = t[0], l = t[1], f = (z + 14) / 28.0F;
                    mesh.spike(side * loftAt(JAW, z, 1) * 0.82F, MOUTH_Y + 1.2F, z, side * 0.22F * l, l, (0.1F + 0.25F * f) * l,
                            0.35F + 0.1F * l, 4, BossMesh.TOOTH);
                }
                mesh.spike(side * 1.5F, MOUTH_Y + 1.0F, 15.0F, side * 1.8F, 12.0F, 3.0F, 1.3F, 5, BossMesh.TOOTH);   // chin fangs
                for (float z = -15; z < 10; z += 3)
                    for (float xo = 3.5F; xo < 8; xo += 3.5F)
                        mesh.spike(side * xo * loftAt(JAW, z, 1) / 13.0F, MOUTH_Y + 0.8F * loftAt(JAW, z, 2), z, 0, 2.0F, -1.2F,
                                0.4F, 3, BossMesh.TOOTH);
            }
        }
        pose.popPose();
        mesh.pose(pose);

        // lures: in the glow pass the bulbs brighten with the flash
        if (glow)
        {
            float f = 0.55F + 0.45F * flash;
            mesh.color(f, f, f, 1);
        }
        lureMeshes(mesh, glow);
        if (glow) mesh.color(1, 1, 1, 1);
    }

    /** A glowing eye on the skull at (z, y), sitting on the skin and facing along its normal. */
    private void headEye(BossMesh mesh, float z, float y, float radius, int side)
    {
        float w = loftAt(SKULL, z, 1), h = loftAt(SKULL, z, 2), yl = y - MOUTH_Y;
        float x = surfX(w, h, 0.85F, yl);
        float gx = (float) Math.pow(x / w, 2 / 0.85F - 1) / w, gy = (float) Math.pow(Math.abs(yl) / h, 2 / 0.85F - 1) / h * Math.signum(yl);
        float dw = (loftAt(SKULL, z + 1, 1) - loftAt(SKULL, z - 1, 1)) * 0.5F;
        float nx = side * gx, ny = gy, nz = -dw * gx, nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl; ny /= nl; nz /= nl;
        disc(mesh, side * x + nx * 0.25F, y + ny * 0.25F, z + nz * 0.25F, nx, ny, nz, 0, 1, 0, radius);
    }

    /** The three eyes along the fore body, both flanks. */
    private void bodyEyes(BossMesh mesh)
    {
        BossMesh.Path p = path;
        for (float[] e : BODY_EYES)
        {
            int i = index(e[0]);
            body(e[0], tmp);
            float w = tmp[0], h = tmp[1], y = 0.32F * h, x = surfX(w, h, BODY_E, y);
            float gx = (float) Math.pow(x / w, 2 / BODY_E - 1) / w, gy = (float) Math.pow(y / h, 2 / BODY_E - 1) / h;
            float gl = Mth.sqrt(gx * gx + gy * gy);
            gx /= gl; gy /= gl;
            if (lights != null) mesh.light(lightAt(e[0], lights[0]));
            for (int side = -1; side <= 1; side += 2)
            {
                float nx = p.rx[i] * side * gx + p.ux[i] * gy, ny = p.ry[i] * side * gx + p.uy[i] * gy, nz = p.rz[i] * side * gx + p.uz[i] * gy;
                float cx = p.x[i] + p.rx[i] * side * x + p.ux[i] * y + nx * 0.25F * s;
                float cy = p.y[i] + p.ry[i] * side * x + p.uy[i] * y + ny * 0.25F * s;
                float cz = p.z[i] + p.rz[i] * side * x + p.uz[i] * y + nz * 0.25F * s;
                disc(mesh, cx, cy, cz, nx, ny, nz, p.ux[i], p.uy[i], p.uz[i], e[1] * s);
            }
        }
    }

    /** A square of the EYE region centred at c facing along the unit normal n, its v axis toward (upx, upy, upz). */
    private static void disc(BossMesh mesh, float cx, float cy, float cz, float nx, float ny, float nz, float upx, float upy, float upz, float r)
    {
        float d = upx * nx + upy * ny + upz * nz;
        float ax = upx - d * nx, ay = upy - d * ny, az = upz - d * nz, al = Mth.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1.0E-4F) return;
        ax *= r / al; ay *= r / al; az *= r / al;
        // b = n x a: the corners then wind counter-clockwise seen from outside (the glow pass culls back faces)
        float bx = ny * az - nz * ay, by = nz * ax - nx * az, bz = nx * ay - ny * ax;
        mesh.quad(new float[]{
                cx + ax + bx, cy + ay + by, cz + az + bz,
                cx - ax + bx, cy - ay + by, cz - az + bz,
                cx - ax - bx, cy - ay - by, cz - az - bz,
                cx + ax - bx, cy + ay - by, cz + az - bz}, BossMesh.EYE);
    }

    // ---------------------------------------------------------------- fins

    /** A body fin's edge at path k: {root offset along up, height, sway}. */
    private void edge(float[][] t, boolean up, float baseFrac, boolean scallop, float phase, float k, float[] o)
    {
        float k9 = k * kk;
        float h = fin(t, k9) * s;
        // scalloped between the spines (one every half joint): the membrane sags, the spines stand clear of it
        if (scallop) h *= 0.62F + 0.38F * Math.abs(Mth.cos(Mth.TWO_PI * (k9 - 1.5F)));
        o[0] = baseFrac * table(BODY, k9, up ? 2 : 3) * s;
        o[1] = h;
        o[2] = Mth.sin(time * 0.06F - k9 * 0.8F + phase) * 0.12F * h;
    }

    private void fins(BossMesh mesh)
    {
        mesh.ribbon(path, (k, o) -> edge(DORSAL, true, 0.8F, true, 0, k, o), 1, FIN, 0.5F, lights);
        mesh.ribbon(path, (k, o) -> edge(ANAL, false, 0.8F, true, 1.0F, k, o), -1, FIN, 0.5F, lights);
        mesh.ribbon(path, (k, o) -> edge(CAUDAL_UP, true, 0.75F, false, 0.5F, k, o), 1, FIN, 0.5F, lights);
        mesh.ribbon(path, (k, o) -> edge(CAUDAL_DOWN, false, 0.75F, false, 1.5F, k, o), -1, FIN, 0.5F, lights);
        for (int side = -1; side <= 1; side += 2)
        {
            fan(mesh, true, side, false);
            fan(mesh, false, side, false);
        }
    }

    /** Dorsal and anal spines, the caudal rays and the fan rays (solid pass). */
    private void spines(BossMesh mesh)
    {
        BossMesh.Path p = path;
        float[] o = tmp;
        for (int f = 0; f < 2; f++)
        {
            float[][] t = f == 0 ? DORSAL : ANAL;
            float side = f == 0 ? 1 : -1;
            for (float k9 = f == 0 ? 2.0F : 5.0F; k9 <= 7.0F + 1.0E-3F; k9 += 0.5F)
            {
                int i = index(k9);
                edge(t, f == 0, 0.8F, true, f == 0 ? 0 : 1.0F, p.k[i], o);
                float h = fin(t, k9) * s * 1.18F, lean = 0.2F * h / 1.18F, sw = o[2] * 1.18F, base = o[0] * side;
                if (lights != null) mesh.light(lightAt(k9, lights[0]));
                mesh.spike(p.x[i] + p.ux[i] * base, p.y[i] + p.uy[i] * base, p.z[i] + p.uz[i] * base,
                        p.ux[i] * side * h + p.tx[i] * lean + p.rx[i] * sw, p.uy[i] * side * h + p.ty[i] * lean + p.ry[i] * sw,
                        p.uz[i] * side * h + p.tz[i] * lean + p.rz[i] * sw, 0.55F * s, 4, BossMesh.HORN);
            }
        }
        // caudal rays fan out from the tail stalk to the rear of both lobes
        for (int f = 0; f < 2; f++)
        {
            float[][] t = f == 0 ? CAUDAL_UP : CAUDAL_DOWN;
            float side = f == 0 ? 1 : -1, phase = f == 0 ? 0.5F : 1.5F;
            int i0 = index(7.9F);
            edge(t, f == 0, 0.75F, false, phase, p.k[i0], o);
            float x0 = p.x[i0] + p.ux[i0] * o[0] * side, y0 = p.y[i0] + p.uy[i0] * o[0] * side, z0 = p.z[i0] + p.uz[i0] * o[0] * side;
            if (lights != null) mesh.light(lightAt(8.5F, lights[0]));
            for (float k9 = 8.25F; k9 <= 9.0F + 1.0E-3F; k9 += 0.25F)
            {
                int i = index(k9);
                edge(t, f == 0, 0.75F, false, phase, p.k[i], o);
                float reach = (o[0] + o[1] * 1.04F) * side;
                mesh.spike(x0, y0, z0, p.x[i] + p.ux[i] * reach + p.rx[i] * o[2] - x0, p.y[i] + p.uy[i] * reach + p.ry[i] * o[2] - y0,
                        p.z[i] + p.uz[i] * reach + p.rz[i] * o[2] - z0, 0.4F * s, 4, BossMesh.HORN);
            }
        }
        for (int side = -1; side <= 1; side += 2)
        {
            fan(mesh, true, side, true);
            fan(mesh, false, side, true);
        }
    }

    /**
     * A pectoral (on the flank behind the gill) or pelvic (across the belly) fan: rays from a short root line spread back
     * and trail, the membrane between them flutters. rays = true draws the bony rays (solid), false the membrane.
     */
    private void fan(BossMesh mesh, boolean pectoral, int side, boolean rays)
    {
        float k9 = pectoral ? PECTORAL_K : PELVIC_K;
        float[] lens = pectoral ? PECTORAL : PELVIC;
        int m = lens.length, i = index(k9);
        body(k9, tmp);
        float w = tmp[0] / s, d = tmp[2] / s;
        BossMesh.Path p = path, f = new BossMesh.Path(m);
        for (int j = 0; j < m; j++)
        {
            float x, y, z, dx, dy, dz;
            if (pectoral)
            {
                // a vertical root on the flank, rays from back-and-up to back-and-down
                y = -(2.0F + 2.0F * j);
                x = side * surfX(w, d, BODY_E, y) * 0.97F;
                z = -0.5F * j;
                dx = side * 0.4F; dy = 0.15F - 0.225F * j; dz = -1;
            }
            else
            {
                // a root across the belly, rays down and back, the outer ones flaring out
                x = 2.0F + 2.4F * j;
                y = -d * (float) Math.pow(1 - Math.pow(Math.min(x / w, 0.999F), 2 / BODY_E), BODY_E / 2) * 0.98F;
                x *= side;
                z = 0;
                float t = j / (float) (m - 1);
                dx = side * (0.1F + 0.45F * t); dy = -0.5F - 0.2F * t; dz = -1 + 0.2F * t;
            }
            f.x[j] = p.x[i] + (p.rx[i] * x + p.ux[i] * y - p.tx[i] * z) * s;
            f.y[j] = p.y[i] + (p.ry[i] * x + p.uy[i] * y - p.ty[i] * z) * s;
            f.z[j] = p.z[i] + (p.rz[i] * x + p.uz[i] * y - p.tz[i] * z) * s;
            float ux = p.rx[i] * dx + p.ux[i] * dy - p.tx[i] * dz, uy = p.ry[i] * dx + p.uy[i] * dy - p.ty[i] * dz,
                    uz = p.rz[i] * dx + p.uz[i] * dy - p.tz[i] * dz, ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
            f.ux[j] = ux / ul; f.uy[j] = uy / ul; f.uz[j] = uz / ul;
            f.k[j] = j;
        }
        for (int j = 0; j < m; j++)
        {
            // along the root line, and the fan's normal (the sway direction) across it
            int a = Math.max(j - 1, 0), b = Math.min(j + 1, m - 1);
            float tx = f.x[b] - f.x[a], ty = f.y[b] - f.y[a], tz = f.z[b] - f.z[a], tl = Mth.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1.0E-5F) tl = 1;
            f.tx[j] = tx / tl; f.ty[j] = ty / tl; f.tz[j] = tz / tl;
            float rx = f.uy[j] * f.tz[j] - f.uz[j] * f.ty[j], ry = f.uz[j] * f.tx[j] - f.ux[j] * f.tz[j], rz = f.ux[j] * f.ty[j] - f.uy[j] * f.tx[j];
            float rl = Mth.sqrt(rx * rx + ry * ry + rz * rz);
            if (rl < 1.0E-5F) rl = 1;
            f.rx[j] = rx / rl; f.ry[j] = ry / rl; f.rz[j] = rz / rl;
        }
        float phase = (pectoral ? 0.3F : 1.1F) + side;
        BossMesh.Section edge = (k, o) -> {
            float len = lens[Mth.clamp(Math.round(k), 0, m - 1)] * s;
            o[0] = 0;
            o[1] = len;
            o[2] = len * 0.12F * Mth.sin(time * 0.07F + phase + k * 0.6F);
        };
        if (lights != null) mesh.light(lightAt(k9, lights[0]));
        if (!rays)
        {
            mesh.ribbon(f, edge, 1, BossMesh.MEMBRANE, 1.0F / (m - 1), null);
            return;
        }
        float[] o = new float[4];
        for (int j = 0; j < m; j++)
        {
            edge.at(j, o);
            float h = o[1] * 1.04F, sw = o[2] * 1.04F;
            mesh.spike(f.x[j], f.y[j], f.z[j], f.ux[j] * h + f.rx[j] * sw, f.uy[j] * h + f.ry[j] * sw, f.uz[j] * h + f.rz[j] * sw,
                    0.4F * s, 4, BossMesh.HORN);
        }
    }
}
