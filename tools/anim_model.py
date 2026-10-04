"""Animated block-entity / entity models: parts JSON or Blockbench .bbmodel -> per-group baked Java mesh + atlas (+ .bbmodel).

Companion of tools/vehicle_model.py (same face / UV / ZYX-rotation conventions, imported from it) but the quads are kept
PER GROUP so the runtime can pose each group (see src/main/java/com/abyssia/client/anim/AnimMeshRenderer.java).

Conventions: px units (16 px = 1 block), origin = centre of the bottom face of the block, x right, y up, -z = front.
Group pivot / cube coordinates are absolute model-space px in the JSON and in the .bbmodel (Blockbench free format).
Baked per group: quads in the group's local space = element rotations applied, pivot subtracted, ancestor rotations NOT
applied.  Part pivot = group pivot relative to the parent group's pivot (model origin for roots), in px; rest rotation =
group rotation in degrees.  Composition (Python compose() / Java renderer):
    M(part) = M(parent) * translate(pivot_rel[/16] + pose offset) * Rz * Ry * Rx (rest + pose rotation, degrees)
which is exactly vehicle_model's  p = R(p - o) + o  chain, i.e. three.js Euler 'ZYX'.

Parts JSON:
  {"texture_size":[w,h] (ignored, the atlas decides), "groups":[{"name","pivot":[x,y,z],"parent":name|null,
    "rotation":[x,y,z],"cubes":[{"from","to","texture":"<tile>" | {face:tile}, "rotation","origin",
    "uv":[u0,v0,u1,v1] (tile px, all faces) | {face:[..]}}]}], "animation":{...free-form, passed through...}}
  Tiles: <tiles-dir>/<tile>.png, 16x16.  Each face samples min(face size, 16) px of the tile, offset by the cube position
  (mod 16) so neighbouring faces are not stretched.  Parents must be listed before children; order = Part index order.

Outputs:  --bbmodel (parts mode only) openable in Blockbench;  --java  <Name>Mesh (public static final Part[] PARTS, Part =
AnimMeshRenderer.Part; ANIMATION_JSON = the animation block as a string constant) + sidecar <java stem>.anim.json;
--texture atlas PNG;  --preview PNG (3/4 view, optional --pose "group=rx,ry,rz[,ox,oy,oz];...").

    python tools/anim_model.py parts dock.json --tiles tiles/ --bbmodel dock.bbmodel --java X.java --package p --name Dock --texture t.png
    python tools/anim_model.py bbmodel dock.bbmodel --java X.java --package p --name Dock --texture t.png [--anim anim.json]
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import math
import os
import sys
import uuid as uuidlib

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vehicle_model as vm  # noqa: E402  (rot_matrix, FACES, NORMALS, icon)

TILE = 16
ANIM_KEY = "abyssia_animation"
FACE_AXES = {"north": (0, 1), "south": (0, 1), "west": (2, 1), "east": (2, 1), "up": (0, 2), "down": (0, 2)}


# ---------------------------------------------------------------- parts JSON -> bbmodel
def build_atlas(tiles_dir, names):
    names = sorted(names)
    cols = max(1, math.ceil(math.sqrt(len(names))))
    rows = max(1, math.ceil(len(names) / cols))
    atlas = Image.new("RGBA", (cols * TILE, rows * TILE), (0, 0, 0, 0))
    pos = {}
    for i, n in enumerate(names):
        p = os.path.join(tiles_dir, n + ".png")
        if not os.path.isfile(p):
            raise SystemExit(f"missing tile: {p}")
        im = Image.open(p).convert("RGBA")
        if im.size != (TILE, TILE):
            raise SystemExit(f"tile {n} must be {TILE}x{TILE}, is {im.size}")
        pos[n] = ((i % cols) * TILE, (i // cols) * TILE)
        atlas.paste(im, pos[n])
    return atlas, pos


def parts_to_bbmodel(spec, tiles_dir):
    used = set()
    for g in spec["groups"]:
        for c in g.get("cubes", []):
            t = c["texture"]
            used.update(t.values() if isinstance(t, dict) else [t])
    atlas, pos = build_atlas(tiles_dir, used)
    gids = {}
    groups, elements = [], []
    children = {}
    for g in spec["groups"]:
        name = g["name"]
        if name in gids:
            raise SystemExit(f"duplicate group name {name}")
        par = g.get("parent")
        if par is not None and par not in gids:
            raise SystemExit(f"group {name}: parent {par} must be listed before it")
        gid = str(uuidlib.uuid4())
        gids[name] = gid
        gd = {"name": name, "origin": [float(v) for v in g["pivot"]], "rotation": [float(v) for v in g.get("rotation", [0, 0, 0])],
              "color": 0, "uuid": gid, "export": True, "isOpen": True, "visibility": True, "locked": False, "autouv": 0}
        groups.append(gd)
        node = {"uuid": gid, "isOpen": True, "children": []}
        children[gid] = node
        for c in g.get("cubes", []):
            lo, hi = [float(v) for v in c["from"]], [float(v) for v in c["to"]]
            size = {"x": hi[0] - lo[0], "y": hi[1] - lo[1], "z": hi[2] - lo[2]}
            faces = {}
            for fname, (ua, va) in FACE_AXES.items():
                tile = c["texture"][fname] if isinstance(c["texture"], dict) else c["texture"]
                if tile is None:
                    continue
                tx, ty = pos[tile]
                ax, ay = "xyz"[ua], "xyz"[va]
                fw, fh = max(min(size[ax], TILE), 0.25), max(min(size[ay], TILE), 0.25)
                uv = c.get("uv")
                if isinstance(uv, dict):
                    uv = uv.get(fname)
                if uv is not None:
                    rect = [tx + uv[0], ty + uv[1], tx + uv[2], ty + uv[3]]
                else:
                    ou = min(lo[ua] % TILE, TILE - fw)
                    ov = min(lo[va] % TILE, TILE - fh)
                    rect = [tx + ou, ty + ov, tx + ou + fw, ty + ov + fh]
                faces[fname] = {"uv": [round(v, 4) for v in rect], "texture": 0}
            el = {"name": c.get("name", "cube"), "type": "cube", "uuid": str(uuidlib.uuid4()), "from": lo, "to": hi,
                  "origin": [float(v) for v in c.get("origin", g["pivot"])], "rotation": [float(v) for v in c.get("rotation", [0, 0, 0])],
                  "faces": faces, "export": True, "color": 0, "box_uv": False, "rescale": False, "locked": False, "autouv": 0}
            elements.append(el)
            node["children"].append(el["uuid"])
        par = g.get("parent")
        if par is None:
            gids.setdefault("_roots", [])
            gids["_roots"].append(node)
        else:
            children[gids[par]]["children"].append(node)
    buf = io.BytesIO()
    atlas.save(buf, "PNG")
    d = {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False}, "name": spec.get("name", "anim_model"),
         "model_identifier": "", "visible_box": [1, 1, 0], "resolution": {"width": atlas.width, "height": atlas.height},
         "elements": elements, "groups": groups, "outliner": gids.get("_roots", []),
         "textures": [{"path": "", "name": "atlas.png", "folder": "", "namespace": "", "id": "0", "particle": True,
                       "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
                       "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": False,
                       "uuid": str(uuidlib.uuid4()), "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}]}
    if spec.get("animation"):
        d[ANIM_KEY] = spec["animation"]
    return d


# ---------------------------------------------------------------- bbmodel -> per-group baked parts
def bake_parts(d):
    """-> (parts, atlas image, animation).  parts: dicts name,parent,pivot(px rel),rot(deg),quads(list of 23 floats, blocks)."""
    tw, th = d["resolution"]["width"], d["resolution"]["height"]
    groups = {g["uuid"]: g for g in d.get("groups", [])}

    def collect(nodes):  # newer Blockbench keeps full group dicts in the outliner
        for n in nodes:
            if isinstance(n, dict):
                if "name" in n:
                    groups.setdefault(n["uuid"], n)
                collect(n.get("children", []))
    collect(d["outliner"])
    els = {e["uuid"]: e for e in d["elements"] if e.get("type", "cube") == "cube"}

    parts, members = [], []  # members[i] = element uuids directly in part i

    def walk(nodes, parent, parent_origin, hidden):
        for n in nodes:
            if isinstance(n, dict):
                g = groups[n["uuid"]]
                o = np.array(g.get("origin", [0, 0, 0]), float)
                parts.append({"name": g["name"], "parent": parent, "pivot": (o - parent_origin).tolist(),
                              "rot": [float(v) for v in g.get("rotation", [0, 0, 0])], "quads": [], "origin": o})
                members.append([])
                idx = len(parts) - 1
                walk(n.get("children", []), idx, o, hidden or g.get("visibility") is False)
            elif n in els:
                if parent < 0:
                    raise SystemExit("ungrouped cubes are not supported (put every cube in a group)")
                if not hidden:
                    members[parent].append(n)

    walk(d["outliner"], -1, np.zeros(3), False)

    chain_of = []
    for i, p in enumerate(parts):
        ch, j = [], i
        while j >= 0:
            ch.append(parts[j]["origin"])
            j = parts[j]["parent"]
        chain_of.append(ch)
    for i, p in enumerate(parts):
        o = p["origin"]
        for uid in members[i]:
            e = els[uid]
            if not e.get("export", True):
                continue
            inf = e.get("inflate", 0)
            lo, hi = np.array(e["from"], float) - inf, np.array(e["to"], float) + inf
            corners = (lo, hi)
            rm, ro = vm.rot_matrix(e.get("rotation", [0, 0, 0])), np.array(e.get("origin", [0, 0, 0]), float)
            size = hi - lo
            for fname, face in e["faces"].items():
                if face.get("texture") is None or fname not in vm.FACES:
                    continue
                axis = {"north": 2, "south": 2, "west": 0, "east": 0, "up": 1, "down": 1}[fname]
                if any(size[k] <= 1e-6 for k in range(3) if k != axis):
                    continue
                u0, v0, u1, v1 = face["uv"]
                uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
                r = face.get("rotation", 0) // 90 % 4
                uvs = uvs[-r:] + uvs[:-r] if r else uvs
                flat = []
                for pick, (u, v) in zip(vm.FACES[fname], uvs):
                    pt = np.array([corners[pick[0]][0], corners[pick[1]][1], corners[pick[2]][2]])
                    pt = rm @ (pt - ro) + ro
                    q = (pt - o) / 16.0
                    flat += [q[0], q[1], q[2], u / tw, v / th]
                flat += list(rm @ np.array(vm.NORMALS[fname], float))
                p["quads"].append(flat)
    src = d["textures"][0]["source"].split(",", 1)[1]
    img = Image.open(io.BytesIO(base64.b64decode(src))).convert("RGBA")
    return parts, img, d.get(ANIM_KEY)


# ---------------------------------------------------------------- composition (python reference of the Java renderer)
def compose(parts, poses=None):
    """-> flat list of quads (23 floats, blocks, model space).  poses: {part name or index: (rx,ry,rz[,ox,oy,oz])}."""
    poses = poses or {}
    mats = []
    out = []
    for i, p in enumerate(parts):
        ps = poses.get(p["name"], poses.get(i, (0, 0, 0, 0, 0, 0)))
        ps = tuple(ps) + (0,) * (6 - len(ps))
        R = vm.rot_matrix([p["rot"][k] + ps[k] for k in range(3)])
        t = np.array(p["pivot"], float) / 16.0 + np.array(ps[3:6], float)
        pm, pt = (mats[p["parent"]] if p["parent"] >= 0 else (np.eye(3), np.zeros(3)))
        M = pm @ R
        T = pm @ t + pt
        mats.append((M, T))
        for q in p["quads"]:
            w = []
            for j in range(4):
                v = M @ np.array(q[j * 5:j * 5 + 3]) + T
                w += [v[0], v[1], v[2], q[j * 5 + 3], q[j * 5 + 4]]
            w += list(M @ np.array(q[20:23]))
            out.append(w)
    return out


# ---------------------------------------------------------------- java
def java_source(pkg, name, parts, anim, src):
    body, decls = [], []

    def farr(vals):
        lines = []
        for i in range(0, len(vals), 10):
            lines.append("            " + ", ".join(f"{v:.5f}f" for v in vals[i:i + 10]) + ",")
        return "\n".join(lines)

    for i, p in enumerate(parts):
        flat = [v for q in p["quads"] for v in q]
        chunks = [flat[k:k + 23 * 250] for k in range(0, len(flat), 23 * 250)]
        calls = []
        for c, ch in enumerate(chunks):
            decls.append(f"    private static float[] q{i}_{c}() {{\n        return new float[] {{\n{farr(ch)}\n        }};\n    }}")
            calls.append(f"q{i}_{c}()")
        arr = f"cat({', '.join(calls)})" if calls else "new float[0]"
        pv, r = p["pivot"], p["rot"]
        body.append(f"        new Part(\"{p['name']}\", {p['parent']}, {pv[0]:.5f}f, {pv[1]:.5f}f, {pv[2]:.5f}f, "
                    f"{r[0]:.5f}f, {r[1]:.5f}f, {r[2]:.5f}f, {arr})")
    aj = json.dumps(anim if anim else {}, separators=(",", ":"))
    if len(aj.encode()) > 60000:
        raise SystemExit("animation JSON too large for a Java string constant")
    return f"""package {pkg};

import com.abyssia.client.anim.AnimMeshRenderer.Part;

/**
 * GENERATED by tools/anim_model.py from {os.path.basename(src)} - do not hand-edit, rerun the tool.
 * One Part per Blockbench group, parents before children (depth-first).  Quads of 23 floats: 4 x (x, y, z, u, v) + (nx, ny, nz);
 * blocks, in the group's local space (pivot at 0, element rotations applied, ancestor rotations not).
 * Part pivot = px relative to the parent's pivot (model origin for roots); rest rotation in degrees (Blockbench ZYX).
 * ANIMATION_JSON = the "animation" block of the source (free-form, may be "{{}}"); also written to <stem>.anim.json.
 */
public final class {name}Mesh
{{
    public static final int STRIDE = 23;
    public static final String ANIMATION_JSON = {json.dumps(aj)};

    public static final Part[] PARTS = {{
{(","+chr(10)).join(body)}
    }};

    private {name}Mesh() {{}}

    private static float[] cat(float[]... a)
    {{
        int n = 0;
        for (float[] x : a) n += x.length;
        float[] r = new float[n];
        int o = 0;
        for (float[] x : a)
        {{
            System.arraycopy(x, 0, r, o, x.length);
            o += x.length;
        }}
        return r;
    }}

{chr(10).join(chr(10).join([d, ""]) for d in decls)}}}
"""


def write(path, data, mode="w"):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    if mode == "w":
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(data)
    else:
        data.save(path)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="mode", required=True)
    for m in ("parts", "bbmodel"):
        s = sub.add_parser(m)
        s.add_argument("input")
        if m == "parts":
            s.add_argument("--tiles", required=True)
            s.add_argument("--bbmodel")
        else:
            s.add_argument("--anim", help="animation JSON (overrides the one stored in the bbmodel)")
        s.add_argument("--java")
        s.add_argument("--package")
        s.add_argument("--name")
        s.add_argument("--texture")
        s.add_argument("--preview")
        s.add_argument("--pose", default="", help="group=rx,ry,rz[,ox,oy,oz];... for --preview")
        s.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    if a.mode == "parts":
        spec = json.load(open(a.input, encoding="utf-8"))
        d = parts_to_bbmodel(spec, a.tiles)
    else:
        d = json.load(open(a.input, encoding="utf-8"))
        if a.anim:
            d[ANIM_KEY] = json.load(open(a.anim, encoding="utf-8"))
    parts, img, anim = bake_parts(d)
    if a.java and not (a.package and a.name):
        raise SystemExit("--java needs --package and --name")
    writes = []
    if a.mode == "parts" and a.bbmodel:
        writes.append(a.bbmodel)
    if a.java:
        writes += [a.java, os.path.splitext(a.java)[0] + ".anim.json"]
    if a.texture:
        writes.append(a.texture)
    if a.preview:
        writes.append(a.preview)
    report = {"parts": [{"name": p["name"], "parent": p["parent"], "quads": len(p["quads"])} for p in parts],
              "atlas": list(img.size), "has_animation": bool(anim), "writes": writes, "dry_run": a.dry_run}
    if not a.dry_run:
        if a.mode == "parts" and a.bbmodel:
            write(a.bbmodel, json.dumps(d, indent=1))
        if a.java:
            write(a.java, java_source(a.package, a.name, parts, anim, a.input))
            write(os.path.splitext(a.java)[0] + ".anim.json", json.dumps(anim or {}, indent=1))
        if a.texture:
            write(a.texture, img, "img")
        if a.preview:
            poses = {}
            for item in filter(None, a.pose.split(";")):
                k, v = item.split("=")
                poses[k] = [float(x) for x in v.split(",")]
            flat = [v for q in compose(parts, poses) for v in q]
            write(a.preview, vm.icon({"HULL": flat, "GLASS": [], "LAMPS": []}, img, size=128, ss=4), "img")
    print(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()
