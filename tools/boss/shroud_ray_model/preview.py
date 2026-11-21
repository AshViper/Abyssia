# -*- coding: utf-8 -*-
"""帳エイの図を out/ に書き出す（書き出し済みの資産ではなく、build.py の形をそのまま描く）。

| 出力 | 中身 |
| --- | --- |
| `out/ray_side.png` `ray_top.png` `ray_front.png` | 静止姿勢の横・上・正面 |
| `out/ray_oblique.png` | 斜め前（デザイン画と同じ向き） |
| `out/ray_attack.png` `ray_shroud.png` `ray_idle.png` | 各 animation の姿勢（斜め前 / 正面） |
| `out/ray_compare.png` | デザイン画の 7 番と、斜め前・横・上の図を並べた比較画像 |
"""
import pathlib
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import boxkit
import build

OUT = HERE / 'out'
SHEET = pathlib.Path('F:/Java/Abyssia/inbox/designs/GNT01-design-sheet.png')
CROP = (60, 385, 570, 520)
CAM = (20, 40, 0)
C = (0, 0, 120)


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    OUT.mkdir(exist_ok=True)
    m = build.build()
    m.bake()
    anims = build.animations()
    side = boxkit.shot(m, OUT / 'ray_side.png', 'LEFT', 4.0)
    top = boxkit.shot(m, OUT / 'ray_top.png', 'TOP', 4.0)
    boxkit.shot(m, OUT / 'ray_bottom.png', 'BOTTOM', 3.0)
    boxkit.shot(m, OUT / 'ray_front.png', 'FRONT', 4.0)
    obl = boxkit.shot(m, OUT / 'ray_oblique.png', 'LEFT', 4.0, cam=CAM, centre=C)
    boxkit.shot(m, OUT / 'ray_attack.png', 'LEFT', 3.0, rots=boxkit.pose_at(anims['attack'], 0.5))
    boxkit.shot(m, OUT / 'ray_shroud.png', 'FRONT', 3.0, rots=boxkit.pose_at(anims['shroud'], 0.8))
    boxkit.shot(m, OUT / 'ray_shroud_oblique.png', 'LEFT', 3.0, rots=boxkit.pose_at(anims['shroud'], 0.8),
                cam=CAM, centre=C)
    boxkit.shot(m, OUT / 'ray_idle.png', 'FRONT', 3.0, rots=boxkit.pose_at(anims['idle'], 1.0))
    design = OUT / '_design.png'
    Image.open(SHEET).convert('RGB').crop(CROP).save(design)
    boxkit.sheet([obl, side, top], OUT / 'ray_compare.png', design=design)


if __name__ == '__main__':
    main()
