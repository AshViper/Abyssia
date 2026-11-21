"""decode.py LINES.txt OUTDIR id1,id2,...  ->  OUTDIR/<id>.png (16x16 RGBA, centred) + OUTDIR/_preview_<name>.png"""
import sys, os
from PIL import Image
AL = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
lines, out, ids = sys.argv[1], sys.argv[2], sys.argv[3].split(',')
opaque = len(sys.argv) > 4 and sys.argv[4] == 'opaque'
os.makedirs(out, exist_ok=True)
tiles = []
for line in open(lines, encoding='utf-8'):
    if not line.strip():
        continue
    t, w, h, pal, idx = line.rstrip('\n').split('|')
    w, h = int(w), int(h)
    pal = [tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) for c in pal.split(',')]
    assert len(idx) == w * h, (t, len(idx), w * h)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    ox, oy = (16 - w) // 2, (16 - h) // 2
    for i, ch in enumerate(idx):
        if ch != '.':
            im.putpixel((ox + i % w, oy + i // w), pal[AL.index(ch)] + (255,))
    if opaque:
        assert all(ch != '.' for ch in idx), 'opaque tile has holes: ' + t
    tiles.append(im)
assert len(tiles) == len(ids), (len(tiles), ids)
prev = Image.new('RGBA', (len(tiles) * 136 + 8, 144), (90, 90, 100, 255))
for k, (im, name) in enumerate(zip(tiles, ids)):
    im.save(os.path.join(out, name + '.png'))
    big = im.resize((128, 128), Image.NEAREST)
    prev.paste(big, (8 + k * 136, 8), big)
prev.save(os.path.join(out, '_preview_' + os.path.basename(lines).split('.')[0] + '.png'))
print('wrote', len(tiles))
