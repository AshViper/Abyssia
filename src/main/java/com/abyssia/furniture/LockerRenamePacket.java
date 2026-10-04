package com.abyssia.furniture;

import com.abyssia.Abyssia;
import net.minecraft.util.StringUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: the large locker screen's name field. Applies to the locker the sender has open (no position is trusted from
 * the client); an empty name clears the custom name. The name shows in the GUI title and on the locker's front.
 */
public record LockerRenamePacket(String name) implements CustomPacketPayload
{
    public static final Type<LockerRenamePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "locker_rename"));
    public static final StreamCodec<FriendlyByteBuf, LockerRenamePacket> STREAM_CODEC = StreamCodec.ofMember(LockerRenamePacket::encode, LockerRenamePacket::decode);

    public static final int MAX_LENGTH = 32;

    @Override
    public Type<LockerRenamePacket> type()
    {
        return TYPE;
    }

    public static void encode(LockerRenamePacket msg, FriendlyByteBuf buf)
    {
        buf.writeUtf(msg.name, 256);
    }

    public static LockerRenamePacket decode(FriendlyByteBuf buf)
    {
        return new LockerRenamePacket(buf.readUtf(256));
    }

    public static void handle(LockerRenamePacket msg, IPayloadContext ctx)
    {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof LargeLockerMenu menu) || !menu.stillValid(player)) return;
        if (!(menu.container() instanceof LargeLockerBlockEntity be)) return;
        String name = clean(msg.name);
        be.setCustomName(name.isEmpty() ? null : Component.literal(name));
    }

    /** Drops control characters, trims, and cuts to MAX_LENGTH code points. */
    static String clean(String raw)
    {
        StringBuilder sb = new StringBuilder();
        raw.codePoints().filter(c -> c > 0xFFFF || StringUtil.isAllowedChatCharacter((char) c)).forEach(sb::appendCodePoint);
        String s = sb.toString().trim();
        if (s.codePointCount(0, s.length()) > MAX_LENGTH) s = s.substring(0, s.offsetByCodePoints(0, MAX_LENGTH)).trim();
        return s;
    }
}
