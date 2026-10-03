package com.abyssia.habitat.ladder;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildChecks;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.habitat.power.HabitatPower;
import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
 * BT01c vertical hatch (customize, acts on existing rooms): two exactly stacked ROOMs (upper floor on the lower roof,
 * same centre column; not moon pools). The lower ceiling centre (its middle light) and the upper floor centre become
 * a 1 x 1 opening holding a ladder column from the lower floor up to the upper floor: lower y1..y3, lower y4, upper
 * y0 (5 blocks; R turns the ladder side). On completion the two modules join one base (HabitatBases.union). Aim at
 * the lower ceiling from below or at the upper floor from above. Stacking alone never connects the rooms.
 */
public final class VerticalHatchEntry implements BuildEntry
{
    public static final String ID = "vertical_hatch";
    private static final int BELOW = 3, ABOVE = 1;

    /** {@code origin} = the lower room's ceiling centre (= target), {@code forward} = ladder FACING */
    public record Placement(BlockPos origin, Direction forward) implements BuildPlacement
    {
        /** ladder cells bottom-up: lower y1..y3, lower ceiling, upper floor */
        public List<BlockPos> cells()
        {
            List<BlockPos> out = new ArrayList<>();
            for (int y = -BELOW; y <= ABOVE; y++) out.add(origin.above(y));
            return out;
        }

        public List<BlockPos> lowerInterior()
        {
            return List.of(origin.below(3), origin.below(2), origin.below(1));
        }

        @Override
        public BlockPos target()
        {
            return origin;
        }

        @Override
        public AABB box()
        {
            return new AABB(origin.getX(), origin.getY() - BELOW, origin.getZ(), origin.getX() + 1, origin.getY() + ABOVE + 1, origin.getZ() + 1);
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            Map<BlockPos, BlockState> out = new HashMap<>();
            for (BlockPos pos : cells()) out.put(pos, LadderEntry.ladder(forward));
            return out;
        }
    }

    @Override
    public String id()
    {
        return ID;
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

    @Override
    public List<ItemStack> cost()
    {
        return List.of(new ItemStack(Items.IRON_INGOT, 16));
    }

    @Nullable
    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        HitResult hit = player.pick(HabitatPlan.MAX_DISTANCE, partialTick, false);
        if (!(hit instanceof BlockHitResult bhit) || hit.getType() != HitResult.Type.BLOCK) return null;
        Level level = player.level();
        BlockPos pos = bhit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        BlockPos ceiling;
        if (bhit.getDirection() == Direction.DOWN && (state.is(ModHabitat.CEILING.get()) || state.is(ModHabitat.LIGHT.get()))) ceiling = pos;
        else if (bhit.getDirection() == Direction.UP && state.is(ModHabitat.FLOOR.get())) ceiling = pos.below();
        else return null;
        BlockPos centre = HabitatPlan.roomCeilingCentre(level, ceiling);
        return centre == null ? null : new Placement(centre.immutable(), HabitatPlan.facing(rot));
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BlockPos c = p.origin();
        BuildCheck interior = BuildChecks.interior(level, player, p.lowerInterior(), p.box());
        if (!interior.ok()) return interior;
        // lower: ROOM (a moon pool has water at its floor centre); upper: same column, floor + air + centre light
        BlockPos upperFloor = c.above();
        BlockPos upperCeiling = upperFloor.above(HabitatPlan.STACK_STEP - 1);
        BlockState lowerFloor = level.getBlockState(c.below(HabitatPlan.STACK_STEP - 1));
        // a middle room of three may already have the lower hatch's ladder in its floor centre
        if (!(lowerFloor.is(ModHabitat.FLOOR.get()) || lowerFloor.is(LadderContent.LADDER.get()))
                ||!level.getBlockState(upperFloor).is(ModHabitat.FLOOR.get())
                || !level.getBlockState(upperFloor.above()).isAir()
                || !HabitatPlan.isCentreLight(level, upperCeiling)) return BuildCheck.fail("not_stacked");
        if (level instanceof ServerLevel server && (!HabitatPlan.registeredRoom(server, c) || !HabitatPlan.registeredRoom(server, upperCeiling)))
            return BuildCheck.fail("not_stacked");
        ItemStack stack = player.getMainHandItem();
        for (BlockPos pos : List.of(c, upperFloor))
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, stack)) return BuildCheck.PERMISSION;
        return BuildCheck.OK;
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BlockState ladder = LadderEntry.ladder(p.forward());
        List<BuildStep> steps = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : p.lowerInterior()) steps.add(new BuildStep(pos, ladder, null, air));
        // the ceiling light / floor cell: a cancel puts the exact previous state back
        steps.add(new BuildStep(p.origin(), ladder, null, level.getBlockState(p.origin())));
        steps.add(new BuildStep(p.origin().above(), ladder, null, level.getBlockState(p.origin().above())));
        return BuildLayout.of(steps);
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        HabitatBases data = HabitatBases.get(level);
        int lower = data.moduleAt(p.origin().below());
        int upper = data.moduleAt(p.origin().above(2));
        if (lower >= 0 && upper >= 0 && lower != upper) data.union(lower, upper, HabitatPower.CAPACITY);
        // roots changed: cables re-resolve their base (HabitatPower re-scans devices every RESCAN_TICKS)
        CableNetworkManager.markAllDirty(level);
        return new Completion(-1, Component.translatable("message." + Abyssia.MODID + ".habitat.vertical_hatch_built"));
    }
}
