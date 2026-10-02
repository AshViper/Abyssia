"""シギウナギ / slender snipe eel, Nemichthys scolopaceus.

Names: シギウナギ (JAMSTEC BISMaL "Nemichthys scolopaceus シギウナギ"; family シギウナギ科 Nemichthyidae); named for
the snipe-like (シギ) beak.

Research (FishBase summary 2660; Monterey Bay Aquarium "Slender snipe eel"; BISMaL; Wikipedia "Slender snipe eel"):
* worldwide in tropical and temperate seas, meso- to bathypelagic: FishBase 0-4337 m, usually 100-1000 m; Monterey
  Bay Aquarium 300-4000 m; Japanese sources 300-2000 m
* to ~1.3-1.5 m but only a few tens of grams: thread-thin body with 750+ vertebrae (the most of any vertebrate),
  ending in a long filament; large eyes
* very long, thin jaws like a bird's beak; the tips bow apart so the mouth cannot close; lined with tiny backward-
  hooked teeth
* dorsal and anal fins run most of the body; grey-brown to tan back, darker belly
* not an active swimmer: drifts in midwater, often hanging head-up or head-down; sweeps the jaws to snag the long
  antennae of shrimp (stomachs hold decapods such as Plesionika, Pasiphaea and euphausiids); solitary
* no light organs

Game simplifications: ~1.3 m adult; depth band 100-2000 m core with the 4337 m record as the limit; hangs still and
snags only the small deep-sea shrimp (never players).
"""

INFO = dict(
    names=("Slender Snipe Eel", "シギウナギ"),
    egg=(0x6B5F55, 0x2B2426),
    role="passive",
    voice="gulper_eel",
    prey=["deep_sea_shrimp"],
    sounds={
        "ambient": (2, "Snipe eel drifts", "シギウナギが漂う"),
        "hurt": (2, "Snipe eel hurts", "シギウナギが傷つく"),
        "death": (1, "Snipe eel dies", "シギウナギが死ぬ"),
        "flop": (2, "Snipe eel flops", "シギウナギが跳ねる"),
        "snap": (2, "Snipe eel snags prey", "シギウナギが獲物を引っかける"),
    },
    # usually 100-1000 m (FishBase), 300-4000 m (MBA); record 4337 m
    spawn=dict(weight=8, group=(1, 1), depth_m=(100, 300, 2000, 4337), placement="open_water",
               cave_factor=0.8, open_factor=1.0, max_light=6, cap=(16, 64)),
    java=dict(kind="swimmer", size_m=1.3, health=6,
              traits=dict(steering=(10, 4, 0.0015), zone=("open_water", 0.5, 300), hangs_still=True,
                          flees=(2.0, 6), hunts=(1.2, 0.2, 0.9), bite_clip="mouth_open", home=32, ambient=400)),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="eelpout_flesh", count=(1, 2), looting=1, chance=0.7)]
