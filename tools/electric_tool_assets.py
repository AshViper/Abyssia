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
    # ECO02: neodymium magnets in the motor (N)
    "electric_abyssal_drill": (["TCT", "AMA", "NHN"],
                               {"T": "tungsten_tip", "C": "conductive_component", "A": "abyssal_alloy_ingot",
                                "M": "machine_frame", "N": "neodymium_ingot", "H": "thermal_component"}),
    # cutter: tungsten tips as the blade edge (the spec leaves the keys open)
    "electric_abyssal_cutter": (["ACA", "HMH", "PNP"],
                                {"A": "abyssal_alloy_ingot", "C": "conductive_component", "H": "thermal_component",
                                 "M": "machine_frame", "P": "iron_plate", "N": "neodymium_ingot"}),
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


SCANNER = "lidar_scanner"
# WRK01: early-game recipe (the scanner unlocks building, so it cannot need an excavator / alloy furnace):
# R redstone, G glass, P iron_plate, F machine_frame, L lumen_cell
_SCANNER_RECIPE = (["RGR", "PFP", " L "], {"R": "minecraft:redstone", "G": "minecraft:glass", "P": "iron_plate",
                                          "F": "machine_frame", "L": "lumen_cell"})


def scanner_model():
    """LS01: drawn by LidarScannerClient (baked mesh via tools/vehicle_model.py); this json only carries the display transforms."""
    def d(rot, tr, sc):
        return {"rotation": rot, "translation": tr, "scale": [sc, sc, sc]}
    return {"parent": "minecraft:builtin/entity", "display": {
        "gui": d([30, 225, 0], [0, -1, 0], 0.95),
        "ground": d([0, 0, 0], [0, 2, 0], 0.5),
        "fixed": d([0, 0, 0], [0, 0, 0], 0.8),
        "thirdperson_righthand": d([90, 0, 0], [0, 3, -1], 0.6),
        "thirdperson_lefthand": d([90, 0, 0], [0, 3, -1], 0.6),
        "firstperson_righthand": d([-15, -25, 0], [-4, 5, 0], 0.55),
        "firstperson_lefthand": d([-15, 25, 0], [4, 5, 0], 0.55)}}


def generate(write, im, data_dir):
    for name in TOOLS:
        write(im(name), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})
        pattern, key = _RECIPES[name]
        write(os.path.join(data_dir, MOD, "recipes", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
            "key": {k: {"item": f"{MOD}:{v}"} for k, v in key.items()},
            "result": {"item": f"{MOD}:{name}", "count": 1}})
    write(im(SCREW), screw_model())
    # The cutter shears plants: plant blocks drop themselves for #forge:shears (and Silk Touch), else only materials.
    write(os.path.join(data_dir, "forge", "tags", "items", "shears.json"),
          {"replace": False, "values": [f"{MOD}:electric_abyssal_cutter"]})
    pattern, key = _SCREW_RECIPE
    write(os.path.join(data_dir, MOD, "recipes", SCREW + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
        "key": {k: {"item": f"{MOD}:{v}"} for k, v in key.items()},
        "result": {"item": f"{MOD}:{SCREW}", "count": 1}})
    write(im(SCANNER), scanner_model())
    pattern, key = _SCANNER_RECIPE
    write(os.path.join(data_dir, MOD, "recipes", SCANNER + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
        "key": {k: {"item": v if ":" in v else f"{MOD}:{v}"} for k, v in key.items()},
        "result": {"item": f"{MOD}:{SCANNER}", "count": 1}})
    return {"items": len(TOOLS) + 2}


if __name__ == "__main__":
    import gen_deep_assets as g
    generate(g.write, lambda n: os.path.join(g.ASSETS, "models", "item", n + ".json"), g.DATA)
    print("electric tools written")
