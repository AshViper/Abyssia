# -*- coding: utf-8 -*-
"""大きな箱を少数組んで作る GeckoLib モデルの小さな道具（GNT01 の巨大野生生物用）。

`tools/boss/boss_kit.py` は輪を板で結ぶ滑らかな形向けなので、こちらは
「バニラ風の直方体 + 粗いドット絵」だけを扱う。`shroud_ray_model` と `duskmaw_model` に
同じ内容を置いてある（共通ツールは変えない約束のため複製）。

- 箱ごと・面ごとに UV を割り当て、1 テクセル = `texel` geo 単位（MODEL_SCALE 2 なら 2 単位 = 0.25 ブロック）
- 絵は `paint(face, x, y, z)` が静止姿勢の geo 座標（テクセルの中心）から `(rgb, 発光)` を返す。
  座標で塗るので、箱をまたぐ模様（翼の縁、体のひび）がつながる
- 発光は同じ配置の `_glow.png` へ分けて書く
- 面と UV の対応は `boss_views.FACE_CORNERS`（GeckoLib 4.9.2 の焼き込みと同じ表）から取る
"""
import copy
import json
import math
import pathlib
import sys
import tempfile

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import boss_views  # noqa: E402

ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/abyssia'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
SOFT = 'easeinoutsine'


def hexrgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)


def cell(x, y, z, size, seed=0):
    """粗い升目ごとの 0..1。色の段を升目単位で選ぶ（画素ごとのノイズにしない）"""
    h = (int(math.floor(x / size)) * 73856093) ^ (int(math.floor(y / size)) * 19349663) \
        ^ (int(math.floor(z / size)) * 83492791) ^ (seed * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


class Model:
    tiles = None     # giant_tiles.Tiler（無ければ平らな色のまま）

    def __init__(self, name, atlas, texel=2.0):
        self.name, self.SIZE, self.texel = name, atlas, texel
        self.bones = []          # dict(name, parent, pivot, rotation, cubes)
        self.by_name = {}
        self._faces = []         # (cube dict, face, tw, th, paint, 箱の範囲)
        self.count = 0

    # ---- 形 ----
    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        b = {'name': name, 'pivot': [float(v) for v in pivot]}
        if parent:
            assert parent in self.by_name, parent
            b['parent'] = parent
        if rotation:
            b['rotation'] = [float(v) for v in rotation]
        b['cubes'] = []
        self.bones.append(b)
        self.by_name[name] = b
        return name

    def cube(self, bone, a, b, paint, texel=None, rot=None, pivot=None):
        """a, b は対角の 2 点（geo 座標）"""
        lo = [min(a[i], b[i]) for i in range(3)]
        hi = [max(a[i], b[i]) for i in range(3)]
        size = [hi[i] - lo[i] for i in range(3)]
        assert min(size) > 0, (bone, a, b)
        t = texel or self.texel
        c = {'origin': [float(v) for v in lo], 'size': [float(v) for v in size], 'uv': {}}
        if rot:
            c['rotation'] = [float(v) for v in rot]
            c['pivot'] = [float(v) for v in (pivot or [(lo[i] + hi[i]) / 2 for i in range(3)])]
        dims = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
        for f in FACES:
            i, j = dims[f]
            tw = max(1, int(round(size[i] / t)))
            th = max(1, int(round(size[j] / t)))
            self._faces.append((c, f, tw, th, paint, (lo, hi)))
        self.by_name[bone]['cubes'].append(c)
        self.count += 1
        return c

    # ---- 絵 ----
    def bake(self):
        """面を棚詰めで並べ、絵を塗る。(base, glow) の PIL 画像を返す"""
        S = self.SIZE
        base = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        glow = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        bp, gp = base.load(), glow.load()
        x = y = shelf = 0
        for c, f, tw, th, paint, (lo, hi) in sorted(self._faces, key=lambda r: -r[3]):
            if x + tw > S:
                x, y, shelf = 0, y + shelf, 0
            assert y + th <= S, f'{self.name}: アトラス {S} に入らない'
            shelf = max(shelf, th)
            c['uv'][f] = {'uv': [x, y], 'uv_size': [tw, th]}
            # 隅は (右上, 左上, 左下, 右下)。x は描画空間（geo の X を反転）
            cor = [(-(hi[0]) + (hi[0] - lo[0]) * k[0], lo[1] + (hi[1] - lo[1]) * k[1],
                    lo[2] + (hi[2] - lo[2]) * k[2]) for k in boss_views.FACE_CORNERS[f]]
            for j in range(th):
                for i in range(tw):
                    u, v = (i + 0.5) / tw, (j + 0.5) / th
                    p = [cor[1][n] + u * (cor[0][n] - cor[1][n]) + v * (cor[2][n] - cor[1][n]) for n in range(3)]
                    got = paint(f, -p[0], p[1], p[2])
                    if got is None:
                        continue
                    rgb, lit = got
                    if self.tiles:
                        rgb = self.tiles.apply(rgb, x, y, i, j, bool(lit))
                    bp[x + i, y + j] = tuple(rgb) + (255,)
                    if lit:
                        gp[x + i, y + j] = tuple(rgb) + (255,)
            x += tw
        self.used_rows = y + shelf
        self._base = base
        return base, glow

    # boss_views.render_parts が呼ぶ（atlas の代わりに自分を渡す）
    def build(self):
        px = self._base.load()
        return [[px[i, j] for i in range(self.SIZE)] for j in range(self.SIZE)]

    # ---- 書き出し ----
    def geo(self):
        return {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': f'geometry.{self.name}', 'texture_width': self.SIZE,
                            'texture_height': self.SIZE, 'visible_bounds_width': 40.0,
                            'visible_bounds_height': 12.0, 'visible_bounds_offset': [0, 0, 0]},
            'bones': self.bones}]}

    def write(self, animations):
        base, glow = self.bake()
        (ASSETS / 'geo/entity' / f'{self.name}.geo.json').write_text(json.dumps(self.geo()), encoding='utf-8')
        (ASSETS / 'animations/entity' / f'{self.name}.animation.json').write_text(
            json.dumps({'format_version': '1.8.0', 'animations': animations}), encoding='utf-8')
        base.save(ASSETS / 'textures/entity/boss' / f'{self.name}_geo.png')
        glow.save(ASSETS / 'textures/entity/boss' / f'{self.name}_geo_glow.png')
        print(f'{self.name}: cube {self.count}、bone {len(self.bones)}、アトラス {self.SIZE} の {self.used_rows} 行を使用')


# ==================== animation ====================
def keys(frames):
    """frames = [(t, (x, y, z)), ...] を rotation のキーにする"""
    out = {}
    for n, (t, v) in enumerate(frames):
        vec = [round(a, 2) for a in v]
        out[f'{t:.2f}'] = vec if n == 0 else {'vector': vec, 'easing': SOFT}
    return {'rotation': out}


def wave(length, amp, phase=0.0, steps=8, bias=(0, 0, 0)):
    """amp = (x, y, z) の振幅の正弦の往復。始点と終点が一致するので loop でつながる"""
    return keys([(length * i / steps,
                  tuple(bias[k] + amp[k] * math.sin(2 * math.pi * i / steps + phase) for k in range(3)))
                 for i in range(steps + 1)])


def anim(length, loop, bones):
    return {'loop': loop, 'animation_length': length, 'bones': bones}


def pose_at(animation, t):
    """その animation の時刻 t の回転（線形補間。図を描くためだけの近似）"""
    out = {}
    for bone, tracks in animation['bones'].items():
        ks = sorted((float(k), v['vector'] if isinstance(v, dict) else v) for k, v in tracks['rotation'].items())
        prev = ks[0]
        val = ks[-1][1]
        for k in ks:
            if k[0] >= t:
                f = 0.0 if k[0] == prev[0] else (t - prev[0]) / (k[0] - prev[0])
                val = [prev[1][i] + (k[1][i] - prev[1][i]) * f for i in range(3)]
                break
            prev = k
        out[bone] = val
    return out


# ==================== 図 ====================
_TMP = tempfile.TemporaryDirectory(prefix='boxkit_posed_')
POSED = pathlib.Path(_TMP.name)


def posed(model, tag, rots=None, cam=None, centre=(0, 0, 0)):
    """rots = {bone: (rx, ry, rz)} を静止回転へ足し、cam = (rx, ry, rz) で全体を回した geo を一時置き場へ書く"""
    geo = copy.deepcopy(model.geo())
    bones = geo['minecraft:geometry'][0]['bones']
    for b in bones:
        add = (rots or {}).get(b['name'])
        if add:
            r = b.get('rotation', [0, 0, 0])
            b['rotation'] = [r[i] + add[i] for i in range(3)]
    if cam:
        for b in bones:
            b.setdefault('parent', '__cam')
        bones.insert(0, {'name': '__cam', 'pivot': list(centre), 'rotation': list(cam), 'cubes': []})
    name = f'{model.name}_{tag}'
    (POSED / f'{name}.geo.json').write_text(json.dumps(geo), encoding='utf-8')
    return name


def shot(model, out, view='LEFT', scale=2.0, **kw):
    """1 方向の図を PNG へ。kw は posed と同じ"""
    name = posed(model, out.stem, **kw)
    views = tuple(v for v in boss_views.VIEWS if v[0] == view)
    boss_views.render_parts(model, [(name, 0.0)], out, scale=scale, views=views, geo_dir=POSED)
    return out


def sheet(paths, out, design=None, width=1500):
    """図を縦に並べる。design を渡すといちばん上にデザイン画の切り抜きを置く（比較画像）"""
    ims = [Image.open(p).convert('RGB') for p in ([design] if design else []) + list(paths)]
    ims = [im.resize((width, max(1, im.height * width // im.width)),
                     Image.LANCZOS if n == 0 and design else Image.NEAREST) if im.width > width or (n == 0 and design)
           else im for n, im in enumerate(ims)]
    H = sum(im.height for im in ims) + 8 * (len(ims) - 1)
    canvas = Image.new('RGB', (width, H), boss_views.BG)
    y = 0
    for im in ims:
        canvas.paste(im, (0, y))
        y += im.height + 8
    canvas.save(out)
    print('wrote', out)
