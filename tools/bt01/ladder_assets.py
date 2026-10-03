"""BT01c: blockstate + element model of abyssia:habitat_ladder (stdlib only, idempotent).

Design sheet inbox/designs/BT01.png "Habitat Ladder": two dark metal side rails, grey rungs, bronze clamps,
cyan light strips. Model faces north (FACING=north = ladder against the wall to the south, z 12..16);
the blockstate rotates it. No BlockItem / item model / loot table (constructor-only habitat block).

Textures (drawn later, not created here): abyssia:block/habitat_ladder_rail, _rung, _clamp, _light.

    python tools/bt01/ladder_assets.py
"""
import json
import os
import sys as _sys
_sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import mc_format  # NeoForge 1.21.1: 1.21 data folders / formats, neoforge_data in models

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssia")
NAME = "habitat_ladder"


def face_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    if face in ("north", "south"):
        return [x1, 16 - y2, x2, 16 - y1]
    if face in ("east", "west"):
        return [z1, 16 - y2, z2, 16 - y1]
    return [x1, z1, x2, z2]


def element(f, t, tex, cull=None, glow=False, faces=("north", "east", "south", "west", "up", "down")):
    out = {"from": f, "to": t, "faces": {}}
    for face in faces:
        entry = {"texture": tex, "uv": face_uv(face, f, t)}
        if cull and face in cull:
            entry["cullface"] = face
        out["faces"][face] = entry
    if glow:
        out["forge_data"] = {"block_light": 15, "sky_light": 15}
    return out


def model():
    els = []
    # side rails (3 wide, 4 deep, against the wall at z 16)
    for x in (1, 12):
        els.append(element([x, 0, 12], [x + 3, 16, 16], "#rail", cull=("up", "down", "south")))
    # rungs between the rails, 4 px apart
    for y in (1.5, 5.5, 9.5, 13.5):
        els.append(element([4, y, 13], [12, y + 1.5, 15], "#rung", faces=("north", "south", "up", "down")))
    # bronze clamps around the rails (left low, right high, like the sheet)
    for x, y in ((0.5, 3), (11.5, 11)):
        els.append(element([x, y, 11.5], [x + 4, y + 2, 16], "#clamp", faces=("north", "east", "west", "up", "down")))
    # cyan light strips on the rail fronts
    for x, y in ((2, 8), (13, 2)):
        els.append(element([x, y, 11.75], [x + 1, y + 5, 12], "#light", glow=True, faces=("north",)))
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": {
            "particle": "abyssia:block/habitat_ladder_rail",
            "rail": "abyssia:block/habitat_ladder_rail",
            "rung": "abyssia:block/habitat_ladder_rung",
            "clamp": "abyssia:block/habitat_ladder_clamp",
            "light": "abyssia:block/habitat_ladder_light",
        },
        "elements": els,
    }


def blockstate():
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in rot.items():
        v = {"model": "abyssia:block/" + NAME}
        if y:
            v["y"] = y
        variants["facing=" + facing] = v
    return {"variants": variants}


def write(path, data):
    path = mc_format.upgrade(path)
    data = mc_format.upgrade_json(path, data)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(data, indent=2) + "\n"
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            if fh.read() == text:
                return False
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    return True


def main():
    files = {
        os.path.join(ASSETS, "blockstates", NAME + ".json"): blockstate(),
        os.path.join(ASSETS, "models", "block", NAME + ".json"): model(),
    }
    for path, data in files.items():
        print(("wrote " if write(path, data) else "same  ") + os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()
