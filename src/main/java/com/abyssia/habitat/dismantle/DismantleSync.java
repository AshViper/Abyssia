package com.abyssia.habitat.dismantle;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatConstructorItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Arrays;

/**
 * BT01b preview without a packet: the client has no BuiltUnits / HabitatBases, so every few ticks the server writes
 * the box of the unit under the crosshair into the held constructor's minecraft:custom_data {@link #BOX} (int[6] min / max, inclusive)
 * while the dismantle mode is selected, and removes it otherwise. Only rewritten when the target changes.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class DismantleSync
{
    public static final String BOX = "DismantleBox";
    private static final int PERIOD = 4;

    private DismantleSync() {}

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % PERIOD != 0 || !(player.level() instanceof ServerLevel level)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof HabitatConstructorItem)) return;
        int[] now = box(stack);
        int[] want = null;
        if (DismantleEntry.ID.equals(HabitatConstructorItem.entryId(stack)))
        {
            DismantleTarget t = Dismantler.find(level, player, 1.0f, false);
            if (t != null)
            {
                BoundingBox b = t.bounds();
                want = new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
            }
        }
        if (Arrays.equals(now, want)) return;
        int[] put = want;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag ->
        {
            if (put == null) tag.remove(BOX);
            else tag.putIntArray(BOX, put);
        });
    }

    /** the synced box (int[6]) on this stack, or null */
    public static int[] box(ItemStack stack)
    {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(BOX)) return null;
        CompoundTag tag = data.copyTag();
        return tag.getIntArray(BOX);
    }
}
