"""クロカムリクラゲ / helmet jellyfish, Periphylla periphylla (Péron & Lesueur, 1810).
Cnidaria > Scyphozoa > Coronatae > Periphyllidae (WoRMS: accepted).

Research (WHOI Ocean Twilight Zone "Helmet jellyfish"; Animal Diversity Web "Periphylla periphylla"; Herring & Widder
2004, Mar. Biol. 146:39; JAMSTEC BISMaL "クロカムリクラゲ"):
* usually below 900 m, to 7000 m, in all oceans except the Black Sea; shallower in dark fjords
* 18-35 cm; a tall conical (helmet) bell, transparent outside over dark red / red-brown pigmented tissue and gut;
  12 thick tentacles up to ~50 cm; coronal groove and marginal lappets
* strongly photophobic: its pigment is destroyed by light; it rises only in darkness and retreats at sunrise
* hunts at night, feeling for shrimp, krill, copepods and small fish with its tentacles
* bioluminescent, blue to blue-green: coronal groove, point sources on the dome, and sparkling particles released
  from the lappet margins when stimulated; the red gut hides glowing prey
* sting risk to humans: UNKNOWN

Game simplifications: it sinks away from any bright block light; by night it drifts higher; a player at its tentacles
is stung and slowed briefly (effect on people is unknown); a faint resting glow is a readability aid.
"""

INFO = dict(
    names=("Helmet Jellyfish", "クロカムリクラゲ"),
    egg=(0x5A1E30, 0x4FE0BF),
    role="neutral",
    voice="jelly",
    sounds={
        "ambient": (2, "Helmet jellyfish pulses", "クロカムリクラゲが拍動する"),
        "pulse": (2, "", ""),
        "hurt": (2, "Helmet jellyfish flinches", "クロカムリクラゲが縮む"),
        "death": (1, "Helmet jellyfish dies", "クロカムリクラゲが死ぬ"),
    },
    spawn=dict(weight=8, group=(1, 3), depth_m=(700, 900, 4000, 7000), placement="open_water",
               cave_factor=0.8, open_factor=1.0, max_light=3, cap=(6, 48)),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="jelly_tentacle", count=(1, 2), looting=1, chance=0.75)]
