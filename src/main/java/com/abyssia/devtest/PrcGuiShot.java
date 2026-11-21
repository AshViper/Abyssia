package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.industry.block.IndustryEntityBlock;
import com.abyssia.industry.blockentity.PrecipitatorBlockEntity;
import com.abyssia.registry.ModBlocks;
import com.abyssia.registry.ModIndustry;
import com.abyssia.registry.ModItems;
import com.abyssia.thermal.ThermalVentType;
import com.abyssia.thermal.VentActivity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dev-only (PRC01): with -Dabyssia.prcguishot (gradle runPrcGuiShot) the client enters a singleplayer world, the integrated
 * server places a powered, stocked precipitator next to a black smoker and opens its menu; the client then saves
 * run/screenshots/prc_gui.png (and prc_gui_2.png in the other language) and quits. Log lines "PRCGUI ...".
 */
public final class PrcGuiShot
{
    private static final boolean ON = System.getProperty("abyssia.prcguishot") != null;
    private static int st = -1;
    private static BlockPos pos;

    @EventBusSubscriber(modid = Abyssia.MODID)
    public static final class Server
    {
        @SubscribeEvent
        public static void tick(ServerTickEvent.Post e)
        {
            if (!ON) return;
            if (e.getServer().getPlayerList().getPlayers().isEmpty()) return;
            ServerPlayer p = e.getServer().getPlayerList().getPlayers().get(0);
            st++;
            if (st == 20)
            {
                p.setGameMode(GameType.CREATIVE);
                var level = p.serverLevel();
                pos = p.blockPosition().offset(3, 1, 0);
                level.setBlockAndUpdate(pos, ModIndustry.HYDROTHERMAL_PRECIPITATOR.get().defaultBlockState());
                level.setBlockAndUpdate(pos.east(), ModBlocks.THERMAL_VENT.get().defaultBlockState()
                        .setValue(ThermalVentBlock.TYPE, ThermalVentType.BLACK_SMOKER).setValue(ThermalVentBlock.ACTIVITY, VentActivity.ACTIVE));
                if (level.getBlockEntity(pos) instanceof PrecipitatorBlockEntity be)
                {
                    be.items().setStackInSlot(0, new ItemStack(ModItems.RAW_COBALT.get(), 12));
                    be.items().setStackInSlot(1, new ItemStack(ModItems.RAW_NICKEL.get(), 5));
                    be.items().setStackInSlot(2, new ItemStack(ModItems.RAW_ZINC.get(), 3));
                    be.items().setStackInSlot(3, new ItemStack(ModItems.RAW_LEAD.get(), 1));
                }
                System.out.println("[PrcGuiShot] placed at " + pos);
            }
            if (pos == null) return;
            if (st > 20 && p.serverLevel().getBlockEntity(pos) instanceof PrecipitatorBlockEntity be)
                be.energyStorage(null).receiveEnergy(256, false);
            if (st == 80 && p.serverLevel().getBlockEntity(pos) instanceof PrecipitatorBlockEntity be)
                p.openMenu(be, buf -> { buf.writeBlockPos(pos); buf.writeByte(com.abyssia.industry.MachineKind.HYDROTHERMAL_PRECIPITATOR.ordinal()); });
        }
    }

    @EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
    public static final class Client
    {
        private static int ct = 0, since = -1, phase = 0, wait = 0;

        @SubscribeEvent
        public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e)
        {
            if (!ON) return;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            ct++;
            boolean open = mc.screen instanceof com.abyssia.industry.client.IndustryScreen;
            if (since < 0)
            {
                if (open) since = 0;
                else if (ct > 600) { System.out.println("PRCGUI FAIL no screen"); mc.stop(); }
                return;
            }
            if (!open) { if (++wait > 200) { System.out.println("PRCGUI FAIL screen closed"); mc.stop(); } return; }
            since++;
            if (phase == 0 && since == 40)
            {
                shot(mc, "prc_gui.png");
                phase = 1;
                String next = "ja_jp".equals(mc.options.languageCode) ? "en_us" : "ja_jp";
                mc.options.languageCode = next;
                mc.getLanguageManager().setSelected(next);
                mc.reloadResourcePacks();
                since = 0;
            }
            else if (phase == 1 && since == 120) { shot(mc, "prc_gui_2.png"); phase = 2; since = 0; }
            else if (phase == 2 && since == 20) mc.stop();
        }

        private static void shot(net.minecraft.client.Minecraft mc, String name)
        {
            mc.getToasts().clear();
            java.io.File dir = new java.io.File(mc.gameDirectory, "screenshots");
            dir.mkdirs();
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                    c -> System.out.println("PRCGUI saved " + new java.io.File(dir, name).getAbsolutePath()));
        }
    }
}
