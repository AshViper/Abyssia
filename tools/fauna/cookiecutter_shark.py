"""ダルマザメ / cookiecutter shark, Isistius brasiliensis.

Japanese name: ダルマザメ (JAMSTEC BISMaL 9001247).

Research (FishBase summary "Isistius brasiliensis"; Wikipedia "Cookiecutter shark"; Widder 1998, "A predatory use
of counterillumination by the squaloid shark, Isistius brasiliensis", Environ. Biol. Fishes 53: 267-273):
* tropical and subtropical oceans worldwide, often near islands; epi- to bathypelagic, 0-3700 m, usually
  0-1000 m; diel vertical migration of 2-3 km: below 1000 m by day, near the surface at night
* small: males to 42 cm, females to 56 cm; cigar-shaped body, short blunt snout, large forward-set eyes, fleshy
  suctorial lips, small narrow upper teeth and a band-saw row of big triangular interlocking lower teeth; two tiny
  spineless dorsal fins far back, no anal fin, broad tail
* dark brown above, paler below; the whole underside is covered in tiny photophores glowing vivid green (the
  strongest shark luminescence known, lasting up to 3 h after death) except a dark non-luminous collar across
  the throat and gill slits, thought to mimic a small fish's silhouette as a lure (Widder 1998)
* facultative ectoparasite: latches on with its lips, spins and cuts a ~5 cm round plug out of tunas, billfishes,
  whales, larger sharks and squid; also eats whole squid, crustaceans and bristlemouths; reported in schools

Sources: https://www.fishbase.se/summary/Isistius-brasiliensis.html  https://en.wikipedia.org/wiki/Cookiecutter_shark
https://link.springer.com/article/10.1023/A:1007498915860  https://www.godac.jamstec.go.jp/bismal/j/view/9001247

Game simplifications: its round plug-bite is an ordinary bite, only when hurt (harmless to players otherwise);
small groups in open water, the night rise to the surface left out; the belly glow is painted rows of green
light on the body (the collar left dark) with a steady low flicker; squid, cod, lanternfish and hatchetfish
stand in for its prey.
"""

INFO = dict(
    names=("Cookiecutter Shark", "ダルマザメ"),
    egg=(0x3A2C25, 0x6DFFA8),
    role="predator",
    voice="shark",
    prey=["lanternfish", "hatchetfish", "deep_sea_shrimp", "minecraft:squid", "minecraft:glow_squid", "minecraft:cod"],
    sounds={
        "ambient": (2, "Cookiecutter shark swims", "ダルマザメが泳ぐ"),
        "hurt": (2, "Cookiecutter shark hurts", "ダルマザメが傷つく"),
        "death": (1, "Cookiecutter shark dies", "ダルマザメが死ぬ"),
        "flop": (2, "Cookiecutter shark flops", "ダルマザメが跳ねる"),
        "bite": (2, "Cookiecutter shark cuts a round bite", "ダルマザメが丸くかじり取る"),
    },
    # 0-3700 m, usually 0-1000 m; below 1000 m by day
    spawn=dict(weight=5, group=(2, 4), depth_m=(100, 500, 1500, 3700), placement="open_water",
               cave_factor=0.3, open_factor=1.0, max_light=7, cap=(16, 64)),
    java=dict(kind="shark", size_m=0.45, health=8, attack=3, armor=0,
              traits=dict(steering=(18, 5, 0.004), cruise_height=8, mouth_forward=0.4),
              render=dict(glow=dict(bones=["body"], halos=[("body", 0.3, 0.45, 1.0, 0.65)], base=0.75, flicker=0.05))),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="shark_flesh", count=(2, 3), looting=1, chance=0.7)]
