package com.abyssia.entity;

import com.abyssia.registry.ModParticles;
import com.abyssia.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import javax.annotation.Nullable;

/**
 * クロカムリクラゲ / helmet jellyfish (Periphylla periphylla), a crown jelly usually below 900 m. Light destroys its
 * dark red pigment, so it shuns light: it sinks away from any bright light and rises only by night. A player coming
 * close makes it spread its twelve thick tentacles and light its coronal groove blue-green; one among them is stung
 * and slowed. Struck, it flashes and sheds sparkling luminous particles from the lappets. Neutral.
 */
public class HelmetJelly extends DriftingMedusa
{
    private static final int BRIGHT = 6;

    public HelmetJelly(EntityType<? extends HelmetJelly> type, Level level)
    {
        super(type, level, ModSounds.HELMET_JELLY);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return medusaAttributes(10.0);
    }

    @Override
    protected int pulseInterval()
    {
        return 50;
    }

    @Override
    protected double pulseStrength()
    {
        return 0.045;
    }

    @Override
    protected float restingGlow()
    {
        return 0.08F;
    }

    @Override
    protected double hangBelow()
    {
        return 1.2;
    }

    @Override
    protected double senseRange()
    {
        return 4.0;
    }

    @Override
    protected float stingDamage()
    {
        return 2.0F;
    }

    /** Diel migration: some blocks higher while it is night at the surface. */
    @Override
    protected double anchorOffset()
    {
        return this.isNightAbove() ? 8.0 : 0.0;
    }

    @Override
    protected void sense(@Nullable Player near)
    {
        // photophobia: bright block light (lamps, glowing blocks) drives it down and away
        if (!this.isFleeing() && this.level().getBrightness(LightLayer.BLOCK, this.blockPosition()) >= BRIGHT)
        {
            this.shiftAnchor(-6.0);
            this.escape(this.position().add(0, 3, 0), 60);
        }
        if (near != null) this.alert(80);
    }

    @Override
    protected void onSting(Player player)
    {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0), this);
        this.alert(120);
    }

    @Override
    protected void onStruck(DamageSource source)
    {
        super.onStruck(source);
        this.alert(160);
        if (this.level() instanceof ServerLevel server)
        {
            // sparkling particles released from the lappet margins
            server.sendParticles(ModParticles.GLOW_DUST.get(), this.getX(), this.getY() + 0.1, this.getZ(), 16, 0.35, 0.1, 0.35, 0.01);
        }
    }
}
