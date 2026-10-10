package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.fauna.DepthZone;
import com.abyssia.registry.ModDataComponents;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * SUB02 one-seat submarine. Not a Boat (its surface buoyancy fights diving). Vanilla vehicles are moved by the
 * pilot's client (ServerboundMoveVehiclePacket), so steering needs no mod packet: the controlling side runs
 * {@link #drive} / {@link #dockMove}. The server keeps energy, damage and the HUD; the headlights are client spotlights (SubmarineLamps).
 * Model front = -Z (rendered with 180 - yaw, so it faces the entity's forward).
 */
public class Submarine extends Entity
{
    private static final EntityDataAccessor<Integer> DATA_ENERGY = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LIGHTS = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_DOCK = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** SUB03 installed upgrades (SubmarineUpgrades.bit), set by the server; the pilot's client reads it for the speeds */
    private static final EntityDataAccessor<Byte> DATA_UPGRADES = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.BYTE);

    public static final float MAX_DAMAGE = 40.0f;
    /** acceleration (blocks / tick^2), sideways / vertical top speeds, water drag per tick without input (SUB07 Seamoth ratios: forward 12.7 / sideways 11.5 / vertical 11 / reverse 5 m/s, longer glide) */
    public static final double ACCEL = 0.025, SIDE_SPEED = 0.38, VERTICAL_SPEED = 0.36, DRAG = 0.94;
    /** SUB07: reverse top speed = sideways x this (5 / 11.5) */
    public static final double REVERSE_RATIO = 5.0 / 11.5;
    /** SUB07: a climb faster than this (blocks / tick) breaches the surface and falls back like the Seamoth */
    public static final double BREACH_SPEED = 0.15;
    /** up-thrust (and neutral buoyancy) only from this submerged fraction of the hull: no jitter at the surface */
    public static final double LIFT_SUBMERGED = 0.6;
    /** seat: 8.5 px ahead of the centre (bbmodel z -11 px; the baked mesh is shifted +2.5 px), rider feet so the hips are at y 12 px */
    public static final double SEAT_FORWARD = 8.5 / 16.0, SEAT_Y = 12.0 / 16.0 - 0.70;
    /** pilot eye height above the hull bottom (seat + standing eye height) */
    public static final double EYE_Y = SEAT_Y + 1.62;
    /** model height (blocks): the docked hull top sits DOCK_GAP below the dock block */
    public static final double HULL_HEIGHT = 2.2733, DOCK_GAP = 0.3, DOCK_APPROACH = 0.12, DOCK_REACH_DOWN = 6.0;
    public static final int UNDOCK_COOLDOWN = 60;
    /** SUB04: yaw turn per tick while pulled into / released from a dock */
    public static final float DOCK_TURN = 6.0f;
    /** SUB05: the hull pitches with the pilot's view up to this many degrees (SUB07: 80); it tilts about the hull centre */
    public static final float MAX_PITCH = 80.0f;
    public static final double PIVOT_Y = HULL_HEIGHT / 2.0;
    /** SUB05b: the rider's eye height above the feet = the point the pilot's body tilts about (Forge uses the same class constant) */
    public static final double SEAT_ANCHOR = 1.62;
    public static final float REPAIR_PER_TICK = 0.05f;

    /** Pilot input on the client (set by the client setup; the server never asks). */
    public interface Pilot
    {
        /** {forward (+1 W / -1 S), strafe (+1 A / -1 D), vertical (+1 Space = hull up / -1 Ctrl = hull down; -1 also releases the dock)} */
        int[] input();

        void requestUndock(Submarine sub);
    }

    private static final int[] NO_INPUT = {0, 0, 0};
    public static Pilot pilot = new Pilot()
    {
        @Override public int[] input() { return NO_INPUT; }
        @Override public void requestUndock(Submarine sub) {}
    };

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot, lerpXRot;
    private boolean wasDocked, yawEase;
    private int undockRequestTicks;
    // server
    private int dockCooldown;
    private Vec3 lastServerPos;
    private boolean movingUp;
    private final List<BlockPos> placedLights = new ArrayList<>();
    /** side storage pods: 0 = right (+x of the model), 1 = left; 27 slots each */
    private final SimpleContainer[] pods = {newPod(), newPod()};
    /** SUB03 upgrade slots (hull, power, thrust, utility, depth); the server mirrors them into DATA_UPGRADES */
    private final SimpleContainer upgrades = new SimpleContainer(SubmarineUpgrades.SLOTS);
    /** sonar (server): last read-out appended to the HUD, creatures already reported */
    @Nullable
    private Component sonarLine;
    private final Set<UUID> sonarSeen = new HashSet<>();

    public Submarine(EntityType<? extends Submarine> type, Level level)
    {
        super(type, level);
        blocksBuilding = true;
        upgrades.addListener(c ->
        {
            if (level().isClientSide) return;
            entityData.set(DATA_UPGRADES, (byte) SubmarineUpgrades.mask(upgrades));
            // battery taken out: the charge above the base capacity is lost
            setEnergy(getEnergy());
        });
    }

    /** SUB03 upgrade slots (the "Submarine Systems" menu works on these directly) */
    public SimpleContainer upgrades()
    {
        return upgrades;
    }

    /** the synced upgrade bit mask (bits 0-4 installed, bits 5-6 Depth Hull tier) */
    public int upgradeMask()
    {
        return entityData.get(DATA_UPGRADES);
    }

    public boolean hasUpgrade(int slot)
    {
        return (entityData.get(DATA_UPGRADES) & SubmarineUpgrades.bit(slot)) != 0;
    }

    private SimpleContainer newPod()
    {
        return new SimpleContainer(27)
        {
            @Override
            public boolean stillValid(Player player)
            {
                return Submarine.this.isAlive() && player.distanceToSqr(Submarine.this) <= 64.0;
            }

            @Override
            public void startOpen(Player player)
            {
                if (!level().isClientSide) level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f, level().random.nextFloat() * 0.1f + 0.9f);
            }

            @Override
            public void stopOpen(Player player)
            {
                if (!level().isClientSide) level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, level().random.nextFloat() * 0.1f + 0.9f);
            }
        };
    }

    public SimpleContainer pod(int side)
    {
        return pods[side];
    }

    /** which pod (SubmarinePods.RIGHT / LEFT) the player's view ray hits, or -1 */
    private int podHit(Player player)
    {
        Vec3 eye = player.getEyePosition().subtract(position());
        Vec3 look = player.getViewVector(1.0f);
        int[] which = {-1};
        double t = SubmarinePods.raycast(getYRot(), getXRot(), eye.x, eye.y, eye.z, look.x, look.y, look.z, player.entityInteractionRange(), which);
        return t < 0 ? -1 : which[0];
    }

    /** base battery (no high-capacity battery) */
    public static int capacity()
    {
        return Config.SUBMARINE_ENERGY_CAPACITY.get();
    }

    /** battery with the installed upgrades */
    public int maxEnergy()
    {
        return capacity(hasUpgrade(SubmarineUpgrades.BATTERY));
    }

    public static int capacity(boolean battery)
    {
        return battery ? Math.max(capacity(), Config.SUB_BATTERY_CAPACITY.get()) : capacity();
    }

    /** breaking threshold of the hull with the installed upgrades */
    public float maxDamage()
    {
        return hasUpgrade(SubmarineUpgrades.HULL) ? Config.SUB_HULL_MAX_DAMAGE.get().floatValue() : MAX_DAMAGE;
    }

    /**
     * Top speeds {forward, sideways, vertical}, the acceleration and (SUB07) the reverse top speed (spec 1.3 order: base, the thruster
     * replaces, x hull, vertical x battery).
     */
    public double[] speeds()
    {
        boolean thruster = hasUpgrade(SubmarineUpgrades.THRUSTER);
        double forward = thruster ? Config.SUB_THRUSTER_FORWARD.get() : Config.SUBMARINE_MAX_SPEED.get();
        double side = thruster ? Config.SUB_THRUSTER_SIDE.get() : SIDE_SPEED;
        double vertical = thruster ? Config.SUB_THRUSTER_VERTICAL.get() : VERTICAL_SPEED;
        if (hasUpgrade(SubmarineUpgrades.HULL))
        {
            double m = Config.SUB_HULL_SPEED_MULT.get();
            forward *= m;
            side *= m;
            vertical *= m;
        }
        if (hasUpgrade(SubmarineUpgrades.BATTERY)) vertical *= Config.SUB_BATTERY_VERTICAL_MULT.get();
        return new double[]{forward, side, vertical, thruster ? Config.SUB_THRUSTER_ACCEL.get() : ACCEL, side * REVERSE_RATIO};
    }

    // ---------------------------------------------------------------- synced data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_ENERGY, 0);
        builder.define(DATA_DAMAGE, 0.0f);
        builder.define(DATA_LIGHTS, false);
        builder.define(DATA_DOCK, Optional.empty());
        builder.define(DATA_UPGRADES, (byte) 0);
    }

    public int getEnergy() { return entityData.get(DATA_ENERGY); }

    public void setEnergy(int energy) { entityData.set(DATA_ENERGY, Mth.clamp(energy, 0, maxEnergy())); }

    public float getDamage() { return entityData.get(DATA_DAMAGE); }

    public void setDamage(float damage) { entityData.set(DATA_DAMAGE, Math.max(0.0f, damage)); }

    public boolean lights() { return entityData.get(DATA_LIGHTS); }

    public void setLights(boolean on) { entityData.set(DATA_LIGHTS, on); }

    public Optional<BlockPos> getDock() { return entityData.get(DATA_DOCK); }

    /** server: dock to the dock block at {@code pos} */
    public void dockTo(BlockPos pos)
    {
        entityData.set(DATA_DOCK, Optional.of(pos.immutable()));
    }

    /** server: release from the dock; no re-capture for {@link #UNDOCK_COOLDOWN} ticks */
    public void undock()
    {
        if (getDock().isEmpty()) return;
        entityData.set(DATA_DOCK, Optional.empty());
        dockCooldown = UNDOCK_COOLDOWN;
    }

    /** server: a dock may take this submarine (not docked, no cooldown, unmanned or rising) */
    public boolean canDock()
    {
        return isAlive() && getDock().isEmpty() && dockCooldown <= 0 && (getControllingPassenger() == null || movingUp);
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick()
    {
        super.tick();
        tickLerp();
        Optional<BlockPos> dock = getDock();
        LivingEntity rider = getControllingPassenger();
        if (isControlledByLocalInstance())
        {
            if (dock.isEmpty())
            {
                if (rider != null && level().isClientSide) followRider(rider.getYRot(), rider.getXRot());
                else if (rider == null) easePitch(0.0f);
            }
            if (dock.isPresent())
            {
                dockMove(dock.get());
                if (rider != null && level().isClientSide && pilot.input()[2] < 0 && undockRequestTicks <= 0)
                {
                    pilot.requestUndock(this);
                    undockRequestTicks = 10;
                }
            }
            else
            {
                if (wasDocked)
                {
                    // just released: a small push down, out of the clamp
                    Vec3 v = getDeltaMovement();
                    setDeltaMovement(v.x, -0.15, v.z);
                }
                drive();
            }
            move(MoverType.SELF, getDeltaMovement());
        }
        else
        {
            setDeltaMovement(Vec3.ZERO);
            // SUB04: a docked hull is locked to the dock's facing on every side (the controlling side turns it in dockMove)
            if (dock.isPresent())
            {
                float yaw = dockYaw(dock.get());
                if (!Float.isNaN(yaw) && Math.abs(Mth.wrapDegrees(yaw - getYRot())) <= 12.0f)
                {
                    setYRot(yaw);
                    lerpYRot = yaw;
                }
            }
        }
        if (undockRequestTicks > 0) undockRequestTicks--;
        wasDocked = dock.isPresent();
        if (!level().isClientSide) serverTick();
        checkInsideBlocks();
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

    /** the nose direction (yaw + pitch); pitch > 0 looks down */
    public static Vec3 nose(float yawDeg, float pitchDeg)
    {
        float yaw = yawDeg * Mth.DEG_TO_RAD, pitch = pitchDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
    }

    /** SUB05 controlling side: W/S thrust along the nose, A/D sideways, Space/Ctrl along the hull's up axis (tilts with the pitch), water drag, gravity out of the water. SUB07: each axis is capped on its own (vector addition, a diagonal is faster); a fast climb at the surface breaches and falls back. */
    private void drive()
    {
        double wet = submergedFraction();
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 n = nose(getYRot(), getXRot());
        double lx = Mth.cos(yaw), lz = Mth.sin(yaw);   // left (horizontal)
        float pitch = getXRot() * Mth.DEG_TO_RAD;
        // hull up = nose x left: straight up at pitch 0, leans toward the nose when the nose points down
        double ux = -Mth.sin(pitch) * Mth.sin(yaw), uy = Mth.cos(pitch), uz = Mth.sin(pitch) * Mth.cos(yaw);
        Vec3 v = getDeltaMovement();
        // nose / left / up are orthonormal, so the velocity splits into them with nothing left over
        double f = v.x * n.x + v.y * n.y + v.z * n.z, s = v.x * lx + v.z * lz, g = v.x * ux + v.y * uy + v.z * uz;
        int[] in = getControllingPassenger() != null && level().isClientSide && getEnergy() > 0 ? pilot.input() : NO_INPUT;
        double x, y, z;
        if (wet > 0.0)
        {
            double[] sp = speeds();
            // along the nose at most the forward / reverse speed; the axes are independent (SUB07)
            f = axis(f, in[0], sp[0], sp[4], sp[3]);
            s = axis(s, in[1], sp[1], sp[1], sp[3]);
            // 上昇 / 下降 (Space / Ctrl): along the hull's up axis, drag when no key is held
            g = axis(g, in[2], sp[2], sp[2], sp[3]);
            x = n.x * f + lx * s + ux * g;
            y = n.y * f + uy * g;
            z = n.z * f + lz * s + uz * g;
            // floating high at the surface: no rising until LIFT_SUBMERGED is under water, settle gently (neutral below that)
            if (wet < LIFT_SUBMERGED)
            {
                // SUB07 a fast climb breaches and falls back like the Seamoth; slow climbs still settle without jitter
                if (v.y > BREACH_SPEED) y = v.y * 0.98 - 0.04 * (1.0 - wet / LIFT_SUBMERGED) - 0.004;
                else y = Math.min(y, 0.0) - 0.004;
                // the dropped upward part must not turn into extra speed: horizontal speed stays within the forward max
                double h = Math.sqrt(x * x + z * z);
                if (h > sp[0])
                {
                    x *= sp[0] / h;
                    z *= sp[0] / h;
                }
            }
        }
        else
        {
            // out of the water: no thrust, gravity, stops on the ground
            double grip = onGround() ? 0.5 : 0.98;
            x = v.x * grip;
            z = v.z * grip;
            y = (v.y - 0.04) * 0.98;
        }
        setDeltaMovement(x, y, z);
    }

    private static double axis(double v, int input, double maxPos, double maxNeg, double accel)
    {
        if (input == 0) return v * DRAG;
        // SUB07 soft cap: already faster than the max (e.g. after the hull turned) glides down instead of snapping
        double nv = v + accel * input;
        if (nv > maxPos) return Math.max(maxPos, v * DRAG);
        if (nv < -maxNeg) return Math.min(-maxNeg, v * DRAG);
        return nv;
    }

    /** Fraction (0..1) of the hull height in water, sampled down the centre line. */
    public double submergedFraction()
    {
        AABB box = getBoundingBox();
        int n = 8, wet = 0;
        for (int i = 0; i < n; i++)
        {
            double y = box.minY + (i + 0.5) * box.getYsize() / n;
            BlockPos pos = BlockPos.containing(getX(), y, getZ());
            FluidState fluid = level().getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && y <= pos.getY() + fluid.getHeight(level(), pos)) wet++;
        }
        return wet / (double) n;
    }

    /** Where the docked hull rests: centred under the dock, its top DOCK_GAP below the dock block. */
    public static Vec3 dockTarget(BlockPos dock)
    {
        return new Vec3(dock.getX() + 0.5, dock.getY() - DOCK_GAP - HULL_HEIGHT, dock.getZ() + 0.5);
    }

    /** SUB04/05: yaw and pitch (clamped) follow the pilot; right after a release they swing there at DOCK_TURN per tick instead of snapping */
    private void followRider(float wantYaw, float wantPitch)
    {
        wantPitch = Mth.clamp(wantPitch, -MAX_PITCH, MAX_PITCH);
        if (wasDocked) yawEase = true;
        if (!yawEase)
        {
            setYRot(wantYaw);
            setXRot(wantPitch);
            return;
        }
        float diff = Mth.wrapDegrees(wantYaw - getYRot()), pdiff = wantPitch - getXRot();
        if (Math.abs(diff) <= DOCK_TURN && Math.abs(pdiff) <= DOCK_TURN)
        {
            setYRot(wantYaw);
            setXRot(wantPitch);
            yawEase = false;
        }
        else
        {
            setYRot(getYRot() + Mth.clamp(diff, -DOCK_TURN, DOCK_TURN));
            setXRot(getXRot() + Mth.clamp(pdiff, -DOCK_TURN, DOCK_TURN));
        }
    }

    /** SUB05: pitch eases to {@code want} at DOCK_TURN per tick */
    private void easePitch(float want)
    {
        setXRot(getXRot() + Mth.clamp(want - getXRot(), -DOCK_TURN, DOCK_TURN));
    }

    /**
     * SUB05: where the rider's feet are relative to the entity position: the seat tilted with the hull about the hull
     * centre (pitch > 0 = nose down).
     */
    public static Vec3 seatOffset(float yawDeg, float pitchDeg)
    {
        float yaw = yawDeg * Mth.DEG_TO_RAD, p = pitchDeg * Mth.DEG_TO_RAD;
        // the rider's eye point (SEAT_ANCHOR above the feet) is what turns with the hull about the pivot; the body is tilted
        // about that point by SubmarineRiderRender, so the whole pilot stays rigid with the hull. Pitch 0 = the old seat.
        double f0 = SEAT_FORWARD, u0 = SEAT_Y - PIVOT_Y + SEAT_ANCHOR;
        double fwd = f0 * Mth.cos(p) + u0 * Mth.sin(p);
        double up = -f0 * Mth.sin(p) + u0 * Mth.cos(p);
        return new Vec3(-Mth.sin(yaw) * fwd, PIVOT_Y + up - SEAT_ANCHOR, Mth.cos(yaw) * fwd);
    }

    /** SUB04: the dock's FACING (nose direction) as a yaw, or NaN when the block is not (yet) there */
    private float dockYaw(BlockPos dock)
    {
        BlockState state = level().getBlockState(dock);
        return state.getBlock() instanceof SubmarineDockBlock ? state.getValue(SubmarineDockBlock.FACING).toYRot() : Float.NaN;
    }

    /** Controlling side while docked: turn to the dock's facing, slide to the target (sideways first, then up), then hold still. */
    private void dockMove(BlockPos dock)
    {
        float yaw = dockYaw(dock);
        if (!Float.isNaN(yaw)) setYRot(getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - getYRot()), -DOCK_TURN, DOCK_TURN));
        easePitch(0.0f);
        Vec3 d = dockTarget(dock).subtract(position());
        Vec3 flat = new Vec3(d.x, 0.0, d.z);
        Vec3 step = flat.length() > 0.05 ? flat : d;
        double len = step.length();
        setDeltaMovement(len <= DOCK_APPROACH ? step : step.scale(DOCK_APPROACH / len));
    }

    // ---------------------------------------------------------------- server

    private void serverTick()
    {
        if (dockCooldown > 0) dockCooldown--;
        Vec3 pos = position();
        double moved = lastServerPos == null ? 0.0 : pos.distanceTo(lastServerPos);
        movingUp = lastServerPos != null && pos.y - lastServerPos.y > 0.005;
        lastServerPos = pos;

        LivingEntity rider = getControllingPassenger();
        int energy = getEnergy();
        Optional<BlockPos> dock = getDock();
        if (dock.isPresent())
        {
            BlockPos at = dock.get();
            if (!level().isLoaded(at) || !(level().getBlockEntity(at) instanceof SubmarineDockBlockEntity station)) undock();
            else
            {
                int want = Math.min(Config.SUBMARINE_DOCK_CHARGE_RATE.get(), maxEnergy() - energy);
                if (want > 0) energy += station.drain(want);
                if (getDamage() > 0.0f) setDamage(getDamage() - REPAIR_PER_TICK);
            }
        }
        else if (rider != null && moved > 0.01)
            energy -= hasUpgrade(SubmarineUpgrades.THRUSTER) ? Config.SUB_THRUSTER_FE.get() : Config.SUBMARINE_THRUST_FE.get();
        boolean sonar = rider != null && hasUpgrade(SubmarineUpgrades.SONAR) && energy > 0;
        if (sonar)
        {
            energy -= Config.SUB_SONAR_FE.get();
            if (tickCount % Config.SUB_SONAR_INTERVAL.get() == 0) sonarLine = SubmarineSonar.scan(this, sonarSeen);
        }
        else
        {
            sonarLine = null;
            sonarSeen.clear();
        }
        if (lights())
        {
            energy -= Config.SUBMARINE_LIGHT_FE.get();
            if (energy <= 0 || rider == null) setLights(false);
        }
        setEnergy(energy);
        if (tickCount % 20 == 0) updateLights();

        crush(rider);
        if (rider instanceof Player player)
        {
            player.setAirSupply(player.getMaxAirSupply());
            if (tickCount % 10 == 0) player.displayClientMessage(hud(), true);
        }
    }

    /** metres below the surface at the hull (DepthZone), and the rated depth of the installed Depth Hull */
    public int depthMetres() { return (int) Math.max(0.0, DepthZone.metres(level(), getY())); }

    public int ratedDepth() { return SubmarineUpgrades.ratedDepth(upgradeMask()); }

    /** SUB06: below the rated depth the hull takes crush damage once a second; a creative pilot is exempt (as in PressureGear) */
    private void crush(@Nullable LivingEntity rider)
    {
        if (tickCount % 20 != 0 || isRemoved() || getDock().isPresent()) return;
        if (rider instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        int depth = depthMetres(), rated = ratedDepth();
        if (depth <= rated) return;
        float damage = Config.SUBMARINE_CRUSH_DAMAGE.get().floatValue() + (depth - rated) / Config.SUBMARINE_CRUSH_STEP.get();
        setDamage(getDamage() + damage);
        markHurt();
        level().playSound(null, this, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5f, 0.6f);
        if (getDamage() > maxDamage()) breakApart();
    }

    private Component hud()
    {
        MutableComponent line = Component.translatable("message." + Abyssia.MODID + ".submarine.hud", energyPercent(), hullPercent());
        int depth = depthMetres(), rated = ratedDepth();
        line.append(Component.translatable("message." + Abyssia.MODID + ".submarine.depth", depth, rated)
                .withStyle(depth > rated ? ChatFormatting.RED : ChatFormatting.WHITE));
        if (getDock().isPresent()) line.append(Component.translatable("message." + Abyssia.MODID + ".submarine.docked"));
        if (sonarLine != null) line.append(sonarLine);
        return line;
    }

    public int energyPercent()
    {
        return Math.round(100.0f * getEnergy() / Math.max(1, maxEnergy()));
    }

    /** hull condition against the installed hull's threshold (70 with the pressure hull) */
    public int hullPercent()
    {
        float max = maxDamage();
        return Math.round(100.0f * (1.0f - Math.min(max, getDamage()) / max));
    }

    /** C2S (SubmarineLightPacket): the pilot flips the headlights. */
    public void toggleLights()
    {
        setLights(!lights() && getEnergy() > 0);
        updateLights();
    }

    // ---------------------------------------------------------------- headlights

    /**
     * The headlights are drawn on each client as shadowed spotlights (client/light/SpotlightProjector, vehicle/client/
     * SubmarineLamps); no blocks are placed any more. Light blocks an older version put ahead of the submarine
     * (saved in PlacedLights) are cleared here once they are loaded.
     */
    private void updateLights()
    {
        if (placedLights.isEmpty()) return;
        for (Iterator<BlockPos> it = placedLights.iterator(); it.hasNext(); )
        {
            BlockPos pos = it.next();
            if (!level().isLoaded(pos)) continue;
            removeLight(pos);
            it.remove();
        }
    }

    private void removeLight(BlockPos pos)
    {
        if (!level().isLoaded(pos)) return;
        BlockState state = level().getBlockState(pos);
        if (!state.is(Blocks.LIGHT)) return;
        level().setBlock(pos, state.getValue(LightBlock.WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
    }

    private void clearLights()
    {
        if (level().isClientSide) return;
        for (BlockPos pos : placedLights) removeLight(pos);
        placedLights.clear();
    }

    /** light cells currently placed (tests) */
    public List<BlockPos> placedLights()
    {
        return List.copyOf(placedLights);
    }

    @Override
    public void remove(RemovalReason reason)
    {
        // a chunk being unloaded is not edited: the cells stay in the save and are cleared after the next load
        if (reason != RemovalReason.UNLOADED_TO_CHUNK) clearLights();
        if (!level().isClientSide && reason.shouldDestroy())
            // the upgrades are already moved into the item when it broke into one (hurt): this drops them for a creative removal
            for (SimpleContainer box : new SimpleContainer[]{pods[0], pods[1], upgrades})
            {
                for (ItemStack stack : box.getItems()) Containers.dropItemStack(level(), getX(), getY() + 0.5, getZ(), stack);
                box.clearContent();
            }
        super.remove(reason);
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
    protected void removePassenger(Entity passenger)
    {
        super.removePassenger(passenger);
        if (!level().isClientSide && getPassengers().isEmpty())
        {
            setLights(false);
            clearLights();
        }
    }

    /** NeoForge 1.21: the attachment point matches positionRider (seat + the rider's own vehicle attachment) */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick)
    {
        Vec3 seat = seatOffset(getYRot(), getXRot());
        return new Vec3(seat.x, seat.y + entity.getVehicleAttachmentPoint(this).y, seat.z);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move)
    {
        if (!hasPassenger(passenger)) return;
        Vec3 seat = seatOffset(getYRot(), getXRot());
        move.accept(passenger, getX() + seat.x, getY() + seat.y, getZ() + seat.z);
        if (passenger instanceof LivingEntity living) living.setYBodyRot(getYRot());
    }

    @Override
    public boolean canBeRiddenUnderFluidType(FluidType type, Entity rider)
    {
        return true;
    }

    @Override
    public boolean dismountsUnderwater()
    {
        return false;
    }

    @Override
    public boolean shouldRiderSit()
    {
        return true;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger)
    {
        Optional<BlockPos> dock = getDock();
        if (dock.isPresent())
        {
            // SUB04: the gangway landing while it is down
            if (level().getBlockEntity(dock.get()) instanceof SubmarineDockBlockEntity station)
            {
                Vec3 landing = station.landing();
                if (landing != null) return landing;
            }
            Vec3 dry = dryFloor(dock.get(), passenger);
            if (dry != null) return dry;
        }
        // beside the hull (left, then right), else vanilla (on top)
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double side = getBbWidth() / 2.0 + passenger.getBbWidth();
        for (int dir : new int[]{1, -1})
        {
            Vec3 at = new Vec3(getX() + Mth.cos(yaw) * side * dir, getY() + 0.3, getZ() + Mth.sin(yaw) * side * dir);
            for (Pose pose : passenger.getDismountPoses())
                if (DismountHelper.canDismountTo(level(), passenger, passenger.getLocalBoundsForPose(pose).move(at)))
                {
                    passenger.setPose(pose);
                    return at;
                }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    /**
     * Dry floor next to the pool: a cell within 8 blocks of the dock column and up to 6 below it with a sturdy top
     * below and two dry, empty cells, outside the hull; the side the passenger faces wins, then the nearest.
     */
    @Nullable
    private Vec3 dryFloor(BlockPos dock, LivingEntity passenger)
    {
        Vec3 look = Vec3.directionFromRotation(0.0f, passenger.getYRot());
        AABB hull = getBoundingBox();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy >= -6; dy--)
            for (int dx = -8; dx <= 8; dx++)
                for (int dz = -8; dz <= 8; dz++)
                {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > 8.0 || dist < 1.0) continue;
                    feet.set(dock.getX() + dx, dock.getY() + dy, dock.getZ() + dz);
                    if (!standable(feet) || hull.intersects(new AABB(feet).inflate(-0.05))) continue;
                    double facing = (dx * look.x + dz * look.z) / dist;
                    double score = dist - 4.0 * facing + 0.1 * -dy;
                    if (score < bestScore)
                    {
                        bestScore = score;
                        best = feet.immutable();
                    }
                }
        return best == null ? null : new Vec3(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
    }

    private boolean standable(BlockPos feet)
    {
        BlockPos below = feet.below();
        BlockState floor = level().getBlockState(below);
        if (!floor.isFaceSturdy(level(), below, Direction.UP)) return false;
        for (BlockPos p : new BlockPos[]{feet, feet.above()})
        {
            BlockState state = level().getBlockState(p);
            if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level(), p).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        // SUB03: sneak + right-click on an unmanned submarine (docked too) opens the upgrade screen, before pods / boarding
        if (player.isSecondaryUseActive())
        {
            if (isVehicle() || hasPassenger(player)) return InteractionResult.PASS;
            if (!level().isClientSide) SubmarineUpgradeMenu.open(player, this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!hasPassenger(player))
        {
            int side = podHit(player);
            if (side >= 0)
            {
                if (!level().isClientSide)
                    player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, pods[side]),
                            Component.translatable("container." + Abyssia.MODID + (side == SubmarinePods.RIGHT ? ".submarine.pod_right" : ".submarine.pod_left"))));
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        if (isVehicle()) return InteractionResult.PASS;
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
        float damage = amount * 10.0f;
        // pressure hull: deep-layer hits reduced (rounded up, a hit never drops to 0)
        if (hasUpgrade(SubmarineUpgrades.HULL) && getY() <= DeepLayer.TOP_Y)
            damage = (float) Math.ceil(damage * Config.SUB_HULL_DEEP_REDUCTION.get());
        setDamage(getDamage() + damage);
        markHurt();
        gameEvent(GameEvent.ENTITY_DAMAGE, attacker);
        boolean creative = attacker instanceof Player player && player.getAbilities().instabuild;
        if (creative) discard();
        else if (getDamage() > maxDamage()) breakApart();
        return true;
    }

    /** the hull gave way: drops the item (keeps energy and upgrades) unless entity drops are off */
    private void breakApart()
    {
        if (level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS))
        {
            spawnAtLocation(toItem());
            upgrades.clearContent();   // kept in the item, not dropped by remove()
        }
        discard();
    }

    /** the item this submarine breaks into (keeps its energy and upgrades; damage is not carried over) */
    public ItemStack toItem()
    {
        ItemStack stack = new ItemStack(VehicleContent.SUBMARINE_ITEM.get());
        CompoundTag installed = SubmarineUpgrades.save(upgrades);
        if (!installed.isEmpty()) stack.set(ModDataComponents.SUBMARINE_UPGRADES.get(), installed);
        SubmarineItem.setEnergy(stack, getEnergy());
        return stack;
    }

    @Override
    public ItemStack getPickResult()
    {
        return new ItemStack(VehicleContent.SUBMARINE_ITEM.get());
    }

    // ---------------------------------------------------------------- collision / picking

    @Override
    public boolean isPickable()
    {
        // the local pilot's eye is inside the hull box: without this every click of theirs would hit the submarine
        return !isRemoved() && !(getFirstPassenger() instanceof Player player && player.isLocalPlayer());
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        // the nose and tail reach past the square hit box
        return getBoundingBox().inflate(1.0);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        tag.put(SubmarineUpgrades.TAG, SubmarineUpgrades.save(upgrades));
        tag.putInt("Energy", getEnergy());
        tag.putFloat("Damage", getDamage());
        tag.putBoolean("Lights", lights());
        getDock().ifPresent(pos -> tag.putLong("Dock", pos.asLong()));
        for (int side = 0; side < 2; side++)
        {
            CompoundTag podTag = new CompoundTag();
            ContainerHelper.saveAllItems(podTag, pods[side].getItems(), registryAccess());
            tag.put(side == SubmarinePods.RIGHT ? "RightPod" : "LeftPod", podTag);
        }
        tag.putLongArray("PlacedLights", placedLights.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        // upgrades first: the battery decides how much energy fits
        SubmarineUpgrades.load(tag.getCompound(SubmarineUpgrades.TAG), upgrades);
        entityData.set(DATA_UPGRADES, (byte) SubmarineUpgrades.mask(upgrades));
        setEnergy(tag.getInt("Energy"));
        setDamage(tag.getFloat("Damage"));
        setLights(tag.getBoolean("Lights"));
        entityData.set(DATA_DOCK, tag.contains("Dock") ? Optional.of(BlockPos.of(tag.getLong("Dock"))) : Optional.empty());
        for (int side = 0; side < 2; side++)
        {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound(side == SubmarinePods.RIGHT ? "RightPod" : "LeftPod"), items, registryAccess());
            for (int i = 0; i < 27; i++) pods[side].setItem(i, items.get(i));
        }
        placedLights.clear();
        // cells left from before the unload: the next server tick clears whatever the beam no longer covers
        for (long l : tag.getLongArray("PlacedLights")) placedLights.add(BlockPos.of(l));
    }
}