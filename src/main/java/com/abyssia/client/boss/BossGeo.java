package com.abyssia.client.boss;

import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssBoss;
import com.abyssia.entity.boss.BossChain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Shared bits of the GeckoLib boss near views (trench wyrm, kraken, mother, angler): placing a spine of root bones along a
 * {@link BossChain} (same maths as {@link AbyssSerpentGeoModel}) and the additive glow layer. Client only.
 */
final class BossGeo
{
    /** Distance (blocks) from which the old renderer's "outside the fog = not drawn" cut-off applies (the model itself is drawn at any distance). */
    static final double GEO_RANGE = AbyssSerpentGeoRenderer.GEO_RANGE;
    /** Distance (blocks, to the nearest joint) from which the small outline-free bones (teeth, bristles, beaks) are hidden. */
    static final double DETAIL_RANGE = 96.0;
    /** {@code seg_01..seg_31}, built once (index = k). */
    static final String[] SEG = names("seg_%02d", 1, 31);
    /** Set by {@link BossFarRenderer} while it draws a tracked boss's model beyond the fog end: lifts the "outside the fog = not drawn" cut-off. */
    static boolean forceFar;

    private BossGeo()
    {
    }

    /** {@code names[i] = String.format(fmt, i)} for i in from..to (other slots null); built once at class load. */
    static String[] names(String fmt, int from, int to)
    {
        String[] a = new String[to + 1];
        for (int i = from; i <= to; i++) a[i] = String.format(fmt, i);
        return a;
    }

    /** Shows or hides the small bones every frame (GeoBone is shared by all individuals of a model). */
    static void setDetailHidden(AnimationProcessor<?> proc, String[] bones, boolean hidden)
    {
        for (String n : bones)
        {
            GeoBone b = (GeoBone) proc.getBone(n);
            if (b != null) b.setHidden(hidden);
        }
    }

    /** Same for a boss measured by its far points (kraken, mother). */
    static boolean farDetail(double[] points)
    {
        if (!ClientConfig.LOD_ENABLED.get()) return false;
        return BossFarRenderer.nearestPoint(points, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) >= DETAIL_RANGE;
    }

    /** True when the nearest joint is far enough ({@link #DETAIL_RANGE}) to drop the small bones; false with the LOD option off. */
    static boolean farDetail(BossChain chain, float pt)
    {
        if (!ClientConfig.LOD_ENABLED.get()) return false;
        return BossFarRenderer.nearestJoint(chain, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), pt) >= DETAIL_RANGE;
    }

    /**
     * {@code head} and {@code seg_01..seg_NN} (root bones, -Z forward, segment k from joint k-1 to k, pivot z = segLen * (k - 0.5))
     * are put along the interpolated chain joints. {@code upb} = geo units per block.
     */
    static void placeSpine(AnimationProcessor<?> proc, AbyssBoss s, float pt, double upb, float segLen, int segs)
    {
        BossChain chain = s.chain();
        if (!chain.isInitialised()) return;
        int n = chain.size();
        double ex = Mth.lerp(pt, s.xo, s.getX()), ey = Mth.lerp(pt, s.yo, s.getY()), ez = Mth.lerp(pt, s.zo, s.getZ());
        double[][] j = new double[n][3];
        for (int i = 0; i < n; i++)
        {
            j[i][0] = (chain.lerpX(i, pt) - ex) * upb;
            j[i][1] = (chain.lerpY(i, pt) - ey) * upb;
            j[i][2] = (chain.lerpZ(i, pt) - ez) * upb;
        }
        if (n >= 2) place((GeoBone) proc.getBone("head"), j[0], j[0], j[1], 0.0F);
        for (int k = 1; k <= segs; k++)
        {
            GeoBone bone = (GeoBone) proc.getBone(SEG[k]);
            if (bone == null) continue;
            if (k >= n)
            {
                bone.setHidden(true);   // chain shorter than the model
                continue;
            }
            double[] a = j[k - 1], b = j[k];
            double[] mid = {(a[0] + b[0]) * 0.5, (a[1] + b[1]) * 0.5, (a[2] + b[2]) * 0.5};
            place(bone, mid, a, b, segLen * (k - 0.5F));
        }
    }

    /** Puts a root bone at {@code target} (render space = world axes, geo units) with local -Z pointing from {@code back} to {@code front}. */
    static void place(GeoBone bone, double[] target, double[] front, double[] back, float restPivotZ)
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

    /** Glow texture drawn additively over the model (RenderType.eyes), full bright. */
    static class GlowLayer<T extends GeoAnimatable> extends GeoRenderLayer<T>
    {
        private final ResourceLocation glow;

        GlowLayer(GeoRenderer<T> renderer, ResourceLocation glow)
        {
            super(renderer);
            this.glow = glow;
        }

        @Override
        public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType, MultiBufferSource bufferSource,
                           @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
        {
            RenderType type = RenderType.eyes(glow);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick,
                    LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
