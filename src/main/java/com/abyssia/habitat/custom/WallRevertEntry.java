package com.abyssia.habitat.custom;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatLayout;
import com.abyssia.habitat.HabitatMode;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildRegistry;
import com.abyssia.habitat.build.BuiltUnits;
import com.abyssia.habitat.build.ModuleEntry;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BT01e: turns the habitat_window cells of the inner 11 x 3 of the aimed face back into hull - habitat_trim on y1,
 * habitat_wall on y2..3. On the server, windows that the module's own layout has there (H01 room windows; known from
 * the BuiltUnits record of the module) stay. Free, the glass is not refunded.
 */
public final class WallRevertEntry extends WallEntry
{
    public static final String ID = "wall_revert";

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public Component detail()
    {
        return Component.translatable("habitat." + Abyssia.MODID + "." + ID + ".detail");
    }

    @Override
    public List<ItemStack> cost()
    {
        return List.of();
    }

    @Override
    protected Map<BlockPos, BlockState> cells(Level level, WallFace face)
    {
        HabitatPlan plan = level instanceof ServerLevel server ? modulePlan(server, face) : null;
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (BlockPos pos : area(level, face, s -> s.is(ModHabitat.WINDOW.get())))
        {
            int y = pos.getY() - face.centre().getY();
            Block original = original(plan, pos, y);
            if (original != ModHabitat.WINDOW.get()) states.put(pos, original.defaultBlockState());
        }
        return link(level, states);
    }

    /** the H01 plan of the module behind the face, from its BuiltUnits record (null: none / built before BT01) */
    @Nullable
    private static HabitatPlan modulePlan(ServerLevel level, WallFace face)
    {
        BoundingBox b = face.module(level);
        if (b == null) return null;
        for (BuiltUnits.Unit unit : BuiltUnits.get(level).units())
        {
            BoundingBox u = unit.box();
            if (u.minX() != b.minX() || u.minY() != b.minY() || u.minZ() != b.minZ()
                    || u.maxX() != b.maxX() || u.maxY() != b.maxY() || u.maxZ() != b.maxZ()) continue;
            ModuleEntry module = com.abyssia.habitat.dismantle.Dismantler.moduleEntry(BuildRegistry.get(unit.entryId()));
            if (module != null)
                return new HabitatPlan(module.mode, unit.origin(), HabitatPlan.facing(unit.rot()), false);
        }
        return null;
    }

    private static Block original(@Nullable HabitatPlan plan, BlockPos pos, int y)
    {
        Block hull = y == 1 ? ModHabitat.TRIM.get() : ModHabitat.WALL.get();
        if (plan == null) return hull;
        HabitatMode mode = plan.mode();
        int hw = HabitatLayout.halfWidth(mode);
        for (int z = 0; z < mode.depth; z++)
            for (int x = -hw; x <= hw; x++)
                if (plan.at(x, y, z).equals(pos))
                    return switch (HabitatLayout.partAt(mode, x, y, z))
                    {
                        case WINDOW -> ModHabitat.WINDOW.get();
                        case TRIM -> ModHabitat.TRIM.get();
                        case WALL -> ModHabitat.WALL.get();
                        default -> hull;
                    };
        return hull;
    }

    @Override
    protected boolean expected(BlockState current)
    {
        return current.is(ModHabitat.WINDOW.get());
    }

    @Override
    protected String nothingProblem()
    {
        return "wall_revert_none";
    }

    @Override
    protected String doneMessage()
    {
        return "wall_revert_done";
    }
}
