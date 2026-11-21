# -*- coding: utf-8 -*-
"""宵喰らいの図を out/ に書き出す（書き出し済みの資産ではなく、build.py の形をそのまま描く）。

| 出力 | 中身 |
| --- | --- |
| `out/maw_side.png` `maw_top.png` `maw_front.png` | 静止姿勢の横・上・正面 |
| `out/maw_oblique.png` | 斜め前 |
| `out/maw_attack.png` `maw_grab.png` `maw_grab_oblique.png` | 噛み合わせた瞬間 / 触手を前へ伸ばした姿勢 |
| `out/maw_compare.png` | デザイン画の 10 番と、横・斜め前・上の図を並べた比較画像 |
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
CROP = (580, 500, 1244, 690)
CAM = (8, 32, 0)
C = (0, 0, 220)


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    OUT.mkdir(exist_ok=True)
    m = build.build()
    m.bake()
    anims = build.animations()
    side = boxkit.shot(m, OUT / 'maw_side.png', 'LEFT', 3.0)
    top = boxkit.shot(m, OUT / 'maw_top.png', 'TOP', 3.0)
    boxkit.shot(m, OUT / 'maw_front.png', 'FRONT', 4.0)
    obl = boxkit.shot(m, OUT / 'maw_oblique.png', 'LEFT', 3.0, cam=CAM, centre=C)
    boxkit.shot(m, OUT / 'maw_attack.png', 'LEFT', 3.0, rots=boxkit.pose_at(anims['attack'], 0.5))
    boxkit.shot(m, OUT / 'maw_grab.png', 'LEFT', 3.0, rots=boxkit.pose_at(anims['grab'], 0.7))
    boxkit.shot(m, OUT / 'maw_grab_oblique.png', 'LEFT', 3.0, rots=boxkit.pose_at(anims['grab'], 0.7),
                cam=CAM, centre=C)
    design = OUT / '_design.png'
    Image.open(SHEET).convert('RGB').crop(CROP).save(design)
    boxkit.sheet([side, obl, top], OUT / 'maw_compare.png', design=design)


if __name__ == '__main__':
    main()
