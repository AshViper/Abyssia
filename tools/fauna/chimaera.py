"""ギンザメ / silver chimaera (ghost shark), Chimaera phantasma.

Japanese name: ギンザメ (JAMSTEC BISMaL 9001276).

Research (FishBase summary "Chimaera phantasma"; JAMSTEC BISMaL 9001276; Wikipedia "Silver chimaera"):
* 20-960 m, mostly 90-540 m (commonest near 500 m) on the outer shelf and upper slope; Japan, Korea, China,
  Taiwan, Philippines; bathydemersal
* up to 1-1.1 m, the tail about a third of that, ending in a whip-like filament; big triangular head with a blunt
  snout, large eyes, underslung mouth with ever-growing crushing tooth plates; silvery with dark bands along
  the sides; large broad pectoral fins
* first dorsal fin tall and narrow with a single venomous spine; long low second dorsal; small caudal lobes
* swims slowly by flapping the big pectoral fins ("flying" over the bottom); finds prey with ampullae of
  Lorenzini and smell; eats benthic crustaceans and molluscs, also jellies and sea squirts
* no bioluminescence; solitary or in loose groups
* lays eggs in horny capsules; fished with bottom trawls in Japan for fish paste

Sources: https://www.fishbase.se/summary/Chimaera-phantasma.html
https://www.godac.jamstec.go.jp/bismal/j/view/9001276  https://en.wikipedia.org/wiki/Silver_chimaera

Game simplifications: neutral - its spine stings a player who attacks it; shrimp stands in for benthic
crustaceans.
"""

INFO = dict(
    names=("Chimaera", "ギンザメ"),
    egg=(0xB6BCC4, 0x3C3A40),
    role="neutral",
    voice="shark",
    prey=["deep_sea_shrimp"],
    sounds={
        "ambient": (2, "Chimaera glides", "ギンザメが泳ぐ"),
        "hurt": (2, "Chimaera hurts", "ギンザメが傷つく"),
        "death": (1, "Chimaera dies", "ギンザメが死ぬ"),
        "flop": (2, "Chimaera flops", "ギンザメが跳ねる"),
        "bite": (2, "Chimaera stings", "ギンザメがトゲで刺す"),
    },
    # 20-960 m, mostly 90-540 m; just above the shelf-edge and slope floor
    spawn=dict(weight=6, group=(1, 2), depth_m=(60, 200, 550, 960), placement="near_floor",
               cave_factor=0.3, open_factor=1.0, max_light=8, clearance=2, cap=(12, 96)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=1.0, health=16, armor=1,
    # glides over the floor flapping its big pectoral fins; keeps its distance
    traits=dict(steering=(12, 4, 0.002), zone=("near_floor", 0.9, 150), flees=(2.2, 10), home=40, ambient=500),
    render=dict(eyeshine=(0.8, ["right_eye", "left_eye"])))

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="shark_flesh", count=(2, 3), looting=1, chance=0.65)]
