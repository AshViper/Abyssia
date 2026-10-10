package com.abyssia.worldgen.cave;

import com.abyssia.Abyssia;
import com.abyssia.worldgen.DeepLayer;
import com.abyssia.worldgen.DepthBand;
import com.abyssia.worldgen.OceanChunkGenerator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
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
 *     <li>{@code /abyssia caves list [range]}: cave systems near you (the shallow network and the crust windows B', C, D, E),
 *     with their window, biome, environment, type, rarity and route</li>
 *     <li>{@code /abyssia caves locate <type or landmark>}: the nearest system of that type or landmark (windows included)</li>
 *     <li>{@code /abyssia caves here}: which cave space you are in, its window, biome and environment</li>
 *     <li>{@code /abyssia caves census <radius> <y0> <y1>}: cave volume by environment per window (from the layouts), plus block counts for small boxes</li>
 *     <li>{@code /abyssia caves stats}: cave generation time per chunk so far, and per crust window</li>
 * </ul>
 * and in any dimension {@code /abyssia map <radius> <step>}: a biome and seabed map around you (PNG + CSV) of the layer
 * you are in (run it below Y -64 for the deep layer).
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class CaveCommand
{
    private static final int LOCATE_RADIUS_CELLS = 48;
    private static final int LOCATE_RADIUS_MINOR = 24;
    /** Cells searched around you in each crust window (their cells are bigger than the shallow network's). */
    private static final int LOCATE_RADIUS_WINDOW = 24;
    /** Cavern centre landmarks are found by building whole systems, so that search stays nearer. */
    private static final int LOCATE_RADIUS_CAVERN = 16;
    private static final String CAVERN_PREFIX = "cavern_";
    /** AB03: {@code hall[_<environment>][_<large|massive|mega>]} locate targets; environments are cave_environment ids plus aliases. */
    private static final String HALL_PREFIX = "hall";
    private static final List<String> HALL_SIZES = List.of("large", "massive", "mega");
    private static final List<String> HALL_ENVIRONMENTS = List.of("abyssal", "plain", "luminous", "forest", "crystal", "mineral", "thermal", "volcanic", "magma",
            "hot", "frozen", "toxic", "anomaly", "eroded", "trench", "underground_sea");

    private CaveCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        List<String> targets = new ArrayList<>();
        Arrays.stream(CaveType.values()).forEach(t -> targets.add(t.getSerializedName()));
        Arrays.stream(CaveLandmark.values()).forEach(l -> targets.add(l.getSerializedName()));
        Arrays.stream(CavernCenter.values()).forEach(c -> targets.add(CAVERN_PREFIX + c.getSerializedName()));
        targets.add(HALL_PREFIX);
        for (String env : HALL_ENVIRONMENTS)
        {
            targets.add(HALL_PREFIX + "_" + env);
            for (String size : HALL_SIZES) targets.add(HALL_PREFIX + "_" + env + "_" + size);
        }
        for (String size : HALL_SIZES) targets.add(HALL_PREFIX + "_" + size);
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
                        .then(Commands.literal("hall")
                                .executes(ctx -> hall(ctx, Mth.floor(ctx.getSource().getPosition().x), Mth.floor(ctx.getSource().getPosition().z)))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> hall(ctx, IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z"))))))
                        .then(Commands.literal("slice")
                                .then(Commands.argument("y", IntegerArgumentType.integer(DeepLayer.MIN_Y, -64))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 256))
                                                .executes(ctx -> render(ctx, () -> CaveDebugRender.slice(ctx.getSource().getLevel(), origin(ctx),
                                                        IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "radius")))))))
                        .then(Commands.literal("section")
                                .then(Commands.argument("axis", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("x", "z"), b))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 256))
                                                .then(Commands.argument("y0", IntegerArgumentType.integer(DeepLayer.MIN_Y, -64))
                                                        .then(Commands.argument("y1", IntegerArgumentType.integer(DeepLayer.MIN_Y, -64))
                                                                .executes(ctx -> render(ctx, () -> CaveDebugRender.section(ctx.getSource().getLevel(), origin(ctx),
                                                                        StringArgumentType.getString(ctx, "axis").equals("x"), IntegerArgumentType.getInteger(ctx, "radius"),
                                                                        IntegerArgumentType.getInteger(ctx, "y0"), IntegerArgumentType.getInteger(ctx, "y1")))))))))
                        .then(Commands.literal("cavities")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(64, 2000))
                                        .executes(ctx -> cavities(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("census")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                        .then(Commands.argument("y0", IntegerArgumentType.integer(DeepLayer.MIN_Y, -64))
                                                .then(Commands.argument("y1", IntegerArgumentType.integer(DeepLayer.MIN_Y, -64))
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
        List<CaveSystem> systems = network.systemsNearAll(pos.getX() - range, pos.getZ() - range, pos.getX() + range, pos.getZ() + range);
        ctx.getSource().sendSuccess(() -> Component.literal(systems.size() + " cave system(s) within " + range + " blocks"), false);
        for (CaveSystem s : systems)
        {
            ctx.getSource().sendSuccess(() -> Component.literal(where(network, s) + (s.minor ? "minor " : "") + s.rarity().name().toLowerCase() + " @ " + s.x + " " + s.y + " " + s.z
                    + " (" + s.shapes.size() + " shapes): " + s.summary + entrances(s)), false);
        }
        return systems.size();
    }

    /** Most cavities listed by one {@code caves cavities} call (one line each; RCON cuts long replies). */
    private static final int CAVITY_LINES = 60;

    /** AB06: every abyss cavity of every crust window within the radius, from the plans (the layout is built, no chunk is generated). */
    private static int cavities(CommandContext<CommandSourceStack> ctx, int radius)
    {
        CaveNetwork network = network(ctx);
        if (network == null) return 0;
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        List<String> lines = new ArrayList<>();
        for (CaveNetwork net : network.windows())
        {
            for (AbyssCavity.Site site : AbyssCavity.near(net, pos.getX(), pos.getZ(), radius))
            {
                CaveSystem system = net.system(site.cellX(), site.cellZ());
                ResourceKey<Biome> biome = net.biomeAt(Mth.floor(site.x()), Mth.floor(site.y() + site.height() * 0.5), Mth.floor(site.z()));
                String route = "no route";
                String[] parts = system.summary.split(" > ");
                for (int i = 0; i < parts.length; i++)
                {
                    if (parts[i].equals("descent route") || parts[i].startsWith("vertical link"))
                    {
                        route = parts[i] + (parts[i].equals("descent route") && i + 1 < parts.length ? " > " + parts[i + 1] : "");
                        break;
                    }
                }
                lines.add("[" + net.label() + "] " + Mth.floor(site.x()) + " " + Mth.floor(site.y()) + " " + Mth.floor(site.z()) + " r" + Mth.floor(site.radius())
                        + " h" + Mth.floor(site.height()) + " " + (biome != null ? biome.location().getPath() : "?") + "/" + site.environment().getPath() + ": " + route);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(lines.size() + " abyss cavit" + (lines.size() == 1 ? "y" : "ies") + " within " + radius + " blocks"
                + (lines.size() > CAVITY_LINES ? " (first " + CAVITY_LINES + " shown)" : "")), false);
        for (int i = 0; i < Math.min(lines.size(), CAVITY_LINES); i++)
        {
            String line = lines.get(i);
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }

    /** "[C] biome abyssia:abyss_toxic, environment abyssia:toxic: " for a system: its window (or shallow), the biome at its hub and its main chamber's environment. */
    private static String where(CaveNetwork network, CaveSystem s)
    {
        CaveSpace main = null;
        for (CaveSpace space : s.spaces)
        {
            if (space.role == CaveSpace.Role.CHAMBER && (main == null || space.radius > main.radius)) main = space;
        }
        ResourceKey<Biome> biome = network.biomeAt(s.x, s.y, s.z);
        return "[" + (s.band != null ? s.band.label() : "shallow") + "] biome " + (biome != null ? biome.location() : "?")
                + (main != null ? ", environment " + main.environmentId : "") + ": ";
    }

    private static String entrances(CaveSystem s)
    {
        StringBuilder out = new StringBuilder();
        for (CaveSpace space : s.spaces)
        {
            if (space.cavern == null || space.cavern.space != space) continue;
            Cavern c = space.cavern;
            if (c.hall != null)
            {
                out.append(" {").append(c.hallReport()).append(" @").append((int) c.hall.cx).append(' ').append(c.hall.level + 2).append(' ').append((int) c.hall.cz).append('}');
                continue;
            }
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
        // The nearest in the shallow network and in each window (windows search a shorter way: their cells are bigger and every system is large).
        Cavern bestCavern = null;
        CaveSystem bestSystem = null;
        double bestDistance = Double.MAX_VALUE;
        for (CaveNetwork net : network.all())
        {
            int cell = net.cellSize(), rings = net.isWindow() ? LOCATE_RADIUS_CAVERN / 2 : LOCATE_RADIUS_CAVERN;
            int cx = Math.floorDiv(pos.getX(), cell), cz = Math.floorDiv(pos.getZ(), cell);
            for (int ring = 0; ring <= rings; ring++)
            {
                Cavern best = null;
                CaveSystem found = null;
                double ringDistance = Double.MAX_VALUE;
                for (int dx = -ring; dx <= ring; dx++)
                {
                    for (int dz = -ring; dz <= ring; dz++)
                    {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || net.plan(cx + dx, cz + dz) == null) continue;
                        CaveSystem system = net.system(cx + dx, cz + dz);
                        if (system == null) continue;
                        for (CaveSpace space : system.spaces)
                        {
                            Cavern c = space.cavern;
                            if (c == null || c.space != space || c.center() != center) continue;
                            double d = Math.hypot(c.centerX() - pos.getX(), c.centerZ() - pos.getZ());
                            if (d < ringDistance)
                            {
                                ringDistance = d;
                                best = c;
                                found = system;
                            }
                        }
                    }
                }
                if (best != null)
                {
                    if (ringDistance < bestDistance)
                    {
                        bestDistance = ringDistance;
                        bestCavern = best;
                        bestSystem = found;
                    }
                    break;
                }
            }
        }
        if (bestCavern != null)
        {
            Cavern found = bestCavern;
            CaveSystem system = bestSystem;
            ctx.getSource().sendSuccess(() -> Component.literal(target + " at " + (int) found.centerX() + " " + (int) found.floor0 + " " + (int) found.centerZ()
                    + " (" + (int) Math.hypot(found.centerX() - pos.getX(), found.centerZ() - pos.getZ()) + " blocks): " + where(network, system) + system.summary + entrances(system)), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("No " + target + " within " + LOCATE_RADIUS_CAVERN * network.cellSize() + " blocks"));
        return 0;
    }

    private static int locate(CommandContext<CommandSourceStack> ctx, String target)
    {
        CaveNetwork network = network(ctx);
        if (network == null) return 0;
        if (target.startsWith(CAVERN_PREFIX)) return locateCavern(ctx, network, target);
        if (target.equals(HALL_PREFIX) || target.startsWith(HALL_PREFIX + "_")) return locateHall(ctx, network, target);
        CaveType type = Stream.of(CaveType.values()).filter(t -> t.getSerializedName().equals(target)).findFirst().orElse(null);
        CaveLandmark landmark = Stream.of(CaveLandmark.values()).filter(l -> l.getSerializedName().equals(target)).findFirst().orElse(null);
        if (type == null && landmark == null)
        {
            ctx.getSource().sendFailure(Component.literal("Unknown cave type or landmark: " + target));
            return 0;
        }
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        int cell = network.cellSize();
        // Mega caverns exist only in the crust windows: skip the long shallow search.
        int shallowRings = type != null && type.size == CaveType.Size.MEGA ? -1 : LOCATE_RADIUS_CELLS;
        if (type == CaveType.SEA_ARCH || type == CaveType.SMALL_SEA_CAVE || type == CaveType.ERODED_CAVE)
        {
            if (locateMinor(ctx, network, pos, type, target)) return 1;
        }
        int cx = Math.floorDiv(pos.getX(), cell), cz = Math.floorDiv(pos.getZ(), cell);
        // Rings outward: plans are light (no layout), so a wide search stays quick.
        for (int ring = 0; ring <= shallowRings; ring++)
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
                        + " (" + (int) Math.hypot(found.x() - pos.getX(), found.z() - pos.getZ()) + " blocks): " + where(network, system) + system.summary + entrances(system)), false);
                return 1;
            }
        }
        if (locateInWindows(ctx, network, pos, type, landmark, target)) return 1;
        ctx.getSource().sendFailure(Component.literal("No " + target + " within " + LOCATE_RADIUS_CELLS * cell + " blocks"
                + (network.windows().length > 0 ? " (shallow) or " + LOCATE_RADIUS_WINDOW + " cells (crust windows)" : "")));
        return 0;
    }

    /** The nearest plan of the type or landmark over all crust windows, searched ring by ring in each. */
    private static boolean locateInWindows(CommandContext<CommandSourceStack> ctx, CaveNetwork network, BlockPos pos, @Nullable CaveType type,
                                           @Nullable CaveLandmark landmark, String target)
    {
        CaveNetworkGenerator.Plan best = null;
        CaveNetwork bestNet = null;
        double bestDistance = Double.MAX_VALUE;
        for (CaveNetwork net : network.windows())
        {
            int cell = net.cellSize();
            int cx = Math.floorDiv(pos.getX(), cell), cz = Math.floorDiv(pos.getZ(), cell);
            for (int ring = 0; ring <= LOCATE_RADIUS_WINDOW; ring++)
            {
                CaveNetworkGenerator.Plan found = null;
                double foundDistance = Double.MAX_VALUE;
                for (int dx = -ring; dx <= ring; dx++)
                {
                    for (int dz = -ring; dz <= ring; dz++)
                    {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                        CaveNetworkGenerator.Plan p = net.plan(cx + dx, cz + dz);
                        if (p == null || (landmark != null ? p.landmark() != landmark : p.type() != type)) continue;
                        double d = Math.hypot(p.x() - pos.getX(), p.z() - pos.getZ());
                        if (d < foundDistance)
                        {
                            foundDistance = d;
                            found = p;
                        }
                    }
                }
                if (found != null)
                {
                    if (foundDistance < bestDistance)
                    {
                        bestDistance = foundDistance;
                        best = found;
                        bestNet = net;
                    }
                    break;
                }
            }
        }
        if (best == null) return false;
        CaveNetworkGenerator.Plan found = best;
        CaveSystem system = bestNet.system(found.cellX(), found.cellZ());
        ctx.getSource().sendSuccess(() -> Component.literal(target + " at " + (int) found.x() + " " + (int) found.y() + " " + (int) found.z()
                + " (" + (int) Math.hypot(found.x() - pos.getX(), found.z() - pos.getZ()) + " blocks): " + where(network, system) + system.summary + entrances(system)), false);
        return true;
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
            // The shallow network and every crust window, each against its own brute force.
            for (CaveNetwork net : network.all())
            {
            List<CaveSystem> filtered = net.systemsNear(x0 - 1, z0 - 1, x0 + 16, z0 + 16);
            for (CaveSystem s : net.systemsNearUnfiltered(x0 - 1, z0 - 1, x0 + 16, z0 + 16))
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
                    String info = "missed chunk " + x0 + "," + z0 + " (" + net.label() + "): " + s.summary;
                    ctx.getSource().sendSuccess(() -> Component.literal(info), false);
                }
            }
            }
        }
        int m = missed, n = systems;
        ctx.getSource().sendSuccess(() -> Component.literal("verify: " + samples + " chunks, " + n + " system hits, " + m + " missed by the pre-filter"), false);
        return m == 0 ? 1 : 0;
    }

    /** Blocks above which the block census is skipped (it reads, and so generates, every block of the box). */
    private static final long CENSUS_BLOCK_LIMIT = 6_000_000L;

    private static int census(CommandContext<CommandSourceStack> ctx)
    {
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        int y0 = Math.min(IntegerArgumentType.getInteger(ctx, "y0"), IntegerArgumentType.getInteger(ctx, "y1"));
        int y1 = Math.max(IntegerArgumentType.getInteger(ctx, "y0"), IntegerArgumentType.getInteger(ctx, "y1"));
        // Cave volume by environment per window, from the layouts (no chunk is generated): the share of each band's volume that is cave.
        ServerLevel level = ctx.getSource().getLevel();
        CaveNetwork net = level.getChunkSource().getGenerator() instanceof OceanChunkGenerator generator
                ? generator.caveNetwork(level.getChunkSource().randomState(), level.registryAccess(), level.getSeed()) : null;
        if (net != null && !net.isEmpty())
        {
            java.util.Map<String, java.util.Map<String, Long>> volume = CaveDebugRender.caveVolume(net, origin(ctx), radius, y0, y1);
            volume.forEach((label, envs) -> {
                long total = Math.max(1, envs.getOrDefault("total", 1L));
                long cave = envs.entrySet().stream().filter(e -> !e.getKey().equals("total")).mapToLong(java.util.Map.Entry::getValue).sum();
                StringBuilder line = new StringBuilder(String.format("%s: %d blocks sampled, cave %.1f%%:", label, total, cave * 100.0 / total));
                envs.entrySet().stream().filter(e -> !e.getKey().equals("total")).sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .forEach(e -> line.append(String.format(" %s %.1f%%", e.getKey(), e.getValue() * 100.0 / Math.max(1, cave))));
                ctx.getSource().sendSuccess(() -> Component.literal(line.toString()), false);
            });
        }
        long box = (2L * radius + 1) * (2L * radius + 1) * (y1 - y0 + 1);
        if (box > CENSUS_BLOCK_LIMIT)
        {
            ctx.getSource().sendSuccess(() -> Component.literal("block counts skipped (" + box + " blocks; use a smaller box, up to " + CENSUS_BLOCK_LIMIT + ")"), false);
            return 1;
        }
        java.util.Map<String, Integer> counts = CaveDebugRender.census(ctx.getSource().getLevel(), origin(ctx), radius, y0, y1);
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
        CaveSystem nearestSystem = null;
        for (CaveSystem s : network.systemsNearAll(pos.getX(), pos.getZ(), pos.getX(), pos.getZ()))
        {
            for (CaveShape shape : s.shapes)
            {
                if (shape.kind != CaveShape.Kind.CARVE || !shape.intersects(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ())) continue;
                double d = shape.distance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (d < best)
                {
                    best = d;
                    nearest = shape;
                    nearestSystem = s;
                }
            }
        }
        ResourceKey<Biome> biomeHere = network.biomeAt(pos.getX(), pos.getY(), pos.getZ());
        DepthBand windowHere = DepthBand.forY(pos.getY());
        String place = "window " + (windowHere != null ? windowHere.label() : "none (shallow or above)") + ", biome " + (biomeHere != null ? biomeHere.location() : "?");
        if (nearest == null || best > 4)
        {
            ctx.getSource().sendSuccess(() -> Component.literal("Not in a cave of the network (" + place
                    + (windowHere == null ? ", seabed at y " + network.seabed(pos.getX(), pos.getZ()) : "") + ")"), false);
            return 0;
        }
        CaveSpace space = nearest.space;
        double distance = best;
        String system = nearestSystem.band != null ? "system of " + nearestSystem.band.label() : "shallow system";
        ctx.getSource().sendSuccess(() -> Component.literal(place + "; " + system + ": " + space.role.name().toLowerCase() + " of a " + space.type.getSerializedName()
                + (space.landmark != null ? " [" + space.landmark.getSerializedName() + "]" : "") + ", environment " + space.environmentId
                + ", radius " + (int) space.radius + (space.hasLake() ? ", lake surface y " + space.waterLevel : "")
                + String.format(" (%.1f blocks from its surface)", -distance)), false);
        if (space.cavern != null)
        {
            Cavern cavern = space.cavern;
            ctx.getSource().sendSuccess(() -> Component.literal(cavern.describe(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)), false);
            if (cavern.hall != null) ctx.getSource().sendSuccess(() -> Component.literal(cavern.hallReport()), false);
        }
        return 1;
    }

    /** {@code /abyssia caves hall [x z]}: the hall over this column (any network), with its dimensions and formations. */
    private static int hall(CommandContext<CommandSourceStack> ctx, int x, int z)
    {
        CaveNetwork network = network(ctx);
        if (network == null) return 0;
        int found = 0;
        for (CaveSystem s : network.systemsNearAll(x, z, x, z))
        {
            for (CaveSpace space : s.spaces)
            {
                Cavern c = space.cavern;
                if (c == null || c.space != space || c.hall == null || c.hall.q(x + 0.5, z + 0.5) > 1.15) continue;
                found++;
                String text = "[" + (s.band != null ? s.band.label() : "shallow") + "] " + s.type.getSerializedName() + " @ " + (int) c.hall.cx + " " + (c.hall.level + 2)
                        + " " + (int) c.hall.cz + ": " + c.hallReport();
                ctx.getSource().sendSuccess(() -> Component.literal(text), false);
            }
        }
        if (found == 0) ctx.getSource().sendFailure(Component.literal("No hall over " + x + " " + z + " (try /abyssia caves locate hall)"));
        return found;
    }

    /** Whether a plan's hub is a hall matching {@code hall[_<environment>][_<size>]}. */
    private static boolean hallMatches(CaveNetworkGenerator.Plan p, String target)
    {
        if (!p.hall()) return false;
        String rest = target.length() > HALL_PREFIX.length() ? target.substring(HALL_PREFIX.length() + 1) : "";
        for (String size : HALL_SIZES)
        {
            if (rest.equals(size) || rest.endsWith("_" + size))
            {
                CaveType type = size.equals("large") ? CaveType.LARGE_ABYSSAL_CAVE : size.equals("massive") ? CaveType.MASSIVE_CAVERN : CaveType.MEGA_CAVERN;
                if (size.equals("mega") ? p.type().size != CaveType.Size.MEGA : p.type() != type) return false;
                rest = rest.equals(size) ? "" : rest.substring(0, rest.length() - size.length() - 1);
                break;
            }
        }
        if (rest.isEmpty()) return true;
        String env = p.environment().getPath();
        return switch (rest)
        {
            case "plain" -> env.equals("abyssal");
            case "volcanic" -> env.equals("thermal");
            case "hot" -> env.equals("thermal") || env.equals("magma");
            default -> env.equals(rest);
        };
    }

    /** The nearest hall of that environment and size over the shallow network and the windows (from plans), with its report. */
    private static int locateHall(CommandContext<CommandSourceStack> ctx, CaveNetwork network, String target)
    {
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        CaveNetworkGenerator.Plan best = null;
        CaveNetwork bestNet = null;
        double bestDistance = Double.MAX_VALUE;
        boolean megaOnly = target.endsWith("_mega");
        for (CaveNetwork net : network.all())
        {
            if (megaOnly && !net.isWindow()) continue;
            int cell = net.cellSize(), rings = net.isWindow() ? LOCATE_RADIUS_WINDOW : LOCATE_RADIUS_CELLS;
            int cx = Math.floorDiv(pos.getX(), cell), cz = Math.floorDiv(pos.getZ(), cell);
            for (int ring = 0; ring <= rings; ring++)
            {
                CaveNetworkGenerator.Plan found = null;
                double foundDistance = Double.MAX_VALUE;
                for (int dx = -ring; dx <= ring; dx++)
                {
                    for (int dz = -ring; dz <= ring; dz++)
                    {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                        CaveNetworkGenerator.Plan p = net.plan(cx + dx, cz + dz);
                        if (p == null || !hallMatches(p, target)) continue;
                        double d = Math.hypot(p.x() - pos.getX(), p.z() - pos.getZ());
                        if (d < foundDistance)
                        {
                            foundDistance = d;
                            found = p;
                        }
                    }
                }
                if (found != null)
                {
                    if (foundDistance < bestDistance)
                    {
                        bestDistance = foundDistance;
                        best = found;
                        bestNet = net;
                    }
                    break;
                }
            }
        }
        if (best == null)
        {
            ctx.getSource().sendFailure(Component.literal("No " + target + " found (shallow network " + LOCATE_RADIUS_CELLS + " cells, windows " + LOCATE_RADIUS_WINDOW + " cells)"));
            return 0;
        }
        CaveNetworkGenerator.Plan found = best;
        CaveSystem system = bestNet.system(found.cellX(), found.cellZ());
        String report = "";
        for (CaveSpace space : system.spaces)
        {
            if (space.cavern != null && space.cavern.space == space && space.cavern.hall != null)
            {
                report = space.cavern.hallReport();
                break;
            }
        }
        String text = target + " at " + (int) found.x() + " " + ((int) found.y() + 2) + " " + (int) found.z() + " (" + (int) Math.hypot(found.x() - pos.getX(), found.z() - pos.getZ())
                + " blocks, [" + bestNet.label() + "] " + found.type().getSerializedName() + ", environment " + found.environment().getPath() + "): " + report;
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
