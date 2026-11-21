package com.abyssia.fauna;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModGiants;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GNT01: loads data/&lt;namespace&gt;/giant_spawns/*.json (reloaded with /reload).
 * <pre>
 * species  giant species id (ModGiants)
 * min_y    lowest world Y of the spawn point
 * max_y    highest world Y of the spawn point
 * weight   relative weight among the species that fit
 * cap      optional {count, radius} (same record as fauna_spawns): no more of this species while count are within radius (default 1 / 256)
 * </pre>
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class GiantSpawnRules extends SimpleJsonResourceReloadListener
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile List<Rule> rules = List.of();

    public record Rule(String species, int minY, int maxY, double weight, FaunaSpawnRule.Cap cap)
    {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("species").forGetter(Rule::species),
                Codec.INT.fieldOf("min_y").forGetter(Rule::minY),
                Codec.INT.fieldOf("max_y").forGetter(Rule::maxY),
                Codec.doubleRange(0, 10000).fieldOf("weight").forGetter(Rule::weight),
                FaunaSpawnRule.Cap.CODEC.lenientOptionalFieldOf("cap", new FaunaSpawnRule.Cap(1, 256)).forGetter(Rule::cap)
        ).apply(i, Rule::new));
    }

    private GiantSpawnRules()
    {
        super(new Gson(), "giant_spawns");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event)
    {
        event.addListener(new GiantSpawnRules());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler)
    {
        List<Rule> loaded = new ArrayList<>();
        files.forEach((id, json) -> Rule.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOGGER.error("Giant spawn rule {}: {}", id, error))
                .ifPresent(r ->
                {
                    if (ModGiants.byId(r.species()) == null) LOGGER.error("Giant spawn rule {}: unknown species {}", id, r.species());
                    else loaded.add(r);
                }));
        rules = List.copyOf(loaded);
        LOGGER.info("Loaded {} giant spawn rules", loaded.size());
    }

    public static List<Rule> rules()
    {
        return rules;
    }
}
