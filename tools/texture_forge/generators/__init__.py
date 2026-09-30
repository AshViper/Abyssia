"""Category generators.  ``get_generator(category)`` returns an instance."""
from __future__ import annotations

import importlib

from .base import BaseGenerator

_REGISTRY: dict[str, tuple[str, str]] = {
    "terrain": ("terrain", "TerrainGenerator"),
    "ore": ("ore", "OreGenerator"),
    "mineral": ("mineral", "MineralGenerator"),
    "plant": ("plant", "PlantGenerator"),
    "kelp": ("kelp", "KelpGenerator"),
    "crystal": ("crystal", "CrystalGenerator"),
    "thermal": ("thermal", "ThermalGenerator"),
    "decoration": ("decoration", "DecorationGenerator"),
    "organic": ("organic", "OrganicGenerator"),
    "metal": ("metal", "MetalGenerator"),
    "wood": ("wood", "WoodGenerator"),
    "item": ("item", "ItemGenerator"),
    "dripstone": ("dripstone", "DripstoneGenerator"),
}


def register(category: str, module: str, cls: str) -> None:
    """Plug in an extra generator; ``module`` is a module in this package or a dotted path."""
    _REGISTRY[category] = (module, cls)


def get_generator(category: str) -> BaseGenerator:
    module, cls = _REGISTRY.get(category, _REGISTRY["terrain"])
    mod = importlib.import_module(module if "." in module else f"{__name__}.{module}")
    return getattr(mod, cls)()


def categories() -> list[str]:
    return list(_REGISTRY)
