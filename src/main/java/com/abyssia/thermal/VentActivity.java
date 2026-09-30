package com.abyssia.thermal;

import net.minecraft.util.StringRepresentable;

/** How vigorously a vent is running. Stored on the vent core block, so no block entity is needed. */
public enum VentActivity implements StringRepresentable
{
    //                     particles  updraft  temperature  light
    DORMANT("dormant", 0.05f, 0.1f, 0.2f, 3),
    WEAK("weak", 0.4f, 0.5f, 0.6f, 6),
    ACTIVE("active", 1.0f, 1.0f, 1.0f, 10),
    STRONG("strong", 1.5f, 1.4f, 1.0f, 12),
    SUPERHEATED("superheated", 2.2f, 2.0f, 1.0f, 15);

    private final String name;
    public final float particles;
    public final float updraft;
    public final float temperature;
    public final int light;

    VentActivity(String name, float particles, float updraft, float temperature, int light)
    {
        this.name = name;
        this.particles = particles;
        this.updraft = updraft;
        this.temperature = temperature;
        this.light = light;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
