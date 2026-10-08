package com.abyssia.habitat.power;

import com.abyssia.Abyssia;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECO03: what happens to a player inside a habitat module whose oxygen reserve ({@link HabitatAir}) is empty: the air
 * bar runs down like under water and, once empty, drowning damage. Every module draws FE for life support
 * ({@link HabitatPower#LIFE_SUPPORT_FE}); with the base unpowered the reserve lasts 5 minutes.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HabitatLifeSupport
{
    /** air value the stale-air effect keeps for each affected player (vanilla would refill it out of water) */
    private static final Map<UUID, Integer> STALE = new ConcurrentHashMap<>();
    /** how fast the air bar drops in stale air (ticks of air per tick; vanilla water drain is 1) */
    private static final int DRAIN = 2;
    private static final int WARN_BELOW = 1_200;

    private HabitatLifeSupport() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (!(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) return;
        if (player.isCreative() || player.isSpectator())
        {
            STALE.remove(player.getUUID());
            return;
        }
        if (player.tickCount % 5 == 0)
        {
            int module = HabitatBases.get(level).moduleAt(player.blockPosition().above());
            int oxygen = module < 0 ? HabitatAir.MAX : HabitatAir.get(level).oxygen(module);
            if (oxygen <= 0) STALE.putIfAbsent(player.getUUID(), player.getAirSupply());
            else STALE.remove(player.getUUID());
            if (module >= 0 && oxygen < WARN_BELOW && player.tickCount % 100 == 0 && !HabitatPower.isPowered(level, module))
                sp.displayClientMessage(Component.translatable(oxygen <= 0 ? "message." + Abyssia.MODID + ".habitat.oxygen_out"
                        : "message." + Abyssia.MODID + ".habitat.oxygen_low", oxygen / 20), true);
        }
        Integer air = STALE.get(player.getUUID());
        if (air == null) return;
        air = Math.min(air, player.getAirSupply()) - DRAIN;
        if (air <= -20)
        {
            air = 0;
            player.hurt(player.damageSources().drown(), 2.0F);
        }
        STALE.put(player.getUUID(), air);
        player.setAirSupply(air);
    }
}
