# -*- coding: utf-8 -*-
"""海溝の大ワーム（trench_wyrm）の GeckoLib モデル用テクスチャアトラス（256x256）。

色・模様・発光は `tools/boss_textures.py` の wyrm()（旧メッシュ描画の皮膚）を引き継ぐ:
赤錆色の甲殻の環（前縁が明るく後ろへ暗くなる、縦の稜と小さな窪み）、環の継ぎ目に
燃えさしの発光（脇腹だけ）、柔らかい肉色の腹（環ごとの皺）、頭の襟は口縁に光る点、
喉の奥は暗い赤で奥ほど光る、骨色の歯、黒赤の角質（剛毛・背の棘・尾の針）、
縦の瞳の燃えさし色の眼。乱数は使わず座標のハッシュだけで描くので、何度流しても
同じ PNG になる（決定的）。

<h2>貼り方の約束（build.py と揃える）</h2>
- 体の環・襟・喉・大顎の板は、どれも領域まるごとを面に貼る（STRETCHED）。板の外の面で
  絵の横（u）は<b>板の長さ方向</b>で、左が前（頭側・根元側）、右が後ろ（尾側・先端側）。
  絵の縦（v）は周方向で、左右の板で上下が裏返る。だから<b>縦に向きのある絵は描かない</b>
  （どの領域も v について上下対称にしてある。ハッシュも上下で同じ値を引く）
- 体の環の板は前の環の縁（袖）の下へ VIS_U0 まで潜る。見えるのは u >= VIS_U0 だけで、
  そこから前縁の明るい稜 → 甲殻 → 後ろの縁の照り、の順に描く。潜った所は継ぎ目の肉で、
  脇腹（RING_L）は燃えさし色に光る。曲げると外側で見えてくる
- 歯・棘・剛毛・肉の房（beam）は絵の上端が先
- 発光は `_glow.png`。光らない画素は (0,0,0,0)

```bash
python tools/boss/wyrm_model/atlas.py [出力先.png]   # アトラスだけを確認用に書き出す
```
"""
import math
import pathlib
import struct
import sys
import zlib

SIZE = 256

# 体の環の板が前の環の袖の下へ潜る割合（build.py の TUCK+LIP / 環の長さ と一致させる。build が確かめる）
VIS_U0 = 26.0 / 58.0

# (x, y, w, h)
RING_T = (0, 0, 48, 24)         # 環の背（正中の板）
RING_T2 = (48, 0, 48, 24)
RING_U = (0, 24, 48, 24)        # 環の脇腹の上
RING_U2 = (48, 24, 48, 24)
RING_L = (0, 48, 48, 24)        # 環の脇腹（継ぎ目が光る）
RING_L2 = (48, 48, 48, 24)
RING_B = (0, 72, 48, 24)        # 柔らかい腹
RING_B2 = (48, 72, 48, 24)
COLLAR = [(96 + 32 * i, 0, 32, 24) for i in range(4)]     # 頭の襟。口縁（0）→ 首（3）
GULLET = [(96 + 32 * i, 24, 32, 24) for i in range(4)]    # 喉。口縁（0）→ 奥（3、光る）
MAND_OUT = [(96 + 16 * i, 48, 16, 16) for i in range(4)]  # 大顎の外側。根元（0）→ 先（3）
MAND_IN = [(96 + 16 * i, 64, 16, 16) for i in range(4)]   # 大顎の内側
EYE = (160, 48, 16, 16)         # 襟の眼（発光）
CAVITY = (176, 48, 16, 16)      # 眼窩の闇
TOOTH = (200, 48, 8, 24)        # 歯。上端が先
HORN = (208, 48, 8, 48)         # 背の棘・尾の針。上端が先
BRISTLE = (216, 48, 8, 48)      # 剛毛。上端が先
LOBE = (160, 64, 32, 16)        # 剛毛の付け根の肉の房（疣足）。上端が先
RIDGE = (224, 0, 32, 32)        # 襟の上の骨の稜（大顎の付け根）

RINGS = (RING_T, RING_T2, RING_U, RING_U2, RING_L, RING_L2, RING_B, RING_B2)
STRETCHED = set(RINGS) | set(COLLAR) | set(GULLET) | set(MAND_OUT) | set(MAND_IN) | {
    EYE, CAVITY, TOOTH, HORN, BRISTLE, LOBE, RIDGE}
TOP_ANCHORED = set()


def rgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)


# boss_textures.py の wyrm() と同じ色
PLATE = [rgb(c) for c in ("#160806", "#2a100c", "#431a12", "#5f2718", "#7f3a20", "#a2562e")]
FLESH = [rgb(c) for c in ("#5c2a26", "#84443a", "#a9665a", "#c98c78", "#e0b29c")]
INNER = [rgb(c) for c in ("#1c0406", "#3c080c", "#681016", "#9a2420", "#c84a34")]
TOOTH_P = [rgb(c) for c in ("#6e5a40", "#a8946c", "#d8c8a0", "#f2e8cc")]
HORN_P = [rgb(c) for c in ("#0e0504", "#2a100c", "#5f2718", "#a2562e")]
IRIS = [rgb(c) for c in ("#7a2a06", "#c8540c", "#ff7a1e", "#ffd08a")]
PUPIL = rgb("#120402")
EMBER = rgb("#ff7a1e")
EMBER_DEEP = rgb("#b0400c")
THROAT = rgb("#7a1c08")
VOID = rgb("#070202")


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
        """fn(u, v, x, y) -> (r,g,b) / None（透明）。y は上下対称に折り返した画素（sy）も渡す"""
        x0, y0, w, h = region
        for y in range(h):
            sy = min(y, h - 1 - y)                      # 上下対称の絵にするため、ハッシュは折り返した y で引く
            for x in range(w):
                u, v = (x + 0.5) / w, (y + 0.5) / h
                c = fn(u, v, x, sy)
                if c is not None:
                    self.px[y0 + y][x0 + x] = c if len(c) == 4 else c + (255,)
                if glow is not None:
                    g = glow(u, v, x, sy)
                    if g is not None:
                        self.gl[y0 + y][x0 + x] = g + (255,)


# ==================== 体の環 ====================
def _vis(u):
    """板の見える部分での位置（0 = 前の袖の縁のすぐ後ろ、1 = 自分の袖の後ろ端）。潜った所は負"""
    return (u - VIS_U0) / (1.0 - VIS_U0)


def _mottle(x, y, seed, cell=3):
    """甲殻のまだら（-0.5..0.5）。縦横どちらにも筋を作らないよう、格子のハッシュを2段に重ねる"""
    return 0.65 * (hashf(x // cell, y // cell, seed) - 0.5) + 0.35 * (hashf(x // (cell * 2), y // (cell * 2), seed + 9) - 0.5)


def _ring(lift, seed):
    """甲殻の環の板。周方向（v）には筋を入れず、前後（u）に「継ぎ目 → 明るい前縁の稜 → 溝 →
    後ろへ暗くなる甲殻 → 段の照り」と並べて、環の律動を出す。lift は背ほど明るくする量"""
    def fn(u, v, x, y):
        a = _vis(u)
        if a < 0.0:
            return ramp(PLATE, 0.04 + 0.10 * hashf(x // 2, y // 2, seed), x, y)    # 潜った継ぎ目
        if a < 0.06:
            return PLATE[0]                               # 袖の縁の影（継ぎ目）
        if a < 0.20:
            return ramp(PLATE, 0.86 + lift + 0.1 * _mottle(x, y, seed), x, y, 0.04)   # 前縁の明るい稜
        if a < 0.25:
            return ramp(PLATE, 0.18, x, y, 0.03)         # 稜の後ろの溝
        t = 0.62 - 0.40 * (a - 0.25) / 0.67 + lift + 0.22 * _mottle(x, y, seed)
        if 0.55 < a < 0.58:
            t -= 0.2                                      # 環を横切る細い溝
        if a > 0.93:
            t = 0.74 + lift                               # 後ろの縁（段）の照り
        if hashf(x // 2, y // 2, seed + 3) > 0.95:
            t -= 0.3                                      # 窪み
        return ramp(PLATE, t, x, y)
    return fn


def _ring_glow(seed):
    """脇腹の継ぎ目（袖の縁のすぐ後ろ）と、潜った所の燃えさし"""
    def fn(u, v, x, y):
        a = _vis(u)
        if 0.0 <= a < 0.07 and hashf(x // 3, y // 6, seed) > 0.30:
            return EMBER if a < 0.04 else EMBER_DEEP
        if a < 0.0 and abs(a + 0.35) < 0.05 and hashf(x // 2, y // 5, seed + 1) > 0.45:
            return EMBER_DEEP                             # 曲げると外側に覗く継ぎ目の奥
        return None
    return fn


def _ring_lit(seed):
    base, glow = _ring(0.0, seed), _ring_glow(seed)

    def fn(u, v, x, y):
        g = glow(u, v, x, y)
        return g if g is not None else base(u, v, x, y)
    return fn


def _belly(seed):
    """柔らかい腹。環ごとに皺が寄る（boss_textures の creased per ring）"""
    def fn(u, v, x, y):
        a = _vis(u)
        if a < 0.0:
            return ramp(FLESH, 0.08, x, y)
        if a < 0.06:
            return FLESH[0]                               # 環の境の深い皺
        t = 0.2 + 0.55 * math.sin(min(1.0, a) * math.pi) + 0.1 * hashf(x // 2, y // 2, seed)
        if x % 7 == 3 and a < 0.9:
            t -= 0.12                                     # 細かい横皺
        return ramp(FLESH, t, x, y)
    return fn


# ==================== 頭 ====================
def _collar(i):
    """襟の板。i は口縁から首への区間。V = 口縁(0)→首(1) で後ろほど明るく、縦の稜が1本、口縁は暗い"""
    def fn(u, v, x, y):
        V = (i + u) / 4.0
        t = 0.2 + 0.45 * V - (0.25 if V < 0.12 else 0.0) + 0.22 * _mottle(x, y, 40 + i)
        if i > 0 and u < 0.08:
            t = 0.08                                      # 襟を横切る環の溝（区間の境）
        elif i > 0 and u < 0.2:
            t += 0.25                                     # 溝の後ろの稜
        if i == 3 and u > 0.9:
            t = 0.78                                      # 首へ被さる後ろの縁（段）の照り
        if hashf(x // 2, y // 2, 44 + i) > 0.95:
            t -= 0.3
        return ramp(PLATE, t, x, y)
    return fn


def _collar_glow(i):
    def fn(u, v, x, y):
        V = (i + u) / 4.0
        if V < 0.08 and (y % 8) < 3:
            return EMBER                                  # 口縁の光る点
        return None
    return fn


def _collar_lit(i):
    base, glow = _collar(i), _collar_glow(i)

    def fn(u, v, x, y):
        g = glow(u, v, x, y)
        return g if g is not None else base(u, v, x, y)
    return fn


def _gullet(i):
    """喉の内壁。V = 口縁(0)→奥(1)。縦の襞、奥ほど明るく赤い"""
    def fn(u, v, x, y):
        V = (i + u) / 4.0
        t = 0.15 + 0.6 * V + 0.05 * math.sin(V * 40) + 0.1 * hashf(x // 2, y // 2, 60 + i)
        if abs(v - 0.5) < 0.08:
            t += 0.12                                     # 襞の稜
        if abs(v - 0.5) > 0.42:
            t -= 0.15
        if V > 0.85:
            return THROAT if hashf(x, y, 61) > 0.25 else INNER[1]
        return ramp(INNER, t, x, y)
    return fn


def _gullet_glow(i):
    def fn(u, v, x, y):
        V = (i + u) / 4.0
        if V > 0.85 and hashf(x, y, 61) > 0.25:
            return THROAT
        return None
    return fn


def _mandible(i, outer):
    """大顎の板。V = 根元(0)→先(1)。外側は背の稜が明るく、先へ暗く硬くなる。成長の縞"""
    def fn(u, v, x, y):
        V = (i + u) / 4.0
        if outer:
            t = 0.30 + 0.45 * (1.0 - V) + 0.2 * _mottle(x, y, 50 + i, 2)
            if (V * 14.0) % 1.0 < 0.12:
                t -= 0.22                                 # 成長の横縞
        else:
            t = 0.12 + 0.25 * (1.0 - V) + 0.12 * _mottle(x, y, 54 + i, 2)
        if V > 0.85:
            t -= 0.15                                     # 先は黒く硬い
        return ramp(PLATE, t, x, y, 0.05)
    return fn


def _ridge(u, v, x, y):
    t = 0.45 + 0.3 * (1.0 - abs(v - 0.5) * 2.0) - 0.15 * u
    if (x * 3 + y * 5) % 23 == 0:
        t = 0.1
    return ramp(PLATE, t, x, y, 0.05)


# ==================== 歯・棘・剛毛・眼 ====================
def _tooth(u, v, x, y):
    return ramp(TOOTH_P, 0.25 + 0.7 * (1.0 - v) - 0.3 * abs(u - 0.5), x, y, 0.03)


def _horn(u, v, x, y):
    t = 0.12 + 0.85 * (1.0 - v) - 0.3 * abs(u - 0.5)
    if y % 6 == 0 and v > 0.2:
        t -= 0.15                                         # 成長輪
    return ramp(HORN_P, t, x, y, 0.04)


def _bristle(u, v, x, y):
    t = 0.2 + 0.85 * (1.0 - v) - 0.25 * abs(u - 0.5)
    return ramp(HORN_P, t, x, y, 0.04)


def _lobe(u, v, x, y):
    t = 0.15 + 0.55 * (1.0 - v) + 0.1 * hashf(x // 2, y // 2, 70)
    if abs(u - 0.5) > 0.42:
        t -= 0.1
    return ramp(FLESH, t, x, y)


def _eye(u, v, x, y):
    dx, dy = u - 0.5, v - 0.5
    d = math.hypot(dx, dy)
    if d > 0.47:
        return PUPIL
    if abs(dx) < 0.07 + 0.05 * (0.5 - abs(dy)):
        return PUPIL                                      # 縦に細い瞳
    return ramp(IRIS, 1.0 - d * 1.7, x, y, 0.04)


def _eye_glow(u, v, x, y):
    c = _eye(u, v, x, y)
    return None if c == PUPIL else c


def _cavity(u, v, x, y):
    return VOID if hashf(x, y, 3) > 0.15 else PLATE[0]


def _draw():
    s = Sheet()
    for region, seed in ((RING_T, 1), (RING_T2, 2)):
        s.fill(region, _ring(0.07, seed))
    for region, seed in ((RING_U, 3), (RING_U2, 4)):
        s.fill(region, _ring(0.03, seed))
    for region, seed in ((RING_L, 5), (RING_L2, 6)):
        s.fill(region, _ring_lit(seed), _ring_glow(seed))
    for region, seed in ((RING_B, 7), (RING_B2, 8)):
        s.fill(region, _belly(seed))
    for i in range(4):
        s.fill(COLLAR[i], _collar_lit(i), _collar_glow(i))
        s.fill(GULLET[i], _gullet(i), _gullet_glow(i))
        s.fill(MAND_OUT[i], _mandible(i, True))
        s.fill(MAND_IN[i], _mandible(i, False))
    s.fill(RIDGE, _ridge)
    s.fill(TOOTH, _tooth)
    s.fill(HORN, _horn)
    s.fill(BRISTLE, _bristle)
    s.fill(LOBE, _lobe)
    s.fill(EYE, _eye, _eye_glow)
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
