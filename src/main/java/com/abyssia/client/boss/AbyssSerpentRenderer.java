package com.abyssia.client.boss;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.entity.boss.AbyssSerpent;
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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * BOSS03: the abyss serpent as a leviathan, one smooth skinned body along the chain (see {@link BossMesh}): a wedge
 * skull with hinged jaws, rows of teeth, swept horns and glowing eyes; a dorsal sail that ripples down the back into
 * a two-lobed tail fluke; two pairs of paddling pectoral fins; photophores along the lateral line (glow pass).
 * Head parts are modelled for a body radius of 9 and scaled to the entity's. Rendering goes by the chain, not by the
 * entity's own position, so the culling box is the whole chain.
 */
public class AbyssSerpentRenderer extends EntityRenderer<AbyssSerpent>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(Abyssia.MODID, "textures/entity/abyss_serpent.png");
    private static final ResourceLocation GLOW = new ResourceLocation(Abyssia.MODID, "textures/entity/abyss_serpent_glow.png");
    private static final int TAIL_JOINTS = 4;
    private static final float HINGE_Y = -1.5F, HINGE_Z = -3.0F;

    /** Loft stations {z, half width, up, down, y offset, exponent}; +Z = out of the snout. */
    private static final float[][] SKULL = {
            {-16, 5.5F, 6.5F, 2.5F, 0, 0.9F}, {-10, 8.0F, 9.0F, 3.0F, 0, 0.9F}, {-4, 10.5F, 10.0F, 2.5F, 0, 0.85F}, {4, 12.0F, 9.0F, 2.0F, 0, 0.8F},
            {12, 10.5F, 7.0F, 1.8F, 0, 0.8F}, {20, 8.0F, 5.0F, 1.5F, 0, 0.8F}, {27, 5.5F, 3.6F, 1.2F, 0, 0.8F},
            {31, 3.0F, 2.2F, 0.8F, 0, 0.9F}, {32.5F, 0.3F, 0.3F, 0.2F, 0, 1}};
    private static final float[][] JAW_LOFT = {
            {-14, 5.0F, 0.8F, 5.5F, -1.5F, 0.9F}, {-8, 7.5F, 1.0F, 7.5F, -1.5F, 0.9F}, {0, 10.0F, 1.2F, 7.0F, -1.5F, 0.85F}, {10, 9.5F, 1.2F, 5.5F, -1.5F, 0.8F},
            {20, 7.0F, 1.0F, 3.8F, -1.5F, 0.8F}, {27, 4.5F, 0.8F, 2.4F, -1.5F, 0.8F}, {30.5F, 2.2F, 0.6F, 1.2F, -1.5F, 0.9F},
            {31.5F, 0.2F, 0.2F, 0.2F, -1.5F, 1}};
    private static final float[][] TONGUE = {{0, 3.0F, 1.4F, 0.4F, -0.6F}, {10, 3.4F, 1.6F, 0.4F, -0.6F}, {20, 2.0F, 1.0F, 0.3F, -0.6F}, {25, 0.2F, 0.2F, 0.1F, -0.6F}};

    /** smoothed jaw opening per serpent (client only) */
    private static final Map<AbyssSerpent, float[]> JAW = new WeakHashMap<>();

    public AbyssSerpentRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssSerpent entity)
    {
        return TEXTURE;
    }

    /** Cross-section scale at joint coordinate k: 1.0 at the neck to 0.55 before the tail, which thins out to a point. */
    private static float bodyScale(float k, int n)
    {
        if (k <= 1) return 1.0F;
        float s = 1.0F - 0.45F * (k - 1) / Math.max(1, n - 2);
        float t = k - (n - 1 - TAIL_JOINTS);
        if (t > 0) s *= Math.max(0.04F, 1.0F - 0.24F * t);
        return s;
    }

    /** Half width of a loft at z (linear between its stations). */
    private static float widthAt(float[][] st, float z)
    {
        for (int i = 1; i < st.length; i++)
            if (z <= st[i][0]) return Mth.lerp(Mth.clamp((z - st[i - 1][0]) / (st[i][0] - st[i - 1][0]), 0, 1), st[i - 1][1], st[i][1]);
        return st[st.length - 1][1];
    }

    @Override
    public void render(AbyssSerpent entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int packedLight)
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
        // LOD: fewer rings and sides once the whole animal is far off
        int sub = !lod || near < 160 ? 4 : near < 320 ? 2 : 1;
        int sides = !lod || near < 160 ? 14 : near < 320 ? 10 : 6;
        boolean detail = !lod || near < 220;

        // jaw: no synced AI state, so the head speed stands in (the charge opens it, the stop on a bite snaps it shut)
        double hs = Math.sqrt(sq(chain.lerpX(0, 1) - chain.lerpX(0, 0)) + sq(chain.lerpY(0, 1) - chain.lerpY(0, 0)) + sq(chain.lerpZ(0, 1) - chain.lerpZ(0, 0)));
        float target = Mth.clamp((float) ((hs / AbyssSerpent.SPEED - 1.05) / 0.12), 0.0F, 1.0F);
        float[] jawState = JAW.computeIfAbsent(entity, e -> new float[1]);
        jawState[0] += (target - jawState[0]) * 0.2F;
        float time = entity.tickCount + pt;
        float jaw = Math.max(jawState[0], 0.10F + 0.07F * Mth.sin(time * 0.08F));

        BossMesh.Path path = BossMesh.spline(jx, jy, jz, 0, n - 1, sub, ex, ey, ez, 0.35F);
        float r = (float) entity.bodyRadius();
        model(new BossMesh(buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), pose, lights[0]), pose, path, lights, n, r, sides, detail, false,
                jaw, time, jx, jy, jz, ex, ey, ez);
        // glow pass: the same surfaces with the glow texture, additive (a BufferSource ends the previous batch when the type changes)
        model(new BossMesh(buffer.getBuffer(RenderType.eyes(GLOW)), pose, LightTexture.FULL_BRIGHT), pose, path, null, n, r, sides, detail, true,
                jaw, time, jx, jy, jz, ex, ey, ez);
    }

    private static double sq(double d)
    {
        return d * d;
    }

    private void model(BossMesh mesh, PoseStack pose, BossMesh.Path path, int[] lights, int n, float r, int sides, boolean detail, boolean glow,
                       float jaw, float time, double[] jx, double[] jy, double[] jz, double ex, double ey, double ez)
    {
        // body: taller than wide, the belly a little flatter
        mesh.tube(path, sides, (k, o) -> {
            float s = bodyScale(k, n) * r;
            o[0] = 0.88F * s; o[1] = 1.08F * s; o[2] = 0.95F * s; o[3] = 0.9F;
        }, BossMesh.SKIN, 1, lights, true, true);

        // dorsal sail: rises behind the skull, ripples down the back, swells into the upper tail lobe
        mesh.ribbon(path, (k, o) -> {
            float s = bodyScale(k, n) * r;
            float rise = Mth.clamp((k - 0.8F) / 2.0F, 0, 1);
            float tail = Mth.clamp((k - (n - 1 - TAIL_JOINTS)) / TAIL_JOINTS, 0, 1);
            float h = rise * (1.15F + 0.3F * Mth.sin(k * 1.7F)) * r * (0.45F + 0.55F * bodyScale(k, n));
            h = Mth.lerp(tail, h, r * 1.7F * Mth.sin(Mth.PI * Math.min(tail * 1.05F, 1)));
            o[0] = 1.0F * s; o[1] = h; o[2] = Mth.sin(time * 0.09F - k * 0.9F) * 0.22F * h;
        }, 1, BossMesh.MEMBRANE, 1, lights);
        // lower tail lobe and a low keel under the hind body
        mesh.ribbon(path, (k, o) -> {
            float s = bodyScale(k, n) * r;
            float tail = Mth.clamp((k - (n - 1 - TAIL_JOINTS)) / TAIL_JOINTS, 0, 1);
            float keel = Mth.clamp((k - n * 0.55F) / 3.0F, 0, 1) * 0.35F * r;
            float h = Mth.lerp(tail, keel, r * 1.25F * Mth.sin(Mth.PI * Math.min(tail * 1.05F, 1)));
            o[0] = 0.88F * s; o[1] = h; o[2] = Mth.sin(time * 0.09F - k * 0.9F + 1.0F) * 0.22F * h;
        }, -1, BossMesh.MEMBRANE, 1, lights);

        if (detail) for (int pair = 0; pair < 2; pair++) pectorals(mesh, path, lights, n, r, pair, time);

        // head, in a frame at joint 0 looking along the neck (+Z out of the snout), modelled for a body radius of 9
        pose.pushPose();
        pose.translate(jx[0] - ex, jy[0] - ey, jz[0] - ez);
        double dx = jx[0] - jx[1], dy = jy[0] - jy[1], dz = jz[0] - jz[1], len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0E-6)
        {
            pose.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
            pose.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(dy / len, -1.0, 1.0))));
        }
        pose.scale(r / 9.0F, r / 9.0F, r / 9.0F);
        if (lights != null) mesh.light(lights[0]);

        pose.pushPose();
        hinge(pose, -0.20F * jaw);
        mesh.pose(pose);
        mesh.loft(SKULL, sides, BossMesh.HEAD);
        for (int side = -1; side <= 1; side += 2)
        {
            // eyes: glowing discs standing just proud of the cheek, under a brow horn
            float x = side * 10.25F;
            mesh.quad(new float[]{x, 5.9F, 10.8F, x, 3.1F, 10.8F, x, 3.1F, 7.2F, x, 5.9F, 7.2F}, BossMesh.EYE);
            if (!detail || glow) continue;
            mesh.spike(side * 9.0F, 6.5F, 6.0F, side * 2.5F, 3.0F, -9.0F, 1.3F, 5, BossMesh.HORN);
            mesh.spike(side * 6.5F, 8.5F, -3.0F, side * 4.0F, 6.5F, -15.0F, 1.9F, 6, BossMesh.HORN);
            mesh.spike(side * 9.8F, 3.0F, -5.0F, side * 6.5F, 1.0F, -13.0F, 1.6F, 6, BossMesh.HORN);
            mesh.spike(side * 8.5F, -1.0F, -6.0F, side * 4.5F, -3.5F, -9.0F, 1.2F, 5, BossMesh.HORN);
            for (int i = 0; i < 9; i++)
            {
                float z = 5.0F + i * 2.9F;
                float l = 2.6F + (i % 2) * 0.9F + (i >= 6 ? 1.4F : 0);
                mesh.spike(side * (widthAt(SKULL, z) * 0.84F), -1.2F, z, side * 0.2F, -l, 0.5F, 0.6F, 4, BossMesh.TOOTH);
            }
            mesh.spike(side * 1.9F, -0.8F, 29.5F, 0, -5.6F, 0.8F, 0.75F, 4, BossMesh.TOOTH);   // fangs
        }
        if (detail && !glow) mesh.spike(0, 3.2F, 26.5F, 0, 4.2F, -2.5F, 1.2F, 5, BossMesh.HORN);   // nose horn
        pose.popPose();

        pose.pushPose();
        hinge(pose, 0.62F * jaw);
        mesh.pose(pose);
        mesh.loft(JAW_LOFT, sides, BossMesh.SKIN2);
        if (detail && !glow)
        {
            mesh.loft(TONGUE, 6, BossMesh.FLESH);
            for (int side = -1; side <= 1; side += 2)
            {
                for (int i = 0; i < 9; i++)
                {
                    float z = 6.4F + i * 2.7F;
                    float l = 2.2F + ((i + 1) % 2) * 0.8F + (i >= 7 ? 1.2F : 0);
                    mesh.spike(side * (widthAt(JAW_LOFT, z) * 0.82F), -0.5F, z, side * 0.2F, l, 0.4F, 0.55F, 4, BossMesh.TOOTH);
                }
                // chin barbs
                mesh.spike(side * 4.0F, -6.5F, 6.0F, side * 1.5F, -5.5F, -5.0F, 0.9F, 5, BossMesh.HORN);
            }
        }
        pose.popPose();
        pose.popPose();
        mesh.pose(pose);
    }

    /** One pair of pectoral fins on the flanks (pair 0 behind the head, pair 1 smaller, a third of the way down), paddling. */
    private static void pectorals(BossMesh mesh, BossMesh.Path p, int[] lights, int n, float r, int pair, float time)
    {
        float k = pair == 0 ? 1.7F : n * 0.33F;
        int i = 0;
        while (i < p.n - 1 && p.k[i] < k) i++;
        float s = bodyScale(k, n) * r, size = (pair == 0 ? 1.0F : 0.7F) * r;
        if (lights != null) mesh.light(lights[Mth.clamp(Math.round(k), 0, n - 1)]);
        for (int side = -1; side <= 1; side += 2)
        {
            float flap = 0.55F + 0.35F * Mth.sin(time * 0.07F + pair * 1.6F);
            // outward and down, by the flap angle
            float ox = p.rx[i] * side * Mth.cos(flap) - p.ux[i] * Mth.sin(flap);
            float oy = p.ry[i] * side * Mth.cos(flap) - p.uy[i] * Mth.sin(flap);
            float oz = p.rz[i] * side * Mth.cos(flap) - p.uz[i] * Mth.sin(flap);
            float bx = p.x[i] + p.rx[i] * side * 0.8F * s - p.ux[i] * 0.35F * s;
            float by = p.y[i] + p.ry[i] * side * 0.8F * s - p.uy[i] * 0.35F * s;
            float bz = p.z[i] + p.rz[i] * side * 0.8F * s - p.uz[i] * 0.35F * s;
            float chord = 1.5F * size, span = 2.1F * size;
            mesh.quad(new float[]{
                    bx, by, bz,
                    bx + ox * span * 0.75F + p.tx[i] * chord * 0.5F, by + oy * span * 0.75F + p.ty[i] * chord * 0.5F, bz + oz * span * 0.75F + p.tz[i] * chord * 0.5F,
                    bx + ox * span + p.tx[i] * chord * 1.5F, by + oy * span + p.ty[i] * chord * 1.5F, bz + oz * span + p.tz[i] * chord * 1.5F,
                    bx + p.tx[i] * chord, by + p.ty[i] * chord, bz + p.tz[i] * chord}, BossMesh.MEMBRANE);
        }
    }

    private static void hinge(PoseStack pose, float angle)
    {
        pose.translate(0, HINGE_Y, HINGE_Z);
        pose.mulPose(Axis.XP.rotation(angle));
        pose.translate(0, -HINGE_Y, -HINGE_Z);
    }
}
