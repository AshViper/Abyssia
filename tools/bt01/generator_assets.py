"""BT01d multiblock generator assets (stdlib only, idempotent).

Writes into src/main/resources/assets/abyssia:
  blockstates/{generator_part,current_turbine,geothermal_generator,biofuel_generator}.json -> invisible model
  models/block/generator/invisible.json
  models/block/generator/<kind>_<cx>_<cy>_<cz>_<layer>.json   (chunk models, drawn by GeneratorRenderer)
  models/block/generator/current_turbine_{hub,blade}_<layer>.json (rotor, spun by the renderer)

Geometry is authored per generator as boxes in structure pixels (1 block = 16), local frame x = right,
y = up, z = forward (z = 0 is the row against the habitat wall). Must match GeneratorKind.solid() roughly
(solid cells = collision). The writer mirrors x (x' = width*16 - x, so the renderer only rotates), splits
every box at block boundaries (per-block UVs tile the 16x16 textures, internal faces dropped) and groups the
pieces into 3x3x3-block chunks shifted by -(chunk + 1) blocks so coordinates stay within -16..32.
Layers: c = cutout (lit), t = translucent, e = emissive (rendered full bright).
Textures (abyssia:block/<name>) are drawn by ChatGPT; this script does not create PNGs.

Run: python tools/bt01/generator_assets.py
"""
import json
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssia")
MODEL_DIR = os.path.join(ASSETS, "models", "block", "generator")
STATE_DIR = os.path.join(ASSETS, "blockstates")

TEX = {
    "frame": "abyssia:block/gen_frame",
    "copper": "abyssia:block/gen_copper",
    "glow": "abyssia:block/gen_glow",
    "blade": "abyssia:block/turbine_blade",
    "rock": "abyssia:block/geo_rock",
    "lava": "abyssia:block/geo_lava",
    "tank": "abyssia:block/bio_tank",
    "burner": "abyssia:block/bio_burner",
}
PARTICLE = TEX["frame"]
LAYERS = ("c", "t", "e")


def box(x0, y0, z0, x1, y1, z1, tex="frame", layer="c", rot=None):
    """rot = (axis, angle, (ox, oy, oz)) in the box's own coordinate space"""
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "tex": tex, "layer": layer, "rot": rot}


# ---------------------------------------------------------------- designs (inbox/designs/BT01.png)

def current_turbine():
    b = []
    # wall mount: two posts, two crossbeams, copper brackets, glow caps
    b += [box(1, 0, 1, 15, 80, 13), box(65, 0, 1, 79, 80, 13)]
    b += [box(0, 0, 0, 16, 4, 14), box(64, 0, 0, 80, 4, 14)]  # post feet
    b += [box(15, 34, 3, 65, 46, 13), box(15, 66, 3, 65, 76, 13)]
    b += [box(13, 30, 0, 17, 50, 14, "copper"), box(63, 30, 0, 67, 50, 14, "copper")]
    b += [box(13, 62, 0, 17, 78, 14, "copper"), box(63, 62, 0, 67, 78, 14, "copper")]
    b += [box(4, 56, 13, 12, 74, 14, "glow", "e"), box(68, 56, 13, 76, 74, 14, "glow", "e")]
    b += [box(20, 37, 13, 60, 43, 14, "glow", "e")]
    # nacelle along z (cells x2 y2 z0..3)
    b += [box(31, 31, 4, 49, 49, 60)]
    b += [box(30, 30, 14, 50, 50, 18, "copper"), box(30, 30, 40, 50, 50, 44, "copper")]
    b += [box(37, 49, 8, 43, 50, 56, "glow", "e")]
    b += [box(49, 36, 8, 52, 40, 58, "copper"), box(28, 36, 8, 31, 40, 58, "copper")]
    b += [box(34, 34, 60, 46, 46, 64)]  # bearing up to the rotor cell
    # pylon under the nacelle (cell x2 z2 y0..1) with a foot plate
    b += [box(28, 0, 28, 52, 4, 52), box(34, 4, 34, 46, 31, 46)]
    b += [box(33, 12, 33, 47, 15, 47, "copper"), box(38, 6, 46, 42, 26, 47, "glow", "e")]
    return b


def turbine_hub():
    # rotor space: axis through (8, -8, 8), z = 8 is the blade plane
    return [
        box(2, -14, 2, 14, -2, 14),
        box(3, -13, 0, 13, -3, 2, "copper"),
        box(4, -12, 14, 12, -4, 15, "glow", "e"),
    ]


def turbine_blade():
    pitch = ("y", 22.5, (8, 0, 8))
    return [
        box(5, -3, 5, 11, 4, 11, "copper"),  # root collar
        box(3, 4, 7, 13, 30, 9, "blade", "c", pitch),
        box(10, 6, 9, 12, 28, 9.5, "glow", "e", pitch),
    ]


def geothermal_generator():
    b = []
    # volcanic rock plinth and rubble
    b += [box(0, 0, 0, 48, 4, 48, "rock")]
    b += [box(0, 4, 0, 10, 10, 8, "rock"), box(38, 4, 36, 48, 9, 48, "rock"), box(0, 4, 38, 8, 7, 48, "rock"), box(40, 4, 0, 48, 6, 9, "rock")]
    # furnace: four pillars round a glowing lava chamber
    b += [box(12, 4, 12, 36, 24, 36, "lava", "e")]
    for x0, z0 in ((4, 4), (34, 4), (4, 34), (34, 34)):
        b.append(box(x0, 4, z0, x0 + 10, 28, z0 + 10))
    b += [box(14, 14, 5, 34, 16, 13, "copper"), box(14, 14, 35, 34, 16, 43, "copper")]
    b += [box(5, 14, 14, 13, 16, 34, "copper"), box(35, 14, 14, 43, 16, 34, "copper")]
    # heat exchanger slab
    b += [box(6, 24, 6, 42, 32, 42)]
    b += [box(8, 32, 8, 40, 34, 40, "copper")]
    # tower with cyan windows
    b += [box(16, 34, 16, 32, 58, 32)]
    b += [box(17, 38, 15, 31, 54, 16, "glow", "e"), box(17, 38, 32, 31, 54, 33, "glow", "e")]
    b += [box(15, 38, 17, 16, 54, 31, "glow", "e"), box(32, 38, 17, 33, 54, 31, "glow", "e")]
    b += [box(15, 35, 15, 33, 37, 33, "copper")]
    b += [box(14, 58, 14, 34, 62, 34), box(19, 62, 19, 29, 64, 29)]
    # copper riser pipe on the right
    b += [box(36, 26, 22, 40, 56, 26, "copper"), box(32, 52, 22, 36, 56, 26, "copper")]
    return b


def biofuel_generator():
    b = []
    # housing (cells y0..1)
    b += [box(0, 0, 0, 48, 4, 48), box(2, 4, 2, 46, 18, 46)]
    b += [box(18, 6, 46, 30, 16, 47, "burner", "e")]  # burner window, front
    b += [box(2, 17, 2, 46, 18, 46, "copper")]
    # green bio-oil tank (left front 2 x 2)
    b += [box(4, 18, 20, 30, 44, 44, "tank", "t")]
    for x0, z0 in ((2, 18), (28, 18), (2, 42), (28, 42)):
        b.append(box(x0, 18, z0, x0 + 4, 44, z0 + 4))
    b += [box(2, 44, 18, 32, 48, 46)]
    # back housing and right machinery
    b += [box(2, 18, 2, 30, 30, 18)]
    b += [box(32, 18, 18, 46, 32, 46), box(46, 20, 26, 47, 30, 38, "glow", "e")]
    # stack (right back) with copper band, pipe from the tank lid
    b += [box(35, 18, 3, 45, 46, 13), box(33, 44, 1, 47, 48, 15)]
    b += [box(34, 30, 2, 46, 32, 14, "copper")]
    b += [box(14, 44, 8, 18, 48, 18, "copper"), box(18, 44, 8, 35, 47, 12, "copper")]
    return b


KINDS = {
    # id: (width, height, depth, boxes)
    "current_turbine": (5, 5, 5, current_turbine),
    "geothermal_generator": (3, 4, 3, geothermal_generator),
    "biofuel_generator": (3, 3, 3, biofuel_generator),
}

# ---------------------------------------------------------------- writer

FACES = ("down", "up", "north", "south", "west", "east")


def uv(face, f, t):
    """vanilla default UVs of a piece inside one block (coords 0..16)"""
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "down": [x0, 16 - z1, x1, 16 - z0],
        "up": [x0, z0, x1, z1],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0],
        "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[face]


def cuts(a, b):
    """split [a, b] at multiples of 16"""
    out, lo = [], a
    while lo < b:
        hi = min(b, (int(lo // 16) + 1) * 16)
        if hi - lo > 1e-6:
            out.append((lo, hi))
        lo = hi
    return out or [(a, b)]


def rnd(v):
    v = round(v, 4)
    return int(v) if v == int(v) else v


def pieces(bx):
    """split a box at block boundaries -> (from, to, faces present, cell)"""
    f, t = bx["from"], bx["to"]
    for x0, x1 in cuts(f[0], t[0]):
        for y0, y1 in cuts(f[1], t[1]):
            for z0, z1 in cuts(f[2], t[2]):
                present = []
                if y0 == f[1]: present.append("down")
                if y1 == t[1]: present.append("up")
                if z0 == f[2]: present.append("north")
                if z1 == t[2]: present.append("south")
                if x0 == f[0]: present.append("west")
                if x1 == t[0]: present.append("east")
                yield (x0, y0, z0), (x1, y1, z1), present


def element(bx, f, t, faces, shift):
    """model element for a piece; shift = pixels subtracted from every coordinate"""
    cell = [int(min(f[i], t[i] - 1e-6) // 16) for i in range(3)]
    lf = [f[i] - cell[i] * 16 for i in range(3)]
    lt = [t[i] - cell[i] * 16 for i in range(3)]
    el = {
        "from": [rnd(f[i] - shift[i]) for i in range(3)],
        "to": [rnd(t[i] - shift[i]) for i in range(3)],
        "faces": {face: {"uv": [rnd(v) for v in uv(face, lf, lt)], "texture": "#" + bx["tex"]} for face in faces},
    }
    if bx["layer"] == "e":
        el["shade"] = False
    if bx["rot"]:
        axis, angle, origin = bx["rot"]
        el["rotation"] = {"origin": [rnd(origin[i] - shift[i]) for i in range(3)], "axis": axis, "angle": angle}
    for c in el["from"] + el["to"]:
        assert -16 <= c <= 32, ("element out of range", el)
    return el


def mirror(bx, width_px):
    f, t = bx["from"], bx["to"]
    out = dict(bx)
    out["from"] = [width_px - t[0], f[1], f[2]]
    out["to"] = [width_px - f[0], t[1], t[2]]
    if bx["rot"]:
        axis, angle, o = bx["rot"]
        out["rot"] = (axis, angle if axis == "x" else -angle, (width_px - o[0], o[1], o[2]))
    return out


def model_json(elements, textures):
    tex = {"particle": PARTICLE}
    for k in sorted(textures):
        tex[k] = TEX[k]
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": elements}


def write(path, data):
    text = json.dumps(data, indent=2) + "\n"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            if fh.read() == text:
                return False
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    return True


def chunk_origins(n):
    return [0, 3] if n > 3 else [0]


def build_kind(kind, width, height, depth, design):
    models = {}
    for cx in chunk_origins(width):
        for cy in chunk_origins(height):
            for cz in chunk_origins(depth):
                for layer in LAYERS:
                    models[(cx, cy, cz, layer)] = ([], set())
    for raw in design():
        bx = mirror(raw, width * 16)
        for i, n in enumerate((width, height, depth)):
            assert 0 <= bx["from"][i] < bx["to"][i] <= n * 16, (kind, "box outside the footprint", raw)
        for f, t, faces in pieces(bx):
            if not faces:
                continue
            centre = [(f[i] + t[i]) / 2 for i in range(3)]
            chunk = tuple(int(centre[i] // 48) * 3 for i in range(3))
            shift = [chunk[i] * 16 + 16 for i in range(3)]
            elements, textures = models[chunk + (bx["layer"],)]
            elements.append(element(bx, f, t, faces, shift))
            textures.add(bx["tex"])
    changed = 0
    for (cx, cy, cz, layer), (elements, textures) in models.items():
        name = f"{kind}_{cx}_{cy}_{cz}_{layer}"
        changed += write(os.path.join(MODEL_DIR, name + ".json"), model_json(elements, textures))
    return changed, len(models)


def build_rotor(name, design):
    """rotor parts: authored directly in model space (axis through (8, -8, 8)), split at block boundaries"""
    per_layer = {layer: ([], set()) for layer in LAYERS}
    for bx in design():
        for f, t, faces in pieces(bx):
            if faces:
                elements, textures = per_layer[bx["layer"]]
                elements.append(element(bx, f, t, faces, (0, 0, 0)))
                textures.add(bx["tex"])
    changed = 0
    for layer, (elements, textures) in per_layer.items():
        changed += write(os.path.join(MODEL_DIR, f"current_turbine_{name}_{layer}.json"), model_json(elements, textures))
    return changed, len(per_layer)


def main():
    changed = total = 0
    for block in ("generator_part", "current_turbine", "geothermal_generator", "biofuel_generator"):
        changed += write(os.path.join(STATE_DIR, block + ".json"), {"variants": {"": {"model": "abyssia:block/generator/invisible"}}})
        total += 1
    changed += write(os.path.join(MODEL_DIR, "invisible.json"), {"textures": {"particle": PARTICLE}})
    total += 1
    for kind, (w, h, d, design) in KINDS.items():
        c, n = build_kind(kind, w, h, d, design)
        changed += c
        total += n
    for name, design in (("hub", turbine_hub), ("blade", turbine_blade)):
        c, n = build_rotor(name, design)
        changed += c
        total += n
    print(f"generator_assets: {total} files, {changed} changed")
    print("textures needed (16x16):", ", ".join(sorted(set(TEX.values()))))


if __name__ == "__main__":
    main()
