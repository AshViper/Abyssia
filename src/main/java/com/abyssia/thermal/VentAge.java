package com.abyssia.thermal;

/** Life stage of a chimney: young ones are small and vigorous, old ones large and crumbling, dead ones cold. */
public enum VentAge
{
    YOUNG(VentActivity.STRONG, 0.6f, 0.0f),
    ACTIVE(VentActivity.ACTIVE, 1.0f, 0.0f),
    OLD(VentActivity.WEAK, 1.3f, 0.35f),
    DEAD(VentActivity.DORMANT, 1.1f, 0.55f);

    public final VentActivity activity;
    /** Chimney height multiplier. */
    public final float size;
    /** Fraction of the chimney top that has collapsed. */
    public final float collapse;

    VentAge(VentActivity activity, float size, float collapse)
    {
        this.activity = activity;
        this.size = size;
        this.collapse = collapse;
    }
}
