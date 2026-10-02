package com.abyssia.habitat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: select a module, set the rotation, or start a build. Only applies while the constructor is in the main hand. */
public record HabitatControlPacket(Action action, HabitatMode mode, int rot)
{
    public enum Action { SELECT, ROTATE, BUILD }

    public static void encode(HabitatControlPacket msg, FriendlyByteBuf buf)
    {
        buf.writeEnum(msg.action);
        buf.writeEnum(msg.mode);
        buf.writeByte(msg.rot);
    }

    public static HabitatControlPacket decode(FriendlyByteBuf buf)
    {
        return new HabitatControlPacket(buf.readEnum(Action.class), buf.readEnum(HabitatMode.class), Math.floorMod(buf.readByte(), 4));
    }

    public static void handle(HabitatControlPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof HabitatConstructorItem)) return;
        switch (msg.action)
        {
            case SELECT -> stack.getOrCreateTag().putString(HabitatConstructorItem.MODE, msg.mode.id);
            case ROTATE -> stack.getOrCreateTag().putInt(HabitatConstructorItem.ROT, msg.rot);
            case BUILD ->
            {
                stack.getOrCreateTag().putString(HabitatConstructorItem.MODE, msg.mode.id);
                HabitatBuilder.startBuild(player, msg.mode, msg.rot);
            }
        }
    }
}
