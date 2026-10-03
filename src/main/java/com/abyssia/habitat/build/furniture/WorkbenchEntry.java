package com.abyssia.habitat.build.furniture;

import com.abyssia.Abyssia;
import com.abyssia.furniture.WallWorkbenchBlock;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildChecks;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.registry.ModFurniture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Map;

/**
 * BT01h: the wall workbench as an EQUIPMENT build entry: one cell of a module's interior next to a wall (aimed wall face,
 * FACING = away from the wall). Cost = the former crafting recipe. Dismantle = the generic Dismantler fixture path
 * (the block's onRemove drops the charge slot) + 80 % refund.
 */
public final class WorkbenchEntry implements BuildEntry
{
    public static final String ID = "wall_workbench";

    /** {@code pos} = the workbench cell, {@code forward} = FACING (away from the wall) */
    public record Placement(BlockPos pos, Direction forward) implements BuildPlacement
    {
        @Override
        public BlockPos origin()
        {
            return pos;
        }

        @Override
        public AABB box()
        {
            return new AABB(pos);
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            return Map.of(pos, state(forward));
        }
    }

    static BlockState state(Direction facing)
    {
        return ModFurniture.WALL_WORKBENCH.get().defaultBlockState().setValue(WallWorkbenchBlock.FACING, facing);
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
    public Component detail()
    {
        return Component.translatable("habitat." + Abyssia.MODID + "." + ID + ".detail");
    }

    @Override
    public List<ItemStack> cost()
    {
        return List.of(FurnitureCost.mod("iron_plate", 3), FurnitureCost.mod("industrial_panel", 2), FurnitureCost.mod("machine_frame", 1),
                FurnitureCost.mod("conductive_alloy_ingot", 2), FurnitureCost.vanilla("crafting_table", 1));
    }

    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        int reach = HabitatPlan.clampDistance(distance);
        HitResult hit = player.pick(reach, partialTick, false);
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK && b.getDirection().getAxis().isHorizontal())
            return new Placement(b.getBlockPos().relative(b.getDirection()).immutable(), b.getDirection());
        return new Placement(FurnitureCost.aimedFloorCell(player, distance, partialTick, 0), HabitatPlan.facing(rot));
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BuildCheck check = BuildChecks.interior(level, player, List.of(p.pos), new AABB(p.pos));
        if (!check.ok()) return check;
        if (!state(p.forward).canSurvive(level, p.pos)) return BuildCheck.fail("furniture_wall");
        return BuildCheck.OK;
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        return BuildLayout.of(List.of(new BuildStep(p.pos, state(p.forward), null, Blocks.AIR.defaultBlockState())));
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        return new Completion(-1, Component.translatable("message." + Abyssia.MODID + ".habitat.furniture_built", displayName()));
    }
}
