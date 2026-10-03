package com.abyssia.habitat.charging;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildChecks;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.habitat.build.BuiltUnits;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Map;

/**
 * BT01e charging station as an EQUIPMENT build entry: one block on the floor of a registered module's interior
 * (aimed cell, dropped onto the floor), front facing the R rotation. Dismantle: block removed, 80 % of the paid
 * materials back (overflow dropped).
 */
public final class ChargingStationEntry implements BuildEntry
{
    public static final String ID = "charging_station";
    private static final int MAX_DROP = 3;

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
        return ChargingContent.STATION.get().defaultBlockState().setValue(ChargingStationBlock.FACING, facing);
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
        return Component.translatable("habitat." + Abyssia.MODID + "." + ID + ".detail", ChargingStationBlockEntity.CHARGE_RATE,
                (int) ChargingStationBlockEntity.RANGE);
    }

    @Override
    public List<ItemStack> cost()
    {
        return List.of(new ItemStack(Items.IRON_INGOT, 16), new ItemStack(Items.COPPER_INGOT, 16), new ItemStack(Items.GLASS, 8));
    }

    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        Level level = player.level();
        int reach = HabitatPlan.clampDistance(distance);
        HitResult hit = player.pick(reach, partialTick, false);
        BlockPos pos = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
                ? b.getBlockPos().relative(b.getDirection())
                : BlockPos.containing(player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(reach)));
        for (int i = 0; i < MAX_DROP && level.getBlockState(pos.below()).isAir(); i++) pos = pos.below();
        return new Placement(pos.immutable(), HabitatPlan.facing(rot));
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        BlockPos pos = ((Placement) placement).pos;
        BuildCheck check = BuildChecks.interior(level, player, List.of(pos), new AABB(pos));
        if (!check.ok()) return check;
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return BuildCheck.fail("charging_floor");
        return BuildCheck.OK;
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        return BuildLayout.of(List.of(new BuildStep(p.pos, state(p.forward), null, Blocks.AIR.defaultBlockState())));
    }

    @Override
    public boolean canDismantle()
    {
        return true;
    }

    @Override
    public boolean dismantle(ServerLevel level, ServerPlayer player, BuiltUnits.Unit unit)
    {
        BlockPos pos = unit.origin();
        if (level.getBlockState(pos).is(ChargingContent.STATION.get())) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (ItemStack paid : unit.paid())
        {
            int count = (int) Math.floor(paid.getCount() * 0.8);
            if (count <= 0) continue;
            ItemStack back = paid.copy();
            back.setCount(count);
            if (!player.getInventory().add(back) && !back.isEmpty()) player.drop(back, false);
        }
        player.inventoryMenu.broadcastChanges();
        return true;
    }
}
