package com.abyssia.environment;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.network.AbyssiaNetwork;
import com.abyssia.network.NaturalCurrentSaltPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Tells each joining client where the natural currents are, so its particles and its own player's drift match the server. */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NaturalCurrentEvents
{
    private NaturalCurrentEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long seed = player.serverLevel().getSeed();
        // The salt goes out whenever either system is on: CU01 streams use it too.
        AbyssiaNetwork.sendTo(player, NaturalCurrentSaltPacket.of(Config.NATURAL_CURRENTS.get(), NaturalCurrents.saltOf(seed),
                Config.NATURAL_CURRENT_CHANCE.get(), Config.NATURAL_CURRENT_MAX_SPEED.get(),
                Config.STREAMS_ENABLED.get() ? CurrentStreams.serverSettings(seed) : null));
    }
}
