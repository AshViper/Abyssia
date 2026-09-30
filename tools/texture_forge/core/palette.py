"""Colour science, palette extraction and palette generation.

Colours are handled in OKLab / OKLCh so that ramps interpolate with even
perceived steps.  Generated textures never reuse the source palette:
the source only contributes the *structure* of its ramp (how many
lightness steps and how they are spaced) while hues come from a theme.
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

RGB = tuple[int, int, int]

# ------------------------------------------------------------ conversions


def hex_to_rgb(value: str) -> RGB:
    v = value.strip().lstrip("#")
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    return int(v[0:2], 16), int(v[2:4], 16), int(v[4:6], 16)


def rgb_to_hex(c) -> str:
    return "#{:02X}{:02X}{:02X}".format(*(int(round(x)) for x in c[:3]))


def _srgb_to_linear(c: np.ndarray) -> np.ndarray:
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def _linear_to_srgb(c: np.ndarray) -> np.ndarray:
    c = np.clip(c, 0.0, 1.0)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * np.power(c, 1 / 2.4) - 0.055)


def rgb_to_oklab(rgb) -> np.ndarray:
    """``rgb`` in 0..255 (any shape ``(..., 3)``) -> OKLab."""
    c = _srgb_to_linear(np.asarray(rgb, dtype=np.float64)[..., :3] / 255.0)
    l = 0.4122214708 * c[..., 0] + 0.5363325363 * c[..., 1] + 0.0514459929 * c[..., 2]
    m = 0.2119034982 * c[..., 0] + 0.6806995451 * c[..., 1] + 0.1073969566 * c[..., 2]
    s = 0.0883024619 * c[..., 0] + 0.2817188376 * c[..., 1] + 0.6299787005 * c[..., 2]
    l, m, s = np.cbrt(l), np.cbrt(m), np.cbrt(s)
    return np.stack([
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    ], axis=-1)


def oklab_to_rgb(lab) -> np.ndarray:
    """OKLab -> sRGB 0..255 (float, clipped)."""
    lab = np.asarray(lab, dtype=np.float64)
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return np.clip(_linear_to_srgb(np.stack([r, g, bl], axis=-1)) * 255.0, 0, 255)


def oklab_to_lch(lab: np.ndarray) -> np.ndarray:
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    return np.stack([L, np.hypot(a, b), np.degrees(np.arctan2(b, a)) % 360.0], axis=-1)


def lch_to_oklab(lch: np.ndarray) -> np.ndarray:
    L, C, h = lch[..., 0], lch[..., 1], np.radians(lch[..., 2])
    return np.stack([L, C * np.cos(h), C * np.sin(h)], axis=-1)


def luminance(rgb) -> np.ndarray:
    """Perceptual lightness (OKLab L, 0..1)."""
    return rgb_to_oklab(rgb)[..., 0]


def to_rgb_tuple(c) -> RGB:
    return tuple(int(round(float(x))) for x in c[:3])  # type: ignore[return-value]


# ------------------------------------------------------------------ ramps


@dataclass
class Ramp:
    """Ordered colours from darkest to lightest."""
    colors: list[RGB]
    name: str = ""

    def __len__(self) -> int:
        return len(self.colors)

    def at(self, level: int) -> RGB:
        return self.colors[int(np.clip(level, 0, len(self.colors) - 1))]

    def as_array(self) -> np.ndarray:
        return np.array(self.colors, dtype=np.float64)

    def hex(self) -> list[str]:
        return [rgb_to_hex(c) for c in self.colors]


# Role names used by generators.  "base" is the body material, "accent"
# is ore / mineral / highlight material, "glow" is emissive colour,
# "secondary" is an optional second body material (mud, crust, stem...).
ROLES = ("base", "secondary", "accent", "accent2", "glow")


@dataclass
class Palette:
    name: str
    ramps: dict[str, Ramp] = field(default_factory=dict)

    def ramp(self, role: str) -> Ramp:
        if role in self.ramps:
            return self.ramps[role]
        fallback = {"secondary": "base", "accent2": "accent", "glow": "accent"}.get(role, "base")
        return self.ramps.get(fallback) or next(iter(self.ramps.values()))

    def all_colors(self) -> list[RGB]:
        seen: list[RGB] = []
        for r in self.ramps.values():
            for c in r.colors:
                if c not in seen:
                    seen.append(c)
        return seen

    def to_dict(self) -> dict:
        return {"name": self.name, "ramps": {k: r.hex() for k, r in self.ramps.items()}}

    @classmethod
    def from_dict(cls, d: dict) -> "Palette":
        return cls(d.get("name", "custom"),
                   {k: Ramp([hex_to_rgb(c) for c in v], k) for k, v in d.get("ramps", {}).items()})


# ---------------------------------------------------------- named colours
# Anchor colours (dark -> light).  Ramps of any length are interpolated
# from these; they are hand-picked for deep-sea moods, not sampled from
# any existing texture.

RAMP_ANCHORS: dict[str, list[str]] = {
    # body materials
    "deep_ocean": ["#0C1519", "#17252B", "#263D43", "#3B5A5D"],
    "abyss": ["#0B0E13", "#161B23", "#232A34", "#343D4A", "#4B5664"],
    "trench": ["#060B18", "#0E1A30", "#1A2C4A", "#2A4368", "#3F5D86"],
    "thermal": ["#0A0706", "#1C100D", "#3A1A12", "#5E2A18"],
    "volcanic": ["#120C0B", "#2A1C19", "#45302A", "#66483D"],
    "crystal": ["#1A1433", "#2E2757", "#4A3F85", "#7466B8", "#A99BE6"],
    "bioluminescent": ["#050C12", "#0B1A24", "#133040", "#1F4A5E"],
    "ancient": ["#1A1712", "#2F2A20", "#4A4232", "#6B604A", "#8F836A"],
    "cold": ["#0E1419", "#1E2A33", "#33444F", "#51646F", "#7D909A"],
    "organic": ["#15100D", "#2C2019", "#4B3627", "#6E5238"],
    "stone_gray": ["#2E3033", "#4A4D51", "#66696D", "#85888B", "#A3A5A7"],
    "mud": ["#120F0C", "#231D17", "#3A3025", "#554636"],
    "sediment": ["#1C1C1E", "#2F2F31", "#46464A", "#626166", "#7E7C80"],
    "white_smoker": ["#4A4A4C", "#707072", "#9C9A96", "#C4C0B8", "#E6E2D8"],
    "black_smoker": ["#050505", "#0F0E0E", "#1C1A19", "#2C2926"],
    "dark_metal": ["#15171B", "#262A31", "#3C424B", "#5A626D", "#8A939E"],
    # accents / minerals
    "sulfur": ["#6E5410", "#B8901A", "#E8C93A", "#FFF08A"],
    "cyan_mineral": ["#0F4A4A", "#1F7F7A", "#3FB8A8", "#8FF0DC"],
    "teal": ["#1F5C5C", "#2E8C85", "#5CC7B8"],
    "manganese": ["#141418", "#2A2A33", "#4A4A5C", "#7A7A96", "#B8B0E0"],
    "copper": ["#4A2410", "#8A4A20", "#C8763A", "#F0B070"],
    "pyrite": ["#3A3014", "#7A6A2A", "#BFA548", "#F0E08A"],
    "heat": ["#5A1405", "#B8380C", "#F0761A", "#FFD060"],
    "amethyst": ["#3A1F5C", "#6A3FA0", "#A070E0", "#E0C8FF"],
    "emerald": ["#0A3A1A", "#1A7A3A", "#3FC06A", "#A0F0B8"],
    "ruby": ["#4A0A14", "#8A1428", "#D0324A", "#FF94A0"],
    "gold": ["#5A3A08", "#A07010", "#E0B030", "#FFF0A0"],
    "silver": ["#3A3F48", "#6A717C", "#A6AEB8", "#E4E8EE"],
    "glow_cyan": ["#0A6070", "#20C0D0", "#9FF6FF", "#EFFFFF"],
    "glow_green": ["#0A5A3A", "#1DBF80", "#7CF2B8", "#E0FFF0"],
    "glow_violet": ["#3A1470", "#7A3AE0", "#C090FF", "#F4E8FF"],
    "blue_ice": ["#1E4A7A", "#3A7AC0", "#7AB8F0", "#D8F0FF"],
    "rust": ["#3A1408", "#6A2A10", "#9A4A20", "#C87A40"],
    "patina": ["#1A4A40", "#2E7A68", "#58A890", "#9CD8C0"],
    "moss": ["#2A3A14", "#46601E", "#6A8A2E", "#9AB84A"],
    "bone": ["#6A6250", "#948A70", "#BEB496", "#E8E0C8"],
    "coral_pink": ["#5A1A2A", "#9A3048", "#D8607A", "#FFB0C0"],
    # plants
    "kelp_green": ["#0E2A14", "#1F4A22", "#36702E", "#5E9A3E", "#9CC45A"],
    "deep_kelp": ["#08201A", "#12382C", "#1F5442", "#317258", "#5A9A78"],
    "abyssal_kelp": ["#0F0A1E", "#1F1838", "#2E2A5C", "#3F4A80", "#5A8AA8"],
    "thermal_kelp": ["#2A0E08", "#4A1E0E", "#7A3A18", "#A8602A", "#D89A48"],
    "giant_kelp": ["#241A08", "#3F3010", "#5E4A1A", "#857030", "#B09A48"],
    "abyssal_grass": ["#061A1A", "#0E302E", "#174A44", "#22685C", "#3A9080"],
    # wood
    "driftwood": ["#2A231C", "#43372B", "#5E4E3C", "#7D6A52", "#A08B6C"],
    "ancient_wood": ["#1E2A26", "#33443C", "#4B6053", "#667E6C", "#8AA08C"],
    "bark": ["#140F0C", "#241B15", "#382A20", "#4E3B2C", "#66503C"],
}


@dataclass(frozen=True)
class PaletteTheme:
    name: str
    label: str
    base: str
    accent: str
    glow: str
    secondary: str | None = None
    accent2: str | None = None
    description: str = ""


THEMES: dict[str, PaletteTheme] = {t.name: t for t in [
    PaletteTheme("deep_ocean", "Deep Ocean", "deep_ocean", "teal", "glow_cyan", "sediment",
                 description="Blue-green slate of the continental slope"),
    PaletteTheme("abyss", "Abyss", "abyss", "cyan_mineral", "glow_cyan", "black_smoker",
                 description="Near-black cold rock with faint teal minerals"),
    PaletteTheme("trench", "Trench", "trench", "blue_ice", "glow_violet", "abyss",
                 description="Crushing dark blue of the hadal zone"),
    PaletteTheme("thermal", "Thermal", "thermal", "sulfur", "heat", "black_smoker", "cyan_mineral",
                 description="Black rock, dark red heat, sulfur crust"),
    PaletteTheme("volcanic", "Volcanic", "volcanic", "heat", "heat", "thermal",
                 description="Ash-brown basalt with glowing seams"),
    PaletteTheme("crystal", "Crystal", "crystal", "glow_cyan", "glow_violet", "trench", "amethyst",
                 description="Violet crystal bodies with cyan light"),
    PaletteTheme("bioluminescent", "Bioluminescent", "bioluminescent", "glow_green", "glow_cyan",
                 "deep_ocean", description="Dark water-blue with living light"),
    PaletteTheme("ancient", "Ancient", "ancient", "patina", "glow_cyan", "sediment",
                 description="Weathered ruin stone with patina"),
    PaletteTheme("cold", "Cold", "cold", "blue_ice", "glow_cyan", "sediment",
                 description="Frigid pale slate"),
    PaletteTheme("organic", "Organic", "organic", "moss", "glow_green", "mud",
                 description="Brown-green living matter"),
]}

# Accent presets offered separately from themes (ore / mineral colours).
ACCENT_PRESETS: dict[str, tuple[str, str | None]] = {
    "sulfur": ("sulfur", None),
    "sulfur_cyan": ("sulfur", "cyan_mineral"),
    "cyan_mineral": ("cyan_mineral", None),
    "manganese": ("manganese", "glow_violet"),
    "copper": ("copper", "patina"),
    "pyrite": ("pyrite", None),
    "heat": ("heat", "sulfur"),
    "amethyst": ("amethyst", None),
    "emerald": ("emerald", None),
    "ruby": ("ruby", None),
    "gold": ("gold", None),
    "silver": ("silver", None),
    "glow_cyan": ("glow_cyan", None),
    "glow_green": ("glow_green", None),
    "glow_violet": ("glow_violet", None),
    "rust": ("rust", None),
    "patina": ("patina", None),
    "bone": ("bone", None),
    "coral_pink": ("coral_pink", "glow_cyan"),
    "cyan_amethyst": ("cyan_mineral", "amethyst"),
    "blue_violet": ("blue_ice", "amethyst"),
}


def theme_names() -> list[str]:
    return list(THEMES)


def accent_names() -> list[str]:
    return list(ACCENT_PRESETS)


# ----------------------------------------------------------- ramp building


def _interp_anchor_lab(anchors_lab: np.ndarray, t: np.ndarray) -> np.ndarray:
    n = len(anchors_lab)
    if n == 1:
        return np.repeat(anchors_lab, len(t), axis=0)
    pos = np.clip(t, 0, 1) * (n - 1)
    i0 = np.clip(np.floor(pos).astype(int), 0, n - 2)
    f = (pos - i0)[:, None]
    return anchors_lab[i0] * (1 - f) + anchors_lab[i0 + 1] * f


def build_ramp(anchors: list[str] | list[RGB], n: int, structure: list[float] | None = None,
               hue_shift: float = 0.35, name: str = "") -> Ramp:
    """Interpolate ``n`` colours through ``anchors`` (dark -> light).

    ``structure`` optionally gives the relative positions (0..1) of each
    level, e.g. the lightness spacing measured from a reference texture.
    ``hue_shift`` bends shadows toward blue and highlights toward yellow,
    the classic pixel-art ramp treatment.
    """
    n = max(1, int(n))
    rgbs = [hex_to_rgb(a) if isinstance(a, str) else a for a in anchors]
    lab = rgb_to_oklab(np.array(rgbs, dtype=np.float64))
    if structure is not None and len(structure) == n:
        t = np.clip(np.asarray(structure, dtype=np.float64), 0, 1)
    else:
        t = np.linspace(0, 1, n) if n > 1 else np.array([0.5])
    out = _interp_anchor_lab(lab, t)
    if hue_shift and n > 1:
        lch = oklab_to_lch(out)
        for i, ti in enumerate(t):
            w = (ti - 0.5) * 2  # -1 shadow .. +1 highlight
            target = 95.0 if w > 0 else 265.0
            amt = abs(w) * 0.22 * hue_shift * min(1.0, lch[i, 1] / 0.04)
            dh = ((target - lch[i, 2] + 180) % 360) - 180
            lch[i, 2] = (lch[i, 2] + dh * amt) % 360
        out = lch_to_oklab(lch)
    return Ramp([to_rgb_tuple(c) for c in oklab_to_rgb(out)], name)


def ramp_from_color(base: RGB | str, n: int, spread: float = 0.5, hue_shift: float = 0.5,
                    name: str = "custom") -> Ramp:
    """Build a ramp centred on a single user colour."""
    rgb = hex_to_rgb(base) if isinstance(base, str) else base
    lch = oklab_to_lch(rgb_to_oklab(np.array(rgb, dtype=np.float64)))
    L, C, h = float(lch[0]), float(lch[1]), float(lch[2])
    span = 0.18 + 0.35 * spread
    lo = max(0.06, L - span * 0.6)
    hi = min(0.97, L + span * 0.4)
    anchors = []
    for tl in np.linspace(0, 1, 4):
        Lx = lo + (hi - lo) * tl
        Cx = C * (0.75 + 0.35 * np.sin(np.pi * tl))
        anchors.append(to_rgb_tuple(oklab_to_rgb(lch_to_oklab(np.array([Lx, Cx, h])))))
    return build_ramp(anchors, n, hue_shift=hue_shift, name=name)


def anchors_for(name: str) -> list[str]:
    """Anchor colours for a ramp name, a single hex colour, or several hex colours (dark -> light, comma separated)."""
    if name in RAMP_ANCHORS:
        return RAMP_ANCHORS[name]
    if "," in name:
        return [h.strip() for h in name.split(",") if h.strip()]
    if name.startswith("#"):
        return ramp_from_color(name, 4).hex()
    return RAMP_ANCHORS["stone_gray"]


# ------------------------------------------------------------- adjustments


@dataclass
class ColorAdjust:
    """All values 0..1 with 0.5 meaning 'no change'."""
    hue: float = 0.5
    saturation: float = 0.5
    brightness: float = 0.5
    contrast: float = 0.5
    temperature: float = 0.5
    tint: float = 0.5

    def is_neutral(self) -> bool:
        return all(abs(v - 0.5) < 1e-6 for v in (self.hue, self.saturation, self.brightness,
                                                  self.contrast, self.temperature, self.tint))


def adjust_colors(colors: np.ndarray, adj: ColorAdjust, pivot: float | None = None) -> np.ndarray:
    """Apply hue/sat/brightness/contrast/temperature/tint to RGB colours.

    This is applied to *palette entries* rather than pixels, so the number
    of colours never grows.
    """
    lab = rgb_to_oklab(colors)
    lch = oklab_to_lch(lab)
    lch[..., 2] = (lch[..., 2] + (adj.hue - 0.5) * 360.0) % 360
    lch[..., 1] *= (2.0 * adj.saturation) ** 1.25
    lab = lch_to_oklab(lch)
    L = lab[..., 0]
    mid = float(np.mean(L)) if pivot is None else pivot
    L = mid + (L - mid) * (2.0 ** ((adj.contrast - 0.5) * 2.4))
    L = L + (adj.brightness - 0.5) * 0.45
    lab[..., 0] = np.clip(L, 0.0, 1.0)
    lab[..., 1] += (adj.temperature - 0.5) * 0.03 + (adj.tint - 0.5) * -0.08
    lab[..., 2] += (adj.temperature - 0.5) * 0.10
    return oklab_to_rgb(lab)


def adjust_ramp(ramp: Ramp, adj: ColorAdjust, pivot: float | None = None) -> Ramp:
    if adj.is_neutral():
        return ramp
    arr = adjust_colors(ramp.as_array(), adj, pivot)
    return Ramp([to_rgb_tuple(c) for c in arr], ramp.name)


def adjust_palette(pal: Palette, adj: ColorAdjust) -> Palette:
    if adj.is_neutral():
        return pal
    pivot = float(np.mean(luminance(pal.ramp("base").as_array())))
    return Palette(pal.name, {k: adjust_ramp(r, adj, pivot) for k, r in pal.ramps.items()})


# ---------------------------------------------------------- palette creation


def generate_palette(theme: str, base_levels: int, structure: list[float] | None = None,
                     accent: str | None = None, rng: np.random.Generator | None = None,
                     accent_levels: int = 4, hue_shift: float = 0.35,
                     base_override: str | None = None) -> Palette:
    """Create a fresh palette for a theme.

    ``structure`` (from the analyzer) shapes the lightness spacing of the
    base ramp; ``rng`` adds a slight per-seed hue drift so variations do
    not share identical colours.
    """
    th = THEMES.get(theme) or THEMES["abyss"]
    base_anchors = anchors_for(base_override or th.base)
    if rng is not None:
        base_anchors = _drift(base_anchors, rng, 6.0)
    ramps = {
        "base": build_ramp(base_anchors, base_levels, structure, hue_shift, "base"),
        "secondary": build_ramp(anchors_for(th.secondary or th.base), max(3, base_levels - 1),
                                None, hue_shift, "secondary"),
        "glow": build_ramp(anchors_for(th.glow), 4, None, 0.1, "glow"),
    }
    acc_main, acc_second = th.accent, th.accent2
    if accent:
        if accent in ACCENT_PRESETS:
            acc_main, acc_second = ACCENT_PRESETS[accent]
        else:
            acc_main, acc_second = accent, None
    ramps["accent"] = build_ramp(anchors_for(acc_main), accent_levels, None, hue_shift, "accent")
    ramps["accent2"] = build_ramp(anchors_for(acc_second or acc_main), max(3, accent_levels - 1),
                                  None, hue_shift, "accent2")
    return Palette(th.name, ramps)


def _drift(anchors: list[str], rng: np.random.Generator, degrees: float) -> list[str]:
    lab = rgb_to_oklab(np.array([hex_to_rgb(a) for a in anchors], dtype=np.float64))
    lch = oklab_to_lch(lab)
    lch[:, 2] = (lch[:, 2] + rng.uniform(-degrees, degrees)) % 360
    lch[:, 1] *= rng.uniform(0.9, 1.1)
    return [rgb_to_hex(c) for c in oklab_to_rgb(lch_to_oklab(lch))]


# -------------------------------------------------------------- extraction


def kmeans_colors(pixels: np.ndarray, k: int, weights: np.ndarray | None = None,
                  iterations: int = 20, seed: int = 7) -> tuple[np.ndarray, np.ndarray]:
    """Deterministic weighted k-means in OKLab.

    Returns ``(centres_rgb, weight_fraction)``, sorted dark -> light.
    """
    pixels = np.asarray(pixels, dtype=np.float64).reshape(-1, 3)
    if len(pixels) == 0:
        return np.zeros((0, 3)), np.zeros(0)
    uniq, inv, counts = np.unique(pixels.round().astype(np.int64), axis=0,
                                  return_inverse=True, return_counts=True)
    w = counts.astype(np.float64)
    if weights is not None:
        w = np.bincount(inv.ravel(), weights=np.asarray(weights).ravel(), minlength=len(uniq))
    lab = rgb_to_oklab(uniq)
    k = max(1, min(k, len(uniq)))
    rng = np.random.default_rng(seed)
    # k-means++ initialisation, weighted
    centres = [lab[int(np.argmax(w))]]
    for _ in range(1, k):
        d2 = np.min(((lab[:, None, :] - np.array(centres)[None]) ** 2).sum(-1), axis=1) * w
        if d2.sum() <= 0:
            break
        centres.append(lab[rng.choice(len(lab), p=d2 / d2.sum())])
    c = np.array(centres)
    for _ in range(iterations):
        assign = np.argmin(((lab[:, None, :] - c[None]) ** 2).sum(-1), axis=1)
        newc = c.copy()
        for j in range(len(c)):
            m = assign == j
            if m.any():
                newc[j] = (lab[m] * w[m, None]).sum(0) / w[m].sum()
        if np.allclose(newc, c, atol=1e-6):
            break
        c = newc
    assign = np.argmin(((lab[:, None, :] - c[None]) ** 2).sum(-1), axis=1)
    frac = np.array([w[assign == j].sum() for j in range(len(c))]) / w.sum()
    order = np.argsort(c[:, 0])
    return oklab_to_rgb(c[order]), frac[order]


@dataclass
class ExtractedPalette:
    colors: list[RGB]
    weights: list[float]

    def hex(self) -> list[str]:
        return [rgb_to_hex(c) for c in self.colors]


def extract_palette(rgba: np.ndarray, k: int = 8) -> ExtractedPalette:
    """Main colours of an RGBA image array (transparent pixels ignored)."""
    px = rgba.reshape(-1, rgba.shape[-1])
    if px.shape[1] == 4:
        px = px[px[:, 3] >= 128]
    if len(px) == 0:
        return ExtractedPalette([], [])
    cols, frac = kmeans_colors(px[:, :3], k)
    return ExtractedPalette([to_rgb_tuple(c) for c in cols], [float(f) for f in frac])


def nearest_color_indices(pixels: np.ndarray, palette: np.ndarray) -> np.ndarray:
    lab = rgb_to_oklab(pixels.reshape(-1, 3))
    plab = rgb_to_oklab(palette.reshape(-1, 3))
    return np.argmin(((lab[:, None, :] - plab[None]) ** 2).sum(-1), axis=1)
