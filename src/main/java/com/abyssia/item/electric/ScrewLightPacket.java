package com.abyssia.item.electric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: G pressed while holding a propulsion screw; flips the light on the screw (main hand first, else offhand). */
public record ScrewLightPacket()
{
    public static void encode(ScrewLightPacket msg, FriendlyByteBuf buf) {}

    public static ScrewLightPacket decode(FriendlyByteBuf buf) { return new ScrewLightPacket(); }

    public static void handle(ScrewLightPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) apply(player);
    }

    /** The server-side effect, also called by the devtest. */
    public static void apply(ServerPlayer player)
    {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof PropulsionScrewItem)) stack = player.getItemInHand(InteractionHand.OFF_HAND);
        if (stack.getItem() instanceof PropulsionScrewItem) PropulsionScrewItem.toggleLight(player, stack);
    }
}
