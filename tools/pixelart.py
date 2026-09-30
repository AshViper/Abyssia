"""Procedural 16x16 pixel-art primitives for Abyssia's textures (no vanilla images are used).

Every texture is deterministic: its random generator is seeded from the texture's name.
"""
import math
import zlib

import numpy as np
from PIL import Image

N = 16


def rng_for(name, salt=0):
    return np.random.default_rng(zlib.crc32(name.encode()) + salt)


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def palette(*hexes):
    """Colours from darkest to lightest."""
    return [hexrgb(h) for h in hexes]


# ---------------------------------------------------------------- noise

def value_noise(rng, cells):
    """Tileable smooth noise on the 16x16 grid, values 0..1."""
    g = rng.random((cells, cells))
    out = np.zeros((N, N))
    for y in range(N):
        for x in range(N):
            fx, fy = x * cells / N, y * cells / N
            x0, y0 = int(fx) % cells, int(fy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            top = g[y0, x0] * (1 - tx) + g[y0, x1] * tx
            bottom = g[y1, x0] * (1 - tx) + g[y1, x1] * tx
            out[y, x] = top * (1 - ty) + bottom * ty
    return out


def fbm(rng, octaves=((2, 0.5), (4, 0.3), (8, 0.2))):
    total = sum(w for _, w in octaves)
    return sum(value_noise(rng, c) * w for c, w in octaves) / total


def normalize(v):
    lo, hi = v.min(), v.max()
    return (v - lo) / (hi - lo + 1e-9)


# ---------------------------------------------------------------- canvas

class Canvas:
    def __init__(self, size=N):
        self.size = size
        self.rgba = np.zeros((size, size, 4), dtype=np.uint8)

    @staticmethod
    def from_values(values, pal):
        """Quantise 0..1 values onto a palette: the characteristic banded pixel-art shading."""
        c = Canvas()
        idx = np.clip((normalize(values) * len(pal)).astype(int), 0, len(pal) - 1)
        for y in range(N):
            for x in range(N):
                c.rgba[y, x] = (*pal[idx[y, x]], 255)
        return c

    def copy(self):
        c = Canvas(self.size)
        c.rgba = self.rgba.copy()
        return c

    def put(self, x, y, rgb, alpha=255):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.rgba[y, x] = (*rgb, alpha)

    def get(self, x, y):
        return tuple(self.rgba[y % self.size, x % self.size][:3])

    def alpha(self, x, y):
        if 0 <= x < self.size and 0 <= y < self.size:
            return self.rgba[y, x][3]
        return 0

    def save(self, path):
        Image.fromarray(self.rgba, "RGBA").save(path)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def lighten(c, t):
    return mix(c, (255, 255, 255), t)


def darken(c, t):
    return mix(c, (0, 0, 0), t)


# ---------------------------------------------------------------- terrain

def rock(name, pal, strata=0.0, cracks=0, speckle=6, holes=0, columns=False):
    """Rock: blotchy fbm, optional horizontal strata, cracks, vesicle holes and columnar joints."""
    rng = rng_for(name)
    v = fbm(rng)
    if strata:
        wobble = value_noise(rng, 2)
        period = int(rng.integers(4, 6))
        for y in range(N):
            for x in range(N):
                if int(y + wobble[y, x] * 3) % period == 0:
                    v[y, x] -= strata
                elif int(y + wobble[y, x] * 3) % period == 1:
                    v[y, x] += strata * 0.4
    if columns:
        for x in range(0, N, int(rng.integers(4, 6))):
            for y in range(N):
                v[y, (x + (y // 6)) % N] -= 0.25
    for _ in range(speckle):
        x, y = rng.integers(0, N, 2)
        v[y, x] += rng.choice([-0.35, 0.35])
    c = Canvas.from_values(v, pal)
    for _ in range(cracks):
        x, y = rng.integers(0, N, 2)
        for _ in range(int(rng.integers(4, 8))):
            c.put(x % N, y % N, pal[0])
            x += int(rng.integers(-1, 2))
            y += int(rng.integers(0, 2))
    for _ in range(holes):
        x, y = rng.integers(0, N, 2)
        c.put(x, y, darken(pal[0], 0.3))
        if rng.random() < 0.5:
            c.put((x + 1) % N, y, darken(pal[0], 0.15))
    return c


def sediment(name, pal, grain=0.55, pebbles=3):
    rng = rng_for(name)
    v = fbm(rng, ((4, 0.6), (8, 0.4))) * (1 - grain) + rng.random((N, N)) * grain
    c = Canvas.from_values(v, pal)
    for _ in range(pebbles):
        x, y = rng.integers(0, N, 2)
        c.put(x, y, pal[-1])
        c.put(x, (y + 1) % N, pal[1])
    return c


def mud(name, pal, blotches=5, sheen=4):
    rng = rng_for(name)
    v = fbm(rng, ((2, 0.5), (4, 0.5)))
    c = Canvas.from_values(v, pal)
    for _ in range(blotches):
        x, y = rng.integers(0, N, 2)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            if rng.random() < 0.8:
                c.put((x + dx) % N, (y + dy) % N, pal[0])
    for _ in range(sheen):
        x, y = rng.integers(0, N, 2)
        c.put(x, y, lighten(pal[-1], 0.15))
    return c


def veined(base, name, vein_pal, count=3, width=1):
    """Glowing or mineral veins meandering across a base texture."""
    rng = rng_for(name, 7)
    c = base.copy()
    for _ in range(count):
        x, y = rng.integers(0, N, 2)
        dx = rng.choice([-1, 1])
        for i in range(int(rng.integers(8, 16))):
            col = vein_pal[min(len(vein_pal) - 1, int(rng.integers(0, len(vein_pal))))]
            c.put(x % N, y % N, col)
            if width > 1:
                c.put((x + 1) % N, y % N, vein_pal[0])
            x += dx if rng.random() < 0.6 else 0
            y += 1 if rng.random() < 0.7 else 0
    return c


def flecks(base, name, pal, density=0.3, threshold=0.55):
    """Mineral crust: patches and flecks of mineral over a host texture."""
    rng = rng_for(name, 3)
    v = fbm(rng, ((4, 0.6), (8, 0.4)))
    c = base.copy()
    for y in range(N):
        for x in range(N):
            if v[y, x] > threshold or rng.random() < density * 0.3:
                c.put(x, y, pal[min(len(pal) - 1, int((v[y, x] + rng.random() * 0.3) * len(pal)))])
    return c


def ore(host, name, mineral_pal, blobs=4):
    """Host rock with crystalline mineral blobs, lit from the top-left."""
    rng = rng_for(name, 5)
    c = host.copy()
    placed = []
    attempts = 0
    while len(placed) < blobs and attempts < 60:
        attempts += 1
        x, y = rng.integers(1, N - 3, 2)
        if any(abs(x - px) < 4 and abs(y - py) < 4 for px, py in placed):
            continue
        placed.append((x, y))
        size = int(rng.integers(2, 4))
        for dy in range(size):
            for dx in range(size):
                if (dx, dy) in ((size - 1, size - 1),) and rng.random() < 0.5:
                    continue
                shade = 1 if dx + dy == 0 else (0 if dx + dy >= size else 2 if dx + dy == 1 else 1)
                shade = min(len(mineral_pal) - 1, shade + (1 if len(mineral_pal) > 3 else 0))
                c.put(x + dx, y + dy, mineral_pal[shade])
        c.put(x, y, mineral_pal[-1])
        c.put(x + size - 1, y + size - 1, mineral_pal[0])
    return c


def facets(name, pal, glints=4):
    """Crystal block: angular facets each shaded differently, with bright glints."""
    rng = rng_for(name)
    c = Canvas()
    lines = [(rng.random() * 2 - 1, rng.random() * 16) for _ in range(4)]
    for y in range(N):
        for x in range(N):
            region = sum(1 << i for i, (k, b) in enumerate(lines) if y > k * x + b - 4)
            shade = (region * 7 + (x + y) // 6) % (len(pal) - 1)
            c.put(x, y, pal[shade])
    for _ in range(glints):
        x, y = rng.integers(0, N, 2)
        c.put(x, y, pal[-1])
    return c


# ---------------------------------------------------------------- plants and small sprites

def blade(c, x, bottom, height, lean, pal, width=1, curl=0.0):
    """A tapering blade from (x, bottom) upward; colour runs dark at the base to light at the tip."""
    for i in range(height):
        t = i / max(1, height - 1)
        px = int(round(x + lean * t * t * height * 0.3 + curl * math.sin(t * math.pi)))
        py = bottom - i
        col = pal[min(len(pal) - 1, int(t * len(pal)))]
        c.put(px, py, col)
        if width > 1 and t < 0.7:
            c.put(px + 1, py, darken(col, 0.15))


def disc(c, cx, cy, r, pal, light_dir=(-1, -1), alpha=255):
    """A shaded ball lit from light_dir."""
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            if dx * dx + dy * dy > r * r:
                continue
            lit = -(dx * light_dir[0] + dy * light_dir[1]) / (r + 1e-9)
            idx = int((lit * 0.5 + 0.5) * len(pal))
            c.put(x, y, pal[max(0, min(len(pal) - 1, idx))], alpha)


def shard(c, base_x, base_y, height, width, lean, pal):
    """A crystal shard: pointed column, left face lit, right face shaded."""
    for i in range(height):
        t = i / max(1, height - 1)
        w = max(1, int(round(width * (1 - t * 0.85))))
        cx = base_x + lean * i
        for k in range(w):
            px = int(round(cx - w / 2 + k))
            col = pal[-2] if k < w / 2 else pal[1]
            if k == 0:
                col = pal[-1]
            if k == w - 1 and w > 1:
                col = pal[0]
            c.put(px, base_y - i, col)


def branch(c, x, y, length, angle, pal, rng, depth=0, tips=None):
    """Recursive branching (corals)."""
    for i in range(length):
        x += math.cos(angle)
        y -= math.sin(angle)
        c.put(int(round(x)), int(round(y)), pal[min(len(pal) - 1, depth + 1)])
    if tips is not None and depth >= 1:
        tips.append((int(round(x)), int(round(y))))
    if depth < 3 and length > 1:
        for turn in (-0.55, 0.5):
            branch(c, x, y, max(2, length - int(rng.integers(0, 3))), angle + turn + rng.random() * 0.2, pal, rng, depth + 1, tips)


def glow_mask(c, points, rgb):
    """Separate emissive layer: only the given pixels, drawn onto both the base and the glow overlay."""
    g = Canvas()
    for x, y in points:
        if 0 <= x < N and 0 <= y < N:
            c.put(x, y, rgb)
            g.put(x, y, rgb)
    return g


def item_outline(c, outline):
    """Dark 1px outline around opaque pixels, like vanilla item sprites."""
    src = c.rgba.copy()
    for y in range(N):
        for x in range(N):
            if src[y, x][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < N and 0 <= ny < N and src[ny, nx][3]:
                    c.put(x, y, outline)
                    break
