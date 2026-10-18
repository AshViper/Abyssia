package com.abyssia.advancement;

import com.abyssia.Abyssia;
import com.abyssia.research.ResearchData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Fire helpers for the custom triggers (server side) and the periodic dive tick. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AbyssiaCriteria
{
    private AbyssiaCriteria() {}

    public static void scan(@Nullable ServerPlayer player)
    {
        if (player == null) return;
        ModTriggers.SCAN.trigger(player, ResearchData.load(player).scanned);
    }

    public static void research(@Nullable ServerPlayer player)
    {
        if (player == null) return;
        ModTriggers.RESEARCH.trigger(player, ResearchData.load(player).unlocked);
    }

    public static void build(@Nullable ServerPlayer player, String entryId)
    {
        if (player != null) ModTriggers.BUILD.trigger(player, entryId);
    }

    public static void capture(@Nullable ServerPlayer player, ResourceLocation entityType)
    {
        if (player != null) ModTriggers.CAPTURE.trigger(player, entityType);
    }

    public static void aquarium(@Nullable ServerPlayer player, String species, int count, int speciesCount)
    {
        if (player != null) ModTriggers.AQUARIUM.trigger(player, species, count, speciesCount);
    }

    public static void event(@Nullable ServerPlayer player, String event, @Nullable String key)
    {
        if (player != null) ModTriggers.EVENT.trigger(player, event, key == null ? "" : key);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END && event.side.isServer() && event.player instanceof ServerPlayer player
                && player.tickCount % 20 == 0 && !player.isSpectator())
            ModTriggers.DIVE.trigger(player);
    }
}
