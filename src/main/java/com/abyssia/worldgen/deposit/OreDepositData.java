package com.abyssia.worldgen.deposit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SavedData for persisting ore deposits across world saves.
 */
public class OreDepositData extends SavedData
{
    public static final String NAME = "abyssia_ore_deposits";

    private final Map<UUID, OreDeposit> deposits = new HashMap<>();
    /** Spatial index: deposits by 64x64 column cell (a deposit is listed in every cell its bounds touch). Never saved. */
    private static final int CELL_SHIFT = 6;
    private final Map<Long, List<OreDeposit>> grid = new HashMap<>();

    public static OreDepositData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(OreDepositData::load, OreDepositData::new, NAME);
    }

    public Optional<OreDeposit> get(UUID id)
    {
        return Optional.ofNullable(deposits.get(id));
    }

    /** Read-only view of every deposit (no copy: callers must not hold it across a registration). */
    public Map<UUID, OreDeposit> getAll()
    {
        return Collections.unmodifiableMap(deposits);
    }

    private static long cell(int cx, int cz)
    {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private void index(OreDeposit d)
    {
        AABB b = d.bounds();
        for (int cx = (int) Math.floor(b.minX) >> CELL_SHIFT; cx <= (int) Math.floor(b.maxX) >> CELL_SHIFT; cx++)
            for (int cz = (int) Math.floor(b.minZ) >> CELL_SHIFT; cz <= (int) Math.floor(b.maxZ) >> CELL_SHIFT; cz++)
                grid.computeIfAbsent(cell(cx, cz), k -> new ArrayList<>(2)).add(d);
    }

    private void unindex(OreDeposit d)
    {
        AABB b = d.bounds();
        for (int cx = (int) Math.floor(b.minX) >> CELL_SHIFT; cx <= (int) Math.floor(b.maxX) >> CELL_SHIFT; cx++)
            for (int cz = (int) Math.floor(b.minZ) >> CELL_SHIFT; cz <= (int) Math.floor(b.maxZ) >> CELL_SHIFT; cz++)
            {
                List<OreDeposit> list = grid.get(cell(cx, cz));
                if (list != null && list.removeIf(o -> o.depositId().equals(d.depositId())) && list.isEmpty()) grid.remove(cell(cx, cz));
            }
    }

    /** True if a deposit of this mineral already contains the point. */
    public boolean containsPoint(ResourceLocation mineralId, double x, double y, double z)
    {
        List<OreDeposit> list = grid.get(cell((int) Math.floor(x) >> CELL_SHIFT, (int) Math.floor(z) >> CELL_SHIFT));
        if (list == null) return false;
        for (OreDeposit d : list) if (d.mineralId().equals(mineralId) && d.bounds().contains(x, y, z)) return true;
        return false;
    }

    /** Deposits whose bounds intersect the area (uses the index; falls back to every deposit for a huge area). */
    public List<OreDeposit> intersecting(AABB area)
    {
        int x0 = (int) Math.floor(area.minX) >> CELL_SHIFT, x1 = (int) Math.floor(area.maxX) >> CELL_SHIFT;
        int z0 = (int) Math.floor(area.minZ) >> CELL_SHIFT, z1 = (int) Math.floor(area.maxZ) >> CELL_SHIFT;
        List<OreDeposit> out = new ArrayList<>();
        if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) > 4096)
        {
            for (OreDeposit d : deposits.values()) if (d.bounds().intersects(area)) out.add(d);
            return out;
        }
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++)
            {
                List<OreDeposit> list = grid.get(cell(cx, cz));
                if (list == null) continue;
                for (OreDeposit d : list) if (d.bounds().intersects(area) && seen.add(d.depositId())) out.add(d);
            }
        return out;
    }

    public void register(OreDeposit deposit)
    {
        if (!deposits.containsKey(deposit.depositId()))
        {
            deposits.put(deposit.depositId(), deposit);
            index(deposit);
            setDirty();
        }
    }

    public boolean remove(UUID id)
    {
        OreDeposit removed = deposits.remove(id);
        if (removed != null)
        {
            unindex(removed);
            setDirty();
            return true;
        }
        return false;
    }

    public void incrementMined(UUID id, int amount)
    {
        OreDeposit deposit = deposits.get(id);
        if (deposit != null)
        {
            unindex(deposit);
            OreDeposit updated = deposit.withMinedAmount(amount);
            deposits.put(id, updated);
            index(updated);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for (OreDeposit deposit : deposits.values())
        {
            CompoundTag depositTag = new CompoundTag();
            deposit.save(depositTag);
            list.add(depositTag);
        }
        tag.put("deposits", list);
        return tag;
    }

    public static OreDepositData load(CompoundTag tag)
    {
        OreDepositData data = new OreDepositData();
        ListTag list = tag.getList("deposits", Tag.TAG_COMPOUND);
        for (Tag t : list)
        {
            CompoundTag depositTag = (CompoundTag) t;
            OreDeposit deposit = OreDeposit.load(depositTag);
            data.deposits.put(deposit.depositId(), deposit);
            data.index(deposit);
        }
        return data;
    }

    public int size()
    {
        return deposits.size();
    }

    public boolean isEmpty()
    {
        return deposits.isEmpty();
    }
}