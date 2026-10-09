package com.abyssia.worldgen.cave;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * One named part of a cave system (a chamber, a tunnel, a shaft, an entrance mouth, an arch...) and everything that
 * decides how it looks: its type and environment, the size of its wall noise, and whether a gas pocket fills its top.
 * Many shapes share one space (every segment of a tunnel, every lobe of a chamber), so a carved block's owner space
 * tells decoration what kind of cave it is in.
 */
public final class CaveSpace
{
    public enum Role { CHAMBER, TUNNEL, SHAFT, ENTRANCE, ARCH, FORMATION, POCKET }

    public static final int NO_WATER_LEVEL = Integer.MIN_VALUE;

    public final Role role;
    public final CaveType type;
    @Nullable
    public final CaveLandmark landmark;
    public final ResourceLocation environmentId;
    public final CaveEnvironment environment;
    public final CaveProfile profile;
    /** Large, smooth wall undulation, in blocks. */
    public final double shapeAmp;
    /** Fine, jagged wall detail, in blocks (reduced by smoothness and by the erosion noise). */
    public final double detailAmp;
    public final double smoothness;
    /** Height of horizontal rock shelves ribbing the walls, in blocks, and their vertical spacing. */
    public final double shelfAmp;
    public final double shelfPeriod;
    /** Top of the water in a chamber holding a gas pocket (an underground lake); air above it. */
    public final int waterLevel;
    public final double x, y, z, radius;
    public final long seed;
    /**
     * The large cavern this space belongs to (its chamber, formations, wall pockets), set while the layout is built
     * and never changed after; null outside caverns. Drives zone- and patch-aware decoration.
     */
    @Nullable
    public Cavern cavern;
    /**
     * AB03: the hall this space belongs to (the hall itself and its formations), set while the layout is built; null elsewhere.
     * Its rock and surfaces take the environment's hall look.
     */
    @Nullable
    public CaveShape.Hall hall;
    /** Plant density multiplier for this space (plant chambers in cavern walls are overgrown). */
    public double vegetationBoost = 1.0;
    /** Share of luminous plants relative to its environment (the backs of wall pockets stay dark). */
    public double glowScale = 1.0;

    public CaveSpace(Role role, CaveType type, @Nullable CaveLandmark landmark, ResourceLocation environmentId, CaveEnvironment environment,
                     CaveProfile profile, double shapeAmp, double detailAmp, double smoothness, double shelfAmp, double shelfPeriod,
                     int waterLevel, double x, double y, double z, double radius, long seed)
    {
        this.role = role;
        this.type = type;
        this.landmark = landmark;
        this.environmentId = environmentId;
        this.environment = environment;
        this.profile = profile;
        this.shapeAmp = shapeAmp;
        this.detailAmp = detailAmp;
        this.smoothness = smoothness;
        this.shelfAmp = shelfAmp;
        this.shelfPeriod = shelfPeriod;
        this.waterLevel = waterLevel;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.seed = seed;
    }

    /** Upper bound on how far wall noise moves a surface, so bounding boxes can include it. */
    public double maxDisplacement()
    {
        return shapeAmp + detailAmp * (1 - smoothness) + shelfAmp + (cavern != null ? cavern.reliefAmplitude() : 0);
    }

    /** The hall's own space (not a formation or pocket of it). */
    public boolean isHall()
    {
        return hall != null && hall.space == this;
    }

    public boolean hasLake()
    {
        return waterLevel != NO_WATER_LEVEL;
    }

    /** Big enough for a 3D forest of giant plants (if its environment grows them). */
    public boolean isCavern()
    {
        return role == Role.CHAMBER && radius >= 14;
    }

    /** Longest stalactite/stalagmite this space grows, from its size. */
    public int maxSpeleothemLength()
    {
        if (role != Role.CHAMBER) return 3;
        return radius < 10 ? 4 : radius < 20 ? 6 : radius < 36 ? 9 : 13;
    }
}
