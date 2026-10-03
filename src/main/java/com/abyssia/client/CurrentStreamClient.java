package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.client.particle.CurrentStreamParticle;
import com.abyssia.environment.CurrentStream;
import com.abyssia.environment.CurrentStreams;
import com.abyssia.environment.ParticleBudget;
import com.abyssia.environment.ParticleBudget.Budget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Makes CU01 current streams visible as bundles of thin white streaks riding the flow. Each stream has a fixed set of
 * lanes across its section (from its id, so the streaks line up into a few dozen continuous strands), thinned in the
 * rim (r 0.7-0.98). Level of detail by distance from the camera to the band: 0-32 blocks 160-80 streaks alive,
 * 32-64 80-30, 64-96 30-10 (scaled by strength), nothing beyond {@code max_render_distance}; all streams share
 * {@code particle_budget}, nearest and most central first.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CurrentStreamClient
{
    private static final int LOOKUP_INTERVAL = 20;
    private static final int MIN_LIFETIME = 20, MAX_LIFETIME = 60;
    private static final double MEAN_LIFETIME = (MIN_LIFETIME + MAX_LIFETIME) * 0.5;
    private static final int MIN_LANES = 24, EXTRA_LANES = 17;
    /** About 15 % of lanes run along the rim (r 0.7-0.98), and 60 % of their spawns are skipped, so the edge stays soft. */
    private static final double RIM_LANES = 0.15, RIM_SKIP = 0.6;
    private static final double LANE_JITTER = 0.15;
    /** "LANE" in ASCII: lane layout seed = stream id ^ this. */
    private static final long LANE_TAG = 0x4C414E45L;

    private record Lane(double u, double v, double r) {}

    private record Visible(CurrentStream stream, double t, double distance, double priority) {}

    private static List<CurrentStream> nearby = List.of();
    private static final Map<Long, List<Lane>> LANES = new HashMap<>();
    private static int ticks;

    private CurrentStreamClient() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        CurrentStreams.setClientSettings(null, null);
        nearby = List.of();
        LANES.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || level.dimension() != Level.OVERWORLD) return;
        int budget = ClientConfig.CURRENT_STREAM_PARTICLE_BUDGET.get();
        if (budget <= 0 || CurrentStreamParticle.sprites == null) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        double range = ClientConfig.CURRENT_STREAM_RENDER_DISTANCE.get();
        if (ticks++ % LOOKUP_INTERVAL == 0)
        {
            nearby = CurrentStreams.near(level, cam, range);
            if (LANES.size() > 256) LANES.clear();
        }
        if (nearby.isEmpty()) return;

        Vector3f look = camera.getLookVector();
        List<Visible> visible = new ArrayList<>();
        for (CurrentStream stream : nearby)
        {
            double[] near = stream.nearest(cam.x, cam.y, cam.z);
            double distance = Math.max(0.0, near[1] - stream.radius());
            if (distance > range) continue;
            Vec3 to = stream.point(near[0]).subtract(cam);
            double facing = to.lengthSqr() < 1.0E-6 ? 1.0 : to.normalize().dot(new Vec3(look.x(), look.y(), look.z()));
            // Nearer first; among similar distances, the one nearer the middle of the screen.
            visible.add(new Visible(stream, near[0], distance, distance + 16.0 * (1.0 - facing)));
        }
        visible.sort(Comparator.comparingDouble(Visible::priority));

        RandomSource random = level.random;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Visible v : visible)
        {
            double perTick = targetCount(v.distance()) * v.stream().strength() / MEAN_LIFETIME;
            int n = (int) perTick + (random.nextDouble() < perTick - (int) perTick ? 1 : 0);
            for (int i = 0; i < n; i++)
            {
                if (ParticleBudget.alive(Budget.STREAM) >= budget) return;
                spawn(mc, level, v, cam, range, random, pos);
            }
        }
    }

    /** Streaks alive per stream by distance to its band: 160-80 near, 80-30 middle, 30-10 far. */
    private static double targetCount(double d)
    {
        if (d < 32.0) return 160.0 - d / 32.0 * 80.0;
        if (d < 64.0) return 80.0 - (d - 32.0) / 32.0 * 50.0;
        if (d < 96.0) return 30.0 - (d - 64.0) / 32.0 * 20.0;
        return 0.0;
    }

    private static void spawn(Minecraft mc, ClientLevel level, Visible v, Vec3 cam, double range, RandomSource random, BlockPos.MutableBlockPos pos)
    {
        CurrentStream stream = v.stream();
        // Along the stretch near the camera: wider for distant streams so their whole visible run shows.
        double arc = Math.max(1.0, stream.length());
        double half = (32.0 + v.distance()) / arc;
        double t = Math.max(0.0, Math.min(1.0, v.t() + (random.nextDouble() * 2.0 - 1.0) * half));
        Vec3 center = stream.point(t);
        Vec3 tangent = stream.tangent(t);
        Vec3 side = new Vec3(-tangent.z, 0, tangent.x);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(tangent).normalize();

        List<Lane> lanes = LANES.computeIfAbsent(stream.id(), id -> lanes(stream));
        Lane lane = lanes.get(random.nextInt(lanes.size()));
        if (lane.r() >= 0.7 && random.nextDouble() < RIM_SKIP) return;
        double radius = stream.radius();
        double u = lane.u() * radius + (random.nextDouble() * 2.0 - 1.0) * LANE_JITTER;
        double w = lane.v() * radius + (random.nextDouble() * 2.0 - 1.0) * LANE_JITTER;
        Vec3 p = center.add(side.scale(u)).add(up.scale(w));
        if (p.distanceToSqr(cam) > range * range) return;
        if (!level.getFluidState(pos.set(p.x, p.y, p.z)).is(FluidTags.WATER)) return;

        double camDistance = Math.sqrt(p.distanceToSqr(cam));
        boolean accent = camDistance < 16.0 && random.nextFloat() < 0.08f;
        float width = accent ? 0.08f + random.nextFloat() * 0.04f : 0.025f + random.nextFloat() * 0.055f;
        float length = accent ? 2.5f + random.nextFloat() * 1.0f : 0.8f + random.nextFloat() * 1.7f;
        // Mid and far streams favour the longer streaks so the band's shape still reads.
        if (v.distance() >= 32.0 && !accent) length = Math.min(2.5f, length + 0.5f);
        int lifetime = MIN_LIFETIME + random.nextInt(MAX_LIFETIME - MIN_LIFETIME + 1);
        // Centre 0.20-0.45, edge 0.05-0.20 (blended over the outer 35 %).
        float edge = (float) Math.max(0.0, Math.min(1.0, (lane.r() - 0.65) / 0.35));
        float lo = 0.20f + (0.05f - 0.20f) * edge, hi = 0.45f + (0.20f - 0.45f) * edge;
        float alpha = lo + random.nextFloat() * (hi - lo);
        mc.particleEngine.add(new CurrentStreamParticle(level, p.x, p.y, p.z, stream, tangent, width, length, lifetime, alpha));
    }

    /** The stream's fixed lanes: points in the unit disc of its section, seeded from its id, thinned in the outer band. */
    private static List<Lane> lanes(CurrentStream stream)
    {
        Random r = new Random(stream.id() ^ LANE_TAG);
        int count = MIN_LANES + r.nextInt(EXTRA_LANES);
        List<Lane> lanes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            double a = r.nextDouble() * Math.PI * 2.0;
            double rr = r.nextDouble() < RIM_LANES ? 0.7 + r.nextDouble() * 0.28 : Math.sqrt(r.nextDouble()) * 0.7;
            lanes.add(new Lane(rr * Math.cos(a), rr * Math.sin(a), rr));
        }
        return lanes;
    }
}
