package com.abyssia.habitat.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Where an entry would be built, computed on both sides from the player's aim ({@link BuildEntry#plan}). Implement it
 * as a record: the hologram rebuilds its mesh only when the placement changes (equals).
 */
public interface BuildPlacement
{
    /** local (0, 0, 0) of the build (modules: near face centre, floor level); TARGET entries: the aimed block */
    BlockPos origin();

    /** horizontal direction of local +z */
    Direction forward();

    /** rotation index stored with the unit (Direction 2D value: 0 south, 1 west, 2 north, 3 east) */
    default int rot()
    {
        return forward().get2DDataValue();
    }

    /** aligned to something existing (hatch snap, stacked roof...); the hologram tints it light blue when valid */
    default boolean snapped()
    {
        return false;
    }

    /** outer box: hologram outline, entity check, cancel distance and the BuiltUnits record */
    AABB box();

    /** extra outlines (connector panels) */
    default List<AABB> highlights()
    {
        return List.of();
    }

    /** translucent ghost blocks, absolute positions (client hologram; may read the level, e.g. legs) */
    Map<BlockPos, BlockState> ghost(BlockGetter level);

    /** TARGET entries: the existing block the action applies to */
    @Nullable
    default BlockPos target()
    {
        return null;
    }
}
