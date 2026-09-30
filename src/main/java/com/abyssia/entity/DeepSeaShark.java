package com.abyssia.entity;

import com.abyssia.entity.ai.PreyStrikeGoal;
import com.abyssia.entity.ai.RetaliateGoal;
import com.abyssia.entity.ai.ScoredSwimGoal;
import com.abyssia.entity.ai.SlowTurnGoal;
import com.abyssia.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A slope shark: it cruises slowly a few blocks above the seabed of the continental slope, takes prey that comes
 * within reach of its jaws, and after a meal is sated for a few minutes. Neutral: it bites back at whoever hurts it,
 * for a while, and never goes after a player unprovoked (neither species has ever attacked a person).
 */
public abstract class DeepSeaShark extends DeepSeaSwimmer
{
    protected static final int ACTION_BITE = 0;

    private final ModSounds.Voice voice;
    private int satedTicks;

    protected DeepSeaShark(EntityType<? extends DeepSeaShark> type, Level level, ModSounds.Voice voice, int maxTurnPitch, int maxTurnYaw,
                           float thrust)
    {
        super(type, level, maxTurnPitch, maxTurnYaw, thrust);
        this.voice = voice;
    }

    /** Blocks above the seabed it likes to cruise at. */
    protected abstract double cruiseHeight();

    protected abstract TagKey<EntityType<?>> prey();

    /** Where the jaws close, relative to the body (forward, in blocks). */
    protected abstract double mouthForward();

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new RetaliateGoal(this, 2.0, 0.8, 240, target -> {
            this.snap();
            this.bite(target);
        }));
        this.goalSelector.addGoal(1, new PreyStrikeGoal(this, this.prey(), 2.4, 0.45, () -> this.satedTicks <= 0,
                () -> this.bodyPoint(0, -0.05, this.mouthForward()), this::snap, prey -> {
                    this.heal(6.0F);
                    this.satedTicks = 2400 + this.random.nextInt(2400);
                }));
        this.goalSelector.addGoal(3, new ScoredSwimGoal(this, 60, 1.0, 12, 4, 400, () -> true, this::cruiseScore));
        this.goalSelector.addGoal(5, new SlowTurnGoal(this, 120, 1.0F, () -> true));
    }

    protected double cruiseScore(BlockPos pos)
    {
        int floor = ScoredSwimGoal.floorDistance(this.level(), pos, 12);
        return -Math.abs(floor - this.cruiseHeight()) + ScoredSwimGoal.darkness(this.level(), pos) * 0.3;
    }

    protected void snap()
    {
        this.broadcastAction(ACTION_BITE);
        this.playSound(this.voice.get("bite"), 0.8F, this.getVoicePitch());
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide) this.animations().tick("swim", this.isSwimmingNow());
        else if (this.satedTicks > 0) --this.satedTicks;
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(1.8);
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
        return this.voice.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 600;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return this.voice.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return this.voice.get("death");
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return this.voice.get("flop");
    }

    @Override
    public float getVoicePitch()
    {
        return 0.75F + this.random.nextFloat() * 0.15F;
    }
}
