# -*- coding: utf-8 -*-
"""海溝の大ワーム（trench_wyrm）の GeckoLib モデル一式を書き出す。

```bash
python tools/boss/wyrm_model/build.py
```

書き出すもの（どれも丸ごと上書き。手で直さず、ここを直して流し直す）:

- `assets/abyssia/geo/entity/trench_wyrm.geo.json`
- `assets/abyssia/animations/entity/trench_wyrm.animation.json`（idle だけ）
- `assets/abyssia/textures/entity/boss/trench_wyrm_geo.png` と `_glow.png`
- `tools/boss/wyrm_model/contract.json`（描画の Java 側との約束）

<h2>骨の約束（Java の描画が鎖の形に曲げる）</h2>
大海蛇（`tools/boss/serpent_model`）と同じ。`head` と `seg_01`〜`seg_26` は<b>すべて root 直下</b>。
静止姿勢では頭から尾まで一直線で、関節 j は (0, 0, SEG * j)。頭は -Z を向き、関節 0 に pivot を置く。
体節 k は関節 k-1（前）から関節 k（後ろ）までで、pivot は体節の中心 (0, 0, SEG * (k - 0.5))。
うねりは Java が入れるので、animation には体の曲げを入れない。

<h2>環は入れ子の甲殻</h2>
1 関節に甲殻の環が 2 つ（旧描画の「2 rings per joint」）。環は前が細く（BAND_IN）後ろが太い
（BAND_OUT）筒で、後ろ端（袖）が次の環の前端（差し込み）へ被さる。だから環の境ごとに段が付き、
それが節の意匠になる。関節をまたぐ環の袖は、曲げても隙間の奥に次の環の皮しか見えない長さにしてある。

<h2>寸法</h2>
実寸は TrenchWyrm.java（SIZE=3、頭＋26 関節、関節間隔 12 ブロック、bodyRadius = 2.75 * SIZE）と、
旧描画 TrenchWyrmRenderer.java（断面の比、bodyScale、襟・喉・大顎の loft 表、歯の輪、剛毛と棘）から取った。
モデルは MODEL_SCALE 分の 1 に縮めて作り、描画側で一様に MODEL_SCALE 倍する。
"""
import json
import math
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import atlas
import boss_kit
from atlas import (RING_T, RING_T2, RING_U, RING_U2, RING_L, RING_L2, RING_B, RING_B2, COLLAR, GULLET,
                   MAND_OUT, MAND_IN, EYE, CAVITY, TOOTH, HORN, BRISTLE, LOBE, RIDGE)
from boss_kit import (_add as vadd, _sub as vsub, _mul as vmul, _unit as vunit, _cross as vcross,
                      _dot as vdot, _mid as vmid, _dist as vdist, rot, EASE_SOFT)

ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/abyssia'
GEO = ASSETS / 'geo/entity'
ANIM = ASSETS / 'animations/entity'
TEX = ASSETS / 'textures/entity/boss'
NAME = 'trench_wyrm'

K = boss_kit.Kit(atlas, stretched=atlas.STRETCHED, top_anchored=atlas.TOP_ANCHORED)

# ============================================================ 実寸
MODEL_SCALE = 3.0           # 描画側で一様に掛ける倍率（TrenchWyrm.SIZE）。geo の 1 単位 = MODEL_SCALE / 16 ブロック
JOINTS = 26                 # 頭の後ろの関節の数（TrenchWyrm: 1 + 26）
JOINT_SPACING = 12.0        # ブロック（TrenchWyrm のコンストラクタ）
BODY_RADIUS = 8.25          # ブロック（TrenchWyrm.bodyRadius() = 2.75 * SIZE）
U = 16.0 / MODEL_SCALE      # 1 ブロックが何単位か（5.33）
SEG = JOINT_SPACING * U     # 体節の長さ（64 単位）
R0 = BODY_RADIUS * U        # 首の半径（44 単位）
HU = BODY_RADIUS / 8.0 * U  # 旧描画の頭の 1 単位（半径 8 で作り r/8 倍）が何単位か（5.5）

# 断面（旧描画の tube と同じ比）: 幅は半径の 1.05、背は 0.95、腹は 0.85、指数 0.95（わずかに角張る）
BODY_W, BODY_UP, BODY_DOWN = 1.05, 0.95, 0.85
BODY_E = 2.0 / 0.95         # ring() の指数（2 で楕円）。旧描画の pow(|cos|, 0.95) と同じ形
BAND_IN, BAND_OUT = 0.76, 1.0   # 環の前端（差し込み）と後ろ端（袖）の太さ
TUCK = 13.0                 # 環の前端が継ぎ目より前へ潜る長さ（単位）
LIP = 13.0                  # 環の袖が継ぎ目より後ろへ被さる長さ（単位）
BAND_LEN = SEG / 2.0 + TUCK + LIP
BRISTLE_A = 31.5            # 剛毛が水平から下がる角（度）。旧描画の a = 0.55 rad

SIDES = ((1, "l"), (-1, "r"))   # +X が左


def body_scale(k, n=JOINTS + 1):
    """関節座標 k での断面の倍率。TrenchWyrmRenderer.bodyScale と同じ式"""
    if k <= 1:
        return 1.0
    s = 1.0 - 0.4 * (k - 1) / max(1, n - 2)
    t = k - (n - 4)
    if t > 0:
        s *= max(0.05, 1.0 - 0.31 * t)
    return s


def radius(k):
    return R0 * body_scale(k)


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


# ============================================================ 断面と外皮
def ring(z, a, up, down, t0, t1, n, e=2.0, cy=0.0):
    """超楕円の縁の点を n+1 個。t は真上が 0、+X 側が正（serpent_model と同じ取り方）"""
    pts = []
    for i in range(n + 1):
        t = math.radians(t0 + (t1 - t0) * i / n)
        s, c = math.sin(t), math.cos(t)
        x = a * math.copysign(abs(s) ** (2.0 / e), s)
        b = up if c >= 0 else down
        y = cy + b * math.copysign(abs(c) ** (2.0 / e), c)
        pts.append((x, y, z))
    return pts


def plates(r0, r1, c0, c1, picks, key, thick, lap=1.12, overlap=1.04, sink=0.0, behind=False):
    """前後2つの断面の縁どうしを板で結ぶ。r0 が前（頭側・根元側）、c0 / c1 は断面の芯。

    serpent_model の ring_loft と同じ置き方だが、板の向きを変えてある: 局所 X が<b>前から後ろへ</b>
    （絵の横は左が前、右が後ろ）、局所 Y が芯から外への法線、局所 Z が周方向。こうすると前後に
    向きのある絵（環の前縁の稜、襟の口縁の光る点）がどの板でも同じ向きに出る。代わりに周方向
    （絵の縦）が左右の板で裏返るので、絵は上下対称に描く（atlas.py）。

    picks[i] は板 i の (領域, tone) か None（張らない）。behind=True で板を面の外側へ置く（喉の
    内壁のように芯の側から見る殻）。sink は板を面から沈める深さ（重なりのちらつき避け）。
    """
    out = []
    axis = vunit(vsub(c1, c0)) if vdist(c0, c1) > 1e-6 else (0.0, 0.0, 1.0)
    for i in range(len(r0) - 1):
        picked = picks[i]
        if picked is None:
            continue
        a0, b0, a1, b1 = r0[i], r0[i + 1], r1[i], r1[i + 1]
        m0, m1 = vmid(a0, b0), vmid(a1, b1)
        along = vsub(m1, m0)
        length = math.sqrt(vdot(along, along))
        along = vunit(along)
        across = vadd(vsub(b0, a0), vsub(b1, a1))
        across = vunit(vsub(across, vmul(along, vdot(along, across))))
        normal = vcross(across, along)
        centre = vmid(m0, m1)
        rel = vsub(centre, c0)
        radial = vsub(rel, vmul(axis, vdot(axis, rel)))
        if vdot(normal, radial) < 0:
            normal = vmul(normal, -1)
        reg, tone = picked
        width = max(vdist(a0, b0), vdist(a1, b1)) * lap
        off = thick / 2.0 + sink
        c = vadd(centre, vmul(normal, off)) if behind else vsub(centre, vmul(normal, off))
        out.append(K.slab(c, (length * overlap, thick, width), along, normal, reg, tone=tone, key=f"{key}{i}"))
    return out


def bezier(p0, p1, p2, s):
    return tuple((1 - s) ** 2 * p0[i] + 2 * (1 - s) * s * p1[i] + s * s * p2[i] for i in range(3))


def bezier_d(p0, p1, p2, s):
    return tuple(2 * (1 - s) * (p1[i] - p0[i]) + 2 * s * (p2[i] - p1[i]) for i in range(3))


def strip_part(region, c0, c1):
    """縦長の領域（上端が先）のうち、棒の c0..c1（0 が根元）に当たる帯。どの面にも帯まるごとを貼る。
    数本に分けた棒の各片へ、根元から先への色の流れを途切れずに配るため"""
    x, y, w, h = region
    top = y + int(round(h * (1.0 - c1)))
    bottom = y + int(round(h * (1.0 - c0)))
    part = (x, top, w, max(1, bottom - top))
    K.stretched.add(part)
    return part


def taper_beam(p0, p1, widths, cuts, region, key, side=(1, 1, 0), overshoot=0.8):
    """p0 から p1 へ、先ほど細い数本の棒（棘・針・歯）。cuts は区切りの位置（0..1）"""
    out = []
    d = vsub(p1, p0)
    for i in range(len(cuts) - 1):
        a, b = vadd(p0, vmul(d, cuts[i])), vadd(p0, vmul(d, cuts[i + 1]))
        out.append(K.beam(a, b, widths[i], widths[i], strip_part(region, cuts[i], cuts[i + 1]), side=side,
                          key=f"{key}{i}", overshoot=overshoot if i else 0.0))
    return out


# ============================================================ 体節
def _abs_angle(t):
    return abs(((t + 180.0) % 360.0) - 180.0)


def band_regions(t, variant):
    at = _abs_angle(t)
    if at < 20:
        return RING_T2 if variant else RING_T
    if at < 65:
        return RING_U2 if variant else RING_U
    if at < 116:
        return RING_L2 if variant else RING_L
    return RING_B2 if variant else RING_B


def band(k, j):
    """体節 k の環 j（0 が前、1 が後ろ）の寸法。z は geo の値（前が小さい）"""
    seam = SEG * (k - 1) + j * SEG / 2.0
    z0, z1 = seam - TUCK, seam + SEG / 2.0 + LIP
    return {"z0": z0, "z1": z1, "r0": BAND_IN * radius(z0 / SEG), "r1": BAND_OUT * radius(z1 / SEG)}


def band_r(b, z):
    """環の z での太さ（半径）"""
    return b["r0"] + (b["r1"] - b["r0"]) * clamp((z - b["z0"]) / (b["z1"] - b["z0"]), 0.0, 1.0)


def section(r, n):
    step = 360.0 / n
    return dict(a=BODY_W * r, up=BODY_UP * r, down=BODY_DOWN * r, t0=-180 - step / 2, t1=180 - step / 2)


def body_point(b, z, t, f=1.0):
    """環 b の外面で、z と角 t（真上が 0、+X 側が正）の点"""
    r = band_r(b, z) * f
    s, c = math.sin(math.radians(t)), math.cos(math.radians(t))
    x = BODY_W * r * math.copysign(abs(s) ** (2.0 / BODY_E), s)
    y = (BODY_UP if c >= 0 else BODY_DOWN) * r * math.copysign(abs(c) ** (2.0 / BODY_E), c)
    return (x, y, z)


def sides_for(r):
    return 14 if r >= 16.0 else 12


def band_skin(k, j):
    b = band(k, j)
    n = sides_for(b["r0"] / BAND_IN)
    step = 360.0 / n
    angles = [-180.0 + step * i for i in range(n)]
    r0 = ring(b["z0"], **_sec_args(b["r0"], n))
    r1 = ring(b["z1"], **_sec_args(b["r1"], n))
    picks = [(band_regions(angles[i], (i + j + k) % 2), None) for i in range(n)]
    thick = max(1.6, 0.11 * b["r1"])
    return plates(r0, r1, (0.0, 0.0, b["z0"]), (0.0, 0.0, b["z1"]), picks, f"s{k}b{j}_", thick), b


def _sec_args(r, n):
    s = section(r, n)
    return dict(a=s["a"], up=s["up"], down=s["down"], t0=s["t0"], t1=s["t1"], n=n, e=BODY_E)


def bristle(b, s, key, scale):
    """脇腹の剛毛の房（旧描画の bristle）。付け根の肉の房（疣足）から細い剛毛が2本、外下へ開く"""
    z = b["z0"] + 0.70 * (b["z1"] - b["z0"])
    base = body_point(b, z, s * 107.0, 0.97)
    a = math.radians(BRISTLE_A)
    d = vunit((s * math.cos(a), -math.sin(a), 0.0))
    out = [K.beam(vsub(base, vmul(d, 3.0 * scale)), vadd(base, vmul(d, 10.0 * scale)), 11.0 * scale, 7.0 * scale,
                  LOBE, side=(0, 0, 1), key=f"{key}lobe")]
    length = 0.75 * scale * R0
    for i, dz in enumerate((-0.2, 0.2)):
        di = vunit(vadd(d, (0.0, 0.0, dz)))
        p0 = vadd(base, vmul(d, 6.0 * scale))
        out.append(K.beam(p0, vadd(p0, vmul(di, length)), 3.2 * scale, 3.2 * scale, BRISTLE,
                          side=(1, 1, 0), key=f"{key}n{i}"))
    return out, base


def spine(b, key, scale):
    """環の頂の棘（旧描画の背の spike）。後ろへ傾く"""
    z = b["z0"] + 0.66 * (b["z1"] - b["z0"])
    base = body_point(b, z, 0.0, 0.93)
    d = vunit((0.0, 0.45, 0.3))
    length = 0.54 * scale * R0
    return taper_beam(base, vadd(base, vmul(d, length + 3.0)), (0.20 * scale * R0, 0.09 * scale * R0),
                      (0.0, 0.48, 1.0), HORN, key)


def segment(k, bones):
    cubes = []
    fringe = {"l": [], "r": []}
    pivots = {}
    for j in (0, 1):
        skin, b = band_skin(k, j)
        cubes += skin
        scale = band_r(b, b["z0"] + 0.7 * (b["z1"] - b["z0"])) / R0
        if scale * R0 > 4.0:
            cubes += spine(b, f"s{k}sp{j}_", scale)
        if scale * R0 > 6.0:
            for s, side in SIDES:
                bc, base = bristle(b, s, f"s{k}{side}{j}_", scale)
                fringe[side] += bc
                pivots.setdefault(side, []).append(base)
    if k == JOINTS:
        # 尾の先の針（旧描画の tail needle: 関節から 1.9r 先まで）。最後の環の中から生やして細りへ繋ぐ
        zb = SEG * k
        cubes += taper_beam((0.0, 0.0, zb - 0.6 * SEG), (0.0, 0.0, zb + 1.9 * R0), (8.5, 6.0, 3.6, 2.0),
                            (0.0, 0.3, 0.6, 0.82, 1.0), HORN, f"s{k}tip")
    name = f"seg_{k:02d}"
    zc = SEG * (k - 0.5)
    K.bone(bones, name, (0.0, 0.0, zc), cubes)
    for s, side in SIDES:
        if fringe[side]:
            ps = pivots[side]
            # pivot は2つの房の付け根を結ぶ線の上（体節の中心の z）。z まわりに上げ下げしても付け根が動かない
            pivot = (sum(p[0] for p in ps) / len(ps), sum(p[1] for p in ps) / len(ps), zc)
            K.bone(bones, f"{name}_bristle_{side}", pivot, fringe[side], parent=name)


# ============================================================ 頭
# 襟（z, 半幅, 背, 腹）。z は geo（-Z が前）。関節 0 が原点。旧描画の COLLAR（半径 8 で作った表）を
# HU 倍・前後反転し、後ろ端を首の差し込みへ被さる袖にした（曲げても首が襟を突き抜けない長さに詰めた）
COLLAR_T = (
    (-50.0, 40.0, 40.0, 40.0),    # 口縁（旧 z=9, 7.2）
    (-38.0, 49.0, 49.0, 49.0),    # 旧 z=7, 8.9
    (-17.0, 52.0, 52.0, 51.0),    # いちばん太い（旧 z=3, 9.4）
    (2.0, 51.0, 48.5, 46.0),
    (16.0, 48.5, 44.5, 40.5),     # 首へ被さる袖（体の断面の比へ寄せる）
)
COLLAR_SIDES = 16
# 喉（z, 半径）。旧描画の GULLET。口縁は襟の板の内側へ収まるよう少し細く
GULLET_T = (
    (-49.0, 36.5),
    (-33.0, 31.5),
    (0.0, 22.0),
    (44.0, 11.5),
    (77.0, 2.0),
)
GULLET_SIDES = 12
# 歯の輪（数, z, 内へ伸びる長さ, 前後（+ が前）, 太さ, 位相）。旧描画の ring() を HU 倍
TEETH = (
    (16, -7.5 * HU, 2.7 * HU, 1.4 * HU, 0.55 * HU, 0.0),
    (12, -3.0 * HU, 2.3 * HU, -0.6 * HU, 0.50 * HU, math.degrees(0.26)),
    (8, 3.0 * HU, 1.8 * HU, -0.8 * HU, 0.45 * HU, 0.0),
)
# 大顎: 蝶番は口縁の後ろ（旧 z=6）で芯から 8.3。正準形（真下、外が -Y）で作り、bone の rz で4隅へ回す
HINGE_R = 8.3 * HU
HINGE_Z = -6.0 * HU
MANDIBLE_ROT = (-135.0, 135.0, 45.0, -45.0)     # mandible_1..4: 左上 / 右上 / 右下 / 左下
# 大顎の背骨（蝶番からの相対、正準形。+Y が内＝芯の側、-Z が前）。閉じた姿で先が芯の手前で揃う鉤
MAND_P1 = (0.0, 7.0, -62.0)
MAND_P2 = (0.0, 38.0, -90.0)
# 断面（背骨の位置 s, 半幅, 外側の厚み, 内側の厚み）。旧描画の MANDIBLE 表を HU 倍
MAND_T = (
    (0.0, 3.2 * HU, 1.6 * HU, 1.2 * HU),
    (0.32, 3.0 * HU, 1.5 * HU, 1.0 * HU),
    (0.64, 1.9 * HU, 1.1 * HU, 0.8 * HU),
    (0.90, 0.6 * HU, 0.5 * HU, 0.4 * HU),
    (1.0, 0.1 * HU, 0.1 * HU, 0.1 * HU),
)
MAND_E = 2.4
MAND_OPEN_MAX = math.degrees(0.45 + 0.8)       # 旧描画の JAW_CLOSED - JAW_OPEN（全開で 72 度）


def interp(table, z, col):
    """z の昇順に並んだ表を z で線形補間する"""
    if z <= table[0][0]:
        return table[0][col]
    if z >= table[-1][0]:
        return table[-1][col]
    for a, b in zip(table, table[1:]):
        if a[0] <= z <= b[0]:
            f = (z - a[0]) / (b[0] - a[0])
            return a[col] + (b[col] - a[col]) * f
    return table[-1][col]


def collar_ring(st):
    z, hw, up, down = st
    step = 360.0 / COLLAR_SIDES
    return ring(z, hw, up, down, -180 - step / 2, 180 - step / 2, COLLAR_SIDES, 2.0)


def collar_apothem(z, t):
    """襟の板の外面（板の中心）までの距離。t の方向（真上が 0）"""
    hw, up, down = (interp(COLLAR_T, z, c) for c in (1, 2, 3))
    s, c = math.sin(math.radians(t)), math.cos(math.radians(t))
    r = math.hypot(hw * s, (up if c >= 0 else down) * c)
    return r * math.cos(math.pi / COLLAR_SIDES)


def skull():
    """襟（眼のない頭）、喉、襟の眼、大顎の付け根の稜"""
    cubes = []
    for i in range(len(COLLAR_T) - 1):
        r0, r1 = collar_ring(COLLAR_T[i]), collar_ring(COLLAR_T[i + 1])
        picks = [(COLLAR[i], None)] * COLLAR_SIDES
        cubes += plates(r0, r1, (0.0, 0.0, COLLAR_T[i][0]), (0.0, 0.0, COLLAR_T[i + 1][0]), picks,
                        f"col{i}_", 3.4, sink=0.3 * (i % 2))
    step = 360.0 / GULLET_SIDES
    for i in range(len(GULLET_T) - 1):
        (za, ra), (zb, rb) = GULLET_T[i], GULLET_T[i + 1]
        r0 = ring(za, ra, ra, ra, -180 - step / 2, 180 - step / 2, GULLET_SIDES)
        r1 = ring(zb, rb, rb, rb, -180 - step / 2, 180 - step / 2, GULLET_SIDES)
        picks = [(GULLET[i], None)] * GULLET_SIDES
        cubes += plates(r0, r1, (0.0, 0.0, za), (0.0, 0.0, zb), picks, f"gul{i}_", 3.0, behind=True)
    # 襟の眼（旧描画の ember eyes）: 大顎のあいだ、真上・左・真下・右
    for j, t in enumerate((0.0, 90.0, 180.0, 270.0)):
        n = (math.sin(math.radians(t)), math.cos(math.radians(t)), 0.0)
        z = -16.5
        r = collar_apothem(z, t)
        # 局所 X を前後、局所 Z を周方向へ。縦に細い瞳が周方向（横から見て縦）に立つ
        cubes.append(K.slab(vadd(vmul(n, r + 0.1), (0, 0, z)), (17.0, 1.6, 15.0), (0, 0, 1), n, CAVITY,
                            key=f"socket{j}"))
        cubes.append(K.slab(vadd(vmul(n, r + 0.5), (0, 0, z)), (14.0, 1.6, 12.0), (0, 0, 1), n, EYE, key=f"eye{j}"))
    # 大顎の付け根の稜: 蝶番から襟に沿って後ろへ流れる骨
    for j, rz in enumerate(MANDIBLE_ROT):
        t = rz + 180.0                                    # 大顎の外向き（真上が 0、+X 側が正。hinge_of と同じ）
        n = (math.sin(math.radians(t)), math.cos(math.radians(t)), 0.0)
        pa = vadd(vmul(n, collar_apothem(-36.0, t) + 1.5), (0, 0, -36.0))
        pb = vadd(vmul(n, collar_apothem(-17.0, t) + 2.5), (0, 0, -17.0))
        pc = vadd(vmul(n, collar_apothem(4.0, t) + 1.0), (0, 0, 4.0))
        cubes.append(K.beam(pa, pb, 13.0, 6.0, RIDGE, side=vcross(n, (0, 0, 1)), key=f"ridge{j}a"))
        cubes.append(K.beam(pb, pc, 10.0, 5.0, RIDGE, side=vcross(n, (0, 0, 1)), key=f"ridge{j}b", overshoot=1.0))
    return cubes


def gullet_r(z):
    return interp(GULLET_T, z, 1)


def teeth():
    """喉の3重の歯の輪。根元は喉の壁、先は芯へ（1 列目は口の外へ、奥の2列は喉の奥へ）傾く"""
    cubes = []
    for ri, (count, z, inward, fwd, thick, phase) in enumerate(TEETH):
        for i in range(count):
            a = math.radians(phase + 360.0 * i / count)
            c, s = math.cos(a), math.sin(a)
            base = (c * (gullet_r(z) + 1.5), s * (gullet_r(z) + 1.5), z)
            tip = (c * (gullet_r(z) - inward), s * (gullet_r(z) - inward), z - fwd)
            side = (-s, c, 0.0)
            if ri == 0:
                cubes += taper_beam(base, tip, (2.0 * thick, 1.1 * thick), (0.0, 0.55, 1.0), TOOTH,
                                    f"t{ri}_{i}_", side=side)
            else:
                cubes.append(K.beam(base, tip, 1.8 * thick, 1.8 * thick, TOOTH, side=side, key=f"t{ri}_{i}"))
    return cubes


def mandible_canonical():
    """真下にある大顎（外が -Y、内が +Y、前が -Z）。蝶番 (0, -HINGE_R, HINGE_Z) からの鉤"""
    h = (0.0, -HINGE_R, HINGE_Z)
    p0, p1, p2 = h, vadd(h, MAND_P1), vadd(h, MAND_P2)
    frames = []
    for s, hw, up, down in MAND_T:
        c = bezier(p0, p1, p2, s)
        t = vunit(bezier_d(p0, p1, p2, s))
        out = vunit(vsub((0.0, -1.0, 0.0), vmul(t, -t[1])))      # 外向き（-Y を背骨に直交させた向き）
        side = vunit(vcross(t, out))
        frames.append((c, t, out, side, hw, up, down))
    angles = [-180.0 + 45.0 * i for i in range(9)]      # 縁の点。0 が外の稜、180 が内の稜、±90 が刃の縁
    rings = []
    for c, t, out, side, hw, up, down in frames:
        pts = []
        for a in angles:
            sn, cs = math.sin(math.radians(a)), math.cos(math.radians(a))
            x = hw * math.copysign(abs(sn) ** (2.0 / MAND_E), sn)
            y = (up if cs >= 0 else down) * math.copysign(abs(cs) ** (2.0 / MAND_E), cs)
            pts.append(vadd(c, vadd(vmul(side, x), vmul(out, y))))
        rings.append(pts)
    cubes = []
    # 板の中心角は -157.5 .. 157.5（45 度おき）。外の4枚は外側の絵、内の4枚は内側の絵
    for i in range(len(rings) - 1):
        picks = []
        for q in range(8):
            a = -157.5 + 45.0 * q
            picks.append((MAND_OUT[i] if abs(a) < 90 else MAND_IN[i], None))
        cubes += plates(rings[i], rings[i + 1], frames[i][0], frames[i + 1][0], picks, f"md{i}_",
                        2.4 if i < 2 else 1.8)
    # 内側の歯（旧描画は1列4本。2列に並べて鋸刃にする）
    for j, s in enumerate((0.18, 0.36, 0.54, 0.72)):
        c = bezier(p0, p1, p2, s)
        t = vunit(bezier_d(p0, p1, p2, s))
        out = vunit(vsub((0.0, -1.0, 0.0), vmul(t, -t[1])))
        side = vunit(vcross(t, out))
        hw = interp(MAND_T, s, 1)
        down = interp(MAND_T, s, 3)
        length = (10.5 - 1.2 * j) * 1.0
        for q, f in enumerate((-0.42, 0.42)):
            base = vadd(vadd(c, vmul(out, -0.6 * down)), vmul(side, f * hw))
            d = vunit(vadd(vmul(out, -1.0), vmul(t, 0.3)))
            cubes.append(K.beam(base, vadd(base, vmul(d, length + 0.6 * down)), 4.0, 3.2, TOOTH,
                                side=side, key=f"mt{j}{q}"))
    return cubes, h


def place_canonical(cubes, src, dst):
    """正準形の箱を src → dst へ平行移動する（箱の向き・UV はそのまま）"""
    d = vsub(dst, src)
    out = []
    for c in cubes:
        c = json.loads(json.dumps(c))
        c["origin"] = [round(c["origin"][i] + d[i], 2) for i in range(3)]
        if "pivot" in c:
            c["pivot"] = [round(c["pivot"][i] + d[i], 2) for i in range(3)]
        out.append(c)
    return out


def hinge_of(rz):
    """bone の rz で正準形の蝶番 (0, -HINGE_R) が回った先。rz > 0 で +Y が +X 側へ倒れる"""
    r = math.radians(rz)
    return (-HINGE_R * math.sin(r), -HINGE_R * math.cos(r), HINGE_Z)


def head(bones):
    K.bone(bones, "head", (0.0, 0.0, 0.0), skull())
    K.bone(bones, "teeth", (0.0, 0.0, 0.0), teeth(), parent="head")
    canon, h = mandible_canonical()
    for m, rz in enumerate(MANDIBLE_ROT, start=1):
        p = hinge_of(rz)
        K.bone(bones, f"mandible_{m}", p, place_canonical(canon, h, p), parent="head", rot=(0.0, 0.0, rz))


# ============================================================ アニメーション
IDLE = 4.0
BRISTLE_LIFT = 16.0         # 剛毛の上げ下げ（度）。旧描画の a = 0.55 ± 0.35 rad を少し控えめに
BRISTLE_SWEEP = 7.0         # 剛毛の前後の掻き（度）
BRISTLE_LAG = 0.9           # 関節ごとの位相の遅れ（rad）。旧描画の k * 0.9
MANDIBLE_IDLE = 9.0         # idle で大顎が開く角（度）


def animations(bones):
    names = {b["name"] for b in bones}
    idle = {}
    for m in range(1, 5):
        # 大顎: ゆっくり少し開いて閉じる（rx が正で開く）。4本を少しずつずらす
        peak = 1.7 + 0.15 * m
        idle[f"mandible_{m}"] = rot([(0.0, (0, 0, 0)), (peak, (MANDIBLE_IDLE, 0, 0), EASE_SOFT),
                                     (peak + 0.4, (MANDIBLE_IDLE * 0.9, 0, 0)),
                                     (IDLE, (0, 0, 0), EASE_SOFT)])
    steps = 16
    for k in range(1, JOINTS + 1):
        for s, side in SIDES:
            name = f"seg_{k:02d}_bristle_{side}"
            if name not in names:
                continue
            frames = []
            for i in range(steps + 1):
                th = 2.0 * math.pi * 2.0 * i / steps - BRISTLE_LAG * k     # 4 秒に 2 回（旧描画の周期 約 2 秒）
                # 左は rz が正で先が下がる、ry が正で先が前へ。右は鏡写しで符号が逆
                frames.append((round(IDLE * i / steps, 2),
                               (0.0, s * BRISTLE_SWEEP * math.cos(th), s * BRISTLE_LIFT * math.sin(th))))
            idle[name] = rot(frames)
    return _no_negative_zero({"idle": {"loop": True, "animation_length": IDLE, "bones": idle}})


def _no_negative_zero(d):
    if isinstance(d, dict):
        return {k: _no_negative_zero(v) for k, v in d.items()}
    if isinstance(d, list):
        return [_no_negative_zero(v) for v in d]
    if isinstance(d, float) and d == 0.0:
        return 0.0
    return d


# ============================================================ 書き出し
def build_geo():
    bones = []
    head(bones)
    for k in range(1, JOINTS + 1):
        segment(k, bones)
    length = (JOINTS * SEG + 130.0 + 1.9 * R0) / 16.0
    return K.model(f"geometry.{NAME}", bones, round(length + 8.0, 1), round(4 * R0 / 16.0, 1), 0), bones


def contract(bones, cubes, anims):
    def entry(b):
        e = {"name": b["name"], "parent": b.get("parent"), "pivot": b["pivot"]}
        if b.get("rotation"):
            e["rest_rotation"] = b["rotation"]
        if b["name"] == "head":
            e["role"] = "頭（襟と喉）。関節 0 に pivot。-Z が口"
        elif b["name"].startswith("seg_") and not b.get("parent"):
            k = int(b["name"][4:])
            e["role"] = f"体節 {k}: 関節 {k - 1}（前）〜関節 {k}（後ろ）。pivot は中心"
            e["joint_front"], e["joint_back"] = k - 1, k
        elif b["name"].startswith("mandible_"):
            e["role"] = "大顎。pivot は蝶番。静止姿勢（rotation x = 0）は閉じた姿。rest_rotation の z は変えない"
        return e
    return {
        "entity": NAME,
        "generator": "tools/boss/wyrm_model/build.py（手で直さず、生成元を直して流し直す）",
        "geo": f"assets/abyssia/geo/entity/{NAME}.geo.json",
        "animation": f"assets/abyssia/animations/entity/{NAME}.animation.json",
        "texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo.png",
        "glow_texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png",
        "glow_note": "光らない画素は (0,0,0,0)。RenderType.eyes（加算）でも GeckoLib の自動発光層でも使える",
        "texture_size": [atlas.SIZE, atlas.SIZE],
        "model_scale": MODEL_SCALE,
        "units": "geo の 1 単位 = 1/16 ブロック。描画で MODEL_SCALE 倍するので、ゲーム内では MODEL_SCALE/16 ブロック",
        "segment_length_units": SEG,
        "segment_length_blocks": JOINT_SPACING,
        "body_radius_units": R0,
        "joints": JOINTS + 1,
        "segments": JOINTS,
        "rest_pose": ("頭から尾まで一直線。関節 j は geo の (0, 0, segment_length_units * j)。"
                      "頭は -Z を向く。体の芯は y = 0"),
        "head_pivot": [0.0, 0.0, 0.0],
        "segment_pivot": "seg_k の pivot = (0, 0, segment_length_units * (k - 0.5))",
        "rest_head_extent_units": {"mandible_tip_z": round(HINGE_Z + MAND_P2[2], 1), "collar_rim_z": COLLAR_T[0][0],
                                   "collar_back_z": COLLAR_T[-1][0],
                                   "half_width": max(r[1] for r in COLLAR_T)},
        "rest_body_section": {"half_width": BODY_W, "up": BODY_UP, "down": BODY_DOWN,
                              "rings": f"1 関節に甲殻の環が 2 つ。環は前端 {BAND_IN}・後ろ端 {BAND_OUT} 倍の筒で、"
                                       f"後ろ端が継ぎ目を {LIP:g} 単位越えて次の環へ被さる",
                              "note": "体の半径 × この比。半径は関節 0 で body_radius_units、尾へ TrenchWyrmRenderer.bodyScale と同じ式で細る"},
        "mandibles": {
            "bones": "mandible_1 左上 / mandible_2 右上 / mandible_3 右下 / mandible_4 左下（+X が左）",
            "rest": "rotation x = 0 で閉じた姿（4 本の先が口の前で揃う。旧描画の JAW_CLOSED）",
            "open": (f"rotation x を正へ（geo の度数）で外へ開く。旧描画の全開 JAW_OPEN は x = {MAND_OPEN_MAX:.0f} 度。"
                     "4 本とも同じ値でよい（各 bone の rest_rotation z が向きを4隅へ回している）"),
            "java": ("Java から開くなら GeoBone.setRotX(-radians(開く角))。setRotZ は触らない"
                     "（触るなら初期値 rest_rotation z を保つ）。idle の値を上書きする"),
        },
        "animation_signs": {
            "mandible_1..4": f"rotation の x が正で開く（idle は 0→{MANDIBLE_IDLE:g} 度）",
            "seg_XX_bristle_l / _r": ("剛毛の房（1 体節に 2 房ずつ）。左は z が正で先が下がり y が正で先が前へ、"
                                      "右は符号が逆（鏡写し）。idle は 4 秒に 2 回、関節ごとに位相が遅れる波"),
        },
        "bone_rule": ("head と seg_01..seg_26 は root 直下。毎フレーム位置と回転を上書きする。"
                      "seg_k は関節 k-1 と k の中点へ、局所 -Z が関節 k から関節 k-1 を向くように置く。"
                      "head は関節 0 へ、-Z が関節 1 から関節 0 の向き（さらに先）を向くように置く。"
                      "子（mandible_*, teeth, seg_XX_bristle_*）は animation に任せる"),
        "geckolib_conventions": [
            "描画空間 = geo の X を反転した空間（GeckoLib 4.9.2 BakedModelFactory）。z と y はそのまま",
            "GeoBone.setPosX/Y/Z は静止姿勢からのずれで、単位は geo と同じ 1/16（MODEL_SCALE を掛ける前）。"
            "RenderUtil.translateMatrixToBone が translate(-posX/16, posY/16, posZ/16) なので、"
            "geo の座標で書けば pos = 目標の pivot - 静止姿勢の pivot（X も符号そのまま）。"
            "描画空間の座標 T で書くなら posX = -T.x - pivot.x, posY = T.y - pivot.y, posZ = T.z - pivot.z",
            "GeoBone.setRotX/Y/Z はラジアンで描画空間の値。geo / animation の度数 (rx, ry, rz) とは "
            "(-rx, -ry, +rz) の関係。頂点へは X→Y→Z の順に効く（行列は Rz・Ry・Rx）",
            "描画空間で局所 -Z（体節の前）を単位ベクトル d（関節 k から k-1 へ）に向けるには "
            "setRotX(asin(d.y))、setRotY(atan2(-d.x, -d.z))、setRotZ(0)。"
            "体節と head は静止姿勢の回転が 0 なので、この値をそのまま入れればよい",
            "子の Bone（mandible_*, seg_XX_bristle_*）は animation が回すので、Java からは触らない"
            "（触るなら animation の値を上書きしてしまう）。mandible_* は静止姿勢の回転 z を持つ"
            "（animation の値は初期値へ足される）",
        ],
        "animations": {name: {"loop": a["loop"], "length": a["animation_length"],
                              "bones": sorted(a["bones"])} for name, a in anims.items()},
        "cube_count": cubes,
        "bones": [entry(b) for b in bones],
    }


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    assert abs((TUCK + LIP) / BAND_LEN - atlas.VIS_U0) < 1e-6, "atlas.VIS_U0 が環の寸法と合わない"
    geo, bones = build_geo()
    anims = animations(bones)
    print(boss_kit.verify(NAME, geo, anims))
    cubes = sum(len(b["cubes"]) for b in bones)
    assert cubes <= 1500, f"cube が多すぎる: {cubes}"
    for d in (GEO, ANIM, TEX):
        d.mkdir(parents=True, exist_ok=True)
    (GEO / f"{NAME}.geo.json").write_text(json.dumps(geo, indent=1), encoding='utf-8')
    (ANIM / f"{NAME}.animation.json").write_text(
        json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1), encoding='utf-8')
    atlas.write_png(TEX / f"{NAME}_geo.png", atlas.build())
    atlas.write_png(TEX / f"{NAME}_geo_glow.png", atlas.build_glow())
    (HERE / 'contract.json').write_text(json.dumps(contract(bones, cubes, anims), indent=1, ensure_ascii=False),
                                        encoding='utf-8')
    per = {}
    for b in bones:
        key = "body" if b["name"].startswith("seg_") else "head"
        per[key] = per.get(key, 0) + len(b["cubes"])
    print(f"cubes: 計 {cubes}（頭 {per.get('head', 0)} / 体 {per.get('body', 0)}）、"
          f"MODEL_SCALE {MODEL_SCALE:g}、体節 {SEG:g} 単位、bones {len(bones)}")
    print("次に流すこと:")
    print("  python tools/boss/check_animations.py trench_wyrm")
    print("  python tools/boss/wyrm_model/preview.py")


if __name__ == '__main__':
    main()
