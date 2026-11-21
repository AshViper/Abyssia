package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** The exosuit's arm: mines like an iron pickaxe, hits +6 damage and double knockback (common events, both sides). */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class ExosuitArm
{
    private ExosuitArm() {}

    private static boolean inSuit(Entity e)
    {
        return e != null && e.getVehicle() instanceof Exosuit;
    }

    /** absolute speed: 6 (iron pickaxe) on blocks it is right for, else 1 (the pre-event penalties are replaced) */
    @SubscribeEvent
    public static void breakSpeed(PlayerEvent.BreakSpeed event)
    {
        if (!inSuit(event.getEntity())) return;
        boolean right = event.getState().is(BlockTags.MINEABLE_WITH_PICKAXE) && !event.getState().is(Tiers.IRON.getIncorrectBlocksForDrops());
        event.setNewSpeed(right ? 6.0f : 1.0f);
    }

    @SubscribeEvent
    public static void harvest(PlayerEvent.HarvestCheck event)
    {
        if (inSuit(event.getEntity()) && !event.getTargetBlock().is(Tiers.IRON.getIncorrectBlocksForDrops())) event.setCanHarvest(true);
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent event)
    {
        if (inSuit(event.getSource().getDirectEntity())) event.setAmount(event.getAmount() + 6.0f);
    }

    @SubscribeEvent
    public static void knockback(LivingKnockBackEvent event)
    {
        if (inSuit(event.getEntity().getLastAttacker())) event.setStrength(event.getStrength() * 2.0f);
    }
}
