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
        if ("giant".equals(System.getProperty("abyssia.bossshot"))) { giantScenes(mc); return; }
        if ("gallery".equals(System.getProperty("abyssia.bossshot"))) { galleryScenes(mc); return; }
        if ("abilities".equals(System.getProperty("abyssia.bossshot"))) { abilityScenes(mc); return; }
        if ("latch".equals(System.getProperty("abyssia.bossshot"))) { latchScenes(mc); return; }
        if ("smoke".equals(System.getProperty("abyssia.bossshot"))) { smokeScenes(mc); return; }
        if ("studio".equals(System.getProperty("abyssia.bossshot"))) { studioScenes(mc); return; }
        if ("serpent".equals(System.getProperty("abyssia.bossshot"))) { serpentScenes(mc); return; }
        if ("far".equals(System.getProperty("abyssia.bossshot"))) { farScenes(mc); return; }
        if ("fog".equals(System.getProperty("abyssia.bossshot"))) { fogScenes(mc); return; }
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

    private static final String[] GALLERY = System.getProperty("abyssia.giantshot.id") != null ? System.getProperty("abyssia.giantshot.id").split(",")
            : new String[] {"lancejaw", "lumenwing", "lantern_grazer", "hush_whale", "slateback", "rift_lamprey", "shroud_ray", "glassback", "crestfin", "duskmaw"};
    private static final int GALLERY_PERIOD = 330;

    /**
     * -PbossShot=gallery (GNT01): each giant in turn in open water at SX -200 SZ, creative + night vision. Spawned,
     * left to swim 120 ticks, then the game is frozen and two shots are taken: gallery_<id>_side (beside the middle of
     * the body at about 0.9 x its length) and gallery_<id>_near (15 blocks off the head, 30 degrees ahead).
     */
    private static void galleryScenes(Minecraft mc)
    {
        if (t == 60) { cmd(mc, "gamemode creative"); cmd(mc, "effect give @s minecraft:night_vision 99999 0 true"); cmd(mc, "tick unfreeze"); cmd(mc, "tp @s " + SX + " -200 " + SZ); mc.options.hideGui = true; }
        if (t < 200) return;
        int b = (t - 200) / GALLERY_PERIOD, d = (t - 200) % GALLERY_PERIOD;
        if (b >= GALLERY.length) { if (d == 0) { cmd(mc, "tick unfreeze"); log("gallery done"); mc.stop(); } return; }
        String id = GALLERY[b];
        switch (d)
        {
            case 0 -> { for (String k : GALLERY) cmd(mc, "kill @e[type=abyssia:" + k + "]"); cmd(mc, "tick unfreeze"); cmd(mc, "tp @s " + SX + " -200 " + SZ); }
            case 20, 30, 40, 50, 60, 70, 80, 90 -> cmd(mc, "execute unless entity @e[type=abyssia:" + id + "] run abyssia giants spawn " + id);
            case 140 -> cmd(mc, "tick freeze");
            case 150, 210, 270 -> {
                var gs = mc.level.getEntitiesOfClass(com.abyssia.entity.giant.GiantFauna.class, mc.player.getBoundingBox().inflate(300));
                if (gs.isEmpty()) { log("gallery " + id + " not found on the client"); return; }
                var g = gs.get(0);
                var ch = g.chain();
                int n = ch.size();
                Vec3 head = new Vec3(ch.x(0), ch.y(0), ch.z(0)), tail = new Vec3(ch.x(n - 1), ch.y(n - 1), ch.z(n - 1));
                Vec3 mid = head.add(tail).scale(0.5), dir = tail.subtract(head);
                double len = dir.length();
                dir = len < 1e-3 ? new Vec3(1, 0, 0) : dir.normalize();
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
                if (d == 150) tpFacing(mc, mid.add(side.scale(len * 0.9)).add(0, len * 0.25, 0), mid);
                else if (d == 270) tpFacing(mc, head.add(side.scale(100)), head);
                else tpFacing(mc, head.add(side.scale(13)).subtract(dir.scale(8)).add(0, 5, 0), head.add(dir.scale(4)));
                log("gallery " + id + " length " + String.format("%.1f", len) + " joints " + n);
            }
            case 175 -> shot(mc, "gallery_" + id + "_side");
            case 235 -> shot(mc, "gallery_" + id + "_near");
            case 295 -> shot(mc, "gallery_" + id + "_far");
            default -> {}
        }
    }

    /** Y of the natural-spawn wait in {@link #giantScenes} (set over RCON with -Dabyssia.giantshot.y, default -300). */
    private static final int GIANT_WAIT_Y = Integer.getInteger("abyssia.giantshot.y", -280);
    /** Species of the giant scene (-Dabyssia.giantshot.id, default lancejaw). */
    private static final String GIANT_ID = System.getProperty("abyssia.giantshot.id", "lancejaw");
    private static final String GIANT = "@e[type=abyssia:" + GIANT_ID + ",limit=1,sort=nearest]";

    /**
     * -PbossShot=giant (GNT01): one giant species (-Dabyssia.giantshot.id, default lancejaw). Part 1 (t 100-1000): spawned 40-80 blocks off a survival
     * player at SX -200 SZ; shots from the player (facing it) and from 30 blocks beside its head, the player's health
     * logged so the hostile AI shows up as damage. Part 2 (t 1100-7300): the giants are killed, the player waits at
     * Y {@link #GIANT_WAIT_Y} with creative flight, and the natural spawner is counted every 600 ticks.
     */
    private static void giantScenes(Minecraft mc)
    {
        var p = mc.player;
        switch (t)
        {
            case 60 -> { cmd(mc, "gamemode survival"); cmd(mc, "clear @s"); cmd(mc, "effect clear @s");
                         cmd(mc, "effect give @s minecraft:water_breathing 99999 0 true"); cmd(mc, "effect give @s minecraft:night_vision 99999 0 true");
                         cmd(mc, "effect give @s minecraft:regeneration 99999 2 true"); cmd(mc, "effect give @s minecraft:resistance 99999 2 true");
                         cmd(mc, "kill @e[type=abyssia:" + GIANT_ID + "]"); cmd(mc, "tp @s " + SX + " -200 " + SZ); mc.options.hideGui = true; }
            case 100 -> cmd(mc, "abyssia giants spawn " + GIANT_ID);
            case 1000 -> { cmd(mc, "kill @e[type=abyssia:" + GIANT_ID + "]"); cmd(mc, "gamemode creative"); }
            case 1100 -> { cmd(mc, "tp @s " + (SX + 600) + " " + GIANT_WAIT_Y + " " + SZ); log("giant wait start y=" + GIANT_WAIT_Y); }
            case 7300 -> { log("giant done"); mc.stop(); }
            default -> {}
        }
        if (t > 100 && t < 1000)
        {
            if (t % 20 == 0) giantState(mc, "near");
            if (t == 200 || t == 400 || t == 600 || t == 800) cmd(mc, "tp @s ~ ~ ~ facing entity " + GIANT);
            if (t == 210 || t == 410 || t == 610 || t == 810) shot(mc, "giant_" + GIANT_ID + "_player_" + (t / 200));
            if (t == 300 || t == 700)
            {
                var gs = mc.level.getEntitiesOfClass(com.abyssia.entity.giant.GiantFauna.class, p.getBoundingBox().inflate(200));
                if (!gs.isEmpty())
                {
                    Vec3 h = gs.get(0).position();
                    tpFacing(mc, h.add(30, 6, 8), h);
                }
            }
            if (t == 320 || t == 720) shot(mc, "giant_" + GIANT_ID + "_side_" + (t / 300));
        }
        if (t >= 1100 && (t - 1100) % 600 == 0) { giantState(mc, "wait"); cmd(mc, "abyssia giants list"); cmd(mc, "abyssia giants stats"); }
    }

    // ------------------------------------------------------------------ latch (GNT01): peel / pull / release checks
    private static final int LA0 = 100, LALEN = 700;
    private static final String[][] LA_SCENES = {{"rift_lamprey", "hit"}, {"duskmaw", "hit"}, {"duskmaw", "lights"}, {"duskmaw", "pull"}, {"rift_lamprey", "killgiant"}, {"duskmaw", "killsub"}};
    private static int laHoldT, laHits, laNextHit;
    private static boolean laHeld, laReleased;
    private static Vec3 laSub0 = Vec3.ZERO, laHead0 = Vec3.ZERO;

    /** -PbossShot=latch: a survival rider on a submarine; per scene a giant takes hold and the rider peels / the hold is cut. Logged as [BossShot] latch. */
    private static void latchScenes(Minecraft mc)
    {
        var p = mc.player;
        int end = LA0 + LALEN * LA_SCENES.length;
        if (t >= end) { if (t == end) { log("latch all done"); mc.stop(); } return; }
        if (t < LA0) return;
        int u = (t - LA0) % LALEN;
        String id = LA_SCENES[(t - LA0) / LALEN][0], mode = LA_SCENES[(t - LA0) / LALEN][1];
        String tag = "latch " + id + "/" + mode + " u=" + u + " ";
        switch (u)
        {
            case 0 -> { resetPlayer(mc, false); laHeld = laReleased = false; laHits = 0; laNextHit = 0; log(tag + "begin"); }
            case 3 -> { cmd(mc, "clear @s"); cmd(mc, "give @s minecraft:stone_sword"); cmd(mc, "attribute @s minecraft:player.entity_interaction_range base set 12"); }
            case 10 -> cmd(mc, "summon abyssia:submarine " + SX + " -200 " + SZ);
            case 20 -> cmd(mc, "ride @s mount @e[type=abyssia:submarine,limit=1,sort=nearest]");
            case 30 -> cmd(mc, "data merge entity @e[type=abyssia:submarine,limit=1,sort=nearest] {Energy:1000000,Lights:1b}");
            case 45 -> { p.getInventory().selected = 0; log(tag + "mounted=" + (p.getVehicle() instanceof com.abyssia.vehicle.Submarine) + " " + subState(firstSub(mc))); }
            case 50 -> cmd(mc, "abyssia giants spawn " + id);
            case 80 ->
            {
                var g = firstGiant(mc);
                if (g == null) { log(tag + "giant not on client"); return; }
                Vec3 h = new Vec3(g.chain().x(0), g.chain().y(0), g.chain().z(0));
                cmd(mc, String.format(java.util.Locale.ROOT, "tp @e[type=abyssia:submarine,limit=1,sort=nearest] %.1f %.1f %.1f", h.x + 30, h.y, h.z));
            }
            default -> {}
        }
        if (t - LA0 < LALEN && u == 97)
        {
            var fg = firstGiant(mc);
            if (fg != null)
            {
                Vec3 h = new Vec3(fg.chain().x(0), fg.chain().y(0), fg.chain().z(0));
                cmd(mc, "ride @s dismount");
                tpFacing(mc, h.add(100, 0, 0), h);
                log(tag + "far shot: player 100 blocks from head " + h);
            }
        }
        if (t - LA0 < LALEN && u == 130) shot(mc, "abilities_far");
        if (u == LALEN - 5) { log(tag + "end held="+ laHeld + " released=" + laReleased + " hits=" + laHits); return; }
        if (u < 95) return;
        var g = firstGiant(mc);
        var s = firstSub(mc);
        Vec3 head = g == null ? Vec3.ZERO : new Vec3(g.chain().x(0), g.chain().y(0), g.chain().z(0));
        boolean slowed = s != null && s.giantSlowed();
        if (!laHeld && !laReleased && slowed)
        {
            laHeld = true; laHoldT = u; laHits = 0; laNextHit = u + 10; laSub0 = s.position(); laHead0 = head;
            log(tag + "HOLD start sub=" + s.position() + " head=" + head + " lights=" + s.lights());
        }
        if (!laHeld) { if (u % 40 == 15) log(tag + "waiting headToSub=" + (g == null || s == null ? -1 : String.format("%.1f", head.distanceTo(s.position()))) + " " + subState(s)); return; }
        int k = u - laHoldT;
        if (!laReleased && !slowed && k > 2)
        {
            laReleased = true;
            log(tag + "RELEASED k=" + k + " hits=" + laHits + " giantHp=" + (g == null ? "gone" : g.getHealth()) + " " + subState(s) + " subMoved=" + (s == null ? "-" : String.format("%.1f", s.position().distanceTo(laSub0))));
        }
        switch (mode)
        {
            case "hit" ->
            {
                if (!laReleased && g != null && u >= laNextHit)
                {
                    var best = g.getParts()[0];
                    for (var pt : g.getParts()) if (pt.position().distanceToSqr(p.getEyePosition()) < best.position().distanceToSqr(p.getEyePosition())) best = pt;
                    float before = g.getHealth();
                    mc.gameMode.attack(p, best);
                    p.swing(InteractionHand.MAIN_HAND);
                    laHits++; laNextHit = u + 14;
                    log(tag + "attack #" + laHits + " partDist=" + String.format("%.1f", Math.sqrt(best.position().distanceToSqr(p.getEyePosition()))) + " hpBefore=" + before + " slowed=" + slowed);
                }
            }
            case "lights" ->
            {
                if (k == 10) { cmd(mc, "data merge entity @e[type=abyssia:submarine,limit=1,sort=nearest] {Lights:0b}"); log(tag + "lights off sent"); }
                if (k > 10 && k % 5 == 0 && k < 60 && !laReleased) log(tag + "k=" + k + " slowed=" + slowed + " " + subState(s));
            }
            case "pull" ->
            {
                if (!laReleased && k % 20 == 0 && s != null)
                {
                    Vec3 hd = head.subtract(laHead0);
                    double along = hd.lengthSqr() < 1e-4 ? 0 : s.position().subtract(laSub0).dot(hd.normalize());
                    log(tag + "pull k=" + k + " sub=" + s.position() + " subMoved=" + String.format("%.1f", s.position().distanceTo(laSub0)) + " headMoved=" + String.format("%.1f", hd.length())
                            + " headToSub=" + String.format("%.1f", head.distanceTo(s.position())) + " subAlongHeadMove=" + String.format("%.1f", along));
                }
            }
            case "killgiant" ->
            {
                if (k == 20) { cmd(mc, "kill @e[type=abyssia:" + id + "]"); log(tag + "kill giant sent " + subState(s)); }
                if (k > 20 && k <= 80 && k % 10 == 0) log(tag + "k=" + k + " giants=" + mc.level.getEntitiesOfClass(com.abyssia.entity.giant.GiantFauna.class, p.getBoundingBox().inflate(300)).size() + " " + subState(s));
            }
            case "killsub" ->
            {
                if (k == 20) { laHead0 = head; cmd(mc, "kill @e[type=abyssia:submarine]"); log(tag + "kill sub sent head=" + head); }
                if (k > 20 && k <= 220 && k % 20 == 0) log(tag + "k=" + k + " giantAlive=" + (g != null) + " head=" + head + " headMoved=" + String.format("%.1f", head.distanceTo(laHead0)) + " subs=" + mc.level.getEntitiesOfClass(com.abyssia.vehicle.Submarine.class, p.getBoundingBox().inflate(300)).size());
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ abilities (GNT01)
    private static long lastJam = -1, lastOverlayT = -1;
    private static String lastOverlay = "";
    private static boolean sawDark, sawPoison, shotTaken;
    private static double minD, maxD;
    private static float lastDmg;
    private static final int AB_SUB0 = 900, AB_SUBLEN = 800, AB_DROP0 = 3800, AB_NAT0 = 5850;
    private static final String[] AB_SUBS = {"hush_whale", "duskmaw", "lantern_grazer"};
    private static final String[] AB_DROPS = {"lancejaw", "hush_whale", "slateback", "rift_lamprey", "shroud_ray", "glassback", "crestfin", "duskmaw"};

    @SubscribeEvent
    public static void chat(net.minecraftforge.client.event.ClientChatReceivedEvent.System e)
    {
        if (!"abilities".equals(System.getProperty("abyssia.bossshot")) || t < 0) return;
        String s = e.getMessage().getString();
        if (e.isOverlay())
        {
            String key = e.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : "";
            if (key.endsWith("submarine.jammed")) lastJam = t;
            if (key.endsWith("giant.shear_wait")) { lastOverlay = s; lastOverlayT = t; }
            return;
        }
        var mc = Minecraft.getInstance();
        log("chat t=" + t + ": " + s);
        var m = java.util.regex.Pattern.compile("^(\\w+) #\\d+ .* @ (-?\\d+) (-?\\d+) (-?\\d+)").matcher(s);
        if (m.find() && mc.player != null)
        {
            double d = Math.sqrt(mc.player.distanceToSqr(Integer.parseInt(m.group(2)) + 0.5, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)) + 0.5));
            String temper = "?";
            for (var sp : com.abyssia.entity.giant.GiantSpecies.SPECS) if (sp.id().equals(m.group(1))) temper = sp.temper().toString();
            log("natural t=" + t + " " + m.group(1) + " " + temper + " dist=" + String.format("%.0f", d));
        }
    }

    private static String effects(Minecraft mc)
    {
        StringBuilder b = new StringBuilder();
        for (var ei : mc.player.getActiveEffects()) b.append(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(ei.getEffect()).getPath()).append(ei.getAmplifier()).append(':').append(ei.getDuration()).append(' ');
        return b.toString().trim();
    }

    private static com.abyssia.entity.giant.GiantFauna firstGiant(Minecraft mc)
    {
        var gs = mc.level.getEntitiesOfClass(com.abyssia.entity.giant.GiantFauna.class, mc.player.getBoundingBox().inflate(300));
        return gs.isEmpty() ? null : gs.get(0);
    }

    private static com.abyssia.vehicle.Submarine firstSub(Minecraft mc)
    {
        var ss = mc.level.getEntitiesOfClass(com.abyssia.vehicle.Submarine.class, mc.player.getBoundingBox().inflate(300));
        return ss.isEmpty() ? null : ss.get(0);
    }

    private static int count(Minecraft mc, String item)
    {
        int n = 0;
        for (var st : mc.player.getInventory().items) if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals(item)) n += st.getCount();
        for (var ie : mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, mc.player.getBoundingBox().inflate(40)))
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ie.getItem().getItem()).getPath().equals(item)) n += ie.getItem().getCount();
        return n;
    }

    private static void resetPlayer(Minecraft mc, boolean nightVision)
    {
        cmd(mc, "gamemode survival"); cmd(mc, "effect clear @s");
        cmd(mc, "effect give @s minecraft:water_breathing 99999 0 true"); cmd(mc, "effect give @s minecraft:resistance 99999 3 true");
        cmd(mc, "effect give @s minecraft:regeneration 99999 2 true");
        if (nightVision) cmd(mc, "effect give @s minecraft:night_vision 99999 0 true");
        cmd(mc, "kill @e[type=item]");
        for (var sp : com.abyssia.entity.giant.GiantSpecies.SPECS) cmd(mc, "kill @e[type=abyssia:" + sp.id() + "]");
        cmd(mc, "kill @e[type=abyssia:submarine]");
        cmd(mc, "tp @s " + SX + " -200 " + SZ);
    }

    /**
     * -PbossShot=abilities (GNT01): player-dependent checks. A shears harvest of the lumenwing (60-145), a shroud ray
     * (250-880), submarine scenes with a hush whale / duskmaw / lantern grazer (AB_SUB0, 800 ticks each, the sub is
     * tp'd 30 blocks from the giant at +80), drops of eight giants killed by the player (AB_DROP0, 250 each) and the
     * natural spawner counted from AB_NAT0 on at Y -280 (6000 ticks, list+stats every 600). Everything is logged as [BossShot].
     */
    private static void abilityScenes(Minecraft mc)
    {
        var p = mc.player;
        if (t == 60) { resetPlayer(mc, true); cmd(mc, "clear @s"); cmd(mc, "give @s minecraft:shears"); mc.options.hideGui = true; }
        // ---- 1: lumenwing shears
        if (t == 100) cmd(mc, "abyssia giants spawn lumenwing");
        if (t == 140)
        {
            var g = firstGiant(mc);
            if (g == null) { log("shear: no lumenwing on client"); return; }
            var parts = g.getParts();
            var part = parts[Math.min(2, parts.length - 1)];
            tpFacing(mc, part.position(), part.position().add(1, 0, 0));
            p.getInventory().selected = 0;
        }
        if (t == 150)
        {
            var g = firstGiant(mc);
            if (g == null) return;
            shearBase = count(mc, "lumen_filament");
            var parts = g.getParts();
            var r = mc.gameMode.interact(p, parts[Math.min(2, parts.length - 1)], InteractionHand.MAIN_HAND);
            log("shear #1 part-interact result=" + r + " held=" + p.getMainHandItem().getItem() + " filament before=" + shearBase + " partsOnClient=" + parts.length);
        }
        if (t == 160) log("shear #1 filament after=" + count(mc, "lumen_filament") + " (delta " + (count(mc, "lumen_filament") - shearBase) + ")");
        if (t == 165)
        {
            var g = firstGiant(mc);
            if (g == null) return;
            shearBase = count(mc, "lumen_filament");
            var r = mc.gameMode.interact(p, g.getParts()[Math.min(2, g.getParts().length - 1)], InteractionHand.MAIN_HAND);
            log("shear #2 part-interact result=" + r + " filament before=" + shearBase);
        }
        if (t == 175) log("shear #2 filament after=" + count(mc, "lumen_filament") + " (delta " + (count(mc, "lumen_filament") - shearBase) + ") overlay=\"" + lastOverlay + "\" at t=" + lastOverlayT);
        if (t == 185)
        {
            var g = firstGiant(mc);
            if (g != null)
            {
                var ch = g.chain();
                int n = ch.size();
                Vec3 head = new Vec3(ch.x(0), ch.y(0), ch.z(0)), tail = new Vec3(ch.x(n - 1), ch.y(n - 1), ch.z(n - 1));
                Vec3 mid = head.add(tail).scale(0.5), dir = tail.subtract(head);
                dir = dir.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : dir.normalize();
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
                tpFacing(mc, mid.add(side.scale(55)).add(0, 14, 0), mid);
            }
        }
        if (t == 205) shot(mc, "abilities_shear");
        // ---- 2: shroud ray on a survival player
        if (t == 250) { resetPlayer(mc, false); sawDark = sawPoison = shotTaken = false; minD = 1e9; lastDmg = p.getHealth(); }
        if (t == 270) cmd(mc, "abyssia giants spawn shroud_ray");
        if (t == 290)
        {
            var g = firstGiant(mc);
            if (g != null) cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f", g.chain().x(0) + 30, g.chain().y(0), g.chain().z(0)));
        }
        if (t >= 280 && t <= 900 && t % 20 == 0)
        {
            var g = firstGiant(mc);
            double d = g == null ? -1 : Math.sqrt(new Vec3(g.chain().x(0), g.chain().y(0), g.chain().z(0)).distanceToSqr(p.position()));
            if (d >= 0) minD = Math.min(minD, d);
            boolean dk = p.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS), po = p.hasEffect(net.minecraft.world.effect.MobEffects.POISON);
            sawDark |= dk; sawPoison |= po;
            log("shroud t=" + t + " headDist=" + String.format("%.1f", d) + " hp=" + p.getHealth() + " effects=[" + effects(mc) + "]");
            if (dk && !shotTaken) { shotTaken = true; shot(mc, "abilities_shroud"); }
        }
        if (t == 905) log("shroud summary sawDarkness=" + sawDark + " sawPoison=" + sawPoison + " minHeadDist=" + String.format("%.1f", minD));
        // ---- 3: submarine scenes
        if (t >= AB_SUB0 && t < AB_SUB0 + AB_SUBLEN * AB_SUBS.length) subScene(mc, AB_SUBS[(t - AB_SUB0) / AB_SUBLEN], (t - AB_SUB0) % AB_SUBLEN);
        // ---- 4: drops
        if (t >= AB_DROP0 && t < AB_DROP0 + 250 * AB_DROPS.length)
        {
            String id = AB_DROPS[(t - AB_DROP0) / 250];
            int d = (t - AB_DROP0) % 250;
            if (d == 0) resetPlayer(mc, true);
            if (d == 20) cmd(mc, "abyssia giants spawn " + id);
            if (d == 60) { var g = firstGiant(mc); log("drop " + id + " before kill: giantOnClient=" + (g != null)); cmd(mc, "damage @e[type=abyssia:" + id + ",limit=1,sort=nearest] 9999 minecraft:player_attack by @p"); }
            if (d == 160)
            {
                var sums = new java.util.TreeMap<String, Integer>();
                for (var ie : mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(400)))
                    sums.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ie.getItem().getItem()).getPath(), ie.getItem().getCount(), Integer::sum);
                log("drop " + id + " items=" + sums + " giantsLeft=" + mc.level.getEntitiesOfClass(com.abyssia.entity.giant.GiantFauna.class, p.getBoundingBox().inflate(300)).size());
            }
        }
        // ---- 5: natural spawn
        if (t == 215 && System.getenv("ABY_AB_NAT_ONLY") != null) t = AB_NAT0 - 1; // run only the shear + natural-spawn parts
        // survival with resistance: the natural spawner skips creative / spectator players
        if (t == AB_NAT0) { resetPlayer(mc, false); cmd(mc, "tp @s 10000 -280 6200"); log("natural start"); }
        if (t > AB_NAT0 && t % 50 == 0 && t <= AB_NAT0 + 6000) cmd(mc, "tp @s 10000 -280 6200");
        if (t > AB_NAT0 && t <= AB_NAT0 + 6000 && (t - AB_NAT0) % 100 == 0)
        {
            var gs = mc.level.getEntitiesOfClass(com.abyssia.entity.giant.GiantFauna.class, p.getBoundingBox().inflate(300));
            StringBuilder b = new StringBuilder();
            for (var g : gs) b.append(g.spec().id()).append('/').append(g.spec().temper()).append('@').append(String.format("%.0f", Math.sqrt(g.distanceToSqr(p)))).append(' ');
            log("natural client t=" + (t - AB_NAT0) + " giants=" + gs.size() + " [" + b.toString().trim() + "] pos=" + p.blockPosition());
        }
        if (t > AB_NAT0 && (t - AB_NAT0) % 600 == 0 && t <= AB_NAT0 + 6000) { cmd(mc, "abyssia giants list"); }
        if (t > AB_NAT0 && (t - AB_NAT0) % 600 == 5 && t <= AB_NAT0 + 6000) { cmd(mc, "abyssia giants stats"); }
        if (t == AB_NAT0 + 6100) { log("abilities done"); mc.stop(); }
    }

    private static int shearBase;

    private static void subScene(Minecraft mc, String id, int u)
    {
        var p = mc.player;
        boolean graze = id.equals("lantern_grazer");
        switch (u)
        {
            case 0 -> { resetPlayer(mc, false); log("subscene " + id + " begin"); sawDark = shotTaken = false; minD = 1e9; maxD = 0; lastDmg = 0; }
            case 10 -> cmd(mc, "summon abyssia:submarine " + SX + " -200 " + SZ);
            case 20 -> cmd(mc, "ride @s mount @e[type=abyssia:submarine,limit=1,sort=nearest]");
            case 30 -> cmd(mc, "data merge entity @e[type=abyssia:submarine,limit=1,sort=nearest] {Energy:" + (id.equals("duskmaw") ? 2500 : 1000000) + ",Lights:1b}");
            case 45 -> { var s = firstSub(mc); log(id + " mounted=" + (p.getVehicle() instanceof com.abyssia.vehicle.Submarine) + " " + subState(s)); }
            case 50 -> cmd(mc, "abyssia giants spawn " + id);
            case 80 ->
            {
                var g = firstGiant(mc);
                if (g == null) { log(id + " giant not on client"); return; }
                Vec3 h = new Vec3(g.chain().x(0), g.chain().y(0), g.chain().z(0));
                log(id + " giant head=" + h + " tp sub to 30 blocks off");
                cmd(mc, String.format(java.util.Locale.ROOT, "tp @e[type=abyssia:submarine,limit=1,sort=nearest] %.1f %.1f %.1f", h.x + 30, h.y, h.z));
            }
            default -> {}
        }
        if (u >= 95 && u % 20 == 15)
        {
            var g = firstGiant(mc);
            var s = firstSub(mc);
            double d = g == null || s == null ? -1 : Math.sqrt(new Vec3(g.chain().x(0), g.chain().y(0), g.chain().z(0)).distanceToSqr(s.position()));
            if (d >= 0) { minD = Math.min(minD, d); if (!graze || u > 300) maxD = Math.max(maxD, d); }
            log(id + " u=" + u + " headToSub=" + String.format("%.1f", d) + " " + subState(s) + " jamAt=" + lastJam + " vehicle=" + (p.getVehicle() != null) + " effects=[" + effects(mc) + "]");
            if (id.equals("duskmaw") && d >= 0 && d < 12 && !shotTaken) { shotTaken = true; shot(mc, "abilities_duskmaw"); }
            if (s != null && s.getDamage() > lastDmg + 0.01f) { log(id + " hull damage rose " + lastDmg + " -> " + s.getDamage()); lastDmg = s.getDamage(); }
        }
        if (u == AB_SUBLEN - 10) log(id + " summary minHeadToSub=" + String.format("%.1f", minD) + (graze ? " maxAfter300=" + String.format("%.1f", maxD) : "") + " jamAt=" + lastJam);
    }

    private static String subState(com.abyssia.vehicle.Submarine s)
    {
        if (s == null) return "sub=none";
        return "energy=" + s.getEnergy() + "/" + s.maxEnergy() + " damage=" + s.getDamage() + " hull%=" + s.hullPercent() + " lights=" + s.lights() + " slowed=" + s.giantSlowed() + " lamp=" + s.lampStrength();
    }

    private static void giantState(Minecraft mc, String tag)
    {
        var gs = mc.level.getEntitiesOfClass(com.abyssia.entity.giant.GiantFauna.class, mc.player.getBoundingBox().inflate(200));
        String d = gs.isEmpty() ? "none" : String.format("%.1f", Math.sqrt(gs.get(0).distanceToSqr(mc.player)));
        log("giant " + tag + " t=" + t + " pos=" + mc.player.blockPosition() + " giants=" + gs.size() + " dist=" + d + " hp=" + mc.player.getHealth());
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

    // ---- BOSS02 review modes: serpent (whole body), far (LOD beyond the fog), fog (colour investigation)

    private static Vec3 serpH, serpT;

    /** Entity / far-LOD counts for the log, as seen by this client. */
    private static String counts(Minecraft mc)
    {
        int n = mc.level.getEntitiesOfClass(AbyssBoss.class, mc.player.getBoundingBox().inflate(900)).size();
        return "clientEntities=" + n + " far=" + BossFarClient.count() + " inCombat=" + BossFarClient.inCombat();
    }

    private static void devtestSetup(Minecraft mc)
    {
        cmd(mc, "gamemode creative"); cmd(mc, "effect give @s minecraft:water_breathing 99999 0 true");
        cmd(mc, "effect give @s minecraft:night_vision 99999 0 true");
        for (String k : STUDIO_IDS) cmd(mc, "kill @e[type=abyssia:" + k + "]");
        cmd(mc, "kill @e[tag=shotdummy]");
        mc.options.renderDistance().set(32); // devtest only: the far shots are not capped by the render distance
        mc.options.hideGui = true;
    }

    /** Refreshes the serpent's head and tail from the client entity when it is synced; otherwise keeps the last (or assumed) ones. */
    private static void serpAnchor(Minecraft mc)
    {
        var ss = mc.level.getEntitiesOfClass(com.abyssia.entity.boss.AbyssSerpent.class, mc.player.getBoundingBox().inflate(900));
        if (ss.isEmpty() || !ss.get(0).chain().isInitialised()) return;
        var c = ss.get(0).chain();
        serpH = new Vec3(c.x(0), c.y(0), c.z(0));
        serpT = new Vec3(c.x(c.size() - 1), c.y(c.size() - 1), c.z(c.size() - 1));
    }

    private static final String[] SERP_SHOTS = {"head_side", "head_front", "side_whole", "oblique", "above", "tail"};

    /**
     * -PbossShot=serpent: a NoAI serpent (it stays put, the body lies straight; /tick freeze does not exist on 1.20.1) in the studio water,
     * six shots serpent_1..6: head from the side / front (entity synced), then the whole body from 150-250 blocks (far LOD), the tail end.
     */
    private static void serpentScenes(Minecraft mc)
    {
        if (t == 60) { devtestSetup(mc); cmd(mc, "tp @s " + SX + " -200 " + SZ); }
        if (t == 120)
        {
            cmd(mc, "summon abyssia:abyss_serpent " + SX + ".5 -200 " + SZ + ".5 {NoAI:1b,Tags:[\"bossshot\"]}");
            serpH = new Vec3(SX + 0.5, -200, SZ + 0.5); serpT = serpH.add(0, 0, -390); // assumed until the entity is seen
        }
        if (t == 125) cmd(mc, "tp @s " + (SX + 32) + " -192 " + SZ + " facing " + SX + " -200 " + SZ);
        if (t == 150)
        {
            serpAnchor(mc);
            log(String.format(java.util.Locale.ROOT, "serpent anchors head=%.0f %.0f %.0f tail=%.0f %.0f %.0f length=%.0f %s",
                    serpH.x, serpH.y, serpH.z, serpT.x, serpT.y, serpT.z, serpH.distanceTo(serpT), counts(mc)));
        }
        if (t < 160) return;
        int i = (t - 160) / 50, d = (t - 160) % 50;
        if (i >= SERP_SHOTS.length) { if (d == 0) { cmd(mc, "kill @e[type=abyssia:abyss_serpent]"); log("serpent done"); mc.stop(); } return; }
        if (d == 0 || d == 10)
        {
            serpAnchor(mc);
            Vec3 m = serpH.add(serpT).scale(0.5), dir = serpT.subtract(serpH).normalize(), up = new Vec3(0, 1, 0);
            Vec3 right = dir.cross(up);
            right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 cam, look;
            switch (i)
            {
                case 0 -> { cam = serpH.add(right.scale(32)).add(0, 8, 0); look = serpH; }
                case 1 -> { cam = serpH.subtract(dir.scale(45)).add(right.scale(12)).add(0, 10, 0); look = serpH; }
                case 2 -> { cam = m.add(right.scale(220)); look = m; }
                case 3 -> { cam = m.add(right.scale(150)).add(0, 150, 0); look = m; }
                case 4 -> { cam = m.add(right.scale(40)).add(0, 200, 0); look = m; }
                default -> { cam = serpT.add(right.scale(60)).add(0, 10, 0); look = serpT; }
            }
            tpFacing(mc, cam, look);
        }
        if (d == 30)
        {
            serpAnchor(mc);
            log("serpent_" + (i + 1) + " (" + SERP_SHOTS[i] + "): cam=" + mc.player.blockPosition() + " camInWater=" + water(mc, mc.player.getEyePosition())
                    + " distToHead=" + String.format("%.0f", mc.player.position().distanceTo(serpH)) + " " + counts(mc)
                    + " fogEnd=" + String.format("%.0f", com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd()));
            shot(mc, "serpent_" + (i + 1));
        }
    }

    /** -PbossShot=far: kraken / mother / angler (NoAI) each looked down on from the air above the sea, +30 / +180 / +300 blocks off (far_<boss>_1..3). */
    private static boolean farOk;
    private static int farY;

    private static void farScenes(Minecraft mc)
    {
        String[] ids = {"abyss_kraken", "abyss_mother", "abyss_angler"};
        int[] offs = {30, 180, 300};
        if (t == 60) { devtestSetup(mc); cmd(mc, "tp @s " + SX + " -200 " + SZ); }
        if (t < 100) return;
        int b = (t - 100) / 570, d = (t - 100) % 570;
        if (b >= ids.length) { if (d == 0) { log("far done"); mc.stop(); } return; }
        String id = ids[b];
        Vec3 head = new Vec3(SX + 0.5, -200, SZ + 0.5);
        if (d == 0) { for (String k : STUDIO_IDS) cmd(mc, "kill @e[type=abyssia:" + k + "]"); cmd(mc, "tp @s " + (SX + 30) + " -200 " + SZ + " facing " + SX + " -200 " + SZ); }
        if (d == 20) cmd(mc, "summon abyssia:" + id + " " + SX + ".5 -200 " + SZ + ".5 {NoAI:1b,Tags:[\"bossshot\"]}");
        for (int j = 0; j < offs.length; j++)
        {
            int s = 60 + j * 120;
            double cx = SX + 0.5 + offs[j];
            // load the chunks at the camera column first, then find the sea surface on the client level (camera = first air above it + 3)
            if (d == s) { farOk = false; cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.1f -200 %.1f", cx, SZ + 0.5)); }
            if (d == s + 60)
            {
                farOk = false;
                int bx = (int) Math.floor(cx), bz = SZ;
                if (mc.level.getChunkSource().hasChunk(bx >> 4, bz >> 4))
                {
                    for (int y = 150; y >= -200; y--)
                        if (mc.level.getBlockState(new BlockPos(bx, y, bz)).getBlock() == net.minecraft.world.level.block.Blocks.WATER) { farY = y + 1 + 3; farOk = true; break; }
                }
                if (farOk) tpFacing(mc, new Vec3(cx, farY, SZ + 0.5), head);
                else log("far " + id + " off=" + offs[j] + " SKIP no sea surface");
            }
            if (d == s + 70 && farOk) tpFacing(mc, new Vec3(cx, farY, SZ + 0.5), head);
            if (d == s + 85 && farOk)
            {
                Vec3 eye = mc.player.getEyePosition();
                log("far " + id + " off=" + offs[j] + " cam=" + mc.player.blockPosition() + " camBlock=" + mc.level.getBlockState(BlockPos.containing(eye)).getBlock()
                        + " camInWater=" + water(mc, eye) + " actual=" + String.format("%.0f", mc.player.position().distanceTo(head)) + " "
                        + counts(mc) + " BossFarClient.count()=" + BossFarClient.count() + " fogEnd=" + String.format("%.0f", com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd()));
                shot(mc, "far_" + id.replace("abyss_", "") + "_" + (j + 1));
            }
        }
        // far_<boss>_0: 110 blocks from the head at the head's depth (underwater, the old record where the model shape was visible)
        if (d == 440) cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.1f -200 %.1f", SX + 0.5 + 110, SZ + 0.5));
        if (d == 480 || d == 490) tpFacing(mc, new Vec3(SX + 0.5 + 110, -200, SZ + 0.5), head);
        if (d == 500)
        {
            Vec3 eye = mc.player.getEyePosition();
            log("far " + id + " off=110 cam=" + mc.player.blockPosition() + " camBlock=" + mc.level.getBlockState(BlockPos.containing(eye)).getBlock()
                    + " camInWater=" + water(mc, eye) + " actual=" + String.format("%.0f", mc.player.position().distanceTo(head)) + " "
                    + counts(mc) + " BossFarClient.count()=" + BossFarClient.count() + " fogEnd=" + String.format("%.0f", com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd()));
            shot(mc, "far_" + id.replace("abyss_", "") + "_0");
        }
        if (d == 550) cmd(mc, "kill @e[type=abyssia:" + id + "]");
    }

    private static void fogLog(Minecraft mc, String tag)
    {
        var p = mc.player;
        BlockPos bp = p.blockPosition();
        var biome = mc.level.getBiome(bp);
        float[] c = com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor();
        log(String.format(java.util.Locale.ROOT, "fog %s: pos=%s biome=%s biomeWaterFog=%06x biomeFog=%06x shaderFogColor=%.3f,%.3f,%.3f start=%.1f end=%.1f inCombat=%b nightVision=%b eyeY=%.1f renderDist=%.0f bosses=%d",
                tag, bp.toShortString(), biome.unwrapKey().map(k -> k.location().toString()).orElse("?"), biome.value().getWaterFogColor(), biome.value().getFogColor(),
                c[0], c[1], c[2], com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart(), com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd(),
                BossFarClient.inCombat(), p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), p.getEyeY(),
                (double) mc.gameRenderer.getRenderDistance(), mc.level.getEntitiesOfClass(AbyssBoss.class, p.getBoundingBox().inflate(900)).size()));
    }

    /**
     * -PbossShot=fog: the studio point (SX, -200, SZ), the same position and facing before and after a boss is summoned:
     * fog_before / fog_after (night vision on, as the studio shots) and fog_before_nonv / fog_after_nonv (night vision off).
     */
    private static void fogScenes(Minecraft mc)
    {
        String at = "tp @s " + SX + " -200 " + SZ + " 90 0";
        switch (t)
        {
            case 60 -> { devtestSetup(mc); cmd(mc, at); }
            case 110 -> cmd(mc, at);
            case 140 -> { fogLog(mc, "before(nv)"); shot(mc, "fog_before"); }
            case 150 -> cmd(mc, "effect clear @s minecraft:night_vision");
            case 190 -> { fogLog(mc, "before(no nv)"); shot(mc, "fog_before_nonv"); }
            case 200 -> cmd(mc, "effect give @s minecraft:night_vision 99999 0 true");
            case 210 -> { cmd(mc, "abyssia boss spawnnear abyss_kraken"); cmd(mc, "execute positioned ~ ~25 ~ run " + DUMMY); }
            case 420 -> cmd(mc, at);
            case 450 -> { fogLog(mc, "after(nv)"); shot(mc, "fog_after"); }
            case 460 -> cmd(mc, "effect clear @s minecraft:night_vision");
            case 500 -> { fogLog(mc, "after(no nv)"); shot(mc, "fog_after_nonv"); }
            case 520 -> { cmd(mc, "kill @e[type=abyssia:abyss_kraken]"); cmd(mc, "kill @e[tag=shotdummy]"); log("fog done"); mc.stop(); }
            default -> {}
        }
    }

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
