package com.abyssia.habitat.relay;

import com.abyssia.Abyssia;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** WR01: link / transfer tick per level (end of tick), client sync on login / dimension change / respawn. */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class RelayEvents
{
    private RelayEvents() {}

    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        RelayNetwork net = RelayNetwork.get(level);
        if (!net.isEmpty()) net.tick(level);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) RelayNetwork.sendTo(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) RelayNetwork.sendTo(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) RelayNetwork.sendTo(player);
    }
}
