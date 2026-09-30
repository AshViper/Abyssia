package com.abyssia.block;

import com.abyssia.environment.CaveAmbience;
import com.abyssia.environment.ParticleBudget;
import com.abyssia.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Occasional spores or glow dust from a plant; called from animateTick, so client-only and naturally sparse.
 * Emission thins out with distance from the viewer and picks up inside caves ({@link CaveAmbience#sporeFactor}),
 * and every emitter shares the plant particle budget, so dense cave forests never flood the screen.
 */
public enum SporeEmitter
{
    NONE(0),
    SPORES(24),
    GLOW_DUST(30);

    /** One particle per this many animation ticks on average. */
    private final int rarity;

    SporeEmitter(int rarity)
    {
        this.rarity = rarity;
    }

    public void emit(Level level, BlockPos pos, RandomSource random)
    {
        if (this == NONE || !ParticleBudget.hasRoom(ParticleBudget.Budget.PLANT)) return;
        if (random.nextFloat() * rarity >= CaveAmbience.sporeFactor(pos)) return;
        level.addParticle(this == SPORES ? ModParticles.SPORE.get() : ModParticles.GLOW_DUST.get(),
                pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.4 + random.nextDouble() * 0.6, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0, 0);
    }
}
