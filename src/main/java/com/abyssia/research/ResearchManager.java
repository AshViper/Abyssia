package com.abyssia.research;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.network.AbyssiaNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Static API of the scan research system (AB05). Server side owns the state ({@link ResearchData}); on a client the
 * query methods read {@link ClientResearch} (display only). Creative players and (config {@code research.op_bypass}) operators
 * pass every unlock check.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class ResearchManager
{
    /** The scan target the old wreck progress (NBT {@code abyssia_wrecks}) is migrated into. */
    public static final ResourceLocation WRECK_TARGET = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "scan_target/wreck_core");
    private static final String LEGACY_WRECKS = "abyssia_wrecks";

    private ResearchManager() {}

    // ---- queries ----

    /** Creative, or an operator when the config allows it. */
    public static boolean bypass(Player player)
    {
        if (player.getAbilities().instabuild) return true;
        return player instanceof ServerPlayer sp && Config.RESEARCH_OP_BYPASS.get() && sp.hasPermissions(2);
    }

    public static boolean isUnlocked(Player player, ResourceLocation technology)
    {
        if (bypass(player)) return true;
        if (player.level().isClientSide) return ClientResearch.isUnlocked(technology);
        return ResearchData.load(player).unlocked.contains(technology);
    }

    /** The target is in the player's database (scanned at least once). No bypass. */
    public static boolean isTargetScanned(Player player, ResourceLocation target)
    {
        if (player.level().isClientSide) return ClientResearch.isScanned(target);
        return ResearchData.load(player).scanned.contains(target);
    }

    /** True when an unlocked technology lists {@code abyssia:building/<buildingId>} (or the player bypasses). */
    public static boolean isBuildingUnlocked(Player player, String buildingId)
    {
        if (bypass(player)) return true;
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "building/" + buildingId);
        if (player.level().isClientSide)
        {
            for (Technology t : ClientResearch.technologies())
                if (t.unlocks().contains(key) && ClientResearch.isUnlocked(t.id())) return true;
            return false;
        }
        ResearchData data = ResearchData.load(player);
        for (Technology t : TechnologyRegistry.granting(key))
            if (data.unlocked.contains(t.id())) return true;
        return false;
    }

    /** {@code abyssia:<kind>/<path>}: the unlock key of a machine, building entry, recipe result or upgrade. */
    public static ResourceLocation key(String kind, String path)
    {
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, kind + "/" + path);
    }

    /** Some technology lists the key in its unlocks (client: from the synced definitions). Not affected by bypass. */
    public static boolean isKeyGated(Player player, ResourceLocation key)
    {
        return player.level().isClientSide ? ClientResearch.isKeyGated(key) : TechnologyRegistry.isGated(key);
    }

    /**
     * Data-driven gate: true when the key is not gated, when the player bypasses, or when a technology listing the key is
     * unlocked. Client and server use the same rule (the client copy is display only).
     */
    public static boolean isKeyUnlocked(Player player, ResourceLocation key)
    {
        if (bypass(player)) return true;
        if (player.level().isClientSide) return ClientResearch.isKeyUnlocked(key);
        List<Technology> granting = TechnologyRegistry.granting(key);
        if (granting.isEmpty()) return true;
        ResearchData data = ResearchData.load(player);
        for (Technology t : granting) if (data.unlocked.contains(t.id())) return true;
        return false;
    }

    /** The technology that still locks the key for the player (empty when the key is usable). */
    public static Optional<Technology> lockedBy(Player player, ResourceLocation key)
    {
        if (isKeyUnlocked(player, key)) return Optional.empty();
        return player.level().isClientSide ? ClientResearch.lockedBy(key) : TechnologyRegistry.gatingTech(key);
    }

    /** Discoveries any technology asks of the target (at least 1). */
    public static int neededFor(ResourceLocation target)
    {
        int need = 1;
        for (Technology t : TechnologyRegistry.all())
            for (Technology.Requirement r : t.requirements())
                if (r.target().equals(target)) need = Math.max(need, r.count());
        return need;
    }

    // ---- changes (server) ----

    /**
     * A completed scan: counts the discovery (once per key), registers the target, unlocks technologies whose conditions are
     * now met, and sends the delta and unlock toasts. An unknown target gives KNOWN with 0/0.
     */
    public static ScanResult scan(ServerPlayer player, ResourceLocation target, String discoveryKey)
    {
        if (!ScanTargetRegistry.exists(target)) return new ScanResult(ScanOutcome.KNOWN, 0, 0, List.of());
        ResearchData data = ResearchData.load(player);
        boolean wasScanned = data.scanned.contains(target);
        boolean added = data.addDiscovery(target, discoveryKey);
        List<ResourceLocation> newly = evaluate(data);
        data.save(player);
        if (added || !newly.isEmpty()) sendDelta(player, data, target, newly);
        ScanOutcome outcome = !added ? ScanOutcome.KNOWN : wasScanned ? ScanOutcome.FRAGMENT : ScanOutcome.NEW;
        return new ScanResult(outcome, data.count(target), neededFor(target), List.copyOf(newly));
    }

    /** Admin unlock of one technology (prerequisites and scans are not checked). False when unknown or already unlocked. */
    public static boolean unlock(ServerPlayer player, ResourceLocation technology)
    {
        if (!TechnologyRegistry.exists(technology)) return false;
        ResearchData data = ResearchData.load(player);
        if (!data.unlocked.add(technology)) return false;
        List<ResourceLocation> newly = new ArrayList<>();
        newly.add(technology);
        newly.addAll(evaluate(data));
        data.save(player);
        sendDelta(player, data, null, newly);
        return true;
    }

    /**
     * Admin reset. null: everything (the old-wreck migration is not repeated). A technology: it and the technologies that
     * depend on it lose their unlock and the scan progress of their requirements. Ends with a full sync.
     */
    public static void reset(ServerPlayer player, @Nullable ResourceLocation technology)
    {
        ResearchData data = ResearchData.load(player);
        if (technology == null)
        {
            ResearchData fresh = new ResearchData();
            fresh.legacyMigrated = true;
            data = fresh;
        }
        else
        {
            Set<ResourceLocation> gone = new HashSet<>();
            gone.add(technology);
            boolean grew = true;
            while (grew)
            {
                grew = false;
                for (Technology t : TechnologyRegistry.all())
                    if (!gone.contains(t.id()) && t.prerequisites().stream().anyMatch(gone::contains))
                    {
                        gone.add(t.id());
                        grew = true;
                    }
            }
            for (ResourceLocation id : gone)
            {
                data.unlocked.remove(id);
                Optional<Technology> t = TechnologyRegistry.get(id);
                if (t.isPresent())
                    for (Technology.Requirement r : t.get().requirements())
                    {
                        data.fragments.remove(r.target());
                        data.scanned.remove(r.target());
                    }
            }
        }
        data.save(player);
        syncFull(player);
    }

    /** Unlocks every technology whose scans and prerequisites are met (repeats for chains). Returns the new ones; does not save. */
    static List<ResourceLocation> evaluate(ResearchData data)
    {
        List<ResourceLocation> newly = new ArrayList<>();
        boolean changed = true;
        while (changed)
        {
            changed = false;
            for (Technology t : TechnologyRegistry.all())
            {
                if (data.unlocked.contains(t.id())) continue;
                boolean ok = true;
                for (ResourceLocation p : t.prerequisites()) if (!data.unlocked.contains(p)) ok = false;
                for (Technology.Requirement r : t.requirements()) if (data.count(r.target()) < r.count()) ok = false;
                if (ok)
                {
                    data.unlocked.add(t.id());
                    newly.add(t.id());
                    changed = true;
                }
            }
        }
        return newly;
    }

    // ---- sync ----

    /** Sends everything (definitions and this player's state). */
    public static void syncFull(ServerPlayer player)
    {
        ResearchData data = ResearchData.load(player);
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        for (ResourceLocation id : data.scanned) counts.put(id, data.count(id));
        AbyssiaNetwork.sendTo(player, new ResearchSyncPayload(TechnologyRegistry.all(), ScanTargetRegistry.all(), counts,
                new ArrayList<>(data.unlocked)));
    }

    private static void sendDelta(ServerPlayer player, ResearchData data, @Nullable ResourceLocation target, List<ResourceLocation> newly)
    {
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        if (target != null) counts.put(target, data.count(target));
        AbyssiaNetwork.sendTo(player, new ResearchDeltaPayload(counts, List.copyOf(newly)));
        for (ResourceLocation id : newly) AbyssiaNetwork.sendTo(player, new TechUnlockedPayload(id));
    }

    /** Migrates the old wreck data once, applies technologies the data now allows, and sends the full state. */
    private static void refresh(ServerPlayer player)
    {
        ResearchData data = ResearchData.load(player);
        if (!data.legacyMigrated)
        {
            CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            for (long pos : persisted.getLongArray(LEGACY_WRECKS)) data.addDiscovery(WRECK_TARGET, Long.toString(pos));
            data.legacyMigrated = true;
        }
        List<ResourceLocation> newly = evaluate(data);
        data.save(player);
        syncFull(player);
        for (ResourceLocation id : newly) AbyssiaNetwork.sendTo(player, new TechUnlockedPayload(id));
    }

    // ---- events ----

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) syncFull(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) syncFull(player);
    }

    /** Death (and the end return) makes a new player entity: carry the research state over. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event)
    {
        ResearchData.copy(event.getOriginal(), event.getEntity());
    }

    /** Datapack reload (and join): new definitions to the clients; unlocked technologies are kept. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event)
    {
        event.getRelevantPlayers().forEach(ResearchManager::refresh);
    }
}
