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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
 *     <li>Links form a forest (never a loop) and any number of links per relay is allowed. Right after a relay is
 *     placed or removed, separate networks within 128 blocks of each other (3D, antenna tip to tip; terrain / water
 *     ignored) are joined by their shortest relay pair: a new relay links to the single nearest relay, and only gets a
 *     second link when it bridges two networks that were out of range of each other. Existing links are never
 *     re-chosen; loops left in older saves lose their longest link.</li>
 *     <li>A link whose chunk (either end) is not loaded is kept but paused: no transfer, not drawn. No chunk tickets.</li>
 *     <li>Every tick each link moves FE once, from the end with the higher fill ratio to the lower (no routing, so a
 *     loop cannot multiply energy): {@code send = min(512, src, ceil(gap * capS*capD/(capS+capD)))}, no loss.</li>
 *     <li>Clients get the link list ({@link RelaySyncPacket}) on change (min 10 ticks apart), login and dimension change.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public class RelayNetwork extends SavedData
{
    public static final String NAME = "abyssia_wireless_relays";
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
        return level.getDataStorage().computeIfAbsent(RelayNetwork::load, RelayNetwork::new, NAME);
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
        List<Link> out = new ArrayList<>();
        for (Link l : links.keySet()) if (l.has(pos)) out.add(l);
        return out;
    }

    private int linkCount(BlockPos pos)
    {
        int n = 0;
        for (Link l : links.keySet()) if (l.has(pos)) n++;
        return n;
    }

    /** No transmission loss (user request 2026-10-04; WR01 first had 100/90/80/70 % by distance). */
    public static double efficiency(double distance)
    {
        return 1.0;
    }

    /** Base (root module id) whose module box holds pos, or -1. */
    public static int baseAt(ServerLevel level, BlockPos pos)
    {
        HabitatBases data = HabitatBases.get(level);
        int m = data.moduleAt(pos);
        return m < 0 ? -1 : data.root(m);
    }

    // ---------------------------------------------------------------- tick

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        RelayNetwork net = get(level);
        if (net.relays.isEmpty() && net.links.isEmpty()) return;
        net.tick(level);
    }

    private void tick(ServerLevel level)
    {
        long now = level.getGameTime();
        if (searchNow || now % SEARCH_INTERVAL == 0) validate(level);
        if (searchNow)
        {
            searchNow = false;
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
            searchNow = true;
        }
        for (BlockPos pos : relays) updateLinked(level, pos);
    }

    /** Joins separate networks in range by their shortest relay pair (Kruskal over the existing forest). */
    private void search(ServerLevel level)
    {
        Map<BlockPos, BlockPos> group = new java.util.HashMap<>();
        dropLoops(level, group);
        List<BlockPos> all = new ArrayList<>(relays);
        if (all.size() < 2) return;
        all.sort(Comparator.comparingLong(BlockPos::asLong)); // deterministic order (ties, restarts)
        double maxSq = MAX_DISTANCE * MAX_DISTANCE;
        List<Link> pairs = new ArrayList<>();
        for (int a = 0; a < all.size(); a++)
            for (int b = a + 1; b < all.size(); b++)
            {
                BlockPos p = all.get(a), q = all.get(b);
                if (p.distSqr(q) <= maxSq && !root(group, p).equals(root(group, q))) pairs.add(Link.of(p, q));
            }
        pairs.sort(Comparator.comparingDouble(Link::distance).thenComparingLong(l -> l.a.asLong()).thenComparingLong(l -> l.b.asLong()));
        for (Link l : pairs)
        {
            if (!join(group, l.a, l.b)) continue; // already connected
            links.put(l, new Stats());
            setDirty();
            syncDirty = true;
            updateLinked(level, l.a);
            updateLinked(level, l.b);
        }
    }

    /** Union-find over the current links (shortest first); a link that closes a loop is removed. */
    private void dropLoops(ServerLevel level, Map<BlockPos, BlockPos> group)
    {
        List<Link> sorted = new ArrayList<>(links.keySet());
        sorted.sort(Comparator.comparingDouble(Link::distance).thenComparingLong(l -> l.a.asLong()).thenComparingLong(l -> l.b.asLong()));
        for (Link l : sorted)
        {
            if (join(group, l.a, l.b)) continue;
            links.remove(l);
            setDirty();
            syncDirty = true;
            updateLinked(level, l.a);
            updateLinked(level, l.b);
        }
    }

    /** Joins the groups of a and b; false when they were already in one group. */
    private static boolean join(Map<BlockPos, BlockPos> group, BlockPos a, BlockPos b)
    {
        BlockPos ra = root(group, a), rb = root(group, b);
        if (ra.equals(rb)) return false;
        group.put(ra, rb);
        return true;
    }

    private static BlockPos root(Map<BlockPos, BlockPos> group, BlockPos p)
    {
        BlockPos r = p;
        for (BlockPos up = group.get(r); up != null; up = group.get(r)) r = up;
        for (BlockPos q = p; !q.equals(r); )
        {
            BlockPos up = group.get(q);
            group.put(q, r);
            q = up;
        }
        return r;
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

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) sendTo(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) sendTo(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) sendTo(player);
    }

    // ---------------------------------------------------------------- status / tooling

    private static final String MSG = "message." + Abyssia.MODID + ".relay.";

    /** Sneak + right-click: link count, endpoint, then per link distance / recent FE/t / direction. */
    public static Component status(ServerLevel level, BlockPos pos)
    {
        RelayNetwork net = get(level);
        int base = baseAt(level, pos);
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
        MutableComponent out = Component.translatable(MSG + "status", mine.size(), endpoint);
        if (mine.isEmpty()) out.append(Component.translatable(MSG + "none"));
        for (Link l : mine)
        {
            Stats s = net.links.get(l);
            double d = l.distance();
            out.append(Component.translatable(MSG + "link", Math.round(d), s.avg,
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
            out.add(String.format(Locale.ROOT, " %s <-> %s  %.1fm  %s  %d FE/t (band %d)  a=%s b=%s",
                    l.a.toShortString(), l.b.toShortString(), d, state, s.avg, s.band(),
                    endKind(level, l.a), endKind(level, l.b)));
        }
        for (BlockPos pos : net.relays)
            if (net.linkCount(pos) == 0) out.add(" unlinked " + pos.toShortString() + " " + endKind(level, pos));
        return out;
    }

    private static String endKind(ServerLevel level, BlockPos pos)
    {
        int base = baseAt(level, pos);
        if (base >= 0) return "base#" + base + "(" + HabitatBases.get(level).energy(base) + " FE)";
        if (!level.isLoaded(pos)) return "unloaded";
        return level.getBlockEntity(pos) instanceof WirelessPowerRelayBlockEntity be
                ? "buffer(" + be.buffer().getEnergyStored() + "/" + WirelessPowerRelayBlockEntity.BUFFER + ")" : "missing";
    }

    // ---------------------------------------------------------------- save / load

    public static RelayNetwork load(CompoundTag tag)
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
    public CompoundTag save(CompoundTag tag)
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
