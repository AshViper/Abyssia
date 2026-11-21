# -*- coding: utf-8 -*-
"""大海蛇（abyss_serpent）の GeckoLib モデル用テクスチャアトラス（256x256）。

色・模様・発光は `tools/boss_textures.py` の serpent()（旧メッシュ描画の皮膚）を引き継ぐ:
深い紺の鱗、明るい灰緑の腹板、側線に沿ったシアンの発光器、破れた縁の鰭膜、骨色の牙、
黒紺の角、スリット瞳の光る眼。乱数は使わず座標のハッシュだけで描くので、何度流しても
同じ PNG になる（決定的）。

<h2>貼り方の約束（build.py と揃える）</h2>
- 鱗・頭・顎・口の中のような地の素材は、`boss_kit.Kit.cube` が面の実寸ぶんを領域の中から
  切り出す。tone（0..1）は領域の縦のどこを使うか。0 が上（暗い背の側）、1 が下（明るい腹の側）
- STRETCHED の領域は、どの面にも領域まるごとを貼る（眼・牙・角・鰭条・腹板・袖）
- 絵の上端は「面に沿って上る向き」（`ring_loft` の板）、または棒の先（`Kit.beam`）、
  または鰭の外縁（`Kit.fan` の膜、MEMBRANE は上端から切り出す）
- 体の左右で板の u が鏡写しになる（板を回転だけで置くため）。だから横方向（u）に向きのある
  絵は描かない。袖と腹板の模様は u について左右対称にしてある

<h2>発光</h2>
`build_glow()` は発光する画素だけを不透明で残し、ほかは透明な黒（0,0,0,0）。加算の
RenderType.eyes でも、GeckoLib の自動発光層でも、光らない所は何も足さない。

```bash
python tools/boss/serpent_model/atlas.py [出力先.png]   # アトラスだけを確認用に書き出す
```
"""
import math
import pathlib
import struct
import sys
import zlib

SIZE = 256

# (x, y, w, h)
BACK = (0, 0, 128, 24)          # 背の帯。最も暗く、正中に竜骨鱗の列
FLANK = (0, 24, 128, 48)        # 脇腹の鱗。上ほど暗く、下ほど明るい（tone）
LATERAL = (0, 72, 128, 24)      # 側線。縦の中央に溝と発光器（発光）
COLLAR_T = (0, 96, 32, 24)      # 袖（体節の後ろの重なり）の背側 (stretch)
COLLAR_S = (32, 96, 32, 24)     # 袖の脇 (stretch)
COLLAR_B = (64, 96, 32, 24)     # 袖の腹 (stretch)
COLLAR_L = (96, 96, 32, 24)     # 袖の側線。発光器付き (stretch)
BELLY = (0, 120, 64, 24)        # 腹板。体を横切る帯が5枚 (stretch)
PLATE = (64, 120, 64, 24)       # 眉・頭頂の骨板
MEMBRANE = (0, 144, 96, 80)     # 鰭の膜。上端が外縁（破れ・発光点）。上端から切り出す
RAY = (96, 144, 12, 64)         # 鰭条 (stretch)。上端が先
HORN = (108, 144, 12, 64)       # 角 (stretch)。上端が先
TOOTH = (120, 144, 8, 24)       # 牙 (stretch)。上端が先
EYE = (128, 144, 16, 16)        # 眼 (stretch, 発光)
GLOWDOT = (144, 144, 8, 8)      # 発光器の珠 (stretch, 発光)
GILL = (152, 144, 32, 32)       # 鰓裂。暗い地に光る裂け目3本 (stretch, 発光)
HEAD = (128, 0, 128, 48)        # 頭の鱗。上ほど暗い
JAW = (128, 48, 128, 32)        # 下顎。上が鱗、下が明るい喉（畝）
INNER = (128, 80, 64, 32)       # 口の中の肉
CAVITY = (192, 80, 32, 32)      # 喉の奥・眼窩の闇

STRETCHED = {COLLAR_T, COLLAR_S, COLLAR_B, COLLAR_L, BELLY, RAY, HORN, TOOTH, EYE, GLOWDOT, GILL}
TOP_ANCHORED = {MEMBRANE}


def rgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)


HIDE = [rgb(c) for c in ("#07111d", "#0b1a2b", "#10263a", "#173449", "#20465c", "#2c5c72")]
BELLY_P = [rgb(c) for c in ("#3e5a62", "#5e7f84", "#86a3a2", "#aec4bd", "#cfdcd2")]
INNER_P = [rgb(c) for c in ("#2a0a12", "#4a121c", "#701c28", "#96303a")]
TOOTH_P = [rgb(c) for c in ("#8d8a78", "#bdb9a2", "#e3dfc8", "#f6f3e4")]
HORN_P = [rgb(c) for c in ("#05090f", "#0b1a2b", "#1c3346", "#3a5a6c", "#5d7d8c")]
IRIS = [rgb(c) for c in ("#0b6f8c", "#18a8cc", "#46e6ff", "#c8fbff")]
PUPIL = rgb("#02060a")
CYAN = rgb("#46e6ff")
CYAN_DEEP = rgb("#1f8fb0")
EDGE = rgb("#3bb4cf")
VOID = rgb("#03070c")


def hashf(*k):
    """座標から 0..1 の値。boss_textures.py と同じ式"""
    h = 0x9E3779B9
    for v in k:
        h = (h ^ (int(v) & 0xFFFFFFFF)) * 0x85EBCA6B & 0xFFFFFFFF
        h ^= h >> 13
    return (h & 0xFFFF) / 65535.0


def ramp(pal, t, px, py, dither=0.06):
    """暗→明の色列から t の色。順序付きの揺らぎで帯を画素に崩す"""
    t = t + (hashf(px, py, 7) - 0.5) * dither * 2
    i = max(0, min(len(pal) - 1, int(t * len(pal))))
    return pal[i]


def scales(px, py, cell=8):
    """互い違いの鱗の列。鱗の縁の影で 0、冠で 1"""
    row = py // cell
    sx = (px + (cell // 2 if row & 1 else 0)) % cell
    sy = py % cell
    d = math.hypot(sx - cell / 2 + 0.5, sy - cell * 0.35)
    return max(0.0, 1.0 - d / (cell * 0.62))


class Sheet:
    def __init__(self):
        self.px = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
        self.gl = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]

    def fill(self, region, fn, glow=None):
        """fn(u, v, x, y) -> (r,g,b) / (r,g,b,a) / None（透明）。x, y は領域の中の画素"""
        x0, y0, w, h = region
        for y in range(h):
            for x in range(w):
                u, v = (x + 0.5) / w, (y + 0.5) / h
                c = fn(u, v, x, y)
                if c is not None:
                    self.px[y0 + y][x0 + x] = c if len(c) == 4 else c + (255,)
                if glow is not None:
                    g = glow(u, v, x, y)
                    if g is not None:
                        self.gl[y0 + y][x0 + x] = g + (255,)


# ==================== 体 ====================
def _back(u, v, x, y):
    keel = abs(v - 0.5) < 0.2
    t = 0.10 + 0.36 * scales(x, y, 8)
    if keel:
        t = 0.06 + 0.42 * scales(x, y + 4, 12)
        if abs(v - 0.5) < 0.05 and x % 12 in (5, 6):
            t += 0.25                                  # 竜骨の稜
    return ramp(HIDE, t, x, y)


def _flank(u, v, x, y):
    t = 0.14 + 0.40 * scales(x, y, 8) + 0.34 * v
    if hashf(x, y, 31) > 0.975:
        t += 0.22                                      # 濡れた鱗の照り
    return ramp(HIDE, t, x, y)


def _lateral(u, v, x, y):
    if abs(v - 0.5) < 0.07:
        if x % 16 in (6, 7, 8, 9):
            return CYAN                                 # 発光器
        return HIDE[0]                                 # 側線の溝
    return ramp(HIDE, 0.24 + 0.40 * scales(x, y, 8) + 0.12 * v, x, y)


def _lateral_glow(u, v, x, y):
    if abs(v - 0.5) < 0.07 and x % 16 in (6, 7, 8, 9):
        return CYAN
    if abs(v - 0.27) < 0.05 and x % 32 in (21, 22) and hashf(x // 32, 5) > 0.3:
        return CYAN_DEEP
    return None


def _lateral_dots(u, v, x, y):
    c = _lateral(u, v, x, y)
    if abs(v - 0.27) < 0.05 and x % 32 in (21, 22) and hashf(x // 32, 5) > 0.3:
        return CYAN_DEEP
    return c


def _collar(pal, lo, hi, dots=False):
    """袖の帯。u について左右対称（板の向きが左右で鏡写しになるため）"""
    def fn(u, v, x, y):
        e = min(u, 1.0 - u)                             # 帯の前後の縁までの近さ
        if e < 0.07:
            return pal[0]                               # 縁の影
        if e < 0.13:
            return pal[min(len(pal) - 1, int(hi * len(pal)))]    # 縁の照り
        if dots and abs(v - 0.5) < 0.09 and abs(u - 0.5) < 0.12:
            return CYAN
        t = lo + (hi - lo) * (0.35 + 0.65 * scales(x, y, 8)) - 0.10 * abs(v - 0.5)
        return ramp(pal, t, x, y, 0.04)
    return fn


def _belly(u, v, x, y):
    k = (u * 5.0) % 1.0
    if k < 0.08 or k > 0.94:
        return BELLY_P[0]                               # 腹板の継ぎ目
    t = 0.30 + 0.45 * (1.0 - abs(v - 0.5) * 2.0) - 0.25 * abs(k - 0.45)
    return ramp(BELLY_P, t, x, y, 0.05)


# ==================== 頭 ====================
def _head(u, v, x, y):
    t = 0.12 + 0.38 * scales(x, y, 6) + 0.30 * v
    if v < 0.3 and (x + (y // 6) * 3) % 14 in (0, 1):
        t += 0.18                                       # 額の皺
    if hashf(x // 5, y // 5, 41) > 0.86:
        t -= 0.10                                       # まだら
    return ramp(HIDE, t, x, y)


def _jaw(u, v, x, y):
    if v < 0.42:
        return ramp(HIDE, 0.18 + 0.38 * scales(x, y, 6) + 0.25 * v, x, y)
    if y % 4 == 0:
        return BELLY_P[0]                               # 喉の畝
    return ramp(BELLY_P, 0.25 + 0.55 * (v - 0.42) / 0.58 + 0.15 * hashf(x // 3, y // 3), x, y)


def _inner(u, v, x, y):
    return ramp(INNER_P, 0.3 + 0.25 * math.sin(v * 14 + 0.8 * math.sin(x * 0.4)) + 0.2 * hashf(x // 2, y // 2), x, y)


def _cavity(u, v, x, y):
    return VOID if hashf(x, y, 3) > 0.15 else HIDE[0]


def _plate(u, v, x, y):
    """眉・頭頂・鰓弓の骨板。黒紺の角質に、縁だけ明るい稜"""
    t = 0.35 + 0.25 * (1.0 - v) + 0.08 * math.sin(x * 0.7)
    if (x * 3 + y * 5) % 29 == 0:
        t = 0.10                                        # ひび
    return ramp(HORN_P, t, x, y, 0.05)


# ==================== 鰭・角・牙・眼 ====================
def _edge_y(x):
    """膜の外縁（破れ）の高さ。画素 x ごとの上からの深さ"""
    return 2 + int(3 * (0.5 + 0.5 * math.sin(x * 0.55)) + 3 * hashf(x // 3, 77))


def _membrane(u, v, x, y):
    top = _edge_y(x)
    if y < top:
        return None
    if top + 9 < y < top + 30 and hashf(x // 3, y // 4, 91) > 0.965:
        return None                                     # 小さな破れ穴
    if y < top + 2:
        return EDGE
    if y in (top + 6, top + 7) and x % 9 in (4, 5):
        return CYAN                                     # 縁の発光点
    t = 0.62 - 0.45 * v + (0.10 if x % 6 == 0 else 0.0)
    return ramp(HIDE[2:] + BELLY_P[:1], t, x, y)


def _membrane_glow(u, v, x, y):
    top = _edge_y(x)
    if y in (top + 6, top + 7) and x % 9 in (4, 5):
        return CYAN
    return None


def _ray(u, v, x, y):
    t = 0.85 - 0.75 * v - 0.25 * abs(u - 0.5)
    if y % 8 == 0:
        t -= 0.25                                       # 鰭条の節
    return ramp(HIDE[1:] + [EDGE], t, x, y, 0.03)


def _horn(u, v, x, y):
    t = 0.10 + 0.85 * (1.0 - v) - 0.35 * abs(u - 0.5)
    if y % 6 == 0 and v > 0.15:
        t -= 0.18                                       # 成長輪
    return ramp(HORN_P, t, x, y, 0.04)


def _tooth(u, v, x, y):
    return ramp(TOOTH_P, 0.25 + 0.75 * (1.0 - v) - 0.35 * abs(u - 0.5), x, y, 0.03)


def _eye(u, v, x, y):
    dx, dy = u - 0.5, v - 0.5
    d = math.hypot(dx, dy)
    if d > 0.47:
        return PUPIL                                    # 眼の縁は闇（透かさない）
    if abs(dx) < 0.06 + 0.05 * (0.5 - abs(dy)):
        return PUPIL                                    # 縦に細い瞳
    return ramp(IRIS, 1.0 - d * 1.7, x, y, 0.04)


def _eye_glow(u, v, x, y):
    c = _eye(u, v, x, y)
    return None if c == PUPIL else c


def _glowdot(u, v, x, y):
    d = math.hypot(u - 0.5, v - 0.5)
    return CYAN if d < 0.3 else CYAN_DEEP


def _gill(u, v, x, y):
    """鰓裂1本。縦（v）が裂け目の長さ。中央に光る線、まわりは闇、縁は暗い鱗"""
    taper = 0.10 * (1.0 - abs(v - 0.5) * 2.0) + 0.03
    if abs(u - 0.5) < taper and 0.06 < v < 0.94:
        return CYAN_DEEP
    return VOID if abs(u - 0.5) < 0.36 else HIDE[1]


def _gill_glow(u, v, x, y):
    c = _gill(u, v, x, y)
    return c if c == CYAN_DEEP else None


def _draw():
    s = Sheet()
    s.fill(BACK, _back)
    s.fill(FLANK, _flank)
    s.fill(LATERAL, _lateral_dots, _lateral_glow)
    s.fill(COLLAR_T, _collar(HIDE, 0.05, 0.45))
    s.fill(COLLAR_S, _collar(HIDE, 0.15, 0.60))
    s.fill(COLLAR_B, _collar(BELLY_P, 0.20, 0.80))
    dots = _collar(HIDE, 0.18, 0.62, dots=True)
    s.fill(COLLAR_L, dots, lambda u, v, x, y: CYAN if dots(u, v, x, y) == CYAN else None)
    s.fill(BELLY, _belly)
    s.fill(PLATE, _plate)
    s.fill(MEMBRANE, _membrane, _membrane_glow)
    s.fill(RAY, _ray)
    s.fill(HORN, _horn)
    s.fill(TOOTH, _tooth)
    s.fill(EYE, _eye, _eye_glow)
    s.fill(GLOWDOT, _glowdot, _glowdot)
    s.fill(GILL, _gill, _gill_glow)
    s.fill(HEAD, _head)
    s.fill(JAW, _jaw)
    s.fill(INNER, _inner)
    s.fill(CAVITY, _cavity)
    return s


_CACHE = {}


def _sheet():
    if 'sheet' not in _CACHE:
        _CACHE['sheet'] = _draw()
    return _CACHE['sheet']


def build():
    """皮膚の画素（行の並び、各画素は (r, g, b, a)）。boss_views.render_parts が読む"""
    return _sheet().px


def build_glow():
    """発光の画素。光らない所は (0, 0, 0, 0)"""
    return _sheet().gl


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
