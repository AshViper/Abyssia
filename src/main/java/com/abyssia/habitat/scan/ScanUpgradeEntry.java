package com.abyssia.habitat.scan;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.power.HabitatBases;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * BT01f: radar upgrade. Aim at a scan console (or any block of a scan room - the console is found from it) and pay
 * the next level's materials; a 20-tick job (no block changes) then raises the console's upgrade level by one.
 * Level 0..3 = radius 32 / 48 / 64 / 96. Not a dismantlable unit.
 */
public final class ScanUpgradeEntry implements BuildEntry
{
    public static final String ID = "scan_upgrade";
    private static final double REACH = 6.0;
    /** console search around the aimed block when no HabitatBases box is known (client) */
    private static final int SEARCH = 4;
    /** iron / copper / glass for level 1..3 */
    private static final int[][] COST = {{16, 16, 8}, {24, 24, 12}, {32, 32, 16}};

    /** client: the level the last plan() would upgrade to (displayName has no context) */
    private static volatile int hintNext = 1;

    /** next = level after the upgrade (1..3) */
    public record Placement(BlockPos console, int next) implements BuildPlacement
    {
        @Override
        public BlockPos origin()
        {
            return console;
        }

        @Override
        public Direction forward()
        {
            return Direction.NORTH;
        }

        @Override
        public AABB box()
        {
            return new AABB(console);
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            return Map.of();
        }

        @Override
        public BlockPos target()
        {
            return console;
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
        return BuildCategory.UPGRADE;
    }

    @Override
    public Mode mode()
    {
        return Mode.TARGET;
    }

    @Override
    public Component displayName()
    {
        return Component.translatable("habitat." + Abyssia.MODID + ".mode." + ID, hintNext);
    }

    @Override
    public Component detail()
    {
        int next = hintNext;
        return Component.translatable("habitat." + Abyssia.MODID + ".scan_upgrade.detail", ScanData.radius(next), ScanData.halfHeight(next));
    }

    @Override
    public List<ItemStack> cost()
    {
        return cost(1);
    }

    @Override
    public List<ItemStack> cost(Level level, BuildPlacement placement)
    {
        return cost(((Placement) placement).next);
    }

    private static List<ItemStack> cost(int next)
    {
        int[] c = COST[Math.max(1, Math.min(ScanData.MAX_TIER, next)) - 1];
        List<ItemStack> cost = List.of(new ItemStack(Items.IRON_INGOT, c[0]), new ItemStack(Items.COPPER_INGOT, c[1]), new ItemStack(Items.GLASS, c[2]));
        // Lv3 also needs the material system's top part (user 2026-10-03: the abyssal power core had no other use)
        if (next >= ScanData.MAX_TIER)
            return List.of(cost.get(0), cost.get(1), cost.get(2), new ItemStack(com.abyssia.registry.ModItems.ABYSSAL_POWER_CORE.get()));
        return cost;
    }

    @Nullable
    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        Level level = player.level();
        HitResult hit = player.pick(REACH, partialTick, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos console = findConsole(level, block.getBlockPos());
        if (console == null || !(level.getBlockEntity(console) instanceof ScanConsoleBlockEntity be)) return null;
        int next = be.upgrade() + 1;
        if (level.isClientSide) hintNext = Math.min(next, ScanData.MAX_TIER);
        return new Placement(console, next);
    }

    @Nullable
    private static BlockPos findConsole(Level level, BlockPos aimed)
    {
        if (level.getBlockState(aimed).is(ModHabitat.SCAN_CONSOLE.get())) return aimed;
        if (level instanceof ServerLevel server)
        {
            for (HabitatBases.Module m : HabitatBases.get(server).modules())
            {
                BoundingBox b = m.box();
                if (!b.isInside(aimed)) continue;
                for (BlockPos pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
                    if (level.getBlockState(pos).is(ModHabitat.SCAN_CONSOLE.get())) return pos.immutable();
            }
            return null;
        }
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(aimed.offset(-SEARCH, -SEARCH, -SEARCH), aimed.offset(SEARCH, SEARCH, SEARCH)))
            if (level.getBlockState(pos).is(ModHabitat.SCAN_CONSOLE.get()) && (best == null || pos.distSqr(aimed) < best.distSqr(aimed)))
                best = pos.immutable();
        return best;
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        if (!(level.getBlockEntity(p.console) instanceof ScanConsoleBlockEntity be)) return BuildCheck.NO_TARGET;
        // sequence break: only the very next level
        if (p.next != be.upgrade() + 1) return BuildCheck.NO_TARGET;
        if (p.next > ScanData.MAX_TIER) return BuildCheck.fail("scan_max");
        if (!level.mayInteract(player, p.console)) return BuildCheck.PERMISSION;
        return BuildCheck.OK;
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        return BuildLayout.of(List.of());
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BlockEntity be = level.getBlockEntity(p.console);
        if (be instanceof ScanConsoleBlockEntity console && console.upgrade() == p.next - 1)
        {
            console.setUpgrade(p.next);
            return new Completion(-1, Component.translatable("message." + Abyssia.MODID + ".habitat.scan_upgraded", p.next,
                    ScanData.radius(p.next), ScanData.halfHeight(p.next)));
        }
        return new Completion(-1, Component.translatable("message." + Abyssia.MODID + ".habitat.no_target"));
    }

    @Override
    public boolean recordsUnit()
    {
        return false;
    }
}
