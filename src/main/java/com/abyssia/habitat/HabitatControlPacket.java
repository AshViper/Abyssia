package com.abyssia.habitat;

import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S (BT01a): select a build entry, set the rotation, set the placement distance, or start a build with
 * entry + rotation + distance (the server clamps the distance to 3..12 and re-plans from the player's aim).
 * Only applies while the constructor is in the main hand. {@code entryId} is "" when unused.
 */
public record HabitatControlPacket(Action action, String entryId, int rot, int distance)
{
    public enum Action { SELECT, ROTATE, DISTANCE, BUILD }

    private static final int MAX_ID = 64;

    public static HabitatControlPacket select(String entryId)
    {
        return new HabitatControlPacket(Action.SELECT, entryId, 0, 0);
    }

    public static HabitatControlPacket rotate(int rot)
    {
        return new HabitatControlPacket(Action.ROTATE, "", rot, 0);
    }

    public static HabitatControlPacket distance(int distance)
    {
        return new HabitatControlPacket(Action.DISTANCE, "", 0, distance);
    }

    public static HabitatControlPacket build(String entryId, int rot, int distance)
    {
        return new HabitatControlPacket(Action.BUILD, entryId, rot, distance);
    }

    public static void encode(HabitatControlPacket msg, FriendlyByteBuf buf)
    {
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.entryId, MAX_ID);
        buf.writeByte(msg.rot);
        buf.writeByte(msg.distance);
    }

    public static HabitatControlPacket decode(FriendlyByteBuf buf)
    {
        return new HabitatControlPacket(buf.readEnum(Action.class), buf.readUtf(MAX_ID), Math.floorMod(buf.readByte(), 4), buf.readByte());
    }

    public static void handle(HabitatControlPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof HabitatConstructorItem)) return;
        int distance = HabitatPlan.clampDistance(msg.distance);
        switch (msg.action)
        {
            case SELECT ->
            {
                if (BuildRegistry.get(msg.entryId) != null) stack.getOrCreateTag().putString(HabitatConstructorItem.MODE, msg.entryId);
            }
            case ROTATE -> stack.getOrCreateTag().putInt(HabitatConstructorItem.ROT, msg.rot);
            case DISTANCE -> stack.getOrCreateTag().putInt(HabitatConstructorItem.DIST, distance);
            case BUILD ->
            {
                BuildEntry entry = BuildRegistry.get(msg.entryId);
                if (entry == null) return;
                stack.getOrCreateTag().putString(HabitatConstructorItem.MODE, entry.id());
                stack.getOrCreateTag().putInt(HabitatConstructorItem.DIST, distance);
                HabitatBuilder.startBuild(player, entry, msg.rot, distance);
            }
        }
    }
}
