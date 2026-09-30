"""フクロウナギ / gulper (pelican) eel, Eurypharynx pelecanoides.

Research (FishBase summary 4526; Australian Museum; Wikipedia "Pelican eel"; Nautilus Live / National Geographic,
Sept. 2018 observation in Papahanaumokuakea):
* meso- to abyssopelagic, 500-3000 m (usually 1200-1400 m), recorded to 7625 m; circumglobal
* to ~0.75 m, 1 m plausible; black or olive, ultra-black skin
* enormous loosely hinged mouth, the gape half or more of the preanal length; a stretchy pouch; tiny teeth; tiny
  eyes close to the snout
* whip-like tail ending in a luminous organ with tentacles that glows pink with occasional red flashes
* eats mainly small crustaceans (also fishes, cephalopods), engulfing them with a mouthful of water
* filmed in 2018 inflating its mouth like a black balloon, then deflating it and swimming away (possibly a
  reaction to feeling threatened)

Game simplification: kept mostly to dark cave water (the real fish lives in open midwater).
"""

INFO = dict(
    names=("Gulper Eel", "フクロウナギ"),
    egg=(0x15171D, 0xF0A0D0),
    role="passive",
    prey=["deep_sea_shrimp", "ohara_shrimp"],
    sounds={
        "ambient": (2, "Gulper eel swims", "フクロウナギが泳ぐ"),
        "gulp": (2, "Gulper eel gulps", "フクロウナギが大口を開ける"),
        "inflate": (1, "Gulper eel inflates", "フクロウナギが口を膨らませる"),
        "deflate": (1, "Gulper eel deflates", "フクロウナギが口をしぼませる"),
        "hurt": (2, "Gulper eel hurts", "フクロウナギが傷つく"),
        "death": (1, "Gulper eel dies", "フクロウナギが死ぬ"),
        "flop": (2, "Gulper eel flops", "フクロウナギが跳ねる"),
    },
    # 500-3000 m (usually 1200-1400 m), to 7625 m; mostly cave water here, open water at a twentieth of the weight
    spawn=dict(weight=8, group=(1, 1), depth_m=(500, 1000, 3000, 7625), placement="open_water",
               cave_factor=3.0, open_factor=0.15, max_light=7, cap=(18, 64)),
)
