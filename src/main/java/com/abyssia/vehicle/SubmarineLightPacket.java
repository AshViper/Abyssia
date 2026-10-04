package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S (SUB02) from the pilot of a submarine: G flips the headlights; Ctrl while docked releases the dock (the
 * server owns the dock state, and the vanilla input packet carries no sprint key). Ignored unless the sender is the
 * submarine's controlling passenger.
 */
public record SubmarineLightPacket(Action action) implements CustomPacketPayload
{
    public enum Action { TOGGLE_LIGHTS, UNDOCK }

    public static final Type<SubmarineLightPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "submarine_light"));
    public static final StreamCodec<FriendlyByteBuf, SubmarineLightPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeEnum(msg.action), buf -> new SubmarineLightPacket(buf.readEnum(Action.class)));

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(SubmarineLightPacket msg, IPayloadContext context)
    {
        context.enqueueWork(() ->
        {
            if (!(context.player() instanceof ServerPlayer player) || !(player.getVehicle() instanceof Submarine sub)
                    || sub.getControllingPassenger() != player) return;
            switch (msg.action)
            {
                case TOGGLE_LIGHTS -> sub.toggleLights();
                case UNDOCK -> sub.undock();
            }
        });
    }
}
