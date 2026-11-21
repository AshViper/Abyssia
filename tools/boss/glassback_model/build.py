# -*- coding: utf-8 -*-
"""硝子背（glassback、GNT01 の 8 番）の GeckoLib モデルを書き出す。

デザイン: inbox/designs/GNT01-design-sheet.png の 8 番。半透明でガラス質の長い捕食者。中に光る背骨が透け、
歯をむいた長い顎、体に沿って鎌状のひれが並び、尾は大きな二叉のひれ。敵対、全長 50 ブロック。

    python tools/boss/glassback_model/build.py
    python tools/boss/check_animations.py glassback
    python tools/boss/glassback_model/preview.py

出力（手で直さない）: geo/entity/glassback.geo.json、animations/entity/glassback.animation.json、
textures/entity/boss/glassback_geo.png・glassback_geo_glow.png、このフォルダの contract.json。

形は「大きな箱を少数」: 体節ごとに外皮 1 個＋背骨の索 1 個＋椎骨 1 個、ひれは 3 箱の階段で鎌の形にする。
半透明の箱は名前が `_skin` で終わる Bone にだけ入っている。発光（背骨・目）は _glow に分けてあり、
「光を消して潜む／急に光る」の切り替えは Java 側で発光層を出し入れする。
"""
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import glasskit as gk  # noqa: E402

NAME = "glassback"

# ------------------------------------------------------------------ 素材タイル（ChatGPT のシート。giant_tiles.py 参照）
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import giant_tiles                      # noqa: E402
MATERIALS = [
    ('glass_skin', 'cold icy translucent blue glass skin with pale frosty vein lines', '54a0f0 326ed2 8ccefc b0e2ff'),
    ('glass_fin', 'icy translucent pale-blue fin membrane with fine white ribs', '60b4fa aae2ff'),
    ('tooth', 'glassy white-blue tooth enamel with faint facets', 'e4f3fc b0cee8'),
    ('spine', 'glowing teal vertebra bone with pale mint highlights', '40e8de aafff4'),
]
TILES = giant_tiles.Tiler(NAME, MATERIALS)
MODEL_SCALE = 2.0
SEGS = 25                   # 体節。関節は 26
SEG = 16.0
SNOUT_Z = -40.0             # 吻端（関節 0 の 5 ブロック前）
TAIL_Z = 360.0              # 尾びれの先。全長 (360 + 40) / 8 = 50 ブロック
D_BODY = 4.0                # 外皮・ひれは 1 テクセル 0.5 ブロック
D_FINE = 2.0                # 頭・顎・歯・背骨は 0.25 ブロック

M = gk.Model(NAME, tex_size=256, density=D_BODY)

M.tiles = TILES

# ------------------------------------------------------------------ 色（灯翼より白く冷たいガラス。陰影 3 段）
SKIN_LIGHT = (140, 206, 252, 135)
SKIN_MID = (84, 160, 240, 118)
SKIN_DARK = (50, 110, 210, 118)
SKIN_RING = (176, 226, 255, 170)
CAP = (84, 160, 240, 64)
FIN = (96, 180, 250, 120)
FIN_RIM = (170, 226, 255, 175)
SPINE = (64, 232, 222, 255)
VERT = (170, 255, 244, 255)
EYE = (120, 255, 240, 255)
TOOTH = (228, 243, 252, 255)
TOOTH_SHADE = (176, 206, 232, 255)


def _tone(t):
    rel = (t.y - t.lo[1]) / (t.hi[1] - t.lo[1])
    return SKIN_LIGHT if rel > 0.72 else SKIN_DARK if rel < 0.25 else SKIN_MID


def skin(t):
    if t.face in ("north", "south"):
        return (SKIN_RING if t.edge() else CAP), None
    n = t.w if t.face in ("east", "west") else t.h
    if t.z - t.lo[2] < (t.hi[2] - t.lo[2]) / n:      # 体節の前縁の 1 列（ガラスの継ぎ目）
        return SKIN_RING, None
    if t.face == "up":
        return SKIN_LIGHT, None
    if t.face == "down":
        return SKIN_DARK, None
    return _tone(t), None


def head_skin(t):
    if t.face == "up":
        return (SKIN_RING if t.edge() else SKIN_LIGHT), None
    if t.face == "down":
        return SKIN_DARK, None
    if t.face in ("north", "south"):
        return (SKIN_RING if t.edge() else SKIN_MID), None
    return (SKIN_RING if t.j == 0 else _tone(t)), None


def fin(t):
    if t.face in ("east", "west"):                   # 前縁の 1 列と外側の 1 段だけ明るい縁
        outer = t.j == (0 if t.tag > 0 else t.h - 1)
        return (FIN_RIM if (t.z - t.lo[2] < D_BODY or outer) else FIN), None
    return FIN_RIM, None


def spine(t):
    return SPINE, SPINE


def vertebra(t):
    c = SPINE if (t.face in ("east", "west") and t.edge() and t.w > 1 and t.h > 2) else VERT
    return c, c


def eyes(t):
    if t.face in ("east", "west"):
        return EYE, EYE
    return SPINE, None


def teeth(gum_top):
    """歯の列の板（切り抜き）。3 列ごとに 1 本、根元が 2 列ぶん太い牙"""
    def mat(t):
        if t.face not in ("east", "west"):
            return None
        col = int((t.z - t.lo[2]) // D_FINE) % 3
        row = t.j if gum_top else t.h - 1 - t.j      # 歯茎からの段
        if col == 0:
            return (TOOTH if row < t.h - 1 else TOOTH_SHADE), None
        if col == 1 and row == 0:
            return TOOTH_SHADE, None
        return None
    return mat


def fang(t):
    return (TOOTH_SHADE if t.face in ("down", "south") else TOOTH), None


# ------------------------------------------------------------------ 体の断面（体節 k → 幅, 高さ）
SKIN_LAST = 21


def body_size(k):
    if k == 2:
        return (26, 38)
    if k <= 7:
        return (26, 40)
    if k <= 19:
        f = (k - 7) / 12.0
        return (round(26 + (10 - 26) * f), round(40 + (14 - 40) * f))
    return (8, 10)


SPINE_Y = 3.0
JAW_HINGE = (0.0, -4.0, 12.0)
JAW_REST = 8.0
JAW_OPEN = 34.0             # attack / jaw_out で足す角度（静止の 8 度に加わる）


def build_head():
    M.bone("head", (0, 0, 0))
    M.box("head", (-1, SPINE_Y - 1, 4), (1, SPINE_Y + 1, 16.5), spine, d=D_FINE, hide={"south"})
    for sx in (1, -1):                               # 目、上顎の歯の列と前の牙
        M.box("head", (sx * 10.5, 10, -5), (sx * 12.3, 14, -1), eyes, d=D_FINE)
        M.box("head", (sx * 7, -8, -38), (sx * 8, 0, -8), teeth(True), d=D_FINE,
              hide={"north", "south", "up", "down"})
        M.box("head", (sx * 2, -9, -40), (sx * 5, 0, -38), fang, d=D_FINE)
    M.bone("head_skin", (0, 0, 0), parent="head")
    M.box("head_skin", (-8, 0, -40), (8, 9, -24), head_skin, d=D_FINE, hide={"south"})
    M.box("head_skin", (-10, 0, -24), (10, 14, -8), head_skin, d=D_FINE, hide={"south"})
    M.box("head_skin", (-12, -8, -8), (12, 20, 17), head_skin, d=D_FINE, hide={"south"})

    # 下顎。pivot は顎関節。rotation x が正で開く。静止で 8 度開いて歯をむく
    M.bone("jaw", JAW_HINGE, parent="head", rot=(JAW_REST, 0, 0))
    for sx in (1, -1):
        M.box("jaw", (sx * 5.5, -5, -37), (sx * 6.5, 3, -9), teeth(False), d=D_FINE,
              hide={"north", "south", "up", "down"})
        M.box("jaw", (sx * 1.5, -5, -39), (sx * 4, 4, -37), fang, d=D_FINE)
    M.bone("jaw_skin", JAW_HINGE, parent="jaw")
    M.box("jaw_skin", (-7.5, -11, -39), (7.5, -5, -8), head_skin, d=D_FINE, hide={"south"})
    M.box("jaw_skin", (-11, -18, -8), (11, -5, 14), head_skin, d=D_FINE)


def build_body():
    M.bone("seg_01", (0, 0, SEG * 0.5))              # 頭と一体（箱なし）
    for k in range(2, SEGS + 1):
        z0, z1 = SEG * (k - 1), SEG * k
        zm = (z0 + z1) / 2
        seg = M.bone(f"seg_{k:02d}", (0, 0, zm))
        if k > SKIN_LAST:
            continue                                 # seg_22..25 は箱なし（尾びれは seg_21 の子）
        w, h = body_size(k)
        vh = max(4, 2 * round(h * 0.2))              # 椎骨の高さ
        M.box(seg, (-1, SPINE_Y - 1, z0 - 0.5), (1, SPINE_Y + 1, z1 + 0.5), spine, d=D_FINE, hide={"north", "south"})
        M.box(seg, (-2, SPINE_Y - vh / 2, zm - 2), (2, SPINE_Y + vh / 2, zm + 2), vertebra, d=D_FINE)
        sk = M.bone(f"seg_{k:02d}_skin", (0, 0, zm), parent=seg)
        prev = body_size(k - 1) if k > 2 else (24, 28)
        nxt = body_size(k + 1) if k < SKIN_LAST else None
        hide = set()
        if prev[0] >= w and prev[1] >= h:
            hide.add("north")
        if nxt and nxt[0] >= w and nxt[1] >= h:
            hide.add("south")
        j = 0.3 if k % 2 == 0 else 0.0
        ext = max(1.0, 0.04 * h)
        M.box(sk, (-w / 2 - j, -h / 2 - j, z0 - ext), (w / 2 + j, h / 2 + j, z1 + (ext if nxt else 0)), skin, hide=hide)
        build_fins(seg, k)


# ------------------------------------------------------------------ 鎌状のひれ（3 箱の階段。後ろへ流れて先が細る）
#          名前: (体節, 付け根の前端 z, 根元の長さ L, 高さ H, 向き)
MEDIAN = {"fin_d1_skin": (8, 114, 34, 40, 1), "fin_d2_skin": (12, 180, 28, 30, 1), "fin_d3_skin": (16, 244, 20, 22, 1),
          "fin_v1_skin": (10, 150, 30, 34, -1), "fin_v2_skin": (14, 212, 24, 26, -1), "fin_v3_skin": (18, 276, 18, 18, -1)}
PECTORAL = (5, 68, 34, 56)                           # (体節, z, L, H)
PEC_SPLAY = 42.0                                     # 胸びれが外へ開く角度
STEPS = ((0.0, 1.0, 0.0, 0.42), (0.4, 1.15, 0.42, 0.74), (0.85, 1.5, 0.74, 1.0))   # (z0, z1 を L で、y0, y1 を H で)


def sickle(bone, z0, y0, length, height, up, x=0.0):
    for a, b, c, d in STEPS:
        M.box(bone, (x - 1, y0 + up * c * height, z0 + a * length), (x + 1, y0 + up * d * height, z0 + b * length), fin, tag=up)


def build_fins(seg, k):
    w, h = body_size(k)
    for name, (kk, z0, length, height, up) in MEDIAN.items():
        if kk == k:
            y0 = up * (h / 2 - 1.5)                  # 根元を外皮へ少し沈める
            M.bone(name, (0, y0, z0 + length / 2), parent=seg)
            sickle(name, z0, y0, length, height, up)
    if k == PECTORAL[0]:
        _, z0, length, height = PECTORAL
        for sx, s in ((1, "l"), (-1, "r")):
            x, y0 = sx * (w / 2 - 1.0), -8.0
            # 真下へ垂らして作り、rotation z で外へ開く（左は負で先が +X へ）
            M.bone(f"fin_{s}_skin", (x, y0, z0 + length / 2), parent=seg, rot=(0, 0, -sx * PEC_SPLAY))
            sickle(f"fin_{s}_skin", z0, y0, length, height, -1, x=x)
    if k == SKIN_LAST:
        M.bone("tail_skin", (0, 0, 324), parent=seg)
        M.box("tail_skin", (-1, -12, 320), (1, 12, 336), fin, tag=1)
        for up in (1, -1):
            for za, zb, ya, yb in ((330, 346, 8, 26), (340, 354, 20, 40), (350, TAIL_Z, 34, 50)):
                M.box("tail_skin", (-1, up * ya, za), (1, up * yb, zb), fin, tag=up)


# ------------------------------------------------------------------ アニメーション
IDLE, ATTACK, JAW_OUT = 4.0, 1.2, 0.3


def animations():
    idle = {"jaw": {"rotation": gk.sine(IDLE, 2.0, axis=(1, 0, 0))},
            "tail_skin": {"rotation": gk.sine(IDLE, 8.0, phase=2.4, axis=(0, 1, 0))}}
    for i, name in enumerate(MEDIAN):
        idle[name] = {"rotation": gk.sine(IDLE, 4.0, phase=0.6 * i)}
    for sx, s in ((1, "l"), (-1, "r")):
        idle[f"fin_{s}_skin"] = {"rotation": gk.sine(IDLE, sx * 6.0, phase=0.3)}
    shut = -JAW_REST                                 # 静止の 8 度を打ち消して噛み合わせる
    attack = {"jaw": {"rotation": gk.track([
        (0.0, (0, 0, 0)), (0.12, (14, 0, 0)), (0.25, (JAW_OPEN, 0, 0)), (0.36, (JAW_OPEN + 2, 0, 0)),
        (0.44, (shut, 0, 0)), (0.62, (shut, 0, 0)), (0.80, (10, 0, 0)), (1.0, (3, 0, 0)), (ATTACK, (0, 0, 0))])}}
    jaw_out = {"jaw": {"rotation": gk.track([
        (0.0, (0, 0, 0)), (0.08, (16, 0, 0)), (0.16, (29, 0, 0)), (0.22, (JAW_OPEN, 0, 0)), (JAW_OUT, (JAW_OPEN, 0, 0))])}}
    return {"idle": gk.anim(IDLE, True, idle), "attack": gk.anim(ATTACK, False, attack),
            "jaw_out": gk.anim(JAW_OUT, "hold_on_last_frame", jaw_out)}


def contract(counts):
    skins = [n for n in M.order if M.is_skin(n)]
    return {
        "entity": NAME,
        "generator": "tools/boss/glassback_model/build.py（手で直さず、生成元を直して流し直す）",
        "geo": f"assets/abyssia/geo/entity/{NAME}.geo.json",
        "animation": f"assets/abyssia/animations/entity/{NAME}.animation.json",
        "texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo.png",
        "texture_glow": f"assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png",
        "texture_size": [M.size, M.size],
        "texel": "外皮・ひれは 1 テクセル 0.5 ブロック、頭・顎・歯・背骨は 0.25 ブロック",
        "model_scale": MODEL_SCALE,
        "segment_length_units": SEG, "segment_length_blocks": 2.0,
        "joints": SEGS + 1, "segments": SEGS,
        "rest_pose": "頭から尾まで一直線。関節 j は geo の (0, 0, 16j)。頭は -Z を向く。体の芯は y = 0、背骨は y = 3",
        "head_pivot": [0.0, 0.0, 0.0],
        "segment_pivot": "seg_k の pivot = (0, 0, 16 * (k - 0.5))",
        "extent_units": {"snout_z": SNOUT_Z, "tail_z": TAIL_Z, "total_length_blocks": (TAIL_Z - SNOUT_Z) / 8,
                         "max_half_width": 50.0, "max_top_y": 58.5, "min_bottom_y": -52.5},
        "bone_rule": "head と seg_01..seg_25 は root 直下（親なし）。seg_01 と seg_22..seg_25 は箱なし"
                     "（尾びれ tail_skin は seg_21 の子で z=360 まで伸びる）。子の Bone は Java から触らない",
        "jaw": {"bone": "jaw（head の子。下顎の歯と牙）→ jaw_skin（下顎の外皮）", "hinge": list(JAW_HINGE),
                "rest_deg": JAW_REST, "open_deg": JAW_OPEN,
                "sign": "rotation x が正で開く。静止姿勢で 8 度開いている（歯をむく）。animation の値は静止に足される"},
        "translucent": {
            "rule": "名前が _skin で終わる Bone の箱だけ半透明（テクスチャのアルファ 40〜65%）。それ以外の Bone の箱は不透明"
                    "（歯の列の板は切り抜き: アルファ 0 と 255 だけ）",
            "bones": skins,
            "draw_order": "geo の Bone 順で、各体節は 不透明の中身（背骨の索・椎骨・目・歯）→ 半透明の外皮 の順に並んでいる。"
                          "外皮が深度を書くと、後から描く発光層の背骨が隠れる。①_skin 以外を先に（地＋発光）描き、②_skin を半透明で描く 2 段にするのが確実",
        },
        "glow": "発光層は _geo_glow.png。背骨の索と椎骨・目だけ（外皮・ひれ・歯には無い）。"
                "潜むときは発光層を描かず、背骨の地の色も暗くするなら _skin 以外の Bone を暗い色で描く（Java 側）",
        "children": {
            "head_skin": "頭の外皮（吻 2 箱＋頭蓋 1 箱）", "jaw / jaw_skin": "下顎",
            "seg_NN_skin": "体節の外皮（seg_02..seg_21、各 1 箱）",
            "fin_d1_skin..fin_d3_skin": "背の鎌びれ（seg_08 / 12 / 16 の子）。rotation z で左右に揺れる",
            "fin_v1_skin..fin_v3_skin": "腹の鎌びれ（seg_10 / 14 / 18 の子）",
            "fin_l_skin / fin_r_skin": "胸の鎌びれ（seg_05 の子）。静止で rotation z ∓42 度（外へ開く）",
            "tail_skin": "二叉の尾びれ（seg_21 の子、7 箱）。rotation y で左右に煽る",
        },
        "animations": {"idle": f"{IDLE} 秒ループ。顎の呼吸（±2 度）、ひれの揺れ、尾びれの煽り",
                       "attack": f"{ATTACK} 秒。0.25 秒で 34 度開き、0.44 秒で噛み合わせ（-8 度）、0.62 秒まで保持して戻る",
                       "jaw_out": f"{JAW_OUT} 秒、hold_on_last_frame。34 度開いて保持"},
        "counts": counts,
    }


def main():
    build_head()
    build_body()
    anims = animations()
    M.write(anims, bounds_w=54.0, bounds_h=14.0)
    counts = M.counts()
    assert 60 <= counts["cubes"] <= 150, counts
    roots = [n for n in M.order if M.bones[n]["parent"] is None]
    assert roots == ["head"] + [f"seg_{k:02d}" for k in range(1, SEGS + 1)], roots
    (HERE / "contract.json").write_text(json.dumps(contract(counts), ensure_ascii=False, indent=1), encoding="utf-8")
    print(NAME, counts, "atlas rows used", M.used_rows, "/", M.size)


if __name__ == "__main__":
    main()
