"""Abyssia resource plants: the one table behind the plant materials, their processing and where each plant grows.

Every plant here yields a crafting material. The rest of the plant system is generated from these tables:

* plant_assets.py (run by gen_deep_assets.py): PlantDefinition JSON, block loot tables, harvest loot tables, recipes,
  lang, item and block models;
* gen_worldgen.py: one placed feature per new plant (biomes + depth band), the Phase 3 landmarks, cave plant lists;
* plant_textures.py: the new plants' sprites and the material icons.

    python tools/plant_defs.py            # JSON dump of the catalogue (materials, plants, recipes)
    python tools/plant_defs.py --check    # consistency checks only

Depth is in metres, as for fauna (DepthZone maps metres onto world Y). The deep ocean spans about 300 m (banks) to
11000 m (trench floors): deep_sea ~300-3500 m, abyssal_ocean ~3500-5300 m, abyssal_trench ~5300-6200 m,
hadal_zone 6200 m and deeper.
"""
from __future__ import annotations

import json
import sys
from dataclasses import asdict, dataclass, field

# ================================================================ materials
# One material per role: "this material comes from these plants". Base = what plants drop, processed = one step on.

RARITIES = ("common", "uncommon", "rare", "very_rare")   # -> Item rarity COMMON / UNCOMMON / RARE / EPIC


@dataclass(frozen=True)
class Material:
    id: str
    en: str
    ja: str
    rarity: str
    stage: str                 # base | processed
    role: str                  # fiber, resin, hard, oil, luminous, thermal, organic, pigment, crystal, pressure
    burn: int = 0              # furnace burn time in ticks (0 = not a fuel)
    source_en: str = ""        # tooltip: where to look (base materials)
    source_ja: str = ""


MATERIALS = [
    # ---- base materials (dropped or harvested from plants)
    Material("deep_fiber", "Deep Fiber", "深海繊維", "common", "base", "fiber",
             source_en="Abyssal grass, kelp, strandweed - forests and shallower seas",
             source_ja="深海草・コンブ・ストランド藻 ― 海藻林や浅めの海"),
    Material("plant_resin", "Sea Resin", "海樹脂", "common", "base", "resin",
             source_en="Amber fans in seabed forests (sparser in deep seas, trenches, crystal fields and caves), resin roots on cave"
                       " ceilings; amber fans also grow in a hydro planter",
             source_ja="海藻林のアンバーファン (深海・海溝・結晶原・洞窟にもまばらに生える)、洞窟天井の樹脂根。アンバーファンは水耕栽培プランターでも育つ"),
    Material("hard_stalk", "Hard Stalk", "硬質茎", "common", "base", "hard", burn=200,
             source_en="Knotstalk, black coral, cinder stalks, root colonies",
             source_ja="フシクキ・黒サンゴ・燠茎・根の群落"),
    Material("organic_matter", "Organic Matter", "有機物", "common", "base", "organic",
             source_en="Silt combs on abyssal mud, mosses, sponges",
             source_ja="深淵の泥のシルトコーム、苔、カイメン"),
    Material("bio_oil", "Bio Oil", "生体油", "uncommon", "base", "oil", burn=800,
             source_en="Oil bladder weed on the abyssal plains",
             source_ja="深淵平原の油胞藻"),
    Material("lumen_gel", "Lumen Gel", "発光ゲル", "uncommon", "base", "luminous",
             source_en="Glowing plants: lumen quills, blooms, glow anemones - caves and the abyss",
             source_ja="発光植物（ヒカリバネ・深淵の花・ヒカリイソギンチャク）― 洞窟と深淵"),
    Material("deep_pigment", "Deep Pigment", "深海色素", "uncommon", "base", "pigment",
             source_en="Tube plants, giant tubes, violet grass, soul and cave coral",
             source_ja="チューブ植物・巨大チューブ・青紫の深海草・ソウルサンゴ"),
    Material("thermal_fiber", "Thermal Fiber", "耐熱繊維", "uncommon", "base", "thermal",
             source_en="Heat moss, thermal tubes and vent grass around hydrothermal vents",
             source_ja="熱水噴出孔まわりの熱苔・熱水チューブ・噴出孔草"),
    Material("crystal_sap", "Crystal Sap", "結晶樹液", "rare", "base", "crystal",
             source_en="Glasslace and crystal plants in crystal fields and crystal caves",
             source_ja="結晶原と結晶洞窟のグラスレース・結晶植物"),
    Material("hadal_husk", "Hadal Husk", "超深海殻", "very_rare", "base", "pressure",
             source_en="Pressure gourds on the hadal floor (below 6000 m)",
             source_ja="超深海帯の海底（6000 m 以深）の耐圧瓢"),
    # ---- processed materials (one step from a base material)
    Material("fiber_rope", "Fiber Rope", "繊維ロープ", "common", "processed", "fiber"),
    Material("sea_cloth", "Sea Cloth", "海布", "common", "processed", "fiber"),
    Material("marine_adhesive", "Marine Adhesive", "海洋接着剤", "common", "processed", "resin"),
    Material("refined_oil", "Refined Oil", "精製油", "uncommon", "processed", "oil", burn=2400),
    Material("lumen_cell", "Lumen Cell", "発光素子", "uncommon", "processed", "luminous"),
    Material("thermal_felt", "Thermal Felt", "耐熱フェルト", "uncommon", "processed", "thermal"),
    Material("crystal_lens", "Crystal Lens", "結晶レンズ", "rare", "processed", "crystal"),
    Material("hadal_plating", "Hadal Plating", "超深海殻板", "very_rare", "processed", "pressure"),
]
MATERIAL = {m.id: m for m in MATERIALS}

# ================================================================ plants

SIZES = ("small", "medium", "large", "giant")
# Depth bands (metres) - the ecosystem changes with depth rather than simply thinning out.
BANDS = {"upper": (200, 1000), "bathyal": (1000, 4000), "abyssal": (4000, 6000), "hadal": (6000, 11000)}


@dataclass(frozen=True)
class Drop:
    item: str                  # "abyssia:x" or "minecraft:x"; bare names are abyssia materials
    min: int = 1
    max: int = 1
    chance: float = 1.0

    @property
    def id(self) -> str:
        return self.item if ":" in self.item else "abyssia:" + self.item


@dataclass(frozen=True)
class Plant:
    id: str
    category: str              # fiber | resin | hard | oil | luminous | crystal | thermal | mud | hadal
    size: str                  # small | medium | large | giant
    rarity: str
    biomes: tuple[str, ...]    # deep-ocean biomes (and "cave:<environment>" for cave floors/ceilings)
    depth: tuple[int, int]     # metres
    drops: tuple[Drop, ...]    # on breaking, besides the plant itself (resource plants: only when ripe)
    roles: tuple[str, ...]     # crafting roles the drops feed
    new: bool = False          # a new block (else an existing plant that gains material drops)
    en: str = ""
    ja: str = ""
    form: str = "cross"        # new plants: cross | hanging | column
    per: str = "block"         # which blocks roll the drops: block (every block) | top | tip (only a column's free end,
                               # so breaking an 80-block kelp does not roll 80 times)
    colour: str = "PLANT"      # MapColor of new plants
    harvest: tuple[Drop, ...] = ()   # right-click / shears on a ripe plant; empty = break only
    growth: float = 0.0        # chance per random tick that a harvested plant ripens again (0 = never)
    light: int = 0             # block light when ripe (luminous plants only)
    real: str = ""             # real-world reference (design note)
    place: dict = field(default_factory=dict)   # worldgen: tries, spread, rarity | count | clustered, height
    extra: tuple = ()          # extra seabed placements (RS01): (biome, (min, max) metres, tries) -> feature plant_<id>_<biome>,
                               # same spread / density as place; the main feature stays on `biomes` only


D = Drop
ANY = (0, 11000)

PLANTS = [
    # ================================================================ Phase 1: fiber, resin, hard, luminous
    Plant("strandweed", "fiber", "medium", "common", ("deep_sea", "abyssal_forest", "deep_forest"), (200, 4000),
          (D("deep_fiber", 1, 2), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber", "textile"), new=True, en="Strandweed", ja="ストランド藻", colour="COLOR_GREEN",
          harvest=(D("deep_fiber", 1, 2), D("abyssia:kelp_leaf", 1, 1, 0.3),), growth=0.12,
          real="filamentous brown / green algae tufts (game simplification: they grow far below the photic zone)",
          place=dict(tries=20, spread=5, clustered=2)),
    Plant("amber_fan", "resin", "medium", "common", ("abyssal_forest", "deep_forest", "abyssal_ocean", "cave:forest", "cave:abyssal"), (500, 5000),
          (D("plant_resin", 1, 2),), ("resin", "adhesive", "coating"), new=True, en="Amber Fan", ja="アンバーファン", colour="TERRACOTTA_RED",
          harvest=(D("plant_resin", 1, 2),), growth=0.06,
          real="sea fans (gorgonians) that exude a sticky coat; the resin beads are a game simplification",
          place=dict(tries=12, spread=5, clustered=1),
          # RS01: sparse fans outside the forests (cave:abyssal = a rare floor plant in gen_worldgen CAVE_ENVIRONMENTS)
          extra=(("deep_sea", (1000, 5000), 4), ("abyssal_trench", (1000, 5000), 3), ("deep_crystal_fields", (1500, 5000), 2))),
    # RS01: also cave:mineral, cave:thermal (deep caves only, max_y in CAVE_ENVIRONMENTS) and cave:luminous (rare)
    Plant("resin_root", "resin", "medium", "uncommon", ("cave:abyssal", "cave:forest", "cave:cavern", "cave:mineral", "cave:thermal",
                                                        "cave:luminous"), ANY,
          (D("plant_resin", 1, 2),), ("resin", "adhesive"), new=True, en="Resin Root", ja="樹脂根", form="hanging",
          per="tip", colour="TERRACOTTA_BROWN",
          real="roots / stolons hanging from overhangs; resin droplets are a game simplification"),
    Plant("knotstalk", "hard", "large", "common", ("deep_sea", "abyssal_ocean", "abyssal_trench"), (1000, 6200),
          (D("hard_stalk", 1, 1, 0.6),), ("hard", "tool", "structure"), new=True, en="Knotstalk", ja="フシクキ",
          form="column", colour="TERRACOTTA_WHITE", real="bamboo coral (Isididae): pale calcite segments joined by dark gorgonin nodes",
          place=dict(tries=10, spread=4, clustered=1, height=(2, 7))),
    Plant("lumen_quill", "luminous", "medium", "uncommon", ("abyssal_ocean", "abyssal_trench", "cave:abyssal", "cave:luminous"),
          (1000, 6200), (D("lumen_gel", 1, 1),), ("luminous", "light", "sensor"), new=True, en="Lumen Quill", ja="ヒカリバネ", colour="COLOR_PURPLE",
          harvest=(D("lumen_gel", 1, 2),), growth=0.05, light=4,
          real="sea pens (Pennatulacea): a feather-shaped colony on soft mud whose polyps glow when touched",
          place=dict(tries=10, spread=4, clustered=1)),
    # existing plants: fiber
    Plant("abyssal_grass", "fiber", "small", "common", ("deep_sea", "abyssal_ocean", "abyssal_forest", "deep_forest"), (200, 5300),
          (D("deep_fiber", 1, 1, 0.3), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",), per="top"),
    Plant("teal_abyssal_grass", "fiber", "small", "common", ("abyssal_forest", "deep_forest", "hadal_zone", "deep_crystal_fields"), ANY,
          (D("deep_fiber", 1, 1, 0.3), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",), per="top"),
    Plant("ashen_abyssal_grass", "fiber", "small", "common", ("abyssal_trench", "volcanic_deep", "thermal_vents"), ANY,
          (D("deep_fiber", 1, 1, 0.3), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",), per="top"),
    Plant("sea_fern", "fiber", "small", "common", ("deep_sea", "abyssal_forest", "deep_forest", "hadal_zone"), ANY,
          (D("deep_fiber", 1, 1, 0.5), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",)),
    Plant("deep_kelp", "fiber", "large", "common", ("deep_sea", "abyssal_forest", "deep_forest"), (200, 5300),
          (D("deep_fiber", 1, 3), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber", "textile"), per="top"),
    Plant("giant_kelp", "fiber", "giant", "common", ("abyssal_forest", "deep_forest"), (200, 5300),
          (D("deep_fiber", 2, 4), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber", "textile"), per="top"),
    Plant("void_kelp", "fiber", "large", "uncommon", ("abyssal_ocean", "abyssal_forest"), ANY,
          (D("deep_fiber", 1, 1, 0.4), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",)),
    Plant("cave_grass", "fiber", "small", "common", ("cave:*",), ANY, (D("deep_fiber", 1, 1, 0.3), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",), per="top"),
    Plant("cave_fern", "fiber", "small", "common", ("cave:*",), ANY, (D("deep_fiber", 1, 1, 0.4), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",)),
    Plant("wall_fern", "fiber", "small", "common", ("cave:*",), ANY, (D("deep_fiber", 1, 1, 0.4), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",)),
    Plant("cave_kelp", "fiber", "large", "common", ("cave:*",), ANY, (D("deep_fiber", 1, 2), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",), per="top"),
    Plant("hanging_kelp", "fiber", "large", "common", ("cave:*",), ANY, (D("deep_fiber", 1, 2), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",), per="tip"),
    Plant("giant_cave_kelp", "fiber", "giant", "common", ("cave:cavern",), ANY, (D("deep_fiber", 2, 3), D("abyssia:kelp_leaf", 1, 1, 0.3),), ("fiber",), per="top"),
    # existing plants: hard
    Plant("black_coral", "hard", "small", "uncommon", ("abyssal_trench", "hadal_zone"), (4000, 11000),
          (D("hard_stalk", 1, 2),), ("hard", "tool"), real="black corals (Antipatharia): a dark, horn-like protein skeleton"),
    Plant("cave_root", "hard", "medium", "common", ("cave:*",), ANY, (D("hard_stalk", 1, 1, 0.5),), ("hard",), per="tip"),
    Plant("deep_root", "hard", "medium", "common", ("cave:*",), ANY, (D("hard_stalk", 1, 1, 0.5),), ("hard",), per="tip"),
    Plant("ancient_cave_plant", "hard", "large", "uncommon", ("cave:cavern",), ANY, (D("hard_stalk", 1, 2),), ("hard",), per="top"),
    # existing plants: luminous
    Plant("glowtip_grass", "luminous", "small", "common", ("deep_sea", "abyssal_ocean", "deep_crystal_fields"), ANY,
          (D("lumen_gel", 1, 1, 0.35),), ("luminous",)),
    Plant("glow_anemone", "luminous", "small", "uncommon", ("deep_sea", "abyssal_forest"), ANY, (D("lumen_gel", 1, 1, 0.6),), ("luminous",)),
    Plant("glow_coral", "luminous", "small", "uncommon", ("deep_sea",), ANY, (D("lumen_gel", 1, 1, 0.4),), ("luminous",)),
    Plant("abyssal_bloom", "luminous", "small", "uncommon", ("abyssal_forest", "abyssal_ocean"), ANY, (D("lumen_gel", 1, 1, 0.8),), ("luminous",)),
    Plant("abyssal_mushroom", "luminous", "small", "uncommon", ("abyssal_ocean", "thermal_vents", "cave:*"), ANY,
          (D("lumen_gel", 1, 1, 0.5), D("abyssia:mushroom_cap", 1, 1, 0.35)), ("luminous",)),
    Plant("floating_bloom", "luminous", "small", "uncommon", ("deep_sea", "abyssal_ocean", "abyssal_forest", "deep_forest", "hadal_zone"), ANY,
          (D("lumen_gel", 1, 1, 0.7),), ("luminous",)),
    Plant("cave_bloom", "luminous", "small", "uncommon", ("cave:*",), ANY, (D("lumen_gel", 1, 1, 0.8),), ("luminous",)),
    Plant("cave_vine", "luminous", "medium", "common", ("cave:*",), ANY, (D("lumen_gel", 1, 1, 0.5),), ("luminous",), per="tip"),
    Plant("abyssal_vine", "luminous", "medium", "uncommon", ("cave:*",), ANY, (D("lumen_gel", 1, 1, 0.6),), ("luminous",), per="tip"),
    Plant("luminous_moss", "luminous", "small", "common", ("cave:cavern",), ANY, (D("lumen_gel", 1, 1, 0.2),), ("luminous",)),

    # ================================================================ Phase 2: thermal, crystal symbiosis, oil
    Plant("oil_bladder_weed", "oil", "medium", "uncommon", ("abyssal_ocean", "deep_sea"), (2500, 5500),
          (D("bio_oil", 1, 1),), ("oil", "fuel", "chemical"), new=True, en="Oil Bladder Weed", ja="油胞藻", colour="COLOR_BROWN",
          harvest=(D("bio_oil", 1, 2),), growth=0.05,
          real="brown algae with gas bladders; lipid-rich microalgae. Oil-filled bladders are a game simplification",
          place=dict(tries=12, spread=5, clustered=1)),
    Plant("glasslace", "crystal", "medium", "rare", ("deep_crystal_fields", "cave:crystal"), (1000, 6200),
          (D("crystal_sap", 1, 1),), ("crystal", "optics", "energy"), new=True, en="Glasslace", ja="グラスレース", colour="COLOR_LIGHT_GRAY",
          harvest=(D("crystal_sap", 1, 1),), growth=0.025, light=3,
          real="glass sponges (Hexactinellida, e.g. Venus' flower basket): a lattice of silica spicules",
          place=dict(tries=10, spread=4, rarity=2)),
    # existing plants: thermal, crystal, pigment
    Plant("thermal_tube", "thermal", "medium", "common", ("thermal_vents", "volcanic_deep"), ANY,
          (D("thermal_fiber", 1, 2),), ("thermal",), per="top"),
    Plant("vent_grass", "thermal", "small", "common", ("thermal_vents", "volcanic_deep"), ANY, (D("thermal_fiber", 1, 1, 0.4),), ("thermal",)),
    Plant("heat_moss", "thermal", "small", "common", ("thermal_vents", "volcanic_deep"), ANY, (D("thermal_fiber", 1, 1, 0.5), D("sulfur", 1, 1, 0.2)), ("thermal",)),
    Plant("thermal_plant", "thermal", "small", "common", ("cave:thermal",), ANY, (D("thermal_fiber", 1, 1, 0.5),), ("thermal",)),
    Plant("mineral_vine", "thermal", "medium", "common", ("thermal_vents", "volcanic_deep"), ANY, (D("thermal_fiber", 1, 1, 0.5),), ("thermal",), per="top"),
    Plant("wall_mineral_vine", "thermal", "small", "common", ("cave:mineral",), ANY, (D("thermal_fiber", 1, 1, 0.3),), ("thermal",)),
    Plant("crystal_plant", "crystal", "small", "uncommon", ("deep_crystal_fields",), ANY, (D("crystal_sap", 1, 1, 0.4),), ("crystal",)),
    Plant("cave_crystal_plant", "crystal", "small", "uncommon", ("cave:crystal",), ANY, (D("crystal_sap", 1, 1, 0.4),), ("crystal",)),
    Plant("crystal_kelp", "crystal", "large", "rare", ("cave:cavern",), ANY, (D("crystal_sap", 1, 1, 0.5),), ("crystal",), per="top"),
    Plant("tube_plant", "pigment", "medium", "common", ("deep_sea", "abyssal_ocean", "deep_forest", "abyssal_trench"), ANY,
          (D("deep_pigment", 1, 1, 0.6),), ("pigment", "dye"), per="top"),
    Plant("cave_tube_plant", "pigment", "medium", "common", ("cave:*",), ANY, (D("deep_pigment", 1, 1, 0.6),), ("pigment", "dye"), per="top"),
    Plant("giant_tube", "pigment", "giant", "uncommon", ("deep_forest", "abyssal_trench", "hadal_zone"), ANY,
          (D("deep_pigment", 1, 2),), ("pigment", "dye"), per="top"),
    Plant("violet_abyssal_grass", "pigment", "small", "common", ("hadal_zone", "deep_crystal_fields"), ANY,
          (D("deep_pigment", 1, 1, 0.3),), ("pigment",), per="top"),
    Plant("soul_coral", "pigment", "small", "uncommon", ("abyssal_ocean", "abyssal_trench"), ANY, (D("deep_pigment", 1, 1, 0.5),), ("pigment",)),
    Plant("cave_coral", "pigment", "small", "common", ("cave:*",), ANY, (D("deep_pigment", 1, 1, 0.5),), ("pigment",)),

    # ================================================================ Phase 3: mud flora, volcanic carbon plants (landmarks: LANDMARKS)
    Plant("silt_comb", "mud", "small", "common", ("abyssal_ocean", "abyssal_trench", "hadal_zone"), (3000, 8000),
          (D("organic_matter", 1, 2), D("minecraft:clay_ball", 1, 1, 0.5)), ("organic", "fertilizer", "clay"), new=True,
          en="Silt Comb", ja="シルトコーム", colour="TERRACOTTA_GRAY",
          real="xenophyophores: giant single-celled organisms on abyssal mud that build fragile tests from sediment",
          place=dict(tries=16, spread=5, clustered=2)),
    Plant("cinder_stalk", "hard", "medium", "uncommon", ("volcanic_deep",), ANY,
          (D("hard_stalk", 1, 1), D("minecraft:charcoal", 1, 1, 0.5)), ("hard", "carbon", "fuel"), new=True,
          en="Cinder Stalk", ja="燠茎", colour="COLOR_BLACK",
          real="heat-tolerant growths on volcanic rock; the charred, carbon-rich stalk is a game simplification",
          place=dict(tries=12, spread=4, clustered=1)),
    Plant("abyssal_moss", "mud", "small", "common", ("deep_sea", "abyssal_ocean", "abyssal_forest", "deep_forest"), ANY,
          (D("organic_matter", 1, 1, 0.4),), ("organic",)),
    Plant("sponge_plant", "mud", "small", "common", ("abyssal_ocean", "abyssal_forest", "deep_forest"), ANY,
          (D("organic_matter", 1, 1, 0.6),), ("organic",)),
    Plant("cave_sponge", "mud", "small", "common", ("cave:*",), ANY, (D("organic_matter", 1, 1, 0.6),), ("organic",)),
    Plant("cave_moss", "mud", "small", "common", ("cave:*",), ANY, (D("organic_matter", 1, 1, 0.3),), ("organic",)),
    Plant("fallen_kelp", "mud", "small", "common", ("cave:*",), ANY, (D("organic_matter", 1, 1, 0.5),), ("organic",)),

    # ================================================================ Phase 4: hadal and rare plants
    Plant("pressure_gourd", "hadal", "small", "very_rare", ("hadal_zone",), (6000, 11000),
          (D("hadal_husk", 1, 1), D("abyssia:gourd_flesh", 1, 1, 0.4)), ("pressure", "special_gear"), new=True, en="Pressure Gourd", ja="耐圧瓢", colour="COLOR_BLUE",
          harvest=(D("hadal_husk", 1, 1), D("abyssia:gourd_flesh", 1, 1, 0.4)), growth=0.008,
          real="slow-growing hadal organisms; the pressure-hardened husk is a game simplification",
          place=dict(tries=6, spread=3, rarity=3)),
    Plant("hadal_bloom", "luminous", "small", "rare", ("hadal_zone",), (6000, 11000),
          (D("lumen_gel", 1, 1), D("hadal_husk", 1, 1, 0.08)), ("luminous", "pressure")),
]
PLANT = {p.id: p for p in PLANTS}
NEW_PLANTS = [p for p in PLANTS if p.new]
HARVESTABLE = [p for p in PLANTS if p.harvest]

# ================================================================ Phase 3 landmarks (plant communities; gen_worldgen.py builds them)
LANDMARKS = {
    # name: (biomes, depth metres, description)
    "giant_kelp_forest": (("abyssal_forest",), (200, 5300), "existing: giant kelp canopy, the fiber forest"),
    "abyssal_root_colony": (("abyssal_ocean", "abyssal_forest", "deep_forest"), (1000, 6000),
                            "arching ancient roots; knotstalk, amber fans and resin roots gather under the arches"),
    "deep_bloom_colony": (("abyssal_ocean", "abyssal_trench", "hadal_zone"), (3500, 11000),
                          "a ring of lumen quills and abyssal / hadal blooms: a light in the dark, a lumen gel source"),
    "thermal_tube_forest": (("thermal_vents",), ANY, "tall thermal tubes over sulfur mats: the thermal fiber source"),
}

# ================================================================ recipes
# Each: (id, type, spec). Types: shaped (pattern, key, result, count), shapeless (ingredients, result, count),
# smelting (ingredient, result, xp) [+ blasting/smoking variant flags].


def _i(n: str) -> str:
    return n if ":" in n else "abyssia:" + n


RECIPES = [
    # ---- fiber
    ("fiber_rope", "shaped", dict(pattern=["F", "F", "F"], key={"F": "deep_fiber"}, result="fiber_rope", count=1)),
    ("sea_cloth", "shaped", dict(pattern=["FF", "FF"], key={"F": "deep_fiber"}, result="sea_cloth", count=1)),
    ("string_from_fiber_rope", "shapeless", dict(ingredients=["fiber_rope"], result="minecraft:string", count=3)),
    ("lead_from_fiber_rope", "shaped", dict(pattern=["RR ", "RA ", "  R"], key={"R": "fiber_rope", "A": "marine_adhesive"},
                                            result="minecraft:lead", count=2)),
    ("leather_from_sea_cloth", "shapeless", dict(ingredients=["sea_cloth", "sea_cloth", "marine_adhesive"], result="minecraft:leather", count=1)),
    ("white_wool_from_sea_cloth", "shapeless", dict(ingredients=["sea_cloth"], result="minecraft:white_wool", count=1)),
    # ---- resin
    ("marine_adhesive", "smelting", dict(ingredient="plant_resin", result="marine_adhesive", xp=0.2)),
    ("sticky_piston_from_marine_adhesive", "shaped", dict(pattern=["A", "P"], key={"A": "marine_adhesive", "P": "minecraft:piston"},
                                                          result="minecraft:sticky_piston", count=1)),
    # raw resin also waxes copper in the world like honeycomb (right-click; ResinItem), so no per-variant recipes
    # ---- hard
    ("stick_from_hard_stalk", "shapeless", dict(ingredients=["hard_stalk"], result="minecraft:stick", count=4)),
    ("charcoal_from_hard_stalk", "smelting", dict(ingredient="hard_stalk", result="minecraft:charcoal", xp=0.15)),
    ("scaffolding_from_hard_stalk", "shaped", dict(pattern=["SRS", "S S", "S S"], key={"S": "hard_stalk", "R": "fiber_rope"},
                                                   result="minecraft:scaffolding", count=6)),
    ("hard_stalk_from_ancient_root", "shapeless", dict(ingredients=["ancient_root"], result="hard_stalk", count=3)),
    # ---- organic
    ("bone_meal_from_organic_matter", "shapeless", dict(ingredients=["organic_matter", "organic_matter"], result="minecraft:bone_meal", count=3)),
    ("gunpowder_from_organic_matter", "shapeless", dict(ingredients=["sulfur", "minecraft:charcoal", "organic_matter"], result="minecraft:gunpowder", count=2)),
    ("packed_mud_from_organic_matter", "shapeless", dict(ingredients=["deep_mud", "organic_matter"], result="minecraft:packed_mud", count=1)),
    # ---- oil
    ("refined_oil", "smelting", dict(ingredient="bio_oil", result="refined_oil", xp=0.3)),
    ("fire_charge_from_refined_oil", "shapeless", dict(ingredients=["refined_oil", "sulfur", "minecraft:charcoal"], result="minecraft:fire_charge", count=3)),
    ("torch_from_bio_oil", "shaped", dict(pattern=["O", "S"], key={"O": "bio_oil", "S": "hard_stalk"}, result="minecraft:torch", count=4)),
    # ---- luminous
    ("lumen_cell", "shaped", dict(pattern=[" G ", "GAG", " G "], key={"G": "lumen_gel", "A": "marine_adhesive"}, result="lumen_cell", count=1)),
    ("sea_lantern_from_lumen_cell", "shaped", dict(pattern=["CC", "CC"], key={"C": "lumen_cell"}, result="minecraft:sea_lantern", count=1)),
    ("glow_item_frame_from_lumen_gel", "shapeless", dict(ingredients=["minecraft:item_frame", "lumen_gel"], result="minecraft:glow_item_frame", count=1)),
    ("glowstone_dust_from_lumen_gel", "shapeless", dict(ingredients=["lumen_gel", "lumen_gel"], result="minecraft:glowstone_dust", count=1)),
    ("redstone_lamp_from_lumen_cell", "shaped", dict(pattern=[" R ", "RCR", " R "], key={"R": "minecraft:redstone", "C": "lumen_cell"},
                                                     result="minecraft:redstone_lamp", count=1)),
    # ---- pigment (minerals as mordants / mineral pigments: cobalt blue, sulfur yellow, manganese black)
    ("magenta_dye_from_deep_pigment", "shapeless", dict(ingredients=["deep_pigment"], result="minecraft:magenta_dye", count=2)),
    ("blue_dye_from_deep_pigment", "shapeless", dict(ingredients=["deep_pigment", "raw_cobalt"], result="minecraft:blue_dye", count=4)),
    ("yellow_dye_from_deep_pigment", "shapeless", dict(ingredients=["deep_pigment", "sulfur"], result="minecraft:yellow_dye", count=3)),
    ("black_dye_from_deep_pigment", "shapeless", dict(ingredients=["deep_pigment", "raw_manganese"], result="minecraft:black_dye", count=4)),
    # ---- thermal
    ("thermal_felt", "shaped", dict(pattern=["TT", "TT"], key={"T": "thermal_fiber"}, result="thermal_felt", count=1)),
    ("blast_furnace_from_thermal_felt", "shaped", dict(pattern=["III", "IFI", "TTT"],
                                                       key={"I": "minecraft:iron_ingot", "F": "minecraft:furnace", "T": "thermal_felt"},
                                                       result="minecraft:blast_furnace", count=1)),
    ("campfire_from_thermal_felt", "shaped", dict(pattern=[" S ", "STS", "HHH"], key={"S": "minecraft:stick", "T": "thermal_felt", "H": "hard_stalk"},
                                                  result="minecraft:campfire", count=1)),
    # ---- crystal
    ("crystal_lens", "smelting", dict(ingredient="crystal_sap", result="crystal_lens", xp=0.5)),
    ("spyglass_from_crystal_lens", "shaped", dict(pattern=["L", "C", "C"], key={"L": "crystal_lens", "C": "minecraft:copper_ingot"},
                                                  result="minecraft:spyglass", count=1)),
    ("daylight_detector_from_crystal_lens", "shaped", dict(pattern=["GGG", "LLL", "HHH"],
                                                           key={"G": "minecraft:glass", "L": "crystal_lens", "H": "hard_stalk"},
                                                           result="minecraft:daylight_detector", count=1)),
    ("tinted_glass_from_crystal_sap", "shaped", dict(pattern=[" S ", "SGS", " S "], key={"S": "crystal_sap", "G": "minecraft:glass"},
                                                     result="minecraft:tinted_glass", count=2)),
    # ---- pressure (hadal)
    ("hadal_plating", "shaped", dict(pattern=["HH", "HA"], key={"H": "hadal_husk", "A": "marine_adhesive"}, result="hadal_plating", count=1)),
    ("turtle_helmet_from_hadal_plating", "shaped", dict(pattern=["PPP", "P P"], key={"P": "hadal_plating"}, result="minecraft:turtle_helmet", count=1)),
    ("conduit_from_hadal_plating", "shaped", dict(pattern=["PPP", "PHP", "PPP"], key={"P": "hadal_plating", "H": "minecraft:heart_of_the_sea"},
                                                  result="minecraft:conduit", count=1)),
]


def uses() -> dict[str, list[str]]:
    """material -> recipe results that consume it (checks that no material is a dead end)."""
    out: dict[str, list[str]] = {m.id: [] for m in MATERIALS}
    for rid, kind, s in RECIPES:
        ins = list(s.get("key", {}).values()) + list(s.get("ingredients", [])) + ([s["ingredient"]] if "ingredient" in s else [])
        for n in ins:
            if n in out:
                out[n].append(s["result"])
    return out


def check() -> list[str]:
    errors = []
    produced = {d.item for p in PLANTS for d in (*p.drops, *p.harvest)}
    for m in MATERIALS:
        if m.stage == "base" and m.id not in produced:
            errors.append(f"{m.id}: no plant produces it")
        if not uses()[m.id]:
            errors.append(f"{m.id}: no recipe uses it")
        if m.rarity not in RARITIES:
            errors.append(f"{m.id}: bad rarity {m.rarity}")
    for p in PLANTS:
        if p.size not in SIZES or p.rarity not in RARITIES:
            errors.append(f"{p.id}: bad size/rarity")
        if p.new and not (p.en and p.ja):
            errors.append(f"{p.id}: new plant without names")
        if p.harvest and p.growth <= 0:
            errors.append(f"{p.id}: harvestable but never regrows")
        if p.depth[0] >= p.depth[1]:
            errors.append(f"{p.id}: empty depth band")
        for b, (lo, hi), tries in p.extra:
            if not p.place or lo >= hi or tries < 1 or b in p.biomes:
                errors.append(f"{p.id}: bad extra placement {b}")
        for d in (*p.drops, *p.harvest):
            if ":" not in d.item and d.item not in MATERIAL and d.item != "sulfur":
                errors.append(f"{p.id}: unknown drop {d.item}")
    ids = [r[0] for r in RECIPES]
    if len(ids) != len(set(ids)):
        errors.append("duplicate recipe ids")
    return errors


def definition(p: Plant) -> dict:
    """The PlantDefinition JSON (data/abyssia/abyssia/plant/<id>.json)."""
    return {"block": "abyssia:" + p.id, "category": p.category, "size": p.size, "rarity": p.rarity,
            "biomes": list(p.biomes) + [b for b, _, _ in p.extra], "min_depth": p.depth[0], "max_depth": p.depth[1],
            "growth_rate": p.growth,
            "drops": [{"item": d.id, "min": d.min, "max": d.max, "chance": d.chance} for d in p.drops],
            "harvest": [{"item": d.id, "min": d.min, "max": d.max, "chance": d.chance} for d in p.harvest],
            "crafting_role": list(p.roles)}


if __name__ == "__main__":
    errs = check()
    if "--check" in sys.argv:
        print(json.dumps({"ok": not errs, "errors": errs}, ensure_ascii=False, indent=1))
    else:
        print(json.dumps({"materials": [asdict(m) for m in MATERIALS], "plants": [definition(p) for p in PLANTS],
                          "uses": uses(), "errors": errs}, ensure_ascii=False, indent=1))
    sys.exit(1 if errs else 0)
