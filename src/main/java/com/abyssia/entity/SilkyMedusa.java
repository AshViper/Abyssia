package com.abyssia.entity;

import com.abyssia.registry.ModParticles;
import com.abyssia.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * ニジクラゲ / silky medusa (Colobonema sericeum), a hydromedusa of 200-700 m. Small and quick: it rests with its
 * curled tentacles spread; disturbed, its bell flashes brilliant blue and it jets away in rapid strokes. Struck, it
 * casts off its sticky tentacles as a decoy (they do not glow) and grows them back. Passive: it never stings.
 */
public class SilkyMedusa extends DriftingMedusa
{
    private static final int REGROW_TICKS = 1200;
    private int regrowTicks;

    public SilkyMedusa(EntityType<? extends SilkyMedusa> type, Level level)
    {
        super(type, level, ModSounds.SILKY_MEDUSA);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return medusaAttributes(3.0);
    }

    @Override
    protected int pulseInterval()
    {
        return 30;
    }

    @Override
    protected double pulseStrength()
    {
        return 0.035;
    }

    @Override
    protected float restingGlow()
    {
        return 0.12F;
    }

    @Override
    protected double hangBelow()
    {
        return 0.4;
    }

    @Override
    protected double senseRange()
    {
        return 4.5;
    }

    @Override
    protected void sense(@Nullable Player near)
    {
        if (this.hasShedTentacles() && (this.regrowTicks -= 10) <= 0) this.setShedTentacles(false);
        if (near == null || this.isFleeing()) return;
        this.flash();
        this.alert(40);
        this.escape(near.position(), 60);
    }

    @Override
    protected void onStruck(DamageSource source)
    {
        super.onStruck(source);
        Entity attacker = source.getEntity();
        this.escape(attacker != null ? attacker.position() : this.position().add(0, -1, 0), 100);
        if (!this.hasShedTentacles())
        {
            // the cast-off tentacles: pale sticky strands left drifting where it was
            this.setShedTentacles(true);
            this.regrowTicks = REGROW_TICKS;
            if (this.level() instanceof ServerLevel server)
            {
                server.sendParticles(ModParticles.MARINE_SNOW.get(), this.getX(), this.getY() - 0.1, this.getZ(), 14, 0.2, 0.2, 0.2, 0.002);
            }
        }
    }
}
