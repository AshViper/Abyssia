"""ヨロイダラ / abyssal grenadier (rattail), Coryphaenoides armatus.

Japanese name: ヨロイダラ (JAMSTEC BISMaL 9001401); ソコダラ is the group name for grenadiers (Macrouridae).

Research (FishBase summary "Coryphaenoides armatus"; JAMSTEC BISMaL 9001401; Wikipedia "Abyssal grenadier";
Bailey, Jamieson, Bagley, Collins & Priede 2007, "A taste of the deep-sea: the roles of gustatory and tactile
searching behaviour in the grenadier fish Coryphaenoides armatus", Deep-Sea Research I 54:99-108):
* 282-5180 m, mainly 2000-4700 m on the continental rise and abyssal plains; circumglobal except polar seas
* 20-40 cm usually, up to ~1 m; large head and eyes, a conical snout overhanging a small underslung mouth, a
  chin barbel; body tapers to a long pointed "rat tail" with no caudal fin (long 2nd dorsal and anal fins run
  to the tip); uniformly brown with a bluish belly
* cruises slowly close to the floor, head-down (about 21 degrees to the seabed), probing with the barbel and
  taste buds; among the first scavengers to arrive at baited landers, then lingers around the bait
* young eat benthic crustaceans and holothurians; adults eat fishes, cephalopods, urchins, carrion
* no light organ in this species (some other rattails have one); solitary, but many gather at a food fall

Sources: https://www.fishbase.se/summary/Coryphaenoides-armatus.html
https://www.godac.jamstec.go.jp/bismal/j/view/9001401  https://en.wikipedia.org/wiki/Abyssal_grenadier
https://www.sciencedirect.com/science/article/abs/pii/S0967063706002615

Game simplifications: cod, shrimp and squid stand in for its prey; may gather around dropped fish / dead mobs
as a nod to its scavenging; harmless to players.
"""

INFO = dict(
    names=("Abyssal Grenadier", "ヨロイダラ"),
    egg=(0x6B5A4A, 0x5A6A80),
    role="predator",
    voice="anglerfish",
    prey=["minecraft:cod", "deep_sea_shrimp", "minecraft:squid"],
    sounds={
        "ambient": (2, "Abyssal grenadier swims", "ヨロイダラが泳ぐ"),
        "hurt": (2, "Abyssal grenadier hurts", "ヨロイダラが傷つく"),
        "death": (1, "Abyssal grenadier dies", "ヨロイダラが死ぬ"),
        "flop": (2, "Abyssal grenadier flops", "ヨロイダラが跳ねる"),
        "snap": (2, "Abyssal grenadier snaps", "ヨロイダラが食らいつく"),
    },
    # 282-5180 m, mainly 2000-4700 m; cruises head-down just above the floor
    spawn=dict(weight=10, group=(1, 3), depth_m=(1000, 2000, 4700, 5200), placement="near_floor",
               cave_factor=0.4, open_factor=1.0, max_light=8, cap=(24, 64)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.8, health=10, attack=1,
    # cruises slowly head-down close to the floor, snapping up shrimp and small fish
    traits=dict(steering=(15, 4, 0.0018), zone=("near_floor", 0.8, 160), flees=(2.0, 8), hunts=(1.6, 0.3, 0.5), home=32, ambient=500),
    render=dict(eyeshine=(0.7, ["right_eye", "left_eye"])))

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="abyssal_fish_fillet", count=(2, 3), looting=1, chance=0.7)]
