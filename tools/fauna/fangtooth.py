"""オニキンメ / common fangtooth, Anoplogaster cornuta.

Research (FishBase summary "Anoplogaster cornuta"; JAMSTEC BISMaL "Anoplogaster cornuta オニキンメ"; Australian
Museum "Fangtooth, Anoplogaster cornuta"; Wikipedia "Anoplogaster cornuta"):
* worldwide in tropical and temperate seas; 2-4992 m, usually 500-2000 m; juveniles live shallower
  (mesopelagic), adults deeper (bathypelagic)
* up to 18 cm; a short, deep body - deepest just behind the huge head, tapering fast to the tail; dark brown to
  black, rough skin with small prickly scales; small eyes in adults; lateral line an open groove
* relative to body size the largest teeth of any marine fish: long fangs in both jaws (the longest lower pair slide
  into sockets beside the brain when the mouth closes); the mouth opens back almost to the rear of the head
* no photophores - its black skin absorbs over 99 % of light, hiding it from others' bioluminescence
* juveniles (grey, long head spines, big eyes - the "oni" horns of the Japanese name) eat crustaceans; adults
  mainly fishes (especially lanternfishes), also squid; alone or in small groups; eaten by tuna and marlin

Game simplifications: adults only (no horned juvenile form); lanternfish, hatchetfish and cod as prey; harmless
to players.
"""

INFO = dict(
    names=("Fangtooth", "オニキンメ"),
    egg=(0x2A201D, 0xD9D2C0),
    role="predator",
    voice="anglerfish",
    prey=["lanternfish", "hatchetfish", "minecraft:cod", "deep_sea_shrimp"],
    sounds={
        "ambient": (2, "Fangtooth swims", "オニキンメが泳ぐ"),
        "hurt": (2, "Fangtooth hurts", "オニキンメが傷つく"),
        "death": (1, "Fangtooth dies", "オニキンメが死ぬ"),
        "flop": (2, "Fangtooth flops", "オニキンメが跳ねる"),
        "snap": (2, "Fangtooth bites", "オニキンメが食らいつく"),
    },
    # usually 500-2000 m, juveniles shallower, recorded to ~5000 m; alone or in small groups
    spawn=dict(weight=7, group=(1, 3), depth_m=(200, 500, 2000, 4992), placement="open_water",
               cave_factor=0.7, open_factor=1.0, max_light=5, cap=(24, 64)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.16, health=5, attack=1,
    traits=dict(steering=(22, 6, 0.0022), zone=("open_water", 0.9, 180), migrates=0.2, flees=(2.5, 8), hunts=(1.3, 0.3, 0.25),
                home=24, ambient=450),
    render=dict(eyeshine=(0.5, ["right_eye", "left_eye"])))
