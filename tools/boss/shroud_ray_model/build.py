# -*- coding: utf-8 -*-
"""帳エイ（shroud_ray、GNT01 の 7 番）の GeckoLib モデルを生成する。

```bash
python tools/boss/shroud_ray_model/build.py            # geo / animation / texture(+glow) / contract.json
python tools/boss/check_animations.py shroud_ray       # ボーン名・キーの順・尺
python tools/boss/shroud_ray_model/preview.py          # out/ に横・上・斜め前・姿勢の図と比較画像
```

出力は手で直さない（ここを直して流し直す）。バニラ風の大きな箱だけで組む（`boxkit.py`）。

- MODEL_SCALE 2（geo 1 単位 = 1/8 ブロック）。全長 32 ブロック、17 関節・間隔 16 単位。頭が -Z
- `head` と `seg_01`〜`seg_16` は root 直下（関節 j = (0,0,16j)、seg_k の pivot = (0,0,16(k-0.5))）
- 体の円盤は head〜seg_08。翼は体節ごとの帯 `wing_l_k` / `wing_r_k`（k=1 は head の子、k=2..8 は seg_k の子）と、
  その外半分 `wing_l_k_tip` / `wing_r_k_tip`。l が geo の +X
- 尾は seg_08 の子の鎖 `tail_01`〜`tail_08`（attack で前へ振り上げるため体節鎖に載せない。seg_09〜16 は箱なし）
"""
import json
import math
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import boxkit
from boxkit import Model, hexrgb, cell, keys, wave, anim

NAME = 'shroud_ray'

# ------------------------------------------------------------------ 素材タイル（ChatGPT のシート。giant_tiles.py 参照）
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import giant_tiles                      # noqa: E402
MATERIALS = [
    ('back', 'dark indigo ray skin with faint darker speckles and soft mottling', '1c2556 141a3e 28346f'),
    ('flank', 'deep navy-indigo skin, very faint scale rows', '111737'),
    ('belly', 'almost black indigo underside, smooth with faint creases', '0e1330 090c20'),
    ('horn', 'pale glowing mint horn tip with thin lengthwise ridges', 'a8f2ec'),
]
TILES = giant_tiles.Tiler(NAME, MATERIALS)
SEG = 16
JOINTS = 17
ATLAS = 256

# ---- 色（平らな 2〜3 段） ----
TOP = [hexrgb(c) for c in ('#141a3e', '#1c2556', '#28346f')]     # 背: 暗い藍
UNDER = [hexrgb(c) for c in ('#090c20', '#0e1330')]              # 腹: ほぼ黒の藍
SIDE = hexrgb('#111737')
SPOT = hexrgb('#3fe0d2')       # 発光斑点
RIM = hexrgb('#7df6e6')        # 翼の縁（発光）
RIM2 = hexrgb('#1d8195')       # 縁の内側のぎざぎざ（発光）
EYE = hexrgb('#9ffaf0')
HORN_TIP = hexrgb('#a8f2ec')

# ---- 平面形（上から見た階段状の三角翼）。帯 k = 1..8 の外端 x と、体の半幅 ----
OUT = {1: 64, 2: 104, 3: 136, 4: 156, 5: 160, 6: 136, 7: 96, 8: 48}
HALF = {1: 20, 2: 20, 3: 20, 4: 20, 5: 20, 6: 16, 7: 12, 8: 8}
BODY_Y = {1: (-4, 8), 2: (-6, 10), 3: (-6, 12), 4: (-6, 12), 5: (-6, 10), 6: (-4, 8), 7: (-4, 6), 8: (-4, 4)}
WING_TOP = 4
TAIL = [8, 8, 6, 6, 4, 4, 4, 4]          # tail_01..08 の太さ
DROOP = 6.0                               # 静止で翼が外へ下がる角度（帯と外半分のそれぞれ）

# 発光斑点（+X 側の翼の上面。左右対称に出す）。(x, z) は 2×1 テクセルの短い線の左前の隅
SPOTS = [(40, 6), (60, 22), (88, 24), (36, 36), (72, 42), (108, 38), (124, 52), (48, 56), (90, 60), (140, 68),
         (32, 74), (66, 78), (112, 76), (48, 94), (84, 92), (120, 94), (34, 106), (62, 110), (86, 106)]


def strip(z):
    return max(1, min(8, int(z // SEG) + 1))


def skin(face, x, y, z):
    """体の円盤と翼。上から見た位置で塗るので、箱をまたいで縁と斑点がつながる"""
    k = strip(z)
    ax = abs(x)
    out, nxt = OUT[k], OUT.get(k + 1, 0)
    on_wing = ax > HALF[k]
    # 縁: 翼端の列と、後ろの帯より外へ出ている後縁の行
    tip_col = on_wing and ax >= out - 2 and k >= 3
    trail_row = on_wing and k >= 4 and (z % SEG) >= SEG - 2 and ax > nxt
    if tip_col or trail_row:
        return RIM, True
    inner_col = on_wing and ax >= out - 4 and k >= 3
    inner_row = on_wing and k >= 4 and (z % SEG) >= SEG - 4 and ax > nxt - 2
    if (inner_col or inner_row) and (int(ax // 2) + int(z // 2)) % 2 == 0:
        return RIM2, True
    if face == 'down':
        return UNDER[1 if cell(x, 0, z, 8, 3) > 0.7 else 0], False
    if face != 'up':
        return SIDE, False
    for sx, sz in SPOTS:
        if sx <= ax < sx + (6 if sz % 4 else 4) and sz <= z < sz + 4:
            return SPOT, True
    if not on_wing and ax < 8:
        return TOP[2], False                       # 背の中央の明るい帯
    if on_wing and k <= 3 and ax >= out - 4:
        return TOP[2], False                       # 前縁は明るい藍（光らない）
    return TOP[0 if cell(x, 0, z, 8, 1) > 0.72 else 1], False


def head_paint(face, x, y, z):
    if face in ('east', 'west') and -8 <= z < -4 and 0 <= y < 4:
        return EYE, True
    return skin(face, x, y, z)


def horn(face, x, y, z):
    return (TOP[2] if face == 'up' else TOP[1] if face in ('east', 'west') else TOP[0]), False


def horn_tip(face, x, y, z):
    return (HORN_TIP, True) if y < -8 else (TOP[1], False)


def tail_paint(face, x, y, z):
    return (TOP[1] if face == 'up' else UNDER[1] if face == 'down' else TOP[0]), False


def barb(face, x, y, z):
    return (RIM if face in ('up', 'north', 'south') else SPOT), True


def fin(face, x, y, z):
    return (TOP[2] if face == 'up' else TOP[1] if face in ('east', 'west') else TOP[0]), False


def build():
    m = Model(NAME, ATLAS, 2.0)
    m.tiles = TILES
    m.bone('head', pivot=(0, 0, 0))
    for k in range(1, JOINTS):
        m.bone(f'seg_{k:02d}', pivot=(0, 0, SEG * (k - 0.5)))

    # 体の円盤（頭は関節 1 まで）。短い角のような頭びれが前下へ出る
    y0, y1 = BODY_Y[1]
    m.cube('head', (-HALF[1], y0, -12), (HALF[1], y1, SEG), head_paint)
    for s in (1, -1):
        m.cube('head', (s * 10, -6, -24), (s * 18, 2, -12), horn)
        m.cube('head', (s * 10, -14, -28), (s * 18, -2, -22), horn_tip)
    for k in range(2, 9):
        y0, y1 = BODY_Y[k]
        m.cube(f'seg_{k:02d}', (-HALF[k], y0, SEG * (k - 1)), (HALF[k], y1, SEG * k), skin)
    # 背の低いひれ（尾の付け根の前。階段 3 段）
    m.cube('seg_06', (-2, 8, 76), (2, 16, 96), fin)
    m.cube('seg_07', (-2, 6, 96), (2, 24, 112), fin)
    m.cube('seg_07', (-2, 24, 104), (2, 32, 118), fin)

    # 翼: 体節ごとの帯。内半分は厚く、外半分は薄い
    for k in range(1, 9):
        parent = 'head' if k == 1 else f'seg_{k:02d}'
        z0, z1, zm = SEG * (k - 1), SEG * k, SEG * (k - 0.5)
        root = HALF[k]
        mid = root + 4 * round((OUT[k] - root) / 8)
        for side, s in (('l', 1), ('r', -1)):
            b = m.bone(f'wing_{side}_{k}', parent, pivot=(s * root, 2, zm), rotation=(0, 0, s * DROOP))
            m.cube(b, (s * (root - 2), -2, z0), (s * mid, WING_TOP, z1), skin)
            t = m.bone(f'wing_{side}_{k}_tip', b, pivot=(s * mid, 2, zm), rotation=(0, 0, s * DROOP))
            m.cube(t, (s * mid, 0, z0), (s * OUT[k], WING_TOP, z1), skin)

    # 尾: seg_08 の子の鎖。先に光る棘
    parent = 'seg_08'
    for i, w in enumerate(TAIL):
        z0 = SEG * 8 + SEG * i
        b = m.bone(f'tail_{i + 1:02d}', parent, pivot=(0, 0, z0))
        m.cube(b, (-w / 2, -w / 2, z0), (w / 2, w / 2, z0 + SEG), tail_paint)
        parent = b
    m.cube(parent, (-5, -2, 236), (5, 2, 244), barb)
    m.cube(parent, (-3, -2, 244), (3, 2, 250), barb)
    m.cube(parent, (-1, -1, 250), (1, 1, 256), barb)
    return m


def animations():
    out = {}
    # idle: 前から後ろへ波が流れる、ゆっくりした羽ばたき。尾はわずかに揺れる
    b = {}
    for k in range(1, 9):
        lag = -0.45 * (k - 1)
        for side, s in (('l', 1), ('r', -1)):
            b[f'wing_{side}_{k}'] = wave(4.0, (0, 0, s * 9), lag)
            b[f'wing_{side}_{k}_tip'] = wave(4.0, (0, 0, s * 13), lag - 0.7)
    for i in range(8):
        b[f'tail_{i + 1:02d}'] = wave(4.0, (1.5, 2.5, 0), -0.5 * i)
    out['idle'] = anim(4.0, True, b)

    # attack: 尾を下げて溜め、背の上を越えて前へ振り上げて刺し、戻す（1 回）
    b = {}
    for i in range(8):
        dip = 4.0 if i < 4 else 2.0
        curl = 14.0 + 2.0 * i                     # 先ほど強く巻く。合計 168 度で棘が頭の上へ来る
        b[f'tail_{i + 1:02d}'] = keys([(0.0, (0, 0, 0)), (0.30, (-dip, 0, 0)), (0.50, (curl, 0, 0)),
                                       (0.70, (curl * 0.92, 0, 0)), (1.20, (0, 0, 0))])
    for k in range(1, 9):
        for side, s in (('l', 1), ('r', -1)):
            b[f'wing_{side}_{k}_tip'] = keys([(0.0, (0, 0, 0)), (0.30, (0, 0, s * 10)), (0.50, (0, 0, s * -8)),
                                              (1.20, (0, 0, 0))])
    out['attack'] = anim(1.2, False, b)

    # shroud: 翼を下へ包み込むように曲げて保持
    b = {}
    for k in range(1, 9):
        for side, s in (('l', 1), ('r', -1)):
            b[f'wing_{side}_{k}'] = keys([(0.0, (0, 0, 0)), (0.8, (0, 0, s * 32))])
            b[f'wing_{side}_{k}_tip'] = keys([(0.0, (0, 0, 0)), (0.8, (0, 0, s * 46))])
    out['shroud'] = anim(0.8, 'hold_on_last_frame', b)
    return out


def contract(m, anims):
    return {
        'entity': NAME,
        'generator': 'tools/boss/shroud_ray_model/build.py（出力は手で直さず、生成元を直して流し直す）',
        'geo': f'assets/abyssia/geo/entity/{NAME}.geo.json',
        'animation': f'assets/abyssia/animations/entity/{NAME}.animation.json',
        'texture': f'assets/abyssia/textures/entity/boss/{NAME}_geo.png',
        'glow_texture': f'assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png',
        'texture_size': [ATLAS, ATLAS],
        'texel_blocks': 0.25,
        'model_scale': 2.0,
        'segment_length_units': SEG, 'segment_length_blocks': 2.0,
        'joints': JOINTS, 'segments': JOINTS - 1,
        'rest_pose': '頭から尾まで一直線。関節 j は geo の (0, 0, 16j)。頭が -Z。体の芯は y = 0',
        'head_pivot': [0, 0, 0],
        'segment_pivot': 'seg_k の pivot = (0, 0, 16 * (k - 0.5))',
        'extent_units': {'front_z': -28, 'tail_z': 256, 'half_span': 160, 'total_length_blocks': 35.5,
                         'wingspan_blocks': 40},
        'bone_rule': 'head と seg_01..seg_16 は root 直下で、体節鎖が毎フレーム上書きする。seg_01 と seg_09..seg_16 は箱なし。'
                     '子の骨（wing_*, tail_*）は animation だけが動かす',
        'child_bones': {
            'wing_l_k / wing_r_k (k=1..8)': '翼の帯の内半分。k=1 は head、k=2..8 は seg_k の子。pivot は付け根。l が geo の +X',
            'wing_l_k_tip / wing_r_k_tip': '帯の外半分。pivot は帯の中ほど',
            'tail_01..tail_08': '尾の鎖（seg_08 の子、前の節の子）。pivot は各節の前端。tail_08 の先に光る棘',
        },
        'animation_signs': {
            'wing_l_*': 'rotation z 正で下がる（r は負で下がる）。静止で 6 度ずつ下げてある',
            'tail_*': 'rotation x 正で上へ（前へ）巻く。y で左右',
        },
        'animations': {n: {'loop': a['loop'], 'length': a['animation_length'], 'bones': len(a['bones'])}
                       for n, a in anims.items()},
        'tail_note': '尾は体節鎖（seg_09..16）ではなく seg_08 の子の鎖。実行時の鎖の曲がりは尾には出ない（attack で振り上げるため）',
        'cube_count': m.count,
        'bones': [{k: v for k, v in b.items() if k != 'cubes'} for b in m.bones],
    }


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    m = build()
    anims = animations()
    m.write(anims)
    (HERE / 'contract.json').write_text(json.dumps(contract(m, anims), ensure_ascii=False, indent=1),
                                        encoding='utf-8')
    return m, anims


if __name__ == '__main__':
    main()
