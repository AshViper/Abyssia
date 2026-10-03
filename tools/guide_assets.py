"""Guide book (feature GB01, spec inbox/specs/GB01-guide-book.md): the abyss_guide_book item model, its shapeless
recipe (minecraft:book + abyssia:kelp_leaf) and the item name.

The Java side is com.abyssia.guide (GuideBookRegistry).  The in-book text lives in assets/abyssia/guide/ (hand/ChatGPT
written JSON, never written here); textures are tools/guide_gui.py (placeholders) and later ChatGPT art.

gen_deep_assets.main() should call generate(write, im, DATA) and lang() should merge LANG (same hook as
diving_gear_assets).  Run standalone to write the model and recipe and merge the lang lines:  python tools/guide_assets.py
"""
import json
import os

MOD = "abyssia"
ID = "abyss_guide_book"

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", MOD)
DATA = os.path.join(ROOT, "data")

LANG = {f"item.{MOD}.{ID}": ("Abyssia Guide", "深海ガイドブック")}


def generate(write, im, data_dir):
    """Writes the item model and the crafting recipe; returns the number of items."""
    write(im(ID), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{ID}"}})
    write(os.path.join(data_dir, MOD, "recipes", ID + ".json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": f"{MOD}:kelp_leaf"}],
        "result": {"item": f"{MOD}:{ID}"}})
    return 1


def _write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    generate(_write, lambda n: os.path.join(ASSETS, "models", "item", n + ".json"), DATA)
    for code, idx in (("en_us", 0), ("ja_jp", 1)):
        path = os.path.join(ASSETS, "lang", code + ".json")
        data = json.load(open(path, encoding="utf-8"))
        data.update({k: v[idx] for k, v in LANG.items()})
        _write(path, dict(sorted(data.items())))


if __name__ == "__main__":
    main()
