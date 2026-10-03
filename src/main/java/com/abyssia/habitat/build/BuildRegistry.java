package com.abyssia.habitat.build;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BT01a: every build menu entry, by string id. Order = category order, then registration order inside a category
 * (BuildContent decides it; BT01h finalises). Filled during mod construction, read-only afterwards.
 */
public final class BuildRegistry
{
    private static final Map<String, BuildEntry> BY_ID = new LinkedHashMap<>();
    private static final Map<BuildCategory, List<BuildEntry>> BY_CATEGORY = new EnumMap<>(BuildCategory.class);
    private static List<BuildEntry> ordered = List.of();

    private BuildRegistry() {}

    public static synchronized <T extends BuildEntry> T register(T entry)
    {
        if (BY_ID.containsKey(entry.id())) throw new IllegalStateException("Duplicate build entry id: " + entry.id());
        BY_ID.put(entry.id(), entry);
        BY_CATEGORY.computeIfAbsent(entry.category(), c -> new ArrayList<>()).add(entry);
        rebuildOrdered();
        return entry;
    }

    private static void rebuildOrdered()
    {
        List<BuildEntry> all = new ArrayList<>();
        for (BuildCategory category : BuildCategory.values()) all.addAll(byCategory(category));
        ordered = Collections.unmodifiableList(all);
    }

    /** BT01h: final menu order - inside each category the listed ids come first in list order, the rest keep registration order */
    public static synchronized void applyOrder(List<String> ids)
    {
        for (List<BuildEntry> list : BY_CATEGORY.values())
            list.sort(Comparator.comparingInt(e -> ids.contains(e.id()) ? ids.indexOf(e.id()) : Integer.MAX_VALUE));
        rebuildOrdered();
    }

    @Nullable
    public static BuildEntry get(String id)
    {
        return BY_ID.get(id);
    }

    /** the entry with this id, else the first registered one (old / unknown NBT) */
    public static BuildEntry getOrDefault(String id)
    {
        BuildEntry entry = BY_ID.get(id);
        return entry != null ? entry : first();
    }

    public static BuildEntry first()
    {
        if (ordered.isEmpty()) throw new IllegalStateException("No build entries registered");
        return ordered.get(0);
    }

    /** all entries in menu order */
    public static List<BuildEntry> all()
    {
        return ordered;
    }

    public static List<BuildEntry> byCategory(BuildCategory category)
    {
        List<BuildEntry> list = BY_CATEGORY.get(category);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /** categories that have at least one entry, in order */
    public static List<BuildCategory> categories()
    {
        List<BuildCategory> out = new ArrayList<>();
        for (BuildCategory category : BuildCategory.values())
            if (!byCategory(category).isEmpty()) out.add(category);
        return out;
    }
}
