# -*- coding: utf-8 -*-
"""冠鰭の図を out/ へ描く（build.py を流したあとで）。

| 出力 | 中身 |
| --- | --- |
| `out/crestfin_views.png` | 静止姿勢の六面図 |
| `out/crestfin_side.png` / `crestfin_oblique.png` | 横 / 斜め前 |
| `out/crestfin_crest_up.png` | crest_up の最後のコマ（横） |
| `out/crestfin_head_<rest|attack|crest_up>.png` | 頭〜seg_04 の拡大（正面と横）と斜め |
| `out/crestfin_swim.png` | idle の途中（上） |
| `out/crestfin_glow.png` | 発光テクスチャだけを貼った横の図 |
| `out/crestfin_compare.png` | デザイン画の 9 番と並べた比較 |
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
from build import NAME

OUT = HERE / 'out'
_TMP = tempfile.TemporaryDirectory(prefix='crestfin_posed_')
POSED = pathlib.Path(_TMP.name)
SHEET = pathlib.Path('F:/Java/Abyssia/inbox/designs/GNT01-design-sheet.png')
CROP = (55, 510, 570, 660)


def atlas_of(path):
    rows = ck.load_png(path)
    return types.SimpleNamespace(SIZE=len(rows), build=lambda: rows)


def views(*names):
    return tuple(v for n in names for v in boss_views.VIEWS if v[0] == n)


def write_posed(g, tag):
    name = f"{NAME}_{tag}"
    (POSED / f"{name}.geo.json").write_text(json.dumps(g), encoding='utf-8')
    return name


def fore(g):
    """head と seg_02..seg_04 の下にある骨"""
    bones = g["minecraft:geometry"][0]["bones"]
    keep = {"head", "seg_02", "seg_03", "seg_04", "_view"}
    grew = True
    while grew:
        grew = False
        for b in bones:
            if b.get("parent") in keep - {"_view"} and b["name"] not in keep:
                keep.add(b["name"])
                grew = True
    return keep


def compare():
    from PIL import Image
    ref = Image.open(SHEET).convert('RGB').crop(CROP)
    ref = ref.resize((ref.width * 2, ref.height * 2), Image.LANCZOS)
    side = Image.open(OUT / 'crestfin_side.png').convert('RGB')
    obl = Image.open(OUT / 'crestfin_oblique.png').convert('RGB')
    w = max(ref.width, side.width, obl.width)
    canvas = Image.new('RGB', (w, ref.height + side.height + obl.height + 16), (20, 22, 26))
    y = 0
    for im in (ref, side, obl):
        canvas.paste(im, (0, y))
        y += im.height + 8
    canvas.save(OUT / 'crestfin_compare.png')
    print('wrote', OUT / 'crestfin_compare.png')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    geo = json.loads((ck.GEO / f"{NAME}.geo.json").read_text(encoding='utf-8'))
    anims = json.loads((ck.ANIM / f"{NAME}.animation.json").read_text(encoding='utf-8'))["animations"]
    atlas = atlas_of(ck.TEX / f"{NAME}_geo.png")
    glow = atlas_of(ck.TEX / f"{NAME}_geo_glow.png")

    boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'crestfin_views.png', scale=3.0)
    boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'crestfin_side.png', scale=3.0, views=views('LEFT'))
    boss_views.render_parts(glow, [(NAME, 0.0)], OUT / 'crestfin_glow.png', scale=3.0, views=views('LEFT'))
    n = write_posed(ck.posed(geo, anims["idle"], t=1.3, view=(-18, 35, 0)), 'oblique')
    boss_views.render_parts(atlas, [(n, 0.0)], OUT / 'crestfin_oblique.png', scale=3.0,
                            views=views('LEFT'), geo_dir=POSED)
    n = write_posed(ck.posed(geo, anims["idle"], t=1.3), 'swim')
    boss_views.render_parts(atlas, [(n, 0.0)], OUT / 'crestfin_swim.png', scale=3.0,
                            views=views('TOP'), geo_dir=POSED)
    n = write_posed(ck.posed(geo, anims["crest_up"], t=0.4), 'crest')
    boss_views.render_parts(atlas, [(n, 0.0)], OUT / 'crestfin_crest_up.png', scale=3.0,
                            views=views('LEFT'), geo_dir=POSED)

    for tag, anim, t in (('rest', None, 0.0), ('attack', anims["attack"], 0.42), ('crest_up', anims["crest_up"], 0.4)):
        g = ck.posed(geo, anim, t)
        n = write_posed(g, f'head_{tag}')
        boss_views.render_parts(atlas, [(n, 0.0)], OUT / f'crestfin_head_{tag}.png', scale=6.0,
                                views=views('LEFT', 'FRONT'), geo_dir=POSED, bones=fore(g))
        g = ck.posed(geo, anim, t, view=(-15, 45, 0))
        n = write_posed(g, f'head_{tag}_o')
        boss_views.render_parts(atlas, [(n, 0.0)], OUT / f'crestfin_head_{tag}_oblique.png', scale=6.0,
                                views=views('LEFT'), geo_dir=POSED, bones=fore(g))
    compare()


if __name__ == '__main__':
    main()
