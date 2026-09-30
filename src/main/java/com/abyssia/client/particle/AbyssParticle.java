package com.abyssia.client.particle;

import com.abyssia.Config;
import com.abyssia.client.ShaderCompat;
import com.abyssia.client.thermal.ClientVentTracker;
import com.abyssia.environment.CaveAmbience;
import com.abyssia.environment.OceanCurrentManager;
import com.abyssia.environment.ParticleBudget;
import com.abyssia.environment.ParticleBudget.Budget;
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

/**
 * Billboard particle for the underwater environment. Motion is fall/rise + ocean current + a small wobble,
 * so particles drift instead of moving in straight lines and reveal the current's direction.
 */
public class AbyssParticle extends TextureSheetParticle
{
    public enum Kind
    {
        /** Sinks slowly (marine snow). */
        SNOW,
        /** Glowing marine snow of the deepest water. */
        GLOW,
        /** Lifts off the seabed and settles back as it slows. */
        SEDIMENT,
        /** Hot plume above a thermal vent: fast rise that spreads and fades. */
        THERMAL,
        /** Volcanic ash: hangs in the water with no clear direction. */
        ASH,
        /** Black / white smoker plume: rises on the updraft, spreading and growing as it goes. */
        SMOKE,
        /** Glinting mineral grains precipitating around vents. */
        MINERAL,
        /** Plant spores: tiny, drifting with the current, barely sinking. */
        SPORE,
        /** Glowing dust shed by luminous plants and crystals. */
        GLOW_DUST
    }

    private static final int CURRENT_SAMPLE_INTERVAL = 10;
    private static final int FADE_TICKS = 20;
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private static final int MIN_BLOCK_LIGHT = 6;

    private final Kind kind;
    private Budget budget;
    private float growth = 1f;
    private float maxSize = 1f;
    private float speed;
    private float baseAlpha = 0.3f;
    private double phase;
    private final double wobbleFrequency;
    private double wobbleAmplitude;
    private Vec3 current = Vec3.ZERO;
    private Vec3 targetCurrent = Vec3.ZERO;

    protected AbyssParticle(ClientLevel level, double x, double y, double z, Kind kind, SpriteSet sprites)
    {
        super(level, x, y, z);
        this.kind = kind;
        pickSprite(sprites);
        this.gravity = 0f;
        this.friction = 1f;
        this.xd = this.yd = this.zd = 0;
        this.hasPhysics = kind == Kind.SNOW || kind == Kind.GLOW || kind == Kind.SEDIMENT;
        this.phase = random.nextDouble() * Math.PI * 2;
        this.wobbleFrequency = 0.03 + random.nextDouble() * 0.05;
        this.wobbleAmplitude = 0.002 + random.nextDouble() * 0.003;
        this.alpha = 0f;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        switch (kind)
        {
            case THERMAL -> { speed = 0.06f; lifetime = 30 + random.nextInt(25); quadSize = 0.12f; baseAlpha = 0.35f; }
            case SEDIMENT -> { speed = 0.006f; lifetime = 80 + random.nextInt(80); quadSize = 0.04f; baseAlpha = 0.35f; }
            case ASH -> { speed = 0.004f; lifetime = 200 + random.nextInt(200); quadSize = 0.035f; baseAlpha = 0.45f; wobbleAmplitude *= 2.5; }
            case SMOKE -> { speed = 0.03f; lifetime = 100 + random.nextInt(100); quadSize = 0.3f; baseAlpha = 0.5f; wobbleAmplitude *= 2; growth = 1.012f; maxSize = 1.1f; }
            case MINERAL -> { speed = 0.004f; lifetime = 60 + random.nextInt(60); quadSize = 0.03f; baseAlpha = 0.7f; }
            case SPORE -> { speed = 0.002f; lifetime = 80 + random.nextInt(80); quadSize = 0.02f; baseAlpha = 0.6f; }
            case GLOW_DUST -> { speed = 0.001f; lifetime = 60 + random.nextInt(60); quadSize = 0.025f; baseAlpha = 0.8f; }
            default -> { speed = 0.012f; lifetime = 240 + random.nextInt(240); quadSize = 0.04f; baseAlpha = 0.3f; }
        }
    }

    /** Sets size, vertical speed (fall for snow, rise for sediment/thermal) and opacity; counts it against a budget. */
    public AbyssParticle configure(float size, float speed, float alpha, Budget budget)
    {
        this.quadSize = size;
        this.maxSize = Math.max(maxSize, size * 3f);
        this.speed = speed;
        this.baseAlpha = alpha;
        if (this.budget == null)
        {
            this.budget = budget;
            ParticleBudget.added(budget);
        }
        return this;
    }

    public AbyssParticle configure(float size, float speed, float alpha)
    {
        return configure(size, speed, alpha, Budget.AMBIENT);
    }

    public AbyssParticle tint(float r, float g, float b)
    {
        setColor(r, g, b);
        return this;
    }

    public AbyssParticle spread(float amount)
    {
        wobbleAmplitude *= amount;
        return this;
    }

    public static int ambientAlive()
    {
        return ParticleBudget.alive(Budget.AMBIENT);
    }

    public static void resetAmbientCount()
    {
        ParticleBudget.reset();
    }

    @Override
    public void remove()
    {
        if (!removed && budget != null) ParticleBudget.removed(budget);
        super.remove();
    }

    @Override
    public void tick()
    {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime)
        {
            remove();
            return;
        }

        if (age % CURRENT_SAMPLE_INTERVAL == 1)
        {
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.getFluidState(pos).is(FluidTags.WATER))
            {
                remove();
                return;
            }
            // Ocean current plus any vent updraft: marine snow near a vent is lifted instead of sinking. Caves shelter
            // the water from the open current, but narrow passages channel it into a faster stream along their axis.
            Vec3 ocean = OceanCurrentManager.getCurrent(level, pos);
            if (Config.CAVE_CURRENT_EFFECTS.get()) ocean = ocean.scale(CaveAmbience.currentShelter()).add(CaveAmbience.passageFlow(x, y, z));
            targetCurrent = ocean.add(ClientVentTracker.updraftAt(x, y, z));
            if (age == 1) current = targetCurrent;
        }
        current = current.lerp(targetCurrent, 0.1);

        phase += wobbleFrequency;
        double wobbleX = Math.sin(phase) * wobbleAmplitude;
        double wobbleZ = Math.cos(phase * 0.73) * wobbleAmplitude;
        float progress = (float) age / lifetime;
        switch (kind)
        {
            case SNOW, GLOW -> { xd = current.x + wobbleX; yd = -speed + current.y; zd = current.z + wobbleZ; }
            case SEDIMENT -> { xd = current.x + wobbleX; yd = speed * (1f - progress) - 0.002 * progress + current.y * 0.5; zd = current.z + wobbleZ; }
            case THERMAL -> { xd = current.x * 0.3 + wobbleX; yd = speed * (1f - 0.7f * progress) + current.y; zd = current.z * 0.3 + wobbleZ; quadSize *= 1.012f; }
            case ASH -> { xd = current.x * 0.7 + wobbleX; yd = Math.sin(phase * 0.5) * speed + current.y * 0.3; zd = current.z * 0.7 + wobbleZ; }
            case SMOKE -> { xd = current.x * 0.5 + wobbleX; yd = speed * (1f - 0.5f * progress) + current.y; zd = current.z * 0.5 + wobbleZ; }
            case MINERAL -> { xd = current.x * 0.3 + wobbleX * 0.5; yd = -speed + current.y * 0.3; zd = current.z * 0.3 + wobbleZ * 0.5; }
            case SPORE, GLOW_DUST -> { xd = current.x * 0.8 + wobbleX; yd = Math.sin(phase * 0.4) * 0.004 - speed + current.y * 0.5; zd = current.z * 0.8 + wobbleZ; }
        }

        // Settled snow stops and fades out instead of sliding along the seabed.
        if (onGround && (kind == Kind.SNOW || kind == Kind.GLOW))
        {
            xd = yd = zd = 0;
            if (lifetime - age > FADE_TICKS) age = lifetime - FADE_TICKS;
        }
        if (growth != 1f && quadSize < maxSize) quadSize *= growth;
        move(xd, yd, zd);

        float fade = Math.min(1f, Math.min(age / (float) FADE_TICKS, (lifetime - age) / (float) FADE_TICKS));
        alpha = baseAlpha * fade;
        if (kind == Kind.GLOW || kind == Kind.GLOW_DUST) alpha *= 0.7f + 0.3f * Mth.sin(age * 0.15f);
    }

    @Override
    public ParticleRenderType getRenderType()
    {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick)
    {
        if (kind == Kind.GLOW || kind == Kind.GLOW_DUST) return FULL_BRIGHT;
        int packed = super.getLightColor(partialTick);
        // A shader pack lights particles with its own falloff, under which the dim minimum below reads as black (a white
        // smoker plume turns into a black one). Full block light makes them read as they do without shaders.
        if (ShaderCompat.shaderPackInUse()) return LightTexture.pack(15, LightTexture.sky(packed));
        // Thermal water glows faintly from the vent's heat; other particles keep a dim minimum so they stay
        // barely visible in lightless water instead of rendering black on black.
        int minBlockLight = kind == Kind.THERMAL || kind == Kind.MINERAL ? 9 : MIN_BLOCK_LIGHT;
        return LightTexture.pack(Math.max(LightTexture.block(packed), minBlockLight), LightTexture.sky(packed));
    }

    /** Provider whose particles get a random grey between {@code min} and {@code max}. */
    public static ParticleProvider<SimpleParticleType> tinted(Kind kind, SpriteSet sprites, float min, float max)
    {
        ParticleProvider<SimpleParticleType> base = provider(kind, sprites);
        return (type, level, x, y, z, dx, dy, dz) -> {
            AbyssParticle particle = (AbyssParticle) base.createParticle(type, level, x, y, z, dx, dy, dz);
            float grey = Mth.lerp(particle.random.nextFloat(), min, max);
            particle.setColor(grey, grey, grey * 1.02f);
            return particle;
        };
    }

    public static ParticleProvider<SimpleParticleType> provider(Kind kind, SpriteSet sprites)
    {
        return (type, level, x, y, z, dx, dy, dz) -> {
            AbyssParticle particle = new AbyssParticle(level, x, y, z, kind, sprites);
            // Non-ambient spawns (e.g. thermal vents) pass their vertical speed in dy.
            if (dy != 0) particle.speed = (float) dy;
            // Plant particles come from block animation ticks, so they are budgeted here rather than by a spawner.
            if (kind == Kind.SPORE || kind == Kind.GLOW_DUST) particle.configure(particle.quadSize, particle.speed, particle.baseAlpha, Budget.PLANT);
            return particle;
        };
    }
}
