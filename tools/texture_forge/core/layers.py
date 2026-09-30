"""Layer system and the indexed canvas every generator paints on.

A pixel on the canvas is not an RGB value but a pair ``(ramp, level)``:
which colour ramp of the palette it uses and how far up that ramp it
sits.  Layers push levels up and down (highlights, shadows, cracks) or
switch ramps (ore, glow).  Colours are only resolved at the very end, so
the colour count is bounded by the palette and every shading step lands
exactly on a palette entry.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable

import numpy as np
from PIL import Image

from .analyzer import TextureAnalysis
from .palette import Palette
from .seed import derive_rng
from .settings import LAYER_ORDER, TextureSettings

ROLE_IDS: dict[str, int] = {"base": 0, "secondary": 1, "accent": 2, "accent2": 3, "glow": 4}
ROLE_NAMES = {v: k for k, v in ROLE_IDS.items()}
TRANSPARENT = -1


class TextureCanvas:
    def __init__(self, width: int, height: int, ramp_lengths: dict[str, int]):
        self.w = width
        self.h = height
        self.ramp = np.zeros((height, width), dtype=np.int8)
        self.level = np.zeros((height, width), dtype=np.int32)
        self.alpha = np.full((height, width), 255, dtype=np.int32)
        self.height_map = np.zeros((height, width), dtype=np.float64)
        self.emissive = np.zeros((height, width), dtype=bool)
        self.tags: dict[str, np.ndarray] = {}
        self.lengths = np.array([ramp_lengths.get(ROLE_NAMES[i], 1) for i in range(len(ROLE_IDS))])

    # ----------------------------------------------------------- queries
    @property
    def opaque(self) -> np.ndarray:
        return (self.ramp >= 0) & (self.alpha > 0)

    def role_mask(self, role: str) -> np.ndarray:
        return self.ramp == ROLE_IDS[role]

    def length_of(self, role: str) -> int:
        return int(self.lengths[ROLE_IDS[role]])

    def max_level(self) -> np.ndarray:
        """Per-pixel top level index of the pixel's ramp."""
        r = np.clip(self.ramp, 0, None)
        return self.lengths[r] - 1

    def tone(self) -> np.ndarray:
        """Level normalised to 0..1 within each pixel's ramp."""
        top = np.maximum(1, self.max_level())
        return self.level / top

    def tag(self, name: str) -> np.ndarray:
        if name not in self.tags:
            self.tags[name] = np.zeros((self.h, self.w), dtype=bool)
        return self.tags[name]

    # ----------------------------------------------------------- editing
    def fill(self, role: str, level: int) -> None:
        self.ramp[:] = ROLE_IDS[role]
        self.level[:] = level
        self.alpha[:] = 255

    def set(self, mask: np.ndarray, role: str, level: np.ndarray | int) -> None:
        rid = ROLE_IDS[role]
        self.ramp[mask] = rid
        lv = np.broadcast_to(np.asarray(level), mask.shape)[mask] if np.ndim(level) else level
        self.level[mask] = np.clip(lv, 0, self.lengths[rid] - 1)
        self.alpha[mask] = np.where(self.alpha[mask] > 0, self.alpha[mask], 255)

    def set_tone(self, mask: np.ndarray, role: str, tone: np.ndarray | float) -> None:
        """Set by normalised tone (0 darkest .. 1 lightest) of ``role``'s ramp."""
        n = self.length_of(role)
        lv = np.rint(np.asarray(tone, dtype=np.float64) * (n - 1)).astype(np.int32)
        self.set(mask, role, lv)

    def shift(self, mask: np.ndarray, delta: np.ndarray | int, floor: int = 0,
              ceil: int | None = None) -> None:
        """Move levels up (lighter) or down (darker) within their own ramps.

        A shift never pushes a pixel past ``floor`` / ``ceil`` (pixels that
        are already beyond them are left where they are).  Detail layers
        use this to keep the extreme ramp ends for cracks and highlights.
        """
        m = mask & self.opaque
        if not m.any():
            return
        d = np.broadcast_to(np.asarray(delta), m.shape)[m]
        cur = self.level[m]
        top = self.lengths[np.clip(self.ramp[m], 0, None)] - 1
        hi = top if ceil is None else np.minimum(top, ceil)
        new = cur + d
        new = np.where(d > 0, np.minimum(new, np.maximum(cur, hi)), new)
        new = np.where(d < 0, np.maximum(new, np.minimum(cur, floor)), new)
        self.level[m] = np.clip(new, 0, top)

    def clear(self, mask: np.ndarray) -> None:
        self.ramp[mask] = TRANSPARENT
        self.alpha[mask] = 0
        self.emissive[mask] = False

    def clear_all(self) -> None:
        self.clear(np.ones((self.h, self.w), dtype=bool))

    def copy(self) -> "TextureCanvas":
        c = TextureCanvas.__new__(TextureCanvas)
        c.w, c.h = self.w, self.h
        c.ramp = self.ramp.copy()
        c.level = self.level.copy()
        c.alpha = self.alpha.copy()
        c.height_map = self.height_map.copy()
        c.emissive = self.emissive.copy()
        c.tags = {k: v.copy() for k, v in self.tags.items()}
        c.lengths = self.lengths.copy()
        return c

    # --------------------------------------------------------- rendering
    def render(self, palette: Palette) -> np.ndarray:
        """Resolve to an RGBA uint8 array."""
        out = np.zeros((self.h, self.w, 4), dtype=np.uint8)
        for role, rid in ROLE_IDS.items():
            m = (self.ramp == rid) & (self.alpha > 0)
            if not m.any():
                continue
            cols = np.array(palette.ramp(role).colors, dtype=np.uint8)
            lv = np.clip(self.level[m], 0, len(cols) - 1)
            out[m, :3] = cols[lv]
            out[m, 3] = np.clip(self.alpha[m], 0, 255)
        return out

    def to_image(self, palette: Palette) -> Image.Image:
        return Image.fromarray(self.render(palette), "RGBA")


@dataclass
class GenContext:
    """Everything a generator needs while running its layers."""
    settings: TextureSettings
    analysis: TextureAnalysis
    palette: Palette
    canvas: TextureCanvas
    wrap: bool
    data: dict = field(default_factory=dict)   # scratch space shared between layers

    @property
    def size(self) -> int:
        return self.canvas.w

    @property
    def w(self) -> int:
        return self.canvas.w

    @property
    def h(self) -> int:
        return self.canvas.h

    @property
    def scale(self) -> float:
        """Texture size relative to 16x16 (feature sizes scale with it)."""
        return self.canvas.w / 16.0

    def rng(self, *keys: object) -> np.random.Generator:
        s = self.settings
        return derive_rng(s.seed, s.category, s.variant, *keys)

    def px(self, n16: float, minimum: int = 1) -> int:
        """Convert a size in 16x16-pixels to this texture's pixels."""
        return max(minimum, int(round(n16 * self.scale)))


LayerFn = Callable[[GenContext], None]


@dataclass
class LayerResult:
    name: str
    enabled: bool
    snapshot: Image.Image | None = None


def run_layers(generator, ctx: GenContext, capture: bool = False,
               progress: Callable[[float, str], None] | None = None,
               cancelled: Callable[[], bool] | None = None,
               span: tuple[float, float] = (0.0, 1.0)) -> list[LayerResult]:
    """Run ``generator.layer_<name>`` for each layer in :data:`LAYER_ORDER`."""
    results = []
    n = len(LAYER_ORDER)
    for i, name in enumerate(LAYER_ORDER):
        if cancelled is not None and cancelled():
            from .generator import GenerationCancelled
            raise GenerationCancelled()
        enabled = ctx.settings.layer_enabled(name) or name == "base"
        fn = getattr(generator, f"layer_{name}", None)
        if enabled and fn is not None:
            fn(ctx)
        snap = ctx.canvas.to_image(ctx.palette) if capture else None
        results.append(LayerResult(name, enabled, snap))
        if progress:
            progress(span[0] + (span[1] - span[0]) * (i + 1) / n, name)
    return results
