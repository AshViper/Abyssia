package com.abyssia.worldgen;

import com.abyssia.Abyssia;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The deep ocean layer: the former {@code abyssia:deep_ocean} dimension, now generated in the overworld below the
 * bedrock band (inbox/specs/M01-deep-layer-merge.md).
 * <p>
 * Old deep-ocean coordinates map to the overworld by one constant, {@code overworldY = deepY + SHIFT}, matching the
 * old transition (overworld Y -40 = deep Y 200). Code tuned in deep-ocean Y converts with {@link #toDeepY}.
 */
public final class DeepLayer
{
    /** Overworld Y = old deep-ocean Y + SHIFT. */
    public static final int SHIFT = -240;
    /** Bottom of the world (bedrock floor of the deep layer). */
    public static final int MIN_Y = -368;
    /** Bottom of the bedrock band between the ocean world and the deep layer; everything below is the deep layer. */
    public static final int TOP_Y = -64;
    /** Top of the bedrock band (exclusive). */
    public static final int BAND_TOP_Y = -59;
    /** Lowest underside of the rock ceiling under the bedrock band; deep caves, structures and entrances stay below it. */
    public static final int CEILING_BOTTOM_Y = -88;
    /** Highest deep seabed (old deep Y 140): at least 12 blocks of water stay under the thickest ceiling. */
    public static final int SEABED_MAX_Y = -100;
    /** Old deep-ocean Y that DepthZone's metre tables count from (deep Y 200 = 63 m below the surface). */
    public static final int DEPTH_ORIGIN_DEEP_Y = 200;

    /** Dimension effects of the Abyssia ocean world's dimension type (the ocean world preset). */
    public static final ResourceLocation OCEAN_WORLD_EFFECTS = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "ocean_world");
    /** Dimension effects of the default world's dimension type: vanilla land above, the deep layer below (M02). */
    public static final ResourceLocation OVERWORLD_EFFECTS = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "overworld");

    private DeepLayer() {}

    public static boolean isDeep(double y)
    {
        return y < TOP_Y;
    }

    /** True in an overworld with a deep layer (ocean world or default world) below the bedrock band (client and server). */
    public static boolean isDeep(Level level, double y)
    {
        return isDeep(y) && level.dimension() == Level.OVERWORLD && hasDeepLayer(level.dimensionType().effectsLocation());
    }

    /** Dimension effects of a dimension type whose overworld has the deep layer. */
    public static boolean hasDeepLayer(ResourceLocation effects)
    {
        return OCEAN_WORLD_EFFECTS.equals(effects) || OVERWORLD_EFFECTS.equals(effects);
    }

    /** Overworld Y to the old deep-ocean Y that deep-layer tables (depth zones, fog, currents) were tuned in. */
    public static double toDeepY(double y)
    {
        return y - SHIFT;
    }

    /** Old deep-ocean Y to overworld Y. */
    public static double fromDeepY(double deepY)
    {
        return deepY + SHIFT;
    }

    /**
     * The deep seabed at a column of real blocks: scans down from under the ceiling, skips the ceiling's rock and
     * returns the Y of the first motion-blocking block with open water (or air in a gas pocket) above it, or
     * {@code MIN_Y} if none. Like {@code OCEAN_FLOOR_WG}, thin blocks (carpets, plants) are not a floor.
     * For code that places on, or moves along, real blocks; planning code reads the density seabed instead.
     */
    public static int floorY(BlockGetter level, int x, int z)
    {
        if (level instanceof WorldGenLevel gen)
        {
            // Straight from the chunk sections: generation calls this for many columns per chunk.
            ChunkAccess chunk = gen.getChunk(x >> 4, z >> 4);
            LevelChunkSection[] sections = chunk.getSections();
            int minY = chunk.getMinBuildHeight(), lx = x & 15, lz = z & 15;
            boolean open = false;
            for (int y = Math.min(CEILING_BOTTOM_Y + 24, chunk.getMaxBuildHeight() - 1); y > Math.max(MIN_Y, minY); y--)
            {
                boolean solid = sections[(y - minY) >> 4].getBlockState(lx, y & 15, lz).blocksMotion();
                if (!solid) open = true;
                else if (open) return y;
            }
            return MIN_Y;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, CEILING_BOTTOM_Y + 24, z);
        boolean open = false;
        for (int y = CEILING_BOTTOM_Y + 24; y > MIN_Y; y--)
        {
            boolean solid = level.getBlockState(pos.setY(y)).blocksMotion();
            if (!solid) open = true;
            else if (open) return y;
        }
        return MIN_Y;
    }
}
