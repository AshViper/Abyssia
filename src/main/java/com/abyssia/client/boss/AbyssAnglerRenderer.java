package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssAngler;
import com.abyssia.entity.boss.BossChain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * BOSS05: the abyss angler (shape in {@link AnglerMesh}). Three passes over the same model: the cutout skin, the ragged
 * fin membranes translucent and two-sided, then the glow texture additively (lure bulbs, eyes, flank photophores, a faint
 * mouth lining), the bulbs brightened by {@link AbyssAngler#lureFlash}. Last, soft halos ({@code glow_halo}, additive)
 * round every lure bulb and the big eyes, because the fog swallows a small emissive surface within a few blocks.
 * Rendering goes by the chain, not by the entity's own position; beyond the fog end {@link BossFarRenderer} takes over.
 */
public class AbyssAnglerRenderer extends EntityRenderer<AbyssAngler>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/abyss_angler.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/abyss_angler_glow.png");
    private static final ResourceLocation HALO = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/glow_halo.png");
    /** Fin membranes on top of the texture's own alpha (ragged holes, thin film between the rays). */
    private static final float FIN_ALPHA = 0.9F;
    /** Lure light, #4cc8ff. */
    private static final float LR = 0.30F, LG = 0.78F, LB = 1.0F;

    public AbyssAnglerRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssAngler entity)
    {
        return TEXTURE;
    }

    @Override
    public void render(AbyssAngler entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain chain = entity.chain();
        if (!chain.isInitialised()) return;
        int n = chain.size();
        if (n < 3) return;
        double ex = Mth.lerp(pt, entity.xo, entity.getX()), ey = Mth.lerp(pt, entity.yo, entity.getY()), ez = Mth.lerp(pt, entity.zo, entity.getZ());
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition();
        boolean lod = ClientConfig.LOD_ENABLED.get();
        // beyond the fog end the model vanishes in the fog: BossFarRenderer draws the fog-ignoring ribbon and lure lights instead
        if (lod && BossFarRenderer.nearestJoint(chain, cp, pt) - entity.bodyRadius() > BossFarRenderer.fogEnd()) return;
        double[] jx = new double[n], jy = new double[n], jz = new double[n];
        int[] lights = new int[n];
        double near = Double.MAX_VALUE;
        for (int i = 0; i < n; i++)
        {
            jx[i] = chain.lerpX(i, pt);
            jy[i] = chain.lerpY(i, pt);
            jz[i] = chain.lerpZ(i, pt);
            lights[i] = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(jx[i], jy[i], jz[i]));
            near = Math.min(near, cp.distanceToSqr(jx[i], jy[i], jz[i]));
        }
        near = Math.sqrt(near);
        // LOD: fewer rings and sides once the whole animal is far off
        int sub = !lod || near < 160 ? 4 : near < 320 ? 2 : 1;
        int sides = !lod || near < 160 ? 16 : near < 320 ? 10 : 6;
        boolean detail = !lod || near < 220;

        float time = entity.tickCount + pt;
        // the jaw never quite closes: a slow gape under whatever the entity asks for
        float jaw = Math.max(Mth.clamp(entity.jawOpen(pt), 0.0F, 1.0F), 0.10F + 0.05F * Mth.sin(time * 0.05F));
        float flash = Mth.clamp(entity.lureFlash(pt), 0.0F, 1.0F);

        BossMesh.Path path = BossMesh.spline(jx, jy, jz, 0, n - 1, sub, ex, ey, ez, 0.35F);
        AnglerMesh angler = new AnglerMesh(pose, path, lights, jx, jy, jz, ex, ey, ez, (float) AbyssAngler.SIZE, jaw, time, flash, sub, sides, detail);
        // a BufferSource ends the previous batch when the type changes: one pass per render type
        angler.draw(new BossMesh(buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), pose, lights[0]), pose, AnglerMesh.Pass.SOLID);
        angler.draw(new BossMesh(buffer.getBuffer(RenderType.entityTranslucent(TEXTURE)), pose, lights[0]).color(1, 1, 1, FIN_ALPHA), pose,
                AnglerMesh.Pass.FIN);
        angler.draw(new BossMesh(buffer.getBuffer(RenderType.eyes(GLOW)), pose, LightTexture.FULL_BRIGHT), pose, AnglerMesh.Pass.GLOW);

        // halos: additive, so the colour carries the intensity; the lures swell with the flash and shimmer out of step
        VertexConsumer halo = buffer.getBuffer(RenderType.eyes(HALO));
        PoseStack.Pose last = pose.last();
        float s = (float) AbyssAngler.SIZE / AnglerMesh.DESIGN;
        for (int l = 0; l < AnglerMesh.LURE_COUNT; l++)
        {
            float a = (0.35F + 0.65F * flash) * (0.85F + 0.15F * Mth.sin(time * 0.11F + l * 1.9F));
            float size = (l == 0 ? 9.0F : 5.5F) * s * (1.0F + 0.35F * flash);
            BossMesh.billboard(halo, last, cam, angler.halo[3 * l], angler.halo[3 * l + 1], angler.halo[3 * l + 2], size, LR * a, LG * a, LB * a, 1);
        }
        for (int e = 0; e < 2; e++)
            BossMesh.billboard(halo, last, cam, angler.eyeHalo[3 * e], angler.eyeHalo[3 * e + 1], angler.eyeHalo[3 * e + 2], 6.0F * s,
                    0.16F, 0.36F, 0.45F, 1);
    }
}
