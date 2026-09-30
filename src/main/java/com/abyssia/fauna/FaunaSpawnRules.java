package com.abyssia.fauna;

import com.abyssia.Abyssia;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads data/&lt;namespace&gt;/fauna_spawns/*.json; reloaded with /reload, so spawn tuning needs no restart. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class FaunaSpawnRules extends SimpleJsonResourceReloadListener
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile List<FaunaSpawnRule> rules = List.of();
    private static volatile Set<EntityType<?>> types = Set.of();
    private static volatile Set<EntityType<?>> mobile = Set.of();

    private FaunaSpawnRules()
    {
        super(new Gson(), "fauna_spawns");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event)
    {
        event.addListener(new FaunaSpawnRules());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler)
    {
        List<FaunaSpawnRule> loaded = new ArrayList<>();
        files.forEach((id, json) -> FaunaSpawnRule.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOGGER.error("Fauna spawn rule {}: {}", id, error))
                .ifPresent(loaded::add));
        Set<EntityType<?>> loadedTypes = new HashSet<>();
        Set<EntityType<?>> loadedMobile = new HashSet<>();
        loaded.forEach(r -> {
            loadedTypes.add(r.entity());
            if (!r.role().equals("environmental") && !r.anchoredToVents()) loadedMobile.add(r.entity());
        });
        rules = List.copyOf(loaded);
        types = Set.copyOf(loadedTypes);
        mobile = Set.copyOf(loadedMobile);
        LOGGER.info("Loaded {} fauna spawn rules", loaded.size());
    }

    public static List<FaunaSpawnRule> rules()
    {
        return rules;
    }

    /** Animals the fauna spawner manages. */
    public static boolean isFauna(EntityType<?> type)
    {
        return types.contains(type);
    }

    /**
     * Fauna that counts toward the per-player limit: not environmental animals (tubeworm colonies are scenery) nor the
     * vent community (vents are dense oases, bounded by each species' cap around its vent), so neither crowds out
     * the sparse midwater life.
     */
    public static boolean countsTowardLimit(EntityType<?> type)
    {
        return mobile.contains(type);
    }
}
