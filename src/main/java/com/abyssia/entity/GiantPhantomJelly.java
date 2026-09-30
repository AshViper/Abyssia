package com.abyssia.entity;

import com.abyssia.registry.ModSounds;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * ダイオウクラゲ / giant phantom jelly (Stygiomedusa gigantea), bathypelagic. A dark crimson bell over a metre wide
 * with four ribbon-like oral arms more than ten metres long and no tentacles. No bioluminescence is known, so it stays
 * invisible in the dark until the player's own light finds it. Unhurried and harmless: slow strokes, and struck, a
 * slow retreat. Only the bell has a hitbox; the arms are drawn but never collide.
 */
public class GiantPhantomJelly extends DriftingMedusa
{
    public GiantPhantomJelly(EntityType<? extends GiantPhantomJelly> type, Level level)
    {
        super(type, level, ModSounds.GIANT_PHANTOM_JELLY);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return medusaAttributes(40.0);
    }

    @Override
    protected int pulseInterval()
    {
        return 100;
    }

    @Override
    protected double pulseStrength()
    {
        return 0.05;
    }

    @Override
    protected double hangBelow()
    {
        return 9.5;
    }

    /** No light organs: it neither glows nor flashes. */
    @Override
    protected void onStruck(DamageSource source)
    {
        Entity attacker = source.getEntity();
        if (attacker != null) this.escape(attacker.position(), 40);
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.6F;
    }

    @Override
    public float getVoicePitch()
    {
        return super.getVoicePitch() * 0.6F;
    }
}
