package com.abyssia.environment;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.network.AbyssiaNetwork;
import com.abyssia.network.NaturalCurrentSaltPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tells each joining client where the natural currents and CU01 current streams are, so its particles and its own player's drift match the server. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class NaturalCurrentEvents
{
    private NaturalCurrentEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean natural = Config.NATURAL_CURRENTS.get(), streams = Config.CURRENT_STREAMS.get();
        long salt = natural || streams ? NaturalCurrents.saltOf(player.serverLevel().getSeed()) : 0L;
        AbyssiaNetwork.sendTo(player, new NaturalCurrentSaltPacket(natural, salt,
                Config.NATURAL_CURRENT_CHANCE.get(), Config.NATURAL_CURRENT_MAX_SPEED.get(),
                streams ? CurrentStreams.Params.fromConfig() : null));
    }
}
