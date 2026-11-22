package com.abyssia.habitat.upkeep;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.habitat.power.HabitatAir;
import com.abyssia.habitat.power.HabitatBases;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BAS01: shell integrity of every habitat module (keyed by module id, never reused). 0..100000 = 0..100 %, no record =
 * 100 % (old worlds start intact). Wear and seabed quakes lower it (never below the configured floor), leaks cost extra
 * FE and oxygen, hull patch kits and reinforcement plates restore / protect it. The quake state (cooldown, warning)
 * is runtime only. Ticked by {@link UpkeepTicker}.
 */
public class HabitatUpkeep extends SavedData
{
    public static final String NAME = "abyssia_habitat_upkeep";
    public static final int MAX = 100_000;
    public static final int WEAR_INTERVAL = 100;
    public static final int WEAR_PER_INTERVAL = 40;
    public static final int QUAKE_ROLL_INTERVAL = 1200;
    public static final int QUAKE_COOLDOWN = 24_000;
    public static final int QUAKE_STARTUP = 6_000;
    public static final int QUAKE_WARN = 600;
    public static final int QUAKE_RANGE = 96;
    public static final int LEAK_RANGE = 32;

    private final Map<Integer, Integer> integrity = new HashMap<>();
    private final Set<Integer> reinforced = new HashSet<>();

    /** runtime only: one per base root */
    private static final class Quake
    {
        long impactAt = -1;
        long cooldownUntil;
        long lastQuake = -1;
        int messages;
    }

    private final Map<Integer, Quake> quakes = new HashMap<>();
    private long startTick = -1;

    public static HabitatUpkeep get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(HabitatUpkeep::load, HabitatUpkeep::new, NAME);
    }

    // ---------------------------------------------------------------- values

    public int integrity(int module)
    {
        return integrity.getOrDefault(module, MAX);
    }

    public void setIntegrity(int module, int value)
    {
        int v = Math.max(0, Math.min(MAX, value));
        if (v == MAX) integrity.remove(module);
        else integrity.put(module, v);
        setDirty();
    }

    /** +amount (1/1000 %), capped at 100 %; returns the new value */
    public int repair(int module, int amount)
    {
        int v = Math.min(MAX, integrity(module) + amount);
        setIntegrity(module, v);
        return v;
    }

    /** true when it was not reinforced before */
    public boolean reinforce(int module)
    {
        if (!reinforced.add(module)) return false;
        setDirty();
        return true;
    }

    public boolean isReinforced(int module)
    {
        return reinforced.contains(module);
    }

    /** drops every record of a module (test clean-up) */
    public void forget(int module)
    {
        integrity.remove(module);
        reinforced.remove(module);
        setDirty();
    }

    /** the floor in 1/1000 % */
    public static int floor()
    {
        return Config.UPKEEP_MIN_INTEGRITY_PERCENT.get() * 1000;
    }

    /** lowers by amount but never below the floor (a value already under the floor stays) */
    private void lower(int module, int amount)
    {
        int cur = integrity(module);
        int fl = floor();
        if (cur <= fl) return;
        setIntegrity(module, Math.max(fl, cur - amount));
    }

    public static double depthMultiplier(BoundingBox box)
    {
        int y = box.minY();
        // depth bands formerly shared with the player pressure limits (removed 2026-11-22), kept for module upkeep
        if (y >= 23) return 1.0;
        if (y >= -300) return 1.5;
        if (y >= -450) return 2.0;
        return 3.0;
    }

    /** 0 = none (60 % and up), 1 = small (30..60 %), 2 = large (under 30 %) */
    public static int leakLevel(int integrity)
    {
        if (integrity >= 60_000) return 0;
        return integrity >= 30_000 ? 1 : 2;
    }

    /** only modules taller than one block (not a foundation slab) wear, leak and take repairs */
    public static boolean wearable(BoundingBox box)
    {
        return box.getYSpan() > 1;
    }

    static boolean active()
    {
        return Config.UPKEEP_ENABLED.get();
    }

    /** the centre chunk of the module's box is ticking (so it is loaded and wears / leaks) */
    public static boolean loaded(ServerLevel level, BoundingBox box)
    {
        return level.shouldTickBlocksAt(ChunkPos.asLong(box.getCenter()));
    }

    // ---------------------------------------------------------------- leaks (called from HabitatPower)

    /** extra life support FE per tick of the loaded leaking modules */
    public static int extraFe(ServerLevel level, List<HabitatBases.Module> modules)
    {
        if (!active()) return 0;
        HabitatUpkeep data = get(level);
        if (data.integrity.isEmpty()) return 0;
        int fe = 0;
        for (HabitatBases.Module m : modules)
        {
            int lvl = leakLevel(data.integrity(m.id()));
            if (lvl > 0 && loaded(level, m.box())) fe += lvl == 1 ? 10 : 20;
        }
        return fe;
    }

    /** extra oxygen loss of one tick (small -2, large -4) */
    public static void leak(ServerLevel level, int module, HabitatAir air)
    {
        if (!active()) return;
        HabitatUpkeep data = get(level);
        if (data.integrity.isEmpty()) return;
        int lvl = leakLevel(data.integrity(module));
        if (lvl == 0) return;
        HabitatBases.Module m = HabitatBases.get(level).module(module);
        if (m == null || !loaded(level, m.box())) return;
        air.drain(module, lvl == 1 ? 2 : 4);
    }

    // ---------------------------------------------------------------- status text

    /** " | Shell 72 % minor leak [reinforced]" and, while a quake is announced, " | Quake in 20 s" */
    public static Component status(ServerLevel level, int module)
    {
        var out = Component.empty();
        if (!active() || module < 0) return out;
        HabitatUpkeep data = get(level);
        int v = data.integrity(module);
        String key = "message." + Abyssia.MODID + ".habitat.upkeep";
        out.append(Component.translatable(key, String.valueOf(v / 1000)));
        int lvl = leakLevel(v);
        if (lvl > 0) out.append(Component.translatable(key + (lvl == 1 ? ".leak_small" : ".leak_large")));
        if (data.isReinforced(module)) out.append(Component.translatable(key + ".reinforced"));
        Quake q = data.quakes.get(HabitatBases.get(level).root(module));
        if (q != null && q.impactAt >= 0)
            out.append(Component.translatable(key + ".quake", String.valueOf(Math.max(0, (q.impactAt - level.getGameTime()) / 20))));
        return out;
    }

    private static final Map<java.util.UUID, Long> LAST_LINE = new HashMap<>();

    /** Standalone chat line of the shell state (sneak + empty-hand click); throttled to once per 40 ticks per player. */
    public static void sendStatusLine(ServerLevel level, net.minecraft.world.entity.player.Player player, int module)
    {
        if (!active() || module < 0) return;
        HabitatBases.Module m = HabitatBases.get(level).module(module);
        if (m == null || !wearable(m.box())) return;
        long now = level.getGameTime();
        Long last = LAST_LINE.get(player.getUUID());
        if (last != null && now >= last && now - last < 40) return;
        LAST_LINE.put(player.getUUID(), now);
        HabitatUpkeep data = get(level);
        int v = data.integrity(module);
        String key = "message." + Abyssia.MODID + ".habitat.upkeep";
        var out = Component.translatable(key + ".line", String.valueOf(v / 1000));
        int lvl = leakLevel(v);
        if (lvl > 0) out.append(Component.translatable(key + (lvl == 1 ? ".leak_small" : ".leak_large")));
        if (data.isReinforced(module)) out.append(Component.translatable(key + ".reinforced"));
        Quake q = data.quakes.get(HabitatBases.get(level).root(module));
        if (q != null && q.impactAt >= 0)
            out.append(Component.translatable(key + ".line_quake", String.valueOf(Math.max(0, (q.impactAt - level.getGameTime()) / 20))));
        player.displayClientMessage(out, false);
    }

    // ---------------------------------------------------------------- tick

    void tick(ServerLevel level)
    {
        tick(level, level.getGameTime(), level.random);
    }

    /** tick with an explicit clock and random source (the production tick passes the level's; tests pass their own) */
    public void tick(ServerLevel level, long now, RandomSource random)
    {
        if (!active())
        {
            quakes.clear();
            return;
        }
        if (startTick < 0) startTick = now;
        boolean warning = false;
        for (Quake q : quakes.values())
            if (q.impactAt >= 0)
            {
                warning = true;
                break;
            }
        if (!warning && now % WEAR_INTERVAL != 0 && now % QUAKE_ROLL_INTERVAL != 0) return;
        HabitatBases bases = HabitatBases.get(level);
        if (bases.modules().isEmpty()) return;
        if (now % WEAR_INTERVAL == 0) wearAndLeaks(level, bases);
        for (int base : bases.bases())
        {
            Quake q = quakes.get(base);
            if (q != null && q.impactAt >= 0)
            {
                long remaining = q.impactAt - now;
                if (remaining <= 0) impact(level, bases, base, q, now);
                else if (remaining % WEAR_INTERVAL == 0) warn(level, bases, base, q, remaining);
            }
            else if (now % QUAKE_ROLL_INTERVAL == 0 && Config.UPKEEP_QUAKES_ENABLED.get() && now - startTick >= QUAKE_STARTUP
                    && canRollQuake(level, base, now) && playerNear(level, bases, base) != null)
            {
                if (lottery(random, maxDepth(bases, base), Config.UPKEEP_QUAKE_FREQUENCY.get())) startWarn(level, bases, base, QUAKE_WARN, now);
            }
        }
    }

    private void wearAndLeaks(ServerLevel level, HabitatBases bases)
    {
        double wear = Config.UPKEEP_WEAR_MULTIPLIER.get();
        for (HabitatBases.Module m : bases.modules())
        {
            if (!wearable(m.box()) || !loaded(level, m.box())) continue;
            double d = WEAR_PER_INTERVAL * depthMultiplier(m.box()) * wear * (isReinforced(m.id()) ? 0.5 : 1.0);
            int amount = (int) Math.round(d);
            if (amount > 0) lower(m.id(), amount);
            int lvl = leakLevel(integrity(m.id()));
            if (lvl == 0) continue;
            Vec3 c = Vec3.atCenterOf(m.box().getCenter());
            boolean near = false;
            for (ServerPlayer p : level.players())
                if (p.distanceToSqr(c) <= LEAK_RANGE * LEAK_RANGE)
                {
                    near = true;
                    break;
                }
            if (!near) continue;
            int n = lvl == 1 ? 4 : 12;
            double sx = Math.max(0.5, m.box().getXSpan() / 2.0 - 1), sy = Math.max(0.5, m.box().getYSpan() / 2.0 - 1),
                    sz = Math.max(0.5, m.box().getZSpan() / 2.0 - 1);
            level.sendParticles(ParticleTypes.BUBBLE, c.x, c.y, c.z, n / 2, sx, sy, sz, 0.02);
            level.sendParticles(ParticleTypes.DRIPPING_WATER, c.x, c.y, c.z, n / 2, sx, sy, sz, 0.0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    // ---------------------------------------------------------------- quakes

    private static double maxDepth(HabitatBases bases, int base)
    {
        double max = 1.0;
        for (HabitatBases.Module m : bases.modules())
            if (bases.root(m.id()) == base) max = Math.max(max, depthMultiplier(m.box()));
        return max;
    }

    private static boolean anyLoaded(ServerLevel level, HabitatBases bases, int base)
    {
        for (HabitatBases.Module m : bases.modules())
            if (bases.root(m.id()) == base && m.box().getYSpan() > 1 && loaded(level, m.box())) return true;
        return false;
    }

    private static boolean near(ServerLevel level, HabitatBases bases, int base, ServerPlayer p)
    {
        for (HabitatBases.Module m : bases.modules())
            if (bases.root(m.id()) == base && loaded(level, m.box())
                    && p.distanceToSqr(Vec3.atCenterOf(m.box().getCenter())) <= QUAKE_RANGE * QUAKE_RANGE)
                return true;
        return false;
    }

    /** test hook: players counted instead of level.players() (null = the level's, always in production) */
    private List<? extends ServerPlayer> playersOverride;

    public void setPlayersOverride(List<? extends ServerPlayer> players)
    {
        playersOverride = players;
    }

    private List<? extends ServerPlayer> players(ServerLevel level)
    {
        return playersOverride != null ? playersOverride : level.players();
    }

    /** the start-up reference tick (game time of the first tick), -1 before it */
    public long startTick()
    {
        return startTick;
    }

    /** chance of a quake per lottery for the deepest module multiplier and the configured frequency */
    public static double rollChance(double maxDepthMult, double frequency)
    {
        return 1.0 / 60.0 * maxDepthMult * frequency;
    }

    public static boolean lottery(RandomSource random, double maxDepthMult, double frequency)
    {
        return random.nextDouble() < rollChance(maxDepthMult, frequency);
    }

    /** a non-spectator player within 96 blocks of a loaded module of the base, or null */
    private ServerPlayer playerNear(ServerLevel level, HabitatBases bases, int base)
    {
        for (ServerPlayer p : players(level))
            if (!p.isSpectator() && near(level, bases, base, p)) return p;
        return null;
    }

    /**
     * The cheap preconditions of a quake: enabled, no quake announced, cooldown over, a loaded module (players and the
     * start-up delay are checked by the lottery itself).
     */
    public boolean canRollQuake(ServerLevel level, int base)
    {
        return canRollQuake(level, base, level.getGameTime());
    }

    public boolean canRollQuake(ServerLevel level, int base, long now)
    {
        if (!active() || !Config.UPKEEP_QUAKES_ENABLED.get()) return false;
        Quake q = quakes.get(base);
        if (q != null && (q.impactAt >= 0 || now < q.cooldownUntil)) return false;
        return anyLoaded(level, HabitatBases.get(level), base);
    }

    /** game time of the last impact of the base, -1 when none yet */
    public long lastQuakeTick(int base)
    {
        Quake q = quakes.get(base);
        return q == null ? -1 : q.lastQuake;
    }

    /** warning messages sent for the current / last quake of the base (test counter, counted with or without players) */
    public int warnMessages(int base)
    {
        Quake q = quakes.get(base);
        return q == null ? 0 : q.messages;
    }

    /** test / tooling: announces a quake now, it lands after warnTicks; false when disabled or already announced */
    public boolean forceQuake(ServerLevel level, int base, int warnTicks)
    {
        if (!active() || !Config.UPKEEP_QUAKES_ENABLED.get()) return false;
        Quake q = quakes.get(base);
        if (q != null && q.impactAt >= 0) return false;
        startWarn(level, HabitatBases.get(level), base, warnTicks, level.getGameTime());
        return true;
    }

    private void startWarn(ServerLevel level, HabitatBases bases, int base, int warnTicks, long now)
    {
        Quake q = quakes.computeIfAbsent(base, b -> new Quake());
        q.impactAt = now + warnTicks;
        q.messages = 0;
        warn(level, bases, base, q, warnTicks);
    }

    private void warn(ServerLevel level, HabitatBases bases, int base, Quake q, long remaining)
    {
        q.messages++;
        Component msg = Component.translatable("message." + Abyssia.MODID + ".habitat.quake.warn", String.valueOf(remaining / 20));
        for (ServerPlayer p : players(level))
        {
            if (p.isSpectator() || !near(level, bases, base, p)) continue;
            p.displayClientMessage(msg, true);
            level.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.0f, 0.6f);
            level.sendParticles(ParticleTypes.ASH, p.getX(), p.getY() + 1, p.getZ(), 40, 4, 2, 4, 0.02);
        }
    }

    private void impact(ServerLevel level, HabitatBases bases, int base, Quake q, long now)
    {
        q.impactAt = -1;
        q.lastQuake = now;
        q.cooldownUntil = now + QUAKE_COOLDOWN;
        double dmg = Config.UPKEEP_QUAKE_DAMAGE.get();
        for (HabitatBases.Module m : bases.modules())
        {
            if (bases.root(m.id()) != base || m.box().getYSpan() <= 1 || !loaded(level, m.box())) continue;
            double d = Math.min(30_000.0, 15_000.0 * depthMultiplier(m.box()) * dmg) * (isReinforced(m.id()) ? 0.5 : 1.0);
            lower(m.id(), (int) Math.round(d));
            Vec3 c = Vec3.atCenterOf(m.box().getCenter());
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.6f, 0.7f);
        }
        Component msg = Component.translatable("message." + Abyssia.MODID + ".habitat.quake.hit");
        for (ServerPlayer p : players(level))
            if (!p.isSpectator() && near(level, bases, base, p)) p.displayClientMessage(msg, true);
    }

    // ---------------------------------------------------------------- save

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        int[] flat = new int[integrity.size() * 2];
        int i = 0;
        for (Map.Entry<Integer, Integer> e : integrity.entrySet())
        {
            flat[i++] = e.getKey();
            flat[i++] = e.getValue();
        }
        tag.putIntArray("Integrity", flat);
        tag.putIntArray("Reinforced", reinforced.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    public static HabitatUpkeep load(CompoundTag tag)
    {
        HabitatUpkeep data = new HabitatUpkeep();
        int[] flat = tag.getIntArray("Integrity");
        for (int i = 0; i + 1 < flat.length; i += 2) data.integrity.put(flat[i], flat[i + 1]);
        for (int m : tag.getIntArray("Reinforced")) data.reinforced.add(m);
        return data;
    }
}
