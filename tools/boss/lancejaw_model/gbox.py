# -*- coding: utf-8 -*-
"""GNT01 巨大野生生物を「大きな直方体を少数組む」方式で作るための道具。

`lancejaw_model/` と `slateback_model/` に同じものを置く（種のフォルダだけで完結させるため）。
共通の `../boss_views.py`（描画）と `../boss_kit.py`（アニメーションのキー、verify）は変えずに使う。

- 座標は**ブロック**で書く（Z- が前、+Y が上、+X が左）。geo へは 1 ブロック = 8 単位（MODEL_SCALE 2）で出す
- 1 テクセル = 0.25 ブロック（2 単位）。箱の寸法は 0.25 ブロック刻みにする
- 塗りは箱ごとの関数 `paint(face, x, y, z)`。テクセルの中心のブロック座標（箱を回す前）を受けて
  `色` か `(色, 発光色)` を返す。面の向きの取り違えが起きない（面の四隅と絵の対応は boss_views の表と同じ）
- 骨の約束: `head` と `seg_01..seg_NN` が root 直下、関節 j は (0,0,2j) ブロック、seg_k の pivot は (0,0,2k-1)
"""
import copy
import json
import math
import pathlib
import shutil
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import boss_kit      # noqa: E402
import boss_views    # noqa: E402

ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/abyssia'
GEO = ASSETS / 'geo/entity'
ANIM = ASSETS / 'animations/entity'
TEX = ASSETS / 'textures/entity/boss'
LOCKS = ROOT / 'tools/texture_locks/assets/textures/entity/boss'

MODEL_SCALE = 2.0
U = 16.0 / MODEL_SCALE      # 1 ブロック = 8 単位
TEXEL = 2.0                 # 1 テクセル = 2 単位 = 0.25 ブロック
JOINT_SPACING = 2.0         # ブロック
SEG = JOINT_SPACING * U


def rgb(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def flat(color):
    """全面を 1 色で塗る paint"""
    return lambda face, x, y, z: color


class Model:
    tiles = None     # giant_tiles.Tiler（無ければ平らな色のまま）

    def __init__(self, name, joints, atlas=256, cube_limit=150):
        self.name, self.joints, self.size, self.cube_limit = name, joints, atlas, cube_limit
        self.bones, self.by, self.recs = [], {}, []
        self.bone("head", (0, 0, 0))
        for k in range(1, joints):
            self.bone(f"seg_{k:02d}", (0, 0, JOINT_SPACING * (k - 0.5)))

    # ------------------------------------------------------------ 骨と箱
    def bone(self, name, pivot, parent=None, rot=None):
        assert name not in self.by, name
        b = {"name": name}
        if parent:
            b["parent"] = parent
        b["pivot"] = [round(v * U, 3) for v in pivot]
        if rot:
            b["rotation"] = [round(v, 3) for v in rot]
        b["cubes"] = []
        self.by[name] = b
        # 親のすぐ後ろへ入れる（読みやすさだけ。GeckoLib は順番を問わない）
        self.bones.append(b)
        return name

    def box(self, bone, x, y, z, paint, rot=None, pivot=None):
        """x, y, z は (小, 大) のブロック座標。rot は箱の回転（度）、pivot はブロック"""
        (x0, x1), (y0, y1), (z0, z1) = x, y, z
        assert x1 > x0 and y1 > y0 and z1 > z0, (bone, x, y, z)
        cube = {"origin": [round(x0 * U, 3), round(y0 * U, 3), round(z0 * U, 3)],
                "size": [round((x1 - x0) * U, 3), round((y1 - y0) * U, 3), round((z1 - z0) * U, 3)], "uv": {}}
        if rot:
            cube["rotation"] = [round(v, 3) for v in rot]
            cube["pivot"] = [round(v * U, 3) for v in pivot]
        t = tuple(max(1, int(round(v / TEXEL))) for v in cube["size"])
        self.by[bone]["cubes"].append(cube)
        self.recs.append({"cube": cube, "t": t, "box": (x, y, z), "paint": paint, "bone": bone,
                          "rot": bool(rot)})
        return cube

    def seg_of(self, z):
        return max(1, min(self.joints - 1, int(z // JOINT_SPACING) + 1))

    # ------------------------------------------------------------ UV
    @staticmethod
    def layout(t):
        w, h, d = t
        return (2 * (d + w), d + h), {"up": (d, 0, w, d), "down": (d + w, 0, w, d), "east": (0, d, d, h),
                                      "north": (d, d, w, h), "west": (d + w, d, d, h),
                                      "south": (2 * d + w, d, w, h)}

    def pack(self):
        order = sorted(range(len(self.recs)), key=lambda i: (-self.layout(self.recs[i]["t"])[0][1],
                                                             -self.layout(self.recs[i]["t"])[0][0]))
        x = y = row_h = 0
        for i in order:
            rec = self.recs[i]
            (bw, bh), faces = self.layout(rec["t"])
            if x + bw > self.size:
                x, y, row_h = 0, y + row_h, 0
            assert y + bh <= self.size, "アトラスに収まらない"
            rec["at"] = (x, y)
            for name, (u, v, fw, fh) in faces.items():
                rec["cube"]["uv"][name] = {"uv": [x + u, y + v], "uv_size": [fw, fh]}
            x += bw
            row_h = max(row_h, bh)
        self.used_rows = y + row_h
        return self.used_rows

    def paint(self):
        """(地のテクスチャ, 発光テクスチャ) を RGBA の PIL 画像で返す。発光しない画素は (0,0,0,0)"""
        base = Image.new('RGBA', (self.size, self.size), (0, 0, 0, 0))
        glow = Image.new('RGBA', (self.size, self.size), (0, 0, 0, 0))
        for rec in self.recs:
            ax, ay = rec["at"]
            (x0, x1), (y0, y1), (z0, z1) = rec["box"]
            for face, (u, v, fw, fh) in self.layout(rec["t"])[1].items():
                for r in range(fh):
                    for c in range(fw):
                        fc, fr = (c + 0.5) / fw, (r + 0.5) / fh
                        # 面の四隅と絵の対応は boss_views.FACE_CORNERS（GeckoLib の GeoQuad）と同じ
                        if face == "west":       # geo +X 側。左の列が前
                            p = (x1, y1 - fr * (y1 - y0), z0 + fc * (z1 - z0))
                        elif face == "east":     # geo -X 側。左の列が後ろ
                            p = (x0, y1 - fr * (y1 - y0), z1 - fc * (z1 - z0))
                        elif face == "north":    # 前。左の列が geo -X
                            p = (x0 + fc * (x1 - x0), y1 - fr * (y1 - y0), z0)
                        elif face == "south":    # 後ろ。左の列が geo +X
                            p = (x1 - fc * (x1 - x0), y1 - fr * (y1 - y0), z1)
                        elif face == "up":       # 上の行が後ろ
                            p = (x0 + fc * (x1 - x0), y1, z1 - fr * (z1 - z0))
                        else:                    # down。上の行が前
                            p = (x0 + fc * (x1 - x0), y0, z0 + fr * (z1 - z0))
                        col = rec["paint"](face, *p)
                        g = None
                        if isinstance(col[0], tuple):
                            col, g = col
                        if self.tiles:
                            col = self.tiles.apply(col, ax + u, ay + v, c, r, g is not None)
                        base.putpixel((ax + u + c, ay + v + r), tuple(col) + (255,))
                        if g:
                            glow.putpixel((ax + u + c, ay + v + r), tuple(g) + (255,))
        return base, glow

    # ------------------------------------------------------------ 点検
    def coplanar(self):
        """回していない箱どうしで、同じ向きの面が同じ平面で重なる組（ちらつきの元）を数える"""
        flat_bones = set()
        for b in self.bones:
            n, ok = b["name"], True
            while n:
                if self.by[n].get("rotation"):
                    ok = False
                n = self.by[n].get("parent")
            if ok:
                flat_bones.add(b["name"])
        recs = [r for r in self.recs if not r["rot"] and r["bone"] in flat_bones]
        hits = []
        for i, a in enumerate(recs):
            for b in recs[i + 1:]:
                for ax in range(3):
                    o1, o2 = [k for k in range(3) if k != ax]
                    over = all(min(a["box"][o][1], b["box"][o][1]) - max(a["box"][o][0], b["box"][o][0]) > 1e-6
                               for o in (o1, o2))
                    if not over:
                        continue
                    for side in (0, 1):
                        if abs(a["box"][ax][side] - b["box"][ax][side]) < 1e-6:
                            hits.append((a["bone"], b["bone"], "xyz"[ax], a["box"][ax][side]))
        return hits

    # ------------------------------------------------------------ 書き出し
    def geo(self, total_blocks, height_blocks):
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.name}", "texture_width": self.size,
                            "texture_height": self.size,
                            "visible_bounds_width": round(total_blocks * U / 16 + 4, 1),
                            "visible_bounds_height": round(height_blocks * U / 16 + 2, 1),
                            "visible_bounds_offset": [0, 0, 0]},
            "bones": self.bones}]}

    def write(self, anims, contract, total_blocks, height_blocks):
        if hasattr(sys.stdout, 'reconfigure'):
            sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        self.pack()
        geo = self.geo(total_blocks, height_blocks)
        anims = _clean(anims)
        print(boss_kit.verify(self.name, geo, anims))
        hits = self.coplanar()
        print(f"同じ平面で重なる面: {len(hits)} 組" + ("" if not hits else " " + str(hits[:10])))
        cubes = len(self.recs)
        assert cubes <= self.cube_limit, f"cube が多すぎる: {cubes}"
        for name in ["head"] + [f"seg_{k:02d}" for k in range(1, self.joints)]:
            b = self.by[name]
            assert "parent" not in b and "rotation" not in b, name
        for d in (GEO, ANIM, TEX):
            d.mkdir(parents=True, exist_ok=True)
        (GEO / f"{self.name}.geo.json").write_text(json.dumps(_clean(geo), indent=1), encoding='utf-8')
        (ANIM / f"{self.name}.animation.json").write_text(
            json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1), encoding='utf-8')
        base, glow = self.paint()
        for fname, img in ((f"{self.name}_geo.png", base), (f"{self.name}_geo_glow.png", glow)):
            if (LOCKS / fname).exists():
                shutil.copyfile(LOCKS / fname, TEX / fname)
                print(f"  {fname}: texture_locks のものを書き出した（UV を変えたら描き直しが要る）")
            else:
                img.save(TEX / fname)
        zs = [r["box"][2] for r in self.recs]
        full = {
            "entity": self.name,
            "generator": f"tools/boss/{self.name}_model/build.py（手で直さず、生成元を直して流し直す）",
            "geo": f"assets/abyssia/geo/entity/{self.name}.geo.json",
            "animation": f"assets/abyssia/animations/entity/{self.name}.animation.json",
            "texture": f"assets/abyssia/textures/entity/boss/{self.name}_geo.png",
            "glow_texture": f"assets/abyssia/textures/entity/boss/{self.name}_geo_glow.png",
            "texture_size": [self.size, self.size], "texture_used_rows": self.used_rows,
            "texel_blocks": TEXEL / U, "model_scale": MODEL_SCALE,
            "units": "geo の 1 単位 = 1/16 ブロック。描画で MODEL_SCALE 倍する",
            "segment_length_units": SEG, "segment_length_blocks": JOINT_SPACING,
            "joints": self.joints, "segments": self.joints - 1,
            "rest_pose": "頭から尾まで一直線。関節 j は geo の (0, 0, 16j)。頭は -Z を向く",
            "head_pivot": [0.0, 0.0, 0.0],
            "segment_pivot": "seg_k の pivot = (0, 0, 16 * (k - 0.5))",
            "extent_units": {"front_z": round(min(z[0] for z in zs) * U, 1),
                             "tail_z": round(max(z[1] for z in zs) * U, 1),
                             "note": "回していない箱の範囲（回した鰭の先は含まない）"},
            "bone_rule": "head と seg_01..seg_NN は root 直下で、Java が毎フレーム位置と回転を上書きする。"
                         "それ以外の骨（子）は animation に任せ、Java からは触らない",
        }
        full.update(contract)
        full["animations"] = {k: {"loop": v.get("loop"), "length": v["animation_length"],
                                  "bones": sorted(v["bones"])} for k, v in anims.items()}
        full["cube_count"] = cubes
        full["bones"] = [{"name": b["name"], "parent": b.get("parent"), "pivot": b["pivot"],
                          "cubes": len(b["cubes"])} for b in self.bones]
        (HERE / 'contract.json').write_text(json.dumps(full, indent=1, ensure_ascii=False), encoding='utf-8')
        per = {}
        for r in self.recs:
            n = r["bone"]
            while self.by[n].get("parent"):
                n = self.by[n]["parent"]
            per[n] = per.get(n, 0) + 1
        print(f"cubes: 計 {cubes}（head {per.get('head', 0)} / 体節の最大 {max(v for k, v in per.items() if k != 'head')}）"
              f"、アトラス {self.size}（使用 {self.used_rows} 行）、前端 z {full['extent_units']['front_z']}"
              f"、後端 z {full['extent_units']['tail_z']}")
        return full


def _clean(d):
    if isinstance(d, dict):
        return {k: _clean(v) for k, v in d.items()}
    if isinstance(d, (list, tuple)):
        return [_clean(v) for v in d]
    if isinstance(d, float) and d == 0.0:
        return 0.0
    return d


# ================================================================ 図
def load_geo(name):
    return json.loads((GEO / f"{name}.geo.json").read_text(encoding='utf-8'))


def _bones(geo):
    return geo['minecraft:geometry'][0]['bones']


def shifted(geo, moves):
    """moves = {骨: (dx, dy, dz) 単位}。その骨と子孫の箱と pivot をずらした geo を返す（position の再現）"""
    geo = copy.deepcopy(geo)
    by = {b['name']: b for b in _bones(geo)}

    def under(n, anc):
        while n:
            if n == anc:
                return True
            n = by[n].get('parent')
        return False
    for anc, d in moves.items():
        for b in _bones(geo):
            if under(b['name'], anc):
                b['pivot'] = [p + q for p, q in zip(b['pivot'], d)]
                for c in b.get('cubes', []):
                    c['origin'] = [p + q for p, q in zip(c['origin'], d)]
                    if 'pivot' in c:
                        c['pivot'] = [p + q for p, q in zip(c['pivot'], d)]
    return geo


def rotated(geo, rots):
    """rots = {骨: (rx, ry, rz) 度}。骨の静止回転へ足した geo を返す（rotation の再現）"""
    geo = copy.deepcopy(geo)
    for b in _bones(geo):
        if b['name'] in rots:
            r = b.get('rotation', [0, 0, 0])
            b['rotation'] = [p + q for p, q in zip(r, rots[b['name']])]
    return geo


def turned(geo, yaw, pitch, centre_z):
    """全身を 1 つの親の骨で回した geo（斜めから見るため）"""
    geo = copy.deepcopy(geo)
    for b in _bones(geo):
        if not b.get('parent'):
            b['parent'] = '_world'
    _bones(geo).append({"name": "_world", "pivot": [0, 0, centre_z], "rotation": [pitch, yaw, 0], "cubes": []})
    return geo


def composite(name):
    """地のテクスチャへ発光を重ねた絵（ゲーム内の見え方に近い）を boss_views の形式で返す"""
    base = Image.open(TEX / f"{name}_geo.png").convert('RGBA')
    glow = Image.open(TEX / f"{name}_geo_glow.png").convert('RGBA')
    w, h = base.size
    px, gl = base.load(), glow.load()
    # 発光層は加算で重なる
    return [[tuple(min(255, a + b) for a, b in zip(px[x, y][:3], gl[x, y][:3])) + (px[x, y][3],)
             if gl[x, y][3] else px[x, y] for x in range(w)] for y in range(h)], w


def glow_only(name):
    glow = Image.open(TEX / f"{name}_geo_glow.png").convert('RGBA')
    base = Image.open(TEX / f"{name}_geo.png").convert('RGBA')
    w, h = glow.size
    g, b = glow.load(), base.load()
    # 光らない所は暗い影絵にして、光る所だけが読めるようにする
    return [[g[x, y] if g[x, y][3] else ((18, 20, 26, 255) if b[x, y][3] else (0, 0, 0, 0)) for x in range(w)]
            for y in range(h)], w


def shot(geo, tex, view='LEFT', scale=4.0, bones=None, tmp_name='_shot'):
    """geo（dict）を 1 方向から描いて PIL 画像で返す"""
    import tempfile
    rows_tex, size = tex
    with tempfile.TemporaryDirectory(prefix='gbox_') as d:
        d = pathlib.Path(d)
        (d / f"{tmp_name}.geo.json").write_text(json.dumps(geo), encoding='utf-8')
        faces = boss_views.collect_faces([(tmp_name, 0.0)], d, bones)
    bounds = boss_views.common_bounds(faces)
    _, hi, vi, di, fh, fv, fd = [v for v in boss_views.VIEWS if v[0] == view][0]
    rows, w, h = boss_views.render(faces, rows_tex, hi, vi, di, fh, fv, fd, bounds, size, scale)
    img = Image.new('RGB', (w, h))
    img.putdata([p for row in rows for p in row])
    return img


def sheet(panels, out, bg=(20, 22, 26), pad=10):
    """[(ラベル, 画像), ...] を縦に並べて 1 枚に書く"""
    from PIL import ImageDraw
    W = max(i.width for _, i in panels) + pad * 2
    H = sum(i.height + 18 + pad for _, i in panels) + pad
    canvas = Image.new('RGB', (W, H), bg)
    dr = ImageDraw.Draw(canvas)
    y = pad
    for label, img in panels:
        dr.text((pad, y), label, fill=(150, 168, 140))
        y += 18
        canvas.paste(img, (pad, y))
        y += img.height + pad
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out)
    print('wrote', out, canvas.size)
