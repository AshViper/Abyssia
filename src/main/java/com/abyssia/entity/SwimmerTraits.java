package com.abyssia.entity;

import com.abyssia.registry.ModSounds;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;

/**
 * Behaviour of a {@link GenericSwimmer} species, so a fish without a behaviour of its own is a table entry instead of
 * a class. Built once per species with the fluent setters (static final in ModEntities); never changed afterwards.
 */
public final class SwimmerTraits
{
    /** Where the fish spends its time. */
    public enum Zone
    {
        /** Midwater: prefers dark water and drifts up at night / down by day (diel vertical migration). */
        OPEN_WATER,
        /** Cruises close above the seabed (grenadiers, chimaeras, blobfish). */
        NEAR_FLOOR,
        /** Rests on the seabed and moves in short hops (tripod fish). */
        BOTTOM
    }

    final ModSounds.Voice voice;
    int maxTurnPitch = 20, maxTurnYaw = 5;
    float thrust = 0.002F;
    Zone zone = Zone.OPEN_WATER;
    double cruiseSpeed = 1.0;
    int wanderChance = 200;
    double migration = 0.0;
    double schoolRadius = 0.0;
    double fleeSpeed = 2.5;
    int fleeDistance = 8;
    @Nullable TagKey<EntityType<?>> prey;
    double preyReach = 1.6, preyLunge = 0.3, mouthForward = 0.3;
    String biteClip = "bite";
    int homeRadius = 24;
    int ambientInterval = 400;
    boolean hangsStill = false;

    private SwimmerTraits(ModSounds.Voice voice)
    {
        this.voice = voice;
    }

    public static SwimmerTraits of(ModSounds.Voice voice)
    {
        return new SwimmerTraits(voice);
    }

    /** Turn limits (degrees per tick) and forward thrust of the swim controller, as in DeepSeaSwimmer. */
    public SwimmerTraits steering(int maxTurnPitch, int maxTurnYaw, float thrust)
    {
        this.maxTurnPitch = maxTurnPitch;
        this.maxTurnYaw = maxTurnYaw;
        this.thrust = thrust;
        return this;
    }

    /** Where it lives, how fast it cruises and how often (1 in {@code wanderChance} ticks) it relocates. */
    public SwimmerTraits zone(Zone zone, double cruiseSpeed, int wanderChance)
    {
        this.zone = zone;
        this.cruiseSpeed = cruiseSpeed;
        this.wanderChance = wanderChance;
        return this;
    }

    /** Diel vertical migration weight per block (open water only; 0.3-0.5 for strong migrators). */
    public SwimmerTraits migrates(double perBlock)
    {
        this.migration = perBlock;
        return this;
    }

    /** Keeps within {@code radius} blocks of others of its kind. */
    public SwimmerTraits schools(double radius)
    {
        this.schoolRadius = radius;
        return this;
    }

    public SwimmerTraits flees(double speed, int distance)
    {
        this.fleeSpeed = speed;
        this.fleeDistance = distance;
        return this;
    }

    /** Strikes small animals of the prey tag within {@code reach} of its mouth ({@code mouthForward} blocks ahead). */
    public SwimmerTraits hunts(TagKey<EntityType<?>> prey, double reach, double lunge, double mouthForward)
    {
        this.prey = prey;
        this.preyReach = reach;
        this.preyLunge = lunge;
        this.mouthForward = mouthForward;
        return this;
    }

    /** The one-shot clip played when it strikes (default "bite"). */
    public SwimmerTraits biteClip(String clip)
    {
        this.biteClip = clip;
        return this;
    }

    /** Ambush / low-energy fish: hangs in place, turning slowly. */
    public SwimmerTraits hangsStill()
    {
        this.hangsStill = true;
        return this;
    }

    public SwimmerTraits home(int radius)
    {
        this.homeRadius = radius;
        return this;
    }

    public SwimmerTraits ambient(int interval)
    {
        this.ambientInterval = interval;
        return this;
    }
}
