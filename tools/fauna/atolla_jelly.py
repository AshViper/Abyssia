"""ムラサキカムリクラゲ / Atolla jellyfish (deep-sea crown jelly), Atolla wyvillei Haeckel, 1880.
Cnidaria > Scyphozoa > Coronatae > Atollidae (WoRMS: accepted).

Research (MBARI "Deep-sea crown jelly"; Herring & Widder 2004, Mar. Biol. 146:39; JAMSTEC BISMaL "ムラサキカムリクラゲ";
Wikipedia "Atolla jellyfish"):
* typically 1000-4000 m, genus 500-5000 m; bathypelagic, worldwide
* bell 2-17 cm (MBARI: to ~15 cm); a flat, disc-shaped bell ringed by a coronal groove and marginal lappets; about 20
  marginal tentacles plus ONE hypertrophied tentacle up to ~6x the bell diameter, trailed passively to catch prey
* red bell and stomach (red looks black at depth)
* eats crustaceans, plankton and other gelatinous animals
* bioluminescent "burglar alarm": touched or attacked, brilliant blue waves of light circle the bell (pinwheel),
  5-60 cm/s, later waves slower; thought to draw larger predators to the attacker; emission wavelength UNKNOWN
* sting risk to humans: UNKNOWN

Game simplifications: the alarm also starts when a player comes close; large predators nearby turn to investigate;
a touch of the trailing tentacle stings weakly (effect on people is unknown); 16 + 1 tentacles instead of ~20 + 1;
a faint resting glow is a readability aid.
"""

INFO = dict(
    names=("Atolla Jellyfish", "ムラサキカムリクラゲ"),
    egg=(0x8C2C34, 0x3D7DFF),
    role="neutral",
    voice="jelly",
    sounds={
        "ambient": (2, "Atolla pulses", "ムラサキカムリクラゲが拍動する"),
        "pulse": (2, "", ""),
        "hurt": (2, "Atolla flinches", "ムラサキカムリクラゲが縮む"),
        "death": (1, "Atolla dies", "ムラサキカムリクラゲが死ぬ"),
    },
    spawn=dict(weight=10, group=(1, 3), depth_m=(500, 1000, 4000, 5000), placement="open_water",
               cave_factor=0.5, open_factor=1.0, max_light=5, cap=(8, 48)),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="atolla_tentacle", count=(1, 2), looting=1, chance=0.75)]
