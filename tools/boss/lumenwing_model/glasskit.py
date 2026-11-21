# -*- coding: utf-8 -*-
"""半透明の巨大生物（GNT01 の灯翼・硝子背）を「大きな箱を少数」で組むための道具。

`lumenwing_model/` と `glassback_model/` に同じものを置いてある（直すときは両方へ）。
共通の `../boss_views.py` は読むだけで、変えない（面の頂点と UV の対応・Bone の回転はそこから借りる）。

<h2>考え方</h2>
- 箱は 1 個ずつ `Model.box(bone, lo, hi, mat)` で置く。面ごとに専用の矩形をアトラスへ詰め、
  1 テクセル = `d` 単位（geo の 1 単位 = 1/8 ブロック。d=4 で 0.5 ブロック、d=2 で 0.25 ブロック）の粗いドット絵にする。
- 絵は「素材関数」が塗る。`mat(c)` はテクセル 1 個ぶんの情報 `c`（面の名前、テクセル中心のモデル座標、
  面の中の位置）を受けて `(地の RGBA, 発光の RGBA か None)` を返す。位置で塗るので、箱をまたいでも模様がつながる。
- 半透明の箱は、名前が `_skin` で終わる Bone にだけ入れる（レンダラーはその Bone を半透明で描く）。
  中の光る器官・背骨・歯は `_skin` でない Bone に入れ、不透明（切り抜き）で先に描く。

<h2>座標</h2>
geo と同じ: -Z が前、+Y が上、+X が左、単位は 1/8 ブロック（MODEL_SCALE 2）。
"""
import json
import math
import pathlib
import sys

import numpy as np
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import boss_views  # noqa: E402  （読むだけ）

ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/abyssia'
GEO = ASSETS / 'geo/entity'
ANIM = ASSETS / 'animations/entity'
TEX = ASSETS / 'textures/entity/boss'

FACES = ("north", "south", "east", "west", "up", "down")


class Texel:
    """素材関数へ渡す、テクセル 1 個ぶんの情報"""
    __slots__ = ("face", "x", "y", "z", "i", "j", "w", "h", "lo", "hi", "tag")

    def edge(self):
        """面の縁のテクセルか"""
        return self.i == 0 or self.j == 0 or self.i == self.w - 1 or self.j == self.h - 1


class Model:
    tiles = None     # giant_tiles.Tiler（無ければ平らな色のまま）

    def __init__(self, name, tex_size=256, density=4.0):
        self.name = name
        self.size = tex_size
        self.density = density
        self.bones = {}
        self.order = []
        self.boxes = []

    # ---------------------------------------------------------------- 骨と箱
    def bone(self, name, pivot, parent=None, rot=None):
        assert name not in self.bones, f"Bone {name} が重複"
        assert parent is None or parent in self.bones, f"親 {parent} が無い"
        self.bones[name] = {"name": name, "pivot": [float(v) for v in pivot], "parent": parent,
                            "rot": rot, "cubes": []}
        self.order.append(name)
        return name

    def box(self, bone, lo, hi, mat, d=None, hide=(), rot=None, pivot=None, tag=None):
        """lo / hi は箱の対角（モデル座標）。hide に入れた面は張らない（透明の 1 点を指す）"""
        lo, hi = (tuple(float(min(a, b)) for a, b in zip(lo, hi)),
                  tuple(float(max(a, b)) for a, b in zip(lo, hi)))
        spec = {"bone": bone, "lo": lo, "hi": hi, "mat": mat, "d": d or self.density, "hide": set(hide),
                "rot": rot, "pivot": pivot, "tag": tag}
        self.bones[bone]["cubes"].append(spec)
        self.boxes.append(spec)
        return spec

    def is_skin(self, bone):
        return bone.endswith("_skin")

    # ---------------------------------------------------------------- アトラス
    def _face_dims(self, spec, face):
        sx, sy, sz = (h - l for l, h in zip(spec["lo"], spec["hi"]))
        a, b = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy),
                "up": (sx, sz), "down": (sx, sz)}[face]
        d = spec["d"]
        return max(1, int(round(a / d))), max(1, int(round(b / d)))

    def _pack(self):
        rects = []
        for spec in self.boxes:
            spec["uv"] = {}
            for face in FACES:
                if face in spec["hide"]:
                    spec["uv"][face] = None
                    continue
                w, h = self._face_dims(spec, face)
                rects.append((h, w, spec, face))
        rects.sort(key=lambda r: (-r[0], -r[1]))
        x, y, shelf = 2, 0, 0           # (0,0)-(2,2) は透明のまま残す（張らない面が指す）
        for h, w, spec, face in rects:
            if x + w > self.size:
                x, y, shelf = 0, y + shelf, 0
            if y == 0 and x < 2:
                x = 2
            assert y + h <= self.size, f"{self.name}: アトラス {self.size} に入らない"
            spec["uv"][face] = (x, y, w, h)
            x += w
            shelf = max(shelf, h)
        self.used_rows = y + shelf

    def _paint(self):
        base = np.zeros((self.size, self.size, 4), dtype=np.uint8)
        glow = np.zeros((self.size, self.size, 4), dtype=np.uint8)
        t = Texel()
        for spec in self.boxes:
            (ox, oy, oz), (hx, hy, hz) = spec["lo"], spec["hi"]
            rx0, rx1 = -hx, -ox               # 描画空間では X が反転する（boss_views と同じ）
            t.lo, t.hi, t.tag = spec["lo"], spec["hi"], spec["tag"]
            for face in FACES:
                r = spec["uv"][face]
                if r is None:
                    continue
                u0, v0, w, h = r
                c0, c1, c2, _ = boss_views.FACE_CORNERS[face]

                def at(c):
                    return (rx0 + (rx1 - rx0) * c[0], oy + (hy - oy) * c[1], oz + (hz - oz) * c[2])
                p0, p1, p2 = at(c0), at(c1), at(c2)
                t.face, t.w, t.h = face, w, h
                for j in range(h):
                    for i in range(w):
                        fu, fv = (i + 0.5) / w, (j + 0.5) / h
                        px = [p1[k] + fu * (p0[k] - p1[k]) + fv * (p2[k] - p1[k]) for k in range(3)]
                        t.x, t.y, t.z, t.i, t.j = -px[0], px[1], px[2], i, j
                        out = spec["mat"](t)
                        if out is None:
                            continue
                        b, g = out
                        if b is not None:
                            if self.tiles:
                                b = self.tiles.apply(b, u0, v0, i, j, g is not None)
                            base[v0 + j, u0 + i] = b
                        if g is not None:
                            glow[v0 + j, u0 + i] = g
        return base, glow

    # ---------------------------------------------------------------- 書き出し
    def geo(self, bounds_w, bounds_h):
        bones = []
        for name in self.order:
            b = self.bones[name]
            out = {"name": name, "pivot": [round(v, 2) for v in b["pivot"]]}
            if b["parent"]:
                out["parent"] = b["parent"]
            if b["rot"] and any(abs(v) > 1e-6 for v in b["rot"]):
                out["rotation"] = [round(v, 2) for v in b["rot"]]
            cubes = []
            for spec in b["cubes"]:
                c = {"origin": [round(v, 2) for v in spec["lo"]],
                     "size": [round(h - l, 2) for l, h in zip(spec["lo"], spec["hi"])],
                     "uv": {}}
                for face in FACES:
                    r = spec["uv"][face]
                    c["uv"][face] = ({"uv": [1, 1], "uv_size": [0, 0]} if r is None
                                     else {"uv": [r[0], r[1]], "uv_size": [r[2], r[3]]})
                if spec["rot"]:
                    c["rotation"] = [round(v, 2) for v in spec["rot"]]
                    c["pivot"] = [round(v, 2) for v in (spec["pivot"] or spec["lo"])]
                cubes.append(c)
            out["cubes"] = cubes
            bones.append(out)
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.name}", "texture_width": self.size,
                            "texture_height": self.size, "visible_bounds_width": bounds_w,
                            "visible_bounds_height": bounds_h, "visible_bounds_offset": [0, 0, 0]},
            "bones": bones}]}

    def write(self, anims, bounds_w, bounds_h):
        self._pack()
        base, glow = self._paint()
        geo = self.geo(bounds_w, bounds_h)
        check(self.name, geo, anims)
        for d in (GEO, ANIM, TEX):
            d.mkdir(parents=True, exist_ok=True)
        (GEO / f"{self.name}.geo.json").write_text(json.dumps(geo, indent=1), encoding="utf-8")
        (ANIM / f"{self.name}.animation.json").write_text(
            json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1), encoding="utf-8")
        Image.fromarray(base, "RGBA").save(TEX / f"{self.name}_geo.png")
        Image.fromarray(glow, "RGBA").save(TEX / f"{self.name}_geo_glow.png")
        return geo

    def counts(self):
        skin = sum(len(b["cubes"]) for n, b in self.bones.items() if self.is_skin(n))
        total = len(self.boxes)
        return {"cubes": total, "translucent_cubes": skin, "opaque_cubes": total - skin, "bones": len(self.bones)}


def check(name, geo, anims):
    """Bone 名の打ち間違い・キーの逆順・尺超えを落とす（boss_kit.verify と同じ検査）"""
    bones = geo["minecraft:geometry"][0]["bones"]
    seen = set()
    for b in bones:
        assert b["name"] not in seen, f"{name}: Bone {b['name']} が重複"
        seen.add(b["name"])
    for b in bones:
        assert not b.get("parent") or b["parent"] in seen, f"{name}: 親 {b['parent']} が無い"
    for an, a in anims.items():
        for bone, tracks in a["bones"].items():
            assert bone in seen, f"{name}/{an}: 未知の Bone {bone}"
            for kind, keys in tracks.items():
                ts = [float(k) for k in keys]
                assert ts == sorted(ts), f"{name}/{an}/{bone}/{kind}: 時刻が逆順"
                assert ts[-1] <= a["animation_length"] + 1e-6, f"{name}/{an}/{bone}/{kind}: 尺を超えた"


# ==================================================================== アニメーション
def track(frames):
    """[(秒, (x, y, z)), ...] → GeckoLib のキー"""
    return {f"{t:.2f}": [round(float(v), 2) + 0.0 for v in vec] for t, vec in frames}


def sine(length, amp, phase=0.0, axis=(0, 0, 1), steps=12, offset=0.0):
    """正弦の往復。phase（ラジアン）を遅らせると波が先へ伝わる"""
    return track([(length * i / steps,
                   tuple(a * (offset + amp * math.sin(2 * math.pi * i / steps - phase)) for a in axis))
                  for i in range(steps + 1)])


def anim(length, loop, bones):
    return {"loop": loop, "animation_length": length, "bones": bones}


def sample(keys, t):
    """キーの線形補間（図に姿勢を出すためだけに使う）"""
    ks = sorted((float(k), v if isinstance(v, list) else v["vector"]) for k, v in keys.items())
    if t <= ks[0][0]:
        return ks[0][1]
    for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 1.0
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return ks[-1][1]


# ==================================================================== 図（半透明を重ねて描く）
BG = (6, 12, 28, 255)


def camera(kind):
    """描画空間（X 反転済み・右手系）での (右, 上, 手前) の単位ベクトル"""
    if kind == "side":                      # 左から。頭が画面の左
        return (0, 0, 1), (0, 1, 0), (-1, 0, 0)
    if kind == "top":                       # 真上から。頭が左
        return (0, 0, 1), (1, 0, 0), (0, 1, 0)
    if kind == "front":
        return (-1, 0, 0), (0, 1, 0), (0, 0, -1)
    if kind == "oblique":                   # 斜め前・左上から
        n = np.array((-0.50, 0.38, -0.78))
    elif kind == "oblique_low":             # 斜め前・左下から（見上げる。デザイン画の向き）
        n = np.array((-0.62, -0.30, -0.72))
    elif kind == "design":                  # デザイン画に近い向き（頭が左手前）
        n = np.array((-0.45, 0.62, -0.64))
    else:
        raise ValueError(kind)
    n = n / np.linalg.norm(n)
    r = np.cross((0, 1, 0), n)
    r = r / np.linalg.norm(r)
    u = np.cross(n, r)
    return tuple(r), tuple(u), tuple(n)


def posed_geo(name, pose):
    """pose = {bone: (rx, ry, rz)} を静止姿勢へ足した geo を返す"""
    geo = json.loads((GEO / f"{name}.geo.json").read_text(encoding="utf-8"))
    for b in geo["minecraft:geometry"][0]["bones"]:
        if b["name"] in pose:
            r = b.get("rotation", [0, 0, 0])
            b["rotation"] = [a + c for a, c in zip(r, pose[b["name"]])]
    return geo


def pose_at(name, anim_name, t):
    doc = json.loads((ANIM / f"{name}.animation.json").read_text(encoding="utf-8"))
    out = {}
    for bone, tracks in doc["animations"][anim_name]["bones"].items():
        if "rotation" in tracks:
            out[bone] = sample(tracks["rotation"], t)
    return out


def _faces(name, geo, tmp, bend=None):
    """boss_views.collect_faces で面を集める。bend = 関節ごとの向き（度）を返す関数なら、体節鎖を曲げる"""
    tmp.mkdir(parents=True, exist_ok=True)
    (tmp / f"{name}.geo.json").write_text(json.dumps(geo), encoding="utf-8")
    if bend is None:
        return boss_views.collect_faces([(name, 0.0)], geo_dir=tmp)
    bones = geo["minecraft:geometry"][0]["bones"]
    by = {b["name"]: b for b in bones}

    def root(n):
        while by[n].get("parent"):
            n = by[n]["parent"]
        return n
    roots = sorted({root(b["name"]) for b in bones})
    nseg = max(int(r[4:]) for r in roots if r.startswith("seg_"))
    joints = [np.array((0.0, 0.0, 0.0))]
    heads = []
    for k in range(1, nseg + 1):
        h = math.radians(bend(k))
        heads.append(h)
        joints.append(joints[-1] + 16.0 * np.array((math.sin(h), 0.0, math.cos(h))))
    faces = []
    for r in roots:
        k = 1 if r == "head" else int(r[4:])
        h = heads[k - 1]
        rest = np.array((0.0, 0.0, 0.0)) if r == "head" else np.array((0.0, 0.0, 16.0 * (k - 0.5)))
        to = joints[0] if r == "head" else (joints[k - 1] + joints[k]) / 2
        ch, sh = math.cos(h), math.sin(h)
        keep = lambda n, r=r: root(n) == r
        for pts, uvs, shade in boss_views.collect_faces([(name, 0.0)], geo_dir=tmp, bones=keep):
            moved = []
            for p in pts:
                x, y, z = np.array(p) - rest
                moved.append((x * ch + z * sh + to[0], y + to[1], -x * sh + z * ch + to[2]))
            faces.append((moved, uvs, shade))
    return faces


def render(name, kind="side", scale=2.5, pose=None, bend=None, tmp=None, glow=True, bg=BG, pad=10):
    """半透明つきの図を 1 枚返す（PIL Image）。奥の面から順に重ねる（painter）"""
    tmp = tmp or (HERE / "out" / "_tmp")
    geo = posed_geo(name, pose or {})
    faces = _faces(name, geo, tmp, bend)
    base = Image.open(TEX / f"{name}_geo.png").convert("RGBA")
    glow_img = Image.open(TEX / f"{name}_geo_glow.png").convert("RGBA") if glow else None
    r, u, n = (np.array(v, dtype=float) for v in camera(kind))
    items = []
    lo, hi = np.array((1e9, 1e9)), np.array((-1e9, -1e9))
    for pts, uvs, shade in faces:
        P = np.array(pts)
        scr = np.stack((P @ r, -(P @ u)), axis=1) * scale
        lo, hi = np.minimum(lo, scr.min(0)), np.maximum(hi, scr.max(0))
        items.append((float((P @ n).mean()), scr, uvs, shade))
    items.sort(key=lambda it: it[0])
    W, H = (int(v) + 2 * pad for v in (hi - lo))
    canvas = Image.new("RGBA", (W, H), bg)
    for _, scr, uvs, shade in items:
        s = scr - lo + pad
        (u1, v1) = uvs[1]
        uw, uh = uvs[0][0] - u1, uvs[2][1] - v1
        if uw <= 0 or uh <= 0:
            continue
        A, B = (s[0] - s[1]) / uw, (s[2] - s[1]) / uh
        det = A[0] * B[1] - A[1] * B[0]
        if abs(det) < 1e-6:
            continue
        x0, y0 = int(math.floor(s[:, 0].min())), int(math.floor(s[:, 1].min()))
        x1, y1 = int(math.ceil(s[:, 0].max())), int(math.ceil(s[:, 1].max()))
        if x1 <= x0 or y1 <= y0:
            continue
        # 出力 (x, y) → 素材 (u, v)。素材は切り出した矩形なので原点は (u1, v1)
        ia, ib, ic, id_ = B[1] / det, -B[0] / det, -A[1] / det, A[0] / det
        ox, oy = x0 + 0.5 - s[1][0], y0 + 0.5 - s[1][1]
        data = (ia, ib, ia * ox + ib * oy, ic, id_, ic * ox + id_ * oy)
        box = (int(u1), int(v1), int(u1 + uw), int(v1 + uh))
        crop = np.array(base.crop(box), dtype=np.float32)
        crop[..., :3] *= shade
        layer = Image.fromarray(crop.astype(np.uint8), "RGBA")
        if glow_img is not None:
            layer = Image.alpha_composite(layer, glow_img.crop(box))
        warped = layer.transform((x1 - x0, y1 - y0), Image.AFFINE, data, resample=Image.NEAREST)
        canvas.alpha_composite(warped, dest=(max(0, x0), max(0, y0)))
    return canvas.convert("RGB")


def stack(images, labels=None, gap=8, bg=BG[:3]):
    """図を縦に並べる"""
    from PIL import ImageDraw
    W = max(im.width for im in images)
    H = sum(im.height for im in images) + gap * (len(images) - 1)
    out = Image.new("RGB", (W, H), bg)
    y = 0
    d = ImageDraw.Draw(out)
    for k, im in enumerate(images):
        out.paste(im, (0, y))
        if labels:
            d.text((6, y + 4), labels[k], fill=(170, 200, 220))
        y += im.height + gap
    return out


def beside(left, right, gap=8, bg=BG[:3]):
    """デザイン画と図を横に並べる（高さは大きい方に合わせ、縮めない）"""
    H = max(left.height, right.height)
    out = Image.new("RGB", (left.width + right.width + gap, H), bg)
    out.paste(left, (0, (H - left.height) // 2))
    out.paste(right, (left.width + gap, (H - right.height) // 2))
    return out
