package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.entity.boss.TrenchWyrm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * GeckoLib model of the trench wyrm (near view). {@code head} and {@code seg_01..seg_26} sit under root and are placed from the
 * chain (tools/boss/wyrm_model/contract.json); the four mandibles open from {@link TrenchWyrm#mawOpen} on top of the idle
 * animation (rotation x, 0 = shut, 72 degrees = the old full gape).
 */
public class TrenchWyrmGeoModel extends GeoModel<TrenchWyrm>
{
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "geo/entity/trench_wyrm.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/boss/trench_wyrm_geo.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "animations/entity/trench_wyrm.animation.json");
    public static final double UNITS_PER_BLOCK = 16.0 / TrenchWyrm.SIZE;
    private static final float SEGMENT_LENGTH = 64.0F;
    private static final int SEGMENTS = 26;
    private static final float JAW_OPEN_RAD = (float) Math.toRadians(72.0);
    /** Small bones that do not shape the outline (teeth, the bristles on every segment), hidden from {@link BossGeo#DETAIL_RANGE}. */
    private static final String[] SMALL = smallBones();

    private static String[] smallBones()
    {
        String[] a = new String[1 + 2 * SEGMENTS];
        a[0] = "teeth";
        for (int k = 1; k <= SEGMENTS; k++)
        {
            a[2 * k - 1] = String.format("seg_%02d_bristle_l", k);
            a[2 * k] = String.format("seg_%02d_bristle_r", k);
        }
        return a;
    }

    private final Map<TrenchWyrm, float[]> jaws = new WeakHashMap<>();

    @Override
    public ResourceLocation getModelResource(TrenchWyrm animatable) { return MODEL; }

    @Override
    public ResourceLocation getTextureResource(TrenchWyrm animatable) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(TrenchWyrm animatable) { return ANIMATION; }

    @Override
    public void setCustomAnimations(TrenchWyrm s, long instanceId, AnimationState<TrenchWyrm> state)
    {
        BossGeo.placeSpine(getAnimationProcessor(), s, state.getPartialTick(), UNITS_PER_BLOCK, SEGMENT_LENGTH, SEGMENTS);
        if (s.chain().isInitialised()) BossGeo.setDetailHidden(getAnimationProcessor(), SMALL, BossGeo.farDetail(s.chain(), state.getPartialTick()));
        // maw: synced from the AI, smoothed here like the old renderer; added to the idle gape (rx > 0 opens = -rotX)
        // time based (0.25 per tick at 20 fps ticks): safe for any frame rate and for repeated calls in one frame
        float time = s.tickCount + state.getPartialTick();
        float[] jaw = jaws.computeIfAbsent(s, e -> new float[]{s.mawOpen() ? 1.0F : 0.0F, time});
        float dt = Mth.clamp(time - jaw[1], 0.0F, 20.0F);
        jaw[1] = time;
        jaw[0] += ((s.mawOpen() ? 1.0F : 0.0F) - jaw[0]) * (1.0F - (float) Math.pow(0.75, dt));
        for (int m = 1; m <= 4; m++)
        {
            GeoBone bone = getAnimationProcessor().getBone("mandible_" + m);
            if (bone != null) bone.setRotX(bone.getRotX() - jaw[0] * JAW_OPEN_RAD);
        }
    }
}
