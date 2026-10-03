"""Hydro planter (features PL01 / PL02, spec inbox/specs/PL02-planter-remake.md): blockstate, models, loot, recipe, names.

gen_deep_assets.main() calls generate() and merges LANG / pickaxe_blocks().  The Java side is
com.abyssia.furniture.HydroPlanter{Block,BlockEntity} + client.HydroPlanterRenderer + registry/ModFurniture.

PL02: a plain bottom half slab (vanilla block/slab parent, no frame, no connection, no GUI).  The four crops in the
2x2 cells are drawn by the block entity renderer from block/planter_<crop>_<stage> (stage 0..2), so they need no
model here.  Textures block/hydro_planter_{top,side,bottom} (sheet PLT1) and planter_*_0..2 (sheet PLT2) are imported
from ChatGPT sheets, never written here.  The PL01 frame texture block/hydro_planter_frame is no longer referenced.
"""
import os

MOD = "abyssia"
ID = "hydro_planter"
FRAME_PX = 2          # width of the frame strip in the frame texture (px of 16)
OFF = 0.01            # overlay offset outside the cube

NAMES = {ID: ("Hydro Planter", "水耕栽培プランター")}
LANG = {
    f"block.{MOD}.{ID}": NAMES[ID],
}

# crop -> planter_<crop>_<stage> textures drawn by HydroPlanterRenderer (listed for tooling; must match PlanterCrop)
CROPS = ("mushroom", "gourd", "kelp")
STAGES = 3


def rl(name, kind="block"):
    return f"{MOD}:{kind}/{name}"


def pickaxe_blocks():
    return [ID]


def crop_textures():
    """The block texture names the renderer needs (imported from sheet PLT2)."""
    return [f"planter_{c}_{s}" for c in CROPS for s in range(STAGES)]


def generate(write, bs, bm, im, data_dir):
    """Writes the model, blockstate, item model, loot table and recipe.  Returns the number of block models."""
    tex = {"bottom": rl(ID + "_bottom"), "top": rl(ID + "_top"), "side": rl(ID + "_side")}
    write(bm(ID), {"parent": "minecraft:block/slab", "textures": tex})
    write(bs(ID), {"variants": {"": {"model": rl(ID)}}})
    write(im(ID), {"parent": rl(ID)})

    lt = os.path.join(data_dir, MOD, "loot_tables", "blocks", ID + ".json")
    write(lt, {"type": "minecraft:block", "pools": [{
        "rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:survives_explosion"}],
        "entries": [{"type": "minecraft:item", "name": f"{MOD}:{ID}"}]}], "random_sequence": f"{MOD}:blocks/{ID}"})
    rd = os.path.join(data_dir, MOD, "recipes", ID + ".json")
    write(rd, {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["IPI", "G G", "IPI"],
               "key": {"I": {"item": "minecraft:iron_ingot"}, "P": {"item": "minecraft:glass_pane"},
                       "G": {"item": f"{MOD}:organic_matter"}},
               "result": {"item": f"{MOD}:{ID}", "count": 1}})
    return 1
