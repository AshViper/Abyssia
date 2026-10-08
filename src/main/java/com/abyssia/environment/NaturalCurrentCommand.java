package com.abyssia.environment;

import com.abyssia.Abyssia;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Natural current debugging:
 * <ul>
 *     <li>{@code /abyssia currents here}: the stream at your position (type, direction, strength, radius, falloff)</li>
 *     <li>{@code /abyssia currents near [radius]}: streams around you, nearest first</li>
 *     <li>{@code /abyssia currents show [seconds]}: outlines nearby streams with dust (axis with arrowheads, edge rings); 0 stops</li>
 *     <li>{@code /abyssia currents streams near [radius]}: CU01 current streams around you, nearest first</li>
 *     <li>{@code /abyssia currents streams show [seconds]}: draws nearby CU01 centrelines with dust; 0 stops</li>
 * </ul>
 * The client shows the stream at your feet on the F3 screen.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class NaturalCurrentCommand
{
    private static final int SHOW_RANGE = 96, SHOW_INTERVAL = 10, CHAT_LINES = 12;
    private static final DustParticleOptions AXIS = new DustParticleOptions(new Vector3f(0.3f, 0.9f, 1f), 1.2f);
    private static final DustParticleOptions HEAD = new DustParticleOptions(new Vector3f(1f, 0.85f, 0.2f), 1.5f);
    private static final DustParticleOptions EDGE = new DustParticleOptions(new Vector3f(0.2f, 0.4f, 1f), 0.9f);

    /** Players outlining streams, and the game tick their outline ends. */
    private static final Map<UUID, Long> SHOWING = new ConcurrentHashMap<>();

    private NaturalCurrentCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("currents")
                        .then(Commands.literal("here").executes(NaturalCurrentCommand::here))
                        .then(Commands.literal("near")
                                .executes(ctx -> near(ctx, 256))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 2048))
                                        .executes(ctx -> near(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("show")
                                .executes(ctx -> show(ctx, 30))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
                                        .executes(ctx -> show(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("streams")
                                .then(Commands.literal("near")
                                        .executes(ctx -> streamsNear(ctx, 384))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 2048))
                                                .executes(ctx -> streamsNear(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                                .then(Commands.literal("show")
                                        .executes(ctx -> streamsShow(ctx, 30))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
                                                .executes(ctx -> streamsShow(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))))));
    }

    private static int here(CommandContext<CommandSourceStack> ctx)
    {
        Vec3 p = ctx.getSource().getPosition();
        CurrentData data = NaturalCurrents.getCurrentAt(ctx.getSource().getLevel(), p.x, p.y, p.z);
        if (!data.isPresent())
        {
            ctx.getSource().sendSuccess(() -> Component.literal("No natural current here."), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(describe(data.current(), p) + String.format(Locale.ROOT, ", falloff %.2f, felt strength %.2f",
                data.falloff(), data.getLocalStrength())), false);
        return 1;
    }

    private static int near(CommandContext<CommandSourceStack> ctx, int radius)
    {
        Vec3 p = ctx.getSource().getPosition();
        List<NaturalCurrent> found = NaturalCurrents.near(ctx.getSource().getLevel(), p.x, p.y, p.z, radius);
        found.sort(Comparator.comparingDouble(c -> c.axisDistance(p.x, p.y, p.z)));
        ctx.getSource().sendSuccess(() -> Component.literal(found.size() + " natural currents within " + radius + " blocks"), false);
        found.stream().limit(CHAT_LINES).forEach(c -> ctx.getSource().sendSuccess(() -> Component.literal(" " + describe(c, p)), false));
        return found.size();
    }

    private static int show(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (seconds == 0)
        {
            SHOWING.remove(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("Current outlines off."), false);
            return 1;
        }
        SHOWING.put(player.getUUID(), player.level().getGameTime() + seconds * 20L);
        ctx.getSource().sendSuccess(() -> Component.literal("Outlining natural currents within " + SHOW_RANGE + " blocks for " + seconds + " s."), false);
        return 1;
    }

    /** Players drawing CU01 centrelines, and the game tick it ends. */
    private static final Map<UUID, Long> SHOWING_STREAMS = new ConcurrentHashMap<>();
    private static final DustParticleOptions STREAM_AXIS = new DustParticleOptions(new Vector3f(0.92f, 0.97f, 1f), 1.4f);
    private static final DustParticleOptions STREAM_CONTROL = new DustParticleOptions(new Vector3f(1f, 0.4f, 0.8f), 2.0f);

    private static int streamsNear(CommandContext<CommandSourceStack> ctx, int radius)
    {
        Vec3 p = ctx.getSource().getPosition();
        List<CurrentStream> found = CurrentStreams.near(ctx.getSource().getLevel(), p.x, p.y, p.z, radius);
        found.sort(Comparator.comparingDouble(c -> c.nearest(p.x, p.y, p.z).distance()));
        ctx.getSource().sendSuccess(() -> Component.literal(found.size() + " current streams within " + radius + " blocks"), false);
        found.stream().limit(CHAT_LINES).forEach(c ->
        {
            CurrentStream.Nearest n = c.nearest(p.x, p.y, p.z);
            Vec3 m = c.center();
            String line = String.format(Locale.ROOT, " %s at (%.0f, %.0f, %.0f), length %.0f, width %.0f, strength %.2f; nearest point (%.0f, %.0f, %.0f), %.0f blocks away",
                    c.tier(), m.x, m.y, m.z, c.length(), c.radius() * 2, c.strength(), n.point().x, n.point().y, n.point().z,
                    Math.max(0.0, n.distance() - c.radius()));
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        });
        return found.size();
    }

    private static int streamsShow(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (seconds == 0)
        {
            SHOWING_STREAMS.remove(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("Stream centrelines off."), false);
            return 1;
        }
        SHOWING_STREAMS.put(player.getUUID(), player.level().getGameTime() + seconds * 20L);
        ctx.getSource().sendSuccess(() -> Component.literal("Drawing current stream centrelines within " + SHOW_RANGE + " blocks for " + seconds + " s."), false);
        return 1;
    }

    private static void showStreams(ServerLevel level, ServerPlayer player)
    {
        for (CurrentStream c : CurrentStreams.near(level, player.getX(), player.getY(), player.getZ(), SHOW_RANGE))
        {
            List<Vec3> path = c.path();
            for (int i = 0; i < path.size() - 1; i++)
            {
                Vec3 a = path.get(i), b = path.get(i + 1);
                double len = a.distanceTo(b);
                for (double s = 0; s < len; s += 1.5)
                {
                    Vec3 q = a.add(b.subtract(a).scale(s / len));
                    if (q.distanceToSqr(player.position()) <= SHOW_RANGE * SHOW_RANGE) dust(level, player, STREAM_AXIS, q);
                }
            }
            for (Vec3 q : c.control()) dust(level, player, STREAM_CONTROL, q);
        }
    }

    private static String describe(NaturalCurrent c, Vec3 from)
    {
        Vec3 m = c.center(), d = c.direction();
        return String.format(Locale.ROOT, "%s at (%.0f, %.0f, %.0f), dir (%.2f, %.2f, %.2f), strength %.2f, radius %.0f, length %.0f, %.0f blocks away",
                c.type(), m.x, m.y, m.z, d.x, d.y, d.z, c.strength(), c.radius(), c.length(), Math.max(0.0, c.axisDistance(from.x, from.y, from.z) - c.radius()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        if (SHOWING.isEmpty() && SHOWING_STREAMS.isEmpty()) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers())
        {
            Long streamsUntil = SHOWING_STREAMS.get(player.getUUID());
            if (streamsUntil != null)
            {
                long now = player.serverLevel().getGameTime();
                if (now > streamsUntil) SHOWING_STREAMS.remove(player.getUUID());
                else if (now % SHOW_INTERVAL == 0) showStreams(player.serverLevel(), player);
            }
            Long until = SHOWING.get(player.getUUID());
            if (until == null) continue;
            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();
            if (now > until)
            {
                SHOWING.remove(player.getUUID());
                continue;
            }
            if (now % SHOW_INTERVAL != 0) continue;
            for (NaturalCurrent c : NaturalCurrents.near(level, player.getX(), player.getY(), player.getZ(), SHOW_RANGE)) outline(level, player, c);
        }
    }

    private static void outline(ServerLevel level, ServerPlayer player, NaturalCurrent c)
    {
        Vec3 dir = c.direction();
        Vec3 across = new Vec3(-dir.z, 0, dir.x);
        across = across.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : across.normalize();
        double length = c.length();
        // Axis, with an arrowhead every 8 blocks pointing downstream.
        for (double s = 0; s <= length; s += 1.5) dust(level, player, AXIS, c.start().add(dir.scale(s)));
        for (double s = 4; s <= Math.max(4, length); s += 8)
        {
            Vec3 tip = c.start().add(dir.scale(s));
            for (double k = 0.5; k <= 2.0; k += 0.5)
            {
                dust(level, player, HEAD, tip.subtract(dir.scale(k)).add(across.scale(k)));
                dust(level, player, HEAD, tip.subtract(dir.scale(k)).subtract(across.scale(k)));
            }
        }
        // Edge rings across the stream at both ends and the middle.
        for (double t : new double[] {0.0, 0.5, 1.0})
        {
            Vec3 ring = c.start().add(dir.scale(length * t));
            for (int i = 0; i < 32; i++)
            {
                double a = i * Math.PI * 2 / 32;
                dust(level, player, EDGE, ring.add(across.scale(Math.cos(a) * c.radius())).add(0, Math.sin(a) * c.height(), 0));
            }
        }
    }

    private static void dust(ServerLevel level, ServerPlayer player, DustParticleOptions dust, Vec3 p)
    {
        level.sendParticles(player, dust, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
    }
}
