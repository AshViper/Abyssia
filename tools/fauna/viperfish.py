"""ホウライエソ / Sloane's viperfish, Chauliodus sloani.

Research (FishBase summary "Chauliodus sloani"; Wikipedia "Sloane's viperfish"; Sutton & Hopkins 1996, "Trophic
ecology of the stomiid (Pisces: Stomiidae) fish assemblage of the eastern Gulf of Mexico"):
* 200-4700 m, usually 494-1000 m by day; migrates toward 200-500 m at night to feed (diel vertical migration)
* up to ~35 cm; slender, dark silvery-blue body with a hexagonal pattern; large eyes
* fangs too long for the closed mouth: the lower ones stand in front of the upper jaw; the skull swings back so
  the jaws open to about 90 degrees
* the first dorsal ray is a long filament tipped with a photophore, held forward over the head as a lure;
  rows of photophores along the belly (counter-illumination); stressed fish flash their photophores
* sit-and-wait predator: lanternfishes and crustaceans (shrimp), taken whole

Game simplifications: cod stands in for lanternfish; only the lure glows (the ventral rows are painted, not lit);
harmless to players.
"""

INFO = dict(
    names=("Viperfish", "ホウライエソ"),
    egg=(0x1C2733, 0x6FA8FF),
    role="predator",
    voice="anglerfish",
    prey=["minecraft:cod", "deep_sea_shrimp"],
    sounds={
        "ambient": (2, "Viperfish swims", "ホウライエソが泳ぐ"),
        "hurt": (2, "Viperfish hurts", "ホウライエソが傷つく"),
        "death": (1, "Viperfish dies", "ホウライエソが死ぬ"),
        "flop": (2, "Viperfish flops", "ホウライエソが跳ねる"),
        "snap": (2, "Viperfish strikes", "ホウライエソが食らいつく"),
    },
    spawn=dict(weight=9, group=(1, 1), depth_m=(200, 494, 1000, 4700), placement="open_water",
               cave_factor=0.6, open_factor=1.0, max_light=6, cap=(30, 64)),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="viper_flesh", count=(1, 2), looting=1, chance=0.7)]
