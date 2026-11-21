# -*- coding: utf-8 -*-
"""灯翼（lumenwing、GNT01 の 2 番）の GeckoLib モデルを書き出す。

デザイン: inbox/designs/GNT01-design-02b.png。半透明でガラス質の長い体、光る心臓の核、頭から尾まで走る神経索、
前方の 1 対の大きな扇形のひれ（光る葉脈）、尾は光る糸の束。友好、全長 60 ブロック。

    python tools/boss/lumenwing_model/build.py
    python tools/boss/check_animations.py lumenwing
    python tools/boss/lumenwing_model/preview.py

出力（手で直さない）: geo/entity/lumenwing.geo.json、animations/entity/lumenwing.animation.json、
textures/entity/boss/lumenwing_geo.png・lumenwing_geo_glow.png、このフォルダの contract.json。

形は「大きな箱を少数」: 体節ごとに外皮 1 個＋神経索 1 個、ひれは片側 9 枚の板を階段状に並べる。
半透明の箱は名前が `_skin` で終わる Bone にだけ入っている。
"""
import json
import math
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import glasskit as gk  # noqa: E402

NAME = "lumenwing"

# ------------------------------------------------------------------ 素材タイル（ChatGPT のシート。giant_tiles.py 参照）
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import giant_tiles                      # noqa: E402
MATERIALS = [
    ('glass_skin', 'translucent sky-blue glass-like skin with faint lighter vein lines and bubbles', '307cf0 56a8fc 1e52c8 68baff'),
    ('glass_fin', 'translucent azure fin membrane with fine pale ribs fanning out', '3480f0 2868de 56a6fc'),
    ('organ_cord', 'bright cyan glowing cord texture with pale beaded highlights', '70e8ff 80ecff 60d6ff 96f0ff beffff'),
    ('filament', 'soft pale-blue thin filament strands, slightly fibrous', '96d6ff'),
    ('node', 'near-white cyan glowing nodule, smooth with a faint ring', 'e2ffff ecffff'),
]
TILES = giant_tiles.Tiler(NAME, MATERIALS)
MODEL_SCALE = 2.0
SEGS = 30                   # 体節。関節は 31
SEG = 16.0                  # 関節間隔 2 ブロック = 16 単位
SNOUT_Z = -14.0             # 吻端（関節 0 の 1.75 ブロック前）
TAIL_Z = 466.0              # 糸の先。全長 (466 + 14) / 8 = 60 ブロック
D_BODY = 4.0                # 外皮・ひれは 1 テクセル 0.5 ブロック
D_FINE = 2.0                # 頭の中・器官・糸は 0.25 ブロック

M = gk.Model(NAME, tex_size=256, density=D_BODY)

M.tiles = TILES

# ------------------------------------------------------------------ 色（平らな色、陰影は 3 段）
SKIN_LIGHT = (86, 168, 252, 138)
SKIN_MID = (48, 124, 240, 122)
SKIN_DARK = (30, 82, 200, 122)
SKIN_RING = (104, 186, 255, 168)      # 体節の前縁の線
CAP = (48, 124, 240, 70)              # 体節の段差に見える端面
CORD = (112, 232, 255, 255)
NODE = (226, 255, 255, 255)
HEART_CORE = (236, 255, 255, 255)
HEART_HALO = (96, 214, 255, 160)
PLATE = (150, 240, 255, 255)
FIN_MID = (52, 128, 240, 118)
FIN_FAR = (40, 104, 222, 118)          # ひれの外側半分
FIN_EDGE = (86, 166, 252, 150)
VEIN = (128, 236, 255, 255)
DOT = (190, 255, 255, 255)
FIL = (150, 214, 255, 255)
FIL_GLOW = (84, 160, 206, 255)


def skin(t):
    if t.face in ("north", "south"):
        return (SKIN_RING if t.edge() else CAP), None
    if t.z - t.lo[2] < t_d(t):                       # 体節の前縁の 1 列
        return SKIN_RING, None
    if t.face == "up":
        return SKIN_LIGHT, None
    if t.face == "down":
        return SKIN_DARK, None
    rel = (t.y - t.lo[1]) / (t.hi[1] - t.lo[1])
    return (SKIN_LIGHT if rel > 0.72 else SKIN_DARK if rel < 0.25 else SKIN_MID), None


def t_d(t):
    """その面の Z 方向 1 テクセルぶんの長さ"""
    n = t.w if t.face in ("east", "west") else t.h
    return (t.hi[2] - t.lo[2]) / n


def head_skin(t):
    if t.face == "up":
        return SKIN_LIGHT, None
    if t.face == "down":
        return SKIN_DARK, None
    if t.face == "south":
        return CAP, None
    rel = (t.y - t.lo[1]) / (t.hi[1] - t.lo[1])
    return (SKIN_LIGHT if rel > 0.72 else SKIN_DARK if rel < 0.25 else SKIN_MID), None


def cord(t):
    c = NODE if abs(t.z - t.tag) < 2.0 else CORD     # 体節の中央に節
    return c, c


def bright(color):
    return lambda t: (color, color)


def heart_core(t):
    c = CORD if t.edge() else HEART_CORE
    return c, c


def heart_halo(t):
    return HEART_HALO, ((70, 170, 220, 255) if not t.edge() else None)


def strand(t):
    return FIL, FIL_GLOW


# ------------------------------------------------------------------ 体の断面（体節 k → 幅, 高さ）
def body_size(k):
    table = {2: (68, 56), 3: (68, 56), 4: (62, 52), 5: (58, 48), 6: (60, 52), 7: (60, 52), 8: (58, 48)}
    if k in table:
        return table[k]
    if k <= 22:
        f = (k - 8) / 14.0
        return (round(58 + (18 - 58) * f), round(48 + (18 - 48) * f))
    return {23: (12, 12), 24: (10, 10)}[k]


SKIN_LAST = 24              # 外皮のある最後の体節。その後ろは糸だけ
HEAD_PARTS = ((-14.0, -8.0, 44, 34), (-8.0, 0.0, 56, 46), (0.0, 16.0, 64, 54))   # (z0, z1, 幅, 高さ)


def skin_box(bone, z0, z1, w, h, prev, nxt, mat, k):
    """外皮の箱。隣より大きい側の端面だけ張る（中に隠れる端面は張らない）。関節で曲げても隙間が出ないよう前後へ少し伸ばす"""
    hide = set()
    if prev and prev[0] >= w and prev[1] >= h:
        hide.add("north")
    if nxt and nxt[0] >= w and nxt[1] >= h:
        hide.add("south")
    j = 0.3 if k % 2 == 0 else 0.0                  # 同じ太さが続く所の面の重なり（ちらつき）を避ける
    ext = max(1.0, 0.04 * w)
    M.box(bone, (-w / 2 - j, -h / 2 - j, z0 - (ext if prev else 0)), (w / 2 + j, h / 2 + j, z1 + (ext if nxt else 0)),
          mat, hide=hide)


def build_head():
    M.bone("head", (0, 0, 0))
    # 顔の板（歯なし、光る帯）と目。外皮より先に描く不透明の箱
    M.box("head", (-18, -12, -13), (18, -6, -10.5), bright(PLATE), d=D_FINE)
    M.box("head", (-28.4, 2, -6), (28.4, 8, 0), eyes, d=D_FINE)
    M.box("head", (-2, -2, -4), (2, 2, 16), cord, d=D_FINE, tag=6.0)
    M.bone("head_skin", (0, 0, 0), parent="head")
    sizes = [(w, h) for _, _, w, h in HEAD_PARTS] + [body_size(2)]
    for i, (z0, z1, w, h) in enumerate(HEAD_PARTS):
        prev = sizes[i - 1] if i else None
        M.box("head_skin", (-w / 2, -h / 2, z0), (w / 2, h / 2, z1 + (1.0 if i == 2 else 0)), head_skin,
              hide={"south"} if sizes[i + 1][0] >= w else set())


def eyes(t):
    if t.face in ("east", "west"):
        return (DOT if not t.edge() else CORD), (DOT if not t.edge() else CORD)
    return CORD, None


def build_body():
    M.bone("seg_01", (0, 0, SEG * 0.5))             # 頭と一体（箱なし）
    for k in range(2, SEGS + 1):
        z0, z1 = SEG * (k - 1), SEG * k
        seg = M.bone(f"seg_{k:02d}", (0, 0, SEG * (k - 0.5)))
        if k <= SKIN_LAST:
            w, h = body_size(k)
            M.box(seg, (-2, -2, z0 - 0.5), (2, 2, z1 + 0.5), cord, d=D_FINE, tag=(z0 + z1) / 2,
                  hide={"north", "south"})
            if 8 <= k <= 20 and k % 2 == 0:         # 神経索から上下へ出る短い血管
                up = 1 if k % 4 == 0 else -1
                length = round(h * 0.32)
                M.box(seg, (-1, 2 * up, (z0 + z1) / 2 - 1), (1, (2 + length) * up, (z0 + z1) / 2 + 1), bright(VEIN),
                      d=D_FINE)
            if k == 6:
                build_heart(seg)
            sk = M.bone(f"seg_{k:02d}_skin", (0, 0, SEG * (k - 0.5)), parent=seg)
            prev = body_size(k - 1) if k > 2 else HEAD_PARTS[-1][2:]
            nxt = body_size(k + 1) if k < SKIN_LAST else None
            skin_box(sk, z0, z1, w, h, prev, nxt, skin, k)
            if k == 6:
                build_fins(seg)
        build_strands(seg, k, z0, z1)


def build_heart(seg):
    """心臓の核（seg_06）。heart を scale で脈打たせる。外側の暈は半透明"""
    M.box(seg, (-28, -1.5, 86.5), (28, 1.5, 89.5), bright(VEIN), d=D_FINE)      # 心臓からひれの付け根へ行く血管
    M.bone("heart", (0, 0, 88), parent=seg)
    M.box("heart", (-10, -10, 80), (10, 10, 96), heart_core, d=D_FINE)
    M.bone("heart_skin", (0, 0, 88), parent="heart")
    M.box("heart_skin", (-17, -17, 76), (17, 17, 100), heart_halo, d=D_FINE)


# ------------------------------------------------------------------ 尾の糸（5 本、体節ごとの細い箱の列）
#            (先の z, 広がる向き x, y)
STRANDS = ((TAIL_Z, 0.0, 0.0), (452.0, 1.0, 0.7), (440.0, -1.0, 0.8), (446.0, 0.9, -0.8), (430.0, -0.8, -0.9))
STRAND_Z0 = 352.0           # 糸が体から出る所（seg_23 の前端）
STRAND_SPREAD = 15.0


def build_strands(seg, k, z0, z1):
    for tip_z, dx, dy in STRANDS:
        a, b = max(z0, STRAND_Z0), min(z1, tip_z)
        if b - a < 1.0:
            continue
        f = ((a + b) / 2 - STRAND_Z0) / (TAIL_Z - STRAND_Z0)
        x, y = round(dx * STRAND_SPREAD * f), round(dy * STRAND_SPREAD * f)
        last = tip_z <= z1
        M.box(seg, (x - 1, y - 1, a), (x + 1, y + 1, b - (3 if last else 0)), strand, d=D_FINE,
              hide={"north", "south"})
        if last:                                    # 糸の先の光る粒
            M.box(seg, (x - 2, y - 2, b - 4), (x + 2, y + 2, b), bright(DOT), d=D_FINE)


# ------------------------------------------------------------------ ひれ（片側 9 枚の板。左で定義して右は鏡写し）
FIN_Y = (-2.0, 0.0)
FIN_X = (16, 40, 64, 88, 112, 136, 160, 184, 208, 228)       # 板の境目（片側 9 枚、3 枚ずつ 3 節）
FIN_LEAD = ((16, 60), (52, 40), (100, 52), (148, 104), (196, 184), (228, 260))      # 前縁 (x, z)
FIN_TRAIL = ((16, 128), (52, 168), (100, 212), (148, 252), (196, 288), (228, 308))  # 後縁


def _lerp(table, x):
    for (x0, v0), (x1, v1) in zip(table, table[1:]):
        if x <= x1:
            return v0 + (v1 - v0) * (x - x0) / (x1 - x0)
    return table[-1][1]


def _panels():
    out = []
    for i, (x0, x1) in enumerate(zip(FIN_X, FIN_X[1:])):
        xm = (x0 + x1) / 2
        z0, z1 = (4 * round(_lerp(t, xm) / 4) for t in (FIN_LEAD, FIN_TRAIL))
        out.append((i // 3, x0, x1, z0, z1))
    return tuple(out)


FIN_PANELS = _panels()      # (Bone の添字, x0, x1, z0, z1)
FIN_BONES = (("fin_{s}_skin", (16, -1, 88), -10.0), ("fin_{s}_mid_skin", (88, -1, 130), 0.0),
             ("fin_{s}_tip_skin", (160, -1, 200), 8.0))          # (名前, pivot, 静止の rz。左の値。負で先が上がる)
VEINS = (((16, 88), (70, 96), (120, 132), (170, 188), (222, 290)),
         ((70, 96), (92, 66), (116, 70), (150, 108)),
         ((48, 92), (74, 136), (86, 164)),
         ((120, 132), (138, 184), (158, 232)),
         ((170, 188), (186, 236), (204, 286)),
         ((96, 114), (132, 104), (160, 128)))


def _cells():
    cells, veins = set(), set()
    for _, x0, x1, z0, z1 in FIN_PANELS:
        for ix in range(int(x0 // D_BODY), int(x1 // D_BODY)):
            for iz in range(int(z0 // D_BODY), int(z1 // D_BODY)):
                cells.add((ix, iz))
    for line in VEINS:
        for (xa, za), (xb, zb) in zip(line, line[1:]):
            n = int(max(abs(xb - xa), abs(zb - za)))
            for i in range(n + 1):
                c = (int((xa + (xb - xa) * i / n) // D_BODY), int((za + (zb - za) * i / n) // D_BODY))
                if c in cells:
                    veins.add(c)
    edge = {c for c in cells if any((c[0] + a, c[1] + b) not in cells for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    # 後縁に並ぶ光る粒
    dots = {c for c in edge if (c[0], c[1] + 1) not in cells and c[0] % 5 == 2}
    dots |= {c for c in edge if (c[0] + 1, c[1]) not in cells and (c[0], c[1] + 1) not in cells}
    return cells, veins, edge, dots


FIN_CELLS, FIN_VEINS, FIN_EDGES, FIN_DOTS = _cells()


def fin(t):
    if t.face not in ("up", "down"):
        return FIN_EDGE, None
    c = (int(abs(t.x) // D_BODY), int(t.z // D_BODY))
    if c in FIN_DOTS:
        return DOT, DOT
    if c in FIN_VEINS:
        return VEIN, VEIN
    if c in FIN_EDGES:
        return FIN_EDGE, None
    return (FIN_MID if abs(t.x) < 112 else FIN_FAR), None


def build_fins(seg):
    for sx, s in ((1, "l"), (-1, "r")):
        names, parent = [], seg
        for fmt, (px, py, pz), rz in FIN_BONES:
            parent = M.bone(fmt.format(s=s), (sx * px, py, pz), parent=parent, rot=(0, 0, sx * rz))
            names.append(parent)
        for idx, x0, x1, z0, z1 in FIN_PANELS:
            M.box(names[idx], (sx * x0, FIN_Y[0], z0), (sx * x1, FIN_Y[1], z1), fin)


# ------------------------------------------------------------------ アニメーション
IDLE = 8.0


def animations():
    bones = {}
    for sx, s in ((1, "l"), (-1, "r")):
        # 左は rz 負で先が上がる。付け根 → 中 → 先へ波が遅れて伝わる
        bones[f"fin_{s}_skin"] = {"rotation": gk.sine(IDLE, -sx * 9.0, phase=0.0)}
        bones[f"fin_{s}_mid_skin"] = {"rotation": gk.sine(IDLE, -sx * 13.0, phase=0.9)}
        bones[f"fin_{s}_tip_skin"] = {"rotation": gk.sine(IDLE, -sx * 17.0, phase=1.8)}
    beats = []
    for b in range(4):                               # 心臓は 2 秒に 1 回脈打つ
        t0 = b * 2.0
        beats += [(t0, (1, 1, 1)), (t0 + 0.25, (1.18, 1.18, 1.18)), (t0 + 0.6, (1, 1, 1))]
    beats.append((IDLE, (1, 1, 1)))
    bones["heart"] = {"scale": gk.track(beats)}
    return {"idle": gk.anim(IDLE, True, bones)}


def contract(counts):
    skins = [n for n in M.order if M.is_skin(n)]
    return {
        "entity": NAME,
        "generator": "tools/boss/lumenwing_model/build.py（手で直さず、生成元を直して流し直す）",
        "geo": f"assets/abyssia/geo/entity/{NAME}.geo.json",
        "animation": f"assets/abyssia/animations/entity/{NAME}.animation.json",
        "texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo.png",
        "texture_glow": f"assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png",
        "texture_size": [M.size, M.size],
        "texel": "外皮・ひれは 1 テクセル 0.5 ブロック、器官・糸・目は 0.25 ブロック",
        "model_scale": MODEL_SCALE,
        "segment_length_units": SEG, "segment_length_blocks": 2.0,
        "joints": SEGS + 1, "segments": SEGS,
        "rest_pose": "頭から尾まで一直線。関節 j は geo の (0, 0, 16j)。頭は -Z を向く。体の芯は y = 0",
        "head_pivot": [0.0, 0.0, 0.0],
        "segment_pivot": "seg_k の pivot = (0, 0, 16 * (k - 0.5))",
        "extent_units": {"snout_z": SNOUT_Z, "tail_z": TAIL_Z, "total_length_blocks": (TAIL_Z - SNOUT_Z) / 8,
                         "fin_tip_x": 228.0, "wingspan_blocks": 2 * 228.0 / 8, "max_top_y": 28.3, "min_bottom_y": -28.3},
        "bone_rule": "head と seg_01..seg_30 は root 直下（親なし）。seg_01 は箱なし（頭と一体）。子の Bone は Java から触らない",
        "translucent": {
            "rule": "名前が _skin で終わる Bone の箱だけ半透明（テクスチャのアルファ 40〜65%）。それ以外の Bone の箱は不透明",
            "bones": skins,
            "draw_order": "geo の Bone 順で、各体節は 不透明の中身（神経索・血管・心臓の核・顔の板・目）→ 半透明の外皮 の順に並んでいる。"
                          "外皮が深度を書くと、後から描く発光層の中身が隠れる。①_skin 以外を先に（地＋発光）描き、②_skin を半透明で描く 2 段にするのが確実",
        },
        "glow": "発光層は _geo_glow.png。神経索と節・心臓の核と暈・血管・顔の板・目・ひれの葉脈と後縁の粒・尾の糸と先の粒",
        "children": {
            "head_skin": "頭の外皮（3 箱）",
            "seg_NN_skin": "体節の外皮（seg_02..seg_24、各 1 箱）。seg_25..seg_30 は尾の糸だけ",
            "heart / heart_skin": "seg_06 の子。心臓の核（不透明）と暈（半透明）。idle で heart を scale 1→1.18 に脈打たせる",
            "fin_l_skin → fin_l_mid_skin → fin_l_tip_skin（右は _r_）": "seg_06 の子。pivot は体軸に平行な蝶番。"
            "rotation z で羽ばたく（左は負で先が上がる、右は逆符号）",
        },
        "animations": {"idle": f"{IDLE} 秒ループ。ひれの 3 節が位相を遅らせて波打つ（±9 / ±13 / ±17 度）、心臓が 2 秒ごとに脈打つ"},
        "counts": counts,
    }


def main():
    build_head()
    build_body()
    anims = animations()
    M.write(anims, bounds_w=64.0, bounds_h=16.0)
    counts = M.counts()
    assert 60 <= counts["cubes"] <= 150, counts
    roots = [n for n in M.order if M.bones[n]["parent"] is None]
    assert roots == ["head"] + [f"seg_{k:02d}" for k in range(1, SEGS + 1)], roots
    (HERE / "contract.json").write_text(json.dumps(contract(counts), ensure_ascii=False, indent=1), encoding="utf-8")
    print(NAME, counts, "atlas rows used", M.used_rows, "/", M.size)


if __name__ == "__main__":
    main()
