package com.abyssia.habitat.dismantle;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.HabitatLayout;
import com.abyssia.habitat.HabitatMode;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.HabitatSupportBlock;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildChecks;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildRegistry;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.habitat.build.BuiltUnits;
import com.abyssia.habitat.build.ModuleEntry;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.habitat.power.HabitatPower;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * BT01b dismantling (spec inbox/specs/BT01-construction-tool.md "解体"). Server only. The tool ({@link DismantleEntry})
 * runs through HabitatBuilder's timed job: {@link #find} picks the unit under the crosshair, {@link #check} refuses
 * occupied modules, {@link #layout} re-shells the neighbours' opened faces first and then turns the unit's blocks and
 * legs back into water, and {@link #finish} unregisters the module, removes the BuiltUnits record(s) and refunds
 * floor(paid x 0.8). A cancelled job reverts its blocks and refunds nothing. {@link #dismantleNow} does it all at once
 * without a UI (test harness).
 */
public final class Dismantler
{
    /** how far the tool reaches */
    public static final double REACH = HabitatPlan.MAX_DISTANCE;
    /** refund = floor(paid x NUM / DEN) */
    private static final int REFUND_NUM = 4, REFUND_DEN = 5;
    /** pool channels / islands (H10 / H12) reach this far into a neighbouring moon pool */
    private static final int POOL_REACH = 3;
    private static final String MSG = "message." + Abyssia.MODID + ".habitat.";

    public static final BuildCheck OCCUPIED = BuildCheck.fail("dismantle_occupied");
    public static final BuildCheck UNKNOWN = BuildCheck.fail("dismantle_unknown");

    private Dismantler() {}

    // ---------------------------------------------------------------- targeting

    /**
     * The unit under the player's crosshair: the latest dismantlable BuiltUnits record containing the aimed block
     * (fixtures before their room), else a module registered before BT01 (no record). {@code withPlan} = also resolve
     * the module layout (false for the cheap preview lookup).
     */
    @Nullable
    public static DismantleTarget find(ServerLevel level, Player player, float partialTick, boolean withPlan)
    {
        HitResult hit = player.pick(REACH, partialTick, false);
        if (!(hit instanceof BlockHitResult bhit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = bhit.getBlockPos();
        BuiltUnits units = BuiltUnits.get(level);
        for (BuiltUnits.Unit unit : units.unitsAt(pos))
            if (dismantlable(level, unit)) return target(level, unit, withPlan);
        HabitatBases bases = HabitatBases.get(level);
        int module = bases.moduleAt(pos);
        if (module >= 0 && units.byModule(module) == null) return legacyTarget(level, module, withPlan);
        return null;
    }

    /** whether the tool may pick this record */
    public static boolean dismantlable(ServerLevel level, BuiltUnits.Unit unit)
    {
        if (unit.moduleId() >= 0) return HabitatBases.get(level).isLive(unit.moduleId());
        BuildEntry entry = BuildRegistry.get(unit.entryId());
        return entry == null || entry.canDismantle() || entry.mode() == BuildEntry.Mode.PLACE;
    }

    /** Target for a recorded unit. */
    public static DismantleTarget target(ServerLevel level, BuiltUnits.Unit unit, boolean withPlan)
    {
        HabitatPlan plan = null;
        if (withPlan && unit.moduleId() >= 0)
        {
            plan = recordedPlan(unit);
            if (plan == null) plan = inferPlan(level, unit.box());
        }
        return new DismantleTarget(unit.unitId(), unit.moduleId(), unit.entryId(), plan, unit.box(), unit.origin(),
                HabitatPlan.facing(unit.rot()));
    }

    /** Target for a HabitatBases module without a BuiltUnits record (built before BT01). */
    @Nullable
    public static DismantleTarget legacyTarget(ServerLevel level, int moduleId, boolean withPlan)
    {
        HabitatBases.Module m = HabitatBases.get(level).module(moduleId);
        if (m == null || m.removed()) return null;
        HabitatPlan plan = withPlan ? inferPlan(level, m.box()) : null;
        BlockPos origin = plan != null ? plan.origin() : new BlockPos(m.box().minX(), m.box().minY(), m.box().minZ());
        Direction forward = plan != null ? plan.forward() : Direction.SOUTH;
        return new DismantleTarget(-1, moduleId, plan != null ? plan.mode().id : "", plan, m.box(), origin, forward);
    }

    /** The layout of a module recorded by a ModuleEntry build, or null. */
    @Nullable
    private static HabitatPlan recordedPlan(BuiltUnits.Unit unit)
    {
        ModuleEntry module = moduleEntry(BuildRegistry.get(unit.entryId()));
        return module == null ? null : new HabitatPlan(module.mode, unit.origin(), HabitatPlan.facing(unit.rot()), false);
    }

    /** The H01 module entry behind a build entry (ModuleEntry itself, or the one an open entrance wraps), or null. */
    @Nullable
    public static ModuleEntry moduleEntry(@Nullable BuildEntry entry)
    {
        if (entry instanceof ModuleEntry module) return module;
        if (entry instanceof com.abyssia.habitat.custom.OpenEntranceEntry open) return open.moduleEntry();
        return null;
    }

    /** Layout of a live module: its BuiltUnits record, else inferred from the box. */
    @Nullable
    public static HabitatPlan modulePlan(ServerLevel level, int moduleId)
    {
        HabitatBases.Module m = HabitatBases.get(level).module(moduleId);
        if (m == null) return null;
        BuiltUnits.Unit unit = BuiltUnits.get(level).byModule(moduleId);
        HabitatPlan plan = unit == null ? null : recordedPlan(unit);
        return plan != null ? plan : inferPlan(level, m.box());
    }

    /**
     * Pre-BT01 module: every mode / rotation whose outer box matches, scored by how many of its shell blocks are in
     * place (a moon pool's open hole beats a room's floor there). Null when no mode fits the size.
     */
    @Nullable
    public static HabitatPlan inferPlan(Level level, BoundingBox box)
    {
        HabitatPlan best = null;
        int bestScore = -1;
        for (HabitatMode mode : HabitatMode.values())
        {
            if (mode.height != box.getYSpan()) continue;
            for (int rot = 0; rot < 4; rot++)
            {
                Direction forward = HabitatPlan.facing(rot);
                HabitatPlan probe = new HabitatPlan(mode, BlockPos.ZERO, forward, false);
                net.minecraft.world.phys.AABB b = probe.box();
                if ((int) Math.round(b.getXsize()) != box.getXSpan() || (int) Math.round(b.getZsize()) != box.getZSpan()) continue;
                BlockPos origin = new BlockPos(box.minX() - (int) Math.floor(b.minX), box.minY() - (int) Math.floor(b.minY),
                        box.minZ() - (int) Math.floor(b.minZ));
                HabitatPlan plan = new HabitatPlan(mode, origin, forward, false);
                int score = 0;
                for (Map.Entry<BlockPos, BlockState> e : HabitatBuilder.shellStates(plan).entrySet())
                    if (level.getBlockState(e.getKey()).is(e.getValue().getBlock())) score++;
                if (score > bestScore)
                {
                    bestScore = score;
                    best = plan;
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- rules

    /** Placement rules; {@code player} null = skip the permission test (harness). */
    public static BuildCheck check(ServerLevel level, @Nullable Player player, DismantleTarget t)
    {
        if (player != null)
        {
            ItemStack stack = player.getMainHandItem();
            for (BlockPos corner : corners(t.bounds()))
                if (!level.mayInteract(player, corner) || !player.mayUseItemAt(corner, Direction.UP, stack)) return BuildCheck.PERMISSION;
        }
        if (!t.isModule())
        {
            if (t.unitId() < 0 || BuiltUnits.get(level).get(t.unitId()) == null) return BuildCheck.NO_TARGET;
            return BuildCheck.OK;
        }
        if (!HabitatBases.get(level).isLive(t.moduleId())) return BuildCheck.NO_TARGET;
        if (t.plan() == null) return UNKNOWN;
        BoundingBox b = t.bounds();
        // fixtures (PLACE units) inside the module must go first; a record whose blocks are all gone (broken by hand /
        // explosion) is stale: dropped without refund
        BuiltUnits records = BuiltUnits.get(level);
        List<Integer> stale = new ArrayList<>();
        for (BuiltUnits.Unit unit : records.units())
            if (unit.unitId() != t.unitId() && blocksModule(unit) && intersectsInterior(unit.box(), b))
            {
                if (!hasFixtureBlocks(level, unit.box())) stale.add(unit.unitId());
                else return OCCUPIED;
            }
        for (int id : stale) records.remove(id);
        // any other block left in the room (pre-BT01 fixtures, player blocks)
        List<BoundingBox> attached = attachedBoxes(level, t);
        Set<BlockPos> shell = HabitatBuilder.shellStates(t.plan()).keySet();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = b.minX() + 1; x < b.maxX(); x++)
            for (int y = b.minY() + 1; y < b.maxY(); y++)
                for (int z = b.minZ() + 1; z < b.maxZ(); z++)
                {
                    pos.set(x, y, z);
                    if (shell.contains(pos) || fluidOrAir(level.getBlockState(pos))) continue;
                    boolean inAttached = false;
                    for (BoundingBox a : attached)
                        if (a.isInside(pos)) inAttached = true;
                    if (!inAttached) return OCCUPIED;
                }
        return BuildCheck.OK;
    }

    /** the box still holds a block {@link #dismantleFixture} would remove (a fixture block) */
    private static boolean hasFixtureBlocks(ServerLevel level, BoundingBox b)
    {
        for (BlockPos pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
            if (fixtureBlock(level.getBlockState(pos))) return true;
        return false;
    }

    private static boolean fixtureBlock(BlockState state)
    {
        return !(fluidOrAir(state) || HabitatPower.isShell(state) || state.getBlock() instanceof HabitatSupportBlock
                || state.is(ModHabitat.SCAN_CONSOLE.get()));
    }

    /** a unit that keeps a module from being dismantled when it stands inside it */
    private static boolean blocksModule(BuiltUnits.Unit unit)
    {
        if (unit.moduleId() >= 0) return false;
        BuildEntry entry = BuildRegistry.get(unit.entryId());
        return entry == null || entry.mode() == BuildEntry.Mode.PLACE;
    }

    /** TARGET units (glass wall, radar upgrade...) touching the module: removed with it */
    private static List<BuiltUnits.Unit> attachedUnits(ServerLevel level, DismantleTarget t)
    {
        List<BuiltUnits.Unit> out = new ArrayList<>();
        for (BuiltUnits.Unit unit : BuiltUnits.get(level).units())
        {
            if (unit.unitId() == t.unitId() || unit.moduleId() >= 0 || !unit.box().intersects(t.bounds())) continue;
            BuildEntry entry = BuildRegistry.get(unit.entryId());
            if (entry != null && entry.mode() == BuildEntry.Mode.TARGET) out.add(unit);
        }
        return out;
    }

    private static List<BoundingBox> attachedBoxes(ServerLevel level, DismantleTarget t)
    {
        List<BoundingBox> out = new ArrayList<>();
        for (BuiltUnits.Unit unit : attachedUnits(level, t)) out.add(unit.box());
        return out;
    }

    private static boolean intersectsInterior(BoundingBox u, BoundingBox b)
    {
        return u.maxX() > b.minX() && u.minX() < b.maxX() && u.maxY() > b.minY() && u.minY() < b.maxY()
                && u.maxZ() > b.minZ() && u.minZ() < b.maxZ();
    }

    private static boolean fluidOrAir(BlockState state)
    {
        return state.isAir() || state.getBlock() instanceof LiquidBlock || state.is(Blocks.BUBBLE_COLUMN);
    }

    private static List<BlockPos> corners(BoundingBox b)
    {
        List<BlockPos> out = new ArrayList<>(8);
        for (int x : new int[]{b.minX(), b.maxX()})
            for (int y : new int[]{b.minY(), b.maxY()})
                for (int z : new int[]{b.minZ(), b.maxZ()}) out.add(new BlockPos(x, y, z));
        return out;
    }

    // ---------------------------------------------------------------- block work

    /**
     * Steps of the timed job: (1) neighbour cells our build had opened (air / water within 1 block of our box, or pool
     * channel / island floor within 3) get their shell back, bottom-up; (2) our loose blocks (doors, console, panels)
     * then the shell top-down become water; (3) our support legs become water. Air / fluid cells of the box become
     * water sources on the last tick. Fixture units: no steps (their removal runs in {@link #finish}).
     */
    public static BuildLayout layout(ServerLevel level, DismantleTarget t)
    {
        if (!t.isModule() || t.plan() == null) return BuildLayout.of(List.of());
        List<BuildStep> steps = new ArrayList<>(reshellSteps(level, t));
        BlockState water = Blocks.WATER.defaultBlockState();
        List<BuildStep> loose = new ArrayList<>(), shell = new ArrayList<>();
        List<BlockPos> fill = new ArrayList<>();
        BoundingBox b = t.bounds();
        for (int y = b.maxY(); y >= b.minY(); y--)
            for (int x = b.minX(); x <= b.maxX(); x++)
                for (int z = b.minZ(); z <= b.maxZ(); z++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (fluidOrAir(state))
                    {
                        fill.add(pos);
                        continue;
                    }
                    if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF))
                    {
                        // one step per door (lower + upper together, no half-door left behind)
                        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER && level.getBlockState(pos.below()).is(state.getBlock())
                                && b.isInside(pos.below())) continue;
                        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && level.getBlockState(pos.above()).is(state.getBlock()))
                            loose.add(new BuildStep(pos, water, water, state));
                        else loose.add(new BuildStep(pos, water, null, state));
                        continue;
                    }
                    (HabitatBuilder.shell(state) ? shell : loose).add(new BuildStep(pos, water, null, state));
                }
        steps.addAll(loose);
        steps.addAll(shell);
        steps.addAll(legSteps(level, t.plan()));
        return new BuildLayout(steps, List.of(), fill);
    }

    /** Neighbour shell cells to close again, bottom-up. */
    private static List<BuildStep> reshellSteps(ServerLevel level, DismantleTarget t)
    {
        List<BuildStep> out = new ArrayList<>();
        BoundingBox ours = t.bounds();
        for (HabitatBases.Module n : neighbours(level, t))
        {
            HabitatPlan plan = modulePlan(level, n.id());
            if (plan == null) continue;
            boolean pool = plan.mode() == HabitatMode.MOON_POOL;
            for (Map.Entry<BlockPos, BlockState> e : HabitatBuilder.shellStates(plan).entrySet())
            {
                BlockPos pos = e.getKey();
                BlockState state = e.getValue();
                if (state.getBlock() instanceof DoorBlock || !n.box().isInside(pos)) continue;
                int d = distance(pos, ours);
                boolean near = d == 1 || (pool && d <= POOL_REACH && pos.getY() == plan.origin().getY());
                if (!near) continue;
                BlockState now = level.getBlockState(pos);
                if (now.isAir() || now.is(Blocks.WATER)) out.add(new BuildStep(pos.immutable(), state, null, now));
            }
        }
        out.sort(Comparator.comparingInt(s -> s.pos().getY()));
        return out;
    }

    /** Our own support columns (H09 leg positions, habitat_support downwards), top-down. */
    private static List<BuildStep> legSteps(ServerLevel level, HabitatPlan plan)
    {
        List<BuildStep> out = new ArrayList<>();
        HabitatMode mode = plan.mode();
        if (!HabitatBuilder.hasLegs(mode)) return out;
        int x = HabitatLayout.halfWidth(mode) - 1;
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int lx : new int[]{-x, x})
            for (int lz : new int[]{1, mode.depth - 2})
            {
                BlockPos pos = plan.at(lx, -1, lz);
                for (int i = 0; i < HabitatBuilder.MAX_LEG; i++, pos = pos.below())
                {
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof HabitatSupportBlock)) break;
                    out.add(new BuildStep(pos, water, null, state));
                }
            }
        return out;
    }

    /** Live modules whose box touches ours (faces, edges or corners). */
    private static List<HabitatBases.Module> neighbours(ServerLevel level, DismantleTarget t)
    {
        List<HabitatBases.Module> out = new ArrayList<>();
        BoundingBox b = t.bounds();
        for (HabitatBases.Module m : HabitatBases.get(level).modules())
            if (m.id() != t.moduleId() && touches(m.box(), b)) out.add(m);
        return out;
    }

    private static boolean touches(BoundingBox a, BoundingBox b)
    {
        return a.minX() <= b.maxX() + 1 && b.minX() <= a.maxX() + 1 && a.minY() <= b.maxY() + 1 && b.minY() <= a.maxY() + 1
                && a.minZ() <= b.maxZ() + 1 && b.minZ() <= a.maxZ() + 1;
    }

    /** Chebyshev distance from pos to the box (0 inside) */
    private static int distance(BlockPos p, BoundingBox b)
    {
        int dx = Math.max(0, Math.max(b.minX() - p.getX(), p.getX() - b.maxX()));
        int dy = Math.max(0, Math.max(b.minY() - p.getY(), p.getY() - b.maxY()));
        int dz = Math.max(0, Math.max(b.minZ() - p.getZ(), p.getZ() - b.maxZ()));
        return Math.max(dx, Math.max(dy, dz));
    }

    // ---------------------------------------------------------------- completion

    /**
     * After the last tick. Modules: leftover air in the box to water, HabitatPower.remove, records removed (the unit and
     * the TARGET units attached to it), neighbours' outer-corner legs rebuilt, refund. Fixtures: the entry's own
     * {@link BuildEntry#dismantle} when it has one, else {@link #dismantleFixture} + refund.
     */
    public static BuildEntry.Completion finish(ServerLevel level, @Nullable ServerPlayer player, DismantleTarget t)
    {
        BuiltUnits units = BuiltUnits.get(level);
        BlockPos at = BlockPos.containing(t.box().getCenter());
        if (!t.isModule())
        {
            BuiltUnits.Unit unit = units.get(t.unitId());
            if (unit == null) return new BuildEntry.Completion(-1, BuildCheck.NO_TARGET.message());
            BuildEntry entry = BuildRegistry.get(unit.entryId());
            int refunded = 0;
            if (entry != null && entry.canDismantle())
            {
                if (player == null || !entry.dismantle(level, player, unit)) return new BuildEntry.Completion(-1, OCCUPIED.message());
                units.remove(unit.unitId());
            }
            else
            {
                // a fixture already broken (by hand / explosion) is only forgotten: no refund
                boolean intact = hasFixtureBlocks(level, unit.box());
                dismantleFixture(level, unit);
                units.remove(unit.unitId());
                if (intact) refunded = refund(level, player, unit.paid(), at);
            }
            sound(level, at);
            return new BuildEntry.Completion(-1, Component.translatable(MSG + "dismantled", name(unit.entryId()), refunded));
        }

        if (!HabitatBases.get(level).isLive(t.moduleId())) return new BuildEntry.Completion(-1, BuildCheck.NO_TARGET.message());
        BoundingBox b = t.bounds();
        BlockState water = Blocks.WATER.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
            if (level.getBlockState(pos).isAir()) level.setBlock(pos, water, Block.UPDATE_ALL);

        List<HabitatBases.Module> around = neighbours(level, t);
        List<BuiltUnits.Unit> attached = attachedUnits(level, t);
        HabitatPower.remove(level, t.moduleId());

        List<ItemStack> paid = new ArrayList<>();
        BuiltUnits.Unit own = t.unitId() >= 0 ? units.remove(t.unitId()) : null;
        if (own != null) paid.addAll(own.paid());
        else if (t.plan() != null)
        {
            BuildEntry entry = BuildRegistry.get(t.plan().mode().id);
            if (entry != null) paid.addAll(entry.cost());
        }
        for (BuiltUnits.Unit unit : attached)
        {
            units.remove(unit.unitId());
            paid.addAll(unit.paid());
        }

        // H12 in reverse: neighbours that lost a 13-wide side neighbour stand on their outer-corner legs again
        for (HabitatBases.Module n : around)
        {
            HabitatPlan plan = modulePlan(level, n.id());
            if (plan == null) continue;
            for (BlockPos pos : HabitatBuilder.legs(level, plan))
                if (HabitatPlan.replaceable(level.getBlockState(pos)))
                    level.setBlock(pos, HabitatBuilder.support(level.getFluidState(pos).is(FluidTags.WATER)), Block.UPDATE_ALL);
        }

        int refunded = refund(level, player, paid, at);
        sound(level, at);
        Component name = own != null ? name(own.entryId()) : t.plan() != null ? t.plan().mode().displayName() : Component.literal("?");
        return new BuildEntry.Completion(-1, Component.translatable(MSG + "dismantled", name, refunded));
    }

    private static Component name(String entryId)
    {
        BuildEntry entry = BuildRegistry.get(entryId);
        return entry != null ? entry.displayName() : Component.literal(entryId);
    }

    private static void sound(ServerLevel level, BlockPos at)
    {
        level.playSound(null, at, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.8f, 0.7f);
    }

    /**
     * Generic fixture removal (entries may call it from their own {@link BuildEntry#dismantle}): drops container /
     * item-handler contents, then every non-habitat block of the recorded box becomes air inside a module, water
     * outside (below sea level). Blocks are removed without neighbour shape updates first, so multi-block parts do not
     * break each other into drops.
     */
    public static void dismantleFixture(ServerLevel level, BuiltUnits.Unit unit)
    {
        BoundingBox b = unit.box();
        Set<Object> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
        {
            BlockPos pos = p.immutable();
            BlockState state = level.getBlockState(pos);
            if (!fixtureBlock(state)) continue;
            cells.add(pos);
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) dropContents(level, pos, be, dropped);
        }
        List<BlockState> placed = new ArrayList<>(cells.size());
        for (BlockPos pos : cells)
        {
            boolean inside = BuildChecks.moduleInterior(level, pos) != null;
            BlockState repl = !inside && pos.getY() < level.getSeaLevel() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(pos, repl, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            placed.add(repl);
        }
        for (int i = 0; i < cells.size(); i++)
        {
            BlockPos pos = cells.get(i);
            placed.get(i).updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, placed.get(i).getBlock());
        }
    }

    private static void dropContents(ServerLevel level, BlockPos pos, BlockEntity be, Set<Object> dropped)
    {
        if (be instanceof Container container)
        {
            if (dropped.add(container))
            {
                Containers.dropContents(level, pos, container);
                container.clearContent();
            }
            return;
        }
        Optional<IItemHandler> handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (handler.isEmpty() || !dropped.add(handler.get())) return;
        IItemHandler h = handler.get();
        for (int i = 0; i < h.getSlots(); i++)
        {
            ItemStack stack = h.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
            if (h instanceof IItemHandlerModifiable m) m.setStackInSlot(i, ItemStack.EMPTY);
            else h.extractItem(i, stack.getCount(), false);
        }
    }

    /**
     * Gives floor(count x 0.8) of each paid stack to the player (dropped at the player when the inventory is full;
     * at {@code at} without a player). Returns the number of items given back.
     */
    public static int refund(ServerLevel level, @Nullable Player player, List<ItemStack> paid, BlockPos at)
    {
        int total = 0;
        for (ItemStack stack : paid)
        {
            int left = stack.getCount() * REFUND_NUM / REFUND_DEN;
            total += left;
            while (left > 0)
            {
                int n = Math.min(left, stack.getMaxStackSize());
                left -= n;
                ItemStack give = stack.copyWithCount(n);
                if (player != null)
                {
                    if (!player.getInventory().add(give) && !give.isEmpty()) player.drop(give, false);
                }
                else Block.popResource(level, at, give);
            }
        }
        if (player != null) player.inventoryMenu.broadcastChanges();
        return total;
    }

    // ---------------------------------------------------------------- harness

    /**
     * Test harness: dismantles a BuiltUnits record at once (no UI, no timer): same rules, block work, records and
     * refund as the tool. {@code player} null = no permission test, refund dropped at the unit. Returns null on success,
     * else the problem key (message.abyssia.habitat.&lt;problem&gt;).
     */
    @Nullable
    public static String dismantleNow(ServerLevel level, @Nullable ServerPlayer player, int unitId)
    {
        BuiltUnits.Unit unit = BuiltUnits.get(level).get(unitId);
        if (unit == null || !dismantlable(level, unit)) return BuildCheck.NO_TARGET.problem();
        return run(level, player, target(level, unit, true));
    }

    /** Test harness: as {@link #dismantleNow} for a HabitatBases module (recorded or built before BT01). */
    @Nullable
    public static String dismantleModuleNow(ServerLevel level, @Nullable ServerPlayer player, int moduleId)
    {
        BuiltUnits.Unit unit = BuiltUnits.get(level).byModule(moduleId);
        if (unit != null) return dismantleNow(level, player, unit.unitId());
        DismantleTarget t = legacyTarget(level, moduleId, true);
        return t == null ? BuildCheck.NO_TARGET.problem() : run(level, player, t);
    }

    @Nullable
    private static String run(ServerLevel level, @Nullable ServerPlayer player, DismantleTarget t)
    {
        BuildCheck check = check(level, player, t);
        if (!check.ok()) return check.problem();
        BuildLayout layout = layout(level, t);
        for (BuildStep step : layout.steps())
        {
            level.setBlock(step.pos(), step.state(), Block.UPDATE_ALL);
            if (step.upper() != null) level.setBlock(step.pos().above(), step.upper(), Block.UPDATE_ALL);
        }
        for (BlockPos pos : layout.water())
            if (!level.getFluidState(pos).isSource()) level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        finish(level, player, t);
        return null;
    }
}
