package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssKraken;
import com.abyssia.entity.boss.BossChain;
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

/**
 * BOSS03: the abyss kraken as a colossal squid (see {@link BossMesh}): a torpedo mantle that breathes, flared at its
 * collar over a round head with two great eyes and a chewing beak, two flapping tail fins, and eight arms skinned
 * smoothly along their chains, sucker side turned inward, swelling into hooked clubs. A severed arm ends in a stump
 * of raw flesh. Mantle space: origin at the spine's first joint, +Y toward the mantle tip.
 */
public class AbyssKrakenRenderer extends EntityRenderer<AbyssKraken>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/abyss_kraken.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/abyss_kraken_glow.png");
    /** the club swells over the last joints */
    private static final int CLUB_START = AbyssKraken.JOINTS - 5;

    /** Loft stations {height, radius...}: lofts run along +Z, turned upright when drawn. */
    private static final float[][] HEAD = {{-8.3F, 1.5F, 1.5F, 1.5F}, {-7.6F, 3.4F, 3.4F, 3.4F}, {-6.0F, 4.6F, 4.6F, 4.6F}, {-4.0F, 5.0F, 5.0F, 5.0F},
            {-2.4F, 4.4F, 4.4F, 4.4F}, {-0.8F, 4.2F, 4.2F, 4.2F}};
    private static final float[][] MANTLE = {{-2.6F, 5.0F, 5.0F, 5.0F}, {-1.6F, 6.4F, 6.4F, 6.4F}, {1.5F, 6.8F, 6.8F, 6.8F}, {5.0F, 6.4F, 6.4F, 6.4F},
            {9.0F, 5.1F, 5.1F, 5.1F}, {12.0F, 3.5F, 3.5F, 3.5F}, {14.5F, 1.7F, 1.7F, 1.7F}, {15.9F, 0.15F, 0.15F, 0.15F}};

    /** A tail fin: a thin diamond lobe; its "up" is the span out from the mantle axis. */
    private static final float[][] FIN = {{6.0F, 0.3F, 1.0F, 0.2F}, {7.5F, 0.4F, 8.6F, 0.2F}, {10.0F, 0.4F, 10.8F, 0.2F}, {13.0F, 0.35F, 7.6F, 0.2F},
            {15.2F, 0.2F, 1.0F, 0.2F}};

    public AbyssKrakenRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssKraken entity)
    {
        return TEXTURE;
    }

    /** Arm radius at joint coordinate k: thick at the root, the club swelling before the pointed tip. */
    private static float armRadius(float k)
    {
        float w = Mth.lerp(k / (AbyssKraken.JOINTS - 1), 3.6F, 1.1F);
        float c = k - CLUB_START;
        if (c > 0) w += 0.9F * Mth.sin(Mth.PI * Math.min(c / 4.6F, 1.0F));
        float tip = AbyssKraken.JOINTS - 1 - k;
        if (tip < 1.2F) w *= Math.max(0.12F, tip / 1.2F);
        return w * 0.5F;
    }

    @Override
    public void render(AbyssKraken entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain spine = entity.chain();
        if (!spine.isInitialised()) return;
        Vec3 cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean lod = ClientConfig.LOD_ENABLED.get();
        if (lod && BossFarRenderer.nearestPoint(entity.farPoints(pt), cp) > BossFarRenderer.fogEnd()) return;
        double ex = Mth.lerp(pt, entity.xo, entity.getX()), ey = Mth.lerp(pt, entity.yo, entity.getY()), ez = Mth.lerp(pt, entity.zo, entity.getZ());
        double hx = spine.lerpX(0, pt), hy = spine.lerpY(0, pt), hz = spine.lerpZ(0, pt);
        float time = entity.tickCount + pt;
        double near = Math.sqrt(cp.distanceToSqr(hx, hy, hz));
        int sub = !lod || near < 110 ? 3 : near < 240 ? 2 : 1;
        int sides = !lod || near < 110 ? 8 : 6;
        boolean detail = !lod || near < 180;

        // arms: one smooth path each, the sucker face (the wrap's belly) turned toward the mantle axis
        int arms = AbyssKraken.TENTACLES, joints = AbyssKraken.JOINTS;
        BossMesh.Path[] paths = new BossMesh.Path[arms];
        int[][] lights = new int[arms][];
        double[] jx = new double[joints], jy = new double[joints], jz = new double[joints];
        for (int t = 0; t < arms; t++)
        {
            BossChain c = entity.tentacle(t);
            int live = entity.liveJoints(t);
            if (!c.isInitialised() || live < 2) continue;
            lights[t] = new int[live];
            for (int i = 0; i < live; i++)
            {
                jx[i] = c.lerpX(i, pt);
                jy[i] = c.lerpY(i, pt);
                jz[i] = c.lerpZ(i, pt);
                lights[t][i] = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(jx[i], jy[i], jz[i]));
            }
            float ox = (float) (jx[0] - hx), oz = (float) (jz[0] - hz), ol = Mth.sqrt(ox * ox + oz * oz);
            if (ol < 1.0E-3F) { ox = 1; oz = 0; ol = 1; }
            paths[t] = BossMesh.spline(jx, jy, jz, 0, live - 1, sub, ex, ey, ez, 0, ox / ol, 0, oz / ol);
        }
        int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(hx, hy, hz));
        model(new BossMesh(buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), pose, light), pose, entity, paths, lights, false, sides, detail, time,
                hx - ex, hy - ey, hz - ez);
        // glow pass: the same surfaces with the glow texture, additive (a BufferSource ends the previous batch when the type changes)
        model(new BossMesh(buffer.getBuffer(RenderType.eyes(GLOW)), pose, LightTexture.FULL_BRIGHT), pose, entity, paths, null, true, sides, detail, time,
                hx - ex, hy - ey, hz - ez);
    }

    private void model(BossMesh mesh, PoseStack pose, AbyssKraken entity, BossMesh.Path[] paths, int[][] lights, boolean glow, int sides, boolean detail,
                       float time, double cx, double cy, double cz)
    {
        for (int t = 0; t < paths.length; t++)
        {
            BossMesh.Path p = paths[t];
            if (p == null) continue;
            boolean severed = entity.severed(t);
            int[] l = lights == null ? null : lights[t];
            mesh.tube(p, sides, (k, o) -> {
                // a stump keeps the root's thickness to its cut end
                float r = severed ? Mth.lerp(k / (AbyssKraken.JOINTS - 1), 1.8F, 0.55F) : armRadius(k);
                o[0] = r * 1.08F; o[1] = r; o[2] = r * 0.92F; o[3] = 1;
            }, BossMesh.SKIN2, 1, l, false, !severed);
            if (glow || !detail) continue;
            int e = p.n - 1;
            if (l != null) mesh.light(l[l.length - 1]);
            if (severed)
            {
                // raw flesh closing the cut
                float r = Mth.lerp(p.k[e] / (AbyssKraken.JOINTS - 1), 1.8F, 0.55F) * 1.05F;
                mesh.spike(p.x[e], p.y[e], p.z[e], p.tx[e] * 0.7F, p.ty[e] * 0.7F, p.tz[e] * 0.7F, r, 8, BossMesh.FLESH);
                continue;
            }
            // hooks on the club's sucker face, two rows
            for (int i = 0; i < p.n; i++)
            {
                if (p.k[i] < CLUB_START + 0.4F || p.k[i] > AbyssKraken.JOINTS - 1.6F) continue;
                float r = armRadius(p.k[i]);
                for (int side = -1; side <= 1; side += 2)
                    mesh.spike(p.x[i] - p.ux[i] * r * 0.85F + p.rx[i] * side * r * 0.45F, p.y[i] - p.uy[i] * r * 0.85F + p.ry[i] * side * r * 0.45F,
                            p.z[i] - p.uz[i] * r * 0.85F + p.rz[i] * side * r * 0.45F,
                            -p.ux[i] * 0.9F - p.tx[i] * 0.35F, -p.uy[i] * 0.9F - p.ty[i] * 0.35F, -p.uz[i] * 0.9F - p.tz[i] * 0.35F, 0.22F, 4, BossMesh.TOOTH);
            }
        }

        pose.pushPose();
        pose.translate(cx, cy, cz);
        // the whole body sways a little, hanging in the water
        pose.mulPose(Axis.ZP.rotation(0.04F * Mth.sin(time * 0.031F)));
        pose.mulPose(Axis.XP.rotation(0.04F * Mth.cos(time * 0.027F)));
        if (lights != null) mesh.light(LevelRenderer.getLightColor(entity.level(), BlockPos.containing(entity.getX() + cx, entity.getY() + cy, entity.getZ() + cz)));

        pose.pushPose();
        pose.mulPose(Axis.XP.rotation(-Mth.HALF_PI));   // loft +Z = up
        mesh.pose(pose);
        mesh.loft(HEAD, sides * 2, BossMesh.HEAD);
        // mantle: breathing in and out round its axis
        float breath = 1.0F + 0.035F * Mth.sin(time * 0.1F);
        pose.scale(breath, breath, 1.0F);
        mesh.pose(pose);
        mesh.loft(MANTLE, sides * 2, BossMesh.SKIN);
        // tail fins: two lobes on the upper mantle, flapping round its axis
        float flap = 0.45F * Mth.sin(time * 0.12F);
        for (int side = -1; side <= 1; side += 2)
        {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(-side * Mth.HALF_PI + side * flap));
            mesh.pose(pose);
            mesh.loft(FIN, 8, BossMesh.HEAD);
            pose.popPose();
        }
        pose.popPose();
        mesh.pose(pose);

        for (int side = -1; side <= 1; side += 2)
        {
            // eyes: great discs standing just proud of the head
            float x = side * 5.12F;
            mesh.quad(new float[]{x, -2.4F, 1.9F, x, -6.0F, 1.9F, x, -6.0F, -1.9F, x, -2.4F, -1.9F}, BossMesh.EYE);
        }
        if (detail && !glow)
        {
            // beak: two dark mandibles chewing slowly under the head (no synced state)
            float open = 0.25F + 0.35F * (0.5F + 0.5F * Mth.sin(time * 0.09F));
            mesh.spike(0, -7.9F, 0.75F, 0, -2.7F, -0.75F + open, 1.15F, 6, BossMesh.HORN);
            mesh.spike(0, -7.9F, -0.75F, 0, -2.2F, 0.75F - open, 1.0F, 6, BossMesh.HORN);
            // siphon under the collar
            mesh.spike(0, -2.2F, 4.6F, 0, -3.4F, 2.2F, 1.2F, 6, BossMesh.FLESH);
        }
        pose.popPose();
        mesh.pose(pose);
    }
}
