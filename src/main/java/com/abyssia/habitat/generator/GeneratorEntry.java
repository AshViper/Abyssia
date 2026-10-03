package com.abyssia.habitat.generator;

import com.abyssia.Abyssia;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildChecks;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.habitat.build.BuiltUnits;
import com.abyssia.habitat.power.HabitatPower;
import com.abyssia.registry.ModHabitat;
import com.abyssia.thermal.VentActivity;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BT01d build entry (POWER) of one multiblock generator. Placement: aiming at the side of a habitat shell block puts
 * the back row (local z = 0) against it, facing away (snapped); the geothermal generator snaps its core onto the top
 * of a thermal vent near the aim point; otherwise (free placement, no base needed) the footprint is centred
 * horizontally on the view-distance aim point with its bottom row on the aimed y, and R rotates it.
 * Rules: every footprint cell is water and (geothermal) a non-dormant thermal vent is right under the core. A base
 * wall is optional: next to one the generator pushes into the base, otherwise cables pull or the buffer fills.
 */
public final class GeneratorEntry implements BuildEntry
{
    private static final int VENT_SEARCH_XZ = 2, VENT_SEARCH_DOWN = 8, VENT_SEARCH_UP = 2;

    public final GeneratorKind kind;

    public GeneratorEntry(GeneratorKind kind)
    {
        this.kind = kind;
    }

    public record Placement(GeneratorKind kind, BlockPos origin, Direction forward, boolean snapped) implements BuildPlacement
    {
        @Override
        public AABB box()
        {
            return kind.box(origin, forward);
        }

        public BlockPos controller()
        {
            return kind.controllerPos(origin, forward);
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            Map<BlockPos, BlockState> out = new LinkedHashMap<>();
            BlockState ghost = ModHabitat.TRIM.get().defaultBlockState();
            for (BlockPos pos : kind.solidCells(origin, forward)) out.put(pos, ghost);
            return out;
        }
    }

    @Override
    public String id()
    {
        return kind.id;
    }

    @Override
    public BuildCategory category()
    {
        return BuildCategory.POWER;
    }

    @Override
    public Component detail()
    {
        return Component.translatable("habitat." + Abyssia.MODID + ".generator.detail." + kind.id, kind.width, kind.depth, kind.height);
    }

    @Override
    public List<ItemStack> cost()
    {
        return kind.cost();
    }

    // ---------------------------------------------------------------- placement

    @Nullable
    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        Level level = player.level();
        double reach = HabitatPlan.clampDistance(distance);
        Vec3 eye = player.getEyePosition(partialTick);
        BlockPos aim = BlockPos.containing(eye.add(player.getViewVector(partialTick).scale(reach)));
        Direction forward = HabitatPlan.facing(rot);

        if (kind == GeneratorKind.GEOTHERMAL)
        {
            BlockPos vent = findVent(level, aim);
            if (vent != null)
            {
                BlockPos core = vent.above();
                return new Placement(kind, core.relative(forward, -kind.cz).relative(forward.getClockWise(), -kind.cx).below(kind.cy), forward, true);
            }
        }
        else
        {
            HitResult hit = player.pick(reach, partialTick, false);
            if (hit instanceof BlockHitResult bhit && hit.getType() == HitResult.Type.BLOCK && bhit.getDirection().getAxis().isHorizontal()
                    && HabitatPower.isShell(level.getBlockState(bhit.getBlockPos())))
            {
                Direction out = bhit.getDirection();
                BlockPos back = bhit.getBlockPos().relative(out);
                // the middle of the back row sits against the hit block
                int mx = kind.width / 2, my = kind.height / 2;
                return new Placement(kind, back.relative(out.getClockWise(), -mx).below(my), out, true);
            }
        }
        // free placement: footprint centred horizontally on the aim point, bottom row on the aimed y
        BlockPos origin = aim.relative(forward, -(kind.depth / 2)).relative(forward.getClockWise(), -(kind.width / 2));
        return new Placement(kind, origin, forward, false);
    }

    /** the topmost thermal vent block closest to the aim point (never loads chunks) */
    @Nullable
    private static BlockPos findVent(Level level, BlockPos aim)
    {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -VENT_SEARCH_XZ; dx <= VENT_SEARCH_XZ; dx++)
            for (int dz = -VENT_SEARCH_XZ; dz <= VENT_SEARCH_XZ; dz++)
                for (int dy = VENT_SEARCH_UP; dy >= -VENT_SEARCH_DOWN; dy--)
                {
                    m.set(aim.getX() + dx, aim.getY() + dy, aim.getZ() + dz);
                    if (!level.isLoaded(m) || !(level.getBlockState(m).getBlock() instanceof ThermalVentBlock)) continue;
                    if (level.getBlockState(m.above()).getBlock() instanceof ThermalVentBlock) continue;
                    double d = m.distSqr(aim);
                    if (d < bestDist)
                    {
                        bestDist = d;
                        best = m.immutable();
                    }
                }
        return best;
    }

    // ---------------------------------------------------------------- rules

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BuildCheck water = BuildChecks.water(level, player, kind.cells(p.origin(), p.forward()), p.box());
        if (!water.ok()) return water;
        if (kind == GeneratorKind.GEOTHERMAL && !activeVent(level, p.controller().below())) return BuildCheck.fail("no_vent");
        return BuildCheck.OK;
    }

    private static boolean activeVent(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ThermalVentBlock && state.getValue(ThermalVentBlock.ACTIVITY) != VentActivity.DORMANT;
    }

    // ---------------------------------------------------------------- build / dismantle

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BlockPos controller = p.controller();
        BlockState part = ModGenerators.PART.get().defaultBlockState();
        BlockState core = kind.controller().defaultBlockState().setValue(GeneratorBlock.FACING, p.forward());
        List<BuildStep> steps = new ArrayList<>();
        for (BlockPos pos : kind.solidCells(p.origin(), p.forward()))
            steps.add(new BuildStep(pos, pos.equals(controller) ? core : part, null));
        return BuildLayout.of(steps);
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BlockPos controller = p.controller();
        for (BlockPos pos : kind.solidCells(p.origin(), p.forward()))
            if (level.getBlockEntity(pos) instanceof GeneratorPartBlockEntity part) part.setController(controller);
        return new Completion(-1, Component.translatable("message." + Abyssia.MODID + ".habitat.generator_built", displayName()));
    }

    @Override
    public boolean canDismantle()
    {
        return true;
    }

    /** Turns every part / the controller of the unit back into water (the controller drops its fuel slot). */
    @Override
    public boolean dismantle(ServerLevel level, ServerPlayer player, BuiltUnits.Unit unit)
    {
        Direction forward = HabitatPlan.facing(unit.rot());
        Block controller = kind.controller();
        List<BlockPos> cells = kind.solidCells(unit.origin(), forward);
        for (int i = cells.size() - 1; i >= 0; i--)
        {
            BlockPos pos = cells.get(i);
            BlockState state = level.getBlockState(pos);
            if (state.is(ModGenerators.PART.get()) || state.is(controller))
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        }
        com.abyssia.habitat.dismantle.Dismantler.refund(level, player, unit.paid(), unit.origin());
        return true;
    }
}
