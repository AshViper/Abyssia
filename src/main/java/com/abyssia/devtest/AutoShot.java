package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.item.light.HandLampItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Scratch-only: stages the hand lamp scenes (real right-click toggle) and saves screenshots. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AutoShot
{
    private static int t = -1;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e)
    {
        if (e.phase != TickEvent.Phase.END || System.getProperty("abyssia.autoshot") == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) { return; }
        if (t < 0) t = 0;
        t++;
        var p = mc.player;
        switch (t)
        {
            case 80 -> { cmd(mc, "gamemode survival"); cmd(mc, "effect clear @s"); cmd(mc, "effect give @s minecraft:water_breathing 999 0 true"); cmd(mc, "clear @s"); cmd(mc, "time set midnight"); cmd(mc, "weather clear"); cmd(mc, "tp @s ~ ~ ~ 0 0"); }
            case 100 -> { cmd(mc, "give @s abyssia:hand_lamp"); cmd(mc, "fill ~-6 ~-1 ~6 ~6 ~5 ~6 minecraft:stone"); cmd(mc, "fill ~-6 ~-1 ~0 ~6 ~-1 ~6 minecraft:stone"); }
            case 130 -> { p.getInventory().selected = 0; log("start dim=" + mc.level.dimension().location() + " pos=" + p.blockPosition()
                    + " mode=" + mc.gameMode.getPlayerMode() + " held=" + p.getMainHandItem() + " on=" + HandLampItem.isOn(p.getMainHandItem())); }
            case 135 -> { p.setXRot(10f); shot(mc, "hl_off"); }
            case 150 -> mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            case 175 -> { log("after use1 on=" + HandLampItem.isOn(p.getMainHandItem()) + " tag=" + p.getMainHandItem().getTag()); shot(mc, "hl_on"); }
            case 200 -> mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            case 225 -> log("after use2 on=" + HandLampItem.isOn(p.getMainHandItem()));
            case 227 -> { shot(mc, "hl_off2"); }
            case 230 -> mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            case 255 -> { p.setXRot(10f); log("after use3 on=" + HandLampItem.isOn(p.getMainHandItem())); p.setXRot(35f); }
            case 262 -> shot(mc, "hl_on_down");
            case 270 -> { cmd(mc, "effect give @s minecraft:night_vision 999 0 true"); p.setXRot(0f); }
            case 285 -> shot(mc, "hl_pose_fp");
            case 288 -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); }
            case 300 -> shot(mc, "hl_on_third");
            case 330 -> { log("done"); mc.stop(); }
            default -> {}
        }
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); }

    private static void log(String s) { System.out.println("[AutoShot] " + s); }

    private static void shot(Minecraft mc, String name)
    {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> log("saved " + name));
    }
}
