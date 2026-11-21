package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssMother;
import com.abyssia.entity.boss.AbyssMotherSpawn;
import com.abyssia.entity.boss.BossChain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * BOSS03: the abyss mother as a colossal jellyfish (see {@link BossMesh}): a translucent domed bell that contracts on
 * every beat and spreads wide in bloom, a frilled margin rippling round its rim, the glowing heart and gonads seen
 * through it, five frilled oral arms trailing from the centre and twelve thin tentacles skinned along their chains.
 * Bell space: origin at the spine's first joint, the rim at y = -6, the apex at y = +8, the heart at y = -4.
 */
public class AbyssMotherRenderer extends EntityRenderer<AbyssMother>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(Abyssia.MODID, "textures/entity/abyss_mother.png");
    private static final ResourceLocation GLOW = new ResourceLocation(Abyssia.MODID, "textures/entity/abyss_mother_glow.png");
    private static final ResourceLocation WHITE = new ResourceLocation("textures/misc/white.png");
    private static final float CORE_Y = -4.0F, BELL_ALPHA = 0.62F;
    private static final float SPAWN_SCALE = 1.0F / 30.0F;
    private static final int ORAL_ARMS = 5, ORAL_POINTS = 10;

    /** Dome profiles {depth below the origin, radius}: lofts run along +Z, turned to point down when drawn. */
    private static final float[][] BELL = {{-8.0F, 0.3F}, {-7.6F, 5.0F}, {-6.4F, 10.0F}, {-4.0F, 14.2F}, {-0.5F, 17.0F}, {3.0F, 18.2F}, {5.2F, 18.0F}, {6.4F, 16.9F}};
    private static final float[][] UNDER = {{-3.6F, 0.5F}, {-3.0F, 6.0F}, {-1.0F, 11.0F}, {2.0F, 14.5F}, {5.0F, 16.2F}, {6.3F, 16.7F}};
    private static final float[][] STALK = {{3.0F, 2.4F, 2.4F, 2.4F}, {6.0F, 1.9F, 1.9F, 1.9F}, {9.0F, 1.5F, 1.5F, 1.5F}, {11.0F, 0.6F, 0.6F, 0.6F}};

    /** smoothed bloom openness per mother (client only): {value, last time} */
    private final Map<AbyssMother, float[]> opens = new WeakHashMap<>();

    public AbyssMotherRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssMother entity)
    {
        return TEXTURE;
    }

    private float openness(AbyssMother entity, float time)
    {
        float target = entity.bloom() ? 1.0F : 0.0F;
        float[] s = opens.computeIfAbsent(entity, e -> new float[]{target, time});
        float dt = Mth.clamp(time - s[1], 0.0F, 20.0F);
        s[1] = time;
        s[0] += (target - s[0]) * Math.min(1.0F, dt * 0.06F);
        return s[0] * s[0] * (3.0F - 2.0F * s[0]);
    }

    @Override
    public void render(AbyssMother entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        BossChain spine = entity.chain();
        if (!spine.isInitialised()) return;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition();
        boolean lod = ClientConfig.LOD_ENABLED.get();
        if (lod && BossFarRenderer.nearestPoint(entity.farPoints(pt), cp) > BossFarRenderer.fogEnd()) return;
        double ex = Mth.lerp(pt, entity.xo, entity.getX()), ey = Mth.lerp(pt, entity.yo, entity.getY()), ez = Mth.lerp(pt, entity.zo, entity.getZ());
        double hx = spine.lerpX(0, pt), hy = spine.lerpY(0, pt), hz = spine.lerpZ(0, pt);
        double ox = hx - ex, oy = hy - ey, oz = hz - ez;
        float time = entity.tickCount + pt;
        float beat = entity.beat(pt);
        float open = openness(entity, time);
        double near = Math.sqrt(cp.distanceToSqr(hx, hy, hz));
        int sub = !lod || near < 110 ? 2 : 1;
        int sides = !lod || near < 160 ? 24 : 14;
        boolean detail = !lod || near < 200;

        // tentacles: one smooth path each, backs turned away from the bell's axis
        int count = AbyssMother.TENTACLES, joints = AbyssMother.JOINTS;
        BossMesh.Path[] paths = new BossMesh.Path[count];
        int[][] lights = new int[count][];
        double[] jx = new double[joints], jy = new double[joints], jz = new double[joints];
        for (int t = 0; t < count; t++)
        {
            BossChain c = entity.tentacle(t);
            if (!c.isInitialised()) continue;
            lights[t] = new int[joints];
            for (int i = 0; i < joints; i++)
            {
                jx[i] = c.lerpX(i, pt);
                jy[i] = c.lerpY(i, pt);
                jz[i] = c.lerpZ(i, pt);
                // light changes slowly down a thin strand: sample every other joint
                lights[t][i] = (i & 1) == 0 || i == joints - 1 ? LevelRenderer.getLightColor(entity.level(), BlockPos.containing(jx[i], jy[i], jz[i])) : lights[t][i - 1];
            }
            float rx = (float) (jx[0] - hx), rz = (float) (jz[0] - hz), rl = Mth.sqrt(rx * rx + rz * rz);
            if (rl < 1.0E-3F) { rx = 1; rz = 0; rl = 1; }
            paths[t] = BossMesh.spline(jx, jy, jz, 0, joints - 1, sub, ex, ey, ez, 0, rx / rl, 0, rz / rl);
        }
        int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(hx, hy, hz));
        float cr = Mth.lerp(open, 3.4F, 4.6F) * (1.0F + 0.05F * beat);

        // 1: everything inside and under the bell
        BossMesh solid = new BossMesh(buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), pose, light);
        tentacles(solid, paths, lights);
        pose.pushPose();
        pose.translate(ox, oy, oz);
        organs(solid, pose, time, beat, open, cr, light, sides, detail);
        pose.popPose();
        // 2: the bell over them, translucent (a BufferSource ends the previous batch when the type changes)
        BossMesh skin = new BossMesh(buffer.getBuffer(RenderType.entityTranslucent(TEXTURE)), pose, light).color(1, 1, 1, BELL_ALPHA);
        pose.pushPose();
        pose.translate(ox, oy, oz);
        under(skin, pose, beat, open, sides);
        bell(skin, pose, time, beat, open, sides);
        pose.popPose();
        // 3: glow pass, the same surfaces with the glow texture, additive
        BossMesh glow = new BossMesh(buffer.getBuffer(RenderType.eyes(GLOW)), pose, LightTexture.FULL_BRIGHT);
        tentacles(glow, paths, null);
        pose.pushPose();
        pose.translate(ox, oy, oz);
        organs(glow, pose, time, beat, open, cr, LightTexture.FULL_BRIGHT, sides, detail);
        under(glow, pose, beat, open, sides);
        bell(glow, pose, time, beat, open, sides);
        pose.popPose();

        // 4: halo round the heart, and beads of light round the rim while the pulse charges
        VertexConsumer gc = buffer.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PoseStack.Pose last = pose.last();
        BossMesh.billboard(gc, last, cam, (float) ox, (float) oy + CORE_Y, (float) oz, cr * 2.2F, 1.0F, 0.35F, 0.85F, 0.14F + 0.22F * open + 0.06F * beat);
        if (entity.warning())
        {
            float a = 0.4F + 0.3F * Mth.sin(time * 0.6F);
            for (int i = 0; i < 16; i++)
            {
                double ang = i * (Math.PI * 2 / 16);
                BossMesh.billboard(gc, last, cam, (float) (ox + Math.cos(ang) * 18.5), (float) oy - 6.0F, (float) (oz + Math.sin(ang) * 18.5), 1.5F, 0.8F, 1.0F, 1.0F, a);
            }
        }
    }

    private static void tentacles(BossMesh mesh, BossMesh.Path[] paths, int[][] lights)
    {
        for (int t = 0; t < paths.length; t++)
        {
            if (paths[t] == null) continue;
            mesh.tube(paths[t], 6, (k, o) -> {
                float r = Mth.lerp(k / (AbyssMother.JOINTS - 1), 0.6F, 0.14F);
                o[0] = r; o[1] = r; o[2] = r; o[3] = 1;
            }, BossMesh.SKIN2, 1, lights == null ? null : lights[t], false, true);
        }
    }

    /** A dome profile as loft stations, squeezed by the beat (the margin pulls in) and spread by the bloom (wider, flatter). */
    private static float[][] dome(float[][] profile, float beat, float open)
    {
        float[][] st = new float[profile.length][];
        for (int i = 0; i < profile.length; i++)
        {
            float f = i / (float) (profile.length - 1);
            float r = profile[i][1] * (1.0F - 0.08F * Math.max(0, beat) * f * f + 0.17F * open * f);
            float z = profile[i][0] * (1.0F - 0.16F * open) + 0.7F * Math.max(0, beat) * f * f;
            st[i] = new float[]{z, r, r, r};
        }
        return st;
    }

    /** The bell and its frilled margin, in bell space. */
    private static void bell(BossMesh mesh, PoseStack pose, float time, float beat, float open, int sides)
    {
        float[][] st = dome(BELL, beat, open);
        pose.pushPose();
        pose.mulPose(Axis.XP.rotation(Mth.HALF_PI));   // loft +Z = down
        mesh.pose(pose);
        mesh.loft(st, sides, BossMesh.HEAD);
        pose.popPose();
        mesh.pose(pose);
        // margin: a frill hanging from the rim, a wave running round it, flaring out as the bell opens
        float[] rim = st[st.length - 1];
        mesh.ribbon(BossMesh.circle(rim[1], -rim[0], sides * 2, 16), (k, o) -> {
            o[0] = 0; o[1] = 3.4F + 0.5F * Mth.sin(k * 5.1F);
            o[2] = 1.1F * Mth.sin(time * 0.12F + k * 2.4F) + 2.6F * open - 0.9F * beat;
        }, 1, BossMesh.MEMBRANE, 1, null);
    }

    /** The under-bell (translucent like the bell, so the heart shows through both), in bell space. */
    private static void under(BossMesh mesh, PoseStack pose, float beat, float open, int sides)
    {
        pose.pushPose();
        pose.mulPose(Axis.XP.rotation(Mth.HALF_PI));   // loft +Z = down
        mesh.pose(pose);
        mesh.loft(dome(UNDER, beat, open), sides, BossMesh.INNER);
        pose.popPose();
        mesh.pose(pose);
    }

    /** Stalk, heart and oral arms, in bell space. */
    private static void organs(BossMesh mesh, PoseStack pose, float time, float beat, float open, float cr, int light, int sides, boolean detail)
    {
        pose.pushPose();
        pose.mulPose(Axis.XP.rotation(Mth.HALF_PI));   // loft +Z = down
        mesh.pose(pose);
        mesh.loft(STALK, 8, BossMesh.SKIN2);
        // the abyss heart: full bright, so it shows through the bell
        pose.translate(0, 0, -CORE_Y);
        mesh.pose(pose).light(LightTexture.FULL_BRIGHT);
        mesh.loft(BossMesh.ellipsoid(-cr, 2 * cr, cr, cr, 8), 10, BossMesh.GLOW);
        mesh.light(light);
        pose.popPose();
        mesh.pose(pose);
        if (!detail) return;

        float[] xs = new float[ORAL_POINTS], ys = new float[ORAL_POINTS], zs = new float[ORAL_POINTS];
        for (int arm = 0; arm < ORAL_ARMS; arm++)
        {
            float a = arm * Mth.TWO_PI / ORAL_ARMS + 0.3F, ca = Mth.cos(a), sa = Mth.sin(a);
            for (int j = 0; j < ORAL_POINTS; j++)
            {
                float f = j / (float) (ORAL_POINTS - 1), rad = 2.2F + (3.5F + 5.0F * open) * f;
                xs[j] = ca * rad + Mth.sin(time * 0.045F + j * 0.55F + arm * 1.7F) * 2.4F * f;
                ys[j] = -6.0F - 30.0F * f * (1.0F - 0.25F * open);
                zs[j] = sa * rad + Mth.cos(time * 0.04F + j * 0.5F + arm) * 2.4F * f;
            }
            BossMesh.Path p = BossMesh.through(xs, ys, zs, ca, 0, sa);
            int phase = arm;
            mesh.tube(p, 6, (k, o) -> {
                float r = 0.75F * (1.0F - 0.6F * k / (ORAL_POINTS - 1));
                o[0] = r; o[1] = r; o[2] = r; o[3] = 1;
            }, BossMesh.SKIN2, 1, null, false, true);
            // frills down both edges, rippling
            for (int side = -1; side <= 1; side += 2)
            {
                int sgn = side;
                mesh.ribbon(p, (k, o) -> {
                    float f = k / (ORAL_POINTS - 1);
                    o[0] = 0.3F; o[1] = 3.8F * (1.0F - 0.55F * f) + 0.4F;
                    o[2] = sgn * 1.5F * (0.4F + f) * Mth.sin(time * 0.13F + k * 1.9F + phase);
                }, side, BossMesh.MEMBRANE, 1, null);
            }
        }
    }

    /**
     * Brood jelly: the mother at 1/30 scale (about 1.2 across, the hitbox), always closed, beating faster, with eight
     * short swaying tentacles; the same textures and glow pass.
     */
    public static class Spawn extends EntityRenderer<AbyssMotherSpawn>
    {
        public Spawn(EntityRendererProvider.Context context)
        {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(AbyssMotherSpawn entity)
        {
            return TEXTURE;
        }

        @Override
        public void render(AbyssMotherSpawn entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
        {
            float time = entity.tickCount + pt + entity.getId() * 7;
            float beat = Mth.sin(time * (Mth.TWO_PI / 24.0F));
            pose.pushPose();
            pose.translate(0, 0.85, 0);
            pose.scale(SPAWN_SCALE, SPAWN_SCALE, SPAWN_SCALE);
            BossMesh.Path[] strands = new BossMesh.Path[8];
            float[] xs = new float[5], ys = new float[5], zs = new float[5];
            for (int t = 0; t < strands.length; t++)
            {
                float a = t * Mth.PI / 4 + Mth.PI / 8, ca = Mth.cos(a), sa = Mth.sin(a);
                for (int j = 0; j < 5; j++)
                {
                    float sway = Mth.sin(time * 0.15F + t + j * 0.9F) * 1.6F * j;
                    xs[j] = ca * (14.0F - j * 0.6F) - sa * sway;
                    ys[j] = -5.0F - j * 6.0F;
                    zs[j] = sa * (14.0F - j * 0.6F) + ca * sway;
                }
                strands[t] = BossMesh.through(xs, ys, zs, ca, 0, sa);
            }
            BossMesh solid = new BossMesh(buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), pose, packedLight);
            strands(solid, strands);
            organs(solid, pose, time, beat, 0.0F, 3.4F, packedLight, 12, false);
            BossMesh skin = new BossMesh(buffer.getBuffer(RenderType.entityTranslucent(TEXTURE)), pose, packedLight).color(1, 1, 1, BELL_ALPHA);
            under(skin, pose, beat, 0.0F, 12);
            bell(skin, pose, time, beat, 0.0F, 12);
            BossMesh glow = new BossMesh(buffer.getBuffer(RenderType.eyes(GLOW)), pose, LightTexture.FULL_BRIGHT);
            strands(glow, strands);
            bell(glow, pose, time, beat, 0.0F, 12);
            pose.popPose();
        }

        private static void strands(BossMesh mesh, BossMesh.Path[] strands)
        {
            for (BossMesh.Path p : strands)
                mesh.tube(p, 5, (k, o) -> {
                    float r = Mth.lerp(k / 4.0F, 1.3F, 0.4F);
                    o[0] = r; o[1] = r; o[2] = r; o[3] = 1;
                }, BossMesh.SKIN2, 1, null, false, true);
        }
    }
}
