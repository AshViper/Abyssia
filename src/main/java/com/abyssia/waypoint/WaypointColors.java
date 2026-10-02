package com.abyssia.waypoint;

/**
 * W01: the 16 preset beacon colours (index = the block's COLOR property). Same order as BEACON_COLORS in
 * tools/industrial_assets.py, which writes the names (gui.abyssia.waypoint.color.&lt;id&gt;).
 */
public final class WaypointColors
{
    public static final String[] IDS = {
            "white", "light_gray", "gray", "black", "red", "orange", "yellow", "green",
            "light_blue", "cyan", "blue", "purple", "pink", "brown", "deep_blue", "deep_purple"};
    private static final int[] RGB = {
            0xFFFFFF, 0xC7C7C7, 0x555555, 0x151923, 0xE53935, 0xFF8A00, 0xFFD83D, 0x45D66A,
            0x36D9FF, 0x00B8D9, 0x3D7CFF, 0x9B5CFF, 0xFF6FAE, 0x9A6840, 0x246B9C, 0x633D91};
    public static final int COUNT = RGB.length;
    /** cyan: new beacons and the item icon */
    public static final int DEFAULT = 9;

    private WaypointColors() {}

    public static int clamp(int index)
    {
        return index < 0 || index >= COUNT ? DEFAULT : index;
    }

    /** 0xRRGGBB of a colour index */
    public static int rgb(int index)
    {
        return RGB[clamp(index)];
    }

    public static String nameKey(int index)
    {
        return "gui.abyssia.waypoint.color." + IDS[clamp(index)];
    }
}
