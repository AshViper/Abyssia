"""ACH01 advancements: generator for data/abyssia/advancement/<tab>/<name>.json (1.21 format) and
tools/lang_parts/advancements.json.  The list below is the content; spec: inbox/specs/ACH01.md.

  python tools/advancement_assets.py [--dry-run] [--regen-lang]

--dry-run     validate and print the summary, write nothing.
--regen-lang  after writing lang_parts, rebuild src/main/resources/assets/abyssia/lang/{en_us,ja_jp}.json through
              gen_deep_assets.lang() (the normal pipeline; lang only, no textures or models are touched).
Forge 1.20.1 files: python tools/mc_format.py --to-forge <Forge checkout>  (converts advancement/ -> advancements/).

Output is generated: never hand-edit the JSON or lang_parts/advancements.json.  Every referenced id is validated
(fails loudly): items/blocks/entities/effects via the lang keys, biomes, scan targets, technologies, background textures.
Custom trigger fields (dive/scan/research/build/capture/aquarium/event) are the contract with src/main/java/com/abyssia/advancement.
"""
import glob
import json
import os
import re
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
DATA = os.path.join(RES, "data", "abyssia")
ASSETS = os.path.join(RES, "assets", "abyssia")
JAVA = os.path.join(HERE, "..", "src", "main", "java")
LANG_PARTS = os.path.join(HERE, "lang_parts", "advancements.json")
NS = "abyssia"

BACKGROUNDS = {"dive": "abyssia:textures/block/abyssal_rock.png",
               "life": "abyssia:textures/block/aquarium_sand.png",
               "engineering": "abyssia:textures/block/abyssal_rock_bricks.png"}
TABS = ("dive", "life", "engineering")

# icon items missing in this mod -> close existing item (reported)
ICON_FALLBACK = {}
EVENTS = {"pressure_damage", "hazard_damage", "submarine_crushed", "habitat_blackout", "aquarium_birth"}


# ================================================================ criterion helpers

class C:
    """criteria {name: {trigger, conditions?}} + requirements (list of OR-rows; rows are ANDed)."""

    def __init__(self, crits, rows, refs=None):
        self.crits, self.rows, self.refs = crits, rows, refs or []

    @staticmethod
    def one(name, trigger, cond=None, refs=()):
        d = {"trigger": trigger}
        if cond:
            d["conditions"] = cond
        return C({name: d}, [[name]], list(refs))


def _merge(cs):
    crits, refs = {}, []
    for c in cs:
        for k, v in c.crits.items():
            if k in crits:
                raise SystemExit(f"duplicate criterion name {k!r}")
            crits[k] = v
        refs += c.refs
    return crits, refs


def each(*cs):
    """every member required (each member keeps its own requirement rows)."""
    crits, refs = _merge(cs)
    return C(crits, [r for c in cs for r in c.rows], refs)


def any_(*cs):
    """any one member is enough (one OR row)."""
    crits, refs = _merge(cs)
    return C(crits, [[n for c in cs for r in c.rows for n in r]], refs)


def _rid(x, default_ns, folder=""):
    return x if ":" in x else f"{default_ns}:{folder}{x}"


def _b(lo=None, hi=None, f=float):
    d = {}
    if lo is not None:
        d["min"] = f(lo)
    if hi is not None:
        d["max"] = f(hi)
    return d


def dive(name="dive", y_max=None, submerged=None, in_submarine=None, upgrades_min=None, tier_min=None, tier_max=None,
         pieces_max=None, health_max=None):
    cond = {}
    if y_max is not None:
        cond["y"] = _b(hi=y_max)
    if submerged is not None:
        cond["submerged"] = submerged
    if in_submarine is not None:
        cond["in_submarine"] = in_submarine
    if upgrades_min is not None:
        cond["submarine_upgrades"] = _b(upgrades_min, f=int)
    if tier_min is not None or tier_max is not None:
        cond["suit_tier"] = _b(tier_min, tier_max, int)
    if pieces_max is not None:
        cond["suit_pieces"] = _b(hi=pieces_max, f=int)
    if health_max is not None:
        cond["health"] = _b(hi=health_max)
    return C.one(name, "abyssia:dive", cond)


def scan(target=None, group=None, count=None, complete=False):
    cond = {}
    refs = []
    if target:
        cond["target"] = f"{NS}:scan_target/{target}"
        refs.append(("scan_target", target))
    if group:
        cond["group"] = group
    if count is not None:
        cond["count"] = _b(count, f=int)
    if complete:
        cond["complete"] = True
    name = "scan_" + (target or (group or "") + ("complete" if complete else f"count{count}" if count else "any"))
    c = C.one(name, "abyssia:scan", cond, refs)
    return c


def research(technology=None, count=None, complete=False):
    cond = {}
    refs = []
    if technology:
        cond["technology"] = f"{NS}:technology/{technology}"
        refs.append(("technology", technology))
    if count is not None:
        cond["count"] = _b(count, f=int)
    if complete:
        cond["complete"] = True
    return C.one("research_" + (technology or ("complete" if complete else f"count{count}")), "abyssia:research", cond, refs)


def build(build=None):
    return C.one("build_" + (build or "any"), "abyssia:build", {"build": build} if build else None)


def capture(entity=None):
    return C.one("capture", "abyssia:capture", {"entity": _rid(entity, NS)} if entity else None,
                 [("entity", entity)] if entity else [])


def aquarium(species=None, count=None, species_count=None):
    cond = {}
    if species:
        cond["species"] = species
    if count is not None:
        cond["count"] = _b(count, f=int)
    if species_count is not None:
        cond["species_count"] = _b(species_count, f=int)
    return C.one("aquarium", "abyssia:aquarium", cond)


def event(name, key=None):
    if name not in EVENTS:
        raise SystemExit(f"unknown event {name!r}")
    cond = {"event": name}
    if key:
        cond["key"] = key
    return C.one("event_" + name + ("_" + key if key else ""), "abyssia:event", cond)


def has(item):
    """inventory_changed: the item is in the inventory."""
    return C.one(item, "minecraft:inventory_changed", {"items": [{"items": _rid(item, NS)}]}, [("item", item)])


def eat(item):
    return C.one(item, "minecraft:consume_item", {"item": {"items": _rid(item, NS)}}, [("item", item)])


def placed(block, y_max=None):
    loc = [{"condition": "minecraft:block_state_property", "block": _rid(block, NS)}]
    if y_max is not None:
        loc.append({"condition": "minecraft:location_check", "predicate": {"position": {"y": _b(hi=y_max)}}})
    return C.one(block, "minecraft:placed_block", {"location": loc}, [("block", block)])


def effect(eff):
    return C.one(eff, "minecraft:effects_changed", {"effects": {_rid(eff, NS): {}}}, [("effect", eff)])


def biome(b):
    return C.one(b, "minecraft:location", {"player": [{"condition": "minecraft:entity_properties", "entity": "this",
                                                      "predicate": {"location": {"biomes": f"{NS}:{b}"}}}]},
                 [("biome", b)])


def hurt_by(entity):
    return C.one(entity, "minecraft:entity_hurt_player",
                 {"damage": {"source_entity": {"type": f"{NS}:{entity}"}}}, [("entity", entity)])


def killed(entity):
    return C.one(entity, "minecraft:player_killed_entity",
                 {"entity": [{"condition": "minecraft:entity_properties", "entity": "this",
                              "predicate": {"type": f"{NS}:{entity}"}}]}, [("entity", entity)])


def tick():
    return C.one("tick", "minecraft:tick")


# ================================================================ lists

SEAFLOOR = ("deep_sea abyssal_ocean abyssal_forest deep_forest abyssal_trench hadal_zone volcanic_deep thermal_vents "
            "deep_crystal_fields sunken_ruins bone_graveyard brine_lakes glow_gardens frost_abyss twilight_reef "
            "abyssal_rift deep_fissure").split()
ABYSS = ("abyss_plain abyss_garden abyss_crystal abyss_toxic abyss_toxic_vents abyss_volcanic abyss_magma "
         "abyss_geothermal abyss_frozen abyss_cryo abyss_anomaly abyss_ruins").split()
DISHES = ("cooked_abyssal_fish cooked_alfonsino cooked_angler_flesh cooked_black_scabbardfish cooked_blobfish "
          "cooked_blue_ling cooked_deepwater_redfish cooked_eelpout_flesh cooked_greenland_halibut cooked_orange_roughy "
          "cooked_patagonian_toothfish cooked_sablefish cooked_shark_flesh cooked_viper_flesh abyssal_vegetable_stew "
          "fish_soup gourd_fish_stew gourd_soup jellyfish_stew kelp_fish_stew kelp_soup mushroom_fish_stew mushroom_stew "
          "abyssal_survival_ration thermal_ration").split()
METALS = [m + "_ingot" for m in ("cobalt iridium lead manganese molybdenum neodymium nickel platinum tellurium thorium "
                                 "titanium tungsten uranium vanadium yttrium zinc").split()]
ALLOYS = [m + "_ingot" for m in ("abyssal_alloy conductive_alloy corrosion_alloy heat_resistant_alloy high_strength_alloy "
                                 "thermal_alloy tungsten_alloy").split()]
JELLIES = ("atolla_jelly", "helmet_jelly")
HABITAT_BUILDS = ("foundation", "room", "corridor", "entrance", "moon_pool", "scan_room")

ADV = []   # entries


def A(tab, name, parent, icon, frame, crit, en, ja, xp=0, hidden=False, noname=False):
    ADV.append(dict(tab=tab, name=name, parent=parent, icon=icon, frame=frame, crit=crit, en=en, ja=ja, xp=xp,
                    hidden=hidden, noname=noname))


def build_list(biomes_ok, dishes_ok):
    ADV.clear()
    seafloor = [b for b in SEAFLOOR if b in biomes_ok]
    abyss = [b for b in ABYSS if b in biomes_ok]
    dishes = list(dishes_ok)
    D = lambda *a, **k: A("dive", *a, **k)
    L = lambda *a, **k: A("life", *a, **k)
    E = lambda *a, **k: A("engineering", *a, **k)

    D("root", None, "abyss_guide_book", "task", tick(), ("Abyssia", "The ocean is deeper than you think"),
      ("アビシア", "海は、思っているよりずっと深い"))
    D("first_dive", "root", "entry_diving_flippers", "task", dive(submerged=True, y_max=50),
      ("Take the Plunge", "Dive below Y 50"), ("最初のひと潜り", "Y 50 より深く潜る"))
    D("suit_up", "first_dive", "entry_diver_helmet", "task", dive(tier_min=1),
      ("Suited Up", "Wear a full set of diving gear"), ("潜水士見習い", "潜水装備を一式そろえて身につける"))
    D("deep_diver", "suit_up", "deep_diver_helmet", "task", dive(tier_min=2),
      ("Deep Diver", "Wear a full set of deep diving gear"), ("深海潜水士", "深海用の潜水装備を一式身につける"))
    D("pressure_proof", "deep_diver", "pressure_diver_helmet", "goal", dive(tier_min=3),
      ("Pressure Proof", "Wear a full pressure suit"), ("耐圧潜水士", "耐圧潜水服を一式身につける"))
    D("twilight_zone", "first_dive", "minecraft:glow_ink_sac", "task", dive(submerged=True, y_max=0),
      ("Twilight Zone", "Dive below Y 0, where sunlight fades"), ("薄明の層", "日の光が消えていく Y 0 より深く潜る"))
    D("free_diver", "twilight_zone", "minecraft:kelp", "challenge", dive(submerged=True, y_max=0, pieces_max=0, in_submarine=False),
      ("Free Diver", "Reach Y 0 underwater without any diving gear or a submarine"),
      ("素潜り名人", "潜水具も潜水艇も使わずに Y 0 まで潜る"), xp=50)
    D("wreck_diver", "twilight_zone", "minecraft:chest", "task", scan(target="wreck_core"),
      ("Wreck Diver", "Scan the core of a sunken wreck"), ("沈没船ダイバー", "沈没船の中枢をスキャンする"))
    D("seafloor_surveyor", "twilight_zone", "deep_sea_map", "goal", each(*[biome(b) for b in seafloor]),
      ("Seafloor Surveyor", "Visit every ocean-floor biome"), ("海底踏査", "海底のバイオームをすべて訪れる"), xp=50)
    D("current_blessing", "first_dive", "minecraft:heart_of_the_sea", "task", effect("abyssal_current"),
      ("Blessing of the Current", "Gain Abyssal Current from a potion or a dish"),
      ("海流の加護", "ポーションや料理で深海の海流の加護を得る"))
    D("into_the_deep", "twilight_zone", "abyssal_rock", "task", dive(y_max=-64),
      ("Into the Deep", "Go below Y -64 into the deep layer"), ("深層へ", "Y -64 より下の深層に入る"))
    D("feel_the_pressure", "into_the_deep", "pressure_valve", "task", event("pressure_damage"),
      ("Feel the Pressure", "Take damage from water pressure. Better gear raises your safe depth"),
      ("水圧の洗礼", "水圧でダメージを受ける。装備が良いほど安全に潜れる深さが増す"))
    D("deep_sight", "into_the_deep", "minecraft:ender_eye", "task", effect("deep_sight"),
      ("Eyes of the Deep", "Gain Deep Sight"), ("深海の瞳", "深海視の効果を得る"))
    D("lightless", "into_the_deep", "abyssal_cave_rock", "task", dive(y_max=-300),
      ("Lightless Depths", "Reach Y -300"), ("光なき深み", "Y -300 に到達する"))
    D("close_call", "lightless", "minecraft:golden_apple", "challenge", dive(submerged=True, y_max=-376, health_max=2),
      ("Close Call", "Drop to one heart in the abyss"), ("九死に一生", "深淵で体力がハート 1 つ以下になる"), xp=50, hidden=True)
    D("lost_civilization", "lightless", "ancient_pressure_plate", "task",
      any_(*[biome(b) for b in ("sunken_ruins", "abyss_ruins") if b in biomes_ok]),
      ("Lost Civilization", "Find ancient ruins in the deep"), ("失われた文明", "深海の古代遺跡を見つける"))
    D("hot_water", "lightless", "thermal_crystal_shard", "task", event("hazard_damage", "heat"),
      ("Hot Water", "Get scalded in a volcanic cavern"), ("茹でダコ", "火山性の洞窟で熱にやられる"), hidden=True)
    D("toxic_shock", "lightless", "minecraft:fermented_spider_eye", "task", event("hazard_damage", "toxic"),
      ("Toxic Shock", "Breathe in the poison of a toxic cavern"), ("毒の海", "有毒な洞窟の毒にやられる"), hidden=True)
    D("cold_shock", "lightless", "minecraft:blue_ice", "task", effect("cold_shock"),
      ("Cold Shock", "Get chilled to the bone by freezing water"), ("骨まで凍える", "凍える海水で寒冷ショックを受ける"), hidden=True)
    D("the_abyss", "lightless", "deep_crystal_shard", "goal", dive(y_max=-376),
      ("The Abyss Gazes Back", "Reach the abyss below Y -376"), ("深淵もまたこちらを覗く", "Y -376 より下の深淵に到達する"))
    D("reckless_descent", "the_abyss", "minecraft:skeleton_skull", "task",
      dive(submerged=True, y_max=-376, tier_max=2, in_submarine=False),
      ("Reckless Descent", "Enter the abyss without a pressure suit or a submarine"),
      ("無謀な潜行", "耐圧潜水服も潜水艇もなしで深淵に入る"), hidden=True)
    D("abyss_cartographer", "the_abyss", "abyss_chart", "challenge", each(*[biome(b) for b in abyss]),
      ("Abyssal Cartographer", "Visit every abyss cavern biome"), ("深淵の地図職人", "深淵の洞窟バイオームをすべて訪れる"), xp=200)
    D("hadal", "the_abyss", "pressure_crystal_shard", "goal", dive(y_max=-860),
      ("Hadal Depths", "Reach Y -860"), ("超深海", "Y -860 に到達する"))
    D("rock_bottom", "hadal", "abyssal_crystal_shard", "challenge", dive(y_max=-980),
      ("Rock Bottom", "Reach the very bottom of the ocean (Y -980)"), ("どん底", "海のいちばん底（Y -980）にたどり着く"), xp=100)
    D("submarine", "first_dive", "submarine", "goal", dive(in_submarine=True),
      ("Dive, Dive, Dive!", "Pilot a submarine"), ("潜航開始！", "潜水艇に乗り込む"))
    D("hull_breach", "submarine", "submarine_upgrade_pressure_hull", "task", event("submarine_crushed"),
      ("Hull Breach", "Have your submarine crushed by water pressure"), ("船体圧壊", "水圧で潜水艇がつぶれる"), hidden=True)
    D("fully_loaded", "submarine", "submarine_upgrade_sonar_scanner", "goal", dive(in_submarine=True, upgrades_min=5),
      ("Fully Loaded", "Pilot a submarine with all five upgrade slots filled"),
      ("フル装備", "アップグレード 5 枠すべてを埋めた潜水艇に乗る"))
    D("captain_of_the_abyss", "fully_loaded", "submarine_upgrade_depth_mk3", "challenge", dive(in_submarine=True, y_max=-860),
      ("Captain of the Abyss", "Take a submarine below Y -860"), ("深淵の艦長", "潜水艇で Y -860 より深く潜る"), xp=150)

    # ---- life
    L("root", None, "lidar_scanner", "task", has("lidar_scanner"),
      ("Life of the Deep", "Get a lidar scanner and start recording the deep"),
      ("深海の生命", "ライダースキャナーを手に入れ、深海の記録を始める"))
    L("first_scan", "root", "lidar_scanner", "task", scan(count=1),
      ("First Entry", "Complete your first scan"), ("はじめての記録", "はじめてスキャンを完了する"))
    L("data_hoarder", "first_scan", "minecraft:writable_book", "task", scan(count=50),
      ("Data Hoarder", "Record 50 scan targets"), ("データ収集家", "スキャン対象を 50 件記録する"))
    L("archivist", "data_hoarder", "minecraft:bookshelf", "goal", scan(count=100),
      ("Archivist", "Record 100 scan targets"), ("深海アーカイブ", "スキャン対象を 100 件記録する"), xp=100)
    L("neighbours", "first_scan", "anglerfish_spawn_egg", "task", scan(group="fauna_", count=10),
      ("Neighbourhood Watch", "Record 10 kinds of deep-sea creature"), ("ご近所さん", "深海生物を 10 種記録する"))
    L("living_encyclopedia", "neighbours", "giant_squid_spawn_egg", "challenge", scan(group="fauna_", complete=True),
      ("Living Encyclopedia", "Record every deep-sea creature"), ("生きた百科事典", "深海生物をすべて記録する"), xp=300)
    L("kraken", "neighbours", "giant_squid_spawn_egg", "goal", scan(target="fauna_giant_squid"),
      ("Release the Kraken", "Scan a giant squid"), ("クラーケン現る", "ダイオウイカをスキャンする"))
    L("living_fossils", "neighbours", "frilled_shark_spawn_egg", "goal",
      each(*[scan(target="fauna_" + f) for f in ("frilled_shark", "goblin_shark", "chimaera", "sea_lily")]),
      ("Living Fossils", "Scan a frilled shark, a goblin shark, a chimaera and a sea lily"),
      ("生きた化石", "ラブカ・ミツクリザメ・ギンザメ・トリノアシをスキャンする"))
    L("light_show", "neighbours", "minecraft:glow_ink_sac", "task",
      each(*[scan(target="fauna_" + f) for f in ("firefly_squid", "lanternfish", "atolla_jelly", "stoplight_loosejaw")]),
      ("Light Show", "Scan four glowing creatures"), ("光のショー", "光る生き物を 4 種スキャンする"), noname=True)
    L("vent_community", "neighbours", "yunohana_crab_spawn_egg", "goal",
      each(*[scan(target="fauna_" + f) for f in ("scaly_foot_snail", "yunohana_crab", "satsuma_tubeworm", "vent_eelpout", "ohara_shrimp")]),
      ("Life Without Sunlight", "Scan five creatures of the hydrothermal vents"),
      ("太陽のいらない生態系", "熱水噴出孔の生き物を 5 種スキャンする"), noname=True)
    L("deepest_fish", "neighbours", "minecraft:cod", "goal", scan(target="fauna_mariana_snailfish"),
      ("The Deepest Fish", "Scan a Mariana snailfish"), ("いちばん深い魚", "マリアナスネイルフィッシュをスキャンする"))
    L("ugly_cute", "neighbours", "minecraft:pink_dye", "task", scan(target="fauna_blobfish"),
      ("Ugly-Cute", "Scan a blobfish"), ("ブサかわ", "ニュウドウカジカをスキャンする"), hidden=True)
    L("vampire_squid", "neighbours", "minecraft:ink_sac", "task", scan(target="fauna_vampire_squid"),
      ("Vampire Squid from Hell", "Scan a vampire squid"), ("地獄の吸血イカ", "コウモリダコをスキャンする"), hidden=True)
    L("botanist", "first_scan", "minecraft:kelp", "task", scan(group="plant_", count=15),
      ("Seafloor Botanist", "Record 15 kinds of deep-sea plant"), ("海底の植物学者", "深海植物を 15 種記録する"))
    L("herbarium", "botanist", "minecraft:glow_berries", "challenge", scan(group="plant_", complete=True),
      ("Herbarium of the Dark", "Record every deep-sea plant"), ("闇の植物標本館", "深海植物をすべて記録する"), xp=200)
    L("prospector", "first_scan", "minecraft:raw_iron", "task", scan(group="mineral_deposit_", count=10),
      ("Prospector", "Record 10 kinds of mineral deposit"), ("鉱脈探査士", "鉱床を 10 種記録する"))
    L("geologist", "prospector", "iridium_ingot", "challenge", scan(group="mineral_deposit_", complete=True),
      ("Master Geologist", "Record every kind of mineral deposit"), ("地質学の大家", "鉱床をすべて記録する"), xp=200)
    L("eureka", "first_scan", "minecraft:redstone_torch", "task", research(count=1),
      ("Eureka!", "Unlock your first technology"), ("ひらめいた！", "はじめて技術を解放する"))
    L("researcher", "eureka", "minecraft:book", "task", research(count=10),
      ("Researcher", "Unlock 10 technologies"), ("研究員", "技術を 10 個解放する"))
    L("chief_scientist", "researcher", "minecraft:enchanted_book", "goal", research(count=30),
      ("Chief Scientist", "Unlock 30 technologies"), ("主任研究員", "技術を 30 個解放する"))
    L("sage_of_the_deep", "chief_scientist", "abyss_guide_book", "challenge", research(complete=True),
      ("Sage of the Deep", "Unlock every technology"), ("深海の賢者", "すべての技術を解放する"), xp=500)
    L("catch_and_keep", "root", "creature_capture_canister_filled", "task", capture(),
      ("Catch and Keep", "Capture a creature in a capture canister"), ("生け捕り", "捕獲カニスターで生き物を捕まえる"))
    L("home_aquarium", "catch_and_keep", "creature_capture_canister_filled", "task", aquarium(),
      ("Home Aquarium", "Release a creature into an aquarium"), ("マイ水族館", "水槽に生き物を放す"))
    L("public_aquarium", "home_aquarium", "minecraft:tropical_fish_bucket", "goal", aquarium(species_count=4),
      ("Public Aquarium", "Keep four different species in one aquarium"), ("みんなの水族館", "1 つの水槽で 4 種類の生き物を飼う"))
    L("full_house", "home_aquarium", "creature_capture_canister", "task", aquarium(count=8),
      ("Full House", "Fill an aquarium to capacity"), ("満員御礼", "水槽を満員にする"))
    L("new_life", "home_aquarium", "minecraft:turtle_egg", "goal", event("aquarium_birth"),
      ("New Life", "Witness a birth in an aquarium"), ("新しい命", "水槽で生き物が生まれるのを見届ける"))
    L("taste_of_the_deep", "root", "cooked_abyssal_fish", "task", any_(*[eat(d) for d in dishes]),
      ("Taste of the Deep", "Eat a dish made from deep-sea ingredients"), ("深海の味", "深海の食材の料理を食べる"))
    L("sorry_blobby", "taste_of_the_deep", "cooked_blobfish", "task", eat("cooked_blobfish"),
      ("Sorry, Blobby", "Eat a cooked blobfish"), ("ごめんね、ブロブ", "ニュウドウカジカの料理を食べる"), hidden=True)
    L("deep_sea_gourmet", "taste_of_the_deep", "jellyfish_stew", "challenge", each(*[eat(d) for d in dishes]),
      ("Deep-Sea Gourmet", "Eat every deep-sea dish"), ("深海グルメ", "深海の料理をすべて食べる"), xp=150)
    L("lured_in", "root", "minecraft:lantern", "task", hurt_by("anglerfish"),
      ("Lured In", "Get bitten by an anglerfish"), ("提灯に誘われて", "チョウチンアンコウにかまれる"), hidden=True)
    L("stung", "root", "minecraft:slime_ball", "task", any_(*[hurt_by(j) for j in JELLIES]),
      ("Stung", "Get stung by a jellyfish"), ("クラゲに刺された", "クラゲに刺される"), hidden=True, noname=True)
    L("lights_out", "lured_in", "minecraft:iron_sword", "task", killed("anglerfish"),
      ("Lights Out", "Defeat an anglerfish"), ("提灯を消せ", "チョウチンアンコウを倒す"))

    # ---- engineering
    E("root", None, "habitat_constructor", "task",
      any_(*[has(i) for i in ("habitat_constructor", "abyssal_drill", "cobalt_ingot", "nickel_ingot", "zinc_ingot")]),
      ("Seabed Engineering", "Build, power and mine at the bottom of the sea"),
      ("海底工学", "海の底で建て、電気を起こし、掘る"))
    E("deep_metallurgy", "root", "cobalt_ingot", "task", any_(*[has(m) for m in METALS]),
      ("Deep Metallurgy", "Obtain a metal refined from deep-sea ore"), ("深海冶金", "深海の鉱石から金属を精錬する"))
    E("alloy_smith", "deep_metallurgy", "tungsten_alloy_ingot", "goal", each(*[has(a) for a in ALLOYS]),
      ("Alloy Smith", "Obtain every kind of alloy"), ("合金職人", "すべての合金を手に入れる"))
    E("rare_metals", "deep_metallurgy", "neodymium_ingot", "challenge",
      each(*[has(m + "_ingot") for m in ("neodymium", "yttrium", "tellurium", "iridium", "platinum", "molybdenum", "vanadium")]),
      ("Rare Metal Collector", "Obtain seven rare metals"), ("レアメタル収集家", "7 種のレアメタルを手に入れる"), xp=150)
    E("dont_lick_it", "deep_metallurgy", "uranium_ingot", "task", has("uranium_ingot"),
      ("Don't Lick It", "Obtain a uranium ingot"), ("なめちゃダメ", "ウランのインゴットを手に入れる"), hidden=True)
    E("crystal_collector", "root", "abyssal_crystal_shard", "goal",
      each(*[has(c + "_crystal_shard") for c in ("deep", "pressure", "thermal", "abyssal")]),
      ("Crystal Collector", "Collect all four kinds of crystal shard"), ("結晶コレクター", "4 種類の結晶の欠片を集める"))
    E("first_module", "root", "habitat_constructor", "task", build(),
      ("Home, Wet Home", "Complete your first habitat module"), ("海底のわが家", "はじめて居住モジュールを完成させる"))
    E("airlock", "first_module", "minecraft:iron_trapdoor", "task", build("entrance"),
      ("Airlock", "Build an entrance module"), ("エアロック", "出入口モジュールを建てる"))
    E("moon_pool", "first_module", "minecraft:water_bucket", "goal", build("moon_pool"),
      ("Moon Pool", "Build a moon pool"), ("ムーンプール", "ムーンプールを建てる"))
    E("scan_room", "first_module", "lidar_scanner", "task", build("scan_room"),
      ("Eyes on the Seafloor", "Build a scan room"), ("海底を見通す部屋", "スキャンルームを建てる"))
    E("seabase_architect", "first_module", "minecraft:glass_pane", "challenge", each(*[build(b) for b in HABITAT_BUILDS]),
      ("Seabase Architect", "Build every type of habitat module"), ("海底基地の建築家", "すべての種類の居住モジュールを建てる"), xp=100)
    E("power_on", "first_module", "minecraft:redstone", "task",
      any_(*[build(g) for g in ("biofuel_generator", "geothermal_generator", "current_turbine")],
           *[placed(g) for g in ("hydrothermal_generator", "auxiliary_generator")]),
      ("Power On", "Build or place a generator"), ("電源オン", "発電機を建てる、または設置する"))
    E("clean_energy", "power_on", "minecraft:lightning_rod", "goal",
      each(build("current_turbine"), any_(build("geothermal_generator"), placed("hydrothermal_generator"))),
      ("Clean Energy", "Build a current turbine and a geothermal or hydrothermal generator"),
      ("海のクリーンエネルギー", "海流タービンと、地熱か熱水の発電機を建てる"))
    E("blackout", "power_on", "minecraft:redstone_lamp", "task", event("habitat_blackout"),
      ("Blackout", "Run out of air in an unpowered habitat"), ("停電", "電気の止まった居住区で息が苦しくなる"), hidden=True)
    E("excavation", "root", "drill_head", "task", build("abyssal_excavator"),
      ("Deep Excavation", "Build an abyssal excavator"), ("海底採掘", "深海掘削機を建てる"))
    E("heavy_industry", "excavation", "electric_abyssal_drill", "goal", build("abyssal_excavator_mk2"),
      ("Heavy Industry", "Build an abyssal excavator Mk2"), ("重工業", "深海掘削機 Mk2 を建てる"))
    E("breadcrumbs", "root", "waypoint_beacon", "task", placed("waypoint_beacon"),
      ("Breadcrumbs", "Place a waypoint beacon to find your way home"), ("帰り道の灯", "帰り道の目印にウェイポイントビーコンを置く"))
    E("lantern_in_the_dark", "breadcrumbs", "waypoint_beacon", "goal", placed("waypoint_beacon", y_max=-860),
      ("Lantern in the Dark", "Place a waypoint beacon below Y -860"), ("闇の灯台", "Y -860 より深い場所にウェイポイントビーコンを置く"))
    E("shipwright", "root", "submarine", "task", has("submarine"),
      ("Shipwright", "Build a submarine"), ("造船技師", "潜水艇を造る"))
    E("dry_dock", "shipwright", "submarine", "task", build("submarine_dock"),
      ("Dry Dock", "Build a submarine dock"), ("潜水艇ドック", "潜水艇ドックを建てる"))


# ================================================================ resources / validation

def _lang_keys(name):
    text = open(os.path.join(ASSETS, "lang", name), encoding="utf-8").read()
    # the file may hold duplicate keys: parse with a regex, value kept (last wins)
    return {k: v for k, v in re.findall(r'^\s*"([^"]+)"\s*:\s*"((?:[^"\\]|\\.)*)"', text, re.M)}


def _biome_set(path):
    found = set()

    def walk(o):
        if isinstance(o, dict):
            for k, v in o.items():
                if k in ("biome", "fissure_biome") and isinstance(v, str):
                    found.add(v)
                else:
                    walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)
    walk(json.load(open(path, encoding="utf-8")))
    return found


def _vanilla_items():
    cands = glob.glob(os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"))
    if not cands:
        return None
    z = zipfile.ZipFile(cands[0])
    return {n[len("assets/minecraft/models/item/"):-5] for n in z.namelist()
            if n.startswith("assets/minecraft/models/item/") and n.endswith(".json")}


def _java_text():
    out = []
    for root, _, files in os.walk(os.path.join(JAVA, "com", "abyssia")):
        for f in files:
            if f.endswith(".java"):
                out.append(open(os.path.join(root, f), encoding="utf-8", errors="replace").read())
    return "\n".join(out)


def _obtainable(item):
    for p in glob.glob(os.path.join(DATA, "recipe", "*.json")):
        o = json.load(open(p, encoding="utf-8"))
        r = o.get("result")
        rid = r if isinstance(r, str) else (r or {}).get("id") or (r or {}).get("item")
        if rid == f"{NS}:{item}":
            return True
    for p in glob.glob(os.path.join(DATA, "loot_table", "**", "*.json"), recursive=True):
        if f'"{NS}:{item}"' in open(p, encoding="utf-8").read():
            return True
    return False


class Ctx:
    def __init__(self):
        en = _lang_keys("en_us.json")
        self.en = en
        self.ja = _lang_keys("ja_jp.json")
        self.items = {k.split(".", 2)[2] for k in en if k.startswith(("item.abyssia.", "block.abyssia."))}
        self.entities = {k.split(".", 2)[2] for k in en if k.startswith("entity.abyssia.")}
        self.effects = {k.split(".", 2)[2] for k in en if k.startswith("effect.abyssia.")}
        self.blocks = {k.split(".", 2)[2] for k in en if k.startswith("block.abyssia.")}
        self.biomes = {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(DATA, "worldgen", "biome", "*.json"))}
        self.targets = {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(DATA, "scan_targets", "*.json"))}
        self.techs = {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(DATA, "technologies", "*.json"))}
        self.vanilla = _vanilla_items()


def _sibling_java():
    """the other loader's checkout (NeoForge tree <-> Forge tree), found next to this one."""
    root = os.path.abspath(os.path.join(HERE, ".."))
    forge = os.path.isdir(os.path.join(DATA, "recipes"))
    other = os.path.join(os.path.dirname(root), "Abyssia-NeoForge" if forge else "Abyssia")
    if not os.path.isdir(other):
        raise SystemExit(f"other loader tree not found: {other} (needed to check items exist on both loaders)")
    return [os.path.join(root, "src", "main"), os.path.join(other, "src", "main")]


def _tree_info(main):
    """(build ids, item ids) of one tree.  Heuristics (Java is not compiled here):
    build ids = strings in BuildContent.applyOrder + `ID = "x"` constants in *Entry.java + GeneratorKind ids.
    an abyssia id is an ITEM if: lang key item.abyssia.<id>, or `items/ITEMS.register("<id>"`, or a
    `block("<id>"` / `rock("<id>"` / MachineKind `("<id>"` registration (those blocks get a BlockItem in ModIndustry.registerItems);
    plain `BLOCKS.register("<id>"` blocks have NO item (generator_part, excavator_part, habitat_window ...)."""
    jroot = os.path.join(main, "java", "com", "abyssia")
    text, builds = {}, set()
    for root, _, files in os.walk(jroot):
        for f in files:
            if f.endswith(".java"):
                t = open(os.path.join(root, f), encoding="utf-8", errors="replace").read()
                text[os.path.join(root, f)] = t
                if f.endswith("Entry.java"):
                    builds.update(re.findall(r'(?<![A-Za-z_])ID\s*=\s*"([a-z0-9_]+)"', t))
    bc = text.get(os.path.join(jroot, "habitat", "build", "BuildContent.java"), "")
    m = re.search(r"applyOrder\((.*?)\)\s*;", bc, re.S)
    builds.update(re.findall(r'"([a-z0-9_]+)"', m.group(1)) if m else [])
    gk = text.get(os.path.join(jroot, "habitat", "generator", "GeneratorKind.java"), "")
    builds.update(re.findall(r'^\s*[A-Z_]+\(\s*"([a-z0-9_]+)"', gk, re.M))
    if not builds:
        raise SystemExit(f"no build ids found in {main}")
    allj = "\n".join(text.values())
    en = json.load(open(os.path.join(main, "resources", "assets", "abyssia", "lang", "en_us.json"), encoding="utf-8"))
    items = {k.split(".", 2)[2] for k in en if k.startswith("item.abyssia.")}
    items.update(re.findall(r'(?:ITEMS|items)\.register\(\s*"([a-z0-9_]+)"', allj))
    items.update(re.findall(r'(?<![A-Za-z_])(?:block|rock)\(\s*"([a-z0-9_]+)"', allj))
    mk = text.get(os.path.join(jroot, "industry", "MachineKind.java"), "")
    items.update(re.findall(r'^\s*[A-Z_]+\(\s*"([a-z0-9_]+)"', mk, re.M))
    return builds, items


def check_item_ids(adv):
    """icon / inventory_changed / consume_item / placed_block ids of this mod: no build id, an item in both trees."""
    infos = [_tree_info(m) for m in _sibling_java()]
    errs = []
    for a in adv:
        used = [("icon", a["icon"])]
        for c in a["crit"].crits.values():
            cond = c.get("conditions", {})
            if c["trigger"] == "minecraft:inventory_changed":
                used.append((c["trigger"], cond["items"][0]["items"]))
            elif c["trigger"] == "minecraft:consume_item":
                used.append((c["trigger"], cond["item"]["items"]))
            elif c["trigger"] == "minecraft:placed_block":
                used.append((c["trigger"], cond["location"][0]["block"]))
        for what, ident in used:
            ident = str(ident)
            if ":" in ident:
                if not ident.startswith(NS + ":"):
                    continue
                ident = ident.split(":", 1)[1]
            for builds, items in infos:
                if ident in builds:
                    errs.append(f"{a['tab']}/{a['name']}: {what} {ident!r} is a build entry (use build(...))")
                    break
                if ident not in items:
                    errs.append(f"{a['tab']}/{a['name']}: {what} {ident!r} is not an item in both trees")
                    break
    if errs:
        raise SystemExit("item validation failed:\n  " + "\n  ".join(sorted(set(errs))))


def check_ref(ctx, kind, ident, where):
    ok = {"item": ctx.items, "block": ctx.blocks, "entity": ctx.entities, "effect": ctx.effects, "biome": ctx.biomes,
          "scan_target": ctx.targets, "technology": ctx.techs}[kind]
    if ":" in (ident or ""):
        return
    if ident not in ok:
        raise SystemExit(f"{where}: unknown {kind} {ident!r}")


def main():
    dry = "--dry-run" in sys.argv
    ctx = Ctx()
    notes = []

    # (a) biomes that generate in both world types
    both = None
    for p in (os.path.join(DATA, "worldgen", "world_preset", "ocean_world.json"),
              os.path.join(RES, "data", "minecraft", "worldgen", "world_preset", "normal.json")):
        s = {b.split(":", 1)[1] for b in _biome_set(p) if b.startswith(NS + ":")}
        both = s if both is None else both & s
    dropped_biomes = [b for b in SEAFLOOR + ABYSS if b not in both]
    if dropped_biomes:
        notes.append("dropped biomes (not in both world types): " + " ".join(dropped_biomes))

    # (b) dishes: edible (food(...) / foodMod(...) in Java) and obtainable (recipe result or loot table)
    java = _java_text()
    dishes, dropped = [], []
    for d in DISHES:
        edible = re.search(r'food(?:Mod)?\(\s*"' + d + '"', java) is not None
        if edible and _obtainable(d):
            dishes.append(d)
        else:
            dropped.append(d + ("" if edible else " (not edible)") + ("" if _obtainable(d) else " (no recipe/loot)"))
    if dropped:
        notes.append("dropped dishes: " + ", ".join(dropped))

    build_list(both, dishes)

    # validation
    names = set()
    for a in ADV:
        key = f"{a['tab']}/{a['name']}"
        where = f"advancement {key}"
        if a["tab"] not in TABS:
            raise SystemExit(f"{where}: bad tab")
        names.add(key)
        if a["parent"] is not None and f"{a['tab']}/{a['parent']}" not in {f"{x['tab']}/{x['name']}" for x in ADV}:
            raise SystemExit(f"{where}: unknown parent {a['parent']}")
        if not a["crit"].crits:
            raise SystemExit(f"{where}: no criteria")
        for kind, ident in a["crit"].refs:
            check_ref(ctx, kind, ident, where)
        icon = ICON_FALLBACK.get(a["icon"], a["icon"])
        if icon != a["icon"]:
            notes.append(f"icon {a['icon']} -> {icon} ({key})")
        a["icon_id"] = icon if ":" in icon else f"{NS}:{icon}"
        if ":" in icon:
            v = icon.split(":", 1)[1]
            if ctx.vanilla is not None and icon.startswith("minecraft:") and v not in ctx.vanilla:
                raise SystemExit(f"{where}: unknown vanilla icon {icon}")
        elif icon not in ctx.items:
            raise SystemExit(f"{where}: icon item {icon!r} does not exist (add it to ICON_FALLBACK)")
        # Japanese creature names must match the existing entity translations
        if not a["noname"]:
            fauna = [i for k, i in a["crit"].refs if k == "entity"] + \
                    [i[len("fauna_"):] for k, i in a["crit"].refs if k == "scan_target" and i.startswith("fauna_")]
            for e in fauna:
                ja = ctx.ja.get("entity.abyssia." + e)
                if ja and ja not in a["ja"][0] + a["ja"][1] and ja.replace("・", "") not in (a["ja"][0] + a["ja"][1]).replace("・", ""):
                    raise SystemExit(f"{where}: ja text lacks the creature name {ja!r} (entity.abyssia.{e})")
    check_item_ids(ADV)
    for t in TABS:
        tex = BACKGROUNDS[t].split(":", 1)[1]
        if not os.path.exists(os.path.join(ASSETS, *tex.split("/")[0:0], *tex.split("/"))):
            raise SystemExit(f"background texture missing: {BACKGROUNDS[t]}")

    # build files
    files, lang = {}, {}
    for a in ADV:
        t, n = a["tab"], a["name"]
        disp = {"icon": {"id": a["icon_id"]},
                "title": {"translate": f"advancements.{NS}.{t}.{n}.title"},
                "description": {"translate": f"advancements.{NS}.{t}.{n}.description"}}
        if a["frame"] != "task":
            disp["frame"] = a["frame"]
        if a["parent"] is None:
            disp["background"] = BACKGROUNDS[t]
            disp["show_toast"] = False
            disp["announce_to_chat"] = False
        if a["hidden"]:
            disp["hidden"] = True
        obj = {}
        if a["parent"] is not None:
            obj["parent"] = f"{NS}:{t}/{a['parent']}"
        obj["display"] = disp
        obj["criteria"] = a["crit"].crits
        obj["requirements"] = a["crit"].rows
        if a["xp"]:
            obj["rewards"] = {"experience": a["xp"]}
        files[(t, n)] = obj
        lang[f"advancements.{NS}.{t}.{n}.title"] = [a["en"][0], a["ja"][0]]
        lang[f"advancements.{NS}.{t}.{n}.description"] = [a["en"][1], a["ja"][1]]

    counts = {t: sum(1 for a in ADV if a["tab"] == t) for t in TABS}
    print(f"advancements: {counts} total {len(ADV)}; xp rewards {sum(1 for a in ADV if a['xp'])}; hidden {sum(1 for a in ADV if a['hidden'])}")
    for n in notes:
        print("NOTE", n)
    if dry:
        print("dry run: nothing written")
        return

    base = os.path.join(DATA, "advancement")
    for t in TABS:
        os.makedirs(os.path.join(base, t), exist_ok=True)
        for old in glob.glob(os.path.join(base, t, "*.json")):
            if (t, os.path.basename(old)[:-5]) not in files:
                os.remove(old)
    for (t, n), obj in files.items():
        with open(os.path.join(base, t, n + ".json"), "w", encoding="utf-8") as f:
            json.dump(obj, f, indent=2, ensure_ascii=False)
            f.write("\n")
    with open(LANG_PARTS, "w", encoding="utf-8") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"wrote {len(files)} advancements and {LANG_PARTS}")
    if "--regen-lang" in sys.argv:
        sys.path.insert(0, HERE)
        import gen_deep_assets
        gen_deep_assets.lang()
        print("regenerated assets/abyssia/lang/en_us.json + ja_jp.json (gen_deep_assets.lang)")


if __name__ == "__main__":
    main()
