package com.abyssia.entity;

import com.abyssia.entity.ai.PreyStrikeGoal;
import com.abyssia.entity.ai.ScoredSwimGoal;
import com.abyssia.entity.ai.SlowTurnGoal;
import com.abyssia.entity.ai.SwimFleeGoal;
import com.abyssia.registry.ModParticles;
import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * ホウライエソ / Sloane's viperfish (Chauliodus sloani). A 35 cm ambush predator of the twilight and midnight zones:
 * it hangs motionless with the light at the tip of its long first dorsal ray held over its head, strikes whatever
 * small animal comes for the light (jaws swing open about 90 degrees; the lower fangs are too long to fit inside
 * and stand in front of the face), and rises toward shallower water at night to feed, sinking back by day. Stressed,
 * its photophores flare for several seconds. It is no danger to a player.
 */
public class Viperfish extends DeepSeaSwimmer implements LureBearer
{
    private static final EntityDataAccessor<Boolean> DIGESTING = SynchedEntityData.defineId(Viperfish.class, EntityDataSerializers.BOOLEAN);
    private static final int ACTION_BITE = 0;

    private int digestTicks;
    // client side: the stress flare (1 on a blow, fading over ~8 s)
    private float stress, stressO;

    public Viperfish(EntityType<? extends Viperfish> type, Level level)
    {
        super(type, level, 25, 6, 0.0025F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.FOLLOW_RANGE, 12.0);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DIGESTING, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new SwimFleeGoal(this, 3.5, 10));
        this.goalSelector.addGoal(1, new PreyStrikeGoal(this, ModTags.VIPERFISH_PREY, 2.0, 0.35, () -> !this.isDigesting() && !this.isFleeing(),
                () -> this.bodyPoint(0, 0.0, 0.3), this::strike, prey -> {
                    this.heal(3.0F);
                    this.digestTicks = 1800 + this.random.nextInt(1200);
                    this.entityData.set(DIGESTING, true);
                }));
        // relocates now and then: dark water, and up by night / down by day
        this.goalSelector.addGoal(3, new ScoredSwimGoal(this, 200, 1.0, 8, 6, 240, () -> !this.isFleeing(),
                pos -> ScoredSwimGoal.darkness(this.level(), pos) * 2.0 + this.migration(pos, 0.4)));
        this.goalSelector.addGoal(4, new SlowTurnGoal(this, 160, 1.5F, () -> !this.isFleeing()));
    }

    @Override
    public boolean isDigesting()
    {
        return this.entityData.get(DIGESTING);
    }

    @Override
    public boolean isLureLit()
    {
        return this.isAlive() && this.isInWater() && !this.isFleeing();
    }

    /** The photophore at the tip of the dorsal ray, held forward over the head. */
    @Override
    public Vec3 lurePosition()
    {
        return this.bodyPoint(0, 0.25, 0.25);
    }

    private void strike()
    {
        this.broadcastAction(ACTION_BITE);
        this.playSound(ModSounds.VIPERFISH.get("snap"), 0.6F, 1.1F + this.random.nextFloat() * 0.2F);
    }

    @Override
    protected void onAction(int action)
    {
        if (action == ACTION_BITE) this.animations().play("bite", 11);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.stressO = this.stress;
            this.stress = Math.max(0.0F, this.stress - 1.0F / 160.0F);
            this.animations().tick("swim", this.isSwimmingNow());
            this.animations().when("glow", this.isInWater());
            if (this.stress > 0.3F && this.random.nextInt(6) == 0)
            {
                Vec3 p = this.bodyPoint(0, -0.08, 0.1);
                this.level().addParticle(ModParticles.GLOW_DUST.get(), p.x, p.y, p.z, 0.0, 0.004, 0.0);
            }
        }
        else if (this.digestTicks > 0 && --this.digestTicks == 0)
        {
            this.entityData.set(DIGESTING, false);
        }
    }

    @Override
    public void handleDamageEvent(DamageSource source)
    {
        super.handleDamageEvent(source);
        this.stress = 1.0F;
    }

    /** 0..1: the stress flare of the light organs. */
    public float stress(float partial)
    {
        return Mth.lerp(partial, this.stressO, this.stress);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide)
        {
            Entity attacker = source.getEntity();
            this.startFleeing(attacker != null ? attacker.position() : this.position().add(this.getLookAngle()), 100 + this.random.nextInt(60));
        }
        return hurt;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("DigestTicks", this.digestTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.digestTicks = tag.getInt("DigestTicks");
        this.entityData.set(DIGESTING, this.digestTicks > 0);
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.VIPERFISH.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 500;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.VIPERFISH.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.VIPERFISH.get("death");
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return ModSounds.VIPERFISH.get("flop");
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.5F;
    }
}
