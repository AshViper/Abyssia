# -*- coding: utf-8 -*-
"""灯翼の図を out/ へ書く（半透明を重ねて描く）。

    python tools/boss/lumenwing_model/preview.py            # 全部
    python tools/boss/lumenwing_model/preview.py views      # 横・上・斜め前
    python tools/boss/lumenwing_model/preview.py compare    # デザイン画と並べた比較
"""
import pathlib
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import glasskit as gk  # noqa: E402

NAME = "lumenwing"
OUT = HERE / "out"
DESIGN = pathlib.Path(r"F:/Java/Abyssia/inbox/designs/GNT01-design-02b.png")


def views():
    side = gk.render(NAME, "side", scale=2.4)
    top = gk.render(NAME, "top", scale=2.4)
    obl = gk.render(NAME, "oblique", scale=2.4)
    gk.stack([side, top, obl], ["SIDE", "TOP", "OBLIQUE FRONT"]).save(OUT / "views.png")
    # 羽ばたきの途中（idle の 2 秒と 6 秒）と、体節鎖を曲げたとき
    flap = [gk.render(NAME, "front", scale=1.6, pose=gk.pose_at(NAME, "idle", t)) for t in (0.0, 2.0, 6.0)]
    bent = gk.render(NAME, "top", scale=1.6, bend=lambda k: 28.0 * __import__("math").sin(k / 30 * 6.283))
    gk.stack(flap + [bent], ["FRONT idle t=0", "t=2", "t=6", "TOP bent"]).save(OUT / "poses.png")
    print("wrote", OUT / "views.png", OUT / "poses.png")


def compare():
    design = Image.open(DESIGN).convert("RGB")
    low = gk.render(NAME, "design", scale=2.0)
    low = low.resize((int(low.width * design.height / low.height), design.height), Image.NEAREST) \
        if low.height > design.height else low
    side = gk.render(NAME, "side", scale=1.6)
    right = gk.stack([low, side])
    gk.beside(design, right).save(OUT / "compare_design.png")
    print("wrote", OUT / "compare_design.png")


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    what = sys.argv[1:] or ["views", "compare"]
    if "views" in what:
        views()
    if "compare" in what:
        compare()
