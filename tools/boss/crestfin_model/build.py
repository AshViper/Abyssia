# -*- coding: utf-8 -*-
"""冠鰭（crestfin、GNT01 の 9 番）の GeckoLib モデルを書き出す。

```bash
python tools/boss/crestfin_model/build.py          # geo / animation / texture(+glow) / contract.json
python tools/boss/check_animations.py crestfin     # ボーン名・キーの順・尺
python tools/boss/crestfin_model/preview.py        # 図を out/ へ
```

出力は手で直さない（ここを直して流し直す）。形は大きな直方体だけ:
体節ごとに 装甲板の箱 1 つ（板どうしに 0.25 ブロックのすき間）+ 暗い芯 1 つ、背に赤い棘（段々の 1〜3 箱、子の骨）、
くさび形の頭（3 箱）と歯の箱、角ばった胸びれ・腹びれ（段々の板）、赤い縁の二股の尾びれ。
テクスチャは 1 テクセル 0.25 ブロックの平らな 3 段。
"""
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import cube_kit as ck
from cube_kit import SEG

NAME = "crestfin"

# ------------------------------------------------------------------ 素材タイル（ChatGPT のシート。giant_tiles.py 参照）
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import giant_tiles                      # noqa: E402
MATERIALS = [
    ('plate', 'dark blue-grey armor plate with lighter edges and small scratches', '383e5c 535c84 262a40'),
    ('head', 'lighter blue-grey head armor with subtle bony ridges and cracks', '505c88 6c79a6 3a4466'),
    ('belly', 'pale grey-blue belly skin with fine creases', '7a82a2 5c6484'),
    ('core', 'near-black blue-grey body core skin, smooth', '12131c'),
    ('crest', 'vivid red crest with darker red ridges and lighter highlights', 'd23a3a ff6b57 8a1f2e'),
    ('fin', 'slate-blue fin membrane with pale ribbing along the edge', '394060 7b84a8 30344e'),
    ('tooth', 'off-white tooth enamel with tan shading and fine cracks', 'e9e3d2 b6ae9c'),
    ('gum', 'dark maroon gum flesh with wet folds', '4a1420'),
]
TILES = giant_tiles.Tiler(NAME, MATERIALS)
JOINTS = 22                    # 全長 42 ブロック / 2 + 1
SEGS = JOINTS - 1              # seg_01..seg_21
FIRST, LAST = 2, 19            # 箱を持つ体節（seg_01 は頭の殻の下。seg_20 / seg_21 は尾びれの後ろ半分と先で、箱は無い）
SNOUT_Z = -28.0                # 吻端（関節 0 の 3.5 ブロック前）
TAIL_Z = 320.0                 # 尾びれの先 → 全長 348 単位 = 43.5 ブロック

# 体節 k（2..20）の高さ。幅は高さ - 6
H = [26, 26, 26, 24, 24, 24, 22, 22, 20, 20, 18, 18, 16, 14, 12, 10, 8, 8]
# 背の棘の高さ（seg_02..seg_17）。頭には別に 2 本
SPINE = [26, 26, 24, 22, 20, 18, 16, 14, 14, 12, 10, 10, 8, 6, 6, 4]
assert len(H) == LAST - FIRST + 1


def hh(k):
    return H[k - FIRST]


def ww(k):
    return max(4, hh(k) - 6)


# ---------------------------------------------------------------- 色
PLATE = 0x383E5C
PLATE_HI = 0x535C84
PLATE_DK = 0x262A40
HEAD = 0x505C88          # 頭の板は体より明るい青灰
HEAD_HI = 0x6C79A6
HEAD_DK = 0x3A4466
BELLY = 0x7A82A2
BELLY_DK = 0x5C6484
CORE = 0x12131C
RED = 0xD23A3A
RED_HI = 0xFF6B57
RED_DK = 0x8A1F2E
FIN = 0x394060
FIN_EDGE = 0x7B84A8
TAIL = 0x30344E
TOOTH = 0xE9E3D2
TOOTH_DK = 0xB6AE9C
GUM = 0x4A1420
EYE = 0xFF2A1A


def plate_paint(h, belly=True, pal=None):
    """装甲板。上面と側面の最上段が明るく、前後の縁 1 列が暗く、腹の 2 段が明るい灰"""
    PLATE, PLATE_HI, PLATE_DK = pal or (globals()["PLATE"], globals()["PLATE_HI"], globals()["PLATE_DK"])

    def paint(f):
        x0, x1, y0, y1, z0, z1 = f.b
        edge = f.face in ("north", "south") or f.z - z0 < 2 or z1 - f.z < 2
        if f.face == "up":
            return PLATE if edge else PLATE_HI
        if belly and (f.face == "down" or f.y < y0 + 4):
            return BELLY_DK if edge or f.face == "down" else BELLY
        if f.face in ("east", "west") and f.y > y1 - 2:
            return PLATE if edge else PLATE_HI
        return PLATE_DK if edge else PLATE
    return paint


def flat(c):
    return lambda f: c


def spine_paint(base):
    """赤い棘。各箱の最上段が明るく（発光）、根元の箱の最下段が暗い"""
    def paint(f):
        y0, y1 = f.b[2], f.b[3]
        if f.face == "up" or (f.face != "down" and f.y > y1 - 2):
            return (RED_HI, RED_HI)
        if base and f.y < y0 + 2:
            return RED_DK
        return RED
    return paint


def fin_paint(f):
    """ひれ。外の端と後ろの縁 1 列が明るい灰"""
    x0, x1, y0, y1, z0, z1 = f.b
    outer = x1 if abs(x1) > abs(x0) else x0
    if f.face in ("up", "down") and (z1 - f.z < 2 or abs(outer - f.x) < 2):
        return FIN_EDGE
    if f.face == "south":
        return FIN_EDGE
    return FIN


def tail_paint(up):
    """尾びれ。後ろの縁と、葉の外側の 1 段が赤"""
    def paint(f):
        x0, x1, y0, y1, z0, z1 = f.b
        if f.face == "south" or (f.face in ("east", "west") and z1 - f.z < 2):
            return RED
        if up > 0 and (f.face == "up" or (f.face in ("east", "west") and y1 - f.y < 2)):
            return RED_DK
        if up < 0 and (f.face == "down" or (f.face in ("east", "west") and f.y - y0 < 2)):
            return RED_DK
        return TAIL
    return paint


def skull_paint(f):
    x0, x1, y0, y1, z0, z1 = f.b
    if f.face in ("east", "west") and 0 < f.z < 4 and 2.5 < f.y < 7:
        return (EYE, EYE)
    return head_plate()(f)


def head_plate():
    return plate_paint(0, belly=False, pal=(HEAD, HEAD_HI, HEAD_DK))


def jaw_paint(f):
    y0 = f.b[2]
    if f.face == "up":
        return GUM
    if f.face == "down" or f.y < y0 + 2:
        return BELLY_DK if f.face == "down" else BELLY
    return HEAD_DK if f.z - f.b[4] < 2 else HEAD


def tooth_paint(upper):
    def paint(f):
        y0, y1 = f.b[2], f.b[3]
        root = f.y > y1 - 2 if upper else f.y < y0 + 1.5
        return TOOTH_DK if root else TOOTH
    return paint


def spine_boxes(m, bone, y, zb, s):
    """棘 1 本。後ろへ流れる段々（高さ s、厚み 4 = 0.5 ブロック）。zb は根元の前端"""
    if s >= 12:
        a = int(round(s * 0.45 / 2)) * 2
        b = int(round(s * 0.75 / 2)) * 2
        m.box(bone, -2, 2, y, y + a, zb, zb + 12, spine_paint(True))
        m.box(bone, -2, 2, y + a, y + b, zb + 4, zb + 14, spine_paint(False))
        m.box(bone, -2, 2, y + b, y + s, zb + 8, zb + 16, spine_paint(False))
    elif s >= 6:
        a = int(round(s * 0.5 / 2)) * 2
        m.box(bone, -2, 2, y, y + a, zb, zb + 10, spine_paint(True))
        m.box(bone, -2, 2, y + a, y + s, zb + 4, zb + 12, spine_paint(False))
    else:
        m.box(bone, -2, 2, y, y + s, zb + 2, zb + 10, spine_paint(True))


CRESTS = []      # 棘の骨の名前（頭から尾へ）


def build_model():
    m = ck.Model(NAME, size=128)
    m.tiles = TILES
    CRESTS.clear()

    # ---- 頭: 吻端・吻・頭蓋の 3 箱（くさび形）。関節 0 から関節 1 まで覆う
    m.bone("head", pivot=(0, 0, 0))
    hp = head_plate()
    m.box("head", -7, 7, -6, 3, SNOUT_Z, -16, hp)
    m.box("head", -10, 10, -6, 9, -16, 0, hp)
    m.box("head", -13, 13, -9, 15, 0, 16, skull_paint)
    m.box("head", -7, 7, -9, -6, -24, 10, flat(GUM))                   # 口の中
    for sx in (1, -1):
        # 眉の板と、後ろへ流れる角の板 2 段
        x0, x1 = sorted((10 * sx, 14 * sx))
        m.box("head", x0, x1, 8, 13, -12, 6, hp)
        x0, x1 = sorted((11 * sx, 15 * sx))
        m.box("head", x0, x1, 11, 17, 4, 24, hp)
        x0, x1 = sorted((11 * sx, 14 * sx))
        m.box("head", x0, x1, 15, 20, 14, 28, hp)
        # 頬の板
        x0, x1 = sorted((13 * sx, 15 * sx))
        m.box("head", x0, x1, -7, 4, 6, 22, hp)
        # 上の歯（側 5 本）と前の牙
        for z, xa in ((-27, 5), (-21, 5), (-14, 8), (-8, 8), (-3, 8)):
            x0, x1 = sorted((xa * sx, (xa + 2) * sx))
            m.box("head", x0, x1, -10, -6, z, z + 2, tooth_paint(True))
        x0, x1 = sorted((1 * sx, 3 * sx))
        m.box("head", x0, x1, -12, -6, SNOUT_Z, SNOUT_Z + 2, tooth_paint(True))
    # 下顎（rotation x 正で開く）
    m.bone("jaw", "head", pivot=(0, -7, 12))
    m.box("jaw", -9, 9, -13, -9, -24, 4, jaw_paint)
    m.box("jaw", -11, 11, -14, -7, 4, 16, jaw_paint)
    for sx in (1, -1):
        for z, xa in ((-24, 5), (-18, 6), (-11, 6), (-6, 6)):
            x0, x1 = sorted((xa * sx, (xa + 2) * sx))
            m.box("jaw", x0, x1, -9, -6, z, z + 2, tooth_paint(False))
    # 頭の棘 2 本
    for i, (y, zb, s) in enumerate(((9, -12, 12), (15, 0, 20)), 1):
        b = m.bone(f"crest_h{i}", "head", pivot=(0, y, zb + 6))
        spine_boxes(m, b, y, zb, s)
        CRESTS.append(b)

    # ---- 体節
    for k in range(1, SEGS + 1):
        pv = (0, 0, SEG * (k - 0.5))
        m.bone(f"seg_{k:02d}", pivot=pv)
        if not FIRST <= k <= LAST:
            continue
        b = m.bone(f"body_{k:02d}", f"seg_{k:02d}", pivot=pv)
        h, w = hh(k), ww(k)
        z0, z1 = SEG * (k - 1), SEG * k
        m.box(b, -w / 2, w / 2, -h / 2, h / 2, z0 + 1, z1 - 1, plate_paint(h))
        cx, cy = max(1, w / 2 - 2), max(1, h / 2 - 2)
        m.box(b, -cx, cx, -cy, cy, z0 - 3, z1 + 1, flat(CORE))
        if k - FIRST < len(SPINE):
            c = m.bone(f"crest_{k:02d}", b, pivot=(0, h / 2, z0 + 7))
            spine_boxes(m, c, h / 2, z0 + 2, SPINE[k - FIRST])
            CRESTS.append(c)

    # ---- 胸びれ（seg_03）と腹びれ（seg_09）: 水平の段々の板を、付け根で下へ垂らす
    def paired(name, parent, k, plates, droop):
        h, w = hh(k), ww(k)
        z0 = SEG * (k - 1)
        y = -h / 2 + 6
        for sx, side in ((1, "l"), (-1, "r")):
            b = m.bone(f"{name}_{side}", parent, pivot=(sx * w / 2, y, z0 + 4), rotation=(0, 0, droop * sx))
            for xa, xb, za, zb in plates:
                x0, x1 = sorted((sx * (w / 2 + xa), sx * (w / 2 + xb)))
                m.box(b, x0, x1, y - 1, y + 1, z0 + za, z0 + zb, fin_paint)

    paired("fin", "body_03", 3, ((-2, 12, 2, 18), (10, 24, 10, 30), (22, 34, 22, 40)), 55)
    paired("fin_pelvic", "body_09", 9, ((-2, 10, 2, 16), (8, 20, 10, 26)), 55)
    # 臀びれ（seg_14）
    h = hh(14)
    z0 = SEG * 13
    m.box("body_14", -1, 1, -h / 2 - 4, -h / 2, z0 + 2, z0 + 14, fin_paint)
    m.box("body_14", -1, 1, -h / 2 - 8, -h / 2 - 4, z0 + 8, z0 + 18, fin_paint)

    # ---- 尾びれ（body_19 の子）: 大きな二股。後ろへ流れる段々 4 枚ずつ（長さ 5 ブロック、高さ 6 ブロック）
    zt = TAIL_Z - 40
    m.bone("tail_fin", f"body_{LAST:02d}", pivot=(0, 0, zt + 4))
    m.box("tail_fin", -1, 1, -6, 6, zt + 4, zt + 24, tail_paint(0))
    for s in (1, -1):
        for ya, yb, za, zb in ((2, 12, 2, 18), (8, 18, 10, 28), (14, 22, 20, 36), (18, 24, 30, 40)):
            y0, y1 = sorted((s * ya, s * yb))
            m.box("tail_fin", -1, 1, y0, y1, zt + za, zt + zb, tail_paint(s))
    return m


# ==================================================================== アニメーション
IDLE = 4.0
ATTACK = 1.0
CREST_UP = 0.4
CREST_RAISE = 26.0       # 冠を立てる角度（度、前へ起こす）
CREST_SCALE = 1.3


def swim_amp(k):
    """前半は装甲で硬く、後ろ半分だけ尾を振る（最大 0.5 ブロック）"""
    if not FIRST <= k <= LAST:
        return 0.0
    return 4.0 * min(1.0, max(0.0, (k - 6) / 10.0))


def animations():
    import math
    idle = ck.swim_tracks(IDLE, SEGS, swim_amp, wavelength=14.0)
    s = lambda p: math.sin(2 * math.pi * p)
    idle["fin_l"] = {"rotation": ck.loop_keys(IDLE, lambda p: (0, 0, -6 * s(p)), 8)}
    idle["fin_r"] = {"rotation": ck.loop_keys(IDLE, lambda p: (0, 0, 6 * s(p)), 8)}
    idle["fin_pelvic_l"] = {"rotation": ck.loop_keys(IDLE, lambda p: (0, 0, -5 * s(p + 0.25)), 8)}
    idle["fin_pelvic_r"] = {"rotation": ck.loop_keys(IDLE, lambda p: (0, 0, 5 * s(p + 0.25)), 8)}
    idle["tail_fin"] = {"rotation": ck.loop_keys(IDLE, lambda p: (0, 10 * s(p - 19 / 14.0), 0), 8)}
    idle["jaw"] = {"rotation": ck.keys([(0.0, (0, 0, 0)), (2.0, (3, 0, 0)), (4.0, (0, 0, 0))])}
    # 棘は頭から尾へ小さくさざ波
    for i, c in enumerate(CRESTS):
        idle[c] = {"rotation": ck.loop_keys(IDLE, lambda p, i=i: (3 * s(p - i / 9.0), 0, 0), 8)}

    # attack: 口を大きく開けて 1 回噛む（当たりは 0.5 秒）。棘も半分立つ
    attack = {"jaw": {"rotation": ck.keys([
        (0.0, (0, 0, 0)),
        (0.30, (40, 0, 0), "easeoutcubic"),
        (0.42, (42, 0, 0)),
        (0.50, (-2, 0, 0), "easeinquint"),
        (0.70, (4, 0, 0)),
        (1.00, (0, 0, 0), "easeoutcubic"),
    ])}}
    for c in CRESTS:
        attack[c] = {"rotation": ck.keys([(0.0, (0, 0, 0)), (0.30, (CREST_RAISE * 0.5, 0, 0)),
                                          (0.50, (CREST_RAISE * 0.5, 0, 0)), (1.0, (0, 0, 0))])}

    # crest_up: 頭から順に冠を立てて保持（行き過ぎてから止まる）
    crest_up = {}
    for i, c in enumerate(CRESTS):
        t = 0.14 + 0.012 * i
        crest_up[c] = {
            "rotation": ck.keys([(0.0, (0, 0, 0)), (t, (CREST_RAISE * 1.2, 0, 0)), (CREST_UP, (CREST_RAISE, 0, 0))]),
            "scale": ck.keys([(0.0, (1, 1, 1)), (t, (1, CREST_SCALE, CREST_SCALE)),
                              (CREST_UP, (1, CREST_SCALE, CREST_SCALE))]),
        }
    crest_up["jaw"] = {"rotation": ck.keys([(0.0, (0, 0, 0)), (CREST_UP, (10, 0, 0))])}
    return {
        "idle": {"loop": True, "animation_length": IDLE, "bones": idle},
        "attack": {"loop": False, "animation_length": ATTACK, "bones": attack},
        "crest_up": {"loop": "hold_on_last_frame", "animation_length": CREST_UP, "bones": crest_up},
    }


def contract(m, anims):
    return {
        "entity": NAME,
        "generator": "tools/boss/crestfin_model/build.py（手で直さず、生成元を直して流し直す）",
        "geo": f"assets/abyssia/geo/entity/{NAME}.geo.json",
        "animation": f"assets/abyssia/animations/entity/{NAME}.animation.json",
        "texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo.png",
        "glow_texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png",
        "glow_note": "光らない画素は (0,0,0,0)。光るのは赤い目と、棘の各段の最上段（明るい赤）",
        "texture_size": [m.size, m.size],
        "texel": "1 テクセル = 2 単位 = 0.25 ブロック",
        "model_scale": 2.0,
        "segment_length_units": SEG,
        "segment_length_blocks": 2.0,
        "joints": JOINTS,
        "segments": SEGS,
        "rest_pose": "一直線。関節 j は (0, 0, 16j)、頭は -Z。芯は y = 0",
        "head_pivot": [0.0, 0.0, 0.0],
        "segment_pivot": "seg_k の pivot = (0, 0, 16 (k - 0.5))",
        "extent_units": {"snout_z": SNOUT_Z, "tail_z": TAIL_Z, "total_length_blocks": (TAIL_Z - SNOUT_Z) / 8,
                         "max_half_width": 33.0, "max_top_y": 39.0, "min_bottom_y": -36.0},
        "bone_rule": ("head と seg_01..seg_21 は root 直下で、Java が毎フレーム位置と回転を上書きする。"
                      "head の殻は関節 1 まで（seg_01 は空）、箱は子の body_02..body_19 にあり、尾びれは body_19 の子（seg_20 / seg_21 は空）。"
                      "idle の尾の振りは body_k の position x と rotation y。Java は子の骨に触らない"),
        "crest": {
            "bones": CRESTS,
            "rule": (f"棘は crest_h1 / crest_h2（head の子）と crest_02..crest_17（body_k の子）。pivot は根元。"
                     f"rotation x 正で前へ起きる。crest_up は {CREST_RAISE:g} 度と scale y/z {CREST_SCALE:g}"),
        },
        "animation_signs": {
            "jaw": "rotation x 正で口が開く（attack は 42 度）。pivot は顎関節 (0, -7, 12)",
            "fin_l / fin_r / fin_pelvic_l / fin_pelvic_r": "付け根が pivot。静止姿勢で z に垂らしてある（左右で符号が逆）",
            "tail_fin": "rotation y で左右に振る",
        },
        "animations": {n: {"loop": a["loop"], "length": a["animation_length"], "bones": sorted(a["bones"])}
                       for n, a in anims.items()},
        "animation_notes": {
            "idle": "後ろ半分だけの尾の振り（最大 0.5 ブロック）、ひれ・尾びれ・顎・棘の小さな揺れ",
            "attack": "口を 42 度開けて噛む 1 回。当たりは 0.5 秒。棘が半分立つ",
            "crest_up": "頭から順に冠を立てて保持（hold_on_last_frame）。口を少し開く",
        },
        "cube_count": m.cube_count(),
        "bones": [{"name": b["name"], "parent": b.get("parent"), "pivot": b["pivot"], "cubes": len(b["cubes"])}
                  for b in m.bones],
    }


def main():
    m = build_model()
    anims = animations()
    m.write(anims, bounds_w=46.0, bounds_h=10.0)
    (HERE / "contract.json").write_text(json.dumps(contract(m, anims), ensure_ascii=False, indent=1), encoding='utf-8')
    print(f"{NAME}: bones={len(m.bones)} cubes={m.cube_count()} atlas={m.size} "
          f"(used rows {m._shelf_y + m._shelf_h})")


if __name__ == '__main__':
    main()
