package com.abyssia.guide;

import com.abyssia.Abyssia;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gives every player one guide book on first login (flag in the persisted player data). */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class GuideBookFirstLogin
{
    public static final String FLAG = "abyssia_guide_received";

    private GuideBookFirstLogin() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(FLAG)) return;
        persisted.putBoolean(FLAG, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(GuideBookRegistry.ABYSS_GUIDE_BOOK.get());
        if (!player.getInventory().add(book)) player.drop(book, false);
    }
}
