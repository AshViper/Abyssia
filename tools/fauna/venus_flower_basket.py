"""カイロウドウケツ / Venus' flower basket, Euplectella aspergillum Owen, 1841.
Porifera > Hexactinellida > Lyssacinosida > Euplectellidae.

Research (Wikipedia "Venus' flower basket"; SeaLifeBase "Euplectella aspergillum"; Animal Diversity Web; Toba
Aquarium "カイロウドウケツ"):
* western Pacific (Philippines, Japan - Sagami Bay, Suruga Bay, Tosa Bay and south) and Indian Ocean; 100-1000 m,
  most common below 500 m
* stands on sandy-mud bottoms, anchored in the sediment by a tuft of hair-thin glassy basal spicules 5-20 cm long
* a curved, thin-walled tube of fused silica spicules in a square-mesh lattice with oblique ridges, closed at the top
  by a sieve plate; usually 10-30 cm tall (7.5 cm - 1.3 m), 2-6 cm wide; white to cream
* filter feeder (bacteria, plankton, marine snow); glass sponges can form reefs, and many stand close together
* a pair of spongicolid shrimps (Spongicola) enters as larvae and ends up trapped inside for life - hence the
  Japanese name "偕老同穴" (growing old together in one grave), a wedding gift symbol
* the sponge itself: bioluminescence only speculated, not shown; its spicules conduct light like optical fibres - it
  does not glow here

Sources: https://en.wikipedia.org/wiki/Venus%27_flower_basket  https://www.sealifebase.ca/summary/Euplectella-aspergillum.html
https://animaldiversity.org/accounts/Euplectella_aspergillum/  https://aquarium.co.jp/picturebook/euplectella-aspergillum.html

Game simplifications: an "environmental" animal - never moves, does not despawn; a sponge cannot withdraw, so the
"retract" event is silent-subtitled (the sound is only water rushing out through the lattice); the lattice is a
texture pattern, the tube is not hollow and the shrimp pair is not modelled; ~0.7 block tall.
"""

INFO = dict(
    names=("Venus' Flower Basket", "カイロウドウケツ"),
    egg=(0xEAE6D8, 0xB8B0A0),
    role="environmental",
    voice="tubeworm",
    sounds={
        "retract": (2, "", ""),
        "hurt": (2, "Glass sponge cracks", "カイロウドウケツがきしむ"),
        "death": (1, "Glass sponge breaks", "カイロウドウケツが砕ける"),
    },
    # 100-1000 m, mostly below 500 m; soft sediment
    spawn=dict(weight=8, group=(1, 3), depth_m=(100, 500, 950, 1000), placement="seabed",
               cave_factor=0.3, open_factor=1.0, max_light=15, cap=(6, 16),
               substrate=[dict(blocks="#abyssia:fauna/soft_sediment", factor=2.0)]),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="sessile", size_m=0.3, health=5, render=dict(fish=False, pitch=False))
