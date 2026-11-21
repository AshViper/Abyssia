# -*- coding: utf-8 -*-
"""石鱗ウオ（slateback）の GeckoLib モデルを書き出す。GNT01 の巨大野生生物 5 番（中立、全長 34）。

```bash
python tools/boss/slateback_model/build.py        # geo / animation / texture / glow / contract.json
python tools/boss/check_animations.py slateback   # 骨の名前・キーの順・尺
python tools/boss/slateback_model/preview.py      # 図を out/ へ
```

デザインは `inbox/designs/GNT01-design-sheet.png` の 5 番。頁岩の石板を瓦のように重ねた装甲の古代魚。
バニラ風に大きな直方体を少数組む（体節ごとに 体 1 + 継ぎ目の栓 1 + 鞍形の石板 1、所どころに背と脇腹の石板）。
座標はブロック（Z- が前、+X が左）、関節 0 が z=0。鼻先は z=-1.5、尾の先は z=32.5。

`head` は Java が毎フレーム上書きするので、頭の箱は子の `skull` に持たせ、体当たり（頭を振る）は skull を動かす。
"""
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import gbox                                             # noqa: E402
from gbox import rgb, flat                              # noqa: E402
from boss_kit import pos, rot, wave, merge, EASE_SOFT, EASE_SLOW, EASE_HIT, EASE_FAST, EASE_BACK   # noqa: E402

NAME = 'slateback'

# ------------------------------------------------------------------ 素材タイル（ChatGPT のシート。giant_tiles.py 参照）
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import giant_tiles                      # noqa: E402
MATERIALS = [
    ('slab', 'pale grey-blue stone slab with chipped edges, thin cracks and speckled lichen-like spots', '8b919c a9afb9 767c87'),
    ('lower_plate', 'mid slate-grey plate armor with horizontal seams and small rivet-like pits', '484d58 4a4f59 565c67'),
    ('underbody', 'dark charcoal-grey rough skin between plates, faint darker wrinkles', '23262f 181a21 353943 2b2f38'),
    ('fin', 'slate-grey membrane fin with parallel lighter ribs', '747a85 8d939d 585d68'),
    ('tooth', 'dull off-white worn tooth with fine vertical cracks', 'd9d9d0'),
    ('mouth', 'almost black grey mouth lining, smooth', '15161c'),
]
TILES = giant_tiles.Tiler(NAME, MATERIALS)
JOINTS = 18
TOTAL = 34.0

# ------------------------------------------------------------ 色（平らな色、陰影 2〜3 段）
SL_HI = rgb('a9afb9')           # 石板の上面
SL_A = rgb('8b919c')            # 石板の側面（明るい板）
SL_B = rgb('767c87')            # 石板の側面（暗い板）
SL_EDGE = rgb('4a4f59')         # 石板の下の縁
UNDER = rgb('23262f')           # 石板の隙間（地の体）
LOW = rgb('484d58')             # 下半身の板
LOW_HI = rgb('565c67')
GAP = rgb('2b2f38')             # 板の継ぎ目
BELLY = rgb('353943')
PLUG = rgb('181a21')
GLOW = rgb('35d8c6')
GLOW_L = rgb('4af5e0')
MOUTH = rgb('15161c')
TOOTH = rgb('d9d9d0')
FIN_HI = rgb('8d939d')
FIN = rgb('747a85')
FIN_LO = rgb('585d68')

# ------------------------------------------------------------ 体の輪郭: 体節 → (半幅, 下, 上)
BODY = {2: (2.25, -2.5, 2.75), 3: (2.5, -2.75, 3.25), 4: (2.5, -3.0, 3.5), 5: (2.5, -3.0, 3.5),
        6: (2.5, -3.0, 3.25), 7: (2.5, -2.75, 3.0), 8: (2.25, -2.5, 2.75), 9: (2.25, -2.25, 2.5),
        10: (2.0, -2.0, 2.25), 11: (1.75, -1.75, 2.0), 12: (1.5, -1.5, 1.75), 13: (1.25, -1.25, 1.5),
        14: (1.0, -1.0, 1.25), 15: (0.75, -0.75, 1.0), 16: (0.5, -0.5, 0.75)}
SKULL = (2.0, -2.0, 2.0)
# 石板を不ぞろいにする表（体節ごとに順に回す）: 上へのせり出し / 下端のずれ / 傾き（度）/ 横へのせり出し
LIFT = (0.5, 0.25, 0.75, 0.25, 0.5)
DROP = (0.0, -0.5, 0.25, -0.25)
TILT = (7, 11, 5, 9)
OUT = (0.25, 0.5, 0.25)
SIDE_PLATES = (3, 5, 6, 8, 10)  # 脇腹にもう 1 枚ずつ石板を貼る体節
GLOW_SEGS = (2, 3, 6, 7, 8, 11, 12)   # 隙間に発光のひびが走る体節
LAST_PLATE = 14                 # 鞍形の石板を載せる最後の体節（尾柄は裸）
RIDGE = {3: 0.5, 4: 0.7, 6: 0.6, 7: 0.4, 9: 0.5, 11: 0.5, 13: 0.5}   # 背にもう 1 枚重ねる石板（体節 → 半幅の比）


def q(v):
    return round(v * 4) / 4.0


def plate_bottom(y0, y1):
    """鞍形の石板の下端（体の高さの上 4 割を覆う）"""
    return q(y1 - 0.4 * (y1 - y0))


def body_paint(y0, y1, armored=True, glow=False):
    s0 = plate_bottom(y0, y1) if armored else y1 + 9

    def paint(face, x, y, z):
        if face == "up":
            return UNDER if armored else LOW_HI
        if face == "down":
            return BELLY
        side = face in ("east", "west")
        if armored and y >= s0 - 0.75:
            # 石板の下にのぞく隙間。発光のひびが段になって走る
            if side and glow:
                zz = z % 2.0
                if (s0 - 0.25 <= y < s0 and 0.25 <= zz < 1.0) or (s0 - 0.5 <= y < s0 - 0.25 and 0.75 <= zz < 1.75):
                    return GLOW, GLOW_L
            return UNDER
        if y < y0 + (0.5 if y1 - y0 >= 3.0 else 0.25):
            return BELLY
        if side and (z % 2.0) < 0.25:
            return GAP
        top = (s0 - 0.75) if armored else y1
        return LOW_HI if y >= top - 0.25 else LOW
    return paint


def plate_paint(tone, y0):
    def paint(face, x, y, z):
        if face == "up":
            return SL_HI
        if face in ("down", "south"):
            return SL_EDGE
        return SL_EDGE if y < y0 + 0.25 else tone
    return paint


def snout_paint(face, x, y, z):
    """鼻先。側面に発光する眼（2x2 テクセル）と暗い眼窩、口の線に歯を描く"""
    if face == "up":
        return SL_HI
    if face == "down":
        return MOUTH
    if face in ("east", "west"):
        if -0.75 <= z < -0.25 and 0.0 <= y < 0.5:
            return GLOW, GLOW_L
        if -1.0 <= z < 0.0 and -0.25 <= y < 0.75:
            return UNDER
    if y < -1.0 and face != "south":
        return TOOTH if int((x + z + 8) * 4) % 2 == 0 else MOUTH
    return SL_B if y < 0.0 else SL_A


def jaw_paint(face, x, y, z):
    return MOUTH if face == "up" else BELLY if face == "down" else LOW if y < -1.5 else LOW_HI


def fin_paint(face, x, y, z):
    if face in ("up", "down") and (z % 1.0) < 0.25:
        return FIN          # ひれの筋
    return FIN_HI if face in ("up", "north") else FIN_LO if face in ("down", "south") else FIN


def build():
    m = gbox.Model(NAME, JOINTS, atlas=512)
    m.tiles = TILES

    # ---- 頭（箱は子の skull に。pivot は首 = 関節 1）
    m.bone("skull", (0, 0, 2.0), parent="head")
    hw, y0, y1 = SKULL
    m.box("skull", (-hw, hw), (y0, y1), (0.0, 2.0), body_paint(y0, y1))                     # 頭骨
    m.box("skull", (-1.5, 1.5), (-1.25, 1.5), (-1.5, 0.0), snout_paint)                     # 鼻先
    m.box("skull", (-2.25, 2.25), (0.75, 2.5), (-0.25, 2.25), plate_paint(SL_A, 0.75),
          rot=(8, 0, 0), pivot=(0, 2.0, 1.0))                                               # 額の石板
    m.box("skull", (-1.75, 1.75), (1.0, 1.875), (-1.5, 0.25), plate_paint(SL_B, 1.0),
          rot=(12, 0, 0), pivot=(0, 1.5, -0.5))                                             # 鼻の石板
    for sx in (1, -1):
        xs = (2.0, 2.25) if sx > 0 else (-2.25, -2.0)
        m.box("skull", xs, (-1.5, 0.25), (0.5, 2.0), plate_paint(SL_B, -1.5))               # 頬の石板
    m.bone("jaw", (0, -1.25, 1.5), parent="skull")
    m.box("jaw", (-1.5, 1.5), (-2.0, -1.25), (-1.25, 0.0), jaw_paint)                       # 下顎

    # ---- 体節: 体 1 + 継ぎ目の栓 1 + 鞍形の石板 1（瓦のように後ろの縁を浮かせて重ねる）
    prev = SKULL
    for k in sorted(BODY):
        hw, y0, y1 = BODY[k]
        z0 = 2.0 * (k - 1)
        seg = f"seg_{k:02d}"
        armored = k <= LAST_PLATE
        m.box(seg, (-hw, hw), (y0, y1), (z0, z0 + 2.0), body_paint(y0, y1, armored, k in GLOW_SEGS))
        ph = min(hw, prev[0]) - 0.125
        m.box(seg, (-ph, ph), (max(y0, prev[1]) + 0.125, min(y1, prev[2]) - 0.125), (z0 - 0.5, z0 + 0.5),
              flat(PLUG))
        if armored:
            s0 = plate_bottom(y0, y1)
            pb = s0 + (DROP[k % 4] if hw >= 1.5 else 0.0)
            out = OUT[k % 3] if hw >= 1.5 else 0.25
            m.box(seg, (-hw - out, hw + out), (pb, y1 + LIFT[k % 5]), (z0 - 0.25, z0 + 2.0),
                  plate_paint(SL_A if k % 2 == 0 else SL_B, pb), rot=(TILT[k % 4], 0, 0), pivot=(0, y1, z0 + 1.0))
            if k in SIDE_PLATES:
                for sx in (1, -1):
                    xs = (hw, hw + 0.25) if sx > 0 else (-hw - 0.25, -hw)
                    m.box(seg, xs, (y0 + 0.75, s0 - 1.0), (z0 + 0.25, z0 + 1.75),
                          plate_paint(SL_B if k % 2 == 0 else SL_A, y0 + 0.75),
                          rot=(6, 0, 0), pivot=(sx * hw, (y0 + s0) / 2, z0 + 1.0))
        if k in RIDGE:
            rw = q(hw * RIDGE[k])
            m.box(seg, (-rw, rw), (y1 + 0.25, y1 + 1.25), (z0 + 0.25, z0 + 2.5),
                  plate_paint(SL_A, y1 + 0.25), rot=(13, 0, 0), pivot=(0, y1 + 0.5, z0 + 1.25))
        prev = BODY[k]

    # ---- 背びれ 2 枚・臀びれ（厚い石の板。箱どうしは重ねず接する）
    m.bone("dorsal_1", (0, 2.5, 16.5), parent="seg_09")
    for z, y in (((15.25, 18.5), (2.75, 3.75)), ((16.25, 19.0), (3.75, 4.75)), ((17.5, 19.25), (4.75, 5.25))):
        m.box("dorsal_1", (-0.25, 0.25), y, z, fin_paint)
    m.bone("dorsal_2", (0, 1.75, 22.5), parent="seg_12")
    for z, y in (((21.75, 24.5), (1.75, 2.75)), ((22.75, 25.0), (2.75, 3.5)), ((23.75, 25.25), (3.5, 4.0))):
        m.box("dorsal_2", (-0.25, 0.25), y, z, fin_paint)
    m.bone("anal", (0, -1.25, 24.5), parent="seg_13")
    for z, y in (((24.0, 26.0), (-2.0, -1.25)), ((25.0, 26.5), (-2.75, -2.0))):
        m.box("anal", (-0.25, 0.25), y, z, fin_paint)

    # ---- 肉厚の葉状のひれ（柄 + 葉 + 先。胸びれと腹びれ）
    for sx, side in ((1, "l"), (-1, "r")):
        def mx(a, b, sx=sx):
            return (a, b) if sx > 0 else (-b, -a)
        m.bone(f"fin_{side}", (sx * 2.5, -2.0, 6.75), parent="seg_04", rot=(0, sx * -12, sx * 32))
        m.box(f"fin_{side}", mx(2.5, 3.5), (-2.25, -1.75), (6.0, 7.5), fin_paint)
        m.box(f"fin_{side}", mx(3.5, 6.0), (-2.25, -1.75), (5.5, 9.0), fin_paint)
        m.box(f"fin_{side}", mx(6.0, 7.0), (-2.25, -1.75), (6.5, 8.75), fin_paint)
        m.bone(f"pelvic_{side}", (sx * 2.25, -1.75, 16.75), parent="seg_09", rot=(0, sx * -12, sx * 36))
        m.box(f"pelvic_{side}", mx(2.25, 3.0), (-2.0, -1.5), (16.25, 17.5), fin_paint)
        m.box(f"pelvic_{side}", mx(3.0, 4.5), (-2.0, -1.5), (16.0, 18.5), fin_paint)
        m.box(f"pelvic_{side}", mx(4.5, 5.25), (-2.0, -1.5), (16.75, 18.25), fin_paint)

    # ---- 尾びれ（上葉 4 段・下葉 3 段・後ろ端の葉）
    m.bone("tail_fin", (0, 0.125, 28.5), parent="seg_15")
    for z, y in (((28.5, 31.0), (1.0, 2.0)), ((29.25, 32.0), (2.0, 3.0)), ((30.25, 32.5), (3.0, 4.0)),
                 ((31.25, 32.5), (4.0, 4.5)), ((28.75, 31.0), (-1.75, -0.75)), ((29.5, 31.75), (-2.75, -1.75)),
                 ((30.5, 32.0), (-3.25, -2.75)), ((32.0, 32.5), (-0.75, 1.0))):
        m.box("tail_fin", (-0.25, 0.25), y, z, fin_paint)
    return m


def animations():
    IDLE, ATTACK, JAW_OUT = 4.0, 1.0, 0.35
    # idle はひれと尾だけ（skull と jaw は attack / jaw_out の受け持ち。同じ骨を 2 つの controller で取り合わない）
    idle = {"tail_fin": wave(IDLE, 7.0, -1.2, (0, 1, 0)),
            "dorsal_1": wave(IDLE, 2.0, -0.4, (0, 0, 1)), "dorsal_2": wave(IDLE, 2.5, -0.8, (0, 0, 1)),
            "anal": wave(IDLE, 2.5, -0.9, (0, 0, 1))}
    for sx, side in ((1, "l"), (-1, "r")):
        idle[f"fin_{side}"] = wave(IDLE, 8.0, 0.0, (0.0, 0.5 * sx, 1.0 * sx))
        idle[f"pelvic_{side}"] = wave(IDLE, 6.0, -1.0, (0.0, 0.5 * sx, 1.0 * sx))
    zero = (0.0, 0.0, 0.0)
    attack = {
        # 頭を片側へ引いてから反対へ振り抜く体当たり。同時に少し前へ突く
        "skull": merge(
            rot([(0.0, zero), (0.32, (-3.0, -14.0, 0.0), EASE_SLOW), (0.36, (-3.0, -14.0, 0.0)),
                 (0.48, (4.0, 22.0, 0.0), EASE_FAST), (0.60, (2.0, 16.0, 0.0), EASE_HIT),
                 (0.80, (0.0, 5.0, 0.0), EASE_BACK), (ATTACK, zero, EASE_BACK)]),
            pos([(0.0, zero), (0.32, (0.0, 0.0, 4.0), EASE_SLOW), (0.48, (0.0, 0.0, -10.0), EASE_FAST),
                 (0.80, (0.0, 0.0, -3.0), EASE_BACK), (ATTACK, zero, EASE_BACK)])),
        "jaw": rot([(0.0, zero), (0.32, (6.0, 0.0, 0.0), EASE_SLOW), (0.48, (16.0, 0.0, 0.0), EASE_FAST),
                    (0.80, (5.0, 0.0, 0.0), EASE_BACK), (ATTACK, zero, EASE_BACK)]),
    }
    # 全種共通の "action" controller が追跡中に保持する。石鱗ウオでは口を少し開けて頭を下げるだけ
    jaw_out = {
        "skull": rot([(0.0, zero), (0.25, (5.0, 0.0, 0.0), EASE_SOFT), (JAW_OUT, (5.0, 0.0, 0.0))]),
        "jaw": rot([(0.0, zero), (0.25, (9.0, 0.0, 0.0), EASE_SOFT), (JAW_OUT, (9.0, 0.0, 0.0))]),
    }
    return {"idle": {"loop": True, "animation_length": IDLE, "bones": idle},
            "attack": {"loop": False, "animation_length": ATTACK, "bones": attack},
            "jaw_out": {"loop": "hold_on_last_frame", "animation_length": JAW_OUT, "bones": jaw_out}}


CONTRACT = {
    "design": "石鱗ウオ。GNT01-design-sheet.png の 5 番。頁岩の灰色の石板を瓦のように重ねた装甲の古代魚、"
              "肉厚の葉状のひれ、装甲の隙間に青緑の発光点、発光する眼",
    "glow_note": "光らない画素は (0,0,0,0)。光るのは石板の下の隙間に並ぶ発光点（青緑）と眼。"
                 "RenderType.eyes（加算）か GeckoLib の AutoGlowingGeoLayer 相当で、同じ geo に重ねる",
    "total_length_blocks": TOTAL,
    "head": "head は Java が上書きするので箱を持たない。頭の箱はすべて子の skull（pivot = 関節 1 の首）と、その子の jaw",
    "animation_signs": {
        "skull": "rotation y で頭を左右に振る、position z（geo 単位）が負で前へ突く",
        "jaw": "下顎（skull の子）。rotation x が正で開く",
        "fin_l / fin_r / pelvic_l / pelvic_r": "胸びれ（seg_04）と腹びれ（seg_09）。付け根が pivot、z で上下に煽る",
        "dorsal_1 / dorsal_2 / anal": "背びれ（seg_09 / seg_12）と臀びれ（seg_13）。z で左右に揺れる",
        "tail_fin": "尾びれ（seg_15 の子）。y で左右に煽る",
    },
}


def main():
    m = build()
    m.write(animations(), CONTRACT, TOTAL, 10.0)


if __name__ == '__main__':
    main()
