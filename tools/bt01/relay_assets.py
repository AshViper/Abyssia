#!/usr/bin/env python3
"""WR01 wireless power relay assets (stdlib only, idempotent): blockstates + block models of
abyssia:wireless_power_relay (lower body) and abyssia:wireless_power_relay_top (antenna). No block items, no item
models, no loot tables (constructor-only habitat blocks).

Design sheet inbox/designs/WR01.png: 2-block-tall relay. Lower = dark blue-grey body on a plinth, copper corner posts
and a copper band, top plate with a small iron cube, tall cyan glowing panel on the front. Upper = copper foot, thin
mast, cross antenna with iron caps on the arm ends, cyan glowing core and a thin tip. Models face north (panel on the
north side); the blockstate rotates them by FACING. LINKED=false swaps the glowing parts to the dim core texture.

Textures (drawn by ChatGPT, imported by Main; NOT created here):
  block/wireless_relay_body, wireless_relay_body_front, wireless_relay_body_top, wireless_relay_copper,
  wireless_relay_antenna, wireless_relay_core (emissive), wireless_relay_core_off,
  item/habitat_icon_wireless_power_relay (build-menu icon, referenced by the build entry id).

    python tools/bt01/relay_assets.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssia")
NS = "abyssia"
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
DIRS = ("north", "east", "south", "west", "up", "down")
GLOW = {"block_light": 15, "sky_light": 15}


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


def box(frm, to, tex, faces=DIRS, glow=False, per_face=None):
    """element with every listed face on #tex (uv auto from position); per_face overrides single faces"""
    el = {"from": frm, "to": to, "faces": {d: {"texture": f"#{(per_face or {}).get(d, tex)}"} for d in faces}}
    if glow:
        el["forge_data"] = dict(GLOW)
    return el


def lower(linked):
    els = [
        box([1, 0, 1], [15, 2, 15], "body", per_face={"up": "top"}),                       # plinth
        box([2, 2, 2], [14, 13, 14], "body", per_face={"north": "front", "up": "top"}),     # body
        box([2, 10, 1.75], [14, 12, 14.25], "copper", ("north", "south")),                 # copper band (front/back)
        box([1.75, 10, 2], [14.25, 12, 14], "copper", ("east", "west")),                   # copper band (sides)
        box([4, 13, 4], [12, 16, 12], "body", per_face={"up": "top"}),                     # top plate
        box([7, 16, 4.5], [9, 17.5, 6.5], "iron"),                                         # small iron cube on the plate (front)
        box([6, 4, 1.5], [10, 10, 2], "core", ("north", "east", "west", "up", "down"), glow=True),  # glowing panel
    ]
    for x0, z0 in ((1.5, 1.5), (11.5, 1.5), (1.5, 11.5), (11.5, 11.5)):                 # copper corner posts
        els.append(box([x0, 2, z0], [x0 + 3, 12, z0 + 3], "copper"))
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": rl("wireless_relay_body"), "body": rl("wireless_relay_body"),
                         "front": rl("wireless_relay_body_front"), "top": rl("wireless_relay_body_top"),
                         "copper": rl("wireless_relay_copper"), "iron": rl("wireless_relay_antenna"),
                         "core": rl("wireless_relay_core" if linked else "wireless_relay_core_off")},
            "elements": els}


def upper(linked):
    els = [
        box([6, 0, 6], [10, 2, 10], "copper"),                                   # copper foot
        box([7, 2, 7], [9, 10, 9], "antenna", ("north", "east", "south", "west")),  # mast
        box([1, 10, 7], [15, 12, 9], "antenna"),                                 # cross arm (x)
        box([7, 10, 1], [9, 12, 15], "antenna"),                                 # cross arm (z)
        box([0.5, 9.5, 6.5], [3.5, 12.5, 9.5], "antenna"),                       # iron caps on the arm ends
        box([12.5, 9.5, 6.5], [15.5, 12.5, 9.5], "antenna"),
        box([6.5, 9.5, 0.5], [9.5, 12.5, 3.5], "antenna"),
        box([6.5, 9.5, 12.5], [9.5, 12.5, 15.5], "antenna"),
        box([6, 9, 6], [10, 14, 10], "core", glow=True),                         # glowing core
        box([7, 14, 7], [9, 16, 9], "antenna"),                                  # tip
    ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": rl("wireless_relay_antenna"), "antenna": rl("wireless_relay_antenna"),
                         "copper": rl("wireless_relay_copper"),
                         "core": rl("wireless_relay_core" if linked else "wireless_relay_core_off")},
            "elements": els}


def blockstate(name):
    variants = {}
    for linked in (True, False):
        model = rl(name if linked else name + "_off")
        for facing, y in FACINGS.items():
            v = {"model": model}
            if y:
                v["y"] = y
            variants[f"facing={facing},linked={'true' if linked else 'false'}"] = v
    return {"variants": variants}


def main():
    changed = 0
    changed += write("models/block/wireless_power_relay.json", lower(True))
    changed += write("models/block/wireless_power_relay_off.json", lower(False))
    changed += write("models/block/wireless_power_relay_top.json", upper(True))
    changed += write("models/block/wireless_power_relay_top_off.json", upper(False))
    changed += write("blockstates/wireless_power_relay.json", blockstate("wireless_power_relay"))
    changed += write("blockstates/wireless_power_relay_top.json", blockstate("wireless_power_relay_top"))
    print(f"relay_assets: {changed} file(s) written")


if __name__ == "__main__":
    main()
