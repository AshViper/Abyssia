# -*- coding: utf-8 -*-
"""裂溝ヤツメの図を out/ へ描く（build.py を流したあとで）。

| 出力 | 中身 |
| --- | --- |
| `out/lamprey_views.png` | 静止姿勢の六面図 |
| `out/lamprey_oblique.png` | 斜め前（idle の途中の蛇行姿勢） |
| `out/lamprey_swim.png` | idle の途中（上と横）。進行波の折れ線を見る |
| `out/lamprey_mouth.png` | 頭の拡大: 静止 / attack の突き出し / latch（正面と横） |
| `out/lamprey_glow.png` | 発光テクスチャだけを貼った横の図 |
| `out/lamprey_compare.png` | デザイン画の 6 番と並べた比較 |
"""
import json
import pathlib
import sys
import tempfile
import types

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import boss_views
import cube_kit as ck
import build
from build import NAME

OUT = HERE / 'out'
_TMP = tempfile.TemporaryDirectory(prefix='lamprey_posed_')
POSED = pathlib.Path(_TMP.name)
SHEET = pathlib.Path('F:/Java/Abyssia/inbox/designs/GNT01-design-sheet.png')
CROP = (630, 270, 1190, 380)


def atlas_of(path):
    rows = ck.load_png(path)
    return types.SimpleNamespace(SIZE=len(rows), build=lambda: rows)


def views(*names):
    return tuple(v for n in names for v in boss_views.VIEWS if v[0] == n)


def write_posed(g, tag):
    name = f"{NAME}_{tag}"
    (POSED / f"{name}.geo.json").write_text(json.dumps(g), encoding='utf-8')
    return name


def head_only(g):
    keep = set()
    for b in g["minecraft:geometry"][0]["bones"]:
        if b["name"] in ("head", "sleeve", "mouth", "teeth", "_view") or b["name"][-2:] in ("01", "02", "03"):
            keep.add(b["name"])
    return keep


def compare():
    from PIL import Image
    ref = Image.open(SHEET).convert('RGB').crop(CROP)
    ref = ref.resize((ref.width * 2, ref.height * 2), Image.LANCZOS)
    side = Image.open(OUT / 'lamprey_side.png').convert('RGB')
    obl = Image.open(OUT / 'lamprey_oblique.png').convert('RGB')
    w = max(ref.width, side.width, obl.width)
    canvas = Image.new('RGB', (w, ref.height + side.height + obl.height + 16), (20, 22, 26))
    y = 0
    for im in (ref, side, obl):
        canvas.paste(im, (0, y))
        y += im.height + 8
    canvas.save(OUT / 'lamprey_compare.png')
    print('wrote', OUT / 'lamprey_compare.png')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    geo = json.loads((ck.GEO / f"{NAME}.geo.json").read_text(encoding='utf-8'))
    anims = json.loads((ck.ANIM / f"{NAME}.animation.json").read_text(encoding='utf-8'))["animations"]
    atlas = atlas_of(ck.TEX / f"{NAME}_geo.png")
    glow = atlas_of(ck.TEX / f"{NAME}_geo_glow.png")

    boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'lamprey_views.png', scale=3.0)
    boss_views.render_parts(glow, [(NAME, 0.0)], OUT / 'lamprey_glow.png', scale=3.0, views=views('LEFT'))

    swim = ck.posed(geo, anims["idle"], t=1.3)
    n = write_posed(swim, 'swim')
    boss_views.render_parts(atlas, [(n, 0.0)], OUT / 'lamprey_swim.png', scale=3.0,
                            views=views('TOP', 'LEFT'), geo_dir=POSED)
    boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'lamprey_side.png', scale=3.0, views=views('LEFT'))
    # 斜め前: 蛇行姿勢を、頭を手前へ 35 度ヨー、上から 18 度
    n = write_posed(ck.posed(geo, anims["idle"], t=1.3, view=(-18, 35, 0)), 'oblique')
    boss_views.render_parts(atlas, [(n, 0.0)], OUT / 'lamprey_oblique.png', scale=3.0,
                            views=views('LEFT'), geo_dir=POSED)

    # 頭の拡大: 静止 / attack の当たり / latch
    for tag, anim, t in (('rest', None, 0.0), ('attack', anims["attack"], 0.5), ('latch', anims["latch"], 0.3)):
        g = ck.posed(geo, anim, t)
        n = write_posed(g, f'mouth_{tag}')
        boss_views.render_parts(atlas, [(n, 0.0)], OUT / f'lamprey_mouth_{tag}.png', scale=7.0,
                                views=views('FRONT', 'LEFT'), geo_dir=POSED, bones=head_only(g))
        g = ck.posed(geo, anim, t, view=(-15, 50, 0))
        n = write_posed(g, f'mouth_{tag}_o')
        boss_views.render_parts(atlas, [(n, 0.0)], OUT / f'lamprey_mouth_{tag}_oblique.png', scale=7.0,
                                views=views('LEFT'), geo_dir=POSED, bones=head_only(g))
    compare()


if __name__ == '__main__':
    main()
