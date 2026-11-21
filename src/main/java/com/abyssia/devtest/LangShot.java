package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.guide.client.GuideBookScreen;
import com.abyssia.habitat.build.BuildRegistry;
import com.abyssia.habitat.client.HabitatMenuScreen;
import com.abyssia.research.client.DatabaseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Dev-only: with -Dabyssia.langshot=zh_cn,ru_ru (gradlew runClient -PlangShot=zh_cn,ru_ru) the client enters the singleplayer world PrcGuiShot,
 * switches to each language in turn and screenshots the text-heavy screens (guide book spreads, research database tabs,
 * habitat build menu tabs) into run/screenshots/lang_&lt;code&gt;_*.png, restores the language and quits.
 * Log lines "LANGSHOT ...".  Used to check fonts and text overflow of a translation.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LangShot
{
    private static final String LANGS = System.getProperty("abyssia.langshot");
    private static final int[] GUIDE_SPREADS = {0, 1, 2, 4, 8, 14, 22};

    private record Step(BooleanSupplier run, int delay) {}

    private static final ArrayDeque<Step> STEPS = new ArrayDeque<>();
    private static boolean built;
    private static int ct, wait, tries;

    private LangShot() {}

    private static void log(String s)
    {
        System.out.println("LANGSHOT " + s);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e)
    {
        if (LANGS == null || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (++ct < 100) return;
        if (!built)
        {
            built = true;
            build(mc);
        }
        if (wait > 0) { wait--; return; }
        Step s = STEPS.peek();
        if (s == null) return;
        boolean ok;
        try { ok = s.run().getAsBoolean(); }
        catch (Throwable t)
        {
            log("FAIL " + t);
            ok = true;
        }
        if (ok || ++tries > 600)
        {
            STEPS.poll();
            tries = 0;
            wait = s.delay();
        }
    }

    private static void run(int delay, Runnable r)
    {
        STEPS.add(new Step(() -> { r.run(); return true; }, delay));
    }

    private static void shot(String name)
    {
        run(4, () ->
        {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> log("saved " + name));
        });
    }

    private static void language(Minecraft mc, String code)
    {
        run(20, () ->
        {
            mc.setScreen(null);
            mc.options.languageCode = code;
            mc.getLanguageManager().setSelected(code);
            mc.reloadResourcePacks();
        });
        STEPS.add(new Step(() -> mc.getOverlay() == null, 40));   // wait for the reload overlay to go
        run(0, () -> log("language " + mc.getLanguageManager().getSelected()));
    }

    private static void build(Minecraft mc)
    {
        String before = mc.options.languageCode;
        for (String code : LANGS.split(","))
        {
            String p = "lang_" + code + "_";
            language(mc, code);

            run(10, () -> mc.setScreen(new GuideBookScreen()));
            int at = 0;
            for (int target : GUIDE_SPREADS)
            {
                int steps = target - at;
                at = target;
                run(6, () -> { for (int i = 0; i < steps; i++) mc.screen.keyPressed(262, 0, 0); });
                shot(p + "guide_" + target);
            }

            run(10, () -> mc.setScreen(new DatabaseScreen()));
            for (int tab = 0; tab < 4; tab++)
            {
                int t = tab;
                run(8, () ->
                {
                    List<Button> buttons = buttons(mc.screen);
                    if (t < buttons.size() && buttons.get(t).active) buttons.get(t).onPress();
                });
                shot(p + "database_" + tab);
            }

            run(10, () ->
            {
                mc.setScreen(new HabitatMenuScreen(BuildRegistry.first()));
                // rest the pointer on a row right of the cost icons, so a shortened detail shows its tooltip
                mouse(mc, mc.screen.width / 2.0 + 110, mc.screen.height / 2.0 - 8);
            });
            for (int tab = 0; tab < 6; tab++)
            {
                shot(p + "habitat_" + tab);
                run(6, () -> mc.screen.keyPressed(262, 0, 0));
            }
        }
        language(mc, before);
        run(20, () -> log("done"));
        run(0, mc::stop);
    }

    /** moves the pointer to a GUI-scaled position without a real mouse (MouseHandler keeps window pixels) */
    private static void mouse(Minecraft mc, double gx, double gy)
    {
        try
        {
            var w = mc.getWindow();
            var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, gx * w.getScreenWidth() / w.getGuiScaledWidth());
            fy.setDouble(mc.mouseHandler, gy * w.getScreenHeight() / w.getGuiScaledHeight());
        }
        catch (Throwable t)
        {
            log("mouse move failed " + t);
        }
    }

    private static List<Button> buttons(Screen screen)
    {
        List<Button> out = new ArrayList<>();
        for (GuiEventListener c : screen.children()) if (c instanceof Button b) out.add(b);
        return out;
    }
}
