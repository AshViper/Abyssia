package com.abyssia.habitat.dismantle.client;

import com.abyssia.habitat.HabitatConstructorItem;
import com.abyssia.habitat.client.HabitatHologram;
import com.abyssia.habitat.dismantle.DismantleEntry;
import com.abyssia.habitat.dismantle.DismantleSync;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.Nullable;

/** BT01b client: the orange dismantle outline = the box the server put in the held constructor's NBT. */
public final class DismantleClient
{
    private DismantleClient() {}

    public static void register(IEventBus bus)
    {
        HabitatHologram.setDismantlePreview(DismantleClient::box);
    }

    @Nullable
    private static AABB box(LocalPlayer player, ItemStack stack, float partialTick)
    {
        if (!DismantleEntry.ID.equals(HabitatConstructorItem.entryId(stack))) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DismantleSync.BOX)) return null;
        int[] b = tag.getIntArray(DismantleSync.BOX);
        if (b.length != 6) return null;
        return new AABB(b[0], b[1], b[2], b[3] + 1, b[4] + 1, b[5] + 1);
    }
}
