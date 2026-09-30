package com.abyssia.entity;

import com.abyssia.entity.ai.PreyStrikeGoal;
import com.abyssia.entity.ai.ScoredSwimGoal;
import com.abyssia.entity.ai.SlowTurnGoal;
import com.abyssia.entity.ai.SwimFleeGoal;
import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ダイオウイカ / giant squid (Architeuthis dux). The largest eyes in the animal kingdom and a body up to about 12 m
 * with its feeding tentacles, found mostly at 300-1000 m (first filmed alive in 2004 off the Ogasawara Islands, at
 * about 900 m). Ammonium-rich tissue makes it neutrally buoyant, so it hangs in open water with little effort, a
 * slow, drifting ambush predator: the two long tentacles shoot out to seize deep-sea fish and other squid and haul
 * them to the beak. It has no light organs. Attacked, it squirts ink and jets away (only a close attacker in front
 * of the arms is seized). Keeps to large bodies of open water; it never breaks blocks.
 */
public class GiantSquid extends DeepSeaSwimmer
{
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(GiantSquid.class, EntityDataSerializers.BOOLEAN);
    private static final int ACTION_GRAB = 0;
    private static final double TENTACLE_REACH = 4.5;

    private int satedTicks;

    public GiantSquid(EntityType<? extends GiantSquid> type, Level level)
    {
        super(type, level, 8, 2, 0.0022F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.MOVEMENT_SPEED, 1.0).add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 20.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.7);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(HUNTING, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new SwimFleeGoal(this, 3.0, 16));
        this.goalSelector.addGoal(1, new PreyStrikeGoal(this, ModTags.GIANT_SQUID_PREY, TENTACLE_REACH, 0.35, true,
                () -> this.satedTicks <= 0 && !this.isFleeing(), this::beak, this::grab, prey -> {
                    this.heal(10.0F);
                    this.satedTicks = 3600 + this.random.nextInt(2400);
                }));
        // drifts to another patch of open, dark water now and then
        this.goalSelector.addGoal(3, new ScoredSwimGoal(this, 300, 0.8, 16, 6, 600, () -> !this.isFleeing(),
                pos -> -ScoredSwimGoal.enclosure(this.level(), pos) * 2.0 + ScoredSwimGoal.darkness(this.level(), pos) * 0.5));
        this.goalSelector.addGoal(4, new SlowTurnGoal(this, 240, 0.6F, () -> !this.isFleeing()));
    }

    /** The beak, at the centre of the arm crown. */
    private Vec3 beak()
    {
        return this.bodyPoint(0, 0, 1.0);
    }

    private void grab()
    {
        this.broadcastAction(ACTION_GRAB);
        this.playSound(ModSounds.GIANT_SQUID.get("grab"), 0.9F, this.getVoicePitch());
    }

    @Override
    protected void onAction(int action)
    {
        if (action == ACTION_GRAB) this.animations().play("grab", 27);
    }

    public boolean isHunting()
    {
        return this.entityData.get(HUNTING);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.animations().tick("swim", this.isSwimmingNow());
            // arms and tentacles spread and searching while prey is about
            this.animations().when("tentacle_move", this.isHunting() && !this.isDeadOrDying());
            return;
        }
        if (this.satedTicks > 0) --this.satedTicks;
        if ((this.tickCount + this.getId()) % 20 == 0)
        {
            boolean prey = this.satedTicks <= 0 && !this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(10.0),
                    e -> e.getType().is(ModTags.GIANT_SQUID_PREY) && isFairPrey(e)).isEmpty();
            if (prey != this.isHunting()) this.entityData.set(HUNTING, prey);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (!hurt || this.level().isClientSide || !this.isAlive()) return hurt;
        Entity attacker = source.getEntity();
        Vec3 from = attacker != null ? attacker.position() : this.position().add(this.getLookAngle());
        // an attacker right at the arms is seized; then ink and a jet away
        if (attacker instanceof LivingEntity living && this.distanceToSqr(living) < 4.0 * 4.0
                && living.position().subtract(this.position()).normalize().dot(this.getLookAngle()) > 0.3 && this.random.nextInt(2) == 0)
        {
            this.grab();
            this.bite(living);
        }
        this.ink(from);
        return true;
    }

    private void ink(Vec3 from)
    {
        if (this.isFleeing()) return;
        Vec3 away = this.position().subtract(from).normalize();
        if (this.level() instanceof ServerLevel server)
        {
            Vec3 p = this.position().add(0, this.getBbHeight() * 0.5, 0);
            server.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 60, 1.2, 0.8, 1.2, 0.05);
        }
        this.playSound(ModSounds.GIANT_SQUID.get("ink"), 1.0F, this.getVoicePitch());
        this.playSound(ModSounds.GIANT_SQUID.get("jet"), 1.0F, this.getVoicePitch());
        this.setDeltaMovement(this.getDeltaMovement().add(away.scale(0.8)));
        this.startFleeing(from, 160);
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(5.0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("SatedTicks", this.satedTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.satedTicks = tag.getInt("SatedTicks");
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.GIANT_SQUID.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 500;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.GIANT_SQUID.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.GIANT_SQUID.get("death");
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return ModSounds.GIANT_SQUID.get("hurt");
    }

    @Override
    protected boolean flopsOnLand()
    {
        return false;
    }

    @Override
    public float getVoicePitch()
    {
        return 0.7F + this.random.nextFloat() * 0.15F;
    }
}
