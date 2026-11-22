"""STY01 decoder: decode.py I01 E03 ...  ->  out/<id>.png (16x16 RGBA) + _preview_<sheet>.png (current vs new)

Input <sheet>.txt, one line per icon: `i|w|h|palette(hex,...)|RLE` (from extract.js __all()).
Ids come from inbox/prompts/STY01-sheets.json. Items are centred, spawn eggs stand on the bottom row."""
import json, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..', '..')
CUR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abyssia', 'textures', 'item')
AL = '0123456789abcdefghijklmnopqrstuvwxyz'


def unrle(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if i + 1 < len(s) and s[i + 1] == '*':
            j = i + 2
            while j < len(s) and s[j].isdigit():
                j += 1
            out.append(c * int(s[i + 2:j]))
            i = j + 1 if j < len(s) and s[j] == ' ' else j
        else:
            if c != ' ':
                out.append(c)
            i += 1
    return ''.join(out)


def outline(im):
    """Ingot-style rim: every edge pixel becomes a dark shade of the colour just inside it (vanilla ingots have
    no black outline, the rim is the item's own hue darkened, a little lighter on the lit top-left side)."""
    px = im.load()
    op = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and px[x, y][3] > 0
    edge = {(x, y) for y in range(16) for x in range(16) if op(x, y) and
            not all(op(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    out = im.copy()
    po = out.load()
    for x, y in edge:
        ins = [px[x + dx, y + dy] for dx in (-1, 0, 1) for dy in (-1, 0, 1)
               if (dx or dy) and op(x + dx, y + dy) and (x + dx, y + dy) not in edge]
        src = tuple(sum(c[i] for c in ins) / len(ins) for i in range(3)) if ins else px[x, y][:3]
        lit = not op(x - 1, y) or not op(x, y - 1)  # open towards the top-left light
        f = 0.55 if lit else 0.38
        # keep it at least as dark as the drawn rim, but in the item's own hue
        cur = px[x, y][:3]
        if sum(cur) < sum(src) * f:  # drawn rim already darker than ours: keep its darkness, take our hue
            f = max(sum(cur) / max(sum(src), 1), 0.22)
        po[x, y] = tuple(int(c * f) for c in src) + (255,)
    return out


def main(sheet):
    ids = json.load(open(os.path.join(ROOT, 'inbox', 'prompts', 'STY01-sheets.json'), encoding='utf-8'))['sheets'][sheet]
    outdir = os.path.join(HERE, 'out')
    os.makedirs(outdir, exist_ok=True)
    tiles = []
    for line in open(os.path.join(HERE, sheet + '.txt'), encoding='utf-8'):
        if not line.strip():
            continue
        t, w, h, pal, rle = line.rstrip('\n').split('|', 4)
        w, h = int(w), int(h)
        pal = [tuple(int(c[k:k + 2], 16) for k in (0, 2, 4)) for c in pal.split(',')]
        idx = unrle(rle.strip())
        assert len(idx) == w * h, (sheet, t, len(idx), w * h)
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        egg = sheet.startswith('E')
        ox, oy = (16 - w) // 2, (16 - h) if egg else (16 - h) // 2
        for k, ch in enumerate(idx):
            c = pal[AL.index(ch)] if ch != '.' else None
            if c and not (c[0] > 150 and c[1] < 120 and c[2] > 150):  # drop magenta fringe
                im.putpixel((ox + k % w, oy + k // w), pal[AL.index(ch)] + (255,))
        tiles.append(outline(im))
    assert len(tiles) == len(ids), (sheet, len(tiles), len(ids))
    prev = Image.new('RGBA', (len(tiles) * 104 + 8, 216), (90, 90, 100, 255))
    for k, (im, name) in enumerate(zip(tiles, ids)):
        im.save(os.path.join(outdir, name + '.png'))
        old = Image.open(os.path.join(CUR, name + '.png')).convert('RGBA').crop((0, 0, 16, 16))
        for row, src in enumerate((old, im)):
            big = src.resize((96, 96), Image.NEAREST)
            prev.paste(big, (8 + k * 104, 8 + row * 104), big)
    prev.save(os.path.join(HERE, '_preview_' + sheet + '.png'))
    print(sheet, 'wrote', len(tiles))


if __name__ == '__main__':
    for s in sys.argv[1:]:
        main(s)
