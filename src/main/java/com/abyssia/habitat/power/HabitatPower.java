package com.abyssia.habitat.power;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.HabitatLightBlock;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.industry.energy.EnergyLookup;
import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * H08 habitat wireless power (spec inbox/specs/H08-habitat-power.md): every base (modules joined by an opened
 * connector or merged wall) has a shared FE buffer. Cables outside every module feed it through any shell block they
 * touch ({@link #externalReceiver}); each tick the buffer pulls from generators inside the base, feeds the FE block
 * entities inside round-robin, charges batteries with the surplus and draws on them when it runs dry.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class HabitatPower
{
    public static final int CAPACITY = 1_000_000;
    public static final int INPUT_CAP = 100_000;
    public static final int PER_DEVICE = 10_000;
    public static final int RESCAN_TICKS = 40;
    /** ECO03: FE per tick every habitable module draws for lighting and life support (a foundation draws none) */
    public static final int LIFE_SUPPORT_FE = 20;
    /** how often the lamps are re-synced to the power state (new modules, reloaded chunks) */
    public static final int LIGHT_SYNC_TICKS = 200;

    /** per-level runtime state of the bases (not saved) */
    private static final Map<Level, Map<Integer, Runtime>> RUNTIME = new WeakHashMap<>();

    private HabitatPower() {}

    private static final class Runtime
    {
        final int base;
        final ServerLevel level;
        int inputThisTick, lastInput, lastOutput;
        long nextScan;
        int cursor;
        /** ECO03: the base paid its life support this tick (lamps on, oxygen refilling); null before the first tick */
        Boolean powered;
        Boolean lightsSet;
        long nextLightSync;
        int lastDrain;
        List<BlockPos> devices = List.of();
        final IEnergyStorage receiver;

        Runtime(ServerLevel level, int base)
        {
            this.level = level;
            this.base = base;
            this.receiver = new Receiver(this);
        }

        HabitatBases data()
        {
            return HabitatBases.get(level);
        }
    }

    /** Cable input of a base: receive only, limited by the free space and the per-tick input cap. */
    private record Receiver(Runtime rt) implements IEnergyStorage
    {
        @Override
        public int receiveEnergy(int max, boolean simulate)
        {
            HabitatBases data = rt.data();
            int stored = data.energy(rt.base);
            int accepted = Math.max(0, Math.min(max, Math.min(CAPACITY - stored, INPUT_CAP - rt.inputThisTick)));
            if (!simulate && accepted > 0)
            {
                data.setEnergy(rt.base, stored + accepted);
                rt.inputThisTick += accepted;
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int max, boolean simulate) { return 0; }

        @Override
        public int getEnergyStored() { return rt.data().energy(rt.base); }

        @Override
        public int getMaxEnergyStored() { return CAPACITY; }

        @Override
        public boolean canExtract() { return false; }

        @Override
        public boolean canReceive() { return true; }
    }

    private static Runtime runtime(ServerLevel level, int base)
    {
        return RUNTIME.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(base, b -> new Runtime(level, b));
    }

    public static boolean isShell(BlockState state)
    {
        return HabitatBuilder.shell(state) || state.is(ModHabitat.DOOR.get());
    }

    // ---------------------------------------------------------------- registration

    /**
     * Called by the builder when a module is complete: registers its box and joins the bases it opened into. Returns
     * the new module id (-1 on the client).
     */
    public static int register(Level level, HabitatPlan plan, List<BlockPos> neighbours)
    {
        if (!(level instanceof ServerLevel server)) return -1;
        HabitatBases data = HabitatBases.get(server);
        List<Integer> joined = new ArrayList<>();
        for (BlockPos pos : neighbours)
        {
            int m = data.moduleAt(pos);
            if (m >= 0) joined.add(m);
        }
        AABB b = plan.box();
        int id = data.add(new BoundingBox((int) b.minX, (int) b.minY, (int) b.minZ, (int) b.maxX - 1, (int) b.maxY - 1, (int) b.maxZ - 1));
        for (int m : joined) data.union(id, m, CAPACITY);
        data.joinFoundations(CAPACITY);
        // roots changed: drop the runtime caches (device lists rebuild next tick) and re-resolve cable endpoints
        Map<Integer, Runtime> rts = RUNTIME.get(server);
        if (rts != null) rts.clear();
        CableNetworkManager.markAllDirty(server);
        return id;
    }

    /**
     * BT01b: removes a dismantled module (HabitatBases.remove: edges, union-find rebuilt, FE split by volume), drops the
     * runtime caches and re-resolves cable endpoints. False when the module was unknown or already removed.
     */
    public static boolean remove(ServerLevel level, int moduleId)
    {
        if (!HabitatBases.get(level).remove(moduleId)) return false;
        Map<Integer, Runtime> rts = RUNTIME.get(level);
        if (rts != null) rts.clear();
        CableNetworkManager.markAllDirty(level);
        return true;
    }

    // ---------------------------------------------------------------- cable input

    /**
     * Storage a cable at {@code pos.relative(side)} sees on the habitat shell block at pos: the base's receiver, when
     * pos belongs to a registered base and the cable is outside every module box; else null.
     */
    @Nullable
    public static IEnergyStorage externalReceiver(Level level, BlockPos pos, @Nullable Direction side)
    {
        if (!(level instanceof ServerLevel server) || side == null || !level.isLoaded(pos) || !isShell(level.getBlockState(pos))) return null;
        HabitatBases data = HabitatBases.get(server);
        int base = data.baseAt(pos);
        if (base < 0 || data.moduleAt(pos.relative(side)) >= 0) return null;
        return runtime(server, base).receiver;
    }

    // ---------------------------------------------------------------- distribution

    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        HabitatBases data = HabitatBases.get(level);
        if (data.modules().isEmpty()) return;
        for (int base : data.bases()) tickBase(level, data, runtime(level, base));
    }

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof Level level) RUNTIME.remove(level);
    }

    private static void tickBase(ServerLevel level, HabitatBases data, Runtime rt)
    {
        long now = level.getGameTime();
        if (now >= rt.nextScan)
        {
            rt.devices = scanDevices(level, data.boxes(rt.base));
            rt.nextScan = now + RESCAN_TICKS;
        }
        int stored = data.energy(rt.base);
        List<IEnergyStorage> generators = new ArrayList<>(), consumers = new ArrayList<>(), batteries = new ArrayList<>();
        Set<IEnergyStorage> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (BlockPos pos : rt.devices)
        {
            IEnergyStorage s = EnergyLookup.get(level, pos, null);
            if (s == null || !seen.add(s)) continue;
            boolean in = s.canReceive(), out = s.canExtract();
            if (in && out) batteries.add(s);
            else if (out) generators.add(s);
            else if (in) consumers.add(s);
        }

        // generators -> buffer (counts as input)
        for (IEnergyStorage g : generators)
        {
            int room = Math.min(CAPACITY - stored, INPUT_CAP - rt.inputThisTick);
            if (room <= 0) break;
            int got = g.extractEnergy(Math.min(PER_DEVICE, room), false);
            stored += got;
            rt.inputThisTick += got;
        }

        // ECO03: life support first - every habitable module draws LIFE_SUPPORT_FE; no power = lamps off, oxygen down
        List<HabitatBases.Module> mods = new ArrayList<>();
        for (HabitatBases.Module m : data.modules())
            if (data.root(m.id()) == rt.base && m.box().getYSpan() > 1) mods.add(m);
        int drain = mods.size() * LIFE_SUPPORT_FE;
        if (stored < drain)
            for (IEnergyStorage b : batteries)
            {
                stored += b.extractEnergy(Math.min(PER_DEVICE, CAPACITY - stored), false);
                if (stored >= drain) break;
            }
        boolean powered = stored >= drain;
        if (powered) stored -= drain;
        rt.powered = powered;
        rt.lastDrain = powered ? drain : 0;
        HabitatAir air = HabitatAir.get(level);
        for (HabitatBases.Module m : mods) air.step(m.id(), powered);
        if (rt.lightsSet == null || rt.lightsSet != powered || now >= rt.nextLightSync)
        {
            syncLights(level, data.boxes(rt.base), powered);
            rt.lightsSet = powered;
            rt.nextLightSync = now + LIGHT_SYNC_TICKS;
        }

        // buffer -> consumers, round-robin start
        int output = 0;
        boolean short_ = false;
        int n = consumers.size();
        for (int k = 0; k < n; k++)
        {
            IEnergyStorage c = consumers.get((rt.cursor + k) % n);
            int want = c.receiveEnergy(PER_DEVICE, true);
            if (want <= 0) continue;
            if (stored <= 0)
            {
                short_ = true;
                continue;
            }
            int given = c.receiveEnergy(Math.min(want, stored), false);
            stored -= given;
            output += given;
            if (given < want) short_ = true;
        }
        if (n > 0) rt.cursor = (rt.cursor + 1) % n;

        // batteries: charge with the surplus (over half full), discharge into an empty buffer when consumers are short
        if (stored > CAPACITY / 2)
            for (IEnergyStorage b : batteries)
            {
                int given = b.receiveEnergy(Math.min(PER_DEVICE, stored - CAPACITY / 2), false);
                stored -= given;
                output += given;
                if (stored <= CAPACITY / 2) break;
            }
        else if (stored <= 0 && short_)
            for (IEnergyStorage b : batteries)
                stored += b.extractEnergy(Math.min(PER_DEVICE, CAPACITY - stored), false);

        data.setEnergy(rt.base, stored);
        rt.lastInput = rt.inputThisTick;
        rt.lastOutput = output;
        rt.inputThisTick = 0;
    }

    /** Sets the lit state of every habitat lamp inside the boxes (loaded positions only). */
    private static void syncLights(ServerLevel level, List<BoundingBox> boxes, boolean lit)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (BoundingBox box : boxes)
            for (int x = box.minX(); x <= box.maxX(); x++)
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                {
                    pos.set(x, box.minY(), z);
                    if (!level.isLoaded(pos)) continue;
                    for (int y = box.minY(); y <= box.maxY(); y++)
                    {
                        pos.set(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.is(ModHabitat.LIGHT.get()) && state.getValue(HabitatLightBlock.LIT) != lit)
                            level.setBlock(pos, state.setValue(HabitatLightBlock.LIT, lit), Block.UPDATE_ALL);
                    }
                }
    }

    /** Whether the base of this module paid its life support last tick (true until its first tick). */
    public static boolean isPowered(ServerLevel level, int module)
    {
        Boolean powered = runtime(level, HabitatBases.get(level).root(module)).powered;
        return powered == null || powered;
    }

    /** Cells above a foundation (1-high module) that its wireless supply reaches: the 3-high interior of a room plus its ceiling. */
    private static final int FOUNDATION_REACH = 4;

    /**
     * Block entities with an energy capability inside the boxes (loaded chunks only). A foundation's box also covers the
     * FOUNDATION_REACH cells above it, so devices standing on it are supplied.
     */
    private static List<BlockPos> scanDevices(ServerLevel level, List<BoundingBox> modules)
    {
        List<BoundingBox> boxes = new ArrayList<>();
        for (BoundingBox b : modules)
            boxes.add(b.getYSpan() == 1 ? new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY() + FOUNDATION_REACH, b.maxZ()) : b);
        List<BlockPos> out = new ArrayList<>();
        Set<Long> chunks = new HashSet<>();
        for (BoundingBox box : boxes)
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                {
                    if (!chunks.add(ChunkPos.asLong(cx, cz))) continue;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet())
                    {
                        BlockPos pos = e.getKey();
                        if (boxes.stream().noneMatch(b -> b.isInside(pos))) continue;
                        if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, e.getValue().getBlockState(), e.getValue(), null) != null) out.add(pos.immutable());
                    }
                }
        return out;
    }

    // ---------------------------------------------------------------- status

    /** Sneak + right-click a shell block with an empty main hand: base power on the action bar. */
    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event)
    {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        Level level = event.getLevel();
        if (!isShell(level.getBlockState(event.getPos()))) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(level instanceof ServerLevel server)) return;
        int base = baseAt(server, event.getPos());
        String key = "message." + Abyssia.MODID + ".habitat.power";
        if (base < 0)
        {
            player.displayClientMessage(Component.translatable(key + ".none"), true);
            return;
        }
        Runtime rt = runtime(server, base);
        int module = HabitatBases.get(server).moduleAt(event.getPos());
        int oxygen = module < 0 ? HabitatAir.MAX : HabitatAir.get(server).oxygen(module);
        player.displayClientMessage(Component.translatable(key, String.format("%,d", stored(server, base)), String.format("%,d", CAPACITY),
                String.format("%,d", rt.lastInput), String.format("%,d", rt.lastOutput), String.format("%,d", rt.lastDrain),
                String.valueOf(oxygen * 100 / HabitatAir.MAX)), true);
    }

    // ---------------------------------------------------------------- harness / tooling

    /** Base id (root module id) at pos, or -1. */
    public static int baseAt(ServerLevel level, BlockPos pos)
    {
        return HabitatBases.get(level).baseAt(pos);
    }

    public static int stored(ServerLevel level, int base)
    {
        return HabitatBases.get(level).energy(HabitatBases.get(level).root(base));
    }

    public static int lastInput(ServerLevel level, int base)
    {
        return runtime(level, HabitatBases.get(level).root(base)).lastInput;
    }

    public static int lastOutput(ServerLevel level, int base)
    {
        return runtime(level, HabitatBases.get(level).root(base)).lastOutput;
    }

    /** Registered modules (id + box); base of each = {@link #baseOf}. */
    public static List<HabitatBases.Module> modules(ServerLevel level)
    {
        return List.copyOf(HabitatBases.get(level).modules());
    }

    public static int baseOf(ServerLevel level, int module)
    {
        return HabitatBases.get(level).root(module);
    }

    /** Test helper: sets the shared FE of a base. */
    public static void setStored(ServerLevel level, int base, int fe)
    {
        HabitatBases data = HabitatBases.get(level);
        data.setEnergy(data.root(base), Math.max(0, Math.min(CAPACITY, fe)));
    }
}
