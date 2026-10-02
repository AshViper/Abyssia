"""Entry diving gear (feature D01, spec inbox/specs/D01-entry-diving-gear.md): the four vanilla-material starter
pieces entry_diver_helmet / entry_dive_tank / entry_diving_suit_leggings / entry_diving_flippers.

Data plus the JSON writers (item models, crafting recipes) and the names; gen_deep_assets.main() calls generate()
and lang() merges LANG.  The Java side is com.abyssia.item.EntryDivingGear.  Textures (textures/item/entry_*.png,
locked in tools/texture_locks) and the armor layers (tools/armor_layers.py) are never written here.
"""
import os

MOD = "abyssia"

# id -> (English, Japanese, recipe pattern, recipe key)
GEAR = {
    "entry_diver_helmet": ("Entry Diver Helmet", "簡易潜水ヘルム", ["IGI", "ILI", "I I"],
                           {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "L": "minecraft:leather"}),
    "entry_dive_tank": ("Entry Dive Tank", "簡易潜水タンク", [" I ", "IKI", "III"],
                        {"I": "minecraft:iron_ingot", "K": "minecraft:kelp"}),
    "entry_diving_suit_leggings": ("Entry Diving Suit Leggings", "簡易潜水レギンス", ["LIL", "L L", "L L"],
                                   {"I": "minecraft:iron_ingot", "L": "minecraft:leather"}),
    "entry_diving_flippers": ("Entry Diving Flippers", "簡易潜水フィン", ["L L", "L L", "S S"],
                              {"L": "minecraft:leather", "S": "minecraft:string"}),
}

LANG = {f"item.{MOD}.{k}": (en, ja) for k, (en, ja, _, _) in GEAR.items()}


def generate(write, im, data_dir):
    """Writes the item models and crafting recipes; returns the number of items."""
    for name, (_, _, pattern, key) in GEAR.items():
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})
        write(os.path.join(data_dir, MOD, "recipes", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
            "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": f"{MOD}:{name}"}})
    return len(GEAR)
