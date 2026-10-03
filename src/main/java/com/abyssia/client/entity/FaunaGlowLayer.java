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
    /** Every part of the model and the glowing subtrees, resolved once (the model never changes shape). */
    private List<ModelPart> all;
    private List<ModelPart> glowing;
    /** Per halo: the parts from the root down to its bone, and the centre of the bone's first cube (null = no such bone). */
    private List<ModelPart>[] haloChains;
    private Vector3f[] haloCentres;

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
            this.all = model.root().getAllParts().toList();
            // a bone's rotated cubes live in sub-parts: draw the whole subtree of each glowing bone
            this.glowing = this.parts.stream().flatMap(ModelPart::getAllParts).toList();
            resolveHalos(model.root());
        }
        // Resolved lists, no per-frame streams: this runs for every visible animal every frame.
        for (ModelPart p : this.all) p.skipDraw = true;
        for (ModelPart p : this.glowing) p.skipDraw = false;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(this.texture));
        // 1.21 takes one packed ARGB tint; clamped so an out-of-range channel cannot spill into its neighbours
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.colorFromFloat(Mth.clamp(alpha, 0.0F, 1.0F), Mth.clamp(c[0], 0.0F, 1.0F), Mth.clamp(c[1], 0.0F, 1.0F), Mth.clamp(c[2], 0.0F, 1.0F)));
        for (ModelPart p : this.all) p.skipDraw = false;

        if (this.halos.isEmpty() || this.mode != Mode.BIOLUMINESCENT) return;
        VertexConsumer halo = buffers.getBuffer(RenderType.eyes(HALO));
        float r = c[0] * alpha, g = c[1] * alpha, b = c[2] * alpha;
        Vector3f centre = new Vector3f();
        for (int i = 0; i < this.halos.size(); i++)
        {
            List<ModelPart> chain = this.haloChains[i];
            if (chain == null) continue;
            Halo h = this.halos.get(i);
            // the same transform ModelPart.visit applies down to the bone, without walking the whole tree with path strings
            poseStack.pushPose();
            for (ModelPart p : chain) p.translateAndRotate(poseStack);
            // the cube centre in view space; the sprite is laid out in view space, so it always faces the camera
            Vector3f local = this.haloCentres[i];
            poseStack.last().pose().transformPosition(local.x, local.y, local.z, centre);
            poseStack.popPose();
            // additive: the colour is the light added, so it carries the intensity
            sprite(halo, centre, h.radius(), h.r() * r, h.g() * g, h.b() * b);
        }
    }

    /**
     * Finds, once, the first cube under each halo's bone (in {@link ModelPart#visit} order, as the halos always used)
     * and the chain of parts from the root down to it.
     */
    @SuppressWarnings("unchecked")
    private void resolveHalos(ModelPart root)
    {
        int n = this.halos.size();
        this.haloChains = new List[n];
        this.haloCentres = new Vector3f[n];
        if (n == 0) return;
        String[] paths = new String[n];
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            for (int i = 0; i < n; i++)
            {
                String bone = this.halos.get(i).bone();
                if (paths[i] != null || !(path.endsWith("/" + bone) || path.contains("/" + bone + "/"))) continue;
                paths[i] = path;
                this.haloCentres[i] = new Vector3f((cube.minX + cube.maxX) / 32.0F, (cube.minY + cube.maxY) / 32.0F, (cube.minZ + cube.maxZ) / 32.0F);
            }
        });
        for (int i = 0; i < n; i++)
        {
            if (paths[i] == null) continue;
            List<ModelPart> chain = new java.util.ArrayList<>();
            chain.add(root);
            ModelPart part = root;
            for (String name : paths[i].split("/"))
            {
                if (name.isEmpty()) continue;
                part = part.getChild(name);
                chain.add(part);
            }
            this.haloChains[i] = List.copyOf(chain);
        }
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
