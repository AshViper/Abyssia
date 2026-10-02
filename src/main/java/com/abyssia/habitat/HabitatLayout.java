package com.abyssia.habitat;

import com.abyssia.habitat.HabitatMode.Face;

/**
 * What goes in each cell of a module, in local coordinates: x = width (centred, -hw..hw, positive = right),
 * z = depth (0 = near face), y = height (0 = floor).
 */
public final class HabitatLayout
{
    public enum Part { KEEP, AIR, WATER, FLOOR, TRIM, WALL, CEILING, WINDOW, LIGHT, FRAME, HATCH, DOOR_LOWER, DOOR_UPPER, CONSOLE }

    /** connector panel width (centred on the face, y1..3) */
    public static final int PANEL = 3;
    /** half size of the moon pool's centre water (7 x 7) */
    private static final int POOL = 3;

    private HabitatLayout() {}

    public static int halfWidth(HabitatMode mode)
    {
        return mode.width / 2;
    }

    /** x / z of the centre of a face */
    public static int faceX(HabitatMode mode, Face face)
    {
        return switch (face)
        {
            case LEFT -> -halfWidth(mode);
            case RIGHT -> halfWidth(mode);
            default -> 0;
        };
    }

    public static int faceZ(HabitatMode mode, Face face)
    {
        return switch (face)
        {
            case NEAR -> 0;
            case FAR -> mode.depth - 1;
            default -> mode.depth / 2;
        };
    }

    public static Part partAt(HabitatMode mode, int x, int y, int z)
    {
        int hw = halfWidth(mode);
        int mid = mode.depth / 2;
        boolean edge = Math.abs(x) == hw || z == 0 || z == mode.depth - 1;
        if (mode == HabitatMode.FOUNDATION)
            return edge && (Math.abs(x) == hw || x == 0) && (z == 0 || z == mode.depth - 1 || z == mid) ? Part.LIGHT : Part.FLOOR;
        if (y == 0)
            return mode == HabitatMode.MOON_POOL && Math.abs(x) <= POOL && Math.abs(z - mid) <= POOL ? Part.WATER : Part.FLOOR;
        if (y == mode.height - 1) return light(mode, x, z) ? Part.LIGHT : Part.CEILING;
        if (!edge) return mode == HabitatMode.SCAN_ROOM && y == 1 && x == 0 && z == mid ? Part.CONSOLE : Part.AIR;

        if (y <= 3)
            for (Face face : Face.values())
            {
                int along = alongFace(mode, face, x, z);
                if (along == Integer.MIN_VALUE) continue;
                if (mode.doors.contains(face))
                {
                    if (along == 0) return y == 1 ? Part.DOOR_LOWER : y == 2 ? Part.DOOR_UPPER : Part.FRAME;
                }
                else if (mode.connectors.contains(face) && Math.abs(along) <= PANEL / 2) return Part.HATCH;
            }
        if ((y == 2 || y == 3) && window(mode, x, z)) return Part.WINDOW;
        return y == 1 ? Part.TRIM : Part.WALL;
    }

    /** Offset of (x, z) from the centre of a face along it, or MIN_VALUE when the cell is not on that face (corners excluded). */
    private static int alongFace(HabitatMode mode, Face face, int x, int z)
    {
        int hw = halfWidth(mode);
        boolean corner = Math.abs(x) == hw && (z == 0 || z == mode.depth - 1);
        if (corner) return Integer.MIN_VALUE;
        return switch (face)
        {
            case NEAR -> z == 0 ? x : Integer.MIN_VALUE;
            case FAR -> z == mode.depth - 1 ? x : Integer.MIN_VALUE;
            case LEFT -> x == -hw ? z - mode.depth / 2 : Integer.MIN_VALUE;
            case RIGHT -> x == hw ? z - mode.depth / 2 : Integer.MIN_VALUE;
        };
    }

    private static boolean light(HabitatMode mode, int x, int z)
    {
        int mid = mode.depth / 2;
        return switch (mode)
        {
            case ROOM, MOON_POOL -> (x == -4 || x == 0 || x == 4) && (z == mid - 4 || z == mid || z == mid + 4);
            case CORRIDOR -> x == 0 && (z == 1 || z == 3 || z == 5);
            default -> x == 0 && z == mid;
        };
    }

    private static boolean window(HabitatMode mode, int x, int z)
    {
        int hw = halfWidth(mode);
        int mid = mode.depth / 2;
        boolean side = Math.abs(x) == hw && z > 0 && z < mode.depth - 1;
        return switch (mode)
        {
            case ROOM -> (z == mode.depth - 1 && Math.abs(x) >= 3 && Math.abs(x) <= 5)
                    || (side && Math.abs(z - mid) >= 3 && Math.abs(z - mid) <= 5);
            case CORRIDOR, ENTRANCE -> side;
            default -> false;
        };
    }
}
