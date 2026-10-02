"""ミツクリザメ / goblin shark, Mitsukurina owstoni.

Research (FishBase summary "Mitsukurina owstoni"; Wikipedia "Goblin shark"; Nakaya et al. 2016, "Slingshot feeding
of the goblin shark", Scientific Reports):
* usually 270-960 m on the upper continental slope and seamounts, recorded 30-1300 m (seen to ~2000 m)
* 3-4 m; soft, flabby pink-grey body (blood vessels under translucent skin), bluish fins; long flat blade-like
  snout packed with electroreceptors (ampullae of Lorenzini)
* protrusible "slingshot" jaws shoot forward at up to 3.1 m/s and snap shut on the prey
* slow, bathydemersal (near the bottom); eats rattails, dragonfishes and other fishes, cephalopods, crustaceans
* no record of attacks on people

Game simplifications: 3 blocks long; neutral (bites back only when hurt); prey includes the mod's viperfish,
barreleye and shrimp, and vanilla cod and squid.
"""

INFO = dict(
    names=("Goblin Shark", "ミツクリザメ"),
    egg=(0xC9A0A0, 0x7C8FA8),
    role="predator",
    voice="shark",
    prey=["viperfish", "barreleye", "deep_sea_shrimp", "minecraft:cod", "minecraft:squid"],
    sounds={
        "ambient": (2, "Goblin shark swims", "ミツクリザメが泳ぐ"),
        "hurt": (2, "Goblin shark hurts", "ミツクリザメが傷つく"),
        "death": (1, "Goblin shark dies", "ミツクリザメが死ぬ"),
        "flop": (2, "Goblin shark flops", "ミツクリザメが跳ねる"),
        "bite": (2, "Goblin shark snaps its jaws out", "ミツクリザメがあごを突き出す"),
    },
    spawn=dict(weight=4, group=(1, 1), depth_m=(200, 270, 960, 2000), placement="near_floor",
               cave_factor=0.3, open_factor=1.0, max_light=8, clearance=3, cap=(12, 96)),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="shark_flesh", count=(2, 3), looting=1, chance=0.7)]
