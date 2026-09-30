"""Natural language → generation parameters.

The language model interprets *what to make*; the pixel generator decides
*how it looks*.  When no model is configured or reachable, a keyword parser
(Japanese + English) produces a reasonable parameter set instead, so the
feature always works offline.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any

from core.settings import TextureSettings

from .ollama import LLMClient, LLMError
from .schema import MappedParams, describe_vocabulary, json_schema, params_to_settings

SYSTEM_PROMPT = """You convert a texture request for a Minecraft deep-sea mod into generation parameters.
You do NOT draw anything. Reply with ONE JSON object only, no prose.

Vocabulary:
{vocab}

Rules:
- Always set "category". Set "palette" (overall colour theme) and "accent" (ore/mineral colour) when colours are mentioned.
- Numeric values are 0..1. brightness/contrast/saturation/hue/temperature use 0.5 as neutral.
- "dark/black" → brightness 0.2-0.35; "faint/slightly/a little" → 0.15-0.3; "some/here and there" → 0.3-0.45; "lots/dense" → 0.7-0.9.
- Only include keys you have a reason to set. "name" is a snake_case texture name.

Example request: 深海の熱水噴出孔周辺にある黒い岩。硫黄が少し付着していて、ところどころ青緑色の鉱物が露出している。Minecraft風16×16。
Example reply: {{"name": "vent_black_rock", "category": "thermal", "variant": "sulfur_rock", "material": "rock", "palette": "thermal", "accent": "sulfur_cyan", "roughness": 0.85, "cracks": 0.45, "mineral": 0.3, "brightness": 0.25, "size": 16}}
"""


def system_prompt() -> str:
    return SYSTEM_PROMPT.format(vocab=describe_vocabulary())


def extract_json(text: str) -> dict:
    """First JSON object in ``text`` (handles code fences and chatter)."""
    if not text:
        raise ValueError("empty response")
    t = re.sub(r"```(?:json)?", "", text)
    start = t.find("{")
    while start != -1:
        depth = 0
        in_str = False
        esc = False
        for i in range(start, len(t)):
            ch = t[i]
            if in_str:
                if esc:
                    esc = False
                elif ch == "\\":
                    esc = True
                elif ch == '"':
                    in_str = False
                continue
            if ch == '"':
                in_str = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    try:
                        obj = json.loads(t[start:i + 1])
                        if isinstance(obj, dict):
                            return obj
                    except ValueError:
                        break
        start = t.find("{", start + 1)
    raise ValueError("no JSON object found in response")


# ------------------------------------------------------------ keyword parser

_CATEGORY_RULES: list[tuple[tuple[str, ...], str]] = [
    (("熱水", "噴出孔", "チムニー", "smoker", "hydrothermal", "vent"), "thermal"),
    (("海藻", "昆布", "ケルプ", "kelp", "seaweed"), "kelp"),
    (("鉱石", " ore", "ore ", "鉱脈", "vein"), "ore"),
    (("結晶", "クリスタル", "水晶", "crystal", "geode", "晶洞"), "crystal"),
    (("サンゴ", "珊瑚", "海綿", "スポンジ", "coral", "sponge", "苔", "moss", "菌", "biomass"), "organic"),
    (("草", "シダ", "植物", "花", "grass", "fern", "plant", "flower", "bush"), "plant"),
    (("レンガ", "煉瓦", "タイル", "装飾", "柱", "ランプ", "brick", "tile", "pillar", "lamp", "lantern", "chiseled", "polished"), "decoration"),
    (("金属", "鉄板", "メタル", "metal", "plate", "grate"), "metal"),
    (("鉱物ブロック", "mineral block", "raw block", "原石ブロック"), "mineral"),
    (("岩", "石", "泥", "堆積", "砂", "rock", "stone", "mud", "sediment", "ground"), "terrain"),
    (("鉱物", "mineral"), "mineral"),
]

_VARIANT_RULES: dict[str, list[tuple[tuple[str, ...], str]]] = {
    "thermal": [(("硫黄", "sulfur", "sulphur"), "sulfur_rock"), (("黒い煙", "ブラックスモーカー", "black smoker"), "black_smoker"),
                (("白い煙", "ホワイトスモーカー", "white smoker"), "white_smoker"), (("結晶", "crystal"), "thermal_crystal"),
                (("皮殻", "クラスト", "crust"), "mineral_crust")],
    "ore": [(("鉱脈", "vein", "帯状", "線状"), "vein"), (("散在", "scattered", "粒"), "scattered")],
    "kelp": [(("巨大", "ジャイアント", "giant"), "giant"), (("アビサル", "深淵", "abyssal"), "abyssal"),
             (("熱水", "thermal"), "thermal"), (("深海", "deep"), "deep")],
    "crystal": [(("ブロック", "block"), "block"), (("単結晶", "single", "一本"), "single"), (("破片", "shard"), "shard"),
                (("芽", "bud"), "bud"), (("群晶", "cluster"), "cluster")],
    "plant": [(("シダ", "fern"), "fern"), (("草", "grass"), "grass"), (("茂み", "bush"), "bush"),
              (("つる", "蔓", "vine"), "vine"), (("球根", "実", "bulb"), "bulb")],
    "mineral": [(("縞", "band"), "banded"), (("粒", "granular", "raw"), "granular"), (("皮殻", "crust"), "crust")],
    "decoration": [(("レンガ", "煉瓦", "brick"), "bricks"), (("タイル", "tile"), "tiles"), (("磨", "polished"), "polished"),
                   (("彫", "模様", "chiseled"), "chiseled"), (("柱", "pillar"), "pillar"), (("ランプ", "灯", "lamp", "lantern"), "lamp")],
    "organic": [(("サンゴ", "珊瑚", "coral"), "coral"), (("海綿", "スポンジ", "sponge"), "sponge"),
                (("脈", "vein"), "vein"), (("苔", "moss"), "moss")],
    "metal": [(("板", "plate"), "plate"), (("格子", "grate"), "grate"), (("原石", "raw"), "raw")],
}

_MATERIAL_RULES: list[tuple[tuple[str, ...], str]] = [
    (("泥", "mud"), "mud"), (("堆積", "砂", "sediment", "sand"), "sediment"),
]

_PALETTE_RULES: list[tuple[tuple[str, ...], str]] = [
    (("熱水", "thermal", "噴出孔"), "thermal"),
    (("火山", "溶岩", "volcan", "lava"), "volcanic"),
    (("海溝", "trench", "超深海"), "trench"),
    (("発光", "生物発光", "biolumin"), "bioluminescent"),
    (("古代", "遺跡", "ancient", "ruin"), "ancient"),
    (("氷", "冷たい", "寒", "cold", "ice"), "cold"),
    (("有機", "organic"), "organic"),
    (("結晶", "crystal"), "crystal"),
    (("黒い", "漆黒", "深淵", "abyss", "black"), "abyss"),
    (("深海", "海底", "deep sea", "deep ocean", "ocean"), "deep_ocean"),
]

_ACCENT_RULES: list[tuple[tuple[str, ...], str]] = [
    (("マンガン", "manganese"), "manganese"),
    (("黄鉄鉱", "pyrite"), "pyrite"),
    (("銅", "copper"), "copper"),
    (("金", "gold"), "gold"),
    (("銀", "silver"), "silver"),
    (("錆", "rust"), "rust"),
    (("緑青", "patina"), "patina"),
    (("アメジスト", "紫", "purple", "amethyst", "violet"), "amethyst"),
    (("エメラルド", "emerald"), "emerald"),
    (("ルビー", "赤い鉱", "ruby"), "ruby"),
    (("骨", "bone"), "bone"),
    (("ピンク", "pink"), "coral_pink"),
    (("熱", "heat", "マグマ", "magma", "赤熱"), "heat"),
    (("青緑", "シアン", "cyan", "teal", "水色"), "cyan_mineral"),
    (("硫黄", "sulfur", "sulphur"), "sulfur"),
]

_LOW = ("少し", "少量", "薄く", "わずか", "かすか", "微弱", "slight", "faint", "a little", "few")
_MID = ("ところどころ", "所々", "点在", "some", "here and there", "scattered")
_HIGH = ("たくさん", "大量", "多く", "びっしり", "濃い", "lots", "dense", "rich", "heavy")


def _has(text: str, keys: tuple[str, ...]) -> bool:
    return any(k in text for k in keys)


def _near_amount(text: str, keys: tuple[str, ...], default: float) -> float:
    """Amount word within a few characters of the first matching key."""
    for k in keys:
        i = text.find(k)
        if i < 0:
            continue
        window = text[max(0, i - 14): i + len(k) + 14]
        if _has(window, _LOW):
            return 0.22
        if _has(window, _HIGH):
            return 0.8
        if _has(window, _MID):
            return 0.38
        return default
    return default


def keyword_params(text: str) -> dict[str, Any]:
    """Rule-based interpretation (Japanese / English keywords)."""
    t = " " + text.lower() + " "
    p: dict[str, Any] = {}
    for keys, cat in _CATEGORY_RULES:
        if _has(t, keys):
            p["category"] = cat
            break
    cat = p.get("category", "terrain")
    for keys, var in _VARIANT_RULES.get(cat, []):
        if _has(t, keys):
            p["variant"] = var
            break
    for keys, mat in _MATERIAL_RULES:
        if _has(t, keys):
            p["material"] = mat
            break
    for keys, pal in _PALETTE_RULES:
        if _has(t, keys):
            p["palette"] = pal
            break
    accents = [acc for keys, acc in _ACCENT_RULES if _has(t, keys)]
    if "sulfur" in accents and "cyan_mineral" in accents:
        p["accent"] = "sulfur_cyan"
    elif accents:
        p["accent"] = accents[0]
    mineral_keys = ("鉱物", "mineral", "露出", "鉱石", "ore", "結晶")
    sulfur_keys = ("硫黄", "sulfur", "sulphur")
    if _has(t, mineral_keys):
        p["mineral"] = _near_amount(t, mineral_keys, 0.35)
        if cat == "ore":
            p["density"] = _near_amount(t, mineral_keys, 0.5)
    elif _has(t, sulfur_keys):
        p["mineral"] = _near_amount(t, sulfur_keys, 0.35)
    if p.get("variant") == "sulfur_rock" and _has(t, sulfur_keys):
        # the sulfur crust is the main feature of this variant → "a little sulfur" = low density
        p["density"] = _near_amount(t, sulfur_keys, 0.45)
    if _has(t, ("ざらざら", "ゴツゴツ", "ごつごつ", "粗い", "荒い", "rough", "jagged")):
        p["roughness"] = 0.88
    elif _has(t, ("滑らか", "なめらか", "つるつる", "smooth", "polished")):
        p["roughness"] = 0.2
    if _has(t, ("亀裂", "ひび", "割れ", "crack")):
        p["cracks"] = 0.65 if _has(t, ("深い亀裂", "大きな亀裂", "deep crack")) else _near_amount(t, ("亀裂", "ひび", "crack"), 0.45)
    if _has(t, ("黒い", "漆黒", "暗い", "black", "dark")):
        p["brightness"] = 0.28
    elif _has(t, ("明るい", "白い", "bright", "white", "pale")):
        p["brightness"] = 0.66
    if _has(t, ("光る", "発光", "輝", "glow", "luminous", "shining")):
        p["glow"] = _near_amount(t, ("光る", "発光", "輝", "glow"), 0.6)
    if _has(t, ("層", "縞", "strata", "layer")):
        p["layering"] = 0.6
    if _has(t, ("湿", "濡れ", "wet", "slimy", "ぬめ")):
        p["moisture"] = 0.7
    if _has(t, ("高コントラスト", "high contrast", "くっきり")):
        p["contrast"] = 0.7
    if _has(t, ("低い", "背の低い", "low", "short")) and cat in ("plant", "kelp", "crystal"):
        p["height"] = 0.3
    if _has(t, ("高い", "背の高い", "長い", "tall", "long")) and cat in ("plant", "kelp", "crystal"):
        p["height"] = 0.9
    if _has(t, ("密集", "びっしり", "dense")):
        p["density"] = 0.85
    m = re.search(r"(8|16|32|64|128|256)\s*[x×*]\s*\1", t)
    if m:
        p["size"] = int(m.group(1))
    return p


# ------------------------------------------------------------------ parser


@dataclass
class ParseResult:
    mapped: MappedParams
    params: dict[str, Any]
    source: str                 # "llm" | "keywords"
    raw: str = ""
    notes: list[str] = field(default_factory=list)

    @property
    def settings(self) -> TextureSettings:
        return self.mapped.settings


class PromptParser:
    def __init__(self, client: LLMClient | None = None):
        self.client = client

    def parse(self, text: str, base: TextureSettings | None = None, allow_fallback: bool = True) -> ParseResult:
        notes: list[str] = []
        if self.client is not None:
            try:
                raw = self.client.chat(system_prompt(), text, json_schema())
                params = extract_json(raw)
                mapped = params_to_settings(params, base)
                return ParseResult(mapped, params, "llm", raw, notes + mapped.warnings)
            except (LLMError, ValueError) as e:
                if not allow_fallback:
                    raise
                notes.append(f"LLM unavailable ({e}); used keyword parser")
        params = keyword_params(text)
        mapped = params_to_settings(params, base)
        return ParseResult(mapped, params, "keywords", "", notes + mapped.warnings)
