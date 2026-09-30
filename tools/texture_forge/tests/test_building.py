"""Tests for the wood, item and dripstone categories and the seagrass kelp variant.

Run from the texture_forge folder:  python -m unittest discover -s tests -v
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from core.generator import TextureGenerator  # noqa: E402
from core.settings import TextureSettings  # noqa: E402


def _alpha(img) -> np.ndarray:
    return np.asarray(img)[..., 3]


class WoodTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gen = TextureGenerator()

    def _make(self, **kw):
        return self.gen.generate(None, TextureSettings.from_dict({"category": "wood", "seed": 5, **kw}))

    def test_blocks_are_opaque(self):
        for v in ("planks", "log", "log_top", "stripped_log", "stripped_log_top"):
            with self.subTest(variant=v):
                self.assertTrue((_alpha(self._make(variant=v).image) == 255).all())

    def test_planks_tile(self):
        a = np.asarray(self._make(variant="planks", size=32).image).astype(np.int32)[..., :3]
        seam = np.abs(a[:, 0] - a[:, -1]).mean()
        interior = np.mean([np.abs(a[:, i] - a[:, i + 1]).mean() for i in range(a.shape[1] - 1)])
        self.assertLess(seam, interior * 2.5 + 1)

    def test_door_halves_share_one_plan(self):
        top = _alpha(self._make(variant="door_top").image)
        bottom = _alpha(self._make(variant="door_bottom").image)
        self.assertTrue((top == 0).any(), "the porthole should be see-through")
        self.assertTrue((bottom == 255).all(), "the bottom half has no window")
        # a dark frame runs down the right edge of both halves
        for half in ("door_top", "door_bottom"):
            a = np.asarray(self._make(variant=half).image).astype(int)
            lum = a[..., :3].sum(-1)
            inside = lum[:, 2:-2][a[:, 2:-2, 3] > 0]
            self.assertTrue((a[:, -1, 3] == 255).all())
            self.assertLess(lum[:, -1].mean(), inside.mean())

    def test_bark_uses_secondary_colour(self):
        r = self._make(variant="log", secondary_color="#204060")
        ramp = {tuple(c) for c in r.palette.ramp("secondary").colors}
        px = {tuple(p[:3]) for p in np.asarray(r.image).reshape(-1, 4)}
        self.assertGreater(len(px & ramp), 2)

    def test_door_item_is_a_sprite(self):
        a = _alpha(self._make(variant="door_item").image)
        self.assertTrue((a == 0).any() and (a == 255).any())


class ItemTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gen = TextureGenerator()

    def test_items_have_outline_and_background(self):
        for v in ("ingot", "raw", "dust", "nugget"):
            with self.subTest(variant=v):
                r = self.gen.generate(None, TextureSettings.from_dict(
                    {"category": "item", "variant": v, "base_color": "#4A78D8", "seed": 3}))
                a = np.asarray(r.image)
                self.assertTrue((a[..., 3] == 0).any(), "items need a transparent background")
                self.assertGreater((a[..., 3] == 255).sum(), 8, "item is too small")
                if v != "dust":   # powder only gets a soft darker rim
                    darkest = tuple(r.palette.ramp("base").colors[0])
                    self.assertTrue((a[..., :3] == np.array(darkest, dtype=np.uint8)).all(-1).any(), "no dark outline")


class DripstoneTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gen = TextureGenerator()

    def _make(self, **kw):
        return self.gen.generate(None, TextureSettings.from_dict({"category": "dripstone", "seed": 8, **kw}))

    def test_up_mirrors_down(self):
        for v in ("tip", "middle", "base"):
            with self.subTest(variant=v):
                down = np.asarray(self._make(variant=v, part="down").image)
                up = np.asarray(self._make(variant=v, part="up").image)
                self.assertTrue((down[::-1] == up).all())

    def test_tip_is_narrower_than_base(self):
        tip = (_alpha(self._make(variant="tip").image) > 0).sum()
        base = (_alpha(self._make(variant="base").image) > 0).sum()
        self.assertLess(tip, base)

    def test_glow_core(self):
        r = self._make(variant="middle", glow=0.8)
        self.assertIsNotNone(r.emission)


class SeagrassTests(unittest.TestCase):
    def test_stalk_tiles_vertically_and_top_continues_it(self):
        gen = TextureGenerator()
        s = {"category": "kelp", "variant": "seagrass", "seed": 12}
        stalk = _alpha(gen.generate(None, TextureSettings.from_dict({**s, "part": "stalk"})).image) > 0
        top = _alpha(gen.generate(None, TextureSettings.from_dict({**s, "part": "top"})).image) > 0
        # blades leave the top of a stalk tile where they enter its bottom ...
        self.assertTrue(stalk[0].any() and stalk[-1].any())
        for x in np.nonzero(stalk[0])[0]:
            self.assertTrue(stalk[-1, max(0, x - 1):x + 2].any())
        # ... and the top part starts from the same columns
        for x in np.nonzero(top[-1])[0]:
            self.assertTrue(stalk[0, max(0, x - 1):x + 2].any())


if __name__ == "__main__":
    unittest.main()


class SlateTests(unittest.TestCase):
    """The deepslate-like strata mode, alteration patches and ramps given as colour lists."""

    @classmethod
    def setUpClass(cls):
        cls.gen = TextureGenerator()

    def _make(self, **kw):
        return self.gen.generate(None, TextureSettings.from_dict({"category": "terrain", "material": "slate", "seed": 4,
                                                                  **kw}))

    def test_colour_list_is_the_ramp(self):
        ramp = "#26282E,#33363D,#40444B,#4E535A,#5C6168,#6B7076"
        r = self._make(base_color=ramp, hue_shift=0.0, levels=4)
        base = r.palette.ramp("base").colors
        self.assertEqual(len(base), 6)
        self.assertLess(sum(base[0]), sum(base[-1]))

    def test_strata_highlights_stay_below_the_top(self):
        r = self._make(levels=5, moisture=0.0)
        top = tuple(r.palette.ramp("base").colors[-1])
        px = {tuple(p[:3]) for p in np.asarray(r.image).reshape(-1, 4)}
        self.assertNotIn(top, px, "slate highlights should never reach the top ramp level")

    def test_strata_clusters_run_along_the_bedding(self):
        a = np.asarray(self._make(size=32, levels=5).image)[..., :3].astype(int)
        horiz = np.mean(np.any(a[:, 1:] != a[:, :-1], axis=-1))
        vert = np.mean(np.any(a[1:] != a[:-1], axis=-1))
        self.assertLess(horiz, vert, "tone changes should be rarer along a row than down a column")

    def test_alteration_uses_the_secondary_ramp(self):
        plain = self._make(alteration=0.0, secondary_color="#6A2E22")
        altered = self._make(alteration=0.6, secondary_color="#6A2E22")
        sec = {tuple(c) for c in altered.palette.ramp("secondary").colors}
        count = lambda r: sum(tuple(p[:3]) in sec for p in np.asarray(r.image).reshape(-1, 4))
        self.assertEqual(count(plain), 0)
        self.assertGreater(count(altered), 20)


class RecolourTests(unittest.TestCase):
    """recolor_source: the reference's own layout, recoloured; source_host splits host from ore; crack veins."""

    @classmethod
    def setUpClass(cls):
        cls.gen = TextureGenerator()
        rng = np.random.default_rng(1)
        tones = np.array([60, 80, 100, 120, 140])
        g = tones[rng.integers(0, 5, (16, 16))]
        cls.stone = np.stack([g, g, g, np.full_like(g, 255)], -1).astype(np.uint8)
        ore = cls.stone.copy()
        ore[4:7, 4:7, :3] = (200, 120, 60)
        ore[10:12, 9:13, :3] = (230, 160, 90)
        cls.ore = ore

    def _gen(self, src, **kw):
        from PIL import Image
        s = TextureSettings.from_dict({"recolor_source": True, "levels": 5, "seed": 3, "cracks": 0.0,
                                       "moisture": 0.0, "layering": 0.0, "mineral": 0.0, **kw})
        return self.gen.generate(Image.fromarray(src, "RGBA"), s)

    def test_layout_is_kept(self):
        r = self._gen(self.stone, category="terrain")
        out = np.asarray(r.image)[..., :3].astype(int).sum(-1)
        src = self.stone[..., 0].astype(int)
        # same tone order everywhere: a lighter source pixel is never darker in the output
        order_src = np.argsort(src.ravel(), kind="stable")
        self.assertTrue((np.diff(out.ravel()[order_src]) >= 0).all())

    def test_source_host_splits_ore(self):
        host = ",".join(f"#{t:02X}{t:02X}{t:02X}" for t in (60, 80, 100, 120, 140))
        r = self._gen(self.ore, category="ore", source_host=host, accent="#3A6AD8")
        acc = {tuple(c) for c in r.palette.ramp("accent").colors}
        a = np.asarray(r.image)
        is_acc = np.array([[tuple(p[:3]) in acc for p in row] for row in a])
        self.assertTrue(is_acc[4:7, 4:7].all() and is_acc[10:12, 9:13].all())
        self.assertEqual(int(is_acc.sum()), 9 + 8)

    def test_crack_veins_use_the_crack_colour(self):
        r = self._gen(self.stone, category="terrain", cracks=0.6, crack_color="#8308E4", glow=0.5)
        vein = {tuple(c) for c in r.palette.ramp("accent2").colors}
        px = [tuple(p[:3]) for p in np.asarray(r.image).reshape(-1, 4)]
        self.assertGreater(sum(p in vein for p in px), 6)
        self.assertIsNotNone(r.emission, "glowing vein cores should be emissive")
