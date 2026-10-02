package com.abyssia.waypoint;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import net.minecraft.util.StringUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: the settings screen's Save. The server checks reach, the block and the permission, and trims / caps the name. */
public record WaypointSavePacket(BlockPos pos, String name, int color) implements CustomPacketPayload
{
    public static final Type<WaypointSavePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "waypoint_save"));
    public static final StreamCodec<FriendlyByteBuf, WaypointSavePacket> STREAM_CODEC = StreamCodec.ofMember(WaypointSavePacket::encode, WaypointSavePacket::decode);

    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    @Override
    public Type<WaypointSavePacket> type()
    {
        return TYPE;
    }

    public static void encode(WaypointSavePacket msg, FriendlyByteBuf buf)
    {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.name, 256);
        buf.writeByte(msg.color);
    }

    public static WaypointSavePacket decode(FriendlyByteBuf buf)
    {
        return new WaypointSavePacket(buf.readBlockPos(), buf.readUtf(256), buf.readByte());
    }

    public static void handle(WaypointSavePacket msg, IPayloadContext ctx)
    {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = msg.pos;
        if (!level.isLoaded(pos) || player.position().distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WaypointBeaconBlock)) return;
        if (!(level.getBlockEntity(pos) instanceof WaypointBeaconBlockEntity be) || !be.mayEdit(player)) return;
        String name = clean(msg.name, Config.WAYPOINT_MAX_NAME_LENGTH.get());
        if (name.isEmpty()) return;
        int color = msg.color;
        if (color < 0 || color >= WaypointColors.COUNT) return;

        be.setName(name);
        if (state.getValue(WaypointBeaconBlock.COLOR) != color)
            level.setBlock(pos, state.setValue(WaypointBeaconBlock.COLOR, color), Block.UPDATE_ALL);
        WaypointRegistry.refresh(level, pos);
    }

    /** Drops control characters, trims, and cuts to max code points. */
    static String clean(String raw, int max)
    {
        StringBuilder sb = new StringBuilder();
        raw.codePoints().filter(c -> StringUtil.isAllowedChatCharacter((char) c) || c > 0xFFFF).forEach(sb::appendCodePoint);
        String s = sb.toString().trim();
        if (s.codePointCount(0, s.length()) > max) s = s.substring(0, s.offsetByCodePoints(0, max)).trim();
        return s;
    }
}
