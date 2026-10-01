package com.abyssia.entity;

import com.abyssia.entity.ai.FishLookControl;
import com.abyssia.entity.ai.HomeLayer;
import com.abyssia.entity.ai.SwimMoveControl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A deep-sea fish: neutrally buoyant (it hangs in the water without sinking, as the watery, low-density bodies of
 * bathypelagic fishes do), steers by turning its whole body, and flops and slowly suffocates out of water.
 */
public abstract class DeepSeaSwimmer extends WaterAnimal implements FaunaAnimated, HomeLayer.Bound
{
    private final HomeLayer homeLayer = new HomeLayer();

    protected static final int DEATH_TICKS = 44;
    /**
     * Entity events 100..103: a species' own one-shot actions (a bite, a flick...), played by its model. Kept far above
     * vanilla's ids (0..63 in 1.20.1): ClientPacketListener consumes 21, 35 and 63 itself and casts the entity (63 to
     * Sniffer), so an overlapping id crashes the client before it reaches {@link #handleEntityEvent}.
     */
    private static final byte EVENT_ACTION = 100;
    private final FaunaAnimations animations = new FaunaAnimations(this);
    private int fleeTicks;
    @Nullable
    private Vec3 fleeFrom;
    @Nullable
    private BlockPos home;

    protected DeepSeaSwimmer(EntityType<? extends DeepSeaSwimmer> type, Level level, int maxTurnPitch, int maxTurnYaw, float thrust)
    {
        super(type, level);
        this.moveControl = new SwimMoveControl(this, maxTurnPitch, maxTurnYaw, thrust);
        this.lookControl = new FishLookControl(this, maxTurnYaw * 1.5F);
    }

    @Override
    protected PathNavigation createNavigation(Level level)
    {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public FaunaAnimations animations()
    {
        return this.animations;
    }

    @Override
    public HomeLayer homeLayer()
    {
        return this.homeLayer;
    }

    /** Swimming rather than hanging still (drives the swim / idle clips). */
    protected boolean isSwimmingNow()
    {
        return this.isInWater() && this.getDeltaMovement().lengthSqr() > 2.5E-4;
    }

    /** Long enough for the model's own death clip (vanilla removes the body after 20 ticks). */
    @Override
    protected void tickDeath()
    {
        ++this.deathTime;
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved())
        {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void handleDamageEvent(DamageSource source)
    {
        super.handleDamageEvent(source);
        this.animations().hurt();
    }

    // ---------------------------------------------------------------- shared behaviour state

    /** Swim away from {@code from} for {@code ticks} (see {@link com.abyssia.entity.ai.SwimFleeGoal}). */
    public void startFleeing(Vec3 from, int ticks)
    {
        this.fleeFrom = from;
        this.fleeTicks = Math.max(this.fleeTicks, ticks);
    }

    public boolean isFleeing()
    {
        return this.fleeTicks > 0;
    }

    public Vec3 fleeFrom()
    {
        return this.fleeFrom != null ? this.fleeFrom : this.position();
    }

    /**
     * Night at the surface (the deep layer shares the overworld's clock): migrators rise toward the shallow end of
     * their range to feed and sink back by day.
     */
    public boolean isNightAbove()
    {
        long t = this.level().getDayTime() % 24000L;
        return t >= 13000L && t < 23000L;
    }

    /** Spot score of a diel vertical migrator: higher water by night, deeper by day. */
    public double migration(BlockPos pos, double perBlock)
    {
        return (this.isNightAbove() ? 1 : -1) * (pos.getY() - this.getY()) * perBlock;
    }

    /** Blocks it keeps within of where it spawned (its vent field); 0 = roams freely. */
    protected int homeRadius()
    {
        return 0;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data,
                                        @Nullable CompoundTag tag)
    {
        this.homeLayer.isDeep(this);
        if (this.homeRadius() > 0) this.setHome(this.blockPosition(), this.homeRadius());
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }


        /** Animals bound to a place (a vent field) keep within {@code radius} of it. */
    public void setHome(BlockPos pos, int radius)
    {
        this.home = pos.immutable();
        this.restrictTo(this.home, radius);
    }

    /** Plays one of the species' actions on every client (0..3, see {@link #onAction}). */
    protected void broadcastAction(int action)
    {
        this.level().broadcastEntityEvent(this, (byte) (EVENT_ACTION + action));
    }

    /** Client side: action {@code action} happened (start its clip). */
    protected void onAction(int action)
    {
    }

    @Override
    public void handleEntityEvent(byte id)
    {
        if (id >= EVENT_ACTION && id < EVENT_ACTION + 4) this.onAction(id - EVENT_ACTION);
        else super.handleEntityEvent(id);
    }

    /** A bite for the attack damage; true when it landed. */
    protected boolean bite(LivingEntity target)
    {
        return target.hurt(this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        this.homeLayer.save(tag);
        if (this.home != null)
        {
            tag.put("Home", NbtUtils.writeBlockPos(this.home));
            tag.putInt("HomeRadius", (int) this.getRestrictRadius());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.homeLayer.load(tag);
        if (tag.contains("Home")) this.setHome(NbtUtils.readBlockPos(tag.getCompound("Home")), Math.max(4, tag.getInt("HomeRadius")));
    }

    /** Share of its velocity the fish keeps each tick in water. */
    protected double waterDrag()
    {
        return 0.9;
    }

    @Override
    public void travel(Vec3 input)
    {
        if (this.isEffectiveAi() && this.isInWater())
        {
            this.moveRelative(this.getSpeed(), input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(this.waterDrag()));
        }
        else
        {
            super.travel(input);
        }
    }

    @Override
    public void aiStep()
    {
        if (!this.isInWater() && this.onGround() && this.verticalCollision && this.flopsOnLand())
        {
            this.setDeltaMovement(this.getDeltaMovement().add((this.random.nextFloat() * 2.0F - 1.0F) * 0.05F, 0.4F,
                    (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F));
            this.setOnGround(false);
            this.hasImpulse = true;
            this.playSound(this.getFlopSound(), this.getSoundVolume(), this.getVoicePitch());
        }
        if (this.fleeTicks > 0 && !this.level().isClientSide) --this.fleeTicks;
        super.aiStep();
    }

    protected abstract SoundEvent getFlopSound();

    protected boolean flopsOnLand()
    {
        return true;
    }

    @Override
    protected SoundEvent getSwimSound()
    {
        return SoundEvents.FISH_SWIM;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
    }

    /** Long animals reach well outside their hitbox: keep them from being culled while their tail is on screen. */
    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(0.8);
    }

    /**
     * Whether a predator may eat this animal: never a named one or one a player released from a bucket (aquarium
     * fish and pets stay safe).
     */
    public static boolean isFairPrey(Mob prey)
    {
        return prey.isAlive() && !prey.hasCustomName() && !(prey instanceof Bucketable bucketed && bucketed.fromBucket());
    }

    /** A point in front of and above the body, rotated with the fish (right, up, forward in blocks). */
    public Vec3 bodyPoint(double right, double up, double forward)
    {
        Vec3 look = Vec3.directionFromRotation(this.getXRot(), this.yBodyRot);
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 upv = side.cross(look).normalize();
        return this.position().add(0, this.getBbHeight() * 0.5, 0).add(look.scale(forward)).add(side.scale(right)).add(upv.scale(up));
    }
}
