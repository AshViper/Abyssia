package com.abyssia.client.particle;

import com.abyssia.client.ShaderCompat;
import com.abyssia.environment.CurrentData;
import com.abyssia.environment.NaturalCurrents;
import com.abyssia.environment.ParticleBudget;
import com.abyssia.environment.ParticleBudget.Budget;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Suspended matter carried by a natural current: a faint streak stretched along its own motion, so a stream reads as
 * fine particles sweeping one way rather than as glowing dots. Its speed follows the stream's local strength, with a
 * small sideways sway; it fades out once it leaves the stream or the water.
 */
public class CurrentParticle extends TextureSheetParticle
{
    private static final int SAMPLE_INTERVAL = 4;
    private static final int FADE_TICKS = 8;
    private static final int MIN_BLOCK_LIGHT = 6;
    /** Particle speed (blocks/tick) = BASE + PER_STRENGTH * local strength: a touch faster than drift, so the flow is legible. */
    private static final double BASE_SPEED = 0.03, SPEED_PER_STRENGTH = 0.26;

    private final double speedJitter;
    private final double swayFrequency, swayAmplitude;
    private double phase;
    private float baseAlpha;
    private float edge = 1f;
    private Vec3 flow = Vec3.ZERO;
    private Vec3 side = new Vec3(1, 0, 0);

    protected CurrentParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites)
    {
        super(level, x, y, z);
        pickSprite(sprites);
        gravity = 0f;
        friction = 1f;
        hasPhysics = false;
        xd = yd = zd = 0;
        lifetime = 40 + random.nextInt(50);
        quadSize = 0.035f + random.nextFloat() * 0.035f;
        baseAlpha = 0.25f + random.nextFloat() * 0.3f;
        speedJitter = 0.8 + random.nextDouble() * 0.4;
        swayFrequency = 0.15 + random.nextDouble() * 0.2;
        swayAmplitude = 0.004 + random.nextDouble() * 0.01;
        phase = random.nextDouble() * Math.PI * 2;
        float tint = 0.85f + random.nextFloat() * 0.15f;
        setColor(0.72f * tint, 0.86f * tint, 0.95f * tint);
        alpha = 0f;
        ParticleBudget.added(Budget.CURRENT);
        sample();
    }

    private void sample()
    {
        BlockPos pos = BlockPos.containing(x, y, z);
        CurrentData data = level.getFluidState(pos).is(FluidTags.WATER) ? NaturalCurrents.getCurrentAt(level, x, y, z) : CurrentData.NONE;
        if (!data.isPresent())
        {
            // Out of the stream: coast on and fade.
            if (lifetime - age > FADE_TICKS) lifetime = age + FADE_TICKS;
            return;
        }
        Vec3 dir = data.getDirection();
        flow = dir.scale((BASE_SPEED + SPEED_PER_STRENGTH * data.getLocalStrength()) * speedJitter);
        Vec3 s = new Vec3(-dir.z, 0, dir.x);
        side = s.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : s.normalize();
        // Most visible on the axis, thinning toward the edge.
        edge = 0.35f + 0.65f * Math.min(1f, data.falloff() * 1.5f);
    }

    @Override
    public void remove()
    {
        if (!removed) ParticleBudget.removed(Budget.CURRENT);
        super.remove();
    }

    @Override
    public void tick()
    {
        if (!removed) ParticleBudget.ticked(Budget.CURRENT);
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime)
        {
            remove();
            return;
        }
        if (age % SAMPLE_INTERVAL == 0) sample();
        phase += swayFrequency;
        double sway = Math.sin(phase) * swayAmplitude;
        double bob = Math.cos(phase * 0.7) * swayAmplitude * 0.5;
        xd = flow.x + side.x * sway;
        yd = flow.y + bob;
        zd = flow.z + side.z * sway;
        move(xd, yd, zd);
        float fade = Math.min(1f, Math.min(age / (float) FADE_TICKS, (lifetime - age) / (float) FADE_TICKS));
        alpha = baseAlpha * edge * Math.max(0f, fade);
    }

    /** A quad stretched along the particle's motion and turned to face the camera around that axis. */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks)
    {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);
        Vector3f axis = new Vector3f((float) xd, (float) yd, (float) zd);
        float speed = axis.length();
        if (speed < 1.0E-5f)
        {
            super.render(buffer, camera, partialTicks);
            return;
        }
        axis.div(speed);
        Vector3f width = new Vector3f(axis).cross(-px, -py, -pz);
        if (width.lengthSquared() < 1.0E-8f)
        {
            super.render(buffer, camera, partialTicks);
            return;
        }
        float size = getQuadSize(partialTicks);
        width.normalize(size * 0.6f);
        axis.mul(size * Math.min(6f, 1.5f + speed * 18f));

        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        vertex(buffer, px - axis.x - width.x, py - axis.y - width.y, pz - axis.z - width.z, u1, v1, light);
        vertex(buffer, px - axis.x + width.x, py - axis.y + width.y, pz - axis.z + width.z, u1, v0, light);
        vertex(buffer, px + axis.x + width.x, py + axis.y + width.y, pz + axis.z + width.z, u0, v0, light);
        vertex(buffer, px + axis.x - width.x, py + axis.y - width.y, pz + axis.z - width.z, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light)
    {
        buffer.vertex(x, y, z).uv(u, v).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
    }

    @Override
    public ParticleRenderType getRenderType()
    {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick)
    {
        int packed = super.getLightColor(partialTick);
        // Same rule as AbyssParticle: shader packs read the dim minimum as black, so give them full block light.
        if (ShaderCompat.shaderPackInUse()) return LightTexture.pack(15, LightTexture.sky(packed));
        return LightTexture.pack(Math.max(LightTexture.block(packed), MIN_BLOCK_LIGHT), LightTexture.sky(packed));
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites)
    {
        return (type, level, x, y, z, dx, dy, dz) -> new CurrentParticle(level, x, y, z, sprites);
    }
}
