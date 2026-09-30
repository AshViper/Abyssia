"""センジュナマコ / sea pig, Scotoplanes globosa (Théel, 1879) (family Elpidiidae).

Research (JAMSTEC BISMaL 9000270 "センジュナマコ", 227 records at 754-6467 m; SeaLifeBase "Scotoplanes globosa",
2100-6770 m on diatom ooze and grey mud; Wikipedia "Scotoplanes globosa"; WoRMS "Scotoplanes globosa"):
* abyssal plains of all oceans, usually deeper than 1000 m (Galathea trawl records to the Kermadec and Philippine
  trenches); on soft mud and ooze
* 2-15 cm; plump, soft, pale pink to whitish and somewhat translucent
* walks on 5-7 pairs of enlarged leg-like tube feet, inflated by pumping water through them (unique to the genus);
  2-3 pairs of long dorsal papillae ("antennae") rise over the front; a ring of feeding tentacles round the mouth
* deposit feeder: eats the organic film of the sediment, strongly preferring freshly fallen phytodetritus; gathers
  at whale falls, found by smell
* lives in herds, commonly 10-30 and up to hundreds, often all facing into the current; about a fifth carry a
  juvenile king crab (Neolithodes diomedeae) that shelters under or on them
* no bioluminescence recorded

Game simplifications: herds of 3-8; one pair of dorsal papillae in the model; the walk is the parts template's
travelling wave through legs and papillae; no hitch-hiking crabs.
"""

INFO = dict(
    names=("Sea Pig", "センジュナマコ"),
    egg=(0xD49CA2, 0xF0D8DA),
    role="passive",
    voice="cucumber",
    # the cucumber voice has no "step" take: GenericWalker then plays "ambient" as its walk sound
    sounds={
        "ambient": (2, "Sea pig squelches", "センジュナマコがぬめる"),
        "hurt": (2, "Sea pig hurts", "センジュナマコが傷つく"),
        "death": (1, "Sea pig dies", "センジュナマコが死ぬ"),
    },
    # BISMaL records 754-6467 m, SeaLifeBase 2100-6770 m; herds on soft mud and ooze of the abyssal plain
    spawn=dict(weight=14, group=(3, 8), depth_m=(750, 1500, 6000, 6800), placement="seabed",
               cave_factor=0.3, open_factor=1.0, max_light=15, cap=(24, 48),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=3.0)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py); no eyes
INFO["java"] = dict(kind="walker", size_m=0.12, health=4,
    # a slow, bobbing plod across the mud
    traits=dict(move_clip="tentacle_move", speed=0.25, wander=100, home=12, ambient=600),
    render=dict(fish=False, pitch=False))
