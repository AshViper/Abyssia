"""The single settings object that drives generation.

Every slider value is a float in 0..1.  Colour adjustments use 0.5 as
"neutral" (so a hue of 0.5 means no rotation).  Settings round-trip
through JSON, which is what presets, history and the AI bridge use.
"""
from __future__ import annotations

import copy
import dataclasses
from dataclasses import dataclass, field
from typing import Any

CATEGORIES: dict[str, str] = {
    "terrain": "Terrain",
    "ore": "Ore",
    "mineral": "Mineral",
    "plant": "Plant",
    "kelp": "Kelp",
    "crystal": "Crystal",
    "thermal": "Thermal",
    "decoration": "Decoration",
    "organic": "Organic",
    "metal": "Metal",
    "wood": "Wood",
    "item": "Item",
    "dripstone": "Dripstone",
}

MATERIALS: dict[str, str] = {
    "rock": "Rock",
    "mud": "Mud",
    "sediment": "Sediment",
    "metal": "Metal",
    "crystal": "Crystal",
    "organic": "Organic",
    "plant": "Plant",
    "mineral": "Mineral",
    "thermal": "Thermal",
    "wood": "Wood",
    "slate": "Slate (deepslate-like)",
}

STYLES: dict[str, str] = {
    "minecraft": "Minecraft Pixel Art",
    "programmer": "Programmer Art (flat)",
    "detailed": "Detailed Pixel Art",
}

SIZES = (8, 16, 32, 64, 128, 256)
COLOR_LIMITS = (8, 12, 16, 24, 32, 0)  # 0 = unlimited

VARIANTS: dict[str, dict[str, str]] = {
    "terrain": {"auto": "Auto (from material)", "rough": "Rough Rock", "layered": "Layered Strata",
                "cobbled": "Cobbled", "smooth": "Smooth", "strata": "Slate Strata (deepslate-like)"},
    "ore": {"cluster": "Ore Cluster", "vein": "Ore Vein", "scattered": "Scattered Specks"},
    "mineral": {"crystalline": "Crystalline", "banded": "Banded", "granular": "Granular (raw)",
                "crust": "Crust"},
    "plant": {"fern": "Fern", "grass": "Grass Tuft", "bush": "Bush", "vine": "Hanging Vine",
              "bulb": "Bulb Stalk"},
    "kelp": {"deep": "Deep Kelp", "giant": "Giant Kelp", "abyssal": "Abyssal Kelp",
             "thermal": "Thermal Kelp", "seagrass": "Seagrass / Strands"},
    "crystal": {"cluster": "Cluster", "single": "Single Prism", "shard": "Shards",
                "bud": "Bud", "block": "Crystal Block"},
    "thermal": {"thermal_rock": "Thermal Rock", "black_smoker": "Black Smoker Rock",
                "white_smoker": "White Smoker Rock", "sulfur_rock": "Sulfur Rock",
                "mineral_crust": "Mineral Crust", "thermal_crystal": "Thermal Crystal"},
    "decoration": {"bricks": "Bricks", "tiles": "Tiles", "polished": "Polished",
                   "chiseled": "Chiseled", "pillar": "Pillar Side", "lamp": "Lamp"},
    "organic": {"coral": "Coral Block", "sponge": "Sponge", "vein": "Vein Mat", "moss": "Moss"},
    "metal": {"plate": "Plated", "block": "Solid Block", "grate": "Grate", "raw": "Raw Chunk"},
    "wood": {"planks": "Planks", "log": "Log Side (bark)", "log_top": "Log End (rings)",
             "stripped_log": "Stripped Log Side", "stripped_log_top": "Stripped Log End",
             "door_top": "Door (top half)", "door_bottom": "Door (bottom half)", "trapdoor": "Trapdoor",
             "door_item": "Door (item icon)"},
    "item": {"ingot": "Ingot", "raw": "Raw Ore Lump", "dust": "Dust Pile", "nugget": "Nugget"},
    "dripstone": {"tip": "Tip", "tip_merge": "Tip (merged)", "frustum": "Frustum", "middle": "Middle",
                  "base": "Base"},
}

# Parts for categories that come in pieces (kelp stalk vs. top etc.)
PARTS: dict[str, dict[str, str]] = {
    "kelp": {"stalk": "Stalk (tiles vertically)", "top": "Top"},
    "plant": {"single": "Single Texture"},
    "dripstone": {"down": "Hanging (stalactite)", "up": "Standing (stalagmite)"},
}

DEFAULT_MATERIAL: dict[str, str] = {
    "terrain": "rock", "ore": "rock", "mineral": "mineral", "plant": "plant", "kelp": "plant",
    "crystal": "crystal", "thermal": "thermal", "decoration": "rock", "organic": "organic",
    "metal": "metal", "wood": "wood", "item": "metal", "dripstone": "rock",
}

SPRITE_CATEGORIES = {"plant", "kelp", "item", "dripstone"}  # always transparent sprites
# wood variants that have holes / a silhouette instead of filling the block
WOOD_SPRITE_VARIANTS = {"door_top", "door_bottom", "trapdoor", "door_item"}

LAYER_ORDER: tuple[str, ...] = (
    "base", "material", "large_detail", "medium_detail", "small_detail",
    "cracks", "highlights", "shadows", "accent",
)
LAYER_LABELS: dict[str, str] = {
    "base": "Base", "material": "Material", "large_detail": "Large Detail",
    "medium_detail": "Medium Detail", "small_detail": "Small Detail", "cracks": "Cracks",
    "highlights": "Highlights", "shadows": "Shadows", "accent": "Accent",
}

FILTER_STEPS: dict[str, str] = {
    "cluster_cleanup": "Pixel Cluster Cleanup",
    "noise_reduction": "Noise Reduction",
    "edge_cleanup": "Edge Cleanup",
    "palette_limit": "Palette Limiting",
    "quantize": "Color Quantization",
    "contrast_normalize": "Contrast Normalization",
}

# Sliders shown in the GUI: (field, label, group)
SLIDERS: tuple[tuple[str, str, str], ...] = (
    ("hue", "Hue", "Color"),
    ("saturation", "Saturation", "Color"),
    ("brightness", "Brightness", "Color"),
    ("contrast", "Contrast", "Color"),
    ("temperature", "Temperature", "Color"),
    ("tint", "Tint", "Color"),
    ("roughness", "Roughness", "Detail"),
    ("noise", "Noise", "Detail"),
    ("cracks", "Cracks", "Detail"),
    ("layering", "Layering", "Detail"),
    ("moisture", "Moisture", "Detail"),
    ("rock", "Rock", "Material"),
    ("crystal", "Crystal", "Material"),
    ("organic", "Organic", "Material"),
    ("metallic", "Metallic", "Material"),
    ("density", "Density", "Feature"),
    ("mineral", "Mineral", "Feature"),
    ("cluster_size", "Cluster Size", "Feature"),
    ("glow", "Glow", "Feature"),
    ("alteration", "Alteration", "Feature"),
    ("stem", "Stem", "Feature"),
    ("leaf", "Leaf", "Feature"),
    ("branch", "Branch", "Feature"),
    ("height", "Height", "Feature"),
    ("facet", "Facet", "Feature"),
    ("transparency", "Transparency", "Feature"),
)

# Which feature sliders matter for which category (GUI hides the rest).
CATEGORY_FEATURES: dict[str, tuple[str, ...]] = {
    "terrain": ("mineral", "cluster_size", "glow", "alteration"),
    "ore": ("density", "cluster_size", "mineral", "glow", "alteration"),
    "mineral": ("density", "cluster_size", "mineral", "glow", "facet"),
    "plant": ("density", "stem", "leaf", "branch", "height", "glow"),
    "kelp": ("density", "stem", "leaf", "height", "glow"),
    "crystal": ("density", "height", "facet", "transparency", "glow"),
    "thermal": ("density", "mineral", "cluster_size", "glow"),
    "decoration": ("density", "mineral", "glow"),
    "organic": ("density", "cluster_size", "glow"),
    "metal": ("density", "mineral", "glow"),
    "wood": ("density", "mineral", "glow"),
    "item": ("density", "mineral", "height"),
    "dripstone": ("density", "mineral", "glow"),
}


@dataclass
class TextureSettings:
    # identity ---------------------------------------------------------
    name: str = "abyssal_rock"
    category: str = "terrain"
    material: str = "rock"
    variant: str = "auto"
    part: str = "stalk"
    style: str = "minecraft"
    size: int = 16
    seed: int = 123456
    # palette ----------------------------------------------------------
    palette: str = "abyss"
    accent: str = ""            # "" = theme default accent
    base_color: str = ""        # optional hex override for the base ramp
    secondary_color: str = ""   # optional hex override for the secondary ramp (bark, stems...)
    color_limit: int = 16       # 0 = unlimited
    levels: int = 0             # base ramp length, 0 = from source analysis
    hue_shift: float = 0.5      # pixel-art hue shifting along the ramp
    # colour adjustment (0.5 = neutral) --------------------------------
    hue: float = 0.5
    saturation: float = 0.5
    brightness: float = 0.5
    contrast: float = 0.5
    temperature: float = 0.5
    tint: float = 0.5
    # detail -----------------------------------------------------------
    roughness: float = 0.6
    noise: float = 0.4
    cracks: float = 0.3
    layering: float = 0.2
    moisture: float = 0.2
    # material mix -----------------------------------------------------
    rock: float = 0.7
    crystal: float = 0.1
    organic: float = 0.1
    metallic: float = 0.05
    # category features ------------------------------------------------
    density: float = 0.5
    mineral: float = 0.15
    cluster_size: float = 0.5
    glow: float = 0.0
    alteration: float = 0.0     # terrain / ore: weathered patches in the secondary ramp (heat, films, crusts)
    stem: float = 0.5
    leaf: float = 0.5
    branch: float = 0.3
    height: float = 0.7
    facet: float = 0.6
    transparency: float = 0.0
    # pixel-art control ------------------------------------------------
    source_influence: float = 0.7
    alpha_threshold: int = 128
    tileable: bool = True
    # Opt-in: use the reference's own pixel layout as the base and recolour it (terrain / ore), instead of
    # generating a new pattern from its statistics.  The result is then a derivative of the reference image.
    recolor_source: bool = False
    # With recolor_source: the reference's host colours (comma separated hex).  Pixels in these colours take the
    # base ramp, every other pixel (ore, inclusions) the accent ramp.  Empty = split by saturation.
    source_host: str = ""
    # Terrain: colour of crying-obsidian-like crack veins (dark saturated fringe, bright core; the core glows with
    # the glow slider).  Empty = classic dark cracks with a lit lip.
    crack_color: str = ""
    layers: dict[str, bool] = field(default_factory=lambda: {k: True for k in LAYER_ORDER})
    filters: dict[str, bool] = field(default_factory=lambda: {k: True for k in FILTER_STEPS})

    # -------------------------------------------------------------- helpers
    def copy(self, **changes: Any) -> "TextureSettings":
        s = copy.deepcopy(self)
        for k, v in changes.items():
            setattr(s, k, v)
        return s

    def layer_enabled(self, name: str) -> bool:
        return self.layers.get(name, True)

    def filter_enabled(self, name: str) -> bool:
        return self.filters.get(name, True)

    @property
    def is_sprite(self) -> bool:
        if self.category in SPRITE_CATEGORIES:
            return True
        if self.category == "crystal" and self.variant != "block":
            return True
        if self.category == "wood" and self.variant in WOOD_SPRITE_VARIANTS:
            return True
        return False

    def clamp(self) -> "TextureSettings":
        for f in dataclasses.fields(self):
            v = getattr(self, f.name)
            if f.type in ("float", float) and isinstance(v, (int, float)):
                setattr(self, f.name, float(min(1.0, max(0.0, v))))
        if self.size not in SIZES:
            self.size = min(SIZES, key=lambda s: abs(s - int(self.size)))
        if self.category not in CATEGORIES:
            self.category = "terrain"
        if self.material not in MATERIALS:
            self.material = DEFAULT_MATERIAL[self.category]
        variants = VARIANTS.get(self.category, {})
        if variants and self.variant not in variants:
            self.variant = next(iter(variants))
        if self.style not in STYLES:
            self.style = "minecraft"
        self.alpha_threshold = int(min(255, max(1, self.alpha_threshold)))
        self.color_limit = int(max(0, self.color_limit))
        self.levels = int(min(12, max(0, self.levels)))
        self.seed = int(self.seed) & 0x7FFFFFFF
        for k in LAYER_ORDER:
            self.layers.setdefault(k, True)
        for k in FILTER_STEPS:
            self.filters.setdefault(k, True)
        return self

    def to_dict(self) -> dict[str, Any]:
        return dataclasses.asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any], base: "TextureSettings | None" = None) -> "TextureSettings":
        """Build settings from a (possibly partial) dict; unknown keys are ignored."""
        s = copy.deepcopy(base) if base is not None else cls()
        names = {f.name: f for f in dataclasses.fields(cls)}
        for key, value in data.items():
            if key not in names:
                continue
            current = getattr(s, key)
            if isinstance(current, dict) and isinstance(value, dict):
                merged = dict(current)
                merged.update({k: bool(v) for k, v in value.items()})
                setattr(s, key, merged)
            elif isinstance(current, bool):
                setattr(s, key, bool(value))
            elif isinstance(current, int) and not isinstance(current, bool):
                try:
                    setattr(s, key, int(value))
                except (TypeError, ValueError):
                    pass
            elif isinstance(current, float):
                try:
                    setattr(s, key, float(value))
                except (TypeError, ValueError):
                    pass
            else:
                setattr(s, key, value)
        if "category" in data and "material" not in data:
            s.material = DEFAULT_MATERIAL.get(s.category, s.material)
        if "category" in data and "variant" not in data:
            s.variant = next(iter(VARIANTS.get(s.category, {"auto": ""})))
        return s.clamp()

    def diff(self, other: "TextureSettings") -> dict[str, tuple[Any, Any]]:
        a, b = self.to_dict(), other.to_dict()
        return {k: (a[k], b[k]) for k in a if a[k] != b[k]}
