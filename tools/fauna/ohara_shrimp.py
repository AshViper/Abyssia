"""オハラエビ / Alvinocaris longirostris.

Research (Kikuchi & Ohta 1995; Yahagi et al. 2015 on Okinawa Trough vent shrimp populations; Wang et al. 2016,
PLOS ONE, on its bacterial associates; Wikipedia "Alvinocarididae"):
* 930-1736 m (35 N to 3 S): hydrothermal vents of the Okinawa Trough and Manus Basin, and cold seeps of Sagami Bay
  (1120-1220 m) and the South China Sea - perhaps the only alvinocaridid on both vents and seeps
* pale, pinkish, with a long straight rostrum, small eyes and long antennae
* swarms over vent chimneys and rock, grazing on chemosynthetic bacteria (it also carries bacteria on its body)

Game simplifications: real depths mapped onto the world's vent fields (fauna/__init__.py vent_depth); bound to the
vent field it spawned at; spawns only around vent cores.
"""

from fauna import vent_depth

INFO = dict(
    names=("Ohara Shrimp", "オハラエビ"),
    egg=(0xE8C8C0, 0xC07870),
    role="passive",
    voice="shrimp",
    sounds={
        "ambient": (2, "Vent shrimp swims", "オハラエビが泳ぐ"),
        "hurt": (2, "Vent shrimp hurts", "オハラエビが傷つく"),
        "death": (1, "Vent shrimp dies", "オハラエビが死ぬ"),
        "flick": (2, "Vent shrimp flicks away", "オハラエビが跳ねて逃げる"),
    },
    spawn=dict(weight=12, group=(3, 6), depth_m=vent_depth(930, 1736), placement="near_floor",
               cave_factor=1.0, open_factor=1.0, max_light=15, cap=(10, 12),
               habitat=[dict(blocks="#abyssia:fauna/vent", radius=4, min=1, factor=1.0, required=True)]),
)
