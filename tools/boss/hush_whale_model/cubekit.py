# -*- coding: utf-8 -*-
"""大きな直方体だけで組む GNT01 巨大生物用の小さな生成キット（バニラ風のキューブ感）。

`boss_kit.Kit` は輪を板で結ぶ滑らかな形の道具なので、ここでは使わない。こちらは
  - 箱はブロック単位で書く（geo へは 8 倍。MODEL_SCALE 2 なので geo 8 単位 = 1 ブロック）
  - テクスチャは 1 テクセル = 0.25 ブロック（geo 2 単位）。面ごとに `paint` 関数を
    静止姿勢の座標（x, y, s。s は関節 0 からの後ろ向き距離）で呼んで平らな色を塗る
  - 同じ絵になった面はアトラス上で 1 か所を共有する
  - 発光する画素は別の `_glow` 画像にも書く（地の画像にも同じ色で入る）
だけを行う。描画と検査は共通の `../boss_views.py` `../check_animations.py` `../boss_kit.py` を
変えずに使う。lantern_grazer_model/cubekit.py と同じ内容の複製（片方を直したら両方へ）。
"""
import copy
import json
import pathlib
import sys
import tempfile

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import boss_kit          # noqa: E402
import boss_views        # noqa: E402

REPO = HERE.parents[2]
ASSETS = REPO / 'src/main/resources/assets/abyssia'
U = 8.0                  # geo 単位 / ブロック
SEG = 2.0                # 体節の長さ（ブロック）
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def rgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)


class Model:
    tiles = None     # giant_tiles.Tiler（無ければ平らな色のまま）

    def __init__(self, name, joints, paint, size=512, texel=0.25):
        self.name, self.joints, self.paint, self.size, self.texel = name, joints, paint, size, texel
        self.bones, self.order, self.cubes = {}, [], []
        self.bone('head', (0, 0, 0))
        for k in range(1, joints):
            self.bone(f'seg_{k:02d}', (0, 0, SEG * (k - 0.5)))

    def bone(self, name, pivot, parent=None, rotation=None):
        assert name not in self.bones, name
        b = {'name': name, 'pivot': [round(v * U, 3) for v in pivot]}
        if parent:
            assert parent in self.bones, parent
            b['parent'] = parent
        if rotation:
            b['rotation'] = list(rotation)
        b['cubes'] = []
        self.bones[name] = b
        self.order.append(name)
        return name

    def box(self, bone, tag, x0, x1, y0, y1, s0, s1):
        """箱を 1 つ。座標はブロック。tag は paint に渡る"""
        assert x1 > x0 and y1 > y0 and s1 > s0, (bone, tag)
        self.cubes.append((bone, tag, (x0, x1, y0, y1, s0, s1)))

    # ---------------------------------------------------------------- 焼き
    def _face_pixels(self, tag, face, b):
        x0, x1, y0, y1, s0, s1 = b
        lo = (-x1, y0, s0)                    # render 空間（X 反転）
        hi = (-x0, y1, s1)
        cs = [tuple(lo[i] + (hi[i] - lo[i]) * c[i] for i in range(3)) for c in boss_views.FACE_CORNERS[face]]
        c0, c1, c2 = cs[0], cs[1], cs[2]
        du = [c0[i] - c1[i] for i in range(3)]
        dv = [c2[i] - c1[i] for i in range(3)]
        nu = max(1, round(max(abs(v) for v in du) / self.texel))
        nv = max(1, round(max(abs(v) for v in dv) / self.texel))
        rows = []
        for j in range(nv):
            row = []
            for i in range(nu):
                fu, fv = (i + 0.5) / nu, (j + 0.5) / nv
                p = [c1[k] + du[k] * fu + dv[k] * fv for k in range(3)]
                c = self.paint(tag, face, -p[0], p[1], p[2], b)
                glow = False
                if isinstance(c, tuple) and isinstance(c[0], str):
                    c, glow = c[0], bool(c[1])
                row.append((rgb(c), glow))
            rows.append(tuple(row))
        return tuple(rows)

    def bake(self):
        rects, uses = {}, []
        for bone, tag, b in self.cubes:
            per = {}
            for face in FACES:
                px = self._face_pixels(tag, face, b)
                rects.setdefault(px, None)
                per[face] = px
            uses.append(per)
        # 棚詰め（高い順）
        x = y = shelf = 0
        for px in sorted(rects, key=lambda p: (-len(p), -len(p[0]))):
            h, w = len(px), len(px[0])
            if x + w > self.size:
                x, y, shelf = 0, y + shelf, 0
            assert y + h <= self.size, f'{self.name}: アトラス {self.size} に入らない'
            rects[px] = (x, y)
            x += w
            shelf = max(shelf, h)
        self.used_rows = y + shelf
        base = Image.new('RGBA', (self.size, self.size), (0, 0, 0, 0))
        glow = Image.new('RGBA', (self.size, self.size), (0, 0, 0, 0))
        bp, gp = base.load(), glow.load()
        for px, (ox, oy) in rects.items():
            for j, row in enumerate(px):
                for i, (c, g) in enumerate(row):
                    if self.tiles:
                        c = self.tiles.apply(c, ox, oy, i, j, bool(g))
                    bp[ox + i, oy + j] = c + (255,)
                    if g:
                        gp[ox + i, oy + j] = c + (255,)
        for b in self.bones.values():
            b['cubes'] = []
        for (bone, tag, b), per in zip(self.cubes, uses):
            x0, x1, y0, y1, s0, s1 = b
            uv = {}
            for face in FACES:
                px = per[face]
                ox, oy = rects[px]
                uv[face] = {'uv': [ox, oy], 'uv_size': [len(px[0]), len(px)]}
            self.bones[bone]['cubes'].append({
                'origin': [round(x0 * U, 3), round(y0 * U, 3), round(s0 * U, 3)],
                'size': [round((x1 - x0) * U, 3), round((y1 - y0) * U, 3), round((s1 - s0) * U, 3)],
                'uv': uv})
        geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': f'geometry.{self.name}', 'texture_width': self.size,
                            'texture_height': self.size, 'visible_bounds_width': 60.0,
                            'visible_bounds_height': 24.0, 'visible_bounds_offset': [0, 0, 0]},
            'bones': [self.bones[n] for n in self.order]}]}
        return geo, base, glow

    def write(self, anims):
        """geo / animation / texture を資産へ書き出し、点検の 1 行を返す"""
        geo, base, glow = self.bake()
        line = boss_kit.verify(self.name, geo, anims)
        (ASSETS / 'geo/entity').mkdir(parents=True, exist_ok=True)
        (ASSETS / f'geo/entity/{self.name}.geo.json').write_text(json.dumps(geo), encoding='utf-8')
        doc = {'format_version': '1.8.0', 'animations': anims}
        (ASSETS / f'animations/entity/{self.name}.animation.json').write_text(json.dumps(doc), encoding='utf-8')
        base.save(ASSETS / f'textures/entity/boss/{self.name}_geo.png')
        glow.save(ASSETS / f'textures/entity/boss/{self.name}_geo_glow.png')
        return line + f' atlas={self.size} 使用行={self.used_rows}'


def anim(length, bones, loop=True):
    return {'loop': loop, 'animation_length': length, 'bones': bones}


# ==================================================================== 図
class _Atlas:
    def __init__(self, name):
        im = Image.open(ASSETS / f'textures/entity/boss/{name}_geo.png').convert('RGBA')
        self.SIZE = im.size[0]
        px = im.load()
        self.rows = [[px[x, y] for x in range(self.SIZE)] for y in range(self.SIZE)]

    def build(self):
        return self.rows


def _view_geo(name, pitch, yaw, centre_s, pose=None):
    """全体を 1 つの骨で回した geo を一時フォルダへ書く（斜めから見るため）。pose は {骨: rotation}"""
    geo = json.loads((ASSETS / f'geo/entity/{name}.geo.json').read_text(encoding='utf-8'))
    geo = copy.deepcopy(geo)
    bones = geo['minecraft:geometry'][0]['bones']
    for b in bones:
        if pose and b['name'] in pose:
            r = b.get('rotation', [0, 0, 0])
            b['rotation'] = [r[i] + pose[b['name']][i] for i in range(3)]
        if 'parent' not in b:
            b['parent'] = '_view'
    bones.insert(0, {'name': '_view', 'pivot': [0, 0, centre_s * U], 'rotation': [pitch, yaw, 0], 'cubes': []})
    d = pathlib.Path(tempfile.mkdtemp(prefix='gnt_view_'))
    (d / f'{name}.geo.json').write_text(json.dumps(geo), encoding='utf-8')
    return d


def preview(name, out_dir, length, scale=3, pose=None, tag=''):
    """横・上・斜め前の 3 枚を out_dir へ。返り値は {名前: パス}"""
    out_dir.mkdir(parents=True, exist_ok=True)
    atlas = _Atlas(name)
    views = {v[0]: v for v in boss_views.VIEWS}
    out = {}
    plain = _view_geo(name, 0, 0, length / 2, pose)
    for key, view in (('side', 'LEFT'), ('top', 'TOP'), ('front', 'FRONT')):
        out[key] = out_dir / f'{name}{tag}_{key}.png'
        boss_views.render_parts(atlas, [(name, 0.0)], out[key], scale=scale, views=(views[view],), geo_dir=plain)
    obl = _view_geo(name, 14, 28, length / 2, pose)
    out['oblique'] = out_dir / f'{name}{tag}_oblique.png'
    boss_views.render_parts(atlas, [(name, 0.0)], out['oblique'], scale=scale, views=(views['LEFT'],), geo_dir=obl)
    return out


def compare(name, out_dir, ref_path, ref_box, shots, title_h=0):
    """デザイン画の該当部分（上）と preview（下）を 1 枚に並べる"""
    ref = Image.open(ref_path).convert('RGB').crop(ref_box)
    ims = [Image.open(p).convert('RGB') for p in shots]
    W = max(i.size[0] for i in ims)
    ref = ref.resize((W, int(ref.size[1] * W / ref.size[0])), Image.LANCZOS)
    H = ref.size[1] + sum(i.size[1] for i in ims) + 8 * len(ims)
    canvas = Image.new('RGB', (W, H), (20, 22, 26))
    canvas.paste(ref, (0, 0))
    y = ref.size[1] + 8
    for i in ims:
        canvas.paste(i, (0, y))
        y += i.size[1] + 8
    path = out_dir / f'{name}_compare.png'
    canvas.save(path)
    print('wrote', path)
    return path
