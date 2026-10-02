"""ニュウドウカジカ / blobfish (blob sculpin), Psychrolutes phrictus.

The Japanese name ニュウドウカジカ belongs to the North Pacific P. phrictus (JAMSTEC BISMaL 9000373), not to the
Australian "Mr Blobby" P. marcidus of the famous photo, which has no standard Japanese name. The mod uses
P. phrictus: it lives off northern Japan, and the two look alike.

Research (FishBase summaries "Psychrolutes phrictus" and "Psychrolutes marcidus"; JAMSTEC BISMaL 9000373;
Wikipedia "Psychrolutes phrictus" and "Psychrolutes marcidus"; Drazen et al. 2003 on its egg nests, Mar. Ecol.
Prog. Ser.):
* P. phrictus: 500-2800 m (BISMaL records 220-1542 m, most near 800-1600 m), bathydemersal on the slope,
  Japan and the Bering Sea to California; up to 70 cm and 9.5 kg. P. marcidus: 600-1200 m off SE Australia, 30 cm
* broad flat head, large widely spaced eyes, a curved mouth with fleshy lips, small cirri on head and body;
  grey to black above, pale below; a gelatinous layer under the skin gives support at depth. The drooping "nose"
  and sagging blob shape are decompression damage of trawled fish - alive it is a tadpole-shaped sculpin
* the gelatinous flesh is slightly less dense than water, so it hovers just above the floor with little muscle
  work; sluggish ambush / sit-and-wait feeder on sea pens, crabs, gastropods, cephalopods, sea cucumbers
* no bioluminescence; solitary, but adults guard egg nests on the seabed (first known deep-sea egg brooding)

Sources: https://www.fishbase.se/summary/Psychrolutes-phrictus.html
https://www.godac.jamstec.go.jp/bismal/j/view/9000373  https://en.wikipedia.org/wiki/Psychrolutes_phrictus
https://en.wikipedia.org/wiki/Psychrolutes_marcidus

Game simplifications: the model keeps the famous surface "blob" look (bulbous head, droopy nose, pink-grey)
because that is how players recognise it; 1 block long; harmless; no nests.
"""

INFO = dict(
    names=("Blobfish", "ニュウドウカジカ"),
    egg=(0xC9A3A0, 0x7A6A70),
    role="passive",
    voice="anglerfish",
    sounds={
        "ambient": (2, "Blobfish drifts", "ニュウドウカジカが漂う"),
        "hurt": (2, "Blobfish hurts", "ニュウドウカジカが傷つく"),
        "death": (1, "Blobfish dies", "ニュウドウカジカが死ぬ"),
        "flop": (2, "Blobfish flops", "ニュウドウカジカが跳ねる"),
    },
    # 500-2800 m, most records 800-1600 m; hovers just above the slope floor
    spawn=dict(weight=6, group=(1, 1), depth_m=(400, 800, 1600, 2800), placement="near_floor",
               cave_factor=0.5, open_factor=1.0, max_light=8, cap=(16, 64),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=1.5)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="swimmer", size_m=0.4, health=8,
    # barely moves: hovers just above the floor, drifting a little now and then
    traits=dict(steering=(10, 3, 0.0012), zone=("near_floor", 0.5, 400), flees=(1.2, 5), hangs_still=True, home=16, ambient=600),
    render=dict(eyeshine=(0.4, ["right_eye", "left_eye"])))

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="blobfish_flesh", count=(1, 2), looting=1, chance=0.75)]
