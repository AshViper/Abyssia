package com.abyssia.thermal;

import net.minecraft.world.phys.Vec3;

/**
 * Rising water above vents: strongest over the core, fading smoothly with horizontal distance (the plume widens
 * as it rises) and with height. Near the base, surrounding water is drawn in toward the vent.
 * Side-agnostic so it can later move players or items; for now particles use it.
 */
public final class ThermalUpdraft
{
    /** Rise speed in blocks per tick over an ACTIVE vent of updraft 1. */
    private static final double BASE_SPEED = 0.08;
    private static final double INFLOW = 0.004;

    private ThermalUpdraft() {}

    public static Vec3 at(double x, double y, double z, Iterable<? extends VentSource> vents, double strengthScale)
    {
        double vx = 0, vy = 0, vz = 0;
        for (VentSource vent : vents)
        {
            double h = y - (vent.pos().getY() + 1);
            double height = vent.type().plumeHeight * Math.max(0.3, vent.activity().updraft);
            if (h < -1 || h > height) continue;
            double dx = x - (vent.pos().getX() + 0.5), dz = z - (vent.pos().getZ() + 0.5);
            double d = Math.sqrt(dx * dx + dz * dz);
            double rise = Math.max(0, h) / height;
            float f = ThermalTemperature.falloff(d, vent.type().radius * (1.0 + rise * 0.8));
            if (f <= 0) continue;
            double fade = 1.0 - rise * rise * (3 - 2 * rise);
            vy += BASE_SPEED * vent.type().updraft * vent.activity().updraft * f * fade * strengthScale;
            if (d > 0.5)
            {
                double pull = INFLOW * f * (1.0 - rise) * strengthScale;
                vx -= dx / d * pull;
                vz -= dz / d * pull;
            }
        }
        return new Vec3(vx, vy, vz);
    }
}
