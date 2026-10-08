package com.abyssia.worldgen.deposit;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
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

    public static OreDepositData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OreDepositData::new, OreDepositData::load, null),
                NAME);
    }

    public Optional<OreDeposit> get(UUID id)
    {
        return Optional.ofNullable(deposits.get(id));
    }

    public Map<UUID, OreDeposit> getAll()
    {
        return Map.copyOf(deposits);
    }

    public void register(OreDeposit deposit)
    {
        if (!deposits.containsKey(deposit.depositId()))
        {
            deposits.put(deposit.depositId(), deposit);
            setDirty();
        }
    }

    public boolean remove(UUID id)
    {
        if (deposits.remove(id) != null)
        {
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
            deposits.put(id, deposit.withMinedAmount(amount));
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
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

    public static OreDepositData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        OreDepositData data = new OreDepositData();
        ListTag list = tag.getList("deposits", Tag.TAG_COMPOUND);
        for (Tag t : list)
        {
            CompoundTag depositTag = (CompoundTag) t;
            OreDeposit deposit = OreDeposit.load(depositTag);
            data.deposits.put(deposit.depositId(), deposit);
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