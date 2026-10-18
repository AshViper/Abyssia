"""Hand lamp (item abyssia:hand_lamp, com.abyssia.item.light.HandLampItem): 3D item models + flat textures.

Shape and display come from tools/electric_models/hand_lamp_parts.json (same element flow as
electric_tool_assets.screw_model(); UVs follow the element position).  Two models are written:
  models/item/hand_lamp.json     off; override predicate abyssia:on >= 1 -> hand_lamp_on
  models/item/hand_lamp_on.json  on; lens + switch use their *_on texture and are full-bright
The predicate abyssia:on is registered client-side by com.abyssia.client.light.HandLampItemProperty.
Textures textures/item/hand_lamp_<tex>.png are drawn here (PIL, few flat colours, propulsion screw palette) and go
through texture_locks.save (missing-only by default; `--redraw` rewrites them).
Run: python tools/hand_lamp_assets.py [--redraw]   (electric_tool_assets.generate() also calls generate()).
"""
import json
import math
import os
import sys

MOD = "abyssia"
NAME = "hand_lamp"
_HERE = os.path.dirname(os.path.abspath(__file__))
_FACES = ("north", "east", "south", "west", "up", "down")

# palette (propulsion_screw_* textures)
BODY = (66, 83, 99)
BODY_HI = (103, 128, 137)
BODY_MID = (80, 101, 112)
BODY_LO = (58, 68, 82)
BODY_DK = (44, 52, 63)
TEAL = (3, 118, 113)
TEAL_HI = (2, 151, 153)
RUB = (48, 52, 57)
RUB_HI = (64, 74, 85)
RUB_DK = (36, 40, 45)
SILVER = (175, 191, 198)
SILVER_HI = (214, 228, 233)
GLASS_DK = (16, 34, 42)
GLASS = (24, 56, 66)
GLASS_HI = (96, 140, 150)
GLOW_EDGE = (2, 151, 153)
GLOW = (18, 250, 253)
GLOW_MID = (107, 250, 253)
GLOW_HI = (197, 250, 253)
GLOW_CORE = (236, 255, 255)
SW_OFF = (112, 44, 40)
SW_OFF_HI = (150, 70, 60)
SW_ON = (96, 255, 160)
SW_ON_HI = (200, 255, 220)


def _spec():
    with open(os.path.join(_HERE, "electric_models", NAME + "_parts.json"), encoding="utf-8") as f:
        return json.load(f)


def _tex(t):
    return f"{MOD}:item/{NAME}_{t}"


def model(on):
    spec = _spec()
    textures = {"particle": _tex("body")}
    elements = []
    for part in spec["parts"]:
        base = part.get("on_tex", part["tex"]) if on else part["tex"]
        faces = {}
        for d in _FACES:
            t = part.get("face_tex", {}).get(d, base)
            textures[t] = _tex(t)
            faces[d] = {"texture": "#" + t}
        element = {"name": part["name"], "from": part["from"], "to": part["to"], "faces": faces}
        if on and part.get("emissive_on"):
            element["forge_data"] = {"block_light": 15, "sky_light": 15}
        elements.append(element)
    out = {"textures": textures, "elements": elements, "display": spec["display"]}
    if not on:
        out["overrides"] = [{"predicate": {f"{MOD}:on": 1}, "model": f"{MOD}:item/{NAME}_on"}]
    return out


# ---------------------------------------------------------------- textures (16x16)

def _img(fill):
    from PIL import Image
    return Image.new("RGBA", (16, 16), fill + (255,))


def _put(im, x, y, c):
    im.putpixel((x, y), c + (255,))


def tex_body():
    """Bevelled 4x4 metal plates: light top/left edge, dark bottom/right edge, a rivet in every other plate."""
    im = _img(BODY)
    for y in range(16):
        for x in range(16):
            lx, ly = x % 4, y % 4
            c = BODY
            if lx == 3 or ly == 3:
                c = BODY_LO
            elif lx == 0 or ly == 0:
                c = BODY_MID
            if (x // 4 + y // 4) % 2 == 0 and lx == 1 and ly == 1:
                c = BODY_HI
            _put(im, x, y, c)
    return im


def tex_grip():
    """Rubber with diamond knurling (reads as grip on every face)."""
    im = _img(RUB)
    for y in range(16):
        for x in range(16):
            a, b = (x + y) % 3, (x - y) % 3
            c = RUB
            if a == 0 and b == 0:
                c = RUB_HI
            elif a == 0 or b == 0:
                c = RUB_DK
            _put(im, x, y, c)
    return im


def tex_accent():
    im = _img(TEAL)
    for x in range(16):
        for y in range(16):
            if (x + y) % 4 == 0:
                _put(im, x, y, TEAL_HI)
    return im


def _radial(colours):
    """Concentric rings around the face centre (8, 8); colours = [(radius_limit, colour), ...] inner first."""
    im = _img(colours[-1][1])
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            for lim, c in colours:
                if r < lim:
                    _put(im, x, y, c)
                    break
    return im


def tex_reflector():
    # lens covers r < 3; the polished cone shows from 3 to 4.5, then the head rim
    return _radial([(3.2, BODY_DK), (3.8, SILVER_HI), (4.4, SILVER), (16, BODY_MID)])


def tex_lens():
    im = _radial([(1.6, GLASS), (2.6, GLASS), (16, GLASS_DK)])
    for x, y in ((6, 6), (7, 6), (6, 7)):     # cold glint top-left
        _put(im, x, y, GLASS_HI)
    return im


def tex_lens_on():
    im = _radial([(1.2, GLOW_CORE), (2.0, GLOW_HI), (2.7, GLOW_MID), (3.2, GLOW), (16, GLOW_EDGE)])
    for x, y in ((6, 6), (7, 6), (6, 7)):
        _put(im, x, y, GLOW_CORE)
    return im


def _switch(base, hi):
    im = _img(base)
    for x in range(16):
        _put(im, x, 0, hi)
    for y in range(16):
        _put(im, 0, y, hi)
    return im


TEXTURES = {
    "body": tex_body, "grip": tex_grip, "accent": tex_accent, "reflector": tex_reflector,
    "lens": tex_lens, "lens_on": tex_lens_on,
    "switch": lambda: _switch(SW_OFF, SW_OFF_HI), "switch_on": lambda: _switch(SW_ON, SW_ON_HI),
}


def generate(write, im, item_tex_dir=None):
    import texture_locks
    write(im(NAME), model(False))
    write(im(NAME + "_on"), model(True))
    if item_tex_dir:
        for t, fn in TEXTURES.items():
            texture_locks.save(fn(), os.path.join(item_tex_dir, f"{NAME}_{t}.png"))
    return {"items": 1}


if __name__ == "__main__":
    sys.path.insert(0, _HERE)
    import gen_deep_assets as g
    import texture_locks
    if "--redraw" in sys.argv:
        texture_locks.set_mode("all")
    generate(g.write, lambda n: os.path.join(g.ASSETS, "models", "item", n + ".json"), g.ITEM_TEX)
    print("hand lamp written")
