# -*- coding: utf-8 -*-
"""大きな直方体だけで GeckoLib モデルを組む小さな道具（GNT01 のバニラ風の巨大生物用）。

`tools/boss/boss_kit.py` は細かい板で曲面を張る道具なので使わない（共通の道具は変えない約束なので、
この種のフォルダに置く。`crestfin_model/cube_kit.py` は同じ中身の複製）。

- 箱は geo 座標（1 単位 = 1/16、MODEL_SCALE 2 なので 1/8 ブロック）で `box(bone, x0, x1, y0, y1, z0, z1, paint)`
- テクスチャは面ごとの UV。1 テクセル = `TEXEL` 単位（既定 2 = 0.25 ブロック）の粗いドット絵
- 塗りは関数 `paint(f)`。`f` は面の名前と、そのテクセルの中心の geo 座標（箱を回す前）を持つ。
  返すのは色 `0xRRGGBB`、発光つきなら `(色, 発光色)`、抜くなら `None`
- 同じ絵になった面はアトラスの同じ場所を共有する（体節の繰り返しで小さく収まる）
"""
import copy
import json
import math
import pathlib

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[3]
ASSETS = ROOT / 'src/main/resources/assets/abyssia'
GEO = ASSETS / 'geo/entity'
ANIM = ASSETS / 'animations/entity'
TEX = ASSETS / 'textures/entity/boss'

U = 8.0            # 1 ブロック = 8 単位（MODEL_SCALE 2）
SEG = 16.0         # 関節の間隔（2 ブロック）
TEXEL = 2.0        # 1 テクセル = 2 単位 = 0.25 ブロック

# boss_views.FACE_CORNERS と同じ（GeckoLib 4.9.2 の面の向き）。隅の順は 右上・左上・左下・右下、x は反転後
FACE_CORNERS = {
    "west": ((0, 1, 1), (0, 1, 0), (0, 0, 0), (0, 0, 1)),
    "east": ((1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)),
    "north": ((0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)),
    "south": ((1, 1, 1), (0, 1, 1), (0, 0, 1), (1, 0, 1)),
    "up": ((0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)),
    "down": ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)),
}


class Texel:
    __slots__ = ("face", "x", "y", "z", "c", "r", "fw", "fh", "b", "tag")


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


class Model:
    tiles = None     # giant_tiles.Tiler（無ければ平らな色のまま）

    def __init__(self, name, size=256, texel=TEXEL):
        self.name = name
        self.size = size
        self.texel = texel
        self.bones = []
        self.by_name = {}
        self.px = [[(0, 0, 0, 0)] * size for _ in range(size)]
        self.gl = [[(0, 0, 0, 0)] * size for _ in range(size)]
        self._shelf_y, self._shelf_h, self._shelf_x = 0, 0, 0
        self._seen = {}

    # ---------------------------------------------------------------- 骨
    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        assert name not in self.by_name, name
        b = {"name": name}
        if parent:
            assert parent in self.by_name, parent
            b["parent"] = parent
        b["pivot"] = [float(v) for v in pivot]
        if rotation:
            b["rotation"] = [float(v) for v in rotation]
        b["cubes"] = []
        self.bones.append(b)
        self.by_name[name] = b
        return name

    # ---------------------------------------------------------------- 箱
    def box(self, bone, x0, x1, y0, y1, z0, z1, paint, rot=None, pivot=None, tag=None):
        assert x1 > x0 and y1 > y0 and z1 > z0, (bone, x0, x1, y0, y1, z0, z1)
        w, h, d = x1 - x0, y1 - y0, z1 - z0
        t = self.texel
        dims = {"west": (d, h), "east": (d, h), "north": (w, h), "south": (w, h), "up": (w, d), "down": (w, d)}
        uv = {}
        for face, corners in FACE_CORNERS.items():
            fw = max(1, int(round(dims[face][0] / t)))
            fh = max(1, int(round(dims[face][1] / t)))
            c0, c1, c2, _ = corners
            cols, glow = [], []
            for r in range(fh):
                for c in range(fw):
                    fu, fv = (c + 0.5) / fw, (r + 0.5) / fh
                    q = [c1[i] + fu * (c0[i] - c1[i]) + fv * (c2[i] - c1[i]) for i in range(3)]
                    f = Texel()
                    f.face, f.c, f.r, f.fw, f.fh, f.tag = face, c, r, fw, fh, tag
                    f.x, f.y, f.z = x1 - q[0] * w, y0 + q[1] * h, z0 + q[2] * d
                    f.b = (x0, x1, y0, y1, z0, z1)
                    v = paint(f)
                    g = None
                    if isinstance(v, tuple):
                        v, g = v
                    cols.append(None if v is None else rgb(v) + (255,))
                    glow.append(None if g is None else rgb(g) + (255,))
            u0, v0 = self._place(fw, fh, tuple(cols), tuple(glow))
            uv[face] = {"uv": [u0, v0], "uv_size": [fw, fh]}
        cube = {"origin": [float(x0), float(y0), float(z0)], "size": [float(w), float(h), float(d)], "uv": uv}
        if rot:
            cube["pivot"] = [float(v) for v in (pivot or ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2))]
            cube["rotation"] = [float(v) for v in rot]
        self.by_name[bone]["cubes"].append(cube)
        return cube

    def _place(self, fw, fh, cols, glow):
        key = (fw, fh, cols, glow)
        if key in self._seen:
            return self._seen[key]
        if self._shelf_x + fw > self.size:
            self._shelf_y += self._shelf_h
            self._shelf_x, self._shelf_h = 0, 0
        assert self._shelf_y + fh <= self.size, f"{self.name}: アトラス {self.size} に収まらない"
        u0, v0 = self._shelf_x, self._shelf_y
        for i, (c, g) in enumerate(zip(cols, glow)):
            x, y = u0 + i % fw, v0 + i // fw
            if c is not None:
                if self.tiles:
                    c = self.tiles.apply(c, u0, v0, i % fw, i // fw, g is not None)
                self.px[y][x] = c
            if g is not None:
                self.gl[y][x] = g
        self._shelf_x += fw
        self._shelf_h = max(self._shelf_h, fh)
        self._seen[key] = (u0, v0)
        return u0, v0

    # ---------------------------------------------------------------- 書き出し
    def cube_count(self):
        return sum(len(b["cubes"]) for b in self.bones)

    def geo(self, bounds_w, bounds_h):
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {"identifier": f"geometry.{self.name}", "texture_width": self.size,
                                "texture_height": self.size, "visible_bounds_width": bounds_w,
                                "visible_bounds_height": bounds_h, "visible_bounds_offset": [0, 0, 0]},
                "bones": self.bones,
            }],
        }

    def write(self, anims, bounds_w, bounds_h, glow=True):
        geo = self.geo(bounds_w, bounds_h)
        verify(self.name, geo, anims)
        for d in (GEO, ANIM, TEX):
            d.mkdir(parents=True, exist_ok=True)
        (GEO / f"{self.name}.geo.json").write_text(json.dumps(geo, indent=1), encoding='utf-8')
        (ANIM / f"{self.name}.animation.json").write_text(
            json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1), encoding='utf-8')
        save_png(TEX / f"{self.name}_geo.png", self.px)
        if glow:
            save_png(TEX / f"{self.name}_geo_glow.png", self.gl)
        return geo


def save_png(path, rows):
    h, w = len(rows), len(rows[0])
    im = Image.new('RGBA', (w, h))
    im.putdata([p for row in rows for p in row])
    im.save(path)


def load_png(path):
    im = Image.open(path).convert('RGBA')
    w, h = im.size
    data = list(im.getdata())
    return [data[y * w:(y + 1) * w] for y in range(h)]


def verify(name, geo, anims):
    """ボーン名の重複・親の欠け・animation の未知のボーン・時刻の逆順と尺超えを落とす"""
    bones = geo["minecraft:geometry"][0]["bones"]
    seen = set()
    for b in bones:
        assert b["name"] not in seen, f"{name}: ボーン名 {b['name']} が重複"
        if b.get("parent"):
            assert b["parent"] in seen, f"{name}: 親 {b['parent']} が先に無い"
        seen.add(b["name"])
    for an, a in anims.items():
        for bone, tracks in a["bones"].items():
            assert bone in seen, f"{name}/{an}: 未知のボーン {bone}"
            for kind, keys in tracks.items():
                times = [float(t) for t in keys]
                assert times == sorted(times), f"{name}/{an}/{bone}/{kind}: 時刻が逆順"
                assert times[-1] <= a["animation_length"] + 1e-6, f"{name}/{an}/{bone}/{kind}: 尺超え"


# ==================================================================== アニメーション
def keys(frames):
    """[(時刻, (x, y, z)) または (時刻, (x, y, z), easing)] → GeckoLib のキー"""
    out = {}
    for f in frames:
        vec = [round(v, 2) for v in f[1]]
        out[f"{f[0]:.2f}"] = vec if len(f) < 3 else {"vector": vec, "easing": f[2]}
    return out


def loop_keys(length, fn, steps=12):
    """周期関数 fn(位相 0..1) を steps 等分のキーにする（最初と最後が同じ値で、継ぎ目なく回る）"""
    return keys([(round(length * i / steps, 2), fn(i / steps)) for i in range(steps + 1)])


def swim_tracks(length, seg_count, amp, wavelength, steps=12, lead=0.0):
    """体節の子の骨 `body_XX` を横へ流す進行波。amp(k) は体節 k の振幅（単位）、wavelength は体節数。

    横ずれ x = A sin(2π(t/T - k/λ))、向きは波の傾きに合わせる（rotation y = atan(dx/dz)）ので、
    箱の列が折れ線の蛇行になる。波は頭から尾へ進む。
    """
    out = {}
    for k in range(1, seg_count + 1):
        a = amp(k)
        if a <= 0:
            continue
        # 中央差分で傾きを出す（振幅が体節ごとに変わる所でも隣とつながる）
        def x_at(kk, ph, a_of=amp):
            return a_of(min(max(kk, 1), seg_count)) * math.sin(2 * math.pi * (ph - kk / wavelength) + lead)
        def p(ph, k=k):
            return (x_at(k, ph), 0.0, 0.0)
        def r(ph, k=k):
            slope = (x_at(k + 0.5, ph) - x_at(k - 0.5, ph)) / SEG
            return (0.0, math.degrees(math.atan(slope)), 0.0)
        out[f"body_{k:02d}"] = {"position": loop_keys(length, p, steps), "rotation": loop_keys(length, r, steps)}
    return out


# ==================================================================== 図のための姿勢
def sample(track, t):
    """キーの辞書を時刻 t で線形に読む（easing は見ない。図の確認用）"""
    ks = sorted((float(k), (v["vector"] if isinstance(v, dict) else v)) for k, v in track.items())
    if t <= ks[0][0]:
        return ks[0][1]
    for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
        if t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return ks[-1][1]


def posed(geo, anim=None, t=0.0, view=None):
    """animation を時刻 t で焼いた geo の写しを返す（図の確認用。ゲームの資産には使わない）。

    rotation は静止姿勢へ足し、position は骨と子孫を平行移動、scale は pivot を中心に箱を伸縮する
    （scale は回していない箱か、伸縮と可換な回転の箱だけに使うこと）。
    view=(rx, ry, rz) を渡すと全体を 1 つの骨の下へ入れて回す（斜めの図）。
    """
    g = copy.deepcopy(geo)
    bones = g["minecraft:geometry"][0]["bones"]
    by = {b["name"]: b for b in bones}

    def below(name):
        out = [name]
        for b in bones:
            if b.get("parent") == name:
                out += below(b["name"])
        return out

    for name, tracks in (anim or {}).get("bones", {}).items():
        b = by[name]
        if "scale" in tracks:
            s = sample(tracks["scale"], t)
            pv = b["pivot"]
            for n in below(name):
                for c in by[n]["cubes"]:
                    o, sz = c["origin"], c["size"]
                    for i in range(3):
                        lo, hi = pv[i] + (o[i] - pv[i]) * s[i], pv[i] + (o[i] + sz[i] - pv[i]) * s[i]
                        o[i], sz[i] = lo, hi - lo
                    if "pivot" in c:
                        c["pivot"] = [pv[i] + (c["pivot"][i] - pv[i]) * s[i] for i in range(3)]
                if n != name:
                    by[n]["pivot"] = [pv[i] + (by[n]["pivot"][i] - pv[i]) * s[i] for i in range(3)]
        if "rotation" in tracks:
            r = sample(tracks["rotation"], t)
            b["rotation"] = [a + c for a, c in zip(b.get("rotation", [0, 0, 0]), r)]
        if "position" in tracks:
            d = sample(tracks["position"], t)
            for n in below(name):
                by[n]["pivot"] = [a + c for a, c in zip(by[n]["pivot"], d)]
                for c in by[n]["cubes"]:
                    c["origin"] = [a + e for a, e in zip(c["origin"], d)]
                    if "pivot" in c:
                        c["pivot"] = [a + e for a, e in zip(c["pivot"], d)]
    if view:
        zs = [c["origin"][2] for b in bones for c in b["cubes"]] + \
             [c["origin"][2] + c["size"][2] for b in bones for c in b["cubes"]]
        for b in bones:
            b.setdefault("parent", "_view")
        bones.insert(0, {"name": "_view", "pivot": [0.0, 0.0, (min(zs) + max(zs)) / 2],
                         "rotation": list(view), "cubes": []})
    return g
