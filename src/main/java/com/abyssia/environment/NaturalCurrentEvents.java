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
        Long salt = NaturalCurrents.saltFor(player.serverLevel());
        AbyssiaNetwork.sendTo(player, new NaturalCurrentSaltPacket(salt != null, salt == null ? 0L : salt,
                Config.NATURAL_CURRENT_CHANCE.get(), Config.NATURAL_CURRENT_MAX_SPEED.get()));
    }
}
