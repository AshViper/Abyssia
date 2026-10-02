"""Electric tools (feature I03, spec inbox/specs/H04-base-equipment.md): electric_abyssal_drill / electric_abyssal_cutter.

Item models (flat icons; the PNGs textures/item/electric_abyssal_*.png are imported from the ChatGPT sheet, never
drawn here), recipes and LANG.  gen_deep_assets.main() calls generate() and merges LANG; the Java side is
com.abyssia.item.electric.  Run on its own (`python tools/electric_tool_assets.py`) to rewrite only these files.
"""
import json
import os

MOD = "abyssia"
TOOLS = ("electric_abyssal_drill", "electric_abyssal_cutter")

# every key (item names, tooltip / message strings of ElectricTools): id -> (English, Japanese)
LANG = {
    f"item.{MOD}.electric_abyssal_drill": ("Electric Abyssal Drill", "電動アビサルドリル"),
    f"item.{MOD}.electric_abyssal_cutter": ("Electric Abyssal Cutter", "電動アビサルカッター"),
    f"tooltip.{MOD}.electric_tool.energy": ("Energy %s%% (%s / %s FE)", "エネルギー %s%% (%s / %s FE)"),
    f"tooltip.{MOD}.electric_tool.charge": ("Charge with FE (no durability)", "FEで充電 (耐久なし)"),
    f"message.{MOD}.electric_tool.empty": ("Out of energy", "エネルギー切れ"),
    # I04 propulsion screw
    f"item.{MOD}.propulsion_screw": ("Propulsion Screw", "推進スクリュー"),
    f"tooltip.{MOD}.propulsion_screw.use": ("Hold right-click under water to thrust forward", "水中で右クリック長押しで推進"),
}

_RECIPES = {
    # T tungsten_tip, C conductive_component, A abyssal_alloy_ingot, M machine_frame, P iron_plate, H thermal_component
    "electric_abyssal_drill": (["TCT", "AMA", "PHP"],
                               {"T": "tungsten_tip", "C": "conductive_component", "A": "abyssal_alloy_ingot",
                                "M": "machine_frame", "P": "iron_plate", "H": "thermal_component"}),
    # cutter: tungsten tips as the blade edge (the spec leaves the keys open)
    "electric_abyssal_cutter": (["ACA", "HMH", "PPP"],
                                {"A": "abyssal_alloy_ingot", "C": "conductive_component", "H": "thermal_component",
                                 "M": "machine_frame", "P": "iron_plate"}),
}

SCREW = "propulsion_screw"
# K conductive_alloy_ingot, T tungsten_tip, A abyssal_alloy_ingot, M machine_frame, C conductive_component
_SCREW_RECIPE = (["KTK", "AMA", "CTC"], {"K": "conductive_alloy_ingot", "T": "tungsten_tip", "A": "abyssal_alloy_ingot",
                                         "M": "machine_frame", "C": "conductive_component"})


def screw_model():
    """I04: 3D item model from tools/electric_models/propulsion_screw_parts.json (UVs follow the element position;
    emissive parts are full-bright), like habitat_assets.constructor_model()."""
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "electric_models", SCREW + "_parts.json"),
              encoding="utf-8") as f:
        spec = json.load(f)
    textures = {"particle": f"{MOD}:item/{SCREW}_body"}
    elements = []
    for part in spec["parts"]:
        textures[part["tex"]] = f"{MOD}:item/{SCREW}_{part['tex']}"
        element = {"name": part["name"], "from": part["from"], "to": part["to"],
                   "faces": {d: {"texture": "#" + part["tex"]} for d in ("north", "east", "south", "west", "up", "down")}}
        if part.get("emissive"):
            element["forge_data"] = {"block_light": 15, "sky_light": 15}
        elements.append(element)
    return {"textures": textures, "elements": elements, "display": spec["display"]}


def generate(write, im, data_dir):
    for name in TOOLS:
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})
        pattern, key = _RECIPES[name]
        write(os.path.join(data_dir, MOD, "recipes", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
            "key": {k: {"item": f"{MOD}:{v}"} for k, v in key.items()},
            "result": {"item": f"{MOD}:{name}", "count": 1}})
    write(im(SCREW), screw_model())
    pattern, key = _SCREW_RECIPE
    write(os.path.join(data_dir, MOD, "recipes", SCREW + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
        "key": {k: {"item": f"{MOD}:{v}"} for k, v in key.items()},
        "result": {"item": f"{MOD}:{SCREW}", "count": 1}})
    return {"items": len(TOOLS) + 1}


if __name__ == "__main__":
    import gen_deep_assets as g
    generate(g.write, lambda n: os.path.join(g.ASSETS, "models", "item", n + ".json"), g.DATA)
    print("electric tools written")
