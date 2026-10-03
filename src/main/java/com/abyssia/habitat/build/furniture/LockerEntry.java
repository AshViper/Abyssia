package com.abyssia.habitat.build.furniture;

import com.abyssia.Abyssia;
import com.abyssia.furniture.LargeLockerBlock;
import com.abyssia.furniture.LockerPart;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BT01h: the large locker (2x1x2, four cells) as an EQUIPMENT build entry on a module's floor. Cost = the former crafting
 * recipe. Dismantle: the base cell goes first (spills the contents, clears the other cells without drops), 80 % back.
 */
public final class LockerEntry implements BuildEntry
{
    public static final String ID = "large_locker";
    private static final int MAX_DROP = 3;

    /** {@code origin} = base (bottom-left) cell, {@code forward} = the locker's FACING */
    public record Placement(BlockPos origin, Direction forward) implements BuildPlacement
    {
        public List<BlockPos> cells()
        {
            List<BlockPos> out = new ArrayList<>(4);
            for (LockerPart part : LockerPart.values()) out.add(part.from(origin, forward));
            return out;
        }

        @Override
        public AABB box()
        {
            AABB box = new AABB(origin);
            for (BlockPos pos : cells()) box = box.minmax(new AABB(pos));
            return box;
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            Map<BlockPos, BlockState> out = new HashMap<>();
            for (LockerPart part : LockerPart.values()) out.put(part.from(origin, forward), state(forward, part));
            return out;
        }
    }

    static BlockState state(Direction facing, LockerPart part)
    {
        return ModFurniture.LARGE_LOCKER.get().defaultBlockState().setValue(LargeLockerBlock.FACING, facing).setValue(LargeLockerBlock.PART, part);
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
        return List.of(FurnitureCost.mod("iron_plate", 6), FurnitureCost.mod("corrosion_alloy_ingot", 2), FurnitureCost.mod("machine_frame", 1));
    }

    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        return new Placement(FurnitureCost.aimedFloorCell(player, distance, partialTick, MAX_DROP), HabitatPlan.facing(rot));
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BuildCheck check = BuildChecks.interior(level, player, p.cells(), p.box());
        if (!check.ok()) return check;
        for (LockerPart part : LockerPart.values())
        {
            if (part.top) continue;
            BlockPos below = part.from(p.origin, p.forward).below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return BuildCheck.fail("furniture_floor");
        }
        return BuildCheck.OK;
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        List<BuildStep> steps = new ArrayList<>(4);
        for (LockerPart part : new LockerPart[] {LockerPart.BL, LockerPart.BR, LockerPart.TL, LockerPart.TR})
            steps.add(new BuildStep(part.from(p.origin, p.forward), state(p.forward, part), null, Blocks.AIR.defaultBlockState()));
        return BuildLayout.of(steps);
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        return new Completion(-1, Component.translatable("message." + Abyssia.MODID + ".habitat.furniture_built", displayName()));
    }

    @Override
    public boolean canDismantle()
    {
        return true;
    }

    @Override
    public boolean dismantle(ServerLevel level, ServerPlayer player, BuiltUnits.Unit unit)
    {
        BlockPos base = unit.origin();
        if (level.getBlockState(base).is(ModFurniture.LARGE_LOCKER.get()))
            level.setBlock(base, level.getFluidState(base).createLegacyBlock(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        Dismantler.refund(level, player, unit.paid(), base);
        return true;
    }
}
