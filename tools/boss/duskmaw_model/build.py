# -*- coding: utf-8 -*-
"""宵喰らい（duskmaw、GNT01 の 10 番）の GeckoLib モデルを生成する。

```bash
python tools/boss/duskmaw_model/build.py            # geo / animation / texture(+glow) / contract.json
python tools/boss/check_animations.py duskmaw       # ボーン名・キーの順・尺
python tools/boss/duskmaw_model/preview.py          # out/ に横・上・斜め前・姿勢の図と比較画像
```

出力は手で直さない（ここを直して流し直す）。バニラ風の大きな箱だけで組む（`boxkit.py`）。

- MODEL_SCALE 2（geo 1 単位 = 1/8 ブロック）。全長 56 ブロック、29 関節・間隔 16 単位。頭が -Z
- `head` と `seg_01`〜`seg_28` は root 直下（関節 j = (0,0,16j)、seg_k の pivot = (0,0,16(k-0.5))）
- 頭骨は硬い 9 ブロック（z -24..48）で `head` に載せる。seg_01〜03 は箱なし、胴は seg_04 から
- `jaw`（head の子、下顎。静止で 38 度開いている。rotation x 正で開く）
- 触手 7 本: `tent_N_a`〜`tent_N_d`（N=1..7、a が付け根で前の節の子）。先（d）に光る赤い玉
- ひれ: `crest`、`dorsal_1..3`、`pectoral_l/r`、`pelvic_l/r`、`anal`、`caudal`
"""
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import boxkit
from boxkit import Model, hexrgb, cell, keys, wave, anim

NAME = 'duskmaw'

# ------------------------------------------------------------------ 素材タイル（ChatGPT のシート。giant_tiles.py 参照）
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import giant_tiles                      # noqa: E402
MATERIALS = [
    ('body', 'near-black purple-tinted skin with faint cracks and dull lighter blotches', '2a2434 1d1925 3a3347'),
    ('belly', 'very dark violet underside, smooth with faint creases', '16131c'),
    ('fin_rim', 'dark wine-red fin trailing edge with thin darker rays', '7c2630'),
    ('tooth', 'yellowed ivory tooth with chips and fine cracks', 'e9dfc4 bfae8c'),
    ('mouth', 'dark crimson-maroon mouth flesh with wet darker folds', '4a161c 2a0e14'),
    ('cord', 'dark purple-grey fibrous cord, twisted strands', '2a2230 1a1620'),
]
TILES = giant_tiles.Tiler(NAME, MATERIALS)
SEG = 16
JOINTS = 29
ATLAS = 256
JAW_OPEN = 38.0

# ---- 色（平らな 2〜3 段） ----
BODY = [hexrgb(c) for c in ('#1d1925', '#2a2434', '#3a3347')]    # 黒に近い紫がかった体
BELLY = hexrgb('#16131c')
CRACK = hexrgb('#ff4c3c')      # 発光線
FIN_RIM = hexrgb('#7c2630')    # ひれの後縁（光らない）
TOOTH = [hexrgb(c) for c in ('#bfae8c', '#e9dfc4')]
MOUTH = [hexrgb(c) for c in ('#2a0e14', '#4a161c')]
EYE = hexrgb('#ff3a30')
CORD = [hexrgb(c) for c in ('#1a1620', '#2a2230')]
ORB = [hexrgb(c) for c in ('#ff5a3c', '#ffd8b4')]

# 体節 k の箱 (y0, y1, 幅)。背の線はほぼ平らで、腹が尾へ向けて上がる
PROFILE = [(range(4, 7), (-24, 28, 38)), (range(7, 11), (-20, 24, 34)), (range(11, 15), (-16, 20, 28)),
           (range(15, 19), (-12, 16, 22)), (range(19, 23), (-8, 12, 16)), (range(23, 26), (-4, 12, 12)),
           (range(26, 29), (-4, 8, 8))]

# ひび割れのような発光線（体の側面。(z, y) の折れ線、2 単位の升目に乗る）
CRACKS = [
    [(28, 8), (40, 8), (40, 4), (48, 4)],
    [(52, 16), (68, 16), (68, 8), (80, 8), (80, 0), (92, 0), (92, -8), (104, -8), (104, -12), (128, -12)],
    [(144, 8), (164, 8), (164, 4), (180, 4), (180, 12), (204, 12)],
    [(216, 6), (236, 6), (236, 0), (262, 0)],
    [(284, 8), (304, 8), (304, 4), (340, 4)],
    [(356, 2), (384, 2), (384, 6), (404, 6)],
]


def _cells():
    out = set()
    for line in CRACKS:
        for (z0, y0), (z1, y1) in zip(line, line[1:]):
            if y0 == y1:
                for z in range(min(z0, z1), max(z0, z1), 2):
                    out.add((z // 2, y0 // 2))
            else:
                for y in range(min(y0, y1), max(y0, y1) + 2, 2):
                    out.add((z0 // 2, y // 2))
    return out


CRACK_CELLS = _cells()


def body(face, x, y, z):
    if face in ('east', 'west'):
        if (int(z // 2), int(y // 2)) in CRACK_CELLS:
            return CRACK, True
        return BODY[0 if cell(0, y, z, 8, 5) > 0.7 else 1], False
    if face == 'up':
        return BODY[2 if cell(x, 0, z, 8, 7) > 0.35 else 1], False
    if face == 'down':
        return BELLY, False
    return BODY[1], False


def skull(face, x, y, z):
    if face in ('east', 'west') and 8 <= z < 12 and 14 <= y < 18:
        return EYE, True
    if face == 'down' and z < 36:
        return MOUTH[0], False                      # 口の天井
    return body(face, x, y, z)


def throat(face, x, y, z):
    if face == 'north':
        return MOUTH[1 if y > -8 else 0], False     # 口の奥
    return body(face, x, y, z)


def jaw_paint(face, x, y, z):
    if face == 'up':
        return MOUTH[1 if abs(x) < 6 else 0], False
    if face == 'down':
        return BELLY, False
    return BODY[2 if face in ('east', 'west', 'north') else 1], False


def tooth(face, x, y, z):
    return TOOTH[0 if face in ('south', 'down', 'up') else 1], False


def fin_paint(z_back):
    """ひれの段。後縁の 1 テクセルを暗い赤にする"""
    def paint(face, x, y, z):
        if face in ('east', 'west') and z >= z_back - 4:
            return FIN_RIM, False
        if face == 'south':
            return FIN_RIM, False
        return BODY[2 if face == 'up' else 1 if face in ('east', 'west') else 0], False
    return paint


def cord(y_band):
    def paint(face, x, y, z):
        if y_band is not None and y_band <= y < y_band + 4:
            return ORB[0], True                     # 玉の手前の赤い帯
        return CORD[1 if face in ('east', 'north') else 0], False
    return paint


def orb(y_mid):
    def paint(face, x, y, z):
        if face in ('up', 'down'):
            return ORB[0], True
        return ORB[1 if abs(y - y_mid) < 2 else 0], True
    return paint


def stairs(m, bone, steps, x=2):
    """ひれを階段状の板で。steps = [(z0, z1, y0, y1), ...]"""
    for z0, z1, y0, y1 in steps:
        m.cube(bone, (-x, y0, z0), (x, y1, z1), fin_paint(max(z0, z1)))


# 触手: (付け根 x, y, z, 親, 節の長さ)。口の下から胸の下にかけて垂れる
TENTACLES = [(-12, -20, 40, 'head', 20, (-18, -14, 10, 16)), (12, -20, 44, 'head', 24, (-8, -16, 14, 8)),
             (-14, -24, 54, 'seg_04', 26, (6, -16, -10, 14)), (14, -24, 60, 'seg_04', 22, (14, -18, 8, -10)),
             (0, -24, 76, 'seg_05', 24, (4, 12, -16, -8)), (-12, -24, 92, 'seg_06', 20, (16, 10, -14, -12)),
             (12, -20, 140, 'seg_09', 22, (22, 12, -10, -16))]
LINKS = 'abcd'
SPLAY = 16.0      # 付け根で外へ開く角度


def build():
    m = Model(NAME, ATLAS, 2.0)
    m.tiles = TILES
    m.bone('head', pivot=(0, 0, 0))
    for k in range(1, JOINTS):
        m.bone(f'seg_{k:02d}', pivot=(0, 0, SEG * (k - 0.5)))

    # ---- 頭骨（硬い 7 ブロック）: 上顎、頭蓋、口の奥 ----
    m.cube('head', (-18, 0, -24), (18, 24, 24), skull)
    m.cube('head', (-20, 0, 24), (20, 28, 48), skull)
    m.cube('head', (-17, -20, 36), (17, 0, 48), throat)
    m.bone('crest', 'head', pivot=(0, 28, 40))
    stairs(m, 'crest', [(28, 44, 28, 36)])
    # 上の歯: 骨色の長い牙。顎の外側を通るので、閉じても下顎の板に刺さらない
    for i, length in enumerate((16, 12, 16, 12, 16, 12, 10)):
        for s in (1, -1):
            m.cube('head', (s * 14, -length, -22 + 8 * i), (s * 18, 0, -18 + 8 * i), tooth)
    for x, length in ((-9, 14), (-2, 16), (5, 14)):
        m.cube('head', (x, -length, -24), (x + 4, 0, -20), tooth)

    # ---- 下顎 ----
    m.bone('jaw', 'head', pivot=(0, -4, 44), rotation=(JAW_OPEN, 0, 0))
    m.cube('jaw', (-14, -16, -20), (14, -4, 44), jaw_paint)
    for i, height in enumerate((12, 10, 12, 10, 12, 8)):
        for s in (1, -1):
            m.cube('jaw', (s * 10, -4, -18 + 8 * i), (s * 14, -4 + height, -14 + 8 * i), tooth)
    for x in (-8, 4):
        m.cube('jaw', (x, -4, -20), (x + 4, 8, -16), tooth)

    # ---- 胴: 体節ごとに 1 箱 ----
    for ks, (y0, y1, w) in PROFILE:
        for k in ks:
            m.cube(f'seg_{k:02d}', (-w / 2, y0, SEG * (k - 1)), (w / 2, y1, SEG * k), body)

    # ---- ひれ（角ばった階段） ----
    m.bone('crest_2', 'seg_04', pivot=(0, 24, 56))
    stairs(m, 'crest_2', [(50, 66, 28, 38)])
    m.bone('dorsal_1', 'seg_10', pivot=(0, 24, 152))
    stairs(m, 'dorsal_1', [(132, 176, 20, 36), (144, 180, 36, 48), (156, 184, 48, 60), (168, 188, 60, 68)])
    m.bone('dorsal_2', 'seg_17', pivot=(0, 16, 264))
    stairs(m, 'dorsal_2', [(252, 280, 12, 24), (262, 286, 24, 34), (272, 290, 34, 42)])
    m.bone('dorsal_3', 'seg_22', pivot=(0, 12, 344))
    stairs(m, 'dorsal_3', [(336, 352, 8, 20), (344, 356, 20, 28)])
    m.bone('anal', 'seg_20', pivot=(0, -8, 312))
    stairs(m, 'anal', [(304, 324, -20, -4), (312, 328, -30, -20)])
    for side, s in (('l', 1), ('r', -1)):
        b = m.bone(f'pectoral_{side}', 'seg_07', pivot=(s * 14, -16, 104), rotation=(0, 0, -s * 32))
        for z0, z1, y0, y1 in [(88, 128, -34, -12), (100, 138, -50, -34), (114, 146, -66, -50)]:
            m.cube(b, (s * 12, y0, z0), (s * 16, y1, z1), fin_paint(z1))
        b = m.bone(f'pelvic_{side}', 'seg_15', pivot=(s * 8, -12, 232), rotation=(0, 0, -s * 28))
        for z0, z1, y0, y1 in [(224, 244, -24, -8), (232, 248, -34, -24)]:
            m.cube(b, (s * 6, y0, z0), (s * 10, y1, z1), fin_paint(z1))
    # 尾びれ: 上下に開く三日月を階段で
    m.bone('caudal', 'seg_28', pivot=(0, 0, 424))
    stairs(m, 'caudal', [(416, 440, -8, 12), (400, 434, 8, 20), (410, 440, 20, 32), (420, 446, 32, 46),
                         (430, 450, 46, 60), (400, 434, -18, -4), (410, 440, -30, -18), (420, 446, -44, -30)])

    # ---- 触手: 4 節の鎖と、先の光る玉 ----
    for n, (x, y, z, parent, length, bends) in enumerate(TENTACLES, 1):
        side = 1 if x > 0 else -1 if x < 0 else 0
        top = y
        for i, link in enumerate(LINKS):
            rot = (bends[i], 0, -side * SPLAY if i == 0 else 0)
            b = m.bone(f'tent_{n}_{link}', parent, pivot=(x, top, z), rotation=rot)
            last = link == LINKS[-1]
            m.cube(b, (x - 2, top - length, z - 2), (x + 2, top, z + 2), cord(top - length if last else None))
            parent, top = b, top - length
        m.cube(parent, (x - 4, top - 8, z - 4), (x + 4, top, z + 4), orb(top - 4))
    return m


def animations():
    out = {}
    tents = range(1, len(TENTACLES) + 1)

    # idle: 顎がゆっくり息づき、触手が節ごとに遅れて揺れ、ひれと尾びれがわずかに動く
    b = {'jaw': wave(5.0, (3, 0, 0)), 'caudal': wave(5.0, (0, 7, 0), steps=8),
         'pectoral_l': wave(5.0, (0, 0, 4)), 'pectoral_r': wave(5.0, (0, 0, -4)),
         'pelvic_l': wave(5.0, (0, 0, 3), -1.0), 'pelvic_r': wave(5.0, (0, 0, -3), -1.0),
         'dorsal_1': wave(5.0, (0, 0, 2), -0.5), 'anal': wave(5.0, (0, 0, 3), -1.5)}
    for n in tents:
        for i, link in enumerate(LINKS):
            b[f'tent_{n}_{link}'] = wave(5.0, (5 + 2 * i, 0, 3 + i), 0.9 * n - 0.8 * i)
    out['idle'] = anim(5.0, True, b)

    # attack: 口をさらに開いて溜め、噛み合わせ、戻す（1 回）
    b = {'jaw': keys([(0.0, (0, 0, 0)), (0.32, (20, 0, 0)), (0.44, (-JAW_OPEN, 0, 0)),
                      (0.62, (-JAW_OPEN, 0, 0)), (1.0, (0, 0, 0))])}
    for n in tents:
        b[f'tent_{n}_a'] = keys([(0.0, (0, 0, 0)), (0.32, (-10, 0, 0)), (0.5, (18, 0, 0)), (1.0, (0, 0, 0))])
        b[f'tent_{n}_c'] = keys([(0.0, (0, 0, 0)), (0.36, (-8, 0, 0)), (0.56, (14, 0, 0)), (1.0, (0, 0, 0))])
    out['attack'] = anim(1.0, False, b)

    # grab: 触手を前へ伸ばし、先を内へ巻いて絡める姿勢を保持
    b = {'jaw': keys([(0.0, (0, 0, 0)), (0.7, (8, 0, 0))])}
    for n, (x, _y, _z, _p, _l, bends) in zip(tents, TENTACLES):
        side = 1 if x > 0 else -1 if x < 0 else 0
        reach = (-72, -30, 26, 58)                 # 付け根 2 節で前へ、先 2 節で巻き戻して抱える
        for i, link in enumerate(LINKS):
            z = side * (10 if i == 0 else 16 if i >= 2 else 0)   # 先を内側へ寄せる
            b[f'tent_{n}_{link}'] = keys([(0.0, (0, 0, 0)), (0.7, (reach[i] - bends[i], 0, z))])
    out['grab'] = anim(0.7, 'hold_on_last_frame', b)
    return out


def contract(m, anims):
    return {
        'entity': NAME,
        'generator': 'tools/boss/duskmaw_model/build.py（出力は手で直さず、生成元を直して流し直す）',
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
        'extent_units': {'front_z': -24, 'tail_z': 450, 'total_length_blocks': 59.25},
        'bone_rule': 'head と seg_01..seg_28 は root 直下で、体節鎖が毎フレーム上書きする。頭骨は head に載る硬い 9 ブロック'
                     '（z -24..48）で、seg_01..seg_03 は箱なし。胴は seg_04 から。子の骨は animation だけが動かす',
        'child_bones': {
            'jaw': '下顎（head の子）。pivot は顎関節 (0, -4, 44)。静止で 38 度開いている',
            'tent_N_a..d (N=1..7)': '触手の鎖。a が付け根（1,2 は head、3,4 は seg_04、5 は seg_05、6 は seg_06、7 は seg_09 の子）。'
                                    'd の先に光る玉',
            'crest / crest_2 / dorsal_1..3 / anal / pectoral_l,r / pelvic_l,r / caudal': 'ひれ。pivot は付け根',
        },
        'animation_signs': {
            'jaw': 'rotation x 正で開く。-38 で噛み合う',
            'tent_*': 'rotation x 負で前（-Z）へ、正で後ろへ。z は左右',
            'caudal': 'rotation y で左右に振る',
        },
        'animations': {n: {'loop': a['loop'], 'length': a['animation_length'], 'bones': len(a['bones'])}
                       for n, a in anims.items()},
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
