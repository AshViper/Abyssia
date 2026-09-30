"""ダイダラボッチ / supergiant amphipod, Alicella gigantea Chevreux, 1899 (family Alicellidae).

Research (Maroni, Niyazi & Jamieson 2025, "The supergiant amphipod Alicella gigantea may inhabit over half of the
world's oceans", Royal Society Open Science 12: 241635; Jamieson et al. 2013, "The supergiant amphipod Alicella
gigantea from hadal depths in the Kermadec Trench", Deep-Sea Research II; Wikipedia ja "ダイダラボッチ (端脚類)"
(Japanese name given by amphipod researcher Ishimaru Shinichi after the folklore giant); Wikipedia "Alicella"):
* lower abyssal to upper hadal: mostly 4000-7000 m (shallowest record ~1720 m, Kermadec Trench to 7000 m);
  195 records from 75 sites in the Pacific, Indian and North Atlantic, incl. near Ogasawara; may occupy ~59 % of
  the world's ocean floor area
* the largest amphipod: usually 10-29 cm, up to ~34 cm; sideways-flattened, arched body of smooth plated segments
  with deep side plates, short antennae; pale cream-brown, with yellowish eyes
* a necrophage: caught almost only in baited traps, it swims just above the soft abyssal floor searching for
  carrion (falls of fish and other animals); large size is thought to help it outlast long fasts and evade fish
  predators (eaten by rattails such as the abyssal grenadier)
* no bioluminescence

Game simplifications: swims in the near-floor zone over soft sediment; passive and shy (flees); a few may gather
where carrion lies, otherwise alone or in twos and threes; not restricted to trench walls.
"""

INFO = dict(
    names=("Supergiant Amphipod", "ダイダラボッチ"),
    egg=(0xCDBB9E, 0x8A7A5A),
    role="passive",
    # the chitin-click giant_isopod voice has no "flop" take (swimmers need one): water / soft sounds instead
    voice="gulper_eel",
    sounds={
        "ambient": (2, "Amphipod paddles", "ダイダラボッチが水をかく"),
        "hurt": (2, "Amphipod hurts", "ダイダラボッチが傷つく"),
        "death": (1, "Amphipod dies", "ダイダラボッチが死ぬ"),
        "flop": (2, "Amphipod flops", "ダイダラボッチが跳ねる"),
    },
    # mostly 4000-7000 m, shallowest record ~1720 m; swims just above soft abyssal sediment
    spawn=dict(weight=8, group=(1, 3), depth_m=(1700, 4000, 6800, 7000), placement="near_floor",
               cave_factor=0.5, open_factor=1.0, max_light=8, cap=(12, 32),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=1.5)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.3, health=6, armor=2,
    # patrols a metre or two above the floor, darting off when disturbed
    traits=dict(steering=(12, 5, 0.0018), zone=("near_floor", 0.5, 300), flees=(1.5, 6), home=16, ambient=500),
    render=dict(eyeshine=(0.3, ["right_eye", "left_eye"])))
