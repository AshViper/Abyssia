package com.abyssia.map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** MP01: ids of the maps that are Deep Sea Maps (authoritative; the stack marker is only for the client tooltip). */
public class DeepMapIndex extends SavedData
{
    public static final String NAME = "abyssia_deep_maps";

    private final Set<Integer> ids = new HashSet<>();

    public static DeepMapIndex get(ServerLevel level)
    {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(DeepMapIndex::new, DeepMapIndex::load, null), NAME);
    }

    public boolean contains(int mapId)
    {
        return ids.contains(mapId);
    }

    public void add(int mapId)
    {
        if (ids.add(mapId)) setDirty();
    }

    public static DeepMapIndex load(CompoundTag tag, HolderLookup.Provider registries)
    {
        DeepMapIndex index = new DeepMapIndex();
        for (int id : tag.getIntArray("ids")) index.ids.add(id);
        return index;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        tag.put("ids", new IntArrayTag(ids.stream().mapToInt(Integer::intValue).toArray()));
        return tag;
    }
}
