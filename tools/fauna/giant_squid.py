"""ダイオウイカ / giant squid, Architeuthis dux.

Research (Kubodera & Mori 2005, "First-ever observations of a live giant squid in the wild", Proc. R. Soc. B;
SeaLifeBase "Architeuthis dux"; Australian Museum; CephRef; Wikipedia "Giant squid"):
* mostly 300-1000 m (bathypelagic 200-1000 m); first photographed alive in 2004 at ~900 m off the Ogasawara Islands
  on a baited line, filmed in 2012 near Chichijima (~630 m) and 2019 in the Gulf of Mexico (~760 m)
* mantle to ~2 m, total length to ~12-13 m with the two long feeding tentacles; eyes ~27 cm across, the largest of
  any animal (with the colossal squid)
* ammonium-rich tissue gives neutral buoyancy: it hovers with little effort; thought to be a slow ambush predator
  that shoots out its tentacles (toothed sucker clubs) and hauls prey to the arm crown and beak
* eats deep-sea fishes (e.g. hoki, orange roughy) and other squid; main predator: sperm whales
* no photophores; it has an ink sac; solitary

Game simplifications: ~7.5 blocks long; only a close attacker at its arms is seized, otherwise it inks and jets
away; keeps to large open water (clearance); never damages terrain.
"""

INFO = dict(
    names=("Giant Squid", "ダイオウイカ"),
    egg=(0x8A3B2E, 0xD9B8A0),
    role="predator",
    voice="squid",
    prey=["minecraft:cod", "minecraft:salmon", "minecraft:squid", "minecraft:glow_squid", "viperfish", "barreleye", "deep_sea_shrimp"],
    sounds={
        "ambient": (2, "Giant squid pumps water", "ダイオウイカが水を吐く"),
        "hurt": (2, "Giant squid hurts", "ダイオウイカが傷つく"),
        "death": (1, "Giant squid dies", "ダイオウイカが死ぬ"),
        "grab": (2, "Giant squid seizes", "ダイオウイカが触腕で捕らえる"),
        "ink": (1, "Giant squid squirts ink", "ダイオウイカが墨を吐く"),
        "jet": (1, "Giant squid jets away", "ダイオウイカが噴射して逃げる"),
    },
    spawn=dict(weight=2, group=(1, 1), depth_m=(200, 400, 1000, 1500), placement="open_water",
               cave_factor=1.0, open_factor=1.0, max_light=6, clearance=6, cap=(6, 128)),
)
