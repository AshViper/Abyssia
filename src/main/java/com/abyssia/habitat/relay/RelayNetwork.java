package com.abyssia.habitat.relay;

import com.abyssia.Abyssia;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.habitat.power.HabitatPower;
import com.abyssia.network.AbyssiaNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * WR01: every wireless power relay of one level and their links (undirected pairs), saved with the level.
 * <ul>
 *     <li>Relays are added when the lower block is placed (constructor build / {@code /abyssia relay place}) and removed
 *     when it is removed.</li>
 *     <li>Auto link every 20 ticks (and right after a placement): each relay with a free slot (max 2) links to the
 *     nearest relays within 128 blocks (3D, antenna tip to tip; terrain / water ignored) that also have a free slot.
 *     Existing links are never re-chosen.</li>
 *     <li>A link whose chunk (either end) is not loaded is kept but paused: no transfer, not drawn. No chunk tickets.</li>
 *     <li>Every tick each link moves FE once, from the end with the higher fill ratio to the lower (no routing, so a
 *     loop cannot multiply energy): {@code send = min(512, src, ceil(gap * capS*capD/(capS+capD)))}, arriving
 *     {@code floor(send * eff)} (100/90/80/70 % by distance), the rest is lost.</li>
 *     <li>Clients get the link list ({@link RelaySyncPacket}) on change (min 10 ticks apart), login and dimension change.</li>
 * </ul>
 */
public class RelayNetwork extends SavedData
{
    public static final String NAME = "abyssia_wireless_relays";
    public static final int MAX_LINKS = 2;
    public static final double MAX_DISTANCE = 128.0;
    public static final int MAX_PER_LINK = 512;
    public static final double MIN_RATIO_GAP = 0.005;
    public static final int SEARCH_INTERVAL = 20, WINDOW = 20, MIN_SYNC_INTERVAL = 10;
    /** antenna tip relative to the lower block origin (upper block + (0.5, 12/16, 0.5)) */
    public static final double TIP_X = 0.5, TIP_Y = 1.0 + 12.0 / 16.0, TIP_Z = 0.5;

    /** Undirected link, a < b by BlockPos.asLong. */
    public record Link(BlockPos a, BlockPos b)
    {
        public static Link of(BlockPos p, BlockPos q)
        {
            return p.asLong() <= q.asLong() ? new Link(p.immutable(), q.immutable()) : new Link(q.immutable(), p.immutable());
        }

        public boolean has(BlockPos p)
        {
            return a.equals(p) || b.equals(p);
        }

        public BlockPos other(BlockPos p)
        {
            return a.equals(p) ? b : a;
        }

        public double distance()
        {
            return Math.sqrt(a.distSqr(b));
        }
    }

    /** Transient per-link state: the 20-tick window of moved FE and what clients last got. */
    public static final class Stats
    {
        int acc, dirAcc;
        /** delivered FE/t averaged over the last window; dir +1 = a -> b, -1 = b -> a */
        public int avg, dir;
        public boolean paused;
        byte band;
        byte sentState = -1, sentBand = -1;

        void closeWindow()
        {
            avg = (acc + WINDOW - 1) / WINDOW;
            dir = Integer.signum(dirAcc);
            band = (byte) (avg < 128 ? 0 : avg < 256 ? 1 : avg < 384 ? 2 : 3);
            acc = 0;
            dirAcc = 0;
        }

        /** 0 paused, 1 idle, 2 a -> b, 3 b -> a */
        public byte state()
        {
            if (paused) return 0;
            if (avg <= 0 || dir == 0) return 1;
            return (byte) (dir > 0 ? 2 : 3);
        }

        public byte band()
        {
            return state() >= 2 ? band : 0;
        }
    }

    /** One side of a transfer. */
    private interface End
    {
        int stored();

        int capacity();

        void add(int fe);

        void take(int fe);
    }

    private record BaseEnd(HabitatBases data, int base) implements End
    {
        public int stored() { return data.energy(base); }

        public int capacity() { return HabitatPower.CAPACITY; }

        public void add(int fe) { data.setEnergy(base, Math.min(HabitatPower.CAPACITY, data.energy(base) + fe)); }

        public void take(int fe) { data.setEnergy(base, Math.max(0, data.energy(base) - fe)); }
    }

    private record BufferEnd(WirelessPowerRelayBlockEntity be) implements End
    {
        public int stored() { return be.buffer().getEnergyStored(); }

        public int capacity() { return WirelessPowerRelayBlockEntity.BUFFER; }

        public void add(int fe) { be.buffer().generate(fe); }

        public void take(int fe) { be.buffer().consume(fe); }
    }

    private final Set<BlockPos> relays = new LinkedHashSet<>();
    private final Map<Link, Stats> links = new LinkedHashMap<>();
    private boolean searchNow = true, syncDirty = true;
    private long lastSync = -MIN_SYNC_INTERVAL; // not Long.MIN_VALUE: now - lastSync would overflow and never sync

    public static RelayNetwork get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(RelayNetwork::new, RelayNetwork::load, null), NAME);
    }

    // ---------------------------------------------------------------- relays

    public static void add(ServerLevel level, BlockPos pos)
    {
        RelayNetwork net = get(level);
        if (net.relays.add(pos.immutable())) net.setDirty();
        net.searchNow = true;
    }

    public static void remove(ServerLevel level, BlockPos pos)
    {
        get(level).removeRelay(level, pos);
    }

    private void removeRelay(ServerLevel level, BlockPos pos)
    {
        if (!relays.remove(pos)) return;
        setDirty();
        List<BlockPos> partners = new ArrayList<>();
        links.keySet().removeIf(l ->
        {
            if (!l.has(pos)) return false;
            partners.add(l.other(pos));
            return true;
        });
        if (!partners.isEmpty()) syncDirty = true;
        for (BlockPos p : partners) updateLinked(level, p);
        searchNow = true;
    }

    public Set<BlockPos> relays()
    {
        return java.util.Collections.unmodifiableSet(relays);
    }

    public Map<Link, Stats> links()
    {
        return java.util.Collections.unmodifiableMap(links);
    }

    public List<Link> linksOf(BlockPos pos)
    {
        List<Link> out = new ArrayList<>(MAX_LINKS);
        for (Link l : links.keySet()) if (l.has(pos)) out.add(l);
        return out;
    }

    private int linkCount(BlockPos pos)
    {
        int n = 0;
        for (Link l : links.keySet()) if (l.has(pos)) n++;
        return n;
    }

    public static double efficiency(double distance)
    {
        if (distance <= 32.0) return 1.00;
        if (distance <= 64.0) return 0.90;
        if (distance <= 96.0) return 0.80;
        return 0.70;
    }

    /** Base (root module id) whose module box holds pos, or -1. */
    public static int baseOf(ServerLevel level, BlockPos pos)
    {
        HabitatBases data = HabitatBases.get(level);
        int m = data.moduleAt(pos);
        return m < 0 ? -1 : data.root(m);
    }

    // ---------------------------------------------------------------- tick

    public boolean isEmpty()
    {
        return relays.isEmpty() && links.isEmpty();
    }


    public void tick(ServerLevel level)
    {
        long now = level.getGameTime();
        if (searchNow || now % SEARCH_INTERVAL == 0)
        {
            searchNow = false;
            validate(level);
            search(level);
        }
        boolean window = now % WINDOW == 0;
        HabitatBases data = links.isEmpty() ? null : HabitatBases.get(level);
        for (Map.Entry<Link, Stats> e : links.entrySet())
        {
            Link link = e.getKey();
            Stats stats = e.getValue();
            boolean paused = !level.isLoaded(link.a) || !level.isLoaded(link.b);
            if (!paused)
            {
                End a = end(level, data, link.a), b = end(level, data, link.b);
                if (a == null || b == null) paused = true;
                else
                {
                    int moved = transfer(a, b, efficiency(link.distance()));
                    stats.acc += Math.abs(moved);
                    stats.dirAcc += moved;
                }
            }
            stats.paused = paused;
            if (window) stats.closeWindow();
            if (stats.state() != stats.sentState || stats.band() != stats.sentBand) syncDirty = true;
        }
        if (syncDirty && (now - lastSync >= MIN_SYNC_INTERVAL || now < lastSync)) sync(level, now);
    }

    @Nullable
    private static End end(ServerLevel level, HabitatBases data, BlockPos pos)
    {
        int m = data.moduleAt(pos);
        if (m >= 0) return new BaseEnd(data, data.root(m));
        return level.getBlockEntity(pos) instanceof WirelessPowerRelayBlockEntity be ? new BufferEnd(be) : null;
    }

    /** One transfer on a link; returns the delivered FE, positive = a -> b. */
    private static int transfer(End a, End b, double eff)
    {
        int capA = a.capacity(), capB = b.capacity();
        double rA = a.stored() / (double) capA, rB = b.stored() / (double) capB;
        if (Math.abs(rA - rB) < MIN_RATIO_GAP) return 0;
        boolean aToB = rA > rB;
        End src = aToB ? a : b, dst = aToB ? b : a;
        double gap = Math.abs(rA - rB);
        double ideal = Math.ceil(gap * ((double) capA * capB / (capA + capB)));
        int send = (int) Math.min(MAX_PER_LINK, Math.min(src.stored(), ideal));
        if (send <= 0) return 0;
        int got = (int) Math.floor(send * eff);
        int room = dst.capacity() - dst.stored();
        if (got > room)
        {
            got = room;
            send = (int) Math.ceil(got / eff);
        }
        if (got <= 0) return 0;
        src.take(send);
        dst.add(got);
        return aToB ? got : -got;
    }

    /** Drops relays whose block is gone (loaded chunks only) and links that are too long; refreshes LINKED. */
    private void validate(ServerLevel level)
    {
        for (BlockPos pos : List.copyOf(relays))
            if (level.isLoaded(pos) && !level.getBlockState(pos).is(RelayContent.RELAY.get())) removeRelay(level, pos);
        List<BlockPos> touched = new ArrayList<>();
        links.keySet().removeIf(l ->
        {
            if (l.distance() <= MAX_DISTANCE && relays.contains(l.a) && relays.contains(l.b)) return false;
            touched.add(l.a);
            touched.add(l.b);
            return true;
        });
        if (!touched.isEmpty())
        {
            setDirty();
            syncDirty = true;
        }
        for (BlockPos pos : relays) updateLinked(level, pos);
    }

    /** Free relays (loaded) link to the nearest free relays in range, both sides need a free slot. */
    private void search(ServerLevel level)
    {
        List<BlockPos> free = new ArrayList<>();
        for (BlockPos pos : relays) if (level.isLoaded(pos) && linkCount(pos) < MAX_LINKS) free.add(pos);
        if (free.size() < 2) return;
        free.sort(Comparator.comparingLong(BlockPos::asLong)); // deterministic order (ties, restarts)
        double maxSq = MAX_DISTANCE * MAX_DISTANCE;
        for (BlockPos p : free)
        {
            if (linkCount(p) >= MAX_LINKS) continue;
            List<BlockPos> candidates = new ArrayList<>();
            for (BlockPos q : free)
                if (!q.equals(p) && p.distSqr(q) <= maxSq && !links.containsKey(Link.of(p, q))) candidates.add(q);
            candidates.sort(Comparator.<BlockPos>comparingDouble(p::distSqr).thenComparingLong(BlockPos::asLong));
            for (BlockPos q : candidates)
            {
                if (linkCount(p) >= MAX_LINKS) break;
                if (linkCount(q) >= MAX_LINKS) continue;
                links.put(Link.of(p, q), new Stats());
                setDirty();
                syncDirty = true;
                updateLinked(level, p);
                updateLinked(level, q);
            }
        }
    }

    /** LINKED on both halves = has at least one link (loaded chunks only). */
    private void updateLinked(ServerLevel level, BlockPos pos)
    {
        if (!level.isLoaded(pos)) return;
        boolean linked = linkCount(pos) > 0;
        BlockState lower = level.getBlockState(pos);
        if (lower.is(RelayContent.RELAY.get()) && lower.getValue(WirelessPowerRelayBlock.LINKED) != linked)
            level.setBlock(pos, lower.setValue(WirelessPowerRelayBlock.LINKED, linked), Block.UPDATE_CLIENTS);
        BlockPos up = pos.above();
        BlockState upper = level.getBlockState(up);
        if (upper.is(RelayContent.RELAY_TOP.get()) && upper.getValue(WirelessPowerRelayTopBlock.LINKED) != linked)
            level.setBlock(up, upper.setValue(WirelessPowerRelayTopBlock.LINKED, linked), Block.UPDATE_CLIENTS);
    }

    // ---------------------------------------------------------------- sync

    private RelaySyncPacket packet()
    {
        List<RelaySyncPacket.Entry> list = new ArrayList<>(links.size());
        for (Map.Entry<Link, Stats> e : links.entrySet())
            list.add(new RelaySyncPacket.Entry(e.getKey().a, e.getKey().b, e.getValue().state(), e.getValue().band()));
        return new RelaySyncPacket(list);
    }

    private void sync(ServerLevel level, long now)
    {
        for (Stats s : links.values())
        {
            s.sentState = s.state();
            s.sentBand = s.band();
        }
        syncDirty = false;
        lastSync = now;
        if (level.players().isEmpty()) return;
        RelaySyncPacket msg = packet();
        for (ServerPlayer player : level.players()) AbyssiaNetwork.sendTo(player, msg);
    }

    public static void sendTo(ServerPlayer player)
    {
        AbyssiaNetwork.sendTo(player, get(player.serverLevel()).packet());
    }

    // ---------------------------------------------------------------- status / tooling

    private static final String MSG = "message." + Abyssia.MODID + ".relay.";

    /** Sneak + right-click: links n/2, endpoint, then per link distance / efficiency / recent FE/t / direction. */
    public static Component status(ServerLevel level, BlockPos pos)
    {
        RelayNetwork net = get(level);
        int base = baseOf(level, pos);
        Component endpoint;
        if (base >= 0)
            endpoint = Component.translatable(MSG + "endpoint.base", base, String.format("%,d", HabitatBases.get(level).energy(base)),
                    String.format("%,d", HabitatPower.CAPACITY));
        else
        {
            int stored = level.getBlockEntity(pos) instanceof WirelessPowerRelayBlockEntity be ? be.buffer().getEnergyStored() : 0;
            endpoint = Component.translatable(MSG + "endpoint.buffer", String.format("%,d", stored),
                    String.format("%,d", WirelessPowerRelayBlockEntity.BUFFER));
        }
        List<Link> mine = net.linksOf(pos);
        MutableComponent out = Component.translatable(MSG + "status", mine.size(), MAX_LINKS, endpoint);
        if (mine.isEmpty()) out.append(Component.translatable(MSG + "none"));
        for (Link l : mine)
        {
            Stats s = net.links.get(l);
            double d = l.distance();
            out.append(Component.translatable(MSG + "link", Math.round(d), Math.round(efficiency(d) * 100), s.avg,
                    Component.translatable(MSG + "dir." + direction(l, s, pos))));
        }
        return out;
    }

    /** paused / idle / send / receive, seen from pos */
    private static String direction(Link l, Stats s, BlockPos pos)
    {
        byte st = s.state();
        if (st == 0) return "paused";
        if (st == 1) return "idle";
        boolean fromA = st == 2;
        return fromA == l.a.equals(pos) ? "send" : "receive";
    }

    /** /abyssia relay list: one line per link, then the unlinked relays. */
    public static List<String> describe(ServerLevel level)
    {
        RelayNetwork net = get(level);
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "%d relays, %d links", net.relays.size(), net.links.size()));
        for (Map.Entry<Link, Stats> e : net.links.entrySet())
        {
            Link l = e.getKey();
            Stats s = e.getValue();
            double d = l.distance();
            String state = switch (s.state())
            {
                case 0 -> "paused";
                case 1 -> "idle";
                case 2 -> "a->b";
                default -> "b->a";
            };
            out.add(String.format(Locale.ROOT, " %s <-> %s  %.1fm eff %d%%  %s  %d FE/t (band %d)  a=%s b=%s",
                    l.a.toShortString(), l.b.toShortString(), d, Math.round(efficiency(d) * 100), state, s.avg, s.band(),
                    endKind(level, l.a), endKind(level, l.b)));
        }
        for (BlockPos pos : net.relays)
            if (net.linkCount(pos) == 0) out.add(" unlinked " + pos.toShortString() + " " + endKind(level, pos));
        return out;
    }

    private static String endKind(ServerLevel level, BlockPos pos)
    {
        int base = baseOf(level, pos);
        if (base >= 0) return "base#" + base + "(" + HabitatBases.get(level).energy(base) + " FE)";
        if (!level.isLoaded(pos)) return "unloaded";
        return level.getBlockEntity(pos) instanceof WirelessPowerRelayBlockEntity be
                ? "buffer(" + be.buffer().getEnergyStored() + "/" + WirelessPowerRelayBlockEntity.BUFFER + ")" : "missing";
    }

    // ---------------------------------------------------------------- save / load

    public static RelayNetwork load(CompoundTag tag, HolderLookup.Provider registries)
    {
        RelayNetwork net = new RelayNetwork();
        for (long p : tag.getLongArray("Relays")) net.relays.add(BlockPos.of(p));
        for (Tag t : tag.getList("Links", Tag.TAG_COMPOUND))
        {
            CompoundTag c = (CompoundTag) t;
            BlockPos a = BlockPos.of(c.getLong("A")), b = BlockPos.of(c.getLong("B"));
            if (!a.equals(b) && net.relays.contains(a) && net.relays.contains(b)) net.links.put(Link.of(a, b), new Stats());
        }
        return net;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        tag.putLongArray("Relays", relays.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag list = new ListTag();
        for (Link l : links.keySet())
        {
            CompoundTag c = new CompoundTag();
            c.putLong("A", l.a.asLong());
            c.putLong("B", l.b.asLong());
            list.add(c);
        }
        tag.put("Links", list);
        return tag;
    }
}
