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
    private static final List<Consumer<ResourceLocation>> UNLOCK_LISTENERS = new CopyOnWriteArrayList<>();

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
    }

    static void applyDelta(ResearchDeltaPayload msg)
    {
        Map<ResourceLocation, Integer> f = new HashMap<>(fragments);
        f.putAll(msg.fragments());
        fragments = f;
        Set<ResourceLocation> u = new HashSet<>(unlocked);
        u.addAll(msg.unlocked());
        unlocked = u;
    }

    static void onUnlocked(ResourceLocation technology)
    {
        for (Consumer<ResourceLocation> l : new ArrayList<>(UNLOCK_LISTENERS)) l.accept(technology);
    }
}
