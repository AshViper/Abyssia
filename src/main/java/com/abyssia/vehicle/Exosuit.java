package com.abyssia.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

/**
 * One-seat walking exosuit for the seabed. A plain Entity like {@link Submarine}: the pilot's client moves it
 * (vanilla ServerboundMoveVehiclePacket), so no mod packet is needed. Always gravity (no buoyancy), W/S/A/D walk
 * relative to the rider's yaw, Space = thrusters (water only, limited gauge, recharges on the ground).
 * Model front = -Z like the submarine (the renderer uses 180 - yaw). Renderer data: {@link #limbPos}/{@link #limbSpeed}
 * (+ the O fields, interpolate with the partial tick), {@link #getDamage()}, {@link #isThrusting()}, {@link #thrustFraction()}.
 */
public class Exosuit extends Entity
{
    private static final EntityDataAccessor<Float> DATA_DAMAGE = SynchedEntityData.defineId(Exosuit.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_THRUSTING = SynchedEntityData.defineId(Exosuit.class, EntityDataSerializers.BOOLEAN);

    public static final float MAX_DAMAGE = 60.0f;
    public static final double WALK_SPEED = 0.15, AIR_SPEED = 0.06, GRAVITY = 0.04, TERMINAL = 0.5, THRUST_ACCEL = 0.08, THRUST_MAX_UP = 0.35;
    public static final int THRUST_TICKS = 40;
    /** rider feet above the suit's feet (the rider sits in the cockpit) */
    public static final double SEAT_Y = 0.7;

    /** Pilot input on the client (set by ExosuitClient; the server never asks). */
    public interface Pilot
    {
        /** {forward (+1 W / -1 S), strafe (+1 A / -1 D), thrust (+1 Space held)} */
        int[] input();
    }

    private static final int[] NO_INPUT = {0, 0, 0};
    public static Pilot pilot = () -> NO_INPUT;

    /** walk animation for the renderer, updated each tick on both sides from the horizontal speed */
    public float limbPos, limbPosO, limbSpeed, limbSpeedO;
    /** client-side thrust gauge, 0..THRUST_TICKS */
    public int thrust = THRUST_TICKS;

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot, lerpXRot;
    private double lastX, lastZ;
    private boolean lastInit;

    public Exosuit(EntityType<? extends Exosuit> type, Level level)
    {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_DAMAGE, 0.0f);
        builder.define(DATA_THRUSTING, false);
    }

    public float getDamage() { return entityData.get(DATA_DAMAGE); }

    public void setDamage(float damage) { entityData.set(DATA_DAMAGE, Math.max(0.0f, damage)); }

    /** synced: the pilot is firing the thrusters right now (for flame / particles) */
    public boolean isThrusting() { return entityData.get(DATA_THRUSTING); }

    /** local gauge 0..1 (only meaningful on the pilot's client) */
    public float thrustFraction() { return thrust / (float) THRUST_TICKS; }

    @Override
    public float maxUpStep() { return 1.0f; }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick()
    {
        super.tick();
        tickLerp();
        LivingEntity rider = getControllingPassenger();
        if (isControlledByLocalInstance())
        {
            if (rider != null && level().isClientSide) setYRot(rider.getYRot());
            drive();
            move(MoverType.SELF, getDeltaMovement());
        }
        else
        {
            setDeltaMovement(Vec3.ZERO);
        }
        updateLimbs();
        if (!level().isClientSide && rider instanceof Player player)
        {
            player.setAirSupply(player.getMaxAirSupply());
            player.resetFallDistance();
        }
        resetFallDistance();
        checkInsideBlocks();
    }

    private void updateLimbs()
    {
        if (!lastInit)
        {
            lastX = getX();
            lastZ = getZ();
            lastInit = true;
        }
        double dx = getX() - lastX, dz = getZ() - lastZ;
        lastX = getX();
        lastZ = getZ();
        float target = Math.min(1.0f, (float) Math.sqrt(dx * dx + dz * dz) * 4.0f);
        limbPosO = limbPos;
        limbSpeedO = limbSpeed;
        limbSpeed += (target - limbSpeed) * 0.4f;
        limbPos += limbSpeed;
    }

    /** controlling side: gravity always (water too), walking from the pilot's input, thrusters in water */
    private void drive()
    {
        LivingEntity rider = getControllingPassenger();
        int[] in = rider != null && level().isClientSide ? pilot.input() : NO_INPUT;
        Vec3 v = getDeltaMovement();
        boolean ground = onGround();
        double x = v.x, z = v.z, y = v.y;
        if (in[0] != 0 || in[1] != 0)
        {
            float yaw = (rider != null ? rider.getYRot() : getYRot()) * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), lx = Mth.cos(yaw), lz = Mth.sin(yaw);
            double tx = fx * in[0] + lx * in[1], tz = fz * in[0] + lz * in[1];
            double len = Math.sqrt(tx * tx + tz * tz);
            double speed = ground ? WALK_SPEED : AIR_SPEED;
            tx = tx / len * speed;
            tz = tz / len * speed;
            double k = ground ? 0.6 : 0.3;
            x += (tx - x) * k;
            z += (tz - z) * k;
        }
        else
        {
            double drag = ground ? 0.5 : 0.9;
            x *= drag;
            z *= drag;
        }
        boolean thrusting = false;
        if (level().isClientSide)
        {
            if (rider != null && in[2] > 0 && thrust > 0 && isInWater())
            {
                thrusting = true;
                thrust--;
                y = Math.min(y + THRUST_ACCEL, THRUST_MAX_UP);
            }
            else if (ground && thrust < THRUST_TICKS) thrust = Math.min(THRUST_TICKS, thrust + 2);
        }
        y = Math.max(y - GRAVITY, -TERMINAL);
        if (isInWater()) y *= 0.98;
        setDeltaMovement(x, y, z);
        if (level().isClientSide && rider != null && isThrusting() != thrusting) entityData.set(DATA_THRUSTING, thrusting);
    }

    private void tickLerp()
    {
        if (isControlledByLocalInstance())
        {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0)
        {
            double x = getX() + (lerpX - getX()) / lerpSteps;
            double y = getY() + (lerpY - getY()) / lerpSteps;
            double z = getZ() + (lerpZ - getZ()) / lerpSteps;
            setYRot(getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
            setXRot(getXRot() + (float) (lerpXRot - getXRot()) / lerpSteps);
            lerpSteps--;
            setPos(x, y, z);
            setRot(getYRot(), getXRot());
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps)
    {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = Math.max(1, steps);
    }

    // ---------------------------------------------------------------- riding

    @Override
    @Nullable
    public LivingEntity getControllingPassenger()
    {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger)
    {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick)
    {
        return new Vec3(0.0, SEAT_Y + entity.getVehicleAttachmentPoint(this).y, 0.0);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move)
    {
        if (!hasPassenger(passenger)) return;
        move.accept(passenger, getX(), getY() + SEAT_Y, getZ());
        if (passenger instanceof LivingEntity living) living.setYBodyRot(getYRot());
    }

    @Override
    public boolean canBeRiddenUnderFluidType(FluidType type, Entity rider) { return true; }

    @Override
    public boolean dismountsUnderwater() { return false; }

    @Override
    public boolean shouldRiderSit() { return true; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        if (player.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (!level().isClientSide) return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- damage

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (isInvulnerableTo(source)) return false;
        Entity attacker = source.getEntity();
        if (attacker != null && hasPassenger(attacker)) return false;
        if (level().isClientSide || isRemoved()) return true;
        boolean creative = attacker instanceof Player player && player.getAbilities().instabuild;
        boolean sneaking = attacker instanceof Player player && player.isShiftKeyDown();
        if (creative || sneaking)
        {
            if (!creative) pop();
            discard();
            return true;
        }
        setDamage(getDamage() + amount * 5.0f);
        markHurt();
        gameEvent(GameEvent.ENTITY_DAMAGE, attacker);
        if (getDamage() >= MAX_DAMAGE)
        {
            pop();
            discard();
        }
        return true;
    }

    private void pop()
    {
        if (level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) spawnAtLocation(new ItemStack(VehicleContent.EXOSUIT_ITEM.get()));
    }

    @Override
    public ItemStack getPickResult()
    {
        return new ItemStack(VehicleContent.EXOSUIT_ITEM.get());
    }

    // ---------------------------------------------------------------- collision / picking

    @Override
    public boolean isPickable()
    {
        return !isRemoved() && !(getFirstPassenger() instanceof Player player && player.isLocalPlayer());
    }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(1.0); }

    // ---------------------------------------------------------------- save

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        tag.putFloat("Damage", getDamage());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        setDamage(tag.getFloat("Damage"));
    }
}
