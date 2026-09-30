"""Seabed structure data for the deep ocean: what each biome's landmarks are and how often they appear.

Written by gen_worldgen.py (run that, not this) into the datapack registries the Java side reads
(com.abyssia.worldgen.structure):

  data/abyssia/abyssia/seabed_structure/<id>.json          one structure: category, tier, formation + parameters,
                                                           terrain conditions, dressing (plants/minerals), preferred fauna
  data/abyssia/abyssia/seabed_structure_profile/<biome>.json  which structures a biome grows, per-cell chance and spacing

Tiers and spacing (the grid cell is max_distance; one candidate per cell, realised with `chance`):
  small     5-15 blocks     cell  40-64   -> a few per 10 chunks of the biome
  medium    15-40 blocks    cell 128-192  -> one per few dozen chunks
  large     40-100 blocks   cell 320-448  -> one per few hundred chunks
  colossal  100+ blocks     cell 1024+    -> rare landmarks (category "landmark"), one per several thousand chunks
Most of the seabed stays plain on purpose: open, empty expanses are part of the deep sea.

Heights: structures stop under max_top_y (110 by default) so the water above Y 100 stays open; only colossal
landmarks may reach Y 125.
"""
import json
import os
import shutil

A = lambda n: "abyssia:" + n


def state(name, **props):
    s = {"Name": name if ":" in name else A(name)}
    if props:
        s["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return s


def mix(*items):
    """Weighted block mix: mix(("abyssal_rock", 4), "deep_sea_rock") - bare names weigh 1; dicts are ready states."""
    out = []
    for item in items:
        name, w = item if isinstance(item, tuple) else (item, 1)
        out.append({"data": name if isinstance(name, dict) else state(name), "weight": w})
    return out


def plants(*items):
    """Weighted plants: ("tube_plant", w) or (("giant_kelp", lo, hi), w) for stacking columns."""
    out = []
    for p, w in items:
        name, lo, hi = (p, 1, 1) if isinstance(p, str) else p
        e = {"state": state(name)}
        if lo != 1:
            e["min_height"] = lo
        if hi != 1:
            e["max_height"] = max(lo, hi)
        out.append({"data": e, "weight": w})
    return out


def span(lo, hi=None):
    return [lo, lo if hi is None else hi]


VENT = lambda t, a: state("thermal_vent", type=t, activity=a)
MOBS = lambda *names: [A(n) for n in names]

STRUCTURES = {}


def structure(name, category, tier, radius, max_height, formation, *, anchor="seabed", dressing=None, mobs=(), **conditions):
    d = {"category": category, "tier": tier, "footprint_radius": radius, "max_height": max_height, "formation": formation}
    if anchor != "seabed":
        d["anchor"] = anchor
    if conditions:
        d["conditions"] = conditions
    if dressing:
        d["dressing"] = dressing
    if mobs:
        d["preferred_mobs"] = list(mobs)
    assert name not in STRUCTURES, name
    STRUCTURES[name] = d


def dress(radius, density, plant_list=(), minerals=(), mineral_chance=0.0):
    d = {"radius": radius, "density": density}
    if plant_list:
        d["plants"] = plants(*plant_list)
    if minerals:
        d["minerals"] = mix(*minerals)
        d["mineral_chance"] = mineral_chance
    return d


def f(kind, **params):
    return {"type": kind, **{k: v for k, v in params.items() if v is not None}}


# ================================================================ Abyssal Plain (abyssal_ocean)
# A vast dark floor of ooze: isolated boulders, old outcrops, soft hills, rock masses, rare spires and a tower.

ABYSSAL_ROCK = mix(("abyssal_rock", 5), ("deep_sea_rock", 2))
MANGANESE = mix(("manganese_ore", 2), ("mineral_host_rock", 1))
PLAIN_LIFE = [("black_coral", 3), ("sponge_plant", 3), ("soul_coral", 2), (("tube_plant", 3, 8), 3), ("seafloor_pebbles", 3),
              ("manganese_nodules", 1),
              # resource plants (plant_defs.py): hard stalks, oil and light on the abyssal plain
              (("knotstalk", 2, 6), 2), ("oil_bladder_weed", 2), ("lumen_quill", 1)]

structure("abyssal_boulder", "geological", "small", 10, 10,
          f("rock", count=span(1, 3), spread=4, size=span(2, 4.5), height_ratio=0.75, sink=0.35, erosion=0.35,
            rock=ABYSSAL_ROCK, accent=MANGANESE, accent_chance=0.1),
          dressing=dress(10, 0.22, [("seafloor_pebbles", 3), ("sponge_plant", 2), ("black_coral", 1), ("manganese_nodules", 1)],
                         [("manganese_crust", 1)], 0.2),
          mobs=MOBS("giant_isopod"), max_slope=1.2, max_top_y=215)
structure("ancient_outcrop", "geological", "small", 14, 14,
          f("rock", count=span(1, 2), spread=5, size=span(3, 6), height_ratio=0.9, elongation=span(1.5, 2.5), sink=0.4, tilt=0.5,
            erosion=0.25, layers=2, rock=mix("abyssal_rock"), accent=mix(("mineral_host_rock", 2), ("deep_sea_rock", 1))),
          dressing=dress(12, 0.15, PLAIN_LIFE), mobs=MOBS("giant_isopod"), max_slope=1.5, max_top_y=215)
structure("abyssal_hill", "sediment", "medium", 40, 12,
          f("sediment", count=span(2, 4), spread=14, radius=span(10, 18), height=span(4, 9), surface=mix(("abyssal_mud", 3), ("deep_sediment", 1)),
            core=mix("deep_sediment"), layers=2, organic=mix("organic_sediment"), organic_chance=0.15),
          dressing=dress(36, 0.07, [(("abyssal_grass", 1, 3), 3), ("sponge_plant", 2), (("tube_plant", 2, 6), 2), ("seafloor_pebbles", 2)]),
          mobs=MOBS("yumenamako", "giant_isopod"), max_slope=0.5)
structure("abyssal_rock_mass", "geological", "medium", 24, 24,
          f("rock", count=span(1, 3), spread=8, size=span(6, 11), height_ratio=0.7, elongation=span(1.2, 2), sink=0.3, erosion=0.4,
            rock=ABYSSAL_ROCK, accent=MANGANESE, accent_chance=0.12),
          dressing=dress(24, 0.16, PLAIN_LIFE, [("manganese_crust", 1)], 0.15), mobs=MOBS("giant_isopod", "anglerfish"), max_slope=1.0)
structure("abyssal_crystal_outcrop", "crystal", "medium", 16, 18,
          f("crystal", count=span(4, 9), spread=5, length=span(4, 10), radius=span(0.9, 1.8), tilt=0.7, center_scale=1.6,
            crystal=mix(("deep_crystal_block", 3), ("blue_crystal_block", 1)), base=mix("crystal_rock"), base_radius=7, base_height=2),
          dressing=dress(14, 0.3, [("abyssal_crystal_cluster", 2), ("deep_crystal_cluster", 2), ("crystal_shards", 3)]),
          max_slope=0.8)
ABYSSAL_SPIRE = dict(taper=0.75, lean=0.1, ledges=0.18, erosion=0.3, cracks=0.1, broken_top=0.35, rock=ABYSSAL_ROCK,
                     accent=MANGANESE, accent_chance=0.12, apron=mix(("deep_sediment", 2), ("abyssal_rock", 1)), apron_height=3)
structure("abyssal_rock_spire", "geological", "large", 44, 60,
          f("pillar", count=span(1, 4), spread=18, height=span(20, 60), radius=span(3.5, 6.5), **ABYSSAL_SPIRE),
          dressing=dress(40, 0.12, PLAIN_LIFE + [(("deep_kelp", 6, 20), 2)], [("manganese_crust", 1)], 0.1),
          mobs=MOBS("giant_isopod", "anglerfish"), max_slope=1.2, max_y=90)
structure("abyssal_tower", "landmark", "colossal", 72, 100,
          f("pillar", count=span(3, 6), spread=32, height=span(60, 100), radius=span(7, 11),
            **{**ABYSSAL_SPIRE, "taper": 0.7, "ledges": 0.22, "cracks": 0.12, "broken_top": 0.25,
               "accent": mix(("manganese_ore", 2), ("cobalt_ore", 1), ("mineral_host_rock", 2)), "apron_height": 5}),
          dressing=dress(70, 0.1, PLAIN_LIFE + [(("deep_kelp", 10, 30), 2)], [("manganese_crust", 2), ("cobalt_crust", 1)], 0.15),
          mobs=MOBS("giant_isopod", "anglerfish", "goblin_shark"), max_slope=1.0, max_y=80, max_top_y=125)

# ================================================================ Abyssal Mud (the ooze-floored plains and deep basins)
# No separate mud biome: soft-sediment landforms grow where the floor is mud (abyssal plain, deep sea, hadal floor).

MUD = mix(("abyssal_mud", 3), ("deep_mud", 1))
MUD_LIFE = [("abyssal_moss", 3), ("seafloor_pebbles", 2), (("abyssal_grass", 1, 2), 2), ("sponge_plant", 1), ("silt_comb", 3)]
structure("mud_mounds", "sediment", "small", 14, 5,
          f("sediment", count=span(2, 5), spread=7, radius=span(3, 6), height=span(1.5, 3.5), surface=MUD, core=mix("deep_sediment")),
          dressing=dress(12, 0.1, MUD_LIFE), mobs=MOBS("yumenamako"), max_slope=0.6, max_top_y=215)
structure("mud_pockmark", "sediment", "small", 12, 4,
          f("crater", radius=span(4, 7), depth=span(1.5, 3), rim_height=span(1, 2), rim_width=0.4, roughness=0.3,
            floor=mix(("deep_mud", 2), ("organic_sediment", 1)), rim=MUD),
          dressing=dress(10, 0.12, [("abyssal_moss", 3), ("seafloor_pebbles", 1)]), mobs=MOBS("yumenamako"), max_slope=0.3, max_top_y=215)
structure("holothurian_mud_flat", "sediment", "medium", 32, 6,
          f("sediment", count=span(3, 6), spread=16, radius=span(8, 14), height=span(1, 2.5), surface=MUD, core=mix("deep_sediment"),
            organic=mix("organic_sediment"), organic_chance=0.4),
          dressing=dress(30, 0.14, MUD_LIFE + [("abyssal_mushroom", 1)]), mobs=MOBS("yumenamako", "giant_isopod"), max_slope=0.3)
structure("sediment_swell", "sediment", "large", 60, 14,
          f("sediment", count=span(3, 5), spread=26, radius=span(18, 30), height=span(5, 12), surface=mix(("deep_sediment", 3), ("abyssal_mud", 2)),
            core=mix("deep_sediment"), layers=3, organic=mix("organic_sediment"), organic_chance=0.1),
          dressing=dress(56, 0.05, MUD_LIFE), mobs=MOBS("yumenamako"), max_slope=0.5)

# ================================================================ Deep Sea (the general deep floor and the shelves)

DEEP_ROCK = mix(("deep_sea_rock", 4), ("mineral_host_rock", 1))
structure("sea_boulder", "geological", "small", 10, 9,
          f("rock", count=span(1, 3), spread=4, size=span(2, 4), height_ratio=0.8, sink=0.35, erosion=0.35, rock=DEEP_ROCK),
          dressing=dress(10, 0.25, [(("tube_plant", 2, 7), 3), ("glow_coral", 1), ("sponge_plant", 2), ("seafloor_pebbles", 2)]),
          mobs=MOBS("goemon_squat_lobster"), max_slope=1.2, max_top_y=215)
structure("rock_ridge", "geological", "medium", 34, 20,
          f("rock", count=span(2, 4), spread=12, size=span(5, 9), height_ratio=0.9, elongation=span(2.5, 4), sink=0.3, tilt=0.4, erosion=0.3,
            layers=3, rock=mix("deep_sea_rock"), accent=mix(("mineral_host_rock", 2), ("abyssal_rock", 1))),
          dressing=dress(30, 0.14, [(("tube_plant", 3, 8), 3), (("deep_kelp", 6, 22), 2), ("sponge_plant", 2), ("glow_coral", 1)]),
          mobs=MOBS("goemon_squat_lobster", "viperfish"), max_slope=1.0, max_top_y=215)

# ================================================================ Deep Trench (abyssal_trench)
# Cliffs, rifts, fallen blocks and pits leading further down.

TRENCH_ROCK = mix(("trench_rock", 5), ("mineral_host_rock", 1))
TRENCH_WALL = mix(("trench_rock", 4), ("mineral_host_rock", 2), ("cobalt_ore", 1))
TRENCH_LIFE = [(("giant_tube", 4, 12), 1), ("soul_coral", 2), ("black_coral", 2), ("pressure_crystal_cluster", 1),
               ("seafloor_pebbles", 2), (("knotstalk", 2, 6), 2), ("silt_comb", 1), ("lumen_quill", 1)]
structure("collapsed_blocks", "geological", "small", 14, 8,
          f("rock", count=span(3, 7), spread=7, size=span(1.5, 3.5), height_ratio=0.9, sink=0.3, tilt=0.8, erosion=0.4, rock=TRENCH_ROCK),
          dressing=dress(12, 0.15, TRENCH_LIFE), mobs=MOBS("frilled_shark"), max_slope=1.5)
structure("trench_fissure", "geological", "medium", 52, 4,
          f("trench", shape="fissure", length=span(40, 80), width=span(5, 10), depth=span(12, 24), meander=0.6, roughness=0.5, rim_height=1.5,
            wall=TRENCH_WALL, floor=mix(("deep_mud", 2), ("mineral_sediment", 1)), debris=TRENCH_ROCK, debris_count=span(2, 5)),
          dressing=dress(44, 0.06, TRENCH_LIFE), mobs=MOBS("frilled_shark", "gulper_eel"), min_y=-70, max_slope=0.6)
structure("trench_cliff", "geological", "medium", 38, 30,
          f("rock", count=span(2, 4), spread=12, size=span(6, 10), height_ratio=1.8, elongation=span(2.5, 4), sink=0.25, tilt=0.15, erosion=0.3,
            layers=3, rock=mix("trench_rock"), accent=mix(("mineral_host_rock", 2), ("abyssal_rock", 1))),
          dressing=dress(34, 0.1, TRENCH_LIFE), mobs=MOBS("frilled_shark"), max_slope=1.5)
structure("trench_shaft", "geological", "large", 26, 4,
          f("trench", shape="shaft", length=span(0), width=span(12, 22), depth=span(25, 45), roughness=0.6, rim_height=1,
            wall=TRENCH_WALL, floor=mix("deep_mud")),
          dressing=dress(24, 0.05, TRENCH_LIFE), mobs=MOBS("gulper_eel", "frilled_shark"), min_y=-60, max_slope=0.6)
structure("great_rift", "landmark", "colossal", 128, 6,
          f("trench", shape="fissure", length=span(180, 230), width=span(14, 24), depth=span(35, 60), meander=0.8, roughness=0.6, rim_height=3,
            wall=TRENCH_WALL, floor=mix(("deep_mud", 2), ("mineral_sediment", 1)), debris=TRENCH_ROCK, debris_count=span(6, 12), shaft=span(20, 40)),
          dressing=dress(120, 0.04, TRENCH_LIFE), mobs=MOBS("frilled_shark", "gulper_eel", "goblin_shark"), min_y=-40, max_slope=0.8)

# ================================================================ Hadal (hadal_zone)
# Bigger, darker, quieter, sparser: lone rock mountains, very tall pillars, pressure crystals, giant craters.

HADAL_ROCK = mix(("trench_rock", 4), ("abyssal_rock", 2))
HADAL_ACCENT = mix(("cobalt_ore", 2), ("deep_nickel_ore", 1), ("mineral_host_rock", 2))
HADAL_LIFE = [("black_coral", 3), ("hadal_bloom", 1), ("pressure_crystal_cluster", 1), ("seafloor_pebbles", 2), ("silt_comb", 1),
              ("pressure_gourd", 1)]
structure("hadal_monolith", "geological", "medium", 30, 36,
          f("rock", count=span(1, 2), spread=6, size=span(7, 12), height_ratio=1.3, elongation=span(1, 1.5), sink=0.2, erosion=0.35, layers=4,
            rock=mix("trench_rock"), accent=mix("abyssal_rock")),
          dressing=dress(26, 0.06, HADAL_LIFE), mobs=MOBS("goblin_shark"), max_slope=1.0)
structure("hadal_pressure_crystals", "crystal", "medium", 18, 16,
          f("crystal", count=span(5, 10), spread=6, length=span(3, 9), radius=span(0.8, 1.6), tilt=0.6, center_scale=1.5,
            crystal=mix(("white_crystal_block", 3), ("violet_crystal_block", 1)), core=mix("deep_crystal_block"),
            base=mix("trench_rock"), base_radius=6, base_height=1.5),
          dressing=dress(16, 0.3, [("pressure_crystal_cluster", 3), ("pale_crystal_cluster", 2), ("crystal_shards", 1)]), max_slope=0.8)
structure("hadal_pillar", "geological", "large", 38, 80,
          f("pillar", count=span(1, 3), spread=16, height=span(35, 75), radius=span(4, 8), taper=0.6, lean=0.05, ledges=0.2, erosion=0.25,
            cracks=0.12, broken_top=0.4, rock=HADAL_ROCK, accent=HADAL_ACCENT, accent_chance=0.1,
            apron=mix(("deep_sediment", 1), ("trench_rock", 1)), apron_height=3),
          dressing=dress(34, 0.06, HADAL_LIFE, [("cobalt_crust", 1), ("nickel_crust", 1)], 0.1), mobs=MOBS("goblin_shark"), max_slope=1.2)
structure("hadal_crater", "geological", "large", 60, 6,
          f("crater", radius=span(22, 34), depth=span(6, 12), rim_height=span(2, 4), rim_width=0.35, roughness=0.4,
            floor=mix(("deep_mud", 2), ("abyssal_mud", 1)), rim=mix(("trench_rock", 2), ("deep_sediment", 1)), ejecta=mix("trench_rock"), ejecta_density=0.04),
          dressing=dress(48, 0.03, HADAL_LIFE), min_y=-105, max_slope=0.3)
structure("hadal_great_crater", "landmark", "colossal", 140, 10,
          f("crater", radius=span(60, 80), depth=span(14, 24), rim_height=span(4, 8), rim_width=0.3, peak=0.6, roughness=0.5,
            floor=mix(("deep_mud", 2), ("abyssal_mud", 1)), rim=mix(("trench_rock", 3), ("abyssal_rock", 1)), ejecta=HADAL_ROCK, ejecta_density=0.03),
          dressing=dress(120, 0.02, HADAL_LIFE), mobs=MOBS("goblin_shark", "giant_squid"), min_y=-100, max_slope=0.35)

# ================================================================ Hydrothermal (thermal_vents)
# Chimney colonies of mixed size on mineral mounds, sulfur heaps, mineral terraces; a great smoker field as landmark.

VENT_LIFE = [(("thermal_tube", 2, 6), 4), ("heat_moss", 2), ("vent_grass", 2), ("thermal_plant", 1), ("sulfur_cluster", 1)]
VENT_MOBS = MOBS("tubeworm", "satsuma_tubeworm", "deep_sea_shrimp", "yunohana_crab", "ohara_shrimp", "scaly_foot_snail", "vent_eelpout")
CHIMNEY = mix(("black_vent_rock", 3), ("vent_rock", 2), ("mineral_vent_rock", 1))
SULFIDE = mix(("black_mineral_deposit", 2), ("sulfur_vent_rock", 1))
VENT_MOUND = mix(("mineral_sediment", 2), ("black_mineral_deposit", 1), ("sulfur_deposit", 1))
structure("sulfur_mound", "thermal", "small", 10, 6,
          f("vent", count=span(1, 2), spread=3, height=span(1, 3), width=span(1, 2), chimney=mix("sulfur_vent_rock"),
            core=mix((VENT("mineral", "weak"), 1)), mound=mix(("sulfur_deposit", 2), ("mineral_sediment", 1)), mound_radius=4, mound_height=2, flanges=0),
          dressing=dress(10, 0.3, VENT_LIFE), mobs=VENT_MOBS, max_slope=0.5)
structure("chimney_cluster", "thermal", "medium", 26, 24,
          f("vent", count=span(4, 9), spread=10, height=span(6, 18), width=span(1.2, 2.5), chimney=CHIMNEY, accent=SULFIDE, accent_chance=0.15,
            core=mix((VENT("black_smoker", "active"), 2), (VENT("white_smoker", "active"), 1)), mound=VENT_MOUND, mound_radius=6, mound_height=3,
            flanges=0.5),
          dressing=dress(26, 0.35, VENT_LIFE, [("sulfur_deposit", 1), ("copper_crust", 1)], 0.2), mobs=VENT_MOBS, avoid_vent_fields=False, max_slope=0.4)
structure("mineral_terraces", "thermal", "medium", 24, 8,
          f("sediment", count=span(3, 6), spread=9, radius=span(4, 8), height=span(2, 5), surface=mix(("mineral_sediment", 2), ("sulfur_deposit", 1)),
            core=mix("black_mineral_deposit"), layers=1),
          dressing=dress(22, 0.25, VENT_LIFE), mobs=VENT_MOBS, avoid_vent_fields=False, max_slope=0.5)
structure("great_smoker_field", "landmark", "colossal", 100, 45,
          f("vent", count=span(18, 30), spread=70, height=span(14, 38), width=span(1.8, 3.2), lean=0.08, chimney=CHIMNEY, accent=SULFIDE, accent_chance=0.2,
            core=mix((VENT("black_smoker", "strong"), 3), (VENT("white_smoker", "active"), 1), (VENT("superheated", "superheated"), 1)),
            mound=VENT_MOUND, mound_radius=12, mound_height=5, flanges=0.6),
          dressing=dress(96, 0.3, VENT_LIFE, [("sulfur_deposit", 2), ("copper_crust", 1)], 0.25), mobs=VENT_MOBS, avoid_vent_fields=False, max_slope=0.4, max_top_y=125)

# ================================================================ Volcanic (volcanic_deep)
# Vent cones, lava ridges, black pinnacles, craters; a submerged volcano is large, a great one is the landmark.

VOLCANIC = mix(("volcanic_rock", 4), ("black_vent_rock", 1))
RIM = mix(("black_vent_rock", 2), ("volcanic_rock", 1))
CRATER_FLOOR = mix(("molten_volcanic_rock", 2), ("volcanic_glass", 2), ("volcanic_rock", 1))
LAVA = mix(("molten_volcanic_rock", 1), ("volcanic_glass", 1))
ASH = mix(("volcanic_ash", 3), ("volcanic_rock", 1))
VOLCANIC_LIFE = [("heat_moss", 3), ("thermal_plant", 1), (("thermal_tube", 1, 4), 1), ("seafloor_pebbles", 1), ("cinder_stalk", 2)]
structure("volcanic_vent", "thermal", "small", 16, 8,
          f("volcano", radius=span(5, 8), height=span(3, 7), crater=span(0.3, 0.4), crater_depth=span(0.3, 0.5), gullies=0.3,
            rock=VOLCANIC, rim=RIM, crater_floor=CRATER_FLOOR, core=mix((VENT("black_smoker", "strong"), 1)), ash=ASH, ash_radius=1.8, lava=0),
          dressing=dress(14, 0.15, VOLCANIC_LIFE), mobs=MOBS("vent_eelpout"), max_slope=0.5)
structure("black_pinnacles", "geological", "small", 12, 14,
          f("pillar", count=span(1, 3), spread=5, height=span(5, 12), radius=span(1.2, 2.2), taper=1.0, lean=0.15, ledges=0.25, erosion=0.35,
            cracks=0.1, broken_top=0.5, rock=VOLCANIC, accent=mix("volcanic_glass"), accent_chance=0.1),
          dressing=dress(12, 0.1, VOLCANIC_LIFE), max_slope=1.2)
structure("lava_ridge", "geological", "medium", 28, 10,
          f("rock", count=span(3, 6), spread=12, size=span(2.5, 5), height_ratio=0.5, elongation=span(2, 3.5), sink=0.45, erosion=0.4,
            rock=VOLCANIC, accent=LAVA, accent_chance=0.15),
          dressing=dress(26, 0.08, VOLCANIC_LIFE), max_slope=0.8)
structure("volcanic_crater", "thermal", "medium", 40, 14,
          f("volcano", radius=span(18, 28), height=span(6, 12), crater=span(0.55, 0.7), crater_depth=span(0.8, 1.3), gullies=0.2,
            rock=VOLCANIC, rim=RIM, crater_floor=mix(("volcanic_glass", 2), ("molten_volcanic_rock", 1)), ash=ASH, ash_radius=1.4, lava=0.2, lava_mix=LAVA),
          dressing=dress(36, 0.06, VOLCANIC_LIFE), avoid_vent_fields=False, max_slope=0.4)
structure("submerged_volcano", "thermal", "large", 84, 45,
          f("volcano", radius=span(40, 60), height=span(22, 42), crater=span(0.18, 0.26), crater_depth=span(0.25, 0.4), gullies=0.5, cones=span(1, 3),
            rock=VOLCANIC, rim=RIM, crater_floor=CRATER_FLOOR, core=mix((VENT("superheated", "superheated"), 1)), ash=ASH, ash_radius=1.35,
            lava=0.35, lava_mix=LAVA),
          dressing=dress(80, 0.04, VOLCANIC_LIFE), mobs=MOBS("vent_eelpout"), avoid_vent_fields=False, max_slope=0.5, max_y=85)
structure("great_submerged_volcano", "landmark", "colossal", 140, 70,
          f("volcano", radius=span(85, 105), height=span(45, 70), crater=span(0.15, 0.2), crater_depth=span(0.25, 0.35), gullies=0.6, cones=span(3, 6),
            rock=VOLCANIC, rim=RIM, crater_floor=CRATER_FLOOR, core=mix((VENT("superheated", "superheated"), 1)), ash=ASH, ash_radius=1.3,
            lava=0.45, lava_mix=LAVA),
          dressing=dress(130, 0.03, VOLCANIC_LIFE), mobs=MOBS("vent_eelpout"), avoid_vent_fields=False, max_slope=0.5, max_y=70, max_top_y=125)

# ================================================================ Crystal (deep_crystal_fields)
# Small, medium and large crystal groups together; toppled crystals spanning like bridges; a crystal cathedral.

CRYSTAL = mix(("deep_crystal_block", 3), ("cyan_crystal_block", 2), ("blue_crystal_block", 1))
CRYSTAL_LIFE = [("deep_crystal_cluster", 3), ("crystal_shards", 3), ("crystal_plant", 2), ("crystal_needle", 1)]
structure("crystal_cluster", "crystal", "small", 9, 10,
          f("crystal", count=span(3, 6), spread=2.5, length=span(2, 5), radius=span(0.6, 1.2), tilt=0.7, center_scale=1.4, crystal=CRYSTAL,
            base=mix("crystal_rock"), base_radius=3, base_height=1),
          dressing=dress(9, 0.3, CRYSTAL_LIFE), max_slope=1.0, max_top_y=215)
structure("crystal_outcrop", "crystal", "medium", 20, 24,
          f("crystal", count=span(6, 12), spread=6, length=span(6, 14), radius=span(1, 2.2), tilt=0.75, center_scale=1.5, crystal=CRYSTAL,
            core=mix(("white_crystal_block", 1), ("cyan_crystal_block", 1)), base=mix(("crystal_rock", 3), ("crystal_sediment", 1)), base_radius=9, base_height=2),
          dressing=dress(20, 0.28, CRYSTAL_LIFE), max_slope=0.8)
structure("fallen_crystal_spans", "crystal", "medium", 30, 14,
          f("crystal", count=span(2, 4), spread=3, length=span(14, 24), radius=span(1.2, 2), tilt=1.3, center_scale=1.1, crystal=CRYSTAL,
            core=mix("white_crystal_block"), base=mix("crystal_rock"), base_radius=5, base_height=1.5),
          dressing=dress(20, 0.2, CRYSTAL_LIFE), max_slope=0.8)
structure("large_crystal_cluster", "crystal", "large", 48, 45,
          f("crystal", count=span(10, 18), spread=14, length=span(12, 34), radius=span(2, 4), tilt=0.8, center_scale=1.5, crystal=CRYSTAL,
            core=mix("white_crystal_block"), base=mix(("crystal_rock", 3), ("crystal_sediment", 1)), base_radius=16, base_height=4),
          dressing=dress(38, 0.22, CRYSTAL_LIFE), max_slope=0.8, max_y=90)
structure("crystal_cathedral", "landmark", "colossal", 100, 70,
          f("crystal", count=span(18, 30), spread=40, length=span(25, 60), radius=span(3, 6), tilt=0.85, center_scale=1.4,
            crystal=mix(("deep_crystal_block", 3), ("cyan_crystal_block", 2), ("violet_crystal_block", 1)), core=mix("white_crystal_block"),
            base=mix(("crystal_rock", 3), ("crystal_sediment", 1)), base_radius=36, base_height=6),
          dressing=dress(80, 0.2, CRYSTAL_LIFE), max_slope=0.8, max_y=80, max_top_y=125)

# ================================================================ Vegetation (abyssal_forest, deep_forest)
# Communities as structures: giant kelp forests with glades, kelp walls and tunnels, overgrown hills, tube groves.

KELP = plants((("giant_kelp", 30, 80), 4), (("deep_kelp", 20, 45), 2))
UNDER = plants((("tube_plant", 2, 6), 3), ("sponge_plant", 2), (("abyssal_grass", 1, 3), 3), ("glowtip_grass", 1), ("sea_fern", 1))
SOIL = mix(("organic_sediment", 3), ("abyssal_mud", 1))
FOREST_MOBS = MOBS("barreleye", "viperfish", "deep_sea_shrimp")
structure("kelp_wall", "vegetation", "medium", 38, 80,
          f("vegetation", shape="wall", radius=span(20, 32), width=span(4, 7), density=0.7, clearings=0.1, plants=KELP, understory=UNDER,
            understory_density=0.2, soil=SOIL, soil_chance=0.6),
          mobs=FOREST_MOBS, max_top_y=215, max_slope=0.8)
structure("kelp_tunnel", "vegetation", "medium", 42, 80,
          f("vegetation", shape="tunnel", radius=span(24, 36), width=span(4, 6), density=0.85, clearings=0, plants=KELP, understory=UNDER,
            understory_density=0.3, soil=SOIL, soil_chance=0.7),
          mobs=FOREST_MOBS, max_top_y=215, max_slope=0.6)
structure("overgrown_hill", "vegetation", "medium", 26, 50,
          f("vegetation", shape="hill", radius=span(10, 18), density=0.35, clearings=0.1,
            plants=plants((("deep_kelp", 8, 25), 3), (("giant_tube", 5, 14), 2)), understory=UNDER, understory_density=0.4,
            mound=mix(("organic_sediment", 2), ("abyssal_mud", 1)), mound_height=span(4, 8), soil=SOIL, soil_chance=0.5),
          mobs=FOREST_MOBS, max_top_y=215, max_slope=0.6)
structure("giant_tube_grove", "vegetation", "medium", 30, 20,
          f("vegetation", shape="grove", radius=span(14, 24), density=0.45, clearings=0.2, plants=plants((("giant_tube", 6, 18), 1)),
            understory=UNDER, understory_density=0.3, soil=SOIL, soil_chance=0.4),
          mobs=FOREST_MOBS, max_top_y=215, max_slope=0.8)
structure("giant_kelp_forest", "vegetation", "large", 74, 80,
          f("vegetation", shape="grove", radius=span(35, 60), density=0.35, clearings=0.25, plants=KELP, understory=UNDER,
            understory_density=0.25, soil=SOIL, soil_chance=0.5),
          mobs=FOREST_MOBS + MOBS("giant_squid"), max_top_y=215, max_slope=0.8)
structure("ancient_kelp_forest", "landmark", "colossal", 128, 90,
          f("vegetation", shape="grove", radius=span(80, 105), density=0.42, clearings=0.2,
            plants=plants((("giant_kelp", 45, 90), 5), (("deep_kelp", 25, 50), 1)), understory=UNDER, understory_density=0.3,
            soil=SOIL, soil_chance=0.6),
          mobs=FOREST_MOBS + MOBS("giant_squid"), max_slope=0.8, max_top_y=215)

# ================================================================ Caverns (large caverns under each biome)
# Fill big cave volumes: floor-to-roof columns, hanging rock, stalagmite clusters, crystal groups, plant colonies.

def cavern_set(prefix, rock, accent, *, crystals=None, plant_list=None, mobs=()):
    """Cavern structures in one rock palette; returns their names."""
    names = []
    structure(f"{prefix}_cavern_column", "cave", "medium", 14, 120,
              f("cave", shape="column", count=span(1, 2), spread=5, radius=span(2.5, 5), waist=0.45, erosion=0.3, rock=rock, accent=accent,
                accent_chance=0.12),
              anchor="cavern", mobs=mobs, min_clearance=10, max_top_y=240)
    structure(f"{prefix}_hanging_rock", "cave", "small", 10, 60,
              f("cave", shape="hanging", count=span(2, 5), spread=4, radius=span(1.5, 3.5), length=span(0.2, 0.5), erosion=0.3, rock=rock),
              anchor="cavern", min_clearance=10, max_top_y=240)
    structure(f"{prefix}_stalagmites", "cave", "small", 8, 40,
              f("cave", shape="stalagmites", count=span(3, 6), spread=3.5, radius=span(1, 2), length=span(0.15, 0.35), erosion=0.25, rock=rock),
              anchor="cavern", min_clearance=8, max_top_y=240)
    names += [f"{prefix}_cavern_column", f"{prefix}_hanging_rock", f"{prefix}_stalagmites"]
    if crystals:
        structure(f"{prefix}_cavern_crystals", "crystal", "medium", 20, 40,
                  f("crystal", count=span(5, 10), spread=5, length=span(5, 14), radius=span(1, 2.2), tilt=0.8, center_scale=1.5, crystal=crystals,
                    core=mix("white_crystal_block"), hanging=0.35),
                  anchor="cavern", dressing=dress(14, 0.3, CRYSTAL_LIFE + [("cave_crystal_plant", 2)]), min_clearance=12, max_top_y=240)
        names.append(f"{prefix}_cavern_crystals")
    if plant_list:
        structure(f"{prefix}_cavern_garden", "vegetation", "small", 12, 30,
                  f("vegetation", shape="grove", radius=span(6, 10), density=0.4, clearings=0.1, plants=plants(*plant_list),
                    understory=plants(("cave_fern", 2), (("cave_grass", 1, 3), 3), ("cave_bloom", 1)), understory_density=0.3,
                    soil=mix("organic_sediment"), soil_chance=0.4),
                  anchor="cavern", mobs=mobs, min_clearance=8, max_top_y=240)
        names.append(f"{prefix}_cavern_garden")
    return names


CAVE_ABYSSAL = cavern_set("abyssal", mix(("abyssal_cave_rock", 3), ("dark_cave_rock", 2), ("layered_cave_rock", 1)), mix("mineral_cave_rock"),
                          plant_list=[(("cave_kelp", 4, 14), 3), (("cave_tube_plant", 2, 6), 2)], mobs=MOBS("goemon_squat_lobster"))
CAVE_TRENCH = cavern_set("trench", mix(("dark_cave_rock", 3), ("trench_rock", 2)), mix("mineral_cave_rock"))
CAVE_FOREST = cavern_set("forest", mix(("organic_cave_rock", 3), ("wet_cave_rock", 2)), mix("layered_cave_rock"),
                         plant_list=[(("cave_kelp", 6, 20), 4), (("cave_tube_plant", 2, 6), 2), ("cave_coral", 1)], mobs=MOBS("barreleye"))
CAVE_CRYSTAL = cavern_set("crystal", mix(("crystal_cave_rock", 3), ("layered_cave_rock", 1)), mix("crystal_rock"),
                          crystals=mix(("deep_crystal_block", 2), ("cyan_crystal_block", 2), ("violet_crystal_block", 1)))
CAVE_THERMAL = cavern_set("thermal", mix(("thermal_cave_rock", 3), ("mineral_cave_rock", 2)), mix("black_mineral_deposit"),
                          mobs=MOBS("vent_eelpout"))

# ================================================================ profiles

SMALL, MEDIUM, LARGE, COLOSSAL = (20, 40), (64, 128), (160, 288), (640, 1280)
CAVERN_SMALL, CAVERN_MEDIUM = (16, 36), (28, 56)


def e(name, chance, spacing):
    lo, hi = spacing
    return {"structure": A(name), "chance": chance, "min_distance": lo, "max_distance": hi}


def cavern_entries(names, chance=0.7):
    out = []
    for n in names:
        tier = STRUCTURES[n]["tier"]
        out.append(e(n, chance if tier == "small" else chance * 0.8, CAVERN_SMALL if tier == "small" else CAVERN_MEDIUM))
    return out


PROFILES = {
    "abyssal_ocean": (["abyssal_ocean"], 1.0, [
        e("abyssal_boulder", 0.45, SMALL), e("ancient_outcrop", 0.25, (28, 56)), e("mud_mounds", 0.35, SMALL), e("mud_pockmark", 0.2, (28, 56)),
        e("abyssal_hill", 0.5, (64, 144)), e("abyssal_rock_mass", 0.45, MEDIUM), e("abyssal_crystal_outcrop", 0.25, (80, 160)),
        e("holothurian_mud_flat", 0.35, (80, 160)),
        e("abyssal_rock_spire", 0.6, (160, 288)), e("sediment_swell", 0.3, LARGE),
        e("abyssal_tower", 0.45, (700, 1280)),
        *cavern_entries(CAVE_ABYSSAL)]),
    "deep_sea": (["deep_sea"], 1.0, [
        e("sea_boulder", 0.4, SMALL), e("mud_mounds", 0.3, SMALL), e("rock_ridge", 0.4, MEDIUM), e("holothurian_mud_flat", 0.2, (72, 144)),
        e("sediment_swell", 0.25, LARGE),
        *cavern_entries(CAVE_ABYSSAL, 0.6)]),
    "abyssal_trench": (["abyssal_trench"], 1.0, [
        e("collapsed_blocks", 0.45, SMALL), e("trench_fissure", 0.55, (72, 144)), e("trench_cliff", 0.4, MEDIUM),
        e("trench_shaft", 0.45, (176, 304)),
        e("great_rift", 0.45, (800, 1280)),
        *cavern_entries(CAVE_TRENCH)]),
    "hadal_zone": (["hadal_zone"], 1.0, [
        e("mud_mounds", 0.15, (40, 80)), e("hadal_monolith", 0.35, (72, 144)), e("hadal_pressure_crystals", 0.25, (80, 160)),
        e("hadal_pillar", 0.5, (192, 352)), e("hadal_crater", 0.35, (224, 384)),
        e("hadal_great_crater", 0.4, (900, 1536)),
        *cavern_entries(CAVE_TRENCH, 0.5)]),
    "thermal_vents": (["thermal_vents"], 1.0, [
        e("sulfur_mound", 0.5, SMALL), e("chimney_cluster", 0.6, (72, 144)), e("mineral_terraces", 0.4, MEDIUM),
        e("great_smoker_field", 0.5, (700, 1280)),
        *cavern_entries(CAVE_THERMAL)]),
    "volcanic_deep": (["volcanic_deep"], 1.0, [
        e("volcanic_vent", 0.45, (28, 56)), e("black_pinnacles", 0.35, SMALL), e("lava_ridge", 0.45, MEDIUM),
        e("volcanic_crater", 0.45, (80, 160)), e("submerged_volcano", 0.5, (224, 384)),
        e("great_submerged_volcano", 0.45, (900, 1536)),
        *cavern_entries(CAVE_THERMAL)]),
    "deep_crystal_fields": (["deep_crystal_fields"], 1.0, [
        e("crystal_cluster", 0.55, SMALL), e("crystal_outcrop", 0.5, MEDIUM), e("fallen_crystal_spans", 0.3, (72, 144)),
        e("large_crystal_cluster", 0.55, (176, 304)),
        e("crystal_cathedral", 0.45, (700, 1280)),
        *cavern_entries(CAVE_CRYSTAL, 0.8)]),
    "abyssal_forest": (["abyssal_forest"], 1.0, [
        e("kelp_wall", 0.45, (72, 144)), e("kelp_tunnel", 0.4, (80, 160)), e("overgrown_hill", 0.45, MEDIUM),
        e("giant_kelp_forest", 0.6, (192, 352)),
        e("ancient_kelp_forest", 0.45, (700, 1280)),
        *cavern_entries(CAVE_FOREST)]),
    "deep_forest": (["deep_forest"], 1.0, [
        e("giant_tube_grove", 0.5, MEDIUM), e("overgrown_hill", 0.4, MEDIUM), e("kelp_wall", 0.3, (72, 144)),
        e("giant_kelp_forest", 0.4, LARGE),
        *cavern_entries(CAVE_FOREST, 0.6)]),
}


def write(root, deep_biomes):
    """Writes the registries under root (data/abyssia/abyssia); returns (structures, profiles)."""
    covered = {b for biomes, _, _ in PROFILES.values() for b in biomes}
    assert covered == set(deep_biomes), f"structure profiles vs deep biomes: {covered ^ set(deep_biomes)}"
    used = {entry["structure"] for _, _, entries in PROFILES.values() for entry in entries}
    assert used == {A(n) for n in STRUCTURES}, f"unused or unknown structures: {used ^ {A(n) for n in STRUCTURES}}"
    for name, d in STRUCTURES.items():
        dr = d.get("dressing")
        assert not dr or dr["radius"] <= d["footprint_radius"], name
        assert d["tier"] != "colossal" or d["category"] == "landmark", name
    for sub in ("seabed_structure", "seabed_structure_profile"):
        shutil.rmtree(os.path.join(root, sub), ignore_errors=True)
    for name, d in STRUCTURES.items():
        _write(os.path.join(root, "seabed_structure", name + ".json"), d)
    for name, (biomes, density, entries) in PROFILES.items():
        _write(os.path.join(root, "seabed_structure_profile", name + ".json"),
               {"biomes": [A(b) for b in biomes], "density": density, "structures": entries})
    return len(STRUCTURES), len(PROFILES)


def _write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, indent=2)
        fh.write("\n")
