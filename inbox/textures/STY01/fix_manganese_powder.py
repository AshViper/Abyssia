"""STY01 fix: the purple manganese_powder pile lost a bay of pixels to the in-page magenta test, and the gap reaches
the border so decode.fill_holes can't see it. Rebuild it from the cobalt_powder pile (same pile drawing) with
manganese_powder's own colours mapped by lightness rank. Run after decode.py."""
from PIL import Image

L = lambda c: 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
src = Image.open('out/cobalt_powder.png').convert('RGBA')
mn = Image.open('out/manganese_powder.png').convert('RGBA')
ramp = sorted({p[:3] for p in mn.getdata() if p[3]}, key=L)
ramp = [ramp[round(i * (len(ramp) - 1) / 5)] for i in range(6)]  # 6 shades like the other piles
cols = sorted({p[:3] for p in src.getdata() if p[3]}, key=L)
m = {c: ramp[round(i * (len(ramp) - 1) / max(len(cols) - 1, 1))] for i, c in enumerate(cols)}
out = Image.new('RGBA', (16, 16))
out.putdata([m[p[:3]] + (255,) if p[3] else (0, 0, 0, 0) for p in src.getdata()])
out.save('out/manganese_powder.png')
print('manganese_powder rebuilt,', len(cols), '->', len(ramp), 'colours')
