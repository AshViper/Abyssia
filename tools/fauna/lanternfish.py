"""ハダカイワシ / Watase's lanternfish (headlight fish), Diaphus watasei.

Names: in Japanese ハダカイワシ is both the family (ハダカイワシ科, Myctophidae) and the standard name of this species
(JAMSTEC BISMaL "Diaphus watasei ハダカイワシ"; Tokyo islands fisheries centre rare-fish report no. 126).

Research (FishBase summary "Diaphus watasei"; BISMaL; Fisheries Science 2024, "Age, growth and feeding habit of
Watase's lanternfish Diaphus watasei in the East China Sea"; PLOS One 2024, "Variation in lanternfish
(Myctophidae) photophore structure"):
* Indo-West Pacific; off Japan from Sagami Bay / Aomori southward, the East China Sea and the Okinawa Trough
* benthopelagic over the outer shelf and continental slope: 100-2005 m, mainly 200-700 m; by day near the bottom at
  ~300-600 m, rising to ~100 m at night; adults are taken by bottom trawls on the slope
* one of the largest lanternfishes, 17-20 cm; slender, blunt-snouted, with huge eyes and large, easily shed silvery
  scales (hence "hadaka", naked); dark blue-grey back
* photophores: rows of round organs along the belly and lower flanks, and big "headlight" organs (dorsonasal and
  ventronasal) in front of the eyes; lanternfish light is blue (~470-490 nm)
* unlike its plankton-eating relatives it is mainly piscivorous (small lanternfishes, squid, larger crustaceans);
  occurs in large numbers on the slope

Game simplifications: the day depth and night rise are merged into one spawn band; kept near the floor
(benthopelagic); shown as schools of 4-10; treated as passive prey for the larger midwater fishes.
"""

INFO = dict(
    names=("Lanternfish", "ハダカイワシ"),
    egg=(0x39424F, 0x6FC0FF),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Lanternfish swims", "ハダカイワシが泳ぐ"),
        "hurt": (2, "Lanternfish hurts", "ハダカイワシが傷つく"),
        "death": (1, "Lanternfish dies", "ハダカイワシが死ぬ"),
        "flop": (2, "Lanternfish flops", "ハダカイワシが跳ねる"),
    },
    # mainly 200-700 m (by day near the floor at 300-600 m, at night up to ~100 m), recorded 100-2005 m
    spawn=dict(weight=16, group=(4, 10), depth_m=(100, 200, 700, 2005), placement="near_floor",
               cave_factor=0.6, open_factor=1.0, max_light=8, cap=(120, 32)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.15, health=3,
    # dense schools over the slope, rising at night
    traits=dict(steering=(25, 8, 0.0025), zone=("near_floor", 1.0, 120), migrates=0.3, schools=6, flees=(3.0, 10), home=32, ambient=350),
    render=dict(eyeshine=(0.6, ["right_eye", "left_eye"]), glow=dict(bones=["body", "tail", "head"], base=0.75, flicker=0.15)))

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="abyssal_fish_fillet", count=(1, 1), looting=1, chance=0.8)]
