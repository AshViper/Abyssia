"""ベニオオウミグモ / giant sea spider, Colossendeis colossea Wilson, 1881.
Arthropoda > Pycnogonida > Pantopoda > Colossendeidae (genus Colossendeis = オオウミグモ属).

The Japanese name ベニオオウミグモ is the one JAMSTEC BISMaL (9000522) gives for C. colossea; "ウミグモ" is only the
name of the whole class.

Research (Wikipedia "Colossendeis colossea"; MBARI "Giant sea spider"; JAMSTEC BISMaL 9000522; Aquamarine Fukushima
"ベニオオウミグモ"):
* continental slopes and abyssal floor, 420-5200 m (MBARI sees Colossendeis mostly at 2200-4000 m); all main oceans
  except the Arctic
* the largest pycnogonid: leg span up to ~70 cm, while trunk + proboscis + abdomen are only ~7 cm; four pairs of very
  long, jointed, stilt-like legs, a long tube-like proboscis, a pair of slender palps; orange-red to crimson ("beni")
* eats cnidarians and other soft invertebrates - sea anemones, hydroids, jellies: the proboscis sucks out their
  tissues, and MBARI has seen one clip anemone tentacles off to eat them elsewhere
* walks slowly over sediment and rock on the tips of its legs; usually met alone
* no bioluminescence

Sources: https://en.wikipedia.org/wiki/Colossendeis_colossea  https://www.mbari.org/animal/giant-sea-spider/
https://www.godac.jamstec.go.jp/bismal/j/view/9000522  https://www.aquamarine.or.jp/animals/giantseaspider/

Game simplifications: harmless, slow stilt-walking grazer of the slope floor that backs away when hit; the body sits
on the floor under high-kneed legs (the model is grounded at the body); legs animate with the generic travelling-
wave clip (tentacle_move) as its walk; a loose preference for company is the generic walker's, real animals are
solitary; spans ~1.1 blocks.
"""

INFO = dict(
    names=("Giant Sea Spider", "ベニオオウミグモ"),
    egg=(0xA8452C, 0xE0A080),
    role="passive",
    voice="crab",
    sounds={
        "ambient": (2, "Sea spider creaks", "ベニオオウミグモがきしむ"),
        "hurt": (2, "Sea spider hurts", "ベニオオウミグモが傷つく"),
        "death": (1, "Sea spider dies", "ベニオオウミグモが死ぬ"),
        "step": (3, None, None),
    },
    # 420-5200 m, most sightings 1000-4000 m; slope floor, sediment and rock alike
    spawn=dict(weight=5, group=(1, 1), depth_m=(420, 1000, 4000, 5200), placement="seabed",
               cave_factor=0.5, open_factor=1.0, max_light=8, cap=(6, 48)),
)

# Behaviour and in-game size of the data-driven entity (tools/fauna_java.py)
INFO["java"] = dict(kind="walker", size_m=0.7, health=6,
    # slow, deliberate stilt walk; long pauses on one spot (feeding on an anemone)
    traits=dict(move_clip="tentacle_move", speed=0.4, wander=200, home=16, ambient=600),
    render=dict(fish=False, pitch=False))
