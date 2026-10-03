package com.abyssia.habitat.ladder;

import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildChecks;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BT01c interior ladder: a 3 block column (room y1..y3, floor to ceiling) against an interior wall (or against the
 * vertical hatch ladder column), inside a registered module box. Aim at the side face of the wall; the column drops
 * to the floor. Cost 12 iron ingots.
 */
public final class LadderEntry implements BuildEntry
{
    public static final String ID = "ladder";
    /** room interior height (y1..y3) */
    public static final int HEIGHT = 3;

    /** {@code origin} = bottom cell (y1), {@code forward} = ladder FACING (away from the wall) */
    public record Placement(BlockPos origin, Direction forward) implements BuildPlacement
    {
        public List<BlockPos> cells()
        {
            List<BlockPos> out = new ArrayList<>(HEIGHT);
            for (int y = 0; y < HEIGHT; y++) out.add(origin.above(y));
            return out;
        }

        @Override
        public AABB box()
        {
            return new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY() + HEIGHT, origin.getZ() + 1);
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            Map<BlockPos, BlockState> out = new HashMap<>();
            for (BlockPos pos : cells()) out.put(pos, ladder(forward));
            return out;
        }
    }

    public static BlockState ladder(Direction facing)
    {
        return LadderContent.LADDER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /** what a ladder may stand against: hull (not the door) or another habitat ladder (hatch column) */
    static boolean backing(BlockState state)
    {
        return HabitatBuilder.shell(state) || state.is(LadderContent.LADDER.get());
    }

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public BuildCategory category()
    {
        return BuildCategory.EQUIPMENT;
    }

    @Override
    public List<ItemStack> cost()
    {
        return List.of(new ItemStack(Items.IRON_INGOT, 12));
    }

    @Nullable
    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        HitResult hit = player.pick(HabitatPlan.MAX_DISTANCE, partialTick, false);
        if (!(hit instanceof BlockHitResult bhit) || hit.getType() != HitResult.Type.BLOCK) return null;
        Direction face = bhit.getDirection();
        if (face.getAxis().isVertical()) return null;
        Level level = player.level();
        if (!backing(level.getBlockState(bhit.getBlockPos()))) return null;
        BlockPos bottom = bhit.getBlockPos().relative(face);
        for (int i = 0; i < HEIGHT && level.getBlockState(bottom.below()).isAir(); i++) bottom = bottom.below();
        return new Placement(bottom.immutable(), face);
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BuildCheck interior = BuildChecks.interior(level, player, p.cells(), p.box());
        if (!interior.ok()) return interior;
        // floor below, ceiling above: exactly floor to ceiling
        if (!HabitatBuilder.shell(level.getBlockState(p.origin().below()))
                || !HabitatBuilder.shell(level.getBlockState(p.origin().above(HEIGHT)))) return BuildCheck.fail("ladder_height");
        Direction back = p.forward().getOpposite();
        for (BlockPos pos : p.cells())
            if (!backing(level.getBlockState(pos.relative(back)))) return BuildCheck.fail("ladder_wall");
        return BuildCheck.OK;
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        List<BuildStep> steps = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : p.cells()) steps.add(new BuildStep(pos, ladder(p.forward()), null, air));
        return BuildLayout.of(steps);
    }
}
