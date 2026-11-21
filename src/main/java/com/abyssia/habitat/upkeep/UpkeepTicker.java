package com.abyssia.habitat.upkeep;

import com.abyssia.Abyssia;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** BAS01: drives {@link HabitatUpkeep#tick} (wear, leak effects, quakes) once per server level tick. */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class UpkeepTicker
{
    private UpkeepTicker() {}

    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post event)
    {
        if (event.getLevel() instanceof ServerLevel level) HabitatUpkeep.get(level).tick(level);
    }
}
