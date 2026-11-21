# -*- coding: utf-8 -*-
"""大クラーケン（abyss_kraken）の GeckoLib モデル一式を書き出す。

```bash
python tools/boss/kraken_model/build.py
```

書き出すもの（どれも丸ごと上書き。手で直さず、ここを直して流し直す）:

- `assets/abyssia/geo/entity/abyss_kraken.geo.json`
- `assets/abyssia/animations/entity/abyss_kraken.animation.json`（idle だけ）
- `assets/abyssia/textures/entity/boss/abyss_kraken_geo.png` と `_glow.png`
- `tools/boss/kraken_model/contract.json`（描画の Java 側との約束）

<h2>骨の約束（Java の描画が鎖の形に置く）</h2>
`mantle`（外套膜・ひれ・頭・目・嘴・漏斗を子に持つ本体）と、腕の体節 `arm_A_SS`
（A = 1..8、SS = 01..15）は<b>すべて root 直下</b>。腕 A は AbyssKraken の tentacle(A - 1)。
腕の静止姿勢は一直線で、関節 j は ARM_REST[A] + (0, 0, SEG * j)。体節 k は関節 k-1（付け根側）
から関節 k（先端側）までで、pivot は体節の中心。局所 -Z が付け根側、局所 -Y が吸盤面。
Java は毎フレーム、各体節を関節の中点へ運び、-Z を関節 k-1 へ、-Y を本体の内側へ向ける。
うねりは Java が入れるので、animation には腕を入れない。

<h2>継ぎ目は袖</h2>
体節の板は、前の体節の袖の下へ差し込む細い前端（`TUCK`）から、関節を越えて次の体節へ被さる
一回り太い後端（`SLEEVE`）まで 1 枚で張る。曲げると外側の継ぎ目が開くが、前後に重なって
いるので、隙間の奥は次の体節の皮が見えるだけで向こうが抜けない。体節あたりの箱を 8 枚に
抑えるための形で、腕には段の付いた環が並ぶ（頭足類の腕の環節に見える）。

<h2>切断</h2>
各腕の 3 番目の体節（切断後も残る関節 0..3 の最後）に、切断面の肉の蓋を持つ子の骨
`arm_A_stump` を付ける。geo では `neverRender: true`（GeckoLib が隠した状態で焼く）。
切断された腕は Java が `arm_A_04`..`arm_A_15` を隠し、`arm_A_stump` を見せる。

<h2>寸法</h2>
実寸は AbyssKraken.java（触手 8 本、関節 16、関節間隔 5.5、付け根の輪 半径 3・高さ -6）と、
旧描画 AbyssKrakenRenderer.java（外套膜・頭・ひれの loft 表、armRadius、棍棒の鉤、眼の位置）から
取った。モデルは MODEL_SCALE 分の 1 に縮めて作り、描画側で一様に MODEL_SCALE 倍する。
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
from atlas import (MANTLE, HEAD, ARM_BACK, ARM_SIDE, FIN, ARM_RIM, SUCKERS, INNER, EYE, SOCKET, CAVITY, BEAK,
                   HOOK, FLESH, FLESH_RAW, SIPHON)
from boss_kit import (_add as vadd, _sub as vsub, _mul as vmul, _unit as vunit, _cross as vcross,
                      _dot as vdot, _mid as vmid, _dist as vdist, EASE_SOFT)

ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/abyssia'
GEO = ASSETS / 'geo/entity'
ANIM = ASSETS / 'animations/entity'
TEX = ASSETS / 'textures/entity/boss'
NAME = 'abyss_kraken'

K = boss_kit.Kit(atlas, stretched=atlas.STRETCHED, top_anchored=atlas.TOP_ANCHORED)

# ============================================================ 実寸
MODEL_SCALE = 4.0           # 描画側で一様に掛ける倍率。geo の 1 単位 = MODEL_SCALE / 16 ブロック
U = 16.0 / MODEL_SCALE      # 1 ブロックが何単位か（4）
ARMS = 8                    # AbyssKraken.TENTACLES
JOINTS = 16                 # AbyssKraken.JOINTS（腕 1 本の関節の数）
SEGS = JOINTS - 1           # 腕 1 本の体節の数
JOINT_SPACING = 5.5         # ブロック（AbyssKraken.SPACING）
SEG = JOINT_SPACING * U     # 体節の長さ（22 単位）
CLUB_START = JOINTS - 5     # 棍棒が膨らみ始める関節（AbyssKrakenRenderer.CLUB_START）
LIVE_WHEN_SEVERED = 4       # 切断後に残る関節の数（AbyssKraken.liveJoints）
CUT_SEG = LIVE_WHEN_SEVERED - 1     # 切断後に残る最後の体節（3）
ANCHOR_Y = -6.0 * U         # 腕の付け根の輪の高さ（AbyssKraken.ANCHOR_Y）
ANCHOR_R = 3.0 * U          # 付け根の輪の半径（AbyssKraken.ANCHOR_R）

# 腕の断面（旧描画の tube と同じ比）: 幅は半径の 1.08、背は 1.0、吸盤面は 0.92
ARM_W, ARM_UP, ARM_DOWN = 1.08, 1.0, 0.92
ARM_E = 2.0                 # 断面の超楕円の指数（2 で楕円）
ARM_SIDES = 8               # 断面の角の数
TUCK, TUCK_LEN = 0.9, 0.5   # 前端（前の袖の下へ差し込む）の太さと、関節より前へ出る長さ（半径に対して）
SLEEVE, SLEEVE_LEN = 1.04, 0.4      # 後端の袖の太さと、関節より後ろへ出る長さ（半径に対して）

# 腕の静止姿勢: 8 本を X に並べ、どれも +Z（付け根→先端）へ一直線
ARM_REST_DX = 22.0
ARM_REST_Y = -64.0
ARM_REST = {A: ((A - 4.5) * ARM_REST_DX, ARM_REST_Y, 0.0) for A in range(1, ARMS + 1)}

# 棍棒の鉤（体節の番号: 体節の中の位置 0..1）。旧描画は関節 11.4〜14.4 の吸盤面に 2 列
HOOKS = {12: (0.3, 0.75), 13: (0.3, 0.75), 14: (0.35,)}

SIDES = ((1, "l"), (-1, "r"))   # +X が左

# 旧描画の loft 表（ブロック、外套膜の空間: 原点は胴の芯、+Y が外套膜の先）
MANTLE_T = ((-2.6, 5.0), (-1.6, 6.4), (1.5, 6.8), (5.0, 6.4), (9.0, 5.1), (12.0, 3.5), (14.5, 1.7), (15.9, 0.15))
HEAD_T = ((-8.3, 1.5), (-7.6, 3.4), (-6.0, 4.6), (-4.0, 5.0), (-2.4, 4.4), (-0.8, 4.2))
# ひれの外縁（高さ, 軸からの張り出し）。旧描画の FIN 表
FIN_T = ((6.0, 1.0), (7.5, 8.6), (10.0, 10.8), (13.0, 7.6), (15.2, 1.0))


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def lerp(a, b, t):
    return a + (b - a) * t


def arm_radius(k):
    """関節座標 k での腕の半径（単位）。AbyssKrakenRenderer.armRadius と同じ式"""
    w = lerp(3.6, 1.1, k / (JOINTS - 1))
    c = k - CLUB_START
    if c > 0:
        w += 0.9 * math.sin(math.pi * min(c / 4.6, 1.0))
    tip = JOINTS - 1 - k
    if tip < 1.2:
        w *= max(0.12, tip / 1.2)
    return w * 0.5 * U


def pchip(table, x):
    """(x, y) の表を形を保つ 3 次で補間する（行き過ぎない）"""
    xs = [p[0] for p in table]
    ys = [p[1] for p in table]
    n = len(xs)
    h = [xs[i + 1] - xs[i] for i in range(n - 1)]
    d = [(ys[i + 1] - ys[i]) / h[i] for i in range(n - 1)]
    m = [d[0]] + [0.0] * (n - 2) + [d[-1]]
    for i in range(1, n - 1):
        if d[i - 1] * d[i] > 0:
            w1, w2 = 2 * h[i] + h[i - 1], h[i] + 2 * h[i - 1]
            m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
    if x <= xs[0]:
        return ys[0]
    if x >= xs[-1]:
        return ys[-1]
    for i in range(n - 1):
        if xs[i] <= x <= xs[i + 1]:
            t = (x - xs[i]) / h[i]
            return ((2 * t ** 3 - 3 * t ** 2 + 1) * ys[i] + (t ** 3 - 2 * t ** 2 + t) * h[i] * m[i]
                    + (-2 * t ** 3 + 3 * t ** 2) * ys[i + 1] + (t ** 3 - t ** 2) * h[i] * m[i + 1])
    return ys[-1]


def mantle_r(y):
    return pchip([(a * U, b * U) for a, b in MANTLE_T], y)


def head_r(y):
    return pchip([(a * U, b * U) for a, b in HEAD_T], y)


def translate(cubes, d):
    """箱を平行移動する（origin と回転の pivot）"""
    for c in cubes:
        c["origin"] = [round(c["origin"][i] + d[i], 2) for i in range(3)]
        if "pivot" in c:
            c["pivot"] = [round(c["pivot"][i] + d[i], 2) for i in range(3)]
    return cubes


def _abs_angle(t):
    return abs(((t + 180.0) % 360.0) - 180.0)


# ============================================================ 断面と外皮
def ring(z, a, up, down, t0, t1, n, e=2.0):
    """超楕円の縁の点を n+1 個（Z 軸の管）。t は真上が 0、+X 側が正"""
    pts = []
    for i in range(n + 1):
        t = math.radians(t0 + (t1 - t0) * i / n)
        s, c = math.sin(t), math.cos(t)
        x = a * math.copysign(abs(s) ** (2.0 / e), s)
        b = up if c >= 0 else down
        y = b * math.copysign(abs(c) ** (2.0 / e), c)
        pts.append((x, y, z))
    return pts


def ring_angles(n):
    step = 360.0 / n
    return [-180.0 + step * i for i in range(n)]


def ring_loft(r0, r1, angles, key, thick, skin, lap=1.12, overlap=1.04, sink=0.0):
    """Z 軸の管の 2 つの断面の縁どうしを板で結ぶ（大海蛇の build.py と同じ置き方）。

    r0 が前（-Z 側）、r1 が後ろ。板の局所 X は管の長さ方向、局所 Z は「面に沿って上る向き」（絵の上端）。
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
        if vdot(normal, (centre[0], centre[1], 0.0)) < 0:
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


def y_ring(y, r, n, phase=0.0):
    """Y 軸の環の縁の点を n+1 個。角は +Z（腹側、漏斗の側）が 0、+X 側が正。板の中心が phase + 360i/n"""
    step = 360.0 / n
    pts = []
    for i in range(n + 1):
        t = math.radians(phase - step / 2.0 + step * i)
        pts.append((r * math.sin(t), y, r * math.cos(t)))
    return pts


def y_loft(s0, s1, n, key, thick, skin, phase=0.0, lap=1.1, overlap=1.05, sink=0.0):
    """Y 軸の環（s = (高さ, 半径)）どうしを板で結ぶ。外套膜・頭・唇。

    板の局所 Y が外向きの法線、局所 Z が子午線に沿って上る向き（絵の上端は外套膜の先の側）。
    skin(角, 中心) が (領域, tone) を返す。
    """
    r0, r1 = y_ring(s0[0], s0[1], n, phase), y_ring(s1[0], s1[1], n, phase)
    step = 360.0 / n
    out = []
    for i in range(n):
        a0, b0, a1, b1 = r0[i], r0[i + 1], r1[i], r1[i + 1]
        m0, m1 = vmid(a0, b0), vmid(a1, b1)
        along = vsub(m1, m0)
        length = math.sqrt(vdot(along, along))
        along = vunit(along)
        across = vadd(vsub(b0, a0), vsub(b1, a1))
        across = vunit(vsub(across, vmul(along, vdot(along, across))))
        normal = vcross(across, along)
        centre = vmid(m0, m1)
        if vdot(normal, (centre[0], 0.0, centre[2])) < 0:
            normal = vmul(normal, -1)
        up = along if along[1] >= 0 else vmul(along, -1)
        reg, tone = skin(phase + step * i, centre)
        width = max(vdist(a0, b0), vdist(a1, b1)) * lap
        c = vsub(centre, vmul(normal, thick / 2.0 + sink))
        out.append(K.slab(c, (width, thick, length * overlap), vcross(normal, up), normal, reg,
                          tone=tone, key=f"{key}{i}"))
    return out


def tube_loft(c0, c1, r0, r1, n, key, thick, region, tone=None, roll=(0.0, 1.0, 0.0), lap=1.1, overlap=1.04):
    """c0 から c1 へ、半径 r0 → r1 の n 角の管（眼の縁・漏斗）。絵の上端は c1 の側"""
    axis = vunit(vsub(c1, c0))
    e1 = vsub(roll, vmul(axis, vdot(axis, roll)))
    e1 = vunit(e1 if vdot(e1, e1) > 1e-6 else vcross(axis, (1.0, 0.0, 0.0)))
    e2 = vcross(axis, e1)
    step = 2.0 * math.pi / n

    def pt(c, r, i):
        t = step * (i - 0.5)
        return vadd(c, vadd(vmul(e1, r * math.cos(t)), vmul(e2, r * math.sin(t))))

    out = []
    for i in range(n):
        a0, b0, a1, b1 = pt(c0, r0, i), pt(c0, r0, i + 1), pt(c1, r1, i), pt(c1, r1, i + 1)
        m0, m1 = vmid(a0, b0), vmid(a1, b1)
        along = vsub(m1, m0)
        length = math.sqrt(vdot(along, along))
        along = vunit(along)
        across = vadd(vsub(b0, a0), vsub(b1, a1))
        across = vunit(vsub(across, vmul(along, vdot(along, across))))
        normal = vcross(across, along)
        centre = vmid(m0, m1)
        radial = vsub(vsub(centre, c0), vmul(axis, vdot(vsub(centre, c0), axis)))
        if vdot(normal, radial) < 0:
            normal = vmul(normal, -1)
        width = max(vdist(a0, b0), vdist(a1, b1)) * lap
        c = vsub(centre, vmul(normal, thick / 2.0))
        out.append(K.slab(c, (width, thick, length * overlap), vcross(normal, along), normal, region,
                          tone=tone, key=f"{key}{i}"))
    return out


def octa_beam(p0, p1, a, region, key, tone=None, roll=(1.0, 0.0, 0.0)):
    """断面が八芒形の棒（正方形の棒を 2 本、45 度ずらして重ねる）。a は正方形の半辺"""
    d = vunit(vsub(p1, p0))
    s0 = vunit(vsub(roll, vmul(d, vdot(d, roll))))
    s1 = vunit(vadd(s0, vcross(d, s0)))
    return [K.beam(p0, p1, 2 * a, 2 * a, region, side=s0, tone=tone, key=f"{key}a"),
            K.beam(p0, p1, 2 * a, 2 * a, region, side=s1, tone=tone, key=f"{key}b")]


def bezier(p0, p1, p2, s):
    return tuple((1 - s) ** 2 * p0[i] + 2 * (1 - s) * s * p1[i] + s * s * p2[i] for i in range(3))


def horn(p0, p1, p2, w0, key, region, cuts, taper):
    """曲がった角（嘴）。2次ベジェを数本の棒に分け、先ほど細くする。棒は菱形に向ける"""
    out = []
    for i in range(len(cuts) - 1):
        a, b = bezier(p0, p1, p2, cuts[i]), bezier(p0, p1, p2, cuts[i + 1])
        d = vunit(vsub(b, a))
        side = vadd(vunit(vcross(d, (0.0, 1.0, 0.0)) if abs(d[1]) < 0.95 else (1.0, 0.0, 0.0)), (0.0, 1.0, 0.0))
        w = w0 * taper[i]
        out.append(K.beam(a, b, w, w * 0.8, region, side=side, key=f"{key}{i}", overshoot=1.0 if i else 0.0))
    return out


# ============================================================ 腕
def arm_ring(z, r):
    step = 360.0 / ARM_SIDES
    return ring(z, ARM_W * r, ARM_UP * r, ARM_DOWN * r, -180 - step / 2, 180 - step / 2, ARM_SIDES, ARM_E)


def sucker_region(length, width):
    """吸盤 2 列が丸く見える領域。板の縦横比 2L/W に、1 列あたりの数がいちばん近いもの"""
    want = 2.0 * length / max(width, 0.5)
    return min(SUCKERS, key=lambda nr: abs(math.log(nr[0] / want)))[1]


def arm_skin(sucker):
    def skin(t, _n):
        at = _abs_angle(t)
        if at < 22.5:
            return ARM_BACK, 0.1          # 背（反口側）の正中。いちばん暗い
        if at < 67.5:
            return ARM_BACK, 0.8
        if at < 112.5:
            return ARM_SIDE, 0.5          # 脇。淡い色素胞
        if at < 157.5:
            return ARM_RIM, None          # 吸盤面の縁
        return sucker, None               # 吸盤面（局所 -Y）
    return skin


def arm_frame(k):
    """体節 k の寸法（腕の局所座標: 関節 0 が原点、付け根→先端が +Z）"""
    zf, zb = SEG * (k - 1), SEG * k
    rf, rb = arm_radius(k - 1), arm_radius(k)
    tip = k == SEGS
    z0 = zf - TUCK_LEN * rf
    if tip:
        z1, r1 = zf + 0.32 * SEG, arm_radius(k - 1 + 0.32)
    else:
        z1, r1 = zb + SLEEVE_LEN * rb, SLEEVE * rb
    return {"zf": zf, "zb": zb, "zc": (zf + zb) / 2.0, "rf": rf, "rb": rb, "tip": tip,
            "z0": z0, "z1": z1, "r0": TUCK * rf, "r1": r1, "sleeve": SLEEVE_LEN * rb}


def arm_surface(f, z):
    """体節の板の太さ（半径）を z で"""
    return f["r0"] + (f["r1"] - f["r0"]) * clamp((z - f["z0"]) / (f["z1"] - f["z0"]), 0.0, 1.0)


def arm_thick(f):
    return max(0.8, 0.12 * (f["r0"] + f["r1"]) / 2.0)


def arm_segment(A, k):
    f = arm_frame(k)
    key = f"a{A}s{k}"
    r0, r1 = arm_ring(f["z0"], f["r0"]), arm_ring(f["z1"], f["r1"])
    width = max(vdist(r0[0], r0[1]), vdist(r1[0], r1[1])) * 1.12      # 吸盤面（-Y）の板の幅
    sucker = sucker_region((f["z1"] - f["z0"]) * 1.04, width)
    cubes = ring_loft(r0, r1, ring_angles(ARM_SIDES), key, arm_thick(f), arm_skin(sucker))
    if f["tip"]:
        # 先端: 細る円錐を八芒形の棒 3 段で尖らせる
        zf = f["zf"]
        cubes += octa_beam((0, 0, zf + 0.27 * SEG), (0, 0, zf + 0.62 * SEG), 0.95 * arm_radius(k - 1 + 0.45),
                           ARM_SIDE, f"{key}t1", tone=0.5)
        cubes += octa_beam((0, 0, zf + 0.58 * SEG), (0, 0, zf + 0.9 * SEG), 0.95 * arm_radius(k - 1 + 0.73),
                           ARM_SIDE, f"{key}t2", tone=0.5)
        cubes.append(K.beam((0, 0, zf + 0.86 * SEG), (0, 0, f["zb"] + 1.5), 0.9, 0.9, ARM_BACK,
                            side=(1, 1, 0), tone=0.5, key=f"{key}t3"))
    # 棍棒の鉤: 吸盤面の 2 列。吸盤面から外へ立ち、先が付け根の側へ曲がる
    for j, q in enumerate(HOOKS.get(k, ())):
        z = f["zf"] + q * SEG
        r = arm_surface(f, z)
        length = 1.05 * arm_radius(k - 1 + q)
        for s, side in SIDES:
            base = (s * 0.42 * ARM_W * r, -0.85 * ARM_DOWN * r + 0.5, z)
            tip = vadd(base, vmul(vunit((s * 0.12, -0.92, -0.38)), length))
            cubes.append(K.beam(base, tip, 1.7, 1.7, HOOK, side=(1, 0, 0), key=f"{key}h{j}{side}"))
    return translate(cubes, ARM_REST[A]), f


def stump(A):
    """切断面の蓋。3 番目の体節の袖の端を、扇に開いた肉の板 8 枚で塞ぎ、真ん中に生肉が盛り上がる"""
    f = arm_frame(CUT_SEG)
    rs = f["r1"]                                # 袖の端の太さ
    zc = f["z1"] - 0.6
    margin = 0.6 * arm_thick(f)
    rays = []
    for i in range(ARM_SIDES + 1):
        ang = -180.0 - 22.5 + 45.0 * i
        t = math.radians(ang)
        b = ARM_UP if math.cos(t) >= 0 else ARM_DOWN
        rho = 1.0 / math.sqrt((math.sin(t) / ARM_W) ** 2 + (math.cos(t) / b) ** 2) * rs
        rays.append((ang, rho - margin))
    cubes = K.fan((0.0, 0.0, zc), (0, 1, 0), (1, 0, 0), rays, f"a{A}cut", FLESH, FLESH, tip_region=FLESH,
                  ray_w=0, lift=0.1)
    cubes += octa_beam((0, 0, zc - 0.8), (0, 0, zc + 1.6), 0.42 * rs, FLESH_RAW, f"a{A}bulge",
                       roll=(1.0, 1.0, 0.0))
    return translate(cubes, ARM_REST[A])


def arms(bones):
    frames = {}
    for A in range(1, ARMS + 1):
        x, y, z = ARM_REST[A]
        for k in range(1, SEGS + 1):
            cubes, f = arm_segment(A, k)
            K.bone(bones, f"arm_{A}_{k:02d}", (x, y, z + f["zc"]), cubes)
            frames[k] = f
            if k == CUT_SEG:
                K.bone(bones, f"arm_{A}_stump", (x, y, z + f["zb"]), stump(A), parent=f"arm_{A}_{k:02d}")
                bones[-1]["neverRender"] = True
    return frames


# ============================================================ 本体
MANTLE_YS = (-10.4, -6.4, 3.0, 14.0, 25.0, 34.0, 42.0, 48.5, 53.5, 57.5)
MANTLE_SIDES = 16
HEAD_YS = (-33.2, -32.0, -30.4, -27.6, -24.0, -16.0, -9.6, -3.2)
HEAD_SIDES = 14
MANTLE_TOP = 64.0
EYE_C = (20.9, -17.5, 0.0)      # 眼の円盤の中心（旧描画の眼: x = ±5.12、y -2.4..-6.0 ブロック）
EYE_RIM = (14.5, 21.8, 9.0)     # 眼の縁の筒（x の始まり, 終わり, 外の半径）
EYE_DISC = 12.6                 # 眼の円盤の一辺
FIN_PIVOT = (16.5, 42.0, 0.0)
SIPHON_BASE = (0.0, -8.0, 18.0)
SIPHON_TIP = (0.0, -21.5, 26.5)


def mantle_skin(_t, c):
    return MANTLE, clamp((c[1] - MANTLE_YS[0]) / (MANTLE_TOP - MANTLE_YS[0]), 0.0, 1.0)


def head_skin(_t, c):
    return HEAD, clamp((c[1] - HEAD_YS[0]) / (HEAD_YS[-1] - HEAD_YS[0]), 0.0, 1.0)


def mantle_shell():
    cubes = []
    for i in range(len(MANTLE_YS) - 1):
        y0, y1 = MANTLE_YS[i], MANTLE_YS[i + 1]
        r0, r1 = mantle_r(y0), mantle_r(y1)
        thick = clamp(0.12 * min(r0, r1), 1.6, 3.0)
        cubes += y_loft((y0, r0), (y1, r1), MANTLE_SIDES, f"m{i}_", thick, mantle_skin, sink=0.3 * (i % 2))
    # 外套膜の先: 細る円錐を八芒形の棒 2 段で
    y = MANTLE_YS[-1]
    cubes += octa_beam((0, y - 2.5, 0), (0, 60.5, 0), 0.92 * mantle_r(58.5), MANTLE, "mtip1", tone=1.0)
    cubes += octa_beam((0, 59.5, 0), (0, MANTLE_TOP, 0), 0.92 * mantle_r(61.5), MANTLE, "mtip2", tone=1.0)
    return cubes


def catmull(points, sub):
    """Catmull-Rom の曲線を区間ごとに sub 分割した点の並び"""
    pts = [points[0]] + list(points) + [points[-1]]
    out = []
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        for j in range(sub):
            t = j / sub
            out.append(tuple(0.5 * (2 * p1[c] + (p2[c] - p0[c]) * t + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t * t
                                    + (3 * p1[c] - p0[c] - 3 * p2[c] + p3[c]) * t ** 3) for c in range(2)))
    out.append(points[-1])
    return out


def fin(s, side):
    """ひれ: 菱形の葉。外縁の辺ごとに、辺へ直交する帯を内側（外套膜の中）まで張る。

    帯は外縁に揃った端を持つので、外形はなめらかな多角形になる。帯どうしの重なりは
    厚みを交互に変えて面を取り合わないようにする。ひれの面は XY（正面と背面を向く）
    """
    outline = catmull([(span * U, h * U) for h, span in FIN_T], 2)   # (x, y)、下から上へ
    centre = (13.0, 42.0)
    cubes = []
    for i in range(len(outline) - 1):
        (px, py), (qx, qy) = outline[i], outline[i + 1]
        mx, my = (px + qx) / 2.0, (py + qy) / 2.0
        if mx < mantle_r(my) + 0.5:
            continue                            # 外套膜の中に隠れる辺
        t = vunit((qx - px, qy - py, 0.0))
        nout = (t[1], -t[0], 0.0)               # 外へ（下から上へ辿るので右手側）
        depth = math.hypot(mx - centre[0], my - centre[1]) + 3.0
        width = math.hypot(qx - px, qy - py) * 1.12
        c = (mx - nout[0] * depth / 2.0, my - nout[1] * depth / 2.0, 0.0)
        thick = 2.6 if i % 2 == 0 else 2.1
        w = (s * nout[0], nout[1], 0.0)
        cubes.append(K.slab((s * c[0], c[1], 0.0), (width, thick, depth), vcross((0, 0, 1), w), (0, 0, 1), FIN,
                            key=f"fin{side}{i}"))
    return cubes


def head_cubes():
    cubes = []
    for i in range(len(HEAD_YS) - 1):
        y0, y1 = HEAD_YS[i], HEAD_YS[i + 1]
        cubes += y_loft((y0, head_r(y0)), (y1, head_r(y1)), HEAD_SIDES, f"h{i}_", 2.6, head_skin,
                        phase=180.0 / HEAD_SIDES, sink=0.3 * (i % 2))
    # 口: 嘴を囲む唇と、その奥の闇
    cubes += y_loft((-35.4, 5.0), (-31.6, 8.8), 8, "lip", 1.6, lambda t, c: (INNER, 0.5), phase=22.5)
    for j, ang in enumerate((0.0, 45.0)):
        r = math.radians(ang)
        cubes.append(K.slab((0.0, -31.2, 0.0), (13.0, 1.0, 13.0), (math.cos(r), 0, math.sin(r)), (0, 1, 0), CAVITY,
                            key=f"maw{j}"))
    # 眼の縁: 頭から突き出た短い 10 角の筒（黒ずんだ赤）。眼の円盤はその奥に収まる
    for s, side in SIDES:
        c0 = (s * EYE_RIM[0], EYE_C[1], EYE_C[2])
        c1 = (s * EYE_RIM[1], EYE_C[1], EYE_C[2])
        cubes += tube_loft(c0, c1, EYE_RIM[2], EYE_RIM[2], 10, f"socket{side}", 2.0, SOCKET)
    return cubes


def eye(s, side):
    """眼の円盤。縁の筒の口より 0.9 奥。四隅は縁の板の中に隠れる"""
    n = (s * 1.0, 0.0, 0.0)
    c = (s * EYE_C[0], EYE_C[1], EYE_C[2])
    return [K.slab(c, (EYE_DISC, 0.7, EYE_DISC), vcross(n, (0, 1, 0)), n, EYE, key=f"eye{side}")]


def beak():
    upper = horn((0.0, -29.5, 3.2), (0.0, -38.5, 4.6), (0.0, -43.5, -1.6), 7.5, "beaku", BEAK,
                 cuts=(0.0, 0.4, 0.75, 1.0), taper=(1.0, 0.72, 0.42))
    lower = horn((0.0, -29.5, -3.2), (0.0, -37.0, -4.6), (0.0, -41.0, 0.8), 6.2, "beakl", BEAK,
                 cuts=(0.0, 0.45, 0.8, 1.0), taper=(1.0, 0.7, 0.4))
    return upper, lower


def siphon():
    """漏斗: 外套膜の襟の下から腹側（+Z）へ下りる、細る管。先に口の闇"""
    d = vunit(vsub(SIPHON_TIP, SIPHON_BASE))
    cubes = tube_loft(SIPHON_BASE, SIPHON_TIP, 5.0, 3.4, 8, "siph", 1.4, SIPHON, roll=(1.0, 0.0, 0.0))
    cubes.append(K.slab(vsub(SIPHON_TIP, vmul(d, 0.5)), (4.8, 0.4, 4.8), (1, 0, 0), d, CAVITY, key="siphmouth"))
    return cubes


def body(bones):
    K.bone(bones, "mantle", (0.0, 0.0, 0.0), [])
    K.bone(bones, "mantle_shell", (0.0, 0.0, 0.0), mantle_shell(), parent="mantle")
    for s, side in SIDES:
        K.bone(bones, f"fin_{side}", (s * FIN_PIVOT[0], FIN_PIVOT[1], FIN_PIVOT[2]), fin(s, side),
               parent="mantle_shell")
    K.bone(bones, "head", (0.0, 0.0, 0.0), head_cubes(), parent="mantle")
    for s, side in SIDES:
        K.bone(bones, f"eye_{side}", (s * EYE_C[0], EYE_C[1], EYE_C[2]), eye(s, side), parent="head")
    upper, lower = beak()
    K.bone(bones, "beak_upper", (0.0, -31.0, 2.5), upper, parent="head")
    K.bone(bones, "beak_lower", (0.0, -31.0, -2.5), lower, parent="head")
    K.bone(bones, "siphon", SIPHON_BASE, siphon(), parent="mantle")


# ============================================================ アニメーション
IDLE = 6.0


def mirror(axis, s):
    """左右の鏡写し。X まわりはそのまま、Y・Z まわりは符号が返る"""
    return (axis[0], axis[1] * s, axis[2] * s)


def cycles(length, n, fn, steps=4):
    """n 周する往復のキー。fn(位相 0..2π) が値（3 成分）を返す"""
    frames = []
    total = n * steps
    for i in range(total + 1):
        frames.append((round(length * i / total, 2), fn(2 * math.pi * n * i / total), EASE_SOFT))
    return frames


def track(kind, frames):
    out = {}
    for t, val, ease in frames:
        vec = [round(v, 3) + 0.0 for v in val]
        out[f"{t:.2f}"] = {"vector": vec, "easing": ease}
    return {kind: out}


def animations(bones):
    names = {b["name"] for b in bones}
    idle = {
        # 外套膜の呼吸: 軸のまわりに膨らんで縮む（旧描画の breath 1 ± 0.035）。2 回
        "mantle_shell": track("scale", cycles(IDLE, 2, lambda p: (1 + 0.035 * math.sin(p), 1 - 0.008 * math.sin(p),
                                                                  1 + 0.035 * math.sin(p)))),
        # 嘴: ゆっくり噛む（上は rx が正、下は負で開く）。2 回
        "beak_upper": track("rotation", cycles(IDLE, 2, lambda p: (7.0 * (0.5 - 0.5 * math.cos(p)), 0, 0))),
        "beak_lower": track("rotation", cycles(IDLE, 2, lambda p: (-6.0 * (0.5 - 0.5 * math.cos(p)), 0, 0))),
        # 漏斗: 呼吸に合わせて少し振れる
        "siphon": track("rotation", cycles(IDLE, 2, lambda p: (-5.0 * math.sin(p), 0, 0))),
    }
    for s, side in SIDES:
        # ひれ: 付け根の蝶番（外套膜の軸に平行）まわりに前後へ煽る。3 回。左右で ry が逆（鏡写し）
        idle[f"fin_{side}"] = track("rotation", cycles(IDLE, 3, lambda p, s=s: mirror((0, 16.0 * math.sin(p), 0), s)))
        # 眼: ゆっくり見回す（両眼とも同じ向きを見る）。1 回
        idle[f"eye_{side}"] = track("rotation", cycles(IDLE, 1, lambda p, s=s: mirror(
            (3.0 * math.sin(p), 5.0 * math.sin(p + 1.3), 0), s), steps=8))
    for b in idle:
        assert b in names, b
    return {"idle": {"loop": True, "animation_length": IDLE, "bones": idle}}


# ============================================================ 書き出し
def build_geo():
    bones = []
    body(bones)
    frames = arms(bones)
    reach = (ANCHOR_R + SEGS * SEG + 8.0) / 16.0
    return K.model(f"geometry.{NAME}", bones, round(2 * reach, 1), round(2 * reach, 1), 0), bones, frames


GECKOLIB_CONVENTIONS = [
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
    "子の Bone（mantle_shell, fin_*, head, eye_*, beak_*, siphon, arm_A_stump）は animation か Java の hidden だけで動かすので、"
    "Java からは位置・回転を触らない（触るなら animation の値を上書きしてしまう）",
]


def contract(bones, cubes, anims, frames):
    def entry(b):
        e = {"name": b["name"], "parent": b.get("parent"), "pivot": b["pivot"]}
        n = b["name"]
        if n == "mantle":
            e["role"] = "本体。pivot は原点 = AbyssKraken の頭の位置（胴の芯、spine の関節 0）。+Y が外套膜の先"
        elif n.startswith("arm_") and not b.get("parent"):
            A, k = int(n.split("_")[1]), int(n.split("_")[2])
            e["role"] = f"腕 {A}（tentacle {A - 1}）の体節 {k}: 関節 {k - 1}（付け根側）〜関節 {k}（先端側）。pivot は中心"
            e["joint_root"], e["joint_tip"] = k - 1, k
        elif n.endswith("_stump"):
            e["role"] = "切断面の蓋。pivot は関節 3。neverRender（普段は隠れている）"
            e["neverRender"] = True
        return e

    radii = [round(arm_radius(j), 2) for j in range(JOINTS)]
    return {
        "entity": NAME,
        "generator": "tools/boss/kraken_model/build.py（手で直さず、生成元を直して流し直す）",
        "geo": f"assets/abyssia/geo/entity/{NAME}.geo.json",
        "animation": f"assets/abyssia/animations/entity/{NAME}.animation.json",
        "texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo.png",
        "glow_texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png",
        "glow_note": "光らない画素は (0,0,0,0)。RenderType.eyes（加算）でも GeckoLib の自動発光層でも使える",
        "texture_size": [atlas.SIZE, atlas.SIZE],
        "model_scale": MODEL_SCALE,
        "units": "geo の 1 単位 = 1/16 ブロック。描画で MODEL_SCALE 倍するので、ゲーム内では MODEL_SCALE/16 = 0.25 ブロック。"
                 "ブロック → geo 単位は × 16 / MODEL_SCALE = × 4",
        "arms": ARMS,
        "joints_per_arm": JOINTS,
        "segments_per_arm": SEGS,
        "segment_length_units": SEG,
        "segment_length_blocks": JOINT_SPACING,
        "arm_rest_origin": {str(A): list(ARM_REST[A]) for A in range(1, ARMS + 1)},
        "rest_pose": ("本体は原点。外套膜は +Y へ立ち、眼は ±X、漏斗は +Z（腹側）、嘴は真下。"
                      "腕 A の関節 j は geo の arm_rest_origin[A] + (0, 0, segment_length_units * j)。"
                      "8 本を X に並べ、どれも +Z（付け根→先端）へ一直線。回転はどの骨も 0"),
        "arm_segment_pivot": "arm_A_SS の pivot = arm_rest_origin[A] + (0, 0, segment_length_units * (SS - 0.5))",
        "arm_axes": {
            "-Z": "付け根側（関節 SS-1 の側）",
            "+Z": "先端側（関節 SS の側）",
            "+Y": "背（反口側。暗い赤）",
            "-Y": "吸盤面（口側。クリームの吸盤 2 列と、体節 12〜14 の鉤）。Java が本体の内側へ向ける",
        },
        "arm_section": {"half_width": ARM_W, "back": ARM_UP, "sucker_side": ARM_DOWN,
                        "note": "腕の半径 × この比。半径（単位）は関節ごとに arm_radius_units（AbyssKrakenRenderer.armRadius × 4）"},
        "arm_radius_units": radii,
        "arm_seam": {"tuck": f"体節の前端は半径 × {TUCK} の細さで、関節より半径 × {TUCK_LEN} 前まで差し込む",
                     "sleeve": f"体節の後端は半径 × {SLEEVE} の袖で、関節より半径 × {SLEEVE_LEN} 後ろまで被さる"},
        "anchor_ring_units": {"y": ANCHOR_Y, "radius": ANCHOR_R,
                              "note": "AbyssKraken.anchor(t) と同じ輪（ブロックで高さ -6・半径 3）。腕の関節 0 はここ。頭の下半分の中に埋まる"},
        "mantle_extent_units": {"top_y": MANTLE_TOP, "collar_y": MANTLE_YS[0], "max_radius": round(mantle_r(3.0), 2),
                                "head_bottom_y": HEAD_YS[0], "beak_tip_y": -43.5,
                                "fin_span_from_axis": max(s for _, s in FIN_T) * U},
        "bone_rule": ("mantle と arm_A_SS（A = 1..8、SS = 01..15）は root 直下。毎フレーム位置と回転を上書きする。"
                      "腕 A は AbyssKraken.tentacle(A - 1)。arm_A_SS は関節 SS-1 と SS の中点へ、局所 -Z が関節 SS から"
                      "関節 SS-1 を向き、局所 -Y（吸盤面）が本体の内側を向くように置く（arm_rotation_with_roll）。"
                      "mantle は頭の位置へ置き、揺れ（旧描画の ±0.04 rad）だけを回転で入れる。"
                      "子（mantle_shell, fin_*, head, eye_*, beak_*, siphon）は animation に任せる"),
        "arm_rotation_with_roll": [
            "腕は吸盤面を内へ向けるので、setRotZ(0) の式（geckolib_conventions の 4 番目）では足りない。"
            "描画空間で f = 関節 SS-1 から SS への単位ベクトル（先端向き）、b = 背（局所 +Y）の単位ベクトル（f に直交）"
            "とし、u = b × f。setRotX(atan2(b.z, f.z))、setRotY(-asin(u.z))、setRotZ(atan2(u.y, u.x))。"
            "（M = Rz・Ry・Rx の列が (u, b, f) になる解き方。b が「上」に近いときは 4 番目の式と同じ値になる）",
            "b の決め方（旧描画 BossMesh.spline と同じ平行移動）: 付け根の体節では、本体の軸から付け根へ向かう水平の"
            "単位ベクトル o を f に直交化して b とする（背が外、吸盤が内）。先の体節へは、前の体節の b を新しい f に"
            "直交化して受け渡す。直交化した長さが 0.2 未満なら (0, 1, 0) を直交化して使う",
            "どの体節も静止姿勢の回転は 0 なので、この値をそのまま入れればよい。位置は geckolib_conventions の 2 番目の式",
        ],
        "severed": {
            "rule": ("AbyssKraken.severed(t) の腕 A = t + 1 は、liveJoints = 4（関節 0..3）だけを描く: "
                     "arm_A_01..arm_A_03 を関節 0..3 から置き、arm_A_04..arm_A_15 を setHidden(true)、"
                     "arm_A_stump を setHidden(false)。切れていない腕は arm_A_04..15 を setHidden(false)、"
                     "arm_A_stump を setHidden(true)"),
            "hidden": ("arm_A_stump は geo で \"neverRender\": true（GeckoLib が焼いた時点で hidden = true）。"
                       "GeoBone はモデル（同じ geo を使う全個体）で共有されるので、hidden は毎フレーム全部の腕について入れ直す"
                       "（前に描いた個体の状態が残る）。setHidden は子も隠すが、stump の親 arm_A_03 は隠さないので問題ない"),
            "stump_parent": "arm_A_03（その体節と一緒に動くので、Java は stump の位置・回転を触らない）",
            "cut_cap": "体節 3 の袖の端（関節 3 から半径 × 0.4 先）を肉の扇 8 枚で塞ぎ、中央に生肉が盛り上がる",
        },
        "geckolib_conventions": GECKOLIB_CONVENTIONS,
        "animation_signs": {
            "mantle_shell": "scale の x と z が 1 ± 0.035 で呼吸（y は 1 ∓ 0.008）。ひれも一緒に膨らむ",
            "fin_l / fin_r": "pivot は外套膜の面の蝶番。y で前後へ煽る。左右で y の符号が逆（鏡写し）",
            "eye_l / eye_r": "x で上下、y で左右を見る。左右で y が逆（両眼が同じ向きを見る）",
            "beak_upper / beak_lower": "上は rotation の x が正、下は負で開く",
            "siphon": "x で前後に振れる",
        },
        "animations": {name: {"loop": a["loop"], "length": a["animation_length"],
                              "bones": sorted(a["bones"])} for name, a in anims.items()},
        "cube_count": cubes,
        "bones": [entry(b) for b in bones],
    }


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    geo, bones, frames = build_geo()
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
    (HERE / 'contract.json').write_text(json.dumps(contract(bones, cubes, anims, frames), indent=1, ensure_ascii=False),
                                        encoding='utf-8')
    per = {}
    for b in bones:
        n = b["name"]
        key = "stump" if n.endswith("_stump") else "arm" if n.startswith("arm_") else "body"
        per[key] = per.get(key, 0) + len(b["cubes"])
    print(f"cubes: 計 {cubes}（本体 {per.get('body', 0)} / 腕 {per.get('arm', 0)} / 切断面 {per.get('stump', 0)}）、"
          f"MODEL_SCALE {MODEL_SCALE:g}、体節 {SEG:g} 単位 x {SEGS} x {ARMS} 本")
    print("次に流すこと:")
    print("  python tools/boss/check_animations.py abyss_kraken")
    print("  python tools/boss/kraken_model/preview.py")


if __name__ == '__main__':
    main()
