package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.entity.boss.AbyssSerpent;
import com.abyssia.entity.boss.BossChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model of the abyss serpent (near view). {@code head} and {@code seg_01..seg_28} sit directly under root and are
 * placed every frame from the interpolated {@link BossChain} joints (tools/boss/serpent_model/contract.json); the jaw, gills
 * and fins are children and left to the idle animation.
 */
public class AbyssSerpentGeoModel extends GeoModel<AbyssSerpent>
{
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "geo/entity/abyss_serpent.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/boss/abyss_serpent_geo.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "animations/entity/abyss_serpent.animation.json");
    /** geo units per block in the rendered model: 16 geo units per block / MODEL_SCALE (= AbyssSerpent.SIZE) */
    public static final double UNITS_PER_BLOCK = 16.0 / AbyssSerpent.SIZE;
    private static final float SEGMENT_LENGTH = 56.0F;
    private static final int SEGMENTS = 28;
    /** Small bones that do not shape the outline, hidden from {@link BossGeo#DETAIL_RANGE}. */
    private static final String[] SMALL = {"teeth_upper", "teeth_lower"};

    @Override
    public ResourceLocation getModelResource(AbyssSerpent animatable) { return MODEL; }

    @Override
    public ResourceLocation getTextureResource(AbyssSerpent animatable) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(AbyssSerpent animatable) { return ANIMATION; }

    @Override
    public void setCustomAnimations(AbyssSerpent s, long instanceId, AnimationState<AbyssSerpent> state)
    {
        BossChain chain = s.chain();
        if (!chain.isInitialised()) return;
        float pt = state.getPartialTick();
        BossGeo.setDetailHidden(getAnimationProcessor(), SMALL, BossGeo.farDetail(chain, pt));
        int n = chain.size();
        double ex = Mth.lerp(pt, s.xo, s.getX()), ey = Mth.lerp(pt, s.yo, s.getY()), ez = Mth.lerp(pt, s.zo, s.getZ());
        double[][] j = new double[n][3];
        for (int i = 0; i < n; i++)
        {
            j[i][0] = (chain.lerpX(i, pt) - ex) * UNITS_PER_BLOCK;
            j[i][1] = (chain.lerpY(i, pt) - ey) * UNITS_PER_BLOCK;
            j[i][2] = (chain.lerpZ(i, pt) - ez) * UNITS_PER_BLOCK;
        }
        if (n >= 2) place(getAnimationProcessor().getBone("head"), j[0], j[0], j[1], 0.0F);
        for (int k = 1; k <= SEGMENTS; k++)
        {
            GeoBone bone = getAnimationProcessor().getBone(BossGeo.SEG[k]);
            if (bone == null) continue;
            if (k >= n)
            {
                bone.setHidden(true);   // chain shorter than the model
                continue;
            }
            double[] a = j[k - 1], b = j[k];
            double[] mid = {(a[0] + b[0]) * 0.5, (a[1] + b[1]) * 0.5, (a[2] + b[2]) * 0.5};
            place(bone, mid, a, b, SEGMENT_LENGTH * (k - 0.5F));
        }
    }

    /** Puts a root bone at {@code target} (render space = world axes, geo units) with local -Z pointing from {@code back} to {@code front}. */
    private static void place(GeoBone bone, double[] target, double[] front, double[] back, float restPivotZ)
    {
        if (bone == null) return;
        bone.setHidden(false);
        bone.setPosX((float) -target[0]);
        bone.setPosY((float) target[1]);
        bone.setPosZ((float) (target[2] - restPivotZ));
        double dx = front[0] - back[0], dy = front[1] - back[1], dz = front[2] - back[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) return;
        dx /= len; dy /= len; dz /= len;
        bone.setRotX((float) Math.asin(Mth.clamp(dy, -1.0, 1.0)));
        bone.setRotY((float) Math.atan2(-dx, -dz));
        bone.setRotZ(0.0F);
    }
}
