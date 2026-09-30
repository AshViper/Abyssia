package com.abyssia.thermal;

import net.minecraft.util.StringRepresentable;

/** Kinds of hydrothermal vent. Values describe an ACTIVE vent; {@link VentActivity} scales them. */
public enum ThermalVentType implements StringRepresentable
{
    //                 name            particles/tick  updraft  radius  temperature  plume height
    BLACK_SMOKER("black_smoker", 0.9f, 1.0f, 8, 1.0f, 40),
    WHITE_SMOKER("white_smoker", 0.7f, 0.7f, 7, 0.7f, 28),
    MINERAL("mineral", 0.25f, 0.3f, 4, 0.45f, 10),
    SUPERHEATED("superheated", 1.4f, 1.6f, 10, 1.0f, 50);

    private final String name;
    public final float particleRate;
    public final float updraft;
    public final int radius;
    public final float maxTemperature;
    public final int plumeHeight;

    ThermalVentType(String name, float particleRate, float updraft, int radius, float maxTemperature, int plumeHeight)
    {
        this.name = name;
        this.particleRate = particleRate;
        this.updraft = updraft;
        this.radius = radius;
        this.maxTemperature = maxTemperature;
        this.plumeHeight = plumeHeight;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
