package com.abyssia.map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** MP01: ids of the filled maps that are deep sea maps (authoritative, server side; saved with the overworld). */
public class DeepMapIndex extends SavedData
{
    public static final String NAME = "abyssia_deep_maps";
    private final Set<Integer> ids = new HashSet<>();

    public static DeepMapIndex get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(DeepMapIndex::load, DeepMapIndex::new, NAME);
    }

    public boolean contains(int id)
    {
        return ids.contains(id);
    }

    public void add(int id)
    {
        if (ids.add(id)) setDirty();
    }

    private static DeepMapIndex load(CompoundTag tag)
    {
        DeepMapIndex index = new DeepMapIndex();
        for (int id : tag.getIntArray("ids")) index.ids.add(id);
        return index;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.put("ids", new IntArrayTag(ids.stream().mapToInt(Integer::intValue).toArray()));
        return tag;
    }
}
