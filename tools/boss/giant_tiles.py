# -*- coding: utf-8 -*-
"""GNT01 巨大野生生物: ChatGPT が描いた「素材タイル」でモデルの面を塗る共通モジュール。

形式: 種ごとに素材タイル（16x16 ドット、1 素材 1 枚）。`tools/boss/<id>_model/tiles/<素材>.png`。
素材の一覧は `tools/boss/<id>_model/tiles/materials.json`（build.py が出す。順番 = ChatGPT のシートのマス順、
左上から行優先。`describe` を ChatGPT への指示に使う）。タイルが無い素材・種は今までの平らな色のまま。

仕組み: build.py が持つ「素材 → その素材に属する色（16 進）」の表を `Tiler` に渡す。各キットは面の画素を平らな色で
塗ったあと `Tiler.apply` を通す。色が素材に属していればタイルの画素（面の中の位置 + 面ごとにずらした開始位置で
16 周期に敷く）に、元の色と素材の基準色の明るさの比を掛ける（= 今の陰影 2〜3 段、上面・側面・下面の差が残る）。
発光する画素と、素材に属さない色（眼・歯の差し色など）は今のまま。半透明のガラスはアルファを保つ。

再生成の手順（ChatGPT がシートを描いたあと。1 枚の画像を cols x rows の格子に区切り、各マスが 1 素材）:
  1. python tools/boss/giant_tiles.py split <id> <sheet.png> <cols> <rows>
       （各マスの内側 8% を余白として捨て、16x16 に縮小、色を減らして tiles/<素材>.png へ）
  2. python tools/boss/<id>_model/build.py            （geo・テクスチャを再生成）
  3. python tools/boss/check_animations.py <id>        （アニメ・面の検証）
  4. python tools/boss/<id>_model/preview.py           （見た目の確認）
他の種: build.py で `TILES = giant_tiles.Tiler(NAME, MATERIALS)` を作りキットの Model に `tiles` として渡す。
"""
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
TILE = 16


def _hex(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def _lum(c):
    return max(1.0, 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2])


def tiles_for(id_):
    """{素材名: 16x16 の RGB 行列} を返す。tiles/ に 1 枚も無ければ None"""
    from PIL import Image
    d = HERE / f'{id_}_model' / 'tiles'
    got = {}
    if d.is_dir():
        for p in sorted(d.glob('*.png')):
            im = Image.open(p).convert('RGB')
            if im.size == (TILE, TILE):
                px = im.load()
                got[p.stem] = [[px[x, y] for x in range(TILE)] for y in range(TILE)]
    return got or None


class Tiler:
    """materials: [(名前, "英語 1 行の説明", "基準色 色 色 ...（16 進）")]。先頭の色が素材の基準色"""

    def __init__(self, id_, materials):
        self.id = id_
        self.materials = materials
        self.color_to = {}       # rgb → (素材名, 基準色の明るさ)
        for name, _desc, colors in materials:
            cols = [_hex(c) for c in colors.split()]
            for c in cols:
                assert c not in self.color_to, (id_, name, c)
                self.color_to[c] = (name, _lum(cols[0]))
        self.tiles = tiles_for(id_)
        self._write_materials()

    def _write_materials(self):
        d = HERE / f'{self.id}_model' / 'tiles'
        d.mkdir(exist_ok=True)
        data = [{'name': n, 'describe': desc, 'base_rgb': list(_hex(cols.split()[0]))}
                for n, desc, cols in self.materials]
        text = json.dumps(data, ensure_ascii=False, indent=1) + '\n'
        p = d / 'materials.json'
        if not p.exists() or p.read_text(encoding='utf-8') != text:
            p.write_text(text, encoding='utf-8')

    def apply(self, col, u, v, i, j, glow=False):
        """col = (r,g,b) か (r,g,b,a)。(u,v) は面のアトラス上の左上、(i,j) は面の中の位置。
        タイル無し・発光・素材外なら col をそのまま返す"""
        if not self.tiles or glow:
            return col
        hit = self.color_to.get((int(col[0]), int(col[1]), int(col[2])))
        if hit is None:
            return col
        tile = self.tiles.get(hit[0])
        if tile is None:
            return col
        h = (u * 73856093 ^ v * 19349663) & 0xFFFFFF
        ox, oy = h % TILE, (h >> 8) % TILE
        r, g, b = tile[(j + oy) % TILE][(i + ox) % TILE]
        k = _lum(col) / hit[1]
        out = (min(255, int(r * k + 0.5)), min(255, int(g * k + 0.5)), min(255, int(b * k + 0.5)))
        return out + tuple(int(x) for x in col[3:])


def split(id_, sheet, cols, rows):
    """シート画像を等分し、各マスの内側 8% を捨てて 16x16 へ縮小・減色し tiles/<素材>.png に保存"""
    from PIL import Image
    mats = json.loads((HERE / f'{id_}_model' / 'tiles' / 'materials.json').read_text(encoding='utf-8'))
    assert len(mats) <= cols * rows, f'素材 {len(mats)} 個に対しマスが {cols * rows} 個'
    im = Image.open(sheet).convert('RGB')
    cw, ch = im.width / cols, im.height / rows
    for n, m in enumerate(mats):
        r, c = divmod(n, cols)
        x0, y0 = c * cw, r * ch
        box = (round(x0 + cw * 0.08), round(y0 + ch * 0.08), round(x0 + cw * 0.92), round(y0 + ch * 0.92))
        cell = im.crop(box)
        small = cell.resize((TILE * 4, TILE * 4), Image.BOX).resize((TILE, TILE), Image.BOX)
        small = small.quantize(colors=5, method=Image.Quantize.MEDIANCUT).convert('RGB')
        out = HERE / f'{id_}_model' / 'tiles' / f'{m["name"]}.png'
        small.save(out)
        print('wrote', out)


if __name__ == '__main__':
    if len(sys.argv) == 6 and sys.argv[1] == 'split':
        split(sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5]))
    else:
        print(__doc__)
        sys.exit(1)
