package com.abyssia.registry;

import com.abyssia.Abyssia;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Particle types are registered on both sides; their rendering lives in the client package. */
public final class ModParticles
{
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Abyssia.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MARINE_SNOW = simple("marine_snow");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DEEP_MARINE_SNOW = simple("deep_marine_snow");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ABYSSAL_MARINE_SNOW = simple("abyssal_marine_snow");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BIOLUMINESCENT_SNOW = simple("bioluminescent_snow");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEDIMENT = simple("sediment");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> THERMAL_VENT = simple("thermal_vent");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOLCANIC_ASH = simple("volcanic_ash");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLACK_SMOKE = simple("black_smoke");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WHITE_SMOKE = simple("white_smoke");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MINERAL_PARTICLE = simple("mineral_particle");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPORE = simple("spore");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GLOW_DUST = simple("glow_dust");

    private ModParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String name)
    {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modBus)
    {
        PARTICLES.register(modBus);
    }
}
