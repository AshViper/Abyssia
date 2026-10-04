"""MP01 deep sea map / abyss chart (spec inbox/specs/MP01-deep-maps.md): item models, shaped recipes, lang.
gen_deep_assets.main() calls generate(); lang() merges LANG.  Java side: com.abyssia.map.  Textures are never drawn here.
"""
import os

MOD = "abyssia"

RECIPES = {
    "deep_sea_map": (["DPG", "PCP", "GPD"], {"D": "abyssia:deep_fiber", "G": "abyssia:deep_pigment",
                                              "P": "minecraft:paper", "C": "minecraft:compass"}),
    "abyss_chart": (["SPL", "PCP", "LPS"], {"S": "abyssia:sea_cloth", "L": "abyssia:lumen_gel",
                                             "P": "minecraft:paper", "C": "minecraft:compass"}),
}

# key -> (English, Japanese)
LANG = {
    f"item.{MOD}.deep_sea_map": ("Empty Deep Sea Map", "深海の白地図"),
    f"item.{MOD}.deep_sea_map.filled": ("Deep Sea Map", "深海の地図"),
    f"item.{MOD}.deep_sea_map.tooltip1": ("An empty map for charting the seafloor of the deep sea", "深海層の海底を記録するための白地図"),
    f"item.{MOD}.deep_sea_map.tooltip2": ("Use it in the deep sea layer to map the seafloor", "深海層で使用すると海底の地形を描きます"),
    f"item.{MOD}.abyss_chart": ("Abyss Chart", "深海への海図"),
    f"item.{MOD}.abyss_chart.filled": ("Abyss Chart", "深海への海図"),
    f"item.{MOD}.abyss_chart.tooltip1": ("A chart that searches for a nearby entrance to the deep sea", "近くの深海への入口を探す海図"),
    f"item.{MOD}.abyss_chart.tooltip2": ("Use it to locate the nearest entrance", "使用すると最寄りの入口を示します"),
    f"message.{MOD}.deep_map.outside": ("The Deep Sea Map must be used in the deep sea layer.", "深海の地図は深海層で使用してください。"),
    f"message.{MOD}.abyss_chart.searching": ("Searching for an entrance to the deep sea...", "深海への入口を探しています……"),
    f"message.{MOD}.abyss_chart.none": ("No entrance to the deep sea could be found nearby.", "近くに深海への入口が見つかりません。"),
    f"message.{MOD}.abyss_chart.cooldown": ("The Abyss Chart cannot be used yet.", "深海への海図はまだ使用できません。"),
}


def generate(write, im, data_dir):
    for name, (pattern, key) in RECIPES.items():
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})
        write(os.path.join(data_dir, MOD, "recipes", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": f"{MOD}:{name}"}})
    return len(RECIPES)
