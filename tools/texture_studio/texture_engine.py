"""Texture Studio engine: layer specs -> pixels.  No GUI imports; the CLI, the GUI and the generators share it.

A texture is a JSON *spec* (``specs/<block|item>/<name>.json``): a size and a stack of layers, bottom first.  Rendering
a spec is deterministic (every random layer has its own seed), so the spec is the source of truth and the PNG under
``assets/abyssia/textures`` is its export.  ``apply_all()`` re-exports every spec; the generators call it last so a
regeneration can never bring back an unapproved texture.
"""
from __future__ import annotations

import base64
import copy
import glob
import io
import json
import math
import os
import shutil
import zipfile

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SPEC_DIR = os.path.join(HERE, "specs")
PRESET_DIR = os.path.join(HERE, "presets")
ASSET_ROOT = os.environ.get("ABYSSIA_ASSETS") or os.path.normpath(
    os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "abyssia"))
TEX_DIR = os.path.join(ASSET_ROOT, "textures")
LOCK_DIR = os.path.normpath(os.path.join(HERE, "..", "texture_locks", "assets", "textures"))
MC_VERSION = "1.20.1"
KINDS = ("block", "item")
SPEC_VERSION = 1

BLENDS = ["normal", "multiply", "screen", "overlay", "add", "darken", "lighten", "erase"]


# ================================================================ colours

def parse_color(s: str | None, default=(0.0, 0.0, 0.0, 1.0)) -> tuple[float, float, float, float]:
    if not s:
        return default
    s = s.lstrip("#")
    try:
        if len(s) == 6:
            return (int(s[0:2], 16) / 255, int(s[2:4], 16) / 255, int(s[4:6], 16) / 255, 1.0)
        if len(s) == 8:
            return (int(s[0:2], 16) / 255, int(s[2:4], 16) / 255, int(s[4:6], 16) / 255, int(s[6:8], 16) / 255)
    except ValueError:
        pass
    return default


def to_hex(rgba, alpha: bool = False) -> str:
    v = [max(0, min(255, int(round(c * 255)))) for c in rgba]
    return "#%02X%02X%02X" % tuple(v[:3]) + ("%02X" % v[3] if alpha and v[3] != 255 else "")


def rgb_to_hsv(rgb: np.ndarray) -> np.ndarray:
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx, mn = rgb.max(-1), rgb.min(-1)
    d = mx - mn
    h = np.zeros_like(mx)
    nz = d > 1e-6
    rc = nz & (mx == r)
    gc = nz & (mx == g) & ~rc
    bc = nz & ~rc & ~gc
    h[rc] = ((g - b)[rc] / d[rc]) % 6
    h[gc] = (b - r)[gc] / d[gc] + 2
    h[bc] = (r - g)[bc] / d[bc] + 4
    s = np.where(mx > 1e-6, d / np.maximum(mx, 1e-6), 0)
    return np.stack([h / 6.0, s, mx], -1)


def hsv_to_rgb(hsv: np.ndarray) -> np.ndarray:
    h, s, v = hsv[..., 0] % 1.0, hsv[..., 1], hsv[..., 2]
    i = np.floor(h * 6).astype(int) % 6
    f = h * 6 - np.floor(h * 6)
    p, q, t = v * (1 - s), v * (1 - f * s), v * (1 - (1 - f) * s)
    out = np.zeros(hsv.shape, np.float32)
    for k, (a, b, c) in enumerate([(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)]):
        m = i == k
        out[m] = np.stack([a[m], b[m], c[m]], -1)
    return out


def shift_hex(color: str, dh: float, ds: float, dv: float) -> str:
    """Hue (degrees) / saturation / brightness (percent) shift of one hex colour."""
    if not color:
        return color
    c = np.array([parse_color(color)], np.float32)
    hsv = rgb_to_hsv(c[:, :3])
    hsv[:, 0] = (hsv[:, 0] + dh / 360.0) % 1.0
    hsv[:, 1] = np.clip(hsv[:, 1] * (1 + ds / 100.0), 0, 1)
    hsv[:, 2] = _brighten(hsv[:, 2], dv)
    rgb = hsv_to_rgb(hsv)[0]
    return to_hex((*rgb, c[0, 3]), alpha=True)


def _brighten(v, amount: float):
    return np.clip(v * (1 + amount / 100.0) if amount < 0 else v + (1 - v) * amount / 100.0, 0, 1)


# ================================================================ image sources

def find_client_jar() -> str | None:
    env = os.environ.get("ABYSSIA_MC_JAR")
    if env and os.path.isfile(env):
        return env
    home = os.path.expanduser("~")
    pats = [os.path.join(home, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", MC_VERSION, n)
            for n in ("client-extra.jar", "client.jar")]
    pats.append(os.path.join(home, ".gradle", "caches", "**", f"*{MC_VERSION}*client*.jar"))
    for pat in pats:
        for path in glob.glob(pat, recursive=True):
            try:
                with zipfile.ZipFile(path) as z:
                    if "assets/minecraft/textures/block/stone.png" in z.namelist():
                        return path
            except (OSError, zipfile.BadZipFile):
                continue
    return None


class Sources:
    """Loads ``vanilla:block/stone`` (client jar) and ``mod:block/abyssal_rock`` (assets) as uint8 RGBA arrays."""

    def __init__(self):
        self._zip = None
        self._zip_tried = False
        self._vanilla_names: dict[str, list[str]] | None = None
        self._cache: dict = {}

    def _jar(self):
        if not self._zip_tried:
            self._zip_tried = True
            path = find_client_jar()
            self._zip = zipfile.ZipFile(path) if path else None
        return self._zip

    @property
    def has_vanilla(self) -> bool:
        return self._jar() is not None

    def vanilla_names(self, kind: str) -> list[str]:
        if self._vanilla_names is None:
            self._vanilla_names = {k: [] for k in KINDS}
            z = self._jar()
            if z:
                for n in z.namelist():
                    for k in KINDS:
                        pre = f"assets/minecraft/textures/{k}/"
                        if n.startswith(pre) and n.endswith(".png") and "/" not in n[len(pre):]:
                            self._vanilla_names[k].append(f"{k}/{n[len(pre):-4]}")
            for k in KINDS:
                self._vanilla_names[k].sort()
        return self._vanilla_names[kind]

    def mod_names(self, kind: str) -> list[str]:
        d = os.path.join(TEX_DIR, kind)
        if not os.path.isdir(d):
            return []
        return sorted(f"{kind}/{f[:-4]}" for f in os.listdir(d) if f.endswith(".png"))

    def load(self, ref: str | None) -> np.ndarray | None:
        if not ref or ":" not in ref:
            return None
        scheme, path = ref.split(":", 1)
        try:
            if scheme == "vanilla":
                key = ref
                if key not in self._cache:
                    z = self._jar()
                    data = z.read(f"assets/minecraft/textures/{path}.png") if z else None
                    self._cache[key] = _frame(Image.open(io.BytesIO(data))) if data else None
                return self._cache[key]
            if scheme == "mod":
                file = os.path.join(TEX_DIR, *path.split("/")) + ".png"
                stamp = os.path.getmtime(file)
                hit = self._cache.get(ref)
                if hit is None or hit[0] != stamp:
                    self._cache[ref] = hit = (stamp, _frame(Image.open(file)))
                return hit[1]
        except (KeyError, OSError, ValueError):
            return None
        return None


def _frame(img: Image.Image) -> np.ndarray:
    img = img.convert("RGBA")
    w, h = img.size
    if h > w and h % w == 0:  # animation strip -> first frame
        img = img.crop((0, 0, w, w))
    return np.array(img, np.uint8)


def _resize(arr: np.ndarray, n: int) -> np.ndarray:
    if arr.shape[0] == n and arr.shape[1] == n:
        return arr
    return np.array(Image.fromarray(arr).resize((n, n), Image.NEAREST), np.uint8)


# ================================================================ layer schema (drives GUI + CLI + defaults)

def F(key, label, kind, default, **kw):
    return dict(key=key, label=label, kind=kind, default=default, **kw)


LAYER_TYPES: dict[str, dict] = {
    "image": dict(label="画像 (バニラ/Mod)", fields=[
        F("source", "元テクスチャ", "source", "vanilla:block/stone"),
        F("mode", "着色モード", "combo", "none", options=[("none", "そのまま"), ("adjust", "色相/彩度/明度"),
                                                    ("ramp", "グラデーションマップ"), ("tint", "乗算ティント")]),
        F("hue", "色相", "int", 0, min=-180, max=180, show_if=("mode", "adjust")),
        F("sat", "彩度", "int", 0, min=-100, max=100, show_if=("mode", "adjust")),
        F("val", "明るさ", "int", 0, min=-100, max=100, show_if=("mode", "adjust")),
        F("contrast", "コントラスト", "int", 0, min=-100, max=100, show_if=("mode", "adjust")),
        F("dark", "暗部の色", "color", "#1B2A3A", show_if=("mode", "ramp")),
        F("mid", "中間の色", "color", "", optional=True, show_if=("mode", "ramp")),
        F("light", "明部の色", "color", "#7FB4C8", show_if=("mode", "ramp")),
        F("normalize", "明度を全域に伸ばす", "check", True, show_if=("mode", "ramp")),
        F("tint", "ティント色", "color", "#5588CC", show_if=("mode", "tint")),
        F("strength", "強さ", "int", 100, min=0, max=100, show_if=("mode", "tint")),
        F("_h", "元画像の加工", "heading", None),
        F("extract_host", "ホストを差し引く", "source", "", optional=True,
          tip="ここに指定した画像と同じ色のピクセルを透明にする（鉱石の粒だけ取り出す等）"),
        F("extract_tol", "差し引きの許容差", "int", 8, min=0, max=100, show_if=("extract_host", "*")),
        F("flip_h", "左右反転", "check", False),
        F("flip_v", "上下反転", "check", False),
        F("rotate", "回転", "combo", "0", options=[("0", "0°"), ("90", "90°"), ("180", "180°"), ("270", "270°")]),
        F("shift_x", "ずらし X", "int", 0, min=-32, max=32),
        F("shift_y", "ずらし Y", "int", 0, min=-32, max=32),
    ]),
    "fill": dict(label="塗りつぶし / グラデ", fields=[
        F("color", "色", "color", "#3A5570"),
        F("color2", "色2 (グラデ)", "color", "", optional=True),
        F("direction", "方向", "combo", "vertical", show_if=("color2", "*"),
          options=[("vertical", "縦"), ("horizontal", "横"), ("diagonal", "斜め"), ("radial", "放射")]),
        F("steps", "段数 (0=滑らか)", "int", 0, min=0, max=16, show_if=("color2", "*")),
    ]),
    "noise": dict(label="ノイズ / まだら", fields=[
        F("seed", "シード", "seed", 1),
        F("color", "色", "color", "#000000"),
        F("color2", "色2 (混ぜる)", "color", "", optional=True),
        F("scale", "粒の大きさ", "int", 2, min=1, max=16),
        F("coverage", "範囲 %", "int", 40, min=0, max=100),
        F("soft", "ふんわり (半透明)", "check", False),
        F("smooth", "滑らかに補間", "check", False),
    ]),
    "cracks": dict(label="ひび / 切れ目", fields=[
        F("seed", "シード", "seed", 1),
        F("count", "本数", "int", 3, min=1, max=40),
        F("length", "長さ", "int", 14, min=2, max=64),
        F("branch", "枝分かれ %", "int", 10, min=0, max=60),
        F("wobble", "うねり", "int", 35, min=0, max=100),
        F("angle", "向き°", "int", 0, min=-180, max=180),
        F("spread", "向きのばらつき°", "int", 180, min=0, max=180),
        F("width", "太さ", "int", 1, min=1, max=3),
        F("gap", "途切れ %", "int", 0, min=0, max=90),
        F("wrap", "端でループ (タイル用)", "check", True),
        F("color", "色", "color", "#0B1218"),
        F("highlight", "ふち色 (2トーン)", "color", "", optional=True),
        F("hl_side", "ふちの位置", "combo", "down_right", show_if=("highlight", "*"),
          options=[("down_right", "右下"), ("up_left", "左上"), ("both", "両側")]),
    ]),
    "lines": dict(label="継ぎ目 / レンガ", fields=[
        F("pattern", "パターン", "combo", "bricks",
          options=[("bricks", "レンガ"), ("grid", "格子"), ("horizontal", "横線"), ("vertical", "縦線"),
                   ("diagonal", "斜線")]),
        F("cell_w", "幅", "int", 8, min=2, max=64),
        F("cell_h", "高さ", "int", 4, min=2, max=64),
        F("thickness", "線の太さ", "int", 1, min=1, max=4),
        F("stagger", "段ごとにずらす", "check", True, show_if=("pattern", "bricks")),
        F("phase_x", "位相 X", "int", 0, min=0, max=63),
        F("phase_y", "位相 Y", "int", 0, min=0, max=63),
        F("color", "線の色", "color", "#0B1218"),
        F("highlight", "ふち色", "color", "", optional=True),
        F("cell_shade", "ブロックごとの明暗 %", "int", 0, min=0, max=40),
        F("seed", "シード", "seed", 1, show_if=("cell_shade", ">0")),
    ]),
    "spots": dict(label="点 / 鉱石の粒", fields=[
        F("seed", "シード", "seed", 1),
        F("count", "個数", "int", 12, min=1, max=200),
        F("size_min", "最小サイズ(px)", "int", 1, min=1, max=10),
        F("size_max", "最大サイズ(px)", "int", 3, min=1, max=10),
        F("c1", "色1", "color", "#D9B84A"),
        F("c2", "色2", "color", "", optional=True),
        F("c3", "色3", "color", "", optional=True),
        F("shade", "立体感 (明/暗ピクセル)", "check", True),
        F("wrap", "端でループ (タイル用)", "check", True),
    ]),
    "border": dict(label="縁取り / ベベル", fields=[
        F("style", "種類", "combo", "bevel", options=[("bevel", "ベベル"), ("frame", "枠")]),
        F("width", "幅", "int", 1, min=1, max=4),
        F("color", "枠の色", "color", "#000000", show_if=("style", "frame")),
        F("light", "明るい辺 (左上)", "color", "#FFFFFF", show_if=("style", "bevel")),
        F("dark", "暗い辺 (右下)", "color", "#000000", show_if=("style", "bevel")),
    ]),
    "paint": dict(label="手描きピクセル", fields=[]),
    "forge": dict(label="Forge生成 (手続き)", fields=[]),   # custom form in gui_forge.py
}

COMMON = dict(visible=True, opacity=1.0, blend="normal", clip=False)


def new_layer(type_: str, **over) -> dict:
    d = {"type": type_, "name": LAYER_TYPES[type_]["label"].split(" ")[0]}
    d.update(COMMON)
    for f in LAYER_TYPES[type_]["fields"]:
        if f["kind"] != "heading":
            d[f["key"]] = f["default"]
    if type_ == "paint":
        d["data"] = ""
    if type_ == "forge":
        d.update(forge={"category": "terrain", "palette": "abyss", "seed": 1234}, source="", output="color")
    d.update(over)
    return d


def field_visible(layer: dict, f: dict) -> bool:
    cond = f.get("show_if")
    if not cond:
        return True
    key, want = cond
    val = layer.get(key)
    if want == "*":
        return bool(val)
    if want == ">0":
        return bool(val) and val > 0
    return val == want


# ================================================================ layer rendering

def _grid(n):
    ys, xs = np.mgrid[0:n, 0:n]
    return xs, ys


def _solid(n, mask, color, out=None):
    out = np.zeros((n, n, 4), np.float32) if out is None else out
    c = np.array(color, np.float32)
    out[mask] = c
    return out


def _shift(mask, dx, dy, wrap=True):
    r = np.roll(np.roll(mask, dy, 0), dx, 1)
    if not wrap:
        n = mask.shape[0]
        if dy > 0: r[:dy, :] = False
        if dy < 0: r[dy:, :] = False
        if dx > 0: r[:, :dx] = False
        if dx < 0: r[:, dx:] = False
    return r


def _highlight(out, mask, color, side, wrap=True):
    hl = np.zeros_like(mask)
    if side in ("down_right", "both"):
        hl |= _shift(mask, 1, 1, wrap)
    if side in ("up_left", "both"):
        hl |= _shift(mask, -1, -1, wrap)
    _solid(mask.shape[0], hl & ~mask, color, out)


def render_image(L: dict, n: int, src: Sources, self_key: str | None) -> np.ndarray:
    ref = L.get("source")
    arr = None if ref == f"mod:{self_key}" else src.load(ref)
    if arr is None:
        return np.zeros((n, n, 4), np.float32)
    arr = _resize(arr, n)
    host = L.get("extract_host")
    if host and host != f"mod:{self_key}":
        h = src.load(host)
        if h is not None:
            h = _resize(h, n).astype(np.int32)
            diff = np.abs(arr.astype(np.int32)[..., :3] - h[..., :3]).max(-1)
            same = (diff <= int(L.get("extract_tol", 8) * 2.55)) & (h[..., 3] > 0)
            arr = arr.copy()
            arr[same, 3] = 0
    if L.get("flip_h"): arr = arr[:, ::-1]
    if L.get("flip_v"): arr = arr[::-1]
    rot = int(L.get("rotate", 0) or 0)
    if rot: arr = np.rot90(arr, -rot // 90)
    sx, sy = int(L.get("shift_x", 0)), int(L.get("shift_y", 0))
    if sx or sy: arr = np.roll(np.roll(arr, sy, 0), sx, 1)
    px = arr.astype(np.float32) / 255.0
    rgb, a = px[..., :3], px[..., 3]
    mode = L.get("mode", "none")
    opaque = a > 0
    if mode == "adjust" and opaque.any():
        hsv = rgb_to_hsv(rgb)
        hsv[..., 0] = (hsv[..., 0] + L.get("hue", 0) / 360.0) % 1.0
        hsv[..., 1] = np.clip(hsv[..., 1] * (1 + L.get("sat", 0) / 100.0), 0, 1)
        hsv[..., 2] = _brighten(hsv[..., 2], L.get("val", 0))
        rgb = hsv_to_rgb(hsv)
        c = L.get("contrast", 0) / 100.0
        if c:
            pivot = rgb[opaque].mean()
            rgb = np.clip((rgb - pivot) * (1 + c) + pivot, 0, 1)
    elif mode == "ramp" and opaque.any():
        lum = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)
        if L.get("normalize", True):
            lo, hi = lum[opaque].min(), lum[opaque].max()
            t = (lum - lo) / max(hi - lo, 1e-4)
        else:
            t = lum
        t = np.clip(t, 0, 1)
        stops = [parse_color(L.get("dark"))[:3]]
        if L.get("mid"): stops.append(parse_color(L.get("mid"))[:3])
        stops.append(parse_color(L.get("light"))[:3])
        xs = np.linspace(0, 1, len(stops))
        stops = np.array(stops, np.float32)
        rgb = np.stack([np.interp(t, xs, stops[:, k]) for k in range(3)], -1).astype(np.float32)
    elif mode == "tint":
        s = L.get("strength", 100) / 100.0
        tint = np.array(parse_color(L.get("tint"))[:3], np.float32)
        rgb = rgb * (1 - s) + rgb * tint * s
    return np.dstack([np.clip(rgb, 0, 1), a]).astype(np.float32)


def render_fill(L, n):
    c1 = np.array(parse_color(L.get("color")), np.float32)
    if not L.get("color2"):
        return np.tile(c1, (n, n, 1))
    c2 = np.array(parse_color(L.get("color2")), np.float32)
    xs, ys = _grid(n)
    d = max(n - 1, 1)
    kind = L.get("direction", "vertical")
    if kind == "horizontal": t = xs / d
    elif kind == "diagonal": t = (xs + ys) / (2 * d)
    elif kind == "radial": t = np.clip(np.hypot(xs - d / 2, ys - d / 2) / (d / 2 * 1.2), 0, 1)
    else: t = ys / d
    steps = int(L.get("steps", 0))
    if steps > 1:
        t = np.floor(np.clip(t, 0, 0.9999) * steps) / (steps - 1)
    t = t[..., None].astype(np.float32)
    return c1 * (1 - t) + c2 * t


def render_noise(L, n):
    rng = np.random.default_rng(int(L.get("seed", 1)))
    g = max(1, int(L.get("scale", 2)))
    cells = max(1, math.ceil(n / g))
    base = rng.random((cells, cells)).astype(np.float32)
    if L.get("smooth") and g > 1:
        v = np.array(Image.fromarray(base, "F").resize((n, n), Image.BICUBIC), np.float32).clip(0, 1)
    else:
        idx = (np.arange(n) // g) % cells
        v = base[np.ix_(idx, idx)]
    cov = L.get("coverage", 40) / 100.0
    alpha = np.clip(1 - v / max(cov, 1e-3), 0, 1) if L.get("soft") else (v < cov).astype(np.float32)
    c1 = np.array(parse_color(L.get("color")), np.float32)
    rgb = np.tile(c1[:3], (n, n, 1))
    if L.get("color2"):
        c2 = np.array(parse_color(L.get("color2")), np.float32)
        pick = rng.random((n, n)) < 0.5
        rgb[pick] = c2[:3]
        alpha = alpha * np.where(pick, c2[3], c1[3])
    else:
        alpha = alpha * c1[3]
    return np.dstack([rgb, alpha]).astype(np.float32)


def render_cracks(L, n):
    rng = np.random.default_rng(int(L.get("seed", 1)))
    wrap = bool(L.get("wrap", True))
    mask = np.zeros((n, n), bool)
    width = int(L.get("width", 1))
    gap = L.get("gap", 0) / 100.0
    wob = L.get("wobble", 35) / 100.0
    branch = L.get("branch", 10) / 100.0
    base_ang = math.radians(L.get("angle", 0))
    spread = math.radians(L.get("spread", 180))

    offsets = {1: [(0, 0)], 2: [(0, 0), (1, 0)]}.get(width, [(0, 0), (1, 0), (0, 1), (1, 1)])

    def put(x, y):
        for ox, oy in offsets:
            xx, yy = x + ox, y + oy
            if wrap:
                mask[yy % n, xx % n] = True
            elif 0 <= xx < n and 0 <= yy < n:
                mask[yy, xx] = True

    def walk(x, y, ang, steps, depth):
        for _ in range(steps):
            if rng.random() >= gap:
                put(int(round(x)), int(round(y)))
            ang += rng.normal(0, wob * 0.5)
            x += math.cos(ang)
            y += math.sin(ang)
            if depth < 2 and rng.random() < branch:
                walk(x, y, ang + rng.choice([-1, 1]) * rng.uniform(0.5, 1.2), max(2, steps // 2), depth + 1)

    for _ in range(int(L.get("count", 3))):
        ang = base_ang + rng.uniform(-spread, spread) + (math.pi if rng.random() < 0.5 else 0)
        walk(rng.uniform(0, n), rng.uniform(0, n), ang, int(L.get("length", 14)), 0)
    out = _solid(n, mask, parse_color(L.get("color")))
    if L.get("highlight"):
        _highlight(out, mask, parse_color(L["highlight"]), L.get("hl_side", "down_right"), wrap)
    return out


def render_lines(L, n):
    xs, ys = _grid(n)
    cw, ch, t = int(L.get("cell_w", 8)), int(L.get("cell_h", 4)), int(L.get("thickness", 1))
    x, y = xs + int(L.get("phase_x", 0)), ys + int(L.get("phase_y", 0))
    pat = L.get("pattern", "bricks")
    row = y // ch
    if pat == "bricks":
        off = np.where((row % 2 == 1) & bool(L.get("stagger", True)), cw // 2, 0)
        hor = (y % ch) < t
        ver = (((x + off) % cw) < t) & ~hor
        cell = row * 1000 + (x + off) // cw
        mask = hor | ver
    elif pat == "grid":
        mask = ((y % ch) < t) | ((x % cw) < t)
        cell = row * 1000 + x // cw
    elif pat == "horizontal":
        mask = (y % ch) < t
        cell = row * 1000
    elif pat == "vertical":
        mask = (x % cw) < t
        cell = x // cw
    else:
        mask = ((x + y) % cw) < t
        cell = (x + y) // cw
    out = np.zeros((n, n, 4), np.float32)
    shade = L.get("cell_shade", 0) / 100.0
    if shade > 0:
        rng = np.random.default_rng(int(L.get("seed", 1)))
        ids = {}
        v = np.zeros((n, n), np.float32)
        for c in np.unique(cell):
            ids[c] = rng.uniform(-shade, shade)
        for c, s in ids.items():
            v[cell == c] = s
        out[..., :3] = np.where(v[..., None] > 0, 1.0, 0.0)
        out[..., 3] = np.abs(v)
    if L.get("highlight"):
        _highlight(out, mask, parse_color(L["highlight"]), "down_right")
    _solid(n, mask, parse_color(L.get("color")), out)
    return out


def render_spots(L, n):
    rng = np.random.default_rng(int(L.get("seed", 1)))
    cols = [parse_color(L.get(k)) for k in ("c1", "c2", "c3") if L.get(k)] or [(1, 1, 1, 1)]
    lo, hi = int(L.get("size_min", 1)), max(int(L.get("size_min", 1)), int(L.get("size_max", 3)))
    wrap = bool(L.get("wrap", True))
    out = np.zeros((n, n, 4), np.float32)

    def clamp(p):
        x, y = p
        if wrap: return (x % n, y % n)
        return (x, y) if 0 <= x < n and 0 <= y < n else None

    for _ in range(int(L.get("count", 12))):
        size = int(rng.integers(lo, hi + 1))
        c0 = clamp((int(rng.integers(0, n)), int(rng.integers(0, n))))
        if c0 is None: continue
        cells = [c0]
        for _ in range(size * 4):
            if len(cells) >= size: break
            bx, by = cells[int(rng.integers(0, len(cells)))]
            dx, dy = [(1, 0), (-1, 0), (0, 1), (0, -1)][int(rng.integers(0, 4))]
            p = clamp((bx + dx, by + dy))
            if p and p not in cells: cells.append(p)
        col = np.array(cols[int(rng.integers(0, len(cols)))], np.float32)
        cs = set(cells)
        for (x, y) in cells:
            c = col.copy()
            if L.get("shade", True) and len(cells) >= 3:
                if clamp((x - 1, y)) not in cs and clamp((x, y - 1)) not in cs:
                    c[:3] = c[:3] + (1 - c[:3]) * 0.35
                elif clamp((x + 1, y)) not in cs and clamp((x, y + 1)) not in cs:
                    c[:3] = c[:3] * 0.65
            out[y, x] = c
    return out


def render_border(L, n):
    xs, ys = _grid(n)
    w = int(L.get("width", 1))
    top, left, bottom, right = ys < w, xs < w, ys >= n - w, xs >= n - w
    out = np.zeros((n, n, 4), np.float32)
    if L.get("style", "bevel") == "frame":
        _solid(n, top | left | bottom | right, parse_color(L.get("color")), out)
    else:
        light = top | left
        dark = (bottom | right) & ~light
        _solid(n, dark, parse_color(L.get("dark")), out)
        _solid(n, light, parse_color(L.get("light")), out)
    return out


def decode_paint(data: str) -> np.ndarray | None:
    if not data:
        return None
    try:
        return np.array(Image.open(io.BytesIO(base64.b64decode(data))).convert("RGBA"), np.uint8)
    except Exception:
        return None


def encode_paint(arr: np.ndarray) -> str:
    buf = io.BytesIO()
    Image.fromarray(np.asarray(arr, np.uint8), "RGBA").save(buf, "PNG")
    return base64.b64encode(buf.getvalue()).decode("ascii")


def render_paint(L, n):
    arr = decode_paint(L.get("data", ""))
    if arr is None:
        return np.zeros((n, n, 4), np.float32)
    return _resize(arr, n).astype(np.float32) / 255.0


# ---- Texture Forge (procedural generators in tools/texture_forge, no GUI/AI involved)
FORGE_DIR = os.path.normpath(os.path.join(HERE, "..", "texture_forge"))
_forge_cache: dict = {}
_forge_gen = None


def forge_modules():
    """(TextureSettings, TextureGenerator, presets module, palette module) - imported lazily."""
    import sys
    if FORGE_DIR not in sys.path:
        sys.path.insert(0, FORGE_DIR)
    from core import palette, presets
    from core.generator import TextureGenerator
    from core.settings import TextureSettings
    return TextureSettings, TextureGenerator, presets, palette


def forge_settings(data: dict, size: int):
    TextureSettings = forge_modules()[0]
    s = TextureSettings.from_dict(dict(data))
    s.size = size
    return s.clamp()


def forge_generate(data: dict, size: int, source: np.ndarray | None, output: str = "color") -> np.ndarray:
    """Run one Forge generation -> uint8 RGBA (size x size).  Cached per (settings, source)."""
    global _forge_gen
    key = (json.dumps(data, sort_keys=True), size, output, None if source is None else source.tobytes())
    hit = _forge_cache.get(key)
    if hit is not None:
        return hit
    _, TextureGenerator, _, _ = forge_modules()
    _forge_gen = _forge_gen or TextureGenerator()
    res = _forge_gen.generate(Image.fromarray(source, "RGBA") if source is not None else None,
                              forge_settings(data, size))
    img = res.emission if output == "emission" else res.image
    arr = np.array(img, np.uint8) if img is not None else np.zeros((size, size, 4), np.uint8)
    if len(_forge_cache) > 64:
        _forge_cache.pop(next(iter(_forge_cache)))
    _forge_cache[key] = arr
    return arr


def render_forge(L, n, src: Sources, self_key):
    ref = L.get("source")
    ref_arr = None if not ref or ref == f"mod:{self_key}" else src.load(ref)
    try:
        arr = forge_generate(L.get("forge", {}), n, ref_arr, L.get("output", "color"))
    except Exception as exc:  # a bad setting must not break the whole editor
        print(f"forge layer failed: {type(exc).__name__}: {exc}")
        return np.zeros((n, n, 4), np.float32)
    return _resize(arr, n).astype(np.float32) / 255.0


def forge_presets() -> dict[str, dict]:
    """Forge's own texture presets (label -> partial settings dict)."""
    presets = forge_modules()[2]
    out = {}
    for p in presets.list_presets():
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            continue
        out[presets.preset_label(p)] = {k: v for k, v in data.items() if k not in ("label", "description")}
    return out


def forge_batch_presets() -> dict[str, list[dict]]:
    """Forge batch (pack) presets: label -> list of fully resolved compact settings dicts."""
    TextureSettings, _, presets, _ = forge_modules()
    out = {}
    for p in presets.list_batch_presets():
        try:
            bp = presets.load_batch_preset(p)
            out[bp.name] = [presets.compact_dict(s) for s in bp.jobs()]
        except Exception:
            continue
    return out


def save_forge_preset(name: str, data: dict) -> None:
    TextureSettings, _, presets, _ = forge_modules()
    presets.save_preset(TextureSettings.from_dict(dict(data)), name, label=name)


def render_layer(L: dict, n: int, src: Sources, self_key: str | None = None) -> np.ndarray:
    """One layer alone, straight-alpha float RGBA (before opacity/blend)."""
    t = L["type"]
    if t == "image": return render_image(L, n, src, self_key)
    if t == "fill": return render_fill(L, n)
    if t == "noise": return render_noise(L, n)
    if t == "cracks": return render_cracks(L, n)
    if t == "lines": return render_lines(L, n)
    if t == "spots": return render_spots(L, n)
    if t == "border": return render_border(L, n)
    if t == "paint": return render_paint(L, n)
    if t == "forge": return render_forge(L, n, src, self_key)
    return np.zeros((n, n, 4), np.float32)


# ================================================================ compositing

def _blend_rgb(mode, d, s):
    if mode == "multiply": return d * s
    if mode == "screen": return 1 - (1 - d) * (1 - s)
    if mode == "overlay": return np.where(d < 0.5, 2 * d * s, 1 - 2 * (1 - d) * (1 - s))
    if mode == "add": return np.minimum(d + s, 1)
    if mode == "darken": return np.minimum(d, s)
    if mode == "lighten": return np.maximum(d, s)
    return s


def composite(dst, src, blend="normal", opacity=1.0, clip=False):
    sa = src[..., 3:4] * float(opacity)
    da = dst[..., 3:4]
    if clip and da.max() > 0:
        sa = sa * da
    if blend == "erase":
        return np.concatenate([dst[..., :3], da * (1 - sa)], -1)
    b = _blend_rgb(blend, dst[..., :3], src[..., :3])
    cs = (1 - da) * src[..., :3] + da * b
    oa = sa + da * (1 - sa)
    rgb = np.where(oa > 1e-6, ((1 - sa) * da * dst[..., :3] + sa * cs) / np.maximum(oa, 1e-6), 0)
    return np.concatenate([rgb, oa], -1).astype(np.float32)


def render(spec: dict, src: Sources | None = None, self_key: str | None = None, upto: int | None = None) -> np.ndarray:
    """Whole spec -> uint8 RGBA (n, n, 4).  ``upto`` renders only layers[:upto]."""
    src = src or Sources()
    n = int(spec.get("size", 16))
    out = np.zeros((n, n, 4), np.float32)
    layers = spec["layers"] if upto is None else spec["layers"][:upto]
    for L in layers:
        if not L.get("visible", True):
            continue
        out = composite(out, render_layer(L, n, src, self_key), L.get("blend", "normal"),
                        L.get("opacity", 1.0), L.get("clip", False))
    rgb = np.where(out[..., 3:4] > 0, out[..., :3], 0)
    return np.round(np.dstack([rgb, out[..., 3]]).clip(0, 1) * 255).astype(np.uint8)


# ================================================================ bulk helpers

def shift_layer_hsv(L: dict, dh: float, ds: float, dv: float) -> None:
    """Recolour one layer in place: hue (deg), saturation and brightness (percent)."""
    t = L["type"]
    if t == "image":
        mode = L.get("mode", "none")
        if mode == "none":
            L["mode"] = "adjust"
            mode = "adjust"
        if mode == "adjust":
            L["hue"] = int(max(-180, min(180, ((L.get("hue", 0) + dh + 180) % 360) - 180)))
            L["sat"] = int(max(-100, min(100, L.get("sat", 0) + ds)))
            L["val"] = int(max(-100, min(100, L.get("val", 0) + dv)))
            return
    if t == "forge":
        f = L.setdefault("forge", {})
        f["hue"] = min(1.0, max(0.0, f.get("hue", 0.5) + dh / 360.0))
        f["saturation"] = min(1.0, max(0.0, f.get("saturation", 0.5) + ds / 200.0))
        f["brightness"] = min(1.0, max(0.0, f.get("brightness", 0.5) + dv / 200.0))
        return
    if t == "paint":
        arr = decode_paint(L.get("data", ""))
        if arr is not None:
            px = arr.astype(np.float32) / 255
            hsv = rgb_to_hsv(px[..., :3])
            hsv[..., 0] = (hsv[..., 0] + dh / 360) % 1
            hsv[..., 1] = np.clip(hsv[..., 1] * (1 + ds / 100), 0, 1)
            hsv[..., 2] = _brighten(hsv[..., 2], dv)
            px[..., :3] = hsv_to_rgb(hsv)
            L["data"] = encode_paint(np.round(px * 255))
        return
    for f in LAYER_TYPES[t]["fields"]:
        if f["kind"] == "color" and L.get(f["key"]):
            L[f["key"]] = shift_hex(L[f["key"]], dh, ds, dv)


# ================================================================ spec storage + export

def spec_path(key: str) -> str:
    return os.path.join(SPEC_DIR, *key.split("/")) + ".json"


def png_path(key: str) -> str:
    return os.path.join(TEX_DIR, *key.split("/")) + ".png"


def new_spec(size: int = 16, layers: list | None = None) -> dict:
    return {"version": SPEC_VERSION, "size": size, "notes": "", "layers": layers or []}


def load_spec(key: str) -> dict | None:
    try:
        with open(spec_path(key), encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def save_spec(key: str, spec: dict) -> None:
    path = spec_path(key)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(spec, f, ensure_ascii=False, indent=1)
        f.write("\n")


def delete_spec(key: str) -> None:
    try:
        os.remove(spec_path(key))
    except OSError:
        pass


def managed_keys() -> list[str]:
    out = []
    for kind in KINDS:
        d = os.path.join(SPEC_DIR, kind)
        if os.path.isdir(d):
            out += [f"{kind}/{f[:-5]}" for f in sorted(os.listdir(d)) if f.endswith(".json")]
    return out


def spec_from_png(key: str) -> dict | None:
    """Wrap an existing PNG as a one-layer (hand paint) spec so it can be edited and bulk-recoloured."""
    try:
        img = Image.open(png_path(key))
    except OSError:
        return None
    arr = _frame(img)
    return new_spec(arr.shape[0], [new_layer("paint", name="取り込み", data=encode_paint(arr))])


def is_locked(key: str) -> bool:
    return os.path.isfile(os.path.join(LOCK_DIR, *key.split("/")) + ".png")


def export(key: str, spec: dict, src: Sources | None = None, dry_run: bool = False) -> bool:
    """Render ``spec`` into the mod's textures folder.  Returns True when the PNG changed.

    A locked texture (tools/texture_locks) also gets its lock file replaced: exporting is the user asking for the
    change, and the lock would otherwise put the old picture back on the next generator run.
    """
    arr = render(spec, src, key)
    buf = io.BytesIO()
    Image.fromarray(arr, "RGBA").save(buf, "PNG")
    data = buf.getvalue()
    path = png_path(key)
    same = False
    try:
        with open(path, "rb") as f:
            same = np.array_equal(_frame(Image.open(f)), arr)
    except OSError:
        pass
    if same:
        return False
    if not dry_run:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
        if is_locked(key):
            shutil.copyfile(path, os.path.join(LOCK_DIR, *key.split("/")) + ".png")
    return True


def is_outdated(key: str, spec: dict, src: Sources | None = None) -> bool:
    return export(key, spec, src, dry_run=True)


def apply_all(quiet: bool = False) -> int:
    """Export every spec (generator hook).  Returns the number of PNGs rewritten."""
    src = Sources()
    changed = 0
    for key in managed_keys():
        spec = load_spec(key)
        if spec and export(key, spec, src):
            changed += 1
    if not quiet:
        print(f"Texture Studio: {len(managed_keys())} managed textures, {changed} written")
    return changed


# ================================================================ presets

def builtin_presets() -> dict[str, dict]:
    P = new_layer
    return {
        "ひび (暗)": P("cracks", name="ひび", count=3, length=14, color="#0B1218"),
        "ひび (発光ライン)": P("cracks", name="発光ひび", count=3, length=16, color="#8FF3FF", highlight="#2A6C86",
                          hl_side="both", blend="screen"),
        "切れ目 (穴あき/透過)": P("cracks", name="切れ目", count=4, length=8, angle=0, spread=25, gap=10,
                            color="#000000", blend="erase", wrap=False),
        "レンガ継ぎ目": P("lines", name="レンガ", pattern="bricks", cell_w=8, cell_h=4, color="#0B1218",
                      highlight="#FFFFFF33", cell_shade=6),
        "タイル格子": P("lines", name="タイル", pattern="grid", cell_w=8, cell_h=8, color="#0B1218"),
        "ベベル縁": P("border", name="ベベル", style="bevel", light="#FFFFFF55", dark="#00000066"),
        "暗い枠": P("border", name="枠", style="frame", width=1, color="#000000", opacity=0.6),
        "苔むしパッチ": P("noise", name="苔", color="#3E7A4C", color2="#2C5A38", scale=3, coverage=35, seed=7, clip=True),
        "湿った暗部": P("noise", name="湿り", color="#000000", scale=4, coverage=45, soft=True, opacity=0.35, seed=3, clip=True),
        "粒ノイズ (グレイン)": P("noise", name="グレイン", color="#FFFFFF", color2="#000000", scale=1, coverage=100,
                          soft=True, opacity=0.18, seed=2, clip=True),
        "霜・白パッチ": P("noise", name="霜", color="#E8F4FF", scale=3, coverage=30, seed=11, opacity=0.8, clip=True),
        "鉱石の粒 (金)": P("spots", name="鉱石の粒", count=10, c1="#E8C24A", c2="#B8902A", c3="#FFE58A", clip=True),
        "鉱石の粒 (青)": P("spots", name="鉱石の粒", count=10, c1="#4AA8E8", c2="#2A78B8", c3="#8AD0FF", clip=True),
        "縦グラデ暗く": P("fill", name="グラデ", color="#00000000", color2="#000000AA", direction="vertical", clip=True),
    }


def user_presets() -> dict[str, dict]:
    out = {}
    if os.path.isdir(PRESET_DIR):
        for f in sorted(os.listdir(PRESET_DIR)):
            if f.endswith(".json"):
                try:
                    with open(os.path.join(PRESET_DIR, f), encoding="utf-8") as fh:
                        out[f[:-5]] = json.load(fh)
                except (OSError, ValueError):
                    pass
    return out


def save_user_preset(name: str, layer: dict) -> None:
    os.makedirs(PRESET_DIR, exist_ok=True)
    with open(os.path.join(PRESET_DIR, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(layer, f, ensure_ascii=False, indent=1)


def clone(layer: dict) -> dict:
    return copy.deepcopy(layer)
