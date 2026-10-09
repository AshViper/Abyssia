package com.abyssia.research;

import com.abyssia.Abyssia;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads data/&lt;namespace&gt;/technologies/*.json (reloaded with /reload):
 * <pre>{"id":"abyssia:technology/wreck_research","type":"building","title":"technology.abyssia.wreck_research",
 *  "tier":"mk0","depth_band":"A","scan_requirements":[{"target":"abyssia:scan_target/wreck_core","count":3}],
 *  "prerequisites":[],"unlocks":["abyssia:building/habitat_constructor"],"icon":"abyssia:wreck_core"}</pre>
 * {@code id} defaults to {@code <namespace>:technology/<file path>}. A technology that is a duplicate, names an undefined
 * scan target or prerequisite, or sits in a prerequisite cycle is logged and left out; the others still load.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class TechnologyRegistry extends SimpleJsonResourceReloadListener
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** parsed, before cross-checks (needs the scan targets, which may load after this) */
    private static volatile Map<ResourceLocation, Technology> raw = Map.of();
    private static volatile Map<ResourceLocation, Technology> valid = Map.of();
    private static volatile List<Technology> ordered = List.of();
    /** unlock key -> the valid technologies listing it (a key in here is "gated"); rebuilt on every reload */
    private static volatile Map<ResourceLocation, List<Technology>> gatedKeys = Map.of();

    private TechnologyRegistry()
    {
        super(new Gson(), "technologies");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event)
    {
        event.addListener(new TechnologyRegistry());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler)
    {
        Map<ResourceLocation, Technology> loaded = new LinkedHashMap<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            try
            {
                Technology t = parse(e.getKey(), e.getValue());
                if (loaded.containsKey(t.id())) LOGGER.error("Technology {} ({}): duplicate id, skipped", t.id(), e.getKey());
                else loaded.put(t.id(), t);
            }
            catch (RuntimeException ex)
            {
                LOGGER.error("Technology {}: {}", e.getKey(), ex.getMessage());
            }
        });
        raw = loaded;
        revalidate();
    }

    private static Technology parse(ResourceLocation file, JsonElement json)
    {
        JsonObject o = GsonHelper.convertToJsonObject(json, "technology");
        ResourceLocation id = o.has("id") ? ScanTargetRegistry.idOf(GsonHelper.getAsString(o, "id"))
                : ResourceLocation.fromNamespaceAndPath(file.getNamespace(), "technology/" + file.getPath());
        String type = GsonHelper.getAsString(o, "type", "misc");
        String title = GsonHelper.getAsString(o, "title", "technology." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        String tier = GsonHelper.getAsString(o, "tier", "mk0");
        String band = GsonHelper.getAsString(o, "depth_band", "A");
        List<Technology.Requirement> reqs = new ArrayList<>();
        if (o.has("scan_requirements"))
            for (JsonElement el : GsonHelper.getAsJsonArray(o, "scan_requirements"))
            {
                JsonObject r = GsonHelper.convertToJsonObject(el, "scan requirement");
                int count = GsonHelper.getAsInt(r, "count", 1);
                if (count < 1) throw new IllegalArgumentException("scan requirement count must be at least 1");
                reqs.add(new Technology.Requirement(ScanTargetRegistry.idOf(GsonHelper.getAsString(r, "target")), count));
            }
        List<ResourceLocation> pre = ids(o, "prerequisites");
        List<ResourceLocation> unlocks = ids(o, "unlocks");
        ResourceLocation icon = o.has("icon") ? ScanTargetRegistry.idOf(GsonHelper.getAsString(o, "icon")) : null;
        return new Technology(id, type, title, tier, band, List.copyOf(reqs), pre, unlocks, icon);
    }

    private static List<ResourceLocation> ids(JsonObject o, String key)
    {
        List<ResourceLocation> list = new ArrayList<>();
        if (o.has(key))
        {
            JsonArray arr = GsonHelper.getAsJsonArray(o, key);
            for (JsonElement el : arr) list.add(ScanTargetRegistry.idOf(GsonHelper.convertToString(el, key)));
        }
        return List.copyOf(list);
    }

    /**
     * Re-checks the loaded technologies against the loaded scan targets (called after either listener applied):
     * removes the ones with undefined references and those in or depending on a prerequisite cycle.
     */
    static void revalidate()
    {
        Map<ResourceLocation, Technology> work = new LinkedHashMap<>(raw);
        boolean changed = true;
        while (changed)
        {
            changed = false;
            for (Technology t : List.copyOf(work.values()))
            {
                String problem = null;
                for (Technology.Requirement r : t.requirements())
                    if (!ScanTargetRegistry.exists(r.target())) problem = "undefined scan target " + r.target();
                for (ResourceLocation p : t.prerequisites())
                {
                    if (p.equals(t.id())) problem = "lists itself as a prerequisite";
                    else if (!work.containsKey(p)) problem = "undefined prerequisite " + p;
                }
                if (problem == null && cyclic(t, work)) problem = "prerequisite cycle";
                if (problem != null)
                {
                    LOGGER.error("Technology {} ignored: {}", t.id(), problem);
                    work.remove(t.id());
                    changed = true;
                }
            }
        }
        valid = Map.copyOf(work);
        ordered = work.values().stream().sorted(Comparator.comparing((Technology t) -> t.id().toString())).toList();
        Map<ResourceLocation, List<Technology>> keys = new LinkedHashMap<>();
        for (Technology t : ordered) for (ResourceLocation k : t.unlocks()) keys.computeIfAbsent(k, x -> new ArrayList<>()).add(t);
        gatedKeys = keys;
        LOGGER.info("{} technologies valid", ordered.size());
    }

    /** True when the technology can reach itself through its prerequisites. */
    private static boolean cyclic(Technology start, Map<ResourceLocation, Technology> all)
    {
        Set<ResourceLocation> seen = new HashSet<>();
        List<ResourceLocation> stack = new ArrayList<>(start.prerequisites());
        while (!stack.isEmpty())
        {
            ResourceLocation next = stack.remove(stack.size() - 1);
            if (next.equals(start.id())) return true;
            if (!seen.add(next)) continue;
            Technology t = all.get(next);
            if (t != null) stack.addAll(t.prerequisites());
        }
        return false;
    }

    public static Optional<Technology> get(ResourceLocation id)
    {
        return Optional.ofNullable(valid.get(id));
    }

    public static boolean exists(ResourceLocation id)
    {
        return valid.containsKey(id);
    }

    /** Every valid technology, sorted by id. */
    public static List<Technology> all()
    {
        return ordered;
    }

    /** The key is gated: some valid technology lists it in {@code unlocks} (keys nobody lists are free). */
    public static boolean isGated(ResourceLocation key)
    {
        return gatedKeys.containsKey(key);
    }

    /** The first technology (by id) that unlocks the key, if the key is gated. */
    public static Optional<Technology> gatingTech(ResourceLocation key)
    {
        List<Technology> list = gatedKeys.get(key);
        return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** Valid technologies whose {@code unlocks} contain the key. */
    public static List<Technology> granting(ResourceLocation key)
    {
        List<Technology> out = new ArrayList<>();
        for (Technology t : ordered) if (t.unlocks().contains(key)) out.add(t);
        return out;
    }
}
