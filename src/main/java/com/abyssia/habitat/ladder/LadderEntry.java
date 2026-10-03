package com.abyssia.habitat.ladder;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildChecks;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.habitat.build.BuiltUnits;
import com.abyssia.habitat.dismantle.Dismantler;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.habitat.power.HabitatPower;
import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
 * to the floor. Cost 12 iron ingots. When a registered ROOM is stacked exactly on top, the ceiling cell and the upper
 * room's floor cell above the column become ladders too (5 blocks, the two modules join one base).
 */
public final class LadderEntry implements BuildEntry
{
    public static final String ID = "ladder";
    /** room interior height (y1..y3) */
    public static final int HEIGHT = 3;

    /** {@code origin} = bottom cell (y1), {@code forward} = ladder FACING (away from the wall) */
    public record Placement(BlockPos origin, Direction forward, boolean up) implements BuildPlacement
    {
        public List<BlockPos> cells()
        {
            List<BlockPos> out = new ArrayList<>(HEIGHT);
            for (int y = 0; y < HEIGHT; y++) out.add(origin.above(y));
            return out;
        }

        /** the column incl. the ceiling cell and the upper floor cell when it connects upwards */
        public List<BlockPos> allCells()
        {
            List<BlockPos> out = cells();
            if (up)
            {
                out.add(origin.above(HEIGHT));
                out.add(origin.above(HEIGHT + 1));
            }
            return out;
        }

        @Override
        public AABB box()
        {
            int h = up ? HEIGHT + 2 : HEIGHT;
            return new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY() + h, origin.getZ() + 1);
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            Map<BlockPos, BlockState> out = new HashMap<>();
            for (BlockPos pos : allCells()) out.put(pos, ladder(forward));
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
        BlockPos at = bottom.immutable();
        return new Placement(at, face, canConnectUp(level, at, face));
    }

    /**
     * A registered ROOM (not a moon pool) is stacked exactly on this one and the ladder stands against its interior
     * wall: the ceiling cell and the upper floor cell above the column are plain shell with plain shell behind them.
     */
    static boolean canConnectUp(Level level, BlockPos bottom, Direction forward)
    {
        BlockPos ceiling = bottom.above(HEIGHT);
        BlockPos floor = ceiling.above();
        BlockState c = level.getBlockState(ceiling);
        if (!(c.is(ModHabitat.CEILING.get()) || c.is(ModHabitat.LIGHT.get())) || !level.getBlockState(floor).is(ModHabitat.FLOOR.get()))
            return false;
        Direction back = forward.getOpposite();
        for (int y = 0; y < HEIGHT + 2; y++)
        {
            BlockState b = level.getBlockState(bottom.above(y).relative(back));
            if (!HabitatBuilder.shell(b) || b.is(ModHabitat.HATCH.get())) return false;
        }
        BlockPos centre = HabitatPlan.roomCeilingCentre(level, ceiling);
        if (centre == null) return false;
        BlockPos upperCeiling = centre.above(HabitatPlan.STACK_STEP);
        if (!HabitatPlan.isCentreLight(level, upperCeiling)
                || level.getBlockState(centre.below(HabitatPlan.STACK_STEP - 1)).getFluidState().is(FluidTags.WATER))
            return false;
        return !(level instanceof ServerLevel server)
                || (HabitatPlan.registeredRoom(server, centre) && HabitatPlan.registeredRoom(server, upperCeiling));
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
        if (p.up())
        {
            if (!canConnectUp(level, p.origin(), p.forward())) return BuildCheck.fail("ladder_height");
            ItemStack stack = player.getMainHandItem();
            for (BlockPos pos : p.allCells().subList(HEIGHT, HEIGHT + 2))
                if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, stack)) return BuildCheck.PERMISSION;
        }
        return BuildCheck.OK;
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        List<BuildStep> steps = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : p.cells()) steps.add(new BuildStep(pos, ladder(p.forward()), null, air));
        // ceiling / upper floor cells: a cancel puts the exact previous state back
        if (p.up())
            for (BlockPos pos : p.allCells().subList(HEIGHT, HEIGHT + 2))
                steps.add(new BuildStep(pos, ladder(p.forward()), null, level.getBlockState(pos)));
        return BuildLayout.of(steps);
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        if (!p.up()) return Completion.NONE;
        HabitatBases data = HabitatBases.get(level);
        int lower = data.moduleAt(p.origin());
        int upper = data.moduleAt(p.origin().above(HEIGHT + 2));
        if (lower >= 0 && upper >= 0 && lower != upper) data.union(lower, upper, HabitatPower.CAPACITY);
        CableNetworkManager.markAllDirty(level);
        return new Completion(-1, Component.translatable("message." + Abyssia.MODID + ".habitat.vertical_hatch_built"));
    }

    @Override
    public boolean canDismantle()
    {
        return true;
    }

    @Override
    public boolean dismantle(ServerLevel level, ServerPlayer player, BuiltUnits.Unit unit)
    {
        if (unit.box().getYSpan() > HEIGHT)
        {
            // put the hull back first (so the generic fixture removal leaves it alone); the base union stays
            BlockPos ceiling = unit.origin().above(HEIGHT);
            BlockPos floor = ceiling.above();
            if (level.getBlockState(ceiling).is(LadderContent.LADDER.get()))
                level.setBlock(ceiling, shellAt(level, ceiling, ModHabitat.CEILING.get().defaultBlockState()), Block.UPDATE_ALL);
            if (level.getBlockState(floor).is(LadderContent.LADDER.get()))
                level.setBlock(floor, shellAt(level, floor, ModHabitat.FLOOR.get().defaultBlockState()), Block.UPDATE_ALL);
        }
        Dismantler.dismantleFixture(level, unit);
        Dismantler.refund(level, player, unit.paid(), unit.origin());
        return true;
    }

    private static BlockState shellAt(ServerLevel level, BlockPos pos, BlockState fallback)
    {
        int id = HabitatBases.get(level).moduleAt(pos);
        HabitatPlan plan = id >= 0 ? Dismantler.modulePlan(level, id) : null;
        BlockState state = plan == null ? null : HabitatBuilder.shellStates(plan).get(pos);
        return state != null ? state : fallback;
    }
}
