package com.abyssia.entity;

import com.abyssia.entity.ai.HomeLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

import javax.annotation.Nullable;

/**
 * A seabed walker (crab, squat lobster, snail): it walks, never swims off the bottom, clambers over rock, and plays its
 * walking clip while it moves. Vent animals are bound to their vent field ({@link #setHome}).
 */
public abstract class BenthicWalker extends WaterAnimal implements FaunaAnimated, HomeLayer.Bound
{
    private final HomeLayer homeLayer = new HomeLayer();
    protected static final int DEATH_TICKS = 44;
    /**
     * Entity events 100..103: a species' own one-shot actions (a bite, a flick...), played by its model. Kept far above
     * vanilla's ids (0..63 in 1.20.1): ClientPacketListener consumes 21, 35 and 63 itself and casts the entity (63 to
     * Sniffer), so an overlapping id crashes the client before it reaches {@link #handleEntityEvent}.
     */
    private static final byte EVENT_ACTION = 100;
    /**
     * Walk input multiplier on the seabed. Under water vanilla moves a walker with the swim thrust (0.02 x zza a tick),
     * and LivingEntity.aiStep zeroes every velocity component under 0.003 each tick: at walker speeds the velocity never
     * builds up, it creeps 0.02 x zza a tick and PathNavigation's stuck check drops each path after 100 ticks.
     */
    static final double SEABED_STRIDE = 5.0;
    private final FaunaAnimations animations = new FaunaAnimations(this);
    private final String moveClip;
    @Nullable
    private BlockPos home;

    protected BenthicWalker(EntityType<? extends BenthicWalker> type, Level level, String moveClip)
    {
        super(type, level);
        this.moveClip = moveClip;
        this.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.0);
        this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
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

    /** Whether the walking clip plays (not while withdrawn or feeding in place). */
    protected boolean showsWalking()
    {
        return this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide) this.animations.tick(this.moveClip, this.showsWalking());
    }

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

    /** Plays one of the species' actions on every client (0..3, see {@link #onAction}). */
    protected void broadcastAction(int action)
    {
        this.level().broadcastEntityEvent(this, (byte) (EVENT_ACTION + action));
    }

    protected void onAction(int action)
    {
    }

    @Override
    public void handleEntityEvent(byte id)
    {
        if (id >= EVENT_ACTION && id < EVENT_ACTION + 4) this.onAction(id - EVENT_ACTION);
        else super.handleEntityEvent(id);
    }

    protected boolean pinch(LivingEntity target)
    {
        return target.hurt(this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    /** Blocks it keeps within of where it spawned (its vent field); 0 = roams freely. */
    protected int homeRadius()
    {
        return 0;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data)
    {
        this.homeLayer.isDeep(this);
        if (this.homeRadius() > 0) this.setHome(this.blockPosition(), this.homeRadius());
        return super.finalizeSpawn(level, difficulty, reason, data);
    }


    public void setHome(BlockPos pos, int radius)
    {
        this.home = pos.immutable();
        this.restrictTo(this.home, radius);
    }

    @Override
    public void travel(Vec3 input)
    {
        super.travel(this.isInWater() && this.onGround() ? input.scale(SEABED_STRIDE) : input);
    }

    /** A walker only pushes itself up in water while clambering against rock, never floating off after something. */
    @Override
    public void jumpInFluid(FluidType type)
    {
        if (this.horizontalCollision) super.jumpInFluid(type);
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> fluid)
    {
        if (this.horizontalCollision) super.jumpInLiquid(fluid);
    }

    /** Walking the seabed plays through the swim-sound path (the game treats any movement in water as swimming). */
    @Override
    protected abstract SoundEvent getSwimSound();

    @Override
    protected void playSwimSound(float volume)
    {
        this.playSound(this.getSwimSound(), 0.1F, 0.9F + this.random.nextFloat() * 0.3F);
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(0.5);
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
        if (tag.contains("Home")) HomeLayer.readHome(tag).ifPresent(home -> this.setHome(home, Math.max(4, tag.getInt("HomeRadius"))));
    }
}
