package com.abyssia.entity.giant;

/**
 * Per-species constants of a giant wild animal (GNT01). {@code joints} counts the head, so the chain is
 * {@code joints - 1} segments of {@link #spacing()} blocks. {@code holdAnim} is the animation held while chasing (null = none);
 * {@code cullSide} / {@code cullDown} grow the culling box around the chain for wings, fins and tentacles. {@code speed} is the cruise speed in blocks per tick.
 */
public record GiantSpec(String id, double length, int joints, double radius, int jointsPerPart, double health, double armor, double speed,
                        GiantBrain.Temper temper, float playerDamage, float subDamage,
                        @javax.annotation.Nullable String holdAnim, double cullSide, double cullDown)
{
    public double spacing()
    {
        return length / (joints - 1);
    }

    /** Hitbox parts (4 to 12 per animal). */
    public int partCount()
    {
        return (joints - 1 + jointsPerPart - 1) / jointsPerPart;
    }
}
