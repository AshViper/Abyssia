package com.abyssia.habitat.custom;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.HabitatConnectedBlock;
import com.abyssia.habitat.HabitatMode;
import com.abyssia.habitat.HabitatMode.Face;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.HabitatWindowBlock;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.habitat.build.BuiltUnits;
import com.abyssia.habitat.build.ModuleEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BT01e open entrance (MODULE): the H01 ENTRANCE (5 x 5 x 5, near-face hatch to the base) whose outer door - the
 * 3 x 3 panel of the far face (door, frame and the trim / wall beside it) - is an air membrane instead: walk / swim
 * straight in, the water stays out. Everything else (snap, rules, connect, HabitatPower registration, dismantle)
 * is the ENTRANCE {@link ModuleEntry}, wrapped.
 */
public final class OpenEntranceEntry implements BuildEntry
{
    public static final String ID = "open_entrance";
    private static final ModuleEntry ENTRANCE = new ModuleEntry(HabitatMode.ENTRANCE);

    public record Placement(HabitatPlan plan) implements BuildPlacement
    {
        @Override
        public BlockPos origin()
        {
            return plan.origin();
        }

        @Override
        public Direction forward()
        {
            return plan.forward();
        }

        @Override
        public boolean snapped()
        {
            return plan.snapped();
        }

        @Override
        public AABB box()
        {
            return plan.box();
        }

        @Override
        public List<AABB> highlights()
        {
            return plan.connectorPanels();
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            Map<BlockPos, BlockState> out = new HashMap<>();
            for (BuildStep step : steps(plan))
            {
                out.put(step.pos(), step.state());
                if (step.upper() != null) out.put(step.pos().above(), step.upper());
            }
            for (BlockPos pos : HabitatBuilder.legs(level, plan)) out.put(pos, HabitatBuilder.support(true));
            return out;
        }

        ModuleEntry.Placement module()
        {
            return new ModuleEntry.Placement(plan);
        }
    }

    /** the H01 module this entry builds (its BuiltUnits record holds that module's origin / rotation) */
    public ModuleEntry moduleEntry()
    {
        return ENTRANCE;
    }

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public BuildCategory category()
    {
        return BuildCategory.MODULE;
    }

    @Override
    public Component detail()
    {
        return ENTRANCE.detail();
    }

    @Override
    public List<ItemStack> cost()
    {
        return List.of(new ItemStack(Items.IRON_INGOT, 16), new ItemStack(Items.GLASS, 8));
    }

    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        return new Placement(ENTRANCE.plan(player, rot, distance, partialTick).plan());
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        return ENTRANCE.check(level, player, ((Placement) placement).module());
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        HabitatPlan plan = ((Placement) placement).plan;
        BuildLayout base = HabitatBuilder.moduleLayout(plan);
        return new BuildLayout(steps(plan), base.air(), base.water());
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        Completion done = ENTRANCE.complete(level, player, ((Placement) placement).module());
        Object opened = 0;
        if (done.message() != null && done.message().getContents() instanceof TranslatableContents tc && tc.getArgs().length > 1)
            opened = tc.getArgs()[1];
        return new Completion(done.moduleId(), Component.translatable("message." + Abyssia.MODID + ".habitat.built", displayName(), opened));
    }

    @Override
    public boolean canDismantle()
    {
        return ENTRANCE.canDismantle();
    }

    @Override
    public boolean dismantle(ServerLevel level, ServerPlayer player, BuiltUnits.Unit unit)
    {
        boolean done = ENTRANCE.dismantle(level, player, unit);
        if (done)
        {
            // the ENTRANCE dismantle knows the door, not the membrane: whatever membrane is left becomes water
            BoundingBox b = unit.box();
            for (BlockPos pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
                if (level.getBlockState(pos).is(CustomContent.MEMBRANE.get()))
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        }
        return done;
    }

    // ---------------------------------------------------------------- layout

    /** the far face's 3 x 3 panel (lateral -1..1, y1..3): door, frame and the hull beside them */
    public static Set<BlockPos> membranePanel(HabitatPlan plan)
    {
        Set<BlockPos> out = new HashSet<>();
        for (int lat = -1; lat <= 1; lat++)
            for (int y = 1; y <= 3; y++) out.add(plan.faceCell(Face.FAR, lat, y));
        return out;
    }

    /** ENTRANCE shell steps with the far panel swapped for membrane, bottom-up, hull connections re-linked */
    static List<BuildStep> steps(HabitatPlan plan)
    {
        Set<BlockPos> panel = membranePanel(plan);
        List<BuildStep> steps = new ArrayList<>();
        for (BuildStep step : HabitatBuilder.moduleLayout(plan).steps())
            if (!panel.contains(step.pos())) steps.add(step);
        BlockState membrane = CustomContent.MEMBRANE.get().defaultBlockState();
        for (BlockPos pos : panel) steps.add(new BuildStep(pos, membrane, null));
        steps.sort(Comparator.comparingInt(s -> s.pos().getY()));

        // the hull next to the panel was linked to the door-side trim / wall: link again without them
        Map<BlockPos, Block> linked = new HashMap<>();
        for (BuildStep step : steps)
        {
            Block block = step.state().getBlock();
            if (step.state().hasProperty(PipeBlock.NORTH) && step.state().hasProperty(PipeBlock.DOWN)
                    && (block instanceof HabitatWindowBlock || block instanceof HabitatConnectedBlock))
                linked.put(step.pos(), block);
        }
        for (int i = 0; i < steps.size(); i++)
        {
            BuildStep step = steps.get(i);
            Block block = linked.get(step.pos());
            if (block != null)
                steps.set(i, new BuildStep(step.pos(), HabitatWindowBlock.connected(step.state(), d -> linked.get(step.pos().relative(d)) == block),
                        step.upper(), step.revert()));
        }
        return steps;
    }
}
