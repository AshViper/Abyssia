"""PNG export with Minecraft-friendly naming."""
from __future__ import annotations

import json
import re
from pathlib import Path

from PIL import Image
from PIL.PngImagePlugin import PngInfo

_NAME = re.compile(r"[^a-z0-9_.\-/]+")


def sanitize_name(name: str) -> str:
    """Resource locations only allow ``[a-z0-9_.-/]``."""
    s = _NAME.sub("_", name.strip().lower().replace(" ", "_"))
    s = re.sub(r"_{2,}", "_", s).strip("_./")
    return s or "texture"


def numbered_name(name: str, index: int) -> str:
    """``abyssal_rock``, ``abyssal_rock_01``, ``abyssal_rock_02`` ..."""
    return name if index == 0 else f"{name}_{index:02d}"


def unique_path(folder: Path, name: str, ext: str = ".png") -> Path:
    """First free path among ``name.png``, ``name_01.png``, ``name_02.png`` ..."""
    folder = Path(folder)
    i = 0
    while True:
        p = folder / f"{numbered_name(name, i)}{ext}"
        if not p.exists():
            return p
        i += 1


def to_png_image(image: Image.Image) -> Image.Image:
    """Ensure a pixel-perfect RGBA image (no resampling ever happens here)."""
    return image if image.mode == "RGBA" else image.convert("RGBA")


def save_png(image: Image.Image, path: Path, settings: dict | None = None) -> Path:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    info = PngInfo()
    info.add_text("Software", "Texture Forge")
    if settings is not None:
        info.add_text("texture_forge", json.dumps(settings, separators=(",", ":")))
    to_png_image(image).save(path, "PNG", optimize=True, pnginfo=info)
    return path


def export_png(image: Image.Image, folder: Path, name: str, settings: dict | None = None,
               overwrite: bool = False) -> Path:
    name = sanitize_name(name)
    path = Path(folder) / f"{name}.png" if overwrite else unique_path(Path(folder), name)
    return save_png(image, path, settings)


def export_series(images: list[Image.Image], folder: Path, name: str,
                  settings: list[dict] | None = None, overwrite: bool = False) -> list[Path]:
    """Save variations as ``name.png``, ``name_01.png`` ... (numbering continues if taken)."""
    name = sanitize_name(name)
    folder = Path(folder)
    out = []
    index = 0
    for i, img in enumerate(images):
        if overwrite:
            path = folder / f"{numbered_name(name, i)}.png"
        else:
            while (folder / f"{numbered_name(name, index)}.png").exists():
                index += 1
            path = folder / f"{numbered_name(name, index)}.png"
            index += 1
        out.append(save_png(img, path, settings[i] if settings else None))
    return out


def read_embedded_settings(path: Path) -> dict | None:
    """Settings embedded by :func:`save_png`, if any."""
    try:
        with Image.open(path) as im:
            raw = im.info.get("texture_forge")
        return json.loads(raw) if raw else None
    except (OSError, ValueError):
        return None
