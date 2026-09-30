"""サツマハオリムシ / Lamellibrachia satsuma.

Research (Miura, Tsukahara & Hashimoto 1997, original description; Wikipedia "Lamellibrachia satsuma"; Kobayashi et
al. 2023, DNA Research, genome of L. satsuma; Miyamoto et al. 2013, PLOS ONE, its neuroanatomy):
* found at a hydrothermal vent in Kagoshima Bay at only 82 m - the shallowest vestimentiferan known; also at cold
  seeps (Hatsushima in Sagami Bay, Daini Tenryu Knoll in the Nankai Trough) to ~1170 m
* thin, very long beige tubes in dense bushes; small orange-red plumes
* like Riftia, fed by symbiotic sulfur-oxidising bacteria (epsilon- and gammaproteobacteria)

Game simplifications: real depths mapped onto the world's vent fields (fauna/__init__.py vent_depth); as the giant
tubeworm; the shallow member of the vent communities.
"""

from fauna import vent_depth

INFO = dict(
    names=("Satsuma Tubeworm", "サツマハオリムシ"),
    egg=(0xD8C8A8, 0xE06030),
    role="environmental",
    voice="tubeworm",
    sounds={
        "retract": (2, "Tubeworms withdraw", "サツマハオリムシが引っ込む"),
        "hurt": (2, "Tubeworm hurts", "サツマハオリムシが傷つく"),
        "death": (1, "Tubeworm dies", "サツマハオリムシが死ぬ"),
    },
    spawn=dict(weight=20, group=(1, 2), depth_m=vent_depth(82, 1170), placement="seabed",
               cave_factor=1.0, open_factor=1.0, max_light=15, cap=(4, 12),
               habitat=[dict(blocks="#abyssia:fauna/vent", radius=3, min=1, factor=1.0, required=True)]),
)
