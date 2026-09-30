package com.abyssia.network;

import com.abyssia.client.DeepOceanClientEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells the client the server's transition boundaries so fades and fog line up with the actual teleport. */
public record DepthSettingsPacket(boolean enabled, int transitionY, int returnY)
{
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBoolean(enabled);
        buf.writeVarInt(transitionY);
        buf.writeVarInt(returnY);
    }

    public static DepthSettingsPacket decode(FriendlyByteBuf buf)
    {
        return new DepthSettingsPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DeepOceanClientEffects.applyServerSettings(this));
    }
}
