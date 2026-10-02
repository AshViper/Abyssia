"""ダイオウクラゲ / giant phantom jelly, Stygiomedusa gigantea (Browne, 1910).
Cnidaria > Scyphozoa > Semaeostomeae > Ulmaridae (WoRMS: accepted).

Research (MBARI "Giant phantom jelly"; JAMSTEC BISMaL "ダイオウクラゲ"; Wikipedia "Stygiomedusa"):
* recorded from near the surface to 6700 m, typically bathypelagic; all oceans except the Arctic; MBARI has seen it
  only nine times (one at 975 m)
* bell more than 1 m across, dark crimson; NO marginal tentacles; four broad, ribbon-like oral arms more than 10 m long
* presumably eats plankton and small fishes (MBARI)
* movement, bioluminescence and defence: UNKNOWN (claims of a faint red glow have no primary source)
* the brotula Thalassobathia pelagica shelters among its arms
* sting risk to humans: UNKNOWN

Game simplifications: ~3 blocks across with ~9-block arms; harmless and unhurried; it does not glow, so it is met in
the beam of the player's own light; only the bell has a hitbox; one per wide area.
"""

INFO = dict(
    names=("Giant Phantom Jelly", "ダイオウクラゲ"),
    egg=(0x4A121C, 0x9A3040),
    role="passive",
    voice="jelly",
    sounds={
        "ambient": (2, "Giant phantom jelly pulses", "ダイオウクラゲが拍動する"),
        "pulse": (2, "", ""),
        "hurt": (2, "Giant phantom jelly flinches", "ダイオウクラゲが縮む"),
        "death": (1, "Giant phantom jelly dies", "ダイオウクラゲが死ぬ"),
    },
    spawn=dict(weight=1, group=(1, 1), depth_m=(700, 1000, 4000, 6700), placement="open_water",
               cave_factor=0.0, open_factor=1.0, max_light=4, clearance=8, cap=(1, 160)),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="phantom_tentacle", count=(1, 3), looting=1, chance=0.7)]
