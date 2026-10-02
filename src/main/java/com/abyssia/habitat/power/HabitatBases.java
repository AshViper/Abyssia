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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * H08: completed habitat modules of one dimension (outer box each) joined into bases by union-find, and the shared FE
 * of each base (keyed by its root module id). Saved with the level.
 */
public class HabitatBases extends SavedData
{
    public static final String NAME = "abyssia_habitat_bases";

    public record Module(int id, BoundingBox box) {}

    private final List<Module> modules = new ArrayList<>();
    /** union-find parent per module id (index = id) */
    private final List<Integer> parent = new ArrayList<>();
    /** shared FE per base root */
    private final Map<Integer, Integer> energy = new HashMap<>();

    public static HabitatBases get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(HabitatBases::new, HabitatBases::load, null), NAME);
    }

    public List<Module> modules()
    {
        return modules;
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

    /** Module containing pos (inside its box, shell included), or -1. */
    public int moduleAt(BlockPos pos)
    {
        for (Module m : modules)
            if (m.box.isInside(pos)) return m.id;
        return -1;
    }

    /** Base (root module id) containing pos, or -1. */
    public int baseAt(BlockPos pos)
    {
        int m = moduleAt(pos);
        return m < 0 ? -1 : root(m);
    }

    public int add(BoundingBox box)
    {
        int id = modules.size();
        modules.add(new Module(id, box));
        parent.add(id);
        energy.put(id, 0);
        setDirty();
        return id;
    }

    /** Joins two bases; the energy is summed (capped). Returns the new root. */
    public int union(int a, int b, int capacity)
    {
        int ra = root(a), rb = root(b);
        if (ra == rb) return ra;
        int keep = Math.min(ra, rb), drop = Math.max(ra, rb);
        parent.set(drop, keep);
        energy.put(keep, (int) Math.min(capacity, (long) energy.getOrDefault(keep, 0) + energy.getOrDefault(drop, 0)));
        energy.remove(drop);
        setDirty();
        return keep;
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

    /** Boxes of every module of a base. */
    public List<BoundingBox> boxes(int base)
    {
        List<BoundingBox> out = new ArrayList<>();
        for (Module m : modules)
            if (root(m.id) == base) out.add(m.box);
        return out;
    }

    /** Distinct base roots. */
    public List<Integer> bases()
    {
        List<Integer> out = new ArrayList<>();
        for (Module m : modules)
            if (root(m.id) == m.id) out.add(m.id);
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (Module m : modules)
        {
            CompoundTag t = new CompoundTag();
            BoundingBox b = m.box;
            t.putIntArray("Box", new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
            t.putInt("Parent", parent.get(m.id));
            t.putInt("Energy", energy.getOrDefault(m.id, 0));
            list.add(t);
        }
        tag.put("Modules", list);
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
            data.modules.add(new Module(i, new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5])));
            data.parent.add(Math.max(0, Math.min(list.size() - 1, t.getInt("Parent"))));
            data.energy.put(i, t.getInt("Energy"));
        }
        // energy only lives on roots
        for (int i = 0; i < data.modules.size(); i++)
            if (data.root(i) != i) data.energy.remove(i);
        return data;
    }
}
