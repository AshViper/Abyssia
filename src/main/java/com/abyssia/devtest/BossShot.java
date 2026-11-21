package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.client.boss.BossFarClient;
import com.abyssia.entity.boss.AbyssBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
        if ("sever".equals(System.getProperty("abyssia.bossshot")) && t == 70) t = 745; // skip to the kraken scenes
        if ("model".equals(System.getProperty("abyssia.bossshot")) || "sever".equals(System.getProperty("abyssia.bossshot"))) { modelScenes(mc); return; }
        switch (t)
        {
            case 60 -> { cmd(mc, "gamemode survival"); cmd(mc, "clear @s"); cmd(mc, "effect clear @s");
                         cmd(mc, "effect give @s minecraft:water_breathing 9999 0 true"); cmd(mc, "effect give @s minecraft:resistance 9999 4 true");
                         cmd(mc, "effect give @s minecraft:regeneration 9999 4 true");
                         cmd(mc, "tp @s 380 -505 -391 facing 380 -506 -389"); }
            case 120 -> { p.getInventory().selected = 0; log("start pos=" + p.blockPosition() + " held=" + p.getMainHandItem()); }
            case 130 -> cmd(mc, "abyssia boss spawnnear abyss_serpent"); // BOSS01: natural spawn 80-120 blocks off (the altar is gone)
            case 150 -> shot(mc, "boss_omen");
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

    private static final String DUMMY = "summon minecraft:pig ~ ~ ~ {NoAI:1b,NoGravity:1b,Invulnerable:1b,Tags:[\"abyssia_boss_dummy\",\"shotdummy\"]}";

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
            case 1380 -> cmd(mc, "tp @s 380 -470 -330 facing entity @e[type=abyssia:abyss_mother,limit=1]");
            case 1395 -> shot(mc, "model_mother_front");
            case 1410 -> cmd(mc, "tp @s 320 -460 -389 facing entity @e[type=abyssia:abyss_mother,limit=1]");
            case 1425 -> shot(mc, "model_mother_side");
            case 1440 -> cmd(mc, "tp @s 380 -505 -389 facing 380 -470 -389");
            case 1455 -> shot(mc, "model_mother_below");
            case 1470 -> cmd(mc, "damage @e[type=abyssia:abyss_mother,limit=1] 2000 minecraft:generic");
            case 1520 -> cmd(mc, "tp @s 330 -470 -389 facing entity @e[type=abyssia:abyss_mother,limit=1]");
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

    /** -PbossShot=smoke: each boss summoned in the Forge test hall (134 -507 130) and photographed once (render smoke test). */
    private static void smokeScenes(Minecraft mc)
    {
        String[] bosses = {"abyss_serpent", "trench_wyrm", "abyss_kraken", "abyss_mother"};
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
