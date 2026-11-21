# -*- coding: utf-8 -*-
"""静寂鯨の図（横・上・正面・斜め前）と、デザイン画との比較画像を out/ へ書く。

    python tools/boss/hush_whale_model/preview.py
"""
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import cubekit                      # noqa: E402
from build import NAME, LENGTH, RAM  # noqa: E402

REF = pathlib.Path('F:/Java/Abyssia/inbox/designs/GNT01-design-sheet.png')
REF_BOX = (660, 115, 1235, 290)     # シートの 4 番


def main():
    out = HERE / 'out'
    shots = cubekit.preview(NAME, out, LENGTH)
    if REF.exists():
        cubekit.compare(NAME, out, REF, REF_BOX, [shots['oblique'], shots['side'], shots['top']])
    # 突進の打撃の瞬間（ram_NN を 14 度）を横から。頭がひとかたまりで下がるかを見る
    cubekit.preview(NAME, out, LENGTH, pose={f'ram_{k:02d}': (14, 0, 0) for k in RAM}, tag='_attack')


if __name__ == '__main__':
    main()
