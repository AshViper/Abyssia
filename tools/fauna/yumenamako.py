"""ユメナマコ / swimming sea cucumber, Enypniastes eximia.

Research (SeaLifeBase "Enypniastes eximia"; Museums Victoria collections; Robison 1992 and Barnes et al. 1976 on
its swimming and bioluminescence; NOAA Ocean Exploration 2017/2018 dives; Wikipedia "Enypniastes"):
* 461/516-5689 m (deepest record ~6900 m, Java Trench), on and above soft abyssal sediment in all oceans
* 11-25 cm; gelatinous, translucent rose-red to red-brown; a large webbed veil at the front used like a wing, a
  smaller brim behind, a ring of feeding tentacles
* feeds on the sediment surface for short spells (about a minute), then swims up off the bottom - sometimes
  hundreds of metres - drifts, and sinks to settle elsewhere
* when touched, bioluminescent granules in the skin light up blue-green and sticky glowing skin is shed, marking a
  predator for its own predators

Game simplifications: rises a handful of blocks, not hundreds of metres.
"""

INFO = dict(
    names=("Swimming Sea Cucumber", "ユメナマコ"),
    egg=(0xB0485A, 0x7DDDC6),
    role="passive",
    voice="cucumber",
    sounds={
        "ambient": (2, "Sea cucumber beats its veil", "ユメナマコがひれを打つ"),
        "hurt": (2, "Sea cucumber hurts", "ユメナマコが傷つく"),
        "death": (1, "Sea cucumber dies", "ユメナマコが死ぬ"),
        "swim": (2, "Sea cucumber lifts off", "ユメナマコが泳ぎ上がる"),
    },
    spawn=dict(weight=14, group=(1, 3), depth_m=(500, 1500, 4500, 6000), placement="seabed",
               cave_factor=0.5, open_factor=1.0, max_light=15, cap=(48, 48),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=3.0)]),
)
