package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.Config;
import com.abyssia.fauna.DepthZone;
import com.abyssia.client.particle.AbyssParticle;
import com.abyssia.environment.CaveAmbience;
import com.abyssia.environment.ParticleBudget;
import com.abyssia.client.thermal.ClientVentTracker;
import com.abyssia.environment.OceanCurrentManager;
import com.abyssia.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Spawns marine snow and other environment particles around the local player only. Depth sets the base
 * density and particle mix, the biome adjusts it, and a budget caps how many are alive at once.
 */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class MarineSnowClientManager
{
    /** Depth below the sea surface at which each band's density applies; interpolated with smoothstep between them. */
    private static final float[] BAND_DEPTHS = {0, 40, 100, 200, 300, 380};
    private static final int SPAWN_INTERVAL = 2;
    /** Ticks to reach the target particle count; spreads spawning out instead of bursts. */
    private static final float FILL_TICKS = 40f;
    private static final int MAX_SPAWNS_PER_TICK = 8;
    private static final double MIN_CAMERA_DISTANCE = 2.0;
    private static final int SEDIMENT_INTERVAL = 3;
    private static final int FLOOR_SCAN_DEPTH = 24;
    private static final int SEDIMENT_RADIUS = 12;

    private static final Map<ResourceKey<Biome>, Supplier<Double>> BIOME_DENSITY = Map.of(
            biome("twilight_reef"), Config.TWILIGHT_DENSITY::get,
            biome("deep_sea"), Config.DEEP_DENSITY::get,
            biome("abyssal_ocean"), Config.ABYSSAL_DENSITY::get,
            biome("abyssal_forest"), Config.ABYSSAL_DENSITY::get,
            biome("deep_crystal_fields"), Config.ABYSSAL_DENSITY::get,
            biome("thermal_vents"), Config.ABYSSAL_DENSITY::get,
            biome("volcanic_deep"), Config.ABYSSAL_DENSITY::get,
            biome("abyssal_trench"), Config.TRENCH_DENSITY::get,
            biome("hadal_zone"), Config.HADAL_DENSITY::get);
    /** How strongly each biome stirs sediment off the seabed. */
    private static final Map<ResourceKey<Biome>, Float> SEDIMENT_ACTIVITY = Map.of(
            biome("abyssal_trench"), 1.0f,
            biome("thermal_vents"), 0.9f,
            biome("hadal_zone"), 0.6f,
            biome("volcanic_deep"), 0.5f,
            biome("abyssal_forest"), 0.4f);
    private static final ResourceKey<Biome> HADAL = biome("hadal_zone");
    private static final ResourceKey<Biome> VOLCANIC = biome("volcanic_deep");
    private static final ResourceKey<Biome> CRYSTAL = biome("deep_crystal_fields");

    private static float smoothedDensity;
    private static int tick;

    private MarineSnowClientManager() {}

    private static ResourceKey<Biome> biome(String name)
    {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name));
    }

    /** Current marine snow density around the player (0..~1), eased over time; used to thicken fog. */
    public static float currentDensity()
    {
        return smoothedDensity;
    }

    /** Blocks below the ocean surface, from the ocean world down through the deep layer (see {@link DepthZone}). */
    public static double depthBelowSurface(Level level, double y)
    {
        return DepthZone.blocksBelowSurface(level, y);
    }

    private static float bandDensity(double depth)
    {
        float[] values = {
                Config.SURFACE_DENSITY.get().floatValue(), Config.TWILIGHT_DENSITY.get().floatValue(), Config.DEEP_DENSITY.get().floatValue(),
                Config.ABYSSAL_DENSITY.get().floatValue(), Config.TRENCH_DENSITY.get().floatValue(), Config.HADAL_DENSITY.get().floatValue()};
        return interpolateBands(depth, values);
    }

    private static float particleLimit(double depth)
    {
        float[] values = {
                Config.SURFACE_PARTICLE_LIMIT.get(), Config.SURFACE_PARTICLE_LIMIT.get(), Config.DEEP_PARTICLE_LIMIT.get(),
                Config.ABYSSAL_PARTICLE_LIMIT.get(), Config.ABYSSAL_PARTICLE_LIMIT.get(), Config.HADAL_PARTICLE_LIMIT.get()};
        return interpolateBands(depth, values);
    }

    private static float interpolateBands(double depth, float[] values)
    {
        if (depth <= BAND_DEPTHS[0]) return values[0];
        for (int i = 1; i < BAND_DEPTHS.length; i++)
        {
            if (depth <= BAND_DEPTHS[i])
            {
                float t = (float) ((depth - BAND_DEPTHS[i - 1]) / (BAND_DEPTHS[i] - BAND_DEPTHS[i - 1]));
                return Mth.lerp(t * t * (3 - 2 * t), values[i - 1], values[i]);
            }
        }
        return values[values.length - 1];
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel().isClientSide())
        {
            AbyssParticle.resetAmbientCount();
            smoothedDensity = 0;
        }
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event)
    {
        // Before the particle engine ticks: recount the particle budgets from the particles that really ticked.
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) ParticleBudget.reconcile();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) return;
        tick++;

        boolean submerged = player.isInWater() && player.isEyeInFluid(FluidTags.WATER);
        if (!Config.MARINE_SNOW_ENABLED.get() || !submerged)
        {
            smoothedDensity = Math.max(0f, smoothedDensity - 0.02f);
            return;
        }

        double depth = depthBelowSurface(level, player.getEyeY());
        BlockPos pos = player.blockPosition();
        Optional<ResourceKey<Biome>> biome = level.getBiome(pos).unwrapKey();
        float target = bandDensity(depth);
        Supplier<Double> biomeDensity = biome.map(BIOME_DENSITY::get).orElse(null);
        if (biomeDensity != null) target = (target + biomeDensity.get().floatValue()) * 0.5f;
        // Open seabed low, deep water medium, caves high, huge caverns very high.
        float caveFactor = CaveAmbience.snowFactor(Config.CAVE_SNOW_MULTIPLIER.get().floatValue());
        target *= caveFactor;
        smoothedDensity += (target - smoothedDensity) * 0.05f;

        float settingScale = switch (mc.options.particles().get())
        {
            case ALL -> 1f;
            case DECREASED -> 0.6f;
            case MINIMAL -> 0.25f;
        };
        RandomSource random = player.getRandom();
        ResourceKey<Biome> biomeKey = biome.orElse(null);

        if (tick % SPAWN_INTERVAL == 0)
        {
            // Caves may exceed the depth band's cap a little: the snow fills a much smaller visible volume.
            float limit = particleLimit(depth) * settingScale * Math.min(1.8f, caveFactor);
            float wanted = Math.min(limit, Config.HADAL_PARTICLE_LIMIT.get() * smoothedDensity * settingScale);
            int spawns = Mth.clamp(Mth.ceil((wanted - AbyssParticle.ambientAlive()) * SPAWN_INTERVAL / FILL_TICKS), 0, MAX_SPAWNS_PER_TICK * SPAWN_INTERVAL);
            for (int i = 0; i < spawns; i++) spawnSnow(mc, level, player, depth, biomeKey, random);

            if (biomeKey == VOLCANIC && Config.VOLCANIC_ASH_ENABLED.get() && AbyssParticle.ambientAlive() < limit + 60)
            {
                for (int i = 0; i < 2; i++) spawnAround(mc, level, player, ModParticles.VOLCANIC_ASH.get(), random, 0.025f + random.nextFloat() * 0.03f, 0.004f, 0.5f, false);
            }
        }

        if (tick % SEDIMENT_INTERVAL == 0 && Config.SEDIMENT_ENABLED.get() && Config.RISING_SEDIMENT_ENABLED.get())
        {
            tickSediment(mc, level, player, biomeKey, random);
        }
    }

    private static void spawnSnow(Minecraft mc, ClientLevel level, LocalPlayer player, double depth, ResourceKey<Biome> biome, RandomSource random)
    {
        float minFall = Config.MIN_FALL_SPEED.get().floatValue();
        float maxFall = Math.max(minFall, Config.MAX_FALL_SPEED.get().floatValue());
        float glowChance = Config.BIOLUMINESCENT_CHANCE.get().floatValue();
        if (biome == CRYSTAL) glowChance = Math.max(glowChance * 3f, 0.02f);
        else if (biome != HADAL && depth < 300) glowChance = 0f;

        SimpleParticleType type;
        float size;
        float alpha;
        if (random.nextFloat() < glowChance)
        {
            type = ModParticles.BIOLUMINESCENT_SNOW.get();
            size = 0.04f + random.nextFloat() * 0.03f;
            alpha = 0.6f;
        }
        else if (depth < 60)
        {
            type = ModParticles.MARINE_SNOW.get();
            size = 0.02f + random.nextFloat() * 0.02f;
            alpha = 0.2f;
        }
        else if (depth < 200)
        {
            type = random.nextFloat() < 0.7f ? ModParticles.DEEP_MARINE_SNOW.get() : ModParticles.MARINE_SNOW.get();
            size = 0.025f + random.nextFloat() * 0.045f;
            alpha = 0.3f;
        }
        else
        {
            type = random.nextFloat() < 0.7f ? ModParticles.ABYSSAL_MARINE_SNOW.get() : ModParticles.DEEP_MARINE_SNOW.get();
            // Mostly fine flakes, occasionally a large clump.
            size = random.nextFloat() < 0.05f ? 0.12f + random.nextFloat() * 0.05f : 0.02f + random.nextFloat() * 0.07f;
            alpha = 0.4f;
        }
        // Bigger flakes sink a little faster; shallow water uses the slower half of the range.
        float sizeFactor = Mth.clamp((size - 0.02f) / 0.15f, 0f, 1f);
        float fall = Mth.lerp(Mth.clamp(random.nextFloat() * 0.7f + sizeFactor * 0.3f, 0f, 1f), minFall, depth < 60 ? (minFall + maxFall) * 0.5f : maxFall);
        spawnAround(mc, level, player, type, random, size, fall, alpha, true);
    }

    /**
     * Spawns an ambient particle at a random water position around the player, biased upward so falling snow passes
     * through view. With {@code depthCue}, inside a large cavern flakes near the camera come out larger and brighter
     * and distant ones smaller and fainter, which makes the size of the hall legible; the count stays the same.
     */
    private static void spawnAround(Minecraft mc, ClientLevel level, LocalPlayer player, SimpleParticleType type, RandomSource random, float size, float speed,
                                    float alpha, boolean depthCue)
    {
        int radius = Config.PARTICLE_RENDER_DISTANCE.get();
        // Inside a cave, spawn within the cavity itself; most of a full-size cube would be solid rock.
        if (CaveAmbience.inCave()) radius = Mth.clamp(Mth.ceil(CaveAmbience.cavitySize() * 1.3f), 8, radius);
        double x = player.getX() + (random.nextDouble() * 2 - 1) * radius;
        double y = player.getEyeY() + (random.nextDouble() * 1.4 - 0.6) * radius * 0.5;
        double z = player.getZ() + (random.nextDouble() * 2 - 1) * radius;
        if (player.distanceToSqr(x, y, z) < MIN_CAMERA_DISTANCE * MIN_CAMERA_DISTANCE) return;
        if (!level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) return;
        float cavern = depthCue && ClientConfig.CAVERN_SNOW_DEPTH.get() ? CaveAmbience.cavernFactor() : 0f;
        if (cavern > 0.01f)
        {
            double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
            float t = Mth.clamp((float) Math.sqrt(dx * dx + dy * dy + dz * dz) / radius, 0f, 1f);
            size *= Mth.lerp(cavern, 1f, Mth.lerp(t, 1.5f, 0.7f));
            alpha *= Mth.lerp(cavern, 1f, Mth.lerp(t, 1.15f, 0.6f));
        }
        add(mc, type, x, y, z, size, speed, alpha);
    }

    private static void add(Minecraft mc, SimpleParticleType type, double x, double y, double z, float size, float speed, float alpha)
    {
        Particle particle = mc.particleEngine.createParticle(type, x, y, z, 0, 0, 0);
        if (particle instanceof AbyssParticle abyss) abyss.configure(size, speed, alpha);
    }

    /** Sediment lifting off nearby seabed: more in trenches, near vents, in strong currents and upwelling. */
    private static void tickSediment(Minecraft mc, ClientLevel level, LocalPlayer player, ResourceKey<Biome> biome, RandomSource random)
    {
        BlockPos pos = player.blockPosition();
        OceanCurrentManager.Zone zone = OceanCurrentManager.getZone(level, pos);
        // Floating matter streams through narrow cave passages.
        float activity = 0.25f + (biome == null ? 0f : SEDIMENT_ACTIVITY.getOrDefault(biome, 0f))
                + CaveAmbience.narrowness() * 1.2f
                + (float) OceanCurrentManager.getCurrentStrength(level, pos) * 1.5f
                + (zone == OceanCurrentManager.Zone.UPWELLING ? 0.8f : zone == OceanCurrentManager.Zone.STRONG ? 0.3f : 0f)
                + ClientVentTracker.temperatureAt(player.getX(), player.getY(), player.getZ()) * 1.5f;
        int attempts = Mth.floor(activity * 2) + (random.nextFloat() < activity * 2 % 1 ? 1 : 0);
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int i = 0; i < attempts; i++)
        {
            int x = pos.getX() + random.nextInt(SEDIMENT_RADIUS * 2 + 1) - SEDIMENT_RADIUS;
            int z = pos.getZ() + random.nextInt(SEDIMENT_RADIUS * 2 + 1) - SEDIMENT_RADIUS;
            for (int dy = 0; dy < FLOOR_SCAN_DEPTH; dy++)
            {
                probe.set(x, pos.getY() - dy, z);
                if (level.getFluidState(probe).is(FluidTags.WATER)) continue;
                if (dy == 0) break; // player column is inside terrain here; skip
                add(mc, ModParticles.SEDIMENT.get(), x + random.nextDouble(), probe.getY() + 1.05 + random.nextDouble() * 0.6, z + random.nextDouble(),
                        0.03f + random.nextFloat() * 0.03f, 0.003f + random.nextFloat() * 0.007f, 0.35f);
                break;
            }
        }
    }
}
