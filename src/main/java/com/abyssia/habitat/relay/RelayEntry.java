package com.abyssia.habitat.relay;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WR01 wireless power relay as an EQUIPMENT build entry: two cells (pos, pos.above()) of air or water at the aimed cell
 * (dropped onto the floor like the charging station), inside or outside a base, no shell contact needed; front facing
 * the R rotation. Dismantle: both blocks removed (water comes back where it was waterlogged), 80 % of the paid
 * materials back (overflow dropped).
 */
public final class RelayEntry implements BuildEntry
{
    public static final String ID = "wireless_power_relay";
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
            return new AABB(pos).expandTowards(0, 1, 0);
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            Map<BlockPos, BlockState> out = new LinkedHashMap<>();
            out.put(pos, lower(forward, false));
            out.put(pos.above(), upper(forward, false));
            return out;
        }
    }

    public static BlockState lower(Direction facing, boolean waterlogged)
    {
        return RelayContent.RELAY.get().defaultBlockState().setValue(WirelessPowerRelayBlock.FACING, facing)
                .setValue(WirelessPowerRelayBlock.WATERLOGGED, waterlogged);
    }

    public static BlockState upper(Direction facing, boolean waterlogged)
    {
        return RelayContent.RELAY_TOP.get().defaultBlockState().setValue(WirelessPowerRelayTopBlock.FACING, facing)
                .setValue(WirelessPowerRelayTopBlock.WATERLOGGED, waterlogged);
    }

    /** air or water (incl. plants standing in water): a relay half may go there */
    public static boolean free(BlockState state)
    {
        return state.isAir() || HabitatPlan.replaceable(state);
    }

    public static boolean wet(Level level, BlockPos pos)
    {
        return level.getFluidState(pos).is(FluidTags.WATER);
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
        return Component.translatable("habitat." + Abyssia.MODID + "." + ID + ".detail", (int) RelayNetwork.MAX_DISTANCE,
                RelayNetwork.MAX_PER_LINK);
    }

    @Override
    public List<ItemStack> cost()
    {
        return List.of(new ItemStack(Items.IRON_INGOT, 12), new ItemStack(Items.COPPER_INGOT, 8), new ItemStack(Items.REDSTONE, 8),
                new ItemStack(Items.GLASS, 4), new ItemStack(Items.IRON_BLOCK, 2));
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
        for (int i = 0; i < MAX_DROP && free(level.getBlockState(pos.below())); i++) pos = pos.below();
        return new Placement(pos.immutable(), HabitatPlan.facing(rot));
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        ItemStack stack = player.getMainHandItem();
        for (BlockPos cell : List.of(p.pos, p.pos.above()))
        {
            if (!free(level.getBlockState(cell))) return BuildCheck.fail("relay_space");
            if (!level.mayInteract(player, cell) || !player.mayUseItemAt(cell, Direction.UP, stack)) return BuildCheck.PERMISSION;
        }
        if (!level.getBlockState(p.pos.below()).isFaceSturdy(level, p.pos.below(), Direction.UP)) return BuildCheck.fail("relay_floor");
        if (!level.getEntitiesOfClass(LivingEntity.class, p.box(), e -> e.isAlive() && !e.isSpectator()).isEmpty()) return BuildCheck.ENTITY;
        return BuildCheck.OK;
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BlockPos up = p.pos.above();
        return BuildLayout.of(List.of(
                new BuildStep(p.pos, lower(p.forward, wet(level, p.pos)), null, revert(level, p.pos)),
                new BuildStep(up, upper(p.forward, wet(level, up)), null, revert(level, up))));
    }

    /** water cells: null (the builder's water rule, cancel puts water back); air cells: that air */
    private static BlockState revert(ServerLevel level, BlockPos pos)
    {
        BlockState now = level.getBlockState(pos);
        return HabitatPlan.replaceable(now) ? null : now;
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
        BlockState lower = level.getBlockState(pos);
        if (lower.is(RelayContent.RELAY.get())) WirelessPowerRelayBlock.clearCell(level, pos, lower); // removes the antenna too
        BlockState upper = level.getBlockState(pos.above());
        if (upper.is(RelayContent.RELAY_TOP.get())) WirelessPowerRelayBlock.clearCell(level, pos.above(), upper);
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
