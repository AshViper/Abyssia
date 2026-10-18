package com.abyssia.advancement;

import com.abyssia.Abyssia;
import com.abyssia.research.ResearchData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Fire helpers for the custom triggers (server side) and the periodic dive tick. */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class AbyssiaCriteria
{
    private AbyssiaCriteria() {}

    public static void scan(@Nullable ServerPlayer player)
    {
        if (player == null) return;
        ModTriggers.SCAN.get().trigger(player, ResearchData.load(player).scanned);
    }

    public static void research(@Nullable ServerPlayer player)
    {
        if (player == null) return;
        ModTriggers.RESEARCH.get().trigger(player, ResearchData.load(player).unlocked);
    }

    public static void build(@Nullable ServerPlayer player, String entryId)
    {
        if (player != null) ModTriggers.BUILD.get().trigger(player, entryId);
    }

    public static void capture(@Nullable ServerPlayer player, ResourceLocation entityType)
    {
        if (player != null) ModTriggers.CAPTURE.get().trigger(player, entityType);
    }

    public static void aquarium(@Nullable ServerPlayer player, String species, int count, int speciesCount)
    {
        if (player != null) ModTriggers.AQUARIUM.get().trigger(player, species, count, speciesCount);
    }

    public static void event(@Nullable ServerPlayer player, String event, @Nullable String key)
    {
        if (player != null) ModTriggers.EVENT.get().trigger(player, event, key == null ? "" : key);
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event)
    {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0 && !player.isSpectator())
            ModTriggers.DIVE.get().trigger(player);
    }
}
