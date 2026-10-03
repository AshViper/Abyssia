"""カラスガレイ / Greenland halibut (Greenland turbot), Reinhardtius hippoglossoides.

Japanese name: カラスガレイ (烏鰈), family カレイ科 (Pleuronectidae).

Research (FishBase summary "Reinhardtius hippoglossoides"; NOAA Fisheries "Greenland turbot"; Wikipedia "Greenland
halibut"; Vollen & Albert 2008, "Pelagic behavior of adult Greenland halibut", Fishery Bulletin 106:457-470):
* Arctic and boreal North Atlantic and North Pacific (Bering Sea, Okhotsk Sea, off northern Japan); 1-2200 m,
  mainly 500-1000 m on the slope and in deep fjords; bathydemersal in cold water (1-4 C)
* 50-80 cm, up to 1.2 m; right-eyed flatfish but less flat than most: elongate oval body, the left eye on the
  head's upper edge so both look up, large mouth with strong teeth, long dorsal and anal fins along the edges,
  broad slightly concave tail
* dark grey-brown above, the blind underside also fairly dark (grey), unlike most flatfish
* swims slowly over the bottom, often also off it in midwater (more pelagic than other flatfish); eats fish,
  squid and shrimp
* important food fish (engawa, fillets) in Japan and the North Atlantic

Sources: https://www.fishbase.se/summary/Reinhardtius-hippoglossoides.html
https://en.wikipedia.org/wiki/Greenland_halibut  https://www.fisheries.noaa.gov/species/greenland-turbot

Game simplifications: modelled as a flat horizontal oval (fins kept low and long because the template's fins stay
upright); glides slowly just above the floor; harmless; drops its own raw fillet.
"""

INFO = dict(
    names=("Greenland Halibut", "カラスガレイ"),
    egg=(0x4A443C, 0x8A8060),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Greenland halibut glides", "カラスガレイが泳ぐ"),
        "hurt": (2, "Greenland halibut hurts", "カラスガレイが傷つく"),
        "death": (1, "Greenland halibut dies", "カラスガレイが死ぬ"),
        "flop": (2, "Greenland halibut flops", "カラスガレイが跳ねる"),
    },
    # 1-2200 m, mainly 500-1000 m on the cold slope
    spawn=dict(weight=8, group=(1, 2), depth_m=(200, 500, 1500, 2200), placement="near_floor",
               cave_factor=0.4, open_factor=1.0, max_light=8, cap=(16, 64),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=1.5)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py); ~1.3 blocks long
INFO["java"] = dict(kind="swimmer", size_m=0.88, health=12,
    # glides slowly and flat just above the floor
    traits=dict(steering=(8, 2, 0.0015), zone=("near_floor", 0.5, 240), flees=(1.8, 6), home=32, ambient=550),
    render=dict(eyeshine=(0.6, ["right_eye", "left_eye"])))

# Drops (FS01): its own raw fillet, looting adds up to 1 per level
INFO["loot"] = [dict(item="raw_greenland_halibut", count=(1, 2), looting=1)]
