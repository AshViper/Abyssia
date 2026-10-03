"""GB01 guide book art: ChatGPT sheet (4x3, white bg) -> 11 16x16 RGBA icons (reuses agentflow/import_item_sheet detection).

Order: abyss_guide_book (item), then gui/guide/icons/{intro,abyss,survival,resources,creatures,diving,base,industry,
waypoints,encyclopedia}.  Also copies them into tools/texture_locks so regeneration keeps them.
Run:  python tools/guide_icons.py [--sheet PATH] [--merge N] [--colors N] [--dry-run]
"""
import argparse, shutil, sys
from pathlib import Path
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import import_item_sheet as sheet  # noqa: E402

TEX = ROOT / "src/main/resources/assets/abyssia/textures"
LOCKS = ROOT / "tools/texture_locks/assets/textures"
DESTS = ["item/abyss_guide_book"] + [f"gui/guide/icons/{n}" for n in
         "intro abyss survival resources creatures diving base industry waypoints encyclopedia".split()]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", default=str(ROOT / "inbox/textures/sheets/GB01-icons.png"))
    ap.add_argument("--merge", type=int, default=6)
    ap.add_argument("--colors", type=int, default=14)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    rgb = np.ascontiguousarray(np.asarray(Image.open(a.sheet).convert("RGB")))
    found = sheet.order(sheet.blobs(rgb, merge=a.merge, clean=True))
    if len(found) != len(DESTS):
        sys.exit(f"found {len(found)} icons, expected {len(DESTS)} (try --merge)")
    for (sl, m), rel in zip(found, DESTS):
        tile = sheet.to_tile(rgb, sl, m, a.colors)
        print(rel, "bbox", (sl[1].start, sl[0].start, sl[1].stop, sl[0].stop))
        if a.dry_run:
            continue
        for base in (TEX, LOCKS):
            p = base / (rel + ".png")
            p.parent.mkdir(parents=True, exist_ok=True)
            tile.save(p)


if __name__ == "__main__":
    main()
