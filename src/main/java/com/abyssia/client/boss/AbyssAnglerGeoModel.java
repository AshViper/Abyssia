package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.entity.boss.AbyssAngler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model of the abyss angler (near view). {@code head} and {@code seg_01..seg_09} sit under root and are placed from the
 * chain (tools/boss/angler_model/contract.json); the lures, fins and the idle gape are left to the idle animation, and the
 * {@code jaw} opens further (up to 45 degrees) by {@link AbyssAngler#jawOpen} on top of it.
 */
public class AbyssAnglerGeoModel extends GeoModel<AbyssAngler>
{
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "geo/entity/abyss_angler.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/boss/abyss_angler_geo.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "animations/entity/abyss_angler.animation.json");
    public static final double UNITS_PER_BLOCK = 16.0 / AbyssAngler.SIZE;
    private static final float SEGMENT_LENGTH = 80.0F;
    private static final int SEGMENTS = 9;
    private static final float JAW_OPEN_RAD = (float) Math.toRadians(45.0);
    /** Small bones that do not shape the outline, hidden from {@link BossGeo#DETAIL_RANGE} (the lures stay: they carry the halos). */
    private static final String[] SMALL = {"teeth_upper", "teeth_lower"};

    @Override
    public ResourceLocation getModelResource(AbyssAngler animatable) { return MODEL; }

    @Override
    public ResourceLocation getTextureResource(AbyssAngler animatable) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(AbyssAngler animatable) { return ANIMATION; }

    @Override
    public void setCustomAnimations(AbyssAngler s, long instanceId, AnimationState<AbyssAngler> state)
    {
        float pt = state.getPartialTick();
        BossGeo.placeSpine(getAnimationProcessor(), s, pt, UNITS_PER_BLOCK, SEGMENT_LENGTH, SEGMENTS);
        if (s.chain().isInitialised()) BossGeo.setDetailHidden(getAnimationProcessor(), SMALL, BossGeo.farDetail(s.chain(), pt));
        GeoBone jaw = getAnimationProcessor().getBone("jaw");
        if (jaw != null) jaw.setRotX(jaw.getRotX() - Mth.clamp(s.jawOpen(pt), 0.0F, 1.0F) * JAW_OPEN_RAD);   // rx > 0 opens = -rotX
    }
}
