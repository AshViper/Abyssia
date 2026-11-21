package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.entity.boss.AbyssMother;
import com.abyssia.entity.boss.BossChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * GeckoLib model of the abyss mother (near view, model scale 4). {@code bell}, the tentacle segments {@code tent_T_SS}
 * (T = 1..12 = tentacle T-1, SS = 01..27) and the oral arm segments {@code oral_O_SS} (O = 1..5, SS = 01..09) sit under root and are
 * placed every frame (tools/boss/mother_model/contract.json, java_orientation): the bell at the head (no rotation), each segment at
 * the midpoint of its two joints with local +Y toward the root joint and the twist carried from its rest outward direction. The
 * bell's children are the idle (beat) and bloom animations'.
 */
public class AbyssMotherGeoModel extends GeoModel<AbyssMother>
{
    private static final ResourceLocation MODEL = new ResourceLocation(Abyssia.MODID, "geo/entity/abyss_mother.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Abyssia.MODID, "textures/entity/boss/abyss_mother_geo.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(Abyssia.MODID, "animations/entity/abyss_mother.animation.json");
    /** model scale (geo 1/16 block drawn 4 times): 1 block = 4 geo units */
    public static final double MODEL_SCALE = 4.0;
    public static final double UNITS_PER_BLOCK = 16.0 / MODEL_SCALE;
    private static final double TENT_SEGMENT = 24.0, TENT_ROOT_R = 60.0, TENT_ROOT_Y = -12.0;
    private static final double ORAL_SEGMENT = 13.5, ORAL_ROOT_R = 8.8, ORAL_ROOT_Y = -24.0, ORAL_SEGMENT_BLOCKS = 3.375;
    private static final int ORAL_ARMS = 5, ORAL_SEGMENTS = 9;
    /** Bone names, built once: tent_T_SS and oral_A_SS (T, A = 1.., SS = 1..). */
    private static final String[][] TENT = boneNames("tent_%d_%02d", AbyssMother.TENTACLES, AbyssMother.JOINTS - 1);
    private static final String[][] ORAL = boneNames("oral_%d_%02d", ORAL_ARMS, ORAL_SEGMENTS);

    /** {@code [group 0..n-1][segment 1..m]} names; the group is formatted as g + 1. */
    private static String[][] boneNames(String fmt, int groups, int segs)
    {
        String[][] a = new String[groups][segs + 1];
        for (int g = 0; g < groups; g++)
            for (int s = 1; s <= segs; s++) a[g][s] = String.format(fmt, g + 1, s);
        return a;
    }

    /** smoothed bloom openness per mother (client only): {value, last time} */
    private final Map<AbyssMother, float[]> opens = new WeakHashMap<>();

    @Override
    public ResourceLocation getModelResource(AbyssMother animatable) { return MODEL; }

    @Override
    public ResourceLocation getTextureResource(AbyssMother animatable) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(AbyssMother animatable) { return ANIMATION; }

    /** Smoothed 0..1 bloom openness (the old renderer's); safe to call several times in one frame. */
    float openness(AbyssMother entity, float time)
    {
        float target = entity.bloom() ? 1.0F : 0.0F;
        float[] s = opens.computeIfAbsent(entity, e -> new float[]{target, time});
        float dt = Mth.clamp(time - s[1], 0.0F, 20.0F);
        s[1] = time;
        s[0] += (target - s[0]) * Math.min(1.0F, dt * 0.06F);
        return s[0] * s[0] * (3.0F - 2.0F * s[0]);
    }

    @Override
    public void setCustomAnimations(AbyssMother m, long instanceId, AnimationState<AbyssMother> state)
    {
        BossChain spine = m.chain();
        if (!spine.isInitialised()) return;
        float pt = state.getPartialTick();
        float time = m.tickCount + pt;
        double ex = Mth.lerp(pt, m.xo, m.getX()), ey = Mth.lerp(pt, m.yo, m.getY()), ez = Mth.lerp(pt, m.zo, m.getZ());
        double hx = spine.lerpX(0, pt) - ex, hy = spine.lerpY(0, pt) - ey, hz = spine.lerpZ(0, pt) - ez;

        GeoBone bell = (GeoBone) getAnimationProcessor().getBone("bell");
        if (bell != null)
        {
            bell.setPosX((float) (-hx * UNITS_PER_BLOCK));
            bell.setPosY((float) (hy * UNITS_PER_BLOCK));
            bell.setPosZ((float) (hz * UNITS_PER_BLOCK));
        }

        // tentacles: one chain each, segment SS between joints SS-1 and SS
        double[][] j = new double[AbyssMother.JOINTS][3];
        for (int t = 0; t < AbyssMother.TENTACLES; t++)
        {
            BossChain c = m.tentacle(t);
            boolean ok = c.isInitialised();
            if (ok)
                for (int i = 0; i < AbyssMother.JOINTS; i++)
                {
                    j[i][0] = c.lerpX(i, pt) - ex;
                    j[i][1] = c.lerpY(i, pt) - ey;
                    j[i][2] = c.lerpZ(i, pt) - ez;
                }
            double a = Math.toRadians(30.0 * t), ca = Math.cos(a), sa = Math.sin(a);
            double[] o = {ca, 0.0, sa}, p = {ca, 0.0, sa}, rot = new double[3];
            for (int ss = 1; ss < AbyssMother.JOINTS; ss++)
            {
                GeoBone bone = (GeoBone) getAnimationProcessor().getBone(TENT[t][ss]);
                if (bone == null) continue;
                if (!ok)
                {
                    bone.setHidden(true);
                    continue;
                }
                bone.setHidden(false);
                segment(bone, j[ss - 1], j[ss], p, o, rot, -TENT_ROOT_R * ca, TENT_ROOT_Y - TENT_SEGMENT * (ss - 0.5), TENT_ROOT_R * sa);
            }
        }

        // oral arms: the old renderer's wavering curve, re-stepped at the model's segment length
        float open = openness(m, time);
        double[][] old = new double[ORAL_SEGMENTS + 1][3], q = new double[ORAL_SEGMENTS + 1][3];
        for (int arm = 0; arm < ORAL_ARMS; arm++)
        {
            double a = arm * Mth.TWO_PI / ORAL_ARMS + 0.3, ca = Math.cos(a), sa = Math.sin(a);
            for (int i = 0; i <= ORAL_SEGMENTS; i++)
            {
                float f = i / (float) ORAL_SEGMENTS, rad = 2.2F + (3.5F + 5.0F * open) * f;
                old[i][0] = ca * rad + Mth.sin(time * 0.045F + i * 0.55F + arm * 1.7F) * 2.4F * f;
                old[i][1] = -6.0F - 30.0F * f * (1.0F - 0.25F * open);
                old[i][2] = sa * rad + Mth.cos(time * 0.04F + i * 0.5F + arm) * 2.4F * f;
            }
            q[0] = old[0].clone();
            for (int i = 0; i < ORAL_SEGMENTS; i++)
            {
                double dx = old[i + 1][0] - old[i][0], dy = old[i + 1][1] - old[i][1], dz = old[i + 1][2] - old[i][2];
                double dl = Math.max(1.0E-6, Math.sqrt(dx * dx + dy * dy + dz * dz)), k = ORAL_SEGMENT_BLOCKS / dl;
                q[i + 1][0] = q[i][0] + dx * k;
                q[i + 1][1] = q[i][1] + dy * k;
                q[i + 1][2] = q[i][2] + dz * k;
            }
            for (double[] v : q)
            {
                v[0] += hx;
                v[1] += hy;
                v[2] += hz;
            }
            double[] o = {ca, 0.0, sa}, p = {ca, 0.0, sa}, rot = new double[3];
            for (int ss = 1; ss <= ORAL_SEGMENTS; ss++)
            {
                GeoBone bone = (GeoBone) getAnimationProcessor().getBone(ORAL[arm][ss]);
                if (bone == null) continue;
                bone.setHidden(false);
                segment(bone, q[ss - 1], q[ss], p, o, rot, -ORAL_ROOT_R * ca, ORAL_ROOT_Y - ORAL_SEGMENT * (ss - 0.5), ORAL_ROOT_R * sa);
            }
        }
    }

    /**
     * Puts a segment bone (local +Y toward the root joint {@code j0}, rest pose pointing straight down) between the joints
     * {@code j0} and {@code j1} (blocks from the entity origin). {@code p} is the carried twist vector (updated), {@code o} the
     * rest outward direction; {@code rot} the last rotation x, y, z (kept for a zero-length segment); {@code pv*} the rest pivot in geo coordinates.
     */
    private static void segment(GeoBone bone, double[] j0, double[] j1, double[] p, double[] o, double[] rot, double pvx, double pvy, double pvz)
    {
        double dx = j0[0] - j1[0], dy = j0[1] - j1[1], dz = j0[2] - j1[2];
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl >= 1.0E-6) rotate(dx / dl, dy / dl, dz / dl, p, o, rot);   // zero length: keep the previous segment's rotation
        bone.setRotX((float) rot[0]);
        bone.setRotY((float) rot[1]);
        bone.setRotZ((float) rot[2]);
        double tx = (j0[0] + j1[0]) * 0.5 * UNITS_PER_BLOCK, ty = (j0[1] + j1[1]) * 0.5 * UNITS_PER_BLOCK, tz = (j0[2] + j1[2]) * 0.5 * UNITS_PER_BLOCK;
        bone.setPosX((float) (-tx - pvx));
        bone.setPosY((float) (ty - pvy));
        bone.setPosZ((float) (tz - pvz));
    }

    /** Rotation (into {@code rot}) of a segment pointing along the unit vector d, carrying the twist vector {@code p}. */
    private static void rotate(double dx, double dy, double dz, double[] p, double[] o, double[] rot)
    {
        // p: carried to the new d (drop the part along d)
        double k = p[0] * dx + p[1] * dy + p[2] * dz;
        double px = p[0] - dx * k, py = p[1] - dy * k, pz = p[2] - dz * k;
        double pl = Math.sqrt(px * px + py * py + pz * pz);
        if (pl < 1.0E-4)
        {
            k = o[0] * dx + o[1] * dy + o[2] * dz;
            px = o[0] - dx * k; py = o[1] - dy * k; pz = o[2] - dz * k;
            pl = Math.sqrt(px * px + py * py + pz * pz);
            if (pl < 1.0E-4) { px = 0.0; py = 0.0; pz = 1.0; pl = 1.0; }
        }
        px /= pl; py /= pl; pz /= pl;
        p[0] = px; p[1] = py; p[2] = pz;
        // R = [p, d, p x d] * [o, y, o x y]^T
        double qx = py * dz - pz * dy, qy = pz * dx - px * dz, qz = px * dy - py * dx;
        double wx = -o[2], wy = 0.0, wz = o[0];   // o x (0,1,0)
        double[][] a = {{px, dx, qx}, {py, dy, qy}, {pz, dz, qz}};
        double[][] b = {{o[0], 0.0, wx}, {o[1], 1.0, wy}, {o[2], 0.0, wz}};
        double[][] r = new double[3][3];
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                r[row][col] = a[row][0] * b[col][0] + a[row][1] * b[col][1] + a[row][2] * b[col][2];
        rot[0] = Math.atan2(r[2][1], r[2][2]);
        rot[1] = -Math.asin(Mth.clamp(r[2][0], -1.0, 1.0));
        rot[2] = Math.atan2(r[1][0], r[0][0]);
    }
}
