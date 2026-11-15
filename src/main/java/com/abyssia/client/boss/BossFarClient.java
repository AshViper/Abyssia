package com.abyssia.client.boss;

import com.abyssia.entity.boss.BossChain;
import com.abyssia.entity.boss.BossFarPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client store of the far boss silhouettes (BOSS01 LOD): the last two snapshots of every boss heard of, interpolated
 * between arrivals (the server sends every 20 ticks). A boss not heard from for 60 ticks is dropped.
 */
public final class BossFarClient
{
    static final int SEND_INTERVAL = 20;
    private static final int TIMEOUT = 60;

    /** Joint positions as x y z triplets: head first, then every {@link BossFarPayload#STEP}th joint. */
    static final class Far
    {
        final int id;
        final int kind;
        double[] prev, cur;
        long curAt, lastRecv;

        Far(int id, int kind, double[] pts, long now)
        {
            this.id = id;
            this.kind = kind;
            this.prev = pts;
            this.cur = pts;
            this.curAt = now;
            this.lastRecv = now;
        }

        /** Interpolated points (x y z each) at game time {@code time} (ticks, fractional). */
        double[] points(double time)
        {
            double t = Math.max(0, Math.min(1, (time - curAt) / SEND_INTERVAL));
            double[] out = new double[cur.length];
            for (int i = 0; i < out.length; i++) out[i] = prev.length == cur.length ? prev[i] + (cur[i] - prev[i]) * t : cur[i];
            return out;
        }
    }

    private static final Map<Integer, Far> BOSSES = new HashMap<>();
    private static ClientLevel level;

    /** Far bosses currently held (dev checks). */
    public static int count() { return BOSSES.size(); }
    private static long combatUntil;

    private BossFarClient() {}

    public static void apply(BossFarPayload msg)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level != level) clear();
        level = mc.level;
        long now = level.getGameTime();
        int m = msg.rel().length / 3;
        double[] pts = new double[3 * (m + 1)];
        pts[0] = msg.hx();
        pts[1] = msg.hy();
        pts[2] = msg.hz();
        for (int i = 0; i < m; i++)
        {
            if (msg.rel()[3 * i] == Short.MIN_VALUE && msg.rel()[3 * i + 1] == Short.MIN_VALUE && msg.rel()[3 * i + 2] == Short.MIN_VALUE)
            {
                // break marker between polylines (the kraken's tentacles)
                pts[3 * (i + 1)] = pts[3 * (i + 1) + 1] = pts[3 * (i + 1) + 2] = Double.NaN;
                continue;
            }
            pts[3 * (i + 1)] = msg.hx() + msg.rel()[3 * i] / BossChain.UNIT;
            pts[3 * (i + 1) + 1] = msg.hy() + msg.rel()[3 * i + 1] / BossChain.UNIT;
            pts[3 * (i + 1) + 2] = msg.hz() + msg.rel()[3 * i + 2] / BossChain.UNIT;
        }
        Far f = BOSSES.get(msg.entityId());
        if (f == null) BOSSES.put(msg.entityId(), new Far(msg.entityId(), msg.kind(), pts, now));
        else
        {
            f.prev = f.cur;
            f.cur = pts;
            f.curAt = now;
            f.lastRecv = now;
        }
        if (msg.combat()) combatUntil = now + TIMEOUT;
    }

    /** Bosses heard of recently (stale ones are dropped). */
    static List<Far> active(ClientLevel lvl)
    {
        if (lvl != level)
        {
            clear();
            return List.of();
        }
        long now = lvl.getGameTime();
        BOSSES.values().removeIf(f -> now - f.lastRecv > TIMEOUT || now < f.lastRecv);
        return new ArrayList<>(BOSSES.values());
    }

    /** True while the server reports a boss fight (a boss bar is up): the deep fog opens up. */
    public static boolean inCombat()
    {
        ClientLevel lvl = Minecraft.getInstance().level;
        return lvl != null && lvl == level && lvl.getGameTime() < combatUntil;
    }

    public static void clear()
    {
        BOSSES.clear();
        level = null;
        combatUntil = 0;
    }
}
