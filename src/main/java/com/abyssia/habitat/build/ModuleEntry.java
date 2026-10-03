package com.abyssia.habitat.build;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.HabitatMode;
import com.abyssia.habitat.HabitatPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BT01a: an H01 habitat module (HabitatMode) as a build entry under {@link BuildCategory#MODULE}. Id = the mode id, so
 * constructors saved before BT01 keep their selection. Behaviour is the H01..H12 code unchanged: HabitatPlan (snap,
 * rules), HabitatBuilder layout / connect / legs, HabitatPower registration.
 */
public final class ModuleEntry implements BuildEntry
{
    public final HabitatMode mode;

    public ModuleEntry(HabitatMode mode)
    {
        this.mode = mode;
    }

    /** the placement of a module: just its HabitatPlan */
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
            return HabitatBuilder.shellStates(plan, level);
        }
    }

    @Override
    public String id()
    {
        return mode.id;
    }

    @Override
    public BuildCategory category()
    {
        return BuildCategory.MODULE;
    }

    @Override
    public Component displayName()
    {
        return mode.displayName();
    }

    @Override
    public Component detail()
    {
        return Component.translatable("screen." + Abyssia.MODID + ".habitat.size", mode.width, mode.depth, mode.height);
    }

    @Override
    public List<ItemStack> cost()
    {
        List<ItemStack> out = new ArrayList<>(mode.cost.size());
        for (HabitatMode.Cost cost : mode.cost)
        {
            Item item = cost.item().get();
            if (item != null) out.add(new ItemStack(item, cost.count()));
        }
        return out;
    }

    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        return new Placement(HabitatPlan.plan(player, mode, rot, distance, partialTick));
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        HabitatPlan plan = ((Placement) placement).plan;
        return switch (plan.check(level, player, player.getMainHandItem()))
        {
            case NONE -> BuildCheck.OK;
            case NOT_WATER -> BuildCheck.NOT_WATER;
            case ENTITY -> BuildCheck.ENTITY;
            case PERMISSION -> BuildCheck.PERMISSION;
        };
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        return HabitatBuilder.moduleLayout(((Placement) placement).plan);
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        return HabitatBuilder.completeModule(level, ((Placement) placement).plan);
    }
}
