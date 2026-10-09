package com.abyssia.research;

import com.abyssia.Abyssia;
import com.abyssia.worldgen.deposit.OreDeposit;
import com.abyssia.worldgen.deposit.OreDepositManager;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads data/&lt;namespace&gt;/scan_targets/*.json (reloaded with /reload):
 * <pre>{"id":"abyssia:scan_target/wreck_core","category":"wreck","name_key":"...","scan_seconds":6,"range":16,
 *  "match":{"block":"abyssia:wreck_core"},"fragment_key":"position"}</pre>
 * {@code match} has exactly one of block / block_tag / entity / deposit_mineral / structure. {@code id} defaults to
 * {@code <namespace>:scan_target/<file path>}. Bad or duplicate files are logged and skipped.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class ScanTargetRegistry extends SimpleJsonResourceReloadListener
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<ResourceLocation, ScanTarget> targets = Map.of();
    private static volatile List<ScanTarget> ordered = List.of();

    private ScanTargetRegistry()
    {
        super(new Gson(), "scan_targets");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event)
    {
        event.addListener(new ScanTargetRegistry());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler)
    {
        Map<ResourceLocation, ScanTarget> loaded = new LinkedHashMap<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            try
            {
                ScanTarget t = parse(e.getKey(), e.getValue());
                if (loaded.containsKey(t.id())) LOGGER.error("Scan target {} ({}): duplicate id, skipped", t.id(), e.getKey());
                else loaded.put(t.id(), t);
            }
            catch (RuntimeException ex)
            {
                LOGGER.error("Scan target {}: {}", e.getKey(), ex.getMessage());
            }
        });
        targets = Map.copyOf(loaded);
        ordered = loaded.values().stream().sorted(Comparator.comparing(t -> t.id().toString())).toList();
        LOGGER.info("Loaded {} scan targets", loaded.size());
        TechnologyRegistry.revalidate();
    }

    private static ScanTarget parse(ResourceLocation file, JsonElement json)
    {
        JsonObject o = GsonHelper.convertToJsonObject(json, "scan target");
        ResourceLocation id = o.has("id") ? idOf(GsonHelper.getAsString(o, "id"))
                : ResourceLocation.fromNamespaceAndPath(file.getNamespace(), "scan_target/" + file.getPath());
        String category = GsonHelper.getAsString(o, "category", "misc");
        String nameKey = GsonHelper.getAsString(o, "name_key", "scan_target." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        float seconds = GsonHelper.getAsFloat(o, "scan_seconds", 5f);
        double range = GsonHelper.getAsDouble(o, "range", 16.0);
        if (seconds <= 0 || range <= 0) throw new IllegalArgumentException("scan_seconds and range must be positive");
        JsonObject m = GsonHelper.getAsJsonObject(o, "match");
        ScanTarget.Match match = null;
        for (String key : m.keySet())
        {
            ScanTarget.MatchType type = ScanTarget.MatchType.byJson(key);
            if (type == null) throw new IllegalArgumentException("unknown match rule '" + key + "'");
            if (match != null) throw new IllegalArgumentException("match needs exactly one rule");
            match = new ScanTarget.Match(type, idOf(GsonHelper.getAsString(m, key)));
        }
        if (match == null) throw new IllegalArgumentException("match needs exactly one rule");
        switch (match.type())
        {
            case BLOCK, DEPOSIT_MINERAL ->
            {
                if (!BuiltInRegistries.BLOCK.containsKey(match.value())) throw new IllegalArgumentException("unknown block " + match.value());
            }
            case ENTITY ->
            {
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(match.value())) throw new IllegalArgumentException("unknown entity " + match.value());
            }
            default -> {}
        }
        String fk = GsonHelper.getAsString(o, "fragment_key", "type");
        ScanTarget.FragmentKey fragmentKey;
        if (fk.equals("position")) fragmentKey = ScanTarget.FragmentKey.POSITION;
        else if (fk.equals("type")) fragmentKey = ScanTarget.FragmentKey.TYPE;
        else throw new IllegalArgumentException("fragment_key must be position or type");
        return new ScanTarget(id, category, nameKey, seconds, range, match, fragmentKey);
    }

    static ResourceLocation idOf(String s)
    {
        ResourceLocation id = ResourceLocation.tryParse(s);
        if (id == null) throw new IllegalArgumentException("bad id '" + s + "'");
        return id;
    }

    public static Optional<ScanTarget> get(ResourceLocation id)
    {
        return Optional.ofNullable(targets.get(id));
    }

    /** Every loaded target, sorted by id. */
    public static List<ScanTarget> all()
    {
        return ordered;
    }

    public static boolean exists(ResourceLocation id)
    {
        return targets.containsKey(id);
    }

    /** The target the hit result rests on (first match by id order), or empty. Server side. */
    public static Optional<ScanTarget> find(ServerPlayer player, HitResult hit)
    {
        List<ScanTarget> list = ordered;
        if (hit == null || list.isEmpty()) return Optional.empty();
        ServerLevel level = player.serverLevel();
        if (hit instanceof EntityHitResult eh)
        {
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(eh.getEntity().getType());
            for (ScanTarget t : list)
                if (t.match().type() == ScanTarget.MatchType.ENTITY && t.match().value().equals(type)) return Optional.of(t);
            return Optional.empty();
        }
        if (!(hit instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return Optional.empty();
        BlockPos pos = bh.getBlockPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        OreDeposit deposit = null;
        boolean depositLooked = false;
        for (ScanTarget t : list)
        {
            ResourceLocation v = t.match().value();
            switch (t.match().type())
            {
                case BLOCK ->
                {
                    if (v.equals(block)) return Optional.of(t);
                }
                case BLOCK_TAG ->
                {
                    if (state.is(TagKey.create(Registries.BLOCK, v))) return Optional.of(t);
                }
                case DEPOSIT_MINERAL ->
                {
                    if (!depositLooked)
                    {
                        deposit = depositAt(level, bh);
                        depositLooked = true;
                    }
                    if (deposit != null && deposit.mineralId().equals(v)) return Optional.of(t);
                }
                case STRUCTURE ->
                {
                    if (inStructure(level, pos, v)) return Optional.of(t);
                }
                default -> {}
            }
        }
        return Optional.empty();
    }

    /** The deposit whose region contains the hit point (the lidar scanner's own rule), or null. */
    private static OreDeposit depositAt(ServerLevel level, BlockHitResult hit)
    {
        return OreDepositManager.findNearby(level, hit.getBlockPos(), 2).stream()
                .filter(d -> d.bounds().inflate(1.0).contains(hit.getLocation()))
                .min(Comparator.comparingDouble(d -> d.center().distSqr(hit.getBlockPos()))).orElse(null);
    }

    private static boolean inStructure(ServerLevel level, BlockPos pos, ResourceLocation id)
    {
        try
        {
            Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
            return structure != null && level.structureManager().getStructureAt(pos, structure).isValid();
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }

    /**
     * What makes this scan "the same discovery": the target id for fragment_key "type", the block position (as
     * {@code BlockPos.asLong}) for "position". An entity hit uses its UUID. Stable across sessions and the legacy wreck data.
     */
    public static String discoveryKey(ScanTarget target, HitResult hit)
    {
        if (target.fragmentKey() == ScanTarget.FragmentKey.TYPE) return target.id().toString();
        if (hit instanceof EntityHitResult eh) return eh.getEntity().getUUID().toString();
        if (hit instanceof BlockHitResult bh) return Long.toString(bh.getBlockPos().asLong());
        return target.id().toString();
    }

    /** As {@link #discoveryKey(ScanTarget, HitResult)}; a deposit target with "position" uses the deposit id instead of the block hit. */
    public static String discoveryKey(ServerPlayer player, ScanTarget target, HitResult hit)
    {
        if (target.fragmentKey() == ScanTarget.FragmentKey.POSITION && target.match().type() == ScanTarget.MatchType.DEPOSIT_MINERAL
                && hit instanceof BlockHitResult bh)
        {
            OreDeposit d = depositAt(player.serverLevel(), bh);
            if (d != null) return d.depositId().toString();
        }
        return discoveryKey(target, hit);
    }
}
