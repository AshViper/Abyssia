"""ORE01: derives the Mk2 excavator textures (hue-shifted Mk1) from the ChatGPT-drawn Mk1 ones.

Sources (imported from the ChatGPT sheets EXC1 / EXC1I through tools/agentflow/sheets.py import, never written here):
  textures/block/exc_<name>.png         the 16x16 tiles of the multiblock (see tools/bt01/excavator_assets.py)
  textures/item/abyssal_excavator.png   the Mk1 icon (build menu)
Writes:
  textures/block/exc2_<name>.png        Mk2 tiles
  textures/item/abyssal_excavator_mk2.png   Mk2 icon
A missing source is skipped with a message; a file is only rewritten when its pixels change. The textures folders are
never wiped by the generators, so the derived files need no entry in tools/texture_locks.
Run: python tools/excavator_textures.py"""
import colorsys
import os

from PIL import Image, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(os.path.dirname(HERE), "src", "main", "resources", "assets", "abyssia", "textures")
HUE_SHIFT = 0.5     # Mk2 colour: opposite side of the hue wheel
NAMES = ("plate", "plate_dark", "hazard", "drill", "pipe", "glow", "frame", "vent")


def recolor(img, shift):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.15:
                h = (h + shift) % 1.0
            r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


def derive(src_path, dst_path):
    """returns 'written' / 'same' / 'missing'"""
    if not os.path.exists(src_path):
        return "missing"
    mk2 = recolor(Image.open(src_path).convert("RGBA"), HUE_SHIFT)
    if os.path.exists(dst_path):
        old = Image.open(dst_path).convert("RGBA")
        if old.size == mk2.size and ImageChops.difference(old, mk2).getbbox() is None:
            return "same"
    os.makedirs(os.path.dirname(dst_path), exist_ok=True)
    mk2.save(dst_path)
    return "written"


def main():
    jobs = [(os.path.join(TEX, "block", f"exc_{n}.png"), os.path.join(TEX, "block", f"exc2_{n}.png")) for n in NAMES]
    jobs.append((os.path.join(TEX, "item", "abyssal_excavator.png"), os.path.join(TEX, "item", "abyssal_excavator_mk2.png")))
    counts = {"written": 0, "same": 0, "missing": 0}
    for src, dst in jobs:
        result = derive(src, dst)
        counts[result] += 1
        if result == "missing":
            print("skipped (source missing, import the ChatGPT sheet EXC1 / EXC1I first):", os.path.relpath(src, TEX))
    print(f"excavator textures: {counts['written']} written, {counts['same']} unchanged, {counts['missing']} skipped")


if __name__ == "__main__":
    main()
