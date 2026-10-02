package com.abyssia.industry.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.abyssia.industry.blockentity.IndustryBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The only place that looks up energy capabilities (so the NeoForge port changes one class). Lookups never load
 * chunks, and callers re-resolve every tick instead of keeping the storage.
 */
public final class EnergyLookup
{
    private EnergyLookup() {}

    /** Energy storage of the block entity at pos, seen through its face side; null if none or not loaded. */
    @Nullable
    public static IEnergyStorage get(Level level, BlockPos pos, @Nullable Direction side)
    {
        if (!level.isLoaded(pos)) return null;
        // NeoForge: block capabilities are looked up through the level (registered in RegisterCapabilitiesEvent)
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
    }

    /** For block shape updates: whether the block at pos offers energy through its face side. */
    public static boolean exposesEnergy(BlockGetter level, BlockPos pos, Direction side)
    {
        if (level instanceof LevelReader reader && !reader.hasChunkAt(pos)) return false;
        if (level instanceof Level lvl) return lvl.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null;
        // not a Level (e.g. a world-gen region): only our own blocks are known to offer energy
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof IndustryBlockEntity;
    }

    /**
     * Pushes up to max FE from source into the neighbours of pos that accept energy (consumersOnly: skip storages
     * that also give energy, so two batteries never ping-pong). Returns the amount moved.
     */
    public static int pushToNeighbours(Level level, BlockPos pos, IEnergyStorage source, int max, boolean consumersOnly)
    {
        List<IEnergyStorage> targets = new ArrayList<>(6);
        for (Direction dir : Direction.values())
        {
            IEnergyStorage target = get(level, pos.relative(dir), dir.getOpposite());
            if (target == null || target == source || !target.canReceive()) continue;
            if (consumersOnly && target.canExtract()) continue;
            targets.add(target);
        }
        // even split; what one neighbour refuses goes to the ones after it
        int budget = Math.min(max, source.extractEnergy(max, true));
        int moved = 0;
        for (int i = 0; i < targets.size() && moved < budget; i++)
        {
            int share = (budget - moved) / (targets.size() - i);
            if (share <= 0) share = budget - moved;
            moved += transfer(source, targets.get(i), share);
        }
        return moved;
    }

    /** extract(simulate) -> receive -> extract(what was accepted): never creates energy. */
    public static int transfer(IEnergyStorage from, IEnergyStorage to, int max)
    {
        int offer = from.extractEnergy(max, true);
        if (offer <= 0) return 0;
        int accepted = to.receiveEnergy(offer, false);
        if (accepted <= 0) return 0;
        return from.extractEnergy(accepted, false);
    }
}
