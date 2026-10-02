"""オオイトヒキイワシ / tripod fish, Bathypterois grallator.

Japanese name: オオイトヒキイワシ (JAMSTEC BISMaL 9000347). イトヒキイワシ is B. atricolor and ナガヅエエソ is
B. guentheri, smaller relatives also found off Japan; B. grallator is the classic, largest tripod fish (the
longest "stilts"), so it is the one used here.

Research (FishBase summary "Bathypterois grallator"; JAMSTEC BISMaL 9000347; Wikipedia "Bathypterois grallator";
Sulak 1977, "The systematics and biology of Bathypterois", Galathea Report):
* 878-4720 m (BISMaL records 1610-5220 m) on the lower slope, continental rise and ocean ridges, on soft sediment
* up to 43 cm; slender, mostly silvery-white body, darker around the head and gills; tiny eyes (not used for
  hunting); the lower caudal ray and the two pelvic rays are elongated up to ~1 m (about 3x body length),
  flexible when swimming and stiffened (probably by fluid pressure) when standing; the long pectoral rays spread
  forward and up over the head like antennae
* stands motionless on the tripod, facing into the current, and snatches drifting copepods and small crustaceans
  (larger fish take small fish and decapods) found by touch/mechanoreception with the pectoral rays
* a poor swimmer: short swims between long rests on the floor; simultaneous hermaphrodite
* no bioluminescence; benthic and solitary (spaced out, though several may stand within sight of each other)

Sources: https://www.fishbase.se/summary/Bathypterois-grallator.html
https://www.godac.jamstec.go.jp/bismal/j/view/9000347  https://en.wikipedia.org/wiki/Bathypterois_grallator

Game simplifications: stands on the floor most of the time and hops a few blocks when disturbed; "feeding" is a
flick of the head at the current; passive; about 1 block tall on its stilts.
"""

INFO = dict(
    names=("Tripod Fish", "オオイトヒキイワシ"),
    egg=(0xB8BCC0, 0x3A3F48),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Tripod fish stands", "オオイトヒキイワシがたたずむ"),
        "hurt": (2, "Tripod fish hurts", "オオイトヒキイワシが傷つく"),
        "death": (1, "Tripod fish dies", "オオイトヒキイワシが死ぬ"),
        "flop": (2, "Tripod fish flops", "オオイトヒキイワシが跳ねる"),
    },
    # 878-4720 m; stands on the open sediment plains
    spawn=dict(weight=8, group=(1, 1), depth_m=(800, 1500, 4000, 5200), placement="seabed",
               cave_factor=0.3, open_factor=1.0, max_light=10, cap=(16, 48),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=3.0)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.35, health=6,
    # stands on its fin-ray stilts facing the current; short hops when disturbed
    traits=dict(steering=(12, 4, 0.0015), zone=("bottom", 0.6, 500), flees=(2.0, 6), hangs_still=True, home=12, ambient=700),
    render=dict(fish=False, eyeshine=(0.4, ["right_eye", "left_eye"])))

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="abyssal_fish_fillet", count=(1, 2), looting=1, chance=0.7)]
