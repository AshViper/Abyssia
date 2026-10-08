package com.abyssia.habitat.power;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * ECO03: the oxygen reserve of each habitat module (keyed by module id, which is never reused), in ticks. It refills
 * while the module's base has power and runs down while it has none; at 0 the air is stale (HabitatLifeSupport).
 */
public class HabitatAir extends SavedData
{
    public static final String NAME = "abyssia_habitat_air";
    /** full reserve: 5 minutes without power */
    public static final int MAX = 6_000;
    /** refill per tick while powered (empty to full in 30 s) */
    public static final int REFILL = 10;

    private final Map<Integer, Integer> oxygen = new HashMap<>();

    public static HabitatAir get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(HabitatAir::new, HabitatAir::load, null), NAME);
    }

    public int oxygen(int module)
    {
        return oxygen.getOrDefault(module, MAX);
    }

    /** One tick of one module: refill when powered, drain when not. */
    public void step(int module, boolean powered)
    {
        int now = oxygen(module);
        int next = powered ? Math.min(MAX, now + REFILL) : Math.max(0, now - 1);
        if (next == now) return;
        if (next == MAX) oxygen.remove(module);
        else oxygen.put(module, next);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        int[] flat = new int[oxygen.size() * 2];
        int i = 0;
        for (Map.Entry<Integer, Integer> e : oxygen.entrySet())
        {
            flat[i++] = e.getKey();
            flat[i++] = e.getValue();
        }
        tag.putIntArray("Oxygen", flat);
        return tag;
    }

    public static HabitatAir load(CompoundTag tag, HolderLookup.Provider registries)
    {
        HabitatAir data = new HabitatAir();
        int[] flat = tag.getIntArray("Oxygen");
        for (int i = 0; i + 1 < flat.length; i += 2) data.oxygen.put(flat[i], flat[i + 1]);
        return data;
    }
}
