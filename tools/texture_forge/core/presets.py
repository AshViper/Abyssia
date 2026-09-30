"""Preset files: single-texture presets and batch (pack) presets.

A texture preset is a partial :class:`TextureSettings` dict, e.g.::

    {"category": "terrain", "size": 16, "palette": "abyss",
     "roughness": 0.82, "contrast": 0.65, "cracks": 0.42, "seed": 123456}

A batch preset lists several textures, each referencing a texture preset
and/or overriding fields::

    {"name": "Deep Sea Terrain Pack",
     "textures": [{"name": "abyssal_rock", "preset": "abyssal_rock"},
                  {"name": "deep_sediment", "category": "terrain", "material": "sediment"}]}
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

from .config import BATCH_PRESET_DIR, PRESET_DIR
from .settings import TextureSettings

_ALWAYS = ("name", "category", "material", "variant", "size", "palette", "seed")


def slug(name: str) -> str:
    s = re.sub(r"[^a-z0-9_]+", "_", name.strip().lower()).strip("_")
    return s or "preset"


def list_presets(folder: Path = PRESET_DIR) -> list[Path]:
    if not folder.is_dir():
        return []
    return sorted(p for p in folder.glob("*.json") if p.is_file())


def preset_label(path: Path) -> str:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(data.get("label"), str):
            return data["label"]
    except (OSError, ValueError):
        pass
    return path.stem.replace("_", " ").title()


def resolve_preset(name_or_path: str | Path, folder: Path = PRESET_DIR) -> Path:
    p = Path(name_or_path)
    if p.suffix == ".json" and p.exists():
        return p
    cand = folder / f"{slug(str(name_or_path))}.json"
    if cand.exists():
        return cand
    raise FileNotFoundError(f"preset not found: {name_or_path}")


def load_preset(name_or_path: str | Path, base: TextureSettings | None = None,
                folder: Path = PRESET_DIR) -> TextureSettings:
    path = resolve_preset(name_or_path, folder)
    data = json.loads(path.read_text(encoding="utf-8"))
    data = {k: v for k, v in data.items() if k not in ("label", "description")}
    return TextureSettings.from_dict(data, base or TextureSettings())


def compact_dict(s: TextureSettings) -> dict:
    """Settings as a preset dict: only values that differ from defaults (+ identity)."""
    default = TextureSettings().to_dict()
    full = s.to_dict()
    out = {}
    for k, v in full.items():
        if k in _ALWAYS or v != default.get(k):
            out[k] = round(v, 3) if isinstance(v, float) else v
    return out


def save_preset(s: TextureSettings, name: str, folder: Path = PRESET_DIR, label: str | None = None) -> Path:
    folder.mkdir(parents=True, exist_ok=True)
    data = compact_dict(s)
    if label:
        data = {"label": label, **data}
    path = folder / f"{slug(name)}.json"
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    return path


# ------------------------------------------------------------ batch presets


@dataclass
class BatchPreset:
    name: str
    textures: list[dict] = field(default_factory=list)
    description: str = ""
    path: Path | None = None

    def jobs(self, base: TextureSettings | None = None, folder: Path = PRESET_DIR
             ) -> list[TextureSettings]:
        out = []
        for i, item in enumerate(self.textures):
            item = dict(item)
            s = base.copy() if base is not None else TextureSettings()
            ref = item.pop("preset", None)
            if ref:
                s = load_preset(ref, s, folder)
            s = TextureSettings.from_dict(item, s)
            if "name" not in item and ref:
                s.name = slug(str(ref))
            out.append(s)
        return out


def list_batch_presets(folder: Path = BATCH_PRESET_DIR) -> list[Path]:
    return list_presets(folder)


def load_batch_preset(name_or_path: str | Path, folder: Path = BATCH_PRESET_DIR) -> BatchPreset:
    path = resolve_preset(name_or_path, folder)
    data = json.loads(path.read_text(encoding="utf-8"))
    return BatchPreset(data.get("name", path.stem), list(data.get("textures", [])),
                       data.get("description", ""), path)
