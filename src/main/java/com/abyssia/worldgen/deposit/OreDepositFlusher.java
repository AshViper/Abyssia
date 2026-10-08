package com.abyssia.worldgen.deposit;

import com.abyssia.Abyssia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Registers the deposits queued by worldgen threads (OreDepositManager#enqueue) on the server thread. */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class OreDepositFlusher
{
    private static final int PER_TICK = 64;

    private OreDepositFlusher() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        OreDepositManager.flush(PER_TICK);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        OreDepositManager.flush(Integer.MAX_VALUE);
    }
}
