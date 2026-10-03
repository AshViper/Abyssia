package com.abyssia.environment;

import com.abyssia.Abyssia;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CU01 current stream debugging:
 * <ul>
 *     <li>{@code /abyssia currents streams near [radius]}: streams around you, nearest first (centre, length, width,
 *     strength, nearest point of the centre line; click a line to fill in a teleport there)</li>
 *     <li>{@code /abyssia currents streams show [seconds]}: draws nearby centre lines with dust (arrowheads downstream,
 *     edge rings at both ends and the middle); 0 stops</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class CurrentStreamCommand
{
    private static final int SHOW_RANGE = 96, SHOW_INTERVAL = 10, CHAT_LINES = 12;
    private static final DustParticleOptions AXIS = new DustParticleOptions(new Vector3f(0.92f, 0.97f, 1f), 1.2f);
    private static final DustParticleOptions HEAD = new DustParticleOptions(new Vector3f(1f, 0.6f, 0.2f), 1.5f);
    private static final DustParticleOptions EDGE = new DustParticleOptions(new Vector3f(0.3f, 0.7f, 1f), 0.9f);

    private static final Map<UUID, Long> SHOWING = new ConcurrentHashMap<>();

    private CurrentStreamCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("currents")
                        .then(Commands.literal("streams")
                                .then(Commands.literal("near")
                                        .executes(ctx -> near(ctx, 256))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 2048))
                                                .executes(ctx -> near(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                                .then(Commands.literal("show")
                                        .executes(ctx -> show(ctx, 30))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
                                                .executes(ctx -> show(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))))));
    }

    private static int near(CommandContext<CommandSourceStack> ctx, int radius)
    {
        Vec3 p = ctx.getSource().getPosition();
        List<CurrentStream> found = CurrentStreams.near(ctx.getSource().getLevel(), p, radius);
        found.sort(Comparator.comparingDouble(c -> c.nearest(p.x, p.y, p.z)[1]));
        if (found.isEmpty())
        {
            ctx.getSource().sendSuccess(() -> Component.literal("No current streams within " + radius + " blocks."), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(found.size() + " current streams within " + radius + " blocks"), false);
        found.stream().limit(CHAT_LINES).forEach(c -> ctx.getSource().sendSuccess(() -> entry(c, p), false));
        return found.size();
    }

    private static Component entry(CurrentStream c, Vec3 from)
    {
        Vec3 m = c.center();
        double[] near = c.nearest(from.x, from.y, from.z);
        Vec3 n = c.point(near[0]);
        String tp = String.format(Locale.ROOT, "/tp @s %.1f %.1f %.1f", n.x, n.y, n.z);
        String text = String.format(Locale.ROOT, " %s at (%.0f, %.0f, %.0f): length %.0f, width %.0f, strength %.2f; nearest point (%.0f, %.0f, %.0f), %.0f blocks away",
                c.tier(), m.x, m.y, m.z, c.length(), c.radius() * 2.0, c.strength(), n.x, n.y, n.z, Math.max(0.0, near[1] - c.radius()));
        return Component.literal(text).withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tp))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (seconds == 0)
        {
            SHOWING.remove(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("Current stream outlines off."), false);
            return 1;
        }
        SHOWING.put(player.getUUID(), player.level().getGameTime() + seconds * 20L);
        ctx.getSource().sendSuccess(() -> Component.literal("Outlining current streams within " + SHOW_RANGE + " blocks for " + seconds + " s."), false);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || SHOWING.isEmpty()) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers())
        {
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
            Vec3 at = player.position();
            for (CurrentStream c : CurrentStreams.near(level, at, SHOW_RANGE)) outline(level, player, c, at);
        }
    }

    private static void outline(ServerLevel level, ServerPlayer player, CurrentStream c, Vec3 at)
    {
        double arc = Math.max(1.0, c.arcLength());
        double range2 = (double) SHOW_RANGE * SHOW_RANGE;
        // Centre line, with an arrowhead every 8 blocks pointing downstream.
        for (double s = 0; s <= arc; s += 1.5)
        {
            Vec3 p = c.point(s / arc);
            if (p.distanceToSqr(at) <= range2) dust(level, player, AXIS, p);
        }
        for (double s = 4; s <= arc; s += 8)
        {
            double t = s / arc;
            Vec3 tip = c.point(t);
            if (tip.distanceToSqr(at) > range2) continue;
            Vec3 dir = c.tangent(t);
            Vec3 across = across(dir);
            for (double k = 0.5; k <= 2.0; k += 0.5)
            {
                dust(level, player, HEAD, tip.subtract(dir.scale(k)).add(across.scale(k)));
                dust(level, player, HEAD, tip.subtract(dir.scale(k)).subtract(across.scale(k)));
            }
        }
        // Edge rings across the band at both ends and the middle.
        for (double t : new double[] {0.0, 0.5, 1.0})
        {
            Vec3 center = c.point(t);
            if (center.distanceToSqr(at) > range2) continue;
            Vec3 dir = c.tangent(t);
            Vec3 across = across(dir);
            Vec3 up = across.cross(dir).normalize();
            for (int i = 0; i < 40; i++)
            {
                double a = i * Math.PI * 2 / 40;
                dust(level, player, EDGE, center.add(across.scale(Math.cos(a) * c.radius())).add(up.scale(Math.sin(a) * c.radius())));
            }
        }
    }

    private static Vec3 across(Vec3 dir)
    {
        Vec3 a = new Vec3(-dir.z, 0, dir.x);
        return a.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : a.normalize();
    }

    private static void dust(ServerLevel level, ServerPlayer player, DustParticleOptions dust, Vec3 p)
    {
        level.sendParticles(player, dust, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
    }
}
