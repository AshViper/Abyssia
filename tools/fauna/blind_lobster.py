"""センジュエビ / blind deep-sea lobster, Polycheles typhlops Heller, 1862 (family Polychelidae, センジュエビ科).

Research (Wikipedia "Polycheles typhlops", citing Cabiddu et al. 2008 and Sarda et al. 2009; WoRMS / SeaLifeBase
"Polycheles typhlops"; JAMSTEC BISMaL: family Polychelidae センジュエビ科, genus Polycheles センジュエビ属 with
records off Japan at 150-960 m; 市場魚貝類図鑑 (zukan-bouz) "センジュエビ" for the Japanese records):
* near-cosmopolitan (Atlantic, Mediterranean, Indo-Pacific); off Japan in Suruga Bay, Enshu-nada and Kumano-nada,
  where it turns up as trawl bycatch; 300-2000 m in the literature, most abundant ~500-1000 m
* soft mud of the continental slope; thought to ambush prey while partly buried in the sediment with its claws raised
* small: ~5-12 cm total length (to ~15 cm with the claws); whitish to orange / pale pink
* blind: no functional eyes, only notches where the eye sockets were (polychelidans descend from sighted ancestors)
* broad, flattened carapace edged with 12-15 spines per side; a straight, flattened abdomen with a tail fan; all
  five pairs of legs end in claws, the first pair greatly elongated and slender
* predator of bony fish and small crustaceans (shrimp, mysids, amphipods)
* no bioluminescence

Game simplifications: walks the mud slowly instead of lying buried in it; passive (flees rather than pinching the
player); modelled with the long first claws and four visible pairs of shorter legs.
"""

INFO = dict(
    names=("Blind Deep-sea Lobster", "センジュエビ"),
    egg=(0xD9A48A, 0xB06E55),
    role="passive",
    voice="crab",
    sounds={
        "ambient": (2, "Blind lobster clicks", "センジュエビがカチカチ鳴る"),
        "hurt": (2, "Blind lobster hurts", "センジュエビが傷つく"),
        "death": (1, "Blind lobster dies", "センジュエビが死ぬ"),
        "step": (3, None, None),
    },
    # 300-2000 m, most abundant 500-1000 m; Japanese genus records 150-960 m; soft slope mud
    spawn=dict(weight=10, group=(1, 2), depth_m=(200, 500, 1000, 2000), placement="seabed",
               cave_factor=0.5, open_factor=1.0, max_light=15, cap=(12, 32),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=2.0)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py); eyeless, so no eyeshine
INFO["java"] = dict(kind="walker", size_m=0.12, health=6, armor=2,
    traits=dict(move_clip="walk", speed=0.5, wander=120, home=12, ambient=500),
    render=dict(fish=False, pitch=False))
