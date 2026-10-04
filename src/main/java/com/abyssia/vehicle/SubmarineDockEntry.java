package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatMode;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildChecks;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.habitat.build.BuiltUnits;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * SUB02 submarine dock as an EQUIPMENT build entry: aim anywhere inside a moon pool and it snaps to the pool centre,
 * top interior cell (x 0, z mid, y = height - 2), hanging from the ceiling. Refused outside a (registered) moon pool and
 * when that pool already has its dock. R rotates it: the facing = where the docked submarine's nose points (SUB04, gangway on the right-hand side). Dismantle: block removed, 80 % of the paid
 * materials back (overflow dropped), like the charging station.
 */
public final class SubmarineDockEntry implements BuildEntry
{
    public static final String ID = "submarine_dock";
    /** how far up from the aimed / eye cell the ceiling is searched (module height 5, aim may land below the floor) */
    private static final int CEILING_SEARCH = 8;

    /** {@code centre} = the moon pool's centre ceiling light; {@code pool} false = some other 13 x 13 room */
    public record Placement(BlockPos pos, BlockPos centre, boolean pool, Direction facing) implements BuildPlacement
    {
        @Override
        public BlockPos origin()
        {
            return pos;
        }

        @Override
        public Direction forward()
        {
            return facing;
        }

        /** always snapped to the pool centre */
        @Override
        public boolean snapped()
        {
            return true;
        }

        @Override
        public AABB box()
        {
            return new AABB(pos);
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            return Map.of(pos, state(facing));
        }
    }

    private static BlockState state(Direction facing)
    {
        return VehicleContent.SUBMARINE_DOCK.get().defaultBlockState().setValue(SubmarineDockBlock.FACING, facing);
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
        return Component.translatable("habitat." + Abyssia.MODID + "." + ID + ".detail", SubmarineDockBlockEntity.CAPACITY);
    }

    @Override
    public List<ItemStack> cost()
    {
        return List.of(mod("abyssal_alloy_ingot", 6), mod("conductive_alloy_ingot", 4), new ItemStack(Items.IRON_INGOT, 12),
                new ItemStack(Items.SEA_LANTERN, 1));
    }

    private static ItemStack mod(String name, int count)
    {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name)), count);
    }

    @Nullable
    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        Level level = player.level();
        int reach = HabitatPlan.clampDistance(distance);
        HitResult hit = player.pick(reach, partialTick, false);
        BlockPos aimed = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
                ? b.getBlockPos().relative(b.getDirection())
                : BlockPos.containing(player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(reach)));
        // up to the ceiling of the module, then its centre light (works on the client too: no HabitatBuilder data there);
        // aiming into the pool water often hits nothing in reach: then start from the player's own eye cell
        BlockPos centre = centreFrom(level, aimed);
        if (centre == null) centre = centreFrom(level, BlockPos.containing(player.getEyePosition(partialTick)));
        if (centre == null) return null;
        // a moon pool has water at its floor centre (y 0)
        boolean pool = level.getFluidState(centre.below(HabitatMode.MOON_POOL.height - 1)).is(FluidTags.WATER);
        return new Placement(centre.below().immutable(), centre.immutable(), pool, HabitatPlan.facing(rot));
    }

    /** centre ceiling light of the 13 x 13 module whose ceiling is above {@code from}, or null */
    @Nullable
    private static BlockPos centreFrom(Level level, BlockPos from)
    {
        BlockPos ceiling = ceilingAbove(level, from);
        return ceiling == null ? null : HabitatPlan.roomCeilingCentre(level, ceiling);
    }

    /** first habitat ceiling / light block at or above {@code from} within CEILING_SEARCH, or null */
    @Nullable
    private static BlockPos ceilingAbove(Level level, BlockPos from)
    {
        for (int i = 0; i <= CEILING_SEARCH; i++)
        {
            BlockState state = level.getBlockState(from.above(i));
            if (state.is(ModHabitat.CEILING.get()) || state.is(ModHabitat.LIGHT.get())) return from.above(i).immutable();
        }
        return null;
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        if (!p.pool) return BuildCheck.fail("dock_not_pool");
        if (level.getBlockState(p.pos).getBlock() instanceof SubmarineDockBlock) return BuildCheck.fail("dock_exists");
        if (level instanceof ServerLevel server && !HabitatPlan.registeredRoom(server, p.centre)) return BuildCheck.fail("dock_not_pool");
        return BuildChecks.interior(level, player, List.of(p.pos), null);
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        return BuildLayout.of(List.of(new BuildStep(p.pos, state(p.facing), null,
                Blocks.AIR.defaultBlockState())));
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
        if (level.getBlockState(pos).getBlock() instanceof SubmarineDockBlock) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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
