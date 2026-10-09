package com.abyssia.research.client;

import com.abyssia.Abyssia;
import com.abyssia.research.scan.ScanProgressPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * AB05 scan HUD under the crosshair: target name, distance, progress bar and status (not analysed / analysed / fragments n/m), a
 * short flash on completion, and "new blueprint" lines ({@link #showUnlocked}, to be called by the unlock-notice handler).
 * Display only; all state comes from {@link ScanProgressPayload} and {@link ResearchView}.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ScanHud
{
    private static final int SCANNING_TIMEOUT = 12, RESULT_TICKS = 50, ABORT_TICKS = 24, UNLOCK_TICKS = 100, MAX_UNLOCK_LINES = 4;
    private static final int BAR_W = 100, BAR_H = 5;

    private static int clock;
    private static ResourceLocation targetId;
    private static float progress;
    private static byte state = ScanProgressPayload.NO_TARGET;
    private static int lastUpdate = Integer.MIN_VALUE / 2, resultStart = Integer.MIN_VALUE / 2;

    private record UnlockLine(Component text, int until) {}
    private static final List<UnlockLine> UNLOCKS = new ArrayList<>();

    private ScanHud() {}

    /** payload handler (client main thread) */
    public static void onProgress(ScanProgressPayload msg)
    {
        state = msg.state();
        targetId = msg.targetId();
        progress = Mth.clamp(msg.progress(), 0F, 1F);
        lastUpdate = clock;
        if (state != ScanProgressPayload.SCANNING) resultStart = clock;
    }

    /** "New blueprint acquired: title" line; for a notice handler that does not already show its own toast */
    public static void showUnlocked(ResourceLocation techId)
    {
        UNLOCKS.add(new UnlockLine(Component.translatable("hud.abyssia.scan.unlocked", ResearchView.techTitle(techId)), clock + UNLOCK_TICKS));
        while (UNLOCKS.size() > MAX_UNLOCK_LINES) UNLOCKS.remove(0);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        clock++;
        UNLOCKS.removeIf(l -> l.until < clock);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        state = ScanProgressPayload.NO_TARGET;
        targetId = null;
        UNLOCKS.clear();
    }

    private static boolean scanning()
    {
        return state == ScanProgressPayload.SCANNING && clock - lastUpdate <= SCANNING_TIMEOUT;
    }

    private static boolean showingResult()
    {
        if (state == ScanProgressPayload.SCANNING || state == ScanProgressPayload.NO_TARGET) return false;
        return clock - resultStart <= (state == ScanProgressPayload.ABORTED ? ABORT_TICKS : RESULT_TICKS);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;
        int cx = width / 2, y = height / 2 + 22;

        if (targetId != null && (scanning() || showingResult()))
        {
            boolean done = !scanning();
            float age = done ? clock - resultStart + partialTick : 0;
            Component name = ResearchView.targetName(targetId);
            g.drawCenteredString(font, name, cx, y, 0xFFFFFFFF);
            y += 11;

            if (done && state == ScanProgressPayload.ABORTED)
            {
                g.drawCenteredString(font, Component.translatable("hud.abyssia.scan.aborted"), cx, y, 0xFFFF6060);
            }
            else
            {
                // progress bar (full + green flash fading out once complete)
                int x0 = cx - BAR_W / 2;
                g.fill(x0 - 1, y - 1, x0 + BAR_W + 1, y + BAR_H + 1, 0xC0101418);
                g.fill(x0, y, x0 + BAR_W, y + BAR_H, 0xFF2A3038);
                int barColor = done ? 0xFF58E6A0 : 0xFF3CC8E6;
                g.fill(x0, y, x0 + Math.round(BAR_W * (done ? 1F : progress)), y + BAR_H, barColor);
                if (done && age < 8)
                {
                    int alpha = (int) (160 * (1F - age / 8F));
                    g.fill(x0 - 3, y - 3, x0 + BAR_W + 3, y + BAR_H + 3, (alpha << 24) | 0x00FFFFFF);
                }
                y += BAR_H + 3;
                Component line = statusLine(done);
                g.drawCenteredString(font, line, cx, y, done ? 0xFF58E6A0 : 0xFFE0E0E0);
            }
            y += 11;
            if (!done)
            {
                Component dist = distanceText(mc);
                if (dist != null) g.drawCenteredString(font, dist, cx, y, 0xFF9AA4AE);
                y += 11;
            }
            y += 4;
        }

        for (UnlockLine l : UNLOCKS)
        {
            g.drawCenteredString(font, l.text, cx, y, 0xFFFFD866);
            y += 10;
        }
    }

    /** status text: result state when complete, else analysed / fragments n/m / not analysed */
    private static Component statusLine(boolean done)
    {
        int[] frag = ResearchView.fragmentStatus(targetId);
        if (done)
        {
            return switch (state)
            {
                case ScanProgressPayload.COMPLETE_NEW -> Component.translatable("hud.abyssia.scan.complete_new");
                case ScanProgressPayload.COMPLETE_FRAGMENT -> frag != null
                        ? Component.translatable("hud.abyssia.scan.fragments", frag[0], frag[1])
                        : Component.translatable("hud.abyssia.scan.complete_new");
                default -> Component.translatable("hud.abyssia.scan.complete_known");
            };
        }
        int pct = Math.round(progress * 100F);
        Component status;
        if (frag != null && frag[0] < frag[1]) status = Component.translatable("hud.abyssia.scan.fragments", frag[0], frag[1]);
        else if (ResearchView.isScanned(targetId)) status = Component.translatable("hud.abyssia.scan.scanned");
        else status = Component.translatable("hud.abyssia.scan.unscanned");
        return Component.translatable("hud.abyssia.scan.progress", status, pct);
    }

    /** distance of the block under the crosshair (client ray along the look vector); null when nothing is hit */
    private static Component distanceText(Minecraft mc)
    {
        if (mc.level == null || mc.player == null) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getLookAngle().scale(32.0));
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return null;
        return Component.translatable("hud.abyssia.scan.distance", Math.round(eye.distanceTo(hit.getLocation())));
    }

    @Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration
    {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event)
        {
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "scan_hud", ScanHud::render);
        }
    }
}
