package com.abyssia.progress;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * WRK01: which wreck cores a player has analysed (lidar scanner). Positions (BlockPos.asLong, no duplicates) live in the
 * persisted player data under {@link #KEY}; enough of them ({@link Config#WRECKS_TO_UNLOCK}) unlocks the habitat constructor.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class WreckProgress
{
    public static final String KEY = "abyssia_wrecks";

    private WreckProgress() {}

    private static long[] read(Player player)
    {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLongArray(KEY);
    }

    public static int count(Player player)
    {
        return read(player).length;
    }

    public static int required()
    {
        return Config.WRECKS_TO_UNLOCK.get();
    }

    public static boolean unlocked(Player player)
    {
        return count(player) >= required();
    }

    /** Records the wreck core; false when it was already analysed. */
    public static boolean add(Player player, BlockPos pos)
    {
        long id = pos.asLong();
        long[] old = read(player);
        for (long l : old) if (l == id) return false;
        long[] next = Arrays.copyOf(old, old.length + 1);
        next[old.length] = id;
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putLongArray(KEY, next);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        return true;
    }

    /** Death (and the end-return) makes a new player entity: carry the analysed wrecks over. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event)
    {
        long[] old = read(event.getOriginal());
        if (old.length == 0) return;
        CompoundTag data = event.getEntity().getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putLongArray(KEY, old);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
