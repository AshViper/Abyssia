"""カグラザメ / bluntnose sixgill shark, Hexanchus griseus.

Japanese name: カグラザメ (JAMSTEC BISMaL 9001134).

Research (FishBase summary "Hexanchus griseus"; Wikipedia "Bluntnose sixgill shark"; JAMSTEC BISMaL 9001134):
* circumglobal but patchy, 65 N-48 S; outer shelves, upper slopes, seamounts and ridges, near the bottom
  (occasionally pelagic); 0-2500 m, usually 180-1100 m; juveniles shallower
* diel vertical migration: on the bottom by day, rising toward the surface at night to feed
* to ~5.5 m (6 m reported), commonly ~3 m; heavy body, broad blunt rounded snout, small fluorescent blue-green
  eyes, SIX gill slits, a single low dorsal fin set far back above the anal fin, broad rounded pectorals, long
  heterocercal tail; brown-grey to blackish above, paler below with a light lateral line stripe, white fin edges
* sluggish generalist predator and scavenger: other sharks, rays, chimaeras, bony fishes, squid, crabs, shrimps,
  carrion, even seals and cetaceans
* no bioluminescence (the green eye colour is reflective/fluorescent, not a light organ); solitary

Sources: https://www.fishbase.se/summary/Hexanchus-griseus.html
https://en.wikipedia.org/wiki/Bluntnose_sixgill_shark  https://www.godac.jamstec.go.jp/bismal/j/view/9001134

Game simplifications: ~3 blocks long (not 5 m); neutral (bites back only when hurt); the night rise to the
surface is left out - it stays near the slope floor; the green eyes are eyeshine only.
"""

INFO = dict(
    names=("Bluntnose Sixgill Shark", "カグラザメ"),
    egg=(0x5B524B, 0x3FCF86),
    role="predator",
    voice="shark",
    prey=["chimaera", "abyssal_grenadier", "deep_sea_shrimp", "minecraft:cod", "minecraft:squid"],
    sounds={
        "ambient": (2, "Sixgill shark swims", "カグラザメが泳ぐ"),
        "hurt": (2, "Sixgill shark hurts", "カグラザメが傷つく"),
        "death": (1, "Sixgill shark dies", "カグラザメが死ぬ"),
        "flop": (2, "Sixgill shark flops", "カグラザメが跳ねる"),
        "bite": (2, "Sixgill shark bites", "カグラザメが噛みつく"),
    },
    # 0-2500 m, usually 180-1100 m, near the bottom by day
    spawn=dict(weight=3, group=(1, 1), depth_m=(90, 180, 1100, 2500), placement="near_floor",
               cave_factor=0.3, open_factor=1.0, max_light=8, clearance=3, cap=(8, 128)),
    java=dict(kind="shark", size_m=4.0, health=50, attack=7, armor=2,
              traits=dict(steering=(10, 3, 0.003), cruise_height=3, mouth_forward=1.1),
              render=dict(eyeshine=(0.9, ["right_eye", "left_eye"]))),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="shark_flesh", count=(3, 4), looting=1, chance=0.7)]
