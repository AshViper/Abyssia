package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.environment.CurrentStream;
import com.abyssia.environment.CurrentStreams;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * CU01 current streams drawn as translucent flowing ribbons so a stream reads from outside, not only as the sparse
 * streaks of {@link CurrentStreamClient}. Each stream gets two crossed core ribbons along its centre line (width
 * radius x 1.4, one flat, one upright) and 5-7 thinner lane ribbons offset from it (seeded from the stream id) that
 * slowly orbit and twist. The texture scrolls downstream at the stream's push speed. Alpha fades over the first / last
 * 12 % of the stream, to 0 at each ribbon's edges, and with distance (full to 48 blocks, 0 at {@code ribbon_distance}).
 * <p>
 * Geometry (positions, normals, base alpha) is built once per stream when it comes into range; each frame only the UV
 * scroll and distance fade change. Drawn at AFTER_TRANSLUCENT_BLOCKS through the vanilla buffer source with
 * {@link RenderType#entityTranslucentEmissive} (no cull, no depth write, full bright), which Iris / Oculus packs keep
 * visible; a custom render type would not be.
 * <p>
 * Loader-specific parts (events, vertex calls, stream polyline / speed) are marked "LOADER"; the rest is shared with
 * the NeoForge branch.
 */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CurrentStreamRibbons
{
    // LOADER
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/misc/current_stream.png");

    /** Blocks between ribbon samples (about one quad per 2 blocks along the stream). */
    private static final double SPACING = 2.0;
    private static final int MAX_QUADS = 4000;
    private static final double FULL_ALPHA_DISTANCE = 48.0;
    private static final double END_FADE = 0.12;
    private static final float CORE_ALPHA = 0.35f, LANE_ALPHA = 0.25f, MAX_ALPHA = 0.5f;
    private static final float RED = 0xEA / 255.0f, GREEN = 0xF8 / 255.0f, BLUE = 1.0f;
    private static final int REFRESH_TICKS = 10, SOLID_TICKS = 20;
    /** "RIBN" in ASCII: ribbon layout seed = stream id ^ this. */
    private static final long RIBBON_TAG = 0x5249424EL;

    /** One ribbon: per sample its centre and half-width vector (relative to the stream origin), face normal and base alpha. */
    private static final class Ribbon
    {
        final float[] cx, cy, cz, hx, hy, hz, nx, ny, nz, alpha;
        final double uScale;
        final boolean[] solid;

        Ribbon(int n, double width)
        {
            cx = new float[n];
            cy = new float[n];
            cz = new float[n];
            hx = new float[n];
            hy = new float[n];
            hz = new float[n];
            nx = new float[n];
            ny = new float[n];
            nz = new float[n];
            alpha = new float[n];
            solid = new boolean[Math.max(0, n - 1)];
            uScale = 1.0 / (width * 4.0);
        }
    }

    private static final class Geometry
    {
        final double ox, oy, oz;
        final double[] arc;
        final List<Ribbon> ribbons;
        final AABB box;
        /** Push speed along the stream, blocks per tick. */
        final double speed;
        long solidCheckedAt = Long.MIN_VALUE;

        Geometry(double ox, double oy, double oz, double[] arc, List<Ribbon> ribbons, AABB box, double speed)
        {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.arc = arc;
            this.ribbons = ribbons;
            this.box = box;
            this.speed = speed;
        }
    }

    private static final Map<Long, Geometry> GEOMETRY = new HashMap<>();
    private static List<Geometry> visible = List.of();
    private static long refreshedAt = Long.MIN_VALUE;
    private static ClientLevel refreshedLevel;

    private CurrentStreamRibbons() {}

    // ---- LOADER: events, stream data, vertices ------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        // 1.21: the view rotation is in the model-view matrix; vertices go in camera-relative with an identity pose
        render(new PoseStack(), event.getCamera().getPosition(), event.getFrustum(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    /** The stream's centre line as a dense polyline, upstream first. */
    private static List<Vec3> polyline(CurrentStream stream)
    {
        return stream.path();
    }

    /** Push speed along the stream (blocks per tick). */
    private static double speed(Level level, CurrentStream stream)
    {
        CurrentStreams.Settings s = CurrentStreams.settings(level);
        return s == null ? 0.16 * stream.strength() : Math.max(0.0, Math.min(s.maxSpeed(), s.baseSpeed() * stream.strength()));
    }

    private static boolean enabled()
    {
        return ClientConfig.STREAM_RIBBONS.get();
    }

    private static double maxDistance()
    {
        return ClientConfig.STREAM_RIBBON_DISTANCE.get();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float a, float u, float v,
                               float nx, float ny, float nz)
    {
        vc.addVertex(pose, x, y, z).setColor(RED, GREEN, BLUE, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    // ---- shared -------------------------------------------------------------------------------------------------

    private static void clear()
    {
        GEOMETRY.clear();
        visible = List.of();
        refreshedAt = Long.MIN_VALUE;
        refreshedLevel = null;
    }

    private static void render(PoseStack poseStack, Vec3 cam, Frustum frustum, float partialTick)
    {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimension() != Level.OVERWORLD || !enabled())
        {
            if (!GEOMETRY.isEmpty()) clear();
            return;
        }
        double maxDist = maxDistance();
        long now = level.getGameTime();
        if (level != refreshedLevel || now - refreshedAt >= REFRESH_TICKS || now < refreshedAt) refresh(level, cam, maxDist, now);
        if (visible.isEmpty()) return;

        double time = now + partialTick;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(TEXTURE);
        VertexConsumer vc = buffers.getBuffer(type);
        PoseStack.Pose pose = poseStack.last();
        int quads = 0;
        for (Geometry g : visible)
        {
            if (frustum != null && !frustum.isVisible(g.box)) continue;
            if (now - g.solidCheckedAt >= SOLID_TICKS || now < g.solidCheckedAt) checkSolid(level, g, now);
            quads = draw(vc, pose, g, cam, maxDist, time, quads);
            if (quads >= MAX_QUADS) break;
        }
        buffers.endBatch(type);
    }

    /** Streams in range, nearest first; geometry built for new ones, dropped for those gone. */
    private static void refresh(ClientLevel level, Vec3 cam, double maxDist, long now)
    {
        if (level != refreshedLevel) GEOMETRY.clear();
        refreshedLevel = level;
        refreshedAt = now;
        List<CurrentStream> near = CurrentStreams.near(level, cam.x, cam.y, cam.z, maxDist);
        Set<Long> ids = new HashSet<>();
        List<Geometry> list = new ArrayList<>(near.size());
        for (CurrentStream stream : near)
        {
            ids.add(stream.id());
            Geometry g = GEOMETRY.get(stream.id());
            if (g == null)
            {
                g = build(level, stream);
                if (g == null) continue;
                GEOMETRY.put(stream.id(), g);
            }
            list.add(g);
        }
        GEOMETRY.keySet().retainAll(ids);
        list.sort((a, b) -> Double.compare(boxDistanceSq(a.box, cam), boxDistanceSq(b.box, cam)));
        visible = list;
    }

    private static double boxDistanceSq(AABB box, Vec3 p)
    {
        double dx = Math.max(0.0, Math.max(box.minX - p.x, p.x - box.maxX));
        double dy = Math.max(0.0, Math.max(box.minY - p.y, p.y - box.maxY));
        double dz = Math.max(0.0, Math.max(box.minZ - p.z, p.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    private static Geometry build(Level level, CurrentStream stream)
    {
        List<Vec3> line = polyline(stream);
        if (line.size() < 2) return null;
        // Resample the centre line evenly by arc length.
        double[] cum = new double[line.size()];
        for (int i = 1; i < line.size(); i++) cum[i] = cum[i - 1] + line.get(i).distanceTo(line.get(i - 1));
        double total = cum[cum.length - 1];
        if (total < 1.0E-3) return null;
        int n = Math.max(2, (int) Math.ceil(total / SPACING) + 1);
        Vec3[] pts = new Vec3[n];
        double[] arc = new double[n];
        int seg = 0;
        for (int k = 0; k < n; k++)
        {
            double s = total * k / (n - 1);
            while (seg < line.size() - 2 && cum[seg + 1] < s) seg++;
            double len = cum[seg + 1] - cum[seg];
            double f = len < 1.0E-9 ? 0.0 : (s - cum[seg]) / len;
            f = Math.max(0.0, Math.min(1.0, f));
            pts[k] = line.get(seg).add(line.get(seg + 1).subtract(line.get(seg)).scale(f));
            arc[k] = s;
        }
        Vec3 origin = pts[0];
        double radius = stream.radius();
        float strength = (float) stream.strength();

        // Ribbon layout from the stream id: two crossed cores, then 5-7 lanes.
        Random r = new Random(stream.id() ^ RIBBON_TAG);
        List<double[]> specs = new ArrayList<>();
        // {offset, offsetAngle, orbitRate, width, angle, twistRate, maxAlpha}
        specs.add(new double[] {0.0, 0.0, 0.0, radius * 1.4, 0.0, 0.0, CORE_ALPHA});
        specs.add(new double[] {0.0, 0.0, 0.0, radius * 1.4, Math.PI * 0.5, 0.0, CORE_ALPHA});
        int lanes = 5 + r.nextInt(3);
        for (int i = 0; i < lanes; i++)
        {
            double offset = radius * (0.25 + r.nextDouble() * 0.45);
            double angle0 = (i + r.nextDouble() * 0.6) / lanes * Math.PI * 2.0;
            double orbit = (r.nextDouble() - 0.5) * 0.03;
            double width = radius * (0.35 + r.nextDouble() * 0.25);
            double tilt = r.nextDouble() * Math.PI;
            double twist = (r.nextDouble() - 0.5) * 0.05;
            specs.add(new double[] {offset, angle0, orbit, width, tilt, twist, LANE_ALPHA});
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        List<Ribbon> ribbons = new ArrayList<>(specs.size());
        for (double[] sp : specs)
        {
            double width = sp[3];
            Ribbon rb = new Ribbon(n, width);
            float maxAlpha = Math.min(MAX_ALPHA, (float) sp[6] * strength);
            for (int k = 0; k < n; k++)
            {
                Vec3 t = pts[Math.min(n - 1, k + 1)].subtract(pts[Math.max(0, k - 1)]);
                t = t.lengthSqr() < 1.0E-12 ? new Vec3(1, 0, 0) : t.normalize();
                Vec3 side = new Vec3(-t.z, 0, t.x);
                side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
                Vec3 up = side.cross(t).normalize();
                double s = arc[k];
                double a = sp[1] + sp[2] * s;
                Vec3 c = pts[k].add(side.scale(sp[0] * Math.cos(a))).add(up.scale(sp[0] * Math.sin(a)));
                double th = sp[4] + sp[5] * s;
                Vec3 across = side.scale(Math.cos(th)).add(up.scale(Math.sin(th)));
                Vec3 half = across.scale(width * 0.5);
                Vec3 normal = t.cross(across);
                normal = normal.lengthSqr() < 1.0E-12 ? up : normal.normalize();
                rb.cx[k] = (float) (c.x - origin.x);
                rb.cy[k] = (float) (c.y - origin.y);
                rb.cz[k] = (float) (c.z - origin.z);
                rb.hx[k] = (float) half.x;
                rb.hy[k] = (float) half.y;
                rb.hz[k] = (float) half.z;
                rb.nx[k] = (float) normal.x;
                rb.ny[k] = (float) normal.y;
                rb.nz[k] = (float) normal.z;
                double u = s / total;
                double end = Math.min(smooth(u / END_FADE), smooth((1.0 - u) / END_FADE));
                rb.alpha[k] = (float) (maxAlpha * end);
                double ex = Math.abs(half.x), ey = Math.abs(half.y), ez = Math.abs(half.z);
                minX = Math.min(minX, c.x - ex);
                minY = Math.min(minY, c.y - ey);
                minZ = Math.min(minZ, c.z - ez);
                maxX = Math.max(maxX, c.x + ex);
                maxY = Math.max(maxY, c.y + ey);
                maxZ = Math.max(maxZ, c.z + ez);
            }
            ribbons.add(rb);
        }
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(1.0);
        return new Geometry(origin.x, origin.y, origin.z, arc, ribbons, box, speed(level, stream));
    }

    private static double smooth(double x)
    {
        x = Math.max(0.0, Math.min(1.0, x));
        return x * x * (3.0 - 2.0 * x);
    }

    /** Marks segments whose ribbon centre (midpoint) sits inside a solid block. */
    private static void checkSolid(Level level, Geometry g, long now)
    {
        g.solidCheckedAt = now;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Ribbon rb : g.ribbons)
        {
            for (int k = 0; k < rb.solid.length; k++)
            {
                double x = g.ox + (rb.cx[k] + rb.cx[k + 1]) * 0.5;
                double y = g.oy + (rb.cy[k] + rb.cy[k + 1]) * 0.5;
                double z = g.oz + (rb.cz[k] + rb.cz[k + 1]) * 0.5;
                pos.set(x, y, z);
                rb.solid[k] = level.getBlockState(pos).isSolidRender(level, pos);
            }
        }
    }

    private static float fade(double distSq, double maxDist)
    {
        if (distSq <= FULL_ALPHA_DISTANCE * FULL_ALPHA_DISTANCE) return 1.0f;
        double d = Math.sqrt(distSq);
        if (d >= maxDist) return 0.0f;
        return (float) (1.0 - (d - FULL_ALPHA_DISTANCE) / Math.max(1.0, maxDist - FULL_ALPHA_DISTANCE));
    }

    /** Emits the stream's ribbons (two quads across each segment: edge-centre, centre-edge); returns the new quad count. */
    private static int draw(VertexConsumer vc, PoseStack.Pose pose, Geometry g, Vec3 cam, double maxDist, double time, int quads)
    {
        double bx = g.ox - cam.x, by = g.oy - cam.y, bz = g.oz - cam.z;
        double travelled = time * g.speed;
        for (Ribbon rb : g.ribbons)
        {
            double shift = travelled * rb.uScale;
            shift -= Math.floor(shift);
            int n = rb.cx.length;
            float prevA = 0.0f, prevU = 0.0f;
            float px = 0, py = 0, pz = 0;
            for (int k = 0; k < n; k++)
            {
                float x = (float) (bx + rb.cx[k]), y = (float) (by + rb.cy[k]), z = (float) (bz + rb.cz[k]);
                float a = rb.alpha[k] * fade((double) x * x + (double) y * y + (double) z * z, maxDist);
                float u = (float) (g.arc[k] * rb.uScale - shift);
                if (k > 0 && !rb.solid[k - 1] && (a > 0.003f || prevA > 0.003f))
                {
                    int j = k - 1;
                    float ax = px - rb.hx[j], ay = py - rb.hy[j], az = pz - rb.hz[j];
                    float cx = px + rb.hx[j], cy = py + rb.hy[j], cz = pz + rb.hz[j];
                    float dx = x - rb.hx[k], dy = y - rb.hy[k], dz = z - rb.hz[k];
                    float ex = x + rb.hx[k], ey = y + rb.hy[k], ez = z + rb.hz[k];
                    float n0x = rb.nx[j], n0y = rb.ny[j], n0z = rb.nz[j];
                    float n1x = rb.nx[k], n1y = rb.ny[k], n1z = rb.nz[k];
                    // edge (V 0) -> centre (V 0.5)
                    vertex(vc, pose, ax, ay, az, 0.0f, prevU, 0.0f, n0x, n0y, n0z);
                    vertex(vc, pose, px, py, pz, prevA, prevU, 0.5f, n0x, n0y, n0z);
                    vertex(vc, pose, x, y, z, a, u, 0.5f, n1x, n1y, n1z);
                    vertex(vc, pose, dx, dy, dz, 0.0f, u, 0.0f, n1x, n1y, n1z);
                    // centre (V 0.5) -> edge (V 1)
                    vertex(vc, pose, px, py, pz, prevA, prevU, 0.5f, n0x, n0y, n0z);
                    vertex(vc, pose, cx, cy, cz, 0.0f, prevU, 1.0f, n0x, n0y, n0z);
                    vertex(vc, pose, ex, ey, ez, 0.0f, u, 1.0f, n1x, n1y, n1z);
                    vertex(vc, pose, x, y, z, a, u, 0.5f, n1x, n1y, n1z);
                    quads += 2;
                    if (quads >= MAX_QUADS) return quads;
                }
                prevA = a;
                prevU = u;
                px = x;
                py = y;
                pz = z;
            }
        }
        return quads;
    }
}
