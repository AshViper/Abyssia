package com.abyssia.item.electric;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C to the players tracking a thruster: that entity is / is no longer thrusting (third-person pose and lay-flat). */
public record ScrewThrustStatePacket(int entityId, boolean on) implements CustomPacketPayload
{
    public static final Type<ScrewThrustStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "screw_thrust_state"));
    public static final StreamCodec<FriendlyByteBuf, ScrewThrustStatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> { buf.writeVarInt(msg.entityId); buf.writeBoolean(msg.on); },
            buf -> new ScrewThrustStatePacket(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScrewThrustStatePacket msg, IPayloadContext context)
    {
        PropulsionScrewClient.setRemoteThrusting(msg.entityId, msg.on);
    }
}
