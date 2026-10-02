"""Builds the I01 industrial block models from the ChatGPT part definitions.

    python tools/industrial_models/build_models.py

parts.json (copied from inbox/designs/I01-models.json, the ChatGPT design) lists every model as
{"model", "parts": [{"name", "from", "to", "faces": {dir: texture}}]}.  Each model is written as a Java block model
<model>.json next to this script; tools/industrial_assets.py copies them into assets/abyssia/models/block/.
UVs are left to Minecraft (auto from the element position, same as Blockbench's auto UV).  Machine face textures map to
the variables front / side / top so the generated *_on and energy_device_c* variants can swap #front.  Faces on the
block boundary get a cullface.  Open the copies under src/main/resources/assets/abyssia/models/block in Blockbench to
inspect them (textures resolve there); edit parts.json and rerun rather than hand-editing the output.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
MACHINES = ("crusher", "refinery_furnace", "alloy_furnace", "high_temp_furnace", "hydrothermal_generator",
            "auxiliary_generator", "energy_device")
CUTOUT = ("industrial_pipe", "energy_cable", "reinforced_energy_cable", "industrial_valve", "work_light", "warning_light")
# Picture-like textures (valve wheel, lamp faces) are shown whole instead of cropped to the element's position.
# A face may also be {"texture": name, "uv": [u0, v0, u1, v1]} in parts.json.
FULL_UV = ("industrial_valve", "work_light", "warning_light")
BOUNDARY = {"north": (2, 0, "from"), "south": (2, 16, "to"), "west": (0, 0, "from"), "east": (0, 16, "to"),
            "down": (1, 0, "from"), "up": (1, 16, "to")}


def big_face(model, texture, side, part):
    """A machine's own face texture on a large face pointing its natural way (front north, top up, side elsewhere)
    is shown whole, so a protruding front housing shows the full turbine / rotor instead of a cropped middle."""
    var = variable(model, texture)
    if var == texture or (var == "front") != (side == "north") or (var == "top") != (side == "up"):
        return False
    axes = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}[side]
    return all(part["to"][a] - part["from"][a] >= 10 for a in axes)


def variable(model, texture):
    for m in MACHINES:
        if texture.startswith(m + "_"):
            return texture[len(m) + 1:]
    return texture


def build(entry):
    name = entry["model"]
    textures, elements = {}, []
    for part in entry["parts"]:
        if name.endswith("_arm") and part["name"] == "core":
            continue  # the multipart blockstate always adds the *_core model
        faces = {}
        for side, tex in part["faces"].items():
            uv = None
            if isinstance(tex, dict):
                tex, uv = tex["texture"], tex.get("uv")
            elif tex in FULL_UV or big_face(name, tex, side, part):
                uv = [0, 0, 16, 16]
            var = variable(name, tex)
            textures[var] = "abyssia:block/" + tex
            face = {"texture": "#" + var}
            if uv:
                face["uv"] = uv
            axis, edge, end = BOUNDARY[side]
            if part[end][axis] == edge:
                face["cullface"] = side
            faces[side] = face
        elements.append({"name": part["name"], "from": part["from"], "to": part["to"], "faces": faces})
    textures["particle"] = textures.get("side") or next(iter(textures.values()))
    model = {"credit": "Abyssia I01 - design: ChatGPT, built by tools/industrial_models/build_models.py",
             "textures": textures, "elements": elements}
    if any(name.startswith(c) for c in CUTOUT):
        model["render_type"] = "minecraft:cutout"
    return name, model


def main():
    with open(os.path.join(HERE, "parts.json"), encoding="utf-8") as f:
        entries = json.load(f)
    for entry in entries:
        name, model = build(entry)
        with open(os.path.join(HERE, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(model, f, indent=2)
            f.write("\n")
    print(f"{len(entries)} models")


if __name__ == "__main__":
    main()
