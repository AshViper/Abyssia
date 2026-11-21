# -*- coding: utf-8 -*-
"""巨大ミツクリザメ（colossal_goblin_shark、GNT01 の巨大野生生物）の GeckoLib モデル一式を書き出す。

```bash
python tools/boss/goblin_shark_model/build.py
python tools/boss/check_animations.py colossal_goblin_shark
python tools/boss/goblin_shark_model/preview.py
```

書き出すもの（どれも丸ごと上書き。手で直さず、ここを直して流し直す）:

- `assets/abyssia/geo/entity/colossal_goblin_shark.geo.json`
- `assets/abyssia/animations/entity/colossal_goblin_shark.animation.json`（idle / attack / jaw_out）
- `assets/abyssia/textures/entity/boss/colossal_goblin_shark_geo.png`（仮テクスチャ。発光は無い）
- `tools/boss/goblin_shark_model/contract.json`（描画の Java 側との約束）

<h2>骨の約束（ボスの「鎖に沿って骨を曲げる」方式と同じ）</h2>
`head` と `seg_01`〜`seg_16` は<b>すべて root 直下</b>。静止姿勢は頭から尾まで一直線で、
関節 j（0〜16、ColossalGoblinShark.SPEC: 17 関節・間隔 2 ブロック）は (0, 0, SEG * j)。頭は -Z を向き、
関節 0 に pivot。体節 k は関節 k-1（前）〜k（後ろ）で、pivot は中心 (0, 0, SEG * (k - 0.5))。
Java は毎フレーム root 直下の骨の位置と回転を上書きする。体のうねりは Java が入れるので、
animation には体の曲げを入れない。

<h2>寸法</h2>
全長 32 ブロック。吻端は関節 0 の 1.75 ブロック前、尾びれの先は関節 15 の 0.25 後ろ（関節 0 から 30.25）。
表は「吻端からの距離 s（ブロック）」で書き、geo の z へは (s - 1.75) × U で直す。
頭（`head`）は吻端〜関節 1 で、吻・眼・頭の前半を持つ。seg_01 は頭と同じ向きに置かれるので箱なし。
各体節の前端は前の関節を中心とする球面で前の殻へ入る（球関節。angler_model と同じ考え方）。

<h2>顎</h2>
`jaw`（head の子）が上顎の弓と歯・口蓋を持ち、position で前下へ 6 ブロック（JAW_D）突き出す。
`jaw_lower`（jaw の子、pivot は顎関節）が下顎で、rotation x が正で口が開く。
`jaw_sling`（jaw の子）は顎の後ろと喉をつなぐ膜の管。静止では長さ 1 ブロックで喉に隠れ、突き出すと
scale z と rotation x で伸びて喉までつながる（値は sling_pose(f) で解く）。
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
from atlas import (BACK, FLANK, BELLY, FIN, SNOUT, SNOUT_UNDER, HEAD, FLESH, MOUTH, SLING, GILL, TOOTH, EYE)
from boss_kit import (_add as vadd, _sub as vsub, _mul as vmul, _unit as vunit, _cross as vcross,
                      _dot as vdot, _mid as vmid, _dist as vdist, rot, pos, scale, wave, EASE_SOFT,
                      EASE_SLOW, EASE_FAST, EASE_BACK)

ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/abyssia'
GEO = ASSETS / 'geo/entity'
ANIM = ASSETS / 'animations/entity'
TEX = ASSETS / 'textures/entity/boss'
NAME = 'colossal_goblin_shark'
CUBE_LIMIT = 1328           # 既存ボス 1 体ぶん（いちばん少ない abyss_serpent の 1328）を超えない

K = boss_kit.Kit(atlas, stretched=atlas.STRETCHED, top_anchored=atlas.TOP_ANCHORED)

# ============================================================ 実寸
MODEL_SCALE = 2.0           # 描画側で一様に掛ける倍率。geo の 1 単位 = 2/16 ブロック
JOINTS = 16                 # 体節の数（関節は 17）
JOINT_SPACING = 2.0         # ブロック（ColossalGoblinShark.SPEC: 全長 32 / 16）
U = 16.0 / MODEL_SCALE      # 1 ブロックが何単位か（8）
SEG = JOINT_SPACING * U     # 体節の長さ（16 単位）
SNOUT_AHEAD = 1.75          # 吻端が関節 0 より前にある長さ（ブロック）
TOTAL = 32.0                # 全長（吻端〜尾びれの先）
TAIL_END = TOTAL            # 体の芯（尾びれの上葉）が終わる s

SIDES = ((1, "l"), (-1, "r"))   # +X が左


def zs(s):
    """吻端からの距離 s（ブロック）を geo の z へ"""
    return (s - SNOUT_AHEAD) * U


def s_of_joint(j):
    return JOINT_SPACING * j + SNOUT_AHEAD


def P(x, y, s):
    return (x * U, y * U, zs(s))


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def lerp_table(table, k, cols=(1, 2, 3)):
    if k <= table[0][0]:
        return tuple(table[0][c] for c in cols)
    for a, b in zip(table, table[1:]):
        if k <= b[0]:
            f = (k - a[0]) / (b[0] - a[0])
            return tuple(a[c] + (b[c] - a[c]) * f for c in cols)
    return tuple(table[-1][c] for c in cols)


# ============================================================ 体の断面
# (s, 半幅, 背の高さ, 腹の高さ)。ブロック、y は体の芯（関節の線）から。
# 吻は薄い剣状の板（厚み 0.1〜0.5、幅は吻端の 0.2 から眼で 1.9）で、芯より上にある。
# 吻の下は顎が収まる所なので腹を引っ込めてある（s 3.2〜5.75）。最大の体高は s 12〜14 で約 3.9（全長の 12%）。
# 尾柄（s 21.75）の後ろは尾びれの上葉の芯で、背の線はほぼ水平のまま腹が上がって細る（芯が上へ反る）
BODY = (
    (0.00, 0.09, 0.42, 0.30), (0.12, 0.21, 0.47, 0.27), (0.40, 0.35, 0.52, 0.24), (0.85, 0.49, 0.57, 0.22),
    (1.45, 0.63, 0.63, 0.20), (2.05, 0.77, 0.70, 0.17), (2.65, 0.93, 0.81, 0.06), (3.20, 1.12, 0.93, -0.16),
    (3.75, 1.30, 1.06, -0.36), (4.75, 1.43, 1.28, -0.60), (5.75, 1.52, 1.48, -0.95), (6.75, 1.59, 1.64, -1.26),
    (7.75, 1.63, 1.78, -1.50), (9.75, 1.67, 1.93, -1.78), (11.75, 1.68, 2.00, -1.92), (13.75, 1.64, 1.98, -1.95),
    (15.75, 1.52, 1.86, -1.84), (17.75, 1.32, 1.62, -1.58), (19.75, 1.02, 1.24, -1.16), (21.75, 0.64, 0.84, -0.64),
    (23.75, 0.43, 0.76, -0.28), (25.75, 0.31, 0.73, -0.04), (27.75, 0.21, 0.71, 0.18), (29.75, 0.12, 0.70, 0.40),
    (31.25, 0.06, 0.69, 0.55), (32.00, 0.02, 0.68, 0.62))
WF = 0.48                   # いちばん幅の広い高さ（腹から背への割合）
SEAM = 0.985                # 体節の前端（関節）の外形 / 前の殻の外形。殻の縁が 1.5% だけ被さる
DOME = 0.45                 # 体節の前端の球面が関節より前へ入る長さ / 断面の半幅（約 27 度まで隙間が出ない）
DOME_F = math.sqrt(1.0 - DOME * DOME)


def section(s):
    """s の断面 (半幅, 背, 腹)。ブロック"""
    return lerp_table(BODY, clamp(s, 0.0, TAIL_END))


def ellipse(hw, top, bot):
    """(半幅, 中心の高さ, 上の半径, 下の半径)"""
    cy = bot + WF * (top - bot)
    return hw, cy, top - cy, cy - bot


def top_y(s):
    return section(s)[1]


def bottom_y(s):
    return section(s)[2]


def ring_pts(z, hw, cy, up, dn, n):
    """楕円の縁の点を n+1 個（ブロック → geo）。真下から始まり、板 i の中心角が -180 + 360 i / n"""
    step = 360.0 / n
    pts = []
    for i in range(n + 1):
        t = math.radians(-180.0 - step / 2.0 + step * i)
        c = math.cos(t)
        pts.append((hw * math.sin(t) * U, (cy + (up if c >= 0 else dn) * c) * U, z))
    return pts


def ring_angles(n):
    step = 360.0 / n
    return [-180.0 + step * i for i in range(n)]


def _abs_angle(t):
    return abs(((t + 180.0) % 360.0) - 180.0)


def ring_loft(r0, r1, angles, key, thick, skin, axis_y, lap=1.12, overlap=1.04, sink=0.0):
    """2つの断面の縁どうしを板で結ぶ（angler_model の ring_loft と同じ置き方）。r0 が前、r1 が後ろ。
    板の局所 Z は「面に沿って上る向き」（絵の上端）。axis_y は断面の中心の高さ（法線を外へ向ける基準）"""
    out = []
    for i in range(len(r0) - 1):
        a0, b0, a1, b1 = r0[i], r0[i + 1], r1[i], r1[i + 1]
        m0, m1 = vmid(a0, b0), vmid(a1, b1)
        along = vsub(m1, m0)
        length = math.sqrt(vdot(along, along))
        if length < 1e-6:
            continue
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


def plate_thick(s0, s1):
    hw0, t0, b0 = section(s0)
    hw1, t1, b1 = section(s1)
    r = (hw0 + hw1 + (t0 - b0) / 2.0 + (t1 - b1) / 2.0) / 4.0
    return clamp(0.045 * r * U, 0.4, 0.9)


def body_skin(t, _n):
    """背（灰藤）→ 脇腹（下ほど桃色）→ 腹（淡い桃白）"""
    at = _abs_angle(t)
    if at < 34:
        return BACK, None
    if at < 62:
        return FLANK, 0.1
    if at < 92:
        return FLANK, 0.45
    if at < 125:
        return FLANK, 0.85
    return BELLY, None


def snout_skin(t, _n):
    return (SNOUT, None) if _abs_angle(t) < 104 else (SNOUT_UNDER, None)


def headback_skin(t, _n):
    """眼の後ろ〜関節 1。背は体と同じ、脇は頭の地、下は吻の下と同じ淡い地（顎が下に来る）"""
    at = _abs_angle(t)
    if at < 34:
        return BACK, None
    if at < 104:
        return HEAD, 0.2 + 0.6 * (at - 34) / 70.0
    return SNOUT_UNDER, None


# ============================================================ 頭（吻・眼）
HEAD_S = (0.0, 0.12, 0.40, 0.85, 1.45, 2.05, 2.65, 3.20, 3.75)   # 頭の殻の輪の位置（s）。最後が関節 1
HEAD_N = 16
EYE_S, EYE_T, EYE_SIZE = 2.6, 56.0, (0.44, 0.34)     # 眼の位置（s, 真上からの角度）と大きさ（ブロック）


def surface(s, t_deg, sx=1):
    """断面 s の角度 t（真上が 0）の外皮上の点と外向き法線（xy 平面、ブロック）"""
    hw, cy, up, dn = ellipse(*section(s))
    t = math.radians(t_deg)
    b = up if math.cos(t) >= 0 else dn
    x, y = hw * math.sin(t), cy + b * math.cos(t)
    n = vunit((math.sin(t) / hw, math.cos(t) / b, 0.0))
    return (sx * x, y), (sx * n[0], n[1])


def eye(sx, key):
    (x, y), (nx, ny) = surface(EYE_S, EYE_T, sx)
    c = (x * U - nx * 0.05, y * U - ny * 0.05, zs(EYE_S))
    tangent = (-ny * sx, nx * sx, 0.0)
    if tangent[1] < 0:
        tangent = vmul(tangent, -1)
    return K.slab(c, (EYE_SIZE[0] * U, EYE_SIZE[1] * U, 0.9), (0.0, 0.0, 1.0), tangent, EYE, key=key)


def head_cubes():
    cubes = []
    angles = ring_angles(HEAD_N)
    for j in range(len(HEAD_S) - 1):
        s0, s1 = HEAD_S[j], HEAD_S[j + 1]
        e0, e1 = ellipse(*section(s0)), ellipse(*section(s1))
        r0 = ring_pts(zs(s0), *e0, HEAD_N)
        r1 = ring_pts(zs(s1), *e1, HEAD_N)
        skin = snout_skin if (s0 + s1) / 2.0 < 2.8 else headback_skin
        cubes += ring_loft(r0, r1, angles, f"h{j}_", plate_thick(s0, s1), skin,
                           (e0[1] + e1[1]) / 2.0 * U, sink=0.25 * (j % 2))
    # 吻端の蓋（いちばん前の輪は 1.3 x 0.6 単位の小さな楕円）
    hw, top, bot = section(0.0)
    cubes.append(K.cube((-hw * U - 0.2, bot * U - 0.15, zs(0.0) - 0.1), (2 * hw * U + 0.4, (top - bot) * U + 0.3, 1.2),
                        SNOUT, key="tip"))
    for sx, side in SIDES:
        cubes.append(eye(sx, f"eye{side}"))
    return cubes


# ============================================================ 顎（突き出す）
# 上顎の弓（U 字）: 前端 s、後ろ端 s（口角）、口角での半幅、帯の中心の高さ。ブロック
UJ_FRONT, UJ_REAR, UJ_W, UJ_Y = 3.45, 5.35, 0.95, -0.38
LJ_FRONT, LJ_REAR, LJ_W, LJ_Y = 3.52, 5.30, 0.84, -0.88
BAND_H, BAND_T = 0.36, 0.24
HINGE = (0.0, -0.62, 5.2)               # 下顎の関節（x, y, s）。jaw・jaw_lower・jaw_sling の pivot
JAW_D = (0.0, -2.4, -5.5)               # 突き出す向きと距離（ブロック）。|JAW_D| = 6
JAW_REACH = math.sqrt(sum(v * v for v in JAW_D))
JAW_TILT = 12.0                         # 突き出しきったときに顎全体が前下へ傾く角度（rotation x、度）
UPPER_TEETH = (8, 22, 36, 50, 63, 75, 86)        # 歯の並ぶ角度（U 字の媒介変数、前が 0、口角が 90）
LOWER_TEETH = (15, 30, 45, 60, 73, 85)
# 喉の膜（jaw_sling）: 顎の後ろの中心から後ろへ伸びる楕円の管
# （静止では喉の皮の内側に収まり、下端だけが顎の下から喉へ続く腹の線になる）。前端の中心は顎関節
SL_FRONT, SL_LEN, SL_Y, SL_HW, SL_UP, SL_DN, SL_N = HINGE[2], 1.0, HINGE[1], 0.86, 0.44, 0.44, 10


def u_point(front, rear, w, y, theta, out=0.0):
    """U 字の弓の上の点（θ は前が 0、+90 が左の口角）。out はその点で弓の外へずらす量"""
    t = math.radians(theta)
    x = (w + out) * math.sin(t)
    s = front + (rear - front) * (1.0 - math.cos(t)) - out * math.cos(t)
    return P(x, y, s)


def u_normal(theta):
    """弓の外向き（前端では前 = -Z、口角では外 = ±X）"""
    t = math.radians(theta)
    return (math.sin(t), 0.0, -math.cos(t))


def u_half_width(front, rear, w, s):
    c = clamp(1.0 - (s - front) / (rear - front), -1.0, 1.0)
    return w * math.sqrt(max(0.0, 1.0 - c * c))


def band(front, rear, w, y, key, m=12):
    pts = [u_point(front, rear, w, y, -90.0 + 180.0 * i / m) for i in range(m + 1)]
    return [K.beam(pts[i], pts[i + 1], BAND_H * U, BAND_T * U, FLESH, side=(0, 1, 0), key=f"{key}{i}",
                   overshoot=0.35) for i in range(m)]


def tooth(base, tip, theta, w, key, curl):
    """細い釘形の歯。根元と先の 2 本の棒で、先は curl だけ反る"""
    mid = vadd(vadd(base, vmul(vsub(tip, base), 0.55)), curl)
    t = math.radians(theta)
    side = (math.cos(t), 0.0, math.sin(t))          # 幅は弓に沿う向き
    return [K.beam(base, mid, w * U, w * 0.85 * U, TOOTH, side=side, key=f"{key}a", overshoot=0.2),
            K.beam(mid, tip, w * 0.6 * U, w * 0.5 * U, TOOTH, side=side, key=f"{key}b", overshoot=0.2)]


def upper_jaw():
    cubes = band(UJ_FRONT, UJ_REAR, UJ_W, UJ_Y, "ub")
    for sgn in (1, -1):
        for th in UPPER_TEETH:
            theta = sgn * th
            f = 1.0 - th / 90.0
            base = u_point(UJ_FRONT, UJ_REAR, UJ_W, UJ_Y - BAND_H * 0.4, theta, out=0.03)
            length = 0.30 + 0.24 * f                    # 前の歯ほど長い
            tip = vadd(base, vadd(vmul(u_normal(theta), (0.05 + 0.16 * f) * U), (0.0, -length * U, 0.0)))
            curl = vmul(u_normal(theta), 0.06 * U)       # 中ほどが外へ張り出す（突き出た歯）
            cubes += tooth(base, tip, theta, 0.13 - 0.03 * (1 - f), f"ut{sgn}{th}", curl)
    # 上顎の屋根（上から見える肉）と口蓋（口の中の暗い天井）
    n = 4
    for j in range(n):
        s0 = UJ_FRONT + 0.14 + j * (UJ_REAR - UJ_FRONT - 0.14) / n
        s1 = s0 + (UJ_REAR - UJ_FRONT - 0.14) / n
        xw = u_half_width(UJ_FRONT, UJ_REAR, UJ_W, s0 + 0.3 * (s1 - s0))
        cubes.append(K.cube(P(-(xw + 0.02), UJ_Y + 0.02, s0), ((2 * xw + 0.04) * U, 0.13 * U, (s1 - s0) * U + 0.3),
                            FLESH, key=f"roof{j}"))
        cubes.append(K.cube(P(-(xw - 0.04), UJ_Y - 0.08, s0), ((2 * xw - 0.08) * U, 0.1 * U, (s1 - s0) * U + 0.3),
                            MOUTH, key=f"pal{j}"))
    return cubes


def lower_jaw():
    cubes = band(LJ_FRONT, LJ_REAR, LJ_W, LJ_Y, "lb")
    for sgn in (1, -1):
        for th in LOWER_TEETH:
            theta = sgn * th
            f = 1.0 - th / 90.0
            base = u_point(LJ_FRONT, LJ_REAR, LJ_W, LJ_Y + BAND_H * 0.4, theta, out=-0.02)
            length = 0.26 + 0.18 * f
            tip = vadd(base, vadd(vmul(u_normal(theta), 0.10 * f * U), (0.0, length * U, 0.0)))
            curl = vmul(u_normal(theta), 0.04 * U)
            cubes += tooth(base, tip, theta, 0.11 - 0.025 * (1 - f), f"lt{sgn}{th}", curl)
    # 下顎の下（顎の皮）と口の床
    n = 7
    span = LJ_REAR - LJ_FRONT - 0.04
    for j in range(n):
        s0 = LJ_FRONT + 0.04 + j * span / n
        s1 = s0 + span / n
        xw = u_half_width(LJ_FRONT, LJ_REAR, LJ_W, s0 + 0.25 * (s1 - s0))
        cubes.append(K.cube(P(-(xw + 0.04), LJ_Y - 0.17, s0), ((2 * xw + 0.08) * U, 0.12 * U, (s1 - s0) * U + 0.25),
                            SNOUT_UNDER, key=f"chin{j}"))
        if j % 2 == 0:
            s2 = min(LJ_REAR, s0 + 2 * span / n)
            xw2 = u_half_width(LJ_FRONT, LJ_REAR, LJ_W, s0 + 0.3 * (s2 - s0))
            cubes.append(K.cube(P(-(xw2 - 0.06), LJ_Y - 0.06, s0), ((2 * xw2 - 0.12) * U, 0.08 * U, (s2 - s0) * U),
                                MOUTH, key=f"floor{j}"))
    return cubes


def sling_skin(_t, _n):
    return SLING, None


def sling_cubes(length):
    """喉の膜の管。jaw_sling の pivot（顎の後ろの中心）から後ろへ length ブロック。
    preview の突き出した図は、伸ばした長さでここを呼び直して描く（ゲームでは scale z で伸びる）"""
    z0 = zs(SL_FRONT)
    r0 = ring_pts(z0, SL_HW, SL_Y, SL_UP, SL_DN, SL_N)
    r1 = ring_pts(z0 + length * U, SL_HW, SL_Y, SL_UP, SL_DN, SL_N)
    return ring_loft(r0, r1, ring_angles(SL_N), "sl", 0.6, sling_skin, SL_Y * U, overlap=1.02)


def sling_pose(f):
    """顎を f（0..1）だけ突き出したときの jaw_sling の (rotation x 度, scale z)。
    膜の後ろ端（静止で pivot の SL_LEN 後ろ）が喉の同じ点に留まるよう、向きと長さを解く。
    jaw_sling は jaw の子で pivot が同じ顎関節なので、jaw の傾き（JAW_TILT * f）を差し引いた値を返す"""
    vy = -JAW_D[1] * f
    vz = SL_LEN - JAW_D[2] * f
    return math.degrees(math.atan2(vy, vz)) - JAW_TILT * f, math.hypot(vy, vz) / SL_LEN


def jaw_bones(bones):
    hinge = P(*HINGE[:2], HINGE[2])
    K.bone(bones, "jaw", hinge, upper_jaw(), parent="head")
    K.bone(bones, "jaw_lower", hinge, lower_jaw(), parent="jaw")
    K.bone(bones, "jaw_sling", P(0.0, SL_Y, SL_FRONT), sling_cubes(SL_LEN), parent="jaw")


# ============================================================ 体節
# 体節ごとの (板の数, 体節の中の輪の位置（関節 k-1 から k への割合）)。前端の球面の帯は別に足す
SEG_PLAN = {2: (20, (0.0, 0.5, 1.0)), 3: (20, (0.0, 0.5, 1.0)), 4: (20, (0.0, 0.5, 1.0)), 5: (20, (0.0, 1.0)),
            6: (20, (0.0, 1.0)), 7: (20, (0.0, 1.0)), 8: (18, (0.0, 1.0)), 9: (18, (0.0, 0.5, 1.0)),
            10: (16, (0.0, 0.5, 1.0)), 11: (14, (0.0, 0.5, 1.0)), 12: (12, (0.0, 1.0)), 13: (12, (0.0, 1.0)),
            14: (10, (0.0, 1.0)), 15: (10, (0.0, 1.0)), 16: (8, (0.0, 1.0))}


def segment(k, bones, extras):
    """体節の外皮。前端は関節 k-1 を中心とする球面で前の殻（頭か前の体節）の内側へ入り、
    関節 k-1 で前の殻の外形 × SEAM、関節 k で次の体節の前端と同じ外形になる"""
    name = f"seg_{k:02d}"
    zc = (k - 0.5) * SEG
    cubes = []
    if k in SEG_PLAN:
        n, fracs = SEG_PLAN[k]
        angles = ring_angles(n)
        sf = s_of_joint(k - 1)
        sb = min(s_of_joint(k), TAIL_END)
        hw, cy, up, dn = ellipse(*section(sf))
        d = DOME * hw
        # 球面の輪: 球面の上の断面（中心の高さはそのまま、大きさ × DOME_F）を、前の殻の内側に収める
        ph, pt, pb = section(sf - d)
        margin = 0.04 * (pt - pb)
        top_d = min(cy + DOME_F * SEAM * up, pt - margin)
        bot_d = max(cy - DOME_F * SEAM * dn, pb + margin)
        dome = ellipse(min(DOME_F * SEAM * hw, 0.96 * ph), top_d, bot_d)
        rings = [(ring_pts(zs(sf - d), *dome, n), dome[1]),
                 (ring_pts(zs(sf), hw * SEAM, cy, up * SEAM, dn * SEAM, n), cy)]
        for f in fracs[1:]:
            s = sf + f * (sb - sf)
            e = ellipse(*section(s))
            rings.append((ring_pts(zs(s), *e, n), e[1]))
        thick = plate_thick(sf, sb)
        for j in range(len(rings) - 1):
            (r0, c0), (r1, c1) = rings[j], rings[j + 1]
            cubes += ring_loft(r0, r1, angles, f"s{k}b{j}_", thick, body_skin, (c0 + c1) / 2.0 * U,
                               sink=0.3 * (j % 2))
        if sb >= TAIL_END:
            hw, top, bot = section(TAIL_END)
            cubes.append(K.cube((-hw * U - 0.3, bot * U - 0.3, zs(TAIL_END) - 1.0),
                                (2 * hw * U + 0.6, (top - bot) * U + 0.6, 1.2), BACK, key="tailtip"))
    # k == 1: 頭と同じ向きに置かれ、頭の殻の内側に丸ごと入る位置合わせだけの骨
    cubes += extras.get(name, [])
    K.bone(bones, name, (0.0, 0.0, zc), cubes)


# ---- 鰓孔（seg_04 の外皮の上に 5 対）
GILL_S = (5.95, 6.25, 6.55, 6.85, 7.15)


def gills():
    cubes = []
    for i, s in enumerate(GILL_S):
        t0, t1 = (60.0, 110.0) if i < 3 else (64.0, 106.0)
        for sx, side in SIDES:
            pts = []
            for t in (t0, (t0 + t1) / 2.0, t1):
                (x, y), (nx, ny) = surface(s, t, sx)
                pts.append((x * U + nx * 0.25, y * U + ny * 0.25, zs(s)))
            for j in range(2):
                cubes.append(K.beam(pts[j], pts[j + 1], 0.6, 0.4, GILL, side=(0, 0, 1), key=f"g{side}{i}{j}",
                                    overshoot=0.15))
    return cubes


# ============================================================ 鰭（厚みのある肉質の板）
FIN_THICK = 0.9
CAUDAL_THICK = 0.7


def fin_panels(outline, normal, key, thick=FIN_THICK, lift=0.12):
    """閉じた外縁 outline（最後の点から最初の点へ付け根を通って戻る）の内側を板で埋める。
    板は外縁の各辺から外縁の重心まで張り、外端を辺にそろえる（辺に直交する向き）。重心と辺の作る三角形を
    丸ごと覆う幅にするので穴が開かない。隣どうしは面の法線方向へ少しずつずらして同じ平面に重ねない。
    板の局所 Y が外縁を向くので、FIN の絵の上端（縁の色）が外縁に来る"""
    root = tuple(sum(p[i] for p in outline) / len(outline) for i in range(3))
    pts = list(outline) + [outline[0]]
    out = []
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        chord = vdist(a, b)
        if chord < 0.05:
            continue
        e = vunit(vsub(b, a))
        d = vunit(vcross(normal, e))
        if vdot(d, vsub(vmid(a, b), root)) < 0:
            d = vmul(d, -1)
        length = vdot(vsub(a, root), d)                 # 重心から辺の線までの距離
        if length < 0.3:
            continue
        proj = vdot(vsub(root, a), e)                   # 重心の、辺に沿った位置（a から）
        lo, hi = min(0.0, proj) - 0.15, max(chord, proj) + 0.15
        off = lift * ((i % 4) - 1.5)
        centre = vadd(vadd(a, vmul(e, (lo + hi) / 2.0)), vadd(vmul(d, -length / 2.0), vmul(normal, off)))
        out.append(K.slab(centre, (thick, length, hi - lo), normal, d, FIN, key=f"{key}{i}"))
    return out


# 外縁の形（(p, q)、p は付け根に沿って後ろへ、q は体から外へ。付け根の長さ B と高さ H で割った値）。
# ミツクリザメの鰭: 背びれ2基は小さく低い丸形で同じくらい、腹びれ・臀びれは背びれより大きい丸形、
# 胸びれは小さく丸い。どれも後ろの端が少し付け根の後ろへ張り出す（遊離端）
DORSAL_SHAPE = ((0.0, 0.0), (0.05, 0.28), (0.14, 0.56), (0.27, 0.8), (0.42, 0.95), (0.58, 1.0), (0.72, 0.94),
                (0.83, 0.78), (0.92, 0.56), (1.0, 0.33), (1.08, 0.13), (1.12, 0.03), (0.92, 0.0))
ANAL_SHAPE = ((0.0, 0.0), (0.06, 0.3), (0.17, 0.6), (0.32, 0.84), (0.5, 0.97), (0.67, 0.98), (0.8, 0.86),
              (0.9, 0.66), (0.98, 0.42), (1.05, 0.2), (1.1, 0.05), (0.92, 0.0))
PEC_SHAPE = ((0.0, 0.0), (-0.02, 0.4), (0.08, 0.78), (0.26, 1.08), (0.5, 1.26), (0.76, 1.3), (0.98, 1.2),
             (1.12, 0.98), (1.15, 0.7), (1.05, 0.44), (0.85, 0.22), (0.6, 0.06), (0.4, 0.0))
PELVIC_SHAPE = ((0.0, 0.0), (0.04, 0.3), (0.14, 0.6), (0.3, 0.85), (0.5, 0.98), (0.7, 1.0), (0.86, 0.88),
                (0.98, 0.66), (1.06, 0.4), (1.1, 0.16), (1.08, 0.03), (0.85, 0.0))
DORSALS = ((6, 11.85, 1.85, 1.05), (9, 17.85, 1.85, 1.0))     # (体節, 付け根の前端 s, B, H)
ANAL = (10, 19.8, 2.0, 1.1)
PEC = (4, 7.45, 122.0, 1.3, 1.45)                             # (体節, s, 付け根の角度, B, H)
PELVIC = (7, 14.05, 150.0, 1.7, 1.15)


def median_fin(s0, length, height, shape, up, key):
    """正中線の鰭（背びれ・臀びれ）。付け根は体の背（腹）の線に沿う"""
    def at(p, q):
        s = s0 + p * length
        base = top_y(s) - 0.06 if up else bottom_y(s) + 0.06
        return P(0.0, base + (q if up else -q) * height, s)
    outline = [at(p, q) for p, q in shape]
    pivot = at(0.5, 0.0)
    return fin_panels(outline, (1.0, 0.0, 0.0), key), pivot


def paired_fin(s, t_deg, length, height, shape, sx, q_hint, p_hint, key):
    """対の鰭（胸びれ・腹びれ）。付け根は断面 s の角度 t の外皮。q_hint の向きへ張り出し、p_hint の向きへ流れる"""
    (x, y), _ = surface(s, t_deg, sx)
    base = P(x, y, s)
    p_dir = vunit((sx * p_hint[0], p_hint[1], p_hint[2]))
    q_dir = vunit((sx * q_hint[0], q_hint[1], q_hint[2]))
    q_dir = vunit(vsub(q_dir, vmul(p_dir, vdot(q_dir, p_dir))))
    normal = vcross(p_dir, q_dir)

    def at(p, q):
        return vadd(base, vadd(vmul(p_dir, p * length * U), vmul(q_dir, q * height * U)))
    outline = [at(p - 0.25, q - 0.12) for p, q in shape]     # 付け根は皮の内側へ少し沈める
    return fin_panels(outline, normal, key), base


# ---- 尾びれ。上葉は体の芯（BODY の尾）そのもの。芯の下に、浅い下葉の膜・欠刻・先の小さな葉が続く
CAUDAL_V = ((22.3, 0.0), (23.0, 0.32), (23.8, 0.66), (24.6, 0.92), (25.5, 1.08), (26.4, 1.08), (27.2, 0.92),
            (27.8, 0.66), (28.2, 0.34), (28.45, 0.22), (28.75, 0.46), (29.4, 0.5), (30.2, 0.38), (31.0, 0.22),
            (31.7, 0.08), (32.0, 0.0))
CAUDAL_STEP = 0.25


def caudal_depth(s):
    return lerp_table(CAUDAL_V, s, cols=(1,))[0]


def caudal_strips():
    """体節ごとの尾びれの板（{体節番号: [cube]}）。板の外端は外縁の辺にそろえる"""
    per = {}
    s = CAUDAL_V[0][0]
    i = 0
    while s < TAIL_END - 1e-6:
        s1 = min(TAIL_END, s + CAUDAL_STEP)
        v0, v1 = caudal_depth(s), caudal_depth(s1)
        if max(v0, v1) > 0.03:
            b0, b1 = P(0.0, bottom_y(s) + 0.08, s), P(0.0, bottom_y(s1) + 0.08, s1)
            o0, o1 = P(0.0, bottom_y(s) - v0, s), P(0.0, bottom_y(s1) - v1, s1)
            e = vunit(vsub(o1, o0))
            d = (0.0, -e[2], e[1])
            if d[1] > 0:
                d = vmul(d, -1)
            mo, mb = vmid(o0, o1), vmid(b0, b1)
            length = vdot(vsub(mo, mb), d)
            if length > 0.4:
                off = 0.1 * ((i % 4) - 1.5)
                centre = vadd(vsub(mo, vmul(d, length / 2.0)), (off, 0.0, 0.0))
                k = min(JOINTS, int(((s + s1) / 2.0 - SNOUT_AHEAD) // JOINT_SPACING) + 1)
                per.setdefault(k, []).append(
                    K.slab(centre, (CAUDAL_THICK, length, vdist(o0, o1) * 1.06 + 0.2), (1.0, 0.0, 0.0), d, FIN,
                           key=f"cd{i}"))
        s = s1
        i += 1
    return per


# ============================================================ アニメーション
IDLE = 3.0
ATTACK = 1.4
JAW_OUT = 0.3


def mirror(axis, s):
    """左右の鏡写し。X まわりはそのまま、Y・Z まわりは符号が返る"""
    return (axis[0], axis[1] * s, axis[2] * s)


def _ease_out(x):
    return 1.0 - (1.0 - x) ** 3


def _ease_in_out(x):
    return 0.5 - 0.5 * math.cos(math.pi * x)


def attack_f(t):
    """攻撃の突き出し量 0..1。溜め → 一気に突き出す → 噛む間だけ止まる → ゆっくり戻す"""
    if t <= 0.10:
        return 0.0
    if t <= 0.28:
        return _ease_out((t - 0.10) / 0.18)
    if t <= 0.45:
        return 1.0
    if t <= 1.15:
        return 1.0 - _ease_in_out((t - 0.45) / 0.70)
    return 0.0


ATTACK_T = (0.0, 0.10, 0.13, 0.16, 0.19, 0.22, 0.25, 0.28, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95, 1.05, 1.15, ATTACK)
JAW_OUT_T = (0.0, 0.05, 0.10, 0.15, 0.20, JAW_OUT)


def jaw_tracks(times, fn):
    """突き出し量 fn(t) を時刻ごとに標本化し、jaw の position と jaw_sling の rotation / scale を線形のキーで書く
    （3つのトラックが同じ時刻・同じ補間なので、膜の後ろ端は喉から離れない）"""
    jp, jr, sr, ss = [], [], [], []
    for t in times:
        f = fn(t)
        jp.append((t, tuple(v * U * f for v in JAW_D)))
        jr.append((t, (JAW_TILT * f, 0.0, 0.0)))
        rx, sz = sling_pose(f)
        sr.append((t, (rx, 0.0, 0.0)))
        ss.append((t, (1.0, 1.0, sz)))
    return {"jaw": boss_kit.merge(pos(jp), rot(jr)), "jaw_sling": boss_kit.merge(rot(sr), scale(ss))}


def animations(bones):
    names = {b["name"] for b in bones}
    idle = {
        # 下顎: 閉じた口が呼吸でわずかに開く（rx が正で開く）
        "jaw_lower": rot([(0.0, (0, 0, 0)), (1.5, (4, 0, 0), EASE_SOFT), (IDLE, (0, 0, 0), EASE_SOFT)]),
    }
    for s, side in SIDES:
        idle[f"fin_{side}"] = wave(IDLE, 5.0, 0.0, mirror((0.35, 0.25, 1.0), s))
        idle[f"fin_pelvic_{side}"] = wave(IDLE, 3.5, 1.2, mirror((0.3, 0.4, 1.0), s))
    for k in range(1, JOINTS + 1):
        name = f"seg_{k:02d}_fin"
        if name in names:
            if k in CAUDAL_SEGS:
                idle[name] = wave(IDLE, 1.5 + 0.5 * (k - CAUDAL_SEGS[0]), -0.7 * k, (0, 1, 0))   # 下葉を左右に煽る
            else:
                idle[name] = wave(IDLE, 3.0, -0.5 * k, (0, 0, 1))                              # 背びれ
        name = f"seg_{k:02d}_anal"
        if name in names:
            idle[name] = wave(IDLE, 3.0, -0.5 * k + 1.0, (0, 0, 1))

    attack = jaw_tracks(ATTACK_T, attack_f)
    attack["jaw_lower"] = rot([(0.0, (0, 0, 0)), (0.10, (14, 0, 0), EASE_SLOW), (0.24, (52, 0, 0), EASE_BACK),
                               (0.30, (52, 0, 0)), (0.36, (-3, 0, 0), EASE_FAST), (0.42, (0, 0, 0), EASE_BACK),
                               (ATTACK, (0, 0, 0))])

    jaw_out = jaw_tracks(JAW_OUT_T, lambda t: _ease_out(min(1.0, t / 0.2)))
    jaw_out["jaw_lower"] = rot([(0.0, (0, 0, 0)), (0.2, (40, 0, 0), EASE_BACK), (JAW_OUT, (40, 0, 0))])

    return _no_negative_zero({
        "idle": {"loop": True, "animation_length": IDLE, "bones": idle},
        "attack": {"loop": False, "animation_length": ATTACK, "bones": attack},
        "jaw_out": {"loop": "hold_on_last_frame", "animation_length": JAW_OUT, "bones": jaw_out},
    })


def _no_negative_zero(d):
    if isinstance(d, dict):
        return {k: _no_negative_zero(v) for k, v in d.items()}
    if isinstance(d, list):
        return [_no_negative_zero(v) for v in d]
    if isinstance(d, float) and d == 0.0:
        return 0.0
    return d


# ============================================================ 書き出し
CAUDAL_SEGS = tuple(range(11, JOINTS + 1))


def build_geo():
    bones = []
    K.bone(bones, "head", (0.0, 0.0, 0.0), head_cubes())
    jaw_bones(bones)
    extras = {"seg_04": gills()}
    caudal = caudal_strips()
    for k in range(1, JOINTS + 1):
        segment(k, bones, extras)
        name = f"seg_{k:02d}"
        for kk, s0, length, height in DORSALS:
            if kk == k:
                cubes, pivot = median_fin(s0, length, height, DORSAL_SHAPE, True, f"d{k}_")
                K.bone(bones, f"{name}_fin", pivot, cubes, parent=name)
        if ANAL[0] == k:
            cubes, pivot = median_fin(ANAL[1], ANAL[2], ANAL[3], ANAL_SHAPE, False, "an")
            K.bone(bones, f"{name}_anal", pivot, cubes, parent=name)
        if PEC[0] == k:
            for sx, side in SIDES:
                cubes, pivot = paired_fin(PEC[1], PEC[2], PEC[3], PEC[4], PEC_SHAPE, sx, (0.8, -0.6, 0.0),
                                          (0.12, -0.08, 1.0), f"pec{side}")
                K.bone(bones, f"fin_{side}", pivot, cubes, parent=name)
        if PELVIC[0] == k:
            for sx, side in SIDES:
                cubes, pivot = paired_fin(PELVIC[1], PELVIC[2], PELVIC[3], PELVIC[4], PELVIC_SHAPE, sx,
                                          (0.45, -0.89, 0.0), (0.15, -0.2, 1.0), f"pel{side}")
                K.bone(bones, f"fin_pelvic_{side}", pivot, cubes, parent=name)
        if k in caudal:
            sf = s_of_joint(k - 1)
            K.bone(bones, f"{name}_fin", P(0.0, bottom_y(sf), sf), caudal[k], parent=name)
    width = (TOTAL * U) / 16.0
    return K.model(f"geometry.{NAME}", bones, round(width + 4.0, 1), round(6.0 * U / 16.0 + 2.0, 1), 0), bones


def contract(bones, cubes, anims):
    def entry(b):
        e = {"name": b["name"], "parent": b.get("parent"), "pivot": b["pivot"]}
        if b["name"] == "head":
            e["role"] = "頭。関節 0 に pivot。-Z が吻端（関節 0 の 1.75 ブロック前）。殻の後ろ端は関節 1"
        elif b["name"].startswith("seg_") and not b.get("parent"):
            k = int(b["name"][4:])
            e["role"] = f"体節 {k}: 関節 {k - 1}（前）〜関節 {k}（後ろ）。pivot は中心"
            if k == 1:
                e["role"] += "。頭の殻の内側に入るので箱なし（位置だけ合わせる）"
            e["joint_front"], e["joint_back"] = k - 1, k
        return e
    f1 = sling_pose(1.0)
    return {
        "entity": NAME,
        "generator": "tools/boss/goblin_shark_model/build.py（手で直さず、生成元を直して流し直す）",
        "geo": f"assets/abyssia/geo/entity/{NAME}.geo.json",
        "animation": f"assets/abyssia/animations/entity/{NAME}.animation.json",
        "texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo.png",
        "texture_note": "仮テクスチャ（atlas.py の領域に沿って塗っただけ）。発光は無いので _glow.png は無い",
        "texture_size": [atlas.SIZE, atlas.SIZE],
        "model_scale": MODEL_SCALE,
        "units": "geo の 1 単位 = 1/16 ブロック。描画で MODEL_SCALE 倍するので、ゲーム内では MODEL_SCALE/16 ブロック",
        "segment_length_units": SEG,
        "segment_length_blocks": JOINT_SPACING,
        "joints": JOINTS + 1,
        "segments": JOINTS,
        "rest_pose": ("頭から尾まで一直線。関節 j は geo の (0, 0, segment_length_units * j)。"
                      "頭は -Z を向く。体の芯（関節の線）は y = 0"),
        "head_pivot": [0.0, 0.0, 0.0],
        "segment_pivot": "seg_k の pivot = (0, 0, segment_length_units * (k - 0.5))",
        "extent_units": {"snout_z": round(zs(0.0), 2), "tail_z": round(zs(TOTAL), 2),
                         "total_length_blocks": TOTAL,
                         "max_half_width": round(max(r[1] for r in BODY) * U, 2),
                         "max_top_y": round(max(r[2] for r in BODY) * U, 2),
                         "min_bottom_y": round(min(r[3] for r in BODY) * U, 2)},
        "jaw": {
            "bones": "jaw（head の子: 上顎・歯・口蓋）→ jaw_lower（下顎）・jaw_sling（喉の膜）",
            "hinge": [round(v, 2) for v in P(*HINGE[:2], HINGE[2])],
            "protrusion_units": [round(v * U, 2) for v in JAW_D],
            "protrusion_blocks": round(JAW_REACH, 3),
            "rest_jaw_tip_z": round(zs(UJ_FRONT), 2),
            "full_jaw_tip": [0.0, round((UJ_Y + JAW_D[1]) * U, 2), round(zs(UJ_FRONT) + JAW_D[2] * U, 2)],
            "tilt_deg": JAW_TILT,
            "sling_rule": ("jaw / jaw_lower / jaw_sling の pivot はどれも顎関節。jaw_sling は静止で長さ 1 ブロック。"
                           "突き出し量 f のとき jaw は position = f × protrusion_units、rotation x = 12f 度。"
                           "jaw_sling は rotation x = atan2(2.4f, 1 + 5.5f) - 12f 度、scale z = hypot(2.4f, 1 + 5.5f)。"
                           f"f=1 で {f1[0]:.2f} 度・{f1[1]:.3f} 倍。3 つを同じ時刻に入れる（animation はそうしてある）"),
        },
        "animation_signs": {
            "jaw": "position（geo 座標、単位）と rotation x（正で前下へ傾く）。静止 (0,0,0)、突き出し (0, -19.2, -44) と 12 度",
            "jaw_lower": "rotation の x が正で口が開く（idle は 0→4 度、attack は 52 度まで開いて噛む）",
            "jaw_sling": "rotation x と scale z。jaw の突き出しに合わせて伸びる（上の sling_rule）",
            "fin_l / fin_r": "胸びれ（seg_04 の子）。付け根が pivot。左右で y と z の符号が逆",
            "fin_pelvic_l / fin_pelvic_r": "腹びれ（seg_07 の子）。付け根が pivot。左右で y と z の符号が逆",
            "seg_06_fin / seg_09_fin": "第1・第2背びれ。pivot は付け根の中央、z で左右に揺れる",
            "seg_10_anal": "臀びれ。pivot は付け根の中央、z で左右に揺れる",
            "seg_11_fin..seg_16_fin": "尾びれの下葉の膜（体節ごと）。pivot は体節の前端の腹、y で左右に煽る",
        },
        "bone_rule": ("head と seg_01..seg_16 は root 直下。毎フレーム位置と回転を上書きする。"
                      "seg_k は関節 k-1 と k の中点へ、局所 -Z が関節 k から関節 k-1 を向くように置く。"
                      "head は関節 0 へ、-Z が関節 1 から関節 0 の向き（さらに先）を向くように置く"
                      "（seg_01 と同じ向きになるので、頭と seg_01 は常に一体）。"
                      "子（jaw, jaw_lower, jaw_sling, fin_*, seg_XX_fin, seg_XX_anal）は animation に任せる"),
        "geckolib_conventions": [
            "描画空間 = geo の X を反転した空間（GeckoLib 4.9.2 BakedModelFactory）。z と y はそのまま",
            "GeoBone.setPosX/Y/Z は静止姿勢からのずれで、単位は geo と同じ 1/16（MODEL_SCALE を掛ける前）。"
            "RenderUtil.translateMatrixToBone が translate(-posX/16, posY/16, posZ/16) なので、"
            "geo の座標で書けば pos = 目標の pivot - 静止姿勢の pivot（X も符号そのまま）",
            "GeoBone.setRotX/Y/Z はラジアンで描画空間の値。geo / animation の度数 (rx, ry, rz) とは "
            "(-rx, -ry, +rz) の関係。頂点へは X→Y→Z の順に効く（行列は Rz・Ry・Rx）",
            "描画空間で局所 -Z（体節の前）を単位ベクトル d（関節 k から k-1 へ）に向けるには "
            "setRotX(asin(d.y))、setRotY(atan2(-d.x, -d.z))、setRotZ(0)。"
            "体節と head は静止姿勢の回転が 0 なので、この値をそのまま入れればよい",
            "子の Bone は animation が回すので、Java からは触らない（触るなら animation の値を上書きしてしまう）",
        ],
        "animations": {name: {"loop": a["loop"], "length": a["animation_length"],
                              "bones": sorted(a["bones"])} for name, a in anims.items()},
        "cube_count": cubes,
        "cube_limit": CUBE_LIMIT,
        "bones": [entry(b) for b in bones],
    }


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    geo, bones = build_geo()
    anims = animations(bones)
    print(boss_kit.verify(NAME, geo, anims))
    cubes = sum(len(b["cubes"]) for b in bones)
    assert cubes <= CUBE_LIMIT, f"cube が多すぎる: {cubes}（上限 {CUBE_LIMIT}）"
    for d in (GEO, ANIM, TEX):
        d.mkdir(parents=True, exist_ok=True)
    (GEO / f"{NAME}.geo.json").write_text(json.dumps(geo, indent=1), encoding='utf-8')
    (ANIM / f"{NAME}.animation.json").write_text(
        json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1), encoding='utf-8')
    atlas.write_png(TEX / f"{NAME}_geo.png", atlas.build())
    (HERE / 'contract.json').write_text(json.dumps(contract(bones, cubes, anims), indent=1, ensure_ascii=False),
                                        encoding='utf-8')
    by = {b["name"]: b for b in bones}
    per = {}
    for b in bones:
        root = b["name"]
        while by[root].get("parent"):
            root = by[root]["parent"]
        key = "head" if root == "head" else "body"
        per[key] = per.get(key, 0) + len(b["cubes"])
    jaw = sum(len(by[n]["cubes"]) for n in ("jaw", "jaw_lower", "jaw_sling"))
    print(f"cubes: 計 {cubes}（頭 {per.get('head', 0)}、うち顎 {jaw} / 体 {per.get('body', 0)}）、上限 {CUBE_LIMIT}、"
          f"MODEL_SCALE {MODEL_SCALE:g}、体節 {SEG:g} 単位、全長 {TOTAL:g} ブロック、顎の突き出し {JAW_REACH:.2f} ブロック")
    print("次に流すこと:")
    print(f"  python tools/boss/check_animations.py {NAME}")
    print("  python tools/boss/goblin_shark_model/preview.py")


if __name__ == '__main__':
    main()
