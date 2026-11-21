# -*- coding: utf-8 -*-
"""石鱗ウオ（slateback）の図を out/ に書き出す（いま置いてある geo とテクスチャを描く）。build.py を先に流すこと。

| 出力 | 中身 |
| --- | --- |
| `out/slateback_views.png` | 横（LEFT）・上（TOP）・斜め前の 3 方向（地 + 発光を重ねた絵） |
| `out/slateback_head.png` | 頭の拡大: 静止 / 体当たりの振り抜き（上から）/ 正面 |
| `out/slateback_glow.png` | 発光テクスチャだけ（光る所の確認） |
| `out/slateback_compare.png` | デザイン画の 5 番と、横・斜め前の図を並べた比較 |
"""
import pathlib
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import gbox                     # noqa: E402
import build                    # noqa: E402

NAME = build.NAME
OUT = HERE / 'out'
DESIGN = pathlib.Path('F:/Java/Abyssia/inbox/designs/GNT01-design-sheet.png')
DESIGN_BOX = (85, 270, 510, 395)
MID_Z = 15.0 * gbox.U


def main():
    geo = gbox.load_geo(NAME)
    tex = gbox.composite(NAME)
    side = gbox.shot(geo, tex, 'LEFT', 4.5)
    top = gbox.shot(geo, tex, 'TOP', 4.5)
    obl = gbox.shot(gbox.turned(geo, 40, -14, MID_Z), tex, 'LEFT', 5.0)
    gbox.sheet([("SIDE", side), ("TOP", top), ("OBLIQUE FRONT", obl)], OUT / f'{NAME}_views.png')

    fore = {'head', 'skull', 'jaw', 'seg_02', 'seg_03', 'seg_04', 'fin_l', 'fin_r'}
    hit = gbox.shifted(gbox.rotated(geo, {"skull": (4, 22, 0), "jaw": (16, 0, 0)}), {"skull": (0, 0, -10)})
    gbox.sheet([("HEAD REST", gbox.shot(geo, tex, 'LEFT', 9.0, bones=fore)),
                ("HEAD ATTACK SIDE", gbox.shot(hit, tex, 'LEFT', 9.0, bones=fore)),
                ("HEAD ATTACK TOP", gbox.shot(hit, tex, 'TOP', 9.0, bones=fore)),
                ("HEAD FRONT", gbox.shot(geo, tex, 'FRONT', 9.0, bones=fore))], OUT / f'{NAME}_head.png')
    gbox.sheet([("GLOW ONLY", gbox.shot(geo, gbox.glow_only(NAME), 'LEFT', 4.5))], OUT / f'{NAME}_glow.png')

    design = Image.open(DESIGN).convert('RGB').crop(DESIGN_BOX)
    k = side.width / design.width
    design = design.resize((side.width, int(design.height * k)), Image.LANCZOS)
    gbox.sheet([("DESIGN 5", design), ("MODEL SIDE", side), ("MODEL OBLIQUE FRONT", obl)],
               OUT / f'{NAME}_compare.png')


if __name__ == '__main__':
    main()
