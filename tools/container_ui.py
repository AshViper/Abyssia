#!/usr/bin/env python3
"""UI02-A: recolour vanilla container GUI textures into the Abyssia deep-sea navy theme.

Reads PNGs from the local vanilla client jar, writes recoloured copies (plus .png.mcmeta)
under <root>/src/main/resources/assets/minecraft/textures/.  Writes nothing else.

  python tools/container_ui.py --loader neoforge --root F:/Java/Abyssia-NeoForge
  python tools/container_ui.py --loader forge --root F:/Java/Abyssia
  options: --jar <path>  --preview <png>  --list

Low-saturation pixels go through a monotonic luminance ramp anchored on the vanilla GUI
greys; saturated pixels (flame, bubbles, XP, red/green marks, lapis) and alpha are kept.
"""
import argparse
import io
import os
import sys
import zipfile

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ui_kit import ABYSS, NAVY_DEEP, NAVY, NAVY_MID, STEEL_DARK  # noqa: E402

JARS = {
    "neoforge": r"C:\Users\nagis\.gradle\caches\neoformruntime\artifacts\minecraft_1.21.1_client.jar",
    "forge": r"C:\Users\nagis\.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client.jar",
}
PFX = "assets/minecraft/textures/"
SAT_MAX = 28  # max(r,g,b)-min(r,g,b) above this = saturated, left untouched

# luminance anchors (vanilla grey -> role colour); monotonic in brightness
RAMP = [
    (0x00, ABYSS),       # 000000 outline
    (0x37, ABYSS),       # 373737 slot shadow
    (0x55, NAVY_DEEP),   # 555555 panel shadow
    (0x8B, NAVY),        # 8B8B8B slot fill (recess)
    (0xC6, NAVY_MID),    # C6C6C6 panel
    (0xFF, STEEL_DARK),  # FFFFFF bevel highlight
]

FOLDERS = ("gui/container/", "gui/sprites/container/", "gui/sprites/recipe_book/")
FILES = ("gui/recipe_book.png",)
EMPTY = "item/empty_"
EXCLUDE_SUB = (
    "container/stats_icons", "container/gamemode_switcher", "container/bundle",
    "sprites/container/bundle/", "sprites/container/enchanting_table/",  # clue buttons + level icons stay vanilla
)
# whole-sheet regions kept vanilla: file -> (x0, y0, x1, y1)
KEEP_REGION = {"gui/container/enchanting_table.png": (0, 166, 256, 256)}


def wanted(rel):
    if not (rel.endswith(".png") or rel.endswith(".png.mcmeta")):
        return False
    if any(s in rel for s in EXCLUDE_SUB):
        return False
    base = rel[:-len(".mcmeta")] if rel.endswith(".mcmeta") else rel
    return (rel.startswith(FOLDERS) or base in FILES or rel.startswith(EMPTY)
            or any(base.startswith(f) for f in FOLDERS))


def ramp(lum):
    for (a, ca), (b, cb) in zip(RAMP, RAMP[1:]):
        if lum <= b:
            t = (lum - a) / (b - a)
            return tuple(round(ca[i] + (cb[i] - ca[i]) * t) for i in range(3))
    return RAMP[-1][1][:3]


def recolour(img, keep=None):
    img = img.convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            if keep and keep[0] <= x < keep[2] and keep[1] <= y < keep[3]:
                continue
            r, g, b, a = px[x, y]
            if a == 0 or max(r, g, b) - min(r, g, b) > SAT_MAX:
                continue
            px[x, y] = ramp(round(0.299 * r + 0.587 * g + 0.114 * b)) + (a,)
    return img


def file_list(z):
    names = [n[len(PFX):] for n in z.namelist() if n.startswith(PFX)]
    return sorted(n for n in names if wanted(n))


def open_jar(path):
    if not os.path.isfile(path):
        sys.exit("ERROR: client jar not found: " + path)
    return zipfile.ZipFile(path)


def load(z, rel):
    return Image.open(io.BytesIO(z.read(PFX + rel))).convert("RGBA")


PREVIEW = ["container/inventory", "container/creative_inventory/tabs", "container/generic_54",
           "container/furnace", "container/crafting_table", "container/anvil",
           "container/enchanting_table", "container/brewing_stand", "container/beacon",
           "container/villager", "container/villager2", "container/horse",
           "container/stonecutter", "container/loom", "container/cartography_table",
           "container/grindstone", "container/smithing", "container/crafter", "recipe_book",
           "container/creative_inventory/tab_items", "container/hopper"]


def preview(z, names, out):
    cells = []
    for p in PREVIEW:
        rel = "gui/" + p + ".png"
        if rel not in names:
            continue
        v = load(z, rel).crop((0, 0, 256, 256))
        n = recolour(v, KEEP_REGION.get(rel))
        cells.append((v, n))
    cw, ch, pad, cols = 256, 256, 4, 3
    rows = (len(cells) + cols - 1) // cols
    W = cols * (2 * cw + 3 * pad)
    sheet = Image.new("RGBA", (W, rows * (ch + pad)), (255, 0, 255, 255))
    for i, (v, n) in enumerate(cells):
        ox = (i % cols) * (2 * cw + 3 * pad)
        oy = (i // cols) * (ch + pad)
        for k, im in enumerate((v, n)):
            sheet.alpha_composite(im, (ox + k * (cw + pad), oy))
    sheet.convert("RGB").save(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--loader", choices=JARS, required=True)
    ap.add_argument("--root", required=True)
    ap.add_argument("--jar")
    ap.add_argument("--preview")
    ap.add_argument("--list", action="store_true")
    a = ap.parse_args()
    z = open_jar(a.jar or JARS[a.loader])
    names = file_list(z)
    if a.list:
        print("\n".join(names))
        print(len(names), "files")
        return
    if a.preview:
        preview(z, set(names), a.preview)
        print("preview ->", a.preview)
        return
    out = os.path.join(a.root, "src", "main", "resources", "assets", "minecraft", "textures")
    for rel in names:
        dst = os.path.join(out, *rel.split("/"))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if rel.endswith(".mcmeta"):
            data = z.read(PFX + rel)
        else:
            buf = io.BytesIO()
            recolour(load(z, rel), KEEP_REGION.get(rel)).save(buf, "PNG")
            data = buf.getvalue()
        with open(dst, "wb") as f:
            f.write(data)
    print("wrote", len(names), "files under", out)


if __name__ == "__main__":
    main()
