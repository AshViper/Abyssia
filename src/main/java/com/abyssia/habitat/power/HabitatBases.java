package com.abyssia.habitat.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H08: completed habitat modules of one dimension (outer box each) joined into bases by union-find, and the shared FE
 * of each base (keyed by its root module id = the smallest module id of the base). Saved with the level.
 * <p>
 * BT01b: modules can be removed (dismantled). Ids are list indices and never reused; a removed module keeps its slot
 * with {@code removed = true} and is skipped by every query ({@link #modules()} lists live modules only). The joins
 * are kept as edges so the bases can be rebuilt after a removal; saves from before BT01b infer the edges from
 * face-adjacent boxes of the same base.
 */
public class HabitatBases extends SavedData
{
    public static final String NAME = "abyssia_habitat_bases";

    public record Module(int id, BoundingBox box, boolean removed)
    {
        public Module(int id, BoundingBox box)
        {
            this(id, box, false);
        }
    }

    /** every module ever added, index = id (removed ones included) */
    private final List<Module> all = new ArrayList<>();
    /** union-find parent per module id (index = id) */
    private final List<Integer> parent = new ArrayList<>();
    /** shared FE per base root */
    private final Map<Integer, Integer> energy = new HashMap<>();
    /** joins (a, b) with a < b, as added by {@link #union} */
    private final List<int[]> edges = new ArrayList<>();
    /** cached live view of {@link #all} */
    private List<Module> live;

    public static HabitatBases get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(HabitatBases::new, HabitatBases::load, null), NAME);
    }

    /** Live (not removed) modules. */
    public List<Module> modules()
    {
        if (live == null)
        {
            List<Module> out = new ArrayList<>(all.size());
            for (Module m : all)
                if (!m.removed) out.add(m);
            live = Collections.unmodifiableList(out);
        }
        return live;
    }

    /** The module with this id (removed ones too), or null. */
    public Module module(int id)
    {
        return id >= 0 && id < all.size() ? all.get(id) : null;
    }

    public boolean isLive(int id)
    {
        Module m = module(id);
        return m != null && !m.removed;
    }

    /** id of the module added last (BT01a HabitatBuilder.completeModule right after register), -1 when none */
    public int lastAdded()
    {
        return all.size() - 1;
    }

    public int root(int id)
    {
        int r = id;
        while (parent.get(r) != r) r = parent.get(r);
        while (parent.get(id) != r)
        {
            int next = parent.get(id);
            parent.set(id, r);
            id = next;
        }
        return r;
    }

    /** Live module containing pos (inside its box, shell included), or -1. */
    public int moduleAt(BlockPos pos)
    {
        for (Module m : modules())
            if (m.box.isInside(pos)) return m.id;
        return -1;
    }

    /** Base (root module id) containing pos, or -1. */
    public int baseAt(BlockPos pos)
    {
        int m = moduleAt(pos);
        return m < 0 ? -1 : root(m);
    }

    /** Registers a module box; returns its id. */
    public int add(BoundingBox box)
    {
        int id = all.size();
        all.add(new Module(id, box));
        parent.add(id);
        energy.put(id, 0);
        live = null;
        setDirty();
        return id;
    }

    /** Joins two bases; the energy is summed (capped). Records the edge. Returns the new root. */
    public int union(int a, int b, int capacity)
    {
        if (a != b && isLive(a) && isLive(b)) addEdge(a, b);
        int ra = root(a), rb = root(b);
        if (ra == rb)
        {
            setDirty();
            return ra;
        }
        int keep = Math.min(ra, rb), drop = Math.max(ra, rb);
        parent.set(drop, keep);
        energy.put(keep, (int) Math.min(capacity, (long) energy.getOrDefault(keep, 0) + energy.getOrDefault(drop, 0)));
        energy.remove(drop);
        setDirty();
        return keep;
    }

    private void addEdge(int a, int b)
    {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        for (int[] e : edges)
            if (e[0] == lo && e[1] == hi) return;
        edges.add(new int[]{lo, hi});
    }

    /** Live modules joined to this one directly. */
    public List<Integer> joinedTo(int id)
    {
        List<Integer> out = new ArrayList<>();
        for (int[] e : edges)
        {
            if (e[0] == id && isLive(e[1])) out.add(e[1]);
            else if (e[1] == id && isLive(e[0])) out.add(e[0]);
        }
        return out;
    }

    /**
     * BT01b: removes a module. Its edges go, the union-find is rebuilt from the remaining edges, and the FE of its old
     * base is split over the surviving parts by box volume (remainder to the largest part; lost when nothing is left).
     * Returns false when the id is unknown or already removed. Callers drop the runtime caches (HabitatPower.remove).
     */
    public boolean remove(int id)
    {
        if (!isLive(id)) return false;
        Map<Integer, Integer> oldRoot = new HashMap<>();
        for (Module m : modules()) oldRoot.put(m.id, root(m.id));
        Map<Integer, Integer> oldEnergy = new HashMap<>(energy);

        all.set(id, new Module(id, all.get(id).box, true));
        live = null;
        edges.removeIf(e -> e[0] == id || e[1] == id || !isLive(e[0]) || !isLive(e[1]));

        // rebuild: every module its own root, then the edges (smaller root kept, as in union)
        for (int i = 0; i < parent.size(); i++) parent.set(i, i);
        for (int[] e : edges)
        {
            int ra = root(e[0]), rb = root(e[1]);
            if (ra != rb) parent.set(Math.max(ra, rb), Math.min(ra, rb));
        }

        // new components grouped by their old base
        Map<Integer, Map<Integer, Long>> volumes = new LinkedHashMap<>();
        for (Module m : modules())
            volumes.computeIfAbsent(oldRoot.get(m.id), k -> new LinkedHashMap<>()).merge(root(m.id), volume(m.box), Long::sum);
        energy.clear();
        for (Map.Entry<Integer, Map<Integer, Long>> e : volumes.entrySet())
        {
            int fe = oldEnergy.getOrDefault(e.getKey(), 0);
            Map<Integer, Long> parts = e.getValue();
            long total = 0;
            int largest = -1;
            long largestVolume = -1;
            for (Map.Entry<Integer, Long> p : parts.entrySet())
            {
                total += p.getValue();
                if (p.getValue() > largestVolume)
                {
                    largestVolume = p.getValue();
                    largest = p.getKey();
                }
            }
            int given = 0;
            for (Map.Entry<Integer, Long> p : parts.entrySet())
            {
                int share = total <= 0 ? 0 : (int) (fe * (double) p.getValue() / total);
                energy.merge(p.getKey(), share, Integer::sum);
                given += share;
            }
            if (largest >= 0) energy.merge(largest, fe - given, Integer::sum);
        }
        setDirty();
        return true;
    }

    private static long volume(BoundingBox b)
    {
        return (long) b.getXSpan() * b.getYSpan() * b.getZSpan();
    }

    public int energy(int base)
    {
        return energy.getOrDefault(base, 0);
    }

    public void setEnergy(int base, int value)
    {
        Integer old = energy.put(base, value);
        if (old == null || old != value) setDirty();
    }

    /** Boxes of every live module of a base. */
    public List<BoundingBox> boxes(int base)
    {
        List<BoundingBox> out = new ArrayList<>();
        for (Module m : modules())
            if (root(m.id) == base) out.add(m.box);
        return out;
    }

    /** Distinct base roots (live modules). */
    public List<Integer> bases()
    {
        List<Integer> out = new ArrayList<>();
        for (Module m : modules())
            if (root(m.id) == m.id) out.add(m.id);
        return out;
    }

    /**
     * A foundation (the only 1-high module) has no walls or hatches, so the builder never opens into it: it joins every
     * face-adjacent module (beside it at floor level, or a module standing on it) and other foundations here instead.
     * Returns how many joins were made.
     */
    public int joinFoundations(int capacity)
    {
        int joins = 0;
        List<Module> mods = modules();
        for (int i = 0; i < mods.size(); i++)
            for (int j = i + 1; j < mods.size(); j++)
            {
                Module a = mods.get(i), c = mods.get(j);
                // the edge is recorded even when both are already in one base (through another module): a later
                // dismantle rebuilds the bases from the edges only
                if ((foundation(a.box) || foundation(c.box)) && faceAdjacent(a.box, c.box) && !hasEdge(a.id, c.id))
                {
                    if (root(a.id) != root(c.id)) joins++;
                    union(a.id, c.id, capacity);
                }
            }
        return joins;
    }

    private boolean hasEdge(int a, int b)
    {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        for (int[] e : edges)
            if (e[0] == lo && e[1] == hi) return true;
        return false;
    }

    private static boolean foundation(BoundingBox box)
    {
        return box.getYSpan() == 1;
    }

    /** Boxes sharing a face (one ends where the other starts on one axis, overlapping on the other two). */
    public static boolean faceAdjacent(BoundingBox a, BoundingBox b)
    {
        boolean ox = a.minX() <= b.maxX() && b.minX() <= a.maxX();
        boolean oy = a.minY() <= b.maxY() && b.minY() <= a.maxY();
        boolean oz = a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ();
        boolean tx = a.maxX() + 1 == b.minX() || b.maxX() + 1 == a.minX();
        boolean ty = a.maxY() + 1 == b.minY() || b.maxY() + 1 == a.minY();
        boolean tz = a.maxZ() + 1 == b.minZ() || b.maxZ() + 1 == a.minZ();
        return (tx && oy && oz) || (ty && ox && oz) || (tz && ox && oy);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (Module m : all)
        {
            CompoundTag t = new CompoundTag();
            BoundingBox b = m.box;
            t.putIntArray("Box", new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
            t.putInt("Parent", parent.get(m.id));
            t.putInt("Energy", energy.getOrDefault(m.id, 0));
            if (m.removed) t.putBoolean("Removed", true);
            list.add(t);
        }
        tag.put("Modules", list);
        int[] flat = new int[edges.size() * 2];
        for (int i = 0; i < edges.size(); i++)
        {
            flat[i * 2] = edges.get(i)[0];
            flat[i * 2 + 1] = edges.get(i)[1];
        }
        tag.putIntArray("Edges", flat);
        return tag;
    }

    public static HabitatBases load(CompoundTag tag, HolderLookup.Provider registries)
    {
        HabitatBases data = new HabitatBases();
        ListTag list = tag.getList("Modules", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag t = list.getCompound(i);
            int[] b = t.getIntArray("Box");
            if (b.length != 6) b = new int[6];
            data.all.add(new Module(i, new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), t.getBoolean("Removed")));
            data.parent.add(Math.max(0, Math.min(list.size() - 1, t.getInt("Parent"))));
            data.energy.put(i, t.getInt("Energy"));
        }
        if (tag.contains("Edges", Tag.TAG_INT_ARRAY))
        {
            int[] flat = tag.getIntArray("Edges");
            for (int i = 0; i + 1 < flat.length; i += 2)
                if (data.isLive(flat[i]) && data.isLive(flat[i + 1]) && flat[i] != flat[i + 1]) data.addEdge(flat[i], flat[i + 1]);
        }
        else
        {
            // pre-BT01b save: joined modules are face-adjacent boxes of the same base
            List<Module> mods = data.modules();
            for (int i = 0; i < mods.size(); i++)
                for (int j = i + 1; j < mods.size(); j++)
                {
                    Module a = mods.get(i), c = mods.get(j);
                    if (data.root(a.id) == data.root(c.id) && faceAdjacent(a.box, c.box)) data.addEdge(a.id, c.id);
                }
            if (!mods.isEmpty()) data.setDirty();
        }
        // foundations built before they joined their neighbours
        if (data.joinFoundations(HabitatPower.CAPACITY) > 0) data.setDirty();
        // energy only lives on live roots
        for (int i = 0; i < data.all.size(); i++)
            if (data.root(i) != i || data.all.get(i).removed) data.energy.remove(i);
        return data;
    }
}
