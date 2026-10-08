package com.abyssia.worldgen.deposit;

import com.abyssia.Abyssia;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the deposits queued by worldgen threads (OreDepositManager#enqueue) on the server thread. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OreDepositFlusher
{
    private static final int PER_TICK = 64;

    private OreDepositFlusher() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END) OreDepositManager.flush(PER_TICK);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        OreDepositManager.flush(Integer.MAX_VALUE);
    }
}
