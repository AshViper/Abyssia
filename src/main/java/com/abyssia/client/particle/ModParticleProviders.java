package com.abyssia.client.particle;

import com.abyssia.Abyssia;
import com.abyssia.client.particle.AbyssParticle.Kind;
import com.abyssia.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModParticleProviders
{
    private ModParticleProviders() {}

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event)
    {
        event.registerSpriteSet(ModParticles.MARINE_SNOW.get(), sprites -> AbyssParticle.provider(Kind.SNOW, sprites));
        event.registerSpriteSet(ModParticles.DEEP_MARINE_SNOW.get(), sprites -> AbyssParticle.provider(Kind.SNOW, sprites));
        event.registerSpriteSet(ModParticles.ABYSSAL_MARINE_SNOW.get(), sprites -> AbyssParticle.provider(Kind.SNOW, sprites));
        event.registerSpriteSet(ModParticles.BIOLUMINESCENT_SNOW.get(), sprites -> AbyssParticle.provider(Kind.GLOW, sprites));
        event.registerSpriteSet(ModParticles.SEDIMENT.get(), sprites -> AbyssParticle.provider(Kind.SEDIMENT, sprites));
        event.registerSpriteSet(ModParticles.THERMAL_VENT.get(), sprites -> AbyssParticle.provider(Kind.THERMAL, sprites));
        event.registerSpriteSet(ModParticles.VOLCANIC_ASH.get(), sprites -> AbyssParticle.provider(Kind.ASH, sprites));
        // Smoke shares one sprite; colour distinguishes black smokers (dark metal sulfides) from white smokers.
        event.registerSpriteSet(ModParticles.BLACK_SMOKE.get(), sprites -> AbyssParticle.tinted(Kind.SMOKE, sprites, 0.12f, 0.26f));
        event.registerSpriteSet(ModParticles.WHITE_SMOKE.get(), sprites -> AbyssParticle.tinted(Kind.SMOKE, sprites, 0.78f, 0.95f));
        event.registerSpriteSet(ModParticles.MINERAL_PARTICLE.get(), sprites -> AbyssParticle.provider(Kind.MINERAL, sprites));
        event.registerSpriteSet(ModParticles.SPORE.get(), sprites -> AbyssParticle.provider(Kind.SPORE, sprites));
        event.registerSpriteSet(ModParticles.GLOW_DUST.get(), sprites -> AbyssParticle.provider(Kind.GLOW_DUST, sprites));
        event.registerSpriteSet(ModParticles.CURRENT_MOTE.get(), CurrentParticle::provider);
    }
}
