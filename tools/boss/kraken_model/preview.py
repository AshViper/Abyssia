# -*- coding: utf-8 -*-
"""大クラーケンの六面図（実テクスチャ貼り）を out/ に書き出す。

```bash
python tools/boss/kraken_model/preview.py              # 全部
python tools/boss/kraken_model/preview.py body bent    # 一部だけ（rest / whole / body / bent / severed）
```

| 出力 | 中身 |
| --- | --- |
| `out/kraken_rest_views.png` | 書き出したままの静止姿勢（腕 8 本が +Z へ一直線）の六面図 |
| `out/kraken_views.png` | 腕を付け根の輪から広げて垂らした姿（Java の置き方）の六面図 |
| `out/kraken_body_views.png` | 本体（外套膜・ひれ・頭・眼・嘴・漏斗）と腕の付け根 3 体節の六面図を拡大 |
| `out/kraken_bent_views.png` | 腕を強く巻いた姿（関節あたり最大 28 度）。TOP / LEFT / FRONT / BOTTOM |
| `out/kraken_bent_close.png` | 巻いた腕 2 本の先半分の拡大。袖の重なりと吸盤・鉤を見る |
| `out/kraken_severed_views.png` | 腕 1 を切断した姿（arm_1_04..15 を隠し、arm_1_stump を見せる） |
| `out/kraken_stump_close.png` | 切断した腕 1 の付け根と蓋の拡大 |

置き方は contract.json の bone_rule / arm_rotation_with_roll と同じ: 各体節を関節の中点へ運び、
局所 +Z を先端へ、局所 +Y（背）を平行移動で受け渡した向きへ（付け根では本体の軸から外へ）。
回転は boss_kit.euler_from_axes で geo の角に直すので、GeckoLib の回転順（X→Y→Z）のまま描かれる。
neverRender の骨（arm_A_stump）は、切断した腕のものだけを描く。
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
from boss_kit import _add as vadd, _sub as vsub, _mul as vmul, _unit as vunit, _cross as vcross, _dot as vdot
from build import GEO, NAME, SEG, SEGS, ARMS, CUT_SEG, ANCHOR_Y, ANCHOR_R

OUT = HERE / 'out'
# 姿勢を付けた geo の置き場。描き終えたら消える（リポジトリに残さない）
_TMP = tempfile.TemporaryDirectory(prefix='kraken_posed_')
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


def write_posed(geo, tag):
    name = f"{NAME}_{tag}"
    (POSED / f"{name}.geo.json").write_text(json.dumps(geo), encoding='utf-8')
    return name


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


def place(by_name, root, target, u, v, w):
    """root Bone の pivot を target へ運び、局所 X / Y / Z を u / v / w（geo 空間）へ向ける"""
    rest = by_name[root]['pivot']
    shift(by_name, root, [target[i] - rest[i] for i in range(3)])
    by_name[root]['rotation'] = [round(a, 3) for a in boss_kit.euler_from_axes(u, v, w)]


def anchor(A):
    a = math.radians(45.0 * (A - 1))
    return (ANCHOR_R * math.cos(a), ANCHOR_Y, ANCHOR_R * math.sin(a))


def path(A, heading):
    """関節 0..15 の位置。heading(j) が関節 j-1 → j の (ピッチ, ヨー) を度で返す"""
    pts = [anchor(A)]
    for j in range(1, SEGS + 1):
        p, y = (math.radians(v) for v in heading(j))
        d = (math.cos(p) * math.cos(y), math.sin(p), math.cos(p) * math.sin(y))
        pts.append(vadd(pts[-1], vmul(d, SEG)))
    return pts


def natural(A):
    """付け根から外へ下り、先へ行くほど水平へ戻って少し揺れる腕"""
    t = A - 1
    return path(A, lambda j: (-52.0 + 4.0 * j + 7.0 * math.sin(0.6 * j + t),
                              45.0 * t + 10.0 * math.sin(0.45 * j + 1.3 * t)))


def curled(A):
    """強く巻いた腕。偶数本目は吸盤面の側へ縦に巻き込み、奇数本目は横へ巻く（関節あたり 24〜28 度）"""
    t = A - 1
    if t % 2 == 0:
        return path(A, lambda j: (-35.0 - (28.0 * (j - 3) if j > 3 else 0.0), 45.0 * t))
    sign = 1.0 if t % 4 == 1 else -1.0
    return path(A, lambda j: (-30.0 + 2.0 * j, 45.0 * t + (sign * 24.0 * (j - 2) if j > 2 else 0.0)))


def pose(geo, paths, severed=()):
    """Java の置き方で腕を置く。描かない骨（隠した体節と切れていない腕の stump）の名前も返す"""
    geo = copy.deepcopy(geo)
    by_name = {b['name']: b for b in bones_of(geo)}
    worst = 0.0
    for A, pts in paths.items():
        o = vunit((pts[0][0], 0.0, pts[0][2]))           # 本体の軸から付け根へ（水平）
        back, prev = o, None
        for k in range(1, SEGS + 1):
            f = vunit(vsub(pts[k], pts[k - 1]))          # 先端向き = 局所 +Z
            if prev is not None:
                worst = max(worst, math.degrees(math.acos(max(-1.0, min(1.0, vdot(prev, f))))))
            prev = f
            b = vsub(back, vmul(f, vdot(back, f)))
            if math.sqrt(vdot(b, b)) < 0.2:
                b = vsub((0.0, 1.0, 0.0), vmul(f, f[1]))
            back = vunit(b)                              # 背 = 局所 +Y（吸盤面 -Y は内へ）
            u = vcross(back, f)
            mid = tuple((pts[k - 1][i] + pts[k][i]) / 2.0 for i in range(3))
            place(by_name, f"arm_{A}_{k:02d}", mid, u, back, f)
    hidden = set()
    for A in range(1, ARMS + 1):
        if A in severed:
            hidden |= {f"arm_{A}_{k:02d}" for k in range(CUT_SEG + 1, SEGS + 1)}
        else:
            hidden.add(f"arm_{A}_stump")
    print(f"姿勢: 関節の最大の折れ角 {worst:.1f} 度")
    return geo, hidden


def keep(geo, hidden, roots=None):
    """描く骨の絞り込み。roots（root 直下の骨の名前）を渡すとその下だけ"""
    by_name = {b['name']: b for b in bones_of(geo)}
    return lambda n: n not in hidden and (roots is None or root_of(by_name, n) in roots)


def main(argv):
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    want = set(argv) or {'rest', 'whole', 'body', 'bent', 'severed'}
    OUT.mkdir(parents=True, exist_ok=True)
    geo = load()
    stumps = {f"arm_{A}_stump" for A in range(1, ARMS + 1)}
    if 'rest' in want:
        boss_views.render_parts(atlas, [(NAME, 0.0)], OUT / 'kraken_rest_views.png', scale=0.9,
                                bones=keep(geo, stumps))
    if 'whole' in want or 'body' in want:
        g, hidden = pose(geo, {A: natural(A) for A in range(1, ARMS + 1)})
        name = write_posed(g, 'natural')
        if 'whole' in want:
            boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'kraken_views.png', scale=0.75, geo_dir=POSED,
                                    bones=keep(g, hidden))
        if 'body' in want:
            roots = {'mantle'} | {f"arm_{A}_{k:02d}" for A in range(1, ARMS + 1) for k in range(1, 4)}
            boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'kraken_body_views.png', scale=3, geo_dir=POSED,
                                    bones=keep(g, hidden, roots))
    if 'bent' in want:
        g, hidden = pose(geo, {A: curled(A) for A in range(1, ARMS + 1)})
        name = write_posed(g, 'bent')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'kraken_bent_views.png', scale=0.75, geo_dir=POSED,
                                views=views('TOP', 'LEFT', 'FRONT', 'BOTTOM'), bones=keep(g, hidden))
        roots = {f"arm_{A}_{k:02d}" for A in (1, 2) for k in range(6, SEGS + 1)}
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'kraken_bent_close.png', scale=2.2, geo_dir=POSED,
                                views=views('TOP', 'LEFT', 'FRONT', 'BOTTOM'), bones=keep(g, hidden, roots))
    if 'severed' in want:
        g, hidden = pose(geo, {A: natural(A) for A in range(1, ARMS + 1)}, severed={1})
        name = write_posed(g, 'severed')
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'kraken_severed_views.png', scale=0.75, geo_dir=POSED,
                                views=views('TOP', 'LEFT', 'FRONT'), bones=keep(g, hidden))
        # 切断面を正面（-Z）へ向けた腕 1 の付け根。FRONT で蓋を真正面から、LEFT / TOP で横から見る
        paths = {A: natural(A) for A in range(1, ARMS + 1)}
        paths[1] = path(1, lambda j: (-12.0, -90.0 + 6.0 * j))
        g, hidden = pose(geo, paths, severed={1})
        name = write_posed(g, 'stump')
        roots = {'mantle'} | {f"arm_1_{k:02d}" for k in range(1, CUT_SEG + 1)}
        boss_views.render_parts(atlas, [(name, 0.0)], OUT / 'kraken_stump_close.png', scale=3.5, geo_dir=POSED,
                                views=views('FRONT', 'LEFT', 'TOP', 'BOTTOM'), bones=keep(g, hidden, roots))


if __name__ == '__main__':
    main(sys.argv[1:])
