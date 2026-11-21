package com.abyssia.worldgen.cave;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Mutable state while one cave system's layout is being built: its random source, shapes, spaces and points. */
final class CaveBuilder
{
    final CaveNetwork net;
    final CaveNoises noises;
    final CaveProfile profile;
    final RandomSource rng;
    final int anchorX, anchorZ;
    final List<CaveShape> shapes = new ArrayList<>();
    final List<CaveSpace> spaces = new ArrayList<>();
    final List<CaveSystem.Site> sites = new ArrayList<>();
    final List<CaveSystem.Vent> vents = new ArrayList<>();
    final List<String> route = new ArrayList<>();
    private int salt;
    private Boolean thermal;

    CaveBuilder(CaveNetwork net, long seed, CaveProfile profile, int anchorX, int anchorZ)
    {
        this.net = net;
        this.noises = net.noises();
        this.profile = profile;
        this.rng = new XoroshiroRandomSource(seed);
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
    }

    /** Wall noise and shelf sizes follow the part's role and size; gas-pocket chambers keep their walls tame. */
    CaveSpace space(CaveSpace.Role role, CaveType type, @Nullable CaveLandmark landmark, ResourceLocation envId,
                    double x, double y, double z, double radius, int waterLevel)
    {
        double smooth = type.smoothness;
        double shape, detail, shelf = 0, period = 9;
        switch (role)
        {
            case CHAMBER ->
            {
                shape = Mth.clamp(radius * 0.13, 1.5, 7.0);
                detail = type.roughness * (0.8 + Math.min(radius, 60) / 60.0);
                if (radius >= 14 && smooth < 0.5)
                {
                    shelf = Mth.clamp(radius * 0.05, 1.0, 3.0);
                    period = 7 + rng.nextDouble() * 5;
                }
            }
            case TUNNEL, ENTRANCE ->
            {
                shape = Mth.clamp(radius * 0.35, 0.8, 2.6);
                detail = type.roughness * 0.7;
            }
            case SHAFT ->
            {
                shape = Mth.clamp(radius * 0.3, 0.8, 2.2);
                detail = type.roughness * 0.8;
                shelf = radius >= 5 ? 1.3 : 0;
                period = 11 + rng.nextDouble() * 6;
            }
            default ->
            {
                shape = 1.0;
                detail = 0.7;
                smooth = Math.max(smooth, 0.3);
            }
        }
        if (waterLevel != CaveSpace.NO_WATER_LEVEL)
        {
            detail = Math.min(detail, 1.0);
            shape = Math.min(shape, 3.5);
        }
        CaveSpace space = new CaveSpace(role, type, landmark, envId, net.environment(envId, profile), profile, shape, detail, smooth,
                shelf, period, waterLevel, x, y, z, radius, rng.nextLong());
        spaces.add(space);
        return space;
    }

    /** A formation space (rock or crystal added into a cave) sharing the look of its cave (and of its hall). */
    CaveSpace formationSpace(CaveSpace of)
    {
        CaveSpace space = space(CaveSpace.Role.FORMATION, of.type, of.landmark, of.environmentId, of.x, of.y, of.z, 2, CaveSpace.NO_WATER_LEVEL);
        space.hall = of.hall;
        return space;
    }

    /**
     * AB03: the space of a hall: walls with a broad, gentle undulation and little fine detail, so the vault reads as one smooth
     * dome; no wall shelves (the hall has its own terraces and ledges).
     */
    CaveSpace hallSpace(CaveType type, @Nullable CaveLandmark landmark, ResourceLocation envId, double x, double y, double z, double radius)
    {
        double shape = Mth.clamp(radius * 0.035, 1.2, 4.0);
        CaveSpace space = new CaveSpace(CaveSpace.Role.CHAMBER, type, landmark, envId, net.environment(envId, profile), profile, shape, 0.9, 0.55,
                0, 9, CaveSpace.NO_WATER_LEVEL, x, y, z, radius, rng.nextLong());
        spaces.add(space);
        return space;
    }

    void add(CaveShape shape)
    {
        shapes.add(shape);
    }

    int nextSalt()
    {
        return ++salt;
    }

    int minCarveY()
    {
        return net.minY() + 10;
    }

    /** Hydrothermal vent fields lie close by, so thermal tunnels and caverns may branch off. */
    boolean thermalContext()
    {
        if (thermal == null) thermal = net.thermalContext(anchorX, anchorZ, 120);
        return thermal;
    }

    double range(double min, double max)
    {
        return min + rng.nextDouble() * (max - min);
    }

    int range(int min, int max)
    {
        return max <= min ? min : min + rng.nextInt(max - min + 1);
    }

    CaveSystem finish(int cellX, int cellZ, boolean minor, CaveType type, @Nullable CaveLandmark landmark, double x, double y, double z)
    {
        StringBuilder summary = new StringBuilder(type.getSerializedName());
        if (landmark != null) summary.append(" [").append(landmark.getSerializedName()).append(']');
        if (!route.isEmpty()) summary.append(": ").append(String.join(" > ", route));
        if (net.isWindow()) dropUnderLand();
        return new CaveSystem(cellX, cellZ, minor, type, landmark, List.copyOf(shapes), List.copyOf(spaces), List.copyOf(sites), List.copyOf(vents),
                summary.toString(), Mth.floor(x), Mth.floor(y), Mth.floor(z), net.band());
    }

    /**
     * The crust under land stays solid rock: a crust window system drops every part (shape, site, vent) whose centre lies under
     * land ({@link CaveNetwork#land}), so tunnels that run from the ocean side toward a coast end there. Only the output is
     * filtered (the random stream was used up as before), so systems clear of land come out unchanged.
     */
    private void dropUnderLand()
    {
        // Judged per 8 x 8 column cell (at its middle), so the answer never depends on which part asked first.
        java.util.Map<Long, Boolean> land = new java.util.HashMap<>();
        java.util.function.BiPredicate<Double, Double> under = (x, z) -> {
            int cx = Mth.floor(x) >> 3, cz = Mth.floor(z) >> 3;
            return land.computeIfAbsent(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), k -> net.land((cx << 3) + 4, (cz << 3) + 4));
        };
        shapes.removeIf(s -> under.test((s.minX + s.maxX) * 0.5, (s.minZ + s.maxZ) * 0.5));
        sites.removeIf(s -> under.test(s.x(), s.z()));
        vents.removeIf(v -> under.test((double) v.x(), (double) v.z()));
    }
}
