"""Abyssia's deep-sea fauna, one module per species: research notes and INFO (names, sounds, spawn rule); models
come from tools/bbmodel-generator.  gen_fauna.py collects them through species()."""
import importlib

NAMES = ["anglerfish", "giant_isopod", "gulper_eel",
         # phase 2
         "viperfish", "goblin_shark", "barreleye", "yumenamako",
         # phase 3
         "giant_squid", "frilled_shark", "deep_sea_shrimp",
         # phase 4: the hydrothermal vent community
         "tubeworm", "satsuma_tubeworm", "yunohana_crab", "goemon_squat_lobster", "ohara_shrimp", "scaly_foot_snail",
         "vent_eelpout",
         # phase 5: deep-sea jellies (medusae)
         "silky_medusa", "atolla_jelly", "helmet_jelly", "giant_phantom_jelly",
         # 2026-09-29, bulk addition 1: midwater and near-floor fish (data-driven entities, INFO["java"])
         "stoplight_loosejaw", "black_dragonfish", "fangtooth", "hatchetfish", "lanternfish",
         "blobfish", "tripod_fish", "abyssal_grenadier", "black_swallower", "chimaera",
         # bulk addition 2: hadal fish, sharks, eel-like fish and cephalopods
         "mariana_snailfish", "pacific_sleeper_shark", "bluntnose_sixgill_shark", "cookiecutter_shark", "hagfish",
         "oarfish", "snipe_eel", "vampire_squid", "firefly_squid", "bigfin_squid",
         # bulk addition 3: seabed walkers, near-floor drifters, sessile animals and a medusa
         "japanese_spider_crab", "blind_lobster", "supergiant_amphipod", "sea_pig", "dumbo_octopus",
         "giant_sea_spider", "brittle_star", "sea_lily", "venus_flower_basket", "deepstaria"]


# Abyssia's vent fields lie 3500-7600 m deep on the mod's depth scale (abyssal plains and volcanic deeps), deeper
# than most real vents. Vent animals keep their real depths *relative to each other*: real depth d maps to
# 3400 + 1.25 d, so the shallow western Pacific vent fauna lives on the shallower fields and the East Pacific Rise /
# Indian Ocean fauna on the deeper ones, and every field has a community.
VENT_FIELDS_MIN, VENT_FIELDS_MAX = 3000, 8000


def vent_depth(real_min, real_max):
    """Spawn depth_m (min, core_min, core_max, max) of a vent animal whose real range is real_min-real_max metres."""
    core_min, core_max = round(3400 + 1.25 * real_min), round(3400 + 1.25 * real_max)
    return (max(VENT_FIELDS_MIN, core_min - 1000), core_min, core_max, min(VENT_FIELDS_MAX, core_max + 1500))


def species():
    return [importlib.import_module("fauna." + n) for n in NAMES]
