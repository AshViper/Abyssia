package com.abyssia.industry.energy;

import com.abyssia.Abyssia;
import com.abyssia.industry.block.EnergyCableBlock;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Energy cable networks. Cables are plain blocks; every connected group of cables (either kind) is one network,
 * cached per level and never saved. A network's endpoints are (pos, face) pairs of neighbouring blocks with an
 * energy capability, re-resolved every tick. Throughput per tick = the slowest cable's rate. Each tick: generators
 * feed machines, then storages; storages feed machines (even split, leftovers redistributed).
 * <p>
 * Networks are (re)built lazily from seeds: changed cables, and cables next to an industrial block entity (so
 * networks reappear after a world load). Building never loads chunks; a chunk load / unload rebuilds the networks
 * that touch it.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CableNetworkManager
{
    /** Cap on the cables of one network (a runaway cable field just splits into several networks). */
    private static final int MAX_CABLES = 4096;

    private static final Map<Level, LevelNetworks> LEVELS = Collections.synchronizedMap(new WeakHashMap<>());

    private CableNetworkManager() {}

    /** A face of a block next to a cable: the block at pos, seen through its side. */
    private record Endpoint(BlockPos pos, Direction side) {}

    private static final class Network
    {
        final Set<BlockPos> cables = new HashSet<>();
        final List<Endpoint> endpoints = new ArrayList<>();
        /** chunks of the cables, endpoints and unloaded edges: a load / unload there rebuilds the network */
        final LongSet chunks = new LongOpenHashSet();
        int rate = Integer.MAX_VALUE;
        boolean dirty;
    }

    private static final class LevelNetworks
    {
        final Map<BlockPos, Network> byCable = new HashMap<>();
        final Set<Network> networks = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<BlockPos> seeds = new LinkedHashSet<>();
        final ConcurrentLinkedQueue<Long> changedChunks = new ConcurrentLinkedQueue<>();
    }

    private static LevelNetworks of(Level level)
    {
        return LEVELS.computeIfAbsent(level, l -> new LevelNetworks());
    }

    // ---------------------------------------------------------------- hooks

    /** A cable was placed / removed or a cable's connection changed: rebuild what touches pos. */
    public static void invalidate(LevelAccessor accessor, BlockPos pos)
    {
        if (!(accessor instanceof ServerLevel level)) return;
        LevelNetworks ln = of(level);
        markAround(ln, pos);
    }

    /** Called by industrial block entities now and then: makes sure neighbouring cables belong to a network. */
    public static void touchAround(Level level, BlockPos pos)
    {
        if (!(level instanceof ServerLevel)) return;
        LevelNetworks ln = null;
        for (Direction dir : Direction.values())
        {
            BlockPos n = pos.relative(dir);
            if (!level.isLoaded(n) || !(level.getBlockState(n).getBlock() instanceof EnergyCableBlock)) continue;
            if (ln == null) ln = of(level);
            if (!ln.byCable.containsKey(n)) ln.seeds.add(n.immutable());
        }
    }

    private static void markAround(LevelNetworks ln, BlockPos pos)
    {
        BlockPos p = pos.immutable();
        ln.seeds.add(p);
        Network own = ln.byCable.get(p);
        if (own != null) own.dirty = true;
        for (Direction dir : Direction.values())
        {
            BlockPos n = p.relative(dir);
            Network net = ln.byCable.get(n);
            if (net != null)
            {
                net.dirty = true;
                ln.seeds.add(n);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event)
    {
        queueChunk(event.getLevel(), event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event)
    {
        queueChunk(event.getLevel(), event.getChunk().getPos());
    }

    private static void queueChunk(LevelAccessor accessor, ChunkPos chunk)
    {
        if (!(accessor instanceof ServerLevel level)) return;
        LevelNetworks ln = LEVELS.get(level);
        if (ln != null) ln.changedChunks.add(chunk.toLong());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof Level level) LEVELS.remove(level);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        LevelNetworks ln = LEVELS.get(level);
        if (ln == null) return;
        applyChunkChanges(ln);
        rebuild(level, ln);
        for (Network net : ln.networks) distribute(level, net);
    }

    // ---------------------------------------------------------------- building

    private static void applyChunkChanges(LevelNetworks ln)
    {
        Long chunk;
        while ((chunk = ln.changedChunks.poll()) != null)
        {
            for (Network net : ln.networks)
                if (net.chunks.contains(chunk.longValue())) net.dirty = true;
        }
    }

    private static void rebuild(ServerLevel level, LevelNetworks ln)
    {
        for (Iterator<Network> it = ln.networks.iterator(); it.hasNext(); )
        {
            Network net = it.next();
            if (!net.dirty) continue;
            it.remove();
            for (BlockPos cable : net.cables)
            {
                if (ln.byCable.get(cable) == net) ln.byCable.remove(cable);
                ln.seeds.add(cable);
            }
        }
        if (ln.seeds.isEmpty()) return;
        List<BlockPos> seeds = new ArrayList<>(ln.seeds);
        ln.seeds.clear();
        for (BlockPos seed : seeds)
        {
            if (ln.byCable.containsKey(seed) || !level.isLoaded(seed)) continue;
            if (!(level.getBlockState(seed).getBlock() instanceof EnergyCableBlock)) continue;
            Network net = build(level, seed);
            ln.networks.add(net);
            Set<Network> absorbed = Collections.newSetFromMap(new IdentityHashMap<>());
            for (BlockPos cable : net.cables)
            {
                Network old = ln.byCable.put(cable, net);
                if (old != null && old != net) absorbed.add(old);
            }
            // a network that grew into another one replaces it; its leftover cables are rebuilt next tick
            for (Network old : absorbed)
            {
                ln.networks.remove(old);
                for (BlockPos cable : old.cables)
                {
                    if (ln.byCable.get(cable) != old) continue;
                    ln.byCable.remove(cable);
                    ln.seeds.add(cable);
                }
            }
        }
    }

    private static Network build(ServerLevel level, BlockPos seed)
    {
        Network net = new Network();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Endpoint> endpoints = new LinkedHashSet<>();
        net.cables.add(seed);
        queue.add(seed);
        while (!queue.isEmpty())
        {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof EnergyCableBlock cable)) continue;
            net.rate = Math.min(net.rate, cable.rate());
            net.chunks.add(ChunkPos.asLong(pos));
            for (Direction dir : Direction.values())
            {
                BlockPos n = pos.relative(dir);
                if (!level.isLoaded(n))
                {
                    net.chunks.add(ChunkPos.asLong(n));
                    continue;
                }
                if (net.cables.contains(n)) continue;
                if (level.getBlockState(n).getBlock() instanceof EnergyCableBlock)
                {
                    if (net.cables.size() < MAX_CABLES)
                    {
                        net.cables.add(n);
                        queue.add(n);
                    }
                }
                else if (EnergyLookup.get(level, n, dir.getOpposite()) != null)
                {
                    endpoints.add(new Endpoint(n, dir.getOpposite()));
                    net.chunks.add(ChunkPos.asLong(n));
                }
            }
        }
        net.endpoints.addAll(endpoints);
        if (net.rate == Integer.MAX_VALUE) net.rate = 0;
        return net;
    }

    // ---------------------------------------------------------------- transfer

    private static void distribute(ServerLevel level, Network net)
    {
        if (net.rate <= 0 || net.endpoints.size() < 2) return;
        List<IEnergyStorage> producers = new ArrayList<>();
        List<IEnergyStorage> buffers = new ArrayList<>();
        List<IEnergyStorage> consumers = new ArrayList<>();
        Set<IEnergyStorage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Endpoint end : net.endpoints)
        {
            IEnergyStorage storage = EnergyLookup.get(level, end.pos(), end.side());
            if (storage == null || !seen.add(storage)) continue;
            boolean out = storage.canExtract();
            boolean in = storage.canReceive();
            if (out && in) buffers.add(storage);
            else if (out) producers.add(storage);
            else if (in) consumers.add(storage);
        }
        int budget = net.rate;
        budget -= move(producers, consumers, budget);
        budget -= move(producers, buffers, budget);
        move(buffers, consumers, budget);
    }

    /** extract(simulate) from the sources -> even split into the sinks -> extract what was accepted. */
    private static int move(List<IEnergyStorage> sources, List<IEnergyStorage> sinks, int budget)
    {
        if (budget <= 0 || sources.isEmpty() || sinks.isEmpty()) return 0;
        int[] offers = new int[sources.size()];
        int available = 0;
        for (int i = 0; i < sources.size() && available < budget; i++)
        {
            offers[i] = Math.max(0, sources.get(i).extractEnergy(budget - available, true));
            available += offers[i];
        }
        if (available <= 0) return 0;

        int remaining = available;
        List<IEnergyStorage> open = new ArrayList<>(sinks);
        while (remaining > 0 && !open.isEmpty())
        {
            int share = Math.max(1, remaining / open.size());
            for (Iterator<IEnergyStorage> it = open.iterator(); it.hasNext() && remaining > 0; )
            {
                IEnergyStorage sink = it.next();
                int give = Math.min(share, remaining);
                int accepted = Math.max(0, sink.receiveEnergy(give, false));
                remaining -= accepted;
                if (accepted < give) it.remove();
            }
        }

        int toTake = available - remaining;
        int taken = 0;
        for (int i = 0; i < sources.size() && taken < toTake; i++)
        {
            int want = Math.min(offers[i], toTake - taken);
            if (want > 0) taken += sources.get(i).extractEnergy(want, false);
        }
        return toTake;
    }
}
