# -*- coding: utf-8 -*-
"""深淵の母の六面図（実テクスチャ貼り）を out/ に書き出す。

```bash
python tools/boss/mother_model/preview.py              # 全部
python tools/boss/mother_model/preview.py bell bloom   # 一部だけ（whole / bell / organs / bottom / beat / bloom / bent）
```

| 出力 | 中身 |
| --- | --- |
| `out/mother_views.png` | 静止姿勢（触手と口腕がまっすぐ下へ垂れる）の全身の六面図 |
| `out/mother_bell_views.png` | 傘・口腕・触手の付け根（tent の SS 01〜03）の六面図を拡大 |
| `out/mother_organs.png` | 傘の板（花弁・裾・フリル）を外した中身（口柄・心臓・管・口腕）。LEFT / TOP / BOTTOM |
| `out/mother_bottom.png` | 下から（BOTTOM）と正面（FRONT）を大きく |
| `out/mother_beat.png` | idle の拍動で裾が縮み切った姿（フリルの波も同じ時刻）。LEFT / BOTTOM |
| `out/mother_bloom_views.png` | bloom の最後の姿（花弁 38 度・心臓 1.35 倍）。LEFT / FRONT / TOP / BOTTOM |
| `out/mother_bent_views.png` | 触手を流して曲げ、口腕を揺らした全身（TOP / LEFT / FRONT） |
| `out/mother_bent_near.png` | 曲げた触手の付け根あたりの拡大（LEFT / TOP）。数珠と継ぎ目を見る |

図は半透明を描かない（alpha 32 以上の画素は不透明に塗る）。傘の中は `organs` で見る。

曲げた図は contract.json の java_orientation と**同じ式**（描画空間で R = [p, d, p×d]·[o, Y, o×Y]^T、
setRot = (atan2(R21, R22), -asin(R20), atan2(R10, R00))、setPos = (-T.x - pivot.x, T.y - pivot.y,
T.z - pivot.z)）で骨を置き、geo の回転 (-rx, -ry, +rz) に直して描く。Java の写しの検算を兼ねる。
"""
import copy
import json
import math
import pathlib
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import atlas
import boss_kit
import boss_views
import build
from build import GEO, NAME, U, PETALS, TENTACLES, TSEGS, ORAL_ARMS, ORAL_SEGS, SPACING, ORAL_SEG_BLOCKS
from boss_kit import _add as vadd, _sub as vsub, _mul as vmul, _unit as vunit, _cross as vcross, _dot as vdot

OUT = HERE / 'out'
# 姿勢を付けた geo の置き場。描き終えたら消える（リポジトリに残さない）
_TMP = tempfile.TemporaryDirectory(prefix='mother_posed_')
POSED = pathlib.Path(_TMP.name)


def views(*names):
    return tuple(v for v in boss_views.VIEWS if v[0] in names)


def load():
    return json.loads((GEO / f"{NAME}.geo.json").read_text(encoding='utf-8'))


def bones_of(geo):
    return geo['minecraft:geometry'][0]['bones']


def root_of(by_name, name):
    while by_name[name].get('parent'):
        name = by_name[name]['parent']
    return name


def keep(geo, roots=None, drop=()):
    """roots（root 直下の Bone 名の集合、または名前を受けて真偽を返す関数）の子孫だけを描き、
    drop で始まる Bone とその子孫は描かない"""
    by_name = {b['name']: b for b in bones_of(geo)}

    def dropped(n):
        while n:
            if n.startswith(drop) if drop else False:
                return True
            n = by_name[n].get('parent')
        return False

    ok = set()
    for n in by_name:
        r = root_of(by_name, n)
        if roots is not None and not (roots(r) if callable(roots) else r in roots):
            continue
        if not dropped(n):
            ok.add(n)
    return lambda n: n in ok


def near_roots(tent_max=3):
    def f(r):
        if r == 'bell' or r.startswith('oral_'):
            return True
        return r.startswith('tent_') and int(r.rsplit('_', 1)[1]) <= tent_max
    return f


def write_posed(geo, tag):
    name = f"{NAME}_{tag}"
    (POSED / f"{name}.geo.json").write_text(json.dumps(geo), encoding='utf-8')
    return name


def scale_cubes(bone, s, centre):
    """Bone の箱を centre まわりに一様に s 倍する（boss_views は scale を描かないので箱を直に縮める）"""
    for c in bone['cubes']:
        c['origin'] = [centre[i] + s * (c['origin'][i] - centre[i]) for i in range(3)]
        c['size'] = [s * v for v in c['size']]
        if 'pivot' in c:
            c['pivot'] = [centre[i] + s * (c['pivot'][i] - centre[i]) for i in range(3)]


def shift(by_name, root, delta):
    """root とその子孫を平行移動する（pivot・箱の origin・箱の pivot）"""
    for b in by_name.values():
        if root_of(by_name, b['name']) != root:
            continue
        b['pivot'] = [b['pivot'][i] + delta[i] for i in range(3)]
        for c in b['cubes']:
            c['origin'] = [c['origin'][i] + delta[i] for i in range(3)]
            if 'pivot' in c:
                c['pivot'] = [c['pivot'][i] + delta[i] for i in range(3)]


# ============================================================ アニメーションの1時刻を当てる
def anim_pose(geo, anim, t):
    """animation.json の anim を時刻 t で線形補間して geo へ当てる（rotation と scale）"""
    data = json.loads((build.ANIM / f"{NAME}.animation.json").read_text(encoding='utf-8'))
    tracks = data['animations'][anim]['bones']
    geo = copy.deepcopy(geo)
    by_name = {b['name']: b for b in bones_of(geo)}
    for name, tr in tracks.items():
        for kind, keys in tr.items():
            times = sorted(keys, key=float)
            val = None
            for a, b in zip(times, times[1:]):
                if float(a) <= t <= float(b):
                    va = keys[a]['vector'] if isinstance(keys[a], dict) else keys[a]
                    vb = keys[b]['vector'] if isinstance(keys[b], dict) else keys[b]
                    f = (t - float(a)) / max(1e-9, float(b) - float(a))
                    val = [va[i] + (vb[i] - va[i]) * f for i in range(3)]
                    break
            if val is None:
                last = keys[times[-1]]
                val = last['vector'] if isinstance(last, dict) else last
            if kind == 'rotation':
                by_name[name]['rotation'] = [round(v, 3) for v in val]
            elif kind == 'scale':
                # scale は Bone の子孫の箱すべてへ効く（ここでは心臓だけなので、子孫を pivot まわりに縮める）
                p = by_name[name]['pivot']
                for b in by_name.values():
                    n = b['name']
                    while n and n != name:
                        n = by_name[n].get('parent')
                    if n == name:
                        scale_cubes(b, val[0], p)
    return geo


# ============================================================ Java の置き方の写し（contract.json の java_orientation）
def java_frames(joints, outward):
    """joints: 描画空間の関節（ブロック、傘の中心から）。体節ごとの (T（geo 単位の描画空間の点）, (rx, ry, rz) ラジアン)"""
    out = []
    p = outward
    o, y = outward, (0.0, 1.0, 0.0)
    f0 = (o, y, vcross(o, y))
    for ss in range(1, len(joints)):
        d = vunit(vsub(joints[ss - 1], joints[ss]))
        p = vunit(vsub(p, vmul(d, vdot(p, d))))
        f1 = (p, d, vcross(p, d))
        r = [[sum(f1[k][i] * f0[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
        rx = math.atan2(r[2][1], r[2][2])
        ry = -math.asin(max(-1.0, min(1.0, r[2][0])))
        rz = math.atan2(r[1][0], r[0][0])
        mid = vmul(vadd(joints[ss - 1], joints[ss]), 0.5 * U)
        out.append((mid, (rx, ry, rz)))
    return out


def place_java(by_name, name, target, rr):
    """setPos / setRot を、静止姿勢の geo へ当てた形に直して書き込む"""
    pv = by_name[name]['pivot']
    pos = (-target[0] - pv[0], target[1] - pv[1], target[2] - pv[2])   # GeoBone.setPosX/Y/Z
    shift(by_name, name, pos)                                          # geo では pos がそのまま pivot のずれ
    by_name[name]['rotation'] = [round(-math.degrees(rr[0]), 3), round(-math.degrees(rr[1]), 3),
                                 round(math.degrees(rr[2]), 3)]


def tentacle_path(t):
    """描画空間の関節。傘の後ろ（+Z）へ流れ、先ほど水平に寝て、左右に揺れる。奇数の触手は強く巻く"""
    a = math.radians((t - 1) * 360.0 / TENTACLES)
    out = (math.cos(a), 0.0, math.sin(a))
    pts = [(15.0 * out[0], -3.0, 15.0 * out[2])]
    amp, freq = (0.75, 0.62) if t % 2 else (0.3, 0.35)
    for j in range(1, TSEGS + 1):
        w = min(0.85, 0.10 + 0.035 * j)
        sway = amp * math.sin(freq * j + 1.1 * t)
        spread = 0.25 * max(0.0, 1.0 - j / 8.0)
        d = vunit((sway + out[0] * spread, -(1.0 - w), w + out[2] * spread))
        pts.append(vadd(pts[-1], vmul(d, SPACING)))
    return pts, out


def oral_path(o):
    a = math.radians((o - 1) * 360.0 / ORAL_ARMS + math.degrees(0.3))
    out = (math.cos(a), 0.0, math.sin(a))
    side = (-out[2], 0.0, out[0])
    pts = [(2.2 * out[0], -6.0, 2.2 * out[2])]
    for j in range(1, ORAL_SEGS + 1):
        wave = 0.45 * math.sin(0.9 * j + 1.7 * o)
        d = vunit(vadd(vadd((0.0, -1.0, 0.0), vmul(out, 0.14)), vmul(side, wave)))
        pts.append(vadd(pts[-1], vmul(d, ORAL_SEG_BLOCKS)))
    return pts, out


def bend(geo):
    geo = copy.deepcopy(geo)
    by_name = {b['name']: b for b in bones_of(geo)}
    worst = 0.0
    for t in range(1, TENTACLES + 1):
        pts, out = tentacle_path(t)
        for j in range(1, len(pts) - 1):
            a = vunit(vsub(pts[j], pts[j - 1]))
            b = vunit(vsub(pts[j + 1], pts[j]))
            worst = max(worst, math.degrees(math.acos(max(-1.0, min(1.0, vdot(a, b))))))
        for ss, (mid, rr) in enumerate(java_frames(pts, out), start=1):
            place_java(by_name, f"tent_{t}_{ss:02d}", mid, rr)
    for o in range(1, ORAL_ARMS + 1):
        pts, out = oral_path(o)
        for ss, (mid, rr) in enumerate(java_frames(pts, out), start=1):
            place_java(by_name, f"oral_{o}_{ss:02d}", mid, rr)
    print(f"曲げた図: 触手の関節の最大の折れ角 {worst:.1f} 度")
    return geo


def main(argv):
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    want = set(argv) or {'whole', 'bell', 'organs', 'bottom', 'beat', 'bloom', 'bent'}
    OUT.mkdir(parents=True, exist_ok=True)
    geo = load()
    shell = ('bell_petal_', 'bell_skirt_', 'bell_frill_')
    if 'whole' in want:
        boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'mother_views.png', scale=0.9)
    if 'bell' in want:
        boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'mother_bell_views.png', scale=2.4,
                                bones=keep(geo, near_roots(3)))
    if 'organs' in want:
        boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'mother_organs.png', scale=2.4,
                                views=views('LEFT', 'TOP', 'BOTTOM'), bones=keep(geo, near_roots(3), shell))
    if 'bottom' in want:
        boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'mother_bottom.png', scale=3.2,
                                views=views('BOTTOM', 'FRONT'), bones=keep(geo, near_roots(2)))
    if 'beat' in want:
        g = anim_pose(geo, 'idle', 0.6)          # 裾が縮み切ったころ
        name = write_posed(g, 'beat')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'mother_beat.png', scale=2.4,
                                views=views('LEFT', 'BOTTOM'), geo_dir=POSED, bones=keep(g, near_roots(2)))
    if 'bloom' in want:
        g = anim_pose(geo, 'bloom', build.BLOOM)
        name = write_posed(g, 'bloom')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'mother_bloom_views.png', scale=2.2,
                                views=views('LEFT', 'FRONT', 'TOP', 'BOTTOM'), geo_dir=POSED,
                                bones=keep(g, near_roots(3)))
    if 'bent' in want:
        g = bend(geo)
        name = write_posed(g, 'bent')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'mother_bent_views.png', scale=0.9,
                                views=views('TOP', 'LEFT', 'FRONT'), geo_dir=POSED)
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'mother_bent_near.png', scale=2.6,
                                views=views('LEFT', 'TOP'), geo_dir=POSED, bones=keep(g, near_roots(7)))


if __name__ == '__main__':
    main(sys.argv[1:])
