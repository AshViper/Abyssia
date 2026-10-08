"""Material processing system (docs/material-system.md): turns tools/material_spec.json into generator data.

The spec is the single source of truth for the new items, their names and recipes.  gen_deep_assets.py imports this
module for item names (lang), item models, recipes and tag entries; nothing here writes files or textures.

    python tools/material_system.py            # JSON summary: {"items": n, "recipes": n, "tools": [...], ...}

Java registration (ModItems / MaterialTools) is hand-written; tools/check_recipes.py verifies it matches the spec.
Machines (spec "machines_phase2") are phase2 and deliberately not generated.
"""
from __future__ import annotations

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SPEC_PATH = os.path.join(HERE, "material_spec.json")
NS = "abyssia"

# abyssia:underwater_tools additions (spec section 5: drill, cutter, crystal pickaxe and the cobalt tools).
UNDERWATER = ["cobalt_pickaxe", "cobalt_shovel", "crystal_pickaxe", "abyssal_drill", "abyssal_cutter"]
# Item tag for the future hadal-pressure exemption (spec armor.pressure_diver_helmet.special).
PRESSURE_PROOF = ["pressure_diver_helmet", "pressure_dive_tank", "pressure_suit_leggings", "pressure_flippers"]
# Rendered like a held tool (the other tools/armor use item/generated).
_HANDHELD_KINDS = {"pickaxe", "axe", "shovel", "hoe", "sword", "hammer"}


def load(path: str = SPEC_PATH) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


SPEC = load()


def ref(name: str) -> str:
    """Spec id -> namespaced id (vanilla ids already carry ``minecraft:``; ``#`` marks a tag)."""
    tag = name.startswith("#")
    name = name.lstrip("#")
    full = name if ":" in name else f"{NS}:{name}"
    return ("#" if tag else "") + full


def ingredient(name: str) -> dict:
    full = ref(name)
    return {"tag": full[1:]} if full.startswith("#") else {"item": full}


def item_ids() -> list[str]:
    return [i["id"] for i in SPEC["items"]]


def item_names() -> dict[str, tuple[str, str]]:
    """ITEM_NAMES entries: id -> (English, Japanese)."""
    return {i["id"]: (i["en"], i["ja"]) for i in SPEC["items"]}


def item_models() -> dict[str, dict]:
    """Item model per new item.  Textures are referenced only (abyssia:item/<id>); PNGs are made elsewhere."""
    kinds = {t["id"]: t["kind"] for t in SPEC["tools"]}
    return {i: {"parent": "minecraft:item/handheld" if kinds.get(i) in _HANDHELD_KINDS else "minecraft:item/generated",
                "textures": {"layer0": f"{NS}:item/{i}"}} for i in item_ids()}


def _category(result: str) -> str:
    kinds = {t["id"] for t in SPEC["tools"]} | {a["id"] for a in SPEC["armor"]}
    return "equipment" if result in kinds else "misc"


def _result(r: dict) -> dict:
    out = {"item": ref(r["result"])}
    if r.get("count", 1) != 1:
        out["count"] = r["count"]
    return out


def recipe_json(r: dict) -> dict:
    """One spec recipe row -> Minecraft 1.20.1 (Forge 47) recipe JSON."""
    t = r["type"]
    if t == "crafting_shaped":
        return {"type": "minecraft:crafting_shaped", "category": _category(r["result"]), "pattern": r["pattern"],
                "key": {k: ingredient(v) for k, v in r["key"].items()}, "result": _result(r)}
    if t == "crafting_shapeless":
        # damage_item (the crushing hammer) is an ordinary ingredient; CrushingHammerItem returns itself damaged by 1
        # as the crafting remainder, so it is not consumed.
        return {"type": "minecraft:crafting_shapeless", "category": _category(r["result"]),
                "ingredients": [ingredient(v) for v in r["ingredients"]], "result": _result(r)}
    if t in ("smelting", "blasting"):
        # Forge accepts an object result (with count); a plain id is the vanilla form.
        result = ref(r["result"]) if r.get("count", 1) == 1 else _result(r)
        return {"type": "minecraft:" + t, "category": "misc", "ingredient": ingredient(r["ingredient"]),
                "result": result, "experience": r.get("xp", 0.1), "cookingtime": r["time"]}
    if t == "stonecutting":
        return {"type": "minecraft:stonecutting", "ingredient": ingredient(r["ingredient"]),
                "result": ref(r["result"]), "count": r.get("count", 1)}
    if t == "smithing_transform":
        return {"type": "minecraft:smithing_transform", "template": ingredient(r["template"]),
                "base": ingredient(r["base"]), "addition": ingredient(r["addition"]), "result": {"item": ref(r["result"])}}
    raise ValueError(f"recipe {r['id']}: unsupported type {t!r}")


# Recipes made in these machines only: no crafting-table JSON (industry/recipe/MachineRecipes holds the table).
MACHINE_ONLY_STATIONS = {"alloy_furnace", "high_temp_furnace"}


def recipes() -> dict[str, dict]:
    out = {}
    for r in SPEC["recipes"]:
        if r.get("station") in MACHINE_ONLY_STATIONS:
            continue
        if r["id"] in out:
            raise ValueError(f"duplicate recipe id {r['id']}")
        out[r["id"]] = recipe_json(r)
    return out


def summary() -> dict:
    return {"spec": os.path.relpath(SPEC_PATH, os.path.join(HERE, "..")), "items": len(SPEC["items"]),
            "recipes": len(SPEC["recipes"]), "tools": [t["id"] for t in SPEC["tools"]],
            "armor": [a["id"] for a in SPEC["armor"]], "underwater_tools": UNDERWATER,
            "machines_phase2_not_generated": [m["id"] for m in SPEC.get("machines_phase2", [])]}


if __name__ == "__main__":
    json.dump(summary(), sys.stdout, indent=2, ensure_ascii=False)
    print()
