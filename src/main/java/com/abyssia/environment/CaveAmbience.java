package com.abyssia.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * What the local viewer's surroundings are like underground: how enclosed they are, how big the cavity is, how narrow
 * the passage is and which way it runs. Measured a few times a second by the client (a handful of short ray casts
 * from the camera, never a volume scan) and read by particle spawners, particles and plant spore emitters.
 * <p>
 * Lives in common code, like {@link ParticleBudget}, because block animation ticks read it; only ever touched from
 * the client thread, and all zero on a dedicated server.
 */
public final class CaveAmbience
{
    /** Within this distance of the viewer particles feel the passage flow; beyond it they keep the open-water current. */
    private static final double FLOW_RANGE = 20.0;
    /** Plants beyond this distance from the viewer shed no spores at all. */
    private static final double SPORE_RANGE = 24.0;

    private static float enclosure;
    private static float cavitySize = 64f;
    private static float narrowness;
    private static Vec3 flow = Vec3.ZERO;
    private static float cavern;
    private static double viewerX, viewerY, viewerZ;
    private static boolean active;

    private CaveAmbience() {}

    /** Called by the client tracker with eased values. */
    public static void update(double x, double y, double z, float enclosure, float cavitySize, float narrowness, Vec3 flow)
    {
        viewerX = x;
        viewerY = y;
        viewerZ = z;
        CaveAmbience.enclosure = enclosure;
        CaveAmbience.cavitySize = cavitySize;
        CaveAmbience.narrowness = narrowness;
        CaveAmbience.flow = flow;
        active = true;
    }

    /** Called by the client tracker with the eased result of its long-range cavern probe. */
    public static void updateCavern(float cavern)
    {
        CaveAmbience.cavern = cavern;
    }

    public static void reset()
    {
        enclosure = 0f;
        cavitySize = 64f;
        narrowness = 0f;
        flow = Vec3.ZERO;
        cavern = 0f;
        active = false;
    }

    /**
     * 0 in open water and ordinary caves, rising to 1 deep inside a large cavern: roof, floor and most walls in
     * range but far off. Measured with long rays, so it still holds in the middle of a hall wider and taller than the
     * short enclosure probe reaches. Drives the cavern haze and the marine snow depth cue.
     */
    public static float cavernFactor()
    {
        return cavern;
    }

    /** 0 in open water, 1 deep inside a cave. */
    public static float enclosure()
    {
        return enclosure;
    }

    /** Typical distance to the cave walls around the viewer, in blocks (large in open water). */
    public static float cavitySize()
    {
        return cavitySize;
    }

    /** 0 in wide spaces, 1 in a tight squeeze. */
    public static float narrowness()
    {
        return narrowness;
    }

    public static boolean inCave()
    {
        return enclosure > 0.5f;
    }

    /**
     * Marine snow density multiplier: settled flakes stirred up in still cave water hang thicker than on the open
     * seabed, and more so the larger the cavern (the far wall stays hidden behind them, which reads as depth).
     */
    public static float snowFactor(float caveMultiplier)
    {
        // Deep in a huge cavern the short enclosure probe can miss the far walls; the cavern probe still sees them.
        float enclosed = Math.max(enclosure, cavern);
        if (enclosed <= 0f) return 1f;
        float size = cavitySize < 10f ? 0.4f : cavitySize < 24f ? 0.65f : cavitySize < 40f ? 0.85f : 1f;
        size = Math.max(size, cavern);
        return 1f + enclosed * (caveMultiplier - 1f) * size;
    }

    /**
     * How strongly the open-ocean current reaches this point: caves shelter the water, so it is damped the more
     * enclosed the viewer is (particles only exist near the viewer, so the viewer's surroundings stand in for theirs).
     */
    public static double currentShelter()
    {
        return 1.0 - enclosure * 0.75;
    }

    /** Extra flow through a narrow passage, along its axis; zero away from the viewer or in wide spaces. */
    public static Vec3 passageFlow(double x, double y, double z)
    {
        if (!active || narrowness <= 0.05f) return Vec3.ZERO;
        double dx = x - viewerX, dy = y - viewerY, dz = z - viewerZ;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 > FLOW_RANGE * FLOW_RANGE) return Vec3.ZERO;
        return flow.scale(1.0 - Math.sqrt(d2) / FLOW_RANGE);
    }

    /**
     * Multiplier on a plant's chance to shed spores this animation tick: fades out with distance from the viewer
     * (animation ticks already only run near the viewer; this thins the far ones) and doubles inside caves, where
     * still water keeps glowing spores visible against the walls.
     */
    public static float sporeFactor(BlockPos pos)
    {
        if (!active) return 1f;
        double dx = pos.getX() + 0.5 - viewerX, dy = pos.getY() + 0.5 - viewerY, dz = pos.getZ() + 0.5 - viewerZ;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > SPORE_RANGE) return 0f;
        float distance = d < 8 ? 1f : (float) (1.0 - (d - 8) / (SPORE_RANGE - 8));
        return distance * (1f + enclosure);
    }
}
