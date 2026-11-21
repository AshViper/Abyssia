# -*- coding: utf-8 -*-
"""槍顎（lancejaw）の図を out/ に書き出す（いま置いてある geo とテクスチャを描く）。build.py を先に流すこと。

| 出力 | 中身 |
| --- | --- |
| `out/lancejaw_views.png` | 横（LEFT）・上（TOP）・斜め前の 3 方向（地 + 発光を重ねた絵） |
| `out/lancejaw_jaw.png` | 頭の拡大: 槍を収めた状態 / 1 段 / 2 段（6 ブロック）伸ばした状態 |
| `out/lancejaw_bent.png` | Java と同じやり方で S 字に曲げた全身（上・横）。継ぎ目を見る |
| `out/lancejaw_glow.png` | 発光テクスチャだけ（光る所の確認） |
| `out/lancejaw_compare.png` | デザイン画の 1 番と、横・斜め前の図を並べた比較 |
"""
import math
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
DESIGN_BOX = (60, 5, 600, 130)
MID_Z = 13.0 * gbox.U


def bent(geo, joints, amp=0.45, freq=0.45):
    """関節の列を S 字に置き、head と seg_k を Java と同じ規則（pivot を中点へ、-Z を前の関節へ）で置く"""
    import copy
    geo = copy.deepcopy(geo)
    by = {b['name']: b for b in geo['minecraft:geometry'][0]['bones']}
    pts, worst = [(0.0, 0.0)], 0.0
    yaws = []
    for j in range(1, joints):
        yaw = amp * math.sin(freq * j + 0.2)
        yaws.append(yaw)
        x, z = pts[-1]
        pts.append((x + gbox.SEG * math.sin(yaw), z + gbox.SEG * math.cos(yaw)))
    for a, b in zip(yaws, yaws[1:]):
        worst = max(worst, abs(math.degrees(b - a)))
    moves = {}
    for k in range(1, joints):
        mx, mz = (pts[k - 1][0] + pts[k][0]) / 2, (pts[k - 1][1] + pts[k][1]) / 2
        rest_z = gbox.SEG * (k - 0.5)
        moves[f"seg_{k:02d}"] = ((mx, 0.0, mz - rest_z), yaws[k - 1])
    moves["head"] = ((0.0, 0.0, 0.0), yaws[0])
    geo = gbox.shifted(geo, {n: d for n, (d, _) in moves.items()})
    by = {b['name']: b for b in geo['minecraft:geometry'][0]['bones']}
    for n, (_, yaw) in moves.items():
        # geo の ry > 0 で前が -X へ向く。関節列は +X へ振っているので符号を合わせる
        by[n]['rotation'] = [0.0, math.degrees(yaw), 0.0]
    print(f"曲げた図: 関節の最大の折れ角 {worst:.1f} 度")
    return geo


def main():
    geo = gbox.load_geo(NAME)
    tex = gbox.composite(NAME)
    side = gbox.shot(geo, tex, 'LEFT', 5.0)
    top = gbox.shot(geo, tex, 'TOP', 5.0)
    obl = gbox.shot(gbox.turned(geo, 40, -14, MID_Z), tex, 'LEFT', 6.0)
    gbox.sheet([("SIDE", side), ("TOP", top), ("OBLIQUE FRONT", obl)], OUT / f'{NAME}_views.png')

    fore = lambda n: not n.startswith('seg_') or n in ('seg_01', 'seg_02', 'seg_03')   # noqa: E731
    head_bones = {b['name'] for b in geo['minecraft:geometry'][0]['bones']
                  if b['name'] in ('head', 'jaw', 'jaw_tip', 'seg_02', 'seg_03')}
    d = build.STAGE
    stages = [("JAW REST", geo), ("JAW STAGE 1", gbox.shifted(geo, {"jaw": (0, 0, d)})),
              ("JAW STAGE 2  6 BLOCKS", gbox.shifted(geo, {"jaw": (0, 0, d), "jaw_tip": (0, 0, d)}))]
    panels = [(label, gbox.shot(g, tex, 'LEFT', 10.0, bones=head_bones)) for label, g in stages]
    panels.append(("JAW STAGE 2 OBLIQUE", gbox.shot(gbox.turned(stages[2][1], 40, -14, 0.0), tex, 'LEFT', 10.0,
                                                    bones=head_bones | {'_world'})))
    panels.append(("HEAD FRONT", gbox.shot(geo, tex, 'FRONT', 10.0, bones=head_bones)))
    panels.append(("HEAD BOTTOM", gbox.shot(geo, tex, 'BOTTOM', 10.0, bones=head_bones)))
    gbox.sheet(panels, OUT / f'{NAME}_jaw.png')

    g = bent(geo, build.JOINTS)
    gbox.sheet([("BENT TOP", gbox.shot(g, tex, 'TOP', 5.0)), ("BENT SIDE", gbox.shot(g, tex, 'LEFT', 5.0))],
               OUT / f'{NAME}_bent.png')
    gbox.sheet([("GLOW ONLY", gbox.shot(geo, gbox.glow_only(NAME), 'LEFT', 5.0))], OUT / f'{NAME}_glow.png')

    design = Image.open(DESIGN).convert('RGB').crop(DESIGN_BOX)
    k = side.width / design.width
    design = design.resize((side.width, int(design.height * k)), Image.LANCZOS)
    gbox.sheet([("DESIGN 1", design), ("MODEL SIDE", side), ("MODEL OBLIQUE FRONT", obl)],
               OUT / f'{NAME}_compare.png')


if __name__ == '__main__':
    main()
