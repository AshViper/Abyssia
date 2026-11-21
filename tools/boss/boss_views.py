# -*- coding: utf-8 -*-
"""ボスの六面図を、実際のテクスチャを貼って描く共通の描画器。

ParaInfaction の `tools/boss_views.py` を Abyssia 用に複写したもの。違いは
読む geo の場所（`assets/abyssia/geo/entity`）と、`bones` で描く Bone を絞れること
（全長 400 ブロックの大海蛇は、頭だけ拡大して見ないと形が読めないため）。

<h2>なぜ六面か</h2>
正面からだけ見て作ると立体が痩せる。胸の厚み・腹の奥行き・頭の奥行き・尾の断面は、
横と上と下から見ないと保たれているか分からない。左右を別々に出すのは、
左右で違う飾りを付けたときに片方だけ壊れているのを見つけるため。

<h2>ゲーム内とずれないこと</h2>
GeckoLib の焼き込み（BakedModelFactory）に合わせて
  X は反転、回転は (-rx, -ry, +rz)、頂点へ掛かる順は X→Y→Z
としてある。Bone の静止姿勢回転と親子関係もそのまま辿るので、見え方がずれない。
アルファ0の画素は飛ばすので、シダの切り抜きやヒレの破れもそのまま見える。

<h2>回転の順番</h2>
GeckoLib の `RenderUtil.rotateMatrixAroundCube` / `rotateMatrixAroundBone` は
`mulPose(Z)` → `mulPose(Y)` → `mulPose(X)` の順に呼ぶ。行列は右から掛かるので
出来上がるのは Rz・Ry・Rx で、<b>頂点に最初に効くのは X</b>、最後が Z になる
（Blockbench の Bedrock 形式、バニラの `ModelPart` と同じ）。

以前ここは逆（Z を先）に掛けていた。1軸しか回していない箱は順番に関係なく同じ絵に
なるので気付きにくいが、2軸以上を回した箱や Bone は<b>図とゲームで向きが食い違う</b>。
4.9.2 の jar を逆アセンブルして確かめたうえで直してある。

<h2>陰影</h2>
面の明るさは、回したあとの法線から決める。回していない箱では従来どおり
（上 1.0 / 北 0.9 / …）になり、斜めの板はその中間になる。面の名前だけで決めると、
横倒しにした板の「上面」が真横を向いていても最も明るく塗られて、丸みが読めない。

```python
import boss_views
boss_views.render_parts(atlas, [("primordial_corroder_head", 0.0), ...], out_path)
```
"""
import json
import math
import pathlib
import struct
import zlib

GEO = pathlib.Path(__file__).resolve().parents[2] / 'src/main/resources/assets/abyssia/geo/entity'

SCALE = 4
BG = (20, 22, 26)
LABEL = (150, 168, 140)

# 面の四隅と、その隅に貼られる絵の位置。GeckoLib 4.9.2 の sources
# （`BakedModelFactory.VertexSet.quadWest()` ほかと `GeoQuad.build`）から写したもの。
# 隅は (x, y, z) の 0/1 で、x は<b>反転後</b>の render 空間（0 が geo の +X 側）。
#
# GeoQuad.build は面ごとの UV を、頂点の並び順に
#   (右上) (左上) (左下) (右下) = (u+w, v) (u, v) (u, v+h) (u+w, v+h)
# と配る。どの面も「外から見て正像」になり、上面は絵の上端が +Z 側（後ろ）、
# 下面は絵の上端が -Z 側（前）に来る。
#
# 以前の表は west と south だけが合っていて、north / east / down は左右が裏返り、
# up は上下左右とも逆だった。乱数で切り出す地の素材では見分けが付かないが、
# 上辺に苔を持つ素材や切り抜きの絵は、図とゲームで向きが食い違っていた。
FACE_CORNERS = {
    "west": ((0, 1, 1), (0, 1, 0), (0, 0, 0), (0, 0, 1)),
    "east": ((1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)),
    "north": ((0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)),
    "south": ((1, 1, 1), (0, 1, 1), (0, 0, 1), (1, 0, 1)),
    "up": ((0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)),
    "down": ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)),
}
_QUAD_UVS = ((1, 0), (0, 0), (0, 1), (1, 1))
FACE_UVS = {face: _QUAD_UVS for face in FACE_CORNERS}
FACE_SHADE = {"up": 1.0, "north": 0.9, "south": 0.72, "east": 0.82, "west": 0.62, "down": 0.45}
# 回していない箱での、各面の外向きの法線（render 空間）
FACE_NORMALS = {"west": (-1, 0, 0), "east": (1, 0, 0), "down": (0, -1, 0), "up": (0, 1, 0),
                "north": (0, 0, -1), "south": (0, 0, 1)}

# (名前, 横軸, 縦軸, 奥行軸, 横符号, 縦符号, 奥行符号)
# 奥行符号は「その軸の値が大きいほど手前」を +1 とする
VIEWS = (
    ("LEFT",   2, 1, 0,  1,  1,  1),
    ("RIGHT",  2, 1, 0,  1,  1, -1),
    ("TOP",    2, 0, 1,  1,  1,  1),
    ("BOTTOM", 2, 0, 1,  1, -1, -1),
    ("FRONT",  0, 1, 2, -1,  1, -1),
    ("BACK",   0, 1, 2,  1,  1,  1),
)


def rotate(p, pivot, rx, ry, rz):
    """render 空間での回転。頂点へは X→Y→Z の順に効く（行列としては Rz・Ry・Rx）"""
    x, y, z = p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]
    cx, sx = math.cos(rx), math.sin(rx)
    y, z = y * cx - z * sx, y * sx + z * cx
    cy, sy = math.cos(ry), math.sin(ry)
    x, z = x * cy + z * sy, -x * sy + z * cy
    cz, sz = math.cos(rz), math.sin(rz)
    x, y = x * cz - y * sz, x * sz + y * cz
    return (x + pivot[0], y + pivot[1], z + pivot[2])


def face_shade(n):
    """法線から面の明るさを決める。軸に沿った面では FACE_SHADE の値そのものになる"""
    table = ((FACE_SHADE["east"], FACE_SHADE["west"]),
             (FACE_SHADE["up"], FACE_SHADE["down"]),
             (FACE_SHADE["south"], FACE_SHADE["north"]))
    length = math.sqrt(sum(v * v for v in n)) or 1.0
    return sum((n[i] / length) ** 2 * table[i][0 if n[i] >= 0 else 1] for i in range(3))


def bone_transform(bones_by_name, name, zoff):
    """その Bone に至るまでの、親を遡った変換を返す"""
    steps = []
    while name:
        b = bones_by_name[name]
        r = b.get("rotation")
        if r:
            px, py, pz = b["pivot"]
            steps.append(((-px, py, pz + zoff),
                          (math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2]))))
        name = b.get("parent")

    def apply(p):
        # 自分の回転から順に、親へ向かって掛けていく
        for pivot, rr in steps:
            p = rotate(p, pivot, *rr)
        return p

    return apply


def collect_faces(parts, geo_dir=None, bones=None):
    """全部位の全面を、頂点と UV の組にして集める。

    parts は (モデル名, その部位が後ろへ下がる距離) の並び。単位は 1/16 ブロック。
    bones を渡すと、その Bone 名（集合）か、名前を受けて真偽を返す関数に当たるものだけを描く。
    """
    geo_dir = geo_dir or GEO
    keep = bones if (bones is None or callable(bones)) else (lambda n, s=frozenset(bones): n in s)
    faces = []
    for name, zoff in parts:
        data = json.loads((geo_dir / f"{name}.geo.json").read_text(encoding='utf-8'))
        all_bones = data['minecraft:geometry'][0]['bones']
        by_name = {b['name']: b for b in all_bones}
        for b in all_bones:
            if keep is not None and not keep(b['name']):
                continue
            xf = bone_transform(by_name, b['name'], zoff)
            for c in b.get('cubes', []):
                ox, oy, oz = c['origin']
                w, h, d = c['size']
                x0, x1 = -(ox + w), -ox      # render 空間では X が反転する
                y0, y1 = oy, oy + h
                z0, z1 = oz + zoff, oz + d + zoff
                rot = c.get('rotation')
                if rot:
                    prx, pry, prz = c['pivot']
                    cpivot = (-prx, pry, prz + zoff)
                    crr = (math.radians(-rot[0]), math.radians(-rot[1]), math.radians(rot[2]))
                centre = ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
                for face, corners in FACE_CORNERS.items():
                    fuv = c['uv'][face]
                    (ux, uy), (uw, uh) = fuv['uv'], fuv['uv_size']
                    # uv_size が 0 の面は張らない面。負の面は厚み 0 の板の裏で、表の鏡写し
                    # （Kit.cube）。表と同じ絵が同じ場所に重なるので、表だけ描けば足りる
                    if uw <= 0 or uh <= 0:
                        continue
                    pts, uvs = [], []
                    for (cxi, cyi, czi), (fu, fv) in zip(corners, FACE_UVS[face]):
                        p = (x0 + (x1 - x0) * cxi, y0 + (y1 - y0) * cyi, z0 + (z1 - z0) * czi)
                        if rot:
                            p = rotate(p, cpivot, *crr)
                        pts.append(xf(p))
                        uvs.append((ux + uw * fu, uy + uh * fv))
                    # 面の外向きの法線を、箱と Bone の回転を通したあとの向きで求める。
                    # 回転は剛体なので、中心と「中心から外へ1歩」の2点を運べば足りる
                    step = FACE_NORMALS[face]
                    a = centre
                    b = (centre[0] + step[0], centre[1] + step[1], centre[2] + step[2])
                    if rot:
                        a, b = rotate(a, cpivot, *crr), rotate(b, cpivot, *crr)
                    a, b = xf(a), xf(b)
                    faces.append((pts, uvs, face_shade((b[0] - a[0], b[1] - a[1], b[2] - a[2]))))
    return faces


def common_bounds(faces):
    """六面を同じ縮尺で並べるための、軸と符号ごとの範囲。

    視点ごとに切り詰めると、頭だけ大きい図が混ざって比べられない。
    """
    lo = [1e9] * 3
    hi = [-1e9] * 3
    for pts, _, _ in faces:
        for p in pts:
            for i in range(3):
                lo[i] = min(lo[i], p[i])
                hi[i] = max(hi[i], p[i])
    out = {}
    for i in range(3):
        for f in (1, -1):
            a, b = lo[i] * f, hi[i] * f
            out[(i, f)] = (min(a, b) - 4, max(a, b) + 4)
    return out


def render(faces, tex, hi, vi, di, fh, fv, fd, bounds, tex_size, scale=None):
    """axes は (横, 縦, 奥行) の軸番号、f* はその符号"""
    scale = scale or SCALE
    (x0, x1), (y0, y1) = bounds[(hi, fh)], bounds[(vi, fv)]
    w = int((x1 - x0) * scale)
    h = int((y1 - y0) * scale)
    px = [[BG] * w for _ in range(h)]
    zbuf = [[-1e9] * w for _ in range(h)]

    for pts, uvs, shade in faces:
        scr = [((p[hi] * fh - x0) * scale, (y1 - p[vi] * fv) * scale, p[di] * fd) for p in pts]
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            _tri(px, zbuf, tex, (scr[a], scr[b], scr[c]), (uvs[a], uvs[b], uvs[c]),
                 shade, w, h, tex_size)
    return px, w, h


def _tri(px, zbuf, tex, s, uv, shade, w, h, tex_size):
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = s
    denom = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(denom) < 1e-9:
        return
    for y in range(max(0, int(min(ay, by, cy))), min(h, int(max(ay, by, cy)) + 1)):
        for x in range(max(0, int(min(ax, bx, cx))), min(w, int(max(ax, bx, cx)) + 1)):
            pxc, pyc = x + 0.5, y + 0.5
            l1 = ((by - cy) * (pxc - cx) + (cx - bx) * (pyc - cy)) / denom
            l2 = ((cy - ay) * (pxc - cx) + (ax - cx) * (pyc - cy)) / denom
            l3 = 1.0 - l1 - l2
            if l1 < -0.001 or l2 < -0.001 or l3 < -0.001:
                continue
            z = l1 * az + l2 * bz + l3 * cz
            if z <= zbuf[y][x]:
                continue
            u = l1 * uv[0][0] + l2 * uv[1][0] + l3 * uv[2][0]
            v = l1 * uv[0][1] + l2 * uv[1][1] + l3 * uv[2][1]
            ui = min(tex_size - 1, max(0, int(u)))
            vi_ = min(tex_size - 1, max(0, int(v)))
            r, g, bb, a = tex[vi_][ui]
            if a < 32:
                continue
            zbuf[y][x] = z
            px[y][x] = (int(r * shade), int(g * shade), int(bb * shade))


# ==================== ラベル ====================
# どの図がどの向きか分からないと六面図の意味が無い。外部フォントに頼らず
# 3x5 のビットマップで必要な字だけ持つ。
FONT = {
    'A': ("010", "101", "111", "101", "101"), 'B': ("110", "101", "110", "101", "110"),
    'C': ("011", "100", "100", "100", "011"), 'E': ("111", "100", "110", "100", "111"),
    'F': ("111", "100", "110", "100", "100"), 'G': ("011", "100", "101", "101", "011"),
    'H': ("101", "101", "111", "101", "101"), 'I': ("111", "010", "010", "010", "111"),
    'K': ("101", "110", "100", "110", "101"), 'L': ("100", "100", "100", "100", "111"),
    'M': ("101", "111", "111", "101", "101"), 'N': ("101", "111", "111", "111", "101"),
    'O': ("010", "101", "101", "101", "010"), 'P': ("110", "101", "110", "100", "100"),
    'R': ("110", "101", "110", "101", "101"), 'S': ("011", "100", "010", "001", "110"),
    'T': ("111", "010", "010", "010", "010"), 'U': ("101", "101", "101", "101", "010"),
    'D': ("110", "101", "101", "101", "110"), 'V': ("101", "101", "101", "101", "010"),
    'W': ("101", "101", "111", "111", "101"), 'X': ("101", "101", "010", "101", "101"),
    'Y': ("101", "101", "010", "010", "010"), 'Z': ("111", "001", "010", "100", "111"),
    'J': ("001", "001", "001", "101", "010"), 'Q': ("010", "101", "101", "011", "001"),
    ' ': ("000", "000", "000", "000", "000"),
}


def draw_text(canvas, x, y, text, scale=3, color=LABEL):
    H = len(canvas)
    W = len(canvas[0]) if H else 0
    cx = x
    for ch in text.upper():
        glyph = FONT.get(ch, FONT[' '])
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit != '1':
                    continue
                for sy in range(scale):
                    for sx in range(scale):
                        px_, py_ = cx + gx * scale + sx, y + gy * scale + sy
                        if 0 <= px_ < W and 0 <= py_ < H:
                            canvas[py_][px_] = color
        cx += 4 * scale


def _chunk(tag, data):
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)


def write(path, rows, w, h):
    raw = b''.join(b'\x00' + b''.join(bytes((*p, 255)) for p in row) for row in rows)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'
                     + _chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                     + _chunk(b'IDAT', zlib.compress(raw, 9))
                     + _chunk(b'IEND', b''))


def render_parts(atlas, parts, out, scale=None, views=VIEWS, geo_dir=None, bones=None):
    """部位を連結した六面図を1枚の PNG に並べて書き出す。bones は collect_faces と同じ"""
    tex = atlas.build()
    faces = collect_faces(parts, geo_dir, bones)
    bounds = common_bounds(faces)
    rendered = [(name, *render(faces, tex, hi, vi, di, fh, fv, fd, bounds, atlas.SIZE, scale))
                for name, hi, vi, di, fh, fv, fd in views]

    gap, pad = 14, 22
    W = max(r[2] for r in rendered)
    H = sum(r[3] + pad for r in rendered) + gap * (len(rendered) - 1)
    canvas = [[BG] * W for _ in range(H)]
    y = 0
    for name, rows, w, h in rendered:
        draw_text(canvas, 6, y + 4, name)
        y += pad
        for j in range(h):
            canvas[y + j][:w] = rows[j]
        y += h + gap
    write(out, canvas, W, H)
    print('wrote', out, W, 'x', H)
