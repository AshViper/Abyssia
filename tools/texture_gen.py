"""Style-matched texture generator: new 16x16 candidates that look like the approved (ChatGPT-made) texture set.

For textures ChatGPT art does not exist for yet (new blocks/items, recolours, tier variants, missing textures).  It
learns palettes / luminance / noise scale / ore-grain statistics from the textures in src/main/resources (the approved
set, see tools/texture_locks) and draws new ones from them.  Everything is pixel-clean: no anti-aliasing, <= 16
colours, blocks opaque (and tileable where the generator says so), sprite alpha 0/255 with the base's 1px outline.

Output policy: candidates go to inbox/generated/<batch>/<name>.png + contact.png (contact sheet) + manifest.json
(params, seed, sources).  Nothing is written under src/main/resources until ``apply``; apply follows the
texture_locks policy (locked files are refused without --force-locked, derived textures need --lock so
derive_textures.py keeps them), backs overwritten files up to inbox/backup/texgen-<ts>/, can --lock, re-derives the
glow / brick / polished textures of the bases it changed and runs check_textures.  ``undo`` restores the last apply.

Texture refs: ``block/deep_sea_rock``, ``item/cobalt_ingot``, ``models/armor/abyssal_alloy_layer_1``, a bare name
(block, then item), or ``gen:<batch>/<name>`` (a generated candidate).  Palettes: a texture ref (its colour ramp),
``metal:<name>`` (METALS), ``ingot:<metal>`` (item/<metal>_ingot), or hex ramp ``#1a1a2e,#3b4a6b,...`` (dark first).

    python tools/texture_gen.py profile --category rocks                      # learn + cache inbox/generated/profiles/rocks.json
    python tools/texture_gen.py recolor block/deep_sea_rock --palette block/cobalt_crust --batch demo
    python tools/texture_gen.py recolor block/deep_sea_rock --hue 40 --sat 1.2 --id warm_sea_rock
    python tools/texture_gen.py variants block/trench_rock --types polished,bricks,mossy,frosted,scorched,bricks+mossy
    python tools/texture_gen.py ore --host block/deep_sea_rock --palette metal:silver --pattern nuggets --id silver_ore --count 4
    python tools/texture_gen.py synth --profile rocks --palette block/volcanic_rock --cracks 2 --count 6 --id ash_rock
    python tools/texture_gen.py sprite block/cave_fern --palette block/amber_crystal_block --mirror --sway 1
    python tools/texture_gen.py tier item/abyssal_alloy_pickaxe --palettes ingot:platinum,metal:orichalcum
    python tools/texture_gen.py blend block/deep_sea_rock block/abyssal_moss --mask vertical --amount 0.4
    python tools/texture_gen.py batch inbox/generated/example-batch.json           # jobs from a JSON spec
    python tools/texture_gen.py list-candidates [--batch demo]
    python tools/texture_gen.py apply demo/silver_ore [demo/x --as block/y] [--lock] [--force-locked] [--dry-run]
    python tools/texture_gen.py undo [--dry-run]

Every generator subcommand takes --seed N / --count N / --seeds 1,5,9, --batch NAME, --id ID, --dry-run (generate
and report, write nothing).  Output is JSON on stdout.  Same seed + params -> same pixels.  The Agent Flow GUI
("テクスチャ生成" tab, tools/agentflow/server.py /api/texgen/*) calls the same functions.
"""
from __future__ import annotations

import argparse
import base64
import fnmatch
import hashlib
import io
import json
import re
import shutil
import sys
import threading
import time
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import texture_locks  # noqa: E402
import derive_textures  # noqa: E402


class TexGenError(ValueError):
    """Bad input (unknown texture, invalid name/param, refused write); message is shown to the user."""


# ================================================================ paths (configure() points everything at a temp copy in the tests)

class _Paths:
    def __init__(self, root: Path, assets: Path | None = None, locks: Path | None = None,
                 generated: Path | None = None, backup: Path | None = None):
        self.root = Path(root)
        self.assets = Path(assets) if assets else Path(texture_locks.ASSETS).resolve()
        self.locks = Path(locks) if locks else Path(texture_locks.LOCKS).resolve()
        self.generated = Path(generated) if generated else self.root / "inbox" / "generated"
        self.backup = Path(backup) if backup else self.root / "inbox" / "backup"

    @property
    def tex(self) -> Path:
        return self.assets / "textures"

    @property
    def real(self) -> bool:
        return self.assets == Path(texture_locks.ASSETS).resolve()


P = _Paths(HERE.parent)
WRITE_LOCK = threading.Lock()


def configure(root=None, assets=None, locks=None, generated=None, backup=None) -> None:
    """Redirect assets / locks / inbox folders (tests use a temp copy; never the real textures)."""
    global P
    P = _Paths(Path(root) if root else HERE.parent, assets, locks, generated, backup)
    _CACHE.clear()


# ================================================================ names / refs

NAME_RE = re.compile(r"^[a-z0-9_]{1,80}$")
BATCH_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
CAND_RE = re.compile(r"^[a-z0-9_]{1,80}(?:__s\d{1,9})?$")
DIRS = ("block", "item", "entity", "particle", "models/armor")
REF_RE = re.compile(r"^(?:(block|item|entity|particle|models/armor)/)?([a-z0-9_]{1,80})$")
GEN_RE = re.compile(r"^gen:([A-Za-z0-9_-]{1,64})/([a-z0-9_]{1,80}(?:__s\d{1,9})?)$")
DEST_RE = re.compile(r"^(block|item|models/armor)/([a-z0-9_]{1,80})$")
MAX_SIDE = 64


def check_name(s, what="id") -> str:
    if not isinstance(s, str) or not NAME_RE.match(s):
        raise TexGenError(f"bad {what} {s!r} (a-z 0-9 _ only)")
    return s


def check_batch(s) -> str:
    if not isinstance(s, str) or not BATCH_RE.match(s) or s == "profiles":
        raise TexGenError(f"bad batch name {s!r} (A-Z a-z 0-9 _ - only)")
    return s


def resolve(ref: str) -> tuple[str, Path]:
    """Texture ref -> (canonical ref, file).  Raises TexGenError for bad / missing refs (no path traversal)."""
    if not isinstance(ref, str):
        raise TexGenError(f"bad texture ref {ref!r}")
    ref = ref.strip().removesuffix(".png").removeprefix("abyssia:").removeprefix("textures/")
    g = GEN_RE.match(ref)
    if g:
        f = (P.generated / g.group(1) / f"{g.group(2)}.png")
        if not f.is_file():
            raise TexGenError(f"no generated candidate {ref}")
        return ref, f
    m = REF_RE.match(ref)
    if not m:
        raise TexGenError(f"bad texture ref {ref!r} (use block/<id>, item/<id>, <id> or gen:<batch>/<name>)")
    for d in ([m.group(1)] if m.group(1) else ("block", "item")):
        f = P.tex / d / f"{m.group(2)}.png"
        if f.is_file():
            return f"{d}/{m.group(2)}", f
    raise TexGenError(f"no texture {ref}")


_CACHE: dict = {}


def load(ref: str) -> np.ndarray:
    """RGBA uint8 of a texture (first frame of an animation strip)."""
    ref, f = resolve(ref)
    key = (str(f), f.stat().st_mtime_ns)
    if key not in _CACHE:
        with Image.open(f) as im:
            a = np.asarray(im.convert("RGBA")).copy()
        h, w = a.shape[:2]
        if h > w and h % w == 0:
            a = a[:w]
        if max(a.shape[:2]) > MAX_SIDE:
            raise TexGenError(f"{ref}: larger than {MAX_SIDE}px")
        _CACHE[key] = a
    return _CACHE[key].copy()


def ref_name(ref: str) -> str:
    return re.split(r"[/:]", ref)[-1].split("__s")[0]


# ================================================================ colour (OKLab: perceptual ramps, hue shifts)

_M1 = np.array([[0.4122214708, 0.5363325363, 0.0514459929], [0.2119034982, 0.6806995451, 0.1073969566],
                [0.0883024619, 0.2817188376, 0.6299787005]])
_M2 = np.array([[0.2104542553, 0.7936177850, -0.0040720468], [1.9779984951, -2.4285922050, 0.4505937099],
                [0.0259040371, 0.7827717662, -0.8086757660]])
_M1i, _M2i = np.linalg.inv(_M1), np.linalg.inv(_M2)


def oklab(rgb) -> np.ndarray:
    c = np.asarray(rgb, float) / 255.0
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return np.cbrt(lin @ _M1.T) @ _M2.T


def rgb_of(lab) -> np.ndarray:
    lin = ((np.asarray(lab, float) @ _M2i.T) ** 3) @ _M1i.T
    lin = np.clip(lin, 0, 1)
    c = np.where(lin <= 0.0031308, 12.92 * lin, 1.055 * np.power(lin, 1 / 2.4) - 0.055)
    return np.clip(np.round(c * 255), 0, 255).astype(np.uint8)


def hexrgb(h: str) -> tuple[int, int, int]:
    h = h.strip().lstrip("#")
    if not re.fullmatch(r"[0-9a-fA-F]{6}", h):
        raise TexGenError(f"bad hex colour {h!r}")
    return int(h[:2], 16), int(h[2:4], 16), int(h[4:], 16)


def tohex(rgb) -> str:
    return "#%02x%02x%02x" % tuple(int(v) for v in rgb)


# ================================================================ palettes / ramps

# New metal ramps, dark -> light, drawn to sit next to the approved ingots (dark cool outline end, pale highlight).
METALS = {
    "silver": ["#1e2029", "#3a3f4d", "#626a7c", "#929bab", "#c3cad5", "#eef2f6"],
    "gold": ["#2e1c0e", "#5e3d12", "#9a6a1c", "#d09a2c", "#efc955", "#fff0a0"],
    "orichalcum": ["#2b1210", "#662a18", "#a4501f", "#d9812f", "#f2b458", "#fde29a"],
    "zinc": ["#1c2427", "#394a4c", "#5f7676", "#8ca3a0", "#b9cdc7", "#e3efe9"],
    "chromium": ["#141b24", "#2c3c4e", "#4f6a82", "#7f9db4", "#b6cddd", "#e9f5fb"],
    "titanium": ["#1b1d27", "#373b4d", "#5c6178", "#8a8fa6", "#b9bdd0", "#e4e6f0"],
    "lithium": ["#2a1629", "#57304f", "#87567c", "#b884a8", "#dfb7d2", "#f8e6f2"],
    "iridium": ["#12162a", "#262f55", "#434f82", "#6e7bab", "#a3aed0", "#dbe1f2"],
    "rhodium": ["#1d1520", "#3d2c45", "#63506c", "#907b95", "#bfaec1", "#ece2ec"],
    "palladium": ["#221f1a", "#45403a", "#6e6860", "#9c958a", "#c9c2b5", "#f1ece2"],
    "bismuth": ["#1d1633", "#3b2e6b", "#5a53a3", "#7c8fca", "#a9c9df", "#e4f1f0"],
    "abyssium": ["#0b1528", "#16305a", "#1f5690", "#2f86bc", "#62c0de", "#bff0fa"],
    "bronze": ["#24130c", "#4d2a14", "#7e4d22", "#ad7835", "#d4a55a", "#f0d493"],
    "lead": ["#16171f", "#2c2e3d", "#474a5e", "#666a80", "#8d91a5", "#bdc0cf"],
}


class Ramp:
    """Colours sorted dark -> light (OKLab) with weights; ``at(t)`` maps a rank 0..1 onto it."""

    def __init__(self, lab, weights=None, name: str = ""):
        lab = np.asarray(lab, float).reshape(-1, 3)
        w = np.ones(len(lab)) if weights is None else np.asarray(weights, float)
        order = np.argsort(lab[:, 0], kind="stable")
        self.lab, self.w, self.name = lab[order], w[order] / w.sum(), name
        edges = np.concatenate([[0], np.cumsum(self.w)])
        self.centres = (edges[:-1] + edges[1:]) / 2
        self.edges = edges

    def __len__(self):
        return len(self.lab)

    def at(self, t, smooth: bool = True) -> np.ndarray:
        t = np.clip(np.asarray(t, float), 0, 1)
        if len(self.lab) == 1:
            return np.broadcast_to(self.lab[0], t.shape + (3,)).copy()
        if not smooth:
            idx = np.clip(np.searchsorted(self.edges, t, side="right") - 1, 0, len(self.lab) - 1)
            return self.lab[idx]
        return np.stack([np.interp(t, self.centres, self.lab[:, k]) for k in range(3)], -1)

    def hex(self) -> list[str]:
        return [tohex(c) for c in rgb_of(self.lab)]

    def sub(self, lo: float, hi: float) -> "Ramp":
        """Part of the ramp between ranks lo..hi (e.g. the bright end for veins)."""
        keep = (self.centres >= lo) & (self.centres <= hi)
        if not keep.any():
            keep[np.argmin(np.abs(self.centres - (lo + hi) / 2))] = True
        return Ramp(self.lab[keep], self.w[keep], self.name)


def kmeans_lab(lab: np.ndarray, weights: np.ndarray, k: int, iters: int = 25) -> tuple[np.ndarray, np.ndarray]:
    """Deterministic weighted k-means (init at luminance quantiles)."""
    k = max(1, min(k, len(lab)))
    order = np.argsort(lab[:, 0], kind="stable")
    cw = np.cumsum(weights[order]) / weights.sum()
    cent = lab[order[np.searchsorted(cw, (np.arange(k) + 0.5) / k).clip(0, len(lab) - 1)]].copy()
    scale = np.array([1.0, 1.6, 1.6])
    for _ in range(iters):
        d = (((lab[:, None] - cent[None]) * scale) ** 2).sum(-1)
        lab_i = d.argmin(1)
        new = cent.copy()
        for j in range(k):
            m = lab_i == j
            if m.any():
                new[j] = (lab[m] * weights[m, None]).sum(0) / weights[m].sum()
        if np.allclose(new, cent):
            break
        cent = new
    wts = np.array([weights[lab_i == j].sum() for j in range(k)])
    keep = wts > 0
    return cent[keep], wts[keep]


def ramp_of(a: np.ndarray, k: int = 8, mask=None, name: str = "") -> Ramp:
    vis = a[..., 3] > 127 if mask is None else mask & (a[..., 3] > 127)
    px = a[..., :3][vis].reshape(-1, 3)
    if not len(px):
        raise TexGenError(f"{name or 'texture'}: no visible pixels")
    u, c = np.unique(px, axis=0, return_counts=True)
    cent, w = kmeans_lab(oklab(u), c.astype(float), k)
    return Ramp(cent, w, name)


def parse_palette(spec, k: int = 8) -> tuple[Ramp, bool, list[str]]:
    """Palette spec -> (ramp, smooth-by-default, source refs)."""
    if isinstance(spec, list):
        return Ramp(oklab(np.array([hexrgb(h) for h in spec])), name="hex"), True, []
    if not isinstance(spec, str) or not spec.strip():
        raise TexGenError("palette required (texture ref, metal:<name>, ingot:<metal> or #hex,#hex,...)")
    s = spec.strip()
    if s.startswith("#") or ("," in s and all(re.fullmatch(r"#?[0-9a-fA-F]{6}", x.strip()) for x in s.split(","))):
        cols = [hexrgb(x) for x in s.split(",") if x.strip()]
        if len(cols) < 2:
            raise TexGenError("hex ramp needs at least 2 colours")
        return Ramp(oklab(np.array(cols)), name="hex"), True, []
    if s.startswith("metal:"):
        m = s[6:]
        if m not in METALS:
            raise TexGenError(f"unknown metal {m!r} (known: {', '.join(sorted(METALS))})")
        return Ramp(oklab(np.array([hexrgb(h) for h in METALS[m]])), name=m), True, []
    if s.startswith("ingot:"):
        m = check_name(s[6:], "metal")
        ref = f"item/{m}_ingot"
        return ramp_of(load(ref), k, name=m), True, [ref]
    ref = resolve(s)[0]
    return ramp_of(load(ref), k, name=ref_name(ref)), True, [ref]


# ================================================================ pixel helpers

def lum_l(a: np.ndarray) -> np.ndarray:
    return oklab(a[..., :3])[..., 0]


def rank(values: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """Rank 0..1 (by value, ties share a rank) of every masked pixel; equal colours get equal ranks."""
    t = np.zeros(values.shape)
    v = values[mask]
    if not len(v):
        return t
    u, inv, cnt = np.unique(np.round(v, 6), return_inverse=True, return_counts=True)
    cum = np.cumsum(cnt)
    mid = (cum - cnt / 2) / cum[-1]
    t[mask] = mid[inv.reshape(-1)]
    return t


def nbrs(m: np.ndarray, wrap: bool = True):
    """(up, down, left, right) neighbour arrays."""
    if wrap:
        return np.roll(m, 1, 0), np.roll(m, -1, 0), np.roll(m, 1, 1), np.roll(m, -1, 1)
    pad = np.pad(m, 1, constant_values=0 if m.dtype != bool else False)
    return pad[:-2, 1:-1], pad[2:, 1:-1], pad[1:-1, :-2], pad[1:-1, 2:]


def limit_colours(a: np.ndarray, n: int = 16) -> np.ndarray:
    """Merge the closest (count-weighted, OKLab) colours until at most n remain; kept colours are real ones."""
    vis = a[..., 3] > 0
    px = a[..., :3][vis]
    u, inv, cnt = np.unique(px, axis=0, return_inverse=True, return_counts=True)
    if len(u) <= n:
        return a
    lab = oklab(u)
    alive = list(range(len(u)))
    target = list(range(len(u)))
    cnt = cnt.astype(float)
    while len(alive) > n:
        L = lab[alive]
        d = ((L[:, None] - L[None]) ** 2).sum(-1)
        np.fill_diagonal(d, np.inf)
        d *= np.minimum.outer(cnt[alive], cnt[alive]) ** 0.5
        i, j = np.unravel_index(np.argmin(d), d.shape)
        keep, drop = (alive[i], alive[j]) if cnt[alive[i]] >= cnt[alive[j]] else (alive[j], alive[i])
        cnt[keep] += cnt[drop]
        for x in range(len(target)):
            if target[x] == drop:
                target[x] = keep
        alive.remove(drop)
    out = a.copy()
    out[..., :3][vis] = u[np.array(target)[inv.reshape(-1)]]
    return out


def has_outline(a: np.ndarray) -> bool:
    """Sprite with a dark 1px rim: edge pixels clearly darker than the interior."""
    vis = a[..., 3] > 127
    if vis.all() or not vis.any():
        return False
    up, dn, lf, rt = nbrs(vis, wrap=False)
    edge = vis & ~(up & dn & lf & rt)
    inner = vis & ~edge
    if edge.sum() < 4 or inner.sum() < 4:
        return False
    L = lum_l(a)
    return float(np.median(L[edge])) < float(np.median(L[inner])) - 0.08


def finish(a: np.ndarray, kind: str, outline_from: np.ndarray | None = None, max_colours: int = 16) -> np.ndarray:
    """Hard alpha (blocks opaque), <= max_colours, optional dark rim restored like the base's."""
    out = np.array(a, np.uint8, copy=True)
    if kind == "block":
        out[..., 3] = 255
    else:
        out[..., 3] = np.where(out[..., 3] >= 128, 255, 0)
        out[..., :3][out[..., 3] == 0] = 0
        if outline_from is not None and has_outline(outline_from):
            vis = out[..., 3] > 0
            up, dn, lf, rt = nbrs(vis, wrap=False)
            edge = vis & ~(up & dn & lf & rt)
            lab = oklab(out[..., :3])
            L = lab[..., 0]
            dark_l = float(np.quantile(lum_l(outline_from)[outline_from[..., 3] > 127], 0.08))
            fix = edge & (L > dark_l + 0.12)
            lab[..., 0] = np.where(fix, np.minimum(L, dark_l + 0.04), L)
            lab[..., 1:] = np.where(fix[..., None], lab[..., 1:] * 0.8, lab[..., 1:])
            out[..., :3] = np.where(fix[..., None], rgb_of(lab), out[..., :3])
    return limit_colours(out, max_colours)


def seam_ratio(a: np.ndarray) -> float:
    """Tileability: colour step across the wrap seams / typical neighbour step inside (about 1 = seamless)."""
    lab = oklab(a[..., :3])
    inner = np.concatenate([np.abs(np.diff(lab, axis=0)).sum(-1).ravel(), np.abs(np.diff(lab, axis=1)).sum(-1).ravel()])
    seam = np.concatenate([np.abs(lab[0] - lab[-1]).sum(-1), np.abs(lab[:, 0] - lab[:, -1]).sum(-1)])
    return float(seam.mean() / (inner.mean() + 1e-9))


def rng_for(seed: int, *salt) -> np.random.Generator:
    return np.random.default_rng([int(seed) & 0xFFFFFFFF, zlib.crc32(repr(salt).encode())])


def spectrum(L: np.ndarray) -> np.ndarray:
    A = np.abs(np.fft.fft2(L - L.mean()))
    A[0, 0] = 0
    return A / (np.sqrt((A ** 2).sum()) + 1e-9)


def iso_spectrum(scale: float, n: int = 16) -> np.ndarray:
    f = np.fft.fftfreq(n) * n
    r = np.hypot(*np.meshgrid(f, f, indexing="ij"))
    peak = n / max(scale, 1.0) / 2
    A = np.exp(-((r - peak) ** 2) / (2 * max(peak, 1.0) ** 2)) * (r > 0)
    return A / np.sqrt((A ** 2).sum())


def spectral_noise(A: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """Tileable Gaussian noise with amplitude spectrum A (random phases)."""
    n = np.real(np.fft.ifft2(A * np.exp(1j * rng.uniform(0, 2 * np.pi, A.shape))))
    return (n - n.mean()) / (n.std() + 1e-9)


def noise_scale(L: np.ndarray) -> float:
    """Autocorrelation half-width in pixels (circular)."""
    F = np.fft.fft2(L - L.mean())
    ac = np.real(np.fft.ifft2(np.abs(F) ** 2))
    if ac[0, 0] <= 0:
        return 0.0
    ac /= ac[0, 0]
    y, x = np.mgrid[0:L.shape[0], 0:L.shape[1]]
    r = np.hypot(np.minimum(y, L.shape[0] - y), np.minimum(x, L.shape[1] - x))
    prev = 1.0
    for d in range(1, 9):
        v = ac[(r >= d - 0.5) & (r < d + 0.5)].mean()
        if v < 0.5:
            return round(d - 1 + (prev - 0.5) / max(prev - v, 1e-9), 2)
        prev = v
    return 8.0


def walk(rng, length: int, start=None, diag: float = 0.6, n: int = 16) -> list[tuple[int, int]]:
    """Tileable 4-connected random walk with a preferred diagonal direction."""
    y, x = start if start is not None else (int(rng.integers(n)), int(rng.integers(n)))
    dy, dx = (1 if rng.random() < 0.5 else -1), (1 if rng.random() < 0.5 else -1)
    pts = [(y % n, x % n)]
    for _ in range(length):
        r = rng.random()
        if r < diag / 2:
            y += dy
        elif r < diag:
            x += dx
        elif r < diag + (1 - diag) / 2:
            y += dy if rng.random() < 0.5 else 0
            x += 0 if rng.random() < 0.5 else dx
        else:
            y += dy
            x += dx
            pts.append(((y - dy) % n, x % n))
        pts.append((y % n, x % n))
    return pts


def clean_isolated(level: np.ndarray, rng, prob: float = 0.7) -> np.ndarray:
    """Single pixels unlike all 4 neighbours (which agree) take the neighbours' level."""
    up, dn, lf, rt = nbrs(level)
    same = (up == dn) & (dn == lf) & (lf == rt) & (level != up) & (np.abs(level - up) >= 2)
    same &= rng.random(level.shape) < prob
    return np.where(same, up, level)


def png_bytes(a: np.ndarray) -> bytes:
    buf = io.BytesIO()
    Image.fromarray(np.asarray(a, np.uint8), "RGBA").save(buf, "PNG", optimize=True)
    return buf.getvalue()


def b64(a: np.ndarray) -> str:
    return base64.b64encode(png_bytes(a)).decode()


# ================================================================ categories / profiles

_PLANT_WORDS = ("kelp", "fern", "bloom", "plant", "grass", "vine", "mushroom", "root", "weed", "frond", "stalk",
                "strandweed", "coral", "sponge", "tube", "gourd", "fan", "moss_carpet", "reed")
CATEGORIES = {
    "rocks": ("block/*rock*.png", r"^(polished_|chiseled_|cracked_)|_bricks$|_glow$|stalactite|_ore$"),
    "ores": ("block/*_ore.png", r"_glow$"),
    "crusts": ("block/*_crust.png", r"^polished_"),
    "crystals": ("block/*crystal_block.png", r"_glow$"),
    "sediments": ("block/*.png", r"_glow$|^(?!.*(sediment|silt|mud|ash|gravel|pebbles|rubble)).*$"),
    "plants": ("block/*.png", r"_glow$|stalactite|cluster|shards|bud|crystal_block"),
    "clusters": ("block/*.png", r"_glow$|^(?!.*(cluster|shards|bud|needle)).*$"),
    "ingots": ("item/*_ingot.png", r"^$^"),
    "tools": ("item/*.png", r"^(?!.*_(pickaxe|axe|shovel|sword|hoe)$).*$"),
    "raw": ("item/raw_*.png", r"^$^"),
    "powders": ("item/*_powder.png", r"^$^"),
    "armor-layers": ("models/armor/*.png", r"^$^"),
}


def category_refs(cat: str) -> list[str]:
    if cat not in CATEGORIES:
        raise TexGenError(f"unknown category {cat!r} (known: {', '.join(CATEGORIES)})")
    pattern, excl = CATEGORIES[cat]
    d, _, glob_pat = pattern.rpartition("/")
    out = []
    for f in sorted((P.tex / d).glob(glob_pat)):
        stem = f.stem
        if re.search(excl, stem):
            continue
        if cat == "plants":
            if not any(w in stem for w in _PLANT_WORDS):
                continue
            with Image.open(f) as im:
                if np.asarray(im.convert("RGBA"))[..., 3].min() > 0:
                    continue
        out.append(f"{d}/{stem}")
    return out


def select_refs(category=None, ids=None, pattern=None) -> list[str]:
    refs = []
    if category:
        refs += category_refs(category)
    for i in ids or []:
        refs.append(resolve(i)[0])
    if pattern:
        pat = pattern.removeprefix("textures/")
        if not re.fullmatch(r"[a-z0-9_*?/\[\]]{1,80}(\.png)?", pat) or ".." in pat:
            raise TexGenError(f"bad glob {pattern!r}")
        pat = pat if pat.endswith(".png") else pat + ".png"
        for d in DIRS:
            for f in sorted((P.tex / d).glob("*.png")):
                if fnmatch.fnmatch(f"{d}/{f.name}", pat):
                    refs.append(f"{d}/{f.stem}")
    seen, out = set(), []
    for r in refs:
        if r not in seen:
            seen.add(r)
            out.append(r)
    if not out:
        raise TexGenError("no textures selected (give --category, --ids or --glob)")
    return out


def ore_split(a: np.ndarray) -> np.ndarray:
    """Grain mask of an ore texture: 2-means on (L, chroma) seeded at the dullest-dark and the brightest-chromatic colour."""
    lab = oklab(a[..., :3]).reshape(-1, 3)
    C = np.hypot(lab[:, 1], lab[:, 2])
    score = C * 3 + lab[:, 0]
    feat = np.stack([lab[:, 0], lab[:, 1] * 2, lab[:, 2] * 2], -1)
    c0, c1 = feat[np.argmin(score)], feat[np.argmax(score)]
    for _ in range(20):
        m = ((feat - c1) ** 2).sum(1) < ((feat - c0) ** 2).sum(1)
        if m.all() or not m.any():
            break
        n0, n1 = feat[~m].mean(0), feat[m].mean(0)
        if np.allclose(n0, c0) and np.allclose(n1, c1):
            break
        c0, c1 = n0, n1
    return m.reshape(a.shape[:2])


def blobs(mask: np.ndarray) -> list[np.ndarray]:
    """4-connected components with wrap-around; list of (k,2) pixel arrays."""
    seen = np.zeros_like(mask, bool)
    out = []
    H, W = mask.shape
    for y0, x0 in np.argwhere(mask):
        if seen[y0, x0]:
            continue
        stack, pts = [(y0, x0)], []
        seen[y0, x0] = True
        while stack:
            y, x = stack.pop()
            pts.append((y, x))
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                v, u = (y + dy) % H, (x + dx) % W
                if mask[v, u] and not seen[v, u]:
                    seen[v, u] = True
                    stack.append((v, u))
        out.append(np.array(pts))
    return out


def _elong(pts: np.ndarray) -> float:
    if len(pts) < 3:
        return 1.0
    ev = np.linalg.eigvalsh(np.cov(pts.T.astype(float)) + np.eye(2) * 0.08)
    return float(np.sqrt(ev[1] / ev[0]))


def ore_stats(a: np.ndarray) -> dict:
    m = ore_split(a)
    bl = [b for b in blobs(m) if len(b) >= 2]
    areas = [len(b) for b in bl] or [0]
    el = [_elong(b) for b in bl] or [1.0]
    big = max(areas)
    pattern = "veins" if big >= 28 or float(np.mean(el)) > 2.2 else ("crystals" if float(np.mean(el)) > 1.6 else "nuggets")
    return {"coverage": round(float(m.mean()), 3), "blobs": len(bl), "mean_area": round(float(np.mean(areas)), 2),
            "max_area": int(big), "elongation": round(float(np.mean(el)), 2), "pattern": pattern}


def source_stats(ref: str) -> dict:
    a = load(ref)
    vis = a[..., 3] > 127
    L = lum_l(a)
    st = {"ref": ref, "size": list(a.shape[:2]), "ramp": ramp_of(a, 8, name=ref).hex(),
          "colours": int(len(np.unique(a[..., :3][vis], axis=0))), "coverage": round(float(vis.mean()), 3)}
    if vis.all():
        st["noise_scale"] = noise_scale(L)
        st["seam"] = round(seam_ratio(a), 2)
        st["dark_frac"] = round(float((L < np.quantile(L, 0.5) - 0.12).mean()), 3)
    else:
        ys, xs = np.nonzero(vis)
        st["bbox"] = [int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())]
        st["outline"] = has_outline(a)
        up, dn, lf, rt = nbrs(vis, wrap=False)
        edge = vis & ~(up & dn & lf & rt)
        st["edge_darkness"] = round(float(np.median(L[vis]) - np.median(L[edge])), 3) if edge.any() else 0.0
    if ref.endswith("_ore") and vis.all():
        st["ore"] = ore_stats(a)
    return st


def _profile_hash(refs: list[str]) -> str:
    h = hashlib.md5()
    for r in refs:
        h.update(r.encode())
        h.update(resolve(r)[1].read_bytes())
    return h.hexdigest()


def profile(name: str | None = None, category=None, ids=None, pattern=None, write: bool = True,
            refresh: bool = False) -> dict:
    """Learn a style profile (palette ramp, luminance histogram, edge darkness, noise scale, alpha, tileability)."""
    refs = select_refs(category, ids, pattern)
    name = check_name((name or category or "custom").replace("-", "_"), "profile name")
    fpath = P.generated / "profiles" / f"{name}.json"
    digest = _profile_hash(refs)
    if not refresh and fpath.is_file():
        try:
            old = json.loads(fpath.read_text(encoding="utf-8"))
            if old.get("hash") == digest:
                return old
        except (OSError, ValueError):
            pass
    srcs = [source_stats(r) for r in refs]
    arrs = [load(r) for r in refs]
    allpx = np.concatenate([a[a[..., 3] > 127][:, :3] for a in arrs])
    u, c = np.unique(allpx, axis=0, return_counts=True)
    cent, w = kmeans_lab(oklab(u), c.astype(float), 8)
    Ls = np.concatenate([lum_l(a)[a[..., 3] > 127] for a in arrs])
    agg = {"count": len(refs), "ramp": Ramp(cent, w).hex(),
           "lum_quantiles": [round(float(q), 4) for q in np.quantile(Ls, np.linspace(0, 1, 17))],
           "lum_hist": [int(v) for v in np.histogram(Ls, 16, (0, 1))[0]],
           "colours_mean": round(float(np.mean([s["colours"] for s in srcs])), 1)}
    blocks = [(s, a) for s, a in zip(srcs, arrs) if "seam" in s and a.shape[:2] == (16, 16)]
    if blocks:
        agg["noise_scale"] = round(float(np.mean([s["noise_scale"] for s, _ in blocks])), 2)
        agg["tileability"] = round(float(np.mean([s["seam"] for s, _ in blocks])), 2)
        agg["dark_frac"] = round(float(np.mean([s["dark_frac"] for s, _ in blocks])), 3)
        agg["spectrum"] = np.round(np.mean([spectrum(lum_l(a)) for _, a in blocks], 0), 4).tolist()
    sprites = [s for s in srcs if "bbox" in s]
    if sprites:
        agg["alpha"] = {"coverage": round(float(np.mean([s["coverage"] for s in sprites])), 3),
                        "bbox_mean": [round(float(v), 1) for v in np.mean([s["bbox"] for s in sprites], 0)],
                        "bottom_anchored": round(float(np.mean([s["bbox"][3] == 15 for s in sprites])), 2),
                        "outline_frac": round(float(np.mean([s["outline"] for s in sprites])), 2),
                        "edge_darkness": round(float(np.mean([s["edge_darkness"] for s in sprites])), 3)}
    ores = [s["ore"] for s in srcs if "ore" in s]
    if ores:
        by = {}
        for o in ores:
            by.setdefault(o["pattern"], []).append(o)
        agg["ore"] = {p: {k: round(float(np.mean([o[k] for o in v])), 3) for k in ("coverage", "blobs", "mean_area",
                                                                                 "elongation")} | {"n": len(v)}
                      for p, v in by.items()}
    prof = {"name": name, "category": category, "hash": digest, "created": time.time(), "sources": srcs, "aggregate": agg}
    if write:
        fpath.parent.mkdir(parents=True, exist_ok=True)
        fpath.write_text(json.dumps(prof, ensure_ascii=False, indent=1), encoding="utf-8")
    return prof


def get_profile(name: str) -> dict:
    if name in CATEGORIES:
        return profile(category=name)
    f = P.generated / "profiles" / f"{check_name(name.replace('-', '_'), 'profile')}.json"
    if not f.is_file():
        raise TexGenError(f"no profile {name!r} (a category: {', '.join(CATEGORIES)}, or run `profile --name {name}`)")
    return json.loads(f.read_text(encoding="utf-8"))


# ================================================================ params

def _p(params: dict, key: str, default=None, typ=None, lo=None, hi=None, choices=None):
    v = params.get(key, default)
    if v is None:
        return None
    if typ is bool:
        if isinstance(v, str):
            v = v.lower() in ("1", "true", "yes", "on")
        return bool(v)
    if typ in (int, float):
        try:
            v = typ(v)
        except (TypeError, ValueError):
            raise TexGenError(f"{key}: expected a number, got {v!r}")
        if (lo is not None and v < lo) or (hi is not None and v > hi):
            raise TexGenError(f"{key}: {v} out of range [{lo}, {hi}]")
    if typ is str:
        v = str(v)
    if choices is not None and v not in choices:
        raise TexGenError(f"{key}: {v!r} not one of {', '.join(map(str, choices))}")
    return v


def _palette_or_shift(params: dict):
    pal = params.get("palette")
    if pal not in (None, ""):
        ramp, smooth, srcs = parse_palette(pal)
        sm = params.get("smooth")
        return ("ramp", ramp, smooth if sm in (None, "", "auto") else _p(params, "smooth", typ=bool), srcs)
    shift = {k: _p(params, k, d, float, lo, hi) for k, d, lo, hi in
             (("hue", 0.0, -360, 360), ("sat", 1.0, 0, 4), ("light", 0.0, -0.5, 0.5))}
    if shift == {"hue": 0.0, "sat": 1.0, "light": 0.0}:
        raise TexGenError("give a palette (texture / metal:x / ingot:x / #hex ramp) or a hue/sat/light shift")
    return ("shift", shift, None, [])


def remap(a: np.ndarray, ramp: Ramp, mask=None, smooth: bool = True, detail: float = 0.0) -> np.ndarray:
    """Luminance-rank remap of the masked pixels onto a ramp (structure and alpha kept)."""
    out = a.copy()
    mask = (a[..., 3] > 0) if mask is None else mask & (a[..., 3] > 0)
    if not mask.any():
        return out
    lab = oklab(a[..., :3])
    t = rank(lab[..., 0], mask)
    new = ramp.at(t, smooth)
    if detail:
        ab = lab[..., 1:]
        new[..., 1:] += detail * (ab - ab[mask].mean(0))
    out[..., :3] = np.where(mask[..., None], rgb_of(new), a[..., :3])
    return out


def shift(a: np.ndarray, hue=0.0, sat=1.0, light=0.0, mask=None) -> np.ndarray:
    out = a.copy()
    mask = (a[..., 3] > 0) if mask is None else mask & (a[..., 3] > 0)
    lab = oklab(a[..., :3])
    C, h = np.hypot(lab[..., 1], lab[..., 2]) * sat, np.arctan2(lab[..., 2], lab[..., 1]) + np.radians(hue)
    new = np.stack([np.clip(lab[..., 0] + light, 0, 1), C * np.cos(h), C * np.sin(h)], -1)
    out[..., :3] = np.where(mask[..., None], rgb_of(new), a[..., :3])
    return out


def _apply_colour(a, params, mask=None):
    mode, x, smooth, srcs = _palette_or_shift(params)
    if mode == "ramp":
        return remap(a, x, mask, smooth, _p(params, "detail", 0.0, float, 0, 1)), srcs
    return shift(a, x["hue"], x["sat"], x["light"], mask), srcs


def kind_of(a: np.ndarray) -> str:
    return "block" if (a[..., 3] > 127).all() else "sprite"


def dest_of(ref: str) -> str:
    return "item" if ref.startswith("item/") else ("models/armor" if ref.startswith("models/armor/") else "block")


# ================================================================ generators

def gen_recolor(params: dict, seed: int):
    base = resolve(_p(params, "base", typ=str) or "")[0]
    a = load(base)
    out, srcs = _apply_colour(a, params)
    kind = kind_of(a)
    tag = ref_name(srcs[0]) if srcs else (params.get("palette", "").split(":")[-1] if params.get("palette") else "shift")
    return finish(out, kind, a if kind == "sprite" else None), {
        "id": f"{ref_name(base)}_{re.sub(r'[^a-z0-9_]', '', str(tag).lower())[:20] or 'recolor'}", "dest": dest_of(base),
        "kind": kind, "sources": [base] + srcs}


VARIANTS = ("polished", "bricks", "cracked", "chiseled", "crust_bricks", "mossy", "frosted", "scorched")
VARIANT_PALETTES = {"mossy": "block/abyssal_moss", "frosted": "block/frost_silt", "scorched": "block/molten_volcanic_rock"}


def variant_name(base: str, vt: str) -> str:
    return {"polished": f"polished_{base}", "bricks": f"{base}_bricks", "cracked": f"cracked_{base}_bricks",
            "chiseled": f"chiseled_{base}", "crust_bricks": f"{base}_bricks"}.get(vt, f"{vt}_{base}")


def _overlay(a: np.ndarray, mask: np.ndarray, ramp: Ramp, smooth=False) -> np.ndarray:
    return remap(a, ramp, mask, smooth)


def _patch_mask(rng, amount: float, scale: float, bias=None) -> np.ndarray:
    n = spectral_noise(iso_spectrum(scale), rng)
    if bias is not None:
        n = n + bias
    return n > np.quantile(n, 1 - amount)


def _variant_step(a: np.ndarray, vt: str, name: str, seed: int, params: dict) -> tuple[np.ndarray, list[str]]:
    rng = rng_for(seed, "variant", vt, name)
    srcs = []
    if vt in derive_textures.RECIPES:
        rec = "crust_bricks" if vt == "crust_bricks" else vt
        return derive_textures.build(rec, a, name if seed == 0 else f"{name}:{seed}"), srcs
    amount = _p(params, "amount", {"mossy": 0.38, "frosted": 0.3, "scorched": 0.5}[vt], float, 0.05, 0.95)
    pal = params.get(f"{vt}_palette") or VARIANT_PALETTES[vt]
    ramp, _, s = parse_palette(pal, 6)
    srcs += s
    y = np.mgrid[0:16, 0:16][0]
    L = lum_l(a)
    if vt == "mossy":
        bias = (1 - y / 15.0) * 0.9 + (L - L.mean()) * 0.8
        m = _patch_mask(rng, amount, 4.0, bias)
        m = clean_isolated(m.astype(int), rng, 1.0).astype(bool)
        out = _overlay(a, m, ramp)
        shade = ~m & np.roll(m, 1, 0)                          # moss overhang shadow
        lab = oklab(out[..., :3])
        lab[..., 0] -= 0.08 * shade
        out[..., :3] = np.where(shade[..., None], rgb_of(lab), out[..., :3])
        return out, srcs
    if vt == "frosted":
        bias = (L - L.mean()) * 2.5 + (1 - y / 15.0) * 0.6
        m = _patch_mask(rng, amount, 4.5, bias)
        m = clean_isolated(m.astype(int), rng, 1.0).astype(bool)
        out = _overlay(a, m, ramp.sub(0.3, 1.0))
        lab = oklab(out[..., :3])                              # cold cast over the rest
        cool = ramp.lab[len(ramp) // 2]
        lab[..., 1:] = lab[..., 1:] * 0.8 + cool[1:] * 0.2
        lab[..., 0] += 0.02
        out[..., :3] = np.where(m[..., None], out[..., :3], rgb_of(lab))
        edge = m & ~np.roll(m, 1, 0)                           # bright frost rims on top
        out[..., :3] = np.where(edge[..., None], rgb_of(ramp.lab[-1]), out[..., :3])
        return out, srcs
    # scorched: darker, drier, soot patches; embers glow in the deepest crevices
    lab = oklab(a[..., :3])
    lab[..., 0] = lab[..., 0] * 0.72
    lab[..., 1:] *= 0.55
    soot = _patch_mask(rng, amount * 0.6, 4.0)
    lab[..., 0] -= 0.07 * soot
    out = a.copy()
    out[..., :3] = rgb_of(lab)
    ember = ramp.sub(0.55, 1.0)
    frac = _p(params, "embers", 0.07, float, 0, 0.3)
    if frac:
        glow = spectral_noise(iso_spectrum(5.0), rng)
        score = -L + 0.08 * glow
        pick = score >= np.quantile(score, 1 - frac)
        t = rank(glow, pick)
        out[..., :3] = np.where(pick[..., None], rgb_of(ember.at(t, smooth=False)), out[..., :3])
    return out, srcs


def gen_variant(params: dict, seed: int):
    base = resolve(_p(params, "base", typ=str) or "")[0]
    vt = _p(params, "type", "polished", str)
    steps = vt.split("+")
    for s in steps:
        if s not in VARIANTS:
            raise TexGenError(f"variant type {s!r} not one of {', '.join(VARIANTS)} (combine with +)")
    a = load(base)
    if a.shape[:2] != (16, 16):
        raise TexGenError("variants need a 16x16 block")
    bname = ref_name(base)
    name = bname
    srcs = [base]
    for s in steps:
        name = variant_name(name, s)
        a, more = _variant_step(a, s, name, seed, params)
        srcs += more
    return finish(a, "block"), {"id": name, "dest": "block", "kind": "block", "sources": srcs}


# ---------------------------------------------------------------- ore

ORE_PATTERNS = ("nuggets", "veins", "crystals")


def ore_host(ref: str) -> np.ndarray:
    """An approved ore with its grains painted out (host pixels spread into the holes)."""
    a = load(ref)
    m = ore_split(a)
    lab = oklab(a[..., :3])
    C = np.hypot(lab[..., 1], lab[..., 2])
    for _ in range(2):                                   # grain rims tinted by the mineral belong to the grain too
        up, dn, lf, rt = nbrs(m)
        m = m | ((up | dn | lf | rt) & (C > np.median(C[~m]) + 0.015))
    out = a.copy()
    rng = rng_for(0, "host", ref)
    hole = m.copy()
    while hole.any():
        ys, xs = np.nonzero(hole)
        for y, x in zip(ys, xs):
            cand = [((y + dy) % 16, (x + dx) % 16) for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1))]
            cand = [c for c in cand if not hole[c]]
            if cand:
                out[y, x] = out[cand[int(rng.integers(len(cand)))]]
                hole[y, x] = False
    return out


def _ore_learned(pattern: str) -> dict:
    try:
        agg = profile(category="ores")["aggregate"].get("ore", {})
    except TexGenError:
        agg = {}
    d = {"nuggets": {"coverage": 0.3, "blobs": 6, "mean_area": 8}, "veins": {"coverage": 0.25, "blobs": 2, "mean_area": 30},
         "crystals": {"coverage": 0.28, "blobs": 5, "mean_area": 7}}[pattern]
    return {**d, **agg.get(pattern, {})}


def _wrap_dist(a, b):
    d = np.abs(np.array(a) - np.array(b))
    d = np.minimum(d, 16 - d)
    return float(np.hypot(*d))


def _diag_line(rng, m: np.ndarray, thick: float) -> None:
    """A vein: a wrapped near-diagonal line across the tile with jitter and thicker knots."""
    y, x = float(rng.uniform(0, 16)), float(rng.uniform(0, 16))
    ang = rng.choice([1, -1]) * rng.uniform(0.55, 1.0)             # radians from horizontal
    dy, dx = np.sin(ang), np.cos(ang) * rng.choice([1, -1])
    for i in range(int(rng.integers(12, 20))):
        py, px = int(np.floor(y)) % 16, int(np.floor(x)) % 16
        m[py, px] = True
        if rng.random() < thick:
            m[(py + 1) % 16, px] = True
        if rng.random() < thick * 0.5:
            m[py, (px + 1) % 16] = True
        y, x = y + dy + rng.uniform(-0.35, 0.35), x + dx + rng.uniform(-0.35, 0.35)


def ore_mask(pattern: str, rng, density: float = 1.0, size: float = 1.0) -> np.ndarray:
    """Grain layout learned from the approved ores (coverage, grain size) for a named pattern; tileable."""
    st = _ore_learned(pattern)
    target = min(0.6, st["coverage"] * density)
    m = np.zeros((16, 16), bool)
    y, x = np.mgrid[0:16, 0:16]
    if pattern == "veins":
        for _ in range(60):
            if m.mean() >= target * 0.75:
                break
            _diag_line(rng, m, 0.7 * size)
        for _ in range(int(rng.integers(2, 5))):              # a few loose flecks between the veins
            m[rng.integers(16), rng.integers(16)] = True
        return m
    centres = []
    area_mean = max(4.0, st["mean_area"] * 1.15) * size
    for _ in range(600):
        if m.mean() >= target:
            break
        c = (rng.uniform(0, 16), rng.uniform(0, 16))
        mind = np.sqrt(area_mean) * (1.45 if pattern == "nuggets" else 1.3)
        if any(_wrap_dist(c, o) < mind for o in centres):
            continue
        dyv = ((y + 0.5 - c[0] + 8) % 16) - 8
        dxv = ((x + 0.5 - c[1] + 8) % 16) - 8
        area = area_mean * rng.uniform(0.7, 1.35)
        if pattern == "nuggets":
            r = np.sqrt(area / np.pi)
            rx, ry = r * rng.uniform(0.85, 1.2), r * rng.uniform(0.85, 1.2)
            blob = (dxv / rx) ** 2 + (dyv / ry) ** 2 <= 1.0 + rng.uniform(0, 0.3)
        else:                                                   # crystal: elongated diamond, mostly diagonal
            ang = rng.choice([np.pi / 4, -np.pi / 4, np.pi / 3, -np.pi / 3, np.pi / 6, -np.pi / 6])
            u = dxv * np.cos(ang) + dyv * np.sin(ang)
            v = -dxv * np.sin(ang) + dyv * np.cos(ang)
            w = 1.25
            ln = max(1.8, area / (2 * w))
            blob = np.abs(u) / ln + np.abs(v) / w <= 1.0
        if blob.sum() >= 3:
            centres.append(c)
            m |= blob
    return m


def shade_grains(host: np.ndarray, m: np.ndarray, ramp: Ramp, rng, glints: bool = True) -> np.ndarray:
    """Ore grains lit from the top-left: lit rim, mid body, shaded bottom-right rim, one glint per grain,
    and a dark drop shadow on the host below/right of each grain (the approved ores' look)."""
    up, dn, lf, rt = nbrs(m)
    t = np.full(m.shape, 0.55)
    t += rng.uniform(-0.08, 0.08, m.shape)
    t = np.where(~up | ~lf, t + 0.22, t)
    t = np.where(~dn | ~rt, t - 0.3, t)
    if glints:
        for b in blobs(m):
            if len(b) >= 3:
                py, px = b[np.argmin(b[:, 0] + b[:, 1] + rng.uniform(0, 0.5, len(b)))]
                t[py, px] = 0.98
    out = host.copy()
    lab = ramp.at(np.clip(t, 0.02, 0.98), smooth=False)
    out[..., :3] = np.where(m[..., None], rgb_of(lab), host[..., :3])
    shadow = ~m & (np.roll(m, 1, 0) | np.roll(m, 1, 1))
    hl = oklab(out[..., :3])
    hl[..., 0] -= 0.07
    out[..., :3] = np.where(shadow[..., None], rgb_of(hl), out[..., :3])
    return out


def gen_ore(params: dict, seed: int):
    rng = rng_for(seed, "ore")
    host_ref = _p(params, "host", "orehost:tungsten_ore", str)
    if host_ref.startswith("orehost:"):
        hr = resolve(host_ref[8:])[0]
        host, srcs = ore_host(hr), [hr]
    else:
        hr = resolve(host_ref)[0]
        host, srcs = load(hr), [hr]
    if host.shape[:2] != (16, 16) or not (host[..., 3] > 127).all():
        raise TexGenError("ore host must be an opaque 16x16 block")
    ramp, _, psrc = parse_palette(_p(params, "palette", typ=str) or "", 7)
    if len(ramp) > 6:
        ramp = Ramp(*kmeans_lab(ramp.lab, ramp.w, 6), name=ramp.name)
    pattern = _p(params, "pattern", "nuggets", str)
    if pattern.startswith("mask:"):
        mref = resolve(pattern[5:])[0]
        m = ore_split(load(mref))
        m = np.roll(m, (int(rng.integers(16)), int(rng.integers(16))), (0, 1))
        if rng.random() < 0.5:
            m = m[:, ::-1]
        srcs.append(mref)
    elif pattern in ORE_PATTERNS:
        m = ore_mask(pattern, rng, _p(params, "density", 1.0, float, 0.3, 2.5), _p(params, "size", 1.0, float, 0.5, 2.0))
    else:
        raise TexGenError(f"pattern must be {', '.join(ORE_PATTERNS)} or mask:<ore ref>")
    out = shade_grains(host, m, ramp, rng)
    name = ramp.name if ramp.name not in ("", "hex") else "new"
    return finish(out, "block"), {"id": f"{re.sub(r'[^a-z0-9_]', '', name.lower()) or 'new'}_ore", "dest": "block",
                                  "kind": "block", "sources": srcs + psrc}


# ---------------------------------------------------------------- synth

def gen_synth(params: dict, seed: int):
    rng = rng_for(seed, "synth")
    srcs = []
    like = params.get("like")
    prof_name = _p(params, "profile", "rocks", str)
    if like:
        lref = resolve(like)[0]
        A = spectrum(lum_l(load(lref)))
        srcs.append(lref)
        pool = [lref]
    else:
        prof = get_profile(prof_name)
        pool = [s["ref"] for s in prof["sources"] if "seam" in s and s["size"] == [16, 16]]
        if not pool:
            raise TexGenError(f"profile {prof_name} has no 16x16 blocks")
        pick = pool[int(rng.integers(len(pool)))]
        mean = np.array(prof["aggregate"]["spectrum"])
        A = 0.6 * spectrum(lum_l(load(pick))) + 0.4 * mean
        srcs.append(f"profile:{prof_name}")
    scale = params.get("scale")
    if scale not in (None, "", "auto"):
        A = 0.5 * A / np.sqrt((A ** 2).sum()) + 0.5 * iso_spectrum(_p(params, "scale", typ=float, lo=1, hi=8))
    pal = params.get("palette")
    if pal:
        ramp, _, ps = parse_palette(pal, 8)
        srcs += ps
    else:
        pref = pool[int(rng.integers(len(pool)))]
        ramp = ramp_of(load(pref), 8, name=ref_name(pref))
        srcs.append(pref)
    n = spectral_noise(A, rng)
    y, x = np.mgrid[0:16, 0:16]
    layers = _p(params, "layers", 0.0, float, 0, 2)
    if layers:
        k = int(rng.integers(1, 3))
        n = n + layers * np.sin(2 * np.pi * (k * y / 16 + 0.15 * spectral_noise(iso_spectrum(6), rng)) + rng.uniform(0, 6.3))
    style = _p(params, "style", "cobble", str, choices=("cobble", "grain"))
    if style == "cobble":
        # low-passed field -> K flat bands (the shapes), lit top-left rims / shaded bottom-right rims between
        # bands (the relief), high-frequency residual as sparse speckle: the approved rocks' construction
        f = np.fft.fftfreq(16) * 16
        r = np.hypot(*np.meshgrid(f, f, indexing="ij"))
        cut = _p(params, "cut", 4.0, float, 1.5, 8)
        lp = np.real(np.fft.ifft2(np.fft.fft2(n) * np.exp(-(r / cut) ** 2)))
        K = _p(params, "bands", 4, int, 2, 7)
        band = np.clip((rank(lp, np.ones_like(lp, bool)) * K).astype(int), 0, K - 1)
        up, _, lf, _ = nbrs(band)
        level = 2 * band + 1
        level = level + ((up < band) | (lf < band)) - ((up > band) | (lf > band))
        resid = n - lp / (lp.std() + 1e-9) * n.std()
        sp = _p(params, "speckle", 0.14, float, 0, 0.5)
        hi = rank(np.abs(resid), np.ones_like(n, bool)) > 1 - sp
        level = np.clip(level + np.where(hi, np.sign(resid), 0).astype(int), 0, 2 * K)
        levels = 2 * K + 1
        level = clean_isolated(level, rng, _p(params, "clean", 0.5, float, 0, 1))
    else:
        emb = _p(params, "emboss", 0.6, float, 0, 2)
        n = n + emb * (n - np.roll(n, (1, 1), (0, 1)))
        levels = _p(params, "levels", 0, int, 0, 16) or min(16, 2 * len(ramp) - 1)
        t = rank(n, np.ones_like(n, bool))
        level = np.clip((t * levels).astype(int), 0, levels - 1)
        level = clean_isolated(level, rng, _p(params, "clean", 0.7, float, 0, 1))
    cols = ramp.at((np.arange(levels) + 0.5) / levels, smooth=True)
    lab = cols[level]
    dark, bright = ramp.lab[0], ramp.lab[-1]
    for _ in range(_p(params, "cracks", 0, int, 0, 6)):
        pts = walk(rng, int(rng.integers(6, 12)))
        for py, px in pts:
            lab[py, px] = dark
            lab[(py + 1) % 16, px] = np.minimum(lab[(py + 1) % 16, px] + [0.04, 0, 0], 1) if (
                (py + 1) % 16, px) not in pts else lab[(py + 1) % 16, px]
    vein_pal = params.get("vein_palette")
    vramp = parse_palette(vein_pal, 6)[0].sub(0.5, 1.0) if vein_pal else Ramp(bright[None])
    for _ in range(_p(params, "veins", 0, int, 0, 6)):
        for i, (py, px) in enumerate(walk(rng, int(rng.integers(8, 16)), diag=0.3)):
            lab[py, px] = vramp.lab[i % len(vramp)]
    out = np.dstack([rgb_of(lab), np.full((16, 16), 255, np.uint8)])
    iid = params.get("name_hint") or f"synth_{ramp.name or prof_name}"
    return finish(out, "block"), {"id": re.sub(r"[^a-z0-9_]", "_", iid.lower())[:60], "dest": "block", "kind": "block",
                                  "sources": srcs}


# ---------------------------------------------------------------- sprite

def gen_sprite(params: dict, seed: int):
    rng = rng_for(seed, "sprite")
    base = resolve(_p(params, "base", typ=str) or "")[0]
    a = load(base)
    if kind_of(a) != "sprite":
        raise TexGenError(f"{base} is opaque; use recolor / variants for blocks")
    H, W = a.shape[:2]

    def pick(key, default, lo, hi, typ=int):
        v = params.get(key, default)
        if v in ("auto", "random"):
            return typ(rng.integers(lo, hi + 1)) if typ is int else float(rng.uniform(lo, hi))
        return _p(params, key, default, typ, lo, hi)

    mirror = params.get("mirror", "auto")
    mirror = bool(rng.integers(2)) if mirror in ("auto", "random") else _p(params, "mirror", False, bool)
    height = pick("height", 1.0, 0.6, 1.0, float)
    sway = pick("sway", 0, -2, 2)
    dx = pick("shift", 0, -2, 2)
    out = a[:, ::-1].copy() if mirror else a.copy()
    vis = out[..., 3] > 127
    ys = np.nonzero(vis.any(1))[0]
    bottom_anchored = ys.max() >= H - 1
    if height < 0.999 and len(ys):                                   # squash towards the anchor, nearest rows
        top, bot = ys.min(), ys.max()
        new = np.zeros_like(out)
        span = bot - top + 1
        nspan = max(2, int(round(span * height)))
        for r in range(nspan):
            src = top + int(r * span / nspan) if not bottom_anchored else bot - int(r * span / nspan)
            dst = (top + (span - nspan) // 2 + r) if not bottom_anchored else bot - r
            new[dst] = out[src]
        out = new
    if sway:                                                          # shear: the top leans, the base stays
        vis = out[..., 3] > 127
        ys = np.nonzero(vis.any(1))[0]
        new = np.zeros_like(out)
        top, bot = (ys.min(), ys.max()) if len(ys) else (0, H - 1)
        for r in range(H):
            f = (bot - r) / max(1, bot - top) if bottom_anchored else (r - (top + bot) / 2) / max(1, (bot - top) / 2)
            off = int(round(sway * f))
            new[r] = np.roll(out[r], off, 0)
            if off > 0:
                new[r, :off] = 0
            elif off < 0:
                new[r, off:] = 0
        out = new
    if dx:
        out = np.roll(out, dx, 1)
        if dx > 0:
            out[:, :dx] = 0
        else:
            out[:, dx:] = 0
    srcs = [base]
    if params.get("palette") or any(params.get(k) not in (None, "", 0, 1, "0", "1") for k in ("hue", "sat", "light")):
        out, more = _apply_colour(out, params)
        srcs += more
    tag = []
    if params.get("palette"):
        tag.append(ref_name(str(params["palette"]).split(",")[0]).lstrip("#"))
    return finish(out, "sprite", a), {"id": f"{ref_name(base)}_{'_'.join(tag) or 'var'}"[:60].replace("#", ""),
                                      "dest": dest_of(base), "kind": "sprite", "sources": srcs,
                                      "warp": {"mirror": mirror, "height": round(height, 2), "sway": sway, "shift": dx}}


# ---------------------------------------------------------------- tier

TOOL_SUFFIXES = ("pickaxe", "axe", "shovel", "sword", "hoe", "ingot", "nugget", "powder", "plate", "helmet",
                 "chestplate", "leggings", "boots", "concentrate", "layer_1", "layer_2")


def material_mask(a: np.ndarray) -> np.ndarray:
    """Pixels of the tool / ingot material: the dominant non-wood hue group (+ dark rim pixels of that hue)."""
    vis = a[..., 3] > 127
    lab = oklab(a[..., :3])
    L, C = lab[..., 0], np.hypot(lab[..., 1], lab[..., 2])
    h = np.degrees(np.arctan2(lab[..., 2], lab[..., 1])) % 360
    wood = vis & (h > 25) & (h < 95) & (C > 0.035) & (L < 0.62)
    chroma = vis & (C > 0.02) & ~wood
    neutral_bright = vis & (C <= 0.02) & (L > 0.3)
    if not chroma.any() and not neutral_bright.any():
        return vis & ~wood if (vis & ~wood).any() else vis
    if chroma.sum() >= max(3, neutral_bright.sum() * 0.5):
        hh = h[chroma]
        w = C[chroma]
        best = max(range(0, 360, 5), key=lambda c: (w * (np.abs((hh - c + 180) % 360 - 180) < 40)).sum())
        near = np.abs((h - best + 180) % 360 - 180) < 50
        m = vis & ~wood & ((near & (C > 0.012)) | (neutral_bright & (L > 0.8)))
    else:
        m = vis & ~wood & (C <= 0.05) & (L > 0.22)
    if not m.any():
        return m
    hm = np.degrees(np.arctan2(lab[..., 2][m].mean(), lab[..., 1][m].mean())) % 360
    up, dn, lf, rt = nbrs(m, wrap=False)
    rim = vis & ~m & ~wood & (L < 0.3) & (up | dn | lf | rt) & ((C < 0.02) | (np.abs((h - hm + 180) % 360 - 180) < 70))
    return m | rim


def gen_tier(params: dict, seed: int):
    base = resolve(_p(params, "base", typ=str) or "")[0]
    a = load(base)
    how = _p(params, "mask", "auto", str, choices=("auto", "all"))
    m = material_mask(a) if how == "auto" else a[..., 3] > 127
    pal = _p(params, "palette", typ=str)
    if not pal:
        raise TexGenError("tier needs a palette (ingot:<metal>, metal:<name>, #hex ramp or a texture)")
    ramp, smooth, srcs = parse_palette(pal, 8)
    sm = params.get("smooth")
    out = remap(a, ramp, m, smooth if sm in (None, "", "auto") else bool(sm))
    bname = ref_name(base)
    metal = re.sub(r"[^a-z0-9_]", "", (pal.split(":", 1)[1] if ":" in pal and not pal.startswith("gen:") else
                                       ref_name(pal) if not pal.startswith("#") else "new").lower())
    metal = metal.removesuffix("_ingot")
    suffix = next((s for s in TOOL_SUFFIXES if bname.endswith("_" + s)), None)
    iid = f"{metal}_{suffix}" if suffix else f"{bname}_{metal}"
    return finish(out, kind_of(a), a), {"id": iid, "dest": dest_of(base), "kind": kind_of(a), "sources": [base] + srcs,
                                        "material_px": int(m.sum())}


# ---------------------------------------------------------------- blend

def gen_blend(params: dict, seed: int):
    rng = rng_for(seed, "blend")
    ra, rb = resolve(_p(params, "a", typ=str) or "")[0], resolve(_p(params, "b", typ=str) or "")[0]
    a, b = load(ra), load(rb)
    if a.shape != b.shape:
        raise TexGenError(f"{ra} and {rb} differ in size")
    H, W = a.shape[:2]
    how = _p(params, "mask", "noise", str, choices=("noise", "vertical", "horizontal", "diagonal", "radial"))
    amount = _p(params, "amount", 0.5, float, 0.02, 0.98)
    rough = _p(params, "rough", 0.6, float, 0, 3)
    y, x = np.mgrid[0:H, 0:W] / (H - 1)
    n = spectral_noise(iso_spectrum(_p(params, "scale", 4.0, float, 1, 8), H), rng) if H == W else rng.normal(size=(H, W))
    field = {"noise": n, "vertical": y * 3 + rough * n * 0.5, "horizontal": x * 3 + rough * n * 0.5,
             "diagonal": (x + y) * 1.5 + rough * n * 0.5,
             "radial": -np.hypot(x - 0.5, y - 0.5) * 4 + rough * n * 0.5}[how]
    m = field >= np.quantile(field, 1 - amount) if how != "vertical" else field <= np.quantile(field, amount)
    m = clean_isolated(m.astype(int), rng, 1.0).astype(bool)
    m &= b[..., 3] > 127                                   # never punch holes: B only where B is visible
    out = np.where(m[..., None], b, a)
    kind = kind_of(a)
    if kind == "sprite":
        out[..., 3] = a[..., 3]
    return finish(out, kind, a if kind == "sprite" else None), {
        "id": f"{ref_name(ra)}_{ref_name(rb)}"[:60], "dest": dest_of(ra), "kind": kind, "sources": [ra, rb]}


GENERATORS = {"recolor": gen_recolor, "variants": gen_variant, "ore": gen_ore, "synth": gen_synth,
              "sprite": gen_sprite, "tier": gen_tier, "blend": gen_blend}


def generate(gen: str, params: dict, seed: int = 0) -> tuple[np.ndarray, dict]:
    if gen not in GENERATORS:
        raise TexGenError(f"unknown generator {gen!r} (known: {', '.join(GENERATORS)})")
    if not isinstance(params, dict):
        raise TexGenError("params must be an object")
    seed = _p({"seed": seed}, "seed", 0, int, 0, 2 ** 31 - 1)
    img, meta = GENERATORS[gen](params, seed)
    iid = params.get("id") or meta["id"]
    meta["id"] = check_name(re.sub(r"_+", "_", str(iid).lower()).strip("_") if not params.get("id") else str(iid))
    if params.get("dest"):
        meta["dest"] = _p(params, "dest", typ=str, choices=("block", "item"))
    meta["colours"] = int(len(np.unique(img[..., :3][img[..., 3] > 0], axis=0)))
    if meta["kind"] == "block":
        meta["seam"] = round(seam_ratio(img), 2)
    return img, meta


def preview(gen: str, params: dict, seeds: list[int]) -> list[dict]:
    """GUI: N seeds side by side, PNG as base64 (nothing written)."""
    if not isinstance(seeds, list) or not 1 <= len(seeds) <= 12:
        raise TexGenError("seeds: 1..12 integers")
    out = []
    for s in seeds:
        img, meta = generate(gen, params, s)
        out.append({"seed": int(s), "png": b64(img), **meta})
    return out


# ================================================================ candidates (inbox/generated/<batch>/)

def _manifest_path(batch: str) -> Path:
    return P.generated / check_batch(batch) / "manifest.json"


def read_manifest(batch: str) -> dict:
    f = _manifest_path(batch)
    if f.is_file():
        try:
            return json.loads(f.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            pass
    return {"batch": batch, "created": time.time(), "candidates": {}}


def run_job(gen: str, params: dict, seeds: list[int], batch: str, dry_run: bool = False,
            suffix_seed: bool | None = None) -> list[dict]:
    """Generate one candidate per seed and (unless dry_run) write them into the batch folder."""
    check_batch(batch)
    if not seeds or len(seeds) > 64:
        raise TexGenError("1..64 seeds per job")
    suffix_seed = len(seeds) > 1 if suffix_seed is None else suffix_seed
    res = []
    for s in seeds:
        img, meta = generate(gen, params, s)
        name = f"{meta['id']}__s{s}" if suffix_seed else meta["id"]
        res.append({"name": name, "gen": gen, "seed": int(s), "params": params, "img": img, **meta})
    if not dry_run:
        save_candidates(batch, res)
    return [{k: v for k, v in r.items() if k != "img"} |
            {"file": None if dry_run else f"inbox/generated/{batch}/{r['name']}.png"} for r in res]


def save_candidates(batch: str, res: list[dict]) -> None:
    with WRITE_LOCK:
        d = P.generated / check_batch(batch)
        d.mkdir(parents=True, exist_ok=True)
        man = read_manifest(batch)
        for r in res:
            if not CAND_RE.match(r["name"]):
                raise TexGenError(f"bad candidate name {r['name']}")
            Image.fromarray(r["img"], "RGBA").save(d / f"{r['name']}.png")
            man["candidates"][r["name"]] = {k: v for k, v in r.items() if k not in ("img", "name")} | {"t": time.time()}
        man["updated"] = time.time()
        _manifest_path(batch).write_text(json.dumps(man, ensure_ascii=False, indent=1), encoding="utf-8")
        contact_sheet(batch)


def contact_sheet(batch: str, scale: int = 5) -> Path:
    """contact.png: per candidate [first source | candidate | 2x2 tile (blocks)] + name."""
    d = P.generated / check_batch(batch)
    man = read_manifest(batch)
    items = list(man["candidates"].items())
    S = 16 * scale
    cw, ch, cols = S * 3 + 16, S + 16, 4
    rows = max(1, (len(items) + cols - 1) // cols)
    sheet = Image.new("RGBA", (cols * cw, rows * ch), (46, 50, 60, 255))
    font = ImageFont.load_default()
    dr = ImageDraw.Draw(sheet)
    for i, (name, c) in enumerate(items):
        f = d / f"{name}.png"
        if not f.is_file():
            continue
        x0, y0 = (i % cols) * cw + 4, (i // cols) * ch + 2
        with Image.open(f) as im:
            img = im.convert("RGBA")
        src = None
        for s in c.get("sources", []):
            try:
                src = Image.fromarray(load(s))
                break
            except TexGenError:
                continue
        if src is not None and src.size == img.size:
            sheet.alpha_composite(src.resize((S // 2, S // 2), Image.NEAREST), (x0, y0))
        sheet.alpha_composite(img.resize((S * img.width // 16, S * img.height // 16), Image.NEAREST).crop((0, 0, S, S)),
                              (x0 + S // 2 + 4, y0))
        if c.get("kind") == "block" and img.size == (16, 16):
            t = Image.new("RGBA", (32, 32))
            for k in range(4):
                t.paste(img, ((k % 2) * 16, (k // 2) * 16))
            sheet.alpha_composite(t.resize((S, S), Image.NEAREST), (x0 + S + S // 2 + 8, y0))
        dr.text((x0, y0 + S + 1), name[:44], fill=(220, 225, 235, 255), font=font)
    out = d / "contact.png"
    sheet.save(out)
    return out


def list_candidates(batch: str | None = None) -> list[dict]:
    out = []
    batches = [check_batch(batch)] if batch else sorted(p.name for p in P.generated.glob("*") if p.is_dir()
                                                         and p.name != "profiles" and BATCH_RE.match(p.name))
    for b in batches:
        man = read_manifest(b)
        for name, c in man["candidates"].items():
            if (P.generated / b / f"{name}.png").is_file():
                out.append({"batch": b, "name": name, "id": c.get("id"), "dest": c.get("dest"), "kind": c.get("kind"),
                            "gen": c.get("gen"), "seed": c.get("seed"), "target": f"{c.get('dest')}/{c.get('id')}",
                            "exists": (P.tex / c.get("dest", "block") / f"{c.get('id')}.png").is_file(),
                            "locked": (P.locks / "textures" / c.get("dest", "block") / f"{c.get('id')}.png").is_file(),
                            "t": c.get("t")})
    return out


def run_batch(spec: dict | str | Path, dry_run: bool = False, batch: str | None = None) -> dict:
    """Spec: {"batch": name, "jobs": [{"gen": ..., "seeds": [..] | "seed": n, "count": n, ...params}]}."""
    if not isinstance(spec, dict):
        spec = json.loads(Path(spec).read_text(encoding="utf-8"))
    batch = check_batch(batch or spec.get("batch") or time.strftime("gen-%Y%m%d-%H%M%S"))
    jobs = spec.get("jobs")
    if not isinstance(jobs, list) or not jobs:
        raise TexGenError("spec needs a non-empty jobs list")
    out = []
    for j in jobs:
        if not isinstance(j, dict):
            raise TexGenError("each job must be an object")
        j = dict(j)
        gen = j.pop("gen", None)
        seeds = _seeds(j.pop("seed", 0), j.pop("count", 1), j.pop("seeds", None))
        many = j.pop("palettes", None)
        for pal in (many if isinstance(many, list) else [None]):
            params = dict(j) | ({"palette": pal} if pal else {})
            out += run_job(gen, params, seeds, batch, dry_run)
    return {"batch": batch, "dry_run": dry_run, "candidates": out}


def _seeds(seed=0, count=1, seeds=None) -> list[int]:
    if seeds:
        if isinstance(seeds, str):
            seeds = [s for s in seeds.split(",") if s.strip()]
        return [int(s) for s in seeds]
    count = int(count or 1)
    if not 1 <= count <= 64:
        raise TexGenError("count 1..64")
    return [int(seed) + i for i in range(count)]


# ================================================================ apply / undo (the only writes into the game assets)

def _rel_root(path: Path) -> str:
    try:
        return path.resolve().relative_to(P.root.resolve()).as_posix()
    except ValueError:
        return str(path)


def _same_file(path: Path, img: np.ndarray) -> bool:
    if not path.is_file():
        return False
    with Image.open(path) as im:
        old = np.asarray(im.convert("RGBA"))
    return old.shape == img.shape and np.array_equal(old, img)


def plan_apply(items: list[dict], lock: bool = False, force_locked: bool = False) -> list[dict]:
    plan = []
    seen = set()
    for it in items:
        if not isinstance(it, dict):
            raise TexGenError("apply items must be objects {batch, name, as?}")
        batch, name = check_batch(it.get("batch")), it.get("name")
        if not isinstance(name, str) or not CAND_RE.match(name):
            raise TexGenError(f"bad candidate name {name!r}")
        f = P.generated / batch / f"{name}.png"
        man = read_manifest(batch)["candidates"].get(name)
        if not f.is_file() or man is None:
            raise TexGenError(f"no candidate {batch}/{name}")
        target = it.get("as") or f"{man.get('dest', 'block')}/{man.get('id')}"
        m = DEST_RE.match(str(target))
        if not m:
            raise TexGenError(f"bad target {target!r} (block/<id> or item/<id>)")
        rel = f"textures/{target}.png"
        if rel in seen:
            raise TexGenError(f"two candidates for {target}")
        seen.add(rel)
        tpath, lpath = (P.assets / rel), (P.locks / rel)
        if P.tex.resolve() not in tpath.resolve().parents:
            raise TexGenError("target outside the textures folder")
        with Image.open(f) as im:
            img = np.asarray(im.convert("RGBA")).copy()
        locked = lpath.is_file()
        derived = derive_textures.is_derived(rel)
        e = {"candidate": f"{batch}/{name}", "target": target, "rel": rel, "locked": locked, "derived": derived,
             "exists": tpath.is_file(), "img": img}
        if locked and not force_locked:
            e["action"], e["reason"] = "refused", "locked (use --force-locked; the lock is updated too)"
        elif derived and not (lock or force_locked):
            e["action"], e["reason"] = "refused", "derived by derive_textures.py (use --lock so it is kept)"
        elif _same_file(tpath, img) and (not (lock or locked) or _same_file(lpath, img)):
            e["action"] = "unchanged"
        else:
            e["action"] = "write"
            e["lock"] = bool(lock or locked)
        plan.append(e)
    return plan


def _check_textures() -> dict:
    import check_textures
    old = check_textures.ASSETS
    try:
        check_textures.ASSETS = str(P.assets)
        chk = check_textures.check()
    finally:
        check_textures.ASSETS = old
    return {"missing": len(chk["missing"]), "invalid_json": len(chk["invalid_json"]),
            "missing_refs": [x["ref"] for x in chk["missing"]][:30]}


def apply(items: list[dict], lock: bool = False, force_locked: bool = False, dry_run: bool = False,
          derive: bool = True) -> dict:
    """Copy candidates into the game textures (the only function that writes there)."""
    if not items:
        raise TexGenError("nothing to apply")
    plan = plan_apply(items, lock, force_locked)
    refused = [e for e in plan if e["action"] == "refused"]
    writes = [e for e in plan if e["action"] == "write"]
    bases = {e["rel"] for e in writes}
    dmap = {rel: v for rel, v in derive_textures.targets().items() if v[1] in bases and rel not in bases} if derive else {}

    def view(e):
        before = None
        tp = P.assets / e["rel"]
        if tp.is_file():
            before = base64.b64encode(tp.read_bytes()).decode()
        return {k: v for k, v in e.items() if k != "img"} | {"after": b64(e["img"]), "before": before}

    summary = {"dry_run": dry_run, "plan": [view(e) for e in plan], "derived_candidates": sorted(dmap),
               "refused": [e["candidate"] for e in refused]}
    if refused:
        summary["error"] = "refused: " + "; ".join(f"{e['target']}: {e['reason']}" for e in refused)
        if not dry_run:
            raise TexGenError(summary["error"])
    if dry_run or not writes:
        return summary
    with WRITE_LOCK:
        stamp = time.strftime("%Y%m%d-%H%M%S")
        bdir = P.backup / f"texgen-{stamp}"
        k = 1
        while bdir.exists():
            k += 1
            bdir = P.backup / f"texgen-{stamp}-{k}"
        entries = []
        roots = {"assets": P.assets, "locks": P.locks}

        def keep(area, rel):
            if any(x["area"] == area and x["rel"] == rel for x in entries):
                return
            src = roots[area] / rel
            existed = src.is_file()
            if existed:
                dst = bdir / area / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
            entries.append({"area": area, "rel": rel, "existed": existed})

        for e in writes:
            keep("assets", e["rel"])
            if e["lock"]:
                keep("locks", e["rel"])
        relock = lock or force_locked
        for rel in dmap:
            keep("assets", rel)
            if (P.locks / rel).is_file() and relock:
                keep("locks", rel)
        man = {"t": time.time(), "items": [e["candidate"] + " -> " + e["target"] for e in writes], "entries": entries,
               "undone": False}
        bdir.mkdir(parents=True, exist_ok=True)
        (bdir / "manifest.json").write_text(json.dumps(man, ensure_ascii=False, indent=1), encoding="utf-8")
        for e in writes:
            tp = P.assets / e["rel"]
            tp.parent.mkdir(parents=True, exist_ok=True)
            Image.fromarray(e["img"], "RGBA").save(tp)
            if e["lock"]:
                lp = P.locks / e["rel"]
                lp.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(tp, lp)
        dsum = {"written": [], "locked_skipped": [], "unchanged": 0}
        for rel, (recipe, base_rel) in dmap.items():
            lp = P.locks / rel
            if lp.is_file() and not relock:
                dsum["locked_skipped"].append(Path(rel).stem)
                continue
            bp = P.assets / base_rel
            with Image.open(bp) as b:
                img = derive_textures.build(recipe, np.asarray(b.convert("RGBA")), Path(rel).stem)
            tp = P.assets / rel
            if _same_file(tp, img):
                dsum["unchanged"] += 1
                continue
            tp.parent.mkdir(parents=True, exist_ok=True)
            Image.fromarray(img, "RGBA").save(tp)
            if lp.is_file():
                shutil.copyfile(tp, lp)
            dsum["written"].append(Path(rel).stem)
        summary.update(backup=_rel_root(bdir), derived=dsum, check=_check_textures(),
                       written=[e["target"] for e in writes])
        man["summary"] = {"written": summary["written"], "derived": dsum["written"]}
        (bdir / "manifest.json").write_text(json.dumps(man, ensure_ascii=False, indent=1), encoding="utf-8")
    return summary


def _apply_manifests() -> list[tuple[Path, dict]]:
    out = []
    for f in sorted(P.backup.glob("texgen-*/manifest.json")):
        try:
            out.append((f.parent, json.loads(f.read_text(encoding="utf-8"))))
        except (OSError, ValueError):
            pass
    return sorted(out, key=lambda x: x[1].get("t", 0))


def undo(dry_run: bool = False) -> dict:
    """Restore the files of the last (not yet undone) apply, locks included."""
    with WRITE_LOCK:
        live = [(d, m) for d, m in _apply_manifests() if not m.get("undone")]
        if not live:
            raise TexGenError("nothing to undo")
        bdir, man = live[-1]
        roots = {"assets": P.assets.resolve(), "locks": P.locks.resolve()}
        done = []
        for e in man["entries"]:
            base = roots.get(e.get("area"))
            dst = (base / e["rel"]).resolve() if base else None
            if not dst or base not in dst.parents or not str(e["rel"]).startswith("textures/"):
                raise TexGenError(f"bad manifest entry {e}")
            act = "restore" if e["existed"] else "delete"
            done.append({"area": e["area"], "rel": e["rel"], "action": act})
            if dry_run:
                continue
            if e["existed"]:
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(bdir / e["area"] / e["rel"], dst)
            elif dst.is_file():
                dst.unlink()
        if not dry_run:
            man["undone"] = time.time()
            (bdir / "manifest.json").write_text(json.dumps(man, ensure_ascii=False, indent=1), encoding="utf-8")
    return {"backup": _rel_root(bdir), "items": man.get("items"), "dry_run": dry_run, "files": done}


# ================================================================ GUI helpers

def meta() -> dict:
    return {"generators": list(GENERATORS), "variants": list(VARIANTS), "ore_patterns": list(ORE_PATTERNS),
            "metals": {k: v for k, v in METALS.items()}, "categories": list(CATEGORIES)}


def sources(kind: str | None = None) -> list[dict]:
    out = []
    for d in ("block", "item", "models/armor"):
        if kind and d != kind:
            continue
        for f in sorted((P.tex / d).glob("*.png")):
            if f.stem.endswith("_glow"):
                continue
            out.append({"ref": f"{d}/{f.stem}", "dir": d, "name": f.stem,
                        "locked": (P.locks / "textures" / d / f.name).is_file()})
    return out


def candidate_file(batch: str, name: str) -> Path:
    if not isinstance(name, str) or not CAND_RE.match(name):
        raise TexGenError("bad candidate name")
    f = P.generated / check_batch(batch) / f"{name}.png"
    if not f.is_file():
        raise TexGenError("no such candidate")
    return f


# ================================================================ CLI

EPILOG = """examples:
  texture_gen.py profile --category ores
  texture_gen.py recolor block/deep_sea_rock --palette block/thermal_rock --batch demo
  texture_gen.py variants block/deep_sea_rock --types mossy,frosted,scorched,bricks+mossy
  texture_gen.py ore --palette metal:silver --pattern nuggets --host block/deep_sea_rock --count 3
  texture_gen.py synth --profile rocks --palette block/trench_rock --cracks 1 --count 4 --id rift_rock
  texture_gen.py sprite block/cave_fern --palette block/thermal_rock --sway auto --count 3
  texture_gen.py tier item/abyssal_alloy_pickaxe --palettes ingot:platinum,metal:orichalcum,metal:silver
  texture_gen.py blend block/deep_sea_rock block/abyssal_moss --mask vertical --amount 0.35
  texture_gen.py batch inbox/generated/example-batch.json --dry-run
  texture_gen.py apply demo/silver_ore --dry-run ; texture_gen.py apply demo/silver_ore --lock ; texture_gen.py undo
"""


def _common(sp):
    sp.add_argument("--seed", type=int, default=0, help="first seed (default 0)")
    sp.add_argument("--count", type=int, default=1, help="number of seeds (seed, seed+1, ...)")
    sp.add_argument("--seeds", help="explicit comma separated seeds")
    sp.add_argument("--batch", help="inbox/generated/<batch> (default gen-<timestamp>)")
    sp.add_argument("--id", help="texture id of the candidate (default derived from the inputs)")
    sp.add_argument("--dest", choices=("block", "item"), help="target folder when applied")
    sp.add_argument("--dry-run", action="store_true", help="generate and report, write nothing")


def _colour_args(sp):
    sp.add_argument("--palette", help="texture ref, metal:<name>, ingot:<metal> or #hex,#hex,... (dark first)")
    sp.add_argument("--hue", type=float, help="hue rotation in degrees (when no palette)")
    sp.add_argument("--sat", type=float, help="chroma multiplier")
    sp.add_argument("--light", type=float, help="lightness offset -0.5..0.5")
    sp.add_argument("--smooth", choices=("auto", "true", "false"), default="auto",
                    help="interpolate between the ramp colours (default true; false snaps to the palette's own colours)")
    sp.add_argument("--detail", type=float, help="keep this much of the base's own hue variation (0..1)")


def build_parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(prog="texture_gen.py", description=__doc__.split("\n")[0],
                                 epilog=EPILOG, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("profile", help="learn a style profile (cached in inbox/generated/profiles)")
    s.add_argument("--category", choices=list(CATEGORIES))
    s.add_argument("--ids", help="comma separated texture refs")
    s.add_argument("--glob", help="e.g. 'block/*_crust'")
    s.add_argument("--name", help="profile name (default: the category)")
    s.add_argument("--refresh", action="store_true")
    s.add_argument("--full", action="store_true", help="print the whole profile (default: summary)")
    s = sub.add_parser("recolor", help="remap a texture onto a palette / hue shift")
    s.add_argument("base")
    _colour_args(s)
    _common(s)
    s = sub.add_parser("variants", help="polished/bricks/cracked/chiseled/mossy/frosted/scorched (+ combos)")
    s.add_argument("base")
    s.add_argument("--types", default="polished,bricks,cracked,chiseled,mossy,frosted,scorched")
    s.add_argument("--amount", type=float, help="coverage of moss / frost / soot")
    for v in VARIANT_PALETTES:
        s.add_argument(f"--{v}-palette", help=f"palette for {v} (default {VARIANT_PALETTES[v]})")
    _common(s)
    s = sub.add_parser("ore", help="host rock + ore grains in the approved ores' style")
    s.add_argument("--host", default="orehost:tungsten_ore", help="block ref or orehost:<ore> (ore with grains removed)")
    s.add_argument("--palette", required=True)
    s.add_argument("--pattern", default="nuggets", help="nuggets | veins | crystals | mask:<ore ref>")
    s.add_argument("--density", type=float, default=1.0)
    s.add_argument("--size", type=float, default=1.0)
    _common(s)
    s = sub.add_parser("synth", help="new tileable material from a style profile")
    s.add_argument("--profile", default="rocks")
    s.add_argument("--like", help="use this texture's noise structure instead of the profile")
    s.add_argument("--palette")
    s.add_argument("--cracks", type=int, default=0)
    s.add_argument("--veins", type=int, default=0)
    s.add_argument("--vein-palette")
    s.add_argument("--layers", type=float, default=0.0)
    s.add_argument("--emboss", type=float, default=0.6)
    s.add_argument("--levels", type=int, default=0, help="colour levels (default 2*ramp-1)")
    s.add_argument("--scale", type=float, help="blend in an isotropic noise of this feature size (px)")
    _common(s)
    s = sub.add_parser("sprite", help="plant / cluster / crystal sprite: warp + recolour an approved sprite")
    s.add_argument("base")
    _colour_args(s)
    s.add_argument("--mirror", nargs="?", const="true", default="auto", help="true/false/auto")
    s.add_argument("--sway", default="auto", help="-2..2 or auto")
    s.add_argument("--shift", default="0", help="-2..2 or auto")
    s.add_argument("--height", default="1.0", help="0.6..1 or auto")
    _common(s)
    s = sub.add_parser("tier", help="tier-coloured family of a tool / ingot icon (silhouette and handle kept)")
    s.add_argument("base")
    s.add_argument("--palettes", required=True, help="comma separated palettes; use ';' when a hex ramp is one of them")
    s.add_argument("--mask", default="auto", choices=("auto", "all"))
    _common(s)
    s = sub.add_parser("blend", help="mix two textures with a noise / gradient mask")
    s.add_argument("a")
    s.add_argument("b")
    s.add_argument("--mask", default="noise", choices=("noise", "vertical", "horizontal", "diagonal", "radial"))
    s.add_argument("--amount", type=float, default=0.5)
    s.add_argument("--rough", type=float, default=0.6)
    s.add_argument("--scale", type=float, default=4.0)
    _common(s)
    s = sub.add_parser("batch", help="run a JSON spec {batch, jobs:[{gen, ...params, seeds|count}]}")
    s.add_argument("spec")
    s.add_argument("--batch")
    s.add_argument("--dry-run", action="store_true")
    s = sub.add_parser("list-candidates", help="candidates in inbox/generated")
    s.add_argument("--batch")
    s = sub.add_parser("apply", help="copy candidates into the game textures (backup + undo)")
    s.add_argument("candidates", nargs="+", help="<batch>/<name>; follow with --as block/<id> to rename (per item)")
    s.add_argument("--as", dest="as_", action="append", default=[], help="target for the candidate at the same position")
    s.add_argument("--lock", action="store_true", help="also update tools/texture_locks")
    s.add_argument("--force-locked", action="store_true", help="overwrite locked textures (their lock is updated)")
    s.add_argument("--no-derive", action="store_true", help="do not re-derive glow/bricks/polished of changed bases")
    s.add_argument("--dry-run", action="store_true")
    s = sub.add_parser("undo", help="restore the files of the last apply")
    s.add_argument("--dry-run", action="store_true")
    return ap


def _cli_params(ns, keys) -> dict:
    out = {}
    for k in keys:
        v = getattr(ns, k, None)
        if v is not None and v != "":
            out[k] = v
    if getattr(ns, "id", None):
        out["id"] = ns.id
    if getattr(ns, "dest", None):
        out["dest"] = ns.dest
    return out


def main(argv=None) -> int:
    ns = build_parser().parse_args(argv)
    try:
        res = _main(ns)
    except TexGenError as e:
        print(json.dumps({"error": str(e)}, ensure_ascii=False))
        return 2
    print(json.dumps(res, ensure_ascii=False, indent=1))
    return 0


def _main(ns):
    batch = getattr(ns, "batch", None) or time.strftime("gen-%Y%m%d-%H%M%S")
    seeds = lambda: _seeds(ns.seed, ns.count, ns.seeds)  # noqa: E731
    wrap = lambda cands: {"batch": batch, "dry_run": ns.dry_run, "candidates": cands,  # noqa: E731
                          "contact_sheet": None if ns.dry_run else f"inbox/generated/{batch}/contact.png"}
    colour = ("palette", "hue", "sat", "light", "detail")
    if ns.cmd == "profile":
        ids = [i for i in (ns.ids or "").split(",") if i.strip()]
        prof = profile(ns.name, ns.category, ids, ns.glob, refresh=ns.refresh)
        if ns.full:
            return prof
        agg = {k: v for k, v in prof["aggregate"].items() if k != "spectrum"}
        return {"name": prof["name"], "file": f"inbox/generated/profiles/{prof['name']}.json",
                "sources": [s["ref"] for s in prof["sources"]], "aggregate": agg}
    if ns.cmd == "recolor":
        p = _cli_params(ns, colour) | {"base": ns.base}
        if ns.smooth != "auto":
            p["smooth"] = ns.smooth == "true"
        return wrap(run_job("recolor", p, seeds(), batch, ns.dry_run))
    if ns.cmd == "variants":
        out = []
        for t in [t.strip() for t in ns.types.split(",") if t.strip()]:
            p = _cli_params(ns, ("amount", "mossy_palette", "frosted_palette", "scorched_palette")) | {
                "base": ns.base, "type": t}
            out += run_job("variants", p, seeds(), batch, ns.dry_run)
        return wrap(out)
    if ns.cmd == "ore":
        return wrap(run_job("ore", _cli_params(ns, ("host", "palette", "pattern", "density", "size")), seeds(), batch,
                            ns.dry_run))
    if ns.cmd == "synth":
        return wrap(run_job("synth", _cli_params(ns, ("profile", "like", "palette", "cracks", "veins", "vein_palette",
                                                      "layers", "emboss", "levels", "scale")), seeds(), batch, ns.dry_run))
    if ns.cmd == "sprite":
        p = _cli_params(ns, colour + ("mirror", "sway", "shift", "height")) | {"base": ns.base}
        return wrap(run_job("sprite", p, seeds(), batch, ns.dry_run))
    if ns.cmd == "tier":
        sep = ";" if ";" in ns.palettes else ","
        out = []
        for pal in [x.strip() for x in ns.palettes.split(sep) if x.strip()]:
            out += run_job("tier", _cli_params(ns, ("mask",)) | {"base": ns.base, "palette": pal}, seeds(), batch,
                           ns.dry_run)
        return wrap(out)
    if ns.cmd == "blend":
        return wrap(run_job("blend", _cli_params(ns, ("a", "b", "mask", "amount", "rough", "scale")), seeds(), batch,
                            ns.dry_run))
    if ns.cmd == "batch":
        return run_batch(ns.spec, ns.dry_run, ns.batch)
    if ns.cmd == "list-candidates":
        return list_candidates(ns.batch)
    if ns.cmd == "apply":
        items = []
        for i, c in enumerate(ns.candidates):
            b, _, n = c.partition("/")
            it = {"batch": b, "name": n}
            if i < len(ns.as_):
                it["as"] = ns.as_[i]
            items.append(it)
        res = apply(items, ns.lock, ns.force_locked, ns.dry_run, not ns.no_derive)
        for e in res["plan"]:
            e.pop("before", None)
            e.pop("after", None)
        return res
    if ns.cmd == "undo":
        return undo(ns.dry_run)
    raise TexGenError(f"unknown command {ns.cmd}")


if __name__ == "__main__":
    sys.exit(main())
