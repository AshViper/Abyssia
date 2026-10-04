"""Checks that every item a recipe names actually exists (read-only).

Walks data/abyssia/recipes/*.json and resolves every item id (ingredients, results, smithing template/base/addition):

* ``abyssia:`` ids must be registered: a string literal in a Java registration call (ModItems / ModTools /
  MaterialTools / ModPlants / ModBlocks ...) or, for block items generated in Java loops (stone families etc.),
  an item model in assets/abyssia/models/item/.
* ``minecraft:`` ids must be on the VANILLA allow-list below (extend it when a recipe uses a new vanilla item).
* ``abyssia:`` tags must have a tag file; ``minecraft:`` / ``forge:`` tags are listed as unchecked.

It also cross-checks tools/material_spec.json: every spec item registered in Java with an item model, every spec
recipe generated.  Exit code 1 on any problem.

    python tools/check_recipes.py              # JSON report (resolved_by_model_only as a count)
    python tools/check_recipes.py --verbose    # ... with the ids resolved only through an item model
"""
from __future__ import annotations

import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "abyssia")
RECIPES = os.path.join(RES, "data", "abyssia", "recipes")
ITEM_MODELS = os.path.join(RES, "assets", "abyssia", "models", "item")
SPEC = os.path.join(HERE, "material_spec.json")

VANILLA = {
    "stick", "string", "leather", "iron_ingot", "copper_ingot", "gold_ingot", "raw_iron", "raw_copper", "raw_gold",
    "iron_nugget", "gold_nugget", "coal", "charcoal", "diamond", "emerald", "redstone", "glowstone_dust", "gunpowder",
    "bone_meal", "bone", "paper", "compass", "book", "slime_ball", "honeycomb", "clay_ball", "brick", "flint", "feather",
    "glass", "glass_pane", "tinted_glass", "torch", "lantern", "sea_lantern", "prismarine_shard", "prismarine_crystals",
    "nautilus_shell", "heart_of_the_sea", "kelp", "dried_kelp", "sponge", "wet_sponge", "sugar", "bowl", "bucket",
    "water_bucket", "glass_bottle", "potion", "lead", "white_wool", "scaffolding", "packed_mud", "mud",
    "black_dye", "blue_dye", "yellow_dye", "white_dye", "red_dye", "green_dye", "cyan_dye", "light_blue_dye",
    "purple_dye", "magenta_dye", "orange_dye", "lime_dye", "pink_dye", "gray_dye", "light_gray_dye", "brown_dye",
    "fire_charge", "blaze_powder", "magma_cream", "conduit", "spyglass", "daylight_detector", "redstone_lamp",
    "sticky_piston", "piston", "campfire", "soul_campfire", "glow_item_frame", "item_frame", "glow_ink_sac", "ink_sac",
    "turtle_helmet", "blast_furnace", "furnace", "chest", "barrel", "crafting_table", "stone", "cobblestone",
    "deepslate", "cobbled_deepslate", "sand", "gravel", "oak_planks", "iron_block", "copper_block", "gold_block",
}

# Registration helpers whose first string argument is an item id.
_REG = re.compile(r'\b(?:item|sourcedItem|tabItem|blockItem|doubleHighBlockItem|spawnEgg|add|register|block|'
                  r'resource|material|plant|ore|crust|rock|soft|simple)\(\s*(?:items,\s*tab,\s*)?"([a-z0-9_/]+)"')


def java_ids() -> set[str]:
    ids = set()
    for path in glob.glob(os.path.join(JAVA, "**", "*.java"), recursive=True):
        with open(path, encoding="utf-8") as f:
            ids.update(_REG.findall(f.read()))
    return ids


def model_ids() -> set[str]:
    return {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(ITEM_MODELS, "*.json"))}


def refs(node, out: list):
    """Collect ("item"|"tag", id) pairs from a recipe (ingredient objects, results, plain-string results)."""
    if isinstance(node, dict):
        for key in ("item", "tag"):
            if isinstance(node.get(key), str):
                out.append((key, node[key]))
        for key, value in node.items():
            if key == "result" and isinstance(value, str):
                out.append(("item", value))
            elif key not in ("item", "tag"):
                refs(value, out)
    elif isinstance(node, list):
        for value in node:
            refs(value, out)
    return out


def tag_exists(full: str) -> bool:
    ns, _, path = full.partition(":")
    return os.path.isfile(os.path.join(RES, "data", ns, "tags", "items", *path.split("/")) + ".json")


def check() -> dict:
    java, models = java_ids(), model_ids()
    report = {"recipes": 0, "refs": 0, "unresolved": [], "unchecked_tags": [], "resolved_by_model_only": [],
              "invalid_json": [], "bad_pattern": [], "spec": {}}
    by_model = set()
    for path in sorted(glob.glob(os.path.join(RECIPES, "**", "*.json"), recursive=True)):
        rid = os.path.relpath(path, RECIPES)[:-5].replace(os.sep, "/")
        try:
            with open(path, encoding="utf-8") as f:
                recipe = json.load(f)
        except (OSError, ValueError) as e:
            report["invalid_json"].append({"recipe": rid, "error": str(e)})
            continue
        report["recipes"] += 1
        if recipe.get("type") == "minecraft:crafting_shaped":
            # vanilla refuses the whole recipe on unused / undefined key symbols or ragged rows
            used = {c for row in recipe.get("pattern", []) for c in row if c != " "}
            keys = set(recipe.get("key", {}))
            if used != keys or len({len(row) for row in recipe.get("pattern", [])}) > 1:
                report["bad_pattern"].append({"recipe": rid, "unused_keys": sorted(keys - used),
                                              "undefined_symbols": sorted(used - keys)})
        for kind, full in refs(recipe, []):
            report["refs"] += 1
            ns, _, name = full.partition(":") if ":" in full else ("minecraft", "", full)
            if kind == "tag":
                if ns == "abyssia":
                    if not tag_exists(full):
                        report["unresolved"].append({"recipe": rid, "tag": full})
                elif full not in report["unchecked_tags"]:
                    report["unchecked_tags"].append(full)
            elif ns == "minecraft":
                if name not in VANILLA:
                    report["unresolved"].append({"recipe": rid, "item": full, "why": "not in VANILLA allow-list"})
            elif ns == "abyssia":
                if name in java:
                    continue
                if name in models:
                    by_model.add(name)
                else:
                    report["unresolved"].append({"recipe": rid, "item": full})
            else:
                report["unresolved"].append({"recipe": rid, "item": full, "why": "unknown namespace"})
    report["resolved_by_model_only"] = sorted(by_model)

    if os.path.isfile(SPEC):
        with open(SPEC, encoding="utf-8") as f:
            spec = json.load(f)
        spec_ids = [i["id"] for i in spec["items"]]
        report["spec"] = {
            "items": len(spec_ids), "recipes": len(spec["recipes"]),
            "items_not_registered_in_java": [i for i in spec_ids if i not in java],
            "items_without_model": [i for i in spec_ids if i not in models],
            "recipes_not_generated": [r["id"] for r in spec["recipes"]
                                      if not os.path.isfile(os.path.join(RECIPES, r["id"] + ".json"))],
        }
    report["ok"] = not (report["unresolved"] or report["invalid_json"] or report["bad_pattern"]
                        or any(v for k, v in report["spec"].items() if isinstance(v, list)))
    return report


if __name__ == "__main__":
    result = check()
    if "--verbose" not in sys.argv:
        result["resolved_by_model_only"] = len(result["resolved_by_model_only"])
    json.dump(result, sys.stdout, indent=2, ensure_ascii=False)
    print()
    sys.exit(0 if result["ok"] else 1)
