package com.abyssia.client.title;

import com.abyssia.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/** Title screen panorama of the deep sea: the cube map plus a faint tint, rising bubbles and drifting dust. */
public final class AbyssPanorama extends PanoramaRenderer
{
    public static final ResourceLocation FIRST_FACE = ResourceLocation.fromNamespaceAndPath("abyssia", "textures/gui/title/background/panorama_0.png");
    private static final int BUBBLES = 40;
    private static final int MOTES = 60;

    private final Random random = new Random(1337L);
    // x, y in 0..1 screen fractions; bubbles: speed, phase, size; motes: vx, vy
    private final float[] bx = new float[BUBBLES], by = new float[BUBBLES], bs = new float[BUBBLES], bp = new float[BUBBLES], bz = new float[BUBBLES];
    private final float[] mx = new float[MOTES], my = new float[MOTES], mvx = new float[MOTES], mvy = new float[MOTES];
    private long last = -1L;

    public AbyssPanorama()
    {
        super(new CubeMap(ResourceLocation.fromNamespaceAndPath("abyssia", "textures/gui/title/background/panorama")));
        for (int i = 0; i < BUBBLES; i++) spawnBubble(i, true);
        for (int i = 0; i < MOTES; i++)
        {
            mx[i] = random.nextFloat();
            my[i] = random.nextFloat();
            mvx[i] = (random.nextFloat() - 0.5f) * 0.004f;
            mvy[i] = (random.nextFloat() - 0.5f) * 0.003f;
        }
    }

    private void spawnBubble(int i, boolean anywhere)
    {
        bx[i] = random.nextFloat();
        by[i] = anywhere ? random.nextFloat() : 1.0f + random.nextFloat() * 0.1f;
        bs[i] = 0.02f + random.nextFloat() * 0.05f;
        bp[i] = random.nextFloat() * 6.283f;
        bz[i] = random.nextFloat() < 0.2f ? 2f : 1f;
    }

    @Override
    public void render(GuiGraphics g, int w, int h, float alpha, float delta)
    {
        super.render(g, w, h, alpha, delta);
        if (!ClientConfig.TITLE_PANORAMA_EFFECTS.get()) return;
        long now = Util.getMillis();
        float dt = last < 0 ? 0f : Math.min((now - last) / 1000f, 0.1f);
        last = now;
        float a = Math.max(0f, Math.min(1f, alpha));

        int tint = (int) (0x30 * a);
        g.fill(0, 0, w, h, (tint << 24) | 0x001830);

        float t = now / 1000f;
        for (int i = 0; i < BUBBLES; i++)
        {
            by[i] -= bs[i] * dt;
            if (by[i] < -0.05f) spawnBubble(i, false);
            int x = (int) ((bx[i] + 0.012f * (float) Math.sin(t * 1.3f + bp[i])) * w);
            int y = (int) (by[i] * h);
            int al = (int) (0x38 * a);
            int s = (int) bz[i];
            g.fill(x, y, x + s, y + s, (al << 24) | 0xA0F0FF);
        }
        for (int i = 0; i < MOTES; i++)
        {
            mx[i] += mvx[i] * dt;
            my[i] += mvy[i] * dt;
            if (mx[i] < 0f) mx[i] += 1f; else if (mx[i] > 1f) mx[i] -= 1f;
            if (my[i] < 0f) my[i] += 1f; else if (my[i] > 1f) my[i] -= 1f;
            int x = (int) (mx[i] * w);
            int y = (int) (my[i] * h);
            int al = (int) (0x1C * a);
            g.fill(x, y, x + 1, y + 1, (al << 24) | 0xC8E6F0);
        }
        RenderSystem.disableDepthTest();
    }
}
