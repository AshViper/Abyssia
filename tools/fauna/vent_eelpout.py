"""ゲンゲ / vent eelpout (pink vent fish), Thermarces cerberus.

Research (FishBase summary "Thermarces cerberus"; Sancho et al. 2005, "Selective predation by the zoarcid fish
Thermarces cerberus at hydrothermal vents", Deep-Sea Research I; Wikipedia "Thermarces cerberus"):
* 2300-2630 m at hydrothermal vents (and seeps) of the East Pacific Rise and Galapagos Rift
* pale pink, eel-shaped, up to ~60 cm; blunt head, fleshy lips, big fan-like pectoral fins, a continuous
  dorsal-anal fin around the tail
* sluggish; lies among the tubeworm clumps and mussel beds
* feeds selectively on vent gastropods (mostly the limpet Lepetodrilus elevatus) and amphipods (Ventiella sulfuris)
  picked from the tubes and rock

Game simplifications: real depths mapped onto the world's vent fields (fauna/__init__.py vent_depth); its prey is
too small to be entities, so foraging is shown as snaps at the rock with a puff of sediment; bound to its vent
field.
"""

from fauna import vent_depth

INFO = dict(
    names=("Vent Eelpout", "ゲンゲ"),
    egg=(0xE6B8B0, 0x9A6A66),
    role="passive",
    voice="gulper_eel",
    sounds={
        "ambient": (2, "Vent eelpout stirs", "ゲンゲが身じろぎする"),
        "hurt": (2, "Vent eelpout hurts", "ゲンゲが傷つく"),
        "death": (1, "Vent eelpout dies", "ゲンゲが死ぬ"),
        "flop": (2, "Vent eelpout flops", "ゲンゲが跳ねる"),
    },
    spawn=dict(weight=6, group=(1, 1), depth_m=vent_depth(2300, 2630), placement="near_floor",
               cave_factor=1.0, open_factor=1.0, max_light=15, cap=(2, 16),
               habitat=[dict(blocks="#abyssia:fauna/vent", radius=4, min=1, factor=1.0, required=True)]),
)

# Drops (F01): the spec drop table; looting adds up to 1 per level
INFO["loot"] = [dict(item="eelpout_flesh", count=(2, 3), looting=1, chance=0.75)]
