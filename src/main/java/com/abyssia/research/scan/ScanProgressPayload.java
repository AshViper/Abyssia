package com.abyssia.research.scan;

import com.abyssia.Abyssia;
import com.abyssia.research.client.ScanHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: scan progress of the target under the crosshair (HUD only). state = one of the byte constants below. */
public record ScanProgressPayload(ResourceLocation targetId, float progress, byte state) implements CustomPacketPayload
{
    public static final byte SCANNING = 0, COMPLETE_NEW = 1, COMPLETE_FRAGMENT = 2, COMPLETE_KNOWN = 3, ABORTED = 4, NO_TARGET = 5;

    public static final Type<ScanProgressPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "scan_progress"));
    public static final StreamCodec<FriendlyByteBuf, ScanProgressPayload> STREAM_CODEC = StreamCodec.ofMember(ScanProgressPayload::encode, ScanProgressPayload::decode);

    @Override
    public Type<ScanProgressPayload> type()
    {
        return TYPE;
    }

    public static void encode(ScanProgressPayload msg, FriendlyByteBuf buf)
    {
        buf.writeResourceLocation(msg.targetId);
        buf.writeFloat(msg.progress);
        buf.writeByte(msg.state);
    }

    public static ScanProgressPayload decode(FriendlyByteBuf buf)
    {
        return new ScanProgressPayload(buf.readResourceLocation(), buf.readFloat(), buf.readByte());
    }

    public static void handle(ScanProgressPayload msg, IPayloadContext ctx)
    {
        // playToClient handlers run on the client main thread.
        ScanHud.onProgress(msg);
    }
}
