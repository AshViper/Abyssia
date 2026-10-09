package com.abyssia.research;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: a technology was just unlocked (toast). The state itself arrives in the {@link ResearchDeltaPayload}. */
public record TechUnlockedPayload(ResourceLocation technology)
{
    public static void encode(TechUnlockedPayload msg, FriendlyByteBuf buf)
    {
        buf.writeResourceLocation(msg.technology);
    }

    public static TechUnlockedPayload decode(FriendlyByteBuf buf)
    {
        return new TechUnlockedPayload(buf.readResourceLocation());
    }

    public static void handle(TechUnlockedPayload msg, Supplier<NetworkEvent.Context> ctx)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientResearch.onUnlocked(msg.technology));
    }
}
