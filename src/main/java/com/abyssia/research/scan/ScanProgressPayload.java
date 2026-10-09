package com.abyssia.research.scan;

import com.abyssia.research.client.ScanHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: scan progress of the target under the crosshair (HUD only). state = one of the byte constants below. */
public record ScanProgressPayload(ResourceLocation targetId, float progress, byte state)
{
    public static final byte SCANNING = 0, COMPLETE_NEW = 1, COMPLETE_FRAGMENT = 2, COMPLETE_KNOWN = 3, ABORTED = 4, NO_TARGET = 5;

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

    public static void handle(ScanProgressPayload msg, Supplier<NetworkEvent.Context> ctx)
    {
        // Registered with consumerMainThread: already on the client thread.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ScanHud.onProgress(msg));
    }
}
