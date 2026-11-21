package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.entity.boss.AbyssKraken;
import com.abyssia.entity.boss.BossChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model of the abyss kraken (near view, model scale 4). {@code mantle} and the arm segments {@code arm_A_SS}
 * (A = 1..8 = tentacle A-1, SS = 01..15) sit under root and are placed every frame (tools/boss/kraken_model/contract.json):
 * the mantle at the head with the old +-0.04 rad sway, each arm segment at the midpoint of its two joints with local -Z toward
 * the root and the sucker face (-Y) toward the body (arm_rotation_with_roll). A severed arm keeps arm_A_01..03 and shows
 * arm_A_stump. Everything else is the idle animation's.
 */
public class AbyssKrakenGeoModel extends GeoModel<AbyssKraken>
{
    private static final ResourceLocation MODEL = new ResourceLocation(Abyssia.MODID, "geo/entity/abyss_kraken.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Abyssia.MODID, "textures/entity/boss/abyss_kraken_geo.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(Abyssia.MODID, "animations/entity/abyss_kraken.animation.json");
    /** model scale (geo 1/16 block drawn 4 times): 1 block = 4 geo units */
    public static final double MODEL_SCALE = 4.0;
    public static final double UNITS_PER_BLOCK = 16.0 / MODEL_SCALE;
    private static final double SEGMENT_LENGTH = 22.0, ARM_Y = -64.0;
    private static final int SEGMENTS = AbyssKraken.JOINTS - 1;
    /** arm_A_SS bone names (A = 1.., SS = 1..SEGMENTS) and the stumps, built once. */
    private static final String[][] ARM = new String[AbyssKraken.TENTACLES][SEGMENTS + 1];
    private static final String[] STUMP = new String[AbyssKraken.TENTACLES];
    /** Small bones that do not shape the outline, hidden from {@link BossGeo#DETAIL_RANGE}. */
    private static final String[] SMALL = {"beak_upper", "beak_lower"};

    static
    {
        for (int t = 0; t < AbyssKraken.TENTACLES; t++)
        {
            STUMP[t] = "arm_" + (t + 1) + "_stump";
            for (int s = 1; s <= SEGMENTS; s++) ARM[t][s] = String.format("arm_%d_%02d", t + 1, s);
        }
    }

    @Override
    public ResourceLocation getModelResource(AbyssKraken animatable) { return MODEL; }

    @Override
    public ResourceLocation getTextureResource(AbyssKraken animatable) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(AbyssKraken animatable) { return ANIMATION; }

    @Override
    public void setCustomAnimations(AbyssKraken k, long instanceId, AnimationState<AbyssKraken> state)
    {
        BossChain spine = k.chain();
        if (!spine.isInitialised()) return;
        float pt = state.getPartialTick();
        double ex = Mth.lerp(pt, k.xo, k.getX()), ey = Mth.lerp(pt, k.yo, k.getY()), ez = Mth.lerp(pt, k.zo, k.getZ());
        double hx = spine.lerpX(0, pt), hy = spine.lerpY(0, pt), hz = spine.lerpZ(0, pt);
        float time = k.tickCount + pt;

        GeoBone mantle = (GeoBone) getAnimationProcessor().getBone("mantle");
        if (mantle != null)
        {
            mantle.setPosX((float) (-(hx - ex) * UNITS_PER_BLOCK));
            mantle.setPosY((float) ((hy - ey) * UNITS_PER_BLOCK));
            mantle.setPosZ((float) ((hz - ez) * UNITS_PER_BLOCK));
            // the whole body sways a little, hanging in the water (the old renderer's Z then X rotation)
            mantle.setRotZ(0.04F * Mth.sin(time * 0.031F));
            mantle.setRotX(0.04F * Mth.cos(time * 0.027F));
        }

        BossGeo.setDetailHidden(getAnimationProcessor(), SMALL, BossGeo.farDetail(k.farPoints(pt)));
        double[][] j = new double[AbyssKraken.JOINTS][3];
        for (int t = 0; t < AbyssKraken.TENTACLES; t++)
        {
            BossChain c = k.tentacle(t);
            int live = k.liveJoints(t);
            boolean sev = k.severed(t);
            boolean ok = c.isInitialised() && live >= 2;
            int arm = t + 1;
            GeoBone stump = (GeoBone) getAnimationProcessor().getBone(STUMP[t]);
            if (stump != null) stump.setHidden(!(ok && sev));   // GeoBone is shared by every kraken: set it every frame
            if (ok)
                for (int i = 0; i < live; i++)
                {
                    j[i][0] = c.lerpX(i, pt);
                    j[i][1] = c.lerpY(i, pt);
                    j[i][2] = c.lerpZ(i, pt);
                }
            // back direction of the root segment: horizontally from the body axis out to the arm's root
            double ox = j[0][0] - hx, oz = j[0][2] - hz, ol = Math.sqrt(ox * ox + oz * oz);
            if (ol < 1.0E-3) { ox = 1; oz = 0; ol = 1; }
            double[] b = {ox / ol, 0.0, oz / ol};
            double pivotX = -77.0 + 22.0 * t;   // arm_rest_origin[A].x
            double rx = 0.0, ry = 0.0, rz = 0.0;   // last rotation, kept for a zero-length segment
            for (int ss = 1; ss <= SEGMENTS; ss++)
            {
                GeoBone bone = (GeoBone) getAnimationProcessor().getBone(ARM[t][ss]);
                if (bone == null) continue;
                if (!ok || ss > live - 1)
                {
                    bone.setHidden(true);   // beyond the stump / not yet synced
                    continue;
                }
                bone.setHidden(false);
                double[] a = j[ss - 1], e = j[ss];
                double fx = e[0] - a[0], fy = e[1] - a[1], fz = e[2] - a[2];
                double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
                if (fl >= 1.0E-6)
                {
                    fx /= fl; fy /= fl; fz /= fl;
                    // b: the back (local +Y) perpendicular to f, carried along from segment to segment
                    double d = b[0] * fx + b[1] * fy + b[2] * fz;
                    double bx = b[0] - fx * d, by = b[1] - fy * d, bz = b[2] - fz * d;
                    double bl = Math.sqrt(bx * bx + by * by + bz * bz);
                    if (bl < 0.2)
                    {
                        d = fy;
                        bx = -fx * d; by = 1.0 - fy * d; bz = -fz * d;
                        bl = Math.sqrt(bx * bx + by * by + bz * bz);
                        if (bl < 1.0E-6) { bx = 1.0; by = 0.0; bz = 0.0; bl = 1.0; }
                    }
                    bx /= bl; by /= bl; bz /= bl;
                    b[0] = bx; b[1] = by; b[2] = bz;
                    // u = b x f
                    double ux = by * fz - bz * fy, uy = bz * fx - bx * fz, uz = bx * fy - by * fx;
                    rx = Math.atan2(bz, fz);
                    ry = -Math.asin(Mth.clamp(uz, -1.0, 1.0));
                    rz = Math.atan2(uy, ux);
                }
                bone.setRotX((float) rx);
                bone.setRotY((float) ry);
                bone.setRotZ((float) rz);
                // position: the segment's midpoint from the entity origin (geo units), minus the rest pivot (X mirrored)
                double mx = ((a[0] + e[0]) * 0.5 - ex) * UNITS_PER_BLOCK, my = ((a[1] + e[1]) * 0.5 - ey) * UNITS_PER_BLOCK,
                        mz = ((a[2] + e[2]) * 0.5 - ez) * UNITS_PER_BLOCK;
                bone.setPosX((float) (-mx - pivotX));
                bone.setPosY((float) (my - ARM_Y));
                bone.setPosZ((float) (mz - SEGMENT_LENGTH * (ss - 0.5)));
            }
        }
    }
}
