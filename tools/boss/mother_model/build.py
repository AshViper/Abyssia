# -*- coding: utf-8 -*-
"""深淵の母（abyss_mother）の GeckoLib モデル一式を書き出す。

```bash
python tools/boss/mother_model/build.py
```

書き出すもの（どれも丸ごと上書き。手で直さず、ここを直して流し直す）:

- `assets/abyssia/geo/entity/abyss_mother.geo.json`
- `assets/abyssia/animations/entity/abyss_mother.animation.json`（idle と bloom）
- `assets/abyssia/textures/entity/boss/abyss_mother_geo.png` と `_glow.png`
- `tools/boss/mother_model/contract.json`（描画の Java 側との約束）

<h2>骨の約束（Java の描画が触手と口腕を曲げる）</h2>
root 直下に `bell`（傘の本体）と、触手の体節 `tent_T_SS`（12 本 × 27）、口腕の体節 `oral_O_SS`
（5 本 × 9）を並べる。体節は静止姿勢でまっすぐ下へ垂れ、pivot は体節の中心。局所 +Y が付け根側。
Java は毎フレーム root の骨の位置と回転を上書きする。うねりは Java が入れるので、animation は
`bell` の子（花弁・裾・フリル・心臓）だけを動かす。

<h2>傘は花弁に割る</h2>
傘は 24 列 × 7 段の外面と、24 列 × 4 段の下傘の板。2 列ずつを 12 枚の花弁 `bell_petal_k`
（pivot は頂点、開花で外へ倒れる）にまとめ、肩より下を子の `bell_skirt_k`（pivot は肩、拍動で
内へ縮む）、縁のフリルをさらに子の `bell_frill_k`（pivot は縁、波打つ）にする。開花と拍動は
別の骨なので、bloom と idle を同時に流しても取り合わない。

<h2>半透明は外殻だけ</h2>
傘・下傘の板は厚み 0.4 単位の1枚で、内側に隠れた箱を作らない。板は長方形だが、テクスチャで
台形の外を透明に抜くので、隣の板と重ならない（重なると半透明が二重になって筋が出る）。
傘の中に見える口柄・放射管・環状管・心臓は不透明。開花すると、放射管と環状管が傘の骨のように
残り、触手はそこから垂れる（触手の付け根は AbyssMother.anchor の固定位置なので）。

<h2>寸法</h2>
実寸は AbyssMother.java（触手 12 本、関節 28・間隔 6、付け根は半径 15・高さ -3）と、
旧描画 AbyssMotherRenderer.java（傘と下傘の断面の表、口柄、心臓 CORE_Y = -4・半径 3.4、
口腕 5 本 10 点、触手の太さ 0.6→0.14）から取った。モデルは MODEL_SCALE 分の 1 に縮めて作る。
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
import shape
from atlas import (BELL_OUT, BELL_IN, UNDER_OUT, UNDER_IN, HEART, STALK, SLEEVE, BULB, CLEAR, ORAL, CANAL,
                   FRILL, ORAL_FRILL, STRAND)
from boss_kit import (_add as vadd, _sub as vsub, _mul as vmul, _unit as vunit, _cross as vcross,
                      _dot as vdot, _mid as vmid, _dist as vdist, rot, scale, euler_from_axes)
from shape import BELL, UNDER, COLUMNS, PETALS, SKIRT_OUTER, SKIRT_UNDER

ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/abyssia'
GEO = ASSETS / 'geo/entity'
ANIM = ASSETS / 'animations/entity'
TEX = ASSETS / 'textures/entity/boss'
NAME = 'abyss_mother'

K = boss_kit.Kit(atlas, stretched=atlas.STRETCHED, top_anchored=atlas.TOP_ANCHORED)

# ============================================================ 実寸
MODEL_SCALE = shape.MODEL_SCALE     # 描画側で一様に掛ける倍率。geo の 1 単位 = MODEL_SCALE / 16 ブロック
U = shape.U                         # 1 ブロックが何単位か（4）

# 触手（AbyssMother）
TENTACLES = 12                      # AbyssMother.TENTACLES
JOINTS = 28                         # AbyssMother.JOINTS（関節 0 が付け根）
SPACING = 6.0                       # AbyssMother.SPACING（ブロック）
TSEGS = JOINTS - 1                  # 1 本の体節の数（27）
TSEG = SPACING * U                  # 体節の長さ（24 単位）
ANCHOR_R, ANCHOR_Y = 15.0, -3.0     # AbyssMother.ANCHOR_R / ANCHOR_Y
TENT_R0, TENT_R1 = 0.6, 0.14        # 太さ（半径、ブロック）。旧描画 tentacles() の lerp(k / 27, 0.6, 0.14)
TENT_SIDE = 1.6                     # 断面の正方形の一辺 / 半径（45 度ずらした2本で八角の星形）
SLEEVE_SEGS = 12                    # 付け根から何体節まで、関節に数珠（袖）を被せるか

# 口腕（AbyssMotherRenderer.organs）
ORAL_ARMS = 5                       # ORAL_ARMS
ORAL_SEGS = 9                       # ORAL_POINTS 10 点 = 9 区間
ORAL_SEG_BLOCKS = 3.375             # 区間の長さ。旧描画の全長約 30 を 9 等分
OSEG = ORAL_SEG_BLOCKS * U          # 13.5 単位
ORAL_R0, ORAL_Y = 2.2, -6.0         # 付け根（軸から 2.2、高さ -6）
ORAL_PHASE = math.degrees(0.3)      # 腕 0 の向き（arm * 72 度 + 0.3 rad）
ORAL_V = 40.0                       # 2 枚のフリルが外向きから左右へ開く角
ORAL_RUFFLE = 14.0                  # フリルを体節ごとに互い違いに振る角（襞）

# 器官（AbyssMotherRenderer）
CORE_Y = -4.0                       # 心臓の中心
HEART_R = 3.4                       # 心臓の半径（閉じた傘）。開花で 4.6
HEART_BLOOM = 4.6 / 3.4
STALK_UP = ((7.4, 1.0), (3.4, 1.3), (-1.0, 1.7))       # 口柄 (y, r)。頂点の下から心臓の中へ（細い胃の柱）
STALK_DOWN = ((-6.5, 2.0), (-9.0, 1.5), (-11.0, 0.6))  # 心臓の下から（旧描画 STALK）
SPOKE = ((6.0, 0.9), (2.8, 9.5), (ANCHOR_Y + 0.4, ANCHOR_R))  # 放射管 (y, r)。下傘の少し上を通って付け根へ
FRILL_H = 3.4                       # フリルの長さ（旧描画 3.4 + 0.5 sin）
FRILL_FLARE = 8.0                   # フリルが外へ開く角
SHELL = 0.4                         # 半透明の板の厚み（単位）

# アニメーション
IDLE = 2.5                          # AbyssMother.beat の周期（50 tick）
SKIRT_DEG = 9.0                     # 拍動で裾が内へ縮む角
FRILL_DEG = 10.0                    # フリルの波の振れ
BLOOM = 3.0                         # 開花にかかる秒数（そのあと保持）
BLOOM_DEG = 38.0                    # 開花で花弁が外へ倒れる角


# ============================================================ 向きと点
def geo_angle(render_deg):
    """描画空間（X 反転後）の角 a（x = cos a, z = sin a）を、geo の角へ。geo では x = -cos a"""
    return 180.0 - render_deg


def radial(theta):
    t = math.radians(theta)
    return (math.cos(t), 0.0, math.sin(t))


def rotate_y(v, deg):
    """水平の向き v を、geo の角が増える向きへ deg 回す"""
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    return (v[0] * c - v[2] * s, v[1], v[0] * s + v[2] * c)


def at(y, r, theta):
    """(y, r) ブロックの断面の点を geo 角 theta の位置へ（単位）"""
    t = math.radians(theta)
    return (r * math.cos(t) * U, y * U, r * math.sin(t) * U)


def petal_theta(k):
    """花弁 k の中心の geo 角。tent_k の付け根の真上"""
    return geo_angle((k - 1) * 360.0 / PETALS)


def tangent(theta):
    """花弁の倒れる軸。この軸まわりに正の角で回すと、下（-Y）が外（radial）へ振れる"""
    return vcross(radial(theta), (0.0, 1.0, 0.0))


def tentacle_root(t):
    """触手 t（1..12）の関節 0。描画空間の AbyssMother.anchor(t-1) - headPos() と同じ点（geo 単位）"""
    th = geo_angle((t - 1) * 360.0 / TENTACLES)
    return at(ANCHOR_Y, ANCHOR_R, th), th


def oral_root(o):
    th = geo_angle((o - 1) * 360.0 / ORAL_ARMS + ORAL_PHASE)
    return at(ORAL_Y, ORAL_R0, th), th


# ============================================================ 板
def panel(a, b, c, d, hint, thick, region=CLEAR, tone=None, key=None, lap_w=1.0, lap_h=1.0):
    """4 点 a b（上辺 = 頂点側）c d（下辺）の台形を囲む長方形の板。

    局所 X = 弦の向き（a→b）、局所 Y = 外の面の法線（hint の側）、局所 Z = 上辺へ上る向き。
    外の面（up）の絵は上端が上辺（頂点側）。外の面は台形の平面に乗り、厚みは内へ取る。
    戻り値の2つ目は局所 X と a→b の向きの内積（正なら絵の右が a→b の先）。
    """
    top, bot = vmid(a, b), vmid(c, d)
    m = vsub(top, bot)
    height = math.sqrt(vdot(m, m))
    m = vunit(m)
    e = vunit(vadd(vsub(b, a), vsub(d, c)))
    n = vunit(vcross(m, e))
    if vdot(n, hint) < 0:
        n = vmul(n, -1.0)
    x = vcross(n, m)
    width = max(vdist(a, b), vdist(c, d)) * lap_w
    centre = vsub(vmul(vadd(vadd(a, b), vadd(c, d)), 0.25), vmul(n, thick / 2.0))
    cube = K.slab(centre, (width, thick, height * lap_h), x, n, region, tone=tone, key=key)
    return cube, vdot(x, e)


def _uv(region):
    return {"uv": [region[0], region[1]], "uv_size": [region[2], region[3]]}


def shell_faces(cube, out_region, in_region):
    """半透明の板の UV。外の面と内の面にだけ絵を貼り、厚みの面は透明（CLEAR）にする"""
    cube["uv"] = {"north": _uv(CLEAR), "south": _uv(CLEAR), "east": _uv(CLEAR), "west": _uv(CLEAR),
                  "up": _uv(out_region), "down": _uv(in_region)}
    return cube


def shell(profile, rows, outer, k, tag):
    """花弁 k の 2 列 × rows の区間の板。outer は傘の外面、でなければ下傘（外の面は傘の内側を向く）"""
    tc = petal_theta(k)
    half = 360.0 / COLUMNS
    regs = (BELL_OUT, BELL_IN) if outer else (UNDER_OUT, UNDER_IN)
    cubes = []
    for col, (t0, t1) in enumerate(((tc - half, tc), (tc, tc + half))):
        centre_up = col == 0                       # 花弁の中心が、角の増える側（t1）にあるか
        for i in rows:
            (y0, r0), (y1, r1) = profile[i], profile[i + 1]
            a, b, c, d = at(y0, r0, t0), at(y0, r0, t1), at(y1, r1, t0), at(y1, r1, t1)
            mid = vmul(vadd(vadd(a, b), vadd(c, d)), 0.25)
            hint = mid if outer else vsub((0.0, -20.0 * U, 0.0), mid)
            cube, sx = panel(a, b, c, d, hint, SHELL, key=f"{tag}{k}_{col}_{i}")
            side = "R" if (sx > 0) == centre_up else "L"
            cubes.append(shell_faces(cube, regs[0][side][i], regs[1][side][i]))
    return cubes


def frill(k):
    """縁から垂れるフリル（列ごとに1枚、厚み 0）。絵の上端が自由縁（垂れた先）"""
    tc = petal_theta(k)
    half = 360.0 / COLUMNS
    yr, rr = BELL[-1]
    out = []
    for col, (t0, t1) in enumerate(((tc - half, tc), (tc, tc + half))):
        a, b = at(yr, rr, t0), at(yr, rr, t1)
        rho = radial((t0 + t1) / 2.0)
        f = math.radians(FRILL_FLARE)
        hang = vunit(vadd((0.0, -math.cos(f), 0.0), vmul(rho, math.sin(f))))
        j = 2 * (k - 1) + col
        length = FRILL_H * (1.0 + 0.13 * math.sin(j * 5.1)) * U
        centre = vadd(vadd(vmid(a, b), vmul(hang, length / 2.0 - 1.0)), vmul(rho, 0.3))
        out.append(K.slab(centre, (vdist(a, b) * 1.02, length, 0.0), vunit(vsub(b, a)), hang, FRILL,
                          key=f"fr{k}_{col}"))
    return out


# ============================================================ 器官（不透明）
def ring_panels(table, sides, region, tag, thick, centre_y=None, lap_w=1.12):
    """軸まわりの筒（口柄）。table は上から (y, r)"""
    cubes = []
    for i in range(len(table) - 1):
        (y0, r0), (y1, r1) = table[i], table[i + 1]
        for j in range(sides):
            t0, t1 = 360.0 * j / sides, 360.0 * (j + 1) / sides
            a, b, c, d = at(y0, r0, t0), at(y0, r0, t1), at(y1, r1, t0), at(y1, r1, t1)
            mid = vmul(vadd(vadd(a, b), vadd(c, d)), 0.25)
            hint = (mid[0], 0.0, mid[2]) if centre_y is None else vsub(mid, (0.0, centre_y, 0.0))
            cubes.append(panel(a, b, c, d, hint, thick, region, key=f"{tag}{i}_{j}", lap_w=lap_w, lap_h=1.04)[0])
    return cubes


def stalk():
    """口柄。傘の頂点の下から心臓を貫いて下へ。心臓の中は作らない"""
    cubes = ring_panels(STALK_UP, 8, STALK, "su", 1.6) + ring_panels(STALK_DOWN, 8, STALK, "sd", 1.6)
    cubes.append(K.slab((0.0, STALK_UP[0][0] * U - 0.4, 0.0), (4.6, 1.2, 4.6), radial(22.5), (0, 1, 0), STALK,
                        key="stalktop"))
    cubes.append(K.slab((0.0, STALK_DOWN[-1][0] * U + 0.4, 0.0), (4.6, 1.2, 4.6), radial(22.5), (0, 1, 0), STALK,
                        key="stalktip"))
    return cubes


def canals():
    """環状管（触手の付け根を結ぶ環）・放射管（口柄から付け根へ）・触手の付け根の球"""
    cubes = []
    ring = [at(ANCHOR_Y, ANCHOR_R, geo_angle(15.0 * j)) for j in range(24)]
    for j in range(24):
        cubes.append(K.beam(ring[j], ring[(j + 1) % 24], 2.0, 2.0, CANAL, side=(0, 1, 0), key=f"ring{j}",
                            overshoot=0.8))
    for t in range(1, TENTACLES + 1):
        root, th = tentacle_root(t)
        pts = [at(y, r, th) for y, r in SPOKE]
        for i in range(len(pts) - 1):
            cubes.append(K.beam(pts[i], pts[i + 1], 1.8, 1.8, CANAL, side=tangent(th), key=f"spoke{t}_{i}",
                                overshoot=0.6))
        cubes.append(K.slab(root, (5.5, 7.0, 5.5), rotate_y(radial(th), 45.0), (0, 1, 0), BULB, key=f"bulb{t}"))
    return cubes


def heart():
    """心臓。緯度 5 帯 × 経度 10 の球（極の帯は三角を長方形で覆う）"""
    c0 = (0.0, CORE_Y * U, 0.0)
    rr = HEART_R * U
    lats = (90.0, 54.0, 18.0, -18.0, -54.0, -90.0)
    n = 10

    def sp(lat, lon):
        a, b = math.radians(lat), math.radians(lon)
        return (rr * math.cos(a) * math.cos(b), c0[1] + rr * math.sin(a), rr * math.cos(a) * math.sin(b))

    cubes = []
    for i in range(len(lats) - 1):
        for j in range(n):
            l0, l1 = 360.0 * j / n, 360.0 * (j + 1) / n
            a, b, c, d = sp(lats[i], l0), sp(lats[i], l1), sp(lats[i + 1], l0), sp(lats[i + 1], l1)
            mid = vmul(vadd(vadd(a, b), vadd(c, d)), 0.25)
            cubes.append(panel(a, b, c, d, vsub(mid, c0), 1.6, HEART, key=f"ht{i}_{j}", lap_w=1.15, lap_h=1.05)[0])
    return cubes


def bell(bones):
    K.bone(bones, "bell", (0.0, 0.0, 0.0), stalk() + canals())
    K.bone(bones, "heart", (0.0, CORE_Y * U, 0.0), [], parent="bell")
    K.bone(bones, "heart_core", (0.0, CORE_Y * U, 0.0), heart(), parent="heart")
    for k in range(1, PETALS + 1):
        tc = petal_theta(k)
        petal = shell(BELL, range(0, SKIRT_OUTER), True, k, "o") + shell(UNDER, range(0, SKIRT_UNDER), False, k, "u")
        K.bone(bones, f"bell_petal_{k}", (0.0, BELL[0][0] * U, 0.0), petal, parent="bell")
        skirt = (shell(BELL, range(SKIRT_OUTER, len(BELL) - 1), True, k, "o")
                 + shell(UNDER, range(SKIRT_UNDER, len(UNDER) - 1), False, k, "u"))
        K.bone(bones, f"bell_skirt_{k}", at(*BELL[SKIRT_OUTER], tc), skirt, parent=f"bell_petal_{k}")
        K.bone(bones, f"bell_frill_{k}", at(*BELL[-1], tc), frill(k), parent=f"bell_skirt_{k}")


# ============================================================ 触手と口腕（root 直下の体節）
def tent_radius(k):
    """関節座標 k（0 = 付け根）での触手の半径（単位）"""
    return (TENT_R0 + (TENT_R1 - TENT_R0) * k / TSEGS) * U


def tentacles(bones):
    for t in range(1, TENTACLES + 1):
        root, th = tentacle_root(t)
        rho = radial(th)
        x, z = root[0], root[2]
        for ss in range(1, TSEGS + 1):
            yf = root[1] - TSEG * (ss - 1)             # 付け根側の関節
            yb = yf - TSEG                             # 先端側の関節
            s = TENT_SIDE * tent_radius(ss - 0.5)
            # 前の体節の袖の下へ差し込み、後ろは関節を越えて次の体節へ被さる（細る先が下に入る）
            p0, p1 = (x, yf + 0.3 * s, z), (x, yb - 0.55 * s, z)
            cubes = [K.beam(p0, p1, s, s, STRAND, side=rho, key=f"t{t}_{ss}a"),
                     K.beam(p0, p1, s, s, STRAND, side=rotate_y(rho, 45.0), key=f"t{t}_{ss}b")]
            if ss <= SLEEVE_SEGS:
                # 袖（刺胞の数珠）: 先端側の関節に被せる。曲げたとき継ぎ目の開きを隠す
                cubes.append(K.slab((x, yb, z), (1.45 * s, 1.1 * s, 1.45 * s), rotate_y(rho, 22.5), (0, 1, 0),
                                    SLEEVE, key=f"t{t}_{ss}s"))
            K.bone(bones, f"tent_{t}_{ss:02d}", (x, (yf + yb) / 2.0, z), cubes)


def oral_arms(bones):
    for o in range(1, ORAL_ARMS + 1):
        root, th = oral_root(o)
        rho = radial(th)
        x, z = root[0], root[2]
        for ss in range(1, ORAL_SEGS + 1):
            yf = root[1] - OSEG * (ss - 1)
            yb = yf - OSEG
            ym = (yf + yb) / 2.0
            f = (ss - 0.5) / ORAL_SEGS
            rc = 0.75 * (1.0 - 0.6 * f) * U            # 芯の半径（旧描画の口腕の tube）
            cubes = [K.beam((x, yf + 0.5 * rc, z), (x, yb - 0.6 * rc, z), 2 * rc, 2 * rc, ORAL,
                            side=rotate_y(rho, 45.0), key=f"o{o}_{ss}c")]
            wf = (3.8 * (1.0 - 0.55 * f) + 0.4) * U   # フリルの幅（旧描画の ribbon）
            for side in (1, -1):
                ruffle = ORAL_RUFFLE * (1 if (ss + (side > 0)) % 2 else -1)
                h = rotate_y(rho, side * ORAL_V + ruffle)          # フリルの自由縁の向き（水平）
                nrm = vunit(vcross(h, (0.0, -1.0, 0.0)))
                c = (x + h[0] * (wf / 2.0 + 0.4 * rc), ym, z + h[2] * (wf / 2.0 + 0.4 * rc))
                cubes.append(K.slab(c, (0.0, wf, OSEG + 3.0), nrm, h, ORAL_FRILL, key=f"o{o}_{ss}f{side}"))
            K.bone(bones, f"oral_{o}_{ss:02d}", (x, ym, z), cubes)


# ============================================================ アニメーション
def axis_euler(axis, deg):
    """geo 空間の軸 axis まわりに deg 回す姿勢を、geo / animation の (rx, ry, rz) に直す"""
    a = vunit(axis)
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)

    def r(v):
        return vadd(vadd(vmul(v, c), vmul(vcross(a, v), s)), vmul(a, vdot(a, v) * (1.0 - c)))

    return euler_from_axes(r((1.0, 0.0, 0.0)), r((0.0, 1.0, 0.0)), r((0.0, 0.0, 1.0)))


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3.0 - 2.0 * x)


def pulse(x):
    """拍動の縮み 0..1。周期の 0.24 で素早く縮み切り、残りでゆっくり戻る"""
    x %= 1.0
    if x < 0.24:
        return math.sin(0.5 * math.pi * x / 0.24)
    return 0.5 * (1.0 + math.cos(math.pi * (x - 0.24) / 0.76))


def animations(bones):
    names = {b["name"] for b in bones}
    steps = [round(0.1 * i, 2) for i in range(int(round(IDLE / 0.1)) + 1)]
    idle = {}
    for k in range(1, PETALS + 1):
        ax = tangent(petal_theta(k))
        # 裾: 拍動で内へ縮む（負の角で内）。全周いっしょ
        idle[f"bell_skirt_{k}"] = rot([(t, axis_euler(ax, -SKIRT_DEG * pulse(t / IDLE))) for t in steps])
        # フリル: 縁を 2 周する波。裾が縮むと少し外へ反る
        ph = 2.0 * math.tau * (k - 1) / PETALS
        idle[f"bell_frill_{k}"] = rot([(t, axis_euler(ax, FRILL_DEG * math.sin(math.tau * t / IDLE - ph)
                                                        + 0.5 * SKIRT_DEG * pulse(t / IDLE - 0.08)))
                                       for t in steps])
    # 心臓: 拍動の頭で二度打つ
    idle["heart_core"] = scale([(0.0, (1.0, 1.0, 1.0)), (0.12, (1.09, 1.09, 1.09)), (0.28, (0.99, 0.99, 0.99)),
                                (0.42, (1.06, 1.06, 1.06)), (0.65, (1.0, 1.0, 1.0)), (IDLE, (1.0, 1.0, 1.0))])

    bsteps = [round(0.25 * i, 2) for i in range(int(round(BLOOM / 0.25)) + 1)]
    bloom = {}
    for k in range(1, PETALS + 1):
        ax = tangent(petal_theta(k))
        bloom[f"bell_petal_{k}"] = rot([(t, axis_euler(ax, BLOOM_DEG * smooth(t / BLOOM))) for t in bsteps])
    bloom["heart"] = scale([(t, (1.0 + (HEART_BLOOM - 1.0) * smooth(t / BLOOM),) * 3) for t in bsteps])
    for a in (idle, bloom):
        for n in a:
            assert n in names, n
    return _no_negative_zero({
        "idle": {"loop": True, "animation_length": IDLE, "bones": idle},
        "bloom": {"loop": "hold_on_last_frame", "animation_length": BLOOM, "bones": bloom},
    })


def _no_negative_zero(d):
    """丸めが出す -0.0 を 0.0 に揃える（読み込みには影響しないが、差分が読みにくい）"""
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
    bell(bones)
    tentacles(bones)
    oral_arms(bones)
    # visible_bounds はブロック単位（MODEL_SCALE を掛ける前）。GeckoLib は描画の間引きに使わない
    width = 2.0 * max(r for _, r in BELL) * U / 16.0 + 2.0
    height = (BELL[0][0] * U - ANCHOR_Y * U + TSEGS * TSEG) / 16.0
    return K.model(f"geometry.{NAME}", bones, round(width, 1), round(height, 1), round(-height / 2.0 + 2.0, 1)), bones


def _r(v, digits=2):
    return [round(c, digits) + 0.0 for c in v]


def contract(bones, cubes, per, anims):
    by = {b["name"]: b for b in bones}

    def entry(b, role):
        return {"name": b["name"], "parent": b.get("parent"), "pivot": b["pivot"], "role": role}

    named = [entry(by["bell"], "傘の本体。pivot = AbyssMother.headPos()（傘の中心）。口柄・放射管・環状管・触手の付け根の球（不透明）"),
             entry(by["heart"], "開花で 1.35 倍（bloom の scale）。中身は無い"),
             entry(by["heart_core"], "心臓の球（不透明・フルブライト・全面が発光）。idle の scale で二度打つ")]
    for k in range(1, PETALS + 1):
        named.append(entry(by[f"bell_petal_{k}"], f"花弁 {k}（2 列）。pivot は頂点。外面の区間 0-3 と下傘の区間 0-2（半透明）。"
                                                   f"中心は tent_{k} の付け根の真上"))
        named.append(entry(by[f"bell_skirt_{k}"], "裾。pivot は肩（外面の (0.5, 17)）。外面の区間 4-6 と下傘の縁の区間（半透明）"))
        named.append(entry(by[f"bell_frill_{k}"], "縁のフリル 2 枚。pivot は縁（半透明）"))
    t_roots = []
    for t in range(1, TENTACLES + 1):
        root, th = tentacle_root(t)
        a = (t - 1) * 360.0 / TENTACLES
        t_roots.append({"T": t, "java_index": t - 1, "render_angle_deg": a,
                        "root_geo_units": _r(root),
                        "rest_outward_render": _r((math.cos(math.radians(a)), 0.0, math.sin(math.radians(a))), 4)})
    o_roots = []
    for o in range(1, ORAL_ARMS + 1):
        root, th = oral_root(o)
        a = (o - 1) * 360.0 / ORAL_ARMS + ORAL_PHASE
        o_roots.append({"O": o, "render_angle_deg": round(a, 3), "root_geo_units": _r(root),
                        "rest_outward_render": _r((math.cos(math.radians(a)), 0.0, math.sin(math.radians(a))), 4)})
    return {
        "entity": NAME,
        "generator": "tools/boss/mother_model/build.py（手で直さず、生成元を直して流し直す）",
        "geo": f"assets/abyssia/geo/entity/{NAME}.geo.json",
        "animation": f"assets/abyssia/animations/entity/{NAME}.animation.json",
        "texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo.png",
        "glow_texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png",
        "glow_note": "光らない画素は (0,0,0,0)。RenderType.eyes（加算）でも GeckoLib の自動発光層でも使える",
        "texture_size": [atlas.SIZE, atlas.SIZE],
        "model_scale": MODEL_SCALE,
        "units": "geo の 1 単位 = 1/16 ブロック。描画で MODEL_SCALE 倍するので、ゲーム内では MODEL_SCALE/16 ブロック（4 単位 = 1 ブロック）",
        "bell": {
            "bone": "bell",
            "pivot": [0.0, 0.0, 0.0],
            "pivot_is": "AbyssMother.headPos()（傘の中心。旧描画の bell space の原点）",
            "rest_extent_units": {"apex_y": BELL[0][0] * U, "rim_y": BELL[-1][0] * U, "rim_radius": BELL[-1][1] * U,
                                  "max_radius": max(r for _, r in BELL) * U, "heart_centre_y": CORE_Y * U,
                                  "heart_radius": HEART_R * U},
            "columns": COLUMNS, "petals": PETALS,
            "note": ("傘の向きは AbyssMother.anchor() と同じ軸に揃えてある（anchor は yaw で回らない）。"
                     "bell の回転は 0 のまま置くか、回すなら触手の付け根の位置（anchor）も同じだけ回っている前提で描く"),
        },
        "tentacles": {
            "count": TENTACLES, "joints": JOINTS, "segments_per_tentacle": TSEGS,
            "bone": "tent_T_SS。T = 1..12 は Java の tentacle(T-1)、SS = 01..27 は関節 SS-1（付け根側）〜関節 SS（先端側）",
            "segment_length_units": TSEG, "segment_length_blocks": SPACING,
            "root": "関節 0 = AbyssMother.anchor(T-1) - headPos() = 描画空間で (15 cos a, -3, 15 sin a) ブロック、a = (T-1) * 30 度",
            "rest_pose": "付け根からまっすぐ下（-Y）。関節 j は geo の (root.x, root.y - segment_length_units * j, root.z)",
            "segment_pivot": "tent_T_SS の pivot = (root.x, root.y - segment_length_units * (SS - 0.5), root.z)（体節の中心）",
            "radius_blocks": [TENT_R0, TENT_R1],
            "section": "一辺 1.6r の四角い棒 2 本を 45 度ずらして重ねた星形（八角に見える）",
            "sleeve": (f"SS = 01..{SLEEVE_SEGS:02d} は先端側の関節に一回り太い数珠（袖）を被せる。それより先は、"
                       "太い前の体節が関節を越えて次の体節の差し込みに被さる（細る先が下に入る）"),
            "roots": t_roots,
        },
        "oral_arms": {
            "count": ORAL_ARMS, "segments_per_arm": ORAL_SEGS,
            "bone": "oral_O_SS。O = 1..5 は旧描画の arm (O-1)、SS = 01..09 は点 SS-1〜SS",
            "segment_length_units": OSEG, "segment_length_blocks": ORAL_SEG_BLOCKS,
            "root": "描画空間で (2.2 cos a, -6, 2.2 sin a) ブロック、a = (O-1) * 72 度 + 0.3 rad（旧描画と同じ）",
            "rest_pose": "付け根からまっすぐ下（-Y）。点 j は geo の (root.x, root.y - segment_length_units * j, root.z)",
            "segment_pivot": "oral_O_SS の pivot = (root.x, root.y - segment_length_units * (SS - 0.5), root.z)",
            "frills": "芯の両側に厚み 0 のフリル 2 枚。外向き（rest_outward）から左右へ 40 度、体節ごとに ±14 度ずつ互い違い（襞）",
            "points_note": ("Java が作る口腕の点は segment_length_blocks（3.375）おきに置く。間隔が違うと継ぎ目が"
                            "開く（または潜る）。旧描画の点（30 ブロックを 9 等分）とほぼ同じ長さ"),
            "roots": o_roots,
        },
        "segment_axis": ("tent_* と oral_* の体節は局所 +Y が付け根側（関節 SS-1）を向く。静止姿勢では真下に垂れ、"
                         "+Y は真上。回転 0 が静止姿勢"),
        "java_orientation": [
            "描画空間（X 反転後）で、d = unit(J[SS-1] - J[SS])（先端側の関節から付け根側へ）、"
            "o = その触手・口腕の rest_outward（水平の単位ベクトル、roots の表）",
            "p は付け根から順に運ぶ: p = o から始め、体節ごとに p = unit(p - d (p·d))（前の体節の p を今の d に"
            "直交化。ねじれも急な裏返りも出ない）",
            "回転行列 R = [p, d, p×d] · [o, (0,1,0), o×(0,1,0)]^T（どちらも列に並べた正規直交基底。"
            "静止姿勢の向きを今の向きへ運ぶ）",
            "setRotX(atan2(R[2][1], R[2][2]))、setRotY(-asin(R[2][0]))、setRotZ(atan2(R[1][0], R[0][0]))"
            "（R = Rz・Ry・Rx の分解。geckolib_conventions と同じ回転の約束）",
            "位置: T = 体節の中点 (J[SS-1] + J[SS]) / 2 を、傘の中心からの描画空間の geo 単位（ブロック × 16 / "
            "MODEL_SCALE = × 4）で。setPosX(-T.x - pivot.x)、setPosY(T.y - pivot.y)、setPosZ(T.z - pivot.z)。"
            "pivot は segment_pivot（geo）",
            "検算: tools/boss/mother_model/preview.py の bent はこの式（同じ写し）で骨を置いて描いている",
        ],
        "translucent_bones": {
            "bones": "bell_petal_1..12、bell_skirt_1..12、bell_frill_1..12（とその箱だけ）",
            "alpha": "画素の alpha で持つ。傘 158〜220（縁と放射管が濃い）、下傘 140〜230（生殖腺が濃い）、フリル 190〜230、台形の外と厚みの面は 0",
            "render_type": ("1 パスで描くなら RenderType.entityTranslucent（不透明の骨の画素は alpha 255 か 0）。"
                            "傘を cull 付き（entityTranslucentCull）で描くと板の表裏が重ならず、旧描画（alpha 0.62 の1枚）に"
                            "近い濃さになる。no-cull だと外の面と内の面（厚み 0.4 単位）が二重になって濃く見える"),
        },
        "opaque_bones": "bell（口柄・管・付け根の球）、heart_core、tent_*、oral_*（alpha は 255 か切り抜きの 0）",
        "fullbright_bones": ["heart_core"],
        "glow_parts": "傘のかすかな発光・放射管・縁の灯り（氷色）・下傘の生殖腺・触手の帯・数珠・付け根の球・管・フリルの斑点・心臓の全面",
        "animations": {name: {"loop": a["loop"], "length": a["animation_length"],
                              "bones": sorted(a["bones"])} for name, a in anims.items()},
        "animation_notes": [
            "idle と bloom は別のコントローラで同時に流す。触る骨が分かれている（idle = bell_skirt_* / bell_frill_* / "
            "heart_core、bloom = bell_petal_* / heart）",
            f"idle の長さ {IDLE:g} 秒 = AbyssMother.beat の周期（50 tick）。0 秒が縮み始め（beat の山の少し前）",
            f"bloom は hold_on_last_frame。AbyssMother.bloom() が真になったら流し、{BLOOM:g} 秒で花弁 {BLOOM_DEG:g} 度・"
            f"心臓 {HEART_BLOOM:.2f} 倍まで開いて止まる（第 3 段階は戻らない。読み込み直後も bloom() なら流す）",
        ],
        "animation_signs": {
            "bell_petal_k": "花弁の接線（radial × up）まわりの回転。正で外へ倒れて開く。キーは軸まわりの回転を (rx, ry, rz) に解いた値",
            "bell_skirt_k": "同じ軸。負で内へ縮む（拍動）",
            "bell_frill_k": "同じ軸。正で外へ反る",
            "heart / heart_core": "scale だけ",
        },
        "bone_rule": ("bell・tent_T_SS・oral_O_SS は root 直下。毎フレーム位置と回転を上書きする。tent / oral の体節は"
                      "関節 SS-1 と SS の中点へ、局所 +Y が関節 SS から SS-1 を向くように置く（java_orientation）。"
                      "bell は headPos() へ。子（bell_petal_*, bell_skirt_*, bell_frill_*, heart, heart_core）は animation に任せる"),
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
            "子の Bone（bell_petal_*, bell_skirt_*, bell_frill_*, heart, heart_core）は animation が回すので、"
            "Java からは触らない（触るなら animation の値を上書きしてしまう）",
        ],
        "geckolib_conventions_note": ("上の 5 項は大海蛇（serpent_model/contract.json）と同じ約束。4 項目の -Z の式は大海蛇の体節用で、"
                                      "母の体節は +Y が付け根側なので java_orientation の式を使う"),
        "render_notes": [
            "旧描画の心臓の光輪（billboard）と、発光パルスの予告の縁の光の粒はモデルに入れていない（Java 側に残す）",
            "触手・口腕の骨は root 直下なので bell を動かしても付いて来ない。毎フレーム全部を置く",
            "第 3 段階の開花では、花弁が頂点を軸に外へ倒れ、放射管と環状管（bell の箱）が傘の骨のように残る。"
            "触手は環状管の付け根の球から垂れるので、付け根（anchor）が動かなくても宙に浮かない",
        ],
        "cube_count": cubes,
        "cube_breakdown": per,
        "bones": named,
    }


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    geo, bones = build_geo()
    anims = animations(bones)
    print(boss_kit.verify(NAME, geo, anims))
    cubes = sum(len(b["cubes"]) for b in bones)
    assert cubes <= 1500, f"cube が多すぎる: {cubes}"
    for b in bones:
        if b["name"].startswith(("tent_", "oral_")) or b["name"] == "bell":
            assert not b.get("parent"), b["name"]
    per = {"bell_translucent": 0, "bell_organs": 0, "tentacles": 0, "oral_arms": 0}
    for b in bones:
        n = b["name"]
        key = ("tentacles" if n.startswith("tent_") else "oral_arms" if n.startswith("oral_")
               else "bell_translucent" if n.startswith("bell_") else "bell_organs")
        per[key] += len(b["cubes"])
    for d in (GEO, ANIM, TEX):
        d.mkdir(parents=True, exist_ok=True)
    (GEO / f"{NAME}.geo.json").write_text(json.dumps(geo, indent=1), encoding='utf-8')
    (ANIM / f"{NAME}.animation.json").write_text(
        json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1), encoding='utf-8')
    atlas.write_png(TEX / f"{NAME}_geo.png", atlas.build())
    atlas.write_png(TEX / f"{NAME}_geo_glow.png", atlas.build_glow())
    (HERE / 'contract.json').write_text(json.dumps(contract(bones, cubes, per, anims), indent=1, ensure_ascii=False),
                                        encoding='utf-8')
    print(f"cubes: 計 {cubes}（傘 {per['bell_translucent']} / 器官 {per['bell_organs']} / 触手 {per['tentacles']} / "
          f"口腕 {per['oral_arms']}）、bones {len(bones)}、MODEL_SCALE {MODEL_SCALE:g}、触手の体節 {TSEG:g} 単位 × {TSEGS}、"
          f"口腕の体節 {OSEG:g} 単位 × {ORAL_SEGS}")
    print("次に流すこと:")
    print("  python tools/boss/check_animations.py abyss_mother")
    print("  python tools/boss/mother_model/preview.py")


if __name__ == '__main__':
    main()
