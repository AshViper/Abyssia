"""ORE01 abyssal excavator assets (stdlib only, idempotent): a 3 x 3 x 3 block multiblock built like the BT01d generators.

Writes into src/main/resources/assets/abyssia, for both tiers (Mk1 = abyssal_excavator, textures abyssia:block/exc_*,
Mk2 = abyssal_excavator_mk2, textures abyssia:block/exc2_*, the same boxes):
  blockstates/{excavator_part,abyssal_excavator,abyssal_excavator_mk2}.json -> invisible model
  models/block/excavator/invisible.json
  models/block/excavator/<tier>_0_0_0_<layer>.json   (chunk model of the whole machine, drawn by ExcavatorRenderer)
  models/block/excavator/<tier>_drill_<layer>.json   (auger drill, spun about the vertical axis by the renderer)

Same conventions as tools/bt01/generator_assets.py (whose writer is reused): geometry is authored as boxes in structure
pixels (1 block = 16), local frame x = right, y = up, z = forward (z = 48 is the FRONT with the lamps, z = 0 the back
with the vents); x is mirrored on output so the renderer only rotates. Layers: c = cutout, t = translucent, e = emissive.
Solid (collision) cells must match industry/ExcavatorStructure.solid(): the four corner columns and the centre column.
The drill is authored directly in drill space: axis through (8, y, 8); the renderer puts that axis on the structure
centre (24, 24) and spins it.
Textures (abyssia:block/exc_<name>, exc2_<name>) are drawn by ChatGPT (Mk2 derived by tools/excavator_textures.py);
this script does not create PNGs.

Run: python tools/bt01/excavator_assets.py
"""
import os
import sys as _sys

_sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generator_assets as ga

MODEL_DIR = os.path.join(ga.ASSETS, "models", "block", "excavator")
STATE_DIR = ga.STATE_DIR
TIERS = {"abyssal_excavator": "exc", "abyssal_excavator_mk2": "exc2"}
NAMES = ("plate", "plate_dark", "hazard", "drill", "pipe", "glow", "frame", "vent")
WIDTH = HEIGHT = DEPTH = 3
box = ga.box


def mx(x0, x1):
    """mirror an x range about the centre line"""
    return 48 - x1, 48 - x0


def design():
    b = []
    # --- four slim splayed legs ending in wide foot plates (x and z mirrored)
    for xa, xb in ((0, 14), (34, 48)):
        for za, zb in ((0, 14), (34, 48)):
            b.append(box(xa, 0, za, xb, 3, zb, "frame"))                                    # foot plate 14 x 3
    for left in (True, False):
        for front in (False, True):
            lo, hi = (4, 10) if left else mx(4, 10)
            up0, up1 = (6, 12) if left else mx(6, 12)
            zlo, zhi = (4, 10) if not front else mx(4, 10)
            zu0, zu1 = (6, 12) if not front else mx(6, 12)
            b.append(box(lo, 3, zlo, hi, 9, zhi, "frame"))                                  # lower leg, splayed out
            b.append(box(up0, 9, zu0, up1, 17, zu1, "plate_dark"))                          # upper leg into the body
    # --- machine body 30 x 30 (x/z 9..39), y 16..38
    b += [box(9, 16, 9, 39, 38, 39, "plate")]
    b += [box(8, 34, 8, 40, 38, 40, "hazard")]                                              # hazard band
    b += [box(11, 17, 8, 37, 33, 9, "plate_dark"), box(15, 20, 7, 33, 30, 8, "vent")]       # back panel + vent grille
    b += [box(12, 20, 39, 22, 28, 40, "glow", "e"), box(26, 20, 39, 36, 28, 40, "glow", "e")]  # front windows
    # --- copper pipes up both sides to the cabin
    for x0, x1 in ((5, 9), (39, 43)):
        b += [box(x0, 22, 14, x1, 26, 34, "pipe"), box(x0, 22, 28, x1, 42, 32, "pipe")]
    b += [box(5, 38, 28, 17, 42, 32, "pipe"), box(31, 38, 28, 43, 42, 32, "pipe")]
    # --- roof cabin 14 x 14 x 8 with window + lamp
    b += [box(17, 38, 17, 31, 46, 31, "plate_dark")]
    b += [box(19, 41, 31, 29, 44, 32, "glow", "e"), box(21, 46, 21, 27, 48, 27, "glow", "e")]
    # --- lamp dots on the top edges
    for x in (10, 22, 35):
        for z in (10, 35):
            b.append(box(x, 38, z, x + 3, 39, z + 3, "glow", "e"))
    # --- fixed collar ring where the drill leaves the body
    b += [box(15, 13, 15, 33, 16, 33, "plate_dark")]
    return b


def drill():
    """auger in drill space (axis x = z = 8): tapering twisted cone from y 13 down to the tip at y 1, shaft up into the body"""
    b = [box(6, 13, 6, 10, 20, 10, "drill")]                       # shaft through the collar
    # model element rotations may only be -45 / -22.5 / 0 / 22.5 / 45 (Minecraft rejects the whole model otherwise)
    segs = ((11, 13, 7.0, 0.0), (9, 11, 6.0, 22.5), (7, 9, 5.0, 45.0), (5, 7, 4.0, 22.5),
            (3, 5, 2.5, 45.0), (1, 3, 1.0, 22.5))
    for y0, y1, half, angle in segs:
        rot = ("y", angle, (8, y0, 8)) if angle else None
        b.append(box(8 - half, y0, 8 - half, 8 + half, y1, 8 + half, "drill", "c", rot))
    return b


def tex_map(prefix):
    return {n: f"abyssia:block/{prefix}_{n}" for n in NAMES}


def model_json(elements, textures, prefix):
    tm = tex_map(prefix)
    tex = {"particle": tm["frame"]}
    for k in sorted(textures):
        tex[k] = tm[k]
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": elements}


def main():
    changed = total = 0
    for block in ("excavator_part",) + tuple(TIERS):
        changed += ga.write(os.path.join(STATE_DIR, block + ".json"), {"variants": {"": {"model": "abyssia:block/excavator/invisible"}}})
        total += 1
    changed += ga.write(os.path.join(MODEL_DIR, "invisible.json"), {"textures": {"particle": tex_map("exc")["frame"]}})
    total += 1
    for tier, prefix in TIERS.items():
        # whole machine: a single 3 x 3 x 3 chunk
        layers = {layer: ([], set()) for layer in ga.LAYERS}
        for raw in design():
            bx = ga.mirror(raw, WIDTH * 16)
            for i, n in enumerate((WIDTH, HEIGHT, DEPTH)):
                assert 0 <= bx["from"][i] < bx["to"][i] <= n * 16, ("box outside the footprint", raw)
            for f, t, faces in ga.pieces(bx):
                if faces:
                    elements, textures = layers[bx["layer"]]
                    elements.append(ga.element(bx, f, t, faces, (16, 16, 16)))
                    textures.add(bx["tex"])
        # the drill, spun by the renderer
        drill_layers = {layer: ([], set()) for layer in ga.LAYERS}
        for bx in drill():
            for f, t, faces in ga.pieces(bx):
                if faces:
                    elements, textures = drill_layers[bx["layer"]]
                    elements.append(ga.element(bx, f, t, faces, (0, 0, 0)))
                    textures.add(bx["tex"])
        for stem, group in ((f"{tier}_0_0_0_", layers), (f"{tier}_drill_", drill_layers)):
            for layer, (elements, textures) in group.items():
                changed += ga.write(os.path.join(MODEL_DIR, stem + layer + ".json"), model_json(elements, textures, prefix))
                total += 1
    print(f"excavator_assets: {total} files, {changed} changed")
    print("textures needed (16x16):", ", ".join(f"{p}_{n}" for p in sorted(set(TIERS.values())) for n in NAMES))


if __name__ == "__main__":
    main()
