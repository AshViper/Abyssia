"""EGG01 decoder: decode.py S1  ->  out/<id>_spawn_egg.png (16x16 RGBA, centred) + _preview_S1.png

Input  inbox/textures/EGG01/<sheet>.txt, one line per egg: `i|w|h|palette(hex,...)|RLE` where RLE is
palette chars ('0'-'9','a'-'z', '.' = transparent) and `c*n ` = c repeated n times.
Ids come from inbox/prompts/EGG01-sheets.json (same order as the 5x2 grid)."""
import json, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
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


def main(sheet):
    ids = json.load(open(os.path.join(HERE, '..', '..', 'prompts', 'EGG01-sheets.json'), encoding='utf-8'))['sheets'][sheet]
    outdir = os.path.join(HERE, 'out')
    os.makedirs(outdir, exist_ok=True)
    tiles = []
    for line in open(os.path.join(HERE, sheet + '.txt'), encoding='utf-8'):
        if not line.strip():
            continue
        t, w, h, pal, rle = line.rstrip('\n').split('|', 4)
        w, h = int(w), int(h)
        pal = [tuple(int(c[k:k + 2], 16) for k in (0, 2, 4)) for c in pal.split(',')]
        idx = unrle(rle)
        assert len(idx) == w * h, (sheet, t, len(idx), w * h)
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        ox, oy = (16 - w) // 2, 16 - h  # centred horizontally, standing on the bottom row
        for k, ch in enumerate(idx):
            if ch != '.':
                im.putpixel((ox + k % w, oy + k // w), pal[AL.index(ch)] + (255,))
        tiles.append(im)
    assert len(tiles) == len(ids), (sheet, len(tiles), len(ids))
    prev = Image.new('RGBA', (len(tiles) * 136 + 8, 144), (90, 90, 100, 255))
    for k, (im, name) in enumerate(zip(tiles, ids)):
        im.save(os.path.join(outdir, name + '_spawn_egg.png'))
        big = im.resize((128, 128), Image.NEAREST)
        prev.paste(big, (8 + k * 136, 8), big)
    prev.save(os.path.join(HERE, '_preview_' + sheet + '.png'))
    print(sheet, 'wrote', len(tiles))


if __name__ == '__main__':
    for s in sys.argv[1:]:
        main(s)
