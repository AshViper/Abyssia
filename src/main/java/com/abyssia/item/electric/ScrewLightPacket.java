package com.abyssia.item.electric;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: G pressed while holding a propulsion screw; flips the light on the screw (main hand first, else offhand). */
public record ScrewLightPacket() implements CustomPacketPayload
{
    public static final Type<ScrewLightPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "screw_light"));
    public static final StreamCodec<FriendlyByteBuf, ScrewLightPacket> STREAM_CODEC = StreamCodec.of((buf, msg) -> {}, buf -> new ScrewLightPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScrewLightPacket msg, IPayloadContext context)
    {
        context.enqueueWork(() ->
        {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!(stack.getItem() instanceof PropulsionScrewItem)) stack = player.getItemInHand(InteractionHand.OFF_HAND);
            if (stack.getItem() instanceof PropulsionScrewItem) PropulsionScrewItem.toggleLight(player, stack);
        });
    }
}
