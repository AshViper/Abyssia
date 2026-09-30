"""シンカイエビ / deep-sea shrimp of the type of Acanthephyra purpurea.

Research (Wikipedia "Acanthephyra purpurea"; Frank et al. 2024/2025 on oplophoroid vision and bioluminescence,
Communications Biology / Vision Research; Herring 1976 on luminous secretions of oceanic decapods):
* meso- and bathypelagic, 300-3292 m, performing diel vertical migration (deeper by day, shallower by night)
* blood-red (red light does not reach these depths, so it reads as black); serrated rostrum, antennae far longer
  than the body; ~8-10 cm
* Acanthephyra has no cuticular photophores (those belong to the related Oplophoridae); threatened, it spews a bright
  bioluminescent secretion from near the mouth and escapes backwards with a tail flip
* swarms loosely; food for the midwater predators (anglerfishes, gulper eels, viperfishes...)

Game simplifications: the generated model carries a photophore mask - unused, since the real animal has none; the
spew is a particle cloud.
"""

INFO = dict(
    names=("Deep-sea Shrimp", "シンカイエビ"),
    egg=(0x8E1B1B, 0xE06040),
    role="passive",
    voice="shrimp",
    sounds={
        "ambient": (2, "Shrimp swims", "シンカイエビが泳ぐ"),
        "hurt": (2, "Shrimp hurts", "シンカイエビが傷つく"),
        "death": (1, "Shrimp dies", "シンカイエビが死ぬ"),
        "flick": (2, "Shrimp flicks away", "シンカイエビが跳ねて逃げる"),
        "spew": (2, "Shrimp spews light", "シンカイエビが発光液を吐く"),
    },
    spawn=dict(weight=16, group=(4, 8), depth_m=(300, 500, 1500, 3300), placement="open_water",
               cave_factor=0.8, open_factor=1.0, max_light=8, cap=(120, 32)),
)
