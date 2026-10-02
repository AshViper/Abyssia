package com.abyssia.industry.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Plug points for other features into the cable network, so they need not edit it. On main (Forge) the H08 habitat
 * code is called directly from EnergyCableBlock / CableNetworkManager; on NeoForge the habitat port sets these:
 * <ul>
 *   <li>{@link #cableConnects}: draw a cable arm towards this block (main: HabitatBuilder.shell(state))</li>
 *   <li>{@link #externalReceiver}: endpoint storage when the block has no energy capability
 *       (main: HabitatPower.externalReceiver(level, pos, side)); habitat changes call CableNetworkManager.markAllDirty</li>
 * </ul>
 * TODO(H08 habitat port): HabitatPower / HabitatBuilder register themselves here.
 */
public final class EnergyHooks
{
    @FunctionalInterface
    public interface ExternalReceiver
    {
        @Nullable
        IEnergyStorage get(ServerLevel level, BlockPos pos, Direction side);
    }

    public static volatile Predicate<BlockState> cableConnects = state -> false;
    public static volatile ExternalReceiver externalReceiver = (level, pos, side) -> null;

    private EnergyHooks() {}
}
