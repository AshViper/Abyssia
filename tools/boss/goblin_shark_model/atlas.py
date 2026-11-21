# -*- coding: utf-8 -*-
"""巨大ミツクリザメ（colossal_goblin_shark）の GeckoLib モデル用の仮テクスチャアトラス（256x256）。

本番のテクスチャは別工程で作る。ここは UV の置き場所（領域）を決め、形が読める程度の
桃色がかった灰色で塗るだけ。乱数は使わず座標のハッシュだけで描くので、何度流しても同じ PNG になる。

<h2>なぜ 256 か</h2>
MODEL_SCALE = 2 なので geo の 1 単位 = 1/8 ブロック。体の板1枚が最大で 17x6 単位、
鰭の板が最大で 14 単位ほどなので、256 の領域で引き伸ばしなく切り出せる。

<h2>貼り方の約束（build.py と揃える）</h2>
- 地の素材（BACK / FLANK / BELLY / SNOUT / SNOUT_UNDER / HEAD / FLESH / MOUTH / SLING）は
  `boss_kit.Kit.cube` が面の実寸ぶんを領域の中から切り出す。tone（0..1）は領域の縦のどこを使うか。
  FLANK は 0 が上（背に近い暗い側）、1 が下（腹に近い明るい側）
- STRETCHED（GILL / TOOTH / EYE）はどの面にも領域まるごとを貼る。TOOTH は上端が歯の先
- FIN は上端から切り出す（TOP_ANCHORED）。上端が鰭の外縁（青みがかった縁）
- 体の左右で板の u が鏡写しになる。横方向（u）に向きのある絵は描かない
- 発光は無い（深海ザメだが発光器を持たない）。_glow.png は書き出さない

```bash
python tools/boss/goblin_shark_model/atlas.py [出力先.png]   # アトラスだけを確認用に書き出す
```
"""
import math
import pathlib
import struct
import sys
import zlib

SIZE = 256

# (x, y, w, h)
BACK = (0, 0, 128, 24)            # 背。灰色がかった藤色
FLANK = (0, 24, 128, 48)          # 脇腹。上ほど灰色、下ほど桃色（tone）
BELLY = (0, 72, 128, 24)          # 腹。淡い桃白
FIN = (0, 96, 128, 40)            # 鰭（厚みのある板）。上端が外縁（青灰の縁）。上端から切り出す
SNOUT = (128, 0, 128, 24)         # 吻の上と脇。半透明に見える桃灰、ロレンチーニ瓶の小孔
SNOUT_UNDER = (128, 24, 128, 24)  # 吻の下。淡い桃色、小孔が密
HEAD = (128, 48, 128, 24)         # 眼の後ろの頭。脇腹より少し桃色
FLESH = (128, 72, 64, 24)         # 顎の歯茎の帯・顎の外側。濃い桃色
MOUTH = (192, 72, 64, 24)         # 口の中。暗い赤紫の畝
SLING = (128, 96, 64, 40)         # 顎を突き出すと伸びる喉の膜。桃色に縦の筋
GILL = (192, 96, 16, 32)          # 鰓孔 (stretch)。暗い赤の切れ目
TOOTH = (208, 96, 8, 32)          # 歯 (stretch)。上端が先。細い釘形
EYE = (216, 96, 16, 16)           # 眼 (stretch)。小さく暗い眼、四隅は皮膚色

STRETCHED = {GILL, TOOTH, EYE}
TOP_ANCHORED = {FIN}


def rgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)


# 桃色がかった灰色（実物のミツクリザメ: 半透明の皮膚から血管が透けて桃色に見える）
BACK_P = [rgb(c) for c in ("#5e4f56", "#6c5b63", "#7a6870", "#86747c", "#928088")]
FLANK_P = [rgb(c) for c in ("#7b6870", "#8c777f", "#9d878f", "#ae969e", "#bea4ab", "#cbb0b6", "#d5babf")]
BELLY_P = [rgb(c) for c in ("#c9aeb3", "#d3b9bd", "#dcc3c6", "#e4cdcf")]
SNOUT_P = [rgb(c) for c in ("#8e7a82", "#9f8a92", "#b09aa1", "#bfa8ae", "#cbb4b9")]
UNDER_P = [rgb(c) for c in ("#bba0a6", "#c8aeb3", "#d4bbbf", "#dec6c9")]
HEAD_P = [rgb(c) for c in ("#866f78", "#977f88", "#a88f97", "#b99ea5", "#c7abb1")]
FIN_P = [rgb(c) for c in ("#867880", "#94838b", "#a28e96", "#ae99a0", "#b8a2a9")]
FIN_EDGE = [rgb(c) for c in ("#7d8597", "#8890a2", "#939bad")]
FLESH_P = [rgb(c) for c in ("#a4505c", "#b8606c", "#c9727d", "#d6848e", "#e0979f")]
MOUTH_P = [rgb(c) for c in ("#3a1219", "#4d1a23", "#5e222d", "#702c38")]
SLING_P = [rgb(c) for c in ("#c06a76", "#cf7c87", "#dc8f99", "#e6a2aa", "#eeb4ba")]
TOOTH_P = [rgb(c) for c in ("#bdb3a0", "#d6cdb9", "#e9e3d3", "#f6f2e7")]
PORE = rgb("#5d4a52")
GILL_DARK, GILL_RIM = rgb("#2e0f15"), rgb("#7a4450")
EYE_DARK, EYE_SHEEN, EYE_RIM = rgb("#101216"), rgb("#4d5c66"), rgb("#4a3a40")


def hashf(*k):
    """座標から 0..1 の値（angler_model/atlas.py と同じ式）"""
    h = 0x9E3779B9
    for v in k:
        h = (h ^ (int(v) & 0xFFFFFFFF)) * 0x85EBCA6B & 0xFFFFFFFF
        h ^= h >> 13
    return (h & 0xFFFF) / 65535.0


def ramp(pal, t, px, py, dither=0.06):
    t = t + (hashf(px, py, 7) - 0.5) * dither * 2
    i = max(0, min(len(pal) - 1, int(t * len(pal))))
    return pal[i]


def _noise(x, y, period, seed):
    """滑らかな2次元の値ノイズ（格子点の値を余弦で繋ぐ）"""
    ix, iy = math.floor(x / period), math.floor(y / period)
    fx, fy = x / period - ix, y / period - iy
    sx, sy = 0.5 - 0.5 * math.cos(math.pi * fx), 0.5 - 0.5 * math.cos(math.pi * fy)
    a, b = hashf(ix, iy, seed), hashf(ix + 1, iy, seed)
    c, d = hashf(ix, iy + 1, seed), hashf(ix + 1, iy + 1, seed)
    return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy


def _mottle(x, y, seed):
    """皮膚のまだら（大きいむらと細かいむら）。-0.5..0.5"""
    return 0.65 * (_noise(x, y, 11, seed) - 0.5) + 0.35 * (_noise(x, y, 4, seed + 3) - 0.5)


class Sheet:
    def __init__(self):
        self.px = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]

    def fill(self, region, fn):
        """fn(u, v, x, y) -> (r,g,b) / (r,g,b,a) / None（透明）。x, y は領域の中の画素"""
        x0, y0, w, h = region
        for y in range(h):
            for x in range(w):
                u, v = (x + 0.5) / w, (y + 0.5) / h
                c = fn(u, v, x, y)
                if c is not None:
                    self.px[y0 + y][x0 + x] = c if len(c) == 4 else c + (255,)


# ==================== 体 ====================
def _back(u, v, x, y):
    return ramp(BACK_P, 0.5 + 0.55 * _mottle(x, y, 11), x, y, 0.05)


def _flank(u, v, x, y):
    t = 0.08 + 0.84 * v + 0.35 * _mottle(x, y, 13)
    # 皮膚越しに透ける細い血管（桃色の筋）
    if abs(_noise(x, y * 0.5, 9, 21) - 0.5) < 0.025 and v > 0.25:
        t += 0.18
    return ramp(FLANK_P, t, x, y, 0.05)


def _belly(u, v, x, y):
    return ramp(BELLY_P, 0.55 + 0.6 * _mottle(x, y, 17), x, y, 0.06)


def _pores(x, y, seed, density):
    """ロレンチーニ瓶の小孔（ところどころの暗い点）"""
    cx, cy = x // 4, y // 4
    if hashf(cx, cy, seed) < density:
        px, py = cx * 4 + 1 + int(2 * hashf(cx, cy, seed + 1)), cy * 4 + 1 + int(2 * hashf(cx, cy, seed + 2))
        return x == px and y == py
    return False


def _snout(u, v, x, y):
    if _pores(x, y, 31, 0.28):
        return PORE
    return ramp(SNOUT_P, 0.5 + 0.6 * _mottle(x, y, 23), x, y, 0.05)


def _snout_under(u, v, x, y):
    if _pores(x, y, 37, 0.5):
        return PORE
    return ramp(UNDER_P, 0.5 + 0.6 * _mottle(x, y, 29), x, y, 0.05)


def _head(u, v, x, y):
    return ramp(HEAD_P, 0.15 + 0.7 * v + 0.35 * _mottle(x, y, 41), x, y, 0.05)


# ==================== 鰭 ====================
def _fin(u, v, x, y):
    """厚みのある肉質の鰭。上端（外縁）の 1 画素だけ青灰、その下 3 画素で地の色へ移る"""
    if y < 1:
        return ramp(FIN_EDGE, 0.5, x, y, 0.1)
    t = 0.35 + 0.5 * min(1.0, y / 24.0) + 0.3 * _mottle(x, y, 53)
    if y < 4:
        t -= 0.25 * (4 - y) / 3.0                       # 縁の近くは少し暗く青み寄り
    return ramp(FIN_P, t, x, y, 0.05)


# ==================== 顎・口・喉 ====================
def _flesh(u, v, x, y):
    return ramp(FLESH_P, 0.5 + 0.7 * _mottle(x, y, 61), x, y, 0.06)


def _mouth(u, v, x, y):
    ridge = 0.5 + 0.5 * math.sin(y * 1.3 + 2.0 * _noise(x, y, 8, 67))
    return ramp(MOUTH_P, 0.2 + 0.6 * ridge + 0.2 * _mottle(x, y, 71), x, y, 0.05)


def _sling(u, v, x, y):
    streak = 0.5 + 0.5 * math.sin(x * 0.9 + 3.0 * _noise(x, y, 10, 73))
    return ramp(SLING_P, 0.25 + 0.55 * streak + 0.3 * _mottle(x, y, 79), x, y, 0.05)


def _gill(u, v, x, y):
    d = abs(u - 0.5)
    if d < 0.22 and 0.06 < v < 0.94:
        return GILL_DARK
    return GILL_RIM


def _tooth(u, v, x, y):
    return ramp(TOOTH_P, 0.2 + 0.75 * (1.0 - v) - 0.35 * abs(u - 0.5), x, y, 0.03)


def _eye(u, v, x, y):
    d = math.hypot(u - 0.5, v - 0.5)
    if d > 0.5:
        return HEAD_P[2]
    if d > 0.42:
        return EYE_RIM
    if 0.16 < d < 0.3 and u < 0.5 and v < 0.5:
        return EYE_SHEEN                                # 上前の照り
    return EYE_DARK


def _draw():
    s = Sheet()
    s.fill(BACK, _back)
    s.fill(FLANK, _flank)
    s.fill(BELLY, _belly)
    s.fill(FIN, _fin)
    s.fill(SNOUT, _snout)
    s.fill(SNOUT_UNDER, _snout_under)
    s.fill(HEAD, _head)
    s.fill(FLESH, _flesh)
    s.fill(MOUTH, _mouth)
    s.fill(SLING, _sling)
    s.fill(GILL, _gill)
    s.fill(TOOTH, _tooth)
    s.fill(EYE, _eye)
    return s


_CACHE = {}


def build():
    """皮膚の画素（行の並び、各画素は (r, g, b, a)）。boss_views.render_parts が読む"""
    if 'sheet' not in _CACHE:
        _CACHE['sheet'] = _draw()
    return _CACHE['sheet'].px


def _chunk(tag, data):
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)


def write_png(path, px):
    raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in px)
    pathlib.Path(path).write_bytes(b'\x89PNG\r\n\x1a\n'
                                   + _chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0))
                                   + _chunk(b'IDAT', zlib.compress(raw, 9))
                                   + _chunk(b'IEND', b''))


if __name__ == '__main__':
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 \
        else pathlib.Path(__file__).resolve().parent / 'out' / 'atlas_preview.png'
    out.parent.mkdir(parents=True, exist_ok=True)
    write_png(out, build())
    print('wrote', out)
