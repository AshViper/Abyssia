package com.abyssia.research;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: a technology was just unlocked (toast). The state itself arrives in the {@link ResearchDeltaPayload}. */
public record TechUnlockedPayload(ResourceLocation technology) implements CustomPacketPayload
{
    public static final Type<TechUnlockedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "tech_unlocked"));
    public static final StreamCodec<FriendlyByteBuf, TechUnlockedPayload> STREAM_CODEC = StreamCodec.ofMember(TechUnlockedPayload::encode, TechUnlockedPayload::decode);

    @Override
    public Type<TechUnlockedPayload> type()
    {
        return TYPE;
    }

    public static void encode(TechUnlockedPayload msg, FriendlyByteBuf buf)
    {
        buf.writeResourceLocation(msg.technology);
    }

    public static TechUnlockedPayload decode(FriendlyByteBuf buf)
    {
        return new TechUnlockedPayload(buf.readResourceLocation());
    }

    public static void handle(TechUnlockedPayload msg, IPayloadContext ctx)
    {
        ClientResearch.onUnlocked(msg.technology);
    }
}
