#!/usr/bin/env python3
"""BT01e assets (stdlib only, idempotent): blockstates + block models of abyssia:habitat_membrane and
abyssia:charging_station. No block items, no item models, no loot tables (constructor-only habitat blocks).

Design sheet inbox/designs/BT01.png: "Open Doorway" (shimmering blue membrane in the door opening) and
"Charging Station" (slim dark pillar on a base plate, cyan bolt screen on the front, cap with cyan lights, a socket
on the right with a cable hanging down to a plug). The station model faces north (screen on the north side);
the blockstate rotates it by FACING.

Textures (drawn later by ChatGPT, not created here):
  block/habitat_membrane (16x16, animated strip 16x(16*n) + .mcmeta allowed), block/charging_station,
  block/charging_station_screen, block/charging_station_cable,
  item/habitat_icon_open_entrance, item/habitat_icon_glass_wall, item/habitat_icon_wall_revert,
  item/habitat_icon_charging_station.

    python tools/bt01/custom_assets.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssia")
NS = "abyssia"
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
DIRS = ("north", "east", "south", "west", "up", "down")


def write(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(data, indent=2) + "\n"
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            if f.read() == text:
                return False
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    return True


def rl(name):
    return f"{NS}:block/{name}"


def box(frm, to, tex, faces=DIRS, rot=None):
    """element with every listed face on #tex (uv auto from position)"""
    el = {"from": frm, "to": to, "faces": {d: {"texture": f"#{tex}"} for d in faces}}
    if rot:
        el["rotation"] = rot
    return el


def membrane():
    # full translucent cube; same-block faces are skipped by HabitatMembraneBlock.skipRendering
    return {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
            "textures": {"all": rl("habitat_membrane"), "particle": rl("habitat_membrane")}}


def station():
    body, screen, cable = "body", "screen", "cable"
    els = [
        box([2, 0, 2], [14, 2, 14], body),                   # base plate
        box([4, 2, 5], [12, 14, 11], body),                  # pillar
        box([3.5, 14, 4.5], [12.5, 16, 11.5], body),         # cap (cyan lights on its texture)
        box([5, 4, 4], [11, 13, 5], screen, ("north", "east", "west", "up", "down")),  # bolt screen, 1 px proud
        box([12, 9, 6.5], [14, 12, 9.5], body),              # socket on the right (east)
        box([13.5, 4, 7.5], [14.5, 9, 8.5], cable),          # cable down
        box([13, 1, 7], [15, 4, 9], cable),                  # plug
    ]
    return {"parent": "minecraft:block/block",
            "textures": {"particle": rl("charging_station"), body: rl("charging_station"),
                         screen: rl("charging_station_screen"), cable: rl("charging_station_cable")},
            "elements": els}


def main():
    changed = 0
    changed += write("models/block/habitat_membrane.json", membrane())
    changed += write("blockstates/habitat_membrane.json", {"variants": {"": {"model": rl("habitat_membrane")}}})
    changed += write("models/block/charging_station.json", station())
    variants = {}
    for facing, y in FACINGS.items():
        v = {"model": rl("charging_station")}
        if y:
            v["y"] = y
        variants[f"facing={facing}"] = v
    changed += write("blockstates/charging_station.json", {"variants": variants})
    print(f"custom_assets: {changed} file(s) written")


if __name__ == "__main__":
    main()
