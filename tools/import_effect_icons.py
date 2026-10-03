"""Import a ChatGPT sheet of mob-effect icons (white background, one row/grid in reading order)
into assets/abyssia/textures/mob_effect/<id>.png as 18x18 (16x16 sprite centred, vanilla size).

  python tools/import_effect_icons.py inbox/textures/sheets/EN01-effects.png deep_sight abyssal_current ... [--dry-run]
Tiles come from import_item_sheet (same detection as the item sheets). Locks go to tools/texture_locks/mob_effect/.
"""
import argparse, shutil, sys
from pathlib import Path
import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import import_item_sheet as sheet  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/abyssia/textures/mob_effect"
LOCKS = ROOT / "tools/texture_locks/mob_effect"


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument("ids", nargs="+")
    ap.add_argument("--colors", type=int, default=14)
    ap.add_argument("--merge", type=int, default=1)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args(argv)
    rgb = np.ascontiguousarray(np.asarray(Image.open(a.image).convert("RGB")))
    found = sheet.order(sheet.blobs(rgb, merge=a.merge, clean=True))
    if len(found) != len(a.ids):
        sys.exit(f"found {len(found)} icons, expected {len(a.ids)}")
    OUT.mkdir(parents=True, exist_ok=True)
    LOCKS.mkdir(parents=True, exist_ok=True)
    for bid, (sl, m) in zip(a.ids, found):
        tile = sheet.to_tile(rgb, sl, m, a.colors, False, False)
        canvas = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
        canvas.paste(tile, (1, 1))
        dst = OUT / f"{bid}.png"
        print(("would write " if a.dry_run else "wrote ") + str(dst.relative_to(ROOT)))
        if not a.dry_run:
            canvas.save(dst)
            shutil.copy2(dst, LOCKS / dst.name)


if __name__ == "__main__":
    main()
