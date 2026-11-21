package com.abyssia.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * BOSS03: smooth meshes for the abyss bosses, in place of the old chains of boxes. A body is a tube skinned along a
 * Catmull-Rom spline through the chain joints (no kinks or gaps at bends), heads and bells are lofts (rings of a
 * super-ellipse along an axis), fins are ribbons along the same spline, teeth and horns are spikes.
 * <p>
 * Texture regions {u, v, w, h} are the 256x256 layout of {@code tools/boss_textures.py}; a wrap puts the right side at
 * u = 0, the back at 1/4, the left at 1/2 and the belly at 3/4. The same geometry is drawn twice: the skin, then the
 * {@code _glow} texture additively ({@code RenderType.eyes}, black = nothing), so glow patterns sit exactly on the skin.
 * <p>
 * {@link #v} is the only loader-specific call (this is the Forge 1.20.1 form; NeoForge 1.21.1 uses addVertex/setColor/...).
 */
final class BossMesh
{
    static final float TEX = 256.0F;
    static final float[] SKIN = {0, 0, 128, 64}, SKIN2 = {128, 0, 128, 64}, HEAD = {0, 64, 128, 64}, INNER = {0, 128, 128, 64},
            MEMBRANE = {128, 64, 64, 64}, TOOTH = {192, 64, 16, 16}, GLOW = {208, 64, 16, 16}, EYE = {224, 64, 16, 16},
            FLESH = {240, 64, 16, 16}, HORN = {192, 80, 16, 16};

    /** Cross-section at joint coordinate k: out = {half width, height above the axis, height below it, exponent (1 = ellipse, lower = boxier)}. */
    @FunctionalInterface
    interface Section
    {
        void at(float k, float[] out);
    }

    /** Points along a body with a frame each (tangent t, up u, right r) and their joint coordinate k. */
    static final class Path
    {
        final int n;
        final float[] x, y, z, tx, ty, tz, ux, uy, uz, rx, ry, rz, k;

        Path(int n)
        {
            this.n = n;
            x = new float[n]; y = new float[n]; z = new float[n];
            tx = new float[n]; ty = new float[n]; tz = new float[n];
            ux = new float[n]; uy = new float[n]; uz = new float[n];
            rx = new float[n]; ry = new float[n]; rz = new float[n];
            k = new float[n];
        }
    }

    private final VertexConsumer vc;
    private PoseStack.Pose last;
    private int light;
    private float cr = 1, cg = 1, cb = 1, ca = 1;
    private boolean outward;
    private final float[] sec = new float[4];

    BossMesh(VertexConsumer vc, PoseStack pose, int light)
    {
        this.vc = vc;
        this.last = pose.last();
        this.light = light;
    }

    /** Call after the pose stack changed (push, translate, rotate). */
    BossMesh pose(PoseStack pose)
    {
        last = pose.last();
        return this;
    }

    BossMesh light(int light)
    {
        this.light = light;
        return this;
    }

    BossMesh color(float r, float g, float b, float a)
    {
        cr = r; cg = g; cb = b; ca = a;
        return this;
    }

    /**
     * Tube (and loft) quads wind inward by default, which only a no-cull type ignores; on, they are emitted in reverse
     * order so their front faces look out (for a back-face-culled pass such as {@code RenderType.eyes}). Default off.
     */
    BossMesh outward(boolean on)
    {
        outward = on;
        return this;
    }

    private void v(float x, float y, float z, float u, float v, float nx, float ny, float nz, int light)
    {
        vc.vertex(last.pose(), x, y, z).color(cr, cg, cb, ca).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(last.normal(), nx, ny, nz).endVertex();
    }

    private static float tu(float[] uv, float f)
    {
        return (uv[0] + 0.5F + f * (uv[2] - 1.0F)) / TEX;
    }

    private static float tv(float[] uv, float f)
    {
        return (uv[1] + 0.5F + f * (uv[3] - 1.0F)) / TEX;
    }

    // ---------------------------------------------------------------- paths

    private static double catmull(double a, double b, double c, double d, double t)
    {
        return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (3 * b - a - 3 * c + d) * t * t * t);
    }

    /**
     * A smooth path through joints from..to ({@code sub} points per joint gap), relative to (ox, oy, oz).
     * upBias pulls each frame's up toward world up (0 = pure parallel transport, for tentacles; ~0.3 keeps a back up).
     */
    static Path spline(double[] jx, double[] jy, double[] jz, int from, int to, int sub, double ox, double oy, double oz, float upBias)
    {
        return spline(jx, jy, jz, from, to, sub, ox, oy, oz, upBias, 0, 1, 0);
    }

    /** As above, the first frame's up starting from the hint (hx, hy, hz): a tentacle's back, turned away from the body. */
    static Path spline(double[] jx, double[] jy, double[] jz, int from, int to, int sub, double ox, double oy, double oz, float upBias,
                       float hx, float hy, float hz)
    {
        int segs = Math.max(1, to - from);
        Path p = new Path(segs * sub + 1);
        for (int i = 0; i < p.n; i++)
        {
            int s = Math.min(i / sub, segs - 1);
            float t = (i - s * sub) / (float) sub;
            int b = from + s, a = Math.max(b - 1, from), c = Math.min(b + 1, to), d = Math.min(c + 1, to);
            p.x[i] = (float) (catmull(jx[a], jx[b], jx[c], jx[d], t) - ox);
            p.y[i] = (float) (catmull(jy[a], jy[b], jy[c], jy[d], t) - oy);
            p.z[i] = (float) (catmull(jz[a], jz[b], jz[c], jz[d], t) - oz);
            p.k[i] = b + t;
        }
        frames(p, upBias, hx, hy, hz);
        return p;
    }

    /** A path through given points (k = index), frames carried from the up hint. */
    static Path through(float[] xs, float[] ys, float[] zs, float hx, float hy, float hz)
    {
        Path p = new Path(xs.length);
        for (int i = 0; i < p.n; i++)
        {
            p.x[i] = xs[i]; p.y[i] = ys[i]; p.z[i] = zs[i];
            p.k[i] = i;
        }
        frames(p, 0, hx, hy, hz);
        return p;
    }

    /** A closed horizontal circle (segs + 1 points) whose up points straight down: a rim to hang a ribbon from. k runs 0..tiles. */
    static Path circle(float radius, float y, int segs, float tiles)
    {
        Path p = new Path(segs + 1);
        for (int i = 0; i <= segs; i++)
        {
            float a = Mth.TWO_PI * (i % segs) / segs, c = Mth.cos(a), s = Mth.sin(a);
            p.x[i] = c * radius; p.y[i] = y; p.z[i] = s * radius;
            p.tx[i] = -s; p.tz[i] = c;
            p.uy[i] = -1;
            p.rx[i] = c; p.rz[i] = s;   // "right" = outward, so a ribbon's sway flares the rim
            p.k[i] = i * tiles / segs;
        }
        return p;
    }

    /** Tangents by central difference, then up vectors carried along the path (and pulled toward world up by upBias). */
    static void frames(Path p, float upBias, float hx, float hy, float hz)
    {
        float ux = hx, uy = hy, uz = hz;
        float ptx = 0, pty = 0, ptz = 1;
        for (int i = 0; i < p.n; i++)
        {
            int a = Math.max(i - 1, 0), b = Math.min(i + 1, p.n - 1);
            float tx = p.x[b] - p.x[a], ty = p.y[b] - p.y[a], tz = p.z[b] - p.z[a];
            float len = Mth.sqrt(tx * tx + ty * ty + tz * tz);
            if (len < 1.0E-5F) { tx = ptx; ty = pty; tz = ptz; }
            else { tx /= len; ty /= len; tz /= len; }
            ptx = tx; pty = ty; ptz = tz;
            float d = ux * tx + uy * ty + uz * tz;
            ux -= d * tx; uy -= d * ty; uz -= d * tz;
            len = Mth.sqrt(ux * ux + uy * uy + uz * uz);
            if (len < 1.0E-3F)
            {
                // up ran into the tangent: restart from the axis least along it
                if (Math.abs(ty) < 0.9F) { ux = -tx * ty; uy = 1 - ty * ty; uz = -tz * ty; }
                else { ux = 1 - tx * tx; uy = -ty * tx; uz = -tz * tx; }
                len = Mth.sqrt(ux * ux + uy * uy + uz * uz);
            }
            ux /= len; uy /= len; uz /= len;
            if (upBias > 0)
            {
                float wx = -tx * ty, wy = 1 - ty * ty, wz = -tz * ty;
                float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
                if (wl > 0.3F)
                {
                    float f = i == 0 ? 1.0F : upBias;
                    ux += (wx / wl - ux) * f; uy += (wy / wl - uy) * f; uz += (wz / wl - uz) * f;
                    len = Mth.sqrt(ux * ux + uy * uy + uz * uz);
                    if (len > 1.0E-3F) { ux /= len; uy /= len; uz /= len; }
                }
            }
            p.tx[i] = tx; p.ty[i] = ty; p.tz[i] = tz;
            p.ux[i] = ux; p.uy[i] = uy; p.uz[i] = uz;
            p.rx[i] = uy * tz - uz * ty; p.ry[i] = uz * tx - ux * tz; p.rz[i] = ux * ty - uy * tx;
        }
    }

    /** A straight path along +Z through loft stations {z, half width, up, down[, y offset[, exponent]]}; k = station index. */
    private static Path straight(float[][] st)
    {
        Path p = new Path(st.length);
        for (int i = 0; i < p.n; i++)
        {
            p.z[i] = st[i][0];
            p.y[i] = st[i].length > 4 ? st[i][4] : 0;
            p.tz[i] = 1; p.uy[i] = 1; p.rx[i] = 1;
            p.k[i] = i;
        }
        return p;
    }

    // ---------------------------------------------------------------- surfaces

    private static int lightAt(int[] lights, float k, int fallback)
    {
        return lights == null ? fallback : lights[Mth.clamp(Math.round(k), 0, lights.length - 1)];
    }

    /**
     * A tube along the path. vPerJoint > 0 tiles the texture region that many times per joint (keep sub a divisor of it);
     * 0 stretches the region once over the whole path. lights (per joint) may be null.
     */
    void tube(Path p, int sides, Section shape, float[] uv, float vPerJoint, int[] lights, boolean capStart, boolean capEnd)
    {
        int m = sides + 1;
        float[] prev = new float[m * 6], cur = new float[m * 6];
        for (int i = 0; i < p.n; i++)
        {
            shape.at(p.k[i], sec);
            float w = Math.max(sec[0], 1.0E-3F), hu = Math.max(sec[1], 1.0E-3F), hd = Math.max(sec[2], 1.0E-3F), e = sec[3] <= 0 ? 1 : sec[3];
            for (int j = 0; j < m; j++)
            {
                float th = Mth.TWO_PI * (j % sides) / sides;
                float c = Mth.cos(th), s = Mth.sin(th);
                float pc = e == 1 ? c : Math.signum(c) * (float) Math.pow(Math.abs(c), e);
                float ps = e == 1 ? s : Math.signum(s) * (float) Math.pow(Math.abs(s), e);
                float h = s >= 0 ? hu : hd;
                float ox = w * pc, oy = h * ps;
                int o = j * 6;
                cur[o] = p.x[i] + p.rx[i] * ox + p.ux[i] * oy;
                cur[o + 1] = p.y[i] + p.ry[i] * ox + p.uy[i] * oy;
                cur[o + 2] = p.z[i] + p.rz[i] * ox + p.uz[i] * oy;
                float nr = c / w, nu = s / h;
                float nx = p.rx[i] * nr + p.ux[i] * nu, ny = p.ry[i] * nr + p.uy[i] * nu, nz = p.rz[i] * nr + p.uz[i] * nu;
                float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 1.0E-6F) nl = 1;
                cur[o + 3] = nx / nl; cur[o + 4] = ny / nl; cur[o + 5] = nz / nl;
            }
            if (i > 0)
            {
                float v0, v1;
                if (vPerJoint > 0)
                {
                    v0 = Mth.frac(p.k[i - 1] * vPerJoint);
                    if (v0 > 0.999F) v0 = 0;
                    v1 = Math.min(1.0F, v0 + (p.k[i] - p.k[i - 1]) * vPerJoint);
                }
                else
                {
                    v0 = (i - 1) / (float) (p.n - 1);
                    v1 = i / (float) (p.n - 1);
                }
                int l0 = lightAt(lights, p.k[i - 1], light), l1 = lightAt(lights, p.k[i], light);
                float tv0 = tv(uv, v0), tv1 = tv(uv, v1);
                for (int j = 0; j < sides; j++)
                {
                    int a = j * 6, b = a + 6;
                    float u0 = tu(uv, j / (float) sides), u1 = tu(uv, (j + 1) / (float) sides);
                    if (outward)
                    {
                        v(prev[a], prev[a + 1], prev[a + 2], u0, tv0, prev[a + 3], prev[a + 4], prev[a + 5], l0);
                        v(prev[b], prev[b + 1], prev[b + 2], u1, tv0, prev[b + 3], prev[b + 4], prev[b + 5], l0);
                        v(cur[b], cur[b + 1], cur[b + 2], u1, tv1, cur[b + 3], cur[b + 4], cur[b + 5], l1);
                        v(cur[a], cur[a + 1], cur[a + 2], u0, tv1, cur[a + 3], cur[a + 4], cur[a + 5], l1);
                        continue;
                    }
                    v(prev[a], prev[a + 1], prev[a + 2], u0, tv0, prev[a + 3], prev[a + 4], prev[a + 5], l0);
                    v(cur[a], cur[a + 1], cur[a + 2], u0, tv1, cur[a + 3], cur[a + 4], cur[a + 5], l1);
                    v(cur[b], cur[b + 1], cur[b + 2], u1, tv1, cur[b + 3], cur[b + 4], cur[b + 5], l1);
                    v(prev[b], prev[b + 1], prev[b + 2], u1, tv0, prev[b + 3], prev[b + 4], prev[b + 5], l0);
                }
            }
            if ((i == 0 && capStart) || (i == p.n - 1 && capEnd))
            {
                float sgn = i == 0 ? -1 : 1;
                int l = lightAt(lights, p.k[i], light);
                float cu = tu(uv, 0.5F), cv = tv(uv, i == 0 ? 0.0F : 1.0F);
                for (int j = 0; j < sides; j++)
                {
                    int a = j * 6, b = a + 6;
                    v(p.x[i], p.y[i], p.z[i], cu, cv, sgn * p.tx[i], sgn * p.ty[i], sgn * p.tz[i], l);
                    v(cur[a], cur[a + 1], cur[a + 2], cu, cv, sgn * p.tx[i], sgn * p.ty[i], sgn * p.tz[i], l);
                    v(cur[b], cur[b + 1], cur[b + 2], cu, cv, sgn * p.tx[i], sgn * p.ty[i], sgn * p.tz[i], l);
                    v(p.x[i], p.y[i], p.z[i], cu, cv, sgn * p.tx[i], sgn * p.ty[i], sgn * p.tz[i], l);
                }
            }
            float[] swap = prev;
            prev = cur;
            cur = swap;
        }
    }

    /** A loft along the pose's +Z: stations {z, half width, up, down[, y offset[, exponent]]}, the region stretched once along it. */
    void loft(float[][] st, int sides, float[] uv)
    {
        tube(straight(st), sides, (k, o) -> {
            float[] s = st[Mth.clamp(Math.round(k), 0, st.length - 1)];
            o[0] = s[1]; o[1] = s[2]; o[2] = s[3]; o[3] = s.length > 5 ? s[5] : 1;
        }, uv, 0, null, false, false);
    }

    /** Stations of an ellipsoid (length along Z from z0, half width w, half height h). */
    static float[][] ellipsoid(float z0, float length, float w, float h, int rings)
    {
        float[][] st = new float[rings + 1][];
        for (int i = 0; i <= rings; i++)
        {
            float a = Mth.PI * i / rings, r = Math.max(Mth.sin(a), 0.02F);
            st[i] = new float[]{z0 + length * 0.5F * (1 - Mth.cos(a)), w * r, h * r, h * r};
        }
        return st;
    }

    /**
     * A fin standing on the path: edge.at(k) = {offset of its root along up, height, sideways sway of the free edge}.
     * side = +1 on the back, -1 under the belly. The region runs along the fin (uPerJoint tiles per joint), v from root to edge.
     */
    void ribbon(Path p, Section edge, float side, float[] uv, float uPerJoint, int[] lights)
    {
        float px0 = 0, py0 = 0, pz0 = 0, px1 = 0, py1 = 0, pz1 = 0, ph = 0;
        for (int i = 0; i < p.n; i++)
        {
            edge.at(p.k[i], sec);
            float base = sec[0] * side, h = sec[1], tip = (sec[0] + sec[1]) * side, sway = sec[2];
            float x0 = p.x[i] + p.ux[i] * base, y0 = p.y[i] + p.uy[i] * base, z0 = p.z[i] + p.uz[i] * base;
            float x1 = p.x[i] + p.ux[i] * tip + p.rx[i] * sway, y1 = p.y[i] + p.uy[i] * tip + p.ry[i] * sway, z1 = p.z[i] + p.uz[i] * tip + p.rz[i] * sway;
            if (i > 0 && (h > 0.01F || ph > 0.01F))
            {
                float u0 = Mth.frac(p.k[i - 1] * uPerJoint);
                if (u0 > 0.999F) u0 = 0;
                float u1 = Math.min(1.0F, u0 + (p.k[i] - p.k[i - 1]) * uPerJoint);
                int l0 = lightAt(lights, p.k[i - 1], light), l1 = lightAt(lights, p.k[i], light);
                float nx = p.rx[i], ny = p.ry[i], nz = p.rz[i];
                v(px0, py0, pz0, tu(uv, u0), tv(uv, 0), nx, ny, nz, l0);
                v(px1, py1, pz1, tu(uv, u0), tv(uv, 1), nx, ny, nz, l0);
                v(x1, y1, z1, tu(uv, u1), tv(uv, 1), nx, ny, nz, l1);
                v(x0, y0, z0, tu(uv, u1), tv(uv, 0), nx, ny, nz, l1);
            }
            px0 = x0; py0 = y0; pz0 = z0; px1 = x1; py1 = y1; pz1 = z1; ph = h;
        }
    }

    /** A flat quad: corners (u0 v0), (u0 v1), (u1 v1), (u1 v0) of the region, 3 floats each. */
    void quad(float[] c, float[] uv)
    {
        float ax = c[3] - c[0], ay = c[4] - c[1], az = c[5] - c[2], bx = c[9] - c[0], by = c[10] - c[1], bz = c[11] - c[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1.0E-6F) return;
        nx /= nl; ny /= nl; nz /= nl;
        v(c[0], c[1], c[2], tu(uv, 0), tv(uv, 0), nx, ny, nz, light);
        v(c[3], c[4], c[5], tu(uv, 0), tv(uv, 1), nx, ny, nz, light);
        v(c[6], c[7], c[8], tu(uv, 1), tv(uv, 1), nx, ny, nz, light);
        v(c[9], c[10], c[11], tu(uv, 1), tv(uv, 0), nx, ny, nz, light);
    }

    /** A cone from a round base at (x, y, z) to its tip at base + (ax, ay, az): teeth, horns, bristles. v runs base to tip. */
    void spike(float x, float y, float z, float ax, float ay, float az, float radius, int sides, float[] uv)
    {
        float al = Mth.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1.0E-5F) return;
        float dx = ax / al, dy = ay / al, dz = az / al;
        float hx = Math.abs(dy) < 0.9F ? 0 : 1, hy = Math.abs(dy) < 0.9F ? 1 : 0;
        float e1x = dy * 0 - dz * hy, e1y = dz * hx - dx * 0, e1z = dx * hy - dy * hx;
        float l = Mth.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        e1x /= l; e1y /= l; e1z /= l;
        float e2x = dy * e1z - dz * e1y, e2y = dz * e1x - dx * e1z, e2z = dx * e1y - dy * e1x;
        float tx = x + ax, ty = y + ay, tz = z + az;
        float v0 = tv(uv, 0), v1 = tv(uv, 1);
        for (int j = 0; j < sides; j++)
        {
            float a0 = Mth.TWO_PI * j / sides, a1 = Mth.TWO_PI * (j + 1) / sides;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float n0x = e1x * c0 + e2x * s0, n0y = e1y * c0 + e2y * s0, n0z = e1z * c0 + e2z * s0;
            float n1x = e1x * c1 + e2x * s1, n1y = e1y * c1 + e2y * s1, n1z = e1z * c1 + e2z * s1;
            float u0 = tu(uv, j / (float) sides), u1 = tu(uv, (j + 1) / (float) sides);
            v(x + n0x * radius, y + n0y * radius, z + n0z * radius, u0, v0, n0x, n0y, n0z, light);
            v(tx, ty, tz, u0, v1, n0x, n0y, n0z, light);
            v(tx, ty, tz, u1, v1, n1x, n1y, n1z, light);
            v(x + n1x * radius, y + n1y * radius, z + n1z * radius, u1, v0, n1x, n1y, n1z, light);
        }
    }

    /** A camera-facing full-bright quad (for a consumer of a plain white texture): distant glow points and halos. */
    static void billboard(VertexConsumer vc, PoseStack.Pose last, Camera cam, float x, float y, float z, float size, float r, float g, float b, float a)
    {
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        for (int i = 0; i < 4; i++)
        {
            float sl = i < 2 ? 1 : -1, su = (i == 1 || i == 2) ? 1 : -1;
            vc.vertex(last.pose(), x + (left.x() * sl + up.x() * su) * size, y + (left.y() * sl + up.y() * su) * size, z + (left.z() * sl + up.z() * su) * size)
                    .color(r, g, b, a).uv(i < 2 ? 0 : 1, (i == 1 || i == 2) ? 1 : 0).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal(last.normal(), 0, 0, 1).endVertex();
        }
    }
}
