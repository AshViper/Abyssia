package com.abyssia.client.entity;

import com.abyssia.Abyssia;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.joml.Vector3f;

import java.util.List;

/**
 * Light on an animal, drawn from the generator's glow mask (same UV as the skin, transparent where nothing glows),
 * limited to the named bones so one mask can serve two kinds of light:
 * <ul>
 *     <li>{@link Mode#BIOLUMINESCENT}: a real light organ (a lure, a tail light, photophores) at full brightness, with
 *     a colour / alpha the animal decides every frame, and optionally a halo: a soft camera-facing sprite added at
 *     the organ, because deep-sea fog swallows a small emissive texture within a few blocks;</li>
 *     <li>{@link Mode#EYESHINE}: light reflected by eyes (a tapetum). It only shows as far as there is light around
 *     the animal: none in the dark, faint near glowing plants. Not every animal glows.</li>
 * </ul>
 */
public class FaunaGlowLayer<T extends Mob, M extends HierarchicalModel<T>> extends RenderLayer<T, M>
{
    private static final ResourceLocation HALO = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/glow_halo.png");

    public enum Mode { BIOLUMINESCENT, EYESHINE }

    /** Colour and alpha of the glow this frame: {r, g, b, a}, each 0..1. */
    @FunctionalInterface
    public interface Glow<T>
    {
        float[] rgba(T entity, float partialTick, float ageInTicks);
    }

    /** A halo at a bone: radius in blocks and tint (multiplied by the frame's glow colour). */
    public record Halo(String bone, float radius, float r, float g, float b) {}

    private final ResourceLocation texture;
    private final List<String> bones;
    private final List<Halo> halos;
    private final Mode mode;
    private final Glow<T> glow;
    private List<ModelPart> parts;

    public FaunaGlowLayer(RenderLayerParent<T, M> parent, ResourceLocation texture, Mode mode, List<String> bones, List<Halo> halos, Glow<T> glow)
    {
        super(parent);
        this.texture = texture;
        this.mode = mode;
        this.bones = bones;
        this.halos = halos;
        this.glow = glow;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch)
    {
        if (entity.isInvisible()) return;
        float[] c = this.glow.rgba(entity, partialTick, ageInTicks);
        float alpha = c[3];
        // eyeshine is reflected light: as bright as the light that reaches the animal
        if (this.mode == Mode.EYESHINE) alpha *= Math.max(LightTexture.block(packedLight), LightTexture.sky(packedLight) * 0.5F) / 15.0F;
        if (alpha <= 0.01F) return;
        M model = this.getParentModel();
        if (this.parts == null)
        {
            this.parts = this.bones.stream().map(n -> model.getAnyDescendantWithName(n).orElseThrow()).toList();
        }
        List<ModelPart> all = model.root().getAllParts().toList();
        all.forEach(p -> p.skipDraw = true);
        // a bone's rotated cubes live in sub-parts: draw the whole subtree of each glowing bone
        this.parts.forEach(p -> p.getAllParts().forEach(q -> q.skipDraw = false));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(this.texture));
        // 1.21 takes one packed ARGB tint; clamped so an out-of-range channel cannot spill into its neighbours
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.colorFromFloat(Mth.clamp(alpha, 0.0F, 1.0F), Mth.clamp(c[0], 0.0F, 1.0F), Mth.clamp(c[1], 0.0F, 1.0F), Mth.clamp(c[2], 0.0F, 1.0F)));
        all.forEach(p -> p.skipDraw = false);

        if (this.halos.isEmpty() || this.mode != Mode.BIOLUMINESCENT) return;
        VertexConsumer halo = buffers.getBuffer(RenderType.eyes(HALO));
        float r = c[0] * alpha, g = c[1] * alpha, b = c[2] * alpha;
        boolean[] drawn = new boolean[this.halos.size()];
        model.root().visit(poseStack, (pose, path, index, cube) -> {
            for (int i = 0; i < this.halos.size(); i++)
            {
                Halo h = this.halos.get(i);
                if (drawn[i] || !(path.endsWith("/" + h.bone()) || path.contains("/" + h.bone() + "/"))) continue;
                drawn[i] = true;
                // the cube centre in view space; the sprite is laid out in view space, so it always faces the camera
                Vector3f centre = pose.pose().transformPosition((cube.minX + cube.maxX) / 32.0F, (cube.minY + cube.maxY) / 32.0F,
                        (cube.minZ + cube.maxZ) / 32.0F, new Vector3f());
                // additive: the colour is the light added, so it carries the intensity
                sprite(halo, centre, h.radius(), h.r() * r, h.g() * g, h.b() * b);
            }
        });
    }

    private static void sprite(VertexConsumer consumer, Vector3f c, float s, float r, float g, float b)
    {
        // both windings, so face culling never hides it
        vertex(consumer, c, -s, -s, 0, 1, r, g, b);
        vertex(consumer, c, s, -s, 1, 1, r, g, b);
        vertex(consumer, c, s, s, 1, 0, r, g, b);
        vertex(consumer, c, -s, s, 0, 0, r, g, b);
        vertex(consumer, c, -s, s, 0, 0, r, g, b);
        vertex(consumer, c, s, s, 1, 0, r, g, b);
        vertex(consumer, c, s, -s, 1, 1, r, g, b);
        vertex(consumer, c, -s, -s, 0, 1, r, g, b);
    }

    private static void vertex(VertexConsumer consumer, Vector3f c, float dx, float dy, float u, float v, float r, float g, float b)
    {
        consumer.addVertex(c.x() + dx, c.y() + dy, c.z()).setColor(r, g, b, 1.0F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 0.0F, 1.0F);
    }
}
