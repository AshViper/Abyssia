package com.abyssia.thermal;

import net.minecraft.util.Mth;

/**
 * Virtual water temperature (0..1) around vents, computed from distance and vent type rather than stored per block.
 * Currently drives particles, fog and mineral zoning; usable later for plants, blocks or players.
 */
public final class ThermalTemperature
{
    private ThermalTemperature() {}

    /** Smooth 1 -> 0 falloff reaching zero at {@code radius}. */
    public static float falloff(double distance, double radius)
    {
        float t = Mth.clamp((float) (1.0 - distance / radius), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float of(ThermalVentType type, VentActivity activity, double distance)
    {
        return type.maxTemperature * activity.temperature * falloff(distance, type.radius * 1.5);
    }

    public static float at(double x, double y, double z, Iterable<? extends VentSource> vents)
    {
        float max = 0f;
        for (VentSource vent : vents)
        {
            double dx = x - (vent.pos().getX() + 0.5), dy = y - (vent.pos().getY() + 0.5), dz = z - (vent.pos().getZ() + 0.5);
            max = Math.max(max, of(vent.type(), vent.activity(), Math.sqrt(dx * dx + dy * dy * 0.25 + dz * dz)));
        }
        return max;
    }
}
