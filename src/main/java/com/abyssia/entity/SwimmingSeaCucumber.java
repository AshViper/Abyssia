package com.abyssia.entity;

import com.abyssia.registry.ModParticles;
import com.abyssia.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * ユメナマコ / swimming sea cucumber (Enypniastes eximia), 516-5689 m. A translucent rose-red animal that settles on
 * the soft seabed to feed on sediment with its tentacles (rarely more than about a minute at a time), then beats its
 * webbed front veil and rises off the bottom, drifting and slowly sinking back to settle somewhere else. Touched or
 * attacked, its skin lights up with blue-green bioluminescent granules and sheds glowing, sticky flakes that mark the
 * attacker - it has no other defence. Passive.
 */
public class SwimmingSeaCucumber extends DeepSeaSwimmer
{
    public static final byte SETTLED = 0, RISING = 1, DRIFTING = 2;
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(SwimmingSeaCucumber.class, EntityDataSerializers.BYTE);
    private static final int ACTION_GLOW = 0;

    private int phaseTicks;
    private int riseTicks;
    // client side: the glow of disturbed skin (1, fading over ~4 s)
    private float glow, glowO;

    public SwimmingSeaCucumber(EntityType<? extends SwimmingSeaCucumber> type, Level level)
    {
        super(type, level, 10, 3, 0.001F);
        this.phaseTicks = 100 + this.random.nextInt(600);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 1.0);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(PHASE, SETTLED);
    }

    public byte phase()
    {
        return this.entityData.get(PHASE);
    }

    private void setPhase(byte phase, int ticks)
    {
        this.entityData.set(PHASE, phase);
        this.phaseTicks = ticks;
    }

    @Override
    protected void registerGoals()
    {
        // no pathfinding: it feeds, rises and sinks (see tick)
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.glowO = this.glow;
            this.glow = Math.max(0.0F, this.glow - 1.0F / 80.0F);
            byte phase = this.phase();
            this.animations().tick("swim", phase != SETTLED && this.isInWater());
            return;
        }
        if (!this.isInWater() || this.isDeadOrDying()) return;
        --this.phaseTicks;
        Vec3 v = this.getDeltaMovement();
        Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());
        switch (this.phase())
        {
            case SETTLED -> {
                // grazing the sediment, creeping on its tube feet
                if (!this.onGround()) this.setDeltaMovement(v.x * 0.5, v.y - 0.004, v.z * 0.5);
                else if (this.random.nextInt(40) == 0) this.setDeltaMovement(forward.scale(0.02));
                if (this.phaseTicks <= 0) this.launch();
            }
            case RISING -> {
                // veil strokes: up and a little forward
                this.setDeltaMovement(forward.x * 0.015, 0.03, forward.z * 0.015);
                if (--this.riseTicks <= 0 || this.verticalCollision) this.setPhase(DRIFTING, 0);
            }
            default -> {
                // drifting down, turning slowly, until it settles
                this.setDeltaMovement(forward.x * 0.008, -0.012, forward.z * 0.008);
                this.setYRot(this.getYRot() + 0.4F);
                this.yBodyRot = this.getYRot();
                if (this.onGround()) this.setPhase(SETTLED, 400 + this.random.nextInt(800));
            }
        }
        // bumped into: light up
        if ((this.tickCount + this.getId()) % 10 == 0)
        {
            Player near = this.level().getNearestPlayer(this, 1.2);
            if (near != null && !near.isSpectator()) this.lightUp(false);
        }
    }

    private void launch()
    {
        int headroom = 0;
        while (headroom < 16 && this.level().getFluidState(this.blockPosition().above(headroom + 1)).isSource()) headroom++;
        this.riseTicks = Math.min(headroom, 4 + this.random.nextInt(10)) * 30;
        this.setPhase(RISING, 0);
        this.setYRot(this.random.nextFloat() * 360.0F);
        this.playSound(ModSounds.YUMENAMAKO.get("swim"), 0.4F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    /** Bioluminescence: the skin lights up and glowing flakes come off; hurt, it also swims up and away. */
    private void lightUp(boolean flee)
    {
        this.broadcastAction(ACTION_GLOW);
        if (this.level() instanceof ServerLevel server)
        {
            server.sendParticles(ModParticles.GLOW_DUST.get(), this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 10, 0.2, 0.15, 0.2, 0.004);
        }
        if (flee && this.phase() == SETTLED) this.launch();
    }

    @Override
    protected void onAction(int action)
    {
        if (action == ACTION_GLOW) this.glow = 1.0F;
    }

    /** 0..1: the glow of the disturbed skin. */
    public float glow(float partial)
    {
        return Mth.lerp(partial, this.glowO, this.glow);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive()) this.lightUp(true);
        return hurt;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putByte("Phase", this.phase());
        tag.putInt("PhaseTicks", this.phaseTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.setPhase(tag.getByte("Phase"), tag.getInt("PhaseTicks"));
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.phase() == SETTLED ? null : ModSounds.YUMENAMAKO.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 300;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.YUMENAMAKO.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.YUMENAMAKO.get("death");
    }

    /** No fish flop: out of water it just lies there. */
    @Override
    protected boolean flopsOnLand()
    {
        return false;
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return ModSounds.YUMENAMAKO.get("hurt");
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.4F;
    }
}
