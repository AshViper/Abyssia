package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * SUB02 one-seat submarine. Not a Boat (its surface buoyancy fights diving). Vanilla 1.20.1 vehicles are moved by the
 * pilot's client (ServerboundMoveVehiclePacket), so steering needs no mod packet: the controlling side runs
 * {@link #drive} / {@link #dockMove}. The server keeps energy, damage, headlight blocks and the HUD.
 * Model front = -Z (rendered with 180 - yaw, so it faces the entity's forward).
 */
public class Submarine extends Entity
{
    private static final EntityDataAccessor<Integer> DATA_ENERGY = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LIGHTS = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_DOCK = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** SUB03 installed upgrades, {@link SubmarineUpgrades.Kind#bit} mask (the pilot's client reads it for the speeds) */
    private static final EntityDataAccessor<Byte> DATA_UPGRADES = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.BYTE);

    /** break threshold without the Pressure Hull ({@link #maxDamage()} with upgrades) */
    public static final float MAX_DAMAGE = 40.0f;
    /** base values without upgrades ({@link SubmarineUpgrades} replaces / multiplies them); acceleration (blocks / tick^2), reverse / sideways / vertical top speeds, water drag per tick without input */
    public static final double ACCEL = 0.04, BACK_SPEED = 0.22, SIDE_SPEED = 0.22, VERTICAL_SPEED = 0.18, DRAG = 0.9;
    /** up-thrust (and neutral buoyancy) only from this submerged fraction of the hull: no jitter at the surface */
    public static final double LIFT_SUBMERGED = 0.6;
    /** seat: 8.5 px ahead of the centre (bbmodel z -11 px; the baked mesh is shifted +2.5 px), rider feet so the hips are at y 12 px */
    public static final double SEAT_FORWARD = 8.5 / 16.0, SEAT_Y = 12.0 / 16.0 - 0.70;
    /** pilot eye height above the hull bottom (seat + standing eye height) */
    public static final double EYE_Y = SEAT_Y + 1.62;
    /** model height (blocks): the docked hull top sits DOCK_GAP below the dock block */
    public static final double HULL_HEIGHT = 2.2733, DOCK_GAP = 0.3, DOCK_APPROACH = 0.12, DOCK_REACH_DOWN = 6.0;
    public static final int UNDOCK_COOLDOWN = 60;
    public static final double LIGHT_RANGE = 12.0;
    /** SUB04: yaw turn per tick while pulled into / released from a dock */
    public static final float DOCK_TURN = 6.0f;
    public static final float REPAIR_PER_TICK = 0.05f;
    /** SUB05: the hull pitches with the pilot's view, clamped to +-PITCH_MAX deg (xRot, + = nose down); docked / unmanned it eases to 0 at PITCH_EASE deg per tick */
    public static final float PITCH_MAX = 45.0f, PITCH_EASE = 6.0f;
    /** SUB05: pitch / seat pivot = hull mid-height (blocks above the origin); the renderer and positionRider rotate about (0, PIVOT_Y, 0) */
    public static final double PIVOT_Y = HULL_HEIGHT / 2.0;
    /** SUB05b: height above the rider's feet of the point that turns rigidly with the hull pitch = the rider's eye (the model tilt pivot, see SubmarineRiderRender) */
    public static final double SEAT_ANCHOR = 1.62;

    /** Pilot input on the client (set by the client setup; the server never asks). */
    public interface Pilot
    {
        /** {forward (+1 W / -1 S), strafe (+1 A / -1 D), undock (-1 while Ctrl is held; Space does nothing since SUB05)} */
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
    private boolean wasDocked, yawEase, pitchEase;
    private int undockRequestTicks;
    // server
    private int dockCooldown;
    private Vec3 lastServerPos;
    private boolean movingUp;
    private final List<BlockPos> placedLights = new ArrayList<>();
    private final Set<UUID> sonarSeen = new HashSet<>();
    @Nullable
    private Component sonarLine;

    public Submarine(EntityType<? extends Submarine> type, Level level)
    {
        super(type, level);
        blocksBuilding = true;
    }

    /** base battery size (Config); {@link #maxEnergy()} includes the battery upgrade */
    public static int capacity()
    {
        return Config.SUBMARINE_ENERGY_CAPACITY.get();
    }

    public int upgradeMask() { return entityData.get(DATA_UPGRADES); }

    public boolean has(SubmarineUpgrades.Kind kind) { return kind.in(upgradeMask()); }

    public int maxEnergy() { return SubmarineUpgrades.capacity(upgradeMask()); }

    public float maxDamage() { return SubmarineUpgrades.maxDamage(upgradeMask()); }

    // ---------------------------------------------------------------- synced data

    @Override
    protected void defineSynchedData()
    {
        entityData.define(DATA_ENERGY, 0);
        entityData.define(DATA_DAMAGE, 0.0f);
        entityData.define(DATA_LIGHTS, false);
        entityData.define(DATA_DOCK, Optional.empty());
        entityData.define(DATA_UPGRADES, (byte) 0);
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
            if (rider != null && level().isClientSide && dock.isEmpty())
            {
                followRiderYaw(rider.getYRot());
                // after a release the pitch swings to the view at PITCH_EASE per tick, like the yaw
                float want = Mth.clamp(rider.getXRot(), -PITCH_MAX, PITCH_MAX);
                if (wasDocked) pitchEase = true;
                if (pitchEase)
                {
                    float diff = want - getXRot();
                    if (Math.abs(diff) <= PITCH_EASE) pitchEase = false;
                    setXRot(Math.abs(diff) <= PITCH_EASE ? want : getXRot() + Math.signum(diff) * PITCH_EASE);
                }
                else setXRot(want);
            }
            else if (dock.isEmpty()) easePitch();
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
                if (!Float.isNaN(yaw))
                {
                    // same turn rate as the controlling side, so it never lags behind the server's lock
                    setYRot(getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - getYRot()), -DOCK_TURN, DOCK_TURN));
                    lerpYRot = getYRot();
                }
                easePitch();
                lerpXRot = getXRot();
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
            lerpSteps--;
            setPos(x, y, z);
            setXRot(getXRot() + (float) (lerpXRot - getXRot()) / (lerpSteps + 1));
            setRot(getYRot(), getXRot());
        }
    }

    /** SUB05: pitch back to level, PITCH_EASE deg per tick (docked, unmanned) */
    private void easePitch()
    {
        float x = getXRot();
        setXRot(Math.abs(x) <= PITCH_EASE ? 0.0f : x - Math.signum(x) * PITCH_EASE);
    }

    /** SUB05: unit vector of the hull's nose (yaw + pitch, xRot + = nose down) */
    public Vec3 noseVector()
    {
        float yaw = getYRot() * Mth.DEG_TO_RAD, pitch = getXRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport)
    {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = Math.max(1, steps);
    }

    /**
     * Controlling side (SUB05): W / S thrust along the hull's nose (yaw + pitch), A / D strafe horizontally, no vertical
     * keys. Velocity lives in the hull frame (nose, left, hull-up); the vertical component is capped at the upgrade's
     * vertical speed by scaling the whole vector (direction kept), and has no upward part below LIFT_SUBMERGED.
     */
    private void drive()
    {
        double wet = submergedFraction();
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 v = getDeltaMovement();
        int[] in = getControllingPassenger() != null && level().isClientSide && getEnergy() > 0 ? pilot.input() : NO_INPUT;
        if (wet > 0.0)
        {
            int mask = upgradeMask();
            double accel = SubmarineUpgrades.accel(mask), side = SubmarineUpgrades.sideSpeed(mask), up = SubmarineUpgrades.verticalSpeed(mask);
            Vec3 n = noseVector();
            Vec3 l = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
            Vec3 h = n.cross(l);
            double f = axis(v.dot(n), in[0], accel, SubmarineUpgrades.forwardSpeed(mask), side);
            double s = axis(v.dot(l), in[1], accel, side, side);
            double u = v.dot(h) * DRAG;
            v = n.scale(f).add(l.scale(s)).add(h.scale(u));
            if (Math.abs(v.y) > up) v = v.scale(up / Math.abs(v.y));
            // floating high at the surface: no upward part, settle gently until LIFT_SUBMERGED is under water (neutral below that)
            if (wet < LIFT_SUBMERGED)
            {
                // the upward part is dropped, the horizontal speed stays within the forward cap (no speed gained from the clip)
                double hz = Math.hypot(v.x, v.z), cap = SubmarineUpgrades.forwardSpeed(mask);
                double k = v.y > 0.0 && hz > cap ? cap / hz : 1.0;
                v = new Vec3(v.x * k, Math.min(v.y, 0.0) - 0.004, v.z * k);
            }
            setDeltaMovement(v);
            return;
        }
        // out of the water: no thrust, gravity, stops on the ground
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), lx = fz, lz = -fx;
        double f = v.x * fx + v.z * fz, s = v.x * lx + v.z * lz, u = v.y;
        double grip = onGround() ? 0.5 : 0.98;
        f *= grip;
        s *= grip;
        u = (u - 0.04) * 0.98;
        setDeltaMovement(fx * f + lx * s, u, fz * f + lz * s);
    }

    private static double axis(double v, int input, double accel, double maxPos, double maxNeg)
    {
        if (input == 0) return v * DRAG;
        return Mth.clamp(v + accel * input, -maxNeg, maxPos);
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

    /** SUB04: yaw follows the pilot; right after a release it swings there at DOCK_TURN per tick instead of snapping */
    private void followRiderYaw(float want)
    {
        if (wasDocked) yawEase = true;
        if (!yawEase)
        {
            setYRot(want);
            return;
        }
        float diff = Mth.wrapDegrees(want - getYRot());
        if (Math.abs(diff) <= DOCK_TURN)
        {
            setYRot(want);
            yawEase = false;
        }
        else setYRot(getYRot() + Math.signum(diff) * DOCK_TURN);
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
        easePitch();
        float yaw = dockYaw(dock);
        if (!Float.isNaN(yaw)) setYRot(getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - getYRot()), -DOCK_TURN, DOCK_TURN));
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
        else if (rider != null && moved > 0.01) energy -= SubmarineUpgrades.thrustFe(upgradeMask());
        if (lights())
        {
            energy -= Config.SUBMARINE_LIGHT_FE.get();
            if (energy <= 0 || rider == null) setLights(false);
        }
        boolean sonar = rider != null && has(SubmarineUpgrades.Kind.UTILITY) && energy > 0;
        if (sonar) energy -= Config.SUBMARINE_SONAR_FE.get();
        setEnergy(energy);
        if (tickCount % 2 == 0 || !lights()) updateLights();
        if (!sonar)
        {
            sonarLine = null;
            sonarSeen.clear();
        }
        else if (sonarLine == null || tickCount % Config.SUBMARINE_SONAR_INTERVAL.get() == 0) sonarLine = SubmarineSonar.scan(this, sonarSeen);

        if (rider instanceof Player player)
        {
            player.setAirSupply(player.getMaxAirSupply());
            if (tickCount % 10 == 0) player.displayClientMessage(hud(), true);
        }
    }

    private Component hud()
    {
        int energy = Math.round(100.0f * getEnergy() / Math.max(1, maxEnergy()));
        MutableComponent line = Component.translatable("message." + Abyssia.MODID + ".submarine.hud", energy, hullPercent());
        if (getDock().isPresent()) line.append(Component.translatable("message." + Abyssia.MODID + ".submarine.docked"));
        if (sonarLine != null) line.append(sonarLine);
        return line;
    }

    /** remaining hull, 0..100 % of {@link #maxDamage()} */
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

    // ---------------------------------------------------------------- headlights (light blocks)

    /**
     * Light blocks ahead: at the end of a ray from the pilot's eye along the yaw (12 blocks, stops before the first
     * solid block) and half way. Only air / water source cells take one; only cells this submarine filled are cleared.
     */
    private void updateLights()
    {
        Set<BlockPos> want = new LinkedHashSet<>();
        if (lights() && isAlive())
        {
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            Vec3 from = position().add(forward.scale(SEAT_FORWARD)).add(0.0, EYE_Y, 0.0);
            Vec3 to = from.add(forward.scale(LIGHT_RANGE));
            BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation().subtract(forward.scale(0.5));
            if (end.distanceTo(from) > 1.0)
            {
                want.add(BlockPos.containing(end));
                want.add(BlockPos.containing(from.add(end).scale(0.5)));
            }
        }
        for (Iterator<BlockPos> it = placedLights.iterator(); it.hasNext(); )
        {
            BlockPos pos = it.next();
            if (want.contains(pos)) continue;
            removeLight(pos);
            it.remove();
        }
        for (BlockPos pos : want)
            if (!placedLights.contains(pos) && placeLight(pos)) placedLights.add(pos);
    }

    private boolean placeLight(BlockPos pos)
    {
        if (!level().isLoaded(pos)) return false;
        BlockState state = level().getBlockState(pos);
        boolean water = state.is(Blocks.WATER) && state.getFluidState().isSource();
        if (!state.isAir() && !water) return false;
        return level().setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15).setValue(LightBlock.WATERLOGGED, water),
                Block.UPDATE_ALL);
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
        if (reason.shouldDestroy() && !level().isClientSide)
            for (Pod pod : pods)
            {
                Containers.dropContents(level(), blockPosition(), pod);
                pod.clearContent();
            }
        // creative removal / kill: the upgrades drop like the pod contents (a normal break moved them into the item)
        if (reason.shouldDestroy() && !level().isClientSide)
        {
            Containers.dropContents(level(), blockPosition(), upgrades);
            upgrades.clearContent();
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

    @Override
    protected void positionRider(Entity passenger, MoveFunction move)
    {
        if (!hasPassenger(passenger)) return;
        float yaw = getYRot() * Mth.DEG_TO_RAD, pitch = getXRot() * Mth.DEG_TO_RAD;
        // SUB05b: the pilot's eye (SEAT_ANCHOR above the feet) is the seat's head point turned with the hull pitch about the
        // pivot, and the rider model is tilted about that eye (SubmarineRiderRender). Pitch 0 = the old seat.
        double a = SEAT_FORWARD, b = SEAT_Y - PIVOT_Y + SEAT_ANCHOR;
        double ahead = a * Mth.cos(pitch) + b * Mth.sin(pitch), up = -a * Mth.sin(pitch) + b * Mth.cos(pitch);
        move.accept(passenger, getX() - Mth.sin(yaw) * ahead, getY() + PIVOT_Y + up - SEAT_ANCHOR, getZ() + Mth.cos(yaw) * ahead);
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

    // ---------------------------------------------------------------- side storage pods

    /** 27-slot pod inventory; valid while the submarine lives and the player is within 8 blocks; chest sounds */
    private final class Pod extends SimpleContainer
    {
        Pod() { super(27); }

        @Override
        public boolean stillValid(Player player)
        {
            return isAlive() && player.distanceToSqr(Submarine.this) <= 64.0;
        }

        @Override
        public void startOpen(Player player)
        {
            if (!level().isClientSide) level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f, 0.9f);
        }

        @Override
        public void stopOpen(Player player)
        {
            if (!level().isClientSide) level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, 0.9f);
        }

        /** with slot indexes (SimpleContainer's own list has none, so a reload packed the items to the front) */
        @Override
        public net.minecraft.nbt.ListTag createTag()
        {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < getContainerSize(); i++)
            {
                ItemStack stack = getItem(i);
                if (stack.isEmpty()) continue;
                CompoundTag tag = new CompoundTag();
                tag.putByte("Slot", (byte) i);
                stack.save(tag);
                list.add(tag);
            }
            return list;
        }

        @Override
        public void fromTag(net.minecraft.nbt.ListTag list)
        {
            clearContent();
            for (int i = 0; i < list.size(); i++)
            {
                CompoundTag tag = list.getCompound(i);
                ItemStack stack = ItemStack.of(tag);
                int slot = tag.getByte("Slot") & 255;
                if (tag.contains("Slot") && slot < getContainerSize()) setItem(slot, stack);
                else addItem(stack);
            }
        }
    }

    private final Pod[] pods = {new Pod(), new Pod()};

    /** pod inventory (0 = right, 1 = left) */
    public SimpleContainer pod(int side)
    {
        return pods[side];
    }

    /** SUB03 upgrade slots (see {@link SubmarineUpgrades}): fixed kind per slot, stack 1, unmanned and within 8 blocks */
    private final class Upgrades extends SimpleContainer
    {
        Upgrades() { super(SubmarineUpgrades.SLOTS); }

        @Override
        public boolean stillValid(Player player)
        {
            return isAlive() && !isVehicle() && player.distanceToSqr(Submarine.this) <= 64.0;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack)
        {
            return SubmarineUpgrades.slotOf(stack) == slot;
        }

        @Override
        public int getMaxStackSize()
        {
            return 1;
        }

        @Override
        public void setChanged()
        {
            super.setChanged();
            if (level().isClientSide) return;
            entityData.set(DATA_UPGRADES, (byte) SubmarineUpgrades.maskOf(this));
            // battery taken out: anything above the plain capacity is lost
            setEnergy(getEnergy());
        }
    }

    private final Upgrades upgrades = new Upgrades();

    /** the 4 upgrade slots (0 hull, 1 battery, 2 thruster, 3 utility) */
    public SimpleContainer upgrades()
    {
        return upgrades;
    }

    private void openUpgrades(ServerPlayer player)
    {
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new SubmarineUpgradeMenu(id, inv, upgrades, this),
                Component.translatable("container." + Abyssia.MODID + ".submarine.upgrades")), buf -> buf.writeVarInt(getId()));
    }

    private void openPod(ServerPlayer player, int side)
    {
        Component title = Component.translatable("container." + Abyssia.MODID + (side == SubmarinePods.RIGHT ? ".submarine.pod_right" : ".submarine.pod_left"));
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, pods[side]), title));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        // SUB03: sneak + right-click from outside (unmanned, docked or not) opens the upgrade screen, before pods / boarding
        if (player.isShiftKeyDown())
        {
            if (isVehicle() || player.isPassenger()) return InteractionResult.PASS;
            if (player instanceof ServerPlayer sp) openUpgrades(sp);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!player.isPassenger())
        {
            Vec3 eye = player.getEyePosition().subtract(position());
            Vec3 look = player.getViewVector(1.0f);
            int side = SubmarinePods.pick(getYRot(), getXRot(), eye.x, eye.y, eye.z, look.x, look.y, look.z, 5.0);
            if (side != SubmarinePods.NONE)
            {
                if (player instanceof ServerPlayer sp) openPod(sp, side);
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
        // SUB03 Pressure Hull: less damage in the deep layer (rounded up, so a hit never becomes 0)
        if (has(SubmarineUpgrades.Kind.HULL) && getY() <= DeepLayer.TOP_Y)
            damage = (float) Math.ceil(damage * Config.SUBMARINE_HULL_DEEP_REDUCTION.get());
        setDamage(getDamage() + damage);
        markHurt();
        gameEvent(GameEvent.ENTITY_DAMAGE, attacker);
        boolean creative = attacker instanceof Player player && player.getAbilities().instabuild;
        if (creative || getDamage() > maxDamage())
        {
            if (!creative && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS))
            {
                spawnAtLocation(toItem());
                upgrades.clearContent();   // they ride in the item
            }
            discard();
        }
        return true;
    }

    /** the item this submarine breaks into (keeps its energy and upgrades; damage is not kept, as in SUB02) */
    public ItemStack toItem()
    {
        ItemStack stack = new ItemStack(VehicleContent.SUBMARINE_ITEM.get());
        SubmarineItem.setUpgrades(stack, SubmarineUpgrades.write(upgrades));
        SubmarineItem.setEnergy(stack, getEnergy());
        return stack;
    }

    /** server, on placing: upgrades first (they set the capacity), then energy; the hull starts at 0 damage */
    public void loadFromItem(ItemStack stack)
    {
        SubmarineUpgrades.read(SubmarineItem.getUpgrades(stack), upgrades);
        setEnergy(SubmarineItem.getEnergy(stack));
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
        tag.putInt("Energy", getEnergy());
        tag.putFloat("Damage", getDamage());
        tag.putBoolean("Lights", lights());
        getDock().ifPresent(pos -> tag.putLong("Dock", pos.asLong()));
        tag.putLongArray("PlacedLights", placedLights.stream().mapToLong(BlockPos::asLong).toArray());
        tag.put("PodRight", pods[0].createTag());
        tag.put("PodLeft", pods[1].createTag());
        tag.put(SubmarineUpgrades.TAG, SubmarineUpgrades.write(upgrades));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        SubmarineUpgrades.read(tag.getCompound(SubmarineUpgrades.TAG), upgrades);
        setEnergy(tag.getInt("Energy"));
        setDamage(tag.getFloat("Damage"));
        setLights(tag.getBoolean("Lights"));
        entityData.set(DATA_DOCK, tag.contains("Dock") ? Optional.of(BlockPos.of(tag.getLong("Dock"))) : Optional.empty());
        placedLights.clear();
        // cells left from before the unload: the next server tick clears whatever the beam no longer covers
        for (long l : tag.getLongArray("PlacedLights")) placedLights.add(BlockPos.of(l));
        pods[0].fromTag(tag.getList("PodRight", Tag.TAG_COMPOUND));
        pods[1].fromTag(tag.getList("PodLeft", Tag.TAG_COMPOUND));
    }
}
