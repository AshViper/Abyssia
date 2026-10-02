package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.environment.CurrentData;
import com.abyssia.environment.NaturalCurrent;
import com.abyssia.environment.NaturalCurrents;
import com.abyssia.environment.ParticleBudget;
import com.abyssia.environment.ParticleBudget.Budget;
import com.abyssia.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;
import java.util.Locale;

/**
 * Makes natural currents visible: streams within {@code natural_currents.particle_distance} of the camera fill with
 * {@code current_mote} particles flowing along them, densest on the axis and thinning toward the edge. Nearby streams
 * are looked up once a second; where there are none, nothing else runs. Also adds the stream at your feet to F3.
 */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class NaturalCurrentClient
{
    private static final int LOOKUP_INTERVAL = 20;
    /** Spawn attempts per stream per tick at density 1: a base plus more for stronger streams. */
    private static final double BASE_ATTEMPTS = 2.0, ATTEMPTS_PER_STRENGTH = 8.0;

    private static List<NaturalCurrent> nearby = List.of();
    private static int ticks;

    private NaturalCurrentClient() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        NaturalCurrents.setClientSettings(null, 0.0, 0.0);
        nearby = List.of();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || level.dimension() != Level.OVERWORLD) return;
        double density = ClientConfig.CURRENT_PARTICLE_DENSITY.get();
        if (density <= 0.0) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double range = ClientConfig.CURRENT_PARTICLE_DISTANCE.get();
        if (ticks++ % LOOKUP_INTERVAL == 0) nearby = NaturalCurrents.near(level, cam.x, cam.y, cam.z, range);
        if (nearby.isEmpty()) return;

        RandomSource random = level.random;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (NaturalCurrent current : nearby)
        {
            double attempts = density * (BASE_ATTEMPTS + ATTEMPTS_PER_STRENGTH * current.strength());
            int n = (int) attempts + (random.nextDouble() < attempts - (int) attempts ? 1 : 0);
            for (int i = 0; i < n && ParticleBudget.hasRoom(Budget.CURRENT); i++)
            {
                Vec3 p = sample(current, cam, range, random);
                if (p == null) continue;
                // Densest on the axis: accept by the square root of the falloff.
                float falloff = current.falloff(p.x, p.y, p.z);
                if (falloff <= 0f || random.nextFloat() > Math.sqrt(falloff)) continue;
                if (!level.getFluidState(pos.set(p.x, p.y, p.z)).is(FluidTags.WATER)) continue;
                mc.particleEngine.createParticle(ModParticles.CURRENT_MOTE.get(), p.x, p.y, p.z, 0, 0, 0);
            }
        }
    }

    /** A random point inside the stream, on the stretch of it within {@code range} of the camera, or null. */
    private static Vec3 sample(NaturalCurrent c, Vec3 cam, double range, RandomSource random)
    {
        Vec3 axis = c.end().subtract(c.start());
        double length = axis.length();
        double t = 0.0;
        if (length > 1.0E-3)
        {
            double t0 = cam.subtract(c.start()).dot(axis) / (length * length);
            double span = range / length;
            double lo = Math.max(0.0, t0 - span), hi = Math.min(1.0, t0 + span);
            if (lo > hi) return null;
            t = lo + random.nextDouble() * (hi - lo);
        }
        Vec3 dir = c.direction();
        Vec3 across = new Vec3(-dir.z, 0, dir.x);
        across = across.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : across.normalize();
        double a = random.nextDouble() * 2 - 1, b = random.nextDouble() * 2 - 1;
        if (a * a + b * b > 1.0) return null;
        // A little along the axis too, so round eddies fill their whole ball.
        double along = length > 1.0E-3 ? 0.0 : (random.nextDouble() * 2 - 1) * c.radius();
        Vec3 p = c.start().add(axis.scale(t)).add(across.scale(a * c.radius())).add(dir.scale(along)).add(0, b * c.height(), 0);
        return p.distanceToSqr(cam) <= range * range ? p : null;
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event)
    {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getDebugOverlay().showDebugScreen() || mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) return;
        CurrentData data = NaturalCurrents.getCurrentAt(mc.level, mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        if (!data.isPresent())
        {
            event.getRight().add("Natural current: none");
            return;
        }
        Vec3 d = data.getDirection();
        event.getRight().add(String.format(Locale.ROOT, "Natural current: %s", data.current().type()));
        event.getRight().add(String.format(Locale.ROOT, " dir (%.2f, %.2f, %.2f)", d.x, d.y, d.z));
        event.getRight().add(String.format(Locale.ROOT, " strength %.2f (here %.2f) radius %.0f", data.getStrength(), data.getLocalStrength(), data.getRadius()));
    }
}
