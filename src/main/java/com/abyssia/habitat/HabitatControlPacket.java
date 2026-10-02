package com.abyssia.habitat;

import com.abyssia.Abyssia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: select a module, set the rotation, or start a build. Only applies while the constructor is in the main hand. */
public record HabitatControlPacket(Action action, HabitatMode mode, int rot) implements CustomPacketPayload
{
    public enum Action { SELECT, ROTATE, BUILD }

    public static final Type<HabitatControlPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "habitat_control"));

    public static final StreamCodec<ByteBuf, HabitatControlPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) ->
            {
                buf.writeByte(msg.action.ordinal());
                buf.writeByte(msg.mode.ordinal());
                buf.writeByte(msg.rot);
            },
            buf ->
            {
                Action[] actions = Action.values();
                HabitatMode[] modes = HabitatMode.values();
                Action action = actions[Math.floorMod(buf.readByte(), actions.length)];
                HabitatMode mode = modes[Math.floorMod(buf.readByte(), modes.length)];
                return new HabitatControlPacket(action, mode, Math.floorMod(buf.readByte(), 4));
            });

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    /** Payload handlers run on the server thread by default. */
    public static void handle(HabitatControlPacket msg, IPayloadContext ctx)
    {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof HabitatConstructorItem)) return;
        switch (msg.action)
        {
            case SELECT -> HabitatConstructorItem.setMode(stack, msg.mode);
            case ROTATE -> HabitatConstructorItem.setRotation(stack, msg.rot);
            case BUILD ->
            {
                HabitatConstructorItem.setMode(stack, msg.mode);
                HabitatBuilder.startBuild(player, msg.mode, msg.rot);
            }
        }
    }
}
