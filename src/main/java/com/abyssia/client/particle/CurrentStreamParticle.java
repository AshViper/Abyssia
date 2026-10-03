package com.abyssia.client.particle;

import com.abyssia.client.ShaderCompat;
import com.abyssia.environment.CurrentStream;
import com.abyssia.environment.CurrentStreams;
import com.abyssia.environment.ParticleBudget;
import com.abyssia.environment.ParticleBudget.Budget;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * One white streak of a CU01 current stream ({@code client.CurrentStreamClient}): the {@code current_mote} sprite
 * stretched to a fixed length and width along the stream's flow, riding the flow at the stream's speed. Vanilla
 * PARTICLE_SHEET_TRANSLUCENT, so shader packs still draw it. Fades in and out; fades fast in rock or out of the stream.
 */
public class CurrentStreamParticle extends TextureSheetParticle
{
    /** The current_mote sprites, captured when the particle providers are registered. */
    public static SpriteSet sprites;

    private static final int SAMPLE_INTERVAL = 2;
    private static final int FADE_TICKS = 6;
    private static final int MIN_BLOCK_LIGHT = 6;
    private static final double MIN_SPEED = 0.02;
    /** #EAF8FF */
    private static final float RED = 0xEA / 255f, GREEN = 0xF8 / 255f, BLUE = 1f;

    private final CurrentStream stream;
    private final float width, length, baseAlpha;
    private Vec3 flow;

    public CurrentStreamParticle(ClientLevel level, double x, double y, double z, CurrentStream stream, Vec3 direction,
                                 float width, float length, int lifetime, float alpha)
    {
        super(level, x, y, z);
        if (sprites != null) pickSprite(sprites);
        this.stream = stream;
        this.width = width;
        this.length = length;
        this.baseAlpha = alpha;
        this.lifetime = lifetime;
        gravity = 0f;
        friction = 1f;
        hasPhysics = false;
        setColor(RED, GREEN, BLUE);
        this.alpha = 0f;
        flow = direction.scale(MIN_SPEED);
        ParticleBudget.added(Budget.STREAM);
        sample();
    }

    private void sample()
    {
        CurrentStreams.Sample s = level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)
                ? CurrentStreams.sampleStream(stream, x, y, z) : null;
        if (s == null)
        {
            // In rock, out of the water or past the band's edge: coast on and fade quickly.
            if (lifetime - age > FADE_TICKS) lifetime = age + FADE_TICKS;
            return;
        }
        CurrentStreams.Params params = CurrentStreams.params(level);
        double speed = params == null ? MIN_SPEED : Math.max(MIN_SPEED, CurrentStreams.flowSpeed(params, s.strength()) * s.flowMultiplier());
        flow = s.direction().scale(speed);
    }

    @Override
    public void remove()
    {
        if (!removed) ParticleBudget.removed(Budget.STREAM);
        super.remove();
    }

    @Override
    public void tick()
    {
        if (!removed) ParticleBudget.ticked(Budget.STREAM);
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime)
        {
            remove();
            return;
        }
        if (age % SAMPLE_INTERVAL == 0) sample();
        xd = flow.x;
        yd = flow.y;
        zd = flow.z;
        move(xd, yd, zd);
        float fade = Math.min(1f, Math.min(age / (float) FADE_TICKS, (lifetime - age) / (float) FADE_TICKS));
        alpha = baseAlpha * Math.max(0f, fade);
    }

    /** A quad {@code length} long along the flow and {@code width} wide, turned to face the camera around the flow. */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks)
    {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);
        Vector3f axis = new Vector3f((float) flow.x, (float) flow.y, (float) flow.z);
        if (axis.lengthSquared() < 1.0E-12f) return;
        axis.normalize();
        Vector3f side = new Vector3f(axis).cross(-px, -py, -pz);
        if (side.lengthSquared() < 1.0E-8f) return;
        side.normalize(width * 0.5f);
        axis.mul(length * 0.5f);

        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        vertex(buffer, px - axis.x - side.x, py - axis.y - side.y, pz - axis.z - side.z, u1, v1, light);
        vertex(buffer, px - axis.x + side.x, py - axis.y + side.y, pz - axis.z + side.z, u1, v0, light);
        vertex(buffer, px + axis.x + side.x, py + axis.y + side.y, pz + axis.z + side.z, u0, v0, light);
        vertex(buffer, px + axis.x - side.x, py + axis.y - side.y, pz + axis.z - side.z, u0, v1, light);
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
        // Same rule as CurrentParticle: shader packs read the dim minimum as black, so give them full block light.
        if (ShaderCompat.shaderPackInUse()) return LightTexture.pack(15, LightTexture.sky(packed));
        return LightTexture.pack(Math.max(LightTexture.block(packed), MIN_BLOCK_LIGHT), LightTexture.sky(packed));
    }
}
