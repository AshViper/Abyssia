package com.abyssia.thermal;

import net.minecraft.core.BlockPos;

/** A vent core somewhere in the world, as seen by temperature and updraft calculations. */
public interface VentSource
{
    BlockPos pos();

    ThermalVentType type();

    VentActivity activity();
}
