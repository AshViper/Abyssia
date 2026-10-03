"""Hydro planter (feature PL01, spec inbox/specs/PL01-hydro-planter.md): blockstate, models, loot, recipe, names.

gen_deep_assets.main() calls generate() and merges LANG / pickaxe_blocks().  The Java side is
com.abyssia.furniture.HydroPlanter* + registry/ModFurniture.  Textures block/hydro_planter_{side,top,bottom,frame}
are imported from the ChatGPT sheet PLANT1, never written here (the GUI comes from tools/planter_gui.py).

Connected look: a multipart blockstate.  The base cube has no frame; the frame is split into 24 thin overlay quads,
4 edge strips for each of the 6 faces.  An edge strip is drawn only when the block next to that edge (north/south/
east/west/up/down boolean of the state) is not a planter, and the whole face is culled by the neighbour like any
face.  Overlays sit 0.01 outside the cube (cutout) so they do not z-fight.  The crop (state crop + ripe) is two
crossed planes standing on the top face.
"""
import os

MOD = "abyssia"
ID = "hydro_planter"
FRAME_PX = 2          # width of the frame strip in the frame texture (px of 16)
OFF = 0.01            # overlay offset outside the cube

NAMES = {ID: ("Hydro Planter", "水耕栽培プランター")}
LANG = {
    f"block.{MOD}.{ID}": NAMES[ID],
    f"container.{MOD}.{ID}": NAMES[ID],
    f"gui.{MOD}.{ID}.growth": ("Growth: %s%%", "成長: %s%%"),
    f"gui.{MOD}.{ID}.ready": ("Ready: right-click to harvest", "収穫可能: 右クリックで収穫"),
    f"gui.{MOD}.{ID}.empty": ("Insert a seedling", "苗を入れてください"),
}

# crop state -> plant texture standing in the planter
CROPS = {"mushroom": "abyssal_mushroom", "gourd": "pressure_gourd", "kelp": "deep_kelp_top"}

DIRS = ("north", "south", "east", "west", "up", "down")
# face -> (fixed axis index, plane coordinate)
PLANE = {"north": (2, -OFF), "south": (2, 16 + OFF), "west": (0, -OFF), "east": (0, 16 + OFF),
         "down": (1, -OFF), "up": (1, 16 + OFF)}


def rl(name, kind="block"):
    return f"{MOD}:{kind}/{name}"


def pickaxe_blocks():
    return [ID]


def _strips(face):
    """[(neighbour direction, [x0, y0, z0], [x1, y1, z1])] of the four frame strips of a face (flat on the plane)."""
    axis, plane = PLANE[face]
    w = FRAME_PX

    def box(a0, a1, b0, b1):  # a, b = the two free axes in ascending index order
        free = [i for i in range(3) if i != axis]
        lo, hi = [0.0] * 3, [0.0] * 3
        lo[axis] = hi[axis] = plane
        lo[free[0]], hi[free[0]] = a0, a1
        lo[free[1]], hi[free[1]] = b0, b1
        return lo, hi

    if face in ("up", "down"):  # free axes x, z
        return [("west", *box(0, w, 0, 16)), ("east", *box(16 - w, 16, 0, 16)),
                ("north", *box(w, 16 - w, 0, w)), ("south", *box(w, 16 - w, 16 - w, 16))]
    if face in ("north", "south"):  # free axes x, y
        return [("west", *box(0, w, w, 16 - w)), ("east", *box(16 - w, 16, w, 16 - w)),
                ("up", *box(0, 16, 16 - w, 16)), ("down", *box(0, 16, 0, w))]
    # west / east: free axes y, z
    return [("north", *box(w, 16 - w, 0, w)), ("south", *box(w, 16 - w, 16 - w, 16)),
            ("up", *box(16 - w, 16, 0, 16)), ("down", *box(0, w, 0, 16))]


def _edge_elements(face):
    """Strip element per neighbour direction, for the given face.  Default UVs map the strip onto the same area of
    the 16x16 frame texture, so no explicit uv is needed."""
    return {d: {"from": lo, "to": hi, "faces": {face: {"texture": "#frame", "cullface": face}}}
            for d, lo, hi in _strips(face)}


def _frame_model(face, direction):
    el = _edge_elements(face)[direction]
    return {"render_type": "minecraft:cutout", "textures": {"particle": rl(ID + "_frame"), "frame": rl(ID + "_frame")},
            "elements": [el]}


def _crop_model(plant, ripe):
    top = 28 if ripe else 22
    uv = [0, 0, 16, 16]
    planes = [{"from": [2, 16, 8], "to": [14, top, 8], "faces": {f: {"uv": uv, "texture": "#plant"} for f in ("north", "south")}},
              {"from": [8, 16, 2], "to": [8, top, 14], "faces": {f: {"uv": uv, "texture": "#plant"} for f in ("east", "west")}}]
    return {"ambientocclusion": False, "render_type": "minecraft:cutout",
            "textures": {"particle": rl(plant), "plant": rl(plant)}, "elements": planes}


def _base_cube():
    return {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "down": {"texture": "#bottom", "cullface": "down"}, "up": {"texture": "#top", "cullface": "up"},
        **{f: {"texture": "#side", "cullface": f} for f in ("north", "south", "east", "west")}}}


def generate(write, bs, bm, im, data_dir):
    """Writes the models, blockstate, loot table and recipe.  Returns the number of block models."""
    tex = {"side": rl(ID + "_side"), "top": rl(ID + "_top"), "bottom": rl(ID + "_bottom")}
    base = {"parent": "minecraft:block/block", "textures": {"particle": tex["side"], **tex}, "elements": [_base_cube()]}
    write(bm(ID), base)

    multipart = [{"apply": {"model": rl(ID)}}]
    count = 1
    item_elements = [_base_cube()]
    for face in PLANE:
        for d in _edge_elements(face):
            name = f"{ID}_frame_{face}_{d}"
            write(bm(name), _frame_model(face, d))
            multipart.append({"when": {face: "false", d: "false"}, "apply": {"model": rl(name)}})
            el = dict(_edge_elements(face)[d])
            el["faces"] = {face: {"texture": "#frame"}}
            item_elements.append(el)
            count += 1
    for crop, plant in CROPS.items():
        for ripe in (False, True):
            name = f"{ID}_crop_{crop}" + ("_ripe" if ripe else "")
            write(bm(name), _crop_model(plant, ripe))
            multipart.append({"when": {"crop": crop, "ripe": str(ripe).lower()}, "apply": {"model": rl(name)}})
            count += 1
    write(bs(ID), {"multipart": multipart})

    write(im(ID), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                   "textures": {"particle": tex["side"], "frame": rl(ID + "_frame"), **tex}, "elements": item_elements})

    lt = os.path.join(data_dir, MOD, "loot_tables", "blocks", ID + ".json")
    write(lt, {"type": "minecraft:block", "pools": [{
        "rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:survives_explosion"}],
        "entries": [{"type": "minecraft:item", "name": f"{MOD}:{ID}"}]}], "random_sequence": f"{MOD}:blocks/{ID}"})
    rd = os.path.join(data_dir, MOD, "recipes", ID + ".json")
    write(rd, {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["IPI", "G G", "IPI"],
               "key": {"I": {"item": "minecraft:iron_ingot"}, "P": {"item": "minecraft:glass_pane"},
                       "G": {"item": f"{MOD}:organic_matter"}},
               "result": {"item": f"{MOD}:{ID}", "count": 1}})
    return count
