package com.abyssia.vehicle;

import com.abyssia.Config;
import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.industry.energy.IndustryEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * SUB02 dock: a 20,000 FE buffer that only receives (Capabilities.EnergyStorage.BLOCK on every face, registered in
 * {@link VehicleContent}, so HabitatPower's wireless distribution and cables fill it, like the charging station).
 * Every 5 ticks it docks one submarine in reach (below the dock, horizontally within dock_capture_range of its
 * centre, down to 6 blocks); the docked submarine draws its charge from here ({@link Submarine} server tick,
 * {@link #drain}).
 */
public class SubmarineDockBlockEntity extends BlockEntity
{
    public static final int CAPACITY = 20_000;
    public static final int MAX_RECEIVE = 2_000;
    public static final int CAPTURE_INTERVAL = 5;

    private final IndustryEnergyStorage energy = new IndustryEnergyStorage(CAPACITY, MAX_RECEIVE, 0, this::setChanged);

    public SubmarineDockBlockEntity(BlockPos pos, BlockState state)
    {
        super(VehicleContent.SUBMARINE_DOCK_ENTITY.get(), pos, state);
    }

    /** the buffer (capability provider, tests / tooling) */
    public IEnergyStorage energy()
    {
        return energy;
    }

    /** takes up to {@code max} FE for the docked submarine; returns what it got */
    public int drain(int max)
    {
        return energy.consume(max);
    }

    public void serverTick()
    {
        if (level == null) return;
        long t = level.getGameTime() + worldPosition.asLong();
        if (t % 10 == 0) CableNetworkManager.touchAround(level, worldPosition);
        if (t % CAPTURE_INTERVAL == 0 && docked() == null) capture();
    }

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

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("Energy"));
    }
}
