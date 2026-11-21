package com.abyssia.client.giant;

import com.abyssia.Abyssia;
import com.abyssia.entity.boss.BossChain;
import com.abyssia.entity.giant.GiantFauna;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * GNT01 shared GeckoLib model of a giant: bones {@code head} and {@code seg_01..seg_N} sit under root and are placed every frame
 * on the entity's chain (head at joint 0, seg_k at the midpoint of joints k-1 and k, local -Z toward the head, rest pivot of seg_k
 * at (0, 0, SEG_UNITS * (k - 0.5))); child bones belong to the animation (tools/boss/goblin_shark_model/contract.json).
 * A species only needs its asset names (the geo, animation and texture paths).
 */
public class GiantGeoModel extends GeoModel<GiantFauna>
{
    /** geo units of one segment at rest (1/16 block, before the model scale) */
    public static final double SEG_UNITS = 16.0;
    private final ResourceLocation model, texture, animation;
    private final double unitsPerBlock;
    private final String[] segNames;

    /** @param name file stem under geo/entity and animations/entity; texture is a full texture path (without assets prefix) */
    public GiantGeoModel(String name, String texturePath, double modelScale, int segments)
    {
        this.model = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "geo/entity/" + name + ".geo.json");
        this.animation = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "animations/entity/" + name + ".animation.json");
        this.texture = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, texturePath);
        this.unitsPerBlock = 16.0 / modelScale;
        this.segNames = new String[segments + 1];
        for (int k = 1; k <= segments; k++) segNames[k] = String.format("seg_%02d", k);
    }

    @Override
    public ResourceLocation getModelResource(GiantFauna animatable) { return model; }

    @Override
    public ResourceLocation getTextureResource(GiantFauna animatable) { return texture; }

    @Override
    public ResourceLocation getAnimationResource(GiantFauna animatable) { return animation; }

    @Override
    public void setCustomAnimations(GiantFauna g, long instanceId, AnimationState<GiantFauna> state)
    {
        BossChain chain = g.chain();
        if (!chain.isInitialised()) return;
        float pt = state.getPartialTick();
        double ex = Mth.lerp(pt, g.xo, g.getX()), ey = Mth.lerp(pt, g.yo, g.getY()), ez = Mth.lerp(pt, g.zo, g.getZ());
        int n = Math.min(chain.size(), segNames.length);
        double[][] j = new double[n][3];
        for (int i = 0; i < n; i++)
        {
            j[i][0] = chain.lerpX(i, pt) - ex;
            j[i][1] = chain.lerpY(i, pt) - ey;
            j[i][2] = chain.lerpZ(i, pt) - ez;
        }
        for (int k = 0; k < n; k++)
        {
            // k = 0: head at joint 0 looking along joint 1 -> joint 0; k >= 1: seg_k between joints k-1 and k
            int a = k == 0 ? 0 : k - 1, b = k == 0 ? 1 : k;
            if (b >= n) break;
            GeoBone bone = getAnimationProcessor().getBone(k == 0 ? "head" : segNames[k]);
            if (bone == null) continue;
            double dx = j[a][0] - j[b][0], dy = j[a][1] - j[b][1], dz = j[a][2] - j[b][2];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-6) continue;
            dx /= len;
            dy /= len;
            dz /= len;
            double mx = k == 0 ? j[0][0] : (j[a][0] + j[b][0]) * 0.5;
            double my = k == 0 ? j[0][1] : (j[a][1] + j[b][1]) * 0.5;
            double mz = k == 0 ? j[0][2] : (j[a][2] + j[b][2]) * 0.5;
            // geo X is mirrored in render space: pos = target pivot - rest pivot, with X sign flipped
            bone.setPosX((float) (-mx * unitsPerBlock));
            bone.setPosY((float) (my * unitsPerBlock));
            bone.setPosZ((float) (mz * unitsPerBlock - (k == 0 ? 0.0 : SEG_UNITS * (k - 0.5))));
            bone.setRotX((float) Math.asin(Mth.clamp(dy, -1.0, 1.0)));
            bone.setRotY((float) Math.atan2(-dx, -dz));
            bone.setRotZ(0.0F);
            // the chain's joint spacing can differ from the model's segment length: stretch the segment so no gap shows
            if (k > 0) bone.setScaleZ((float) (len * unitsPerBlock / SEG_UNITS));
        }
    }
}
