# -*- coding: utf-8 -*-
"""静寂鯨（hush_whale、GNT01 の 4 番）の GeckoLib モデルを書き出す。

    python tools/boss/hush_whale_model/build.py
    python tools/boss/check_animations.py hush_whale
    python tools/boss/hush_whale_model/preview.py

敵対の鯨型、全長 48 ブロック（25 関節・24 体節）。暗い紫黒の巨体、額を覆う灰色の段になった装甲板、
頭の両側の光る青緑の輪、体側の光る点、灰色の腹と下顎、縦の三日月形の尾。
大きな箱を少数組むバニラ風（cubekit.py）。出力は手で直さず、ここを直して流し直す。

head / seg_NN は Java（GiantGeoModel）が毎フレーム置くので、突進の「頭を下げる」は
子の骨 ram_00（head の子）・ram_02〜ram_10（seg_02〜10 の子）を同じ pivot（首の背、s=20）で
同じ角度だけ回して作る。頭の箱はすべてこの ram_NN に入っている。
"""
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import cubekit                      # noqa: E402
from cubekit import boss_kit        # noqa: E402

NAME = 'hush_whale'

# ------------------------------------------------------------------ 素材タイル（ChatGPT のシート。giant_tiles.py 参照）
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import giant_tiles                      # noqa: E402
MATERIALS = [
    ('body', 'dark violet-black whale skin with faint scar lines and soft darker blotches', '2d2242 40325a 1f172f'),
    ('belly', 'lavender-grey belly skin with long pleat grooves', '7a84a2 99a2bd 5e6786'),
    ('armor', 'pale grey-lilac bone armor plates with chipped edges and cracks', 'a3a5b8 c4c6d6 7a7c92'),
    ('jaw', 'blue-grey leathery lower-jaw skin with fine creases', '6c7594 8d96b4 525a78'),
    ('fin_edge', 'pale lavender fin rim, thin streaks', '9aa3c2'),
    ('mouth', 'near-black violet mouth lining, smooth', '0d0a16'),
]
TILES = giant_tiles.Tiler(NAME, MATERIALS)
LENGTH = 48
JOINTS = LENGTH // 2 + 1            # 25
HEAD_END = 14.0                     # 装甲と下顎はここまで（s）
NECK = 20.0                         # ram_NN の pivot（s）。輪（s 13.5〜18.5）より後ろ
NECK_Y = 4.25                       # pivot は背の高さ: 頭を下げても首の上が開かず、下は胴の中へ入る
RAM = [0, 2, 3, 4, 5, 6, 7, 8, 9, 10]   # ram_NN を持つ体節（0 = head）

# 体節 k（s = 2(k-1)〜2k）の (上, 下, 半幅)。1〜7 は頭の芯（下に下顎が付く）
PROF = {k: (4.25, -2.5, 4.0) for k in range(1, 8)}
PROF.update({
    8: (4.5, -3.5, 4.0), 9: (4.5, -3.5, 4.0), 10: (4.5, -3.5, 4.0),
    11: (4.25, -3.5, 4.0), 12: (4.25, -3.25, 3.75),
    13: (4.0, -3.0, 3.75), 14: (3.75, -2.75, 3.5),
    15: (3.5, -2.5, 3.25), 16: (3.0, -2.25, 3.0),
    17: (2.75, -2.0, 2.75), 18: (2.25, -1.75, 2.25),
    19: (2.0, -1.5, 2.0), 20: (1.75, -1.25, 1.75),
    21: (1.5, -1.0, 1.5), 22: (1.25, -0.75, 1.25), 23: (1.0, -0.5, 0.75), 24: (0.75, -0.5, 0.5),
})
JAW_Y = (-4.0, -2.5)
JAW_HW = 3.5

# 装甲板: 体節 k → [(s0, s1, y0, y1, 半幅)]。高さと張り出しを互い違いにして段を作る
PLATES = {
    1: [(-0.75, 2.0, -1.5, 4.75, 4.5), (-1.25, 0.5, 1.0, 4.0, 3.5), (-1.0, 1.5, -2.5, -1.5, 4.25)],
    2: [(2.0, 4.0, 0.5, 5.25, 4.75), (2.0, 3.25, -1.25, 0.5, 4.25), (2.5, 3.75, 5.25, 5.75, 2.5)],
    3: [(4.0, 6.0, 1.5, 5.0, 4.5), (4.0, 5.0, 0.25, 1.5, 4.25)],
    4: [(6.0, 8.0, 2.25, 5.5, 4.75), (6.25, 7.75, 5.5, 6.0, 2.75)],
    5: [(8.0, 10.0, 3.0, 5.0, 4.5)],
    6: [(10.0, 12.0, 3.5, 5.25, 4.25), (10.5, 11.5, 5.25, 5.75, 2.0)],
    7: [(12.0, 13.5, 4.0, 4.75, 4.25)],
}
BUMPS = [20.25, 26.25, 32.25, 38.25]

# ------------------------------------------------------------------ 色
BODY_L, BODY, BODY_D = '#40325a', '#2d2242', '#1f172f'
BELLY_L, BELLY, BELLY_D = '#99a2bd', '#7a84a2', '#5e6786'
ARM_L, ARM, ARM_D = '#c4c6d6', '#a3a5b8', '#7a7c92'
JAW_L, JAW, JAW_D = '#8d96b4', '#6c7594', '#525a78'
MOUTH = '#0d0a16'
GLOW = '#3ff0e0'
FIN_EDGE = '#9aa3c2'

RING = (16.0, 0.5, 2.0, 2.5)        # 中心 s, y と内径・外径（ブロック）
SIDE_DOTS = [(8.25, -0.25, 0.5, 0.5), (11.0, 0.0, 0.5, 0.5),
             (26.5, -0.75, 0.5, 0.5), (28.0, -0.75, 0.5, 0.5), (29.5, -0.5, 0.5, 0.5)]
TOP_DOTS = [(30.25, 0.5), (36.25, 0.5)]
BELLY_PTS = [(20, -2.5), (22, -2.0), (26, -1.5), (34, -1.0), (40, -0.5), (60, -0.25)]


def belly_line(s):
    for (s0, y0), (s1, y1) in zip(BELLY_PTS, BELLY_PTS[1:]):
        if s0 <= s < s1:
            y = y0 + (y1 - y0) * (s - s0) / (s1 - s0)
            y += 0.25 if int(s) % 2 else -0.25
            return round(y * 2) / 2
    return -99.0


def top_of(s):
    return PROF[max(1, min(24, int(s // 2) + 1))][0]


def paint(tag, face, x, y, s, b):
    x0, x1, y0, y1, s0, s1 = b
    if tag == 'armor':
        if face == 'up':
            return ARM_L if s < s1 - 0.25 else ARM
        if face == 'down':
            return ARM_D
        if y < y0 + 0.5:
            return ARM_D
        if face in ('east', 'west') and s > s1 - 0.25:
            return ARM_D
        return ARM_L if y > y1 - 0.5 and face != 'north' else ARM
    if tag == 'jaw':
        if face == 'down':
            return JAW_D
        if face in ('east', 'west') and y > y1 - 0.5:      # ブロック状の歯の並び
            return BELLY_L if int(s * 2) % 2 == 0 else JAW_D
        return JAW_L if y > y0 + 1.0 else (JAW if y > y0 + 0.5 else JAW_D)
    if tag == 'fin':
        if face == 'down':
            return BODY_D
        if face != 'up' or s > s1 - 0.5:
            return FIN_EDGE
        return BODY_L if s < s0 + 1.0 else BODY
    if tag == 'tail':
        if face in ('east', 'west'):
            return BELLY if s > s1 - 0.5 else (BODY_L if s < s0 + 0.75 else BODY)
        return BELLY if face == 'south' else BODY_D
    if tag == 'bump':
        return BODY_L if face == 'up' else BODY
    # 体と頭の芯
    if face == 'up':
        if any(c <= s < c + w and abs(x) < 0.25 for c, w in TOP_DOTS):
            return GLOW, True
        return BODY_L
    if face == 'down':
        return BELLY_D if s >= 20.0 else BODY_D
    if face in ('east', 'west'):
        d = ((s - RING[0]) ** 2 + (y - RING[1]) ** 2) ** 0.5
        if RING[2] <= d < RING[3]:
            return GLOW, True
        if any(r[0] <= s < r[0] + r[2] and r[1] <= y < r[1] + r[3] for r in SIDE_DOTS):
            return GLOW, True
    if s < HEAD_END and y < -2.0:
        return MOUTH                                       # 口の線
    line = belly_line(s)
    if y < line:
        return BELLY_L if y > line - 0.5 else BELLY
    if y > top_of(s) - 0.75:
        return BODY_L
    return BODY_D if y < -1.0 else BODY


def build_model():
    m = cubekit.Model(NAME, JOINTS, paint, size=256)
    m.tiles = TILES
    ram = {}
    for k in RAM:
        parent = 'head' if k == 0 else f'seg_{k:02d}'
        ram[max(k, 1)] = m.bone(f'ram_{k:02d}', (0, NECK_Y, NECK), parent)
    ov = 0.25
    for k in range(1, 25):
        top, bot, hw = PROF[k]
        bone = ram.get(k, f'seg_{k:02d}')
        s0, s1 = 2.0 * (k - 1), 2.0 * k
        m.box(bone, 'body', -hw, hw, bot, top, (0.0 if k == 1 else s0 - ov), s1 + (ov if k < 24 else 0))
        if k <= 7:
            m.box(bone, 'jaw', -JAW_HW, JAW_HW, JAW_Y[0], JAW_Y[1], (0.5 if k == 1 else s0 - ov), s1 + (ov if k < 7 else 0))
        for (p0, p1, y0, y1, phw) in PLATES.get(k, ()):
            m.box(bone, 'armor', -phw, phw, y0, y1, p0, p1)
    for s in BUMPS:
        k = int(s // 2) + 1
        m.box(f'seg_{k:02d}', 'bump', -0.5, 0.5, PROF[k][0], PROF[k][0] + 0.75, s, s + 1.5)

    # 胸びれ: 3 枚の段になった板。骨の回転で下げて後ろへ流す
    for side, sg in (('l', 1), ('r', -1)):
        hw = PROF[11][2]
        fin = m.bone(f'fin_{side}', (sg * (hw - 0.25), -2.25, 21.0), 'seg_11', (0, -sg * 25, sg * 42))
        for d0, d1, z0, z1 in ((0, 3.5, 0, 5.0), (3.5, 7.0, 1.5, 6.0), (7.0, 9.5, 3.5, 6.5)):
            a, c = sg * (hw - 0.25 + d0), sg * (hw - 0.25 + d1)
            m.box(fin, 'fin', min(a, c), max(a, c), -2.5, -2.0, 19.5 + z0, 19.5 + z1)

    # 尾びれ: 縦の三日月（上葉と少し短い下葉）。1 本の骨で左右に振る
    tail = m.bone('tail', (0, 0, 44.0), 'seg_23')
    # 重なる箱は厚みを変える（同じ面に並ぶと、違う絵どうしがちらつく）
    for y0, y1, z0, z1, th in ((0.5, 3.0, 43.5, 46.5, 0.375), (2.5, 5.0, 44.75, 47.5, 0.25), (4.5, 7.0, 46.25, 48.5, 0.125),
                               (-2.5, -0.5, 43.5, 46.5, 0.375), (-4.5, -2.0, 44.75, 47.5, 0.25), (-6.0, -4.0, 46.25, 48.25, 0.125)):
        m.box(tail, 'tail', -th, th, y0, y1, z0, z1)
    return m


def animations():
    rams = [f'ram_{k:02d}' for k in RAM]
    # idle: ゆっくりした遊泳。胸びれ・尾びれと、頭のごく小さなうなずき
    L = 6.0
    idle = {'fin_l': boss_kit.wave(L, 4.0, 0.0, (0, 0, 1)), 'fin_r': boss_kit.wave(L, -4.0, 0.0, (0, 0, 1)),
            'tail': boss_kit.wave(L, 7.0, 0.8, (0, 1, 0))}
    for r in rams:
        idle[r] = boss_kit.wave(L, 0.8, 0.0, (1, 0, 0))
    # attack: 頭を少し上げて溜め、一気に下げて突く（1 回）。rotation x の正が頭を下げる
    A = 1.6
    attack = {r: boss_kit.strike(A, 0.0, -5.0, 14.0, 9.0, 0.92) for r in rams}
    sweep = [(0.0, 0.0), (0.72, -8.0, boss_kit.EASE_SLOW), (0.92, 26.0, boss_kit.EASE_FAST),
             (1.2, 12.0, boss_kit.EASE_BACK), (A, 0.0, boss_kit.EASE_BACK)]
    attack['fin_l'] = boss_kit.rot([(t, (0, -v, v * 0.4)) + tuple(e) for t, v, *e in sweep])
    attack['fin_r'] = boss_kit.rot([(t, (0, v, -v * 0.4)) + tuple(e) for t, v, *e in sweep])
    attack['tail'] = boss_kit.rot([(0.0, (0, 0, 0)), (0.72, (0, 10, 0), boss_kit.EASE_SLOW),
                                   (0.92, (0, -16, 0), boss_kit.EASE_FAST), (1.2, (0, 8, 0), boss_kit.EASE_BACK),
                                   (A, (0, 0, 0), boss_kit.EASE_BACK)])
    # charge: 突進の構え。頭を下げ、胸びれを後ろへ畳んだまま保つ
    C = 0.5
    charge = {r: boss_kit.rot([(0.0, (0, 0, 0)), (0.4, (7, 0, 0), boss_kit.EASE_BACK), (C, (7, 0, 0))]) for r in rams}
    charge['fin_l'] = boss_kit.rot([(0.0, (0, 0, 0)), (0.4, (0, -22, 8), boss_kit.EASE_BACK), (C, (0, -22, 8))])
    charge['fin_r'] = boss_kit.rot([(0.0, (0, 0, 0)), (0.4, (0, 22, -8), boss_kit.EASE_BACK), (C, (0, 22, -8))])
    return {'idle': cubekit.anim(L, idle, True), 'attack': cubekit.anim(A, attack, False),
            'charge': cubekit.anim(C, charge, 'hold_on_last_frame')}


def main():
    m = build_model()
    anims = animations()
    print(m.write(anims))
    cubes = len(m.cubes)
    assert 60 <= cubes <= 150, cubes
    contract = {
        'entity': NAME,
        'generator': 'tools/boss/hush_whale_model/build.py（手で直さず、生成元を直して流し直す）',
        'geo': f'assets/abyssia/geo/entity/{NAME}.geo.json',
        'animation': f'assets/abyssia/animations/entity/{NAME}.animation.json',
        'texture': f'assets/abyssia/textures/entity/boss/{NAME}_geo.png',
        'glow_texture': f'assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png',
        'glow_note': '光らない画素は (0,0,0,0)。頭の両側の輪・頭と体側の点・背の点。地の画像にも同じ色で入っている',
        'texture_size': [m.size, m.size],
        'texel': '1 テクセル = 0.25 ブロック（geo 2 単位）',
        'model_scale': 2.0,
        'segment_length_units': 16.0,
        'joints': JOINTS,
        'segments': JOINTS - 1,
        'rest_pose': '頭は -Z。関節 j = (0,0,16j)、seg_k の pivot = (0,0,16(k-0.5))。装甲の先は関節 0 の 1.25 ブロック前、尾の先は 48.5',
        'cubes': cubes,
        'child_bones': {
            'ram_00 / ram_02..ram_10': 'head と seg_02..10 の子。頭の箱をすべて持つ。pivot は共通で首の背 (0,34,160)。同じ rotation x を入れると頭全体がひとかたまりで下がる（正で下がる）',
            'fin_l / fin_r': '胸びれ（seg_11 の子）。rotation z で上下、y で前後',
            'tail': '尾びれ（seg_23 の子、pivot は s=44）。rotation y で左右',
        },
        'ring': {'centre_units': [0, 4.0, 128.0], 'outer_radius_blocks': 2.5, 'half_width_units': 32.0,
                 'note': '輪は頭の両側面（x = ±32 単位 = ±4 ブロック）に描いてある。seg_07〜10 の ram にまたがる'},
        'animations': {'idle': '6 秒ループ', 'attack': '1.6 秒・1 回（溜め 0.72 秒、打撃 0.92 秒）',
                       'charge': '0.5 秒・hold_on_last_frame（構えを保持）'},
        'renderer': 'GiantRenderer.registerGeo("hush_whale", "hush_whale", "textures/entity/boss/hush_whale_geo.png", 2.0F, 24)。発光層（_geo_glow.png）は未実装なら追加が要る',
    }
    (HERE / 'contract.json').write_text(json.dumps(contract, ensure_ascii=False, indent=1), encoding='utf-8')
    print('cubes', cubes, 'bones', len(m.bones))


if __name__ == '__main__':
    main()
