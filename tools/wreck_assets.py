"""WRK01 wreck core assets: blockstate, block / item model, and a placeholder texture.

Run: python tools/wreck_assets.py   (writes only these files; lang is tools/lang_parts/wreck.json; the block is
com.abyssia.registry.ModWrecks, no loot table: the block has noLootTable()).

The placeholder texture is the industrial_panel tile shifted to rusty orange with a lit core. It is written only when
textures/block/wreck_core.png does not exist, so a ChatGPT texture (inbox/prompts/WRK01-textures.md) is never overwritten.
"""
import colorsys
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssia")
NAME = "wreck_core"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(obj, fh, indent=2)
        fh.write("\n")


def placeholder_texture(path):
    from PIL import Image
    src = Image.open(os.path.join(ASSETS, "textures", "block", "industrial_panel.png")).convert("RGBA")
    out = Image.new("RGBA", src.size)
    w, h = src.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = src.getpixel((x, y))
            hh, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            s = min(1.0, 0.55 + s * 0.3)
            v = min(1.0, v * 0.95 + 0.05)
            # a lit square in the middle (the core)
            if abs(x - (w - 1) / 2) < w / 5 and abs(y - (h - 1) / 2) < h / 5:
                hh, s, v = 0.12, 0.7, 1.0
            else:
                hh = 0.06
            rr, gg, bb = colorsys.hsv_to_rgb(hh, s, v)
            out.putpixel((x, y), (round(rr * 255), round(gg * 255), round(bb * 255), a))
    out.save(path)


def generate(write, bs, bm, im):
    """gen_deep_assets hook (it wipes blockstates / models first): the JSON files plus the placeholder texture."""
    write(bs(NAME), {"variants": {"": {"model": f"abyssia:block/{NAME}"}}})
    write(bm(NAME), {"parent": "minecraft:block/cube_all", "textures": {"all": f"abyssia:block/{NAME}"}})
    write(im(NAME), {"parent": f"abyssia:block/{NAME}"})
    tex = os.path.join(ASSETS, "textures", "block", NAME + ".png")
    if not os.path.exists(tex):
        placeholder_texture(tex)
        print("placeholder texture written:", tex)


def main():
    j = lambda *p: os.path.join(ASSETS, *p)
    generate(write, lambda n: j("blockstates", n + ".json"), lambda n: j("models", "block", n + ".json"),
             lambda n: j("models", "item", n + ".json"))
    print("wreck assets written")


if __name__ == "__main__":
    main()
