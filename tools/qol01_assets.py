"""QOL01 assets: supply contract item model, recipe and a PLACEHOLDER 16x16 texture (to be replaced by a ChatGPT drawing).
Writes only its own files; if textures/item/supply_contract.png already exists it is left alone (the final art wins)."""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")


def write_json(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def placeholder_texture():
    path = os.path.join(ROOT, "assets", "abyssia", "textures", "item", "supply_contract.png")
    if os.path.exists(path):
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(2, 14):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (2, 13)
            px[x, y] = (122, 154, 178, 255) if edge else (214, 232, 240, 255)
    for y in (5, 7, 9):                      # text lines
        for x in range(5, 11):
            px[x, y] = (33, 74, 110, 255)
    for x in range(8, 11):                   # stamp
        for y in range(10, 12):
            px[x, y] = (200, 70, 60, 255)
    img.save(path)


def main():
    write_json("assets/abyssia/models/item/supply_contract.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "abyssia:item/supply_contract"}})
    write_json("data/abyssia/recipes/supply_contract.json", {      # 1.20.1 folder name and result format
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:paper"}, {"item": "abyssia:deep_fiber"}],
        "result": {"item": "abyssia:supply_contract"}})
    placeholder_texture()
    print("QOL01 assets written")


if __name__ == "__main__":
    main()
