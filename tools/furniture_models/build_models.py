"""Builds the H04 / H05 furniture block models (large locker, wall workbench) from parts.json.

parts.json lists templates {"model", ["parent"], ["full_uv": [variables]], "parts": [{"name", "from", "to",
"faces": {dir: variable | {"texture": variable, "uv": [u0, v0, u1, v1]}}}]}.  Faces name texture *variables*;
tools/furniture_assets.py binds them to textures per blockstate variant (locker part / open, workbench powered) and
writes the models.  UVs are left to Minecraft (auto from the element position) except for the full_uv variables,
which are shown whole.  Faces on the block boundary get a cullface.  Edit parts.json and rerun
gen_deep_assets.py (or furniture_assets.generate) rather than hand-editing the output.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
BOUNDARY = {"north": (2, 0, "from"), "south": (2, 16, "to"), "west": (0, 0, "from"), "east": (0, 16, "to"),
            "down": (1, 0, "from"), "up": (1, 16, "to")}


def templates():
    with open(os.path.join(HERE, "parts.json"), encoding="utf-8") as f:
        return {entry["model"]: entry for entry in json.load(f)}


def build(entry, textures, particle=None, credit="Abyssia H04/H05 - built by tools/furniture_models/build_models.py"):
    """entry: a parts.json template; textures: variable -> texture id (e.g. "abyssia:block/large_locker_top")."""
    full = set(entry.get("full_uv", ()))
    used, elements = {}, []
    for part in entry["parts"]:
        faces = {}
        for side, var in part["faces"].items():
            uv = None
            if isinstance(var, dict):
                var, uv = var["texture"], var.get("uv")
            elif var in full:
                uv = [0, 0, 16, 16]
            if var not in textures:
                raise KeyError(f"{entry['model']}: no texture bound to #{var}")
            used[var] = textures[var]
            face = {"texture": "#" + var}
            if uv:
                face["uv"] = uv
            axis, edge, end = BOUNDARY[side]
            if part[end][axis] == edge:
                face["cullface"] = side
            faces[side] = face
        elements.append({"name": part["name"], "from": part["from"], "to": part["to"], "faces": faces})
    used["particle"] = textures.get(particle) if particle else next(iter(used.values()))
    model = {"credit": credit}
    if "parent" in entry:
        model["parent"] = entry["parent"]
    model["textures"] = used
    model["elements"] = elements
    return model
