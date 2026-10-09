package com.abyssia.research;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A player's research state, stored in the persisted player data ({@link Player#PERSISTED_NBT_TAG}) under {@link #KEY}:
 * <pre>{schema_version, scanned_targets:[id], fragments:{id:[discovery key]}, unlocked_technologies:[id], legacy_wrecks_migrated}</pre>
 * {@code scanned_targets} is the database (a target is registered once); {@code fragments} holds the discovery keys that
 * counted, so the same discovery never counts twice. Load, change, then {@link #save}; the object is a snapshot.
 */
public final class ResearchData
{
    public static final String KEY = "abyssia_research";
    public static final int SCHEMA_VERSION = 1;

    public final Set<ResourceLocation> scanned = new LinkedHashSet<>();
    public final Map<ResourceLocation, Set<String>> fragments = new LinkedHashMap<>();
    public final Set<ResourceLocation> unlocked = new LinkedHashSet<>();
    public boolean legacyMigrated;

    public static ResearchData load(Player player)
    {
        return fromTag(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY));
    }

    public void save(Player player)
    {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(KEY, toTag());
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Copies the stored research state (not the data object) from one player to another (death, end return). */
    public static void copy(Entity from, Entity to)
    {
        CompoundTag old = from.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!old.contains(KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag data = to.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(KEY, old.getCompound(KEY).copy());
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static ResearchData fromTag(CompoundTag tag)
    {
        ResearchData d = new ResearchData();
        // schema_version is for later migrations; version 1 is the only layout so far
        for (Tag t : tag.getList("scanned_targets", Tag.TAG_STRING))
        {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) d.scanned.add(id);
        }
        CompoundTag frag = tag.getCompound("fragments");
        for (String k : frag.getAllKeys())
        {
            ResourceLocation id = ResourceLocation.tryParse(k);
            if (id == null) continue;
            Set<String> keys = new LinkedHashSet<>();
            for (Tag t : frag.getList(k, Tag.TAG_STRING)) keys.add(t.getAsString());
            d.fragments.put(id, keys);
        }
        for (Tag t : tag.getList("unlocked_technologies", Tag.TAG_STRING))
        {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) d.unlocked.add(id);
        }
        d.legacyMigrated = tag.getBoolean("legacy_wrecks_migrated");
        return d;
    }

    public CompoundTag toTag()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", SCHEMA_VERSION);
        ListTag sc = new ListTag();
        for (ResourceLocation id : scanned) sc.add(StringTag.valueOf(id.toString()));
        tag.put("scanned_targets", sc);
        CompoundTag frag = new CompoundTag();
        fragments.forEach((id, keys) -> {
            ListTag l = new ListTag();
            for (String k : keys) l.add(StringTag.valueOf(k));
            frag.put(id.toString(), l);
        });
        tag.put("fragments", frag);
        ListTag un = new ListTag();
        for (ResourceLocation id : unlocked) un.add(StringTag.valueOf(id.toString()));
        tag.put("unlocked_technologies", un);
        tag.putBoolean("legacy_wrecks_migrated", legacyMigrated);
        return tag;
    }

    /** How many distinct discoveries of the target have counted. */
    public int count(ResourceLocation target)
    {
        Set<String> keys = fragments.get(target);
        return keys == null ? 0 : keys.size();
    }

    /** Records a discovery; false when it was already counted. */
    public boolean addDiscovery(ResourceLocation target, String key)
    {
        boolean added = fragments.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(key);
        scanned.add(target);
        return added;
    }
}
