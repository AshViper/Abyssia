package com.abyssia.fauna.external;

import com.abyssia.Abyssia;
import com.abyssia.fauna.FaunaSpawnRule;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

/**
 * Testing aid for the external animals (ops only, merged into {@code /abyssia fauna}):
 * <ul>
 *     <li>{@code /abyssia fauna external}: what may spawn in the deep ocean, and what was kept out and why</li>
 *     <li>{@code /abyssia fauna external entity <id>}: the full verdict on one entity type (score, reasons, profile,
 *     biomes)</li>
 *     <li>{@code /abyssia fauna external here}: the external spawn rules of the biome at this position</li>
 *     <li>{@code /abyssia fauna external rescan}: rebuild everything now (and retry types dropped for failing their
 *     spawn checks)</li>
 * </ul>
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class ExternalFaunaCommand
{
    private ExternalFaunaCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("fauna").then(Commands.literal("external")
                        .executes(ExternalFaunaCommand::summary)
                        .then(Commands.literal("entity")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                        .executes(ExternalFaunaCommand::entity)))
                        .then(Commands.literal("here").executes(ExternalFaunaCommand::here))
                        .then(Commands.literal("rescan").executes(ExternalFaunaCommand::rescan)))));
    }

    private static int summary(CommandContext<CommandSourceStack> ctx)
    {
        ExternalSpawnProvider.Snapshot s = ExternalSpawnProvider.snapshot(ctx.getSource().getServer());
        say(ctx, String.format("External fauna (%s): %d of %d entity types are sea animals, %d may spawn in the deep ocean",
                ExternalFaunaConfig.ENABLED.get() ? "enabled" : "disabled", s.detected(), s.classifications().size(), s.profiles().size()));
        for (DeepSeaSpawnProfile p : s.profiles().values())
        {
            List<String> biomes = ExternalSpawnProvider.biomesOf(s, p.entityType());
            say(ctx, "  " + p.describe() + " - " + (biomes.isEmpty() ? "no biome" : biomes.size() + " biomes")
                    + (ExternalSpawnProvider.demoted().contains(p.entityType()) ? " - DROPPED (fails its spawn checks)" : ""));
        }
        s.dropped().forEach((type, why) -> say(ctx, "  not spawned: " + EntityType.getKey(type) + " (" + why + ")"));
        return s.profiles().size();
    }

    private static int entity(CommandContext<CommandSourceStack> ctx)
    {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id))
        {
            say(ctx, "No entity type " + id);
            return 0;
        }
        ExternalSpawnProvider.Snapshot s = ExternalSpawnProvider.snapshot(ctx.getSource().getServer());
        OceanMobClassification c = s.classifications().get(type);
        if (c == null)
        {
            say(ctx, id + " is not scanned (Abyssia's own fauna, or disabled by feature flags)");
            return 0;
        }
        say(ctx, c.describe());
        say(ctx, String.format("  category %s, spawn placement %s, size %.2f, health %.0f, %s%s%s; %s", c.category().getSerializedName(),
                c.placement(), c.extent(), c.maxHealth(), c.hostile() ? "hostile" : "not hostile", c.airBreather() ? ", air breather" : "",
                c.despawns() ? "" : ", never despawns", c.evidence().describe()));
        DeepSeaSpawnProfile p = s.profiles().get(type);
        if (p != null)
        {
            say(ctx, "  " + p.describe());
            say(ctx, "  biomes: " + String.join(", ", ExternalSpawnProvider.biomesOf(s, type)));
        }
        else if (s.dropped().containsKey(type))
        {
            say(ctx, "  not spawned: " + s.dropped().get(type));
        }
        else
        {
            say(ctx, "  not a sea animal (needs score >= " + ExternalFaunaConfig.MIN_SCORE.get() + " and no exclusion; or whitelist it)");
        }
        return 1;
    }

    private static int here(CommandContext<CommandSourceStack> ctx)
    {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        ExternalSpawnProvider.Snapshot s = ExternalSpawnProvider.snapshot(ctx.getSource().getServer());
        ResourceKey<Biome> biome = level.getBiome(pos).unwrapKey().orElse(null);
        List<FaunaSpawnRule> rules = biome != null ? s.byBiome().getOrDefault(biome, List.of()) : List.of();
        say(ctx, String.format("%s: %d external species, %d spawned by this system within 96 blocks (limit %d)",
                biome != null ? biome.location() : "?", rules.size(), ExternalSpawnProvider.countSpawned(level, pos, s), ExternalFaunaConfig.MAX_PER_PLAYER.get()));
        for (FaunaSpawnRule r : rules)
        {
            say(ctx, String.format("  %s: %s, weight %.1f, %.0f-%.0f m, %s, cap %d/%d", EntityType.getKey(r.entity()), r.role(), r.weight(),
                    r.depth().min(), r.depth().max(), r.placement().getSerializedName(), r.cap().count(), r.cap().radius()));
        }
        return rules.size();
    }

    private static int rescan(CommandContext<CommandSourceStack> ctx)
    {
        ExternalSpawnProvider.Snapshot s = ExternalSpawnProvider.rescan(ctx.getSource().getServer());
        say(ctx, "Rescanned: " + s.detected() + " sea animals, " + s.profiles().size() + " may spawn (details in the log with debug_log = true)");
        return s.profiles().size();
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String text)
    {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }
}
