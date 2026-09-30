"""Minecraft resource pack export.

    resourcepack/
    ├── pack.mcmeta
    ├── pack.png
    └── assets/<modid>/textures/<block|item|particle>/<name>.png
"""
from __future__ import annotations

import json
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

from .png import sanitize_name, save_png, unique_path

TEXTURE_FOLDERS = ("block", "item", "particle", "entity", "environment", "misc")


@dataclass
class PackTexture:
    name: str
    image: Image.Image
    folder: str = "block"
    settings: dict | None = None
    emission: Image.Image | None = None  # optional <name>_e.png emissive layer


def mcmeta(description: str, pack_format: int) -> dict:
    return {"pack": {"pack_format": int(pack_format), "description": description}}


def pack_icon(images: list[Image.Image], size: int = 64) -> Image.Image:
    """Pack icon: up to 4 textures in a 2x2 grid, scaled with nearest neighbour."""
    icon = Image.new("RGBA", (size, size), (13, 15, 18, 255))
    if not images:
        return icon
    cells = images[:4] if len(images) >= 4 else images[:1]
    n = 2 if len(cells) == 4 else 1
    cell = size // n
    for i, img in enumerate(cells):
        tile = img.convert("RGBA").resize((cell, cell), Image.NEAREST)
        icon.alpha_composite(tile, ((i % n) * cell, (i // n) * cell))
    return icon


def export_resource_pack(textures: list[PackTexture], out_dir: Path, mod_id: str,
                         pack_name: str = "texture_forge_pack", description: str = "",
                         pack_format: int = 15, as_zip: bool = False, overwrite: bool = True,
                         write_icon: bool = True) -> Path:
    """Write a resource pack folder (or .zip) and return its path.

    Writing into an existing pack folder only adds/replaces the files it
    generates; anything else already in the folder is left alone.  With
    ``overwrite=False`` existing texture files are kept and new ones get a
    numbered name instead.
    """
    mod_id = sanitize_name(mod_id).replace("/", "_").replace(".", "_")
    pack_name = sanitize_name(pack_name).replace("/", "_")
    if as_zip:
        with tempfile.TemporaryDirectory(prefix="texture_forge_") as tmp:
            staged = _write_pack(Path(tmp) / pack_name, textures, mod_id, description,
                                 pack_format, True, write_icon)
            zpath = Path(out_dir) / f"{pack_name}.zip"
            if zpath.exists() and not overwrite:
                zpath = unique_path(Path(out_dir), pack_name, ".zip")
            Path(out_dir).mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as zf:
                for f in sorted(staged.rglob("*")):
                    if f.is_file():
                        zf.write(f, f.relative_to(staged).as_posix())
            return zpath
    return _write_pack(Path(out_dir) / pack_name, textures, mod_id, description, pack_format,
                       overwrite, write_icon)


def _write_pack(root: Path, textures: list[PackTexture], mod_id: str, description: str,
                pack_format: int, overwrite: bool, write_icon: bool) -> Path:
    root.mkdir(parents=True, exist_ok=True)
    (root / "pack.mcmeta").write_text(
        json.dumps(mcmeta(description or f"{mod_id} textures", pack_format), indent=2),
        encoding="utf-8")
    if write_icon:
        pack_icon([t.image for t in textures]).save(root / "pack.png")
    for t in textures:
        folder = t.folder if t.folder in TEXTURE_FOLDERS else "block"
        tex_dir = root / "assets" / mod_id / "textures" / folder
        name = sanitize_name(t.name)
        dest = tex_dir / f"{name}.png" if overwrite else unique_path(tex_dir, name)
        save_png(t.image, dest, t.settings)
        if t.emission is not None:
            save_png(t.emission, dest.with_name(dest.stem + "_e.png"))
    return root
