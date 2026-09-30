package com.abyssia.entity;

import com.abyssia.entity.ai.PreyStrikeGoal;
import com.abyssia.entity.ai.SchoolGoal;
import com.abyssia.entity.ai.ScoredSwimGoal;
import com.abyssia.entity.ai.SlowTurnGoal;
import com.abyssia.entity.ai.SwimFleeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A deep-sea fish whose behaviour is fully described by {@link SwimmerTraits}: fleeing, optional prey strikes,
 * optional schooling, and wandering by zone (midwater with day/night migration, near the floor, or resting on the
 * seabed). Used for the species that need no behaviour of their own; the others keep dedicated classes.
 */
public class GenericSwimmer extends DeepSeaSwimmer
{
    private static final int ACTION_BITE = 0;

    private final SwimmerTraits traits;
    private int satedTicks;

    public GenericSwimmer(EntityType<? extends GenericSwimmer> type, Level level, SwimmerTraits traits)
    {
        super(type, level, traits.maxTurnPitch, traits.maxTurnYaw, traits.thrust);
        this.traits = traits;
        // Mob's constructor calls registerGoals() before the traits are set: add the goals here instead
        if (!level.isClientSide) this.addTraitGoals();
    }

    public static AttributeSupplier.Builder attributes(double health, double attack, double armor)
    {
        AttributeSupplier.Builder b = Mob.createMobAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.FOLLOW_RANGE, 12.0);
        if (attack > 0) b.add(Attributes.ATTACK_DAMAGE, attack);
        if (armor > 0) b.add(Attributes.ARMOR, armor);
        return b;
    }

    @Override
    protected void registerGoals()
    {
        // see the constructor
    }

    private void addTraitGoals()
    {
        SwimmerTraits t = this.traits;
        this.goalSelector.addGoal(0, new SwimFleeGoal(this, t.fleeSpeed, t.fleeDistance));
        if (t.prey != null)
        {
            this.goalSelector.addGoal(1, new PreyStrikeGoal(this, t.prey, t.preyReach, t.preyLunge, () -> this.satedTicks <= 0 && !this.isFleeing(),
                    () -> this.bodyPoint(0, 0.0, t.mouthForward), this::strike, prey -> {
                        this.heal(2.0F);
                        this.satedTicks = 1500 + this.random.nextInt(1500);
                    }));
        }
        if (t.schoolRadius > 0) this.goalSelector.addGoal(2, new SchoolGoal(this, t.schoolRadius, t.cruiseSpeed, () -> !this.isFleeing()));
        this.goalSelector.addGoal(3, new ScoredSwimGoal(this, t.wanderChance, t.cruiseSpeed, t.zone == SwimmerTraits.Zone.BOTTOM ? 5 : 8,
                t.zone == SwimmerTraits.Zone.OPEN_WATER ? 6 : 2, 240, () -> !this.isFleeing(), this::wanderScore));
        if (t.hangsStill) this.goalSelector.addGoal(4, new SlowTurnGoal(this, 160, 1.5F, () -> !this.isFleeing()));
    }

    private double wanderScore(BlockPos pos)
    {
        return switch (this.traits.zone)
        {
            case OPEN_WATER -> ScoredSwimGoal.darkness(this.level(), pos) * 2.0 + this.migration(pos, this.traits.migration);
            case NEAR_FLOOR -> -Math.abs(ScoredSwimGoal.floorDistance(this.level(), pos, 8) - 2) + ScoredSwimGoal.darkness(this.level(), pos) * 0.5;
            case BOTTOM -> -ScoredSwimGoal.floorDistance(this.level(), pos, 6);
        };
    }

    private void strike()
    {
        this.broadcastAction(ACTION_BITE);
        if (this.traits.voice.has("snap")) this.playSound(this.traits.voice.get("snap"), 0.6F, 1.0F + this.random.nextFloat() * 0.2F);
    }

    @Override
    protected void onAction(int action)
    {
        if (action == ACTION_BITE) this.animations().play(this.traits.biteClip, 12);
    }

    @Override
    protected int homeRadius()
    {
        return this.traits.homeRadius;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.animations().tick("swim", this.isSwimmingNow());
            return;
        }
        if (this.satedTicks > 0) --this.satedTicks;
        // bottom dwellers are heavier than water: at rest they settle onto the seabed
        if (this.traits.zone == SwimmerTraits.Zone.BOTTOM && this.isInWater() && !this.isFleeing() && this.getNavigation().isDone() && !this.onGround())
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.003, 0));
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide)
        {
            Entity attacker = source.getEntity();
            this.startFleeing(attacker != null ? attacker.position() : this.position().add(this.getLookAngle()), 80);
        }
        return hurt;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("Sated", this.satedTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.satedTicks = tag.getInt("Sated");
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.traits.voice.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return this.traits.ambientInterval;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return this.traits.voice.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return this.traits.voice.get("death");
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return this.traits.voice.get("flop");
    }
}
