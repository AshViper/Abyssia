# -*- coding: utf-8 -*-
"""巨大ミツクリザメの六面図（仮テクスチャ貼り）を out/ に書き出す。

```bash
python tools/boss/goblin_shark_model/preview.py              # 全部
python tools/boss/goblin_shark_model/preview.py head jaw     # 一部だけ（whole / head / jaw / bent / oblique）
```

| 出力 | 中身 |
| --- | --- |
| `out/goblin_views.png` | 静止姿勢（一直線）の全身の六面図 |
| `out/goblin_head_views.png` | 頭〜seg_04（顎を収めた状態）の六面図を拡大 |
| `out/goblin_jaw_out.png` | 顎を 6 ブロック突き出し、下顎を 50 度開いた頭（LEFT / FRONT / BOTTOM / TOP） |
| `out/goblin_bent_views.png` | Java の描画と同じやり方で S 字に曲げた全身（TOP / LEFT） |
| `out/goblin_bent_neck.png` | 関節 1〜3 を折った頭〜seg_06（TOP / BOTTOM）。継ぎ目の球関節を見る |
| `out/goblin_oblique.png` | 斜め前から見た全身（吻を手前へ 40 度ヨー・15 度ピッチ）。まっすぐ / 顎を突き出す |

曲げた図は、各 root Bone を「関節の中点へ運び、局所 -Z を前の関節へ向ける」ことで作る
（contract.json の bone_rule と同じ）。顎を突き出した図は jaw の子孫を JAW_D だけ動かし、
jaw_sling はゲームでは scale z で伸びるところを、伸ばした長さで作り直して描く。
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
from build import GEO, NAME, SEG, JOINTS, U

OUT = HERE / 'out'
_TMP = tempfile.TemporaryDirectory(prefix='goblin_posed_')
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


def descends(by_name, name, ancestor):
    while name:
        if name == ancestor:
            return True
        name = by_name[name].get('parent')
    return False


def under(geo, roots):
    by_name = {b['name']: b for b in bones_of(geo)}
    keep = {n for n in by_name if root_of(by_name, n) in roots}
    return lambda n: n in keep


def write_posed(geo, tag):
    name = f"{NAME}_{tag}"
    (POSED / f"{name}.geo.json").write_text(json.dumps(geo), encoding='utf-8')
    return name


def shift_bones(bones, delta):
    for b in bones:
        b['pivot'] = [b['pivot'][i] + delta[i] for i in range(3)]
        for c in b['cubes']:
            c['origin'] = [c['origin'][i] + delta[i] for i in range(3)]
            if 'pivot' in c:
                c['pivot'] = [c['pivot'][i] + delta[i] for i in range(3)]


def place(by_name, root, target, d):
    rest = by_name[root]['pivot']
    shift_bones([b for b in by_name.values() if root_of(by_name, b['name']) == root],
                [target[i] - rest[i] for i in range(3)])
    w = boss_kit._mul(d, -1.0)
    v = boss_kit._unit(boss_kit._sub((0.0, 1.0, 0.0), boss_kit._mul(w, w[1])))
    u = boss_kit._cross(v, w)
    by_name[root]['rotation'] = [round(a, 3) for a in boss_kit.euler_from_axes(u, v, w)]


def bent_path(amp=0.5, freq=0.4, pitch=0.05, phase=0.2, yaw0=0.0, pitch0=0.0, kink=None):
    """S 字に曲がった関節の並び。kink=(関節, 度) で、その関節から後ろの向きを1か所だけ折る"""
    pts = [(0.0, 0.0, 0.0)]
    extra = 0.0
    for j in range(1, JOINTS + 1):
        if kink and j - 1 in kink:
            extra += math.radians(kink[j - 1])
        yaw = yaw0 + amp * math.sin(freq * j + phase) + extra
        pit = pitch0 + pitch * math.sin(0.5 * j)
        x, y, z = pts[-1]
        pts.append((x + SEG * math.sin(yaw) * math.cos(pit), y + SEG * math.sin(pit),
                    z + SEG * math.cos(yaw) * math.cos(pit)))
    return pts


def bend(geo, **path):
    geo = copy.deepcopy(geo)
    by_name = {b['name']: b for b in bones_of(geo)}
    pts = bent_path(**path)
    angles = []
    for j in range(1, len(pts) - 1):
        a = boss_kit._unit(boss_kit._sub(pts[j - 1], pts[j]))
        b = boss_kit._unit(boss_kit._sub(pts[j], pts[j + 1]))
        angles.append(math.degrees(math.acos(max(-1.0, min(1.0, boss_kit._dot(a, b))))))
    place(by_name, 'head', pts[0], boss_kit._unit(boss_kit._sub(pts[0], pts[1])))
    for k in range(1, JOINTS + 1):
        mid = boss_kit._mid(pts[k - 1], pts[k])
        place(by_name, f"seg_{k:02d}", mid, boss_kit._unit(boss_kit._sub(pts[k - 1], pts[k])))
    print(f"曲げた図: 関節の最大の折れ角 {max(angles):.1f} 度（関節 1〜3: "
          + ", ".join(f"{a:.1f}" for a in angles[:3]) + "）")
    return geo


def jaw_out(geo, f=1.0, open_deg=50.0):
    """顎を f だけ突き出し、下顎を open_deg 開いた姿勢（jaw の子孫を動かし、膜を伸ばした長さで作り直す）"""
    geo = copy.deepcopy(geo)
    by_name = {b['name']: b for b in bones_of(geo)}
    delta = [v * U * f for v in build.JAW_D]
    shift_bones([b for b in by_name.values() if descends(by_name, b['name'], 'jaw')], delta)
    by_name['jaw']['rotation'] = [round(build.JAW_TILT * f, 3), 0, 0]
    by_name['jaw_lower']['rotation'] = [open_deg, 0, 0]
    rx, sz = build.sling_pose(f)
    sling = by_name['jaw_sling']
    pivot = sling['pivot']
    cubes = build.sling_cubes(build.SL_LEN * sz)
    rest = build.P(0.0, build.SL_Y, build.SL_FRONT)
    sling['cubes'] = cubes
    shift_bones([{'pivot': [0, 0, 0], 'cubes': cubes}], [pivot[i] - rest[i] for i in range(3)])
    sling['rotation'] = [round(rx, 3), 0, 0]
    return geo


def main(argv):
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    want = set(argv) or {'whole', 'head', 'jaw', 'bent', 'oblique'}
    OUT.mkdir(parents=True, exist_ok=True)
    geo = load()
    fore = {'head'} | {f"seg_{k:02d}" for k in range(1, 5)}
    if 'whole' in want:
        boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'goblin_views.png', scale=2.6)
    if 'head' in want:
        boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'goblin_head_views.png', scale=6.0,
                                bones=under(geo, fore))
    if 'jaw' in want:
        g = jaw_out(geo)
        name = write_posed(g, 'jaw')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'goblin_jaw_out.png', scale=5.0,
                                views=views('LEFT', 'FRONT', 'BOTTOM', 'TOP'), geo_dir=POSED,
                                bones=under(g, fore))
    if 'bent' in want:
        g = bend(geo)
        name = write_posed(g, 'bent')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'goblin_bent_views.png', scale=2.6,
                                views=views('TOP', 'LEFT'), geo_dir=POSED)
        g = bend(geo, amp=0.0, pitch=0.0, kink={1: 18.0, 2: -16.0, 3: 18.0})
        name = write_posed(g, 'neck')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'goblin_bent_neck.png', scale=5.0,
                                views=views('TOP', 'BOTTOM'), geo_dir=POSED,
                                bones=under(g, {'head'} | {f"seg_{k:02d}" for k in range(1, 7)}))
    if 'oblique' in want:
        # 斜め前: 吻をカメラ側へ 40 度ヨー、15 度ピッチ。LEFT の図が左前の 3/4 の見え方になる
        g = bend(geo, amp=0.0, pitch=0.0, yaw0=math.radians(-40), pitch0=math.radians(-15))
        name = write_posed(g, 'oblique')
        g2 = jaw_out(geo)
        g2 = bend(g2, amp=0.0, pitch=0.0, yaw0=math.radians(-40), pitch0=math.radians(-15))
        name2 = write_posed(g2, 'oblique_jaw')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'goblin_oblique.png', scale=2.6,
                                views=views('LEFT'), geo_dir=POSED)
        boss_views.render_parts(atlas, [(name2, 0.0)], OUT / 'goblin_oblique_jaw.png', scale=2.6,
                                views=views('LEFT'), geo_dir=POSED)


if __name__ == '__main__':
    main(sys.argv[1:])
