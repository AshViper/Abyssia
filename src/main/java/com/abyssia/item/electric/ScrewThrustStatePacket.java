package com.abyssia.item.electric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C to the players tracking a thruster: that entity is / is no longer thrusting (third-person pose and lay-flat). */
public record ScrewThrustStatePacket(int entityId, boolean on)
{
    public static void encode(ScrewThrustStatePacket msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.on);
    }

    public static ScrewThrustStatePacket decode(FriendlyByteBuf buf) { return new ScrewThrustStatePacket(buf.readVarInt(), buf.readBoolean()); }

    public static void handle(ScrewThrustStatePacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        PropulsionScrewClient.setRemoteThrusting(msg.entityId, msg.on);
    }
}
