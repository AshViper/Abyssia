package com.abyssia.industry.block;

import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.industry.energy.EnergyHooks;
import com.abyssia.industry.energy.EnergyLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Energy cable (128 FE/t, core 6..10) and reinforced energy cable (512 FE/t, core 5..11). No block entity: the
 * connected cables of either kind form one network in CableNetworkManager. Connects to every cable and to every
 * block exposing Capabilities.EnergyStorage.BLOCK on the facing side.
 */
public class EnergyCableBlock extends ConnectingBlock
{
    private final int rate;

    public EnergyCableBlock(Properties properties, int rate, double min, double max)
    {
        super(properties, min, max);
        this.rate = rate;
    }

    /** FE per tick this cable lets through; a network moves as much as its slowest cable. */
    public int rate()
    {
        return rate;
    }

    @Override
    protected boolean connectsTo(BlockGetter level, BlockPos pos, Direction dir)
    {
        BlockPos n = pos.relative(dir);
        BlockState other = level.getBlockState(n);
        if (other.getBlock() instanceof EnergyCableBlock) return true;
        // H08 (main d0ec43e): habitat hull shows an arm; the habitat port plugs HabitatBuilder.shell in here
        if (EnergyHooks.cableConnects.test(other)) return true;
        return EnergyLookup.exposesEnergy(level, n, dir.getOpposite());
    }

    @Override
    protected void onConnectionChanged(LevelAccessor level, BlockPos pos)
    {
        CableNetworkManager.invalidate(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved)
    {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(state.getBlock())) CableNetworkManager.invalidate(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock())) CableNetworkManager.invalidate(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
