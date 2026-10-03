package com.abyssia.habitat.dismantle;

import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * BT01b: what the dismantle tool acts on (server only). {@code unitId} = the BuiltUnits record (-1 = a module built
 * before BT01), {@code moduleId} = the HabitatBases module it removes (-1 = a fixture unit), {@code plan} = the module's
 * layout (recorded or inferred from the box; null for fixtures and for the light preview lookup).
 */
public record DismantleTarget(int unitId, int moduleId, String entryId, @Nullable HabitatPlan plan, BoundingBox bounds,
                              BlockPos origin, Direction forward) implements BuildPlacement
{
    public boolean isModule()
    {
        return moduleId >= 0;
    }

    @Override
    public AABB box()
    {
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
    }

    @Override
    public Map<BlockPos, BlockState> ghost(BlockGetter level)
    {
        return Map.of();
    }

    @Override
    public BlockPos target()
    {
        return origin;
    }
}
