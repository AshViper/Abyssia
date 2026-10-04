package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.registry.ModDataComponents;
import com.abyssia.worldgen.DeepLayer;
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
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * SUB02 one-seat submarine. Not a Boat (its surface buoyancy fights diving). Vanilla vehicles are moved by the
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
    /** SUB03 installed upgrades (SubmarineUpgrades.bit), set by the server; the pilot's client reads it for the speeds */
    private static final EntityDataAccessor<Byte> DATA_UPGRADES = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.BYTE);

    public static final float MAX_DAMAGE = 40.0f;
    /** acceleration (blocks / tick^2), reverse / sideways / vertical top speeds, water drag per tick without input */
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
    public static final float REPAIR_PER_TICK = 0.05f;

    /** Pilot input on the client (set by the client setup; the server never asks). */
    public interface Pilot
    {
        /** {forward (+1 W / -1 S), strafe (+1 A / -1 D), vertical (+1 Space / -1 Ctrl)} */
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
    private double lerpX, lerpY, lerpZ, lerpYRot;
    private boolean wasDocked;
    private int undockRequestTicks;
    // server
    private int dockCooldown;
    private Vec3 lastServerPos;
    private boolean movingUp;
    private final List<BlockPos> placedLights = new ArrayList<>();
    /** side storage pods: 0 = right (+x of the model), 1 = left; 27 slots each */
    private final SimpleContainer[] pods = {newPod(), newPod()};
    /** SUB03 upgrade slots (hull, power, thrust, utility); the server mirrors them into DATA_UPGRADES */
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
        double t = SubmarinePods.raycast(getYRot(), eye.x, eye.y, eye.z, look.x, look.y, look.z, player.entityInteractionRange(), which);
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
     * Top speeds {forward, reverse / sideways, vertical} and the acceleration (spec 1.3 order: base, the thruster
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
        return new double[]{forward, side, vertical, thruster ? Config.SUB_THRUSTER_ACCEL.get() : ACCEL};
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
            if (rider != null && level().isClientSide) setYRot(rider.getYRot());
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
        else setDeltaMovement(Vec3.ZERO);
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
        lerpSteps = Math.max(1, steps);
    }

    /** Controlling side: thrust in the hull's frame (forward, left, up), water drag, gravity out of the water. */
    private void drive()
    {
        double wet = submergedFraction();
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);   // forward
        double lx = fz, lz = -fx;                       // left
        Vec3 v = getDeltaMovement();
        double f = v.x * fx + v.z * fz, s = v.x * lx + v.z * lz, u = v.y;
        int[] in = getControllingPassenger() != null && level().isClientSide && getEnergy() > 0 ? pilot.input() : NO_INPUT;
        if (wet > 0.0)
        {
            double[] sp = speeds();
            f = axis(f, in[0], sp[0], sp[1], sp[3]);
            s = axis(s, in[1], sp[1], sp[1], sp[3]);
            int vertical = in[2] > 0 && wet < LIFT_SUBMERGED ? 0 : in[2];
            u = axis(u, vertical, sp[2], sp[2], sp[3]);
            // floating high at the surface: settle gently until LIFT_SUBMERGED is under water (neutral below that)
            if (vertical == 0 && wet < LIFT_SUBMERGED) u -= 0.004;
        }
        else
        {
            // out of the water: no thrust, gravity, stops on the ground
            double grip = onGround() ? 0.5 : 0.98;
            f *= grip;
            s *= grip;
            u = (u - 0.04) * 0.98;
        }
        setDeltaMovement(fx * f + lx * s, u, fz * f + lz * s);
    }

    private static double axis(double v, int input, double maxPos, double maxNeg, double accel)
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

    /** Controlling side while docked: slide to the target (sideways first, then up), then hold still. */
    private void dockMove(BlockPos dock)
    {
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
        if (tickCount % 2 == 0 || !lights()) updateLights();

        if (rider instanceof Player player)
        {
            player.setAirSupply(player.getMaxAirSupply());
            if (tickCount % 10 == 0) player.displayClientMessage(hud(), true);
        }
    }

    private Component hud()
    {
        MutableComponent line = Component.translatable("message." + Abyssia.MODID + ".submarine.hud", energyPercent(), hullPercent());
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
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * SEAT_FORWARD, SEAT_Y + entity.getVehicleAttachmentPoint(this).y, Mth.cos(yaw) * SEAT_FORWARD);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move)
    {
        if (!hasPassenger(passenger)) return;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        move.accept(passenger, getX() - Mth.sin(yaw) * SEAT_FORWARD, getY() + SEAT_Y, getZ() + Mth.cos(yaw) * SEAT_FORWARD);
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
        if (creative || getDamage() > maxDamage())
        {
            if (!creative && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS))
            {
                spawnAtLocation(toItem());
                upgrades.clearContent();   // kept in the item, not dropped by remove()
            }
            discard();
        }
        return true;
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
