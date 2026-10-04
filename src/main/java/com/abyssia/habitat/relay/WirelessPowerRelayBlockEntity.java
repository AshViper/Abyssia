package com.abyssia.habitat.relay;

import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.habitat.power.HabitatPower;
import com.abyssia.industry.block.EnergyCableBlock;
import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.industry.energy.EnergyLookup;
import com.abyssia.industry.energy.IndustryEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * WR01 relay port. Endpoint kind is decided from the position every call:
 * <ul>
 *     <li>base endpoint (inside a registered module box): the ENERGY capability reads / writes the base's shared FE
 *     ({@link HabitatBases#energy}); the internal buffer is unused. Not exposed with a null side, so HabitatPower's
 *     own device scan never takes it for a battery (its tick would overwrite what the relay wrote).</li>
 *     <li>buffer endpoint (anywhere else): a 1,024 FE buffer, receive + extract on every face (cables see a buffer),
 *     and every tick up to 512 FE pushed into adjacent FE receivers that are neither cables nor relays.</li>
 * </ul>
 */
public class WirelessPowerRelayBlockEntity extends BlockEntity
{
    public static final int BUFFER = 1_024;
    public static final int IO = 512;
    public static final int PUSH = 512;

    private final IndustryEnergyStorage buffer = new IndustryEnergyStorage(BUFFER, IO, IO, this::setChanged);
    private final IEnergyStorage port = new Port();
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> port);

    public WirelessPowerRelayBlockEntity(BlockPos pos, BlockState state)
    {
        super(RelayContent.RELAY_ENTITY.get(), pos, state);
    }

    /** the internal buffer (buffer endpoints; RelayNetwork moves link power in and out of it directly) */
    public IndustryEnergyStorage buffer()
    {
        return buffer;
    }

    /** base (root module id) this relay feeds, or -1 = buffer endpoint (always -1 on the client) */
    public int base()
    {
        return level instanceof ServerLevel server ? RelayNetwork.baseAt(server, worldPosition) : -1;
    }

    public void serverTick()
    {
        if (level == null) return;
        if ((level.getGameTime() + worldPosition.asLong()) % 10 == 0) CableNetworkManager.touchAround(level, worldPosition);
        if (buffer.getEnergyStored() <= 0 || base() >= 0) return;
        List<IEnergyStorage> targets = new ArrayList<>(6);
        for (Direction dir : Direction.values())
        {
            BlockPos n = worldPosition.relative(dir);
            if (!level.isLoaded(n)) continue;
            Block block = level.getBlockState(n).getBlock();
            if (block instanceof EnergyCableBlock || block instanceof WirelessPowerRelayBlock || block instanceof WirelessPowerRelayTopBlock) continue;
            IEnergyStorage target = EnergyLookup.get(level, n, dir.getOpposite());
            if (target != null && target.canReceive()) targets.add(target);
        }
        int budget = Math.min(PUSH, buffer.getEnergyStored());
        int moved = 0;
        for (int i = 0; i < targets.size() && moved < budget; i++)
        {
            int share = (budget - moved) / (targets.size() - i);
            if (share <= 0) share = budget - moved;
            int got = targets.get(i).receiveEnergy(share, false);
            if (got > 0) moved += buffer.consume(got);
        }
    }

    /** The capability view: the base's shared FE for base endpoints, else the buffer. */
    private final class Port implements IEnergyStorage
    {
        @Override
        public int receiveEnergy(int max, boolean simulate)
        {
            int base = base();
            if (base < 0) return buffer.receiveEnergy(max, simulate);
            HabitatBases data = HabitatBases.get((ServerLevel) level);
            int stored = data.energy(base);
            int n = Math.max(0, Math.min(Math.min(max, IO), HabitatPower.CAPACITY - stored));
            if (n > 0 && !simulate) data.setEnergy(base, stored + n);
            return n;
        }

        @Override
        public int extractEnergy(int max, boolean simulate)
        {
            int base = base();
            if (base < 0) return buffer.extractEnergy(max, simulate);
            HabitatBases data = HabitatBases.get((ServerLevel) level);
            int stored = data.energy(base);
            int n = Math.max(0, Math.min(Math.min(max, IO), stored));
            if (n > 0 && !simulate) data.setEnergy(base, stored - n);
            return n;
        }

        @Override
        public int getEnergyStored()
        {
            int base = base();
            return base < 0 ? buffer.getEnergyStored() : HabitatBases.get((ServerLevel) level).energy(base);
        }

        @Override
        public int getMaxEnergyStored()
        {
            return base() < 0 ? BUFFER : HabitatPower.CAPACITY;
        }

        @Override
        public boolean canExtract()
        {
            return true;
        }

        @Override
        public boolean canReceive()
        {
            return true;
        }
    }

    // ---------------------------------------------------------------- capabilities

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ENERGY)
        {
            if (side == null && base() >= 0) return LazyOptional.empty();
            return energyCap.cast();
        }
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
        energyCap = LazyOptional.of(() -> port);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putInt("Buffer", buffer.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        buffer.setEnergy(tag.getInt("Buffer"));
    }
}
