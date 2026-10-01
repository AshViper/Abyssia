package com.abyssia.client.thermal;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.client.particle.AbyssParticle;
import com.abyssia.environment.ParticleBudget;
import com.abyssia.registry.ModParticles;
import com.abyssia.thermal.ThermalTemperature;
import com.abyssia.thermal.ThermalUpdraft;
import com.abyssia.thermal.ThermalVentType;
import com.abyssia.thermal.VentActivity;
import com.abyssia.thermal.VentSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds vent cores near the player (by scanning loaded chunk sections, skipping any section whose palette
 * cannot contain a vent) and drives everything client-side about them: plumes, bubbles, stirred-up sediment,
 * the updraft felt by all environment particles, and the water temperature used for fog.
 * The server only stores the vent blocks themselves.
 */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientVentTracker
{
    public record TrackedVent(BlockPos pos, ThermalVentType type, VentActivity activity) implements VentSource {}

    private static final int SCAN_RADIUS_CHUNKS = 4;
    private static final int SCAN_INTERVAL = 40;
    /** Full effect within this distance, reduced effect out to MAX_DISTANCE, nothing beyond. */
    private static final double FULL_DISTANCE = 32;
    private static final double MAX_DISTANCE = 64;
    private static final float REDUCED_FACTOR = 0.35f;
    /** Cap on vent particles alive at once across all vents. */
    private static final int VENT_PARTICLE_LIMIT = 500;
    /** Vents further than this from a point are ignored for its updraft/temperature. */
    private static final double INFLUENCE_RANGE = 24;

    private static List<TrackedVent> vents = List.of();
    private static int tick;
    private static int soundCooldown;

    private ClientVentTracker() {}

    public static List<TrackedVent> vents()
    {
        return vents;
    }

    private static List<TrackedVent> near(double x, double z)
    {
        List<TrackedVent> all = vents;
        if (all.isEmpty()) return all;
        List<TrackedVent> result = new ArrayList<>(4);
        for (TrackedVent v : all)
        {
            double dx = v.pos().getX() + 0.5 - x, dz = v.pos().getZ() + 0.5 - z;
            if (dx * dx + dz * dz < INFLUENCE_RANGE * INFLUENCE_RANGE) result.add(v);
        }
        return result;
    }

    /** Vent updraft at a point (blocks per tick), zero when no vent is near. */
    public static Vec3 updraftAt(double x, double y, double z)
    {
        List<TrackedVent> nearby = near(x, z);
        if (nearby.isEmpty()) return Vec3.ZERO;
        return ThermalUpdraft.at(x, y, z, nearby, Config.THERMAL_UPDRAFT_STRENGTH.get());
    }

    /** Water temperature from vents at a point (0..1). */
    public static float temperatureAt(double x, double y, double z)
    {
        List<TrackedVent> nearby = near(x, z);
        return nearby.isEmpty() ? 0f : ThermalTemperature.at(x, y, z, nearby);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel().isClientSide()) vents = List.of();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) return;
        if (tick++ % SCAN_INTERVAL == 0) vents = scan(level, player.blockPosition());
        if (vents.isEmpty() || !Config.VENT_PARTICLES_ENABLED.get()) return;

        float settingScale = switch (mc.options.particles().get())
        {
            case ALL -> 1f;
            case DECREASED -> 0.5f;
            case MINIMAL -> 0.2f;
        };
        RandomSource random = player.getRandom();
        for (TrackedVent vent : vents)
        {
            double dist = Math.sqrt(vent.pos().distToCenterSqr(player.position()));
            if (dist > MAX_DISTANCE) continue;
            float factor = (dist <= FULL_DISTANCE ? 1f : REDUCED_FACTOR) * settingScale;
            emit(mc, level, vent, factor, random);
        }

        // Low bubbling from the nearest active vent; a hook for future dedicated vent sounds.
        if (--soundCooldown <= 0)
        {
            soundCooldown = 60 + random.nextInt(80);
            for (TrackedVent vent : vents)
            {
                if (vent.activity() != VentActivity.DORMANT && vent.pos().distToCenterSqr(player.position()) < 16 * 16)
                {
                    level.playLocalSound(vent.pos(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.5f, 0.5f + random.nextFloat() * 0.2f, false);
                    break;
                }
            }
        }
    }

    private static List<TrackedVent> scan(ClientLevel level, BlockPos center)
    {
        List<TrackedVent> found = new ArrayList<>();
        int ccx = SectionPos.blockToSectionCoord(center.getX()), ccz = SectionPos.blockToSectionCoord(center.getZ());
        for (int cx = ccx - SCAN_RADIUS_CHUNKS; cx <= ccx + SCAN_RADIUS_CHUNKS; cx++)
        {
            for (int cz = ccz - SCAN_RADIUS_CHUNKS; cz <= ccz + SCAN_RADIUS_CHUNKS; cz++)
            {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++)
                {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof ThermalVentBlock)) continue;
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++)
                    {
                        for (int z = 0; z < 16; z++)
                        {
                            for (int x = 0; x < 16; x++)
                            {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!(state.getBlock() instanceof ThermalVentBlock)) continue;
                                found.add(new TrackedVent(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z),
                                        state.getValue(ThermalVentBlock.TYPE), state.getValue(ThermalVentBlock.ACTIVITY)));
                            }
                        }
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    private static void emit(Minecraft mc, ClientLevel level, TrackedVent vent, float factor, RandomSource random)
    {
        BlockPos above = vent.pos().above();
        if (!level.getFluidState(above).is(FluidTags.WATER)) return;
        double x = vent.pos().getX() + 0.5, y = vent.pos().getY() + 1.05, z = vent.pos().getZ() + 0.5;
        ThermalVentType type = vent.type();
        float activity = vent.activity().particles;

        float rate = type.particleRate * activity * Config.THERMAL_PARTICLE_DENSITY.get().floatValue() * factor;
        for (int i = count(rate, random); i > 0 && ParticleBudget.alive(ParticleBudget.Budget.VENT) < VENT_PARTICLE_LIMIT; i--)
        {
            float roll = random.nextFloat();
            switch (type)
            {
                case BLACK_SMOKER -> {
                    if (roll < 0.7f) plume(mc, ModParticles.BLACK_SMOKE.get(), x, y, z, 0.3, random, 0.25f + random.nextFloat() * 0.2f, 0.035f, 0.55f, 1f);
                    else steam(mc, x, y, z, random);
                }
                case WHITE_SMOKER -> {
                    // White smoke spreads wider and further than black.
                    if (roll < 0.7f) plume(mc, ModParticles.WHITE_SMOKE.get(), x, y, z, 0.6, random, 0.3f + random.nextFloat() * 0.25f, 0.025f, 0.35f, 1.8f);
                    else steam(mc, x, y, z, random);
                }
                case MINERAL -> {
                    if (roll < 0.6f) steam(mc, x, y, z, random);
                    else plume(mc, ModParticles.MINERAL_PARTICLE.get(), x, y - 0.5, z, 3.0, random, 0.03f + random.nextFloat() * 0.02f, 0.004f, 0.7f, 1f);
                }
                case SUPERHEATED -> {
                    if (roll < 0.55f) steam(mc, x, y, z, random);
                    else plume(mc, ModParticles.BLACK_SMOKE.get(), x, y, z, 0.4, random, 0.3f + random.nextFloat() * 0.25f, 0.05f, 0.5f, 1.2f);
                }
            }
        }

        float bubbles = Config.BUBBLE_DENSITY.get().floatValue() * activity * factor * (type == ThermalVentType.SUPERHEATED ? 1.6f : 0.6f);
        for (int i = count(bubbles, random); i > 0; i--)
        {
            level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5, 0, 0.04, 0);
        }

        // microbial floc: flakes torn off the mats of chemosynthetic bacteria that coat the warm rock around the vent,
        // the base of the vent food web (grazed by shrimp, farmed by squat lobsters, housed by tubeworms and snails)
        float floc = 0.15f * activity * factor;
        for (int i = count(floc, random); i > 0 && ParticleBudget.alive(ParticleBudget.Budget.VENT) < VENT_PARTICLE_LIMIT; i--)
        {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 1.0 + random.nextDouble() * 2.5;
            plume(mc, ModParticles.MARINE_SNOW.get(), x + Math.cos(angle) * dist, y - 0.6, z + Math.sin(angle) * dist, 0.2, random,
                    0.035f + random.nextFloat() * 0.03f, -0.005f, 0.5f, 1f);
        }

        float sediment = Config.VENT_SEDIMENT_DENSITY.get().floatValue() * 0.4f * activity * factor;
        if (Config.SEDIMENT_ENABLED.get()) for (int i = count(sediment, random); i > 0; i--) stirSediment(mc, level, vent, random);
    }

    private static int count(float rate, RandomSource random)
    {
        int whole = (int) rate;
        return whole + (random.nextFloat() < rate - whole ? 1 : 0);
    }

    private static void steam(Minecraft mc, double x, double y, double z, RandomSource random)
    {
        plume(mc, ModParticles.THERMAL_VENT.get(), x, y, z, 0.25, random, 0.12f + random.nextFloat() * 0.08f, 0.05f + random.nextFloat() * 0.03f, 0.35f, 1f);
    }

    private static void plume(Minecraft mc, SimpleParticleType type, double x, double y, double z, double offset, RandomSource random,
                              float size, float speed, float alpha, float spread)
    {
        Particle particle = mc.particleEngine.createParticle(type,
                x + (random.nextDouble() - 0.5) * 2 * offset, y, z + (random.nextDouble() - 0.5) * 2 * offset, 0, 0, 0);
        if (particle instanceof AbyssParticle abyss) abyss.configure(size, speed, alpha, ParticleBudget.Budget.VENT).spread(spread);
    }

    /** Sediment lifted off the seabed around a vent by its inflow. */
    private static void stirSediment(Minecraft mc, ClientLevel level, TrackedVent vent, RandomSource random)
    {
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 2 + random.nextDouble() * (vent.type().radius - 2);
        int x = Mth.floor(vent.pos().getX() + 0.5 + Math.cos(angle) * dist);
        int z = Mth.floor(vent.pos().getZ() + 0.5 + Math.sin(angle) * dist);
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(x, vent.pos().getY() + 1, z);
        for (int i = 0; i < 16; i++, probe.move(0, -1, 0))
        {
            if (level.getFluidState(probe).is(FluidTags.WATER)) continue;
            if (i == 0) return;
            plume(mc, ModParticles.SEDIMENT.get(), x + 0.5, probe.getY() + 1.1, z + 0.5, 0.5, random, 0.03f + random.nextFloat() * 0.03f, 0.008f, 0.4f, 1f);
            return;
        }
    }
}
