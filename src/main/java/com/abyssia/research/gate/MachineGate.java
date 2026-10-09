package com.abyssia.research.gate;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.research.ResearchManager;
import com.abyssia.research.Technology;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Optional;

/**
 * AB05 machine gating. A block with registry path {@code P} (namespace abyssia) is gated when some technology lists
 * {@code abyssia:machine/P} in its {@code unlocks}; until the player has unlocked such a technology the block can be neither
 * placed nor used (opened). Creative players and {@link ResearchManager#bypass} are exempt; non-player entities (dispensers,
 * mobs) are not affected. Machines a player placed before the technology existed (or before it was reset) stay in the world
 * but cannot be opened until the technology is unlocked. Switched off by config {@code research.gate_machines}.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class MachineGate
{
    private MachineGate() {}

    /** The technology that locks this block for the player, empty when the block is free (or the gate is off / bypassed). */
    public static Optional<Technology> lockedBy(Player player, Block block)
    {
        if (!Config.RESEARCH_GATE_MACHINES.get()) return Optional.empty();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (!Abyssia.MODID.equals(id.getNamespace())) return Optional.empty();
        return ResearchManager.lockedBy(player, ResearchManager.key("machine", id.getPath()));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getEntity() instanceof Player player)) return;
        Optional<Technology> tech = lockedBy(player, event.getPlacedBlock().getBlock());
        if (tech.isEmpty()) return;
        event.setCanceled(true);
        notify(player, tech.get());
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event)
    {
        Player player = event.getEntity();
        // like vanilla: sneaking with something in hand does not use the block (the held item may still be placed elsewhere)
        boolean holding = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        if (player.isSecondaryUseActive() && holding) return;
        Optional<Technology> tech = lockedBy(player, event.getLevel().getBlockState(event.getPos()).getBlock());
        if (tech.isEmpty()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getHand() == InteractionHand.MAIN_HAND) notify(player, tech.get());
    }

    /** Action-bar message, server side only (the client cancels silently so it does not show twice). */
    private static void notify(Player player, Technology tech)
    {
        if (player instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("message.abyssia.research.machine_locked", Component.translatable(tech.titleKey())), true);
    }
}
