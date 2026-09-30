"""Comparison views: difference, palette and brightness images."""
from __future__ import annotations

import numpy as np
from PIL import Image

from . import pixel_art as pa
from .palette import luminance, rgb_to_oklab


def _match_size(img: Image.Image, size: tuple[int, int]) -> Image.Image:
    img = img.convert("RGBA")
    if img.height > img.width and img.height % img.width == 0:
        img = img.crop((0, 0, img.width, img.width))  # first frame of an animation strip
    return img if img.size == size else img.resize(size, Image.NEAREST)


def brightness_map(img: Image.Image) -> Image.Image:
    """Grey-scale perceptual lightness (alpha kept)."""
    a = np.asarray(img.convert("RGBA"))
    L = (luminance(a[..., :3].astype(np.float64)) * 255).clip(0, 255).astype(np.uint8)
    out = np.stack([L, L, L, a[..., 3]], axis=-1)
    return Image.fromarray(out, "RGBA")


def difference_map(source: Image.Image, generated: Image.Image) -> Image.Image:
    """Per-pixel colour distance (OKLab), shown as a heat ramp.

    High values everywhere mean the output is not a copy of the reference.
    """
    s = np.asarray(_match_size(source, generated.size)).astype(np.float64)
    g = np.asarray(generated.convert("RGBA")).astype(np.float64)
    d = np.linalg.norm(rgb_to_oklab(s[..., :3]) - rgb_to_oklab(g[..., :3]), axis=-1)
    alpha_diff = np.abs(s[..., 3] - g[..., 3]) / 255.0
    d = np.clip(np.maximum(d / 0.35, alpha_diff), 0, 1)
    # dark blue -> cyan -> yellow -> white
    stops = np.array([[13, 15, 40], [30, 110, 200], [80, 220, 200], [240, 220, 80], [255, 255, 255]], float)
    pos = d * (len(stops) - 1)
    i0 = np.clip(np.floor(pos).astype(int), 0, len(stops) - 2)
    f = (pos - i0)[..., None]
    rgb = stops[i0] * (1 - f) + stops[i0 + 1] * f
    out = np.concatenate([rgb, np.full(d.shape + (1,), 255.0)], axis=-1)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def similarity(source: Image.Image, generated: Image.Image) -> float:
    """Fraction of pixels that are (nearly) identical - a copy check (0 = nothing shared)."""
    s = np.asarray(_match_size(source, generated.size)).astype(np.int32)
    g = np.asarray(generated.convert("RGBA")).astype(np.int32)
    same = (np.abs(s - g).max(axis=-1) <= 2) & (g[..., 3] > 0)
    vis = (g[..., 3] > 0) | (s[..., 3] > 0)
    return float(same.sum() / max(1, vis.sum()))


def palette_image(img: Image.Image, cell: int = 1) -> Image.Image:
    """Image re-drawn as sorted palette swatches (colour usage view)."""
    a = np.asarray(img.convert("RGBA"))
    cols = pa.unique_colors(a)
    if len(cols) == 0:
        return Image.new("RGBA", img.size, (0, 0, 0, 0))
    order = np.argsort(luminance(cols.astype(np.float64)))
    cols = cols[order]
    px = a.reshape(-1, 4)
    vis = px[:, 3] > 0
    counts = np.array([np.all(px[vis, :3] == c, axis=1).sum() for c in cols], dtype=np.float64)
    w, h = img.size
    total = w * h
    cells = np.maximum(1, np.round(counts / counts.sum() * total)).astype(int)
    seq = np.repeat(np.arange(len(cols)), cells)[:total]
    if len(seq) < total:
        seq = np.concatenate([seq, np.full(total - len(seq), len(cols) - 1)])
    # fill column-major so each colour forms a vertical band
    grid = seq.reshape(w, h).T
    out = np.concatenate([cols[grid], np.full((h, w, 1), 255, np.uint8)], axis=-1)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def histogram(img: Image.Image, bins: int = 16) -> np.ndarray:
    a = np.asarray(img.convert("RGBA"))
    vis = a[..., 3] > 0
    if not vis.any():
        return np.zeros(bins)
    L = luminance(a[..., :3][vis].astype(np.float64))
    h, _ = np.histogram(L, bins=bins, range=(0, 1))
    return h / h.sum()
