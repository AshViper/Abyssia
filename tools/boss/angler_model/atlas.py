# -*- coding: utf-8 -*-
"""アビスアングラー（abyss_angler）の GeckoLib モデル用テクスチャアトラス（512x512）。

色・模様・発光は `tools/boss_textures.py` の angler()（旧メッシュ描画の皮膚）を引き継ぐ:
紺黒の鱗、腹は暗い紫紺の細かい鱗、下脇腹の2列の発光器（シアンの芯と暗い縁）、
頭の眼下と顎の線の発光点、青紫の畝のある口の中（裏地がわずかに光る）、
破れて透ける青い鰭膜、骨色の牙、骨の角と棘、提灯の珠（首から先へ明るくなる）、
青い虹彩の眼。乱数は使わず座標のハッシュだけで描くので、何度流しても同じ PNG になる。

<h2>なぜ 512 か</h2>
MODEL_SCALE = 3（AbyssAngler.SIZE）なので geo の 1 単位 = 0.1875 ブロック。体の板1枚が
90x40 単位、背びれの膜が高さ 140 単位ほどになり、256 では領域に収まらず引き伸ばされる。

<h2>貼り方の約束（build.py と揃える）</h2>
- 鱗・頭・顎・口の中のような地の素材は、`boss_kit.Kit.cube` が面の実寸ぶんを領域の中から
  切り出す。tone（0..1）は領域の縦のどこを使うか。0 が上（暗い背の側）、1 が下（明るい側）
- STRETCHED の領域は、どの面にも領域まるごとを貼る（眼・牙・角・棘・柄・珠）
- 絵の上端は「面に沿って上る向き」（`ring_loft` の板）、棒の先（`Kit.beam`）、
  鰭膜の外縁（FIN / MEMBRANE は上端から切り出す）、珠の首（`axis_loft`）
- 体の左右で板の u が鏡写しになる。横方向（u）に向きのある絵は描かない

<h2>発光</h2>
`build_glow()` は発光する画素だけを不透明で残し、ほかは透明な黒（0,0,0,0）。
鰭膜は旧テクスチャと同じく部分的な不透明度（120〜235）を持つ。cutout なら不透明、
translucent なら透ける膜になる。

```bash
python tools/boss/angler_model/atlas.py [出力先.png]   # アトラスだけを確認用に書き出す
```
"""
import math
import pathlib
import struct
import sys
import zlib

SIZE = 512

# (x, y, w, h)
BACK = (0, 0, 256, 48)          # 背の帯。背びれの下の暗い稜
FLANK = (0, 48, 256, 96)        # 脇腹の鱗。上ほど暗く、下ほど明るい（tone）
PHOTO = (0, 144, 256, 48)       # 下脇腹。発光器の2列（発光）
BELLY = (0, 192, 256, 48)       # 腹。暗い紫紺の細かい鱗
FIN = (0, 240, 256, 144)        # 背・臀・尾びれの膜。上端が外縁（破れ）。上端から切り出す
INNER = (0, 384, 128, 64)       # 口蓋・口の床。青紫の畝（裏地がわずかに光る）
THROAT = (128, 384, 128, 64)    # 喉の奥。青紫の渦（わずかに光る）
PLATE = (0, 448, 128, 64)       # 眉・鰓蓋の縁・口縁の骨の稜
CAVITY = (128, 448, 64, 64)     # 眼窩・喉の奥の闇
HEAD = (256, 0, 256, 96)        # 頭の鱗。上ほど暗い
HEAD_PHOTO = (256, 96, 256, 32)  # 頭の下縁（口縁の上）。眼下の発光点の列（発光）
JAW = (256, 128, 256, 48)       # 下顎の脇。暗い鱗
JAW_UNDER = (256, 176, 256, 48)  # 下顎の下。上端に発光点の列（発光）、下は腹の鱗
MEMBRANE = (256, 224, 128, 192)  # 胸びれ・腹びれの膜。上端が外縁。上端から切り出す
RAY = (384, 224, 16, 128)       # 鰭の棘と鰭条 (stretch)。上端が先
HORN = (400, 224, 16, 128)      # 頭の骨の角 (stretch)。上端が先
TOOTH = (416, 224, 12, 64)      # 牙 (stretch)。上端が先
STALK = (428, 224, 12, 96)      # 提灯の柄 (stretch)。上端が先
EYE = (448, 224, 32, 32)        # 眼 (stretch, 発光)。四隅は透明（丸い眼）
BULB = (480, 224, 32, 32)       # 提灯の珠 (stretch, 発光)。上端が首、下へ明るくなる

STRETCHED = {RAY, HORN, TOOTH, STALK, EYE, BULB}
TOP_ANCHORED = {FIN, MEMBRANE}


def rgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)


# angler() の色をそのまま
HIDE = [rgb(c) for c in ("#03060d", "#071020", "#0c1830", "#122442", "#1a3256", "#26446c")]
BELLY_P = [rgb(c) for c in ("#0c0f22", "#141a34", "#1e2646", "#2a3458")]
VIOLET = [rgb(c) for c in ("#160c34", "#241852", "#342672", "#463494", "#5c48b6", "#7c68d4")]
FILM = [rgb(c) for c in ("#1a1f5c", "#22337a", "#2a4a98", "#3466b4", "#4486cc", "#62a8e2")]
CYAN, PALE, DIM = rgb("#4cc8ff"), rgb("#a8e8ff"), rgb("#1a6a9a")
SPOT_RIM = rgb("#0e3c5a")
LINE_GLOW = rgb("#2a8ac0")
LINING, LINING_HI = rgb("#140c30"), rgb("#251a58")
TOOTH_P = [rgb(c) for c in ("#8a8068", "#b8ae92", "#e0d8bc", "#f6f1e0")]
FLESH_P = [rgb(c) for c in ("#0a0f24", "#141c3c", "#202a58")]
HORN_P = [rgb(c) for c in ("#2a2620", "#5c5444", "#9a8e72", "#d4c8a8")]
BULB_P = [rgb(c) for c in ("#2a8ac8", "#4cc8ff", "#8cdcff", "#c8f2ff")]
IRIS = [rgb(c) for c in ("#1a5a8c", "#3aa8e0", "#7cd8ff", "#d4f6ff")]
PUPIL = rgb("#03070e")
EYE_RIM = rgb("#0a1a2c")
VOID = rgb("#03070e")


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
    """背びれの下の暗い稜（angler() の b > 0.92）"""
    t = (0.10 + 0.45 * scales(x, y, 10)) * 0.55
    return ramp(HIDE, t, x, y)


def _flank(u, v, x, y):
    t = 0.10 + 0.45 * scales(x, y, 10) + 0.25 * v
    if hashf(x, y, 17) > 0.985 and 0.15 < v < 0.85:
        t += 0.3                                       # 散らばった明るい鱗の先
    return ramp(HIDE, t, x, y)


PHOTO_ROWS = ((12, 0), (34, 10))   # 発光器の列（領域の中の y と、x のずれ）
PHOTO_PERIOD = 20


def _spot(x, y):
    """下脇腹の発光器: 2 = 芯、1 = 縁、0 = 無し（angler() の spot と同じ2列）"""
    for ry, off in PHOTO_ROWS:
        dx = ((x + off) % PHOTO_PERIOD) - (PHOTO_PERIOD - 1) / 2.0
        d = math.hypot(dx / 4.2, (y - ry) / 3.4)
        if d < 0.6:
            return 2
        if d < 1.0:
            return 1
    return 0


def _photo(u, v, x, y):
    sp = _spot(x, y)
    if sp:
        return PALE if sp == 2 else DIM
    return ramp(HIDE, 0.28 + 0.42 * scales(x, y, 10) + 0.12 * v, x, y)


def _photo_glow(u, v, x, y):
    sp = _spot(x, y)
    if sp:
        return CYAN if sp == 2 else SPOT_RIM
    return None


def _belly(u, v, x, y):
    return ramp(BELLY_P, 0.2 + 0.5 * scales(x, y, 6) + 0.2 * (1.0 - v), x, y)


# ==================== 頭 ====================
def _head(u, v, x, y):
    t = 0.10 + 0.40 * scales(x, y, 8) + 0.2 * v
    if v < 0.3 and (x + (y // 8) * 3) % 18 in (0, 1):
        t += 0.14                                       # 額の皺
    if hashf(x // 6, y // 6, 41) > 0.88:
        t -= 0.08                                       # まだら
    return ramp(HIDE, t, x, y)


def _head_dot(x, y):
    return 14 <= y <= 17 and x % 6 in (2, 3)


def _head_photo(u, v, x, y):
    if _head_dot(x, y):
        return DIM                                      # 眼下の発光点
    return ramp(HIDE, 0.26 + 0.40 * scales(x, y, 8), x, y)


def _head_photo_glow(u, v, x, y):
    return LINE_GLOW if _head_dot(x, y) else None


def _jaw(u, v, x, y):
    return ramp(HIDE, 0.12 + 0.42 * scales(x, y, 6) + 0.15 * v, x, y)


def _jaw_dot(x, y):
    return 4 <= y <= 7 and x % 6 < 2


def _jaw_under(u, v, x, y):
    if _jaw_dot(x, y):
        return DIM                                      # 顎の線の下の発光点
    return ramp(BELLY_P, 0.25 + 0.45 * scales(x, y, 6), x, y)


def _jaw_under_glow(u, v, x, y):
    return LINE_GLOW if _jaw_dot(x, y) else None


def _ridges(v, n=60):
    return 0.5 + 0.5 * math.sin(v * n)


def _inner(u, v, x, y):
    """口蓋・口の床: 青紫の畝が横切る"""
    return ramp(VIOLET, 0.2 + 0.35 * _ridges(v) + 0.25 * (0.5 + 0.5 * math.sin(u * 9.0)), x, y)


def _inner_glow(u, v, x, y):
    return LINING_HI if _ridges(v) > 0.75 else LINING


def _swirl(u, v):
    return math.sin(v * 36 + 1.5 * math.sin(u * math.tau * 3))


def _throat(u, v, x, y):
    return ramp(VIOLET, 0.15 + 0.5 * (0.5 + 0.5 * _swirl(u, v)) + 0.15 * hashf(x // 2, y // 2, 5), x, y)


def _throat_glow(u, v, x, y):
    return LINING_HI if _swirl(u, v) > 0.6 else LINING


def _cavity(u, v, x, y):
    return VOID if hashf(x, y, 3) > 0.15 else HIDE[0]


def _plate(u, v, x, y):
    """骨の稜。紺黒の角質に、上ほど明るい縁"""
    t = 0.30 + 0.35 * (1.0 - v) + 0.08 * math.sin(x * 0.7)
    if (x * 3 + y * 5) % 29 == 0:
        t = 0.05                                        # ひび
    return ramp(HIDE, t, x, y, 0.05)


# ==================== 鰭・角・牙・柄・珠・眼 ====================
def _noise1(x, period, seed):
    """滑らかな1次元の値ノイズ（period 画素ごとの格子点の値をコサインで繋ぐ）"""
    i = math.floor(x / period)
    f = x / period - i
    a, b = hashf(i, seed), hashf(i + 1, seed)
    return a + (b - a) * (0.5 - 0.5 * math.cos(math.pi * f))


def _tear_depth(x, seed):
    """外縁の破れの深さ（上端から何画素欠けるか）。緩い波＋ときどき深い V 字の裂け目"""
    d = 2.0 + 9.0 * (0.65 * _noise1(x, 14, seed) + 0.35 * _noise1(x, 5, seed + 9))
    cell = x // 36
    if hashf(cell, seed + 5) > 0.55:
        c = cell * 36 + 8 + int(20 * hashf(cell, seed + 4))
        dx = abs(x - c)
        if dx < 4:
            d += (16.0 + 14.0 * hashf(cell, seed + 6)) * (1.0 - dx / 4.0)
    return d


def _membrane(period, seed):
    """破れた半透明の膜（angler() の membrane_fn の色・不透明度）。上端が外縁。

    板は上端から切り出すので、破れは画素の深さで決める（膜の高さによらず外縁の数画素だけ欠ける）。
    膜の板は z に沿って u が続くよう切り出すので（build.film）、破れと鰭条は板を跨いで続く"""
    def fn(u, v, x, y):
        top = _tear_depth(x, seed)
        if y < top:
            return None
        r = 1.0 - v                                     # 付け根 0 → 外縁 1
        ray = (x % period) < 2
        if not ray and y > top + 10:
            hx, hy = x // 14, y // 18
            if hashf(hx, hy, seed + 2) > 0.9:
                cx, cy = hx * 14 + 7, hy * 18 + 9
                if math.hypot((x - cx) / 4.0, (y - cy) / 5.5) < 1.0:
                    return None                         # 穴
        t = 0.25 + 0.55 * r + 0.12 * hashf(x // 2, y // 3, seed + 3)
        if y < top + 2:
            return ramp(FILM, 0.95, x, y) + (210,)      # 明るいほつれた縁
        if ray:
            return ramp(FILM, t - 0.2, x, y, 0.03) + (235,)
        return ramp(FILM, t, x, y) + (int(120 + 70 * (1 - r)),)
    return fn


def _ray(u, v, x, y):
    """鰭の棘と鰭条。骨色で、先（上）ほど明るい"""
    t = 0.15 + 0.70 * (1.0 - v) - 0.30 * abs(u - 0.5)
    if y % 10 == 0:
        t -= 0.2                                        # 節
    return ramp(HORN_P, t, x, y, 0.04)


def _horn(u, v, x, y):
    t = 0.15 + 0.75 * (1.0 - v) + 0.15 * math.sin(v * 22) - 0.3 * abs(u - 0.5)
    return ramp(HORN_P, t, x, y, 0.05)


def _tooth(u, v, x, y):
    return ramp(TOOTH_P, 0.25 + 0.7 * (1.0 - v) - 0.3 * abs(u - 0.5), x, y, 0.03)


def _stalk(u, v, x, y):
    return ramp(FLESH_P, 0.5 + 0.4 * math.sin(v * 9 + hashf(x, 1) * 3) - 0.3 * abs(u - 0.5), x, y, 0.15)


def _bulb(u, v, x, y):
    return ramp(BULB_P, 0.5 + 0.5 * v, x, y, 0.03)


def _bulb_glow(u, v, x, y):
    return ramp(BULB_P, 0.15 + 0.85 * min(1.0, v * 1.6), x, y, 0.03)


def _eye(u, v, x, y):
    d = math.hypot(u - 0.5, v - 0.5)
    if d > 0.5:
        return None
    if d < 0.13:
        return PUPIL
    if d > 0.44:
        return EYE_RIM
    return ramp(IRIS, 1.0 - (d - 0.13) * 2.4, x, y, 0.04)


def _eye_glow(u, v, x, y):
    d = math.hypot(u - 0.5, v - 0.5)
    if 0.13 <= d <= 0.44:
        return ramp(IRIS, 1.0 - (d - 0.13) * 2.4, x, y, 0.04)
    return None


def _draw():
    s = Sheet()
    s.fill(BACK, _back)
    s.fill(FLANK, _flank)
    s.fill(PHOTO, _photo, _photo_glow)
    s.fill(BELLY, _belly)
    s.fill(FIN, _membrane(16, 47))
    s.fill(INNER, _inner, _inner_glow)
    s.fill(THROAT, _throat, _throat_glow)
    s.fill(PLATE, _plate)
    s.fill(CAVITY, _cavity)
    s.fill(HEAD, _head)
    s.fill(HEAD_PHOTO, _head_photo, _head_photo_glow)
    s.fill(JAW, _jaw)
    s.fill(JAW_UNDER, _jaw_under, _jaw_under_glow)
    s.fill(MEMBRANE, _membrane(16, 31))
    s.fill(RAY, _ray)
    s.fill(HORN, _horn)
    s.fill(TOOTH, _tooth)
    s.fill(STALK, _stalk)
    s.fill(EYE, _eye, _eye_glow)
    s.fill(BULB, _bulb, _bulb_glow)
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
