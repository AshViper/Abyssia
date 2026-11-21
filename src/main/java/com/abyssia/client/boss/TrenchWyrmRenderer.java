package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.BossChain;
import com.abyssia.entity.boss.TrenchWyrm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * BOSS03: the trench wyrm as a plated bristle worm, one smooth ringed body along the chain (see {@link BossMesh}):
 * two armour rings bulge per joint, rows of paddling bristles run down both flanks, the tail ends in a needle. The
 * head is an eyeless collar round a lamprey maw (three rings of teeth down a glowing gullet) closed by four hooked
 * mandibles that splay open when the AI opens the maw. Head parts are modelled for a body radius of 8.
 */
public class TrenchWyrmRenderer extends EntityRenderer<TrenchWyrm>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/trench_wyrm.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/trench_wyrm_glow.png");
    private static final float JAW_CLOSED = 0.45F, JAW_OPEN = -0.8F;

    /** Loft stations {z, half width, up, down}; +Z = out of the maw. Both run from the rim backwards (v = 0 at the rim). */
    private static final float[][] COLLAR = {{9, 7.2F, 7.2F, 7.2F}, {7, 8.9F, 8.9F, 8.9F}, {3, 9.4F, 9.4F, 9.4F}, {-2, 8.6F, 8.6F, 8.6F}, {-8, 8.0F, 8.0F, 8.0F}};
    private static final float[][] GULLET = {{9, 7.2F, 7.2F, 7.2F}, {6, 6.0F, 6.0F, 6.0F}, {0, 4.2F, 4.2F, 4.2F}, {-8, 2.2F, 2.2F, 2.2F}, {-14, 0.4F, 0.4F, 0.4F}};
    private static final float[][] MANDIBLE = {{0, 3.2F, 1.6F, 1.2F}, {6, 3.0F, 1.5F, 1.0F}, {12, 1.9F, 1.1F, 0.8F}, {16.5F, 0.6F, 0.5F, 0.4F}, {18, 0.1F, 0.1F, 0.1F}};

    private static final Map<TrenchWyrm, float[]> JAW = new WeakHashMap<>();

    public TrenchWyrmRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TrenchWyrm entity)
    {
        return TEXTURE;
    }

    /** Cross-section scale at joint coordinate k: 1.0 behind the head to 0.6 before the tail, which thins to the needle. */
    private static float bodyScale(float k, int n)
    {
        if (k <= 1) return 1.0F;
        float s = 1.0F - 0.4F * (k - 1) / Math.max(1, n - 2);
        float t = k - (n - 4);
        if (t > 0) s *= Math.max(0.05F, 1.0F - 0.31F * t);
        return s;
    }

    @Override
    public void render(TrenchWyrm entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain chain = entity.chain();
        if (!chain.isInitialised()) return;
        int n = chain.size();
        if (n < 3) return;
        double ex = Mth.lerp(pt, entity.xo, entity.getX()), ey = Mth.lerp(pt, entity.yo, entity.getY()), ez = Mth.lerp(pt, entity.zo, entity.getZ());
        Vec3 cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean lod = ClientConfig.LOD_ENABLED.get();
        // beyond the fog end the model vanishes in the fog: BossFarRenderer draws the fog-ignoring ribbon instead
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
        // LOD: the armour rings need 4 points per joint; far off the body is a plain tube
        int sub = !lod || near < 160 ? 4 : near < 320 ? 2 : 1;
        int sides = !lod || near < 160 ? 14 : near < 320 ? 10 : 6;
        boolean detail = !lod || near < 220;

        // maw: synced from the AI (open while erupting / sweeping, snaps shut on the bite), smoothed here
        float time = entity.tickCount + pt;
        float[] jawState = JAW.computeIfAbsent(entity, e -> new float[1]);
        jawState[0] += ((entity.mawOpen() ? 1.0F : 0.0F) - jawState[0]) * 0.25F;
        float jaw = Math.max(jawState[0], 0.10F + 0.08F * Mth.sin(time * 0.07F));

        BossMesh.Path path = BossMesh.spline(jx, jy, jz, 0, n - 1, sub, ex, ey, ez, 0.3F);
        float r = (float) entity.bodyRadius();
        model(new BossMesh(buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), pose, lights[0]), pose, path, lights, n, r, sides, sub, detail, false,
                jaw, time, jx, jy, jz, ex, ey, ez);
        // glow pass: the same surfaces with the glow texture, additive (a BufferSource ends the previous batch when the type changes)
        model(new BossMesh(buffer.getBuffer(RenderType.eyes(GLOW)), pose, LightTexture.FULL_BRIGHT), pose, path, null, n, r, sides, sub, detail, true,
                jaw, time, jx, jy, jz, ex, ey, ez);
    }

    private void model(BossMesh mesh, PoseStack pose, BossMesh.Path path, int[] lights, int n, float r, int sides, int sub, boolean detail, boolean glow,
                       float jaw, float time, double[] jx, double[] jy, double[] jz, double ex, double ey, double ez)
    {
        // body: a little flattened, each joint two armour rings standing proud of the seams between them
        boolean rings = sub >= 4;
        mesh.tube(path, sides, (k, o) -> {
            float s = bodyScale(k, n) * r * (rings ? 0.92F + 0.08F * Math.abs(Mth.sin(k * Mth.TWO_PI)) : 0.97F);
            o[0] = 1.05F * s; o[1] = 0.95F * s; o[2] = 0.85F * s; o[3] = 0.95F;
        }, BossMesh.SKIN, 1, lights, true, true);

        if (detail && !glow)
        {
            // bristles: two per joint and flank, paddling in a wave down the body; a low spine on every ring's crest
            for (int i = sub; i < path.n - sub; i += Math.max(1, sub / 2))
            {
                float k = path.k[i], s = bodyScale(k, n) * r;
                if (lights != null) mesh.light(lights[Mth.clamp(Math.round(k), 0, n - 1)]);
                float a = 0.55F + 0.35F * Mth.sin(time * 0.16F - k * 0.9F);
                float sweep = 0.35F * Mth.cos(time * 0.16F - k * 0.9F);
                for (int side = -1; side <= 1; side += 2)
                {
                    float ox = path.rx[i] * side, oy = path.ry[i] * side, oz = path.rz[i] * side;
                    float ca = Mth.cos(a) * 0.75F * s, sa = Mth.sin(a) * 0.75F * s, sw = sweep * 0.75F * s;
                    mesh.spike(path.x[i] + ox * 0.95F * s - path.ux[i] * 0.3F * s, path.y[i] + oy * 0.95F * s - path.uy[i] * 0.3F * s,
                            path.z[i] + oz * 0.95F * s - path.uz[i] * 0.3F * s,
                            ox * ca - path.ux[i] * sa + path.tx[i] * sw, oy * ca - path.uy[i] * sa + path.ty[i] * sw, oz * ca - path.uz[i] * sa + path.tz[i] * sw,
                            0.13F * s, 4, BossMesh.HORN);
                }
                mesh.spike(path.x[i] + path.ux[i] * 0.9F * s, path.y[i] + path.uy[i] * 0.9F * s, path.z[i] + path.uz[i] * 0.9F * s,
                        (path.ux[i] * 0.45F + path.tx[i] * 0.3F) * s, (path.uy[i] * 0.45F + path.ty[i] * 0.3F) * s, (path.uz[i] * 0.45F + path.tz[i] * 0.3F) * s,
                        0.16F * s, 4, BossMesh.HORN);
            }
            // tail needle
            int e = path.n - 1;
            if (lights != null) mesh.light(lights[n - 1]);
            mesh.spike(path.x[e], path.y[e], path.z[e], path.tx[e] * 1.9F * r, path.ty[e] * 1.9F * r, path.tz[e] * 1.9F * r, 0.14F * r, 5, BossMesh.HORN);
        }

        // head, in a frame at joint 0 looking along the neck (+Z out of the maw), modelled for a body radius of 8
        pose.pushPose();
        pose.translate(jx[0] - ex, jy[0] - ey, jz[0] - ez);
        double dx = jx[0] - jx[1], dy = jy[0] - jy[1], dz = jz[0] - jz[1], len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0E-6)
        {
            pose.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
            pose.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(dy / len, -1.0, 1.0))));
        }
        pose.scale(r / 8.0F, r / 8.0F, r / 8.0F);
        if (lights != null) mesh.light(lights[0]);
        mesh.pose(pose);
        mesh.loft(COLLAR, sides, BossMesh.HEAD);
        mesh.loft(GULLET, sides, BossMesh.INNER);
        if (detail && !glow)
        {
            // three rings of teeth down the gullet, leaning toward the throat
            ring(mesh, 16, 6.5F, 7.5F, 2.7F, 1.4F, 0.55F, 0);
            ring(mesh, 12, 5.0F, 3.0F, 2.3F, -0.6F, 0.5F, 0.26F);
            ring(mesh, 8, 3.4F, -3.0F, 1.8F, -0.8F, 0.45F, 0);
        }
        float tilt = Mth.lerp(jaw, JAW_CLOSED, JAW_OPEN);
        for (int m = 0; m < 4; m++)
        {
            // mandible m: its own frame hinged on the rim, +Y = outward, +Z = along the hook
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(m * Mth.HALF_PI - Mth.PI / 4));
            if (detail)
            {
                // ember "eyes" on the collar between the mandibles
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotation(Mth.PI / 4));
                mesh.pose(pose);
                mesh.quad(new float[]{-1.3F, 9.55F, 4.6F, -1.3F, 9.55F, 1.4F, 1.3F, 9.55F, 1.4F, 1.3F, 9.55F, 4.6F}, BossMesh.EYE);
                pose.popPose();
            }
            pose.translate(0, 8.3F, 6.0F);
            pose.mulPose(Axis.XP.rotation(tilt + 0.04F * Mth.sin(time * 0.11F + m)));
            mesh.pose(pose);
            mesh.loft(MANDIBLE, 8, BossMesh.SKIN2);
            if (detail && !glow)
                for (int t = 0; t < 4; t++) mesh.spike(0, -0.9F, 3.0F + t * 3.4F, 0, -1.9F + t * 0.2F, 0.5F, 0.45F, 4, BossMesh.TOOTH);
            pose.popPose();
        }
        pose.popPose();
        mesh.pose(pose);
    }

    /** A ring of teeth on the gullet wall at z: bases on radius, pointing inward by {@code in} and along Z by {@code along}. */
    private static void ring(BossMesh mesh, int count, float radius, float z, float in, float along, float thick, float phase)
    {
        for (int i = 0; i < count; i++)
        {
            float a = Mth.TWO_PI * i / count + phase, c = Mth.cos(a), s = Mth.sin(a);
            mesh.spike(c * radius, s * radius, z, -c * in, -s * in, along, thick, 4, BossMesh.TOOTH);
        }
    }
}
