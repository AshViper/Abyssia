#!/usr/bin/env python3
"""BAS01 habitat upkeep assets (idempotent): item models + recipes (1.20.1 format) of abyssia:hull_patch_kit and
abyssia:hull_reinforcement, and placeholder textures (a tinted copy of item/iron_plate.png, written only when the file is
missing, so a ChatGPT-made texture dropped in later is never overwritten).

    python tools/habitat_upkeep_assets.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
NS = "abyssia"


def write_json(path, data):
    text = json.dumps(data, indent=2) + "\n"
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            if f.read() == text:
                return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("wrote", os.path.relpath(path, ROOT))


def item(i):
    return {"item": i}


def placeholder(name, tint):
    out = os.path.join(RES, "assets", NS, "textures", "item", name + ".png")
    if os.path.exists(out):
        return
    from PIL import Image
    src = Image.open(os.path.join(RES, "assets", NS, "textures", "item", "iron_plate.png")).convert("RGBA")
    px = src.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (min(255, int(r * tint[0])), min(255, int(g * tint[1])), min(255, int(b * tint[2])), a)
    src.save(out)
    print("wrote", os.path.relpath(out, ROOT))


def main():
    for name, tint in (("hull_patch_kit", (0.75, 1.05, 1.25)), ("hull_reinforcement", (1.25, 1.0, 0.75))):
        write_json(os.path.join(RES, "assets", NS, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        placeholder(name, tint)
    recipes = os.path.join(RES, "data", NS, "recipes")
    write_json(os.path.join(recipes, "hull_patch_kit.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "equipment",
        "ingredients": [item(f"{NS}:iron_plate"), item(f"{NS}:iron_plate"), item("minecraft:dried_kelp"), item("minecraft:dried_kelp")],
        "result": {"item": f"{NS}:hull_patch_kit", "count": 2},
    })
    write_json(os.path.join(recipes, "hull_reinforcement.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": ["PTP", "PLP", "PTP"],
        "key": {"P": item(f"{NS}:iron_plate"), "T": item(f"{NS}:titanium_ingot"), "L": item(f"{NS}:lead_ingot")},
        "result": {"item": f"{NS}:hull_reinforcement", "count": 1},
    })


if __name__ == "__main__":
    main()
