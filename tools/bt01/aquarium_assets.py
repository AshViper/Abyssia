#!/usr/bin/env python3
"""BT01g aquarium assets (stdlib only, idempotent): blockstates, block/item models, entity tag, canister recipe.

Writes into src/main/resources. No loot tables (aquarium blocks drop nothing) and no textures (drawn by ChatGPT):
  block/aquarium_frame, block/aquarium_frame_light, block/aquarium_glass, block/aquarium_sand, block/aquarium_coral,
  item/creature_capture_canister, item/creature_capture_canister_filled, item/habitat_icon_aquarium.
Run: python tools/bt01/aquarium_assets.py
"""
import json
import os
from pathlib import Path
import sys as _sys
_sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import mc_format  # NeoForge 1.21.1: 1.21 data folders / formats, neoforge_data in models

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / "abyssia"
DATA = RES / "data" / "abyssia"
NS = "abyssia"

FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
TIERS = ["bottom", "middle", "top"]
KINDS = ["corner", "side", "floor", "floor_coral", "water"]

TEX = {
    "frame": f"{NS}:block/aquarium_frame",
    "light": f"{NS}:block/aquarium_frame_light",
    "glass": f"{NS}:block/aquarium_glass",
    "sand": f"{NS}:block/aquarium_sand",
    "coral": f"{NS}:block/aquarium_coral",
}

# Capturable: Abyssia fish, eels, small sharks, jellies. Excluded: giant squid, giant phantom jelly, oarfish,
# sixgill / sleeper sharks (giants), walkers / sessile animals, players.
CAPTURABLE = [
    "abyssal_grenadier", "barreleye", "black_swallower", "hatchetfish", "lanternfish", "mariana_snailfish",
    "stoplight_loosejaw", "tripod_fish", "black_dragonfish", "fangtooth", "viperfish", "blobfish", "anglerfish",
    "vent_eelpout", "chimaera",
    "gulper_eel", "snipe_eel", "hagfish",
    "cookiecutter_shark", "frilled_shark", "goblin_shark",
    "silky_medusa", "atolla_jelly", "helmet_jelly", "deepstaria",
]


def write(path: Path, obj) -> None:
    path = Path(mc_format.upgrade(str(path)))
    obj = mc_format.upgrade_json(str(path), obj)
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(obj, indent=2, ensure_ascii=False) + "\n"
    if not path.exists() or path.read_text(encoding="utf-8") != text:
        path.write_text(text, encoding="utf-8", newline="\n")
        print("wrote", path.relative_to(ROOT))


def el(frm, to, tex, faces=None, shade=True, rotation=None):
    """element with every face (or the given ones) textured #tex (auto uv)."""
    faces = faces or ["north", "south", "east", "west", "up", "down"]
    e = {"from": frm, "to": to, "faces": {f: {"texture": "#" + tex} for f in faces}}
    if not shade:
        e["shade"] = False
    if rotation:
        e["rotation"] = rotation
    return e


def lit_el(frm, to, lit_faces):
    """frame element whose lit_faces show the cyan light strip."""
    e = el(frm, to, "frame")
    for f in lit_faces:
        e["faces"][f] = {"texture": "#light"}
    return e


def model(elements, textures, render_type, particle="frame"):
    t = {k: TEX[k] for k in textures}
    t["particle"] = TEX[particle]
    return {"parent": "minecraft:block/block", "render_type": render_type, "textures": t, "elements": elements}


def coral(y0=5, y1=13):
    rot = lambda a: {"origin": [8, 8, 8], "axis": "y", "angle": a}
    return [el([1, y0, 8], [15, y1, 8], "coral", ["north", "south"], False, rot(45)),
            el([8, y0, 1], [8, y1, 15], "coral", ["east", "west"], False, rot(45))]


def side(tier):
    """outer face = north (z 0). Glass pane z1..3, base rail + sand at the bottom, rim at the top."""
    lo, hi = (4, 16) if tier == "bottom" else (0, 13) if tier == "top" else (0, 16)
    els = [el([0, lo, 1], [16, hi, 3], "glass")]
    if tier == "bottom":
        els.append(lit_el([0, 0, 0], [16, 4, 4], ["north"]))
        els.append(el([0, 0, 4], [16, 5, 16], "sand"))
    if tier == "top":
        els.append(el([0, 13, 0], [16, 16, 4], "frame"))
    return model(els, ["frame", "light", "glass", "sand"], "minecraft:translucent")


def corner(tier):
    """outer corner = north-west. Column with light strips, panes along both outer edges."""
    lo, hi = (4, 16) if tier == "bottom" else (0, 13) if tier == "top" else (0, 16)
    els = [lit_el([0, 0, 0], [5, 16, 5], ["north", "west"]),
           el([5, lo, 1], [16, hi, 3], "glass"),
           el([1, lo, 5], [3, hi, 16], "glass")]
    if tier == "bottom":
        els += [lit_el([5, 0, 0], [16, 4, 4], ["north"]), lit_el([0, 0, 5], [4, 4, 16], ["west"]),
                el([4, 0, 4], [16, 5, 16], "sand")]
    if tier == "top":
        els += [el([5, 13, 0], [16, 16, 4], "frame"), el([0, 13, 5], [4, 16, 16], "frame"),
                lit_el([0.5, 15.5, 0.5], [4.5, 16, 4.5], ["up"])]
    return model(els, ["frame", "light", "glass", "sand"], "minecraft:translucent")


def floor(with_coral):
    els = [el([0, 0, 0], [16, 5, 16], "sand")]
    if with_coral:
        els.append(el([10, 5, 2], [14, 7, 6], "frame"))  # a rock
        els += coral()
    return model(els, ["sand", "frame", "coral"] if with_coral else ["sand", "frame"], "minecraft:cutout", "sand")


def controller():
    """floor centre: sand, a small air-stone / bubbler with a light ring, coral."""
    els = [el([0, 0, 0], [16, 5, 16], "sand"),
           lit_el([6, 5, 6], [10, 7, 10], ["north", "south", "east", "west"]),
           el([7, 7, 7], [9, 9, 9], "frame")]
    rot = lambda a: {"origin": [8, 8, 8], "axis": "y", "angle": a}
    els += [el([1, 5, 3], [6, 12, 3], "coral", ["north", "south"], False, rot(22.5)),
            el([11, 5, 13], [15, 11, 13], "coral", ["north", "south"], False, rot(-22.5))]
    return model(els, ["sand", "frame", "light", "coral"], "minecraft:cutout", "sand")


def main() -> None:
    models = ASSETS / "models" / "block"
    for t in TIERS:
        write(models / f"aquarium_side_{t}.json", side(t))
        write(models / f"aquarium_corner_{t}.json", corner(t))
    write(models / "aquarium_floor.json", floor(False))
    write(models / "aquarium_floor_coral.json", floor(True))
    write(models / "aquarium_water.json", {"textures": {"particle": TEX["glass"]}})
    write(models / "aquarium.json", controller())

    variants = {}
    for kind in KINDS:
        for tier in TIERS:
            for facing, y in FACINGS.items():
                if kind in ("corner", "side"):
                    v = {"model": f"{NS}:block/aquarium_{kind}_{tier}"}
                    if y:
                        v["y"] = y
                elif kind == "water":
                    v = {"model": f"{NS}:block/aquarium_water"}
                else:
                    v = {"model": f"{NS}:block/aquarium_{kind}"}
                    if y:
                        v["y"] = y
                variants[f"facing={facing},kind={kind},tier={tier}"] = v
    write(ASSETS / "blockstates" / "aquarium_part.json", {"variants": variants})
    write(ASSETS / "blockstates" / "aquarium.json", {"variants": {"": {"model": f"{NS}:block/aquarium"}}})

    for item in ("creature_capture_canister", "creature_capture_canister_filled"):
        write(ASSETS / "models" / "item" / f"{item}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})

    write(DATA / "tags" / "entity_types" / "aquarium_capturable.json",
          {"replace": False, "values": [f"{NS}:{e}" for e in CAPTURABLE]})
    write(DATA / "recipes" / "creature_capture_canister.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["IGI", "G G", "IGI"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "G": {"item": "minecraft:glass"}},
        "result": {"item": f"{NS}:creature_capture_canister"},
    })


if __name__ == "__main__":
    main()
