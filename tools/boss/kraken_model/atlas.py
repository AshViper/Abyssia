# -*- coding: utf-8 -*-
"""大クラーケン（abyss_kraken）の GeckoLib モデル用テクスチャアトラス（256x256）。

色・模様・発光は `tools/boss_textures.py` の kraken()（旧メッシュ描画の皮膚）を引き継ぐ:
血のような赤の斑の皮膚と淡い色素胞、クリーム色の吸盤面、金の眼、黒褐色の嘴と鉤、
切断面の生肉、淡い水色（#bfe6ff）の発光点。乱数は使わず座標のハッシュだけで描くので、
何度流しても同じ PNG になる（決定的）。

<h2>貼り方の約束（build.py と揃える）</h2>
- 外套膜・頭・腕の背・腕の脇・ひれのような地の素材は、`boss_kit.Kit.cube` が面の実寸ぶんを
  領域の中から切り出す。tone（0..1）は領域の縦のどこを使うか（0 が上）
- STRETCHED の領域は、どの面にも領域まるごとを貼る（吸盤・吸盤の縁・眼・嘴・鉤・切断面・漏斗）
- 絵の上端は「面に沿って上る向き」（腕の板は断面を背の側へ上る向き、外套膜と頭は先端へ上る向き）、
  または棒の先（`Kit.beam`）、または扇の外縁（切断面の `Kit.fan`）
- 板を回転だけで置くので、左右で u が鏡写しになる。横（u）に向きのある絵は描かない

<h2>吸盤の領域は 4 種類</h2>
腕の吸盤面（体節の局所 -Y の板）は先端へ細るので、板の縦横比が体節ごとに変わる。
2 列の吸盤が丸く見えるよう、1 列あたりの数が 6 / 9 / 12 / 16 の 4 枚を用意し、
build.py が板の縦横比に近いものを選ぶ（絵の横 = 腕の長さ方向、縦 = 腕を横切る向き）。

<h2>発光</h2>
`build_glow()` は発光する画素だけを不透明で残し、ほかは透明な黒（0,0,0,0）。
加算の RenderType.eyes でも、GeckoLib の自動発光層でも、光らない所は何も足さない。

```bash
python tools/boss/kraken_model/atlas.py [出力先.png]   # アトラスだけを確認用に書き出す
```
"""
import math
import pathlib
import struct
import sys
import zlib

SIZE = 256

# (x, y, w, h)
MANTLE = (0, 0, 128, 64)        # 外套膜。上ほど明るく、下ほど暗い（tone = 高さ）
HEAD = (128, 0, 128, 64)        # 頭
ARM_BACK = (0, 64, 128, 32)     # 腕の背（反口側）。上ほど暗い
ARM_SIDE = (0, 96, 128, 32)     # 腕の脇。淡い色素胞（発光）
FIN = (128, 64, 128, 40)        # ひれ
ARM_RIM = (128, 104, 32, 16)    # 吸盤面の縁。上が赤い皮膚、下がクリームの縁と小さな吸盤 (stretch)
SUCKER_6 = (128, 120, 64, 16)   # 吸盤 2 列 x 6 (stretch, 発光)
SUCKER_9 = (192, 120, 64, 16)   # 2 列 x 9
SUCKER_12 = (128, 136, 64, 16)  # 2 列 x 12
SUCKER_16 = (192, 136, 64, 16)  # 2 列 x 16
INNER = (0, 128, 64, 32)        # 口のまわりの唇（クリームがかった桃色）
EYE = (64, 128, 32, 32)         # 眼 (stretch, 発光)
SOCKET = (96, 128, 16, 16)      # 眼窩の縁（黒ずんだ赤）
CAVITY = (96, 144, 16, 16)      # 口の奥の闇
BEAK = (112, 128, 8, 32)        # 嘴 (stretch)。上端が先
HOOK = (120, 128, 8, 16)        # 腕の鉤 (stretch)。上端が先
FLESH = (0, 160, 32, 32)        # 切断面の扇 1 枚 (stretch)。下端が芯、上端が皮の縁
FLESH_RAW = (32, 160, 32, 32)   # 盛り上がった生肉
SIPHON = (64, 160, 32, 32)      # 漏斗 (stretch)。上端が口

SUCKERS = ((6, SUCKER_6), (9, SUCKER_9), (12, SUCKER_12), (16, SUCKER_16))
STRETCHED = {ARM_RIM, SUCKER_6, SUCKER_9, SUCKER_12, SUCKER_16, EYE, BEAK, HOOK, FLESH, SIPHON}
TOP_ANCHORED = set()


def rgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)


# boss_textures.py の kraken() と同じ色
RED = [rgb(c) for c in ("#2a0610", "#450a18", "#641222", "#881c2c", "#ad3038", "#cc5248")]
CREAM = [rgb(c) for c in ("#8a6258", "#b48a7a", "#d8b8a4", "#efdccb")]
PALE = rgb("#bfe6ff")
TOOTH = [rgb(c) for c in ("#14100c", "#2e261c", "#57492f", "#8a7648")]
FLESH_P = [rgb(c) for c in ("#641222", "#ad3038", "#e08a80")]
HORN = [rgb(c) for c in ("#0c0806", "#2e261c", "#57492f", "#8a7648")]
IRIS = [rgb(c) for c in ("#7a5a10", "#c89a18", "#f2d040", "#fff4a8")]
PUPIL = rgb("#060404")
DARK = [rgb(c) for c in ("#060404", "#14050a", "#2a0610")]


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


def mottled(u, v, px, py, k):
    """boss_textures.py の kraken() の斑。淡い色素胞の点が混じる"""
    t = 0.30 + 0.25 * math.sin(u * math.tau * 3 + v * 5) * math.sin(v * 9 + u * 4) + 0.2 * hashf(px // 3, py // 3, k)
    if hashf(px // 2, py // 2, k + 1) > 0.94:
        t += 0.35
    return t


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


def _spots(key, rate):
    """まばらな発光点（2x2 画素）。旧テクスチャと同じ密度"""
    return lambda u, v, x, y: PALE if hashf(x // 2, y // 2, key) > rate else None


# ==================== 体 ====================
def _mantle(u, v, x, y):
    return ramp(RED, mottled(u, v, x, y, 1) + 0.15 * (1 - v), x, y)


def _head(u, v, x, y):
    return ramp(RED, mottled(u, v, x, y, 3) + 0.2 * v - 0.1, x, y)


def _arm_back(u, v, x, y):
    return ramp(RED, mottled(u, v, x, y, 2) - 0.06 + 0.16 * v, x, y)


def _arm_side(u, v, x, y):
    return ramp(RED, mottled(u, v, x, y, 5) + 0.06 + 0.08 * v, x, y)


def _fin(u, v, x, y):
    t = mottled(u, v, x, y, 4) + 0.05
    if x % 11 == 0 and hashf(x // 11, y // 6, 13) > 0.4:
        t -= 0.12                                       # ひれの筋
    return ramp(RED, t, x, y)


# ==================== 吸盤 ====================
def _sucker_cell(n, u, v):
    """吸盤の格子の中の位置。(列の番号, 行の番号, 中心からの距離 0..1)"""
    cu = u * n
    i = int(cu)
    j = 0 if v < 0.5 else 1
    du = (cu - i) * 2.0 - 1.0
    dv = ((v * 2.0) % 1.0) * 2.0 - 1.0
    return i, j, math.hypot(du, dv)


def _sucker(n):
    def fn(u, v, x, y):
        i, j, d = _sucker_cell(n, u, v)
        if d < 0.34:
            return ramp(RED, 0.12, x, y, 0.03)              # 吸盤の口
        if d < 0.52:
            return ramp(CREAM, 0.95, x, y, 0.03)            # 縁の照り
        if d < 0.78:
            return ramp(CREAM, 0.62 - 0.4 * (d - 0.52), x, y, 0.04)
        if d < 0.9:
            return CREAM[0]                                  # 吸盤の付け根の影
        return ramp(CREAM, 0.30, x, y, 0.05)
    return fn


def _sucker_glow(n):
    def fn(u, v, x, y):
        i, j, d = _sucker_cell(n, u, v)
        return PALE if d < 0.26 and (i + j) % 2 == 0 else None
    return fn


def _arm_rim(u, v, x, y):
    """吸盤面の縁。上半分は腕の脇の皮膚、下半分はクリームの縁に小さな吸盤"""
    if v < 0.42:
        return ramp(RED, mottled(u, v, x, y, 6) + 0.1, x, y)
    if v < 0.52:
        return RED[1]                                         # 皮膚とクリームの境の溝
    cu = (u * 4.0) % 1.0 * 2.0 - 1.0
    cv = (v - 0.78) / 0.2
    d = math.hypot(cu, cv)
    if d < 0.35:
        return ramp(RED, 0.15, x, y, 0.03)
    if d < 0.75:
        return ramp(CREAM, 0.8, x, y, 0.04)
    return ramp(CREAM, 0.35, x, y, 0.05)


# ==================== 頭まわり ====================
def _inner(u, v, x, y):
    return ramp(CREAM, 0.2 + 0.5 * v + 0.2 * hashf(x // 2, y // 2), x, y)


def _eye(u, v, x, y):
    dx, dy = u - 0.5, v - 0.5
    d = math.hypot(dx, dy)
    if d > 0.46:
        return PUPIL                                     # 眼の縁は闇（透かさない）
    if abs(dy) < 0.10 or (abs(dx) > 0.2 and dy < 0.0 and abs(dy) < 0.22):
        return PUPIL                                     # 横に長い瞳（旧テクスチャと同じ形）
    return ramp(IRIS, 1.0 - d * 1.7, x, y, 0.04)


def _eye_glow(u, v, x, y):
    c = _eye(u, v, x, y)
    return None if c == PUPIL else c


def _socket(u, v, x, y):
    return ramp(DARK, 0.45 + 0.35 * hashf(x // 2, y // 2, 21), x, y, 0.08)


def _cavity(u, v, x, y):
    return DARK[0] if hashf(x, y, 3) > 0.2 else DARK[1]


def _beak(u, v, x, y):
    """嘴。上端（先）が黒く、付け根ほど褐色。縦に成長の筋"""
    t = 0.10 + 0.75 * v + 0.12 * math.sin(v * 22) - 0.30 * abs(u - 0.5)
    return ramp(HORN, t, x, y, 0.05)


def _hook(u, v, x, y):
    return ramp(TOOTH, 0.25 + 0.7 * (1 - v) - 0.3 * abs(u - 0.5), x, y, 0.03)


def _flesh(u, v, x, y):
    """切断面の扇 1 枚。下端が芯（暗い神経の管）、上へ筋肉の輪、上端に皮の縁"""
    r = 1.0 - v
    if r < 0.14:
        return RED[0]
    if r > 0.88:
        return ramp(RED, 0.22, x, y, 0.04)
    t = 0.45 + 0.35 * math.sin(r * 21.0 + 0.6 * math.sin(u * 9.0)) + 0.15 * hashf(x // 2, y // 2, 17)
    return ramp(FLESH_P, t, x, y, 0.1)


def _flesh_raw(u, v, x, y):
    return ramp(FLESH_P, 0.5 + 0.4 * math.sin(v * 9 + hashf(x, 1) * 3), x, y, 0.15)


def _siphon(u, v, x, y):
    if v < 0.12:
        return DARK[1] if abs(u - 0.5) < 0.32 else RED[1]   # 漏斗の口
    t = 0.35 + 0.18 * math.sin(v * 20.0) - 0.30 * abs(u - 0.5) + 0.1 * hashf(x // 2, y // 2, 19)
    return ramp(RED, t, x, y)


def _draw():
    s = Sheet()
    s.fill(MANTLE, _mantle, _spots(9, 0.975))
    s.fill(HEAD, _head, _spots(12, 0.985))
    s.fill(ARM_BACK, _arm_back)
    s.fill(ARM_SIDE, _arm_side, _spots(14, 0.985))
    s.fill(FIN, _fin, _spots(15, 0.98))
    s.fill(ARM_RIM, _arm_rim)
    for n, region in SUCKERS:
        s.fill(region, _sucker(n), _sucker_glow(n))
    s.fill(INNER, _inner)
    s.fill(EYE, _eye, _eye_glow)
    s.fill(SOCKET, _socket)
    s.fill(CAVITY, _cavity)
    s.fill(BEAK, _beak)
    s.fill(HOOK, _hook)
    s.fill(FLESH, _flesh)
    s.fill(FLESH_RAW, _flesh_raw)
    s.fill(SIPHON, _siphon)
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
