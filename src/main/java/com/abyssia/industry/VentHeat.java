package com.abyssia.industry;

import com.abyssia.block.ThermalVentBlock;
import com.abyssia.thermal.VentActivity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Thermal vent checks of the hydrothermal generator and the high-temperature furnace (never load chunks). */
public final class VentHeat
{
    private VentHeat() {}

    /** Output multiplier of the hydrothermal generator per vent activity (vent type does not matter). */
    public static float multiplier(@Nullable VentActivity activity)
    {
        if (activity == null) return 0.0f;
        return switch (activity)
        {
            case DORMANT -> 0.0f;
            case WEAK -> 0.5f;
            case ACTIVE -> 1.0f;
            case STRONG -> 1.5f;
            case SUPERHEATED -> 2.0f;
        };
    }

    /** The most active thermal vent among the 6 neighbours of pos, or null if none touches it. */
    @Nullable
    public static VentActivity adjacentActivity(Level level, BlockPos pos)
    {
        VentActivity best = null;
        for (Direction dir : Direction.values())
        {
            BlockPos n = pos.relative(dir);
            if (!level.isLoaded(n)) continue;
            VentActivity activity = activity(level.getBlockState(n));
            if (activity != null && (best == null || activity.ordinal() > best.ordinal())) best = activity;
        }
        return best;
    }

    /** Whether a thermal vent that is not DORMANT lies within radius blocks (sphere) of pos. */
    public static boolean liveVentNearby(Level level, BlockPos pos, int radius)
    {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
            {
                m.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
                if (!level.isLoaded(m)) continue;
                for (int dy = -radius; dy <= radius; dy++)
                {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    VentActivity activity = activity(level.getBlockState(m));
                    if (activity != null && activity != VentActivity.DORMANT) return true;
                }
            }
        return false;
    }

    @Nullable
    private static VentActivity activity(BlockState state)
    {
        return state.getBlock() instanceof ThermalVentBlock ? state.getValue(ThermalVentBlock.ACTIVITY) : null;
    }
}
