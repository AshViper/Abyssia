package com.abyssia.client.entity;

import com.abyssia.Abyssia;
import com.abyssia.entity.FaunaAnimated;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Renders a fauna model: its skin, a translucent pass for see-through bones (a barreleye's head shield, a sea
 * cucumber's gelatinous body), glow layers, a size correction (the generator draws every animal at a readable
 * size; the scale restores the real size order) and, for fish, lying on their side when stranded. The model's own
 * death clip replaces vanilla's sideways flop.
 */
public class FaunaRenderer<T extends Mob & FaunaAnimated> extends MobRenderer<T, FaunaModel<T>>
{
    private final ResourceLocation texture;
    private final float scale;
    private final boolean strandedOnSide;

    private FaunaRenderer(EntityRendererProvider.Context context, Spec<T> spec)
    {
        super(context, spec.model.apply(context.bakeLayer(spec.layer)), spec.shadow);
        this.texture = texture(spec.id, "");
        this.scale = spec.scale;
        this.strandedOnSide = spec.strandedOnSide;
        if (!this.model.translucentBones().isEmpty()) this.addLayer(new TranslucentLayer<>(this, this.texture));
        ResourceLocation glow = texture(spec.id, "_glow");
        for (GlowSpec<T> g : spec.glows)
        {
            // Eyeshine reflects light off the eyes, so it is drawn from the skin.
            // The glow mask only covers real light organs and does not exist at all
            // for species without bioluminescence (using it logged "Failed to load
            // texture: ..._glow.png" for every eyeshine-only animal).
            ResourceLocation layerTexture = g.mode == FaunaGlowLayer.Mode.EYESHINE ? this.texture : glow;
            this.addLayer(new FaunaGlowLayer<>(this, layerTexture, g.mode, g.bones, g.halos, g.glow));
        }
    }

    private static ResourceLocation texture(String id, String suffix)
    {
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/" + id + suffix + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(T entity)
    {
        return this.texture;
    }

    @Override
    protected void scale(T entity, PoseStack pose, float partialTick)
    {
        pose.scale(this.scale, this.scale, this.scale);
    }

    @Override
    protected float getFlipDegrees(T entity)
    {
        return 0.0F;
    }

    @Override
    protected void setupRotations(T entity, PoseStack pose, float ageInTicks, float rotationYaw, float partialTicks, float entityScale)
    {
        super.setupRotations(entity, pose, ageInTicks, rotationYaw, partialTicks, entityScale);
        if (this.strandedOnSide && !entity.isInWater() && !entity.isDeadOrDying())
        {
            pose.translate(0.1F, 0.1F, -0.1F);
            pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }
    }

    /** Draws only the translucent bones, blended, with the scene's light. */
    private static final class TranslucentLayer<T extends Mob & FaunaAnimated> extends RenderLayer<T, FaunaModel<T>>
    {
        private final ResourceLocation texture;
        private List<ModelPart> parts;
        private List<ModelPart> all;

        TranslucentLayer(FaunaRenderer<T> parent, ResourceLocation texture)
        {
            super(parent);
            this.texture = texture;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, T entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch)
        {
            if (entity.isInvisible()) return;
            FaunaModel<T> model = this.getParentModel();
            if (this.parts == null)
            {
                this.parts = model.translucentBones().stream().map(model::bone).toList();
                this.all = model.root().getAllParts().toList();
            }
            // resolved once: no per-frame stream over the whole model for every visible animal
            for (ModelPart p : this.all) p.skipDraw = true;
            for (ModelPart p : this.parts) p.skipDraw = false;
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(this.texture));
            model.renderToBuffer(pose, consumer, packedLight, LivingEntityRenderer.getOverlayCoords(entity, 0.0F));
            for (ModelPart p : this.all) p.skipDraw = false;
        }
    }

    // ---------------------------------------------------------------- configuration

    public static <T extends Mob & FaunaAnimated> Spec<T> spec(String id, ModelLayerLocation layer, Function<ModelPart, FaunaModel<T>> model)
    {
        return new Spec<>(id, layer, model);
    }

    private record GlowSpec<T>(FaunaGlowLayer.Mode mode, List<String> bones, List<FaunaGlowLayer.Halo> halos, FaunaGlowLayer.Glow<T> glow) {}

    public static final class Spec<T extends Mob & FaunaAnimated>
    {
        private final String id;
        private final ModelLayerLocation layer;
        private final Function<ModelPart, FaunaModel<T>> model;
        private final List<GlowSpec<T>> glows = new ArrayList<>();
        private float scale = 1.0F;
        private float shadow = 0.3F;
        private boolean strandedOnSide;

        private Spec(String id, ModelLayerLocation layer, Function<ModelPart, FaunaModel<T>> model)
        {
            this.id = id;
            this.layer = layer;
            this.model = model;
        }

        /** Size correction on top of the generated model, and the shadow radius (in blocks, after scaling). */
        public Spec<T> scale(float scale, float shadow)
        {
            this.scale = scale;
            this.shadow = shadow;
            return this;
        }

        /** Fish: out of water it lies on its side. */
        public Spec<T> fish()
        {
            this.strandedOnSide = true;
            return this;
        }

        public Spec<T> light(List<String> bones, List<FaunaGlowLayer.Halo> halos, FaunaGlowLayer.Glow<T> glow)
        {
            this.glows.add(new GlowSpec<>(FaunaGlowLayer.Mode.BIOLUMINESCENT, bones, halos, glow));
            return this;
        }

        /** Eyes that reflect light (a tapetum); invisible in the dark. */
        public Spec<T> eyeshine(float intensity, String... bones)
        {
            float[] c = {1.0F, 1.0F, 1.0F, intensity};
            this.glows.add(new GlowSpec<>(FaunaGlowLayer.Mode.EYESHINE, List.of(bones), List.of(), (e, p, a) -> c));
            return this;
        }

        public EntityRendererProvider<T> provider()
        {
            return context -> new FaunaRenderer<>(context, this);
        }
    }
}
