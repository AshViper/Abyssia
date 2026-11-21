package com.abyssia.entity.boss;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModEntities;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;

/**
 * BOSS01 natural spawn. Every 200 ticks each survival player standing in a boss biome ({@code #abyssia:boss_spawn/<boss>})
 * in open water gets a 5% roll; a hit spawns that boss 80-120 blocks away and gives the player a 30 minute cooldown
 * (stored in the player's persistent data). Nothing spawns when a boss is already within 512 blocks.
 * <p>
 * Test command ({@code /abyssia boss ...}, op level 2): {@code spawnnear <boss>} runs the spawn point search and spawn only
 * (no biome / open water / chance / cooldown checks); {@code status} reports the checks for the sender's position.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class BossSpawner
{
    /** Boss names by {@link BossContent} KIND index; also the {@code boss_spawn/<name>} tag names. */
    public static final String[] NAMES = {"abyss_serpent", "trench_wyrm", "abyss_kraken", "abyss_mother", "abyss_angler"};
    private static final String COOLDOWN_KEY = "abyssia_boss_cd";
    private static final int INTERVAL = 200, COOLDOWN = 36000, ARENA_RADIUS = 110, ATTEMPTS = 24;
    private static final double CHANCE = 0.05, OPEN_RATIO = 0.85;
    private static final int OPEN_UP = 40, OPEN_SIDE = 30;

    private BossSpawner() {}

    private static TagKey<Biome> tag(int kind)
    {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "boss_spawn/" + NAMES[kind]));
    }

    /** The boss kind whose spawn biome tag contains the biome at {@code pos}, or -1. */
    public static int kindAt(ServerLevel level, BlockPos pos)
    {
        var biome = level.getBiome(pos);
        for (int k = 0; k < NAMES.length; k++) if (biome.is(tag(k))) return k;
        return -1;
    }

    // ---- natural spawning

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        var server = event.getServer();
        if (server.getTickCount() % INTERVAL != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
        {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            ServerLevel level = p.serverLevel();
            BlockPos pos = p.blockPosition();
            int kind = kindAt(level, pos);
            if (kind < 0) continue;
            if (openRatio(level, pos) < OPEN_RATIO) continue;
            if (bossesNear(level, p.position()) > 0) continue;
            long now = level.getGameTime();
            if (now < p.getPersistentData().getLong(COOLDOWN_KEY)) continue;
            if (level.random.nextDouble() >= CHANCE) continue;
            if (spawnNear(level, p, kind) != null) p.getPersistentData().putLong(COOLDOWN_KEY, now + COOLDOWN);
        }
    }

    /** Bosses (loaded) within the LOD range of {@code c}. */
    public static int bossesNear(ServerLevel level, Vec3 c)
    {
        // live ones only: a boss killed in a chunk outside the simulation distance lies there dead until the chunk ticks again
        return level.getEntitiesOfClass(AbyssBoss.class, new AABB(c, c).inflate(BossFarPayload.RANGE), AbyssBoss::isAlive).size();
    }

    /**
     * Open-water check at {@code pos} (the old altar test): 40 blocks straight up must be free, then the four sides are
     * sampled every 2 blocks out to 30. Returns -1 when the way up is blocked, else the open fraction 0..1.
     */
    public static double openRatio(ServerLevel level, BlockPos pos)
    {
        for (int dy = 1; dy <= OPEN_UP; dy++) if (!open(level, pos.above(dy))) return -1;
        int samples = 0, openCount = 0;
        for (Direction d : Direction.Plane.HORIZONTAL)
            for (int i = 2; i <= OPEN_SIDE; i += 2) { samples++; if (open(level, pos.relative(d, i).above(2))) openCount++; }
        return (double) openCount / samples;
    }

    private static boolean open(ServerLevel level, BlockPos p)
    {
        if (!level.hasChunkAt(p)) return false;
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /**
     * Spawns boss {@code kind} 80-120 blocks horizontally from {@code player} (up to 24 tries, +-20 vertically, free box in
     * loaded chunks; the wyrm starts 3 blocks inside the floor). Returns the spawn point, or null when none was found.
     */
    @Nullable
    public static Vec3 spawnNear(ServerLevel level, ServerPlayer player, int kind)
    {
        AbyssBoss boss = kind == BossContent.KIND_WYRM ? ModEntities.TRENCH_WYRM.get().create(level)
                : kind == BossContent.KIND_MOTHER ? ModEntities.ABYSS_MOTHER.get().create(level)
                : kind == BossContent.KIND_ANGLER ? ModEntities.ABYSS_ANGLER.get().create(level)
                : kind == BossContent.KIND_KRAKEN ? ModEntities.ABYSS_KRAKEN.get().create(level) : ModEntities.ABYSS_SERPENT.get().create(level);
        if (boss == null) return null;
        boolean wyrm = kind == BossContent.KIND_WYRM;
        double h = boss.getBbHeight(), w = boss.getBbWidth();
        Vec3 pp = player.position();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++)
        {
            double dist = 80 + level.random.nextDouble() * 40;
            double ang = level.random.nextDouble() * Math.PI * 2;
            double x = pp.x + Math.cos(ang) * dist, z = pp.z + Math.sin(ang) * dist;
            double y = pp.y + (level.random.nextDouble() * 2 - 1) * 20;
            double floor = Double.NaN;
            if (wyrm)
            {
                floor = floorBelow(level, x, y, z);
                if (Double.isNaN(floor)) continue;
                y = floor - 3 * boss.size();
            }
            AABB box = new AABB(x - w / 2, y - h / 2, z - w / 2, x + w / 2, y + h / 2, z + w / 2);
            if (!level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) continue;
            if (!wyrm && !level.noCollision(box)) continue;
            // arena round the player who met it (spawning 80-120 off, the boss swims in toward the player)
            Vec3 center = wyrm ? new Vec3(pp.x, floor, pp.z) : pp;
            boss.setPos(x, y - h / 2, z);
            boss.bindArena(center, ARENA_RADIUS, new Vec3(pp.x - x, 0, pp.z - z).normalize().scale(0.8));
            if (!level.addFreshEntity(boss)) return null;
            boss.keepLoaded();
            level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 10.0F, 0.5F);
            return new Vec3(x, y, z);
        }
        return null;
    }

    /** Top of the first solid block under (x, y, z) within 48 blocks (loaded chunks only), or NaN. */
    private static double floorBelow(ServerLevel level, double x, double y, double z)
    {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(y), Math.floor(z));
        for (int i = 0; i < 48; i++, m.move(Direction.DOWN))
        {
            if (!level.hasChunkAt(m)) return Double.NaN;
            if (m.getY() <= level.getMinBuildHeight()) return Double.NaN;
            if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) return m.getY() + 1;
        }
        return Double.NaN;
    }

    // ---- command

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        LiteralArgumentBuilder<CommandSourceStack> near = Commands.literal("spawnnear");
        for (int k = 0; k < NAMES.length; k++)
        {
            final int kind = k;
            near.then(Commands.literal(NAMES[k]).executes(ctx -> spawnNearCommand(ctx, kind)));
        }
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("boss")
                        .then(near)
                        .then(Commands.literal("status").executes(BossSpawner::status))));
    }

    private static int spawnNearCommand(CommandContext<CommandSourceStack> ctx, int kind)
    {
        ServerPlayer p;
        try { p = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { ctx.getSource().sendFailure(Component.literal("Run this as a player.")); return 0; }
        Vec3 at = spawnNear(p.serverLevel(), p, kind);
        if (at == null)
        {
            ctx.getSource().sendFailure(Component.literal("No free spawn point for " + NAMES[kind] + " (24 tries)."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("Spawned %s at %.0f %.0f %.0f", NAMES[kind], at.x, at.y, at.z)), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer p;
        try { p = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { ctx.getSource().sendFailure(Component.literal("Run this as a player.")); return 0; }
        ServerLevel level = p.serverLevel();
        BlockPos pos = p.blockPosition();
        int kind = kindAt(level, pos);
        double open = openRatio(level, pos);
        long left = Math.max(0, p.getPersistentData().getLong(COOLDOWN_KEY) - level.getGameTime());
        String line = String.format("biome %s, boss %s, open %s, cooldown %d s, bosses within %d: %d",
                level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("?"),
                kind < 0 ? "none" : NAMES[kind],
                open < 0 ? "blocked above" : String.format("%.0f%% (need %.0f%%)", open * 100, OPEN_RATIO * 100),
                left / 20, BossFarPayload.RANGE, bossesNear(level, p.position()))
                + " " + level.getEntitiesOfClass(AbyssBoss.class, new AABB(p.position(), p.position()).inflate(BossFarPayload.RANGE)).stream()
                        .map(e -> e.getClass().getSimpleName() + "#" + e.getId() + (e.isRemoved() ? "(removed)" : "") + (e.isAlive() ? "" : "(dead)") + "@" + e.blockPosition().toShortString()).toList();
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }
}
