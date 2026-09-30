package com.abyssia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import com.abyssia.network.AbyssiaNetwork;
import com.abyssia.network.DepthSettingsPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moves players between the ocean world (overworld) and the deep ocean dimension based on depth,
 * keeping X/Z, rotation and velocity so the switch feels like continuing the dive.
 * <p>
 * The default transition depth lies inside the ocean world's bedrock floor, so the way down is an abyssal rift
 * (abyssia:abyssal_rift, a shaft through the bedrock); the way back up works anywhere.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeepOceanTransition
{
    public static final ResourceKey<Level> OCEAN_WORLD = Level.OVERWORLD;
    public static final ResourceKey<Level> DEEP_OCEAN = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "deep_ocean"));
    /** Ocean world biome of the shafts through the bedrock floor: the only place the default transition depth is reachable. */
    public static final ResourceKey<Biome> ABYSSAL_RIFT = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "abyssal_rift"));

    // Server tick until which a player may not transition again. Transient on purpose: losing it on restart is harmless.
    private static final Map<UUID, Integer> COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    private static final Set<UUID> DIED_IN_DEEP_OCEAN = ConcurrentHashMap.newKeySet();

    // Keeps destination chunks generated/loaded while a player approaches a boundary; expires on its own once they swim away.
    private static final TicketType<ChunkPos> PRELOAD = TicketType.create("abyssia_deep_ocean_preload", Comparator.comparingLong(ChunkPos::toLong), 100);
    private static final int PRELOAD_RADIUS = 2;
    private static final int PRELOAD_INTERVAL = 20;

    private DeepOceanTransition() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!Config.DEEP_OCEAN_ENABLED.get() || player.isSpectator() || !player.isAlive() || player.isPassenger()) return;

        ResourceKey<Level> dim = player.level().dimension();
        if (player.tickCount % PRELOAD_INTERVAL == 0) preload(player, dim);
        if (player.server.getTickCount() < COOLDOWN_UNTIL.getOrDefault(player.getUUID(), 0)) return;

        int offset = Config.DEEP_OCEAN_COORDINATE_OFFSET_Y.get();
        if (dim == OCEAN_WORLD && player.getY() <= Config.DEEP_OCEAN_TRANSITION_Y.get())
        {
            transition(player, DEEP_OCEAN, player.getY() + offset, true);
        }
        else if (dim == DEEP_OCEAN && player.getY() >= Config.DEEP_OCEAN_RETURN_Y.get())
        {
            transition(player, OCEAN_WORLD, player.getY() - offset, false);
        }
    }

    private static void preload(ServerPlayer player, ResourceKey<Level> dim)
    {
        int distance = Config.DEEP_OCEAN_PRELOAD_DISTANCE.get();
        ResourceKey<Level> targetKey;
        if (dim == OCEAN_WORLD && player.getY() - Config.DEEP_OCEAN_TRANSITION_Y.get() <= distance) targetKey = DEEP_OCEAN;
        else if (dim == DEEP_OCEAN && Config.DEEP_OCEAN_RETURN_Y.get() - player.getY() <= distance) targetKey = OCEAN_WORLD;
        else return;

        ServerLevel target = player.server.getLevel(targetKey);
        if (target == null) return;
        ChunkPos pos = player.chunkPosition();
        target.getChunkSource().addRegionTicket(PRELOAD, pos, PRELOAD_RADIUS, pos);
    }

    private static void transition(ServerPlayer player, ResourceKey<Level> targetKey, double targetY, boolean searchDown)
    {
        ServerLevel target = player.server.getLevel(targetKey);
        if (target == null) return;

        double x = player.getX();
        double z = player.getZ();
        Vec3 motion = player.getDeltaMovement();
        // Coming up, the target Y usually lies inside the ocean world's rock (it is below most of the seabed): surface
        // on top of the seabed instead of in the nearest, possibly sealed, cave.
        if (!searchDown) targetY = Math.max(targetY, target.getHeight(Heightmap.Types.OCEAN_FLOOR, Mth.floor(x), Mth.floor(z)));
        double y = findSafeY(target, x, targetY, z, searchDown);

        COOLDOWN_UNTIL.put(player.getUUID(), player.server.getTickCount() + Config.DEEP_OCEAN_TRANSITION_COOLDOWN.get());
        player.teleportTo(target, x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(motion);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /**
     * Returns the nearest Y to {@code preferredY} with room for a player (non-colliding blocks at feet and head),
     * searching first in the direction of travel so a diver is never placed inside terrain.
     */
    private static double findSafeY(ServerLevel level, double x, double preferredY, double z, boolean searchDown)
    {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        int start = Mth.clamp(Mth.floor(preferredY), minY, maxY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        level.getChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);

        for (int d = 0; d <= maxY - minY; d++)
        {
            int first = searchDown ? start - d : start + d;
            int second = searchDown ? start + d : start - d;
            if (first >= minY && first <= maxY && hasRoom(level, pos, x, first, z)) return first == start ? Mth.clamp(preferredY, minY, maxY) : first;
            if (d > 0 && second >= minY && second <= maxY && hasRoom(level, pos, x, second, z)) return second;
        }
        return start;
    }

    private static boolean hasRoom(ServerLevel level, BlockPos.MutableBlockPos pos, double x, int y, double z)
    {
        pos.set(x, y, z);
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        pos.move(0, 1, 0);
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension() == DEEP_OCEAN)
        {
            DIED_IN_DEEP_OCEAN.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) return;
        if (!DIED_IN_DEEP_OCEAN.remove(player.getUUID())) return;
        // Players with their own respawn point (bed etc.) keep vanilla behaviour.
        if (Config.DEEP_OCEAN_RESPAWN_IN_OCEAN_WORLD.get() || player.getRespawnPosition() != null) return;

        ServerLevel deep = player.server.getLevel(DEEP_OCEAN);
        if (deep == null) return;
        BlockPos spawn = player.server.overworld().getSharedSpawnPos();
        double x = spawn.getX() + 0.5;
        double z = spawn.getZ() + 0.5;
        double y = findSafeY(deep, x, Config.DEEP_OCEAN_RETURN_Y.get() - 20, z, true);
        COOLDOWN_UNTIL.put(player.getUUID(), player.server.getTickCount() + Config.DEEP_OCEAN_TRANSITION_COOLDOWN.get());
        player.teleportTo(deep, x, y, z, player.getYRot(), player.getXRot());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            AbyssiaNetwork.sendTo(player, new DepthSettingsPacket(Config.DEEP_OCEAN_ENABLED.get(),
                    Config.DEEP_OCEAN_TRANSITION_Y.get(), Config.DEEP_OCEAN_RETURN_Y.get()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        COOLDOWN_UNTIL.remove(event.getEntity().getUUID());
    }
}
