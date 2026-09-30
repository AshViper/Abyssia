package com.abyssia.registry;

import com.abyssia.Abyssia;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particle types are registered on both sides; their rendering lives in the client package. */
public final class ModParticles
{
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Abyssia.MODID);

    public static final RegistryObject<SimpleParticleType> MARINE_SNOW = simple("marine_snow");
    public static final RegistryObject<SimpleParticleType> DEEP_MARINE_SNOW = simple("deep_marine_snow");
    public static final RegistryObject<SimpleParticleType> ABYSSAL_MARINE_SNOW = simple("abyssal_marine_snow");
    public static final RegistryObject<SimpleParticleType> BIOLUMINESCENT_SNOW = simple("bioluminescent_snow");
    public static final RegistryObject<SimpleParticleType> SEDIMENT = simple("sediment");
    public static final RegistryObject<SimpleParticleType> THERMAL_VENT = simple("thermal_vent");
    public static final RegistryObject<SimpleParticleType> VOLCANIC_ASH = simple("volcanic_ash");
    public static final RegistryObject<SimpleParticleType> BLACK_SMOKE = simple("black_smoke");
    public static final RegistryObject<SimpleParticleType> WHITE_SMOKE = simple("white_smoke");
    public static final RegistryObject<SimpleParticleType> MINERAL_PARTICLE = simple("mineral_particle");
    public static final RegistryObject<SimpleParticleType> SPORE = simple("spore");
    public static final RegistryObject<SimpleParticleType> GLOW_DUST = simple("glow_dust");

    private ModParticles() {}

    private static RegistryObject<SimpleParticleType> simple(String name)
    {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modBus)
    {
        PARTICLES.register(modBus);
    }
}
