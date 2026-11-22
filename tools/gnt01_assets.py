"""GNT01 material assets: the ten giant-animal drops, the Lumen Lantern, loot, recipes, scan targets and research.

Writes only its own files (1.21 layout; Forge: python tools/mc_format.py --to-forge <checkout>):
  assets/abyssia/models/item/<10 materials>.json, blockstates/lumen_lantern.json, models/block|item/lumen_lantern.json
  data/abyssia/loot_table/entities/<10 giants>.json, loot_table/blocks/lumen_lantern.json
  data/abyssia/recipe/*.json (lantern + 8 alternative recipes), scan_targets/giant_<id>.json, technologies/giant_*.json
  tools/lang_parts/gnt01.json (en/ja, merged by gen_deep_assets.lang) and the same keys in ru_ru / zh_cn (English text)
Run by gen_deep_assets.py after it wipes models/ recipes/ blockstates/.  Textures are NOT written (ChatGPT, later):
  textures/item/<material>.png x10 and textures/block/lumen_lantern.png.

    python tools/gnt01_assets.py [--regen-lang]
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "abyssia")
DATA = os.path.join(RES, "data", "abyssia")
LANG_PART = os.path.join(HERE, "lang_parts", "gnt01.json")

# id -> (en, ja)
MATERIALS = {
    "lance_jaw": ("Lance Jaw", "槍顎の骨槍"),
    "lumen_filament": ("Lumen Filament", "灯翼の糸"),
    "lantern_baleen": ("Lantern Baleen", "灯髭"),
    "echo_organ": ("Echo Organ", "反響器官"),
    "slateback_oil": ("Slateback Oil", "石鱗油"),
    "rasp_ring": ("Rasp Ring", "やすり歯輪"),
    "shroud_membrane": ("Shroud Membrane", "帳膜"),
    "glass_spine": ("Glass Spine", "硝子背骨"),
    "crest_spine": ("Crest Spine", "冠鰭の棘"),
    "umbral_gland": ("Umbral Gland", "宵腺"),
}

# giant entity id -> (material, min, max); None = no drops here (lumenwing is sheared, lantern_grazer drops baleen while escorting)
GIANT_DROPS = {
    "lancejaw": ("lance_jaw", 1, 1),
    "lumenwing": None,
    "lantern_grazer": None,
    "hush_whale": ("echo_organ", 1, 1),
    "slateback": ("slateback_oil", 2, 4),
    "rift_lamprey": ("rasp_ring", 1, 1),
    "shroud_ray": ("shroud_membrane", 1, 2),
    "glassback": ("glass_spine", 1, 3),
    "crestfin": ("crest_spine", 2, 5),
    "duskmaw": ("umbral_gland", 1, 1),
}


def write(rel, data):
    path = os.path.normpath(os.path.join(RES, rel))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def item(i, n=1):
    return {"id": "abyssia:" + i, "count": n} if n != 1 else {"id": "abyssia:" + i}


def ing(i):
    return {"item": i if ":" in i else "abyssia:" + i}


def shaped(pattern, key, result, count=1):
    return {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: ing(v) for k, v in key.items()}, "result": item(result, count)}


def shapeless(items, result, count=1):
    return {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [ing(i) for i in items],
            "result": item(result, count)}


RECIPES = {
    # new block: needs the research (recipe key = result item)
    "lumen_lantern": shaped(["PFP", "CBC", "PFP"], {"P": "iron_plate", "F": "lumen_filament", "C": "lumen_cell",
                                                     "B": "lantern_baleen"}, "lumen_lantern"),
    # alternatives for existing items (gated by the research of the result item)
    "abyssal_alloy_sword_from_lance_jaw": shaped(["L", "A", "S"], {"L": "lance_jaw", "A": "abyssal_alloy_ingot", "S": "minecraft:stick"},
                                                 "abyssal_alloy_sword"),
    "refined_oil_from_slateback_oil": shapeless(["slateback_oil"], "refined_oil", 2),
    "drill_head_from_rasp_ring": shaped(["RR", "HG"], {"R": "rasp_ring", "H": "high_strength_alloy_ingot", "G": "iron_gear"}, "drill_head"),
    "pressure_shell_from_glass_spine": shaped(["SS", "RV"], {"S": "glass_spine", "R": "marine_resin", "V": "pressure_valve"}, "pressure_shell"),
    "hardened_tip_from_crest_spine": shapeless(["crest_spine", "iron_plate"], "hardened_tip", 2),
    "abyssal_energy_cell_from_umbral_gland": shapeless(["lumen_cell", "umbral_gland", "crystal_core", "lead_ingot"], "abyssal_energy_cell"),
    "submarine_upgrade_sonar_scanner_from_echo_organ": shaped(
        ["LPL", "KEK", "RAR"], {"L": "minecraft:sea_lantern", "P": "pressure_shell", "K": "minecraft:copper_ingot", "E": "echo_organ",
                                "R": "minecraft:redstone", "A": "abyssal_alloy_ingot"}, "submarine_upgrade_sonar_scanner"),
    "sea_cloth_from_shroud_membrane": shapeless(["shroud_membrane"], "sea_cloth", 3),
}

TECHS = {
    "giant_survey_a": dict(type="research", tier="mk1", band="D", scan=["lantern_grazer", "hush_whale", "slateback", "rift_lamprey"],
                           pre=["marine_biology"], unlocks=[], icon="echo_organ",
                           en=("Giant Survey I", "Scan the escorting, singing and burrowing giants."),
                           ja=("巨大生物調査 I", "灯髭ウオ・静寂鯨・石鱗ウオ・裂溝ヤツメをスキャンする。")),
    "giant_survey_b": dict(type="research", tier="mk2", band="E", scan=["shroud_ray", "glassback", "crestfin", "duskmaw"],
                           pre=["giant_survey_a"], unlocks=[], icon="umbral_gland",
                           en=("Giant Survey II", "Scan the shrouded, glass, crested and night-hunting giants."),
                           ja=("巨大生物調査 II", "帳エイ・硝子背・冠鰭・宵喰らいをスキャンする。")),
}


def loot(giant, drop):
    pools = []
    if drop:
        mat, lo, hi = drop
        funcs = []
        if (lo, hi) != (1, 1):
            funcs.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
        funcs.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                      "count": {"type": "minecraft:uniform", "min": 0, "max": 1}, "limit": 1})   # at most +1 however high the level
        pools.append({"rolls": 1, "bonus_rolls": 0,
                      "entries": [{"type": "minecraft:item", "name": "abyssia:" + mat, "functions": funcs}]})
    return {"type": "minecraft:entity", "pools": pools, "random_sequence": "abyssia:entities/" + giant}


def lang_parts():
    d = {"item.abyssia." + k: list(v) for k, v in MATERIALS.items()}
    d["block.abyssia.lumen_lantern"] = ["Lumen Lantern", "灯翼ランタン"]
    # GNT01 abilities (hush whale sonar jam line on the submarine HUD, lumenwing shearing cooldown)
    d["message.abyssia.submarine.jammed"] = ["SONAR JAMMED - something huge is close", "ソナー妨害中 - 巨大な何かが近くにいる"]
    d["message.abyssia.giant.shear_wait"] = ["The filaments have not grown back yet (%s s)", "糸がまだ生え揃っていない（あと %s 秒）"]
    for k, t in TECHS.items():
        d["technology.abyssia." + k] = [t["en"][0], t["ja"][0]]
        d["technology.abyssia." + k + ".desc"] = [t["en"][1], t["ja"][1]]
    return d


def fill_locales(parts):
    """ru_ru / zh_cn are hand-maintained; give the new keys the English text so they are not blank (missing keys fall back anyway)."""
    for loc in ("ru_ru", "zh_cn"):
        path = os.path.join(ASSETS, "lang", loc + ".json")
        d = json.load(open(path, encoding="utf-8"))
        add = {k: v[0] for k, v in parts.items() if k not in d}
        if add:
            d.update(add)
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(json.dumps(dict(sorted(d.items())), indent=2, ensure_ascii=False) + "\n")


def main():
    for m in MATERIALS:
        write(f"assets/abyssia/models/item/{m}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "abyssia:item/" + m}})
    write("assets/abyssia/blockstates/lumen_lantern.json", {"variants": {"": {"model": "abyssia:block/lumen_lantern"}}})
    write("assets/abyssia/models/block/lumen_lantern.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "abyssia:block/lumen_lantern"}})
    write("assets/abyssia/models/item/lumen_lantern.json", {"parent": "abyssia:block/lumen_lantern"})
    write("data/abyssia/loot_table/blocks/lumen_lantern.json", {
        "type": "minecraft:block", "random_sequence": "abyssia:blocks/lumen_lantern",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "abyssia:lumen_lantern"}]}]})
    for giant, drop in GIANT_DROPS.items():
        write(f"data/abyssia/loot_table/entities/{giant}.json", loot(giant, drop))
        write(f"data/abyssia/scan_targets/giant_{giant}.json", {
            "id": f"abyssia:scan_target/giant_{giant}", "category": "fauna", "name_key": "entity.abyssia." + giant,
            "scan_seconds": 4, "range": 24, "match": {"entity": "abyssia:" + giant}, "fragment_key": "type"})
    for rid, r in RECIPES.items():
        write(f"data/abyssia/recipe/{rid}.json", r)
    for tid, t in TECHS.items():
        write(f"data/abyssia/technologies/{tid}.json", {
            "id": f"abyssia:technology/{tid}", "type": t["type"], "title": "technology.abyssia." + tid, "tier": t["tier"],
            "depth_band": t["band"],
            "scan_requirements": [{"target": f"abyssia:scan_target/giant_{g}", "count": 1} for g in t["scan"]],
            "prerequisites": [f"abyssia:technology/{p}" for p in t["pre"]], "unlocks": t["unlocks"], "icon": "abyssia:" + t["icon"]})
    parts = lang_parts()
    with open(LANG_PART, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(parts.items())), f, indent=2, ensure_ascii=False)
        f.write("\n")
    fill_locales(parts)
    print(f"GNT01 assets written: {len(MATERIALS)} materials, {len(RECIPES)} recipes, {len(TECHS)} technologies")
    if "--regen-lang" in sys.argv:
        sys.path.insert(0, HERE)
        import gen_deep_assets
        gen_deep_assets.lang()
        print("regenerated en_us / ja_jp (gen_deep_assets.lang)")


if __name__ == "__main__":
    main()
