package com.abyssia.vehicle;

import net.minecraft.world.entity.Entity;

public final class Vehicles
{
    private Vehicles() {}

    /** the rider sits in a sealed vehicle (submarine): own air, no pressure / hazard damage */
    public static boolean isShielded(Entity rider)
    {
        Entity v = rider.getVehicle();
        return v instanceof Submarine;
    }
}
