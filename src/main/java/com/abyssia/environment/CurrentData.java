package com.abyssia.environment;

import net.minecraft.world.phys.Vec3;

/**
 * What a {@link NaturalCurrents} stream does at one position: the stream itself plus how far inside it the position is.
 * {@link #NONE} outside every stream. This is the API for machines that use the flow (e.g. a future current turbine:
 * {@code output = powerFactor(turbineAxis) * efficiency}).
 *
 * @param current the stream, or {@code null} for {@link #NONE}
 * @param falloff 1 on the stream's axis, falling smoothly to 0 at its edge
 */
public record CurrentData(NaturalCurrent current, float falloff)
{
    public static final CurrentData NONE = new CurrentData(null, 0f);

    public boolean isPresent()
    {
        return current != null && falloff > 0f;
    }

    /** Unit flow direction ({@link Vec3#ZERO} outside a stream). */
    public Vec3 getDirection()
    {
        return current == null ? Vec3.ZERO : current.direction();
    }

    /** The stream's nominal strength, 0..1 (0 outside a stream). */
    public float getStrength()
    {
        return current == null ? 0f : current.strength();
    }

    /** Strength felt at this position: nominal strength times the edge falloff. */
    public float getLocalStrength()
    {
        return getStrength() * falloff;
    }

    /** Horizontal radius of the stream in blocks (0 outside a stream). */
    public float getRadius()
    {
        return current == null ? 0f : current.radius();
    }

    /** Local strength times how squarely the flow meets {@code axis} (|cos| of the angle), for current-driven machines. */
    public float powerFactor(Vec3 axis)
    {
        if (!isPresent() || axis.lengthSqr() < 1.0E-8) return 0f;
        return getLocalStrength() * (float) Math.abs(axis.normalize().dot(current.direction()));
    }
}
