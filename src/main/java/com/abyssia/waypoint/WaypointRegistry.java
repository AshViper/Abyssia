package com.abyssia.waypoint;

import com.abyssia.Config;
import com.abyssia.network.AbyssiaNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * W01: every waypoint beacon of one dimension (the deep layer is part of the overworld), saved with the level. Clients
 * draw their HUD markers from this list only, since far beacons sit in chunks the client does not have. Any change is
 * sent straight to the players in that dimension (with share_beacons off each player gets only their own beacons).
 */
public class WaypointRegistry extends SavedData
{
    public static final String NAME = "abyssia_waypoints";

    private final Map<BlockPos, WaypointEntry> beacons = new LinkedHashMap<>();

    public static WaypointRegistry get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WaypointRegistry::new, WaypointRegistry::load, null), NAME);
    }

    /** Writes the entry at pos from the current block (colour) and block entity (name, owner), then syncs. */
    public static void refresh(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WaypointBeaconBlock)) return;
        WaypointRegistry reg = get(level);
        WaypointEntry old = reg.beacons.get(pos);
        String name = old != null ? old.name() : "";
        UUID owner = old != null ? old.owner() : null;
        if (level.getBlockEntity(pos) instanceof WaypointBeaconBlockEntity be)
        {
            name = be.getName();
            if (be.getOwner() != null) owner = be.getOwner();
        }
        WaypointEntry entry = new WaypointEntry(pos.immutable(), name, state.getValue(WaypointBeaconBlock.COLOR), owner);
        if (entry.equals(old)) return;
        reg.beacons.put(entry.pos(), entry);
        reg.setDirty();
        syncLevel(level);
    }

    public static void remove(ServerLevel level, BlockPos pos)
    {
        WaypointRegistry reg = get(level);
        if (reg.beacons.remove(pos) == null) return;
        reg.setDirty();
        syncLevel(level);
    }

    /** The beacons a player may see: all of them, or with share_beacons off only the ones they placed. */
    public List<WaypointEntry> visibleTo(ServerPlayer player)
    {
        boolean share = Config.WAYPOINT_SHARE.get();
        List<WaypointEntry> out = new ArrayList<>();
        for (WaypointEntry e : beacons.values())
            if (share || player.getUUID().equals(e.owner())) out.add(e);
        return out;
    }

    /** Sends a player the full list of their current dimension (login, dimension change, respawn). */
    public static void sendTo(ServerPlayer player)
    {
        AbyssiaNetwork.sendTo(player, new WaypointSyncPacket(get(player.serverLevel()).visibleTo(player),
                Config.WAYPOINT_MAX_NAME_LENGTH.get()));
    }

    private static void syncLevel(ServerLevel level)
    {
        for (ServerPlayer player : level.players()) sendTo(player);
    }

    // ---------- save / load ----------

    public static WaypointRegistry load(CompoundTag tag, HolderLookup.Provider registries)
    {
        WaypointRegistry reg = new WaypointRegistry();
        for (Tag t : tag.getList("Beacons", Tag.TAG_COMPOUND))
        {
            CompoundTag b = (CompoundTag) t;
            BlockPos pos = BlockPos.of(b.getLong("Pos"));
            UUID owner = b.hasUUID("Owner") ? b.getUUID("Owner") : null;
            reg.beacons.put(pos, new WaypointEntry(pos, b.getString("Name"), WaypointColors.clamp(b.getInt("Color")), owner));
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (WaypointEntry e : beacons.values())
        {
            CompoundTag b = new CompoundTag();
            b.putLong("Pos", e.pos().asLong());
            b.putString("Name", e.name());
            b.putInt("Color", e.color());
            if (e.owner() != null) b.putUUID("Owner", e.owner());
            list.add(b);
        }
        tag.put("Beacons", list);
        return tag;
    }
}
