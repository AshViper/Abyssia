"""ウロコフネタマガイ (スケーリーフット) / scaly-foot snail, Chrysomallon squamiferum.

Research (Wikipedia "Scaly-foot gastropod"; Chen et al. 2015, "The heart of a dragon", PLOS ONE; Smithsonian Ocean
"Meet the metal snail from the bottom of the ocean"; IUCN Red List assessment, Endangered):
* 2400-2900 m, known only from three vent fields of the Indian Ocean: Kairei and Solitaire (Central Indian Ridge)
  and Longqi (Southwest Indian Ridge)
* the only living animal known to build iron sulfides (pyrite, greigite) into its skeleton: a black, metallic outer
  shell layer and hundreds of overlapping mineralised scales (sclerites) on the foot; ~4.5 cm
* feeds on symbiotic gammaproteobacteria in a hugely enlarged oesophageal gland, served by an outsized heart
* lives in dense aggregations on warm chimney walls; withdraws into its armour

Game simplifications: real depths mapped onto the world's vent fields (fauna/__init__.py vent_depth); armour 10 and
a quarter of the damage while withdrawn; bound to its vent field; no drops (an endangered species, not a resource).
"""

from fauna import vent_depth

INFO = dict(
    names=("Scaly-foot Snail", "ウロコフネタマガイ"),
    egg=(0x202020, 0xB08040),
    role="passive",
    voice="snail",
    sounds={
        "step": (2, None, None),
        "hurt": (2, "Scaly-foot snail clinks", "ウロコフネタマガイが硬い音を立てる"),
        "death": (1, "Scaly-foot snail dies", "ウロコフネタマガイが死ぬ"),
        "retract": (2, "Scaly-foot snail withdraws", "ウロコフネタマガイが殻にこもる"),
    },
    spawn=dict(weight=8, group=(2, 4), depth_m=vent_depth(2400, 2900), placement="seabed",
               cave_factor=1.0, open_factor=1.0, max_light=15, cap=(6, 10),
               habitat=[dict(blocks="#abyssia:fauna/vent", radius=4, min=1, factor=1.0, required=True)]),
)
