"""TextureGenerator - the one entry point the GUI, batch mode and tests use.

    result = TextureGenerator().generate(source_image, settings)
    result.image  # PIL.Image, RGBA, pixel perfect

Pipeline::

    reference ─► analyze ─► style profile ─┐
    settings ─► palette generator ─────────┼─► category layers ─► style filter ─► PNG
                                           └─ seed streams
"""
from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass, field, replace
from datetime import datetime
from typing import Callable

import numpy as np
from PIL import Image

from . import pixel_art as pa
from .analyzer import TextureAnalysis, analyze, load_rgba
from .layers import GenContext, LayerResult, TextureCanvas, run_layers
from .palette import ColorAdjust, Palette, Ramp, adjust_palette, generate_palette, luminance
from .seed import derive_rng, variation_seed
from .settings import TextureSettings

ProgressFn = Callable[[float, str], None]


class GenerationCancelled(Exception):
    pass


@dataclass
class GenerationResult:
    image: Image.Image
    settings: TextureSettings
    palette: Palette
    analysis: TextureAnalysis
    layers: list[LayerResult] = field(default_factory=list)
    elapsed: float = 0.0
    created: datetime = field(default_factory=datetime.now)
    emission: Image.Image | None = None

    @property
    def seed(self) -> int:
        return self.settings.seed

    @property
    def name(self) -> str:
        return self.settings.name

    def colors_used(self) -> int:
        return int(len(pa.unique_colors(np.asarray(self.image))))


def _image_key(img: Image.Image) -> str:
    h = hashlib.sha1(img.tobytes())
    h.update(f"{img.size}{img.mode}".encode())
    return h.hexdigest()


def _resample_profile(positions: list[float], weights: list[float], k: int) -> tuple[np.ndarray, np.ndarray]:
    """Resample a tone profile (positions, weights) to ``k`` levels."""
    p = np.asarray(positions, dtype=np.float64)
    w = np.asarray(weights, dtype=np.float64)
    w = w / w.sum() if w.sum() > 0 else np.full(len(w), 1 / len(w))
    if len(p) == k:
        return p, w
    cdf = np.concatenate([[0], np.cumsum(w)])
    src = np.linspace(0, 1, len(cdf))
    new_cdf = np.interp(np.linspace(0, 1, k + 1), src, cdf)
    new_w = np.maximum(np.diff(new_cdf), 1e-3)
    new_p = np.interp(np.linspace(0, 1, k), np.linspace(0, 1, len(p)), p)
    return new_p, new_w / new_w.sum()


def blend_analysis(src: TextureAnalysis, default: TextureAnalysis, influence: float) -> TextureAnalysis:
    """Mix a measured profile with the category's built-in profile."""
    t = float(np.clip(influence, 0, 1))
    if src.is_default or t >= 0.999:
        return src
    out = replace(src)
    for f in ("cluster_size", "noise_density", "edge_density", "contrast", "anisotropy",
              "accent_fraction", "accent_cluster", "saturation"):
        setattr(out, f, getattr(default, f) * (1 - t) + getattr(src, f) * t)
    if t < 0.5:
        out.direction = default.direction
    k = int(round(default.levels * (1 - t) + src.levels * t))
    p1, w1 = _resample_profile(src.level_positions, src.level_weights, k)
    p0, w0 = _resample_profile(default.level_positions, default.level_weights, k)
    out.levels = k
    out.level_positions = list(p0 * (1 - t) + p1 * t)
    ww = w0 * (1 - t) + w1 * t
    out.level_weights = list(ww / ww.sum())
    if not src.has_alpha:
        out.silhouette = default.silhouette
    return out


class TextureGenerator:
    """Stateless apart from an analysis cache; safe to use from a worker thread."""

    def __init__(self) -> None:
        self._cache: dict[str, TextureAnalysis] = {}

    # ------------------------------------------------------------ analysis
    def analyze(self, image: Image.Image | None, name: str = "") -> TextureAnalysis | None:
        if image is None:
            return None
        key = _image_key(image)
        if key not in self._cache:
            self._cache[key] = analyze(image, name)
        return self._cache[key]

    def style_profile(self, source: Image.Image | None, s: TextureSettings) -> TextureAnalysis:
        default = TextureAnalysis.default_for(s.category, s.size)
        measured = self.analyze(source)
        if measured is None:
            return default
        return blend_analysis(measured, default, s.source_influence)

    # ------------------------------------------------------------- palette
    def build_palette(self, s: TextureSettings, profile: TextureAnalysis, generator, k: int) -> Palette:
        positions, _ = _resample_profile(profile.level_positions, profile.level_weights, k)
        structure = [0.0] + [0.14 + 0.72 * float(p) for p in positions] + [1.0]
        roles = generator.palette_roles(s)
        lengths = generator.ramp_lengths(k)
        pal = generate_palette(s.palette, lengths["base"], structure, s.accent or None,
                               derive_rng(s.seed, "palette"), lengths["accent"],
                               0.7 * s.hue_shift, s.base_color or roles.get("base"))
        if roles:
            from .palette import anchors_for, build_ramp
            for role, anchor in roles.items():
                if role == "base" or (role == "accent" and s.accent):
                    continue
                n = lengths.get(role, 4)
                pal.ramps[role] = build_ramp(anchors_for(anchor), n, None, 0.7 * s.hue_shift, role)
        if s.secondary_color:
            from .palette import anchors_for, build_ramp
            pal.ramps["secondary"] = build_ramp(anchors_for(s.secondary_color), lengths.get("secondary", 4),
                                                None, 0.7 * s.hue_shift, "secondary")
        if s.crack_color and s.category == "terrain":
            from .palette import anchors_for, build_ramp
            pal.ramps["accent2"] = build_ramp(anchors_for(s.crack_color), lengths.get("accent2", 4),
                                              None, 0.7 * s.hue_shift, "accent2")
        for role, n in lengths.items():
            r = pal.ramps.get(role)
            if r is not None and len(r) != n:
                from .palette import build_ramp
                pal.ramps[role] = build_ramp(r.colors, n, None, 0.0, role)
        adj = ColorAdjust(s.hue, s.saturation, s.brightness, s.contrast, s.temperature, s.tint)
        pal = adjust_palette(pal, adj)
        if s.filter_enabled("contrast_normalize"):
            pal = _normalize_contrast(pal)
        return pal

    # ------------------------------------------------------------ generate
    def generate(self, source: Image.Image | None, settings: TextureSettings,
                 progress: ProgressFn | None = None, cancelled: Callable[[], bool] | None = None,
                 capture_layers: bool = False) -> GenerationResult:
        from generators import get_generator

        t0 = time.perf_counter()
        s = settings.copy().clamp()
        if progress:
            progress(0.02, "analyze")
        profile = self.style_profile(source, s)
        gen = get_generator(s.category)
        k = gen.body_levels(s, profile)
        palette = self.build_palette(s, profile, gen, k)
        lengths = {role: len(palette.ramp(role)) for role in ("base", "secondary", "accent", "accent2", "glow")}
        canvas = TextureCanvas(s.size, s.size, lengths)
        wrap = bool(s.tileable) and not gen.is_sprite(s)
        _, weights = _resample_profile(profile.level_positions, profile.level_weights, k)
        ctx = GenContext(s, profile, palette, canvas, wrap, {"K": k, "weights": weights})
        if source is not None and s.recolor_source:
            src = Image.fromarray(load_rgba(source), "RGBA")
            if src.size != (s.size, s.size):
                src = src.resize((s.size, s.size), Image.NEAREST)
            ctx.data["source_rgba"] = np.asarray(src)
        gen.prepare(ctx)
        layers = run_layers(gen, ctx, capture_layers, progress, cancelled, (0.05, 0.85))
        if progress:
            progress(0.88, "style filter")
        minecraft_style_filter(ctx)
        gen.finish(ctx)
        rgba = finalize_pixels(ctx)
        image = Image.fromarray(rgba, "RGBA")
        emission = None
        if canvas.emissive.any():
            em = np.zeros_like(rgba)
            m = canvas.emissive & (rgba[..., 3] > 0)
            em[m] = rgba[m]
            emission = Image.fromarray(em, "RGBA")
        if progress:
            progress(1.0, "done")
        return GenerationResult(image, s, palette, profile, layers, time.perf_counter() - t0,
                                emission=emission)

    def generate_image(self, source: Image.Image | None, settings: TextureSettings) -> Image.Image:
        return self.generate(source, settings).image

    def generate_variations(self, source: Image.Image | None, settings: TextureSettings, count: int,
                            progress: ProgressFn | None = None,
                            cancelled: Callable[[], bool] | None = None) -> list[GenerationResult]:
        results = []
        for i in range(count):
            if cancelled is not None and cancelled():
                raise GenerationCancelled()
            s = settings.copy(seed=variation_seed(settings.seed, i))

            def sub(p: float, msg: str, i=i) -> None:
                if progress:
                    progress((i + p) / count, f"variation {i + 1}/{count}: {msg}")

            results.append(self.generate(source, s, sub, cancelled))
        return results


# --------------------------------------------------------------- style filter


def minecraft_style_filter(ctx: GenContext) -> None:
    """Final clean-up in level space (before colours are resolved)."""
    s, c = ctx.settings, ctx.canvas
    opaque = c.opaque
    protect = c.tags.get("protect")
    key = c.ramp.astype(np.int32) * 64 + c.level
    key[~opaque] = -1
    if s.filter_enabled("cluster_cleanup"):
        min_size = 2 if s.style != "programmer" else 3
        if ctx.canvas.w >= 64:
            min_size += 1
        cleaned = pa.remove_small_clusters(key, min_size, ctx.wrap, opaque, protect)
        _apply_key(c, cleaned, opaque)
        key = cleaned
    if s.filter_enabled("noise_reduction"):
        cleaned = pa.mode_filter(key, ctx.wrap, opaque, protect)
        _apply_key(c, cleaned, opaque)
    if s.filter_enabled("edge_cleanup") and ctx.settings.is_sprite:
        m = c.opaque
        cleaned = pa.alpha_cleanup(m, 2, fill_holes=False)
        c.clear(m & ~cleaned)


def _apply_key(c: TextureCanvas, key: np.ndarray, mask: np.ndarray) -> None:
    c.ramp[mask] = (key[mask] // 64).astype(np.int8)
    c.level[mask] = key[mask] % 64


def finalize_pixels(ctx: GenContext) -> np.ndarray:
    s, c = ctx.settings, ctx.canvas
    rgba = c.render(ctx.palette)
    translucent = None
    if s.transparency > 0.02:
        translucent = int(round(255 - 150 * s.transparency))
        translucent = max(s.alpha_threshold, translucent)
    rgba[..., 3] = pa.threshold_alpha(rgba[..., 3].astype(np.int32), s.alpha_threshold, translucent)
    rgba[rgba[..., 3] == 0, :3] = 0
    if s.color_limit and s.filter_enabled("palette_limit"):
        rgba = pa.limit_colors(rgba, s.color_limit)
    return rgba


def _normalize_contrast(pal: Palette, min_range: float = 0.16) -> Palette:
    """Stretch the base ramp if it became too flat to read at 16x16."""
    base = pal.ramp("base")
    L = luminance(base.as_array())
    rng = float(L.max() - L.min())
    if rng >= min_range or len(base) < 2:
        return pal
    from .palette import oklab_to_rgb, rgb_to_oklab, to_rgb_tuple
    lab = rgb_to_oklab(base.as_array())
    mid = float(L.mean())
    factor = min_range / max(rng, 1e-3)
    lab[:, 0] = np.clip(mid + (lab[:, 0] - mid) * factor, 0.02, 0.98)
    ramps = dict(pal.ramps)
    ramps["base"] = Ramp([to_rgb_tuple(x) for x in oklab_to_rgb(lab)], "base")
    return Palette(pal.name, ramps)
