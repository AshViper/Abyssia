#!/usr/bin/env python3
"""ChatGPT ImageGen sheet workflow: presets, prompts, icon detection, import and undo (CLI + library for server.py).

Presets live in inbox/prompts/sheets.json (one entry per sheet: ids in reading order, kind, dest, skip, lock,
derive; the prompt text is read from the inbox/prompts/*.md file at request time).  The Agent Flow web GUI
(tab 画像生成, tools/agentflow/server.py) calls the same functions.  All output is JSON.

    python tools/agentflow/sheets.py list                          # presets + status (prompt / image / imported)
    python tools/agentflow/sheets.py prompt ORE                    # prompt text to paste into ChatGPT
    python tools/agentflow/sheets.py panels IMAGE                  # panel rectangles suggested from white gutters
    python tools/agentflow/sheets.py detect IMAGE --preset ORE [--region x0,y0,x1,y1 | --region panel:0] [--merge 3]
    python tools/agentflow/sheets.py import IMAGE --preset ORE [--region ...] [--merge N] [--ids a,b,-,c] [--dry-run]
    python tools/agentflow/sheets.py undo [--dry-run]              # restore the last GUI/CLI import (textures + locks)

IMAGE may be ``auto``: the image uploaded for that preset (inbox/textures/sheets/<name>.*) or the preset's
``image`` fallback.  Without --region the preset's ``panel`` of the auto-detected panels is used (else the whole
image).  --ids overrides the id of every detected icon in reading order ('-' = do not write that icon).

Import: 16x16 tiles -> src/main/resources/assets/abyssia/textures/<dest>/<id>.png; overwritten files (and locks)
are backed up to inbox/backup/gui-import-<timestamp>/ with manifest.json; locks updated when preset.lock; derived
textures (glow, polished/bricks, ...) of the imported bases rebuilt with derive_textures; check_textures run.
ids in preset.skip are never written.  Needs Pillow, numpy, scipy (loaded lazily).
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import re
import shutil
import sys
import threading
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
TOOLS = ROOT / "tools"
PROMPTS = ROOT / "inbox" / "prompts"
PRESETS = PROMPTS / "sheets.json"
INBOX_TEX = ROOT / "inbox" / "textures"
SHEETS = INBOX_TEX / "sheets"
BACKUP = ROOT / "inbox" / "backup"
TEX = ROOT / "src" / "main" / "resources" / "assets" / "abyssia" / "textures"
LOCKS = TOOLS / "texture_locks" / "assets" / "textures"

NAME_RE = re.compile(r"^[A-Za-z0-9_]{1,40}$")
ID_RE = re.compile(r"^[a-z0-9_]{1,64}$")
DESTS = ("block", "item")
EXTS = (".png", ".webp", ".jpg", ".jpeg")
MAX_UPLOAD = 25 * 1024 * 1024
MAX_PIXELS = 40_000_000
WRITE_LOCK = threading.Lock()

if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))


class SheetError(Exception):
    """User-facing error (bad name, count mismatch, ...); reported as {"error": ...}."""


# ================================================================ presets / prompts

def load() -> dict:
    data = json.loads(PRESETS.read_text(encoding="utf-8"))
    kinds = data.get("kinds", {})
    for s in data["sheets"]:
        if not NAME_RE.match(s.get("name", "")):
            raise SheetError(f"bad preset name {s.get('name')!r}")
        if s.get("kind") not in kinds:
            raise SheetError(f"{s['name']}: unknown kind {s.get('kind')!r}")
        bad = [i for i in s.get("ids", []) + s.get("skip", []) if not ID_RE.match(i)]
        if bad:
            raise SheetError(f"{s['name']}: bad ids {bad}")
    return data


def preset(name: str) -> dict:
    if not isinstance(name, str) or not NAME_RE.match(name):
        raise SheetError("bad preset name")
    data = load()
    for s in data["sheets"]:
        if s["name"] == name:
            p = {"lock": True, "derive": False, "skip": [], **data["kinds"][s["kind"]], **s}
            if p["dest"] not in DESTS:
                raise SheetError(f"{name}: dest must be block or item")
            return p
    raise SheetError(f"unknown preset {name}")


def _sections(md: str) -> list[tuple[str, str]]:
    out, head, buf = [], "", []
    for line in md.splitlines():
        if line.startswith("## "):
            out.append((head, "\n".join(buf)))
            head, buf = line[3:].strip(), []
        else:
            buf.append(line)
    out.append((head, "\n".join(buf)))
    return out


def _fenced(body: str) -> str | None:
    m = re.search(r"```[^\n]*\n(.*?)```", body, re.S)
    return m.group(1).strip() if m else None


def _section(secs, key: str, exact: bool = False) -> str:
    for head, body in secs:
        if (head.split()[0] == key if exact and head else key in head):
            return body
    raise SheetError(f"section {key!r} not found")


def prompt(name: str) -> str:
    p = preset(name)
    spec = p["prompt"]
    f = (PROMPTS / Path(spec["file"]).name)
    md = f.read_text(encoding="utf-8")
    secs = _sections(md)
    common = ""
    if spec.get("common"):
        common = _fenced(_section(secs, spec["common"])) or ""
    elif spec.get("common_para"):
        paras = [x.strip() for x in secs[0][1].split("\n\n")]
        common = next((x for x in paras if x.startswith(spec["common_para"])), "")
    if spec.get("per_id"):
        data = load()
        n, cols = len(p["ids"]), p.get("cols", 4)
        lines = [common, "", data["sheet_note"].format(n=n, cols=cols, rows=-(-n // cols)), "このシートの内容（左→右、上→下の順）:"]
        for i, bid in enumerate(p["ids"], 1):
            body = _section(secs, bid, exact=True)
            keep = [x.strip() for x in body.splitlines()
                    if x.strip() and not x.startswith(("保存先", "inbox/", "生成指示", "---"))]
            lines.append(f"{i}. {bid}: " + " / ".join(keep))
        text = "\n".join(lines).strip()
    else:
        text = _fenced(_section(secs, spec["section"]))
        if text is None:
            raise SheetError(f"no fenced prompt in section {spec['section']!r}")
        if common:
            text = text.replace("（共通プロンプト）", common)
    for d in spec.get("drop", []):
        text = text.replace(d, "")
    return text


# ================================================================ images

def _images_named(folder: Path, stem: str) -> list[Path]:
    return sorted((folder / (stem + e) for e in EXTS if (folder / (stem + e)).is_file()),
                  key=lambda q: q.stat().st_mtime, reverse=True)


def image_for(p: dict) -> tuple[Path | None, str]:
    """(path, source): 'uploaded' (inbox/textures/sheets/<name>.*) or 'preset' (the preset's image fallback)."""
    up = _images_named(SHEETS, p["name"])
    if up:
        return up[0], "uploaded"
    if p.get("image"):
        rel = Path(p["image"])
        if rel.is_absolute() or ".." in rel.parts:
            raise SheetError("bad preset image path")
        got = _images_named(INBOX_TEX / rel.parent, rel.name)
        if got:
            return got[0], "preset"
    return None, ""


def sniff(data: bytes) -> str | None:
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return ".png"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return ".webp"
    if data[:3] == b"\xff\xd8\xff":
        return ".jpg"
    return None


def save_upload(name: str, data: bytes) -> dict:
    p = preset(name)
    if len(data) > MAX_UPLOAD:
        raise SheetError(f"image too large (max {MAX_UPLOAD // 1024 // 1024}MB)")
    ext = sniff(data)
    if not ext:
        raise SheetError("not a png / webp / jpg image")
    SHEETS.mkdir(parents=True, exist_ok=True)
    for old in _images_named(SHEETS, p["name"]):
        old.unlink()
    dst = SHEETS / (p["name"] + ext)
    dst.write_bytes(data)
    im = _open(dst)
    return {"name": p["name"], "path": dst.relative_to(ROOT).as_posix(), "size": list(im.size), "bytes": len(data)}


def _open(path: Path):
    from PIL import Image
    im = Image.open(path)
    if im.size[0] * im.size[1] > MAX_PIXELS:
        raise SheetError("image has too many pixels")
    return im


def _rgb(path: Path):
    import numpy as np
    return np.ascontiguousarray(np.asarray(_open(path).convert("RGB")))


def panels(rgb) -> list[list[int]]:
    """Panel rectangles split by white gutter lines (recursive XY cut). Sheets without magenta: whole image."""
    import numpy as np
    a = rgb.astype(np.int16)
    h, w = a.shape[:2]
    mag = (a[..., 0] > 180) & (a[..., 1] < 110) & (a[..., 2] > 180)
    if mag.mean() < 0.05:
        return [[0, 0, w, h]]
    white = a.min(axis=2) > 225

    def runs(idx):
        out = []
        for i in idx:
            if out and i == out[-1][1]:
                out[-1][1] = i + 1
            else:
                out.append([i, i + 1])
        return out

    def cut(x0, y0, x1, y1, depth=0):
        sub = white[y0:y1, x0:x1]
        for axis in (1, 0):                       # 1: white rows -> horizontal cut, 0: white columns
            frac = sub.mean(axis=axis)
            n = len(frac)
            segs, prev = [], 0
            for r0, r1 in runs(np.nonzero(frac > 0.97)[0]):
                if r1 - r0 < 2:
                    continue
                if r0 - prev >= 60:
                    segs.append((prev, r0))
                prev = r1
            if n - prev >= 60:
                segs.append((prev, n))
            if depth < 6 and (len(segs) > 1 or (segs and segs[0] != (0, n))):
                out = []
                for s0, s1 in segs:
                    out += cut(x0, y0 + s0, x1, y0 + s1, depth + 1) if axis == 1 else \
                        cut(x0 + s0, y0, x0 + s1, y1, depth + 1)
                return out
        return [[int(x0), int(y0), int(x1), int(y1)]]

    got = [r for r in cut(0, 0, w, h) if mag[r[1]:r[3], r[0]:r[2]].mean() > 0.1]
    return got or [[0, 0, w, h]]


def _region(rgb, p, region):
    h, w = rgb.shape[:2]
    if region is None or region == "auto":
        ps = panels(rgb)
        k = p.get("panel")
        if isinstance(k, int) and len(ps) > 1 and 0 <= k < len(ps):
            return ps[k], f"panel:{k}"
        return [0, 0, w, h], "image"
    if isinstance(region, str) and region.startswith("panel:"):
        ps = panels(rgb)
        k = int(region[6:])
        if not 0 <= k < len(ps):
            raise SheetError(f"panel {k} not found ({len(ps)} panels)")
        return ps[k], region
    try:
        x0, y0, x1, y1 = (int(round(float(v))) for v in region)
    except (TypeError, ValueError):
        raise SheetError("region must be [x0, y0, x1, y1]")
    x0, x1 = sorted((max(0, min(w, x0)), max(0, min(w, x1))))
    y0, y1 = sorted((max(0, min(h, y0)), max(0, min(h, y1))))
    if x1 - x0 < 20 or y1 - y0 < 20:
        raise SheetError("region too small")
    return [x0, y0, x1, y1], "user"


# ================================================================ detection / tiles

def _tile(rgb, sl, m, p):
    import numpy as np
    from PIL import Image
    import import_item_sheet as sheet
    if p.get("tileable"):                         # rock blocks: same as import_chatgpt_textures on the inset crop
        import import_chatgpt_textures as ict
        k = int(p.get("inset", 4))
        t = rgb[sl][k:-k or None, k:-k or None]
        h, w = t.shape[:2]
        s = min(h, w)
        t = t[(h - s) // 2:(h - s) // 2 + s, (w - s) // 2:(w - s) // 2 + s]
        arr = ict.make_tileable(t.astype(np.float32))
        im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).resize((16, 16), Image.BOX)
        im = im.quantize(colors=p["colors"], method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
        return im.convert("RGBA")
    return sheet.to_tile(rgb, sl, m, p["colors"], p.get("bottom", False), p.get("opaque", False))


def _png_b64(im) -> str:
    buf = io.BytesIO()
    im.save(buf, "PNG")
    return base64.b64encode(buf.getvalue()).decode()


def _file_b64(path: Path) -> str | None:
    return base64.b64encode(path.read_bytes()).decode() if path.is_file() else None


def _target(p, bid) -> tuple[str, Path, Path]:
    rel = f"textures/{p['dest']}/{bid}.png"
    path = (TEX / p["dest"] / f"{bid}.png").resolve()
    lock = (LOCKS / p["dest"] / f"{bid}.png").resolve()
    if TEX.resolve() not in path.parents or LOCKS.resolve() not in lock.parents:
        raise SheetError("path outside textures")
    return rel, path, lock


def _find(p, image, region, merge):
    import numpy as np
    import import_item_sheet as sheet
    if image in (None, "", "auto"):
        path, src = image_for(p)
        if not path:
            raise SheetError(f"{p['name']}: no image uploaded (inbox/textures/sheets/{p['name']}.png)")
    else:
        path, src = Path(image), "file"
        if not path.is_file():
            raise SheetError(f"image not found: {image}")
    rgb = _rgb(path)
    box, how = _region(rgb, p, region)
    try:
        merge = int(p["merge"] if merge in (None, "") else merge)
    except (TypeError, ValueError):
        raise SheetError("merge must be an integer")
    if not 1 <= merge <= 20:
        raise SheetError("merge must be 1..20")
    x0, y0, x1, y1 = box
    sub = np.ascontiguousarray(rgb[y0:y1, x0:x1])
    found = sheet.order(sheet.blobs(sub, merge=merge, clean=True))
    return path, src, rgb, box, how, merge, sub, found


def _assign(p, n, ids):
    if ids is None:
        return [p["ids"][i] if i < len(p["ids"]) else "-" for i in range(n)]
    if isinstance(ids, str):
        ids = ids.split(",")
    ids = [str(i).strip() for i in ids]
    if len(ids) != n:
        raise SheetError(f"ids has {len(ids)} entries, {n} icons detected")
    seen = set()
    for bid in ids:
        if bid == "-":
            continue
        if not ID_RE.match(bid):
            raise SheetError(f"bad id {bid!r}")
        if bid in seen:
            raise SheetError(f"id used twice: {bid}")
        seen.add(bid)
        if bid not in p["ids"] and not _target(p, bid)[1].is_file():
            raise SheetError(f"{bid}: not in the preset and no existing texture/{p['dest']}/{bid}.png")
    return ids


def detect(name: str, image=None, region=None, merge=None, ids=None, b64: bool = True) -> dict:
    p = preset(name)
    path, src, rgb, box, how, merge, sub, found = _find(p, image, region, merge)
    assign = _assign(p, len(found), ids if ids is not None and len(ids) == len(found) else None)
    icons = []
    for i, ((sl, m), bid) in enumerate(zip(found, assign)):
        e = {"i": i, "bbox": [box[0] + sl[1].start, box[1] + sl[0].start, box[0] + sl[1].stop, box[1] + sl[0].stop],
             "id": bid}
        if bid != "-":
            rel, tpath, lock = _target(p, bid)
            e.update(exists=tpath.is_file(), locked=lock.is_file(), skip=bid in p["skip"], rel=rel)
        if b64:
            e["png"] = _png_b64(_tile(sub, sl, m, p))
        icons.append(e)
    n = len(found)
    return {"name": p["name"], "image": _rel(path), "source": src, "size": [rgb.shape[1], rgb.shape[0]],
            "region": box, "region_from": how, "merge": merge, "count": n, "expected": len(p["ids"]),
            "ok": n == len(p["ids"]), "ids": p["ids"], "skip": p["skip"], "dest": p["dest"], "icons": icons,
            "panels": panels(rgb)}


def _rel(path: Path) -> str:
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


# ================================================================ import / undo

def _same(path: Path, im) -> bool:
    import numpy as np
    if not path.is_file():
        return False
    from PIL import Image
    with Image.open(path) as old:
        a = np.asarray(old.convert("RGBA"))
    b = np.asarray(im.convert("RGBA"))
    return a.shape == b.shape and np.array_equal(a, b)


def import_sheet(name: str, image=None, region=None, merge=None, ids=None, dry_run: bool = False) -> dict:
    p = preset(name)
    path, src, rgb, box, how, merge, sub, found = _find(p, image, region, merge)
    if ids is None and len(found) != len(p["ids"]):
        raise SheetError(f"found {len(found)} icons, expected {len(p['ids'])} (change the region / merge or pass ids)")
    assign = _assign(p, len(found), ids)
    plan, writes = [], []
    for i, ((sl, m), bid) in enumerate(zip(found, assign)):
        e = {"i": i, "id": bid}
        if bid == "-":
            e["action"] = "ignored"
        elif bid in p["skip"]:
            e["action"] = "protected"
        else:
            rel, tpath, lock = _target(p, bid)
            tile = _tile(sub, sl, m, p)
            e.update(rel=rel, before=_file_b64(tpath), after=_png_b64(tile))
            if lock.is_file() and not p["lock"]:
                e["action"] = "locked"
            elif _same(tpath, tile) and (not p["lock"] or _same(lock, tile)):
                e["action"] = "unchanged"
            else:
                e["action"] = "write"
                writes.append((rel, tpath, lock, tile))
        plan.append(e)
    derived = {}
    if p["derive"] and writes:
        import derive_textures
        bases = {rel for rel, *_ in writes}
        derived = {rel: v for rel, v in derive_textures.targets().items() if v[1] in bases}
    summary = {"name": p["name"], "image": _rel(path), "region": box, "region_from": how, "merge": merge,
               "dry_run": dry_run, "plan": plan, "written": [w[0] for w in writes],
               "derived_candidates": sorted(derived), "lock": p["lock"]}
    if dry_run:
        return summary
    with WRITE_LOCK:
        stamp = time.strftime("%Y%m%d-%H%M%S")
        bdir = BACKUP / f"gui-import-{stamp}"
        k = 1
        while bdir.exists():
            k += 1
            bdir = BACKUP / f"gui-import-{stamp}-{k}"
        entries = []

        def keep(area: str, rel: str, fpath: Path):
            if any(x["area"] == area and x["rel"] == rel for x in entries):
                return
            existed = fpath.is_file()
            if existed:
                dst = bdir / area / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(fpath, dst)
            entries.append({"area": area, "rel": rel, "existed": existed})

        for rel, tpath, lock, tile in writes:
            keep("assets", rel, tpath)
            if p["lock"]:
                keep("locks", rel, lock)
        import texture_locks
        locked_derived, free_derived = [], []
        for rel in derived:
            if texture_locks.is_locked(rel):
                locked_derived.append(rel)
            else:
                free_derived.append(rel)
            keep("assets", rel, TEX.parent / rel)
            if texture_locks.is_locked(rel) and p["lock"]:
                keep("locks", rel, LOCKS.parent / rel)
        manifest = {"preset": p["name"], "image": _rel(path), "region": box, "merge": merge, "t": time.time(),
                    "entries": entries, "undone": False}
        bdir.mkdir(parents=True, exist_ok=True)
        (bdir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
        for rel, tpath, lock, tile in writes:
            tpath.parent.mkdir(parents=True, exist_ok=True)
            tile.save(tpath)
            if p["lock"]:
                lock.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(tpath, lock)
        dsum = {"written": [], "locked_skipped": [], "unchanged": 0}
        if derived:
            import derive_textures
            import numpy as np
            from PIL import Image
            if free_derived:
                r = derive_textures.run(only={Path(x).stem for x in free_derived}, quiet=True)
                dsum["written"] += r["written"]
                dsum["unchanged"] += r["unchanged"]
            for rel in locked_derived:           # e.g. plant *_glow locks: re-derive from the new art and re-lock
                if not p["lock"]:
                    dsum["locked_skipped"].append(Path(rel).stem)
                    continue
                recipe, base_rel = derived[rel]
                with Image.open(TEX.parent / base_rel) as b:
                    img = Image.fromarray(derive_textures.build(recipe, np.asarray(b.convert("RGBA")), Path(rel).stem), "RGBA")
                if _same(TEX.parent / rel, img):
                    dsum["unchanged"] += 1
                    continue
                img.save(TEX.parent / rel)
                shutil.copyfile(TEX.parent / rel, LOCKS.parent / rel)
                dsum["written"].append(Path(rel).stem)
        import check_textures
        chk = check_textures.check()
        summary.update(backup=_rel(bdir), derived=dsum,
                       check={"missing": len(chk["missing"]), "invalid_json": len(chk["invalid_json"]),
                              "missing_refs": [x["ref"] for x in chk["missing"]][:30]})
        manifest["summary"] = {"written": summary["written"], "derived": dsum["written"]}
        (bdir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    return summary


def _manifests() -> list[tuple[Path, dict]]:
    out = []
    for f in sorted(BACKUP.glob("gui-import-*/manifest.json")):
        try:
            out.append((f.parent, json.loads(f.read_text(encoding="utf-8"))))
        except (OSError, json.JSONDecodeError):
            pass
    return sorted(out, key=lambda x: x[1].get("t", 0))


def undo(dry_run: bool = False) -> dict:
    with WRITE_LOCK:
        live = [(d, m) for d, m in _manifests() if not m.get("undone")]
        if not live:
            raise SheetError("nothing to undo")
        bdir, man = live[-1]
        bases = {"assets": TEX.parent.resolve(), "locks": LOCKS.parent.resolve()}
        done = []
        for e in man["entries"]:
            base = bases.get(e["area"])
            dst = (base / e["rel"]).resolve() if base else None
            if not dst or base not in dst.parents or not e["rel"].startswith("textures/"):
                raise SheetError(f"bad manifest entry {e}")
            act = "restore" if e["existed"] else "delete"
            done.append({"area": e["area"], "rel": e["rel"], "action": act})
            if dry_run:
                continue
            if e["existed"]:
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(bdir / e["area"] / e["rel"], dst)
            elif dst.is_file():
                dst.unlink()
        if not dry_run:
            man["undone"] = time.time()
            (bdir / "manifest.json").write_text(json.dumps(man, ensure_ascii=False, indent=1), encoding="utf-8")
    return {"backup": _rel(bdir), "preset": man.get("preset"), "dry_run": dry_run, "files": done}


def status() -> list[dict]:
    data = load()
    last = {}
    for d, m in _manifests():
        last[m.get("preset")] = {"backup": _rel(d), "t": m.get("t"), "undone": bool(m.get("undone")),
                                 "written": len((m.get("summary") or {}).get("written", []))}
    out = []
    for s in data["sheets"]:
        p = preset(s["name"])
        try:
            prompt(p["name"])
            pr = True
        except (SheetError, OSError, KeyError) as e:
            pr = str(e)
        img, src = image_for(p)
        locked = sum((LOCKS / p["dest"] / f"{i}.png").is_file() for i in p["ids"])
        out.append({"name": p["name"], "title": p.get("title", ""), "kind": p["kind"], "dest": p["dest"],
                    "cols": p.get("cols"), "ids": p["ids"], "skip": p["skip"], "lock": p["lock"],
                    "derive": p["derive"], "merge": p["merge"], "colors": p["colors"], "panel": p.get("panel"),
                    "prompt_file": p["prompt"]["file"], "prompt_ok": pr is True, "prompt_error": None if pr is True else pr,
                    "image": _rel(img) if img else None, "image_source": src, "locked": locked,
                    "last_import": last.get(p["name"])})
    return out


# ================================================================ CLI

def _region_arg(s):
    if not s:
        return None
    if s.startswith("panel:") or s == "auto":
        return s
    return s.split(",")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list")
    sp = sub.add_parser("prompt"); sp.add_argument("name")
    sp = sub.add_parser("panels"); sp.add_argument("image")
    for cmd in ("detect", "import"):
        sp = sub.add_parser(cmd)
        sp.add_argument("image", help="sheet image path, or 'auto' (uploaded / preset image)")
        sp.add_argument("--preset", required=True)
        sp.add_argument("--region", help="x0,y0,x1,y1 | panel:N | auto")
        sp.add_argument("--merge", type=int)
        sp.add_argument("--ids", help="comma list for every detected icon in reading order, '-' = ignore")
        if cmd == "detect":
            sp.add_argument("--crops", help="also save the 16x16 tiles into this folder")
        else:
            sp.add_argument("--dry-run", action="store_true")
    sp = sub.add_parser("undo"); sp.add_argument("--dry-run", action="store_true")
    a = ap.parse_args(argv)
    try:
        if a.cmd == "list":
            res = status()
        elif a.cmd == "prompt":
            print(prompt(a.name))
            return 0
        elif a.cmd == "panels":
            rgb = _rgb(Path(a.image))
            res = {"size": [rgb.shape[1], rgb.shape[0]], "panels": panels(rgb)}
        elif a.cmd == "detect":
            ids = a.ids.split(",") if a.ids else None
            res = detect(a.preset, a.image, _region_arg(a.region), a.merge, ids, b64=bool(a.crops))
            if a.crops:
                out = Path(a.crops)
                out.mkdir(parents=True, exist_ok=True)
                for e in res["icons"]:
                    (out / f"{e['i']:02d}_{e['id'].replace('-', 'ignored')}.png").write_bytes(base64.b64decode(e.pop("png")))
        elif a.cmd == "import":
            res = import_sheet(a.preset, a.image, _region_arg(a.region), a.merge,
                               a.ids.split(",") if a.ids else None, a.dry_run)
            for e in res["plan"]:
                e.pop("before", None); e.pop("after", None)
        else:
            res = undo(a.dry_run)
    except SheetError as e:
        print(json.dumps({"error": str(e)}, ensure_ascii=False))
        return 1
    print(json.dumps(res, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
