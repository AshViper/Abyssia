"""Export, batch, presets, palette, history and AI-bridge tests."""
from __future__ import annotations

import json
import shutil
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ai.prompt_parser import PromptParser, extract_json, keyword_params  # noqa: E402
from ai.schema import params_to_settings  # noqa: E402
from core.batch import guess_category, jobs_from_folder, jobs_from_preset, run_batch  # noqa: E402
from core.config import BATCH_PRESET_DIR, PRESET_DIR, AppConfig  # noqa: E402
from core.generator import TextureGenerator  # noqa: E402
from core.history import History, HistoryEntry, UndoStack  # noqa: E402
from core.palette import (ColorAdjust, adjust_colors, build_ramp, extract_palette, generate_palette,  # noqa: E402
                          luminance, THEMES)
from core.presets import list_presets, load_batch_preset, load_preset, save_preset  # noqa: E402
from core.settings import TextureSettings  # noqa: E402
from export.png import export_png, export_series, read_embedded_settings, sanitize_name  # noqa: E402
from export.resource_pack import PackTexture, export_resource_pack  # noqa: E402


class TempDirCase(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="tf_test_"))

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)


class ExportTests(TempDirCase):
    def test_png_export_naming(self):
        img = TextureGenerator().generate(None, TextureSettings(seed=1)).image
        p0 = export_png(img, self.tmp, "Abyssal Rock", {"seed": 1})
        p1 = export_png(img, self.tmp, "abyssal_rock")
        p2 = export_png(img, self.tmp, "abyssal_rock")
        self.assertEqual([p0.name, p1.name, p2.name],
                         ["abyssal_rock.png", "abyssal_rock_01.png", "abyssal_rock_02.png"])
        with Image.open(p0) as im:
            self.assertEqual(im.size, (16, 16))
            self.assertEqual(im.mode, "RGBA")
            self.assertEqual(np.asarray(im).tobytes(), np.asarray(img).tobytes())
        self.assertEqual(read_embedded_settings(p0), {"seed": 1})

    def test_export_series(self):
        gen = TextureGenerator()
        res = gen.generate_variations(None, TextureSettings(seed=3), 3)
        paths = export_series([r.image for r in res], self.tmp, "trench_rock")
        self.assertEqual([p.name for p in paths], ["trench_rock.png", "trench_rock_01.png", "trench_rock_02.png"])

    def test_resource_pack(self):
        img = TextureGenerator().generate(None, TextureSettings(seed=2)).image
        root = export_resource_pack([PackTexture("abyssal_rock", img)], self.tmp, "abyssalworld", "pack", "desc", 15)
        mc = json.loads((root / "pack.mcmeta").read_text())
        self.assertEqual(mc["pack"]["pack_format"], 15)
        self.assertTrue((root / "assets/abyssalworld/textures/block/abyssal_rock.png").exists())
        self.assertTrue((root / "pack.png").exists())

    def test_resource_pack_zip_keeps_existing_folder(self):
        img = TextureGenerator().generate(None, TextureSettings(seed=2)).image
        keep = self.tmp / "pack" / "my_notes.txt"
        keep.parent.mkdir(parents=True)
        keep.write_text("mine")
        z = export_resource_pack([PackTexture("rock", img, "block")], self.tmp, "modid", "pack", as_zip=True)
        self.assertTrue(keep.exists(), "zip export must not delete an existing folder")
        with zipfile.ZipFile(z) as zf:
            self.assertIn("assets/modid/textures/block/rock.png", zf.namelist())
            self.assertIn("pack.mcmeta", zf.namelist())

    def test_sanitize(self):
        self.assertEqual(sanitize_name("Deep Kelp (Top)!"), "deep_kelp_top")


class BatchTests(TempDirCase):
    def test_batch_from_folder(self):
        src = self.tmp / "in"
        src.mkdir()
        rng = np.random.default_rng(0)
        for name in ("stone", "iron_ore", "kelp"):
            arr = rng.integers(60, 160, (16, 16, 4)).astype(np.uint8)
            arr[..., 3] = 255
            if name == "kelp":
                arr[:, :6, 3] = 0
            Image.fromarray(arr, "RGBA").save(src / f"{name}.png")
        jobs = jobs_from_folder(src, TextureSettings(seed=5), prefix="abyssal_")
        self.assertEqual([j.settings.category for j in jobs], ["ore", "kelp", "terrain"])
        out = run_batch(jobs, self.tmp / "out", variations=2,
                        resource_pack={"mod_id": "abyssalworld", "pack_name": "batch_pack"})
        self.assertTrue(all(not o.error for o in out), [o.error for o in out])
        files = sorted(p.name for p in (self.tmp / "out").glob("*.png"))
        self.assertIn("abyssal_stone.png", files)
        self.assertIn("abyssal_stone_01.png", files)
        self.assertTrue((self.tmp / "out/batch_pack/pack.mcmeta").exists())

    def test_batch_preset(self):
        bp = load_batch_preset("deep_sea_terrain_pack")
        jobs = jobs_from_preset(bp)
        self.assertEqual([j.settings.name for j in jobs],
                         ["abyssal_rock", "deep_sediment", "trench_rock", "thermal_rock", "mineral_sediment",
                          "crystal_rock"])
        out = run_batch(jobs, self.tmp, 1)
        self.assertEqual(len(out), 6)
        self.assertTrue(all(o.files for o in out))

    def test_guess_category(self):
        self.assertEqual(guess_category("deepslate_iron_ore"), "ore")
        self.assertEqual(guess_category("amethyst_cluster", True), "crystal")
        self.assertEqual(guess_category("tall_seagrass_top", True), "plant")
        self.assertEqual(guess_category("stone"), "terrain")


class PresetTests(TempDirCase):
    def test_all_shipped_presets_generate(self):
        gen = TextureGenerator()
        presets = list_presets(PRESET_DIR)
        self.assertGreaterEqual(len(presets), 8)
        for p in presets:
            with self.subTest(preset=p.stem):
                s = load_preset(p)
                gen.generate(None, s)

    def test_all_batch_presets_resolve(self):
        for p in BATCH_PRESET_DIR.glob("*.json"):
            with self.subTest(batch=p.stem):
                self.assertTrue(jobs_from_preset(load_batch_preset(p)))

    def test_spec_example_json(self):
        data = {"category": "terrain", "size": 16, "palette": "abyss", "roughness": 0.82, "contrast": 0.65,
                "noise": 0.35, "cracks": 0.42, "crystal": 0.08, "organic": 0.02, "seed": 123456}
        path = self.tmp / "x.json"
        path.write_text(json.dumps(data))
        s = load_preset(path)
        self.assertEqual((s.roughness, s.seed, s.palette), (0.82, 123456, "abyss"))

    def test_save_and_load_roundtrip(self):
        s = TextureSettings(category="ore", variant="vein", roughness=0.33, seed=99, accent="copper")
        path = save_preset(s, "My Ore", self.tmp)
        self.assertEqual(path.name, "my_ore.json")
        back = load_preset(path)
        self.assertEqual(back.to_dict(), s.clamp().to_dict())


class PaletteTests(unittest.TestCase):
    def test_ramp_is_dark_to_light(self):
        r = build_ramp(["#0C1519", "#3B5A5D"], 6)
        L = luminance(r.as_array())
        self.assertTrue(np.all(np.diff(L) > 0))

    def test_generated_palette_is_new(self):
        """Generation must not reuse the reference palette."""
        ref = np.zeros((16, 16, 4), np.uint8)
        ref[..., :3] = 119
        ref[..., 3] = 255
        ext = extract_palette(ref)
        pal = generate_palette("abyss", 6)
        self.assertFalse(set(ext.colors) & set(pal.all_colors()))

    def test_every_theme_builds(self):
        for t in THEMES:
            generate_palette(t, 7, accent="sulfur_cyan")

    def test_neutral_adjust_is_identity(self):
        c = np.array([[10, 20, 30], [200, 100, 50]], float)
        self.assertTrue(np.allclose(adjust_colors(c, ColorAdjust()), c, atol=1.0))


class HistoryTests(TempDirCase):
    def test_undo_redo(self):
        st = UndoStack()
        img = Image.new("RGBA", (16, 16))
        for seed in (1, 2, 3):
            st.push(HistoryEntry(TextureSettings(seed=seed), img))
        self.assertEqual(st.undo().settings.seed, 2)
        self.assertEqual(st.undo().settings.seed, 1)
        self.assertIsNone(st.undo())
        self.assertEqual(st.redo().settings.seed, 2)
        st.push(HistoryEntry(TextureSettings(seed=9), img))
        self.assertFalse(st.can_redo())

    def test_history_persists(self):
        h = History(self.tmp)
        img = TextureGenerator().generate(None, TextureSettings(seed=4)).image
        h.add(HistoryEntry(TextureSettings(seed=4, name="deep_kelp"), img))
        h2 = History(self.tmp)
        h2.load()
        self.assertEqual(len(h2.entries), 1)
        self.assertEqual(h2.entries[0].settings.seed, 4)
        self.assertEqual(h2.entries[0].label()[-9:], "deep_kelp")

    def test_config_roundtrip(self):
        cfg = AppConfig(mod_id="abyssalworld")
        cfg.ai.model = "qwen3.5:4b"
        cfg.save(self.tmp / "c.json")
        back = AppConfig.load(self.tmp / "c.json")
        self.assertEqual(back.mod_id, "abyssalworld")
        self.assertEqual(back.ai.model, "qwen3.5:4b")
        self.assertEqual(back.pack_format, 15)


class FakeLLM:
    def __init__(self, reply: str):
        self.reply = reply

    def chat(self, system, user, schema=None):
        return self.reply

    def list_models(self):
        return ["fake"]

    def test(self):
        return True, "ok"


class AITests(unittest.TestCase):
    SPEC_PROMPT = ("深海の熱水噴出孔周辺にある黒い岩。\n硫黄が少し付着していて、\n"
                   "ところどころ青緑色の鉱物が露出している。\nMinecraft風16×16。")

    def test_llm_json_is_mapped(self):
        reply = ('<think>hmm</think>```json\n{"category": "thermal", "material": "rock", "base_palette": "abyss", '
                 '"accent_palette": "sulfur_cyan", "roughness": 0.85, "cracks": 0.45, '
                 '"mineral_density": 0.30, "brightness": 0.25}\n```')
        res = PromptParser(FakeLLM(reply)).parse(self.SPEC_PROMPT)
        s = res.settings
        self.assertEqual(res.source, "llm")
        self.assertEqual((s.category, s.palette, s.accent), ("thermal", "abyss", "sulfur_cyan"))
        self.assertAlmostEqual(s.mineral, 0.30)
        self.assertAlmostEqual(s.brightness, 0.25)

    def test_bad_llm_output_falls_back(self):
        res = PromptParser(FakeLLM("sorry, I can't")).parse(self.SPEC_PROMPT)
        self.assertEqual(res.source, "keywords")
        self.assertEqual(res.settings.category, "thermal")
        self.assertEqual(res.settings.accent, "sulfur_cyan")
        self.assertEqual(res.settings.size, 16)

    def test_keyword_parser(self):
        p = keyword_params("黒い深海岩石。青緑色の鉱物が少量露出している")
        self.assertEqual(p["category"], "terrain")
        self.assertEqual(p["accent"], "cyan_mineral")
        self.assertLess(p["brightness"], 0.5)

    def test_schema_clamps_and_warns(self):
        m = params_to_settings({"category": "vent", "roughness": 85, "glow": "high", "bogus": 1})
        self.assertEqual(m.settings.category, "thermal")
        self.assertAlmostEqual(m.settings.roughness, 0.85)
        self.assertAlmostEqual(m.settings.glow, 0.75)
        self.assertTrue(any("bogus" in w for w in m.warnings))

    def test_extract_json(self):
        self.assertEqual(extract_json('text {"a": {"b": "}"}} more'), {"a": {"b": "}"}})

    def test_ai_params_generate(self):
        s = PromptParser(None).parse(self.SPEC_PROMPT).settings
        img = TextureGenerator().generate(None, s).image
        self.assertEqual(img.size, (16, 16))


if __name__ == "__main__":
    unittest.main()
