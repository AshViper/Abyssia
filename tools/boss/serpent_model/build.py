# -*- coding: utf-8 -*-
"""大海蛇（abyss_serpent）の GeckoLib モデル一式を書き出す。

```bash
python tools/boss/serpent_model/build.py
```

書き出すもの（どれも丸ごと上書き。手で直さず、ここを直して流し直す）:

- `assets/abyssia/geo/entity/abyss_serpent.geo.json`
- `assets/abyssia/animations/entity/abyss_serpent.animation.json`（idle だけ）
- `assets/abyssia/textures/entity/boss/abyss_serpent_geo.png` と `_glow.png`
- `tools/boss/serpent_model/contract.json`（描画の Java 側との約束）

<h2>骨の約束（Java の描画が鎖の形に曲げる）</h2>
`head` と `seg_01`〜`seg_28` は<b>すべて root 直下</b>。静止姿勢では頭から尾まで一直線で、
関節 j は (0, 0, SEG * j)。頭は -Z（GeckoLib の正面 = north）を向き、関節 0 に pivot を置く。
体節 k は関節 k-1（前）から関節 k（後ろ）までで、pivot は体節の中心 (0, 0, SEG * (k - 0.5))。
Java は毎フレーム、各骨の位置と回転を上書きして、体節の -Z が関節 k から関節 k-1 を向くように
置く。うねりは Java が入れるので、animation には体の曲げを入れない。

<h2>継ぎ目は袖</h2>
体節の後ろ端は一回り太い袖（`sleeve`）になって関節を越え、次の体節の前端（細い差し込み
`tuck`）へ被さる。曲げると外側の継ぎ目が開くが、袖と差し込みが前後に重なっているので
隙間の奥は次の体節の皮が見えるだけで、向こうが抜けない。

<h2>寸法</h2>
実寸は AbyssSerpent.java（頭＋28 関節、関節間隔 14 ブロック、bodyRadius 9）と、
旧描画 AbyssSerpentRenderer.java（断面の比、頭の loft 表、角・歯・鰭の位置）から取った。
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
from atlas import (BACK, FLANK, LATERAL, COLLAR_T, COLLAR_S, COLLAR_B, COLLAR_L, BELLY, PLATE, MEMBRANE,
                   RAY, HORN, TOOTH, EYE, GLOWDOT, GILL, HEAD, JAW, INNER, CAVITY)
from boss_kit import (_add as vadd, _sub as vsub, _mul as vmul, _unit as vunit, _cross as vcross,
                      _dot as vdot, _mid as vmid, _dist as vdist, rot, merge, wave, EASE_SOFT)

ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/abyssia'
GEO = ASSETS / 'geo/entity'
ANIM = ASSETS / 'animations/entity'
TEX = ASSETS / 'textures/entity/boss'
NAME = 'abyss_serpent'

K = boss_kit.Kit(atlas, stretched=atlas.STRETCHED, top_anchored=atlas.TOP_ANCHORED)

# ============================================================ 実寸
MODEL_SCALE = 4.0           # 描画側で一様に掛ける倍率。geo の 1 単位 = MODEL_SCALE / 16 ブロック
JOINTS = 28                 # 頭の後ろの関節の数（AbyssSerpent: 1 + 28）
JOINT_SPACING = 14.0        # ブロック（AbyssSerpent のコンストラクタ）
BODY_RADIUS = 9.0           # ブロック（AbyssSerpent.bodyRadius() = 2.25 * SIZE）
TAIL_JOINTS = 4             # 尾の細り（AbyssSerpentRenderer.TAIL_JOINTS）
U = 16.0 / MODEL_SCALE      # 1 ブロックが何単位か（4）
SEG = JOINT_SPACING * U     # 体節の長さ（56 単位）
R0 = BODY_RADIUS * U        # 首の半径（36 単位）

# 断面（旧描画の tube と同じ比）: 幅は半径の 0.88、背は 1.08、腹は 0.95
BODY_W, BODY_UP, BODY_DOWN = 0.88, 1.08, 0.95
BODY_E = 2.3                # 超楕円の指数。2 で楕円、大きいほど角張る
TUCK = 0.88                 # 体節の前端（前の袖の下へ差し込む部分）の太さ
TUCK_LEN = 0.32             # 差し込みが関節より前へ出る長さ（半径に対して）
SLEEVE_IN, SLEEVE_OUT = 1.02, 1.055   # 袖の前端・後端の太さ
SLEEVE_LEN = 0.30           # 袖の長さ（関節の前後それぞれ、半径に対して）
SAIL = 0.9                  # 背びれの高さの倍率（旧描画の帆に対して）

SIDES = ((1, "l"), (-1, "r"))   # +X が左


def body_scale(k, n=JOINTS + 1):
    """関節座標 k での断面の倍率。AbyssSerpentRenderer.bodyScale と同じ式"""
    if k <= 1:
        return 1.0
    s = 1.0 - 0.45 * (k - 1) / max(1, n - 2)
    t = k - (n - 1 - TAIL_JOINTS)
    if t > 0:
        s *= max(0.04, 1.0 - 0.24 * t)
    return s


def radius(k):
    return R0 * body_scale(k)


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


# ============================================================ 断面と外皮
def ring(z, a, up, down, t0, t1, n, e=2.0, cy=0.0):
    """超楕円の縁の点を n+1 個。t は真上が 0、+X 側が正（boss_kit.loft と同じ取り方）"""
    pts = []
    for i in range(n + 1):
        t = math.radians(t0 + (t1 - t0) * i / n)
        s, c = math.sin(t), math.cos(t)
        x = a * math.copysign(abs(s) ** (2.0 / e), s)
        b = up if c >= 0 else down
        y = cy + b * math.copysign(abs(c) ** (2.0 / e), c)
        pts.append((x, y, z))
    return pts


def ring_loft(r0, r1, angles, key, thick, skin, axis_y=0.0, lap=1.12, overlap=1.04, sink=0.0):
    """2つの断面の縁どうしを板で結ぶ。boss_kit.Kit.loft と同じ置き方を、超楕円の断面で行う。

    r0 が前（-Z 側）、r1 が後ろ。angles は板ごとの中心角で、skin(角, 法線) が (領域, tone) を返す。
    板の局所 Z は「面に沿って上る向き」（絵の上端）。左右で板の u は鏡写しになる。
    """
    out = []
    for i in range(len(r0) - 1):
        a0, b0, a1, b1 = r0[i], r0[i + 1], r1[i], r1[i + 1]
        m0, m1 = vmid(a0, b0), vmid(a1, b1)
        along = vsub(m1, m0)
        length = math.sqrt(vdot(along, along))
        along = vunit(along)
        across = vadd(vsub(b0, a0), vsub(b1, a1))
        across = vunit(vsub(across, vmul(along, vdot(along, across))))
        normal = vcross(across, along)
        centre = vmid(m0, m1)
        if vdot(normal, vsub(centre, (0.0, axis_y, centre[2]))) < 0:
            normal = vmul(normal, -1)
        down = across if across[1] <= 0 else vmul(across, -1)
        picked = skin(angles[i], normal)
        if picked is None:
            continue
        reg, tone = picked
        width = max(vdist(a0, b0), vdist(a1, b1)) * lap
        c = vsub(centre, vmul(normal, thick / 2.0 + sink))
        out.append(K.slab(c, (length * overlap, thick, width), vcross(down, normal), normal, reg,
                          tone=tone, key=f"{key}{i}"))
    return out


def deck(p0, p1, width, thick, region, key, tone=None):
    """p0 から p1 へ渡す水平に近い板（口蓋・顎の底・舌）。幅は X 方向"""
    d = vunit(vsub(p1, p0))
    up = vunit(vsub((0.0, 1.0, 0.0), vmul(d, d[1])))
    return K.slab(vmid(p0, p1), (width, thick, vdist(p0, p1)), vcross(up, d), up, region, tone=tone, key=key)


def bezier(p0, p1, p2, s):
    return tuple((1 - s) ** 2 * p0[i] + 2 * (1 - s) * s * p1[i] + s * s * p2[i] for i in range(3))


HORN_CUTS = (0.0, 0.22, 0.44, 0.64, 0.82, 1.0)
HORN_TAPER = (1.0, 0.84, 0.68, 0.52, 0.34)


def horn(p0, p1, p2, w0, key, region=HORN, cuts=HORN_CUTS, taper=HORN_TAPER):
    """曲がった角。2次ベジェを数本の棒に分け、先ほど細くする。棒は菱形に向ける（面取り）"""
    out = []
    for i in range(len(cuts) - 1):
        a, b = bezier(p0, p1, p2, cuts[i]), bezier(p0, p1, p2, cuts[i + 1])
        d = vunit(vsub(b, a))
        side = vadd(vunit(vcross(d, (0.0, 1.0, 0.0)) if abs(d[1]) < 0.95 else (1.0, 0.0, 0.0)), (0.0, 1.0, 0.0))
        w = w0 * taper[i]
        out.append(K.beam(a, b, w, w, region, side=side, key=f"{key}{i}", overshoot=1.0 if i else 0.0))
    return out


def fan_to(base, e1, e2, tips, key, ray_w, ray_region=RAY, web_region=MEMBRANE):
    """鰭条の先の点（tips）から角度と長さを解いて Kit.fan に渡す。e1 / e2 は鰭の面を張る向き"""
    rays = []
    for p in tips:
        d = vsub(p, base)
        rays.append((math.degrees(math.atan2(vdot(d, e2), vdot(d, e1))), math.sqrt(vdot(d, d))))
    return K.fan(base, e1, e2, rays, key, ray_region, web_region, tip_region=web_region,
                 ray_w=ray_w, lift=0.15)


# ============================================================ 体節
def body_ring(z, r, f, n):
    step = 360.0 / n
    return ring(z, BODY_W * r * f, BODY_UP * r * f, BODY_DOWN * r * f, -180 - step / 2, 180 - step / 2, n, BODY_E)


def ring_angles(n):
    step = 360.0 / n
    return [-180.0 + step * i for i in range(n)]


def _abs_angle(t):
    return abs(((t + 180.0) % 360.0) - 180.0)


def body_skin(t, _n):
    at = _abs_angle(t)
    if at < 15:
        return BACK, 0.5
    if at < 44:
        return FLANK, 0.05
    if at < 75:
        return FLANK, 0.40
    if at < 105:
        return LATERAL, 0.5
    if at < 134:
        return FLANK, 1.0
    return BELLY, None


def collar_skin(t, _n):
    at = _abs_angle(t)
    if at < 50:
        return COLLAR_T, None
    if at < 75:
        return COLLAR_S, None
    if at < 105:
        return COLLAR_L, None
    if at < 134:
        return COLLAR_S, None
    return COLLAR_B, None


def seg_frame(k):
    """体節 k の寸法。z は geo の値（前が小さい）"""
    zc = (k - 0.5) * SEG
    rf, rb = radius(k - 1), max(radius(k), 1.2)
    return {
        "zc": zc, "zf": zc - SEG / 2, "zb": zc + SEG / 2, "rf": rf, "rb": rb,
        "n": 12 if rf > 18 else 8,
        "tuck": max(3.0, TUCK_LEN * rf),
        "sleeve": SLEEVE_LEN * rb,
        "last": k == JOINTS,
    }


def body_surface(k, z):
    """体節 k の本体（差し込み→袖の手前）の、z での太さ（半径 × 倍率）"""
    f = seg_frame(k)
    z0, r0 = f["zf"] - f["tuck"], TUCK * f["rf"]
    z1 = f["zb"] if f["last"] else f["zb"] - f["sleeve"] + 2.0
    r1 = (1.0 if f["last"] else 0.99) * f["rb"]
    return r0 + (r1 - r0) * clamp((z - z0) / (z1 - z0), 0.0, 1.0)


def top_y(k, z):
    return BODY_UP * body_surface(k, z) * 0.97


def bottom_y(k, z):
    return -BODY_DOWN * body_surface(k, z) * 0.97


def sail_height(kz):
    """背びれの高さ。旧描画の帆（首の後ろで立ち上がり、波打ちながら細る）と同じ式"""
    rise = clamp((kz - 0.8) / 2.0, 0.0, 1.0)
    return SAIL * rise * (1.15 + 0.3 * math.sin(1.7 * kz)) * R0 * (0.45 + 0.55 * body_scale(kz))


def fluke_up(kz):
    """尾びれの上葉の高さ（体の背から）。旧描画の r * 1.7 * sin(π t) を、尾の先の後ろまで延ばした形"""
    t = clamp((kz - 24.0) / 4.6, 0.0, 1.0)
    return 1.7 * R0 * math.sin(math.pi * t ** 0.8)


def fluke_down(kz):
    """尾びれの下葉（腹から）。旧描画の r * 1.25 * sin(π t)"""
    t = clamp((kz - 24.4) / 4.2, 0.0, 1.0)
    return 1.25 * R0 * math.sin(math.pi * t ** 0.8)


def keel_height(kz):
    """後半身の腹の竜骨鰭。旧描画の keel（n * 0.55 から立ち上がる）"""
    return clamp((kz - (JOINTS + 1) * 0.55) / 3.0, 0.0, 1.0) * 0.35 * R0


def segment(k, bones):
    f = seg_frame(k)
    zf, zb, rf, rb, n = f["zf"], f["zb"], f["rf"], f["rb"], f["n"]
    angles = ring_angles(n)
    thick = max(1.6, 0.11 * rf)
    cubes = []
    # 本体: 前の袖の下へ差し込む細い前端から、袖の手前まで。強く細る尾は2区間に割る
    # （板は長方形なので、太さが大きく変わる区間を1枚で張ると後ろ端が四角く張り出す）
    z0, z1 = zf - f["tuck"], (zb if f["last"] else zb - f["sleeve"] + 2.0)
    spans = 2 if rb / rf < 0.75 else 1
    for j in range(spans):
        za, zz = z0 + (z1 - z0) * j / spans, z0 + (z1 - z0) * (j + 1) / spans
        ra, rz = body_surface(k, za), body_surface(k, zz)
        cubes += ring_loft(body_ring(za, ra, 1.0, n), body_ring(zz, rz, 1.0, n), angles,
                           f"s{k}b{j}_", thick, body_skin)
    # 袖: 関節の前後へ一回り太く。次の体節の差し込み（TUCK 倍）へ被さる
    if not f["last"]:
        s0, s1 = zb - f["sleeve"], zb + f["sleeve"]
        cubes += ring_loft(body_ring(s0, rb, SLEEVE_IN, n), body_ring(s1, rb, SLEEVE_OUT, n), angles,
                           f"s{k}c", max(1.4, 0.085 * rb), collar_skin)
    else:
        # 尾の先の棘
        cubes.append(K.beam((0, 0, zb - 6), (0, 0, zb + 10), 2.4, 2.4, HORN, side=(1, 1, 0), key=f"s{k}tip"))
    # 後半身の腹の竜骨鰭（尾びれの下葉が始まる前まで）
    kh = keel_height(k - 0.5)
    if kh > 1.0 and k < 25:
        base = (0.0, bottom_y(k, zf + 0.2 * SEG) + 2.0, zf + 0.2 * SEG)
        tips = [(0.0, bottom_y(k, zf + dz * SEG) - kh * hf, zf + dz * SEG)
                for dz, hf in ((0.18, 1.0), (0.5, 0.85), (0.85, 0.45))]
        cubes += fan_to(base, (0, -1, 0), (0, 0, 1), tips, f"s{k}keel", ray_w=2.2)
    name = f"seg_{k:02d}"
    K.bone(bones, name, (0.0, 0.0, f["zc"]), cubes)

    # 背びれ（体節ごとの帆）と尾びれ。子の Bone にして idle で揺らす
    fin = []
    if k >= 25:
        # 尾びれ。pivot は体節の前端の芯で、Y まわりに振る（尾の先を左右に煽る）
        reach = 1.35 if f["last"] else 1.0
        base = (0.0, top_y(k, zf + 0.05 * SEG) - 2.0, zf + 0.05 * SEG)
        tips = []
        for dz in (0.12, 0.42, 0.72, reach):
            z = zf + dz * SEG
            tips.append((0.0, top_y(k, min(z, zb)) + fluke_up(z / SEG), z))
        fin += fan_to(base, (0, 1, 0), (0, 0, 1), tips, f"s{k}fu", ray_w=max(2.0, 0.1 * rf))
        base = (0.0, bottom_y(k, zf + 0.1 * SEG) + 2.0, zf + 0.1 * SEG)
        tips = []
        for dz in (0.22, 0.58, 0.95 if not f["last"] else 1.3):
            z = zf + dz * SEG
            tips.append((0.0, bottom_y(k, min(z, zb)) - fluke_down(z / SEG), z))
        fin += fan_to(base, (0, -1, 0), (0, 0, 1), tips, f"s{k}fd", ray_w=max(2.0, 0.09 * rf))
        # 扇のあいだの切れ込みを埋める膜。体の背（腹）から外縁の手前まで張り、尾びれを1枚に見せる
        z_end = zf + (1.3 if f["last"] else 1.0) * SEG
        pieces = 4                      # 外縁の段を小さくするため、体節あたり4枚に割る
        cuts = [zf - 2.0 + (z_end - zf + 4.0) * q / pieces for q in range(pieces + 1)]
        for j in range(pieces):
            za, zz = cuts[j], cuts[j + 1]
            zs = [za + (zz - za) * q / 4.0 for q in range(5)]
            y0 = max(top_y(k, min(z, zb)) for z in zs) - 3.0
            y1 = min(top_y(k, min(z, zb)) + 0.8 * fluke_up(z / SEG) for z in zs)
            if y1 - y0 > 4.0:
                fin.append(K.slab((0.0, (y0 + y1) / 2.0, (za + zz) / 2.0), (0.0, y1 - y0, zz - za),
                                  (1, 0, 0), (0, 1, 0), MEMBRANE, key=f"s{k}su{j}"))
            y0 = min(bottom_y(k, min(z, zb)) for z in zs) + 3.0
            y1 = max(bottom_y(k, min(z, zb)) - 0.8 * fluke_down(z / SEG) for z in zs)
            if y0 - y1 > 4.0:
                fin.append(K.slab((0.0, (y0 + y1) / 2.0, (za + zz) / 2.0), (0.0, y0 - y1, zz - za),
                                  (1, 0, 0), (0, -1, 0), MEMBRANE, key=f"s{k}sd{j}"))
        pivot = (0.0, 0.0, zf)
    else:
        h = sail_height(k - 0.5)
        if h > 4.0:
            zb0 = zf + 0.18 * SEG
            base = (0.0, top_y(k, zb0) - 2.0, zb0)
            tips = [(0.0, top_y(k, zb0 + dz * SEG) + h * hf, zb0 + dz * SEG)
                    for dz, hf in ((0.04, 1.0), (0.30, 0.94), (0.58, 0.70), (0.86, 0.36))]
            fin += fan_to(base, (0, 1, 0), (0, 0, 1), tips, f"s{k}d", ray_w=max(2.2, 0.09 * rf))
            pivot = base
    if fin:
        K.bone(bones, f"{name}_fin", pivot, fin, parent=name)


# ============================================================ 頭
# 頭骨の上半（z, 半幅, 背の高さ, 口縁の高さ）。z は geo（-Z が前）。関節 0 が原点。
# 旧描画の SKULL 表（ブロック、+Z が前）を 4 倍・前後反転し、後ろ端を首へ被さる襟にした
CRANIUM = (
    (18.0, 34.0, 40.0, -1.0),     # 襟（首の差し込みへ被さる）
    (6.0, 40.0, 44.0, -3.0),      # 後頭
    (-10.0, 45.0, 43.0, -5.0),    # こめかみ
    (-28.0, 47.0, 38.0, -6.0),    # 頬（いちばん広い）
    (-48.0, 43.0, 31.0, -6.5),    # 眼の前
    (-70.0, 36.0, 24.0, -6.5),
    (-92.0, 27.0, 17.0, -6.0),
    (-110.0, 18.0, 11.0, -5.0),
    (-122.0, 10.0, 6.5, -4.0),
    (-129.0, 3.5, 2.5, -3.0),     # 吻の先
)
CRANIUM_E = 2.6                 # 角張った頭骨（平らな頭頂と、面取りした縁）
CRANIUM_EDGE = 108.0            # 頭骨の殻が下りる角度（真上から。90 で真横）
CRANIUM_PLATES = 10

# 下顎（z, 半幅, 口縁の高さ, 顎の底）。旧描画の JAW_LOFT から
JAW_T = (
    (24.0, 24.0, -3.0, -24.0),
    (12.0, 33.0, -4.0, -31.0),    # 蝶番
    (-6.0, 38.0, -5.5, -34.0),
    (-28.0, 38.0, -6.0, -31.0),
    (-50.0, 34.0, -6.5, -25.0),
    (-74.0, 28.0, -6.5, -19.0),
    (-96.0, 21.0, -6.0, -14.0),
    (-112.0, 14.0, -5.0, -10.0),
    (-122.0, 8.0, -4.5, -7.0),
    (-127.0, 3.0, -4.0, -5.0),
)
JAW_E = 2.4
JAW_EDGE = 72.0                 # 顎の殻が上がる角度（真上から）
JAW_PLATES = 8
HINGE = (0.0, -6.0, 12.0)       # 旧描画の HINGE_Y / HINGE_Z（-1.5, -3 ブロック）


def interp(table, z, col):
    """z の降順に並んだ表を z で線形補間する"""
    if z >= table[0][0]:
        return table[0][col]
    if z <= table[-1][0]:
        return table[-1][col]
    for a, b in zip(table, table[1:]):
        if b[0] <= z <= a[0]:
            f = (z - a[0]) / (b[0] - a[0])
            return a[col] + (b[col] - a[col]) * f
    return table[-1][col]


def cranium_params(z):
    hw, top, lip = (interp(CRANIUM, z, c) for c in (1, 2, 3))
    edge = abs(math.cos(math.radians(CRANIUM_EDGE))) ** (2.0 / CRANIUM_E)
    b = (top - lip) / (1.0 + edge)
    return hw, b, top - b


def jaw_params(z):
    hw, rim, bottom = (interp(JAW_T, z, c) for c in (1, 2, 3))
    edge = abs(math.cos(math.radians(JAW_EDGE))) ** (2.0 / JAW_E)
    b = (rim - bottom) / (1.0 + edge)
    return hw, b, bottom + b


def cranium_point(z, t, s=1):
    """頭骨の外面の点と外向きの法線。t は真上からの角度（度）"""
    def p(zz, tt):
        a, b, cy = cranium_params(zz)
        r = math.radians(tt)
        sn, cs = math.sin(r), math.cos(r)
        return (s * a * abs(sn) ** (2.0 / CRANIUM_E),
                cy + b * math.copysign(abs(cs) ** (2.0 / CRANIUM_E), cs), zz)
    c = p(z, t)
    dt = vsub(p(z, t + 1.0), p(z, t - 1.0))
    dz = vsub(p(z + 1.0, t), p(z - 1.0, t))
    nrm = vunit(vcross(dz, dt))
    if vdot(nrm, (c[0], c[1] - cranium_params(z)[2], 0.0)) < 0:
        nrm = vmul(nrm, -1)
    return c, nrm


def cranium_at_y(z, y, s=1):
    """頭骨の側面で高さ y の点（t を二分法で解く）"""
    lo, hi = 0.0, CRANIUM_EDGE
    for _ in range(40):
        mid = (lo + hi) / 2.0
        if cranium_point(z, mid, s)[0][1] > y:
            lo = mid
        else:
            hi = mid
    return cranium_point(z, (lo + hi) / 2.0, s)


def lip(z):
    """上顎の口縁 (x, y)"""
    a, b, cy = cranium_params(z)
    r = math.radians(CRANIUM_EDGE)
    return a * abs(math.sin(r)) ** (2.0 / CRANIUM_E), interp(CRANIUM, z, 3)


def jaw_rim(z):
    """下顎の口縁 (x, y)"""
    a, b, cy = jaw_params(z)
    r = math.radians(JAW_EDGE)
    return a * abs(math.sin(r)) ** (2.0 / JAW_E), interp(JAW_T, z, 2)


def cranium_skin(t, _n):
    at = abs(t)
    if at < 25:
        return HEAD, 0.0
    if at < 60:
        return HEAD, 0.35
    if at < 90:
        return HEAD, 0.7
    return HEAD, 1.0


def jaw_skin(t, _n):
    db = abs(t - 180.0)
    if db < 30:
        return JAW, 1.0         # 喉（明るい畝）
    if db < 60:
        return JAW, 0.3
    return JAW, 0.0             # 顎の脇は暗い鱗


def skull():
    """頭骨の殻・口蓋・眼・眉・頭頂の稜・吻の蓋・頬の発光器"""
    cubes = []
    step = 2.0 * CRANIUM_EDGE / CRANIUM_PLATES
    angles = [-CRANIUM_EDGE + step * (i + 0.5) for i in range(CRANIUM_PLATES)]
    rings = []
    for z, *_ in CRANIUM:
        a, b, cy = cranium_params(z)
        rings.append((ring(z, a, b, b, -CRANIUM_EDGE, CRANIUM_EDGE, CRANIUM_PLATES, CRANIUM_E, cy), cy))
    for i in range(len(rings) - 1):
        (r1, cy1), (r0, cy0) = rings[i], rings[i + 1]     # 表は後ろ→前の順。前を r0 にする
        cubes += ring_loft(r0, r1, angles, f"cr{i}_", 3.4 if i < 6 else 2.6, cranium_skin,
                           axis_y=(cy0 + cy1) / 2.0, sink=0.3 * (i % 2))
    # 吻の先の蓋（殻の前の開口を塞ぐ）
    cubes.append(K.cube((-4.0, -3.5, -131.0), (8.0, 6.5, 3.0), HEAD, tone=0.6, key="snoutcap"))
    # 口蓋。口を開けたとき殻の内側（中空）が見えないように
    for i in range(1, len(CRANIUM) - 1):
        z0, z1 = CRANIUM[i][0], CRANIUM[i + 1][0]
        (x0, y0), (x1, y1) = lip(z0), lip(z1)
        y = max(y0, y1) + 1.2
        cubes.append(deck((0.0, y, z0 + 1.0), (0.0, y, z1 - 1.0), 2.0 * 0.95 * min(x0, x1), 2.0, INNER,
                          f"palate{i}"))
    # 喉の奥の闇
    cubes.append(K.cube((-30.0, -30.0, 10.0), (60.0, 34.0, 2.0), CAVITY, key="throat"))
    for s, side in SIDES:
        # 眼: 細長い光る眼を、後ろへ吊り上がるように頬へ埋め、外へ闇の縁（眼窩）を回す
        c, n = cranium_point(-36.0, 60.0, s)
        back = vunit(vsub(cranium_point(-35.0, 60.0, s)[0], cranium_point(-37.0, 60.0, s)[0]))
        rise = vunit(vcross(back, n)) if s > 0 else vunit(vcross(n, back))     # 面に沿って上る向き
        slant = vunit(vadd(vmul(back, math.cos(math.radians(16))), vmul(rise, math.sin(math.radians(16)))))
        cubes.append(K.slab(vsub(c, vmul(n, 1.0)), (18.0, 2.0, 10.5), slant, n, CAVITY, key=f"socket{side}"))
        cubes.append(K.slab(vsub(c, vmul(n, 0.2)), (13.5, 2.0, 7.0), slant, n, EYE, key=f"eye{side}"))
        # 眉の稜: 眼の上を前後に走る骨板。後ろで眉の角へ繋がる
        f0, n0 = cranium_point(-56.0, 46.0, s)
        f1, n1 = cranium_point(-34.0, 42.0, s)
        f2, n2 = cranium_point(-14.0, 40.0, s)
        cubes.append(K.beam(vadd(f0, vmul(n0, 1.0)), vadd(f1, vmul(n1, 3.2)), 6.0, 5.0, PLATE,
                            side=n1, key=f"brow{side}0", overshoot=1.0))
        cubes.append(K.beam(vadd(f1, vmul(n1, 3.2)), vadd(f2, vmul(n2, 2.2)), 7.0, 5.0, PLATE,
                            side=n1, key=f"brow{side}1", overshoot=1.0))
        # 頬の発光器の列（口縁の少し上）
        for j, z in enumerate((-54.0, -66.0, -78.0, -90.0)):
            p, nn = cranium_at_y(z, lip(z)[1] + 5.5, s)
            cubes.append(K.slab(p, (3.2, 3.2, 3.2), (0, 0, 1), nn, GLOWDOT, key=f"pho{side}{j}"))
    # 頭頂の稜（正中の低い骨板の列）
    crest = [(-78.0, 0.6), (-52.0, 1.4), (-26.0, 2.0), (-2.0, 2.4), (14.0, 1.6)]
    for j in range(len(crest) - 1):
        (za, ha), (zb, hb) = crest[j], crest[j + 1]
        ya = interp(CRANIUM, za, 2) + ha
        yb = interp(CRANIUM, zb, 2) + hb
        cubes.append(K.beam((0.0, ya - 2.0, za), (0.0, yb - 2.0, zb), 6.0, 6.0, PLATE, side=(1, 1, 0),
                            key=f"crest{j}", overshoot=1.5))
    return cubes


def jaw():
    """下顎の殻・顎の底・舌・顎の下の髭棘。pivot は HINGE"""
    cubes = []
    step = (360.0 - 2.0 * JAW_EDGE) / JAW_PLATES
    angles = [JAW_EDGE + step * (i + 0.5) for i in range(JAW_PLATES)]
    rings = []
    for z, *_ in JAW_T:
        a, b, cy = jaw_params(z)
        rings.append((ring(z, a, b, b, JAW_EDGE, 360.0 - JAW_EDGE, JAW_PLATES, JAW_E, cy), cy))
    for i in range(len(rings) - 1):
        (r1, cy1), (r0, cy0) = rings[i], rings[i + 1]
        cubes += ring_loft(r0, r1, angles, f"jw{i}_", 3.0 if i < 6 else 2.4, jaw_skin,
                           axis_y=(cy0 + cy1) / 2.0, sink=0.3 * (i % 2))
    cubes.append(K.cube((-3.0, -6.5, -129.0), (6.0, 3.0, 3.0), JAW, tone=0.3, key="chincap"))
    # 顎の底（口の中の肉）
    for i in range(1, len(JAW_T) - 1):
        z0, z1 = JAW_T[i][0], JAW_T[i + 1][0]
        (x0, y0), (x1, y1) = jaw_rim(z0), jaw_rim(z1)
        y = min(y0, y1) - 3.0
        cubes.append(deck((0.0, y, z0 + 1.0), (0.0, y, z1 - 1.0), 2.0 * 0.9 * min(x0, x1), 2.0, INNER,
                          f"floor{i}"))
    # 舌
    cubes.append(K.beam((0.0, -8.0, 8.0), (0.0, -7.0, -50.0), 18.0, 4.0, INNER, side=(1, 0, 0),
                        key="tongue0"))
    cubes.append(K.beam((0.0, -7.0, -50.0), (0.0, -7.5, -92.0), 12.0, 3.4, INNER, side=(1, 0, 0),
                        key="tongue1", overshoot=1.0))
    # 顎の下の髭棘（旧描画の chin barbs）。下へ垂れて後ろへ流れる
    for s, side in SIDES:
        cubes += horn((s * 11.0, -27.0, -40.0), (s * 15.0, -42.0, -34.0), (s * 19.0, -50.0, -18.0), 5.0,
                      f"barb{side}", cuts=(0.0, 0.5, 1.0), taper=(1.0, 0.55))
    return cubes


def upper_teeth():
    cubes = []
    for s, side in SIDES:
        for i in range(9):
            z = -18.0 - 11.2 * i
            x, y = lip(z)
            depth = clamp((interp(JAW_T, z, 2) - interp(JAW_T, z, 3)) / 24.0, 0.45, 1.0)
            length = 4.0 * (2.4 + (i % 2) * 0.8 + (0.9 if i >= 6 else 0.0)) * depth
            base = (s * x * 0.91, y + 2.5, z)
            mid = (s * x * 0.925, y - length * 0.45, z + 0.4)
            tip = (s * x * 0.94, y - length, z + 2.2)          # 先が少し後ろへ反る
            cubes.append(K.beam(base, mid, 3.6, 3.6, TOOTH, side=(s, 0, 1), key=f"ut{side}{i}a"))
            cubes.append(K.beam(mid, tip, 2.0, 2.0, TOOTH, side=(s, 0, 1), key=f"ut{side}{i}b", overshoot=0.8))
        # 前の牙（上顎の先で下顎の外へ垂れる）
        x, y = lip(-116.0)
        cubes += horn((s * x * 0.72, y + 2.0, -116.0), (s * x * 0.78, y - 9.0, -116.5), (s * x * 0.85, y - 18.0, -113.0),
                      6.0, f"fang{side}", region=TOOTH, cuts=(0.0, 0.55, 1.0), taper=(1.0, 0.6))
    return cubes


def lower_teeth():
    cubes = []
    for s, side in SIDES:
        for i in range(9):
            z = -24.0 - 10.4 * i
            x, y = jaw_rim(z)
            depth = clamp((interp(JAW_T, z, 2) - interp(JAW_T, z, 3)) / 24.0, 0.45, 1.0)
            length = 4.0 * (2.0 + ((i + 1) % 2) * 0.7 + (0.8 if i >= 7 else 0.0)) * depth
            base = (s * x * 0.80, y - 2.5, z)
            mid = (s * x * 0.79, y + length * 0.45, z - 0.4)
            tip = (s * x * 0.78, y + length, z - 1.8)
            cubes.append(K.beam(base, mid, 3.2, 3.2, TOOTH, side=(s, 0, 1), key=f"lt{side}{i}a"))
            cubes.append(K.beam(mid, tip, 1.8, 1.8, TOOTH, side=(s, 0, 1), key=f"lt{side}{i}b", overshoot=0.8))
        x, y = jaw_rim(-110.0)
        cubes += horn((s * x * 0.66, y - 2.0, -110.0), (s * x * 0.66, y + 7.0, -110.5), (s * x * 0.6, y + 14.0, -112.0),
                      5.0, f"lfang{side}", region=TOOTH, cuts=(0.0, 0.55, 1.0), taper=(1.0, 0.6))
    return cubes


def horns():
    """角（旧描画の4対の spike と鼻の角）。後ろへ反り、先ほど細い"""
    cubes = []
    for s, side in SIDES:
        cubes += horn((s * 22.0, 40.0, -4.0), (s * 34.0, 58.0, 26.0), (s * 46.0, 64.0, 72.0), 13.0, f"hmain{side}")
        cubes += horn((s * 36.0, 30.0, -26.0), (s * 46.0, 40.0, -6.0), (s * 52.0, 42.0, 16.0), 8.0, f"hbrow{side}")
        cubes += horn((s * 44.0, 16.0, -14.0), (s * 58.0, 18.0, 12.0), (s * 68.0, 22.0, 46.0), 10.0, f"hcheek{side}")
        cubes += horn((s * 38.0, -4.0, 6.0), (s * 48.0, -10.0, 22.0), (s * 54.0, -18.0, 44.0), 8.0, f"hjaw{side}")
    cubes += horn((0.0, 9.0, -108.0), (0.0, 22.0, -107.0), (0.0, 30.0, -96.0), 7.0, "hnose",
                  cuts=(0.0, 0.5, 1.0), taper=(1.0, 0.5))
    return cubes


GILL_SLITS = (-2.0, 5.5, 13.0)  # 鰓裂の z（頬の後ろ、首の襟の手前）
GILL_Y = (-1.0, 19.0)           # 鰓裂の下端と上端
GILL_LEAN = 3.0                 # 鰓裂の上端が前へ出る量（後ろへ傾いた三日月）


def gill(s, side):
    """鰓: 光る鰓裂3本と、その前縁の鰓弓。pivot は最前の鰓裂の前で、Y まわりに開く（呼吸）"""
    cubes = []
    ymid = sum(GILL_Y) / 2.0
    for j, z in enumerate(GILL_SLITS):
        top, nt = cranium_at_y(z - GILL_LEAN, GILL_Y[1], s)
        bot, nb = cranium_at_y(z + GILL_LEAN, GILL_Y[0], s)
        mid, nm = cranium_at_y(z, ymid, s)
        along = vunit(vsub(top, bot))
        # 鰓裂: 皮膚から少し沈めた暗い溝。局所 Z（絵の縦）を鰓裂の長さ方向へ
        cubes.append(K.slab(vsub(mid, vmul(nm, 0.3)), (4.5, 1.2, vdist(top, bot) + 2.0),
                            vcross(along, nm), nm, GILL, key=f"gillslit{side}{j}"))
        # 鰓弓: 鰓裂の前縁に盛り上がった骨
        cubes.append(K.beam(vadd(vadd(bot, vmul(nb, 1.0)), (0, 0, -3.2)), vadd(vadd(top, vmul(nt, 1.0)), (0, 0, -3.2)),
                            3.0, 2.6, PLATE, side=nm, key=f"gillarch{side}{j}", overshoot=1.0))
    front, _ = cranium_at_y(GILL_SLITS[0] - 6.0, ymid, s)
    return cubes, front


PECTORALS = {
    # 名前: (付け根, 開く向き e1（左側）, 鰭条の (角度, 長さ))。角度は e1 から後ろ（e2）へ
    1: ((33.0, -14.0, 10.0), (1.0, -0.45, 0.30), ((-10, 54), (8, 62), (26, 62), (44, 54), (62, 40))),
    2: ((22.0, -30.0, 16.0), (0.75, -0.8, 0.35), ((-5, 38), (14, 44), (33, 42), (52, 32))),
}


def pectoral(s, pair):
    root, e1, rays = PECTORALS[pair]
    root = (s * root[0], root[1], root[2])
    e1 = vunit((s * e1[0], e1[1], e1[2]))
    e2 = vunit(vsub((0.0, 0.0, 1.0), vmul(e1, e1[2])))
    cubes = K.fan(root, e1, e2, rays, f"pec{pair}{s}", RAY, MEMBRANE, tip_region=MEMBRANE,
                  ray_w=3.0 if pair == 1 else 2.4, lift=0.15)
    return cubes, root


def head(bones):
    K.bone(bones, "head", (0.0, 0.0, 0.0), skull())
    K.bone(bones, "jaw", HINGE, jaw(), parent="head")
    K.bone(bones, "teeth_lower", HINGE, lower_teeth(), parent="jaw")
    K.bone(bones, "teeth_upper", (0.0, 0.0, 0.0), upper_teeth(), parent="head")
    K.bone(bones, "horns", (0.0, 0.0, 0.0), horns(), parent="head")
    for s, side in SIDES:
        cubes, front = gill(s, side)
        K.bone(bones, f"gill_{side}", front, cubes, parent="head")
    for pair in (1, 2):
        for s, side in SIDES:
            cubes, root = pectoral(s, pair)
            K.bone(bones, f"fin_{side}{pair}", root, cubes, parent="head")


# ============================================================ アニメーション
IDLE = 4.0


def mirror(axis, s):
    """左右の鏡写し。X まわりはそのまま、Y・Z まわりは符号が返る"""
    return (axis[0], axis[1] * s, axis[2] * s)


def animations(bones):
    names = {b["name"] for b in bones}
    idle = {
        # 顎: ゆっくり少し開いて閉じる（rx が正で口が開く）
        "jaw": rot([(0.0, (0, 0, 0)), (1.5, (6, 0, 0), EASE_SOFT), (2.3, (6, 0, 0)),
                    (IDLE, (0, 0, 0), EASE_SOFT)]),
    }
    for s, side in SIDES:
        # 鰓: 顎が閉じるのに合わせて外へ開く（呼吸）。左は ry が正で外、右は負
        idle[f"gill_{side}"] = rot([(0.0, (0, 0, 0)), (1.4, (0, 0, 0)), (2.6, (0, 9 * s, 0), EASE_SOFT),
                                    (IDLE, (0, 0, 0), EASE_SOFT)])
        idle[f"fin_{side}1"] = wave(IDLE, 9.0, 0.0, mirror((0.25, 0.4, 1.0), s))
        idle[f"fin_{side}2"] = wave(IDLE, 7.0, 1.6, mirror((0.2, 0.3, 1.0), s))
    for k in range(1, JOINTS + 1):
        name = f"seg_{k:02d}_fin"
        if name not in names:
            continue
        if k >= 25:
            idle[name] = wave(IDLE, 4.0 + 2.0 * (k - 25), -0.45 * k, (0, 1, 0))     # 尾びれを左右に煽る
        else:
            idle[name] = wave(IDLE, 3.5, -0.45 * k, (0, 0, 1))                       # 背びれを左右に揺らす
    return _no_negative_zero({"idle": {"loop": True, "animation_length": IDLE, "bones": idle}})


def _no_negative_zero(d):
    """wave() の丸めが出す -0.0 を 0.0 に揃える（読み込みには影響しないが、差分が読みにくい）"""
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
    # visible_bounds はブロック単位（MODEL_SCALE を掛ける前）。GeckoLib は描画の間引きに使わない
    length = (JOINTS * SEG + 140.0) / 16.0
    return K.model(f"geometry.{NAME}", bones, round(length + 8.0, 1), round(5 * R0 / 16.0, 1), 0), bones


def contract(bones, cubes, anims):
    def entry(b):
        e = {"name": b["name"], "parent": b.get("parent"), "pivot": b["pivot"]}
        if b["name"] == "head":
            e["role"] = "頭。関節 0 に pivot。-Z が鼻先"
        elif b["name"].startswith("seg_") and not b.get("parent"):
            k = int(b["name"][4:])
            e["role"] = f"体節 {k}: 関節 {k - 1}（前）〜関節 {k}（後ろ）。pivot は中心"
            e["joint_front"], e["joint_back"] = k - 1, k
        return e
    return {
        "entity": NAME,
        "generator": "tools/boss/serpent_model/build.py（手で直さず、生成元を直して流し直す）",
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
        "jaw_hinge": list(HINGE),
        "rest_head_extent_units": {"snout_z": CRANIUM[-1][0], "collar_z": CRANIUM[0][0],
                                   "half_width": max(r[1] for r in CRANIUM)},
        "rest_body_section": {"half_width": BODY_W, "up": BODY_UP, "down": BODY_DOWN,
                              "note": "体の半径 × この比。半径は関節 0 で body_radius_units、尾へ AbyssSerpentRenderer.bodyScale と同じ式で細る"},
        "animation_signs": {
            "jaw": "rotation の x が正で口が開く（idle は 0→6 度）",
            "gill_l / gill_r": "y が左は正・右は負で外へ開く",
            "fin_l* / fin_r*": "左右で y と z の符号が逆（鏡写し）",
            "seg_02_fin..seg_24_fin": "背びれ。pivot は付け根、z で左右に揺れる",
            "seg_25_fin..seg_28_fin": "尾びれ（上葉と下葉）。pivot は体節の前端の芯、y で左右に煽る",
        },
        "bone_rule": ("head と seg_01..seg_28 は root 直下。毎フレーム位置と回転を上書きする。"
                      "seg_k は関節 k-1 と k の中点へ、局所 -Z が関節 k から関節 k-1 を向くように置く。"
                      "head は関節 0 へ、-Z が関節 1 から関節 0 の向き（さらに先）を向くように置く。"
                      "子（jaw, teeth_*, horns, gill_*, fin_*, seg_XX_fin）は animation に任せる"),
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
            "子の Bone（jaw, gill_*, fin_*, seg_XX_fin）は animation が回すので、Java からは触らない"
            "（触るなら animation の値を上書きしてしまう）",
        ],
        "animations": {name: {"loop": a["loop"], "length": a["animation_length"],
                              "bones": sorted(a["bones"])} for name, a in anims.items()},
        "cube_count": cubes,
        "bones": [entry(b) for b in bones],
    }


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
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
        key = "head" if not b["name"].startswith("seg_") else "body"
        per[key] = per.get(key, 0) + len(b["cubes"])
    print(f"cubes: 計 {cubes}（頭 {per.get('head', 0)} / 体 {per.get('body', 0)}）、"
          f"MODEL_SCALE {MODEL_SCALE:g}、体節 {SEG:g} 単位")
    print("次に流すこと:")
    print("  python tools/boss/check_animations.py abyss_serpent")
    print("  python tools/boss/serpent_model/preview.py")


if __name__ == '__main__':
    main()
