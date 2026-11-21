package com.abyssia.habitat.upkeep;

import com.abyssia.Abyssia;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** BAS01: drives {@link HabitatUpkeep#tick} (wear, leak effects, quakes) once per server level tick. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UpkeepTicker
{
    private UpkeepTicker() {}

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !event.side.isServer()) return;
        if (event.level instanceof ServerLevel level) HabitatUpkeep.get(level).tick(level);
    }
}
