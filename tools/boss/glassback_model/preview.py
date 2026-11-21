# -*- coding: utf-8 -*-
"""硝子背の図を out/ へ書く（半透明を重ねて描く）。

    python tools/boss/glassback_model/preview.py            # 全部
    python tools/boss/glassback_model/preview.py views      # 横・上・斜め前
    python tools/boss/glassback_model/preview.py compare    # デザイン画（シートの 8 番）と並べた比較
"""
import math
import pathlib
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import glasskit as gk  # noqa: E402

NAME = "glassback"
OUT = HERE / "out"
SHEET = pathlib.Path(r"F:/Java/Abyssia/inbox/designs/GNT01-design-sheet.png")
SHEET_BOX = (615, 372, 1238, 518)       # 8 番（右 4 段目）


def views():
    side = gk.render(NAME, "side", scale=2.8)
    top = gk.render(NAME, "top", scale=2.8)
    obl = gk.render(NAME, "oblique", scale=2.8)
    gk.stack([side, top, obl], ["SIDE", "TOP", "OBLIQUE FRONT"]).save(OUT / "views.png")
    heads = []
    for pose in ({}, gk.pose_at(NAME, "attack", 0.30), gk.pose_at(NAME, "attack", 0.50)):
        im = gk.render(NAME, "side", scale=8.0, pose=pose)
        heads.append(im.crop((0, 0, 700, im.height)))
    front = gk.render(NAME, "front", scale=5.0)
    bent = gk.render(NAME, "top", scale=2.0, bend=lambda k: 28.0 * math.sin(k / 25 * 6.283))
    gk.stack(heads + [front, bent],
             ["HEAD rest", "attack t=0.30", "attack t=0.50", "FRONT", "TOP bent"]).save(OUT / "poses.png")
    print("wrote", OUT / "views.png", OUT / "poses.png")


def compare():
    crop = Image.open(SHEET).convert("RGB").crop(SHEET_BOX)
    crop = crop.resize((crop.width * 2, crop.height * 2), Image.LANCZOS)
    side = gk.render(NAME, "side", scale=crop.width / 420.0)
    obl = gk.render(NAME, "design", scale=2.2)
    gk.stack([crop, side, obl],
             ["DESIGN (sheet #8, x2)", "MODEL side", "MODEL oblique"]).save(OUT / "compare_design.png")
    print("wrote", OUT / "compare_design.png")


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    what = sys.argv[1:] or ["views", "compare"]
    if "views" in what:
        views()
    if "compare" in what:
        compare()
