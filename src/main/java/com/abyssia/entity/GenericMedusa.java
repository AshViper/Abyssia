package com.abyssia.entity;

import com.abyssia.registry.ModSounds;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * A true medusa configured by data (pulse interval and strength): the drift and pulse of {@link DriftingMedusa}
 * without light organs of its own; struck, it pulses away. Voice kinds: ambient, pulse, hurt, death.
 */
public class GenericMedusa extends DriftingMedusa
{
    private final int pulseInterval;
    private final double pulseStrength;

    public GenericMedusa(EntityType<? extends GenericMedusa> type, Level level, ModSounds.Voice voice, int pulseInterval, double pulseStrength)
    {
        super(type, level, voice);
        this.pulseInterval = pulseInterval;
        this.pulseStrength = pulseStrength;
    }

    public static AttributeSupplier.Builder attributes(double health)
    {
        return medusaAttributes(health);
    }

    @Override
    protected int pulseInterval()
    {
        // guard: DriftingMedusa may ask before this constructor has run
        return this.pulseInterval > 0 ? this.pulseInterval : 60;
    }

    @Override
    protected double pulseStrength()
    {
        return this.pulseStrength > 0 ? this.pulseStrength : 0.04;
    }

    @Override
    protected void onStruck(DamageSource source)
    {
        Entity attacker = source.getEntity();
        if (attacker != null) this.escape(attacker.position(), 40);
    }
}
