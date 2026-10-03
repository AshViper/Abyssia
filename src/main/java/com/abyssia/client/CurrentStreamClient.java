package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.client.particle.CurrentParticle;
import com.abyssia.environment.CurrentStream;
import com.abyssia.environment.CurrentStreams;
import com.abyssia.environment.ParticleBudget;
import com.abyssia.environment.ParticleBudget.Budget;
import com.abyssia.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Makes CU01 current streams visible as bundles of thin white streaks ({@code current_mote} in stream mode, vanilla
 * translucent sheet so shader packs keep them). Each band has a fixed set of "lanes" across its cross-section (from its
 * id), and streaks are released along those lanes so they line up into continuous threads that flow with the water.
 * LOD by distance to the band: near 0-32 (80-160 streaks), mid 32-64 (30-80), far 64-96 (10-30), none beyond
 * {@code current_streams.max_render_distance}. One shared budget ({@code particle_budget}), handed out nearest band
 * first and bands in front of the camera before ones behind it. The outer 30% of each band gets few lanes.
 * <p>
 * Same behaviour as the NeoForge branch (canonical for CU01); only the band geometry API differs (the spline is
 * evaluated by parameter t here, so the release span is converted from blocks to t by the band's arc length).
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CurrentStreamClient
{
    private static final int LOOKUP_INTERVAL = 20;
    /** Average streak lifetime: spawning target / this per tick keeps about 'target' alive. */
    private static final double AVG_LIFETIME = 40.0;
    /** Streaks are released on the stretch of band within this many blocks (along it) of the camera's nearest point. */
    private static final double SPAN_NEAR = 40.0, SPAN_FAR = 64.0;
    private static final long LANE_SALT = 0x4C414E45L;

    private record Lane(double u, double v, boolean outer) {}

    private static List<CurrentStream> nearby = List.of();
    private static final Map<Long, List<Lane>> LANES = new HashMap<>();
    private static final Map<Long, Double> ARC_LENGTHS = new HashMap<>();
    private static int ticks;

    private CurrentStreamClient() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        CurrentStreams.setClientSettings(null, null);
        nearby = List.of();
        LANES.clear();
        ARC_LENGTHS.clear();
    }

    private static List<Lane> lanes(CurrentStream c)
    {
        if (LANES.size() > 256) LANES.clear();
        return LANES.computeIfAbsent(c.id(), id ->
        {
            Random r = new Random(id ^ LANE_SALT);
            int count = 24 + r.nextInt(17);
            List<Lane> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
            {
                // ~85% of lanes in the inner 70% of the radius, the rest thinly in the outer rim.
                boolean outer = r.nextDouble() < 0.15;
                double rad = outer ? 0.7 + r.nextDouble() * 0.28 : Math.sqrt(r.nextDouble()) * 0.7;
                double a = r.nextDouble() * Math.PI * 2.0;
                list.add(new Lane(Math.cos(a) * rad, Math.sin(a) * rad, outer));
            }
            return list;
        });
    }

    private static double arcLength(CurrentStream c)
    {
        if (ARC_LENGTHS.size() > 256) ARC_LENGTHS.clear();
        return ARC_LENGTHS.computeIfAbsent(c.id(), id -> Math.max(1.0, c.arcLength()));
    }

    /** {@code t}: centre-line parameter of the band's nearest point to the camera. */
    private record Visible(CurrentStream stream, double t, double distance, double score) {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || level.dimension() != Level.OVERWORLD) return;
        int budget = ClientConfig.CURRENT_STREAM_PARTICLE_BUDGET.get();
        double maxDist = ClientConfig.CURRENT_STREAM_RENDER_DISTANCE.get();
        if (budget <= 0 || maxDist <= 0 || CurrentStreams.params(level) == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 look = new Vec3(mc.gameRenderer.getMainCamera().getLookVector());
        if (ticks++ % LOOKUP_INTERVAL == 0) nearby = CurrentStreams.near(level, cam.x, cam.y, cam.z, maxDist);
        if (nearby.isEmpty()) return;

        List<Visible> visible = new ArrayList<>();
        for (CurrentStream c : nearby)
        {
            double[] n = c.nearest(cam.x, cam.y, cam.z);
            double d = Math.max(0.0, n[1] - c.radius());
            if (d > maxDist) continue;
            Vec3 to = c.point(n[0]).subtract(cam);
            double facing = to.lengthSqr() < 1.0E-6 ? 1.0 : to.normalize().dot(look);
            // Nearest first; among similar distances, the one nearer the screen centre.
            visible.add(new Visible(c, n[0], d, d * (1.5 - 0.5 * facing)));
        }
        visible.sort(Comparator.comparingDouble(Visible::score));

        RandomSource random = level.random;
        int remaining = budget;
        for (Visible v : visible)
        {
            double strengthT = Math.max(0.0, Math.min(1.0, (v.stream().strength() - 0.65) / 0.55));
            int target = v.distance() < 32 ? (int) (80 + 80 * strengthT) : v.distance() < 64 ? (int) (30 + 50 * strengthT) : (int) (10 + 20 * strengthT);
            target = Math.min(target, remaining);
            remaining -= target;
            if (target <= 0) break;
            double rate = target / AVG_LIFETIME;
            int n = (int) rate + (random.nextDouble() < rate - (int) rate ? 1 : 0);
            for (int i = 0; i < n && ParticleBudget.alive(Budget.STREAM) < budget; i++) spawn(mc, level, v, cam, maxDist, random);
        }
    }

    private static void spawn(Minecraft mc, ClientLevel level, Visible v, Vec3 cam, double maxDist, RandomSource random)
    {
        CurrentStream c = v.stream();
        CurrentStreams.Params params = CurrentStreams.params(level);
        if (params == null) return;
        List<Lane> lanes = lanes(c);
        Lane lane = lanes.get(random.nextInt(lanes.size()));
        // Outer-rim lanes release far fewer streaks.
        if (lane.outer() && random.nextFloat() < 0.6f) return;

        // A point along the band near the camera; nothing past either end of the band.
        double span = v.distance() < 32 ? SPAN_NEAR : SPAN_FAR;
        double offset = (random.nextDouble() * 2.0 - 1.0) * span;
        double t = v.t() + offset / arcLength(c);
        if (t < 0.0 || t > 1.0) return;
        Vec3 center = c.point(t);
        Vec3 dir = c.tangent(t);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        double jitter = 0.15;
        Vec3 p = center.add(side.scale(lane.u() * c.radius() + (random.nextDouble() * 2 - 1) * jitter))
                .add(up.scale(lane.v() * c.radius() + (random.nextDouble() * 2 - 1) * jitter));
        if (p.distanceToSqr(cam) > maxDist * maxDist) return;
        if (!level.getFluidState(BlockPos.containing(p.x, p.y, p.z)).is(FluidTags.WATER)) return;

        double r = Math.sqrt(lane.u() * lane.u() + lane.v() * lane.v());
        double flow = 1.0 - CurrentStreams.smoothstep(0.65, 1.0, r);
        double speed = CurrentStreams.flowSpeed(params, c.strength()) * Math.max(0.3, flow);
        Vec3 vel = dir.scale(Math.max(0.02, speed));
        Particle particle = mc.particleEngine.createParticle(ModParticles.CURRENT_MOTE.get(), p.x, p.y, p.z, vel.x, vel.y, vel.z);
        if (!(particle instanceof CurrentParticle streak)) return;

        float width, length;
        if (v.distance() < 16 && random.nextFloat() < 0.1f)
        {
            // Near accent.
            width = 0.08f + random.nextFloat() * 0.04f;
            length = 2.5f + random.nextFloat() * 1.0f;
        }
        else if (v.distance() < 32)
        {
            width = 0.025f + random.nextFloat() * 0.055f;
            length = 0.8f + random.nextFloat() * 1.7f;
        }
        else if (v.distance() < 64)
        {
            width = 0.04f + random.nextFloat() * 0.04f;
            length = 1.5f + random.nextFloat() * 1.0f;
        }
        else
        {
            width = 0.06f + random.nextFloat() * 0.02f;
            length = 2.0f + random.nextFloat() * 0.5f;
        }
        float alpha = r < 0.7 ? 0.20f + random.nextFloat() * 0.25f : 0.05f + random.nextFloat() * 0.15f;
        streak.setStreak(width, length, alpha, 20 + random.nextInt(41));
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event)
    {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.renderDebug || mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) return;
        CurrentStreams.Sample s = CurrentStreams.sample(mc.level, mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        if (s == null) return;
        Vec3 d = s.direction();
        event.getRight().add(String.format(Locale.ROOT, "Current stream: %s strength %.2f r %.2f flow %.2f", s.stream().tier(), s.strength(), s.r(), s.flowMultiplier()));
        event.getRight().add(String.format(Locale.ROOT, " dir (%.2f, %.2f, %.2f) streaks %d", d.x, d.y, d.z, ParticleBudget.alive(Budget.STREAM)));
    }
}
