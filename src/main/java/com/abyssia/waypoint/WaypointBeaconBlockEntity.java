package com.abyssia.waypoint;

import com.abyssia.Config;
import com.abyssia.registry.ModIndustry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** W01: the beacon's name ("" = default) and who placed it. No ticking; the colour lives in the blockstate. */
public class WaypointBeaconBlockEntity extends BlockEntity
{
    private String name = "";
    @Nullable
    private UUID owner;

    public WaypointBeaconBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModIndustry.WAYPOINT_BEACON_ENTITY.get(), pos, state);
    }

    public String getName()
    {
        return name;
    }

    @Nullable
    public UUID getOwner()
    {
        return owner;
    }

    public void setOwner(@Nullable UUID owner)
    {
        this.owner = owner;
        setChanged();
    }

    public void setName(String name)
    {
        this.name = name;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Anyone may rename a shared beacon; with share_beacons off only its owner (or an operator). */
    public boolean mayEdit(ServerPlayer player)
    {
        return Config.WAYPOINT_SHARE.get() || owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        name = tag.getString("Name");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putString("Name", name);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    // the client needs the name to fill the settings screen
    @Override
    public CompoundTag getUpdateTag()
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
