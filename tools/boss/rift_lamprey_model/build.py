# -*- coding: utf-8 -*-
"""裂溝ヤツメ（rift_lamprey、GNT01 の 6 番）の GeckoLib モデルを書き出す。

```bash
python tools/boss/rift_lamprey_model/build.py          # geo / animation / texture(+glow) / contract.json
python tools/boss/check_animations.py rift_lamprey     # ボーン名・キーの順・尺
python tools/boss/rift_lamprey_model/preview.py        # 図を out/ へ
```

出力は手で直さない（ここを直して流し直す）。形は大きな直方体だけ（バニラのガーディアンやイルカの作り）:
体節ごとに 八角断面の箱 2 つ + 継ぎ目を埋める芯 1 つ + 背と腹の低いひれ。頭の正面は八角の輪（吸盤）で、
内側に放射状の歯 12 本、中心は暗い穴。テクスチャは 1 テクセル 0.25 ブロックの平らな 3 段。
"""
import json
import math
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import cube_kit as ck
from cube_kit import SEG

NAME = "rift_lamprey"

# ------------------------------------------------------------------ 素材タイル（ChatGPT のシート。giant_tiles.py 参照）
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import giant_tiles                      # noqa: E402
MATERIALS = [
    ('back', 'deep indigo-violet skin with faint segment seams and darker mottling', '2d2759 1d1a3d 231e48 161331 120f26'),
    ('belly', 'lilac-purple underside skin with soft ring creases', '6c62a4 554c88'),
    ('collar', 'pale lavender fleshy sucker collar with fine radial ridges', '7a71ab 5a5289 9c92c8'),
    ('fin', 'blue-indigo thin fin membrane with lighter ribs', '3a4a86 5669ab 544c8e'),
    ('flesh', 'dark plum mouth flesh with wet deeper-purple folds', '3a1d45 1b0e25 06040c'),
    ('tooth', 'cream and tan sharp tooth enamel with fine vertical grain', 'ede0c8 b7a38c'),
    ('sleeve', 'magenta-purple muscular sleeve skin with ring grooves', '5a2f6a 43204f'),
]
TILES = giant_tiles.Tiler(NAME, MATERIALS)
JOINTS = 23                    # 全長 44 ブロック / 2 + 1
SEGS = JOINTS - 1              # seg_01..seg_22
BODY = 21                      # 箱を持つ体節（seg_22 は尾の先より後ろなので空）
HEAD_Z = -16.0                 # 吸盤の正面（関節 0 の 2 ブロック前）。尾の先は z = 336 → 全長 352 単位 = 44 ブロック

# 体節 k の高さ（単位、8 = 1 ブロック）。幅は同じ（八角断面）
H = [22, 22, 22, 22, 22, 22, 20, 20, 20, 20, 20, 18, 18, 18, 16, 16, 14, 12, 10, 8, 6]
# 背びれ（低い山が 2 つ）と腹びれ（後ろ半分）の高さ
DORSAL = [0, 0, 0, 2, 4, 6, 8, 8, 6, 4, 2, 2, 4, 6, 10, 10, 8, 8, 6, 4, 2]
VENTRAL = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 4, 4, 4, 4, 4, 2, 2]
assert len(H) == len(DORSAL) == len(VENTRAL) == BODY

# ---------------------------------------------------------------- 色（平らな 3 段 + 差し色）
BACK = 0x1D1A3D
SIDE = 0x2D2759
SIDE_SEAM = 0x231E48
BACK_SEAM = 0x161331
BELLY = 0x6C62A4
BELLY_SEAM = 0x554C88
CORE = 0x120F26
FIN = 0x3A4A86
FIN_HI = 0x5669AB
VFIN = 0x544C8E
DOT = 0x45E6DC
DOT_GLOW = 0x45E6DC
EYE = 0xFFC98A
COLLAR = 0x7A71AB
COLLAR_DK = 0x5A5289
LIP = 0x9C92C8
FLESH = 0x3A1D45
FLESH_DK = 0x1B0E25
HOLE = 0x06040C
TOOTH = 0xEDE0C8
TOOTH_DK = 0xB7A38C
SLEEVE = 0x5A2F6A
SLEEVE_DK = 0x43204F


def body_paint(k, wide):
    """体節の箱。world の高さで 背 / 側 / 腹 の 3 段、前端の 1 列は継ぎ目の暗い輪。wide の側面に光る点"""
    h = H[k - 1]
    z0 = SEG * (k - 1)
    mid = z0 + SEG / 2
    big = k <= 14

    def paint(f):
        seam = f.face in ("north", "south") or f.z - z0 < 2
        if f.face == "up" or f.y > h * 0.27:
            return BACK_SEAM if seam else BACK
        if f.face == "down" or f.y < -h * 0.27:
            return BELLY_SEAM if seam else BELLY
        if wide and f.face in ("east", "west") and k <= 19:
            # 点は 2x2 テクセル（0.5 ブロック）、尾では 1 テクセル。行は面の中央から数える
            rows = (f.fh // 2 - 1, f.fh // 2) if big else (f.fh // 2,)
            if f.r in rows and (abs(f.z - mid) < 2 if big else 0 < f.z - mid < 2):
                return (DOT, DOT_GLOW)
        return SIDE_SEAM if seam else SIDE
    return paint


def fin_paint(base, top):
    def paint(f):
        if top > 0:
            return FIN_HI if f.face == "up" or f.y > top - 2 else FIN
        return VFIN
    return paint


def flat(c):
    return lambda f: c


def collar_paint(inner):
    """吸盤の輪。正面は明るい唇、穴に向く面（inner）は暗い肉、外側は灰紫で後ろの 1 列が暗い"""
    def paint(f):
        if f.face == inner:
            return FLESH
        if f.face == "north":
            return LIP
        if f.face == "south" or f.z > -8:
            return COLLAR_DK
        return COLLAR
    return paint


def throat_paint(f):
    r = math.hypot(f.x, f.y)
    return HOLE if r < 6 else FLESH_DK if r < 10 else FLESH


def tooth_paint(f):
    return TOOTH_DK if f.y > 10 or f.face == "south" else TOOTH


def neck_paint(wide):
    h = H[0]

    def paint(f):
        if f.face == "up" or f.y > h * 0.27:
            return BACK
        if f.face == "down" or f.y < -h * 0.27:
            return BELLY
        if wide and f.face in ("east", "west") and f.r == f.fh // 2 - 1 and -6 < f.z < -4:
            return (EYE, EYE)
        return SIDE
    return paint


def sleeve_paint(f):
    return SLEEVE_DK if int((f.z + 16) // 2) % 2 else SLEEVE


def build_model():
    m = ck.Model(NAME, size=128)
    m.tiles = TILES

    # ---- 頭: 首（関節 0 の前 1 ブロック）と吸盤
    m.bone("head", pivot=(0, 0, 0))
    h = H[0]
    m.box("head", -h / 2 + 4, h / 2 - 4, -h / 2, h / 2, -8, 0, neck_paint(False))
    m.box("head", -h / 2, h / 2, -h / 2 + 4, h / 2 - 4, -8, 0, neck_paint(True))
    # 喉の筒: 突き出すときに scale z で伸びて、吸盤と首のすき間を埋める（静止では首の中）
    m.bone("sleeve", "head", pivot=(0, 0, 0))
    m.box("sleeve", -6, 6, -6, 6, -8, 0, sleeve_paint)
    # 吸盤: 八角の輪（外径 36 = 4.5 ブロック、穴 24）。mouth を前へ出し、scale xy で開く
    m.bone("mouth", "head", pivot=(0, 0, -6))
    Z0, Z1 = HEAD_Z, -6
    m.box("mouth", -8, 8, 12, 18, Z0, Z1, collar_paint("down"))
    m.box("mouth", -8, 8, -18, -12, Z0, Z1, collar_paint("up"))
    m.box("mouth", 12, 18, -8, 8, Z0, Z1, collar_paint("east"))
    m.box("mouth", -18, -12, -8, 8, Z0, Z1, collar_paint("west"))
    for sx in (1, -1):
        for sy in (1, -1):
            x0, x1 = sorted((8 * sx, 14 * sx))
            y0, y1 = sorted((8 * sy, 14 * sy))
            m.box("mouth", x0, x1, y0, y1, Z0, Z1, collar_paint(None))
    m.box("mouth", -13, 13, -13, 13, -9, -7, throat_paint)
    # 歯: 放射状に 12 本（30 度おき）。teeth を z で回すと削る動きになる
    m.bone("teeth", "mouth", pivot=(0, 0, -14))
    for i in range(12):
        m.box("teeth", -1, 1, 5, 13, -15, -13, tooth_paint, rot=(0, 0, 30 * i), pivot=(0, 0, -14))

    # ---- 体節: seg_k は Java が鎖へ置く。箱は子の body_k に持たせ、idle の進行波は body_k を動かす
    for k in range(1, SEGS + 1):
        pv = (0, 0, SEG * (k - 0.5))
        m.bone(f"seg_{k:02d}", pivot=pv)
        if k > BODY:
            continue
        b = m.bone(f"body_{k:02d}", f"seg_{k:02d}", pivot=pv)
        h = H[k - 1]
        z0, z1 = SEG * (k - 1), SEG * k
        if h >= 14:
            m.box(b, -h / 2 + 4, h / 2 - 4, -h / 2, h / 2, z0, z1, body_paint(k, False))
            m.box(b, -h / 2, h / 2, -h / 2 + 4, h / 2 - 4, z0, z1, body_paint(k, True))
            # 継ぎ目の栓: 曲がったときに外側へ開くすき間を暗い色で埋める（まっすぐなら箱の中）
            core = h / 2 - 4
            m.box(b, -core, core, -core, core, z0 - 4, z0 + 4, flat(CORE))
        else:
            # 尾: 縦に平たい 1 箱（幅は高さより 2 細い）
            w = max(2, h - 4)
            m.box(b, -w / 2, w / 2, -h / 2, h / 2, z0, z1, body_paint(k, True))
            cw = min(w / 2 - 0.5, 2.5)
            m.box(b, -cw, cw, -h / 2 + 1, h / 2 - 1, z0 - 4, z0 + 4, flat(CORE))
        d, v = DORSAL[k - 1], VENTRAL[k - 1]
        if d:
            m.box(b, -1, 1, h / 2, h / 2 + d, z0, z1, fin_paint(h / 2, h / 2 + d))
        if v:
            m.box(b, -1, 1, -h / 2 - v, -h / 2, z0, z1, fin_paint(-h / 2, 0))
    return m


# ==================================================================== アニメーション
IDLE = 4.0
ATTACK = 1.2
LATCH = 0.3
GRIND = 0.8


def swim_amp(k):
    """頭は振らず、5 節かけて 0.75 ブロックまで、尾はさらに大きく"""
    if k > BODY:
        return 0.0
    return 6.0 * min(1.0, (k - 1) / 5.0) + 0.4 * max(0, k - 14)


def mouth_pose(push, open_):
    """push = 前へ出す量（単位）、open_ = 輪の拡がり（scale xy）。喉の筒は同じ時刻に伸ばす"""
    return (0.0, 0.0, -push), (open_, open_, 1.0), (1.0, 1.0, 1.0 + push / 8.0)


def animations():
    idle = ck.swim_tracks(IDLE, SEGS, swim_amp, wavelength=11.0)
    # 吸盤がゆっくりすぼまっては戻る
    idle["mouth"] = {"scale": ck.keys([(0.0, (1, 1, 1)), (1.0, (0.94, 0.94, 1)), (2.0, (1, 1, 1)),
                                       (3.0, (0.94, 0.94, 1)), (4.0, (1, 1, 1))])}

    def mouth_track(frames):
        p, s, sl = [], [], []
        for f in frames:
            t, push, open_ = f[0], f[1], f[2]
            ease = f[3:]
            a, b, c = mouth_pose(push, open_)
            p.append((t, a) + ease)
            s.append((t, b) + ease)
            sl.append((t, c) + ease)
        return {"mouth": {"position": ck.keys(p), "scale": ck.keys(s)}, "sleeve": {"scale": ck.keys(sl)}}

    # attack: すぼめて引く → 開いて 1.75 ブロック突き出す → 少し保って戻る
    attack = mouth_track([
        (0.0, 0, 1.0),
        (0.35, -2, 0.85, "easeinsine"),
        (0.50, 14, 1.35, "easeinquint"),
        (0.80, 12, 1.30),
        (1.20, 0, 1.0, "easeoutcubic"),
    ])
    attack["teeth"] = {"rotation": ck.keys([(0.0, (0, 0, 0)), (0.5, (0, 0, 0)), (0.8, (0, 0, 60)),
                                            (1.2, (0, 0, 60))])}
    # latch: 開いたまま保持（最後のコマで止める）
    latch = mouth_track([(0.0, 0, 1.0), (LATCH, 6, 1.3, "easeoutcubic")])
    # latch_grind: 開いたまま歯を回して削るループ（歯は 30 度おきなので 30 度回すと継ぎ目なく回る）
    grind = mouth_track([(0.0, 6, 1.3), (GRIND, 6, 1.3)])
    grind["teeth"] = {"rotation": ck.keys([(0.0, (0, 0, 0)), (GRIND, (0, 0, 30))])}
    return {
        "idle": {"loop": True, "animation_length": IDLE, "bones": idle},
        "attack": {"loop": False, "animation_length": ATTACK, "bones": attack},
        "latch": {"loop": "hold_on_last_frame", "animation_length": LATCH, "bones": latch},
        "latch_grind": {"loop": True, "animation_length": GRIND, "bones": grind},
    }


def contract(m, anims):
    return {
        "entity": NAME,
        "generator": "tools/boss/rift_lamprey_model/build.py（手で直さず、生成元を直して流し直す）",
        "geo": f"assets/abyssia/geo/entity/{NAME}.geo.json",
        "animation": f"assets/abyssia/animations/entity/{NAME}.animation.json",
        "texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo.png",
        "glow_texture": f"assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png",
        "glow_note": "光らない画素は (0,0,0,0)。光るのは体側の点の列（青緑、seg_01..19）と首の小さな目（淡い橙）",
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
        "extent_units": {"mouth_z": HEAD_Z, "tail_z": SEG * BODY, "total_length_blocks": (SEG * BODY - HEAD_Z) / 8,
                         "max_half_width": 18.0, "max_top_y": 18.0, "min_bottom_y": -18.0},
        "bone_rule": ("head と seg_01..seg_22 は root 直下で、Java が毎フレーム位置と回転を上書きする。"
                      "箱は子の body_01..body_21 にあり（seg_22 は尾の先より後ろで空）、idle の進行波は body_k の "
                      "position x と rotation y。Java は body_k / mouth / teeth / sleeve に触らない"),
        "mouth": {
            "bones": "mouth（head の子: 吸盤の輪と喉の壁）→ teeth（歯 12 本）。sleeve（head の子: 喉の筒）",
            "rule": ("突き出し量 p（単位）のとき mouth position = (0, 0, -p)、sleeve scale z = 1 + p / 8 を同じ時刻に入れる。"
                     "開きは mouth scale xy（静止 1、開く 1.3〜1.35）。teeth の rotation z で削る"),
            "attack_reach_blocks": 1.75,
        },
        "animations": {n: {"loop": a["loop"], "length": a["animation_length"], "bones": sorted(a["bones"])}
                       for n, a in anims.items()},
        "animation_notes": {
            "idle": "蛇行遊泳ループ（頭→尾へ進む波、振幅 0.75 ブロック、波長 22 ブロック）と吸盤の伸縮",
            "attack": "吸盤をすぼめて引き、開いて 1.75 ブロック突き出す 1 回。当たりは 0.5 秒",
            "latch": "吸盤を開いて 0.75 ブロック出したまま保持（hold_on_last_frame）",
            "latch_grind": "追加。latch と同じ姿勢で歯を回すループ（吸い付いて削る間に使える。使わなくてもよい）",
        },
        "cube_count": m.cube_count(),
        "bones": [{"name": b["name"], "parent": b.get("parent"), "pivot": b["pivot"], "cubes": len(b["cubes"])}
                  for b in m.bones],
    }


def main():
    m = build_model()
    anims = animations()
    m.write(anims, bounds_w=48.0, bounds_h=8.0)
    (HERE / "contract.json").write_text(json.dumps(contract(m, anims), ensure_ascii=False, indent=1), encoding='utf-8')
    print(f"{NAME}: bones={len(m.bones)} cubes={m.cube_count()} atlas={m.size} "
          f"(used rows {m._shelf_y + m._shelf_h})")


if __name__ == '__main__':
    main()
