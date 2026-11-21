# -*- coding: utf-8 -*-
"""灯髭ウオ（lantern_grazer、GNT01 の 3 番）の GeckoLib モデルを書き出す。

    python tools/boss/lantern_grazer_model/build.py
    python tools/boss/check_animations.py lantern_grazer
    python tools/boss/lantern_grazer_model/preview.py

友好の濾過食者、全長 52 ブロック（27 関節・26 体節）。明るい青の巨体、開いた口（上の頭の板と
下顎の棒）の下に光る髭のカーテン、背の光る瘤と斑点、大きな胸びれ、水平の三日月形の尾。
大きな箱を少数組むバニラ風（cubekit.py）。出力は手で直さず、ここを直して流し直す。
"""
import json
import math
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import cubekit                      # noqa: E402
from cubekit import boss_kit        # noqa: E402

NAME = 'lantern_grazer'

# ------------------------------------------------------------------ 素材タイル（ChatGPT のシート。giant_tiles.py 参照）
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import giant_tiles                      # noqa: E402
MATERIALS = [
    ('back', 'medium blue smooth skin with darker blue mottled patches and tiny pale dots', '3c80c8 5aa6e8'),
    ('flank', 'deep blue skin with slightly darker horizontal mottling', '2c62a8 224c88'),
    ('belly', 'pale grey-blue soft belly skin with faint wrinkles', '93a8c4 b4c6dc 778cab'),
    ('jaw', 'light blue-grey leathery jaw skin with fine creases', '9db1cb b9cadf'),
    ('mouth', 'very dark navy mouth lining, smooth', '0b1930'),
]
TILES = giant_tiles.Tiler(NAME, MATERIALS)
LENGTH = 52
JOINTS = LENGTH // 2 + 1            # 27
MOUTH_END = 14.0                    # 口はここまで（s、ブロック）

# 体節 k（s = 2(k-1)〜2k）の (上, 下, 半幅)。1〜7 は頭の上板（下は口）
PROF = {
    1: (2.75, 0.5, 3.5), 2: (3.25, 0.0, 3.75),
    3: (3.5, 0.0, 4.0), 4: (3.5, 0.0, 4.0), 5: (3.5, 0.0, 4.0), 6: (3.5, 0.0, 4.0), 7: (3.5, 0.0, 4.0),
    8: (3.5, -4.5, 4.0), 9: (3.5, -4.75, 4.0), 10: (3.5, -4.75, 4.0), 11: (3.5, -4.75, 4.0),
    12: (3.25, -4.5, 3.75), 13: (3.25, -4.5, 3.75),
    14: (3.0, -4.0, 3.5), 15: (3.0, -3.75, 3.5),
    16: (2.75, -3.25, 3.0), 17: (2.5, -2.75, 2.75),
    18: (2.25, -2.25, 2.5), 19: (2.0, -1.75, 2.0),
    20: (1.75, -1.5, 1.75), 21: (1.5, -1.0, 1.5),
    22: (1.25, -1.0, 1.25), 23: (1.0, -0.75, 1.0),
    24: (1.0, -0.5, 0.75), 25: (0.75, -0.5, 0.75), 26: (0.5, -0.5, 0.5),
}
JAW_Y = (-2.75, -1.25)

# ------------------------------------------------------------------ 色
BACK_L, BACK, FLANK, FLANK_D = '#5aa6e8', '#3c80c8', '#2c62a8', '#224c88'
BELLY_L, BELLY, BELLY_D = '#b4c6dc', '#93a8c4', '#778cab'
JAW_L, JAW = '#b9cadf', '#9db1cb'
MOUTH = '#0b1930'
SPOT, STRAND, BEAD, EYE = '#66f0f0', '#7fdcf0', '#fff3b0', '#8ff8ff'

TOP_PATCH = [(1, 4, -2, 1), (6, 9, 0.5, 3), (10, 13, -3, -0.5), (15, 17, -1, 2), (20, 22, -2, 0),
             (25, 27, 0, 1.5), (31, 33, -1, 0.5)]
SIDE_SPOTS = [(15.5, 2.25, 0.5, 0.5), (18.0, 2.5, 1.0, 0.5), (21.5, 2.25, 0.5, 0.5), (25.0, 2.0, 1.0, 0.5),
              (28.5, 1.75, 0.5, 0.5), (32.0, 1.25, 0.75, 0.5), (36.25, 1.0, 0.5, 0.5), (40.25, 0.5, 0.5, 0.25)]
EYE_RECT = (12.75, 1.0, 0.5, 0.5)
KNOBS = [14.25, 22.25, 28.25, 32.25, 38.25]
BELLY_PTS = [(14, -1.0), (18, -1.5), (22, -2.5), (30, -2.5), (38, -1.25), (44, -0.25), (60, -0.25)]


def _in(rects, a, b):
    return any(r[0] <= a < r[0] + r[2] and r[1] <= b < r[1] + r[3] for r in rects)


def belly_line(s):
    for (s0, y0), (s1, y1) in zip(BELLY_PTS, BELLY_PTS[1:]):
        if s0 <= s < s1:
            y = y0 + (y1 - y0) * (s - s0) / (s1 - s0)
            y += 0.25 if int(s) % 2 else -0.25          # 1 ブロックごとの段
            return round(y * 2) / 2
    return -99.0


def top_of(s):
    return PROF[max(1, min(26, int(s // 2) + 1))][0]


def paint(tag, face, x, y, s, b):
    if tag == 'strand':
        return STRAND, True
    if tag == 'bead':
        return BEAD, True
    if tag == 'knob':
        return (SPOT, True) if face == 'up' else BACK
    if tag == 'jaw':
        if face == 'up':
            return MOUTH
        if face == 'down':
            return BELLY
        return JAW_L if y > -1.75 else JAW
    if tag == 'fin':
        x0, x1, y0, y1, s0, s1 = b
        if face == 'down':
            return FLANK_D
        if face != 'up':
            return BELLY_L
        if s > s1 - 0.5:                               # 後縁の明るい縁
            return BELLY_L
        u, v = abs(x) - min(abs(x0), abs(x1)), s - s0
        if 1.0 <= u < 1.5 and 1.5 <= v < 2.5:
            return SPOT, True
        return BACK if (int(u // 1.5) + int(v // 1.5)) % 2 == 0 else FLANK
    # 体と頭の上板
    if face == 'up':
        if any(p[0] <= s < p[1] and p[2] <= x < p[3] for p in TOP_PATCH):
            return BACK_L
        return BACK
    if face == 'down':
        return MOUTH if (tag == 'upper' and s < MOUTH_END) else BELLY_D
    if face in ('east', 'west'):
        if _in(SIDE_SPOTS, s, y):
            return SPOT, True
        if _in([EYE_RECT], s, y):
            return EYE, True
    if tag == 'body':
        line = belly_line(s)
        if y < line:
            return BELLY_L if y > line - 0.75 else BELLY
    if y > top_of(s) - 0.75:
        return BACK
    return FLANK_D if y < 0.75 else FLANK     # upper と body が重なる所で同じ色になるよう tag を見ない


def build_model():
    m = cubekit.Model(NAME, JOINTS, paint, size=256)
    m.tiles = TILES
    ov = 0.25                                           # 曲がったとき隙間が開かないよう前後へ重ねる
    for k in range(1, 27):
        top, bot, hw = PROF[k]
        bone = 'head' if k == 1 else f'seg_{k:02d}'
        s0, s1 = 2.0 * (k - 1), 2.0 * k
        if k <= 7:
            m.box(bone, 'upper', -hw, hw, bot, top, (-0.5 if k == 1 else s0 - ov), s1 + ov)
            jw = 3.25 if k == 1 else 3.75
            m.box(bone, 'jaw', -jw, jw, JAW_Y[0], JAW_Y[1], (-0.25 if k == 1 else s0 - ov), s1 + ov)
        else:
            m.box(bone, 'body', -hw, hw, bot, top, s0 - ov, s1 + (ov if k < 26 else 0))
    for s in KNOBS:
        k = int(s // 2) + 1
        m.box(f'seg_{k:02d}', 'knob', -0.5, 0.5, PROF[k][0], PROF[k][0] + 0.5, s, s + 1.5)

    # 髭のカーテン: 細い箱と先の光る玉。体節ごと・左右ごとに 1 本の骨で揺れる
    lens = [2.0, 3.0, 2.5, 4.5, 2.25, 3.5, 5.25, 2.75, 3.25, 4.0, 2.5, 3.0, 2.0, 2.75, 3.75, 2.25]
    n = 0

    def strand(bone, x, s, length):
        y1 = JAW_Y[0]
        m.box(bone, 'strand', x - 0.125, x + 0.125, y1 - length, y1, s - 0.125, s + 0.125)
        m.box(bone, 'bead', x - 0.25, x + 0.25, y1 - length - 0.5, y1 - length, s - 0.25, s + 0.25)

    for k in range(1, 8):
        parent = 'head' if k == 1 else f'seg_{k:02d}'
        for side, sg in (('l', 1), ('r', -1)):
            bone = m.bone(f'whisker_{k:02d}_{side}', (sg * 3.5, JAW_Y[0], 2.0 * k - 1.0), parent)
            for i, ds in enumerate((0.5, 1.5)):
                xx = sg * (3.0 if k == 1 else 3.5)
                strand(bone, xx, 2.0 * (k - 1) + ds, lens[(n + (3 if sg < 0 else 0)) % len(lens)])
                n += 1
    front = m.bone('whisker_front', (0, JAW_Y[0], 0.25), 'head')
    for i, xx in enumerate((-2.0, 0.0, 2.0)):
        strand(front, xx, 0.25, (2.75, 3.75, 2.25)[i])

    # 胸びれ: 3 枚の段になった大きな板。骨の回転で下げて後ろへ流す
    for side, sg in (('l', 1), ('r', -1)):
        hw = PROF[9][2]
        fin = m.bone(f'fin_{side}', (sg * (hw - 0.25), -2.25, 17.0), 'seg_09', (0, -sg * 22, sg * 38))
        for d0, d1, z0, z1 in ((0, 4.0, 0, 6.0), (4.0, 8.0, 1.5, 7.0), (8.0, 11.0, 3.5, 7.5)):
            a, c = sg * (hw - 0.25 + d0), sg * (hw - 0.25 + d1)
            m.box(fin, 'fin', min(a, c), max(a, c), -2.5, -2.0, 16.0 + z0, 16.0 + z1)

    # 尾びれ: 水平の三日月（先が後ろへ流れる）。1 本の骨で上下に打つ
    fluke = m.bone('fluke', (0, 0, 48.0), 'seg_25')
    for sg in (1, -1):
        for d0, d1, z0, z1 in ((0.5, 3.5, 46.5, 50.5), (3.5, 6.5, 48.0, 51.5), (6.5, 9.0, 50.0, 52.5)):
            a, c = sg * d0, sg * d1
            m.box(fluke, 'fin', min(a, c), max(a, c), -0.25, 0.25, z0, z1)
    return m


def animations():
    L = 8.0
    bones = {}

    def sway(ph, sg):
        """前後（x、4 秒周期）と左右（z、8 秒周期）の揺れを 0.5 秒刻みのキーで"""
        frames = []
        for i in range(int(L * 2) + 1):
            t = i * 0.5
            frames.append((t, (7.0 * math.sin(2 * math.pi * t / 4.0 + ph), 0.0,
                               3.0 * sg * math.sin(2 * math.pi * t / L + ph + 1.0))))
        return boss_kit.rot(frames)

    for k in range(1, 8):
        for side, sg in (('l', 1), ('r', -1)):
            bones[f'whisker_{k:02d}_{side}'] = sway(-0.7 * k + (0.4 if sg < 0 else 0.0), sg)
    bones['whisker_front'] = sway(0.3, 0)
    bones['fin_l'] = boss_kit.wave(L, 6.0, 0.0, (0, 0, 1))
    bones['fin_r'] = boss_kit.wave(L, -6.0, 0.0, (0, 0, 1))
    bones['fluke'] = boss_kit.wave(L, 8.0, 1.2, (1, 0, 0))
    return {'idle': cubekit.anim(L, bones, True)}


def main():
    m = build_model()
    anims = animations()
    print(m.write(anims))
    cubes = len(m.cubes)
    assert 60 <= cubes <= 150, cubes
    contract = {
        'entity': NAME,
        'generator': 'tools/boss/lantern_grazer_model/build.py（手で直さず、生成元を直して流し直す）',
        'geo': f'assets/abyssia/geo/entity/{NAME}.geo.json',
        'animation': f'assets/abyssia/animations/entity/{NAME}.animation.json',
        'texture': f'assets/abyssia/textures/entity/boss/{NAME}_geo.png',
        'glow_texture': f'assets/abyssia/textures/entity/boss/{NAME}_geo_glow.png',
        'glow_note': '光らない画素は (0,0,0,0)。髭・髭の玉・背の瘤・体側の斑点・目・ひれの印。地の画像にも同じ色で入っている',
        'texture_size': [m.size, m.size],
        'texel': '1 テクセル = 0.25 ブロック（geo 2 単位）',
        'model_scale': 2.0,
        'segment_length_units': 16.0,
        'joints': JOINTS,
        'segments': JOINTS - 1,
        'rest_pose': '頭は -Z。関節 j = (0,0,16j)、seg_k の pivot = (0,0,16(k-0.5))。吻端は関節 0 の 0.5 ブロック前、尾の先は 52.5',
        'cubes': cubes,
        'child_bones': {
            'whisker_NN_l / whisker_NN_r (NN=01..07)': '髭 2 本ずつ。head（01）と seg_02..07 の子。pivot は下顎の下面',
            'whisker_front': '前の 3 本（head の子）',
            'fin_l / fin_r': '胸びれ（seg_09 の子）。rotation z で上下（l は正で下がる、r は逆）',
            'fluke': '尾びれ（seg_25 の子、pivot は s=48）。rotation x で上下',
        },
        'animations': {'idle': '8 秒ループ。髭の揺れ・胸びれ・尾びれ。head / seg_NN は Java が置くので触らない'},
        'renderer': 'GiantRenderer.registerGeo("lantern_grazer", "lantern_grazer", "textures/entity/boss/lantern_grazer_geo.png", 2.0F, 26)。発光層（_geo_glow.png）は未実装なら追加が要る',
    }
    (HERE / 'contract.json').write_text(json.dumps(contract, ensure_ascii=False, indent=1), encoding='utf-8')
    print('cubes', cubes, 'bones', len(m.bones))


if __name__ == '__main__':
    main()
