# -*- coding: utf-8 -*-
"""灯髭ウオの図（横・上・正面・斜め前）と、デザイン画との比較画像を out/ へ書く。

    python tools/boss/lantern_grazer_model/preview.py
"""
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import cubekit                      # noqa: E402
from build import NAME, LENGTH      # noqa: E402

REF = pathlib.Path('F:/Java/Abyssia/inbox/designs/GNT01-design-sheet.png')
REF_BOX = (80, 125, 630, 280)       # シートの 3 番


def main():
    out = HERE / 'out'
    shots = cubekit.preview(NAME, out, LENGTH)
    if REF.exists():
        cubekit.compare(NAME, out, REF, REF_BOX, [shots['oblique'], shots['side'], shots['top']])


if __name__ == '__main__':
    main()
