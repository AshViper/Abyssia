"""Tests for tools/texture_gen.py (plain asserts):  python tools/test_texture_gen.py

Generation reads the real textures (read-only).  apply / undo / lock refusal run on a temp copy of a few textures
and locks (texture_gen.configure), never on the game assets.
"""
from __future__ import annotations

import hashlib
import shutil
import sys
import tempfile
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import texture_gen as tg  # noqa: E402

REAL_TEX = tg.P.tex
REAL_LOCKS = tg.P.locks

JOBS = [
    ("recolor", {"base": "block/deep_sea_rock", "palette": "block/thermal_rock"}),
    ("recolor", {"base": "block/cave_fern", "hue": 90}),
    ("variants", {"base": "block/deep_sea_rock", "type": "bricks+mossy"}),
    ("variants", {"base": "block/trench_rock", "type": "frosted"}),
    ("ore", {"palette": "metal:silver", "pattern": "nuggets"}),
    ("ore", {"palette": "metal:gold", "pattern": "veins", "host": "block/deep_sea_rock"}),
    ("ore", {"palette": "metal:lithium", "pattern": "crystals"}),
    ("synth", {"profile": "rocks", "palette": "block/volcanic_rock", "cracks": 1}),
    ("sprite", {"base": "block/cave_fern", "palette": "block/amber_crystal_block", "sway": "auto"}),
    ("tier", {"base": "item/abyssal_alloy_pickaxe", "palette": "metal:orichalcum"}),
    ("blend", {"a": "block/deep_sea_rock", "b": "block/abyssal_moss", "mask": "vertical", "amount": 0.4}),
]


def _tree_hash(root: Path) -> str:
    h = hashlib.md5()
    for f in sorted(root.rglob("*.png")):
        h.update(str(f.relative_to(root)).encode())
        h.update(f.read_bytes())
    return h.hexdigest()


def test_determinism_and_quality():
    for gen, params in JOBS:
        a, ma = tg.generate(gen, params, 7)
        b, mb = tg.generate(gen, params, 7)
        assert np.array_equal(a, b), f"{gen} not deterministic"
        assert a.shape == (16, 16, 4) and a.dtype == np.uint8, gen
        vis = a[..., 3] > 0
        assert len(np.unique(a[..., :3][vis], axis=0)) <= 16, f"{gen}: more than 16 colours"
        assert set(np.unique(a[..., 3])) <= {0, 255}, f"{gen}: soft alpha"
        if ma["kind"] == "block":
            assert (a[..., 3] == 255).all(), f"{gen}: block not opaque"
    s0, _ = tg.generate("synth", {"profile": "rocks"}, 1)
    s1, _ = tg.generate("synth", {"profile": "rocks"}, 2)
    assert not np.array_equal(s0, s1), "different seeds should differ"


def test_tileable():
    """Seam across the wrap edges is no worse than inside (generated blocks tile)."""
    for gen, params in JOBS:
        if gen in ("variants", "blend") or tg.generate(gen, params, 3)[1]["kind"] != "block":
            continue
        for seed in (1, 2, 3):
            img, meta = tg.generate(gen, params, seed)
            assert meta["seam"] < 1.6, f"{gen} seed {seed}: seam ratio {meta['seam']}"
    # the noise itself is exactly periodic
    n = tg.spectral_noise(tg.iso_spectrum(4), np.random.default_rng(1))
    big = np.tile(n, (2, 2))
    assert np.allclose(big[:16, :16], big[16:, 16:])


def test_alpha_preserved():
    for gen, params in (("recolor", {"base": "block/cave_fern", "palette": "metal:gold"}),
                        ("tier", {"base": "item/abyssal_alloy_pickaxe", "palette": "ingot:platinum"})):
        base = tg.load(params["base"])
        img, _ = tg.generate(gen, params, 0)
        assert np.array_equal(img[..., 3] > 0, base[..., 3] > 127), f"{gen}: silhouette changed"
    # tier keeps the wooden handle
    base = tg.load("item/abyssal_alloy_pickaxe")
    img, meta = tg.generate("tier", {"base": "item/abyssal_alloy_pickaxe", "palette": "metal:gold"}, 0)
    assert np.array_equal(img[14, 2], base[14, 2]), "handle pixel changed"
    assert meta["id"] == "gold_pickaxe"


def test_palette_and_profile():
    r, smooth, _ = tg.parse_palette("#101010,#808080,#f0f0f0")
    assert len(r) == 3 and smooth
    assert len(tg.ramp_of(tg.load("block/deep_sea_rock"), 6)) <= 6
    try:
        tg.parse_palette("metal:nope")
        raise AssertionError("unknown metal accepted")
    except tg.TexGenError:
        pass
    for bad in ("../x", "block/../../x", "C:/x", "block/a b"):
        try:
            tg.resolve(bad)
            raise AssertionError(f"bad ref accepted: {bad}")
        except tg.TexGenError:
            pass


def _temp_copy(tmp: Path):
    assets, locks = tmp / "assets", tmp / "locks"
    for rel in ("textures/block/deep_sea_rock.png", "textures/block/trench_rock.png", "textures/block/thermal_rock.png",
                "textures/block/cave_fern.png", "textures/block/abyssal_moss.png", "textures/block/tungsten_ore.png",
                "textures/block/amber_crystal_block.png", "textures/block/crystal_rock.png"):
        dst = assets / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REAL_TEX.parent / rel, dst)
    (locks / "textures/block").mkdir(parents=True)
    shutil.copy2(assets / "textures/block/trench_rock.png", locks / "textures/block/trench_rock.png")
    (assets / "models").mkdir()
    tg.configure(root=tmp, assets=assets, locks=locks, generated=tmp / "generated", backup=tmp / "backup")
    return assets, locks


def test_apply_undo_and_locks():
    real_before = _tree_hash(REAL_TEX)
    locks_before = _tree_hash(REAL_LOCKS)
    tmp = Path(tempfile.mkdtemp(prefix="texgen-test-"))
    try:
        assets, locks = _temp_copy(tmp)
        tg.run_job("recolor", {"base": "block/deep_sea_rock", "palette": "block/thermal_rock", "id": "deep_sea_rock"},
                   [0], "t1")
        tg.run_job("ore", {"palette": "metal:silver", "id": "silver_ore"}, [0], "t1")
        tg.run_job("recolor", {"base": "block/trench_rock", "hue": 40, "id": "trench_rock"}, [0], "t1")
        assert (tmp / "generated/t1/contact.png").is_file() and (tmp / "generated/t1/manifest.json").is_file()
        assert {c["name"] for c in tg.list_candidates("t1")} == {"deep_sea_rock", "silver_ore", "trench_rock"}

        target = assets / "textures/block/deep_sea_rock.png"
        before = target.read_bytes()
        dry = tg.apply([{"batch": "t1", "name": "deep_sea_rock"}], dry_run=True)
        assert dry["plan"][0]["action"] == "write" and target.read_bytes() == before, "dry run wrote"

        res = tg.apply([{"batch": "t1", "name": "deep_sea_rock"}, {"batch": "t1", "name": "silver_ore"}])
        assert sorted(res["written"]) == ["block/deep_sea_rock", "block/silver_ore"]
        assert target.read_bytes() != before
        assert (assets / "textures/block/silver_ore.png").is_file()
        assert not (locks / "textures/block/deep_sea_rock.png").exists(), "locked without --lock"

        # locked target refused without force_locked, nothing written
        lock_file = locks / "textures/block/trench_rock.png"
        lbefore, tbefore = lock_file.read_bytes(), (assets / "textures/block/trench_rock.png").read_bytes()
        try:
            tg.apply([{"batch": "t1", "name": "trench_rock"}])
            raise AssertionError("locked texture overwritten")
        except tg.TexGenError as e:
            assert "locked" in str(e)
        assert lock_file.read_bytes() == lbefore
        assert (assets / "textures/block/trench_rock.png").read_bytes() == tbefore

        # forced: texture and its lock updated; undo restores both
        tg.apply([{"batch": "t1", "name": "trench_rock"}], force_locked=True)
        assert lock_file.read_bytes() != lbefore
        u = tg.undo()
        assert lock_file.read_bytes() == lbefore
        assert (assets / "textures/block/trench_rock.png").read_bytes() == tbefore
        assert {f["rel"] for f in u["files"]} >= {"textures/block/trench_rock.png"}

        # undo the first apply: modified file restored, new file removed
        tg.undo()
        assert target.read_bytes() == before
        assert not (assets / "textures/block/silver_ore.png").exists()
        try:
            tg.undo()
            raise AssertionError("undo with nothing left")
        except tg.TexGenError:
            pass

        # derived textures need --lock (derive_textures would overwrite them)
        tg.run_job("variants", {"base": "block/deep_sea_rock", "type": "bricks"}, [0], "t1")
        try:
            tg.apply([{"batch": "t1", "name": "deep_sea_rock_bricks"}])
            raise AssertionError("derived texture applied without lock")
        except tg.TexGenError as e:
            assert "derive" in str(e)

        # bad names / traversal
        for bad in ({"batch": "../x", "name": "a"}, {"batch": "t1", "name": "../../a"},
                    {"batch": "t1", "name": "silver_ore", "as": "block/../../evil"}):
            try:
                tg.apply([bad], dry_run=True)
                raise AssertionError(f"accepted {bad}")
            except tg.TexGenError:
                pass
    finally:
        tg.configure()
        shutil.rmtree(tmp, ignore_errors=True)
    assert _tree_hash(REAL_TEX) == real_before, "real textures changed"
    assert _tree_hash(REAL_LOCKS) == locks_before, "real locks changed"


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for t in tests:
        t()
        print("ok", t.__name__)
    print(f"{len(tests)} passed")
