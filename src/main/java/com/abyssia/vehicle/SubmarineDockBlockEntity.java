package com.abyssia.vehicle;

import com.abyssia.Config;
import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.industry.energy.IndustryEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SUB02 dock: a 20,000 FE buffer that only receives (ENERGY on every face, so HabitatPower's wireless distribution and
 * cables fill it, like the charging station). Every 5 ticks it docks one submarine in reach (below the dock,
 * horizontally within dock_capture_range of its centre, down to 6 blocks); the docked submarine draws its charge from
 * here ({@link Submarine} server tick, {@link #drain}).
 * <p>
 * SUB04 animation state machine (shared contract with the NeoForge port - keep ids / timings / NBT identical;
 * poses live in tools/vehicle_models/dock/submarine_dock.parts.json "animation", client: DockRenderer / DockAnim):
 * <pre>
 *  NBT   "State" string IDLE|CAPTURE|DOCKED|RELEASE, "StateTicks" int (ticks since the state began, both sides count),
 *        "Gangway" long[] (BlockPos.asLong of the placed helper blocks), "Energy" int.
 *        Synced with the block update packet (getUpdateTag); a state change sends it.
 *  IDLE     arms open, gangway raised (-80 deg). A submarine captured (dockTo) -> CAPTURE.
 *  CAPTURE  30 ticks (upper links 0..18, lower 6..24, ease_in_out_cubic); then waits until the submarine has arrived
 *           (target +-0.15 blocks, yaw within 1 deg of FACING; the sub then holds yaw = FACING) -> DOCKED. The sub's yaw is turned to FACING while pulled.
 *  DOCKED   gangway lowers over ticks 6..20 (ease_out_cubic); at tick 20 the helper blocks are placed (walkable) and
 *           dismounting puts the player on the landing. Sub gone (Ctrl release / killed) -> RELEASE.
 *  RELEASE  34 ticks: the helper blocks are removed at once, gangway raises 0..14 (ease_in_cubic), lower links 14..28,
 *           upper links 20..34 (ease_in_out_cubic) -> IDLE.
 * </pre>
 * FACING (block state) = direction of the docked submarine's nose = dock frame -z; the gangway is on the frame's +x
 * (FACING.getClockWise()).
 */
public class SubmarineDockBlockEntity extends BlockEntity
{
    public static final int CAPACITY = 20_000;
    public static final int MAX_RECEIVE = 2_000;
    public static final int CAPTURE_INTERVAL = 5;

    public enum State { IDLE, CAPTURE, DOCKED, RELEASE }

    public static final int CAPTURE_TICKS = 30, GANGWAY_DELAY = 6, GANGWAY_TICKS = 14, RELEASE_TICKS = 34;
    /** DOCKED tick at which the gangway is fully down (walkable) */
    public static final int GANGWAY_DONE = GANGWAY_DELAY + GANGWAY_TICKS;
    /**
     * Where a docked player steps out: on the gangway deck (dock frame x 34 px, top y -24 px; the deck is 8 px above the
     * floor, so the head stays 11 px under the ceiling). v1's steps up to -4 px were not walkable (room is 3 blocks high).
     */
    private static final double LANDING_X = 34.0 / 16.0, LANDING_Y = -24.0 / 16.0;
    /** helper cells: {dx cells along the frame's +x, dy from the dock block, part} (see SubmarineDockGangwayBlock) */
    private static final int[][] GANGWAY_CELLS = {{2, -2, 0}, {3, -2, 1}, {4, -2, 3}};
    /** the fixed mount cell (visible always): part 2 up / part 3 with the deck end */
    private static final int[] MOUNT_CELL = {4, -2, 2};

    private final IndustryEnergyStorage energy = new IndustryEnergyStorage(CAPACITY, MAX_RECEIVE, 0, this::setChanged);
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);
    private State state = State.IDLE;
    private int stateTicks;
    private final List<BlockPos> gangway = new ArrayList<>();

    public SubmarineDockBlockEntity(BlockPos pos, BlockState state)
    {
        super(VehicleContent.SUBMARINE_DOCK_ENTITY.get(), pos, state);
    }

    /** the buffer (tests / tooling; the base uses the capability) */
    public IEnergyStorage energy()
    {
        return energy;
    }

    /** takes up to {@code max} FE for the docked submarine; returns what it got */
    public int drain(int max)
    {
        return energy.consume(max);
    }

    public State animState()
    {
        return state;
    }

    public int animTicks()
    {
        return stateTicks;
    }

    /** direction of the docked submarine's nose */
    public Direction facing()
    {
        BlockState bs = getBlockState();
        return bs.hasProperty(SubmarineDockBlock.FACING) ? bs.getValue(SubmarineDockBlock.FACING) : Direction.NORTH;
    }

    /** where a docked pilot / passenger steps out: the gangway landing while it is down, else null */
    @Nullable
    public Vec3 landing()
    {
        if (state != State.DOCKED || stateTicks < GANGWAY_DONE) return null;
        Direction right = facing().getClockWise();
        return new Vec3(worldPosition.getX() + 0.5 + right.getStepX() * LANDING_X, worldPosition.getY() + LANDING_Y,
                worldPosition.getZ() + 0.5 + right.getStepZ() * LANDING_X);
    }

    public void clientTick()
    {
        if (stateTicks < 100_000) stateTicks++;
    }

    public void serverTick()
    {
        if (level == null) return;
        long t = level.getGameTime() + worldPosition.asLong();
        if (t % 10 == 0) CableNetworkManager.touchAround(level, worldPosition);
        if (stateTicks < 100_000) stateTicks++;
        if (state == State.IDLE)
        {
            if (t % CAPTURE_INTERVAL == 0)
            {
                if (docked() == null) capture();
                if (docked() != null) setState(State.CAPTURE);
            }
        }
        else
        {
            Submarine sub = docked();
            if (state != State.RELEASE && sub == null) setState(State.RELEASE);
            else switch (state)
            {
                case CAPTURE ->
                {
                    if (stateTicks >= CAPTURE_TICKS && (arrived(sub) || stateTicks > 600))
                    {
                        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.7f);
                        setState(State.DOCKED);
                    }
                }
                case DOCKED ->
                {
                    if (stateTicks == GANGWAY_DONE)
                    {
                        placeDeck();
                        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
                    }
                }
                case RELEASE ->
                {
                    if (stateTicks >= RELEASE_TICKS) setState(State.IDLE);
                }
                default -> { }
            }
        }
        boolean down = state == State.DOCKED && stateTicks >= GANGWAY_DONE;
        if (!down) removeDeck();
        else if (stateTicks == GANGWAY_DONE || t % 20 == 0) placeDeck();
        if (t % 20 == 0) placeMount();
    }

    private void setState(State next)
    {
        if (next == state) return;
        state = next;
        stateTicks = 0;
        if (next == State.RELEASE)
        {
            removeDeck();
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.6f);
        }
        else if (next == State.CAPTURE) level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.8f, 0.6f);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** the submarine sits at its dock target, nose along FACING */
    private boolean arrived(@Nullable Submarine sub)
    {
        if (sub == null) return false;
        Vec3 d = Submarine.dockTarget(worldPosition).subtract(sub.position());
        return Math.abs(d.x) < 0.15 && Math.abs(d.z) < 0.15 && Math.abs(d.y) < 0.15
                && Math.abs(Mth.wrapDegrees(sub.getYRot() - facing().toYRot())) <= 1.0f;
    }

    // ---------------------------------------------------------------- gangway helper blocks

    private void put(int[] c, int part)
    {
        Direction right = facing().getClockWise();
        BlockPos p = worldPosition.relative(right, c[0]).above(c[1]);
        if (!level.isLoaded(p)) return;
        BlockState at = level.getBlockState(p);
        BlockState want = VehicleContent.SUBMARINE_DOCK_GANGWAY.get().defaultBlockState()
                .setValue(SubmarineDockGangwayBlock.FACING, right).setValue(SubmarineDockGangwayBlock.PART, part);
        if (at.getBlock() instanceof SubmarineDockGangwayBlock)
        {
            if (at != want) level.setBlock(p, want, Block.UPDATE_ALL);
        }
        else if (at.isAir() && at.getFluidState().isEmpty()) level.setBlock(p, want, Block.UPDATE_ALL);
        else return;
        if (!gangway.contains(p)) gangway.add(p.immutable());
        setChanged();
    }

    /** the fixed mount's collision: exists whenever the dock does */
    private void placeMount()
    {
        if (level != null && !level.isClientSide) put(MOUNT_CELL, 2);
    }

    /** deck cells + the mount's deck end (gangway down) */
    private void placeDeck()
    {
        if (level == null || level.isClientSide) return;
        for (int[] c : GANGWAY_CELLS) put(c, c[2]);
    }

    /** gangway up: deck cells removed, the mount cell back to mount only */
    private void removeDeck()
    {
        if (level == null || level.isClientSide || gangway.isEmpty()) return;
        Direction right = facing().getClockWise();
        boolean[] changed = {false};
        gangway.removeIf(p ->
        {
            if (!level.isLoaded(p)) return false;
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof SubmarineDockGangwayBlock)) { changed[0] = true; return true; }
            int part = st.getValue(SubmarineDockGangwayBlock.PART);
            if (part == 3)
            {
                level.setBlock(p, st.setValue(SubmarineDockGangwayBlock.PART, 2), Block.UPDATE_ALL);
                return false;
            }
            if (part == 2) return false;
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            changed[0] = true;
            return true;
        });
        if (changed[0]) setChanged();
    }

    /** removes all helper blocks (dock removed); positions in unloaded chunks are kept for later */
    public void removeGangway()
    {
        if (level == null || level.isClientSide || gangway.isEmpty()) return;
        gangway.removeIf(p ->
        {
            if (!level.isLoaded(p)) return false;
            if (level.getBlockState(p).getBlock() instanceof SubmarineDockGangwayBlock)
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        });
        setChanged();
    }

    // ---------------------------------------------------------------- capture

    /** reach of the dock: below it, +-range around its centre, 6 blocks down */
    private AABB reach()
    {
        double r = Config.SUBMARINE_DOCK_CAPTURE_RANGE.get();
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        return new AABB(cx - r, worldPosition.getY() - Submarine.DOCK_REACH_DOWN, cz - r, cx + r, worldPosition.getY(), cz + r);
    }

    /** the submarine docked here, or null */
    @Nullable
    public Submarine docked()
    {
        if (level == null) return null;
        return level.getEntitiesOfClass(Submarine.class, reach().inflate(4.0), s -> worldPosition.equals(s.getDock().orElse(null)))
                .stream().findFirst().orElse(null);
    }

    private void capture()
    {
        double r = Config.SUBMARINE_DOCK_CAPTURE_RANGE.get();
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        level.getEntitiesOfClass(Submarine.class, reach(), s -> s.canDock()
                        && Math.abs(s.getX() - cx) <= r && Math.abs(s.getZ() - cz) <= r && s.getBoundingBox().maxY <= worldPosition.getY() + 0.01)
                .stream().min(Comparator.comparingDouble(s -> s.distanceToSqr(Submarine.dockTarget(worldPosition))))
                .ifPresent(s -> s.dockTo(worldPosition));
    }

    /** the whole animated dock: arms at +-80 px, beam z +-19 px, gangway to x 77 px, down to y -33 px */
    @Override
    public AABB getRenderBoundingBox()
    {
        return new AABB(worldPosition.getX() - 5.5, worldPosition.getY() - 3.0, worldPosition.getZ() - 5.5,
                worldPosition.getX() + 6.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 6.5);
    }

    // ---------------------------------------------------------------- capabilities

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps()
    {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energy);
    }

    // ---------------------------------------------------------------- save / sync

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putString("State", state.name());
        tag.putInt("StateTicks", stateTicks);
        tag.put("Gangway", new LongArrayTag(gangway.stream().mapToLong(BlockPos::asLong).toArray()));
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        energy.setEnergy(tag.getInt("Energy"));
        try
        {
            state = tag.contains("State", Tag.TAG_STRING) ? State.valueOf(tag.getString("State")) : State.IDLE;
        }
        catch (IllegalArgumentException e)
        {
            state = State.IDLE;
        }
        stateTicks = tag.getInt("StateTicks");
        gangway.clear();
        for (long l : tag.getLongArray("Gangway")) gangway.add(BlockPos.of(l));
    }

    @Override
    public CompoundTag getUpdateTag()
    {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
