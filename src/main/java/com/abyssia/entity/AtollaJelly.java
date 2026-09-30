package com.abyssia.entity;

import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * ムラサキカムリクラゲ / Atolla jellyfish (Atolla wyvillei), a crown jelly of 1000-4000 m. A red disc of a bell that
 * trails one very long tentacle to catch prey. Approached or struck, it sets off its "burglar alarm": brilliant blue
 * waves of light racing around the bell, slowing as the display goes on - bright enough to be seen from far off in
 * the dark, and thought to call larger predators to whatever is attacking it. Here the nearby big predators turn
 * toward the light. A player at its trailing tentacle is stung weakly. Neutral.
 */
public class AtollaJelly extends DriftingMedusa
{
    public static final int SECTORS = 8;
    private int summonCooldown;

    public AtollaJelly(EntityType<? extends AtollaJelly> type, Level level)
    {
        super(type, level, ModSounds.ATOLLA_JELLY);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return medusaAttributes(6.0);
    }

    @Override
    protected int pulseInterval()
    {
        return 45;
    }

    @Override
    protected double pulseStrength()
    {
        return 0.04;
    }

    @Override
    protected float restingGlow()
    {
        return 0.06F;
    }

    @Override
    protected double hangBelow()
    {
        return 1.8;
    }

    @Override
    protected float stingDamage()
    {
        return 1.0F;
    }

    @Override
    protected void sense(@Nullable Player near)
    {
        if (this.summonCooldown > 0) this.summonCooldown -= 10;
        if (near != null) this.alarm(160);
    }

    @Override
    protected void onSting(Player player)
    {
        this.alarm(200);
    }

    @Override
    protected void onStruck(DamageSource source)
    {
        super.onStruck(source);
        this.alarm(240);
    }

    /** The burglar alarm: the light display, and the big predators around turn toward it. */
    private void alarm(int ticks)
    {
        this.alert(ticks);
        if (this.summonCooldown > 0) return;
        this.summonCooldown = 200;
        for (Mob predator : this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(24.0),
                m -> m.isAlive() && m.getType().is(ModTags.ALARM_RESPONDERS) && m.getTarget() == null))
        {
            predator.getNavigation().moveTo(this.getX(), this.getY(), this.getZ(), 1.0);
        }
    }

    /**
     * 0..1: the light of coronal-groove sector {@code sector} (0..7, clockwise from above). The alarm is a wave running
     * around the bell, fast at first and slowing down; the resting glow and a flash light every sector.
     */
    public float sectorGlow(int sector, float partial)
    {
        float alarm = this.alertLevel(partial);
        if (alarm <= 0.01F) return this.glow(partial);
        float age = this.alertAge(partial);
        // wave front (in sectors): speed 0.6 sector/tick slowing with time, integrated: 36 ln(1 + age / 60)
        float front = (float) (36.0 * Math.log1p(age / 60.0)) % SECTORS;
        float d = Math.abs(sector - front);
        d = Math.min(d, SECTORS - d);
        float wave = Math.max(0.0F, 1.0F - d / 1.8F);
        // dark between the waves, so the light is seen to run around the rim
        float rest = this.restingGlow() * (1.0F - alarm);
        return Mth.clamp(rest + alarm * (0.05F + 0.95F * wave * wave) + this.flashLevel(partial), 0.0F, 1.0F);
    }
}
