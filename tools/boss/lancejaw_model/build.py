# -*- coding: utf-8 -*-
"""槍顎（lancejaw）の GeckoLib モデルを書き出す。GNT01 の巨大野生生物 1 番（敵対、全長 32）。

```bash
python tools/boss/lancejaw_model/build.py        # geo / animation / texture / glow / contract.json
python tools/boss/check_animations.py lancejaw   # 骨の名前・キーの順・尺
python tools/boss/lancejaw_model/preview.py      # 図を out/ へ
```

デザインは `inbox/designs/GNT01-design-sheet.png` の 1 番。バニラ風に大きな直方体を少数組む
（体節ごとに 体 1 + 継ぎ目の栓 1）。座標はブロック（Z- が前、+X が左）、関節 0 が z=0。
槍の先（収めた状態）は z=-4、尾の先は z=28（全長 32）。

槍: `jaw`（下顎の骨 + 1 段目の筒 + 鉤歯）が前へ 3 ブロック、その中の `jaw_tip`（2 段目の槍）が
さらに 3 ブロック滑る（position z = -24 単位ずつ）。箱が入れ子でずれるだけで、喉へつながる筒は無い。
"""
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import gbox                                             # noqa: E402
from gbox import rgb, flat                              # noqa: E402
from boss_kit import pos, rot, wave, merge, EASE_SOFT, EASE_SLOW, EASE_HIT   # noqa: E402

NAME = 'lancejaw'

# ------------------------------------------------------------------ 素材タイル（ChatGPT のシート。giant_tiles.py 参照）
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import giant_tiles                      # noqa: E402
MATERIALS = [
    ('back', 'dark indigo-black shark skin with faint darker scale rows and subtle lighter flecks', '212a52 10152b 171d3d 0c1022'),
    ('belly', 'pale grey-blue smooth belly skin with soft horizontal creases', '5a6a8e 46557a'),
    ('bone', 'weathered ivory bone plate with fine cracks and darker pitted grooves', 'd8d1bc b9b19a 938b76'),
    ('tooth', 'bright ivory sharp tooth enamel with a faint vertical grain', 'f0ead8'),
    ('fin', 'deep navy translucent fin membrane with thin lighter rays radiating outward', '1b2247 2c3768 131935'),
    ('gill', 'dark crimson gill flesh with thin deep-red slit lines', 'c8252f'),
    ('mouth', 'near-black purple mouth lining with very faint dark ridges', '0a0c18'),
]
TILES = giant_tiles.Tiler(NAME, MATERIALS)
JOINTS = 17
TOTAL = 32.0
STAGE = -3.0 * gbox.U           # 槍の 1 段ぶん（単位）。負が前

# ------------------------------------------------------------ 色（平らな色、陰影 2〜3 段）
TOP = rgb('10152b')             # 背（藍黒）
SIDE_HI = rgb('171d3d')
SIDE_MID = rgb('212a52')
BELLY = rgb('5a6a8e')           # 腹（灰青）
BELLY_LO = rgb('46557a')
PLUG = rgb('0c1022')            # 継ぎ目の栓（曲がったときだけ見える）
LINE = rgb('2fd8cc')            # 発光線（青緑）
LINE_GLOW = rgb('46f2e2')
GILL = rgb('c8252f')
GILL_GLOW = rgb('4a0a0e')       # うっすら
BONE = rgb('d8d1bc')
BONE_MID = rgb('b9b19a')
BONE_LO = rgb('938b76')
MOUTH = rgb('0a0c18')
TOOTH = rgb('f0ead8')
EYE = rgb('58fff0')
FIN_HI = rgb('2c3768')
FIN = rgb('1b2247')
FIN_LO = rgb('131935')

# ------------------------------------------------------------ 体の輪郭: 体節 → (半幅, 下, 上)
BODY = {2: (1.5, -1.75, 1.75), 3: (1.5, -1.75, 1.75), 4: (1.5, -1.5, 1.75), 5: (1.25, -1.5, 1.75),
        6: (1.25, -1.25, 1.5), 7: (1.0, -1.25, 1.25), 8: (1.0, -1.0, 1.25), 9: (1.0, -1.0, 1.0),
        10: (0.75, -0.75, 1.0), 11: (0.75, -0.75, 0.75), 12: (0.5, -0.5, 0.75), 13: (0.5, -0.5, 0.5)}
HEAD_BLOCK = (1.5, -1.75, 1.75)  # 頭の後ろ半分（暗い塊）。体節 1 の位置
STALK = (0.25, -0.25, 0.5)       # 尾柄（seg_14 の前半だけ）


def line_y(z):
    """発光線の高さ（その 0.25 の段の下端）。後ろへ行くほど段々に下がる"""
    return 0.75 if z < 8 else 0.5 if z < 16 else 0.25 if z < 22 else 0.0


def body_paint(y0, y1, gills=False):
    def paint(face, x, y, z):
        if face == "up":
            return TOP
        if face == "down":
            return BELLY_LO
        side = face in ("east", "west")
        if side and gills and -1.0 < y < 1.0 and any(a <= z < a + 0.25 for a in (2.5, 3.0, 3.5)):
            return GILL, GILL_GLOW
        if side and 4.25 <= z < 26.0 and line_y(z) <= y < line_y(z) + 0.25 and (z % 2.0) < 1.5:
            return LINE, LINE_GLOW
        if y < y0 + (0.5 if y1 - y0 >= 2.0 else 0.25):
            return BELLY
        if y > y1 - 0.5:
            return SIDE_HI
        return SIDE_MID
    return paint


def sheath_paint(y0):
    """鼻先の骨の鞘。側面に眼（発光 1 テクセル + 暗い眼窩）、下の段を一段暗く"""
    def paint(face, x, y, z):
        if face == "down":
            return MOUTH
        if face in ("east", "west"):
            if 0.25 <= z < 0.5 and 0.75 <= y < 1.0:
                return EYE, EYE
            if 0.0 <= z < 0.75 and 0.75 <= y < 1.0:
                return MOUTH
        if face == "up":
            return BONE
        return BONE_MID if y < y0 + 0.25 else BONE
    return paint


def jaw_paint(face, x, y, z):
    if face == "up":
        return MOUTH
    if face == "down":
        return BONE_LO
    return BONE_MID if y < -1.0 else BONE


def spear_paint(face, x, y, z):
    return BONE if face == "up" else BONE_LO if face == "down" else BONE_MID


def fin_paint(face, x, y, z):
    return FIN_HI if face in ("up", "north") else FIN_LO if face in ("down", "south") else FIN


def build():
    m = gbox.Model(NAME, JOINTS)
    m.tiles = TILES

    # ---- 頭（head は関節 0 に pivot。鞘の先は z=-3、頭の後ろ端は関節 1）
    m.box("head", (-0.75, 0.75), (0.25, 1.0), (-3.0, -2.0), sheath_paint(0.25))       # 鞘の先
    m.box("head", (-1.0, 1.0), (0.25, 1.5), (-2.0, 1.25), sheath_paint(0.25))         # 鞘
    hw, y0, y1 = HEAD_BLOCK
    m.box("head", (-hw, hw), (y0, y1), (0.75, 2.0), body_paint(y0, y1))               # 頭の後ろ半分
    m.box("head", (-1.0, 1.0), (-1.75, -0.5), (-0.75, 0.75), body_paint(-1.75, 9.0))  # 下顎が出入りする鞘口
    for sx in (1, -1):
        for z, edge in ((-2.75, 0.75), (-1.75, 1.0), (-1.0, 1.0), (-0.25, 1.0)):     # 上の歯
            xa, xb = sorted((sx * (edge - 0.25), sx * edge))
            m.box("head", (xa, xb), (-0.25, 0.25), (z, z + 0.25), flat(TOOTH))

    # ---- 下顎（骨の槍の 1 段目）と槍先（2 段目）。pivot は鞘口の奥
    m.bone("jaw", (0, -1.0, 2.0), parent="head")
    m.box("jaw", (-0.75, 0.75), (-1.5, -0.75), (-2.5, 3.25), jaw_paint)               # 下顎の骨
    m.box("jaw", (-0.375, 0.375), (-1.0, -0.25), (-2.75, 1.0), spear_paint)           # 1 段目の筒
    for sx in (1, -1):
        for z in (-2.25, -1.25):                                                      # 内側の鉤歯
            xa, xb = sorted((sx * 0.5, sx * 0.75))
            m.box("jaw", (xa, xb), (-0.75, -0.25), (z, z + 0.25), flat(TOOTH))
            m.box("jaw", (xa, xb), (-0.5, -0.25), (z + 0.25, z + 0.5), flat(TOOTH))   # 鉤（後ろへ曲がる先）
    m.bone("jaw_tip", (0, -1.0, 2.0), parent="jaw")
    m.box("jaw_tip", (-0.25, 0.25), (-0.875, -0.375), (-3.0, 1.5), spear_paint)       # 槍の柄
    m.box("jaw_tip", (-0.5, 0.5), (-0.75, -0.5), (-3.5, -3.0), spear_paint)           # かえし
    m.box("jaw_tip", (-0.125, 0.125), (-0.75, -0.5), (-4.0, -3.5), spear_paint)       # 穂先

    # ---- 体節: 体 1 + 継ぎ目の栓 1
    prev = HEAD_BLOCK
    for k in sorted(BODY) + [14]:
        hw, y0, y1 = BODY.get(k, STALK)
        z0 = 2.0 * (k - 1)
        seg = f"seg_{k:02d}"
        m.box(seg, (-hw, hw), (y0, y1), (z0, z0 + (2.0 if k in BODY else 1.0)),
              body_paint(y0, y1, gills=(k == 2)))
        ph = min(hw, prev[0]) - 0.125
        m.box(seg, (-ph, ph), (max(y0, prev[1]) + 0.125, min(y1, prev[2]) - 0.125), (z0 - 0.5, z0 + 0.5),
              flat(PLUG))
        prev = (hw, y0, y1)

    # ---- 背びれ 2 枚（板。箱どうしは重ねず接する）
    m.bone("dorsal_1", (0, 1.75, 7.0), parent="seg_04")
    for z, y in (((6.0, 9.0), (1.75, 2.5)), ((6.75, 9.5), (2.5, 3.25)), ((7.75, 9.75), (3.25, 4.0)),
                 ((8.75, 10.0), (4.0, 4.5))):
        m.box("dorsal_1", (-0.125, 0.125), y, z, fin_paint)
    m.bone("dorsal_2", (0, 1.0, 17.0), parent="seg_09")
    for z, y in (((16.25, 18.5), (1.0, 1.75)), ((17.0, 19.0), (1.75, 2.5)), ((18.0, 19.25), (2.5, 3.0))):
        m.box("dorsal_2", (-0.125, 0.125), y, z, fin_paint)

    # ---- 胸びれ（後ろへ流れる板、少し垂らす）・腹びれ・臀びれ
    for sx, side in ((1, "l"), (-1, "r")):
        m.bone(f"fin_{side}", (sx * 1.5, -1.25, 6.5), parent="seg_04", rot=(0, 0, sx * 32))
        for (xa, xb), z in (((1.5, 3.0), (5.75, 8.0)), ((3.0, 4.25), (6.75, 9.25)), ((4.25, 5.25), (8.0, 10.0))):
            xs = (xa, xb) if sx > 0 else (-xb, -xa)
            m.box(f"fin_{side}", xs, (-1.5, -1.25), z, fin_paint)
        m.bone(f"pelvic_{side}", (sx * 1.0, -0.75, 16.75), parent="seg_09", rot=(0, 0, sx * 40))
        xs = (1.0, 2.0) if sx > 0 else (-2.0, -1.0)
        m.box(f"pelvic_{side}", xs, (-1.0, -0.75), (16.5, 18.25), fin_paint)
    m.bone("anal", (0, -0.75, 20.5), parent="seg_11")
    m.box("anal", (-0.125, 0.125), (-1.5, -0.75), (20.5, 21.75), fin_paint)

    # ---- 二叉の尾びれ（上葉と下葉、3 段ずつ）
    m.bone("tail_fin", (0, 0.125, 26.0), parent="seg_14")
    for z, y in (((26.0, 27.5), (0.5, 1.5)), ((26.75, 28.0), (1.5, 2.5)), ((27.25, 28.0), (2.5, 3.5)),
                 ((26.0, 27.5), (-1.25, -0.25)), ((26.75, 28.0), (-2.25, -1.25)), ((27.25, 28.0), (-3.25, -2.25))):
        m.box("tail_fin", (-0.125, 0.125), y, z, fin_paint)
    return m


def animations():
    IDLE, ATTACK, JAW_OUT = 3.0, 1.4, 0.35
    # idle は鰭と尾だけ（槍は attack / jaw_out の受け持ち。同じ骨を 2 つの controller で取り合わない）
    idle = {"tail_fin": wave(IDLE, 9.0, -1.2, (0, 1, 0)),
            "dorsal_1": wave(IDLE, 2.5, -0.4, (0, 0, 1)), "dorsal_2": wave(IDLE, 3.0, -0.8, (0, 0, 1)),
            "anal": wave(IDLE, 3.0, -0.9, (0, 0, 1))}
    for sx, side in ((1, "l"), (-1, "r")):
        idle[f"fin_{side}"] = wave(IDLE, 6.0, 0.0, (0.0, 0.3 * sx, 1.0 * sx))
        idle[f"pelvic_{side}"] = wave(IDLE, 5.0, -0.7, (0.0, 0.0, 1.0 * sx))
    zero, out = (0.0, 0.0, 0.0), (0.0, 0.0, STAGE)
    attack = {
        # 1 段目: 下顎が 3 ブロック前へ。突く前に少し引いて先が上がる
        "jaw": merge(
            pos([(0.0, zero), (0.10, (0.0, 0.0, 3.0), EASE_SLOW), (0.22, out, EASE_HIT), (0.55, out),
                 (1.05, zero, EASE_SOFT), (ATTACK, zero)]),
            rot([(0.0, zero), (0.10, (-3.0, 0.0, 0.0), EASE_SLOW), (0.22, (1.0, 0.0, 0.0), EASE_HIT),
                 (0.6, zero, EASE_SOFT), (ATTACK, zero)])),
        # 2 段目: 槍先がさらに 3 ブロック前へ（計 6）。先に戻る
        "jaw_tip": pos([(0.0, zero), (0.16, zero), (0.28, out, EASE_HIT), (0.45, out), (0.85, zero, EASE_SOFT),
                        (ATTACK, zero)]),
    }
    jaw_out = {
        "jaw": pos([(0.0, zero), (0.18, out, EASE_HIT), (JAW_OUT, out)]),
        "jaw_tip": pos([(0.0, zero), (0.12, zero), (0.30, out, EASE_HIT), (JAW_OUT, out)]),
    }
    return {"idle": {"loop": True, "animation_length": IDLE, "bones": idle},
            "attack": {"loop": False, "animation_length": ATTACK, "bones": attack},
            "jaw_out": {"loop": "hold_on_last_frame", "animation_length": JAW_OUT, "bones": jaw_out}}


CONTRACT = {
    "design": "槍顎（ヤリアゴ）。GNT01-design-sheet.png の 1 番。藍黒の背・灰青の腹、鼻先を覆う骨の鞘、"
              "骨の下顎から 2 段で伸びる槍、背の両脇の青緑の発光線、赤いえら 3 本、板状の背びれ 2 枚、二叉の尾",
    "glow_note": "光らない画素は (0,0,0,0)。光るのは体側の発光線（青緑）・眼（青緑）・えら 3 本（暗い赤 = うっすら）。"
                 "RenderType.eyes（加算）か GeckoLib の AutoGlowingGeoLayer 相当で、同じ geo に重ねる",
    "total_length_blocks": TOTAL,
    "jaw": {
        "bones": "jaw（head の子: 下顎の骨・1 段目の筒・鉤歯）→ jaw_tip（槍の柄・かえし・穂先）",
        "stage_units": [0.0, 0.0, STAGE], "reach_blocks": 6.0,
        "rule": "jaw の position z = -24 で 1 段目（3 ブロック）、jaw_tip の position z = -24 を足して 2 段目（計 6 ブロック）",
    },
    "animation_signs": {
        "jaw / jaw_tip": "position（geo 単位）。z が負で前へ伸びる",
        "fin_l / fin_r / pelvic_l / pelvic_r": "胸びれ（seg_04）と腹びれ（seg_09）。付け根が pivot、z で上下に煽る",
        "dorsal_1 / dorsal_2 / anal": "背びれ（seg_04 / seg_09）と臀びれ（seg_11）。z で左右に揺れる",
        "tail_fin": "二叉の尾びれ（seg_14 の子）。y で左右に煽る",
    },
}


def main():
    m = build()
    m.write(animations(), CONTRACT, TOTAL, 7.0)


if __name__ == '__main__':
    main()
