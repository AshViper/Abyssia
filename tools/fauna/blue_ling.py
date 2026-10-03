"""ブルーリング / blue ling, Molva dypterygia.

Japanese name: no standard Japanese name (it does not occur off Japan); the FS01 spec uses the katakana ブルーリング.
Family クロタラ科 / Lotidae (lings and burbots), close to the cods.

Research (FishBase summary "Molva dypterygia"; ICES stock annex "Blue ling"; Wikipedia "Blue ling"; Magnússon &
Magnússon 1995, "The distribution, relative abundance and biology of the deep-sea fishes of the Icelandic slope"):
* North-east Atlantic and Mediterranean: Iceland, Faroes, west of Scotland, Norway, Bay of Biscay; 150-1000 m,
  mostly 350-500 m (spawning aggregations ~750-1000 m); bathydemersal on the slope, often on rough ground
* up to 1.5 m (commonly ~1 m); long, slender, eel-like cod relative but thicker than an eel, with an ordinary fish
  head: large mouth, projecting lower jaw, a small chin barbel; a short first dorsal and one very long second
  dorsal and anal fin running to a small rounded tail
* blue-grey to bronze-grey above, silvery-grey belly, fins with dark edges and white margins
* swims along the bottom alone or in small groups; eats fish (redfish, small gadoids), crustaceans, cephalopods
* traditional food fish (salted, dried) in the North Atlantic; vulnerable to trawling

Sources: https://www.fishbase.se/summary/Molva-dypterygia.html  https://en.wikipedia.org/wiki/Blue_ling

Game simplifications: built on the eel template with a normal fish head (no barbel; the two dorsals are one ribbon);
cruises along the floor; harmless; drops its own raw fillet.
"""

INFO = dict(
    names=("Blue Ling", "ブルーリング"),
    egg=(0x5A6E80, 0xB4BEC8),
    role="passive",
    voice="gulper_eel",
    sounds={
        "ambient": (2, "Blue ling swims", "ブルーリングが泳ぐ"),
        "hurt": (2, "Blue ling hurts", "ブルーリングが傷つく"),
        "death": (1, "Blue ling dies", "ブルーリングが死ぬ"),
        "flop": (2, "Blue ling flops", "ブルーリングが跳ねる"),
    },
    # 150-1000 m, mostly 350-500 m, spawning deeper
    spawn=dict(weight=8, group=(1, 3), depth_m=(200, 400, 1000, 1500), placement="near_floor",
               cave_factor=0.6, open_factor=1.0, max_light=8, cap=(24, 64)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py); ~1.1 blocks long
INFO["java"] = dict(kind="swimmer", size_m=0.66, health=10,
    # undulates along the floor alone or in small groups
    traits=dict(steering=(12, 4, 0.0018), zone=("near_floor", 0.6, 200), flees=(2.0, 8), home=32, ambient=500),
    render=dict(eyeshine=(0.6, ["right_eye", "left_eye"])))

# Drops (FS01): its own raw fillet, looting adds up to 1 per level
INFO["loot"] = [dict(item="raw_blue_ling", count=(1, 2), looting=1)]
