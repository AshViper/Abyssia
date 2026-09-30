"""Generator tests: sizes, categories, transparency, determinism, pixel-art rules.

Run from the texture_forge folder:  python -m unittest discover -s tests -v
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from core import compare  # noqa: E402
from core import pixel_art as pa  # noqa: E402
from core.analyzer import analyze  # noqa: E402
from core.generator import TextureGenerator  # noqa: E402
from core.settings import CATEGORIES, LAYER_ORDER, VARIANTS, TextureSettings  # noqa: E402
from generators import get_generator  # noqa: E402


def _stone_like(size: int = 16, seed: int = 3) -> Image.Image:
    """A synthetic grey reference (tests never depend on Mojang assets)."""
    rng = np.random.default_rng(seed)
    base = rng.integers(0, 4, (size // 2, size // 2))
    base = np.kron(base, np.ones((2, 2), dtype=int))
    tones = np.array([96, 112, 124, 138])
    g = tones[base]
    arr = np.stack([g, g, g, np.full_like(g, 255)], axis=-1).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def _sprite_like(size: int = 16) -> Image.Image:
    arr = np.zeros((size, size, 4), dtype=np.uint8)
    arr[size // 3:, size // 2 - 1: size // 2 + 1] = (40, 120, 60, 255)
    arr[size // 2, size // 4: size // 2] = (60, 150, 70, 255)
    return Image.fromarray(arr, "RGBA")


class GenerationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gen = TextureGenerator()
        cls.stone = _stone_like()

    def _check_pixel_perfect(self, img: Image.Image, s: TextureSettings) -> None:
        a = np.asarray(img)
        self.assertEqual(img.mode, "RGBA")
        alphas = set(np.unique(a[..., 3]).tolist())
        allowed = {0, 255}
        if s.transparency > 0.02:
            allowed.add(max(s.alpha_threshold, int(round(255 - 150 * s.transparency))))
        self.assertTrue(alphas <= allowed, f"unexpected alpha values {alphas - allowed}")
        if s.color_limit:
            self.assertLessEqual(len(pa.unique_colors(a)), s.color_limit)

    def test_16x16(self):
        s = TextureSettings(size=16)
        r = self.gen.generate(self.stone, s)
        self.assertEqual(r.image.size, (16, 16))
        self._check_pixel_perfect(r.image, s)

    def test_32x32(self):
        s = TextureSettings(size=32)
        r = self.gen.generate(self.stone, s)
        self.assertEqual(r.image.size, (32, 32))
        self._check_pixel_perfect(r.image, s)

    def test_all_sizes(self):
        for size in (8, 64, 128, 256):
            with self.subTest(size=size):
                r = self.gen.generate(None, TextureSettings(size=size, seed=size))
                self.assertEqual(r.image.size, (size, size))

    def test_every_category_and_variant(self):
        for cat in CATEGORIES:
            for variant in VARIANTS.get(cat, {"auto": ""}):
                with self.subTest(category=cat, variant=variant):
                    s = TextureSettings.from_dict({"category": cat, "variant": variant, "seed": 7})
                    r = self.gen.generate(None, s)
                    self._check_pixel_perfect(r.image, r.settings)
                    self.assertGreater(np.asarray(r.image)[..., 3].max(), 0, "texture is empty")

    def test_ore(self):
        s = TextureSettings.from_dict({"category": "ore", "accent": "manganese", "density": 0.7, "seed": 11})
        r = self.gen.generate(self.stone, s)
        a = np.asarray(r.image)
        accent = {tuple(c) for c in r.palette.ramp("accent").colors}
        ore_px = sum(1 for px in a.reshape(-1, 4) if tuple(px[:3]) in accent)
        self.assertGreater(ore_px, 4, "ore colour should appear")

    def test_ore_clusters_are_not_single_points(self):
        s = TextureSettings.from_dict({"category": "ore", "variant": "cluster", "seed": 5, "density": 0.6})
        r = self.gen.generate(None, s)
        a = np.asarray(r.image)
        accent = np.array(r.palette.ramp("accent").colors)
        is_ore = (a[..., None, :3] == accent[None, None]).all(-1).any(-1)
        _, sizes = pa.label_components(is_ore.astype(np.int32), wrap=True, mask=is_ore)
        self.assertTrue(sizes, "no ore found")
        self.assertGreaterEqual(float(np.mean(sizes)), 2.0)

    def test_plant_is_transparent(self):
        s = TextureSettings.from_dict({"category": "plant", "seed": 4})
        r = self.gen.generate(_sprite_like(), s)
        alpha = np.asarray(r.image)[..., 3]
        self.assertTrue((alpha == 0).any(), "plant needs transparent background")
        self.assertTrue((alpha == 255).any(), "plant needs opaque pixels")

    def test_transparent_texture_kelp(self):
        for part in ("stalk", "top"):
            with self.subTest(part=part):
                s = TextureSettings.from_dict({"category": "kelp", "variant": "deep", "part": part, "seed": 9})
                r = self.gen.generate(None, s)
                alpha = np.asarray(r.image)[..., 3]
                self.assertTrue((alpha == 0).any() and (alpha == 255).any())

    def test_crystal(self):
        s = TextureSettings.from_dict({"category": "crystal", "variant": "cluster", "glow": 0.6, "seed": 12})
        r = self.gen.generate(None, s)
        self.assertTrue((np.asarray(r.image)[..., 3] == 0).any())
        self.assertIsNotNone(r.emission, "glowing crystal should produce an emission mask")

    def test_crystal_transparency_single_step(self):
        s = TextureSettings.from_dict({"category": "crystal", "variant": "single", "transparency": 0.6,
                                       "seed": 12})
        r = self.gen.generate(None, s)
        self._check_pixel_perfect(r.image, r.settings)

    def test_thermal(self):
        for variant in VARIANTS["thermal"]:
            with self.subTest(variant=variant):
                s = TextureSettings.from_dict({"category": "thermal", "variant": variant, "seed": 21})
                r = self.gen.generate(self.stone, s)
                self.assertEqual(r.image.size, (16, 16))
                self.assertTrue((np.asarray(r.image)[..., 3] == 255).all(), "thermal blocks are opaque")

    def test_seed_reproducibility(self):
        for cat in ("terrain", "ore", "plant", "kelp", "crystal", "thermal"):
            with self.subTest(category=cat):
                s = TextureSettings.from_dict({"category": cat, "seed": 424242})
                a = self.gen.generate(self.stone, s).image.tobytes()
                b = TextureGenerator().generate(self.stone, s.copy()).image.tobytes()
                self.assertEqual(a, b)

    def test_different_seeds_differ(self):
        a = self.gen.generate(None, TextureSettings(seed=1)).image.tobytes()
        b = self.gen.generate(None, TextureSettings(seed=2)).image.tobytes()
        self.assertNotEqual(a, b)

    def test_layer_toggle_is_isolated(self):
        """Switching a layer off must not reshuffle the layers before it."""
        s = TextureSettings(seed=77, cracks=0.8)
        full = self.gen.generate(None, s, capture_layers=True)
        off = self.gen.generate(None, s.copy(layers={**s.layers, "accent": False}), capture_layers=True)
        before = [l.snapshot.tobytes() for l in full.layers if l.name != "accent"]
        after = [l.snapshot.tobytes() for l in off.layers if l.name != "accent"]
        self.assertEqual(before, after)

    def test_every_layer_can_be_disabled(self):
        for cat in ("terrain", "ore", "plant", "crystal"):
            for layer in LAYER_ORDER[1:]:
                with self.subTest(category=cat, layer=layer):
                    s = TextureSettings.from_dict({"category": cat, "seed": 3})
                    s.layers[layer] = False
                    self.gen.generate(None, s)

    def test_color_limit(self):
        for limit in (8, 12, 16):
            with self.subTest(limit=limit):
                s = TextureSettings(color_limit=limit, mineral=0.6, glow=0.5, metallic=0.5, seed=2)
                img = self.gen.generate(self.stone, s).image
                self.assertLessEqual(len(pa.unique_colors(np.asarray(img))), limit)

    def test_not_a_copy_of_the_source(self):
        s = TextureSettings(seed=8, source_influence=1.0, palette="cold")
        img = self.gen.generate(self.stone, s).image
        self.assertLess(compare.similarity(self.stone, img), 0.05)

    def test_blocks_tile(self):
        """Opposite edges of a tileable block should be as coherent as interior rows."""
        s = TextureSettings(seed=19, size=32)
        a = np.asarray(self.gen.generate(None, s).image).astype(np.int32)[..., :3]
        seam = np.abs(a[0] - a[-1]).mean()
        interior = np.mean([np.abs(a[i] - a[i + 1]).mean() for i in range(a.shape[0] - 1)])
        self.assertLess(seam, interior * 2.5 + 1)

    def test_no_salt_and_pepper(self):
        s = TextureSettings(seed=31, noise=0.6)
        a = np.asarray(self.gen.generate(None, s).image)
        key = a[..., 0].astype(np.int64) * 65536 + a[..., 1].astype(np.int64) * 256 + a[..., 2]
        iso = pa.isolated_pixels(key, wrap=True)
        self.assertLess(iso.mean(), 0.2, "too many isolated pixels")


class AnalyzerTests(unittest.TestCase):
    def test_analyze_stone(self):
        a = analyze(_stone_like())
        self.assertEqual(a.levels, 4)
        self.assertAlmostEqual(sum(a.level_weights), 1.0, places=5)
        self.assertFalse(a.has_alpha)

    def test_analyze_sprite(self):
        a = analyze(_sprite_like())
        self.assertTrue(a.has_alpha)
        self.assertGreater(a.silhouette.coverage, 0)
        self.assertEqual(a.silhouette.stems, 1)

    def test_generator_registry(self):
        for cat in CATEGORIES:
            self.assertEqual(get_generator(cat).category, cat)


if __name__ == "__main__":
    unittest.main()
