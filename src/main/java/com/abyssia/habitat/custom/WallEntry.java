package com.abyssia.habitat.custom;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatWindowBlock;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * BT01e: a TARGET entry that swaps cells of one 13-wide wall face ({@link WallFace}) - the inner 11 x 3 (lateral
 * -5..5, y1..3; floor / ceiling rows and corner pillars never). Subclasses pick the cells and their new states.
 * Not recorded as a BuiltUnit (nothing to dismantle).
 */
public abstract class WallEntry implements BuildEntry
{
    /** lateral half span of the converted area (11 wide) */
    public static final int SPAN = WallFace.HALF - 1;
    private static final double MIN_REACH = 6.0;

    /** {@code cells} = new state per cell, bottom-up (record: the hologram rebuilds when it changes) */
    public record Placement(WallFace face, Map<BlockPos, BlockState> cells) implements BuildPlacement
    {
        @Override
        public BlockPos origin()
        {
            return face.centre();
        }

        @Override
        public Direction forward()
        {
            return face.inward().getOpposite();
        }

        @Override
        public AABB box()
        {
            return face.box();
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            return cells;
        }

        @Override
        public BlockPos target()
        {
            return face.cell(0, 2);
        }
    }

    @Override
    public BuildCategory category()
    {
        return BuildCategory.CUSTOMIZE;
    }

    @Override
    public Mode mode()
    {
        return Mode.TARGET;
    }

    /** the new state of every cell to change (bottom-up), connection booleans already set */
    protected abstract Map<BlockPos, BlockState> cells(Level level, WallFace face);

    /** the block a cell must still be when the change is applied */
    protected abstract boolean expected(BlockState current);

    /** problem key when the face has nothing to change */
    protected abstract String nothingProblem();

    protected abstract String doneMessage();

    @Nullable
    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        WallFace face = WallFace.aimed(player, Math.max(MIN_REACH, distance), partialTick);
        return face == null ? null : new Placement(face, cells(player.level(), face));
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        if (p.cells.isEmpty()) return BuildCheck.fail(nothingProblem());
        ItemStack stack = player.getMainHandItem();
        for (BlockPos pos : p.cells.keySet())
        {
            if (!expected(level.getBlockState(pos))) return BuildCheck.NO_TARGET;
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, stack)) return BuildCheck.PERMISSION;
        }
        if (level instanceof ServerLevel && p.face.module(level) == null) return BuildCheck.NOT_INTERIOR;
        return BuildCheck.OK;
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        List<BuildStep> steps = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : ((Placement) placement).cells.entrySet())
            steps.add(new BuildStep(e.getKey(), e.getValue(), null, level.getBlockState(e.getKey())));
        return BuildLayout.of(steps);
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        return new Completion(-1, Component.translatable("message." + Abyssia.MODID + ".habitat." + doneMessage(),
                ((Placement) placement).cells.size()));
    }

    @Override
    public boolean recordsUnit()
    {
        return false;
    }

    // ---------------------------------------------------------------- helpers

    /** cells of the 11 x 3 area matching {@code test}, bottom-up */
    protected static List<BlockPos> area(Level level, WallFace face, Predicate<BlockState> test)
    {
        List<BlockPos> out = new ArrayList<>();
        for (int y = 1; y <= 3; y++)
            for (int lat = -SPAN; lat <= SPAN; lat++)
            {
                BlockPos pos = face.cell(lat, y);
                if (test.test(level.getBlockState(pos))) out.add(pos);
            }
        return out;
    }

    /**
     * Sets the six connection booleans (HabitatWindowBlock / HabitatConnectedBlock) of every new state: connected
     * when the neighbour will be the same block (another changed cell, else the block there now).
     */
    protected static Map<BlockPos, BlockState> link(Level level, Map<BlockPos, BlockState> states)
    {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : states.entrySet())
        {
            BlockPos pos = e.getKey();
            BlockState state = e.getValue();
            if (state.hasProperty(PipeBlock.NORTH) && state.hasProperty(PipeBlock.DOWN))
            {
                Block block = state.getBlock();
                state = HabitatWindowBlock.connected(state, d ->
                {
                    BlockPos n = pos.relative(d);
                    BlockState next = states.get(n);
                    return next != null ? next.is(block) : level.getBlockState(n).is(block);
                });
            }
            out.put(pos, state);
        }
        return out;
    }
}
