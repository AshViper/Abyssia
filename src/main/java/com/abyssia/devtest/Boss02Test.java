package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.entity.boss.AbyssBoss;
import com.abyssia.entity.boss.BossSpawner;
import com.abyssia.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dev-only (BOSS02): with -Dabyssia.boss02test (gradle runServer -Pboss02Test=1) the dedicated server checks the boss
 * natural spawn table, the angler's biome tag, a forced BossSpawner.spawnNear, the loot of all five bosses and the
 * titan advancements (kills credited to a FakePlayer), logs one "BOSS02 name PASS|FAIL|SKIP measured" line each,
 * then "BOSS02 DONE pass= fail=" and halts. Everything it creates is discarded and the forced chunks are released.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Boss02Test
{
    private static final Logger LOG = LogUtils.getLogger();
    private static final int KIND_ANGLER = 4, RADIUS_CHUNKS = 8;

    /** Expected loot per boss name: item path -> {min, max} (loot_tables/entities/<name>.json). */
    private static final Map<String, Map<String, int[]>> LOOT = new LinkedHashMap<>();
    static
    {
        LOOT.put("abyss_serpent", Map.of("abyss_serpent_scale", new int[]{3, 5}, "deep_crystal_shard", new int[]{4, 8}, "pressure_crystal_shard", new int[]{2, 4}));
        LOOT.put("trench_wyrm", Map.of("trench_wyrm_fang", new int[]{2, 4}, "deep_crystal_shard", new int[]{4, 8}, "pressure_crystal_shard", new int[]{3, 5}));
        LOOT.put("abyss_kraken", Map.of("kraken_beak", new int[]{1, 1}, "kraken_ink_sac", new int[]{3, 6}, "deep_crystal_shard", new int[]{4, 8}, "luminous_crystal", new int[]{2, 4}));
        LOOT.put("abyss_mother", Map.of("mother_bell_gel", new int[]{3, 6}, "abyss_heart", new int[]{1, 1}, "deep_crystal_shard", new int[]{4, 8}, "luminous_crystal", new int[]{2, 4}));
        LOOT.put("abyss_angler", Map.of("angler_lure", new int[]{3, 5}, "deep_crystal_shard", new int[]{4, 8}, "pressure_crystal_shard", new int[]{2, 4}));
    }

    private static int phase, passes, fails, waited, serverTicks;
    private static boolean finished, located;
    private static ServerLevel level;
    private static BlockPos target;
    private static final List<long[]> forced = new ArrayList<>();
    private static FakePlayer fp;

    private static void result(String name, boolean ok, String measured)
    {
        if (ok) passes++; else fails++;
        LOG.info("BOSS02 {} {} {}", name, ok ? "PASS" : "FAIL", measured);
    }

    private static void skip(String name, String why) { LOG.info("BOSS02 {} SKIP {}", name, why); }

    private static TagKey<Biome> tag(String name) { return TagKey.create(Registries.BIOME, new ResourceLocation(Abyssia.MODID, "boss_spawn/" + name)); }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || System.getProperty("abyssia.boss02test") == null || finished) return;
        MinecraftServer server = event.getServer();
        if (++serverTicks < 100) return;
        try { run(server); }
        catch (Throwable e)
        {
            LOG.error("BOSS02 exception", e);
            result("exception", false, e.toString());
            finish(server);
        }
    }

    private static void run(MinecraftServer server)
    {
        if (phase == 0)
        {
            level = server.overworld();
            checkTable();
            // (b) the closest angler biome (3D search from the deep layer; a big radius takes a while, once)
            BlockPos start = new BlockPos(0, -250, 0);
            var found = level.findClosestBiome3d(h -> h.is(tag("abyss_angler")), start, 2048, 32, 64);
            if (found != null)
            {
                target = found.getFirst();
                located = true;
                LOG.info("BOSS02 locate angler biome {} at {}", found.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("?"), target);
            }
            else
            {
                target = level.getSharedSpawnPos();
                LOG.info("BOSS02 locate angler biome not found within 2048, falling back to spawn {}", target);
            }
            int cx = target.getX() >> 4, cz = target.getZ() >> 4;
            int r = located ? RADIUS_CHUNKS : 2;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++)
            {
                level.setChunkForced(cx + x, cz + z, true);
                forced.add(new long[]{cx + x, cz + z});
            }
            phase = 1;
            return;
        }
        if (phase == 1)
        {
            // wait until the forced block is loaded (or give up after 30 s and use what is there)
            boolean all = true;
            for (long[] c : forced) if (!level.hasChunk((int) c[0], (int) c[1])) { all = false; break; }
            if (all || ++waited > 600) { LOG.info("BOSS02 chunks loaded={} waited={} ticks", all, waited); phase = 2; }
            return;
        }
        if (phase == 2)
        {
            runBosses(server);
            finish(server);
        }
    }

    /** (a) the five names, their entity types, and the angler biome tag. */
    private static void checkTable()
    {
        String[] names = BossSpawner.NAMES;
        boolean ok = names.length == 5;
        StringBuilder sb = new StringBuilder();
        for (String n : names)
        {
            boolean reg = BuiltInRegistries.ENTITY_TYPE.containsKey(new ResourceLocation(Abyssia.MODID, n));
            ok &= reg && LOOT.containsKey(n);
            sb.append(n).append(reg ? "" : "(unregistered)").append(' ');
        }
        result("spawner_table", ok, "n=" + names.length + " " + sb.toString().trim());
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (String n : names)
        {
            var set = biomes.getTag(tag(n));
            List<String> ids = set.map(s -> s.stream().map(h -> h.unwrapKey().map(k -> k.location().toString()).orElse("?")).toList()).orElse(List.of());
            if (n.equals("abyss_angler")) result("angler_biome_tag", !ids.isEmpty(), ids.toString());
            else LOG.info("BOSS02 info tag {} = {}", n, ids);
        }
    }

    private static Entity create(int kind)
    {
        return switch (kind)
        {
            case 0 -> ModEntities.ABYSS_SERPENT.get().create(level);
            case 1 -> ModEntities.TRENCH_WYRM.get().create(level);
            case 2 -> ModEntities.ABYSS_KRAKEN.get().create(level);
            case 3 -> ModEntities.ABYSS_MOTHER.get().create(level);
            default -> ModEntities.ABYSS_ANGLER.get().create(level);
        };
    }

    /** A point inside the loaded area with a free 20-block cube, preferring the angler biome; falls back to the target. */
    private static BlockPos openPoint()
    {
        BlockPos best = null;
        for (int pass = 0; pass < 2 && best == null; pass++)
            for (int dx = -48; dx <= 48 && best == null; dx += 12)
                for (int dz = -48; dz <= 48 && best == null; dz += 12)
                    for (int dy = -24; dy <= 24 && best == null; dy += 8)
                    {
                        BlockPos p = target.offset(dx, dy, dz);
                        if (p.getY() < level.getMinBuildHeight() + 8 || !level.hasChunkAt(p)) continue;
                        if (pass == 0 && !level.getBiome(p).is(tag("abyss_angler"))) continue;
                        if (level.noCollision(new AABB(p).inflate(10))) best = p;
                    }
        return best;
    }

    /**
     * BossSpawner.spawnNear places the boss 80-120 blocks from the player where its (collision-free) box fits in loaded chunks;
     * here: the nearest point within 96 blocks of the locate point (y: seabed-ish .. +120, step 8) in the angler biome whose 3x3x3 is all water.
     */
    private static BlockPos waterPoint()
    {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -96; dx <= 96; dx += 8)
            for (int dz = -96; dz <= 96; dz += 8)
                for (int y = Math.max(level.getMinBuildHeight() + 8, target.getY() - 64); y <= target.getY() + 120; y += 8)
                {
                    BlockPos p = new BlockPos(target.getX() + dx, y, target.getZ() + dz);
                    if (!level.hasChunkAt(p) || !level.getBiome(p).is(tag("abyss_angler"))) continue;
                    boolean ok = true;
                    for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1)))
                        if (!level.getFluidState(q).is(net.minecraft.tags.FluidTags.WATER) || !level.getBlockState(q).getCollisionShape(level, q).isEmpty()) { ok = false; break; }
                    double d = p.distSqr(target);
                    if (ok && d < bestD) { best = p; bestD = d; }
                }
        return best;
    }

    private static void runBosses(MinecraftServer server)
    {
        fp = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("abyssia-boss02".getBytes()), "[boss02]"));
        BlockPos water = located ? waterPoint() : null;
        BlockPos open = water != null ? water : openPoint();
        BlockPos at = open != null ? open : target;
        fp.setPos(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        LOG.info("BOSS02 stand at {} open={} biome={}", at, open != null, level.getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("?"));

        // (b) forced BossSpawner pass (the code path of /abyssia boss spawnnear and the natural roll)
        AbyssBoss angler = null;
        if (!located || water == null)
            skip("angler_spawn", located ? "no angler-biome point with 3x3x3 water within 96 blocks of the locate point" : "angler biome not found within 2048 blocks");
        else
        {
            Vec3 where = null;
            for (int i = 0; i < 6 && where == null; i++) where = BossSpawner.spawnNear(level, fp, KIND_ANGLER);
            if (where == null) skip("angler_spawn", "spawnNear found no free point in 6 x 24 tries (loaded chunks / terrain)");
            else
            {
                final Vec3 w = where;
                var list = level.getEntitiesOfClass(AbyssBoss.class, new AABB(fp.position(), fp.position()).inflate(200), b -> b.getType() == ModEntities.ABYSS_ANGLER.get());
                if (!list.isEmpty()) angler = list.get(0);
                result("angler_spawn", angler != null, "at " + String.format("%.0f %.0f %.0f", w.x, w.y, w.z) + " biome=" + level.getBiome(BlockPos.containing(w)).unwrapKey().map(k -> k.location().toString()).orElse("?") + " stand=" + at.toShortString() + " entities=" + list.size()
                        + " dist=" + String.format("%.0f", Math.sqrt(w.distanceToSqr(fp.position()))));
            }
        }
        if (angler != null)
        {
            var a = angler.getAttribute(Attributes.MAX_HEALTH);
            result("angler_attributes", a != null && a.getValue() > 0 && angler.getAttribute(Attributes.ARMOR) != null,
                    "max_health=" + (a == null ? "none" : a.getValue()) + " health=" + angler.getHealth());
        }

        Advancement slayer = server.getAdvancements().getAdvancement(new ResourceLocation(Abyssia.MODID, "dive/titan_slayer"));
        Advancement hunter = server.getAdvancements().getAdvancement(new ResourceLocation(Abyssia.MODID, "dive/titan_hunter"));
        result("advancements_exist", slayer != null && hunter != null, "titan_slayer=" + (slayer != null) + " titan_hunter=" + (hunter != null));
        if (slayer != null) result("titan_slayer_criteria", slayer.getCriteria().keySet().containsAll(List.of(BossSpawner.NAMES)) && slayer.getCriteria().size() == 5,
                slayer.getCriteria().keySet() + " requirements=" + java.util.Arrays.deepToString(slayer.getRequirements()));
        if (hunter != null) LOG.info("BOSS02 info titan_hunter criteria {} requirements={}", hunter.getCriteria().keySet(), java.util.Arrays.deepToString(hunter.getRequirements()));

        // (c) the angler (the spawned one when there is one, else a direct one), then (d) the other four
        String[] names = BossSpawner.NAMES;
        int[] order = {4, 0, 1, 2, 3};
        for (int kind : order)
        {
            String n = names[kind];
            AbyssBoss b = kind == KIND_ANGLER && angler != null ? angler : null;
            if (b == null)
            {
                Entity e = create(kind);
                if (!(e instanceof AbyssBoss ab)) { result("loot_" + n, false, "create() returned " + e); continue; }
                ab.setPos(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
                if (!level.addFreshEntity(ab)) { result("loot_" + n, false, "addFreshEntity refused"); continue; }
                b = ab;
            }
            killAndCheck(n, b);
        }
        // FakePlayer advancement grants are not reliable: checked with a real ServerPlayer in ContentShot scene "titan" (-PcontentShot=1)
        skip("advancement_titan_slayer_after_angler", "checked with a real player: ContentShot scene titan");
        skip("advancement_titan_hunter_after_four", "checked with a real player: ContentShot scene titan");
        skip("advancement_titan_slayer_after_all", "checked with a real player: ContentShot scene titan");
    }

    /** Kills {@code b} with a player-attributed bypassing source (lastHurtByPlayer = the FakePlayer), counts the dropped items, cleans up. */
    private static void killAndCheck(String name, AbyssBoss b)
    {
        Vec3 pos = b.position();
        var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC_KILL);
        b.hurt(new DamageSource(type, fp), 100000F);
        AABB box = new AABB(pos, pos).inflate(12);
        Map<String, Integer> got = new LinkedHashMap<>();
        for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, box))
        {
            got.merge(BuiltInRegistries.ITEM.getKey(it.getItem().getItem()).getPath(), it.getItem().getCount(), Integer::sum);
            it.discard();
        }
        Map<String, int[]> exp = LOOT.get(name);
        boolean ok = got.keySet().equals(exp.keySet());
        for (var e : exp.entrySet())
        {
            int n = got.getOrDefault(e.getKey(), 0);
            ok &= n >= e.getValue()[0] && n <= e.getValue()[1];
        }
        result("loot_" + name, ok, "dead=" + b.isDeadOrDying() + " drops=" + got + " expected=" + describe(exp));
        b.discard();
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(pos, pos).inflate(150), x -> x instanceof AbyssBoss || x instanceof com.abyssia.entity.boss.AbyssMotherSpawn)) e.discard();
    }

    private static String describe(Map<String, int[]> exp)
    {
        StringBuilder sb = new StringBuilder();
        for (var e : exp.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()[0]).append('-').append(e.getValue()[1]).append(' ');
        return sb.toString().trim();
    }

    private static void finish(MinecraftServer server)
    {
        finished = true;
        try
        {
            for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], false);
            if (fp != null)
            {
                for (String id : new String[]{"dive/titan_slayer", "dive/titan_hunter"})
                {
                    Advancement a = server.getAdvancements().getAdvancement(new ResourceLocation(Abyssia.MODID, id));
                    if (a != null) for (String crit : a.getCriteria().keySet()) fp.getAdvancements().revoke(a, crit);
                }
            }
            int left = 0;
            List<Entity> stray = new ArrayList<>();
            for (Entity e : level.getAllEntities()) if (e instanceof AbyssBoss || (e instanceof ItemEntity ie && isBossLoot(ie))) stray.add(e);
            for (Entity e : stray) { e.discard(); left++; }
            LOG.info("BOSS02 cleanup leftovers_discarded={} forced_released={}", left, forced.size());
        }
        catch (Throwable e) { LOG.error("BOSS02 cleanup failed", e); }
        LOG.info("BOSS02 DONE pass={} fail={}", passes, fails);
        server.halt(false);
    }

    private static boolean isBossLoot(ItemEntity ie)
    {
        String p = BuiltInRegistries.ITEM.getKey(ie.getItem().getItem()).getPath();
        for (var m : LOOT.values()) if (m.containsKey(p)) return true;
        return false;
    }
}
