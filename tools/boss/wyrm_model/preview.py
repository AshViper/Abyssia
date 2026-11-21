# -*- coding: utf-8 -*-
"""海溝の大ワームの六面図（実テクスチャ貼り）を out/ に書き出す。

```bash
python tools/boss/wyrm_model/preview.py            # 全部
python tools/boss/wyrm_model/preview.py head bent  # 一部だけ（whole / head / tail / open / bent）
```

| 出力 | 中身 |
| --- | --- |
| `out/wyrm_views.png` | 静止姿勢（一直線）の全身の六面図 |
| `out/wyrm_head_views.png` | 頭と首（seg_01〜02）の六面図を拡大 |
| `out/wyrm_head_open.png` | 大顎を全開にした頭（LEFT / FRONT / TOP） |
| `out/wyrm_tail_views.png` | 尾（seg_20〜26）の LEFT / TOP / BACK |
| `out/wyrm_bent_views.png` | Java の描画と同じやり方で S 字に曲げた全身（TOP / LEFT） |
| `out/wyrm_bent_neck.png` | 曲げた首（頭〜seg_05）の拡大（TOP / LEFT）。袖の重なりを見る |
| `out/wyrm_bent_mid.png` | 折れ角が最大（約 23 度）の関節 14 のまわり（seg_12〜16、TOP / BOTTOM） |

曲げた図は serpent_model/preview.py と同じく、各 root Bone を「関節の中点へ運び、局所 -Z を前の関節へ
向ける」ことで作る（contract.json の bone_rule と同じ）。大顎を開いた図は、各 mandible の rotation に
x を足す（静止姿勢の z はそのまま。GeckoLib が animation を初期値へ足すのと同じ）。
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
from build import GEO, NAME, SEG, JOINTS, MAND_OPEN_MAX

OUT = HERE / 'out'
_TMP = tempfile.TemporaryDirectory(prefix='wyrm_posed_')
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


def under(geo, roots):
    by_name = {b['name']: b for b in bones_of(geo)}
    keep = {n for n in by_name if root_of(by_name, n) in roots}
    return lambda n: n in keep


def write_posed(geo, tag):
    name = f"{NAME}_{tag}"
    (POSED / f"{name}.geo.json").write_text(json.dumps(geo), encoding='utf-8')
    return name


def shift(by_name, root, delta):
    for b in by_name.values():
        if root_of(by_name, b['name']) != root:
            continue
        b['pivot'] = [b['pivot'][i] + delta[i] for i in range(3)]
        for c in b['cubes']:
            c['origin'] = [c['origin'][i] + delta[i] for i in range(3)]
            if 'pivot' in c:
                c['pivot'] = [c['pivot'][i] + delta[i] for i in range(3)]


def place(by_name, root, target, d):
    """root Bone の pivot を target へ運び、局所 -Z を d（単位ベクトル、geo 空間）へ向ける"""
    rest = by_name[root]['pivot']
    shift(by_name, root, [target[i] - rest[i] for i in range(3)])
    w = boss_kit._mul(d, -1.0)
    v = boss_kit._unit(boss_kit._sub((0.0, 1.0, 0.0), boss_kit._mul(w, w[1])))
    u = boss_kit._cross(v, w)
    r = boss_kit.euler_from_axes(u, v, w)
    by_name[root]['rotation'] = [round(a, 3) for a in r]


def bent_path(amp=0.95, freq=0.42, pitch=0.22):
    """S 字に曲がった関節の並び（serpent_model と同じ曲げ方。関節ごとの折れは最大で約 23 度）"""
    pts = [(0.0, 0.0, 0.0)]
    for j in range(1, JOINTS + 1):
        yaw = amp * math.sin(freq * j + 0.4)
        pit = pitch * math.sin(0.3 * j)
        x, y, z = pts[-1]
        pts.append((x + SEG * math.sin(yaw) * math.cos(pit), y + SEG * math.sin(pit),
                    z + SEG * math.cos(yaw) * math.cos(pit)))
    return pts


def bend(geo):
    geo = copy.deepcopy(geo)
    by_name = {b['name']: b for b in bones_of(geo)}
    pts = bent_path()
    worst = 0.0
    for j in range(1, len(pts) - 1):
        a = boss_kit._unit(boss_kit._sub(pts[j - 1], pts[j]))
        b = boss_kit._unit(boss_kit._sub(pts[j], pts[j + 1]))
        worst = max(worst, math.degrees(math.acos(max(-1.0, min(1.0, boss_kit._dot(a, b))))))
    place(by_name, 'head', pts[0], boss_kit._unit(boss_kit._sub(pts[0], pts[1])))
    for k in range(1, JOINTS + 1):
        mid = boss_kit._mid(pts[k - 1], pts[k])
        place(by_name, f"seg_{k:02d}", mid, boss_kit._unit(boss_kit._sub(pts[k - 1], pts[k])))
    print(f"曲げた図: 関節の最大の折れ角 {worst:.1f} 度")
    return geo


def open_mouth(geo, angle=MAND_OPEN_MAX):
    geo = copy.deepcopy(geo)
    for b in bones_of(geo):
        if b['name'].startswith('mandible_'):
            r = b.get('rotation', [0, 0, 0])
            b['rotation'] = [r[0] + angle, r[1], r[2]]
    return geo


def main(argv):
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    want = set(argv) or {'whole', 'head', 'tail', 'open', 'bent'}
    OUT.mkdir(parents=True, exist_ok=True)
    geo = load()
    neck = {'head'} | {f"seg_{k:02d}" for k in range(1, 3)}
    if 'whole' in want:
        boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'wyrm_views.png', scale=0.55)
    if 'head' in want:
        boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'wyrm_head_views.png', scale=2.2,
                                bones=under(geo, neck))
    if 'tail' in want:
        boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'wyrm_tail_views.png', scale=1.6,
                                views=views('LEFT', 'TOP', 'BACK'),
                                bones=under(geo, {f"seg_{k:02d}" for k in range(20, JOINTS + 1)}))
    if 'open' in want:
        g = open_mouth(geo)
        name = write_posed(g, 'open')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'wyrm_head_open.png', scale=2.2,
                                views=views('LEFT', 'FRONT', 'TOP'), geo_dir=POSED, bones=under(g, {'head'}))
    if 'bent' in want:
        g = bend(geo)
        name = write_posed(g, 'bent')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'wyrm_bent_views.png', scale=0.55,
                                views=views('TOP', 'LEFT'), geo_dir=POSED)
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'wyrm_bent_neck.png', scale=1.6,
                                views=views('TOP', 'LEFT'), geo_dir=POSED,
                                bones=under(g, {'head'} | {f"seg_{k:02d}" for k in range(1, 6)}))
        # 折れ角がいちばん大きい（約 23 度）関節 14 のまわり。外側で袖の奥が抜けていないかを見る
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'wyrm_bent_mid.png', scale=1.6,
                                views=views('TOP', 'BOTTOM'), geo_dir=POSED,
                                bones=under(g, {f"seg_{k:02d}" for k in range(12, 17)}))


if __name__ == '__main__':
    main(sys.argv[1:])
