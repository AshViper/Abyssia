# -*- coding: utf-8 -*-
"""深淵の母（abyss_mother）の GeckoLib モデル用テクスチャアトラス（256x256）。

色・模様・発光は `tools/boss_textures.py` の mother()（旧メッシュ描画の皮膚）を引き継ぐ:
すみれ色の半透明の傘と放射管、縁の厚い帯と氷色の縁の灯り、下傘の桃色の生殖腺（馬蹄形）と
マゼンタの発光、桃色のぎざぎざのフリル、桃色の帯を持つ細い触手、マゼンタに光る心臓。
乱数は使わず座標のハッシュだけで描くので、何度流しても同じ PNG になる（決定的）。

<h2>半透明は画素の alpha で持つ</h2>
傘（BELL_*）・下傘（UNDER_*）・フリル（FRILL）だけが半透明（alpha 140〜220）。ほかは不透明（255）か
透明（0）。傘と下傘の板は長方形なので、台形（上辺 = 頂点側の弦、下辺 = 縁側の弦）の外を
alpha 0 で切り抜き、隣の板と重ならないようにしてある（重なると半透明が二重になって筋が出る）。
切り抜きの寸法は shape.py の表から計算するので、形を変えたら流し直すだけで揃う。

<h2>貼り方の約束（build.py と揃える）</h2>
- 傘・下傘の板は build.py が面ごとの UV を直接書く。外の面（up）に `*_OUT`、内の面（down）に
  上下を返した `*_IN`、厚みの面（4方）に透明な CLEAR。絵の上端は頂点（傘の中心）側
- 列ごとに R（花弁の中心が絵の右）と L（左）の2枚がある。花弁の縁（開花で割れる線）の管を太く、
  中心（触手の真上）の管を細く描く
- STRETCHED の領域は、どの面にも領域まるごとを貼る（フリル・触手・数珠・口腕・管）
- フリルと口腕のフリルは絵の上端が自由縁（ぎざぎざ）、下端が付け根
- 触手（STRAND）は絵の上端が先端側（体節の後ろの関節）。そこに桃色の帯（発光）

<h2>発光</h2>
`build_glow()` は発光する画素だけを不透明で残し、ほかは透明な黒（0,0,0,0）。心臓は全面が光る。

```bash
python tools/boss/mother_model/atlas.py [出力先.png]   # アトラスだけを確認用に書き出す
```
"""
import math
import pathlib
import struct
import sys
import zlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import shape

SIZE = 256
TILE = 32

# (x, y, w, h)
BELL_OUT = {s: [(TILE * i, y0, TILE, TILE) for i in range(len(shape.BELL_ROWS))] for s, y0 in (("R", 0), ("L", 32))}
BELL_IN = {s: [(TILE * i, y0, TILE, TILE) for i in range(len(shape.BELL_ROWS))] for s, y0 in (("R", 64), ("L", 96))}
UNDER_OUT = {s: [(TILE * (i + 4 * k), 128, TILE, TILE) for i in range(len(shape.UNDER_ROWS))]
             for k, s in enumerate(("R", "L"))}
UNDER_IN = {s: [(TILE * (i + 4 * k), 160, TILE, TILE) for i in range(len(shape.UNDER_ROWS))]
            for k, s in enumerate(("R", "L"))}
HEART = (224, 0, 32, 32)        # 心臓（発光）
STALK = (224, 32, 32, 32)       # 口柄（傘の中心から下がる柱）
SLEEVE = (224, 64, 16, 16)      # 触手の数珠（関節の袖）(stretch, 発光の輪)
BULB = (240, 64, 16, 16)        # 触手の付け根の球 (stretch, 発光)
CLEAR = (224, 80, 8, 8)         # 透明。半透明の板の厚みの面
ORAL = (224, 96, 16, 32)        # 口腕の芯 (stretch)
CANAL = (240, 96, 8, 32)        # 放射管・環状管 (stretch, 発光)
FRILL = (0, 192, 64, 32)        # 傘の縁のフリル (stretch, 半透明)。上端が自由縁
ORAL_FRILL = (64, 192, 64, 32)  # 口腕のフリル (stretch, 不透明・切り抜き)。上端が自由縁
STRAND = (128, 192, 8, 64)      # 触手 (stretch)。上端が先端側で桃色の帯

STRETCHED = {SLEEVE, BULB, ORAL, CANAL, FRILL, ORAL_FRILL, STRAND}
TOP_ANCHORED = set()
TRANSLUCENT = "BELL_OUT / BELL_IN / UNDER_OUT / UNDER_IN / FRILL"


def rgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)


# 旧 boss_textures.py mother() の色
BELL_P = [rgb(c) for c in ("#1c1040", "#2c1a5e", "#40287e", "#5a3ea0", "#7c5cc0", "#a488dc")]
PINK = [rgb(c) for c in ("#70205e", "#a43288", "#d850b4", "#ff8ade", "#ffd0f4")]
MAGENTA = rgb("#ff5ad8")
ICE = rgb("#c4d4ff")
CANAL_GLOW = rgb("#b040c0")
FAINT = rgb("#241048")          # 傘全体のかすかな発光
EDGE = rgb("#ffd0f4")
DIM_PINK = rgb("#a43288")

BELL_ALPHA = 158                # 0.62（旧描画の BELL_ALPHA）
UNDER_ALPHA = 140
FRILL_ALPHA = 190


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

    def flip(self, src, dst):
        """src の領域を上下に返して dst へ写す（板の内の面は、外の面と絵の上下が逆に貼られる）"""
        x0, y0, w, h = src
        x1, y1, _, _ = dst
        for y in range(h):
            for x in range(w):
                self.px[y1 + y][x1 + x] = self.px[y0 + h - 1 - y][x0 + x]
                self.gl[y1 + y][x1 + x] = self.gl[y0 + h - 1 - y][x0 + x]


# ==================== 傘と下傘（台形に切り抜いた半透明の板） ====================
def _trapezoid(info, u, v):
    """台形の内側までの距離（u の単位、負なら外）と、頂点→縁の位置 vg、花弁の向き"""
    ft, fb, v0, dv = info
    hw = 0.5 * (ft + (fb - ft) * v) + shape.PAD_PX / TILE
    return hw - abs(u - 0.5), v0 + dv * v, hw


def _canal(side, u):
    """この画素に近い台形の辺が花弁の中心（触手の真上）側か。R は中心が絵の右"""
    return (u > 0.5) == (side == "R")


def _bell(row, side):
    info = shape.BELL_ROWS[row]

    def fn(u, v, x, y):
        d, vg, _ = _trapezoid(info, u, v)
        if d < 0:
            return None
        e = d * TILE                                   # 台形の辺までの画素数
        t = 0.22 + 0.36 * vg + 0.08 * math.sin(u * math.tau + vg * 9.0) + 0.10 * hashf(x // 3, y // 3, row)
        a = BELL_ALPHA + int(30 * vg)                  # 縁ほど厚い（濃い）
        if e < 1.8:
            if _canal(side, u):
                t, a = t + 0.22, 195                   # 花弁の中心の放射管
            else:
                t, a = t + 0.36, 220                   # 花弁の縁の放射管（開花で割れる線）
        if vg > 0.94:
            t, a = t + 0.12, max(a, 205)               # 厚い縁
        if hashf(x, y, row, 4) > 0.955:
            t += 0.3                                   # 細かな照り
        if abs(vg - 0.97) < 0.007 and x % 8 in (3, 4):
            return ICE + (230,)                        # 縁の灯り（発光と同じ点）
        return ramp(BELL_P, t, x + TILE * row, y) + (min(255, a),)

    def glow(u, v, x, y):
        d, vg, _ = _trapezoid(info, u, v)
        if d < 0:
            return None
        if abs(vg - 0.97) < 0.007 and x % 8 in (3, 4):
            return ICE                                 # 縁の灯り（縁の帯の中ほどに点線）
        if d * TILE < 1.5 and vg > 0.1:
            if _canal(side, u):
                return CANAL_GLOW
            return MAGENTA if y % 4 else CANAL_GLOW
        return FAINT                                   # 傘全体がかすかに光る

    return fn, glow


def _gonad(uc, vg):
    """下傘の生殖腺（馬蹄形）。花弁ごとに1つ、開いた側が傘の中心を向く。0..1 の濃さ（外は 0）"""
    dx = uc * 3.1                                      # 半花弁の弧（ブロック、vg = 0.5 のあたり）
    dy = (vg - 0.55) * 19.0                            # 下傘の頂点→縁の長さ（ブロック）
    if dy < -1.0:
        return 0.0
    band = abs(math.hypot(dx, dy) - 2.4)
    return max(0.0, 1.0 - band / 0.75)


def _under(row, side):
    info = shape.UNDER_ROWS[row]

    def uc_of(u, hw):
        # 0 が花弁の中心、1 が花弁の縁。台形の中で測るので、隣の列と模様が繋がる
        return (0.5 + hw - u) / (2 * hw) if side == "R" else (u - 0.5 + hw) / (2 * hw)

    def fn(u, v, x, y):
        d, vg, hw = _trapezoid(info, u, v)
        if d < 0:
            return None
        g = _gonad(uc_of(u, hw), vg)
        if g > 0.0:
            return ramp(PINK, 0.30 + 0.55 * g, x, y) + (min(255, UNDER_ALPHA + int(90 * g)),)
        t = 0.16 + 0.40 * vg + 0.12 * hashf(x // 2, y // 2, row + 20)
        a = UNDER_ALPHA
        if d * TILE < 1.6:
            t, a = t + (0.20 if _canal(side, u) else 0.30), 190
        return ramp(BELL_P[:5], t, x + TILE * row, y + 64) + (a,)

    def glow(u, v, x, y):
        d, vg, hw = _trapezoid(info, u, v)
        if d < 0:
            return None
        g = _gonad(uc_of(u, hw), vg)
        return MAGENTA if g > 0.55 else None

    return fn, glow


# ==================== 膜・触手・器官 ====================
def _membrane(alpha, lobes, edge_alpha, seed):
    """フリル。上端（v = 0）が自由縁、下端が付け根。縁は丸い襞（lobes 個）で、襞の谷が暗い。
    旧 boss_textures.membrane の桃色と縁の色を引き継ぎ、尖った歯は丸い波に変えた"""
    def edge(u, x):
        s = abs(math.sin(math.pi * u * lobes))         # 襞の山で 1、谷で 0
        return 0.70 + 0.25 * s ** 0.6 + 0.05 * (hashf(x // 2, seed) - 0.5)

    def fn(u, v, x, y):
        vv = 1.0 - v                                   # 0 = 付け根, 1 = 自由縁
        e = edge(u, x)
        if vv > e:
            return None
        if vv > e - 0.06:
            return EDGE + (edge_alpha,)
        fold = 0.5 + 0.5 * math.cos(math.tau * u * lobes)          # 襞の山で明るい
        vein = abs(((u * lobes * 3.0) % 1.0) - 0.5) < 0.07 and vv > 0.15
        t = 0.30 + 0.30 * fold + 0.20 * vv + (0.12 if vein else 0.0) + 0.06 * hashf(x // 2, y // 2, seed)
        return ramp(PINK, t, x, y) + (alpha,)

    def glow(u, v, x, y):
        vv = 1.0 - v
        e = edge(u, x)
        if vv > e:
            return None
        if e - 0.17 < vv < e - 0.10 and abs(((u * lobes) % 1.0) - 0.5) < 0.08:
            return MAGENTA                             # 襞の山の縁に光の粒
        return DIM_PINK if (vv > 0.45 and hashf(x // 2, y // 2, 6) > 0.6) else None

    return fn, glow


def _strand(u, v, x, y):
    """触手。上端（先端側の関節）に桃色の帯"""
    across = 1.0 - abs(u - 0.5) * 2.0
    if v < 0.10:
        return ramp(PINK, 0.50 + 0.35 * across, x, y)
    return ramp(BELL_P[2:], 0.30 + 0.40 * across + 0.10 * math.sin(v * 40.0), x, y)


def _strand_glow(u, v, x, y):
    return MAGENTA if v < 0.07 else None


def _sleeve(u, v, x, y):
    if abs(v - 0.5) < 0.14:
        return MAGENTA                                 # 刺胞の輪
    return ramp(PINK, 0.25 + 0.5 * (1.0 - abs(v - 0.5) * 2.0), x, y)


def _sleeve_glow(u, v, x, y):
    return MAGENTA if abs(v - 0.5) < 0.14 else None


def _bulb(u, v, x, y):
    d = math.hypot(u - 0.5, v - 0.5)
    return MAGENTA if d < 0.3 else ramp(PINK, 0.9 - d, x, y)


def _bulb_glow(u, v, x, y):
    return MAGENTA if math.hypot(u - 0.5, v - 0.5) < 0.3 else None


def _canal_px(u, v, x, y):
    across = 1.0 - abs(u - 0.5) * 2.0
    return ramp([BELL_P[3], BELL_P[4], PINK[1], PINK[2], PINK[3]], 0.25 + 0.6 * across + 0.1 * math.sin(v * 25), x, y)


def _canal_glow(u, v, x, y):
    return CANAL_GLOW if abs(u - 0.5) < 0.25 else None


def _stalk(u, v, x, y):
    fold = 0.5 + 0.5 * math.sin(y * 0.8 + 1.6 * math.sin(x * 0.45))
    return ramp([BELL_P[2], BELL_P[3], BELL_P[4], PINK[1], PINK[2]], 0.20 + 0.55 * fold + 0.1 * hashf(x // 2, y // 2, 9), x, y)


def _heart(u, v, x, y):
    vein = abs(math.sin(x * 0.55 + 2.2 * math.sin(y * 0.31))) < 0.16
    if vein:
        return PINK[1]
    return ramp([PINK[1], PINK[2], MAGENTA, PINK[3]], 0.20 + 0.65 * (0.5 + 0.5 * math.sin(y * 0.4 + x * 0.2)), x, y, 0.08)


def _heart_glow(u, v, x, y):
    c = _heart(u, v, x, y)
    return DIM_PINK if c == PINK[1] else c


def _oral(u, v, x, y):
    across = 1.0 - abs(u - 0.5) * 2.0
    return ramp([BELL_P[3], PINK[0], PINK[1], PINK[2], PINK[3]], 0.20 + 0.55 * across + 0.12 * math.sin(v * 30.0), x, y)


def _draw():
    s = Sheet()
    for side in ("R", "L"):
        for i, reg in enumerate(BELL_OUT[side]):
            fn, gl = _bell(i, side)
            s.fill(reg, fn, gl)
            s.flip(reg, BELL_IN[side][i])
        for i, reg in enumerate(UNDER_OUT[side]):
            fn, gl = _under(i, side)
            s.fill(reg, fn, gl)
            s.flip(reg, UNDER_IN[side][i])
    s.fill(CLEAR, lambda u, v, x, y: None)
    fn, gl = _membrane(FRILL_ALPHA, 2, 230, 3)
    s.fill(FRILL, fn, gl)
    fn, gl = _membrane(255, 2, 255, 5)
    s.fill(ORAL_FRILL, fn, gl)
    s.fill(STRAND, _strand, _strand_glow)
    s.fill(SLEEVE, _sleeve, _sleeve_glow)
    s.fill(BULB, _bulb, _bulb_glow)
    s.fill(CANAL, _canal_px, _canal_glow)
    s.fill(STALK, _stalk)
    s.fill(HEART, _heart, _heart_glow)
    s.fill(ORAL, _oral)
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
    write_png(out.with_name(out.stem + '_glow.png'), build_glow())
    print('wrote', out)
