package com.abyssia.research;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The client copy of the research state, filled by the sync payloads (display only, never trusted by the server).
 * Plain data with no client-only classes, so it is safe to load on a server.
 */
public final class ClientResearch
{
    private static volatile List<Technology> technologies = List.of();
    private static volatile List<ScanTarget> targets = List.of();
    private static volatile Map<ResourceLocation, Integer> fragments = Map.of();
    private static volatile Set<ResourceLocation> unlocked = Set.of();
    /** unlock key -> technologies listing it (gated keys), derived from {@link #technologies} */
    private static volatile Map<ResourceLocation, List<Technology>> gated = Map.of();
    private static final List<Consumer<ResourceLocation>> UNLOCK_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Runnable> REFRESH_LISTENERS = new CopyOnWriteArrayList<>();

    private ClientResearch() {}

    public static boolean isUnlocked(ResourceLocation technology)
    {
        return unlocked.contains(technology);
    }

    /** The target is in the database (scanned at least once). */
    public static boolean isScanned(ResourceLocation target)
    {
        return fragments.containsKey(target);
    }

    /** Counted discoveries of the target (0 when unscanned). */
    public static int fragments(ResourceLocation target)
    {
        return fragments.getOrDefault(target, 0);
    }

    /** Discoveries the target needs: the largest count a technology asks of it (1 when none does). */
    public static int needed(ResourceLocation target)
    {
        int need = 1;
        for (Technology t : technologies)
            for (Technology.Requirement r : t.requirements())
                if (r.target().equals(target)) need = Math.max(need, r.count());
        return need;
    }

    /** {have, need} summed over the technology's scan requirements (each capped at its count); {0, 0} when it has none. */
    public static int[] progress(ResourceLocation technology)
    {
        int have = 0, need = 0;
        Optional<Technology> t = technology(technology);
        if (t.isPresent())
            for (Technology.Requirement r : t.get().requirements())
            {
                need += r.count();
                have += Math.min(r.count(), fragments(r.target()));
            }
        return new int[] {have, need};
    }

    public static List<Technology> technologies()
    {
        return technologies;
    }

    public static List<ScanTarget> targets()
    {
        return targets;
    }

    public static Optional<Technology> technology(ResourceLocation id)
    {
        for (Technology t : technologies) if (t.id().equals(id)) return Optional.of(t);
        return Optional.empty();
    }

    public static Optional<ScanTarget> target(ResourceLocation id)
    {
        for (ScanTarget t : targets) if (t.id().equals(id)) return Optional.of(t);
        return Optional.empty();
    }

    public static Set<ResourceLocation> unlockedTechnologies()
    {
        return unlocked;
    }

    /** Some synced technology lists the key in its unlocks (such keys are locked until one of them is unlocked). */
    public static boolean isKeyGated(ResourceLocation key)
    {
        return gated.containsKey(key);
    }

    /** True when the key is not gated, or a technology listing it is unlocked. (Creative/op bypass is not applied here.) */
    public static boolean isKeyUnlocked(ResourceLocation key)
    {
        List<Technology> list = gated.get(key);
        if (list == null) return true;
        for (Technology t : list) if (unlocked.contains(t.id())) return true;
        return false;
    }

    /** The technology that still locks the key; empty when the key is free or already unlocked. */
    public static Optional<Technology> lockedBy(ResourceLocation key)
    {
        List<Technology> list = gated.get(key);
        if (list == null || list.isEmpty() || isKeyUnlocked(key)) return Optional.empty();
        return Optional.of(list.get(0));
    }

    /** Called on the client main thread after every full sync or delta (lock states may have changed). */
    public static void addRefreshListener(Runnable listener)
    {
        REFRESH_LISTENERS.add(listener);
    }

    private static void refreshed()
    {
        for (Runnable l : new ArrayList<>(REFRESH_LISTENERS)) l.run();
    }

    /** Called on the client main thread for every unlock toast (the technology id). */
    public static void addUnlockListener(Consumer<ResourceLocation> listener)
    {
        UNLOCK_LISTENERS.add(listener);
    }

    static void applyFull(ResearchSyncPayload msg)
    {
        technologies = List.copyOf(msg.technologies());
        targets = List.copyOf(msg.targets());
        fragments = new HashMap<>(msg.fragments());
        unlocked = new HashSet<>(msg.unlocked());
        Map<ResourceLocation, List<Technology>> keys = new HashMap<>();
        for (Technology t : technologies) for (ResourceLocation k : t.unlocks()) keys.computeIfAbsent(k, x -> new ArrayList<>()).add(t);
        gated = keys;
        refreshed();
    }

    static void applyDelta(ResearchDeltaPayload msg)
    {
        Map<ResourceLocation, Integer> f = new HashMap<>(fragments);
        f.putAll(msg.fragments());
        fragments = f;
        Set<ResourceLocation> u = new HashSet<>(unlocked);
        u.addAll(msg.unlocked());
        unlocked = u;
        refreshed();
    }

    static void onUnlocked(ResourceLocation technology)
    {
        for (Consumer<ResourceLocation> l : new ArrayList<>(UNLOCK_LISTENERS)) l.accept(technology);
    }
}
