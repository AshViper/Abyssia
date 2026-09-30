"""The structured-parameter contract between a language model and the generator.

The LLM never produces pixels.  It only translates intent into this small
JSON vocabulary, which :func:`params_to_settings` validates and maps onto
:class:`TextureSettings`.  Anything unknown or out of range is dropped or
clamped, so a confused model can never crash the generator.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from core.palette import ACCENT_PRESETS, THEMES
from core.settings import CATEGORIES, MATERIALS, SIZES, VARIANTS, TextureSettings

# numeric parameters the model may set (all 0..1)
NUMERIC_PARAMS: dict[str, str] = {
    "roughness": "surface roughness / bumpiness",
    "cracks": "amount of cracks",
    "noise": "fine grain / speckle",
    "layering": "horizontal strata",
    "moisture": "wetness (darker, glossy)",
    "contrast": "tonal contrast (0.5 neutral)",
    "brightness": "overall brightness (0.5 neutral, 0.2 very dark)",
    "saturation": "colour saturation (0.5 neutral)",
    "hue": "hue rotation (0.5 = none)",
    "temperature": "warm/cool shift (0.5 neutral)",
    "rock": "rock character",
    "crystal": "crystalline facets",
    "organic": "organic / curvy character",
    "metallic": "metallic glints",
    "density": "amount of the main feature (ore clumps, leaves, crystals)",
    "mineral": "exposed mineral grains / secondary accent",
    "cluster_size": "size of ore / mineral clumps",
    "glow": "emissive glow",
    "height": "plant / crystal height",
    "stem": "stem thickness",
    "leaf": "leaf amount",
    "branch": "branching",
    "facet": "crystal facet strength",
    "transparency": "translucency",
}

# alternative names a model (or a human) might use
ALIASES: dict[str, str] = {
    "base_palette": "palette", "theme": "palette", "color_theme": "palette", "colour_theme": "palette",
    "accent_palette": "accent", "accent_color": "accent", "ore_color": "accent",
    "mineral_density": "mineral", "mineral_amount": "mineral", "accent_amount": "mineral",
    "ore_density": "density", "feature_density": "density",
    "crack": "cracks", "crack_amount": "cracks",
    "rough": "roughness", "grain": "noise", "wetness": "moisture",
    "sat": "saturation", "light": "brightness",
    "shape": "variant", "type": "variant", "crystal_shape": "variant", "subtype": "variant",
    "resolution": "size", "texture_size": "size", "texture_name": "name",
    "cluster": "cluster_size", "glow_amount": "glow", "emission": "glow",
    "colors": "color_limit", "color_count": "color_limit",
}

CATEGORY_SYNONYMS: dict[str, str] = {
    "rock": "terrain", "stone": "terrain", "ground": "terrain", "mud": "terrain", "sediment": "terrain",
    "vent": "thermal", "hydrothermal": "thermal", "smoker": "thermal",
    "seaweed": "kelp", "algae": "kelp", "grass": "plant", "flora": "plant", "flower": "plant",
    "gem": "crystal", "geode": "crystal", "minerals": "mineral", "coral": "organic",
    "sponge": "organic", "brick": "decoration", "bricks": "decoration", "tile": "decoration",
    "metals": "metal", "ores": "ore",
}


def _norm(v: Any) -> str:
    return str(v).strip().lower().replace(" ", "_").replace("-", "_")


def _unit(v: Any) -> float | None:
    try:
        f = float(v)
    except (TypeError, ValueError):
        if isinstance(v, str):
            words = {"none": 0.0, "very_low": 0.1, "low": 0.25, "medium": 0.5, "mid": 0.5,
                     "high": 0.75, "very_high": 0.9, "max": 1.0}
            return words.get(_norm(v))
        return None
    if f > 1.0:
        f = f / 100.0  # percentages
    return max(0.0, min(1.0, f))


def json_schema() -> dict:
    """JSON schema of the parameters (also usable as Ollama's ``format``)."""
    props: dict[str, Any] = {
        "name": {"type": "string"},
        "category": {"type": "string", "enum": list(CATEGORIES)},
        "material": {"type": "string", "enum": list(MATERIALS)},
        "variant": {"type": "string"},
        "palette": {"type": "string", "enum": list(THEMES)},
        "accent": {"type": "string", "enum": list(ACCENT_PRESETS)},
        "size": {"type": "integer", "enum": list(SIZES)},
    }
    for k in NUMERIC_PARAMS:
        props[k] = {"type": "number", "minimum": 0, "maximum": 1}
    return {"type": "object", "properties": props, "required": ["category"]}


@dataclass
class MappedParams:
    settings: TextureSettings
    applied: dict[str, Any] = field(default_factory=dict)
    warnings: list[str] = field(default_factory=list)


def params_to_settings(params: dict, base: TextureSettings | None = None) -> MappedParams:
    """Validate model output and apply it on top of ``base``."""
    s = (base or TextureSettings()).copy()
    applied: dict[str, Any] = {}
    warnings: list[str] = []
    flat = {}
    for k, v in (params or {}).items():
        key = ALIASES.get(_norm(k), _norm(k))
        flat[key] = v

    if "category" in flat:
        cat = _norm(flat["category"])
        cat = CATEGORY_SYNONYMS.get(cat, cat)
        if cat in CATEGORIES:
            if cat != s.category:
                s.category = cat
                from core.settings import DEFAULT_MATERIAL
                s.material = DEFAULT_MATERIAL[cat]
                s.variant = next(iter(VARIANTS.get(cat, {"auto": ""})))
            applied["category"] = cat
        else:
            warnings.append(f"unknown category '{flat['category']}'")
    if "material" in flat:
        m = _norm(flat["material"])
        if m in MATERIALS:
            s.material = m
            applied["material"] = m
        else:
            warnings.append(f"unknown material '{flat['material']}'")
    if "variant" in flat:
        v = _norm(flat["variant"])
        variants = VARIANTS.get(s.category, {})
        match = v if v in variants else next((k for k in variants if v in k or k in v), None)
        if match:
            s.variant = match
            applied["variant"] = match
        else:
            warnings.append(f"variant '{flat['variant']}' not available for {s.category}")
    if "palette" in flat:
        p = _norm(flat["palette"])
        if p in THEMES:
            s.palette = p
            applied["palette"] = p
        else:
            match = next((t for t in THEMES if p in t or t in p), None)
            if match:
                s.palette = match
                applied["palette"] = match
            else:
                warnings.append(f"unknown palette '{flat['palette']}'")
    if "accent" in flat:
        a = _norm(flat["accent"])
        if a in ("", "none", "default"):
            s.accent = ""
        elif a in ACCENT_PRESETS:
            s.accent = a
            applied["accent"] = a
        else:
            match = next((t for t in ACCENT_PRESETS if a in t or t in a), None)
            if match:
                s.accent = match
                applied["accent"] = match
            else:
                warnings.append(f"unknown accent '{flat['accent']}'")
    if "size" in flat:
        try:
            size = int(float(flat["size"]))
            s.size = min(SIZES, key=lambda x: abs(x - size))
            applied["size"] = s.size
        except (TypeError, ValueError):
            warnings.append(f"bad size '{flat['size']}'")
    if "name" in flat and isinstance(flat["name"], str) and flat["name"].strip():
        from export.png import sanitize_name
        s.name = sanitize_name(flat["name"])
        applied["name"] = s.name
    if "color_limit" in flat:
        try:
            s.color_limit = int(flat["color_limit"])
            applied["color_limit"] = s.color_limit
        except (TypeError, ValueError):
            pass
    for k in NUMERIC_PARAMS:
        if k in flat:
            val = _unit(flat[k])
            if val is None:
                warnings.append(f"'{k}' is not a number")
                continue
            setattr(s, k, val)
            applied[k] = round(val, 3)
    ignored = set(flat) - set(applied) - {"category", "material", "variant", "palette", "accent",
                                          "size", "name", "color_limit"} - set(NUMERIC_PARAMS)
    for k in sorted(ignored):
        warnings.append(f"ignored '{k}'")
    return MappedParams(s.clamp(), applied, warnings)


def describe_vocabulary() -> str:
    """Human/LLM readable vocabulary used in the system prompt."""
    lines = ["categories: " + ", ".join(CATEGORIES)]
    for cat, vs in VARIANTS.items():
        lines.append(f"  variants for {cat}: " + ", ".join(vs))
    lines.append("materials: " + ", ".join(MATERIALS))
    lines.append("palettes: " + ", ".join(f"{k} ({t.description})" for k, t in THEMES.items()))
    lines.append("accents: " + ", ".join(ACCENT_PRESETS))
    lines.append("sizes: " + ", ".join(str(x) for x in SIZES))
    lines.append("numeric parameters (0..1):")
    for k, d in NUMERIC_PARAMS.items():
        lines.append(f"  {k}: {d}")
    return "\n".join(lines)
