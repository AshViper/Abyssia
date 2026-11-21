# -*- coding: utf-8 -*-
"""アビスアングラー（abyss_angler）の GeckoLib モデル一式を書き出す。

```bash
python tools/boss/angler_model/build.py
```

書き出すもの（どれも丸ごと上書き。手で直さず、ここを直して流し直す）:

- `assets/abyssia/geo/entity/abyss_angler.geo.json`
- `assets/abyssia/animations/entity/abyss_angler.animation.json`（idle だけ）
- `assets/abyssia/textures/entity/boss/abyss_angler_geo.png` と `_glow.png`
- `tools/boss/angler_model/contract.json`（描画の Java 側との約束）

<h2>骨の約束（大海蛇 serpent_model と同じ）</h2>
`head` と `seg_01`〜`seg_09` は<b>すべて root 直下</b>。静止姿勢は頭から尾まで一直線で、
関節 j は (0, 0, SEG * j)。頭は -Z を向き、関節 0 に pivot を置く。体節 k は関節 k-1（前）から
関節 k（後ろ）までで、pivot は中心 (0, 0, SEG * (k - 0.5))。Java は毎フレーム root 直下の骨の
位置と回転を上書きする。体のうねりは Java が入れるので、animation には体の曲げを入れない。

<h2>頭が体の大半</h2>
頭の殻は関節 0 の 15 ブロック前（顎先は 21.8 前）から関節 1 まで。頭と seg_01 は
どちらも「関節 1 → 関節 0」の向きに置かれるので、二つは常に一体で動く。だから seg_01 は頭の殻の
内側に隠れる位置合わせだけの骨（箱なし）。殻は関節 1 で切り、seg_02 が同じ外形のまま続く。
各体節の前端は前の関節を中心とする球面で前の殻の内側へ入る（球関節）ので、袖も段差も無く、
曲げても割れ目や殻の内側が見えない。体は横から見て凸の涙滴形で尾柄（k 6）へ細り、
その後ろの seg_08 / seg_09 は尾びれだけ（BODY 表、CORE_END）。

<h2>寸法</h2>
実寸は AbyssAngler.java（頭＋9 関節、関節間隔 15 ブロック、SIZE 3）と、旧描画
AnglerMesh.java（体の断面表、頭骨・下顎の loft 表、牙・眼・提灯・鰭の表）から取った。
表はブロック単位・旧描画の向き（+Z が鼻先）のまま書き、geo へは (x, y, -z) × U で直す。
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
from atlas import (BACK, FLANK, PHOTO, BELLY, FIN, INNER, THROAT, PLATE, CAVITY, HEAD, HEAD_PHOTO, JAW,
                   JAW_UNDER, MEMBRANE, RAY, HORN, TOOTH, STALK, EYE, BULB)
from boss_kit import (_add as vadd, _sub as vsub, _mul as vmul, _unit as vunit, _cross as vcross,
                      _dot as vdot, _mid as vmid, _dist as vdist, rot, wave, EASE_SOFT)

ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/abyssia'
GEO = ASSETS / 'geo/entity'
ANIM = ASSETS / 'animations/entity'
TEX = ASSETS / 'textures/entity/boss'
NAME = 'abyss_angler'
CUBE_LIMIT = 1500

K = boss_kit.Kit(atlas, stretched=atlas.STRETCHED, top_anchored=atlas.TOP_ANCHORED)

# ============================================================ 実寸
MODEL_SCALE = 3.0           # 描画側で一様に掛ける倍率（AbyssAngler.SIZE）。geo の 1 単位 = 3/16 ブロック
JOINTS = 9                  # 頭の後ろの関節の数（AbyssAngler: 1 + 9）
JOINT_SPACING = 15.0        # ブロック（AbyssAngler のコンストラクタ）
U = 16.0 / MODEL_SCALE      # 1 ブロックが何単位か（5.333）
SEG = JOINT_SPACING * U     # 体節の長さ（80 単位）

SIDES = ((1, "l"), (-1, "r"))   # +X が左


def hz(zm):
    """旧描画の頭の座標（ブロック、+Z が鼻先）の z を geo の z へ"""
    return -zm * U


def hp(x, y, zm):
    """旧描画の頭の座標（ブロック）を geo の点へ"""
    return (x * U, y * U, -zm * U)


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def lerp_table(table, k, cols=(1, 2, 3)):
    """k の昇順の表を線形補間（端は止める）"""
    if k <= table[0][0]:
        return tuple(table[0][c] for c in cols)
    for a, b in zip(table, table[1:]):
        if k <= b[0]:
            f = (k - a[0]) / (b[0] - a[0])
            return tuple(a[c] + (b[c] - a[c]) * f for c in cols)
    return tuple(table[-1][c] for c in cols)


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


# ============================================================ 体の断面
# (関節座標 k, 半幅, 背, 腹)。ブロック。頭の後ろ端（関節 1）で頭の外形にそのまま続き
# （k 1〜1+SEAM_BLEND は頭の外形からこの表へ混ぜる）、尾柄（k 6）まで横から見て凸の涙滴形に細る。
# k 6 の後ろは尾びれの付け根の側扁した短い芯で、CORE_END で消える（その後ろは鰭の膜と鰭条だけ）
BODY = (
    (1.0, 26.8, 37.2, 35.0), (1.5, 26.7, 36.7, 34.4), (2.0, 26.2, 35.6, 33.2), (2.5, 25.2, 33.8, 31.3),
    (3.0, 23.6, 31.0, 28.6), (3.5, 21.4, 27.3, 25.1), (4.0, 18.6, 22.8, 20.9), (4.5, 15.2, 17.8, 16.3),
    (5.0, 11.4, 12.8, 11.8), (5.5, 7.4, 8.9, 8.3), (6.0, 4.2, 6.6, 6.2), (6.5, 2.5, 4.6, 4.3),
    (6.9, 0.9, 1.8, 1.7))
CORE_END = 6.9              # 体の芯が終わる関節座標（その後ろの seg_08 / seg_09 は尾びれだけ）
BODY_E = 2.25               # 超楕円の指数（AnglerMesh.BODY_E 0.9 と同じ丸み）
SEAM_SCALE = 0.985          # 継ぎ目（関節）の体の外形 / 前の殻の外形。殻の縁が 1.5% だけ被さる
SEAM_BLEND = 0.6            # 関節 1 の後ろで、頭の外形から BODY 表へ混ぜきるまでの長さ（関節座標）
DOME = 0.45                 # 体節の前端の球面（関節が中心）が関節より前へ入る長さ / 断面の半幅。
                            # sin(21°) = 0.36 より長いので、21 度曲げても球面の前の縁は殻の内側に残る


def sec(k):
    """関節座標 k の断面（半幅, 背, 腹）。geo 単位"""
    return tuple(v * U for v in lerp_table(BODY, k))


def top_y(z):
    k = z / SEG
    return 0.0 if k > CORE_END else sec(k)[1] * 0.97


def bottom_y(z):
    k = z / SEG
    return 0.0 if k > CORE_END else -sec(k)[2] * 0.97


# ---- 断面を「芯 (0, 0) から角度 t の向きの半径」で持つ（t は真上が 0、+X 側が正。度）。
# 頭の殻（頭骨の上半と下顎の椀）の外形と体の超楕円を同じ形で混ぜられ、関節を中心とする球面も作れる
POLAR_N = 720
POLAR_T = [-180.0 + 360.0 * i / POLAR_N for i in range(POLAR_N + 1)]


def polar_superellipse(hw, up, dn, e=BODY_E):
    out = []
    for t in POLAR_T:
        r = math.radians(t)
        s, c = abs(math.sin(r)), math.cos(r)
        b = up if c >= 0 else dn
        out.append(((s / hw) ** e + (abs(c) / b) ** e) ** (-1.0 / e))
    return out


def polar_at(p, t):
    t = ((t + 180.0) % 360.0) - 180.0
    f = (t + 180.0) / 360.0 * POLAR_N
    i = min(int(f), POLAR_N - 1)
    return p[i] + (p[i + 1] - p[i]) * (f - i)


def polar_from_inside(inside, rmax=400.0):
    """inside(x, y) の境界を芯からの半径で（芯から見て星形の領域）"""
    out = []
    for t in POLAR_T:
        r = math.radians(t)
        s, c = math.sin(r), math.cos(r)
        lo, hi = 0.0, rmax
        for _ in range(36):
            mid = (lo + hi) / 2.0
            if inside(mid * s, mid * c):
                lo = mid
            else:
                hi = mid
        out.append(lo)
    return out


def half_width_fn(p):
    """断面の、高さ y での半幅 x(y)（1 単位刻みの表を線形補間）"""
    pts = [(r * math.sin(math.radians(t)), r * math.cos(math.radians(t))) for t, r in zip(POLAR_T, p) if t >= 0]
    y_lo, y_hi = int(math.floor(min(q[1] for q in pts))), int(math.ceil(max(q[1] for q in pts)))
    table = []
    for y in range(y_lo, y_hi + 1):
        best = 0.0
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            if (y0 - y) * (y1 - y) <= 0 and y0 != y1:
                best = max(best, x0 + (x1 - x0) * (y - y0) / (y1 - y0))
        table.append(best)

    def xs(y):
        if y <= y_lo or y >= y_hi:
            return 0.0
        f = y - y_lo
        i = int(f)
        return table[i] + (table[i + 1] - table[i]) * (f - i)
    return xs


_POLAR_CACHE = {}


def body_polar(z):
    """体の z（関節 1 より後ろ）の断面。関節 1 では頭の殻の外形 × SEAM_SCALE"""
    key = round(z, 3)
    if key not in _POLAR_CACHE:
        k = z / SEG
        base = polar_superellipse(*sec(k))
        w = clamp((k - 1.0) / SEAM_BLEND, 0.0, 1.0)
        w = w * w * (3.0 - 2.0 * w)
        if w < 1.0:
            seam = head_polar()
            base = [SEAM_SCALE * s + (b - SEAM_SCALE * s) * w for s, b in zip(seam, base)]
        _POLAR_CACHE[key] = base
    return _POLAR_CACHE[key]


def dome_polar(p, d):
    """断面 p（関節の位置）を、関節を中心とする球面で d だけ前へ閉じた断面。
    高さ y ごとに x² + d² ≤ x(y)²（ヨーで回しても同じ面の上を滑る）"""
    xs = half_width_fn(p)
    return polar_from_inside(lambda x, y: x * x + d * d <= xs(y) ** 2, rmax=max(p) + 1.0)


def polar_ring(z, p, n):
    """体の板の縁の点（ring と同じ並び: 真下から、板 i の中心が -180 + 360 i / n）"""
    step = 360.0 / n
    pts = []
    for i in range(n + 1):
        t = -180.0 - step / 2.0 + step * i
        r = polar_at(p, t)
        a = math.radians(t)
        pts.append((r * math.sin(a), r * math.cos(a), z))
    return pts


# ============================================================ 断面と外皮（serpent_model と同じ置き方）
def ring(z, a, up, down, t0, t1, n, e=2.0, cy=0.0):
    """超楕円の縁の点を n+1 個。t は真上が 0、+X 側が正"""
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
    """2つの断面の縁どうしを板で結ぶ。r0 が前（-Z 側）、r1 が後ろ。
    板の局所 Z は「面に沿って上る向き」（絵の上端）。左右で板の u は鏡写しになる"""
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


def axis_loft(centres, radii, n, region, key, thick, lap=1.18, overlap=1.06, phase=0.0):
    """任意の軸まわりの多角形の管（提灯の珠）。centres[0] が首。

    板の局所 Z（絵の上端）は軸に沿って首の側を向く。BULB は上端が首で、下へ明るくなる"""
    axis = vunit(vsub(centres[-1], centres[0]))
    ref = (1.0, 0.0, 0.0) if abs(axis[0]) < 0.8 else (0.0, 0.0, 1.0)
    e1 = vunit(vcross(axis, ref))
    e2 = vcross(axis, e1)
    rings = []
    for c, r in zip(centres, radii):
        pts = []
        for i in range(n + 1):
            a = math.tau * i / n + phase
            pts.append(vadd(c, vadd(vmul(e1, r * math.cos(a)), vmul(e2, r * math.sin(a)))))
        rings.append(pts)
    out = []
    for j in range(len(rings) - 1):
        ra, rb = rings[j], rings[j + 1]
        ca, cb = centres[j], centres[j + 1]
        for i in range(n):
            a0, b0, a1, b1 = ra[i], ra[i + 1], rb[i], rb[i + 1]
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
            if vdot(normal, vsub(centre, vmid(ca, cb))) < 0:
                normal = vmul(normal, -1)
            u = across
            if vdot(vcross(u, normal), along) > 0:      # 局所 Z を首の側へ
                u = vmul(u, -1)
            width = max(vdist(a0, b0), vdist(a1, b1)) * lap
            c = vsub(centre, vmul(normal, thick / 2.0))
            out.append(K.slab(c, (width, thick, length * overlap), u, normal, region, key=f"{key}{j}_{i}"))
    return out


def deck(p0, p1, width, thick, region, key, tone=None):
    """p0 から p1 へ渡す水平に近い板（口蓋・口の床）。幅は X 方向"""
    d = vunit(vsub(p1, p0))
    up = vunit(vsub((0.0, 1.0, 0.0), vmul(d, d[1])))
    return K.slab(vmid(p0, p1), (width, thick, vdist(p0, p1)), vcross(up, d), up, region, tone=tone, key=key)


def bezier(p0, p1, p2, s):
    return tuple((1 - s) ** 2 * p0[i] + 2 * (1 - s) * s * p1[i] + s * s * p2[i] for i in range(3))


def diamond_side(d):
    """棒を菱形に向ける side（面取りした稜に見える）"""
    flat = vunit(vcross(d, (0.0, 1.0, 0.0))) if abs(d[1]) < 0.95 else (1.0, 0.0, 0.0)
    return vadd(flat, (0.0, 1.0, 0.0))


def rod(points, widths, region, key, overshoot=0.8):
    """点の列をなぞる棒の連なり。widths は区間ごとの太さ。菱形に向けて面取りする"""
    out = []
    for i in range(len(points) - 1):
        a, b = points[i], points[i + 1]
        d = vunit(vsub(b, a))
        w = widths[i]
        out.append(K.beam(a, b, w, w, region, side=diamond_side(d), key=f"{key}{i}",
                          overshoot=overshoot if i else 0.0))
    return out


def horn(p0, p1, p2, w0, key, region=HORN, cuts=(0.0, 0.3, 0.58, 0.8, 1.0), taper=(1.0, 0.78, 0.55, 0.32)):
    """曲がった角・牙。2次ベジェを数本の棒に分け、先ほど細くする"""
    pts = [bezier(p0, p1, p2, c) for c in cuts]
    return rod(pts, [w0 * t for t in taper], region, key)


def fan_to(base, e1, e2, tips, key, ray_w, ray_region=RAY, web_region=MEMBRANE):
    """鰭条の先の点（tips）から角度と長さを解いて Kit.fan に渡す。e1 / e2 は鰭の面を張る向き"""
    rays = []
    for p in tips:
        d = vsub(p, base)
        rays.append((math.degrees(math.atan2(vdot(d, e2), vdot(d, e1))), math.sqrt(vdot(d, d))))
    return K.fan(base, e1, e2, rays, key, ray_region, web_region, tip_region=web_region,
                 ray_w=ray_w, lift=0.15)


def film(za, zz, y0, y1, key, up=True):
    """縦の膜（背・臀・尾びれ）。厚み 0 の板で、外縁（上端の破れ）を外へ向ける。

    板は 10 単位ずつに刻むので、切り出す位置を乱数にすると破れと鰭条が板ごとに途切れて
    櫛のように見える。z から切り出す位置を決め、隣の板と絵を続ける（west 面は局所 +Z へ u が増える。
    下向きの膜は局所 Z が -z を向くので、-z で数える）"""
    if up:
        c = K.slab((0.0, (y0 + y1) / 2.0, (za + zz) / 2.0), (0.0, y1 - y0, zz - za),
                   (1, 0, 0), (0, 1, 0), FIN, key=key)
    else:
        c = K.slab((0.0, (y0 + y1) / 2.0, (za + zz) / 2.0), (0.0, y0 - y1, zz - za),
                   (1, 0, 0), (0, -1, 0), FIN, key=key)
    west = c["uv"]["west"]
    span = FIN[2] - west["uv_size"][0]
    start = (za if up else -zz) % span
    west["uv"][0] = round(FIN[0] + start, 2)
    c["uv"]["east"] = boss_kit._mirrored(west, 0)
    return c


# ============================================================ 体節
def ring_angles(n):
    step = 360.0 / n
    return [-180.0 + step * i for i in range(n)]


def _abs_angle(t):
    return abs(((t + 180.0) % 360.0) - 180.0)


def body_skin(t, _n):
    """背の稜（暗い）→ 脇腹（下ほど明るい）→ 下脇腹の発光器2列 → 腹（暗い紫紺）"""
    at = _abs_angle(t)
    if at < 30:
        return BACK, None
    if at < 55:
        return FLANK, 0.1
    if at < 80:
        return FLANK, 0.45
    if at < 100:
        return FLANK, 0.85
    if at < 128:
        return PHOTO, 0.5
    return BELLY, None


HEAD_REAR_ZM = -15.0          # 頭の殻の後ろ端（旧描画の頭の座標）＝関節 1。ここから後ろが体

# 体節ごとの (板の数, 体節の中の輪の位置（関節 k-1 から k への割合）)。前の体節ほど太いので板を多く
SEG_RINGS = {2: (24, (0.0, 0.3, 0.62, 1.0)), 3: (22, (0.0, 0.5, 1.0)), 4: (20, (0.0, 0.5, 1.0)),
             5: (16, (0.0, 0.5, 1.0)), 6: (14, (0.0, 0.5, 1.0)), 7: (10, (0.0, 0.42, 0.9))}


def segment(k, bones, extras):
    """体節の外皮。前端は関節 k-1 を中心とする球面で前の殻（頭か前の体節）の内側へ入り、
    関節 k-1 で前の殻と同じ外形、関節 k で次の体節の球面と同じ外形になる（袖・段差なし）。
    曲げると外側では前の殻の縁の下から球面が出てきて継ぎ目を埋め、内側では前の殻へ潜る"""
    name = f"seg_{k:02d}"
    zc = (k - 0.5) * SEG
    zf = zc - SEG / 2
    cubes = []
    if k in SEG_RINGS:
        n, fracs = SEG_RINGS[k]
        angles = ring_angles(n)
        front = body_polar(zf)
        d = DOME * half_width_fn(front)(0.0)
        zs = [zf + SEG * f for f in fracs]
        rings = [polar_ring(zf - d, dome_polar(front, d), n)] + [polar_ring(z, body_polar(z), n) for z in zs]
        thick = max(1.8, 0.07 * sum(sec(k - 1)) / 3.0)
        for j in range(len(rings) - 1):
            cubes += ring_loft(rings[j], rings[j + 1], angles, f"s{k}b{j}_", thick, body_skin, sink=0.4 * (j % 2))
        if zs[-1] < k * SEG - 1.0:
            # 芯の先の蓋（尾びれの膜の中で終わる）
            hw, up, dn = sec(zs[-1] / SEG)
            cubes.append(K.cube((-hw, -dn, zs[-1] - 3.0), (2 * hw, up + dn, 4.0), BACK, key="tailcap"))
    # k == 1: 頭と同じ向きに置かれ、頭の殻の内側に丸ごと入る位置合わせだけの骨
    # k >= 8: 体の芯は無く、尾びれの膜と鰭条だけ（_fin の子に積む）
    cubes += extras.get(name, [])
    K.bone(bones, name, (0.0, 0.0, zc), cubes)


# ---- 背びれ・臀びれ・尾びれ（関節座標 k, 高さ ブロック）
# 深海アンコウ（Melanocetus）の鰭: 背びれと臀びれは尾柄の手前に向かい合う小さな軟条の鰭、
# 尾びれは尾柄から丸く開く扇（切れ込みなし）。どれも頭と体の塊より十分小さく
DORSAL = ((4.25, 0.0), (4.45, 5.0), (4.75, 8.0), (5.05, 9.0), (5.35, 8.6), (5.65, 6.6), (5.95, 0.0))
ANAL = ((4.55, 0.0), (4.75, 4.5), (5.05, 7.0), (5.35, 7.4), (5.65, 6.0), (5.95, 0.0))
# 芯（CORE_END まで）の背からの高さ。芯の後ろは芯の線（y = 0）からの高さ
CAUDAL_UP = ((6.02, 0.0), (6.2, 4.5), (6.6, 8.5), (7.0, 12.5), (7.5, 15.0), (8.0, 16.8), (8.4, 17.6),
             (8.7, 17.4), (8.95, 15.8), (9.1, 12.0), (9.2, 6.5))
CAUDAL_DOWN = tuple((k, 0.92 * h) for k, h in CAUDAL_UP)
CAUDAL_SEGS = (7, 8, 9)        # 尾びれを持つ体節（seg_07〜seg_09 の _fin）
CAUDAL_RAYS = (6.35, 6.7, 7.3, 7.65, 7.95, 8.3, 8.6, 8.9, 9.12)
SPINE_K = (4.45, 4.75, 5.05, 5.35, 5.65)
ANAL_SPINE_K = (4.75, 5.05, 5.35, 5.65)
FORK = 99.0                    # 尾びれは丸い扇なので切れ込みは作らない（旧: 最後の関節から二叉）
FIN_STEP = 10.0                # 膜の板1枚の長さ（単位）。背びれの波（棘のあいだのたるみ）を刻む
CAUDAL_STEP = 5.0              # 尾びれは外縁が急に立ち上がるので、板を細かく刻んで段を小さくする


def fin_h(table, k):
    if k < table[0][0] or k > table[-1][0]:
        return 0.0
    return lerp_table(table, k, (1,))[0] * U


def scallop(k):
    """棘のあいだで膜がたるむ（棘の上で 1、棘のあいだで 0.86）。AnglerMesh.edge は 0.62 まで
    たるむが、膜は縦の板を並べて作るので、深いたるみは板ごとの段になって箱っぽく見える"""
    return 0.86 + 0.14 * abs(math.cos(math.tau * (k - 0.25)))


def spine(base_y, z, h, sign, key, region=RAY):
    """膜を支える棘。付け根は体に埋め、先は少し後ろへ反る"""
    y0 = base_y - 6.0 * sign
    tip = (0.0, base_y + sign * h, z + 0.2 * h)
    mid = (0.0, base_y + sign * 0.55 * h, z + 0.07 * h)
    w = max(3.0, 0.55 * U * 1.6)
    return rod([(0.0, y0, z), mid, tip], [w, w * 0.55], region, key)


def dorsal(k):
    """seg_k の背びれ（膜と棘）。pivot は体節の背の中央"""
    zf, zb = (k - 1) * SEG, k * SEG
    out = []
    z = zf
    j = 0
    while z < zb - 1e-6:
        za, zz = z, min(z + FIN_STEP, zb)
        kc = (za + zz) / 2.0 / SEG
        h = fin_h(DORSAL, kc) * scallop(kc)
        if h > 1.5 * U:
            y0 = min(top_y(za), top_y(zz)) - 5.0
            out.append(film(za, zz, y0, top_y((za + zz) / 2.0) + h, f"d{k}m{j}"))
        z = zz
        j += 1
    for kk in SPINE_K:
        if k - 1 < kk <= k:
            zz = kk * SEG
            out += spine(top_y(zz), zz, 1.18 * fin_h(DORSAL, kk), 1, f"d{k}sp{kk:g}")
    return out


def anal(k):
    zf, zb = (k - 1) * SEG, k * SEG
    out = []
    z = zf
    j = 0
    while z < zb - 1e-6:
        za, zz = z, min(z + FIN_STEP, zb)
        kc = (za + zz) / 2.0 / SEG
        h = fin_h(ANAL, kc) * scallop(kc)
        if h > 1.5 * U:
            y0 = max(bottom_y(za), bottom_y(zz)) + 5.0
            out.append(film(za, zz, y0, bottom_y((za + zz) / 2.0) - h, f"a{k}m{j}", up=False))
        z = zz
        j += 1
    for kk in ANAL_SPINE_K:
        if k - 1 < kk <= k:
            zz = kk * SEG
            out += spine(bottom_y(zz), zz, 1.18 * fin_h(ANAL, kk), -1, f"a{k}sp{kk:g}")
    return out


def caudal(k):
    """尾びれ（上葉と下葉）。seg_08 は付け根、seg_09 は鰭条と二叉の葉（最後の関節の後ろへ張り出す）"""
    zf = (k - 1) * SEG
    z_end = CAUDAL_UP[-1][0] * SEG if k == JOINTS else k * SEG
    out = []
    z = max(zf, CAUDAL_UP[0][0] * SEG)
    j = 0
    while z < z_end - 1e-6:
        za, zz = z, min(z + CAUDAL_STEP, z_end)
        kc = (za + zz) / 2.0 / SEG
        ka = min(kc, JOINTS)
        for table, up in ((CAUDAL_UP, True), (CAUDAL_DOWN, False)):
            h = fin_h(table, kc)
            if h < 1.0 * U:
                continue
            if kc > JOINTS:
                # 体の先（最後の関節）より後ろは膜だけ。上下の膜を芯で合わせる
                inner = 0.0
                edge = (top_y(JOINTS * SEG) + h) if up else (bottom_y(JOINTS * SEG) - h)
            elif kc <= FORK:
                base = top_y(min(za, zz)) if up else bottom_y(min(za, zz))
                if kc > CORE_END:
                    inner = 0.0               # 芯の後ろ: 上下の膜を芯の線で合わせる
                else:
                    inner = (base - 5.0) if up else (base + 5.0)
                edge = (top_y(ka * SEG) + h) if up else (bottom_y(ka * SEG) - h)
            else:
                # 切れ込み: 葉の内側の縁が、最後の関節の芯から先端へ向けて持ち上がる
                f = (kc - FORK) / (CAUDAL_UP[-1][0] - FORK)
                inner = (1.0 if up else -1.0) * (0.85 * f * h)
                edge = (1.0 if up else -1.0) * (h * (1.0 - 0.12 * f))
            out.append(film(za, zz, inner, edge, f"c{k}{'u' if up else 'd'}{j}", up=up))
        z = zz
        j += 1
    # 鰭条: 体の縁から外縁へ、後ろへ寝て扇に開く。曲げたとき折れないよう、根元も先も同じ体節の中に置く
    for table, sign, tag in ((CAUDAL_UP, 1, "u"), (CAUDAL_DOWN, -1, "d")):
        for i, kk in enumerate(CAUDAL_RAYS):
            if not (k - 1 < kk <= min(k, CAUDAL_UP[-1][0]) or (k == JOINTS and kk > k)):
                continue
            h = fin_h(table, kk)
            if h < 2.0 * U:
                continue
            bz = max(zf + 2.0, (kk - 0.45) * SEG)
            by = top_y(bz) - 3.0 if sign > 0 else bottom_y(bz) + 3.0
            y = (top_y(min(kk, JOINTS) * SEG) + h) if sign > 0 else (bottom_y(min(kk, JOINTS) * SEG) - h)
            tip = (0.0, y * 0.97, kk * SEG)
            out.append(K.beam((0.0, by, bz), tip, 3.0, 3.0, RAY, side=(1, 0, 0), tone=0.4, key=f"cr{tag}{i}"))
    return out


# ---- 腹びれ（seg_03 の子）。AnglerMesh.fan（pectoral = false）を点で置き直したもの
PELVIC_K = 2.9
PELVIC = (9.0, 11.0, 11.5, 9.0)        # 深海アンコウの腹びれは無いに等しい。腹の丸みを崩さない小さな房


def pelvic(s):
    z = PELVIC_K * SEG
    hw, up, dn = sec(PELVIC_K)
    root = (s * 5.0 * U, -dn * 0.95, z)
    tips = []
    for j, length in enumerate(PELVIC):
        t = j / (len(PELVIC) - 1)
        d = vunit((s * (0.1 + 0.45 * t), -0.5 - 0.2 * t, 1.0 - 0.2 * t))
        tips.append(vadd(root, vmul(d, length * U)))
    e1 = vunit(vsub(tips[0], root))
    last = vunit(vsub(tips[-1], root))
    e2 = vunit(vsub(last, vmul(e1, vdot(e1, last))))
    return fan_to(root, e1, e2, tips, f"pel{s}", ray_w=2.2), root


def body_eye(k, radius, s, key):
    """体の側の眼（発光）。旧描画の BODY_EYES と同じ位置（背の高さの 0.32）"""
    z = k * SEG
    hw, up, dn = sec(k)
    y = 0.32 * up
    a = (1.0 - (y / up) ** BODY_E) ** (1.0 / BODY_E)
    x = hw * a
    # 超楕円の外向きの法線
    gx = (x / hw) ** (BODY_E - 1) / hw
    gy = (y / up) ** (BODY_E - 1) / up
    n = vunit((s * gx, gy, 0.0))
    c = vadd((s * x, y, z), vmul(n, 0.6))
    r = radius * U
    out = [K.slab(vsub(c, vmul(n, 1.2)), (2.5 * r, 2.0, 2.5 * r), (0, 0, 1), n, CAVITY, key=f"{key}s"),
           K.slab(c, (2.0 * r, 1.6, 2.0 * r), (0, 0, 1), n, EYE, key=f"{key}e")]
    return out


# ============================================================ 頭
# 頭骨の上半（旧描画の z, 半幅, 背の高さ, 口縁の高さ）。ブロック、+Z が鼻先、関節 0 が原点。
# AnglerMesh.SKULL（z -28..15）の後ろを関節 1（z -15）で切った（その後ろは体節が同じ外形で続く）
# 深海アンコウの頭: 額が急に盛り上がる球に近い殻。口縁（口の線）は蝶番から吻の先へ斜めに上り、
# 頭の前面いっぱいが口になる。吻は尖らせず、上から見て丸い U 字
# 殻は関節 1（z -15）で切る。その後ろは seg_02 が同じ外形で続き、seg_02 の前端の球面が殻の内側へ入る
SKULL_T = (
    (HEAD_REAR_ZM, 26.8, 37.2, -12.8),   # 後ろ端＝関節 1（seg_02 の前端と同じ外形。背は体へ緩く下る）
    (-10.0, 27.0, 37.8, -10.6),          # いちばん高い・広い
    (-5.0, 26.4, 36.8, -7.4),
    (0.0, 25.0, 33.2, -3.6),
    (5.0, 22.5, 27.5, 0.4),
    (9.0, 19.0, 22.5, 3.6),
    (12.0, 15.0, 17.5, 6.0),
    (14.0, 11.0, 13.0, 7.6),
    (15.0, 6.0, 9.6, 8.5),               # 吻の先（丸い）
)
CRANIUM_E = 2.35            # AnglerMesh の 0.85（丸みのある箱形）
CRANIUM_EDGE = 104.0        # 頭骨の殻が下りる角度（真上から。90 で真横）
CRANIUM_PLATES = 16

# 下顎（z, 半幅, 口縁の高さ, 顎の底）。縁がいちばん広い半分の椀（JAW_EDGE = 90）で、
# 頭骨より 16% 狭いので、閉じると縁は頭骨の口縁の内側へ入り、上の牙が顎の外へ垂れる。
# 先だけ吻より 1 ブロック前へ出る（受け口）
# 頭の下半分を丸ごと占める深い椀。口縁は頭骨の口縁に沿って斜めに上り、先は吻より 3.6 前へ出て
# 吻の先より高く反り上がる（受け口）。吻の先までは頭骨の口縁の 0.88 倍以内に収め、閉じると内側へ入る
JAW_T = (
    (HEAD_REAR_ZM, 25.0, -13.3, -35.0),  # 後ろ端（口の角の後ろ）は頭骨の口縁のすぐ内側まで広げ、体へ段なく続ける
    (-10.0, 23.3, -11.0, -35.6),         # いちばん深い
    (-5.0, 22.8, -7.8, -35.6),
    (0.0, 21.6, -4.0, -33.4),
    (5.0, 19.4, 0.0, -29.0),
    (9.0, 16.3, 3.2, -24.0),
    (12.0, 13.0, 5.4, -18.0),
    (14.0, 9.4, 7.0, -12.5),
    (16.0, 8.0, 9.0, -7.0),
    (18.5, 6.2, 11.0, -1.5),
    (20.5, 4.0, 12.6, 3.5),
    (21.8, 1.8, 13.4, 8.0),              # 顎の先（吻の先より 7 前・5 上へ反り上がる受け口）
)
JAW_E = 2.35
JAW_EDGE = 90.0
JAW_PLATES = 12
HINGE_ZM, HINGE_Y = -12.5, -12.5         # 顎の蝶番（口の裂け目の奥。眼より後ろの低い位置）
HINGE = hp(0.0, HINGE_Y, HINGE_ZM)

# 喉の奥（動かない）: 顎を落とすと歯の奥に見える口腔の底。(z, 半幅, 上, 下)、中心は y = THROAT_Y
THROAT_T = ((-15.0, 18.0, 3.0, 11.5), (-13.0, 19.5, 3.0, 16.0), (-8.0, 17.5, 3.0, 15.0),
            (-1.0, 12.0, 2.5, 10.0), (5.0, 4.0, 2.0, 4.0))
THROAT_Y = -13.0


def geo_table(t):
    return tuple((hz(r[0]),) + tuple(v * U for v in r[1:]) for r in t)


CRANIUM = geo_table(SKULL_T)
JAWG = geo_table(JAW_T)


def cranium_params(z):
    hw, top, lip_y = (interp(CRANIUM, z, c) for c in (1, 2, 3))
    edge = abs(math.cos(math.radians(CRANIUM_EDGE))) ** (2.0 / CRANIUM_E)
    b = (top - lip_y) / (1.0 + edge)
    return hw, b, top - b


def jaw_params(z):
    hw, rim, bottom = (interp(JAWG, z, c) for c in (1, 2, 3))
    edge = abs(math.cos(math.radians(JAW_EDGE))) ** (2.0 / JAW_E)
    b = (rim - bottom) / (1.0 + edge)
    return hw, b, bottom + b


def cranium_point(z, t, s=1):
    """頭骨の外面の点と外向きの法線。t は真上からの角度（度）"""
    def p(zz, tt):
        a, b, cy = cranium_params(zz)
        r = math.radians(tt)
        sn, cs = math.sin(r), math.cos(r)
        return (s * a * abs(sn) ** (2.0 / CRANIUM_E), cy + b * math.copysign(abs(cs) ** (2.0 / CRANIUM_E), cs), zz)
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
    return a * abs(math.sin(math.radians(CRANIUM_EDGE))) ** (2.0 / CRANIUM_E), interp(CRANIUM, z, 3)


def jaw_rim(z):
    """下顎の口縁 (x, y)。縁がいちばん広い"""
    return interp(JAWG, z, 1), interp(JAWG, z, 2)


def jaw_x_at(z, y):
    """下顎の外面の、高さ y での半幅"""
    a, b, cy = jaw_params(z)
    q = clamp((cy - y) / b, 0.0, 0.999)
    return a * (1.0 - q ** JAW_E) ** (1.0 / JAW_E)


def skull_top(z):
    return interp(CRANIUM, z, 2)


def head_inside(x, y, z):
    """(x, y) が頭の殻（頭骨の上半＋閉じた下顎の椀）の外形の内側か"""
    x = abs(x)
    if y >= interp(CRANIUM, z, 3):
        a, b, cy = cranium_params(z)
        q = abs(y - cy) / b
        return q < 1.0 and x <= a * (1.0 - q ** CRANIUM_E) ** (1.0 / CRANIUM_E)
    a, b, cy = jaw_params(z)
    if y >= cy:
        return x <= a
    q = (cy - y) / b
    return q < 1.0 and x <= a * (1.0 - q ** JAW_E) ** (1.0 / JAW_E)


_HEAD_POLAR = []


def head_polar():
    """頭の殻の後ろ端（関節 1）の外形。seg_02 の前端はこれに合わせる"""
    if not _HEAD_POLAR:
        z = hz(HEAD_REAR_ZM)
        _HEAD_POLAR.append(polar_from_inside(lambda x, y: head_inside(x, y, z)))
    return _HEAD_POLAR[0]


def cranium_skin(zmid):
    photo = -8.5 <= -zmid / U <= 8.5         # 眼下の発光点の列は頭の中ほどだけ（angler() の 0.35 < v < 0.8）

    def skin(t, _n):
        at = abs(t)
        if at < 25:
            return HEAD, 0.0
        if at < 55:
            return HEAD, 0.3
        if at < 82:
            return HEAD, 0.65
        if photo:
            return HEAD_PHOTO, 0.5
        return HEAD, 1.0
    return skin


def jaw_skin(t, _n):
    db = abs(t - 180.0)
    if db < 35:
        return BELLY, None          # 顎の下（暗い紫紺）
    if db < 60:
        return JAW_UNDER, 0.0       # 顎の線の下の発光点の列（上端）
    return JAW, 0.3 if db < 75 else 0.0


def eye(zm, y, radius, s, key, rim=True):
    """頭骨の側面の眼。闇の八角の眼窩、光る眼、骨の眼輪"""
    z = hz(zm)
    c, n = cranium_at_y(z, y * U, s)
    back = vunit(vsub(cranium_at_y(z + 1.0, y * U, s)[0], cranium_at_y(z - 1.0, y * U, s)[0]))
    up = vunit(vcross(back, n)) if s > 0 else vunit(vcross(n, back))
    r = radius * U
    # 眼の絵は四隅が透明で、縁が暗い（丸い眼）。眼窩は描き込みの暗い縁と、大きい眼だけ骨の眼輪で出す
    out = [K.slab(vadd(c, vmul(n, 0.4)), (2.05 * r, 1.6, 2.05 * r), back, n, EYE, key=f"{key}e")]
    if rim:
        pts = []
        for i in range(9):
            a = math.tau * i / 8 + math.pi / 8
            pts.append(vadd(vadd(c, vmul(n, 1.4)), vadd(vmul(back, 1.14 * r * math.cos(a)),
                                                          vmul(up, 1.14 * r * math.sin(a)))))
        for i in range(8):
            out.append(K.beam(pts[i], pts[i + 1], 0.24 * r, 0.22 * r, PLATE, side=n, key=f"{key}r{i}",
                              overshoot=0.16 * r))
    return out, vadd(c, vmul(n, 0.4))


EYES = ((2.0, 11.0, 4.2), (-5.0, 18.0, 1.4), (-3.0, 4.5, 1.2), (7.5, 9.5, 1.0))   # (z, y, 半径) 最初が大きい眼


def skull(info):
    """頭骨の殻・口蓋・喉・眼・眉・口縁の稜"""
    cubes = []
    step = 2.0 * CRANIUM_EDGE / CRANIUM_PLATES
    angles = [-CRANIUM_EDGE + step * (i + 0.5) for i in range(CRANIUM_PLATES)]
    rings = []
    for z, *_ in CRANIUM:
        a, b, cy = cranium_params(z)
        rings.append((ring(z, a, b, b, -CRANIUM_EDGE, CRANIUM_EDGE, CRANIUM_PLATES, CRANIUM_E, cy), cy, z))
    for i in range(len(rings) - 1):
        (r1, cy1, z1), (r0, cy0, z0) = rings[i], rings[i + 1]     # 表は後ろ→前の順。前を r0 にする
        cubes += ring_loft(r0, r1, angles, f"cr{i}_", 3.4 if i < 7 else 2.6, cranium_skin((z0 + z1) / 2.0),
                           axis_y=(cy0 + cy1) / 2.0, sink=0.35 * (i % 2))
    # 吻の先の蓋
    zt, hwt, topt, lipt = CRANIUM[-1]
    cubes.append(K.cube((-0.9 * hwt, lipt - 1.0, zt - 3.0), (1.8 * hwt, topt - lipt + 1.0, 4.0), HEAD, tone=0.8,
                        key="snoutcap"))
    # 口蓋（口を開けたとき殻の内側が見えないように）
    for i in range(len(CRANIUM) - 2):
        z0, z1 = CRANIUM[i][0], CRANIUM[i + 1][0]
        (x0, y0), (x1, y1) = lip(z0), lip(z1)
        y = max(max(y0, y1) + 1.6 * U, max(jaw_rim(z0)[1], jaw_rim(z1)[1]) + 2.0)
        cubes.append(deck((0.0, y, z0 + 1.0), (0.0, y, z1 - 1.0), 2.0 * 0.9 * min(x0, x1), 2.4, INNER,
                          f"palate{i}"))
    # 喉の奥（下半分の管）と、その奥の闇の壁
    tri = [(hz(zm), hw * U, up * U, dn * U) for zm, hw, up, dn in THROAT_T]
    n = 8
    t_angles = [70.0 + (220.0 / n) * (i + 0.5) for i in range(n)]
    for i in range(len(tri) - 1):
        za, ha, ua, da = tri[i + 1]           # 前
        zb_, hb, ub, db_ = tri[i]             # 後ろ
        r0 = ring(za, ha, ua, da, 70.0, 290.0, n, 2.2, THROAT_Y * U)
        r1 = ring(zb_, hb, ub, db_, 70.0, 290.0, n, 2.2, THROAT_Y * U)
        cubes += ring_loft(r0, r1, t_angles, f"th{i}_", 2.4, lambda t, nn: (THROAT, None), axis_y=THROAT_Y * U)
    zb_, hb, ub, db_ = tri[0]
    y_bot, y_top = THROAT_Y * U - db_, lip(zb_)[1] + 1.6 * U
    cubes.append(K.slab((0.0, (y_bot + y_top) / 2.0, zb_ + 2.0), (2.0 * hb, 2.0, y_top - y_bot),
                        (1, 0, 0), (0, 0, -1), CAVITY, key="gullet"))
    # 眼（大きい眼1つと小さい眼3つ）。大きい眼の位置は光暈のために contract へ
    info["eyes"] = []
    for s, side in SIDES:
        for j, (zm, y, r) in enumerate(EYES):
            out, centre = eye(zm, y, r, s, f"eye{side}{j}", rim=(j == 0))
            cubes += out
            if j == 0:
                info["eyes"].append({"bone": "head", "side": side, "center": [round(v, 2) for v in centre],
                                     "radius_units": round(r * U, 2)})
        # 眉の稜: 大きい眼の上を前後に走る骨板
        brow = []
        for zm, y, off in ((8.0, 14.0, 1.0), (2.0, 16.6, 3.0), (-4.5, 15.6, 1.6)):
            p, nn = cranium_at_y(hz(zm), y * U, s)
            brow.append(vadd(p, vmul(nn, off)))
        cubes += rod(brow, [7.0, 6.0], PLATE, f"brow{side}")
        # 口縁の稜（口の線をはっきりさせる面取り）
        lips = []
        for zm in (-14.5, -9.5, -4.5, 0.5, 5.5, 9.5, 12.5, 14.6):
            x, y = lip(hz(zm))
            lips.append((s * (x + 0.6), y + 1.0, hz(zm)))
        cubes += rod(lips, [6.0, 6.0, 5.6, 5.2, 4.6, 4.0, 3.4], PLATE, f"lip{side}")
    # 鰓蓋の縁の稜は置かない（頭と体を段なく一続きに見せる）
    # 頭頂の角（旧 AnglerMesh の horn spike）は、額の丸い盛り上がりを崩すので置かない
    return cubes


def jaw():
    """下顎の殻・口の床・顎先の蓋。pivot は HINGE"""
    cubes = []
    step = (360.0 - 2.0 * JAW_EDGE) / JAW_PLATES
    angles = [JAW_EDGE + step * (i + 0.5) for i in range(JAW_PLATES)]
    rings = []
    for z, *_ in JAWG:
        a, b, cy = jaw_params(z)
        rings.append((ring(z, a, b, b, JAW_EDGE, 360.0 - JAW_EDGE, JAW_PLATES, JAW_E, cy), cy))
    for i in range(len(rings) - 1):
        (r1, cy1), (r0, cy0) = rings[i], rings[i + 1]
        cubes += ring_loft(r0, r1, angles, f"jw{i}_", 3.2 if i < 7 else 2.4, jaw_skin,
                           axis_y=(cy0 + cy1) / 2.0, sink=0.35 * (i % 2))
    zt, hwt, rimt, bott = JAWG[-1]
    cubes.append(K.cube((-0.9 * hwt, bott - 1.0, zt - 3.0), (1.8 * hwt, rimt - bott + 1.0, 4.0), JAW, tone=0.3,
                        key="chincap"))
    # 口の床（縁のすぐ下）
    for i in range(len(JAWG) - 2):
        z0, z1 = JAWG[i][0], JAWG[i + 1][0]
        y = min(jaw_rim(z0)[1], jaw_rim(z1)[1]) - 0.8 * U
        w = min(jaw_x_at(z0, y), jaw_x_at(z1, y))
        cubes.append(deck((0.0, y, z0 + 1.0), (0.0, y, z1 - 1.0), 2.0 * 0.9 * w, 2.4, INNER, f"floor{i}"))
    return cubes


# 歯（旧描画の z, 長さ。ブロック）。上は口蓋の縁から垂れ、下は顎の縁に立つ。前ほど長い
# 不揃い: 長い牙と短い牙を交互に、前ほど長く
UPPER = ((-11.0, 3.0), (-8.5, 6.0), (-6.0, 3.4), (-3.5, 7.6), (-1.0, 4.2), (1.5, 9.8), (4.0, 5.0),
         (6.5, 12.0), (9.0, 6.4), (11.2, 13.5), (13.2, 8.0))
LOWER = ((-10.0, 3.6), (-7.0, 6.6), (-4.0, 4.0), (-1.0, 8.6), (2.0, 5.0), (5.0, 10.5), (8.0, 6.4),
         (10.6, 12.5), (12.8, 8.0), (15.8, 14.5), (18.6, 10.0))
SPINE_ROWS = (-11.0, -6.5, -2.0, 2.5, 7.0, 11.0)    # 口蓋と口の床の、喉へ寝た小棘の列


def tooth(base, tip, w, key, curl=(0.0, 0.0, 0.0)):
    """牙1本。根元から先へ 3 本の棒で細り、curl の向きへ少し反る（針のような牙）"""
    ctrl = vadd(vmid(base, tip), curl)
    pts = [bezier(base, ctrl, tip, c) for c in (0.0, 0.38, 0.72, 1.0)]
    return rod(pts, [w, w * 0.68, w * 0.38], TOOTH, key, overshoot=0.5)


def upper_teeth():
    cubes = []
    for s, side in SIDES:
        for i, (zm, length) in enumerate(UPPER):
            z = hz(zm)
            lx, ly = lip(z)
            jx = jaw_rim(z)[0]
            x = max((lx + jx) / 2.0, jx + 2.5)
            f = (zm + 15.0) / 28.0
            L = length * U
            base = (s * x, ly + 3.0, z)
            tip = (s * (x + 0.18 * L), ly - L, z - (0.12 + 0.25 * f) * L)
            w = 2.0 * (0.26 + 0.045 * length) * U * 0.8
            cubes += tooth(base, tip, w, f"ut{side}{i}", curl=(0.0, 0.0, 0.08 * L))     # 先が喉の側へ反る
        # 吻の牙: 吻の角から顎の外へ垂れる長い牙
        lx, ly = lip(hz(13.4))
        b = (s * (lx + 0.3 * U), ly + 1.5 * U, hz(13.4))
        cubes += horn(b, vadd(b, (s * 0.9 * U, -7.0 * U, 0.6 * U)), vadd(b, (s * 2.2 * U, -14.0 * U, 2.6 * U)),
                      1.3 * U, f"sfang{side}", region=TOOTH, cuts=(0.0, 0.45, 0.8, 1.0), taper=(1.0, 0.7, 0.4))
        # 口蓋の小棘（喉へ寝る）
        for j, zm in enumerate(SPINE_ROWS):
            z = hz(zm)
            lx, ly = lip(z)
            y = ly + 1.6 * U
            p = (s * 0.45 * lx, y, z)
            cubes.append(K.beam(p, vadd(p, (0.0, -2.0 * U, 1.2 * U)), 0.9 * U, 0.9 * U, TOOTH,
                                side=(1, 0, 0), key=f"psp{side}{j}"))
    return cubes


def lower_teeth():
    cubes = []
    for s, side in SIDES:
        for i, (zm, length) in enumerate(LOWER):
            z = hz(zm)
            jx, jy = jaw_rim(z)
            # 閉じたとき頭骨を突き抜けない長さまで（前の長い牙は顎先の2本が受け持つ）
            if zm < SKULL_T[-1][0] - 0.5:          # 吻より前（受け口の先）は頭骨に当たらない
                room = (skull_top(z) - jy) / U
                length = min(length, 0.8 * room + 1.0)
            f = (zm + 14.0) / 28.0
            L = length * U
            x = 0.84 * jx
            base = (s * x, jy - 3.0, z)
            tip = (s * (x + 0.05 * L), jy + L, z - (0.1 + 0.25 * f) * L)
            w = 2.0 * (0.26 + 0.045 * length) * U * 0.8
            cubes += tooth(base, tip, w, f"lt{side}{i}", curl=(0.0, 0.0, 0.08 * L))
        # 顎先の牙: 吻の前へ高く立ち上がる2本（AnglerMesh の chin fangs）
        jx, jy = jaw_rim(hz(20.6))
        b = (s * 0.7 * jx, jy - 1.5 * U, hz(20.6))
        cubes += horn(b, vadd(b, (s * 0.6 * U, 8.0 * U, -0.4 * U)), vadd(b, (s * 1.6 * U, 15.5 * U, 1.4 * U)),
                      1.6 * U, f"cfang{side}", region=TOOTH, cuts=(0.0, 0.45, 0.8, 1.0), taper=(1.0, 0.7, 0.4))
        for j, zm in enumerate(SPINE_ROWS):
            z = hz(zm)
            jx, jy = jaw_rim(z)
            y = jy - 0.8 * U
            p = (s * 0.45 * jx, y, z)
            cubes.append(K.beam(p, vadd(p, (0.0, 2.0 * U, 1.2 * U)), 0.9 * U, 0.9 * U, TOOTH,
                                side=(1, 0, 0), key=f"fsp{side}{j}"))
    return cubes


# ---- 提灯（AnglerMesh.LURES と同じ柄の曲線）
# (名前, 親, 付け根の場所, x, 付け根の z, 出る向き xyz, 曲がり xyz, 長さ, 珠の半径, 位相)
# 付け根の場所: "skull"（頭の上面）/ "jaw"（顎の下面）/ "back" / "belly"（関節座標 k の体の背・腹）。
# 向きと曲がりは旧描画の向き（+Z が前）。柄の点 = 付け根 + 長さ × (向き t + 曲がり t²)
LURES = (
    # 主の提灯（額の柄と珠）。吻の上の額から前へ弧を描き、珠は口の前に垂れる
    ("lure_stalk", "head", "skull", 0.0, 9.0, (0.0, 0.9, 0.45), (0.0, -0.95, 0.42), 30.0, 2.6, 0.0),
    # ほかは頭と体の丸い輪郭を崩さない短い房（体に沿う小さな発光器）
    ("lure_nape_1", "head", "skull", -5.0, -6.0, (-0.3, 0.95, 0.0), (-0.05, -0.5, -0.5), 11.0, 1.3, 1.7),
    ("lure_nape_2", "head", "skull", 5.5, -13.0, (0.3, 0.95, -0.1), (0.05, -0.5, -0.5), 10.0, 1.2, 3.1),
    ("lure_chin_1", "jaw", "jaw", 0.0, 9.0, (0.0, -1.0, 0.25), (0.0, 0.2, -0.6), 12.0, 1.3, 0.9),
    ("lure_chin_2", "jaw", "jaw", -6.0, 2.0, (-0.25, -1.0, 0.05), (-0.05, 0.25, -0.6), 10.0, 1.2, 2.3),
    ("lure_chin_3", "jaw", "jaw", 6.0, -5.0, (0.25, -1.0, 0.0), (0.05, 0.25, -0.6), 9.0, 1.2, 4.0),
    ("lure_back_1", "seg_03", "back", -3.0, 2.5, (-0.2, 1.0, 0.2), (0.0, -0.4, -0.6), 9.0, 1.2, 5.1),
    ("lure_back_2", "seg_04", "back", 2.5, 3.5, (0.2, 1.0, 0.1), (0.0, -0.4, -0.65), 8.0, 1.1, 0.4),
    ("lure_belly_1", "seg_03", "belly", 0.0, 2.1, (0.0, -1.0, 0.1), (0.0, 0.25, -0.65), 9.0, 1.2, 2.9),
    ("lure_belly_2", "seg_03", "belly", -5.0, 2.6, (-0.2, -1.0, 0.0), (0.0, 0.25, -0.6), 8.0, 1.1, 3.7),
    ("lure_belly_3", "seg_04", "belly", 4.5, 3.2, (0.2, -1.0, 0.0), (0.0, 0.25, -0.6), 8.0, 1.1, 1.2),
    ("lure_belly_4", "seg_04", "belly", 0.0, 3.8, (0.0, -1.0, -0.1), (0.0, 0.2, -0.55), 7.0, 1.0, 4.6),
)
STALK_POINTS = 7
BULB_LENGTH = 2.6                      # 珠の長さ / 半径（AnglerMesh.BULB_LENGTH）
BULB_MAIN = ((0.0, 0.28), (0.14, 0.5), (0.34, 0.8), (0.58, 0.97), (0.8, 0.82), (0.95, 0.38))
BULB_SMALL = ((0.0, 0.35), (0.3, 0.85), (0.62, 0.95), (0.9, 0.4))


def lure_root(where, x, zk):
    """付け根の geo の点（少し皮へ埋める）"""
    if where == "skull":
        z = hz(zk)
        a, b, cy = cranium_params(z)
        q = clamp(abs(x * U) / a, 0.0, 0.999)
        y = cy + b * (1.0 - q ** CRANIUM_E) ** (1.0 / CRANIUM_E)
        return (x * U, y - 1.2 * U, z)
    if where == "jaw":
        z = hz(zk)
        a, b, cy = jaw_params(z)
        q = clamp(abs(x * U) / a, 0.0, 0.999)
        y = cy - b * (1.0 - q ** JAW_E) ** (1.0 / JAW_E)
        return (x * U, y + 1.0 * U, z)
    hw, up, dn = sec(zk)
    q = clamp(abs(x * U) / hw, 0.0, 0.999)
    f = (1.0 - q ** BODY_E) ** (1.0 / BODY_E)
    y = (up * f - 1.0 * U) if where == "back" else (-dn * f + 1.0 * U)
    return (x * U, y, zk * SEG)


def lure(spec):
    """柄の点の列と珠の箱。返り値: (柄の箱, 珠の箱, 付け根, 柄の先, 珠の中心)"""
    name, parent, where, x, zk, d, b, length, r, _ph = spec
    root = lure_root(where, x, zk)
    L = length * U
    pts = []
    for j in range(STALK_POINTS):
        t = j / (STALK_POINTS - 1)
        off = (d[0] * t + b[0] * t * t, d[1] * t + b[1] * t * t, -(d[2] * t + b[2] * t * t))
        pts.append(vadd(root, vmul(off, L)))
    widths = [2.0 * (0.55 - 0.04 * j) * U * 0.9 for j in range(STALK_POINTS - 1)]
    stalk = rod(pts, widths, STALK, f"{name}st", overshoot=1.0)
    # 珠: 柄の最後の向きと真下のあいだへ垂れる（AnglerMesh.lures と同じ）
    tx, ty, tz = d[0] + 2 * b[0], d[1] + 2 * b[1], -(d[2] + 2 * b[2])
    tl = math.sqrt(tx * tx + ty * ty + tz * tz)
    axis = vunit((0.5 * tx / tl, 0.5 * ty / tl - 1.0, 0.5 * tz / tl))
    lb = BULB_LENGTH * r * U
    tip = pts[-1]
    stations = BULB_MAIN if name == "lure_stalk" else BULB_SMALL
    centres = [vadd(tip, vmul(axis, lb * f)) for f, _ in stations]
    radii = [r * U * q for _, q in stations]
    n = 8 if name == "lure_stalk" else 6
    bulb = axis_loft(centres, radii, n, BULB, f"{name}bu", thick=max(1.6, 0.25 * r * U))
    end = vadd(tip, vmul(axis, lb * (stations[-1][0] + 0.03)))
    cap = r * U * stations[-1][1] * 1.6
    bulb.append(K.slab(end, (cap, cap, cap), vcross(axis, (0, 0, 1)) if abs(axis[2]) < 0.9 else (1, 0, 0),
                       axis, BULB, key=f"{name}cap"))
    centre = vadd(tip, vmul(axis, lb * 0.6))
    return stalk, bulb, root, tip, centre


# ---- 胸びれ（頭の子）。頭の殻の後ろ端のすぐ前から後ろへ流れる（AnglerMesh の pectoral fan）
PECTORAL = (13.0, 16.0, 17.0, 15.0, 11.0)     # 頭に対して小さく丸い胸びれ
PECTORAL_ROOT = (-6.0, -14.0)      # (y, z)。頭の殻の後ろ端のすぐ前の側面（付け根は皮に少し埋める）


def pectoral(s):
    p, nn = cranium_at_y(hz(PECTORAL_ROOT[1]), PECTORAL_ROOT[0] * U, s)
    root = vsub(p, vmul(nn, 0.5 * U))
    tips = []
    for j, length in enumerate(PECTORAL):
        d = vunit((s * 0.4, 0.15 - 0.225 * j, 1.0))
        tips.append(vadd(root, vmul(d, length * U)))
    e1 = vunit((s * 0.4, 0.0, 1.0))
    e2 = (0.0, -1.0, 0.0)
    return fan_to(root, e1, e2, tips, f"pec{s}", ray_w=2.4), root


def head(bones, info):
    K.bone(bones, "head", (0.0, 0.0, 0.0), skull(info))
    K.bone(bones, "jaw", HINGE, jaw(), parent="head")
    K.bone(bones, "teeth_lower", HINGE, lower_teeth(), parent="jaw")
    K.bone(bones, "teeth_upper", (0.0, 0.0, 0.0), upper_teeth(), parent="head")
    for s, side in SIDES:
        cubes, root = pectoral(s)
        K.bone(bones, f"fin_{side}", root, cubes, parent="head")


def lures(bones, info, extras_after):
    """提灯の骨。頭と顎の子はここで、体の子は体節の後に積む（親を先に置くため）"""
    info["lures"] = []
    for spec in LURES:
        name, parent = spec[0], spec[1]
        stalk, bulb, root, tip, centre = lure(spec)
        entry = {"bone": "lure_bulb" if name == "lure_stalk" else name, "parent": parent,
                 "center": [round(v, 2) for v in centre], "radius_units": round(spec[8] * U, 2),
                 "main": name == "lure_stalk"}
        info["lures"].append(entry)
        if name == "lure_stalk":
            K.bone(bones, "lure_stalk", root, stalk, parent=parent)
            K.bone(bones, "lure_bulb", tip, bulb, parent="lure_stalk")
        elif parent in ("head", "jaw"):
            K.bone(bones, name, root, stalk + bulb, parent=parent)
        else:
            extras_after.append((name, root, stalk + bulb, parent))


# ============================================================ アニメーション
IDLE = 4.0


def mirror(axis, s):
    """左右の鏡写し。X まわりはそのまま、Y・Z まわりは符号が返る"""
    return (axis[0], axis[1] * s, axis[2] * s)


def orbit(length, ax, az, phase, steps=8):
    """x と z を 1/4 周期ずらして揺らす（提灯が小さな楕円を描いて揺れる）"""
    frames = []
    for i in range(steps + 1):
        a = math.tau * i / steps + phase
        frames.append((round(length * i / steps, 2), (ax * math.sin(a), 0.0, az * math.cos(a)), EASE_SOFT))
    return rot(frames)


def animations(bones):
    names = {b["name"] for b in bones}
    idle = {
        # 顎: 閉じきらず、ゆっくり少し開いて閉じる（rx が正で口が開く）
        "jaw": rot([(0.0, (3, 0, 0)), (1.6, (11, 0, 0), EASE_SOFT), (2.4, (11, 0, 0)),
                    (IDLE, (3, 0, 0), EASE_SOFT)]),
        # 主の提灯: 柄はゆっくり、珠は遅れて大きく振り子に揺れる
        "lure_stalk": orbit(IDLE, 3.0, 2.5, 0.0),
        "lure_bulb": orbit(IDLE, 9.0, 7.0, -0.9),
    }
    for spec in LURES[1:]:
        idle[spec[0]] = orbit(IDLE, 5.0, 4.0, spec[9])
    for s, side in SIDES:
        idle[f"fin_{side}"] = wave(IDLE, 8.0, 0.3, mirror((0.25, 1.0, 0.4), s))
        if f"fin_pelvic_{side}" in names:
            idle[f"fin_pelvic_{side}"] = wave(IDLE, 6.0, 1.1, mirror((0.3, 0.5, 1.0), s))
    for k in range(1, JOINTS + 1):
        name = f"seg_{k:02d}_fin"
        if name in names:
            if k in CAUDAL_SEGS:
                idle[name] = wave(IDLE, 3.0 + 1.5 * (k - CAUDAL_SEGS[0]), -0.6 * k, (0, 1, 0))   # 尾びれを左右に煽る
            else:
                idle[name] = wave(IDLE, 3.0, -0.6 * k, (0, 0, 1))                    # 背びれを左右に揺らす
        name = f"seg_{k:02d}_anal"
        if name in names:
            idle[name] = wave(IDLE, 3.0, -0.6 * k + 1.0, (0, 0, 1))
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
    bones, info = [], {}
    head(bones, info)
    body_lures = []
    lures(bones, info, body_lures)
    extras = {}
    eyes_body = []
    for j, (kk, r) in enumerate(((2.15, 1.5), (2.5, 1.3), (2.85, 1.1))):
        for s, side in SIDES:
            eyes_body += body_eye(kk, r, s, f"beye{side}{j}")
    extras["seg_03"] = eyes_body
    for k in range(1, JOINTS + 1):
        segment(k, bones, extras)
        name = f"seg_{k:02d}"
        zc = (k - 0.5) * SEG
        if k not in CAUDAL_SEGS:
            d = dorsal(k)
            if d:
                K.bone(bones, f"{name}_fin", (0.0, top_y(zc), zc), d, parent=name)
            a = anal(k)
            if a:
                K.bone(bones, f"{name}_anal", (0.0, bottom_y(zc), zc), a, parent=name)
        else:
            K.bone(bones, f"{name}_fin", (0.0, 0.0, (k - 1) * SEG), caudal(k), parent=name)
        if k == 3:
            for s, side in SIDES:
                cubes, root = pelvic(s)
                K.bone(bones, f"fin_pelvic_{side}", root, cubes, parent=name)
        for lname, root, cubes, parent in body_lures:
            if parent == name:
                K.bone(bones, lname, root, cubes, parent=parent)
    length = (JOINTS * SEG + 16.0 * U + 30 * U) / 16.0
    return K.model(f"geometry.{NAME}", bones, round(length + 8.0, 1), round(80 * U / 16.0, 1), 0), bones, info


def contract(bones, cubes, anims, info):
    def entry(b):
        e = {"name": b["name"], "parent": b.get("parent"), "pivot": b["pivot"]}
        if b["name"] == "head":
            e["role"] = ("頭。関節 0 に pivot。-Z が鼻先。殻の後ろ端は関節 1（seg_02 の前端の球面が"
                         "殻の内側へ入る）")
        elif b["name"].startswith("seg_") and not b.get("parent"):
            k = int(b["name"][4:])
            e["role"] = f"体節 {k}: 関節 {k - 1}（前）〜関節 {k}（後ろ）。pivot は中心"
            if k == 1:
                e["role"] += "。頭の殻の内側に入るので箱なし（位置だけ合わせる）"
            elif k > CORE_END + 1:
                e["role"] += "。体の芯は無く、尾びれ（_fin の子）だけ"
            e["joint_front"], e["joint_back"] = k - 1, k
        return e
    return {
        "entity": NAME,
        "generator": "tools/boss/angler_model/build.py（手で直さず、生成元を直して流し直す）",
        "geo": f"assets/abyssia/geo/entity/{NAME}.geo.json",
        "animation": f"assets/abyssia/animations/entity/{NAME}.animation.json",
        "texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo.png",
        "glow_texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png",
        "glow_note": ("光らない画素は (0,0,0,0)。RenderType.eyes（加算）でも GeckoLib の自動発光層でも使える。"
                      "光るのは提灯の珠・眼・体の発光器・頭と顎の発光点・口の中の裏地（ごく暗い紫）"),
        "membrane_note": ("鰭膜（FIN / MEMBRANE 領域）は旧テクスチャと同じ部分不透明（alpha 120〜235）。"
                          "entityCutoutNoCull なら不透明の膜、entityTranslucent なら透ける膜になる。"
                          "膜は厚み 0 の板なので裏面も描く（NoCull）こと"),
        "texture_size": [atlas.SIZE, atlas.SIZE],
        "model_scale": MODEL_SCALE,
        "units": "geo の 1 単位 = 1/16 ブロック。描画で MODEL_SCALE 倍するので、ゲーム内では MODEL_SCALE/16 ブロック",
        "segment_length_units": SEG,
        "segment_length_blocks": JOINT_SPACING,
        "joints": JOINTS + 1,
        "segments": JOINTS,
        "rest_pose": ("頭から尾まで一直線。関節 j は geo の (0, 0, segment_length_units * j)。"
                      "頭は -Z を向く。体の芯は y = 0"),
        "head_pivot": [0.0, 0.0, 0.0],
        "segment_pivot": "seg_k の pivot = (0, 0, segment_length_units * (k - 0.5))",
        "jaw_hinge": [round(v, 2) for v in HINGE],
        "rest_head_extent_units": {"snout_z": round(CRANIUM[-1][0], 2), "chin_z": round(JAWG[-1][0], 2),
                                   "rear_z": round(hz(HEAD_REAR_ZM), 2),
                                   "half_width": round(max(r[1] for r in CRANIUM), 2),
                                   "top_y": round(max(r[2] for r in CRANIUM), 2),
                                   "jaw_bottom_y": round(min(r[3] for r in JAWG), 2)},
        "body_section_units": [{"k": r[0], "half_width": round(r[1] * U, 2), "up": round(r[2] * U, 2),
                                "down": round(r[3] * U, 2)} for r in BODY],
        "body_section_note": "関節座標 k（0 が頭の関節、9 が尾の先）での体の断面。超楕円の指数 BODY_E",
        "lure_bulbs": info["lures"],
        "lure_note": ("光暈（glow_halo のビルボード）の中心。center は静止姿勢の geo 座標で、bone の動き"
                      "（親の体節・顎・柄の揺れ）に付いて動く。main が主の提灯（額から鼻先の前へ垂れる）"),
        "eye_halos": info["eyes"],
        "animation_signs": {
            "jaw": "rotation の x が正で口が開く（idle は 3→11 度。噛みつきの大口は 40〜50 度が目安）",
            "lure_stalk / lure_bulb": "柄は付け根、珠は柄の先が pivot。x と z で小さな楕円を描いて揺れる",
            "lure_nape_* / lure_chin_* / lure_back_* / lure_belly_*": "付け根が pivot。x と z で揺れる",
            "fin_l / fin_r": "胸びれ。付け根が pivot。左右で y と z の符号が逆（鏡写し）",
            "fin_pelvic_l / fin_pelvic_r": "腹びれ（seg_03 の子）。付け根が pivot。左右で y と z の符号が逆",
            "seg_02_fin..seg_07_fin": "背びれ。pivot は体節の背の中央、z で左右に揺れる",
            "seg_05_anal..seg_07_anal": "臀びれ。pivot は体節の腹の中央、z で左右に揺れる",
            "seg_08_fin / seg_09_fin": "尾びれ（上葉と下葉）。pivot は体節の前端の芯、y で左右に煽る",
        },
        "bone_rule": ("head と seg_01..seg_09 は root 直下。毎フレーム位置と回転を上書きする。"
                      "seg_k は関節 k-1 と k の中点へ、局所 -Z が関節 k から関節 k-1 を向くように置く。"
                      "head は関節 0 へ、-Z が関節 1 から関節 0 の向き（さらに先）を向くように置く"
                      "（seg_01 と同じ向きになるので、頭と seg_01 は常に一体）。"
                      "子（jaw, teeth_*, lure_*, fin_*, seg_XX_fin, seg_XX_anal）は animation に任せる"),
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
        "cube_limit": CUBE_LIMIT,
        "bones": [entry(b) for b in bones],
    }


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    geo, bones, info = build_geo()
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
    atlas.write_png(TEX / f"{NAME}_geo_glow.png", atlas.build_glow())
    (HERE / 'contract.json').write_text(json.dumps(contract(bones, cubes, anims, info), indent=1,
                                                   ensure_ascii=False), encoding='utf-8')
    per = {}
    for b in bones:
        root = b["name"]
        by = {x["name"]: x for x in bones}
        while by[root].get("parent"):
            root = by[root]["parent"]
        key = "head" if root == "head" else "body"
        per[key] = per.get(key, 0) + len(b["cubes"])
    print(f"cubes: 計 {cubes}（頭 {per.get('head', 0)} / 体 {per.get('body', 0)}）、"
          f"MODEL_SCALE {MODEL_SCALE:g}、体節 {SEG:g} 単位")
    print("次に流すこと:")
    print("  python tools/boss/check_animations.py abyss_angler")
    print("  python tools/boss/angler_model/preview.py")


if __name__ == '__main__':
    main()
