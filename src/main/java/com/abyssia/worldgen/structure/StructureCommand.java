package com.abyssia.worldgen.structure;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.worldgen.DeepLayer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Seabed structure debugging (run in the overworld's deep layer, below the bedrock band at Y -64, e.g. via
 * {@code execute positioned <x> -200 <z> run ...}):
 * <ul>
 *     <li>{@code /abyssia structures here}: biome, its profile entries with their odds, and the structures covering this spot
 *     (with their preferred fauna)</li>
 *     <li>{@code /abyssia structures near [radius]}: every candidate position around you with its fate (placed, or why not:
 *     chance roll, biome, depth/slope, cave, vent field, overlap); full list as CSV in {@code run/abyssia_debug/}</li>
 *     <li>{@code /abyssia structures locate <id> [range]}: nearest placed instance of a structure</li>
 *     <li>{@code /abyssia structures render top <radius> [plants]} / {@code render section <x|z> <radius> <y0> <y1>}: PNG of the
 *     generated floor (hill-shaded, footprints outlined) or a vertical cut, in {@code run/abyssia_debug/}; generates chunks</li>
 *     <li>{@code /abyssia structures list}: loaded definitions and profiles</li>
 *     <li>{@code /abyssia structures stats}: painting time per chunk so far</li>
 * </ul>
 */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class StructureCommand
{
    private static final int CHAT_LINES = 24;

    private StructureCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("structures")
                        .then(Commands.literal("here").executes(StructureCommand::here))
                        .then(Commands.literal("near")
                                .executes(ctx -> near(ctx, 256))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 4096))
                                        .executes(ctx -> near(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("locate")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .suggests((c, b) -> {
                                            SeabedStructures s = SeabedStructures.of(c.getSource().getLevel());
                                            return SharedSuggestionProvider.suggestResource(s == null ? List.<ResourceLocation>of()
                                                    : s.slots().stream().map(SeabedStructures.Slot::id).distinct().toList(), b);
                                        })
                                        .executes(ctx -> locate(ctx, 20000))
                                        .then(Commands.argument("range", IntegerArgumentType.integer(64, 100000))
                                                .executes(ctx -> locate(ctx, IntegerArgumentType.getInteger(ctx, "range"))))))
                        .then(Commands.literal("render")
                                .then(Commands.literal("top")
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 512))
                                                .executes(ctx -> render(ctx, s -> StructureRender.top(ctx.getSource().getLevel(), origin(ctx),
                                                        IntegerArgumentType.getInteger(ctx, "radius"), s, false)))
                                                .then(Commands.literal("plants")
                                                        .executes(ctx -> render(ctx, s -> StructureRender.top(ctx.getSource().getLevel(), origin(ctx),
                                                                IntegerArgumentType.getInteger(ctx, "radius"), s, true))))))
                                .then(Commands.literal("section")
                                        .then(Commands.argument("axis", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("x", "z"), b))
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(8, 256))
                                                        .then(Commands.argument("y0", IntegerArgumentType.integer(DeepLayer.MIN_Y, DeepLayer.TOP_Y))
                                                                .then(Commands.argument("y1", IntegerArgumentType.integer(DeepLayer.MIN_Y, DeepLayer.TOP_Y))
                                                                        .executes(ctx -> render(ctx, s -> StructureRender.section(ctx.getSource().getLevel(), origin(ctx),
                                                                                StringArgumentType.getString(ctx, "axis").equals("x"), IntegerArgumentType.getInteger(ctx, "radius"),
                                                                                IntegerArgumentType.getInteger(ctx, "y0"), IntegerArgumentType.getInteger(ctx, "y1"))))))))))
                        .then(Commands.literal("list").executes(StructureCommand::list))
                        .then(Commands.literal("stats").executes(ctx -> {
                            SeabedStructures s = structures(ctx);
                            if (s == null) return 0;
                            ctx.getSource().sendSuccess(() -> Component.literal(s.stats()), false);
                            return 1;
                        }))));
    }

    private interface Render
    {
        String run(SeabedStructures structures) throws IOException;
    }

    private static int render(CommandContext<CommandSourceStack> ctx, Render render)
    {
        SeabedStructures s = structures(ctx);
        if (s == null) return 0;
        try
        {
            say(ctx, "Wrote " + render.run(s));
            return 1;
        }
        catch (IOException e)
        {
            ctx.getSource().sendFailure(Component.literal("Render failed: " + e.getMessage()));
            return 0;
        }
    }

    private static BlockPos origin(CommandContext<CommandSourceStack> ctx)
    {
        return BlockPos.containing(ctx.getSource().getPosition());
    }

    @Nullable
    private static SeabedStructures structures(CommandContext<CommandSourceStack> ctx)
    {
        SeabedStructures s = SeabedStructures.of(ctx.getSource().getLevel());
        if (s == null) ctx.getSource().sendFailure(Component.literal("No seabed structures in this dimension (use it in the overworld's deep layer)"));
        else if (!Config.STRUCTURES_ENABLED.get()) ctx.getSource().sendSuccess(() -> Component.literal("Note: seabed_structures.enabled is false in the config").withStyle(ChatFormatting.YELLOW), false);
        return s;
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String text)
    {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    /** Expected instances per 1000 chunks of the biome: chance per cell over the cell's area. */
    private static double perThousandChunks(SeabedStructures.Slot slot)
    {
        double chance = slot.entry().chance() * slot.profile().density() * Config.STRUCTURE_DENSITY.get()
                * (slot.definition().category() == SeabedStructure.Category.LANDMARK ? Config.LANDMARK_STRUCTURE_CHANCE.get() : 1.0);
        return Math.min(1, chance) * 1000.0 * 256 / ((double) slot.cellSize() * slot.cellSize());
    }

    private static String describe(SeabedStructures.Slot slot)
    {
        SeabedStructure d = slot.definition();
        return slot.id() + " [" + d.category().getSerializedName() + "/" + d.tier().getSerializedName() + "/" + d.anchor().getSerializedName()
                + ", " + d.formation().type() + ", r" + d.footprintRadius() + "]";
    }

    private static int here(CommandContext<CommandSourceStack> ctx)
    {
        SeabedStructures s = structures(ctx);
        if (s == null) return 0;
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        ResourceKey<Biome> biome = s.biome(pos.getX(), pos.getZ());
        say(ctx, "Biome " + (biome == null ? "?" : biome.location()) + " at " + pos.getX() + " " + pos.getZ());
        int entries = 0;
        for (SeabedStructures.Slot slot : s.slots())
        {
            if (biome == null || !slot.biomes().contains(biome)) continue;
            entries++;
            say(ctx, String.format(java.util.Locale.ROOT, "  %s: chance %.3f/cell, cell %d, min gap %d -> ~%.2f per 1000 chunks (profile %s)",
                    describe(slot), slot.entry().chance(), slot.cellSize(), slot.entry().minDistance(), perThousandChunks(slot), slot.profileId().getPath()));
        }
        if (entries == 0) say(ctx, "  no structure profile for this biome");
        List<SeabedStructures.Candidate> covering = s.at(pos.getX(), pos.getZ());
        if (covering.isEmpty()) say(ctx, "No structure covers this spot");
        for (SeabedStructures.Candidate c : covering)
        {
            say(ctx, "Inside " + describe(c.slot()) + " centred " + c.x() + " " + c.site().baseY + " " + c.z()
                    + (c.site().definition.preferredMobs().isEmpty() ? "" : ", preferred fauna " + c.site().definition.preferredMobs()));
        }
        return 1;
    }

    private static int near(CommandContext<CommandSourceStack> ctx, int radius)
    {
        SeabedStructures s = structures(ctx);
        if (s == null) return 0;
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        List<SeabedStructures.Candidate> all = s.explain(pos.getX() - radius, pos.getZ() - radius, pos.getX() + radius, pos.getZ() + radius);
        Map<SeabedStructures.Status, Integer> counts = new EnumMap<>(SeabedStructures.Status.class);
        all.forEach(c -> counts.merge(c.status(), 1, Integer::sum));
        say(ctx, all.size() + " candidate cells within " + radius + ": " + counts);
        Comparator<SeabedStructures.Candidate> byDistance = Comparator.comparingDouble(c -> Math.hypot(c.x() - pos.getX(), c.z() - pos.getZ()));
        // Chat shows what matters here: placed ones first, then local rejections; rolls and other biomes' entries go to the CSV.
        List<SeabedStructures.Candidate> shown = all.stream()
                .filter(c -> c.status() != SeabedStructures.Status.NO_ROLL && c.status() != SeabedStructures.Status.WRONG_BIOME)
                .sorted(Comparator.comparing((SeabedStructures.Candidate c) -> !c.placed()).thenComparing(byDistance)).toList();
        for (int i = 0; i < Math.min(CHAT_LINES, shown.size()); i++)
        {
            SeabedStructures.Candidate c = shown.get(i);
            ChatFormatting colour = c.placed() ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            String text = String.format(java.util.Locale.ROOT, "%s %s @ %d %s %d (%dm, chance %.3f)%s", c.status().label(), c.slot().id().getPath(), c.x(),
                    c.site() != null ? String.valueOf(c.site().baseY) : "~", c.z(), (int) Math.hypot(c.x() - pos.getX(), c.z() - pos.getZ()), c.chance(),
                    c.reason().isEmpty() ? "" : ": " + c.reason());
            ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(colour), false);
        }
        if (shown.size() > CHAT_LINES) say(ctx, "... " + (shown.size() - CHAT_LINES) + " more in the CSV");
        try
        {
            say(ctx, "Wrote " + csv(ctx.getSource().getLevel(), pos, all));
        }
        catch (IOException e)
        {
            ctx.getSource().sendFailure(Component.literal("CSV failed: " + e.getMessage()));
        }
        return (int) all.stream().filter(SeabedStructures.Candidate::placed).count();
    }

    private static String csv(ServerLevel level, BlockPos pos, List<SeabedStructures.Candidate> all) throws IOException
    {
        StringBuilder out = new StringBuilder("structure,profile,category,tier,status,x,base_y,z,distance,chance,reason\n");
        for (SeabedStructures.Candidate c : all)
        {
            SeabedStructure d = c.slot().definition();
            out.append(c.slot().id()).append(',').append(c.slot().profileId()).append(',').append(d.category().getSerializedName()).append(',')
                    .append(d.tier().getSerializedName()).append(',').append(c.status().label()).append(',').append(c.x()).append(',')
                    .append(c.site() != null ? c.site().baseY : "").append(',').append(c.z()).append(',')
                    .append((int) Math.hypot(c.x() - pos.getX(), c.z() - pos.getZ())).append(',').append(SeabedStructures.fmt(c.chance())).append(',')
                    .append('"').append(c.reason().replace('"', '\'')).append("\"\n");
        }
        File dir = new File(level.getServer().getServerDirectory().toAbsolutePath().toFile(), Abyssia.MODID + "_debug");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File file = new File(dir, "structures_" + pos.getX() + "_" + pos.getZ() + ".csv");
        Files.writeString(file.toPath(), out);
        return file.getPath();
    }

    private static int locate(CommandContext<CommandSourceStack> ctx, int range)
    {
        SeabedStructures s = structures(ctx);
        if (s == null) return 0;
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        if (s.slots().stream().noneMatch(slot -> slot.id().equals(id)))
        {
            ctx.getSource().sendFailure(Component.literal(id + " is in no structure profile"));
            return 0;
        }
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        SeabedStructures.Candidate c = s.locate(id, pos.getX(), pos.getZ(), range);
        if (c == null)
        {
            ctx.getSource().sendFailure(Component.literal("No " + id + " within " + range + " blocks"));
            return 0;
        }
        say(ctx, id + " at " + c.x() + " " + c.site().baseY + " " + c.z() + " (" + (int) Math.hypot(c.x() - pos.getX(), c.z() - pos.getZ())
                + " blocks, profile " + c.slot().profileId().getPath() + ")");
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx)
    {
        SeabedStructures s = structures(ctx);
        if (s == null) return 0;
        say(ctx, s.slots().size() + " profile entries:");
        s.slots().stream().sorted(Comparator.comparing((SeabedStructures.Slot slot) -> slot.profileId().toString()).thenComparing(slot -> slot.tier()))
                .forEach(slot -> say(ctx, String.format(java.util.Locale.ROOT, "  %s: %s ~%.2f/1000 chunks", slot.profileId().getPath(), describe(slot),
                        perThousandChunks(slot))));
        return s.slots().size();
    }
}
