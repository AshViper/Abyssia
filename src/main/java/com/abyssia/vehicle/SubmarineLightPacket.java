package com.abyssia.vehicle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S (SUB02) from the pilot of a submarine: G flips the headlights; Ctrl while docked releases the dock (the
 * server owns the dock state, and the vanilla input packet carries no sprint key). Ignored unless the sender is the
 * submarine's controlling passenger.
 */
public record SubmarineLightPacket(Action action)
{
    public enum Action { TOGGLE_LIGHTS, UNDOCK }

    public static void encode(SubmarineLightPacket msg, FriendlyByteBuf buf)
    {
        buf.writeEnum(msg.action);
    }

    public static SubmarineLightPacket decode(FriendlyByteBuf buf)
    {
        return new SubmarineLightPacket(buf.readEnum(Action.class));
    }

    public static void handle(SubmarineLightPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !(player.getVehicle() instanceof Submarine sub) || sub.getControllingPassenger() != player) return;
        switch (msg.action)
        {
            case TOGGLE_LIGHTS -> sub.toggleLights();
            case UNDOCK -> sub.undock();
        }
    }
}
