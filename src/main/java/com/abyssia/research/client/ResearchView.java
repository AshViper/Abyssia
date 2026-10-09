package com.abyssia.research.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * AB05 adapter between the UI (ScanHud, DatabaseScreen) and agent R's synced client state (ClientResearch). The UI reads only this
 * facade, never R's types; {@link #source} is set once to {@link ClientResearchSource} (the single place to fix when R's record
 * accessors differ). With no source everything is empty, so the UI never crashes on missing data.
 */
public final class ResearchView
{
    /** requirement of a technology: scan {@code count} (fragments of) {@code target} */
    public record Req(ResourceLocation target, int count) {}

    public record Tech(ResourceLocation id, Component title, String tier, String depthBand, List<Req> requirements,
                       List<ResourceLocation> prerequisites, List<String> unlocks) {}

    /** category is the ScanTarget category string; nameKey / descKey are lang keys (descKey may be empty) */
    public record Target(ResourceLocation id, String category, String nameKey, String descKey) {}

    public interface Source
    {
        boolean isUnlocked(ResourceLocation tech);
        boolean isScanned(ResourceLocation target);
        /** distinct fragments of this target collected so far (0 when none) */
        int fragments(ResourceLocation target);
        List<Tech> technologies();
        List<Target> targets();
    }

    private static final Source EMPTY = new Source()
    {
        public boolean isUnlocked(ResourceLocation tech) { return false; }
        public boolean isScanned(ResourceLocation target) { return false; }
        public int fragments(ResourceLocation target) { return 0; }
        public List<Tech> technologies() { return List.of(); }
        public List<Target> targets() { return List.of(); }
    };

    private static Source source = EMPTY;

    private ResearchView() {}

    public static void setSource(Source s)
    {
        source = s == null ? EMPTY : s;
    }

    public static boolean isUnlocked(ResourceLocation tech) { return safe(() -> source.isUnlocked(tech), false); }
    public static boolean isScanned(ResourceLocation target) { return safe(() -> source.isScanned(target), false); }
    public static int fragments(ResourceLocation target) { return safe(() -> source.fragments(target), 0); }
    public static List<Tech> technologies() { return safe(() -> source.technologies(), List.of()); }
    public static List<Target> targets() { return safe(() -> source.targets(), List.of()); }

    /** progress n of a requirement: fragments collected, at least 1 once the target is scanned */
    public static int progress(Req req)
    {
        return Math.min(req.count(), Math.max(fragments(req.target()), isScanned(req.target()) ? 1 : 0));
    }

    /** {n, m} while some technology still needs more than one scan of this target and it is not analysed yet; else null */
    public static int[] fragmentStatus(ResourceLocation target)
    {
        int need = 0;
        for (Tech t : technologies())
            for (Req r : t.requirements())
                if (r.target().equals(target)) need = Math.max(need, r.count());
        if (need <= 1) return null;
        return new int[] {Math.min(need, fragments(target)), need};
    }

    /** display name of a target (lang key, else the id) */
    public static Component targetName(ResourceLocation id)
    {
        for (Target t : targets())
            if (t.id().equals(id) && !t.nameKey().isEmpty()) return Component.translatable(t.nameKey());
        return Component.literal(id.getPath());
    }

    public static Component techTitle(ResourceLocation id)
    {
        for (Tech t : technologies())
            if (t.id().equals(id)) return t.title();
        return Component.literal(id.getPath());
    }

    private interface Getter<T> { T get(); }

    private static <T> T safe(Getter<T> getter, T fallback)
    {
        try
        {
            T v = getter.get();
            return v == null ? fallback : v;
        }
        catch (RuntimeException e)
        {
            return fallback;
        }
    }
}
