"""チョウチンアンコウ / deep-sea anglerfish (suborder Ceratioidei).

The model (tools/bbmodel-generator, definition "anglerfish") is the humpback anglerfish Melanocetus johnsonii, the
iconic big-toothed ceratioid; the Japanese name チョウチンアンコウ strictly belongs to the Atlantic footballfish
Himantolophus groenlandicus of the same suborder.

Research:
* Melanocetus johnsonii (FishBase): meso- and bathypelagic, usually 100-1500 m, to 4500 m; females to 18 cm TL,
  dwarf males 2.9 cm; 48-134 upper and 32-78 lower teeth, the longest 8-25 % of SL; an esca with crests
* Himantolophus groenlandicus (FishBase 3099; Wikipedia): usually 200-800 m, to 1830 m; females to 60 cm SL; males
  free-living; eats fishes, squid and crustaceans; the esca's light comes from symbiotic bacteria (blue-green)
* MBARI "Deep-sea anglerfish": ambush predators that set out a glowing lure and wait; an Oneirodes filmed at 1474 m
  drifted passively in 74 % of 24 minutes, swimming only intermittently (0.24 body lengths/s)

Game simplifications: only lure-bearing females appear; kept larger than the giant isopod (as Himantolophus is) so
the size order the design asks for holds; whether anglerfish dim their esca at will is not settled - here the lure
goes dark when the fish flees.
"""

INFO = dict(
    names=("Anglerfish", "チョウチンアンコウ"),
    egg=(0x1B2029, 0x8FF0FF),
    role="predator",
    prey=["minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "deep_sea_shrimp", "ohara_shrimp"],
    sounds={   # kind: (variants, subtitle en, subtitle ja)
        "ambient": (2, "Anglerfish drifts", "チョウチンアンコウが漂う"),
        "hurt": (2, "Anglerfish hurts", "チョウチンアンコウが傷つく"),
        "death": (1, "Anglerfish dies", "チョウチンアンコウが死ぬ"),
        "threat": (2, "Anglerfish gapes", "チョウチンアンコウが口を開いて威嚇する"),
        "snap": (2, "Anglerfish snaps", "チョウチンアンコウが食らいつく"),
        "flop": (2, "Anglerfish flops", "チョウチンアンコウが跳ねる"),
    },
    # usually 100-1500 m, to 4500 m; pelagic, hanging in open water (or dark caves)
    spawn=dict(weight=10, group=(1, 1), depth_m=(100, 200, 1500, 4500), placement="open_water",
               cave_factor=0.8, open_factor=1.0, max_light=5, cap=(24, 64)),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="angler_flesh", count=(1, 2), looting=1, chance=0.7)]
