package com.abyssia.research;

import com.abyssia.Abyssia;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * {@code /abyssia research check|unlock|reset <player> [technology]} (permission level 2):
 * check lists the player's research (or one technology), unlock grants one technology (none: all), reset clears one
 * technology with its dependents and scan progress (none: everything).
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ResearchCommand
{
    private static final SuggestionProvider<CommandSourceStack> TECHS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(TechnologyRegistry.all().stream().map(Technology::id), builder);

    private ResearchCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("research")
                        .then(Commands.literal("check")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> check(ctx, null))
                                        .then(Commands.argument("technology", ResourceLocationArgument.id()).suggests(TECHS)
                                                .executes(ctx -> check(ctx, ResourceLocationArgument.getId(ctx, "technology"))))))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> unlock(ctx, null))
                                        .then(Commands.argument("technology", ResourceLocationArgument.id()).suggests(TECHS)
                                                .executes(ctx -> unlock(ctx, ResourceLocationArgument.getId(ctx, "technology"))))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> reset(ctx, null))
                                        .then(Commands.argument("technology", ResourceLocationArgument.id()).suggests(TECHS)
                                                .executes(ctx -> reset(ctx, ResourceLocationArgument.getId(ctx, "technology"))))))));
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String text)
    {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    private static int check(CommandContext<CommandSourceStack> ctx, ResourceLocation tech) throws CommandSyntaxException
    {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResearchData data = ResearchData.load(player);
        String name = player.getGameProfile().getName();
        if (tech != null)
        {
            Optional<Technology> t = TechnologyRegistry.get(tech);
            if (t.isEmpty())
            {
                ctx.getSource().sendFailure(Component.literal("Unknown technology " + tech));
                return 0;
            }
            say(ctx, name + ": " + tech + (data.unlocked.contains(tech) ? " UNLOCKED" : " locked"));
            for (Technology.Requirement r : t.get().requirements())
                say(ctx, "  scan " + r.target() + " " + Math.min(data.count(r.target()), r.count()) + "/" + r.count());
            for (ResourceLocation p : t.get().prerequisites())
                say(ctx, "  needs " + p + (data.unlocked.contains(p) ? " (unlocked)" : " (locked)"));
            return 1;
        }
        say(ctx, name + ": " + data.unlocked.size() + "/" + TechnologyRegistry.all().size() + " technologies, "
                + data.scanned.size() + " targets scanned");
        for (Technology t : TechnologyRegistry.all())
        {
            StringBuilder sb = new StringBuilder("  ").append(data.unlocked.contains(t.id()) ? "[x] " : "[ ] ").append(t.id());
            if (!data.unlocked.contains(t.id()))
                for (Technology.Requirement r : t.requirements())
                    sb.append(' ').append(r.target().getPath()).append(' ').append(Math.min(data.count(r.target()), r.count())).append('/').append(r.count());
            say(ctx, sb.toString());
        }
        return data.unlocked.size();
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx, ResourceLocation tech) throws CommandSyntaxException
    {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String name = player.getGameProfile().getName();
        if (tech == null)
        {
            int n = 0;
            for (Technology t : TechnologyRegistry.all()) if (ResearchManager.unlock(player, t.id())) n++;
            say(ctx, "Unlocked " + n + " technologies for " + name);
            return n;
        }
        if (!TechnologyRegistry.exists(tech))
        {
            ctx.getSource().sendFailure(Component.literal("Unknown technology " + tech));
            return 0;
        }
        boolean done = ResearchManager.unlock(player, tech);
        say(ctx, done ? "Unlocked " + tech + " for " + name : name + " already has " + tech);
        return done ? 1 : 0;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, ResourceLocation tech) throws CommandSyntaxException
    {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String name = player.getGameProfile().getName();
        if (tech != null && !TechnologyRegistry.exists(tech))
        {
            ctx.getSource().sendFailure(Component.literal("Unknown technology " + tech));
            return 0;
        }
        ResearchManager.reset(player, tech);
        say(ctx, tech == null ? "Reset all research of " + name : "Reset " + tech + " (and dependents) of " + name);
        return 1;
    }
}
