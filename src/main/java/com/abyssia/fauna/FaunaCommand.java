package com.abyssia.fauna;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Testing aid (ops only):
 * <ul>
 *     <li>{@code /abyssia fauna here}: depth (blocks, metres, zone), cover, seabed and light here, and what each
 *     species' rule would weigh at this spot</li>
 *     <li>{@code /abyssia fauna census [radius]}: deep-sea animals around, by species</li>
 *     <li>{@code /abyssia fauna list [radius]}: each deep-sea animal around, with what it is doing</li>
 *     <li>{@code /abyssia fauna spawn [attempts]}: run spawn attempts around you (or the command position) now,
 *     with the reasoning</li>
 *     <li>{@code /abyssia fauna depth <metres>}: where a real depth lies in each dimension</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class FaunaCommand
{
    private FaunaCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("fauna")
                        .then(Commands.literal("here").executes(FaunaCommand::here))
                        .then(Commands.literal("census")
                                .executes(ctx -> census(ctx, 96))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(8, 512))
                                        .executes(ctx -> census(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx, 64))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(4, 512))
                                        .executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("spawn")
                                .executes(ctx -> spawn(ctx, 20))
                                .then(Commands.argument("attempts", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "attempts")))))
                        .then(Commands.literal("vents")
                                .executes(ctx -> vents(ctx, 20))
                                .then(Commands.argument("attempts", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> vents(ctx, IntegerArgumentType.getInteger(ctx, "attempts")))))
                        .then(Commands.literal("depth")
                                .then(Commands.argument("metres", IntegerArgumentType.integer(0, 12000))
                                        .executes(ctx -> depth(ctx, IntegerArgumentType.getInteger(ctx, "metres")))))));
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String line)
    {
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    private static int here(CommandContext<CommandSourceStack> ctx)
    {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        double blocks = DepthZone.blocksBelowSurface(level, pos.getY());
        double metres = DepthZone.metres(blocks);
        say(ctx, String.format("depth %.0f blocks = %.0f m (%s), light %d, %s%s", blocks, metres, DepthZone.Zone.of(metres).name().toLowerCase(),
                level.getMaxLocalRawBrightness(pos), level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("?"),
                FaunaSpawner.isFaunaLevel(level) ? "" : " (the fauna spawner does not run in this dimension)"));
        SpawnSite site = SpawnSite.at(level, pos);
        if (site == null)
        {
            say(ctx, "not in open water");
            return 0;
        }
        say(ctx, String.format("seabed %s, %d blocks of water overhead%s", site.hasFloor() ? site.floorDistance() + " blocks below" : "not within 32 blocks",
                site.ceilingDistance(), site.covered(pos) ? ", under rock (cave or overhang)" : ", open water above"));
        java.util.Random random = new java.util.Random(pos.asLong());
        for (FaunaSpawnRule rule : FaunaSpawnRules.rules())
        {
            String name = BuiltInRegistries.ENTITY_TYPE.getKey(rule.entity()).getPath();
            BlockPos spot = rule.place(site, net.minecraft.util.RandomSource.create(random.nextLong()));
            if (spot == null)
            {
                say(ctx, "  " + name + ": " + rule.placement().getSerializedName() + " does not fit here");
                continue;
            }
            double spotMetres = DepthZone.metres(level, spot.getY());
            say(ctx, String.format("  %s: weight %.2f at %s (%.0f m, depth factor %.2f, %s)", name, rule.weight(site, spot) * Config.FAUNA_DENSITY.get(),
                    spot.toShortString(), spotMetres, rule.depth().factor(spotMetres), site.covered(spot) ? "covered" : "open"));
        }
        return 1;
    }

    private static int census(CommandContext<CommandSourceStack> ctx, int radius)
    {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        Map<String, Integer> counts = new TreeMap<>();
        for (Entity e : level.getEntities((Entity) null, new AABB(pos).inflate(radius), e -> e.isAlive() && !(e instanceof net.minecraftforge.entity.PartEntity<?>) && FaunaSpawnRules.isFauna(e.getType())))
        {
            counts.merge(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(), 1, Integer::sum);
        }
        StringBuilder line = new StringBuilder("fauna within " + radius + ":");
        counts.forEach((k, v) -> line.append(' ').append(k).append('=').append(v));
        say(ctx, counts.isEmpty() ? line + " none" : line.toString());
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, int attempts)
    {
        return spawn(ctx, attempts, false);
    }

    /** Vent-anchored attempts only (the vent fauna). */
    private static int vents(CommandContext<CommandSourceStack> ctx, int attempts)
    {
        return spawn(ctx, attempts, true);
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, int attempts, boolean vents)
    {
        ServerLevel level = ctx.getSource().getLevel();
        List<String> log = new ArrayList<>();
        int spawned = 0;
        for (int i = 0; i < attempts; i++)
        {
            spawned += vents ? FaunaSpawner.ventAttempt(level, ctx.getSource().getPosition(), level.random, log)
                    : FaunaSpawner.attempt(level, ctx.getSource().getPosition(), level.random, log);
        }
        Map<String, Integer> outcomes = new TreeMap<>();
        for (String line : log)
        {
            if (line.contains(": weight")) continue;
            // group the outcomes: drop the position and the detail in brackets
            int at = line.indexOf(" at ");
            String key = at >= 0 ? line.substring(0, at) : line;
            int detail = key.indexOf(" (");
            outcomes.merge(detail >= 0 ? key.substring(0, detail) : key, 1, Integer::sum);
        }
        say(ctx, attempts + " attempts, " + spawned + " animals spawned");
        outcomes.forEach((k, v) -> say(ctx, "  " + v + " x " + k));
        return spawned;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, int radius)
    {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        List<Entity> found = level.getEntities((Entity) null, new AABB(pos).inflate(radius), e -> e.isAlive() && !(e instanceof net.minecraftforge.entity.PartEntity<?>) && FaunaSpawnRules.isFauna(e.getType()));
        for (Entity e : found)
        {
            String state = e instanceof com.abyssia.entity.Anglerfish a ? new String[]{"idle", "threat", "flee"}[a.mood()] + (a.isDigesting() ? ", digesting" : "")
                    : e instanceof com.abyssia.entity.GiantIsopod g ? (g.isCurled() ? "curled" : g.isFeeding() ? "feeding" : g.isResting() ? "resting" : "active")
                    : e instanceof com.abyssia.entity.GulperEel g ? new String[]{"cruise", "gulp", "balloon", "flee"}[g.mood()]
                    : e instanceof com.abyssia.entity.DriftingMedusa m ? (m.isAlerted() ? "alert" : "calm") + (m.isEscaping() ? ", escaping" : "")
                            + (m.hasShedTentacles() ? ", tentacles shed" : "") : "";
            double metres = DepthZone.metres(level, e.getY());
            say(ctx, String.format("%s @ %.1f %.1f %.1f (%.0f m) %s, hp %.0f", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(),
                    e.getX(), e.getY(), e.getZ(), metres, state, e instanceof net.minecraft.world.entity.LivingEntity l ? l.getHealth() : 0));
        }
        say(ctx, found.size() + " animal(s) within " + radius);
        return found.size();
    }

    private static int depth(CommandContext<CommandSourceStack> ctx, int metres)
    {
        double blocks = DepthZone.blocks(metres);
        int oceanY = (int) Math.round(DepthZone.OCEAN_SURFACE_Y - blocks);
        int deepY = oceanY + DepthZone.deepOceanOffset();
        say(ctx, String.format("%d m = %.0f blocks below the surface (%s): ocean world y %d, deep ocean y %d", metres, blocks,
                DepthZone.Zone.of(metres).name().toLowerCase(), oceanY, deepY));
        return 1;
    }
}
