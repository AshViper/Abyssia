package com.abyssia.worldgen.cave;

import com.abyssia.Abyssia;
import com.abyssia.worldgen.DeepLayer;
import com.abyssia.worldgen.OceanChunkGenerator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Testing aid (ops only), run in the overworld's deep layer (below the bedrock band):
 * <ul>
 *     <li>{@code /abyssia caves list [range]}: cave systems near you, with their type, rarity and route</li>
 *     <li>{@code /abyssia caves locate <type or landmark>}: the nearest system of that type or landmark</li>
 *     <li>{@code /abyssia caves here}: which cave space you are in and its environment</li>
 *     <li>{@code /abyssia caves stats}: cave generation time per chunk so far</li>
 * </ul>
 * and in any dimension {@code /abyssia map <radius> <step>}: a biome and seabed map around you (PNG + CSV) of the layer
 * you are in (run it below Y -64 for the deep layer).
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class CaveCommand
{
    private static final int LOCATE_RADIUS_CELLS = 48;
    private static final int LOCATE_RADIUS_MINOR = 24;
    /** Cavern centre landmarks are found by building whole systems, so that search stays nearer. */
    private static final int LOCATE_RADIUS_CAVERN = 16;
    private static final String CAVERN_PREFIX = "cavern_";

    private CaveCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        List<String> targets = new ArrayList<>();
        Arrays.stream(CaveType.values()).forEach(t -> targets.add(t.getSerializedName()));
        Arrays.stream(CaveLandmark.values()).forEach(l -> targets.add(l.getSerializedName()));
        Arrays.stream(CavernCenter.values()).forEach(c -> targets.add(CAVERN_PREFIX + c.getSerializedName()));
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("caves")
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx, 400))
                                .then(Commands.argument("range", IntegerArgumentType.integer(16, 4000))
                                        .executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "range")))))
                        .then(Commands.literal("locate")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(targets, b))
                                        .executes(ctx -> locate(ctx, StringArgumentType.getString(ctx, "target")))))
                        .then(Commands.literal("here").executes(CaveCommand::here))
                        .then(Commands.literal("slice")
                                .then(Commands.argument("y", IntegerArgumentType.integer(-512, 512))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 256))
                                                .executes(ctx -> render(ctx, () -> CaveDebugRender.slice(ctx.getSource().getLevel(), origin(ctx),
                                                        IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "radius")))))))
                        .then(Commands.literal("section")
                                .then(Commands.argument("axis", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("x", "z"), b))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 256))
                                                .then(Commands.argument("y0", IntegerArgumentType.integer(-512, 512))
                                                        .then(Commands.argument("y1", IntegerArgumentType.integer(-512, 512))
                                                                .executes(ctx -> render(ctx, () -> CaveDebugRender.section(ctx.getSource().getLevel(), origin(ctx),
                                                                        StringArgumentType.getString(ctx, "axis").equals("x"), IntegerArgumentType.getInteger(ctx, "radius"),
                                                                        IntegerArgumentType.getInteger(ctx, "y0"), IntegerArgumentType.getInteger(ctx, "y1")))))))))
                        .then(Commands.literal("census")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                        .then(Commands.argument("y0", IntegerArgumentType.integer(-512, 512))
                                                .then(Commands.argument("y1", IntegerArgumentType.integer(-512, 512))
                                                        .executes(CaveCommand::census)))))
                        .then(Commands.literal("verify")
                                .then(Commands.argument("samples", IntegerArgumentType.integer(1, 5000))
                                        .executes(ctx -> verify(ctx, IntegerArgumentType.getInteger(ctx, "samples")))))
                        .then(Commands.literal("stats").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(CaveGenerator.stats()), false);
                            return 1;
                        })))
                .then(Commands.literal("shafts")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(64, 1600))
                                .executes(ctx -> shafts(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("map")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(64, 16384))
                                .then(Commands.argument("step", IntegerArgumentType.integer(4, 512))
                                        .executes(ctx -> render(ctx, () -> CaveDebugRender.map(ctx.getSource().getLevel(), origin(ctx),
                                                IntegerArgumentType.getInteger(ctx, "radius"), IntegerArgumentType.getInteger(ctx, "step"))))))));
    }

    @Nullable
    private static CaveNetwork network(CommandContext<CommandSourceStack> ctx)
    {
        ServerLevel level = ctx.getSource().getLevel();
        if (level.getChunkSource().getGenerator() instanceof OceanChunkGenerator generator)
        {
            CaveNetwork network = generator.caveNetwork(level.getChunkSource().randomState(), level.registryAccess(), level.getSeed());
            if (!network.isEmpty()) return network;
        }
        ctx.getSource().sendFailure(Component.literal("No cave network in this dimension (use it in the overworld's deep layer, below Y " + DeepLayer.TOP_Y + ")"));
        return null;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, int range)
    {
        CaveNetwork network = network(ctx);
        if (network == null) return 0;
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        List<CaveSystem> systems = network.systemsNear(pos.getX() - range, pos.getZ() - range, pos.getX() + range, pos.getZ() + range);
        ctx.getSource().sendSuccess(() -> Component.literal(systems.size() + " cave system(s) within " + range + " blocks"), false);
        for (CaveSystem s : systems)
        {
            ctx.getSource().sendSuccess(() -> Component.literal((s.minor ? "minor " : "") + s.rarity().name().toLowerCase() + " @ " + s.x + " " + s.y + " " + s.z
                    + " (" + s.shapes.size() + " shapes): " + s.summary + entrances(s)), false);
        }
        return systems.size();
    }

    private static String entrances(CaveSystem s)
    {
        StringBuilder out = new StringBuilder();
        for (CaveSpace space : s.spaces)
        {
            if (space.cavern == null || space.cavern.space != space) continue;
            Cavern c = space.cavern;
            out.append(" {").append(c.templateId.getPath()).append(' ').append(c.tier.name().toLowerCase()).append(" cavern @")
                    .append((int) c.x).append(' ').append((int) c.floor0).append(' ').append((int) c.z)
                    .append(c.center() != null ? ", centre " + c.center().getSerializedName() : "").append(", ").append(c.lakes().size()).append(" lakes}");
        }
        for (CaveSystem.Site site : s.sites)
        {
            if (site.kind() != CaveSystem.SiteKind.COLLAPSE)
            {
                out.append(out.isEmpty() ? " | " : ", ").append(site.kind().name().toLowerCase()).append('@')
                        .append((int) site.x()).append(' ').append((int) site.y()).append(' ').append((int) site.z());
            }
        }
        return out.toString();
    }

    /** The nearest cavern whose centre landmark is {@code cavern_<centre>} (e.g. cavern_ancient_plant). */
    private static int locateCavern(CommandContext<CommandSourceStack> ctx, CaveNetwork network, String target)
    {
        String name = target.substring(CAVERN_PREFIX.length());
        CavernCenter center = Stream.of(CavernCenter.values()).filter(c -> c.getSerializedName().equals(name)).findFirst().orElse(null);
        if (center == null)
        {
            ctx.getSource().sendFailure(Component.literal("Unknown cavern centre: " + name));
            return 0;
        }
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        int cell = network.cellSize();
        int cx = Math.floorDiv(pos.getX(), cell), cz = Math.floorDiv(pos.getZ(), cell);
        for (int ring = 0; ring <= LOCATE_RADIUS_CAVERN; ring++)
        {
            Cavern best = null;
            CaveSystem bestSystem = null;
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++)
            {
                for (int dz = -ring; dz <= ring; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || network.plan(cx + dx, cz + dz) == null) continue;
                    CaveSystem system = network.system(cx + dx, cz + dz);
                    if (system == null) continue;
                    for (CaveSpace space : system.spaces)
                    {
                        Cavern c = space.cavern;
                        if (c == null || c.space != space || c.center() != center) continue;
                        double d = Math.hypot(c.centerX() - pos.getX(), c.centerZ() - pos.getZ());
                        if (d < bestDistance)
                        {
                            bestDistance = d;
                            best = c;
                            bestSystem = system;
                        }
                    }
                }
            }
            if (best != null)
            {
                Cavern found = best;
                CaveSystem system = bestSystem;
                ctx.getSource().sendSuccess(() -> Component.literal(target + " at " + (int) found.centerX() + " " + (int) found.floor0 + " " + (int) found.centerZ()
                        + " (" + (int) Math.hypot(found.centerX() - pos.getX(), found.centerZ() - pos.getZ()) + " blocks): " + system.summary + entrances(system)), false);
                return 1;
            }
        }
        ctx.getSource().sendFailure(Component.literal("No " + target + " within " + LOCATE_RADIUS_CAVERN * cell + " blocks"));
        return 0;
    }

    private static int locate(CommandContext<CommandSourceStack> ctx, String target)
    {
        CaveNetwork network = network(ctx);
        if (network == null) return 0;
        if (target.startsWith(CAVERN_PREFIX)) return locateCavern(ctx, network, target);
        CaveType type = Stream.of(CaveType.values()).filter(t -> t.getSerializedName().equals(target)).findFirst().orElse(null);
        CaveLandmark landmark = Stream.of(CaveLandmark.values()).filter(l -> l.getSerializedName().equals(target)).findFirst().orElse(null);
        if (type == null && landmark == null)
        {
            ctx.getSource().sendFailure(Component.literal("Unknown cave type or landmark: " + target));
            return 0;
        }
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        int cell = network.cellSize();
        if (type == CaveType.SEA_ARCH || type == CaveType.SMALL_SEA_CAVE || type == CaveType.ERODED_CAVE)
        {
            if (locateMinor(ctx, network, pos, type, target)) return 1;
        }
        int cx = Math.floorDiv(pos.getX(), cell), cz = Math.floorDiv(pos.getZ(), cell);
        // Rings outward: plans are light (no layout), so a wide search stays quick.
        for (int ring = 0; ring <= LOCATE_RADIUS_CELLS; ring++)
        {
            CaveNetworkGenerator.Plan best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++)
            {
                for (int dz = -ring; dz <= ring; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    CaveNetworkGenerator.Plan p = network.plan(cx + dx, cz + dz);
                    if (p == null || (landmark != null ? p.landmark() != landmark : p.type() != type)) continue;
                    double d = Math.hypot(p.x() - pos.getX(), p.z() - pos.getZ());
                    if (d < bestDistance)
                    {
                        bestDistance = d;
                        best = p;
                    }
                }
            }
            if (best != null)
            {
                CaveNetworkGenerator.Plan found = best;
                CaveSystem system = network.system(found.cellX(), found.cellZ());
                ctx.getSource().sendSuccess(() -> Component.literal(target + " at " + (int) found.x() + " " + (int) found.y() + " " + (int) found.z()
                        + " (" + (int) Math.hypot(found.x() - pos.getX(), found.z() - pos.getZ()) + " blocks): " + system.summary + entrances(system)), false);
                return 1;
            }
        }
        ctx.getSource().sendFailure(Component.literal("No " + target + " within " + LOCATE_RADIUS_CELLS * cell + " blocks"));
        return 0;
    }

    @FunctionalInterface
    private interface Render
    {
        String run() throws java.io.IOException;
    }

    /**
     * {@code /abyssia shafts <radius>}: the hadal shafts (the openings down to the abyss layer) within a radius, from the
     * generator's density (no chunks are generated): a column is a shaft where the abyss ceiling slab is open water.
     */
    private static int shafts(CommandContext<CommandSourceStack> ctx, int radius)
    {
        ServerLevel level = ctx.getSource().getLevel();
        DensityFunction density = level.getChunkSource().randomState().router().finalDensity();
        BlockPos centre = origin(ctx);
        int y = DeepLayer.ABYSS_CEILING_BOTTOM_Y + 24;
        List<BlockPos> found = new ArrayList<>();
        for (int x = centre.getX() - radius; x <= centre.getX() + radius; x += 16)
        {
            for (int z = centre.getZ() - radius; z <= centre.getZ() + radius; z += 16)
            {
                if (density.compute(new DensityFunction.SinglePointContext(x, y, z)) >= 0) continue;
                BlockPos p = new BlockPos(x, y, z);
                if (found.stream().noneMatch(f -> f.distSqr(p) < 48 * 48)) found.add(p);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(found.size() + " hadal shaft(s) within " + radius + " blocks"), false);
        found.stream().sorted(java.util.Comparator.comparingDouble(f -> f.distSqr(centre))).limit(20).forEach(f ->
                ctx.getSource().sendSuccess(() -> Component.literal("  " + f.getX() + " " + f.getZ() + " (" + (int) Math.sqrt(f.distSqr(centre)) + " blocks)"), false));
        return found.size();
    }

    private static BlockPos origin(CommandContext<CommandSourceStack> ctx)
    {
        return BlockPos.containing(ctx.getSource().getPosition());
    }

    private static int render(CommandContext<CommandSourceStack> ctx, Render render)
    {
        try
        {
            String file = render.run();
            ctx.getSource().sendSuccess(() -> Component.literal("Wrote " + file), false);
            return 1;
        }
        catch (java.io.IOException e)
        {
            ctx.getSource().sendFailure(Component.literal("Render failed: " + e.getMessage()));
            return 0;
        }
    }

    /** Checks the plan pre-filter against brute force for random chunks: it must never miss a system. */
    private static int verify(CommandContext<CommandSourceStack> ctx, int samples)
    {
        CaveNetwork network = network(ctx);
        if (network == null) return 0;
        java.util.Random random = new java.util.Random(samples);
        int missed = 0, systems = 0;
        for (int i = 0; i < samples; i++)
        {
            int x0 = (random.nextInt(600) - 300) * 16, z0 = (random.nextInt(600) - 300) * 16;
            List<CaveSystem> filtered = network.systemsNear(x0 - 1, z0 - 1, x0 + 16, z0 + 16);
            for (CaveSystem s : network.systemsNearUnfiltered(x0 - 1, z0 - 1, x0 + 16, z0 + 16))
            {
                // Only a real overlap counts: a shape or a decorated site reaching the chunk (with its one-block rim).
                boolean reaches = false;
                for (CaveShape shape : s.shapes)
                {
                    reaches |= shape.maxX >= x0 - 1 && shape.minX <= x0 + 16 && shape.maxZ >= z0 - 1 && shape.minZ <= z0 + 16;
                }
                for (CaveSystem.Site site : s.sites)
                {
                    reaches |= site.x() + site.radius() >= x0 - 1 && site.x() - site.radius() <= x0 + 16
                            && site.z() + site.radius() >= z0 - 1 && site.z() - site.radius() <= z0 + 16;
                }
                if (!reaches) continue;
                systems++;
                if (!filtered.contains(s))
                {
                    missed++;
                    String info = "missed chunk " + x0 + "," + z0 + ": " + s.summary;
                    ctx.getSource().sendSuccess(() -> Component.literal(info), false);
                }
            }
        }
        int m = missed, n = systems;
        ctx.getSource().sendSuccess(() -> Component.literal("verify: " + samples + " chunks, " + n + " system hits, " + m + " missed by the pre-filter"), false);
        return m == 0 ? 1 : 0;
    }

    private static int census(CommandContext<CommandSourceStack> ctx)
    {
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        java.util.Map<String, Integer> counts = CaveDebugRender.census(ctx.getSource().getLevel(), origin(ctx), radius,
                IntegerArgumentType.getInteger(ctx, "y0"), IntegerArgumentType.getInteger(ctx, "y1"));
        StringBuilder line = new StringBuilder();
        counts.forEach((k, v) -> line.append(k).append('=').append(v).append(' '));
        ctx.getSource().sendSuccess(() -> Component.literal(line.toString().trim()), false);
        return counts.size();
    }

    /** Nearest minor cave (small sea cave, sea arch, eroded passage) of this type, searched ring by ring. */
    private static boolean locateMinor(CommandContext<CommandSourceStack> ctx, CaveNetwork network, BlockPos pos, CaveType type, String target)
    {
        int mx = Math.floorDiv(pos.getX(), CaveNetwork.MINOR_CELL), mz = Math.floorDiv(pos.getZ(), CaveNetwork.MINOR_CELL);
        for (int ring = 0; ring <= LOCATE_RADIUS_MINOR; ring++)
        {
            for (int dx = -ring; dx <= ring; dx++)
            {
                for (int dz = -ring; dz <= ring; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    CaveSystem s = network.minor(mx + dx, mz + dz);
                    if (s.isEmpty() || s.type != type) continue;
                    ctx.getSource().sendSuccess(() -> Component.literal(target + " (minor) at " + s.x + " " + s.y + " " + s.z + " ("
                            + (int) Math.hypot(s.x - pos.getX(), s.z - pos.getZ()) + " blocks): " + s.summary + entrances(s)), false);
                    return true;
                }
            }
        }
        return false;
    }

    private static int here(CommandContext<CommandSourceStack> ctx)
    {
        CaveNetwork network = network(ctx);
        if (network == null) return 0;
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        CaveShape nearest = null;
        double best = Double.MAX_VALUE;
        for (CaveSystem s : network.systemsNear(pos.getX(), pos.getZ(), pos.getX(), pos.getZ()))
        {
            for (CaveShape shape : s.shapes)
            {
                if (shape.kind != CaveShape.Kind.CARVE || !shape.intersects(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ())) continue;
                double d = shape.distance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (d < best)
                {
                    best = d;
                    nearest = shape;
                }
            }
        }
        if (nearest == null || best > 4)
        {
            ctx.getSource().sendSuccess(() -> Component.literal("Not in a cave of the network (seabed at y " + network.seabed(pos.getX(), pos.getZ()) + ")"), false);
            return 0;
        }
        CaveSpace space = nearest.space;
        double distance = best;
        ctx.getSource().sendSuccess(() -> Component.literal(space.role.name().toLowerCase() + " of a " + space.type.getSerializedName()
                + (space.landmark != null ? " [" + space.landmark.getSerializedName() + "]" : "") + ", environment " + space.environmentId
                + ", radius " + (int) space.radius + (space.hasLake() ? ", lake surface y " + space.waterLevel : "")
                + String.format(" (%.1f blocks from its surface)", -distance)), false);
        if (space.cavern != null)
        {
            Cavern cavern = space.cavern;
            ctx.getSource().sendSuccess(() -> Component.literal(cavern.describe(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)), false);
        }
        return 1;
    }
}
