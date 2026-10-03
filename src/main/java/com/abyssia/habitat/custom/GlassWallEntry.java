package com.abyssia.habitat.custom;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BT01e glass wall: the plain hull cells (habitat_wall / habitat_trim) of the inner 11 x 3 of the aimed 13-wide face
 * become connected habitat_window. Hatches, doors, opened cells and windows are left alone. 1 glass per cell
 * (up to 33); the hull is not refunded.
 */
public final class GlassWallEntry extends WallEntry
{
    public static final String ID = "glass_wall";
    public static final int MAX_CELLS = (2 * SPAN + 1) * 3;

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public Component detail()
    {
        return Component.translatable("habitat." + Abyssia.MODID + "." + ID + ".detail", 2 * SPAN + 1, 3);
    }

    @Override
    public List<ItemStack> cost()
    {
        return List.of(new ItemStack(Items.GLASS, MAX_CELLS));
    }

    @Override
    public List<ItemStack> cost(Level level, BuildPlacement placement)
    {
        int n = ((Placement) placement).cells().size();
        return n <= 0 ? List.of() : List.of(new ItemStack(Items.GLASS, n));
    }

    static boolean plainHull(BlockState state)
    {
        return state.is(ModHabitat.WALL.get()) || state.is(ModHabitat.TRIM.get());
    }

    @Override
    protected Map<BlockPos, BlockState> cells(Level level, WallFace face)
    {
        BlockState window = ModHabitat.WINDOW.get().defaultBlockState();
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (BlockPos pos : area(level, face, GlassWallEntry::plainHull)) states.put(pos, window);
        return link(level, states);
    }

    @Override
    protected boolean expected(BlockState current)
    {
        return plainHull(current);
    }

    @Override
    protected String nothingProblem()
    {
        return "glass_wall_none";
    }

    @Override
    protected String doneMessage()
    {
        return "glass_wall_done";
    }
}
