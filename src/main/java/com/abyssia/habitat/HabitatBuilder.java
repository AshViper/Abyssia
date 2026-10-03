package com.abyssia.habitat;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatLayout.Part;
import com.abyssia.habitat.HabitatMode.Face;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildRegistry;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.habitat.build.BuiltUnits;
import com.abyssia.habitat.build.ModuleEntry;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.habitat.power.HabitatPower;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server side of the habitat constructor (H02, BT01a): validation, materials paid up front, a {@link #DURATION}-tick
 * bottom-up assembly, auto-connection, and cancel with full refund (shell reverted to water). Runs any BuildEntry;
 * a finished build is recorded in BuiltUnits.
 */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class HabitatBuilder
{
    public static final int DURATION = 20;
    public static final double CANCEL_DISTANCE = 16.0;
    private static final String MSG = "message." + Abyssia.MODID + ".habitat.";

    public enum Result { STARTED, BUSY, NOT_WATER, ENTITY, PERMISSION, MISSING, INVALID }

    private static final Map<UUID, Job> JOBS = new HashMap<>();

    private HabitatBuilder() {}

    // ---------------------------------------------------------------- entry points

    /** H02 entry point (test harness): this module at the constructor's distance (default when not holding one). */
    public static Result startBuild(ServerPlayer player, HabitatMode mode, int rot)
    {
        ItemStack held = player.getMainHandItem();
        int distance = held.getItem() instanceof HabitatConstructorItem ? HabitatConstructorItem.distance(held) : HabitatPlan.DEFAULT_DISTANCE;
        return startBuild(player, BuildRegistry.getOrDefault(mode.id), rot, distance);
    }

    /**
     * Plans the entry from the player's aim (distance clamped 3..12), checks it, takes the materials (not in creative)
     * and starts the timed assembly. Sends the action-bar message. Usable without a client (test harness).
     */
    public static Result startBuild(ServerPlayer player, BuildEntry entry, int rot, int distance)
    {
        if (JOBS.containsKey(player.getUUID()))
        {
            message(player, Component.translatable(MSG + "busy"));
            return Result.BUSY;
        }
        BuildPlacement placement = entry.plan(player, Math.floorMod(rot, 4), HabitatPlan.clampDistance(distance), 1.0f);
        BuildCheck check = placement == null ? BuildCheck.NO_TARGET : entry.check(player.level(), player, placement);
        if (!check.ok())
        {
            message(player, check.message());
            return result(check);
        }
        boolean free = player.getAbilities().instabuild;
        List<ItemStack> cost = entry.cost(player.level(), placement);
        List<ItemStack> missing = free ? List.of() : missing(player, cost);
        if (!missing.isEmpty())
        {
            MutableComponent list = Component.empty();
            for (int i = 0; i < missing.size(); i++)
            {
                if (i > 0) list.append(", ");
                list.append(missing.get(i).getCount() + "x ").append(missing.get(i).getHoverName());
            }
            message(player, Component.translatable(MSG + "missing", list));
            return Result.MISSING;
        }
        List<ItemStack> paid = free ? List.of() : consume(player, cost);
        boolean holding = player.getMainHandItem().getItem() instanceof HabitatConstructorItem;
        BuildLayout layout = entry.layout(player.serverLevel(), placement);
        JOBS.put(player.getUUID(), new Job(player.getUUID(), player.serverLevel(), entry, placement, layout, paid, holding));
        message(player, Component.translatable(MSG + (isDismantle(entry) ? "started_dismantle" : "started"), entry.displayName()));
        return Result.STARTED;
    }

    /** BT01h: the dismantle tool returns nothing at cancel and is not a "build" in the messages */
    private static boolean isDismantle(BuildEntry entry)
    {
        return entry instanceof com.abyssia.habitat.dismantle.DismantleEntry;
    }

    private static Result result(BuildCheck check)
    {
        for (Result r : Result.values())
            if (r.name().equalsIgnoreCase(check.problem())) return r;
        return Result.INVALID;
    }

    /** The placement being assembled for this player, if any. */
    public static Optional<BuildPlacement> activePlacement(UUID player)
    {
        Job job = JOBS.get(player);
        return job == null ? Optional.empty() : Optional.of(job.placement);
    }

    /** The module plan being assembled for this player, if any (empty for other entries). */
    public static Optional<HabitatPlan> activeBuild(UUID player)
    {
        Job job = JOBS.get(player);
        return job != null && job.placement instanceof ModuleEntry.Placement p ? Optional.of(p.plan()) : Optional.empty();
    }

    public static boolean isBuilding(UUID player)
    {
        return JOBS.containsKey(player);
    }

    /** Cancels this player's build (refund + revert); false when there was none. */
    public static boolean cancelBuild(ServerPlayer player)
    {
        Job job = JOBS.remove(player.getUUID());
        if (job == null) return false;
        job.cancel(player, "cancelled");
        return true;
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event)
    {
        if (JOBS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        Iterator<Job> it = JOBS.values().iterator();
        while (it.hasNext())
        {
            Job job = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(job.owner);
            String reason = job.cancelReason(player);
            if (reason != null)
            {
                it.remove();
                job.cancel(player, reason);
            }
            else if (job.tick(player)) it.remove();
        }
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        Job job = JOBS.remove(event.getEntity().getUUID());
        if (job != null) job.cancel(event.getEntity(), "left");
    }

    // ---------------------------------------------------------------- materials

    private static void message(Player player, Component text)
    {
        player.displayClientMessage(text, true);
    }

    public static int count(Player player, Item item)
    {
        return player.getInventory().clearOrCountMatchingItems(s -> s.is(item), 0, player.inventoryMenu.getCraftSlots());
    }

    /** Costs not covered by the inventory, with the missing amount (matched by item; same items summed). */
    public static List<ItemStack> missing(Player player, List<ItemStack> cost)
    {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack need : merge(cost))
        {
            int have = count(player, need.getItem());
            if (have < need.getCount()) out.add(new ItemStack(need.getItem(), need.getCount() - have));
        }
        return out;
    }

    private static List<ItemStack> consume(Player player, List<ItemStack> cost)
    {
        List<ItemStack> paid = new ArrayList<>();
        for (ItemStack need : merge(cost))
        {
            Item item = need.getItem();
            int taken = player.getInventory().clearOrCountMatchingItems(s -> s.is(item), need.getCount(), player.inventoryMenu.getCraftSlots());
            if (taken > 0) paid.add(new ItemStack(item, taken));
        }
        player.inventoryMenu.broadcastChanges();
        return paid;
    }

    /** one stack per item, counts summed (order kept) */
    private static List<ItemStack> merge(List<ItemStack> cost)
    {
        Map<Item, Integer> sum = new java.util.LinkedHashMap<>();
        for (ItemStack stack : cost)
            if (!stack.isEmpty()) sum.merge(stack.getItem(), stack.getCount(), Integer::sum);
        List<ItemStack> out = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : sum.entrySet()) out.add(new ItemStack(e.getKey(), e.getValue()));
        return out;
    }

    // ---------------------------------------------------------------- layout to block states

    /** The H01 module's timed build: shell steps bottom-up, interior air, kept water (BT01a ModuleEntry). */
    public static BuildLayout moduleLayout(HabitatPlan plan)
    {
        List<BuildStep> shell = new ArrayList<>();
        List<BlockPos> air = new ArrayList<>(), water = new ArrayList<>();
        layout(plan, shell, air, water);
        return new BuildLayout(shell, air, water);
    }

    /** Shell blocks bottom-up (doors as one step with their upper half), interior air, kept water. */
    private static void layout(HabitatPlan plan, List<BuildStep> shell, List<BlockPos> air, List<BlockPos> water)
    {
        HabitatMode mode = plan.mode();
        int hw = HabitatLayout.halfWidth(mode);
        for (int y = 0; y < mode.height; y++)
            for (int z = 0; z < mode.depth; z++)
                for (int x = -hw; x <= hw; x++)
                {
                    Part part = HabitatLayout.partAt(mode, x, y, z);
                    BlockPos pos = plan.at(x, y, z);
                    Direction out = outwardOfShell(plan, x, z);
                    switch (part)
                    {
                        case KEEP, DOOR_UPPER -> {}
                        case AIR -> air.add(pos);
                        case WATER -> water.add(pos);
                        case DOOR_LOWER -> shell.add(new BuildStep(pos, door(out, DoubleBlockHalf.LOWER), door(out, DoubleBlockHalf.UPPER)));
                        default -> shell.add(new BuildStep(pos, stateFor(part, out), null));
                    }
                }
        // connected-texture blocks (window + hull blocks): connection booleans from the other steps of the same block
        java.util.Map<BlockPos, net.minecraft.world.level.block.Block> linked = new java.util.HashMap<>();
        for (BuildStep step : shell)
            if (step.state().hasProperty(PipeBlock.NORTH) && step.state().hasProperty(PipeBlock.DOWN)
                    && (step.state().is(ModHabitat.WINDOW.get()) || step.state().getBlock() instanceof HabitatConnectedBlock))
                linked.put(step.pos(), step.state().getBlock());
        for (int i = 0; i < shell.size(); i++)
        {
            BuildStep step = shell.get(i);
            net.minecraft.world.level.block.Block block = linked.get(step.pos());
            if (block != null)
                shell.set(i, new BuildStep(step.pos(), connectWindow(step.state(), d -> linked.get(step.pos().relative(d)) == block), null));
        }
    }

    /** Window state with its six connection properties (PipeBlock.NORTH..DOWN, when the block has them) set. */
    private static BlockState connectWindow(BlockState base, Predicate<Direction> isWindow)
    {
        BlockState state = base;
        for (Map.Entry<Direction, BooleanProperty> e : PipeBlock.PROPERTY_BY_DIRECTION.entrySet())
            if (state.hasProperty(e.getValue())) state = state.setValue(e.getValue(), isWindow.test(e.getKey()));
        return state;
    }

    public static final int MAX_LEG = 96;

    /** H09: modules that stand on support legs */
    public static boolean hasLegs(HabitatMode mode)
    {
        return mode == HabitatMode.ROOM || mode == HabitatMode.MOON_POOL || mode == HabitatMode.FOUNDATION;
    }

    /**
     * H09 support legs under the plan: 4 columns at local x = +-(hw - 1), z = 1 / depth - 2, from just below the floor
     * down through water / replaceable-in-water cells to the first other (non-air) block. A column that finds no such
     * ground within {@link #MAX_LEG} cells (or ends on air / the world bottom) is left out entirely. H12: so is a
     * column whose corner has a 13-wide neighbour on either side (only the outer corners of a group keep legs).
     */
    public static List<BlockPos> legs(BlockGetter level, HabitatPlan plan)
    {
        List<BlockPos> out = new ArrayList<>();
        HabitatMode mode = plan.mode();
        if (!hasLegs(mode)) return out;
        int x = HabitatLayout.halfWidth(mode) - 1;
        int minY = level.getMinBuildHeight();
        for (int lx : new int[]{-x, x})
            for (int lz : new int[]{1, mode.depth - 2})
            {
                Direction dx = plan.outward(lx > 0 ? Face.RIGHT : Face.LEFT), dz = plan.outward(lz == 1 ? Face.NEAR : Face.FAR);
                BlockPos corner = plan.at(lx > 0 ? x + 1 : -x - 1, 0, lz == 1 ? 0 : mode.depth - 1);
                if (sideNeighbour(level, corner, dx, dz) || sideNeighbour(level, corner, dz, dx)) continue;
                BlockPos top = plan.at(lx, -1, lz);
                List<BlockPos> column = new ArrayList<>();
                BlockPos pos = top;
                while (column.size() < MAX_LEG && pos.getY() >= minY && HabitatPlan.replaceable(level.getBlockState(pos)))
                {
                    column.add(pos);
                    pos = pos.below();
                }
                if (pos.getY() < minY || level.getBlockState(pos).isAir() || HabitatPlan.replaceable(level.getBlockState(pos))) continue;
                out.addAll(column);
            }
        return out;
    }

    /**
     * H12: a 13-wide module (room / moon pool / foundation) flush beyond {@code side} at this corner (our corner cell
     * at y0, {@code along} = the other outward direction there): its corner cell and the next wall cell are habitat
     * shell at our floor (y0) or at our ceiling (y4; pool channels / islands turn some floor into water).
     */
    private static boolean sideNeighbour(BlockGetter level, BlockPos corner, Direction side, Direction along)
    {
        BlockPos a = corner.relative(side), b = a.relative(along.getOpposite());
        return (shell(level.getBlockState(a)) && shell(level.getBlockState(b)))
                || (shell(level.getBlockState(a.above(4))) && shell(level.getBlockState(b.above(4))));
    }

    /**
     * H12: after a module is built, the legs of the 13-wide neighbours beside each of its corners no longer stand on an
     * outer corner of the group: their columns (habitat_support from below the floor down) go back to water.
     */
    private static void trimNeighbourLegs(Level level, HabitatPlan plan)
    {
        HabitatMode mode = plan.mode();
        if (!hasLegs(mode) || mode.width != 13) return;
        int hw = HabitatLayout.halfWidth(mode);
        for (int sx : new int[]{-1, 1})
            for (int cz : new int[]{0, mode.depth - 1})
            {
                Direction dx = plan.outward(sx > 0 ? Face.RIGHT : Face.LEFT), dz = plan.outward(cz == 0 ? Face.NEAR : Face.FAR);
                BlockPos corner = plan.at(sx * hw, 0, cz);
                // the neighbour's leg is 1 in from its corner (corner + side) on both axes
                if (sideNeighbour(level, corner, dx, dz)) removeLeg(level, corner.relative(dx, 2).relative(dz, -1).below());
                if (sideNeighbour(level, corner, dz, dx)) removeLeg(level, corner.relative(dz, 2).relative(dx, -1).below());
            }
    }

    private static void removeLeg(Level level, BlockPos top)
    {
        for (BlockPos pos = top; level.getBlockState(pos).is(ModHabitat.SUPPORT.get()); pos = pos.below())
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    }

    public static BlockState support(boolean waterlogged)
    {
        return ModHabitat.SUPPORT.get().defaultBlockState().setValue(HabitatSupportBlock.WATERLOGGED, waterlogged);
    }

    /** Shell states plus the support legs found in this level (hologram). */
    public static Map<BlockPos, BlockState> shellStates(HabitatPlan plan, BlockGetter level)
    {
        Map<BlockPos, BlockState> out = shellStates(plan);
        for (BlockPos pos : legs(level, plan)) out.put(pos, support(true));
        // H10: the floor cells a pool-to-pool channel would open are left out of the hologram
        if (plan.mode() == HabitatMode.MOON_POOL)
            for (Face face : plan.mode().connectors)
            {
                Direction dir = plan.outward(face);
                if (mergeableNeighbour(level, plan, face, dir) && poolNeighbour(level, plan, face, dir))
                    for (BlockPos pos : poolChannel(plan, face, dir)) out.remove(pos);
            }
        return out;
    }

    /** half width of the moon pool hole (7 x 7) and its distance from the wall (frame 2 + wall 1) */
    private static final int POOL_HALF = 3, POOL_EDGE = 3;

    /**
     * H10: the neighbour across this face is a moon pool flush with ours: its floor (frame + wall) is shell for the
     * 3 cells out and its hole (water source) starts 4 cells out, over lateral -3..3 at floor level.
     */
    public static boolean poolNeighbour(BlockGetter level, HabitatPlan plan, Face face, Direction out)
    {
        for (int lat = -POOL_HALF; lat <= POOL_HALF; lat++)
        {
            BlockPos wall = plan.faceCell(face, lat, 0);
            for (int d = 1; d <= POOL_EDGE; d++)
                if (!shell(level.getBlockState(wall.relative(out, d)))) return false;
            if (!level.getFluidState(wall.relative(out, POOL_EDGE + 1)).isSource()
                    || !level.getBlockState(wall.relative(out, POOL_EDGE + 1)).is(Blocks.WATER)) return false;
        }
        return true;
    }

    /**
     * H10: floor cells (y0, lateral -3..3) joining our hole to the neighbour's: our 2 frame cells and wall cell, then
     * the neighbour's wall cell and 2 frame cells (6 deep).
     */
    public static List<BlockPos> poolChannel(HabitatPlan plan, Face face, Direction out)
    {
        List<BlockPos> cells = new ArrayList<>();
        for (int lat = -POOL_HALF; lat <= POOL_HALF; lat++)
        {
            BlockPos wall = plan.faceCell(face, lat, 0);
            for (int d = -(POOL_EDGE - 1); d <= POOL_EDGE; d++) cells.add(wall.relative(out, d));
        }
        return cells;
    }

    /** Every block state of the finished shell (for the client hologram). */
    public static Map<BlockPos, BlockState> shellStates(HabitatPlan plan)
    {
        List<BuildStep> shell = new ArrayList<>();
        layout(plan, shell, new ArrayList<>(), new ArrayList<>());
        Map<BlockPos, BlockState> out = new HashMap<>();
        for (BuildStep step : shell)
        {
            out.put(step.pos(), step.state());
            if (step.upper() != null) out.put(step.pos().above(), step.upper());
        }
        return out;
    }

    private static BlockState door(Direction out, DoubleBlockHalf half)
    {
        return ModHabitat.DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, out.getOpposite())
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false).setValue(DoorBlock.HALF, half);
    }

    /** Outward direction of a wall cell (the hatch / door facing); near / far win at the corners. */
    private static Direction outwardOfShell(HabitatPlan plan, int x, int z)
    {
        HabitatMode mode = plan.mode();
        if (z == 0) return plan.outward(Face.NEAR);
        if (z == mode.depth - 1) return plan.outward(Face.FAR);
        return plan.outward(x < 0 ? Face.LEFT : Face.RIGHT);
    }

    private static BlockState stateFor(Part part, Direction out)
    {
        return switch (part)
        {
            case FLOOR -> ModHabitat.FLOOR.get().defaultBlockState();
            case TRIM -> ModHabitat.TRIM.get().defaultBlockState();
            case WALL -> ModHabitat.WALL.get().defaultBlockState();
            case CEILING -> ModHabitat.CEILING.get().defaultBlockState();
            case WINDOW -> ModHabitat.WINDOW.get().defaultBlockState();
            case LIGHT -> ModHabitat.LIGHT.get().defaultBlockState();
            case FRAME -> ModHabitat.DOOR_FRAME.get().defaultBlockState();
            case HATCH -> ModHabitat.HATCH.get().defaultBlockState().setValue(HabitatHatchBlock.FACING, out);
            case CONSOLE -> ModHabitat.SCAN_CONSOLE.get().defaultBlockState();
            default -> throw new IllegalArgumentException(part.name());
        };
    }

    /**
     * Opens connectors (H03): two 13-wide modules (room / moon pool) meeting wall to wall open the whole shared wall
     * (lateral -5..5, y1..3 on both walls; corners, floor and ceiling stay); otherwise a connector facing a full 3 x 3
     * hatch panel opens both panels; H11 then opens the 2 x 2 pillar where four merged modules meet. Returns how many
     * faces were opened.
     */
    public static int connect(Level level, HabitatPlan plan)
    {
        return connect(level, plan, new ArrayList<>());
    }

    /** As {@link #connect(Level, HabitatPlan)}; adds one cell of each opened neighbour (its wall) to {@code neighbours}. */
    public static int connect(Level level, HabitatPlan plan, List<BlockPos> neighbours)
    {
        HabitatMode mode = plan.mode();
        int opened = 0;
        for (Face face : mode.connectors)
        {
            Direction out = plan.outward(face);
            if (mode.mergesWalls() && mergeableNeighbour(level, plan, face, out))
            {
                int span = HabitatLayout.halfWidth(mode) - 1;
                for (int lat = -span; lat <= span; lat++)
                    for (int y = 1; y <= 3; y++)
                    {
                        BlockPos pos = plan.faceCell(face, lat, y);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        level.setBlock(pos.relative(out), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                if (mode == HabitatMode.MOON_POOL && poolNeighbour(level, plan, face, out))
                    for (BlockPos pos : poolChannel(plan, face, out)) poolWater(level, pos);
                neighbours.add(plan.faceCell(face, 0, 0).relative(out));
                opened++;
                continue;
            }
            List<BlockPos> ours = new ArrayList<>();
            for (int[] col : HabitatPlan.panelColumns(mode, face))
                for (int y = 1; y <= 3; y++) ours.add(plan.at(col[0], y, col[1]));
            if (!ours.stream().allMatch(p -> level.getBlockState(p.relative(out)).is(ModHabitat.HATCH.get()))) continue;
            for (BlockPos pos : ours)
            {
                level.setBlock(pos.relative(out), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                if (!mode.doors.contains(face)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            neighbours.add(ours.get(0).relative(out));
            opened++;
        }
        if (mode.mergesWalls()) openInnerCorners(level, plan);
        return opened;
    }

    /**
     * H11: where four merged 13-wide modules meet, their four corner columns form a 2 x 2 pillar inside the combined
     * room. For each corner of this module: when the 2 x 2 columns (ours + the three neighbours') are shell with floor
     * (y0) and ceiling (y4) intact, and the eight cells around them at y1..3 are opened walls (not shell, no fluid),
     * y1..3 of the columns become air. Only those eight cells touch the columns sideways, so the room stays sealed;
     * the last module of the four to finish opens it (any build order).
     */
    private static void openInnerCorners(Level level, HabitatPlan plan)
    {
        HabitatMode mode = plan.mode();
        int hw = HabitatLayout.halfWidth(mode);
        for (int sx : new int[]{-1, 1})
            for (int cz : new int[]{0, mode.depth - 1})
            {
                Direction dx = plan.outward(sx > 0 ? Face.RIGHT : Face.LEFT);
                Direction dz = plan.outward(cz == 0 ? Face.NEAR : Face.FAR);
                BlockPos base = plan.at(sx * hw, 0, cz);
                List<BlockPos> pillar = new ArrayList<>();
                for (int i = 0; i <= 1; i++)
                    for (int j = 0; j <= 1; j++) pillar.add(base.relative(dx, i).relative(dz, j));
                int[][] ring = {{-1, 0}, {-1, 1}, {2, 0}, {2, 1}, {0, -1}, {1, -1}, {0, 2}, {1, 2}};
                boolean inner = true;
                for (BlockPos col : pillar)
                {
                    if (!shell(level.getBlockState(col)) || !shell(level.getBlockState(col.above(4)))) inner = false;
                    for (int y = 1; y <= 3; y++)
                    {
                        BlockState state = level.getBlockState(col.above(y));
                        if (!shell(state) && !state.isAir()) inner = false;
                    }
                }
                for (int[] r : ring)
                    for (int y = 1; y <= 3; y++)
                    {
                        BlockState state = level.getBlockState(base.relative(dx, r[0]).relative(dz, r[1]).above(y));
                        if (shell(state) || !state.getFluidState().isEmpty()) inner = false;
                    }
                if (mode == HabitatMode.MOON_POOL) openPoolIsland(level, base, dx, dz);
                if (!inner) continue;
                for (BlockPos col : pillar)
                    for (int y = 1; y <= 3; y++) level.setBlock(col.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
    }

    /**
     * H12: four moon pools in a 2 x 2 whose holes are joined by H10 channels on all four seams leave a 6 x 6 floor
     * island (y0) between the channels around the shared corner. When the 24 floor cells around it (the inner ends
     * of the four channels) are water sources and the island is still shell (or already water), the island becomes
     * water source too: one pool. {@code base} is this module's corner cell at y0, {@code dx} / {@code dz} point out.
     */
    private static void openPoolIsland(Level level, BlockPos base, Direction dx, Direction dz)
    {
        int lo = -(POOL_EDGE - 1), hi = POOL_EDGE;
        List<BlockPos> island = new ArrayList<>();
        for (int i = lo; i <= hi; i++)
            for (int j = lo; j <= hi; j++)
            {
                BlockPos pos = base.relative(dx, i).relative(dz, j);
                BlockState state = level.getBlockState(pos);
                if (!shell(state) && !(state.is(Blocks.WATER) && level.getFluidState(pos).isSource())) return;
                island.add(pos);
            }
        for (int k = lo; k <= hi; k++)
            for (BlockPos pos : new BlockPos[]{base.relative(dx, lo - 1).relative(dz, k), base.relative(dx, hi + 1).relative(dz, k),
                    base.relative(dz, lo - 1).relative(dx, k), base.relative(dz, hi + 1).relative(dx, k)})
                if (!level.getBlockState(pos).is(Blocks.WATER) || !level.getFluidState(pos).isSource()) return;
        for (BlockPos pos : island)
            if (shell(level.getBlockState(pos))) poolWater(level, pos);
    }

    /** H10 / H12: a floor cell becomes pool water (source). */
    private static void poolWater(Level level, BlockPos pos)
    {
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * A 13-wide neighbour flush against this face: its wall (one step out) is habitat shell over the full width
     * (lateral -6..6, y0..4) with a 3 x 3 hatch panel in the middle, and its interior (two steps out) is air at
     * lateral -5..5, y1..3.
     */
    private static boolean mergeableNeighbour(BlockGetter level, HabitatPlan plan, Face face, Direction out)
    {
        int hw = HabitatLayout.halfWidth(plan.mode());
        for (int lat = -hw; lat <= hw; lat++)
            for (int y = 0; y <= 4; y++)
            {
                BlockPos wall = plan.faceCell(face, lat, y).relative(out);
                BlockState state = level.getBlockState(wall);
                boolean panel = Math.abs(lat) <= HabitatLayout.PANEL / 2 && y >= 1 && y <= 3;
                if (panel ? !state.is(ModHabitat.HATCH.get()) : !shell(state)) return false;
                if (Math.abs(lat) < hw && y >= 1 && y <= 3 && !level.getBlockState(wall.relative(out)).isAir()) return false;
            }
        return true;
    }

    /** Any block of a module shell (not the door / console). */
    public static boolean shell(BlockState state)
    {
        return state.is(ModHabitat.FLOOR.get()) || state.is(ModHabitat.TRIM.get()) || state.is(ModHabitat.WALL.get())
                || state.is(ModHabitat.CEILING.get()) || state.is(ModHabitat.WINDOW.get()) || state.is(ModHabitat.LIGHT.get())
                || state.is(ModHabitat.DOOR_FRAME.get()) || state.is(ModHabitat.HATCH.get());
    }

    // ---------------------------------------------------------------- module completion

    /**
     * Finishes an H01 module after its last tick (BT01a ModuleEntry.complete): connect hatches / walls, register with
     * HabitatPower, legs. Returns the HabitatBases module id it registered and the "built" message.
     */
    public static BuildEntry.Completion completeModule(ServerLevel level, HabitatPlan plan)
    {
        List<BlockPos> neighbours = new ArrayList<>();
        int opened = connect(level, plan, neighbours);
        HabitatPower.register(level, plan, neighbours);
        // register() appends the new module last (HabitatBases.add)
        int moduleId = HabitatBases.get(level).lastAdded();
        for (BlockPos pos : legs(level, plan))
            if (HabitatPlan.replaceable(level.getBlockState(pos)))
                level.setBlock(pos, support(level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)), Block.UPDATE_ALL);
        trimNeighbourLegs(level, plan);
        return new BuildEntry.Completion(moduleId, Component.translatable(MSG + "built", plan.mode().displayName(), opened));
    }

    private static BoundingBox blockBox(AABB b)
    {
        return new BoundingBox((int) Math.floor(b.minX), (int) Math.floor(b.minY), (int) Math.floor(b.minZ),
                (int) Math.ceil(b.maxX) - 1, (int) Math.ceil(b.maxY) - 1, (int) Math.ceil(b.maxZ) - 1);
    }

    // ---------------------------------------------------------------- a running build

    private static final class Job
    {
        final UUID owner;
        final ServerLevel level;
        final BuildEntry entry;
        final BuildPlacement placement;
        final List<ItemStack> paid;
        final boolean needsItem;
        final List<BuildStep> shell;
        final List<BlockPos> air;
        final List<BlockPos> water;
        final Vec3 centre;
        final int duration;
        int placed;
        int ticks;

        Job(UUID owner, ServerLevel level, BuildEntry entry, BuildPlacement placement, BuildLayout layout, List<ItemStack> paid, boolean needsItem)
        {
            this.owner = owner;
            this.level = level;
            this.entry = entry;
            this.placement = placement;
            this.paid = paid;
            this.needsItem = needsItem;
            this.centre = placement.box().getCenter();
            this.shell = new ArrayList<>(layout.steps());
            this.air = new ArrayList<>(layout.air());
            this.water = new ArrayList<>(layout.water());
            this.duration = Math.max(1, entry.duration());
            // BT01b: a dismantle opens the shell step by step, so the room floods during the job; remember its dry cells
            this.dry = new ArrayList<>();
            if (isDismantle(entry))
                for (BlockPos pos : water)
                    if (level.getBlockState(pos).isAir()) dry.add(pos);
        }

        /** cells of {@link #water} that were air when a dismantle started (dried again on cancel) */
        final List<BlockPos> dry;

        /** the cell still holds what the step expects to replace */
        private boolean untouched(BlockPos pos, BuildStep step)
        {
            BlockState now = level.getBlockState(pos);
            return step.revert() == null ? HabitatPlan.replaceable(now) : now.is(step.revert().getBlock());
        }

        /** null = keep going */
        String cancelReason(ServerPlayer player)
        {
            if (player == null || !player.isAlive() || player.level() != level) return "left";
            if (player.position().distanceTo(centre) > CANCEL_DISTANCE) return "too_far";
            if (needsItem && !(player.getMainHandItem().getItem() instanceof HabitatConstructorItem)) return "switched";
            for (int i = placed; i < shell.size(); i++)
            {
                BuildStep step = shell.get(i);
                if (!untouched(step.pos(), step)) return "blocked";
                if (step.upper() != null && !untouched(step.pos().above(), step)) return "blocked";
            }
            for (BlockPos pos : air)
                if (!HabitatPlan.replaceable(level.getBlockState(pos))) return "blocked";
            return null;
        }

        /** One tick of assembly; true when finished. */
        boolean tick(ServerPlayer player)
        {
            ticks++;
            int target = (int) Math.ceil(shell.size() * (double) ticks / duration);
            int from = placed;
            for (; placed < Math.min(target, shell.size()); placed++)
            {
                BuildStep step = shell.get(placed);
                level.setBlock(step.pos(), step.state(), Block.UPDATE_ALL);
                if (step.upper() != null) level.setBlock(step.pos().above(), step.upper(), Block.UPDATE_ALL);
            }
            effects(from);
            if (ticks < duration) return false;

            for (BlockPos pos : air) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            for (BlockPos pos : water)
                if (!level.getFluidState(pos).isSource()) level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            BuildEntry.Completion done = entry.complete(level, player, placement);
            if (entry.recordsUnit())
                BuiltUnits.get(level).add(entry.id(), placement.origin(), placement.rot(), blockBox(placement.box()), paid, done.moduleId());
            BlockPos at = BlockPos.containing(centre);
            level.playSound(null, at, SoundType.METAL.getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
            level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.3f, 1.4f);
            message(player, done.message() != null ? done.message() : Component.translatable(MSG + "built", entry.displayName(), 0));
            return true;
        }

        private void effects(int from)
        {
            if (placed == from) return;
            BlockPos at = shell.get(placed - 1).pos();
            if (ticks % 2 == 0)
                level.playSound(null, at, SoundType.METAL.getPlaceSound(), SoundSource.BLOCKS, 0.6f, 0.8f + level.random.nextFloat() * 0.4f);
            if (ticks % 5 == 0)
                level.playSound(null, at, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.15f, 1.3f);
            int step = Math.max(1, (placed - from) / 6);
            for (int i = from; i < placed; i += step)
            {
                Vec3 p = Vec3.atCenterOf(shell.get(i).pos());
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.01);
                level.sendParticles(ParticleTypes.GLOW, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.01);
            }
        }

        /** Blocks placed so far back to what they were (water by default), materials back to the player (dropped when full / gone). */
        void cancel(Player player, String reason)
        {
            for (int i = placed - 1; i >= 0; i--)
            {
                BuildStep step = shell.get(i);
                if (step.upper() != null) revert(step.pos().above(), step.upper(), upperHalf(step.revert()));
                revert(step.pos(), step.state(), step.revert());
            }
            // the shell is whole again: water that flowed into the room during the dismantle is drained
            for (BlockPos pos : dry)
            {
                BlockState now = level.getBlockState(pos);
                if (!now.getFluidState().isEmpty() && (now.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock || now.is(Blocks.BUBBLE_COLUMN)))
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            for (ItemStack stack : paid)
            {
                ItemStack copy = stack.copy();
                if (player != null)
                {
                    if (!player.getInventory().add(copy) && !copy.isEmpty()) player.drop(copy, false);
                }
                else Block.popResource(level, BlockPos.containing(centre), copy);
            }
            if (player != null)
            {
                player.inventoryMenu.broadcastChanges();
                String key = isDismantle(entry) ? "cancelled_dismantle" : paid.isEmpty() ? "cancelled_plain" : "cancelled";
                message(player, Component.translatable(MSG + key, Component.translatable(MSG + "cancel." + reason)));
            }
        }

        /** the revert state for the upper cell of a two-high step: a door's lower half becomes its upper half */
        private static BlockState upperHalf(BlockState previous)
        {
            if (previous != null && previous.getBlock() instanceof DoorBlock && previous.hasProperty(DoorBlock.HALF))
                return previous.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            return previous;
        }

        private void revert(BlockPos pos, BlockState ours, BlockState previous)
        {
            BlockState now = level.getBlockState(pos);
            if (previous == null)
            {
                if (now.is(ours.getBlock()) || now.isAir()) level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            }
            else if (now.is(ours.getBlock())) level.setBlock(pos, previous, Block.UPDATE_ALL);
        }
    }
}
