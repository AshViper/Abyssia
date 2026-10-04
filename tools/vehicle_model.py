"""Bake a Blockbench free-format vehicle model (.bbmodel) into a loader-neutral Java mesh class + entity texture + item icon.

Vanilla ModelPart only does box UV, but the user's models use per-face UV and arbitrary (multi-axis) rotations, so the
model is baked into plain quads here (every element / group rotation applied, Blockbench ZYX euler order) and the
renderer just emits them.  Output (never hand-edit, rerun this):

    src/main/java/com/abyssia/vehicle/client/<Name>Mesh.java      float[] quads per layer
    src/main/resources/assets/abyssia/textures/entity/<id>.png      texture embedded in the .bbmodel
    src/main/resources/assets/abyssia/textures/item/<id>.png        16x16 icon rendered from the mesh

Quad layout (23 floats): 4 x (x, y, z, u, v) + (nx, ny, nz).  x/y/z in blocks, y up, model front = -Z, bottom = y 0,
centred on x/z 0; u/v normalised.  Layers: HULL (opaque), GLASS (group named Grass/Glass: translucent), LAMPS (the
smallest cube of each group whose name contains "light": drawn full-bright while the lights are on).

    python tools/vehicle_model.py F:/BlockBench/bbmodel/Submarine.bbmodel --id submarine --name Submarine [--root DIR]
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import math
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))


def rot_matrix(deg):
    rx, ry, rz = (math.radians(a) for a in deg)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx  # three.js Euler order 'ZYX' (Blockbench): X applied first


# face -> (corner picks as (x, y, z) index 0=from 1=to) TL, TR, BR, BL ; uv corners (u0,v0) (u1,v0) (u1,v1) (u0,v1)
FACES = {
    "north": ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)),
    "south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)),
    "west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)),
    "east": ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)),
    "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
    "down": ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)),
}
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def walk(nodes, groups, chain, out):
    for node in nodes:
        if isinstance(node, dict):
            g = groups[node["uuid"]]
            walk(node.get("children", []), groups, chain + [g], out)
        else:
            out[node] = chain


def bake(path):
    d = json.load(open(path, encoding="utf-8"))
    tw, th = d["resolution"]["width"], d["resolution"]["height"]
    groups = {g["uuid"]: g for g in d.get("groups", [])}
    chains = {}
    walk(d["outliner"], groups, [], chains)
    els = {e["uuid"]: e for e in d["elements"] if e.get("type", "cube") == "cube"}

    lamps = set()
    for g in groups.values():
        if "light" in g["name"].lower():
            members = [u for u, c in chains.items() if c and c[-1] is g and u in els]
            if members:
                vol = lambda u: np.prod(np.abs(np.subtract(els[u]["to"], els[u]["from"])) + 1e-3)
                lamps.add(min(members, key=vol))

    layers = {"HULL": [], "GLASS": [], "LAMPS": []}
    for uuid, e in els.items():
        chain = chains.get(uuid, [])
        if not e.get("export", True) or any(g.get("visibility") is False for g in chain):
            continue
        inf = e.get("inflate", 0)
        lo = np.array(e["from"], float) - inf
        hi = np.array(e["to"], float) + inf
        corners = (lo, hi)
        mats = [(rot_matrix(e.get("rotation", [0, 0, 0])), np.array(e.get("origin", [0, 0, 0]), float))]
        for g in reversed(chain):
            mats.append((rot_matrix(g.get("rotation", [0, 0, 0])), np.array(g.get("origin", [0, 0, 0]), float)))
        layer = "GLASS" if any(g["name"].lower() in ("grass", "glass") for g in chain) else "LAMPS" if uuid in lamps else "HULL"
        size = hi - lo
        for fname, face in e["faces"].items():
            if face.get("texture") is None or fname not in FACES:
                continue
            axis = {"north": 2, "south": 2, "west": 0, "east": 0, "up": 1, "down": 1}[fname]
            others = [i for i in range(3) if i != axis]
            if any(size[i] <= 1e-6 for i in others):
                continue  # degenerate edge face of a plane
            u0, v0, u1, v1 = face["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            r = face.get("rotation", 0) // 90 % 4
            uvs = uvs[-r:] + uvs[:-r] if r else uvs
            verts = []
            for pick, (u, v) in zip(FACES[fname], uvs):
                p = np.array([corners[pick[0]][0], corners[pick[1]][1], corners[pick[2]][2]])
                for m, o in mats:
                    p = m @ (p - o) + o
                verts.append((p, u / tw, v / th))
            n = np.array(NORMALS[fname], float)
            for m, _ in mats:
                n = m @ n
            layers[layer].append((verts, n))

    pts = np.array([v[0] for q in sum(layers.values(), []) for v in q[0]])
    lo, hi = pts.min(0), pts.max(0)
    shift = np.array([-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2])
    out = {}
    for name, quads in layers.items():
        flat = []
        for verts, n in quads:
            for p, u, v in verts:
                q = (p + shift) / 16.0
                flat += [q[0], q[1], q[2], u, v]
            flat += list(n)
        out[name] = flat
    tex = d["textures"][0]["source"].split(",", 1)[1]
    img = Image.open(io.BytesIO(base64.b64decode(tex))).convert("RGBA")
    dims = (hi - lo) / 16.0
    return out, img, dims


def java(name, layers, dims, src):
    def arr(vals):
        lines, row = [], []
        for i, v in enumerate(vals):
            row.append(f"{v:.5f}f")
            if len(row) == 10:
                lines.append("            " + ", ".join(row) + ",")
                row = []
        if row:
            lines.append("            " + ", ".join(row) + ",")
        return "\n".join(lines)

    body = "\n\n".join(f"    public static final float[] {k} = {{\n{arr(v)}\n    }};" for k, v in layers.items())
    return f"""package com.abyssia.vehicle.client;

/**
 * GENERATED by tools/vehicle_model.py from {os.path.basename(src)} - do not hand-edit, rerun the tool.
 * Quads of 23 floats: 4 x (x, y, z, u, v) + (nx, ny, nz); blocks, y up, front = -Z, bottom = 0.
 * Size {dims[0]:.3f} x {dims[1]:.3f} x {dims[2]:.3f} blocks (x, y, z).
 */
public final class {name}Mesh
{{
    public static final int STRIDE = 23;
    public static final float WIDTH = {dims[0]:.4f}f, HEIGHT = {dims[1]:.4f}f, LENGTH = {dims[2]:.4f}f;

    private {name}Mesh() {{}}

{body}
}}
"""


def icon(layers, img, size=16, ss=8):
    """Orthographic 3/4 view (from front-left, a little above), z-buffered, supersampled."""
    tex = np.asarray(img).astype(float) / 255.0
    th, tw = tex.shape[:2]
    yaw, pitch = math.radians(135), math.radians(25)
    ry = np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
    rx = np.array([[1, 0, 0], [0, math.cos(pitch), -math.sin(pitch)], [0, math.sin(pitch), math.cos(pitch)]])
    view = rx @ ry
    quads = []
    for name in ("HULL", "LAMPS", "GLASS"):
        f = layers[name]
        for i in range(0, len(f), 23):
            q = f[i:i + 23]
            quads.append(([np.array(q[j * 5:j * 5 + 3]) for j in range(4)], [(q[j * 5 + 3], q[j * 5 + 4]) for j in range(4)],
                          np.array(q[20:23]), name))
    pts = np.array([view @ p for q in quads for p in q[0]])
    lo, hi = pts[:, :2].min(0), pts[:, :2].max(0)
    n = size * ss
    scale = (n - 2 * ss) / max(hi - lo)
    off = (n - (hi - lo) * scale) / 2
    color = np.zeros((n, n, 4))
    depth = np.full((n, n), -1e9)
    light = view @ np.array([0.3, 0.8, -0.5])
    light /= np.linalg.norm(light)
    for verts, uvs, nrm, name in quads:
        vn = view @ nrm
        shade = 0.55 + 0.45 * max(0.0, float(vn @ light))
        sp = [view @ p for p in verts]
        scr = [((p[0] - lo[0]) * scale + off[0], n - ((p[1] - lo[1]) * scale + off[1]), p[2]) for p in sp]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (np.array(scr[t]) for t in tri)
            ua, ub, uc = (np.array(uvs[t]) for t in tri)
            xmin, xmax = int(max(0, math.floor(min(a[0], b[0], c[0])))), int(min(n - 1, math.ceil(max(a[0], b[0], c[0]))))
            ymin, ymax = int(max(0, math.floor(min(a[1], b[1], c[1])))), int(min(n - 1, math.ceil(max(a[1], b[1], c[1]))))
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-9:
                continue
            ys, xs = np.mgrid[ymin:ymax + 1, xmin:xmax + 1]
            px, py = xs + 0.5, ys + 0.5
            w0 = ((b[1] - c[1]) * (px - c[0]) + (c[0] - b[0]) * (py - c[1])) / den
            w1 = ((c[1] - a[1]) * (px - c[0]) + (a[0] - c[0]) * (py - c[1])) / den
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * a[2] + w1 * b[2] + w2 * c[2]
            u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
            v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
            tx = np.clip((u * tw).astype(int), 0, tw - 1)
            ty = np.clip((v * th).astype(int), 0, th - 1)
            texel = tex[ty, tx]
            ok = inside & (texel[..., 3] > 0.1) & (z > depth[ys, xs])
            if name == "GLASS":  # glass blends over what is behind, never writes depth
                al = texel[..., 3:4] * 0.6
                rgb = texel[..., :3] * shade
                cur = color[ys, xs]
                blend = np.concatenate([cur[..., :3] * (1 - al) + rgb * al, np.maximum(cur[..., 3:4], al)], -1)
                color[ys[ok], xs[ok]] = blend[ok]
                continue
            rgb = texel[..., :3] * (1.0 if name == "LAMPS" else shade)
            color[ys[ok], xs[ok], :3] = rgb[ok]
            color[ys[ok], xs[ok], 3] = 1.0
            depth[ys[ok], xs[ok]] = z[ok]
    big = Image.fromarray((np.clip(color, 0, 1) * 255).astype(np.uint8), "RGBA")
    small = big.resize((size, size), Image.BOX)
    a = np.asarray(small).copy()
    a[..., 3] = np.where(a[..., 3] > 100, 255, 0)
    return Image.fromarray(a, "RGBA")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("bbmodel")
    ap.add_argument("--id", required=True)
    ap.add_argument("--name", required=True)
    ap.add_argument("--root", default=os.path.join(HERE, ".."), help="project root (main or the NeoForge worktree)")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    layers, img, dims = bake(a.bbmodel)
    root = os.path.abspath(a.root)
    jpath = os.path.join(root, "src/main/java/com/abyssia/vehicle/client", f"{a.name}Mesh.java")
    tpath = os.path.join(root, "src/main/resources/assets/abyssia/textures/entity", f"{a.id}.png")
    ipath = os.path.join(root, "src/main/resources/assets/abyssia/textures/item", f"{a.id}.png")
    report = {"quads": {k: len(v) // 23 for k, v in layers.items()}, "size_blocks": [round(x, 3) for x in dims],
              "writes": [jpath, tpath, ipath], "dry_run": a.dry_run}
    if not a.dry_run:
        os.makedirs(os.path.dirname(jpath), exist_ok=True)
        with open(jpath, "w", encoding="utf-8", newline="\n") as f:
            f.write(java(a.name, layers, dims, a.bbmodel))
        os.makedirs(os.path.dirname(tpath), exist_ok=True)
        os.makedirs(os.path.dirname(ipath), exist_ok=True)
        img.save(tpath)
        icon(layers, img).save(ipath)
    print(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()
