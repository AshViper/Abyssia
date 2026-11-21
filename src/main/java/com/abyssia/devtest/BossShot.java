package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.client.boss.BossFarClient;
import com.abyssia.entity.boss.AbyssBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only (BOSS01): with -Dabyssia.bossshot (gradle runBossShot) the client joins the local test server and stages
 * the serpent scenes around the B' hall point (380 -506 -389): summon with /abyssia boss spawnnear abyss_serpent, then shots near / mid / far.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BossShot
{
    private static final BlockPos ALTAR = new BlockPos(380, -506, -389);
    private static final String BOSS = "@e[type=abyssia:abyss_serpent,limit=1,sort=nearest]";
    private static int t = -1;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e)
    {
        if (e.phase != TickEvent.Phase.END || System.getProperty("abyssia.bossshot") == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) { if (t > 0) mc.setScreen(null); return; }
        t++;
        var p = mc.player;
        if ("idle".equals(System.getProperty("abyssia.bossshot")))
        {
            // the player only stands in the world; the test is driven over RCON
            if (t == 60) { cmd(mc, "gamemode survival"); cmd(mc, "effect give @s minecraft:water_breathing 99999 0 true"); cmd(mc, "effect give @s minecraft:resistance 99999 4 true"); cmd(mc, "effect give @s minecraft:regeneration 99999 4 true"); }
            if (t == 24000) { log("idle done"); mc.stop(); }
            return;
        }
        if ("smoke".equals(System.getProperty("abyssia.bossshot"))) { smokeScenes(mc); return; }
        if ("studio".equals(System.getProperty("abyssia.bossshot"))) { studioScenes(mc); return; }
        if ("scale".equals(System.getProperty("abyssia.bossshot"))) { scaleScenes(mc); return; }
        if ("sever".equals(System.getProperty("abyssia.bossshot")) && t == 70) t = 745; // skip to the kraken scenes
        if ("mother".equals(System.getProperty("abyssia.bossshot")) && t == 70) t = 1225; // skip to the mother scenes
        if ("model".equals(System.getProperty("abyssia.bossshot")) || "sever".equals(System.getProperty("abyssia.bossshot")) || "mother".equals(System.getProperty("abyssia.bossshot"))) { modelScenes(mc); return; }
        switch (t)
        {
            case 60 -> { cmd(mc, "gamemode survival"); cmd(mc, "clear @s"); cmd(mc, "effect clear @s");
                         cmd(mc, "effect give @s minecraft:water_breathing 9999 0 true"); cmd(mc, "effect give @s minecraft:resistance 9999 4 true");
                         cmd(mc, "effect give @s minecraft:regeneration 9999 4 true");
                         cmd(mc, "tp @s 380 -505 -391 facing 380 -506 -389"); }
            case 120 -> { p.getInventory().selected = 0; log("start pos=" + p.blockPosition() + " held=" + p.getMainHandItem()); }
            case 130 -> cmd(mc, "abyssia boss spawnnear abyss_serpent"); // BOSS01: natural spawn 80-120 blocks off (the altar is gone)
            case 150 -> shot(mc, "boss_omen");
            case 255 -> { if ("kill".equals(System.getProperty("abyssia.bossshot"))) { cmd(mc, "damage " + BOSS + " 5000 minecraft:generic"); } }
            case 256 -> { if ("kill".equals(System.getProperty("abyssia.bossshot"))) t = 900; }
            case 960 -> { if ("kill".equals(System.getProperty("abyssia.bossshot"))) { log("kill done"); mc.stop(); } }
            case 260 -> { state(mc, "after summon"); mc.options.hideGui = true; mc.getToasts().clear();  }
            // near: stand 30 blocks off the serpent's head
            case 300 -> cmd(mc, "execute at " + BOSS + " run tp @s ~ ~10 ~30 facing entity " + BOSS);
            case 320 -> { state(mc, "near"); shot(mc, "boss_near"); }
            case 330 -> cmd(mc, "execute at " + BOSS + " run tp @s ~ ~25 ~ facing entity " + BOSS);
            case 345 -> shot(mc, "boss_near_above");
            // mid: 110 blocks off the altar
            case 360 -> cmd(mc, "tp @s 490 -470 -389 facing entity " + BOSS + "");
            case 395 -> cmd(mc, "tp @s 490 -470 -389 facing entity " + BOSS);
            case 400 -> { state(mc, "mid"); shot(mc, "boss_mid"); }
            // far: 250 and 450 blocks off (outside entity tracking, far LOD ribbon)
            case 420 -> cmd(mc, "tp @s 630 -470 -389 facing entity " + BOSS + "");
            case 470 -> cmd(mc, "execute as @e[type=abyssia:abyss_serpent] run data get entity @s Pos");
            case 490 -> cmd(mc, "execute as @e[type=abyssia:abyss_serpent] run data get entity @s Pos");
            case 495 -> cmd(mc, "tp @s 630 -470 -389 facing entity " + BOSS);
            case 500 -> { state(mc, "far250"); shot(mc, "boss_far250"); }
            case 520 -> cmd(mc, "tp @s 830 -470 -389 facing entity " + BOSS + "");
            case 590 -> cmd(mc, "execute as @e[type=abyssia:abyss_serpent] run data get entity @s Pos");
            case 610 -> cmd(mc, "execute as @e[type=abyssia:abyss_serpent] run data get entity @s Pos");
            case 615 -> cmd(mc, "tp @s 830 -470 -389 facing entity " + BOSS);
            case 620 -> { state(mc, "far450"); shot(mc, "boss_far450"); }
            // open ocean serpent (summoned over RCON with tag "ocean" at 313 45 -178): view through water fog
            case 640 -> cmd(mc, "tp @s 373 50 -178 facing 313 45 -178");
            case 660 -> cmd(mc, "summon abyssia:abyss_serpent 313 45 -178 {Tags:[\"ocean\"]}");
            case 700 -> { cmd(mc, "tp @s 373 50 -178 facing 313 45 -178"); }
            case 710 -> { state(mc, "ocean60"); shot(mc, "boss_ocean60"); }
            case 720 -> cmd(mc, "tp @s 513 58 -178 facing 313 45 -178");
            case 795 -> cmd(mc, "tp @s 513 58 -178 facing 313 45 -178");
            case 805 -> { state(mc, "ocean200"); shot(mc, "boss_ocean200"); }
            case 820 -> cmd(mc, "tp @s 713 60 -178 facing 313 45 -178");
            case 895 -> cmd(mc, "tp @s 713 60 -178 facing 313 45 -178");
            case 905 -> { state(mc, "ocean400"); shot(mc, "boss_ocean400"); }
            case 920 -> { log("done"); mc.stop(); }
            default -> {}
        }
    }

    private static void state(Minecraft mc, String tag)
    {
        var bosses = mc.level.getEntitiesOfClass(AbyssBoss.class, mc.player.getBoundingBox().inflate(600));
        String d = bosses.isEmpty() ? "none" : String.format("%.1f", Math.sqrt(bosses.get(0).distanceToSqr(mc.player)));
        log(tag + ": pos=" + mc.player.blockPosition() + " clientBossEntities=" + bosses.size() + " dist=" + d
                + " far=" + BossFarClient.count() + " inCombat=" + BossFarClient.inCombat() + " hp=" + mc.player.getHealth());
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); }

    private static void log(String s) { System.out.println("[BossShot] " + s); }

    private static void shot(Minecraft mc, String name)
    {
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> log("saved " + name));
    }

    private static final String[] STUDIO_IDS = {"abyss_serpent", "trench_wyrm", "abyss_kraken", "abyss_mother", "abyss_angler"};
    private static final java.util.List<Class<? extends AbyssBoss>> STUDIO_TYPES = java.util.List.of(com.abyssia.entity.boss.AbyssSerpent.class,
            com.abyssia.entity.boss.TrenchWyrm.class, com.abyssia.entity.boss.AbyssKraken.class, com.abyssia.entity.boss.AbyssMother.class,
            com.abyssia.entity.boss.AbyssAngler.class);
    /** per boss, four cameras {joint, ahead, side, up}: three lit by night vision, the last one dark (glow only) */
    private static final double[][][] STUDIO_CAMS = {
            {{0, 34, 24, 6}, {4, 0, 42, 8}, {14, 0, 58, 18}, {0, 26, 18, 2}},
            {{0, 30, 26, 8}, {2, 0, 40, 6}, {8, 0, 58, 15}, {0, 24, 18, 2}},
            {{0, 0, 40, 4}, {0, 0, 26, -24}, {0, 0, 75, 20}, {0, 0, 30, 2}},
            {{0, 0, 52, 0}, {0, 0, 30, -30}, {0, 0, 85, 24}, {0, 0, 34, -6}},
            {{0, 60, 70, 15}, {3, 0, 110, 20}, {5, 0, 160, 35}, {0, 50, 60, 5}}};

    /**
     * -PbossShot=studio (BOSS03): model review. Each boss in turn is spawned in the open deep water of t2_o12345, the
     * game is frozen (/tick freeze) and five shots are taken: studio_<id>_1..3 under night vision, studio_<id>_4 dark, studio_<id>_5 from about 110 blocks (night vision).
     */
    private static double studioY = Double.NaN;
    /** joint 0 and the last joint of the boss, as of the last successful {@link #cam} (the client may no longer have the entity) */
    private static Vec3 lastHead, lastTail;
    /** ticks per boss in the studio run (five shots) */
    private static final int STUDIO_PERIOD = 360;

    private static void studioScenes(Minecraft mc)
    {
        if (t == 60)
        {
            cmd(mc, "gamemode creative"); cmd(mc, "effect give @s minecraft:water_breathing 99999 0 true");
            freeze(mc, false); cmd(mc, "tp @s " + SX + " -200 " + SZ); mc.options.hideGui = true;
        }
        int b = (t - 300) / STUDIO_PERIOD, d = (t - 300) % STUDIO_PERIOD;
        if (t < 300) return;
        if (b >= STUDIO_IDS.length) { if (d == 0) { log("studio done"); mc.stop(); } return; }
        String id = STUDIO_IDS[b];
        switch (d)
        {
            case 0 -> { for (String k : STUDIO_IDS) cmd(mc, "kill @e[type=abyssia:" + k + "]"); cmd(mc, "tp @s " + SX + " -200 " + SZ); }
            // a target dummy over the camera, so the boss shows its attack pose (and the wyrm comes out of the floor)
            case 20 -> { cmd(mc, "abyssia boss spawnnear " + id); cmd(mc, "execute positioned ~ ~25 ~ run " + DUMMY); studioY = Double.NaN; lastHead = lastTail = null; }
            case 200 -> { info(mc, id); freeze(mc, true); cmd(mc, "effect give @s minecraft:night_vision 99999 0 true"); }
            case 264 -> cmd(mc, "effect clear @s minecraft:night_vision");
            // shot 5: the camera about 110 blocks from the nearest joint (the mid-range view; the model must look as it does close up)
            case 300 -> cmd(mc, "effect give @s minecraft:night_vision 99999 0 true");
            case 306 ->
            {
                // the client may have lost the entity while the earlier cameras were far away: go back near the head first
                if (lastHead != null) tpFacing(mc, lastHead.add(80, 70, 30), lastHead);
            }
            case 320 -> shot(mc, "studio_" + id + "_5");
            // shot 6 (chain bosses): 40 blocks beside the far end of the body, away from the head (the entity may not be synced: the LOD model)
            case 322 ->
            {
                if ((b == 0 || b == 1 || b == 4) && lastTail != null)
                {
                    Vec3 dir = lastHead == null ? new Vec3(1, 0, 0) : lastTail.subtract(lastHead);
                    Vec3 side = new Vec3(-dir.z, 0, dir.x);
                    side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
                    tpFacing(mc, lastTail.add(side.scale(40)), lastTail);
                }
            }
            case 340 -> { if (b == 0 || b == 1 || b == 4) shot(mc, "studio_" + id + "_6"); }
            case 350 -> { freeze(mc, false); cmd(mc, "kill @e[type=abyssia:" + id + "]"); cmd(mc, "kill @e[tag=shotdummy]"); }
            default -> {
                if (b == 1 && d > 40 && d < 199)
                {
                    // the wyrm: freeze as soon as its head has risen 30 blocks out of the floor
                    var ws = mc.level.getEntitiesOfClass(com.abyssia.entity.boss.TrenchWyrm.class, mc.player.getBoundingBox().inflate(600));
                    if (!ws.isEmpty() && ws.get(0).chain().isInitialised())
                    {
                        double y = ws.get(0).chain().y(0);
                        if (Double.isNaN(studioY) || y < studioY) studioY = y;
                        if (y > studioY + 30) t = 300 + b * STUDIO_PERIOD + 199;
                    }
                }
                for (int i = 0; i < 4; i++)
                {
                    double[] c = STUDIO_CAMS[b][i];
                    if (d == 208 + i * 20) cam(mc, STUDIO_TYPES.get(b), (int) c[0], c[1], c[2], c[3], null);
                    if (d == 222 + i * 20) shot(mc, "studio_" + id + "_" + (i + 1));
                }
            }
        }
    }

    private static final String DUMMY = "summon minecraft:pig ~ ~ ~ {NoAI:1b,NoGravity:1b,Invulnerable:1b,Tags:[\"abyssia_boss_dummy\",\"shotdummy\"]}";

    private static int floorY = Integer.MIN_VALUE;
    private static final int SX = 9400, SZ = 6200; // open deep-layer water in t2_o12345 (ocean_world seed 12345)
    private static int sx = SX, sz = SZ, hops, wyrmShot, serpentShot;

    /**
     * -PbossShot=scale (BOSS02): leviathan-scale check in the deep layer under the ocean (fresh chunks at 3000 3000).
     * The serpent charges a dummy 45 blocks over the seabed, so it passes over the camera on the floor (the reference
     * video's framing); then side shots with the dummy pig as a size reference, then the wyrm erupting from the floor.
     */
    private static void scaleScenes(Minecraft mc)
    {
        String sp = "@e[type=abyssia:abyss_serpent,limit=1,sort=nearest]", wy = "@e[type=abyssia:trench_wyrm,limit=1,sort=nearest]";
        switch (t)
        {
            case 60 -> { cmd(mc, "gamemode creative"); cmd(mc, "effect give @s minecraft:water_breathing 99999 0 true");
                         cmd(mc, "kill @e[type=abyssia:abyss_serpent]"); cmd(mc, "kill @e[type=abyssia:trench_wyrm]"); cmd(mc, "kill @e[tag=shotdummy]");
                         cmd(mc, "tp @s " + SX + " -200 " + SZ); mc.options.hideGui = true; }
            case 400 -> {
                // deep-layer water: the loaded column round the camera with the longest water run below the -88 ceiling
                int bestRun = 0, bx = 0, bz = 0, bf = 0, open = 0, all = 0;
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
                for (int dx = -96; dx <= 96; dx += 8)
                    for (int dz = -96; dz <= 96; dz += 8)
                    {
                        // longest water run below the -88 deep-layer ceiling; y = the block under it
                        int run = 0, y = 0, cur = 0;
                        for (int yy = -89; yy > -570; yy--)
                        {
                            m.set(px + dx, yy, pz + dz);
                            if (mc.level.getFluidState(m).is(net.minecraft.tags.FluidTags.WATER)) { if (++cur > run) { run = cur; y = yy - 1; } }
                            else cur = 0;
                        }
                        all++;
                        if (run >= 180) open++;
                        if (run > bestRun) { bestRun = run; bx = px + dx; bz = pz + dz; bf = y; }
                    }
                log("scale: search at " + px + " " + pz + " open " + open + "/" + all + " best water run " + bestRun + " at " + bx + " " + bz + " floor " + bf);
                if (open * 2 < all && hops < 16) { hops++; t = 250; cmd(mc, "tp @s " + (px + 640) + " -200 " + (pz + 320)); return; }
                sx = bx; sz = bz; floorY = bf;
                cmd(mc, "tp @s " + sx + " " + (floorY + 2) + " " + sz + " facing " + (sx + 150) + " " + (floorY + 60) + " " + sz);
                cmd(mc, "execute positioned " + sx + " " + (floorY + 45) + " " + sz + " run " + DUMMY);
            }
            case 430 -> { cmd(mc, "tp @s " + sx + " " + ((floorY - 89) / 2) + " " + sz);
                          // leftovers from earlier runs only load once the player is here
                          cmd(mc, "kill @e[type=abyssia:abyss_serpent]"); cmd(mc, "kill @e[type=abyssia:trench_wyrm]"); cmd(mc, "kill @e[tag=scalepig]"); }
            case 440 -> cmd(mc, "abyssia boss spawnnear abyss_serpent"); // arena round the player at mid depth
            case 500 -> info(mc, "serpent");
            case 1100 -> info(mc, "wyrm2");
            // frozen (/tick freeze) once the head passes within 70 blocks of the camera column (sx, sz: water from the
            // ceiling down to the floor), then shots from that column at the head, the middle and the tail
            default -> {
                if (t >= 450 && t < 980 && serpentShot == 0 && t % 2 == 0)
                {
                    var ss = mc.level.getEntitiesOfClass(com.abyssia.entity.boss.AbyssSerpent.class, mc.player.getBoundingBox().inflate(600));
                    if (!ss.isEmpty() && ss.get(0).chain().isInitialised())
                    {
                        var c = ss.get(0).chain();
                        double hd = Math.hypot(c.x(0) - sx, c.z(0) - sz);
                        if (t % 40 == 0) log(String.format("serpent head %.0f %.0f %.0f, %.0f from the column", c.x(0), c.y(0), c.z(0), hd));
                        // clear water between the column (at head height) and a point 14 short of the head
                        Vec3 cpos = new Vec3(sx + 0.5, Mth.clamp(c.y(0), floorY + 3, -95), sz + 0.5), hp = new Vec3(c.x(0), c.y(0), c.z(0));
                        boolean los = true;
                        double len = cpos.distanceTo(hp);
                        for (double u = 0; u < len - 14 && los; u += 3) los = water(mc, cpos.add(hp.subtract(cpos).scale(u / len)));
                        if ((hd < 90 && hd > 30 && los) || t == 960) { serpentShot = t; freeze(mc, true); cmd(mc, "effect give @s minecraft:night_vision 99999 0 true"); }
                    }
                }
                if (serpentShot > 0 && t < 980)
                {
                    int d = t - serpentShot;
                    if (d == 6) column(mc, 0, 0, "pig");
                    if (d == 14) { state(mc, "head"); shot(mc, "scale_serpent_head"); }
                    if (d == 20) column(mc, 0, -40, null);
                    if (d == 28) shot(mc, "scale_serpent_head_below");
                    if (d == 34) column(mc, 14, -45, null);
                    if (d == 42) shot(mc, "scale_serpent_below_body");
                    if (d == 48) column(mc, 14, 25, null);
                    if (d == 56) shot(mc, "scale_serpent_body_above");
                    if (d == 62) { cmd(mc, "effect clear @s minecraft:night_vision"); column(mc, 14, -45, null); }
                    if (d == 70) shot(mc, "scale_serpent_below_body_dark");
                    if (d == 76) column(mc, 0, 0, null);
                    if (d == 84) shot(mc, "scale_serpent_head_dark");
                    // let it swim on past the column, then the body passing by
                    if (d == 90) { freeze(mc, false); cmd(mc, "kill @e[tag=scalepig]"); cmd(mc, "effect give @s minecraft:night_vision 99999 0 true"); }
                    if (d == 150) freeze(mc, true);
                    if (d == 156) column(mc, 4, 0, null);
                    if (d == 164) { state(mc, "pass_neck"); shot(mc, "scale_serpent_pass_neck"); }
                    if (d == 170) column(mc, 10, 0, null);
                    if (d == 178) shot(mc, "scale_serpent_pass_body");
                    if (d == 184) column(mc, 10, -30, null);
                    if (d == 192) shot(mc, "scale_serpent_pass_body_below");
                    if (d == 198) { freeze(mc, false); t = 975; }
                }
                wyrmScenes(mc, wy);
            }
            case 980 -> { cmd(mc, "kill @e[type=abyssia:abyss_serpent]");
                          cmd(mc, "tp @s " + (sx + 70) + " " + (floorY + 12) + " " + sz + " facing " + sx + " " + (floorY + 20) + " " + sz); }
            case 990 -> { cmd(mc, "tp @s " + sx + " " + (floorY + 2) + " " + sz); cmd(mc, "kill @e[type=abyssia:trench_wyrm]"); }
            case 1000 -> { cmd(mc, "abyssia boss spawnnear trench_wyrm"); } // creative, not spectator: bosses only hold chunks for non-spectators
            case 1010 -> info(mc, "wyrm");
        }
    }

    /**
     * Camera off serpent joint {@code k}: {@code ahead} along the body (toward the head), {@code side} to its right,
     * {@code up} above; it faces the joint. {@code pig} != null also summons a scale pig 8 off the head's side.
     */
    private static void tpFacing(Minecraft mc, Vec3 p, Vec3 look)
    {
        cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f", p.x, p.y, p.z, look.x, look.y, look.z));
    }

    private static void cam(Minecraft mc, int k, double ahead, double side, double up, String pig)
    {
        cam(mc, com.abyssia.entity.boss.AbyssSerpent.class, k, ahead, side, up, pig);
    }

    private static void cam(Minecraft mc, Class<? extends AbyssBoss> type, int k, double ahead, double side, double up, String pig)
    {
        var ss = mc.level.getEntitiesOfClass(type, mc.player.getBoundingBox().inflate(600));
        if (ss.isEmpty()) { log("cam: no serpent"); return; }
        var c = ss.get(0).chain();
        lastHead = new Vec3(c.x(0), c.y(0), c.z(0));
        lastTail = new Vec3(c.x(c.size() - 1), c.y(c.size() - 1), c.z(c.size() - 1));
        int a = Math.max(0, k - 1), b = Math.min(c.size() - 1, k + 1);
        Vec3 j = new Vec3(c.x(k), c.y(k), c.z(k));
        Vec3 dir = new Vec3(c.x(a) - c.x(b), c.y(a) - c.y(b), c.z(a) - c.z(b)).normalize();
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        // first candidate whose camera block and the block halfway to the joint are water (mirror side, then higher)
        Vec3 p = null;
        for (int tryUp = 0; tryUp < 10 && p == null; tryUp++)
            for (int sign = 1; sign >= -1 && p == null; sign -= 2)
            {
                Vec3 q = j.add(dir.scale(ahead)).add(right.scale(side * sign)).add(0, up + tryUp * 12, 0);
                if (water(mc, q) && water(mc, q.add(0, 1.62, 0)) && water(mc, q.add(0, 2.5, 0)) && water(mc, q.add(j).scale(0.5)) && clear(c, q, j, k)) p = q;
            }
        if (p == null) p = j.add(dir.scale(ahead)).add(right.scale(side)).add(0, up, 0);
        cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f", p.x, p.y, p.z, j.x, j.y, j.z));
        if (pig != null)
        {
            Vec3 q = j.add(dir.scale(4)).add(right.scale(16));
            cmd(mc, String.format(java.util.Locale.ROOT, "summon minecraft:pig %.1f %.1f %.1f {NoAI:1b,NoGravity:1b,Invulnerable:1b,Tags:[\"scalepig\"]}", q.x, q.y, q.z));
        }
        log(String.format(java.util.Locale.ROOT, "cam joint %d at %.0f %.0f %.0f, camera %.0f %.0f %.0f", k, j.x, j.y, j.z, p.x, p.y, p.z));
    }

    /** No other part of the body within 14 blocks of the sight line from the camera to joint k (or of the camera). */
    private static boolean clear(com.abyssia.entity.boss.BossChain c, Vec3 cam, Vec3 j, int k)
    {
        for (int i = 0; i < c.size(); i++)
        {
            if (Math.abs(i - k) <= 2) { if (cam.distanceTo(new Vec3(c.x(i), c.y(i), c.z(i))) < 24) return false; continue; }
            Vec3 p = new Vec3(c.x(i), c.y(i), c.z(i));
            Vec3 d = j.subtract(cam);
            double tt = Math.max(0, Math.min(1, p.subtract(cam).dot(d) / d.lengthSqr()));
            if (cam.add(d.scale(tt)).distanceTo(p) < 14) return false;
        }
        return true;
    }

    private static void wyrmScenes(Minecraft mc, String wy)
    {
                if (t > 1010 && t <= 1800 && t % 10 == 0 && wyrmShot == 0)
                {
                    var ws = mc.level.getEntitiesOfClass(com.abyssia.entity.boss.TrenchWyrm.class, mc.player.getBoundingBox().inflate(600));
                    if (!ws.isEmpty() && t % 40 == 0) log("wyrm head y=" + String.format("%.0f", ws.get(0).getY()) + " floor=" + floorY);
                    if (!ws.isEmpty() && ws.get(0).getY() > floorY + 20) { wyrmShot = t; freeze(mc, true); }
                }
                if (wyrmShot > 0)
                {
                    int d = t - wyrmShot;
                    var W = com.abyssia.entity.boss.TrenchWyrm.class;
                    if (d == 10) column(mc, W, 8, 0, "pig");
                    if (d == 20) { state(mc, "wyrm_level"); shot(mc, "scale_wyrm_level"); }
                    if (d == 30) column(mc, W, 0, 45, null);
                    if (d == 40) shot(mc, "scale_wyrm_above");
                    if (d == 50) { cmd(mc, "effect clear @s minecraft:night_vision"); column(mc, W, 8, 0, null); }
                    if (d == 60) shot(mc, "scale_wyrm_level_dark");
                    if (d == 70) { freeze(mc, false); cmd(mc, "gamemode creative"); t = 1810; }
                }
                if (t == 1820) { cmd(mc, "kill @e[type=abyssia:trench_wyrm]"); cmd(mc, "kill @e[tag=shotdummy]"); log("scale done"); mc.stop(); }
    }

    /**
     * Camera in the water column (sx, sz), {@code up} blocks above serpent joint {@code k}'s height, facing that joint
     * (the column was water from the -88 ceiling to the floor when searched). {@code pig}: a scale pig 16 off the joint.
     */
    private static void column(Minecraft mc, int k, double up, String pig)
    {
        column(mc, com.abyssia.entity.boss.AbyssSerpent.class, k, up, pig);
    }

    private static void column(Minecraft mc, Class<? extends AbyssBoss> type, int k, double up, String pig)
    {
        var ss = mc.level.getEntitiesOfClass(type, mc.player.getBoundingBox().inflate(600));
        if (ss.isEmpty()) { log("column: no serpent"); return; }
        var c = ss.get(0).chain();
        double y = Mth.clamp(c.y(k) + up, floorY + 3, -95);
        cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %d %.1f %d facing %.1f %.1f %.1f", sx, y, sz, c.x(k), c.y(k), c.z(k)));
        if (pig != null)
        {
            Vec3 j = new Vec3(c.x(k), c.y(k), c.z(k));
            Vec3 to = new Vec3(sx - j.x, y - j.y, sz - j.z).normalize();
            Vec3 q = j.add(to.scale(16));
            cmd(mc, String.format(java.util.Locale.ROOT, "summon minecraft:pig %.1f %.1f %.1f {NoAI:1b,NoGravity:1b,Invulnerable:1b,Tags:[\"scalepig\"]}", q.x, q.y, q.z));
        }
        log(String.format(java.util.Locale.ROOT, "column joint %d at %.0f %.0f %.0f, camera %d %.0f %d, distance %.0f", k, c.x(k), c.y(k), c.z(k), sx, y, sz,
                Math.sqrt((c.x(k) - sx) * (c.x(k) - sx) + (c.y(k) - y) * (c.y(k) - y) + (c.z(k) - sz) * (c.z(k) - sz))));
    }

    private static boolean water(Minecraft mc, Vec3 v)
    {
        // plain water only: waterlogged solid blocks would put the in-wall overlay over the whole screen
        return mc.level.getBlockState(BlockPos.containing(v)).is(net.minecraft.world.level.block.Blocks.WATER);
    }

    private static void info(Minecraft mc, String tag)
    {
        for (AbyssBoss b : mc.level.getEntitiesOfClass(AbyssBoss.class, mc.player.getBoundingBox().inflate(600)))
        {
            var c = b.chain();
            log("scale " + tag + ": " + b.getType() + " size=" + b.size() + " bb=" + b.getBbWidth() + " parts=" + b.getParts().length
                    + " partBb=" + b.getParts()[0].getBbWidth() + " joints=" + c.size() + " spacing=" + c.spacing()
                    + " length=" + String.format("%.0f", (c.size() - 1) * c.spacing()));
        }
    }

    /** -PbossShot=model: close shots of the serpent (open water) and the wyrm (B' hall floor) for the model check. */
    private static void modelScenes(Minecraft mc)
    {
        String sp = "@e[type=abyssia:abyss_serpent,limit=1,sort=nearest]", wy = "@e[type=abyssia:trench_wyrm,limit=1,sort=nearest]";
        switch (t)
        {
            case 60 -> { cmd(mc, "gamemode creative"); cmd(mc, "effect give @s minecraft:night_vision 9999 0 true"); cmd(mc, "effect give @s minecraft:water_breathing 9999 0 true");
                         cmd(mc, "kill @e[type=abyssia:abyss_serpent]"); cmd(mc, "kill @e[type=abyssia:trench_wyrm]"); cmd(mc, "kill @e[tag=shotdummy]");
                         cmd(mc, "tp @s 330 40 -178"); mc.options.hideGui = true; }
            case 100 -> { cmd(mc, "execute positioned 313 42 -178 run " + DUMMY); cmd(mc, "summon abyssia:abyss_serpent 300 42 -178"); }
            case 200 -> cmd(mc, "execute at " + sp + " run tp @s ~12 ~4 ~12 facing entity " + sp);
            case 215 -> shot(mc, "model_serpent_head");
            case 240 -> cmd(mc, "execute at " + sp + " run tp @s ~ ~30 ~ facing entity " + sp);
            case 255 -> shot(mc, "model_serpent_above");
            case 280 -> cmd(mc, "tp @s 313 70 -178 facing 313 42 -178");
            case 300 -> shot(mc, "model_serpent_coil");
            case 320 -> cmd(mc, "execute at " + sp + " run tp @s ~-8 ~2 ~ facing entity " + sp);
            case 335 -> shot(mc, "model_serpent_side");
            case 360 -> { cmd(mc, "kill @e[type=abyssia:abyss_serpent]"); cmd(mc, "kill @e[tag=shotdummy]"); cmd(mc, "tp @s 430 -505 -389"); }
            case 420 -> { cmd(mc, "execute positioned 400 -512 -389 run " + DUMMY); cmd(mc, "summon abyssia:trench_wyrm 380 -530 -389"); }
            case 520 -> { cmd(mc, "tp @s 430 -505 -389 facing 400 -512 -389"); }
            case 540 -> shot(mc, "model_wyrm_1");
            case 580 -> cmd(mc, "tp @s 430 -505 -389 facing entity " + wy);
            case 590 -> shot(mc, "model_wyrm_2");
            case 640 -> cmd(mc, "tp @s 430 -505 -389 facing entity " + wy);
            case 650 -> shot(mc, "model_wyrm_3");
            case 700 -> cmd(mc, "tp @s 430 -505 -389 facing entity " + wy);
            case 710 -> shot(mc, "model_wyrm_4");
            case 740 -> { cmd(mc, "kill @e[type=abyssia:trench_wyrm]"); cmd(mc, "kill @e[tag=shotdummy]"); }
            // kraken in the B' hall
            case 760 -> { cmd(mc, "execute positioned 380 -500 -389 run " + DUMMY); cmd(mc, "summon abyssia:abyss_kraken 380 -470 -389"); }
            case 860 -> cmd(mc, "tp @s 380 -470 -350 facing 380 -478 -389");
            case 875 -> shot(mc, "model_kraken_front");
            case 890 -> cmd(mc, "tp @s 345 -465 -389 facing 380 -478 -389");
            case 905 -> shot(mc, "model_kraken_side");
            case 920 -> cmd(mc, "tp @s 380 -505 -360 facing 380 -478 -389");
            case 935 -> shot(mc, "model_kraken_below");
            // sever tentacle 0 with real attacks
            case 950 -> { cmd(mc, "gamemode survival"); cmd(mc, "effect give @s minecraft:resistance 9999 4 true"); cmd(mc, "effect give @s minecraft:strength 9999 9 true");
                          cmd(mc, "item replace entity @s weapon.mainhand with minecraft:netherite_sword"); }
            case 1000, 1030, 1060, 1090, 1120, 1150 -> severHit(mc, 0, true);
            case 1003, 1033, 1063, 1093, 1123, 1153 -> severHit(mc, 0, false);
            case 1165 -> { var ks = mc.level.getEntitiesOfClass(com.abyssia.entity.boss.AbyssKraken.class, mc.player.getBoundingBox().inflate(300));
                           if (!ks.isEmpty()) log("sever result: tentacle0 severed=" + ks.get(0).severed(0) + " count=" + ks.get(0).severedCount()); }
            case 1170 -> { cmd(mc, "data get entity @e[type=abyssia:abyss_kraken,limit=1] Severed"); cmd(mc, "data get entity @e[type=abyssia:abyss_kraken,limit=1] TentacleHP"); cmd(mc, "gamemode creative"); }
            case 1180 -> cmd(mc, "tp @s 345 -465 -389 facing 380 -478 -389");
            case 1195 -> shot(mc, "model_kraken_severed");
            case 1210 -> { cmd(mc, "kill @e[type=abyssia:abyss_kraken]"); cmd(mc, "kill @e[tag=shotdummy]"); if ("sever".equals(System.getProperty("abyssia.bossshot"))) { log("done"); mc.stop(); } }
            // mother in the B' hall
            case 1230 -> { cmd(mc, "execute positioned 380 -500 -420 run " + DUMMY); cmd(mc, "summon abyssia:abyss_mother 380 -470 -389"); }
            case 1380 -> cmd(mc, "tp @s 380 -462 -362 facing entity @e[type=abyssia:abyss_mother,limit=1]");
            case 1395 -> shot(mc, "model_mother_front");
            case 1410 -> cmd(mc, "tp @s 352 -458 -389 facing entity @e[type=abyssia:abyss_mother,limit=1]");
            case 1425 -> shot(mc, "model_mother_side");
            case 1440 -> cmd(mc, "tp @s 380 -505 -389 facing 380 -470 -389");
            case 1455 -> shot(mc, "model_mother_below");
            case 1470 -> cmd(mc, "damage @e[type=abyssia:abyss_mother,limit=1] 1500 minecraft:generic");
            case 1520 -> cmd(mc, "tp @s 355 -480 -389 facing entity @e[type=abyssia:abyss_mother,limit=1]");
            case 1535 -> shot(mc, "model_mother_bloom");
            case 1550 -> { cmd(mc, "kill @e[type=abyssia:abyss_mother]"); cmd(mc, "kill @e[tag=shotdummy]"); log("done"); mc.stop(); }
            default -> {}
        }
    }

    /** Teleports next to a part of the given kraken tentacle (tp=true), then hits it (tp=false). */
    private static void severHit(Minecraft mc, int tentacle, boolean tp)
    {
        var ks = mc.level.getEntitiesOfClass(com.abyssia.entity.boss.AbyssKraken.class, mc.player.getBoundingBox().inflate(300));
        if (ks.isEmpty()) { log("sever: no kraken"); return; }
        for (var part : ks.get(0).getParts())
            if (part instanceof com.abyssia.entity.boss.KrakenPart kp && kp.tentacle() == tentacle)
            {
                if (tp) { var c = kp.getBoundingBox().getCenter(); cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f", c.x + 2.2, c.y - 1.0, c.z, c.x, c.y, c.z)); }
                else { mc.gameMode.attack(mc.player, kp); mc.player.resetAttackStrengthTicker(); log("sever: hit part of tentacle " + tentacle); }
                return;
            }
        log("sever: tentacle part not found");
    }

    /** 1.20.1 has no /tick command: the studio / scale scenes cannot freeze the game, so the boss keeps moving between shots. */
    private static void freeze(Minecraft mc, boolean on) {}

    /** -PbossShot=smoke: each boss summoned in the Forge test hall (134 -507 130) and photographed once (render smoke test). */
    private static void smokeScenes(Minecraft mc)
    {
        String[] bosses = {"abyss_serpent", "trench_wyrm", "abyss_kraken", "abyss_mother", "abyss_angler"};
        if (t == 60) { cmd(mc, "gamemode creative"); cmd(mc, "effect give @s minecraft:night_vision 9999 0 true"); cmd(mc, "effect give @s minecraft:water_breathing 9999 0 true");
                       cmd(mc, "tp @s 134 -480 175 facing 134 -490 130"); mc.options.hideGui = true; return; }
        int i = (t - 100) / 200, k = (t - 100) % 200;
        if (t < 100) return;
        if (i >= bosses.length) { if (k == 0) { log("done"); mc.stop(); } return; }
        String b = bosses[i], sel = "@e[type=abyssia:" + b + ",limit=1]";
        switch (k)
        {
            case 0 -> { cmd(mc, "execute positioned 134 -495 130 run " + DUMMY); cmd(mc, "summon abyssia:" + b + (b.equals("trench_wyrm") ? " 134 -530 130" : " 134 -485 130")); }
            case 120 -> cmd(mc, "tp @s 134 -480 175 facing entity " + sel);
            case 135 -> { var bs = mc.level.getEntitiesOfClass(com.abyssia.entity.boss.AbyssBoss.class, mc.player.getBoundingBox().inflate(300));
                          log("smoke " + b + ": clientEntities=" + bs.size() + " far=" + com.abyssia.client.boss.BossFarClient.count()); shot(mc, "forge_" + b); }
            case 150 -> { cmd(mc, "kill " + sel); cmd(mc, "kill @e[tag=shotdummy]"); }
            default -> {}
        }
    }
}
