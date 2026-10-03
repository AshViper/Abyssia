package com.abyssia.habitat.dismantle;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatConstructorItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * BT01b preview without a packet: the client has no BuiltUnits / HabitatBases, so every few ticks the server writes
 * the box of the unit under the crosshair into the held constructor's NBT {@link #BOX} (int[6] min / max, inclusive)
 * while the dismantle mode is selected, and removes it otherwise. Only rewritten when the target changes.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DismantleSync
{
    public static final String BOX = "DismantleBox";
    private static final int PERIOD = 4;

    private DismantleSync() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % PERIOD != 0 || !(player.level() instanceof ServerLevel level)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof HabitatConstructorItem)) return;
        CompoundTag tag = stack.getTag();
        int[] now = tag != null && tag.contains(BOX) ? tag.getIntArray(BOX) : null;
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
        if (want == null) stack.removeTagKey(BOX);
        else stack.getOrCreateTag().putIntArray(BOX, want);
    }
}
