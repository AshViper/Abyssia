package com.abyssia.fauna;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.registry.ModGiants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** GNT01 test commands (op level 2): {@code /abyssia giants list | spawn <id> | stats | coverage}. */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class GiantCommand
{
    private static final int ATTEMPTS = 24;

    private GiantCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("giants")
                        .then(Commands.literal("list").executes(GiantCommand::list))
                        .then(Commands.literal("stats").executes(GiantCommand::stats))
                        .then(Commands.literal("coverage").executes(GiantCommand::coverage))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ModGiants.ids(), b))
                                        .executes(GiantCommand::spawn)))));
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String line)
    {
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    private static int list(CommandContext<CommandSourceStack> ctx)
    {
        var server = ctx.getSource().getServer();
        say(ctx, String.format("giants loaded %d / %d, enabled=%s", GiantSpawner.loadedCount(server), Config.GIANT_MAX_LOADED.get(), Config.GIANT_ENABLED.get()));
        for (ModGiants.Species sp : ModGiants.all())
        {
            int loaded = 0;
            for (ServerLevel level : server.getAllLevels())
                loaded += level.getEntities(EntityTypeTest.forClass(GiantFauna.class), g -> g.isAlive() && g.getType() == sp.type().get()).size();
            StringBuilder rules = new StringBuilder();
            for (GiantSpawnRules.Rule r : GiantSpawnRules.rules())
                if (r.species().equals(sp.id())) rules.append(String.format(" y %d..%d w %.1f cap %d/%d", r.minY(), r.maxY(), r.weight(), r.cap().count(), r.cap().radius()));
            say(ctx, String.format("%s %s length %.0f hp %.0f loaded %d rules:%s", sp.id(), sp.spec().temper(), sp.spec().length(), sp.spec().health(), loaded,
                    rules.length() == 0 ? " none" : rules));
        }
        return 1;
    }

    private static int coverage(CommandContext<CommandSourceStack> ctx)
    {
        var biomes = ctx.getSource().getServer().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        int uncovered = 0, total = 0;
        for (var entry : biomes.holders().sorted(java.util.Comparator.comparing(h -> h.key().location().toString())).toList())
        {
            if (!entry.key().location().getNamespace().equals(Abyssia.MODID)) continue;
            total++;
            java.util.Set<String> species = new java.util.TreeSet<>();
            for (GiantSpawnRules.Rule r : GiantSpawnRules.rules())
                if (r.matchesBiome(entry)) species.add(r.species() + (r.biomes().isEmpty() ? "(any)" : ""));
            if (species.isEmpty()) uncovered++;
            say(ctx, entry.key().location().getPath() + ": " + (species.isEmpty() ? "UNCOVERED" : String.join(", ", species)));
        }
        say(ctx, "coverage: " + total + " biomes, " + uncovered + " UNCOVERED");
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx)
    {
        int n = 0;
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels())
        {
            for (GiantFauna g : level.getEntities(EntityTypeTest.forClass(GiantFauna.class), e -> true))
            {
                n++;
                say(ctx, String.format("%s #%d length %.2f parts %d in_solid %d hp %.1f/%.1f state %s @ %.0f %.0f %.0f%s", g.spec().id(), g.getId(),
                        g.measuredLength(), g.getParts().length, g.partsInSolid(), g.getHealth(), g.getMaxHealth(), g.brain().state(),
                        g.getX(), g.getY(), g.getZ(), g.isAlive() ? "" : " (dead)"));
            }
        }
        if (n == 0) say(ctx, "no giants loaded");
        return n;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx)
    {
        String id = StringArgumentType.getString(ctx, "id");
        ModGiants.Species sp = ModGiants.byId(id);
        if (sp == null)
        {
            ctx.getSource().sendFailure(Component.literal("Unknown giant: " + id + " (" + String.join(", ", ModGiants.ids()) + ")"));
            return 0;
        }
        // Source position, so `execute positioned X Y Z run ...` works from the console and RCON too.
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 pp = ctx.getSource().getPosition();
        for (int i = 0; i < ATTEMPTS; i++)
        {
            double dist = 40 + level.random.nextDouble() * 40, ang = level.random.nextDouble() * Math.PI * 2;
            Vec3 head = new Vec3(pp.x + Math.cos(ang) * dist, pp.y + (level.random.nextDouble() * 2 - 1) * 8, pp.z + Math.sin(ang) * dist);
            double toward = Math.atan2(pp.z - head.z, pp.x - head.x) + (level.random.nextDouble() - 0.5);
            GiantFauna g = GiantSpawner.create(level, sp, head, new Vec3(Math.cos(toward), 0, Math.sin(toward)));
            if (g != null)
            {
                say(ctx, String.format("Spawned %s at %.0f %.0f %.0f", id, head.x, head.y, head.z));
                return 1;
            }
        }
        ctx.getSource().sendFailure(Component.literal("No free water for " + id + " (" + ATTEMPTS + " tries)."));
        return 0;
    }
}
