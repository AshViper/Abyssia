"""Pixel Art Analyzer.

Measures the *style* of a reference texture - how many tone levels it
uses and how they are spaced, how big its pixel clusters are, how noisy
and directional it is, whether it has accent (ore-like) colours and, for
sprites, what its silhouette looks like.

Only these statistics reach the generator.  No pixel of the reference is
ever copied into a generated texture.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass, field

import numpy as np
from PIL import Image

from . import pixel_art as pa
from .palette import ExtractedPalette, extract_palette, kmeans_colors, oklab_to_lch, rgb_to_oklab, rgb_to_hex


@dataclass
class Silhouette:
    coverage: float = 0.0        # opaque fraction
    top: float = 0.0             # highest opaque row, as fraction of height from bottom
    width: float = 0.0           # mean opaque run width per row / image width
    stems: int = 0               # opaque runs touching the bottom row
    symmetry: float = 0.0        # 0..1 mirror similarity
    verticality: float = 0.5     # >0.5 tall and thin
    touches_top: bool = False    # continues past the top edge (tiles vertically)
    perimeter_ratio: float = 0.0  # edge pixels / area (leafiness)


@dataclass
class TextureAnalysis:
    width: int = 16
    height: int = 16
    has_alpha: bool = False
    color_count: int = 0
    palette: list[str] = field(default_factory=list)
    palette_weights: list[float] = field(default_factory=list)
    brightness_mean: float = 0.5
    brightness_std: float = 0.1
    brightness_hist: list[float] = field(default_factory=list)
    contrast: float = 0.3
    saturation: float = 0.02
    edge_density: float = 0.4
    cluster_size: float = 3.0     # mean cluster area in pixels, at 16x16 scale
    noise_density: float = 0.15   # fraction of isolated pixels
    direction: float = 0.0        # dominant feature orientation, degrees (0 = horizontal)
    anisotropy: float = 0.0       # 0 isotropic .. 1 strongly directional
    levels: int = 6
    level_positions: list[float] = field(default_factory=lambda: [0, .2, .4, .6, .8, 1])
    level_weights: list[float] = field(default_factory=lambda: [.08, .17, .25, .25, .17, .08])
    accent_fraction: float = 0.0
    accent_cluster: float = 0.0
    accent_colors: list[str] = field(default_factory=list)
    silhouette: Silhouette = field(default_factory=Silhouette)
    source_name: str = ""
    is_default: bool = False

    # ------------------------------------------------------------------
    def feature_scale(self, size: int) -> float:
        """Suggested lattice frequency for a texture of ``size`` pixels."""
        area = max(1.0, self.cluster_size) * (size / 16.0) ** 2
        blob = np.sqrt(area)
        return float(np.clip(size / (blob * 1.6), 2.0, size / 2))

    def aspect(self) -> tuple[float, float]:
        """Noise aspect (fx, fy multipliers) matching the dominant direction."""
        a = min(1.0, self.anisotropy) * 1.6
        ang = np.radians(self.direction)
        horiz = abs(np.cos(ang))  # 1 -> horizontal features
        return (1.0 + a * (1 - horiz), 1.0 + a * horiz)

    def to_dict(self) -> dict:
        return asdict(self)

    def summary(self) -> list[tuple[str, str]]:
        s = self.silhouette
        rows = [
            ("Size", f"{self.width} x {self.height}"),
            ("Colors", str(self.color_count)),
            ("Tone levels", str(self.levels)),
            ("Brightness", f"{self.brightness_mean:.2f} ± {self.brightness_std:.2f}"),
            ("Contrast", f"{self.contrast:.2f}"),
            ("Saturation", f"{self.saturation:.3f}"),
            ("Edge density", f"{self.edge_density:.2f}"),
            ("Cluster size", f"{self.cluster_size:.1f} px"),
            ("Noise density", f"{self.noise_density:.2f}"),
            ("Direction", f"{self.direction:.0f}° ({self.anisotropy:.2f})"),
            ("Accent", f"{self.accent_fraction * 100:.0f}% · {self.accent_cluster:.1f} px"),
        ]
        if self.has_alpha:
            rows.append(("Coverage", f"{s.coverage * 100:.0f}%"))
            rows.append(("Silhouette", f"top {s.top:.2f} · stems {s.stems} · sym {s.symmetry:.2f}"))
        return rows

    # ------------------------------------------------------------------
    @classmethod
    def default_for(cls, category: str, size: int = 16) -> "TextureAnalysis":
        """Built-in style profile used when no reference image is loaded."""
        a = cls(width=size, height=size, is_default=True, source_name="(built-in profile)")
        profiles = {
            "terrain": dict(levels=6, cluster_size=3.2, noise_density=0.12, contrast=0.28,
                            anisotropy=0.25, direction=0.0),
            "ore": dict(levels=6, cluster_size=3.0, noise_density=0.12, contrast=0.26,
                        accent_fraction=0.18, accent_cluster=4.0),
            "mineral": dict(levels=6, cluster_size=4.5, noise_density=0.1, contrast=0.35),
            "thermal": dict(levels=6, cluster_size=3.5, noise_density=0.1, contrast=0.35),
            "decoration": dict(levels=5, cluster_size=5.0, noise_density=0.06, contrast=0.3),
            "organic": dict(levels=6, cluster_size=4.0, noise_density=0.1, contrast=0.32),
            "metal": dict(levels=6, cluster_size=6.0, noise_density=0.05, contrast=0.35,
                          anisotropy=0.4, direction=0.0),
            "plant": dict(levels=5, has_alpha=True, contrast=0.4,
                          silhouette=Silhouette(0.3, 0.8, 0.3, 3, 0.4, 0.7)),
            "kelp": dict(levels=5, has_alpha=True, contrast=0.4, anisotropy=0.6, direction=90.0,
                         silhouette=Silhouette(0.35, 1.0, 0.4, 1, 0.3, 0.8, True)),
            "crystal": dict(levels=5, has_alpha=True, contrast=0.5,
                            silhouette=Silhouette(0.35, 0.85, 0.4, 3, 0.6, 0.7)),
        }
        for k, v in profiles.get(category, profiles["terrain"]).items():
            setattr(a, k, v)
        n = a.levels
        a.level_positions = [float(x) for x in np.linspace(0, 1, n) ** 0.9]
        a.level_weights = [float(x) for x in pa.bell_weights(n, 0.5, 0.3)]
        return a


# ---------------------------------------------------------------- analysis


def load_rgba(image: Image.Image | np.ndarray) -> np.ndarray:
    if isinstance(image, np.ndarray):
        arr = image
        if arr.ndim == 2:
            arr = np.stack([arr] * 3 + [np.full_like(arr, 255)], axis=-1)
        if arr.shape[-1] == 3:
            arr = np.concatenate([arr, np.full(arr.shape[:2] + (1,), 255, arr.dtype)], axis=-1)
        return arr.astype(np.uint8)
    return np.asarray(image.convert("RGBA"), dtype=np.uint8)


MAX_ANALYSIS_SIZE = 256


def analyze(image: Image.Image | np.ndarray, name: str = "") -> TextureAnalysis:
    if isinstance(image, Image.Image) and max(image.size) > MAX_ANALYSIS_SIZE * 2:
        # huge references (photos, atlases): statistics at pixel-art scale are what matter
        f = max(image.size) / MAX_ANALYSIS_SIZE
        image = image.convert("RGBA").resize((max(1, int(image.width / f)), max(1, int(image.height / f))),
                                             Image.NEAREST)
    rgba = load_rgba(image)
    # animated strips (e.g. 16x256) - analyse the first square frame
    if rgba.shape[0] > rgba.shape[1] and rgba.shape[0] % rgba.shape[1] == 0:
        rgba = rgba[: rgba.shape[1]]
    h, w = rgba.shape[:2]
    alpha = rgba[..., 3]
    opaque = alpha >= 128
    has_alpha = bool((~opaque).any())
    res = TextureAnalysis(width=w, height=h, has_alpha=has_alpha, source_name=name)
    if not opaque.any():
        return res

    rgb = rgba[..., :3].astype(np.float64)
    lab = rgb_to_oklab(rgb)
    L = lab[..., 0]
    lch = oklab_to_lch(lab)
    vis = opaque
    Lv = L[vis]

    res.color_count = int(len(pa.unique_colors(rgba)))
    ext: ExtractedPalette = extract_palette(rgba, 8)
    res.palette = ext.hex()
    res.palette_weights = [round(x, 4) for x in ext.weights]
    res.brightness_mean = float(Lv.mean())
    res.brightness_std = float(Lv.std())
    hist, _ = np.histogram(Lv, bins=16, range=(0, 1))
    res.brightness_hist = [float(x) for x in hist / max(1, hist.sum())]
    res.contrast = float(np.percentile(Lv, 95) - np.percentile(Lv, 5))
    res.saturation = float(lch[..., 1][vis].mean())

    # ---- accent separation (ore-like colours that differ from the body)
    body = _body_mask(lch, vis)
    accent = vis & ~body
    res.accent_fraction = float(accent.sum() / max(1, vis.sum()))
    if accent.any():
        acc_cols, _ = kmeans_colors(rgb[accent], 3)
        res.accent_colors = [rgb_to_hex(c) for c in acc_cols]
        _, sizes = pa.label_components(accent.astype(np.int32), wrap=not has_alpha, mask=accent)
        res.accent_cluster = float(np.mean(sizes)) * (16.0 / w) ** 2 if sizes else 0.0

    # ---- tone levels of the body material
    body_px = rgb[body] if body.any() else rgb[vis]
    k = int(np.clip(len(np.unique(body_px.round(), axis=0)), 2, 9))
    cents, frac = kmeans_colors(body_px, k)
    cl = rgb_to_oklab(cents)[:, 0]
    cl, frac = _merge_close_levels(cl, frac, 0.022)
    res.levels = int(len(cl))
    span = cl.max() - cl.min()
    res.level_positions = [float(x) for x in ((cl - cl.min()) / span if span > 1e-6 else np.linspace(0, 1, len(cl)))]
    res.level_weights = [float(x) for x in frac / frac.sum()]

    # ---- quantized level map for spatial statistics
    level_map = np.full((h, w), -1, dtype=np.int32)
    level_map[vis] = np.argmin(np.abs(L[vis][:, None] - cl[None]), axis=1)
    wrap = not has_alpha
    labels, sizes = pa.label_components(level_map, wrap=wrap, mask=vis)
    res.cluster_size = float(np.mean(sizes)) * (16.0 / w) ** 2 if sizes else 1.0
    iso = pa.isolated_pixels(level_map, wrap, vis)
    res.noise_density = float(iso.sum() / max(1, vis.sum()))
    right = pa.neighbour(level_map, 1, 0, wrap, fill=-1)
    down = pa.neighbour(level_map, 0, 1, wrap, fill=-1)
    pairs = (vis & (right >= 0)).sum() + (vis & (down >= 0)).sum()
    diffs = (vis & (right >= 0) & (right != level_map)).sum() + (vis & (down >= 0) & (down != level_map)).sum()
    res.edge_density = float(diffs / max(1, pairs))

    # ---- direction from the structure tensor of lightness
    Lf = np.where(vis, L, float(Lv.mean()))
    gx = pa.neighbour(Lf, 1, 0, wrap) - pa.neighbour(Lf, -1, 0, wrap)
    gy = pa.neighbour(Lf, 0, 1, wrap) - pa.neighbour(Lf, 0, -1, wrap)
    jxx, jyy, jxy = float((gx * gx).mean()), float((gy * gy).mean()), float((gx * gy).mean())
    tr = jxx + jyy
    if tr > 1e-9:
        grad_angle = 0.5 * np.degrees(np.arctan2(2 * jxy, jxx - jyy))
        res.direction = float((grad_angle + 90.0) % 180.0)  # features run across the gradient
        res.anisotropy = float(np.sqrt((jxx - jyy) ** 2 + 4 * jxy * jxy) / tr)

    if has_alpha:
        res.silhouette = _silhouette(opaque)
    return res


def _body_mask(lch: np.ndarray, vis: np.ndarray) -> np.ndarray:
    """Pixels belonging to the dominant material (as opposed to accents)."""
    C = lch[..., 1]
    hue = lch[..., 2]
    Cv = C[vis]
    c_med = float(np.median(Cv))
    if c_med < 0.035:
        # near-grey body: accents are the clearly chromatic pixels
        return vis & (C < max(0.05, c_med * 2.5 + 0.02))
    # chromatic body: accents differ in hue
    weights = Cv
    ang = np.radians(hue[vis])
    mean_h = np.degrees(np.arctan2((np.sin(ang) * weights).sum(), (np.cos(ang) * weights).sum())) % 360
    dh = np.abs(((hue - mean_h + 180) % 360) - 180)
    return vis & ((dh < 45) | (C < 0.04))


def _merge_close_levels(L: np.ndarray, frac: np.ndarray, tol: float) -> tuple[np.ndarray, np.ndarray]:
    Ls, fs = [float(L[0])], [float(frac[0])]
    for l, f in zip(L[1:], frac[1:]):
        if l - Ls[-1] < tol:
            tot = fs[-1] + f
            Ls[-1] = (Ls[-1] * fs[-1] + l * f) / tot if tot > 0 else Ls[-1]
            fs[-1] = tot
        else:
            Ls.append(float(l))
            fs.append(float(f))
    return np.array(Ls), np.array(fs)


def _silhouette(mask: np.ndarray) -> Silhouette:
    h, w = mask.shape
    rows = np.nonzero(mask.any(axis=1))[0]
    s = Silhouette()
    s.coverage = float(mask.mean())
    if len(rows) == 0:
        return s
    s.top = float((h - rows[0]) / h)
    s.touches_top = bool(mask[0].any())
    widths = mask.sum(axis=1)[rows]
    s.width = float(widths.mean() / w)
    bottom = mask[rows[-1]]
    s.stems = int(np.sum(np.diff(np.concatenate([[0], bottom.astype(np.int8)])) == 1))
    s.symmetry = float((mask & mask[:, ::-1]).sum() / max(1, (mask | mask[:, ::-1]).sum()))
    cols = np.nonzero(mask.any(axis=0))[0]
    bw = cols[-1] - cols[0] + 1
    bh = rows[-1] - rows[0] + 1
    s.verticality = float(bh / (bw + bh))
    edge = mask & ~pa.erode(mask)
    s.perimeter_ratio = float(edge.sum() / max(1, mask.sum()))
    return s
